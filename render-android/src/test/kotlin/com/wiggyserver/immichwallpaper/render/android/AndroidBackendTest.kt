package com.wiggyserver.immichwallpaper.render.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import com.wiggyserver.immichwallpaper.core.ApplyPlanner
import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.LayoutPlanner
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.RenderPlanner
import com.wiggyserver.immichwallpaper.core.Screen
import com.wiggyserver.immichwallpaper.core.ScreenKind
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.render.SlideComposer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDateTime
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class AndroidBackendTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val backend = AndroidBackend()

    private fun solidJpeg(name: String, size: Size, color: Int): File {
        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        return tmp.newFile(name).also { f -> f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 95, it) } }
    }

    private fun near(expected: Int, actual: Int, tolerance: Int = 12) =
        abs(Color.red(expected) - Color.red(actual)) <= tolerance &&
            abs(Color.green(expected) - Color.green(actual)) <= tolerance &&
            abs(Color.blue(expected) - Color.blue(actual)) <= tolerance

    @Test
    fun `reads the image size from the header`() {
        assertEquals(Size(300, 400), backend.imageSize(solidJpeg("a.jpg", Size(300, 400), Color.RED)))
    }

    @Test
    fun `decodes only the region, subsampled`() {
        val file = solidJpeg("a.jpg", Size(400, 300), Color.RED)
        val bmp = backend.decodeRegion(file, PixelRect(0, 0, 200, 300), 2)
        assertEquals(100, bmp.width)
        assertEquals(150, bmp.height)
        backend.release(bmp)
        assertTrue(bmp.isRecycled)
    }

    @Test
    fun `renders a side-by-side pair with a seam on a 1-8 scale Fold 8 inner screen`() {
        val inner = Screen(ScreenKind.INNER, Size(306, 231), smallestWidthDp = 704, rotates = true)
        val cover = Screen(ScreenKind.COVER, Size(156, 246), smallestWidthDp = 475, rotates = false)
        val a = Asset("a", Size(3000, 4000), LocalDateTime.of(2024, 5, 1, 10, 0))
        val b = Asset("b", Size(3000, 4000), LocalDateTime.of(2024, 5, 1, 11, 0))
        val sources = mapOf("a" to solidJpeg("a.jpg", Size(300, 400), Color.RED), "b" to solidJpeg("b.jpg", Size(300, 400), Color.BLUE))
        val jobs = ApplyPlanner().plan(ApplyTarget.LOCK_AND_HOME, Capabilities(true, true), listOf(cover, inner), cover)
        val plan = LayoutPlanner().plan(listOf(a, b), jobs.flatMap { it.viewports }, sideBySide = true)!!
        val render = RenderPlanner(seamWidthPx = 8).plan(plan, jobs)[1]

        val out = tmp.newFile("out.jpg")
        out.outputStream().use { SlideComposer(backend).render(render, sources, it) }
        val result = BitmapFactory.decodeFile(out.path)
        assertEquals(render.canvas, Size(result.width, result.height))

        val zone = PixelRect(231, 0, 231 + 306, 231) // landscape zone of the crops composite
        val mid = zone.left + zone.width / 2
        assertTrue("left photo", near(Color.RED, result.getPixel(zone.left + 40, 100)))
        assertTrue("right photo", near(Color.BLUE, result.getPixel(zone.right - 40, 100)))
        val seam = result.getPixel(mid, 100)
        assertTrue("seam is darker than both photos: ${Integer.toHexString(seam)}", Color.red(seam) < 220 && Color.blue(seam) < 220)
        assertTrue("portrait zone shows the single photo", near(Color.RED, result.getPixel(100, 150)))
        assertTrue("outside every zone stays black", near(Color.BLACK, result.getPixel(300, 280)))
    }
}
