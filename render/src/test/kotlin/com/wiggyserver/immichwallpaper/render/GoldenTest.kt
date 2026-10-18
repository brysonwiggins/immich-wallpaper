package com.wiggyserver.immichwallpaper.render

import com.wiggyserver.immichwallpaper.core.ApplyPlanner
import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Box
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.Face
import com.wiggyserver.immichwallpaper.core.LayoutPlanner
import com.wiggyserver.immichwallpaper.core.RenderPlanner
import com.wiggyserver.immichwallpaper.core.SafeZone
import com.wiggyserver.immichwallpaper.core.Screen
import com.wiggyserver.immichwallpaper.core.ScreenKind
import com.wiggyserver.immichwallpaper.core.ScreenRender
import com.wiggyserver.immichwallpaper.core.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.BasicStroke
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.time.LocalDateTime
import javax.imageio.ImageIO
import kotlin.math.abs

/**
 * Golden-image tests for the composer. Screens are the Fold 8 at 1/8 scale so
 * the goldens stay small. Update with: ./gradlew :render:test -Pgolden.update=true
 */
class GoldenTest {
    @TempDir
    lateinit var dir: File

    private val composer = SlideComposer(AwtBackend())
    private val cover = Screen(ScreenKind.COVER, Size(156, 246), smallestWidthDp = 475, rotates = false, safeZone = SafeZone(0.28, 0.18))
    private val inner = Screen(ScreenKind.INNER, Size(306, 231), smallestWidthDp = 704, rotates = true, safeZone = SafeZone(0.33, 0.18))
    private val caps = Capabilities(perScreenFlags = true, cropsApi = true)

    /** Original 3000x4000 asset whose downloaded "preview" is only 300x400, like Immich. */
    private fun photo(id: String, hue: Float, original: Size = Size(3000, 4000), face: Box? = null): Pair<Asset, File> {
        val preview = Size(original.width / 10, original.height / 10)
        val img = BufferedImage(preview.width, preview.height, BufferedImage.TYPE_INT_RGB)
        val g = img.createGraphics()
        val w = preview.width
        val h = preview.height
        listOf(0f, 0.08f, 0.16f, 0.24f).forEachIndexed { i, dh ->
            g.color = Color.getHSBColor(hue + dh, 0.7f, if (i % 2 == 0) 0.85f else 0.55f)
            g.fillRect((i % 2) * w / 2, (i / 2) * h / 2, w / 2, h / 2)
        }
        g.color = Color.BLACK
        g.stroke = BasicStroke(3f)
        g.drawLine(w / 2, 0, w / 2, h)
        g.drawLine(0, h / 2, w, h / 2)
        g.drawRect(1, 1, w - 3, h - 3)
        face?.let {
            g.color = Color.WHITE
            g.fillRect((it.left * w).toInt(), (it.top * h).toInt(), (it.width * w).toInt(), (it.height * h).toInt())
        }
        g.dispose()
        val file = File(dir, "$id.png").also { ImageIO.write(img, "png", it) }
        val asset = Asset(id, original, LocalDateTime.of(2024, 5, 1, 10, 0), faces = listOfNotNull(face?.let { Face(it) }))
        return asset to file
    }

    private fun renders(target: ApplyTarget, photos: List<Pair<Asset, File>>, sideBySide: Boolean): List<ScreenRender> {
        val jobs = ApplyPlanner().plan(target, caps, listOf(cover, inner), cover)
        val plan = LayoutPlanner().plan(photos.map { it.first }, jobs.flatMap { it.viewports }, sideBySide)!!
        return RenderPlanner(seamWidthPx = 4).plan(plan, jobs)
    }

    private fun check(name: String, render: ScreenRender, photos: List<Pair<Asset, File>>) {
        val out = File(dir, "$name.jpg")
        out.outputStream().use { composer.render(render, photos.associate { it.first.id to it.second }, it) }
        val actual = ImageIO.read(out)
        assertEquals(render.canvas, Size(actual.width, actual.height))

        val golden = File(System.getProperty("golden.dir"), "$name.png")
        if (System.getProperty("golden.update") == "true" || !golden.exists()) {
            ImageIO.write(actual, "png", golden)
            if (System.getProperty("golden.update") != "true") error("golden $name was missing and has been written; review and re-run")
            return
        }
        assertSimilar(ImageIO.read(golden), actual, name)
    }

    /** Mean channel error catches color shifts; 8x8 block means catch layout shifts JPEG noise cannot explain. */
    private fun assertSimilar(expected: BufferedImage, actual: BufferedImage, name: String) {
        assertEquals(expected.width, actual.width)
        assertEquals(expected.height, actual.height)
        var total = 0L
        var worstBlock = 0.0
        for (by in 0 until expected.height step 8) for (bx in 0 until expected.width step 8) {
            var blockSum = 0L
            var n = 0
            for (y in by until minOf(by + 8, expected.height)) for (x in bx until minOf(bx + 8, expected.width)) {
                val a = expected.getRGB(x, y)
                val b = actual.getRGB(x, y)
                val d = (0..2).sumOf { abs(((a shr (it * 8)) and 0xFF) - ((b shr (it * 8)) and 0xFF)) }
                blockSum += d
                n++
            }
            total += blockSum
            worstBlock = maxOf(worstBlock, blockSum / (3.0 * n))
        }
        val mean = total / (3.0 * expected.width * expected.height)
        assertTrue(mean < 3.0 && worstBlock < 24.0, "$name differs from golden: mean $mean, worst 8x8 block $worstBlock")
    }

    @Test
    fun `cover single portrait`() {
        val p = listOf(photo("a", 0.0f))
        check("cover-single", renders(ApplyTarget.LOCK_AND_HOME, p, false)[0], p)
    }

    @Test
    fun `inner crops composite, single photo in both orientations`() {
        val p = listOf(photo("a", 0.0f))
        val r = renders(ApplyTarget.LOCK_AND_HOME, p, false)[1]
        assertEquals(Size(231 + 306, 306), r.canvas)
        check("inner-crops-single", r, p)
    }

    @Test
    fun `inner landscape pair with a seam at the hinge`() {
        val p = listOf(photo("a", 0.0f), photo("b", 0.55f))
        val r = renders(ApplyTarget.LOCK_AND_HOME, p, true)[1]
        assertEquals(1, r.seams.size)
        check("inner-crops-pair", r, p)
    }

    @Test
    fun `lock-only plain square with a pair`() {
        val p = listOf(photo("a", 0.0f), photo("b", 0.55f))
        val r = renders(ApplyTarget.LOCK, p, true)[1]
        assertEquals(Size(306, 306), r.canvas)
        check("inner-plain-pair", r, p)
    }

    @Test
    fun `face-aware crop keeps a face near the edge of a landscape photo`() {
        val p = listOf(photo("f", 0.3f, original = Size(4000, 3000), face = Box(0.04, 0.45, 0.16, 0.6)))
        check("cover-face-left", renders(ApplyTarget.LOCK_AND_HOME, p, false)[0], p)
    }
}
