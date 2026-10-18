package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LayoutPlannerTest {
    private val planner = LayoutPlanner()
    private val inner = Screens.FOLD8_INNER
    private val cover = Screens.FOLD8_COVER
    private val innerPortrait = inner.viewports()[0]
    private val innerLandscape = inner.viewports()[1]
    private val coverPortrait = cover.viewports()[0]
    private val allViewports = cover.viewports() + inner.viewports()

    private val p1 = portrait("p1", "2024-05-01T10:00")
    private val p2 = portrait("p2", "2024-05-01T11:00")
    private val l1 = landscape("l1")

    @Test
    fun `nothing to plan without candidates`() = assertNull(planner.plan(emptyList(), allViewports, sideBySide = true))

    @Test
    fun `with side-by-side off every viewport shows the first asset alone`() {
        val plan = planner.plan(listOf(p1, p2), allViewports, sideBySide = false)!!
        assertEquals(listOf(p1), plan.consumed)
        allViewports.forEach { assertEquals(Layout.Single(p1), plan.layouts[it]) }
    }

    @Test
    fun `with side-by-side on, inner landscape pairs two portraits and every other viewport stays single`() {
        val plan = planner.plan(listOf(p1, p2), allViewports, sideBySide = true)!!
        assertEquals(Layout.Pair(p1, p2), plan.layouts[innerLandscape])
        assertEquals(Layout.Single(p1), plan.layouts[innerPortrait])
        assertEquals(Layout.Single(p1), plan.layouts[coverPortrait])
        assertEquals(listOf(p1, p2), plan.consumed)
    }

    @Test
    fun `a landscape first asset is never paired, and the portraits wait for the next slide`() {
        val plan = planner.plan(listOf(l1, p1, p2), allViewports, sideBySide = true)!!
        assertEquals(listOf(l1), plan.consumed)
        allViewports.forEach { assertEquals(Layout.Single(l1), plan.layouts[it]) }
    }

    @Test
    fun `a portrait without a portrait partner is shown alone`() {
        val plan = planner.plan(listOf(p1, l1), allViewports, sideBySide = true)!!
        assertEquals(Layout.Single(p1), plan.layouts[innerLandscape])
        assertEquals(listOf(p1), plan.consumed)
    }

    @Test
    fun `screens that do not support side-by-side never pair even when the setting is on`() {
        val phone = Screen(ScreenKind.PHONE, Size(1080, 2400), smallestWidthDp = 411, rotates = true)
        val plan = planner.plan(listOf(p1, p2), phone.viewports(), sideBySide = true)!!
        plan.layouts.values.forEach { assertEquals(Layout.Single(p1), it) }
        assertEquals(listOf(p1), plan.consumed)
    }

    @Test
    fun `the partner is the best match, not just the next asset`() {
        val far = portrait("far", "2010-01-01T00:00")
        val plan = planner.plan(listOf(p1, far, p2), allViewports, sideBySide = true)!!
        assertEquals(Layout.Pair(p1, p2), plan.layouts[innerLandscape])
    }

    @Test
    fun `the plain square viewport of a rotating screen counts as landscape-capable for pairing`() {
        val square = Viewport(inner, inner.plainImageSize())
        val plan = planner.plan(listOf(p1, p2), listOf(square), sideBySide = true)!!
        assertEquals(Layout.Pair(p1, p2), plan.layouts[square])
    }
}
