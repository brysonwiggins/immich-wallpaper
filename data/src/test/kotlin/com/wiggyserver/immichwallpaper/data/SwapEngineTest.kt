package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.SlideQueue
import com.wiggyserver.immichwallpaper.core.SwapDecision
import com.wiggyserver.immichwallpaper.core.SwapResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.InputStream

class SwapEngineTest {
    @TempDir
    lateinit var dir: File

    private val api = FakeApi()
    private val renderer = FakeRenderer()
    private lateinit var queueFiles: FileStore
    private lateinit var offlineFiles: FileStore
    private lateinit var queueRepo: QueueRepository
    private lateinit var offlineRepo: OfflineSetRepository
    private val applied = mutableListOf<String>()
    private var failWith: Exception? = null
    private val errors = mutableListOf<ApplyException>()
    private val results = mutableListOf<SwapResult<PreparedSlide>>()
    private var refills = 0
    private var now = 0L

    private val setter = object : WallpaperSetter {
        override fun setPlain(stream: InputStream, which: Int) = record(which)
        override fun setWithCrops(stream: InputStream, crops: Map<Int, PixelRect>, which: Int) = record(which)
        private fun record(which: Int) {
            failWith?.let { throw it }
            applied += "which=$which"
        }
    }

    @BeforeEach
    fun setUp() {
        queueFiles = FileStore(File(dir, "queue"))
        offlineFiles = FileStore(File(dir, "offline"))
        queueRepo = QueueRepository(api, queueFiles, SlideQueue(3), InMemoryQueueStore(), renderer, { foldContext() }, FakeNetwork())
        offlineRepo = OfflineSetRepository(api, offlineFiles, InMemoryOfflineSetStore(), renderer) { foldContext() }
    }

    private fun engine(queueOnly: Boolean = true) = SwapEngine(
        queue = queueRepo,
        offline = offlineRepo,
        applier = SlideApplier(setter, queueFiles),
        offlineApplier = SlideApplier(setter, offlineFiles),
        activeScreenBit = { 16 },
        refill = { refills++ },
        clock = { now },
        cooldownMillis = { 0 },
        executor = { it.run() },
        onApplyError = { errors += it },
        onResult = { results += it },
    )

    @Test
    fun `applies a queued slide and deletes its files afterwards`() {
        queueRepo.refill()
        val slide = queueRepo.queued().first()
        assertEquals(SwapDecision.Started, engine().onScreenOff())
        assertEquals(2, applied.size)
        slide.files.forEach { assertFalse(queueFiles.exists(it.fileName)) }
        assertEquals(1, refills)
    }

    @Test
    fun `falls back to the offline set and keeps its files`() {
        offlineRepo.setSlot(0, "p1")
        engine().onScreenOff()
        assertEquals(2, applied.size)
        val slide = (results.single() as SwapResult.Applied).slide
        slide.files.forEach { assertTrue(offlineFiles.exists(it.fileName)) }
    }

    @Test
    fun `reports unsupported platform features and drops the failed queue slide`() {
        queueRepo.refill()
        val slide = queueRepo.queued().first()
        failWith = UnsupportedOperationException("gone")
        engine().onScreenOff()
        assertTrue(errors.single() is ApplyException.CropsUnsupported)
        slide.files.forEach { assertFalse(queueFiles.exists(it.fileName)) }
    }

    @Test
    fun `other failures are reported as results only`() {
        queueRepo.refill()
        failWith = IllegalStateException("io")
        engine().onScreenOff()
        assertTrue(errors.isEmpty())
        assertTrue(results.single() is SwapResult.Failed)
    }
}
