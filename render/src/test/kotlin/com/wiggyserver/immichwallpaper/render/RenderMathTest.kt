package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RenderMathTest {
    @Test
    fun `scales a crop from original pixels to the preview's pixels`() {
        // Crop planned on the 3000x4000 original, source file is the 1440x1920 preview.
        assertEquals(PixelRect(0, 384, 1440, 1536), RenderMath.scaleRect(PixelRect(0, 800, 3000, 3200), Size(3000, 4000), Size(1440, 1920)))
    }

    @Test
    fun `scaled rects never leave the target image`() {
        assertEquals(PixelRect(0, 0, 1440, 1920), RenderMath.scaleRect(PixelRect(0, 0, 3001, 4001), Size(3000, 4000), Size(1440, 1920)))
    }

    @Test
    fun `identical sizes leave the rect alone`() {
        val r = PixelRect(10, 20, 110, 220)
        assertEquals(r, RenderMath.scaleRect(r, Size(500, 500), Size(500, 500)))
    }

    @Test
    fun `sample size is the largest power of two that still covers the destination`() {
        assertEquals(1, RenderMath.sampleSize(PixelRect(0, 0, 1440, 1920), PixelRect(0, 0, 1224, 1848)))
        assertEquals(2, RenderMath.sampleSize(PixelRect(0, 0, 4000, 3000), PixelRect(0, 0, 1900, 1400)))
        assertEquals(4, RenderMath.sampleSize(PixelRect(0, 0, 4000, 4000), PixelRect(0, 0, 900, 900)))
        assertEquals(1, RenderMath.sampleSize(PixelRect(0, 0, 100, 100), PixelRect(0, 0, 1000, 1000)))
    }

    @Test
    fun `averages colors per channel`() {
        assertEquals(0xFF804020.toInt(), RenderMath.average(intArrayOf(0xFF000000.toInt(), 0xFFFF8040.toInt())))
        assertEquals(0xFF000000.toInt(), RenderMath.average(IntArray(0)))
    }

    @Test
    fun `seam colors are darkened edge averages`() {
        assertEquals(0xFF666666.toInt(), RenderMath.darken(0xFF808080.toInt(), 0.2))
    }

    @Test
    fun `edge strips sit just outside the seam, clipped to the canvas`() {
        val seam = PixelRect(100, 0, 116, 50)
        assertEquals(PixelRect(92, 0, 100, 50), RenderMath.leftStrip(seam, 8))
        assertEquals(PixelRect(116, 0, 124, 50), RenderMath.rightStrip(seam, 8, canvasWidth = 200))
        assertEquals(PixelRect(0, 0, 4, 50), RenderMath.leftStrip(PixelRect(4, 0, 10, 50), 8))
        assertEquals(PixelRect(10, 0, 12, 50), RenderMath.rightStrip(PixelRect(4, 0, 10, 50), 8, canvasWidth = 12))
    }
}
