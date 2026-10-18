package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.core.SlideQueue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class QueueRepositoryTest {
    @TempDir
    lateinit var dir: File

    private val api = FakeApi()
    private val renderer = FakeRenderer()
    private val network = FakeNetwork()
    private val store = InMemoryQueueStore()
    private var context = foldContext()
    private lateinit var files: FileStore
    private lateinit var queue: SlideQueue<com.wiggyserver.immichwallpaper.core.PreparedSlide>
    private var nextId = 0

    @BeforeEach
    fun setUp() {
        files = FileStore(dir)
        queue = SlideQueue(3)
    }

    private fun repo() = QueueRepository(api, files, queue, store, renderer, { context }, network, newId = { "s${nextId++}" })

    @Test
    fun `refill fills the queue to three slides, one file per screen`() {
        val result = repo().refill()
        assertEquals(RefillResult.Done(added = 3), result)
        assertEquals(3, queue.size)
        queue.snapshot().forEach { slide ->
            assertEquals(listOf(1 or 2 or 16, 1 or 2 or 4), slide.files.map { it.which })
            slide.files.forEach { assertTrue(files.exists(it.fileName)) }
        }
        assertEquals(queue.snapshot(), store.load())
    }

    @Test
    fun `source previews are deleted once the slide is rendered`() {
        repo().refill()
        val expected = queue.snapshot().flatMap { s -> s.files.map { it.fileName } }.toSet()
        assertEquals(expected, files.names().toSet())
    }

    @Test
    fun `crops slides carry crop hints, lock-only slides do not`() {
        repo().refill()
        assertEquals(setOf(2, 3), queue.peek()!!.files[1].cropHints!!.keys)
        context = foldContext(target = com.wiggyserver.immichwallpaper.core.ApplyTarget.LOCK)
        repo().invalidate()
        repo().refill()
        assertNull(queue.peek()!!.files[1].cropHints)
    }

    @Test
    fun `with side-by-side on, slides use two portraits and the inner render shows both`() {
        context = foldContext(Settings(sideBySide = true))
        repo().refill()
        val slide = queue.peek()!!
        assertEquals(2, slide.assetIds.size)
        val inner = files.file(slide.files[1].fileName).readText()
        assertTrue(slide.assetIds.all { inner.contains(it) }, inner)
    }

    @Test
    fun `never queues the same photo twice`() {
        api.pool = (1..4).map { portraitAsset("p$it") }
        repo().refill()
        val ids = queue.snapshot().flatMap { it.assetIds }
        assertEquals(ids.distinct(), ids)
    }

    @Test
    fun `does not repeat recently shown photos while others are available`() {
        val r = repo()
        r.refill()
        val firstIds = queue.snapshot().flatMap { it.assetIds }.toSet()
        repeat(3) { r.take()?.let(r::discard) }
        r.refill()
        assertTrue(queue.snapshot().flatMap { it.assetIds }.none { it in firstIds })
    }

    @Test
    fun `a full queue makes no network calls`() {
        val r = repo()
        r.refill()
        val calls = api.randomCalls
        assertEquals(RefillResult.Done(added = 0), r.refill())
        assertEquals(calls, api.randomCalls)
    }

    @Test
    fun `offline or wifi-only on a metered network skips the refill`() {
        network.isOnline = false
        assertEquals(RefillResult.Skipped(SkipReason.OFFLINE), repo().refill())
        network.isOnline = true
        network.isUnmetered = false
        context = foldContext(Settings(wifiOnlyRefill = true))
        assertEquals(RefillResult.Skipped(SkipReason.WIFI_ONLY), repo().refill())
        assertEquals(0, api.randomCalls)
    }

    @Test
    fun `an unreachable server stops the refill and keeps what was added`() {
        api.failWith = ImmichException.Unreachable(IOException("down"))
        assertEquals(RefillResult.Failed(FailReason.UNREACHABLE, added = 0), repo().refill())
    }

    @Test
    fun `a rejected key is reported as an auth failure`() {
        api.failWith = ImmichException.Unauthorized()
        assertEquals(RefillResult.Failed(FailReason.AUTH, added = 0), repo().refill())
    }

    @Test
    fun `a selection smaller than the queue fills what it can and says so`() {
        api.pool = listOf(portraitAsset("a"), portraitAsset("b"))
        val r = repo()
        assertEquals(RefillResult.Done(2, limited = true), r.refill())
        assertEquals(setOf("a", "b"), r.queued().flatMap { it.assetIds }.toSet())
        assertEquals(2, r.smallPoolSize)
    }

    @Test
    fun `a small selection keeps rotating, shown photos come back`() {
        api.pool = listOf(portraitAsset("a"), portraitAsset("b"))
        val r = repo()
        r.refill()
        val shown = r.take()!!
        r.discard(shown)
        r.refill()
        assertEquals(2, r.size)
        assertTrue(shown.assetIds.single() in r.queued().flatMap { it.assetIds })
    }

    @Test
    fun `an album smaller than the shown history keeps cycling after every photo was shown`() {
        // 0.6.2 bug: a 162-photo album stopped with "No photos" once all of it had been shown.
        context = foldContext(Settings(sideBySide = true))
        api.pool = (1..40).map { portraitAsset("p$it") }
        val r = repo()
        repeat(300) {
            assertTrue(r.refill() is RefillResult.Done, "refill $it")
            r.discard(r.take()!!)
        }
        assertEquals(RefillResult.Done(1), r.refill())
        assertEquals(3, r.size)
    }

    @Test
    fun `a single photo keeps coming back`() {
        api.pool = listOf(portraitAsset("only"))
        val r = repo()
        assertEquals(RefillResult.Done(1, limited = true), r.refill())
        r.discard(r.take()!!)
        assertEquals(RefillResult.Done(1, limited = true), r.refill())
        assertEquals(listOf("only"), r.queued().flatMap { it.assetIds })
        assertEquals(1, r.smallPoolSize)
    }

    @Test
    fun `after a sources change no photo fetched for the old sources is used`() {
        val r = repo()
        r.refill() // buffers more photos than it queues
        api.pool = (1..40).map { portraitAsset("new$it") }
        r.invalidate()
        r.refill()
        assertEquals(3, r.size)
        assertTrue(r.queued().flatMap { it.assetIds }.all { it.startsWith("new") }, r.queued().toString())
    }

    @Test
    fun `a large selection is not a small pool, and changing sources forgets the size`() {
        val r = repo()
        r.refill()
        assertEquals(null, r.smallPoolSize)
        api.pool = listOf(portraitAsset("a"))
        r.invalidate()
        r.refill()
        assertEquals(1, r.smallPoolSize)
        api.pool = (1..40).map { portraitAsset("p$it") }
        r.invalidate()
        assertEquals(null, r.smallPoolSize)
    }

    @Test
    fun `an empty library ends the refill instead of looping`() {
        api.pool = emptyList()
        assertEquals(RefillResult.Failed(FailReason.NO_PHOTOS, added = 0), repo().refill())
    }

    @Test
    fun `a failed render skips that slide and leaves no stray files`() {
        renderer.failNext = true
        val result = repo().refill()
        assertEquals(RefillResult.Done(added = 3), result)
        val expected = queue.snapshot().flatMap { s -> s.files.map { it.fileName } }.toSet()
        assertEquals(expected, files.names().toSet())
    }

    @Test
    fun `face lookups only happen with face-aware cropping, and their failure is not fatal`() {
        api.failFacesWith = ImmichException.Http(500)
        assertEquals(RefillResult.Done(added = 3), repo().refill())
        api.failFacesWith = null
        repo().invalidate()
        context = foldContext(Settings(faceAwareCrop = false))
        api.faceCalls.clear()
        repo().refill()
        assertTrue(api.faceCalls.isEmpty())
    }

    @Test
    fun `take removes the next slide and persists the rest, discard deletes its files`() {
        val r = repo()
        r.refill()
        val slide = r.take()!!
        assertEquals(2, store.load().size)
        r.discard(slide)
        slide.files.forEach { assertFalse(files.exists(it.fileName)) }
    }

    @Test
    fun `restore reloads persisted slides whose files exist and removes orphans`() {
        repo().refill()
        val saved = store.load()
        files.delete(saved[1].files[0].fileName)
        files.write("orphan.jpg") { it.write(1) }
        queue = SlideQueue(3)
        repo().restore()
        assertEquals(listOf(saved[0], saved[2]), queue.snapshot())
        assertFalse(files.exists("orphan.jpg"))
        assertFalse(files.exists(saved[1].files[1].fileName))
    }

    @Test
    fun `invalidate drops queued slides and their files`() {
        val r = repo()
        r.refill()
        r.invalidate()
        assertEquals(0, queue.size)
        assertTrue(files.names().isEmpty())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `drop removes only the slides showing a given photo`() {
        val r = repo()
        r.refill()
        val victim = r.queued()[1]
        val kept = r.queued() - victim
        r.drop { victim.assetIds.first() in it.assetIds }
        assertEquals(kept, r.queued())
        assertEquals(kept, store.load())
        victim.files.forEach { assertFalse(files.exists(it.fileName)) }
        kept.flatMap { it.files }.forEach { assertTrue(files.exists(it.fileName)) }
    }

    @Test
    fun `stops when the file store is over its size cap`() {
        files = FileStore(dir, maxBytes = 1)
        assertEquals(RefillResult.Failed(FailReason.STORAGE_FULL, added = 1), repo().refill())
    }

    @Test
    fun `the swap path is not blocked while a refill downloads and renders`() {
        val r = repo()
        r.refill() // fill, then make room for one more
        r.take()?.let(r::discard)
        var tookWithoutBlocking = false
        renderer.duringRender = {
            val t = Thread { r.take() }
            t.start()
            t.join(2_000)
            tookWithoutBlocking = !t.isAlive
        }
        r.refill()
        assertTrue(tookWithoutBlocking)
    }

    @Test
    fun `invalidating during a refill drops the stale slide and stops the refill`() {
        val r = repo()
        renderer.duringRender = { r.invalidate() }
        r.refill()
        assertEquals(0, queue.size)
        assertTrue(files.names().isEmpty(), files.names().toString())
    }
}
