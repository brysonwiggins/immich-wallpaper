package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.ApplyMethod
import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Box
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.core.SlideFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.time.LocalDateTime

class JsonStoresTest {
    @TempDir
    lateinit var dir: File

    private val slide = PreparedSlide(
        "s1", listOf("a", "b"),
        listOf(
            SlideFile(19, ApplyMethod.CROPS, "slide-s1-0.jpg", mapOf(0 to PixelRect(0, 0, 1248, 1972))),
            SlideFile(6, ApplyMethod.PLAIN, "slide-s1-1.jpg", null),
        ),
    )
    private val asset = Asset("a", Size(3000, 4000), LocalDateTime.parse("2024-05-01T10:00"), setOf("p1"), listOf(Face(Box(0.1, 0.2, 0.3, 0.4), "p1"), Face(Box(0.5, 0.5, 0.6, 0.6))))

    @Test
    fun `queue store round-trips slides across instances`() {
        JsonQueueStore(File(dir, "queue.json")).save(listOf(slide))
        assertEquals(listOf(slide), JsonQueueStore(File(dir, "queue.json")).load())
    }

    @Test
    fun `missing or corrupt files load as empty instead of crashing`() {
        assertTrue(JsonQueueStore(File(dir, "none.json")).load().isEmpty())
        File(dir, "bad.json").writeText("{not json")
        assertTrue(JsonQueueStore(File(dir, "bad.json")).load().isEmpty())
        File(dir, "bad2.json").writeText("[[[")
        assertTrue(JsonOfflineSetStore(File(dir, "bad2.json")).all().isEmpty())
    }

    @Test
    fun `offline set store round-trips slots with assets, faces and the rotation index`() {
        val s = JsonOfflineSetStore(File(dir, "offline.json"))
        s.put(OfflineSlot(3, "a", SlotStatus.READY, "v1", asset, slide))
        s.put(OfflineSlot(1, "b", SlotStatus.PENDING))
        s.rotationIndex = 2
        val reloaded = JsonOfflineSetStore(File(dir, "offline.json"))
        assertEquals(listOf(1, 3), reloaded.all().map { it.index })
        assertEquals(OfflineSlot(3, "a", SlotStatus.READY, "v1", asset, slide), reloaded.all()[1])
        assertEquals(2, reloaded.rotationIndex)
        reloaded.remove(1)
        assertEquals(listOf(3), JsonOfflineSetStore(File(dir, "offline.json")).all().map { it.index })
    }

    @Test
    fun `config defaults to the owner defaults and persists changes`() {
        val repo = ConfigRepository(File(dir, "config.json"))
        assertEquals(AppConfig(), repo.config.value)
        assertEquals(Settings(), repo.config.value.settings)
        assertFalse(repo.config.value.onboarded)
        val changed = AppConfig(
            serverUrl = "https://photos.example.com",
            filter = SourceFilter(listOf("al"), listOf("pe"), true, excludedAlbumIds = listOf("xa"), excludedPersonIds = listOf("xp"), excludedAssetIds = listOf("x1"), notInAlbum = true),
            settings = Settings(applyTarget = ApplyTarget.LOCK, sideBySide = true, cooldownMillis = 0, faceAwareCrop = false, wifiOnlyRefill = true),
            onboarded = true,
        )
        repo.update { changed }
        assertEquals(changed, repo.config.value)
        assertEquals(changed, ConfigRepository(File(dir, "config.json")).config.value)
    }

    @Test
    fun `a corrupt or invalid config falls back to defaults`() {
        File(dir, "c.json").writeText("""{"settings":{"cooldownMillis":-5}}""")
        assertEquals(AppConfig(), ConfigRepository(File(dir, "c.json")).config.value)
        File(dir, "d.json").writeText("garbage")
        assertEquals(AppConfig(), ConfigRepository(File(dir, "d.json")).config.value)
    }

    @Test
    fun `the api key is never written to the config file`() {
        val repo = ConfigRepository(File(dir, "config.json"))
        repo.update { it.copy(serverUrl = "https://x.example") }
        val text = File(dir, "config.json").readText()
        assertTrue(!text.contains("apiKey") && !text.contains("key"), text)
    }
}
