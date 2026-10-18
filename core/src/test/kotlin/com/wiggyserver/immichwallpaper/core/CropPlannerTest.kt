package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class CropPlannerTest {
    private val planner = CropPlanner()
    private val landscapeSrc = Size(4000, 3000)
    private val portraitSrc = Size(3000, 4000)
    private val cover = Size(1248, 1972)
    private val innerLandscape = Size(2448, 1848)
    private val safe = SafeZone(top = 0.33, bottom = 0.18)

    private fun assertAspect(crop: PixelRect, target: Size) =
        assertTrue(abs(crop.width.toDouble() / crop.height - target.ratio) < 0.002, "crop $crop is not ${target.ratio}")

    private fun assertInside(crop: PixelRect, src: Size) =
        assertTrue(crop.left >= 0 && crop.top >= 0 && crop.right <= src.width && crop.bottom <= src.height, "crop $crop outside $src")

    @Test
    fun `without faces it center-crops to fill`() {
        val crop = planner.crop(landscapeSrc, cover)
        assertEquals(3000, crop.height)
        assertEquals(1899, crop.width)
        assertEquals(1051, crop.left)
        assertAspect(crop, cover)
    }

    @Test
    fun `a wider target keeps full width and centers vertically`() {
        val crop = planner.crop(portraitSrc, innerLandscape)
        assertEquals(PixelRect(0, 868, 3000, 3133), crop)
        assertAspect(crop, innerLandscape)
    }

    @Test
    fun `same aspect keeps the whole image`() =
        assertEquals(PixelRect(0, 0, 3000, 4000), planner.crop(portraitSrc, Size(1500, 2000)))

    @Test
    fun `slides horizontally to keep a face near the edge`() {
        val face = Face(Box(0.02, 0.4, 0.12, 0.55))
        val crop = planner.crop(landscapeSrc, cover, listOf(face))
        assertEquals(0, crop.left)
        assertTrue(crop.right >= 480)
    }

    @Test
    fun `centers the face horizontally when there is room`() {
        val face = Face(Box(0.60, 0.4, 0.70, 0.55)) // center x = 2600
        val crop = planner.crop(landscapeSrc, cover, listOf(face))
        assertTrue(abs((crop.left + crop.right) / 2 - 2600) <= 1, "$crop")
    }

    @Test
    fun `places faces inside the safe band, below the clock`() {
        // Portrait photo shown on inner landscape: crop is 3000x2265 out of 4000 tall, room to move.
        val face = Face(Box(0.4, 0.30, 0.6, 0.40)) // y 1200..1600
        val crop = planner.crop(portraitSrc, innerLandscape, listOf(face), safe)
        val bandTop = crop.top + safe.top * crop.height
        val bandBottom = crop.bottom - safe.bottom * crop.height
        assertTrue(1200 >= bandTop && 1600 <= bandBottom, "face 1200..1600 outside band $bandTop..$bandBottom ($crop)")
        assertInside(crop, portraitSrc)
    }

    @Test
    fun `a face at the very top clamps the crop to the image edge`() {
        val face = Face(Box(0.4, 0.0, 0.6, 0.05))
        val crop = planner.crop(portraitSrc, innerLandscape, listOf(face), safe)
        assertEquals(0, crop.top)
    }

    @Test
    fun `keeps every face when their union fits`() {
        val faces = listOf(Face(Box(0.10, 0.4, 0.18, 0.5)), Face(Box(0.40, 0.4, 0.48, 0.5)))
        val crop = planner.crop(landscapeSrc, cover, faces)
        assertTrue(crop.left <= 400 && crop.right >= 1920, "$crop drops a face")
    }

    @Test
    fun `when faces cannot all fit, keeps the largest one`() {
        val small = Face(Box(0.02, 0.4, 0.06, 0.45))
        val big = Face(Box(0.80, 0.3, 0.95, 0.5)) // x 3200..3800
        val crop = planner.crop(landscapeSrc, cover, listOf(small, big))
        assertTrue(crop.left <= 3200 && crop.right >= 3800, "$crop drops the big face")
    }

    @Test
    fun `always returns a crop of the target aspect inside the image`() {
        val faces = listOf(Face(Box(0.9, 0.9, 1.0, 1.0)))
        for (src in listOf(landscapeSrc, portraitSrc, Size(1067, 1600), Size(1080, 1920))) {
            for (target in listOf(cover, innerLandscape, Size(1224, 1848), Size(2448, 2448))) {
                val crop = planner.crop(src, target, faces, safe)
                assertInside(crop, src)
                assertAspect(crop, target)
            }
        }
    }
}
