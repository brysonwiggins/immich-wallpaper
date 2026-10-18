package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyPlanner
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.LayoutPlanner
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.SlideQueue
import java.io.File
import java.util.UUID

enum class SkipReason { OFFLINE, WIFI_ONLY }
enum class FailReason { UNREACHABLE, AUTH, NO_PHOTOS, STORAGE_FULL }

sealed interface RefillResult {
    /** [limited]: the selection has fewer photos than the queue holds, so it was filled as far as possible. */
    data class Done(val added: Int, val limited: Boolean = false) : RefillResult
    data class Skipped(val reason: SkipReason) : RefillResult
    data class Failed(val reason: FailReason, val added: Int) : RefillResult
}

/**
 * Keeps [queue] at its target with ready-to-apply slides. Runs on a
 * background worker; the swap path only calls [take] and [discard], which never touch the network.
 */
class QueueRepository(
    private val api: ImmichApi,
    private val files: FileStore,
    private val queue: SlideQueue<PreparedSlide>,
    private val store: QueueStore,
    renderer: SlideRenderer,
    private val context: () -> RenderContext,
    private val network: NetworkMonitor,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    private val builder = SlideBuilder(api, renderer)
    private val layout = LayoutPlanner()
    private val buffer = mutableListOf<Asset>()
    private val recent = ArrayDeque<String>()

    // Two locks: refillLock serializes refills (slow: network + render); stateLock guards the queue,
    // its persistence and `recent` only briefly, so the swap path (take/discard) never waits on I/O.
    private val refillLock = Any()
    private val stateLock = Any()
    @Volatile private var generation = 0

    /**
     * How many photos the selection has, when that is fewer than one search batch (the same short
     * answer twice in a row); null for larger selections. Drives "only N photos" on Home.
     */
    @Volatile var smallPoolSize: Int? = null
        private set
    private var lastShortAnswer: Set<String>? = null
    private var bufferGeneration = 0

    fun refill(): RefillResult = synchronized(refillLock) { refillLocked() }

    private fun refillLocked(): RefillResult {
        // Photos fetched for earlier sources or settings must never be used after a change.
        if (bufferGeneration != generation) {
            buffer.clear()
            bufferGeneration = generation
        }
        if (queue.isFull) return RefillResult.Done(0)
        val ctx = context()
        if (!network.isOnline) return RefillResult.Skipped(SkipReason.OFFLINE)
        if (ctx.settings.wifiOnlyRefill && !network.isUnmetered) return RefillResult.Skipped(SkipReason.WIFI_ONLY)

        val jobs = ApplyPlanner().plan(ctx.settings.applyTarget, ctx.capabilities, ctx.screens, ctx.active)
        val viewports = jobs.flatMap { it.viewports }
        val sideBySide = ctx.settings.effectiveSideBySide(ctx.screens)
        val startGeneration = generation
        var added = 0
        var attempts = 0
        while (queue.deficit > 0 && attempts++ < MAX_ATTEMPTS) {
            if (files.isOverCap()) return RefillResult.Failed(FailReason.STORAGE_FULL, added)
            try {
                topUpBuffer(ctx.filter)
                val plan = layout.plan(buffer, viewports, sideBySide) ?: return RefillResult.Failed(FailReason.NO_PHOTOS, added)
                buffer.removeAll { a -> plan.consumed.any { it.id == a.id } }
                val id = newId()
                val sources = download(plan.consumed)
                val slide = try {
                    builder.render(id, builder.withFaces(plan, ctx.settings.faceAwareCrop), jobs, sources, files, ctx.settings.faceAwareCrop)
                } finally {
                    sources.values.forEach(File::delete)
                }
                val stale = synchronized(stateLock) {
                    if (generation != startGeneration) {
                        true
                    } else {
                        queue.offer(slide)
                        persist()
                        false
                    }
                }
                if (stale) {
                    discard(slide) // settings or screens changed mid-refill; the caller schedules a fresh one
                    return RefillResult.Done(added)
                }
                added++
            } catch (e: ImmichException.Unreachable) {
                return RefillResult.Failed(FailReason.UNREACHABLE, added)
            } catch (e: ImmichException.Unauthorized) {
                return RefillResult.Failed(FailReason.AUTH, added)
            } catch (e: NoPhotos) {
                smallPoolSize = 0
                return RefillResult.Failed(FailReason.NO_PHOTOS, added)
            } catch (e: PoolExhausted) {
                return RefillResult.Done(added, limited = true)
            } catch (e: Exception) {
                // One bad photo (HTTP error, undecodable image, render failure): skip it and carry on.
            }
        }
        return RefillResult.Done(added)
    }

    private object NoPhotos : Exception() {
        private fun readResolve(): Any = NoPhotos
    }

    /** Every photo of a small selection is already queued: the queue is as full as it can get. */
    private object PoolExhausted : Exception() {
        private fun readResolve(): Any = PoolExhausted
    }

    private fun topUpBuffer(filter: SourceFilter) {
        if (buffer.size >= MIN_BUFFER) return
        val fetched = api.randomAssets(filter, BATCH).distinctBy { it.id }
        notePoolSize(fetched)
        if (fetched.isEmpty() && buffer.isEmpty()) throw NoPhotos
        val (queued, shown) = synchronized(stateLock) { queue.snapshot().flatMap { it.assetIds }.toSet() to recent.toSet() }
        val candidates = fetched.filter { a -> a.id !in queued && buffer.none { it.id == a.id } }
        // Prefer photos not shown lately; a small selection has nothing else, so shown ones come back.
        val fresh = candidates.filter { it.id !in shown }.ifEmpty { candidates }
        if (fresh.isEmpty() && buffer.isEmpty()) throw PoolExhausted
        buffer += fresh
    }

    private fun notePoolSize(fetched: List<Asset>) {
        if (fetched.size >= BATCH) {
            smallPoolSize = null
            lastShortAnswer = null
            return
        }
        val ids = fetched.map { it.id }.toSet()
        if (ids == lastShortAnswer) smallPoolSize = ids.size else lastShortAnswer = ids
    }

    private fun download(assets: List<Asset>): Map<String, File> {
        val sources = mutableMapOf<String, File>()
        try {
            assets.forEach { a -> sources[a.id] = files.write("src-${a.id}.jpg") { api.downloadPreview(a.id, it) } }
        } catch (e: Exception) {
            sources.values.forEach(File::delete)
            throw e
        }
        return sources
    }

    /** Next slide for the swap path (no network, never waits for a refill). */
    fun take(): PreparedSlide? = synchronized(stateLock) {
        queue.poll()?.also { slide ->
            persist()
            slide.assetIds.forEach { recent.addLast(it) }
            while (recent.size > RECENT_LIMIT) recent.removeFirst()
        }
    }

    /** Deletes a slide's files after it was applied (the system keeps its own copy). */
    fun discard(slide: PreparedSlide) = slide.files.forEach { files.delete(it.fileName) }

    /** On start: reload persisted slides whose files survived, delete everything else. */
    fun restore() = synchronized(refillLock) {
        synchronized(stateLock) {
            val ok = store.load().filter { s -> s.files.all { files.exists(it.fileName) } }
            ok.forEach { queue.offer(it) }
            persist()
            files.cleanup(keep = queue.snapshot().flatMap { s -> s.files.map { it.fileName } }.toSet())
        }
    }

    /** Settings or screens changed: queued slides are stale (an in-flight refill drops what it built). */
    fun invalidate() {
        synchronized(stateLock) {
            smallPoolSize = null
            lastShortAnswer = null
            generation++
            queue.clear().forEach(::discard)
            persist()
        }
    }

    /** Removes the slides matching [predicate] (e.g. ones showing a photo the user just hid); an in-flight refill drops its result. */
    fun drop(predicate: (PreparedSlide) -> Boolean) {
        synchronized(stateLock) {
            generation++
            queue.removeIf(predicate).forEach(::discard)
            persist()
        }
    }

    /** Current queued slides, in order (status display). */
    fun queued(): List<PreparedSlide> = queue.snapshot()

    val size: Int get() = queue.size

    private fun persist() = store.save(queue.snapshot())

    private companion object {
        const val BATCH = 25
        const val MIN_BUFFER = 6
        const val MAX_ATTEMPTS = 12
        const val RECENT_LIMIT = 200
    }
}
