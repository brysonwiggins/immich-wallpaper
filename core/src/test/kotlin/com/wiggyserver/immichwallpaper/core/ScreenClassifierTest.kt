package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ScreenClassifierTest {
    private val cover = ObservedDisplay(Size(1248, 1972), 420)
    private val inner = ObservedDisplay(Size(2448, 1848), 420)

    @Test
    fun `a foldable that has shown both screens reproduces the measured Fold 8 profiles`() {
        val screens = ScreenClassifier.screens(setOf(cover, inner), foldable = true)
        assertEquals(listOf(Screens.FOLD8_COVER, Screens.FOLD8_INNER), screens)
    }

    @Test
    fun `a foldable seen only folded knows just its cover`() {
        assertEquals(listOf(ScreenKind.COVER), ScreenClassifier.screens(setOf(cover), foldable = true).map { it.kind })
    }

    @Test
    fun `a foldable seen only unfolded knows just its inner screen`() {
        assertEquals(listOf(Screens.FOLD8_INNER), ScreenClassifier.screens(setOf(inner), foldable = true))
    }

    @Test
    fun `a regular phone is a non-rotating phone screen`() {
        val s = ScreenClassifier.screens(setOf(ObservedDisplay(Size(1080, 2400), 420)), foldable = false).single()
        assertEquals(ScreenKind.PHONE, s.kind)
        assertEquals(false, s.rotates)
        assertEquals(411, s.smallestWidthDp)
        assertEquals(SafeZone(0.28, 0.18), s.safeZone)
    }

    @Test
    fun `a large non-foldable is a rotating tablet`() {
        val s = ScreenClassifier.screens(setOf(ObservedDisplay(Size(2560, 1600), 320)), foldable = false).single()
        assertEquals(ScreenKind.TABLET, s.kind)
        assertEquals(true, s.rotates)
        assertEquals(800, s.smallestWidthDp)
    }

    @Test
    fun `a rotated report of the same panel is not a new screen`() {
        val rotated = ObservedDisplay(Size(1972, 1248), 420)
        assertEquals(1, ScreenClassifier.screens(setOf(cover, rotated), foldable = true).size)
    }

    @Test
    fun `nothing observed means no screens`() {
        assertEquals(emptyList<Screen>(), ScreenClassifier.screens(emptySet(), foldable = true))
    }

    @Test
    fun `the active screen is the one matching the current display, in either orientation`() {
        val screens = ScreenClassifier.screens(setOf(cover, inner), foldable = true)
        assertEquals(Screens.FOLD8_INNER, ScreenClassifier.active(screens, ObservedDisplay(Size(1848, 2448), 420), foldable = true))
        assertEquals(Screens.FOLD8_COVER, ScreenClassifier.active(screens, cover, foldable = true))
    }

    @Test
    fun `an unknown current display is classified on its own`() {
        val screens = ScreenClassifier.screens(setOf(cover), foldable = true)
        assertEquals(Screens.FOLD8_INNER, ScreenClassifier.active(screens, inner, foldable = true))
    }
}
