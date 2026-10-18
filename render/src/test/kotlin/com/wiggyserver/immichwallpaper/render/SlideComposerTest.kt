package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.ApplyMethod
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Region
import com.wiggyserver.immichwallpaper.core.ScreenJob
import com.wiggyserver.immichwallpaper.core.ScreenRender
import com.wiggyserver.immichwallpaper.core.Screens
import com.wiggyserver.immichwallpaper.core.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

class SlideComposerTest {
    /** Records calls; images are just labels. */
    private class RecordingBackend : RasterBackend<String> {
        val calls = mutableListOf<String>()
        val released = mutableListOf<String>()
        var failDraw = false
        override fun imageSize(file: File) = Size(1440, 1920)
        override fun decodeRegion(file: File, region: PixelRect, sampleSize: Int) = "img:${file.name}:$region:$sampleSize".also { calls += "decode $it" }
        override fun createCanvas(size: Size) = "canvas".also { calls += "canvas $size" }
        override fun draw(canvas: String, image: String, dest: PixelRect) { if (failDraw) error("draw failed"); calls += "draw $dest" }
        override fun fill(canvas: String, rect: PixelRect, argb: Int) { calls += "fill $rect" }
        override fun fillHorizontalGradient(canvas: String, rect: PixelRect, leftArgb: Int, rightArgb: Int) { calls += "gradient $rect ${Integer.toHexString(leftArgb)} ${Integer.toHexString(rightArgb)}" }
        override fun averageColor(canvas: String, rect: PixelRect) = 0xFF808080.toInt()
        override fun encodeJpeg(canvas: String, out: OutputStream, quality: Int) { calls += "encode $quality" }
        override fun release(image: String) { released += image }
    }

    private val asset = Asset("a", Size(3000, 4000), null)
    private val job = ScreenJob(Screens.FOLD8_COVER, 2, ApplyMethod.PLAIN, Screens.FOLD8_COVER.viewports())
    private val render = ScreenRender(
        job, Size(200, 100),
        regions = listOf(Region(asset, PixelRect(0, 0, 96, 100), PixelRect(0, 0, 3000, 4000)), Region(asset, PixelRect(104, 0, 200, 100), PixelRect(0, 0, 3000, 4000))),
        seams = listOf(PixelRect(96, 0, 104, 100)),
        cropHints = null,
    )

    @Test
    fun `decodes each crop in preview pixels, subsampled, then fills seams and encodes`() {
        val backend = RecordingBackend()
        SlideComposer(backend, quality = 90).render(render, mapOf("a" to File("a.jpg")), ByteArrayOutputStream())
        assertEquals("canvas Size(width=200, height=100)", backend.calls[0])
        assertEquals("decode img:a.jpg:PixelRect(left=0, top=0, right=1440, bottom=1920):8", backend.calls[2])
        assertEquals("gradient PixelRect(left=96, top=0, right=104, bottom=100) ff606060 ff606060", backend.calls[backend.calls.size - 2])
        assertEquals("encode 90", backend.calls.last())
    }

    @Test
    fun `releases every image, including after a failure`() {
        val backend = RecordingBackend().apply { failDraw = true }
        assertThrows<IllegalStateException> { SlideComposer(backend).render(render, mapOf("a" to File("a.jpg")), ByteArrayOutputStream()) }
        assertEquals(2, backend.released.size)
        assertEquals("canvas", backend.released.last())
    }

    @Test
    fun `a missing source fails loudly`() {
        assertThrows<IllegalStateException> { SlideComposer(RecordingBackend()).render(render, emptyMap(), ByteArrayOutputStream()) }
    }
}
