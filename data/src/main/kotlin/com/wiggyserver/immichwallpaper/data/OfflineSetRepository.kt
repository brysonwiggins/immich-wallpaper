package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyPlanner
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.FallbackRotator
import com.wiggyserver.immichwallpaper.core.IndexStore
import com.wiggyserver.immichwallpaper.core.LayoutPlanner
import com.wiggyserver.immichwallpaper.core.PreparedSlide

enum class SlotResult { Ready, Pending, NotFound }

data class SyncResult(val updated: Int, val missing: Int, val failed: FailReason?)

/**
 * The hand-picked offline photos: up to [MAX_SLOTS],
 * each kept as a source preview plus a ready slide, rotated in order when the queue is empty.
 */
class OfflineSetRepository(
    private val api: ImmichApi,
    private val files: FileStore,
    private val store: OfflineSetStore,
    renderer: SlideRenderer,
    private val context: () -> RenderContext,
) : OfflineSlots {
    private val builder = SlideBuilder(api, renderer)

    @Synchronized
    override fun setSlot(index: Int, assetId: String): SlotResult {
        require(index in 0 until MAX_SLOTS) { "slot must be 0..${MAX_SLOTS - 1}" }
        store.all().firstOrNull { it.index == index }?.let(::deleteFiles)
        val pending = OfflineSlot(index, assetId, SlotStatus.PENDING)
        store.put(pending)
        return try {
            refresh(pending)
        } catch (e: ImmichException) {
            SlotResult.Pending // offline or key problem: the next sync retries
        }
    }

    @Synchronized
    override fun clearSlot(index: Int) {
        store.all().firstOrNull { it.index == index }?.let(::deleteFiles)
        store.remove(index)
    }

    /** Re-checks every slot against the server: deleted → MISSING, edited or pending → re-download. */
    @Synchronized
    fun sync(): SyncResult {
        var updated = 0
        var missing = 0
        for (slot in store.all()) {
            try {
                val remote = api.asset(slot.assetId)
                when {
                    remote == null -> if (slot.status != SlotStatus.MISSING) {
                        deleteFiles(slot)
                        store.put(slot.copy(status = SlotStatus.MISSING, slide = null, asset = null))
                        missing++
                    }
                    slot.status == SlotStatus.PENDING || remote.updatedAt != slot.updatedAt ->
                        if (refresh(slot) == SlotResult.Ready) updated++
                }
            } catch (e: ImmichException.Unreachable) {
                return SyncResult(updated, missing, FailReason.UNREACHABLE)
            } catch (e: ImmichException.Unauthorized) {
                return SyncResult(updated, missing, FailReason.AUTH)
            }
        }
        return SyncResult(updated, missing, null)
    }

    /** Re-renders ready slots for the current settings/screens from kept sources (no network). Returns how many. */
    @Synchronized
    fun rerender(): Int = store.all().count { slot ->
        val asset = slot.asset
        if (slot.status != SlotStatus.READY || asset == null || !files.exists(sourceName(asset.id))) return@count false
        runCatching { store.put(slot.copy(slide = render(slot.index, asset))) }.isSuccess
    }

    /**
     * Next offline slide in slot order (for the swap path; no network). Deliberately not synchronized
     * with the slow operations above: it only reads the store (itself thread-safe) and files that are
     * replaced atomically.
     */
    fun next(): PreparedSlide? {
        val slots = store.all().sortedBy { it.index }
        val byKey = slots.associateBy { it.index.toString() }
        val rotator = FallbackRotator(slots.map { it.index.toString() }, object : IndexStore {
            override fun get() = store.rotationIndex
            override fun set(index: Int) { store.rotationIndex = index }
        }) { key -> byKey[key]?.slide?.files?.all { files.exists(it.fileName) } == true }
        return rotator.next()?.let { byKey.getValue(it).slide }
    }

    /** Current slots in index order (status display; no network). */
    override fun slots(): List<OfflineSlot> = store.all().sortedBy { it.index }

    @Synchronized
    fun cleanup() {
        val keep = store.all().flatMap { referencedFiles(it) }.toSet()
        files.cleanup(keep)
    }

    /** Network and auth errors propagate; anything else (bad image, render failure) leaves the slot pending. */
    private fun refresh(slot: OfflineSlot): SlotResult {
        return try {
            val remote = api.asset(slot.assetId) ?: run {
                store.remove(slot.index)
                return SlotResult.NotFound
            }
            val faces = runCatching { api.faces(slot.assetId) }.getOrDefault(emptyList())
            val asset = remote.asset.copy(faces = faces)
            files.write(sourceName(asset.id)) { api.downloadPreview(asset.id, it) }
            store.put(slot.copy(status = SlotStatus.READY, updatedAt = remote.updatedAt, asset = asset, slide = render(slot.index, asset)))
            SlotResult.Ready
        } catch (e: ImmichException.Unreachable) {
            throw e
        } catch (e: ImmichException.Unauthorized) {
            throw e
        } catch (e: Exception) {
            SlotResult.Pending
        }
    }

    private fun render(index: Int, asset: Asset): PreparedSlide {
        val ctx = context()
        val jobs = ApplyPlanner().plan(ctx.settings.applyTarget, ctx.capabilities, ctx.screens, ctx.active)
        val plan = LayoutPlanner().plan(listOf(asset), jobs.flatMap { it.viewports }, sideBySide = false)!!
        return builder.render("offline-$index", plan, jobs, mapOf(asset.id to files.file(sourceName(asset.id))), files, ctx.settings.faceAwareCrop)
    }

    private fun referencedFiles(slot: OfflineSlot): List<String> =
        slot.slide?.files.orEmpty().map { it.fileName } + listOfNotNull(slot.asset?.let { sourceName(it.id) })

    private fun deleteFiles(slot: OfflineSlot) {
        val stillUsed = store.all().filter { it.index != slot.index }.flatMap(::referencedFiles).toSet()
        (referencedFiles(slot) + sourceName(slot.assetId)).filter { it !in stillUsed }.forEach(files::delete)
    }

    private fun sourceName(assetId: String) = "src-$assetId.jpg"

    companion object {
        const val MAX_SLOTS = 10
    }
}
