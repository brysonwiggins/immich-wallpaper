package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApplyPlannerTest {
    private val planner = ApplyPlanner()
    private val cover = Screens.FOLD8_COVER
    private val inner = Screens.FOLD8_INNER
    private val fold = listOf(cover, inner)
    private val samsungFold = Capabilities(perScreenFlags = true, cropsApi = true)

    @Test
    fun `lock + home with crops updates both screens with their screen bit`() {
        val jobs = planner.plan(ApplyTarget.LOCK_AND_HOME, samsungFold, fold, active = cover)
        assertEquals(listOf(1 or 2 or 16, 1 or 2 or 4), jobs.map { it.which })
        jobs.forEach { assertEquals(ApplyMethod.CROPS, it.method) }
        assertEquals(cover.viewports(), jobs[0].viewports)
        assertEquals(inner.viewports(), jobs[1].viewports)
    }

    @Test
    fun `home only uses crops with the system flag`() {
        val jobs = planner.plan(ApplyTarget.HOME, samsungFold, fold, active = inner)
        assertEquals(listOf(1 or 16, 1 or 4), jobs.map { it.which })
        jobs.forEach { assertEquals(ApplyMethod.CROPS, it.method) }
    }

    @Test
    fun `lock only always uses plain images because One UI ignores lock-only crops`() {
        val jobs = planner.plan(ApplyTarget.LOCK, samsungFold, fold, active = inner)
        assertEquals(listOf(2 or 16, 2 or 4), jobs.map { it.which })
        jobs.forEach { assertEquals(ApplyMethod.PLAIN, it.method) }
        assertEquals(listOf(Viewport(cover, Size(1248, 1972))), jobs[0].viewports)
        assertEquals(listOf(Viewport(inner, Size(2448, 2448))), jobs[1].viewports)
    }

    @Test
    fun `without the crops API every target falls back to plain images`() {
        val jobs = planner.plan(ApplyTarget.LOCK_AND_HOME, samsungFold.copy(cropsApi = false), fold, active = cover)
        jobs.forEach { assertEquals(ApplyMethod.PLAIN, it.method) }
        assertEquals(listOf(3 or 16, 3 or 4), jobs.map { it.which })
    }

    @Test
    fun `without per-screen flags only the active screen is updated, with plain flags`() {
        val jobs = planner.plan(ApplyTarget.LOCK, Capabilities(perScreenFlags = false, cropsApi = false), fold, active = inner)
        assertEquals(1, jobs.size)
        assertEquals(inner, jobs[0].screen)
        assertEquals(2, jobs[0].which)
    }

    @Test
    fun `a normal phone gets one job without a screen bit`() {
        val phone = Screen(ScreenKind.PHONE, Size(1080, 2400), smallestWidthDp = 411, rotates = true)
        val jobs = planner.plan(ApplyTarget.LOCK_AND_HOME, Capabilities(perScreenFlags = false, cropsApi = true), listOf(phone), active = phone)
        assertEquals(listOf(3), jobs.map { it.which })
        assertEquals(ApplyMethod.CROPS, jobs[0].method)
    }

    @Test
    fun `screen bits only apply to cover and inner screens`() {
        val tablet = Screen(ScreenKind.TABLET, Size(2560, 1600), smallestWidthDp = 800, rotates = true)
        val jobs = planner.plan(ApplyTarget.LOCK, Capabilities(perScreenFlags = true, cropsApi = false), listOf(tablet), active = tablet)
        assertEquals(listOf(2), jobs.map { it.which })
    }
}
