package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class RenderPlannerTest {
    private val renderer = RenderPlanner(seamWidthPx = 16)
    private val cover = Screens.FOLD8_COVER
    private val inner = Screens.FOLD8_INNER
    private val fold = listOf(cover, inner)
    private val p1 = portrait("p1", "2024-05-01T10:00")
    private val p2 = portrait("p2", "2024-05-01T11:00")
    private val cropsJobs = ApplyPlanner().plan(ApplyTarget.LOCK_AND_HOME, Capabilities(true, true), fold, cover)
    private val plainJobs = ApplyPlanner().plan(ApplyTarget.LOCK, Capabilities(true, true), fold, cover)

    private fun plan(jobs: List<ScreenJob>, sideBySide: Boolean) =
        LayoutPlanner().plan(listOf(p1, p2), jobs.flatMap { it.viewports }, sideBySide)!!

    private fun assertAspect(crop: PixelRect, dest: PixelRect) =
        assertTrue(abs(crop.width.toDouble() / crop.height - dest.width.toDouble() / dest.height) < 0.003, "$crop vs $dest")

    @Test
    fun `crops method renders one composite per screen with crop hints`() {
        val renders = renderer.plan(plan(cropsJobs, sideBySide = false), cropsJobs)
        assertEquals(2, renders.size)
        val innerRender = renders[1]
        assertEquals(Size(1848 + 2448, 2448), innerRender.canvas)
        assertEquals(setOf(2, 3), innerRender.cropHints!!.keys)
        assertEquals(2, innerRender.regions.size)
        innerRender.regions.forEach { assertEquals(p1, it.asset); assertAspect(it.crop, it.dest) }
        assertTrue(innerRender.seams.isEmpty())
    }

    @Test
    fun `a pair splits the zone at the hinge with a seam between the photos`() {
        val renders = renderer.plan(plan(cropsJobs, sideBySide = true), cropsJobs)
        val landscapeZone = PixelRect(1848, 0, 4296, 1848)
        val regions = renders[1].regions.filter { it.dest.left >= landscapeZone.left }
        assertEquals(listOf(p1, p2), regions.map { it.asset })
        assertEquals(PixelRect(1848, 0, 1848 + 1216, 1848), regions[0].dest)
        assertEquals(PixelRect(1848 + 1232, 0, 4296, 1848), regions[1].dest)
        assertEquals(listOf(PixelRect(1848 + 1216, 0, 1848 + 1232, 1848)), renders[1].seams)
        regions.forEach { assertAspect(it.crop, it.dest) }
    }

    @Test
    fun `plain method renders the plain image size without crop hints`() {
        val renders = renderer.plan(plan(plainJobs, sideBySide = false), plainJobs)
        assertEquals(Size(1248, 1972), renders[0].canvas)
        assertEquals(Size(2448, 2448), renders[1].canvas)
        renders.forEach { assertNull(it.cropHints) }
    }

    @Test
    fun `plain square on a rotating screen keeps faces clear of the clock in both orientations`() {
        val zone = renderer.effectiveSafeZone(inner, Size(2448, 2448))
        // Landscape shows the middle 1848 rows (offset 300); its clock zone ends at 300 + 0.33 * 1848 = 910.
        assertEquals(910.0 / 2448, zone.top, 0.002)
        assertEquals((300 + 0.18 * 1848) / 2448, zone.bottom, 0.002)
    }

    @Test
    fun `a non-rotating screen keeps its own safe zone`() =
        assertEquals(cover.safeZone, renderer.effectiveSafeZone(cover, Size(1248, 1972)))

    @Test
    fun `face-aware cropping can be turned off`() {
        val face = Face(Box(0.0, 0.0, 0.1, 0.1))
        val withFace = portrait("f", faces = listOf(face))
        val slide = LayoutPlanner().plan(listOf(withFace), cover.viewports(), sideBySide = false)!!
        val job = ScreenJob(cover, 2, ApplyMethod.PLAIN, cover.viewports())
        val aware = renderer.plan(slide, listOf(job))[0].regions[0].crop
        val center = RenderPlanner(faceAware = false).plan(slide, listOf(job))[0].regions[0].crop
        assertEquals(CropPlanner().crop(withFace.size, Size(1248, 1972)), center)
        assertTrue(aware != center)
    }
}
