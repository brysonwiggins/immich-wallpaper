package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class OfflineSetRepositoryTest {
    @TempDir
    lateinit var dir: File

    private val api = FakeApi()
    private val renderer = FakeRenderer()
    private val store = InMemoryOfflineSetStore()
    private var context = foldContext()
    private lateinit var files: FileStore

    @BeforeEach
    fun setUp() {
        files = FileStore(dir)
    }

    private fun repo() = OfflineSetRepository(api, files, store, renderer) { context }

    private fun slot(i: Int) = store.all().single { it.index == i }

    @Test
    fun `adding a photo downloads it once and renders a ready slide`() {
        assertEquals(SlotResult.Ready, repo().setSlot(0, "p1"))
        val s = slot(0)
        assertEquals(SlotStatus.READY, s.status)
        assertEquals("v1", s.updatedAt)
        assertEquals(2, s.slide!!.files.size)
        s.slide!!.files.forEach { assertTrue(files.exists(it.fileName)) }
        assertTrue(files.exists("src-p1.jpg"))
        assertEquals(listOf("p1"), api.downloads)
    }

    @Test
    fun `slots are listed in index order`() {
        val r = repo()
        r.setSlot(3, "p1")
        r.setSlot(1, "p2")
        assertEquals(listOf(1, 3), r.slots().map { it.index })
    }

    @Test
    fun `only ten slots exist`() {
        assertThrows<IllegalArgumentException> { repo().setSlot(10, "p1") }
        assertThrows<IllegalArgumentException> { repo().setSlot(-1, "p1") }
    }

    @Test
    fun `a photo that no longer exists is rejected`() {
        assertEquals(SlotResult.NotFound, repo().setSlot(0, "gone"))
        assertTrue(store.all().isEmpty())
    }

    @Test
    fun `while the server is unreachable the slot waits as pending`() {
        api.failWith = ImmichException.Unreachable(IOException("down"))
        assertEquals(SlotResult.Pending, repo().setSlot(0, "p1"))
        assertEquals(SlotStatus.PENDING, slot(0).status)
    }

    @Test
    fun `a render failure leaves the slot pending, to retry on the next sync`() {
        renderer.failNext = true
        assertEquals(SlotResult.Pending, repo().setSlot(0, "p1"))
        assertEquals(SlotStatus.PENDING, slot(0).status)
    }

    @Test
    fun `replacing a slot removes the old photo's files`() {
        val r = repo()
        r.setSlot(0, "p1")
        r.setSlot(0, "p2")
        assertFalse(files.exists("src-p1.jpg"))
        assertEquals("p2", slot(0).assetId)
    }

    @Test
    fun `clearing a slot removes it and its files`() {
        val r = repo()
        r.setSlot(0, "p1")
        r.clearSlot(0)
        assertTrue(store.all().isEmpty())
        assertTrue(files.names().isEmpty())
    }

    @Test
    fun `rotation goes in slot order, wraps, and skips slots that are not ready`() {
        val r = repo()
        r.setSlot(0, "p1"); r.setSlot(2, "p3")
        api.failWith = ImmichException.Unreachable(IOException("down"))
        r.setSlot(1, "p2") // pending
        assertEquals(listOf("p1", "p3", "p1"), List(3) { r.next()!!.assetIds.single() })
    }

    @Test
    fun `rotation position is persisted`() {
        repo().apply { setSlot(0, "p1"); setSlot(1, "p2") }
        repo().next()
        assertEquals("p2", repo().next()!!.assetIds.single())
    }

    @Test
    fun `no ready slots means nothing to rotate`() = assertNull(repo().next())

    @Test
    fun `sync marks photos deleted on the server as missing and frees their files`() {
        val r = repo()
        r.setSlot(0, "p1")
        api.remote["p1"] = null
        assertEquals(SyncResult(updated = 0, missing = 1, failed = null), r.sync())
        assertEquals(SlotStatus.MISSING, slot(0).status)
        assertNull(slot(0).slide)
        assertTrue(files.names().isEmpty())
    }

    @Test
    fun `sync re-downloads photos edited on the server`() {
        val r = repo()
        r.setSlot(0, "p1")
        api.remote["p1"] = RemoteAsset(Asset("p1", Size(4000, 3000), null), "v2")
        assertEquals(SyncResult(updated = 1, missing = 0, failed = null), r.sync())
        assertEquals("v2", slot(0).updatedAt)
        assertEquals(listOf("p1", "p1"), api.downloads)
    }

    @Test
    fun `sync completes pending slots and leaves unchanged ones alone`() {
        val r = repo()
        r.setSlot(0, "p1")
        api.failWith = ImmichException.Unreachable(IOException("down"))
        r.setSlot(1, "p2")
        api.failWith = null
        assertEquals(SyncResult(updated = 1, missing = 0, failed = null), r.sync())
        assertEquals(SlotStatus.READY, slot(1).status)
        assertEquals(listOf("p1", "p2"), api.downloads)
    }

    @Test
    fun `sync stops quietly when the server is unreachable`() {
        repo().setSlot(0, "p1")
        api.failWith = ImmichException.Unreachable(IOException("down"))
        assertEquals(SyncResult(0, 0, FailReason.UNREACHABLE), repo().sync())
        assertEquals(SlotStatus.READY, slot(0).status)
    }

    @Test
    fun `re-render after a settings change works offline from the kept source`() {
        val r = repo()
        r.setSlot(0, "p1")
        context = foldContext(target = ApplyTarget.LOCK)
        api.failWith = ImmichException.Unreachable(IOException("down"))
        assertEquals(1, r.rerender())
        assertNull(slot(0).slide!!.files[0].cropHints)
        assertEquals(listOf("p1"), api.downloads)
    }

    @Test
    fun `cleanup removes files no slot references`() {
        val r = repo()
        r.setSlot(0, "p1")
        files.write("stray.jpg") { it.write(1) }
        r.cleanup()
        assertFalse(files.exists("stray.jpg"))
        assertTrue(files.exists("src-p1.jpg"))
    }

    @Test
    fun `the swap path can rotate while a sync re-renders`() {
        val r = repo()
        r.setSlot(0, "p1")
        api.remote["p1"] = RemoteAsset(Asset("p1", Size(3000, 4000), null), "v2")
        var rotated = false
        renderer.duringRender = {
            val t = Thread { rotated = r.next() != null }
            t.start()
            t.join(2_000)
        }
        r.sync()
        assertTrue(rotated)
    }
}
