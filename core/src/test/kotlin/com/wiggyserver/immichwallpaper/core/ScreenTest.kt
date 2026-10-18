package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenTest {
    @Test
    fun `orientation buckets match AOSP getOrientation on the Fold 8`() {
        assertEquals(ScreenOrientation.PORTRAIT, ScreenOrientation.of(Size(1248, 1972)))
        assertEquals(ScreenOrientation.LANDSCAPE, ScreenOrientation.of(Size(1972, 1248)))
        assertEquals(ScreenOrientation.SQUARE_PORTRAIT, ScreenOrientation.of(Size(1848, 2448)))
        assertEquals(ScreenOrientation.SQUARE_LANDSCAPE, ScreenOrientation.of(Size(2448, 1848)))
    }

    @Test
    fun `orientation bucket boundaries`() {
        assertEquals(ScreenOrientation.LANDSCAPE, ScreenOrientation.of(Size(400, 300)))
        assertEquals(ScreenOrientation.SQUARE_PORTRAIT, ScreenOrientation.of(Size(1000, 1000)))
        assertEquals(ScreenOrientation.PORTRAIT, ScreenOrientation.of(Size(300, 400)))
    }

    @Test
    fun `orientation keys are the AOSP integer values`() {
        assertEquals(listOf(0, 1, 2, 3), ScreenOrientation.entries.map { it.key })
    }

    @Test
    fun `a rotating screen has a portrait and a landscape viewport`() {
        val v = Screens.FOLD8_INNER.viewports()
        assertEquals(listOf(Size(1848, 2448), Size(2448, 1848)), v.map { it.size })
        assertEquals(listOf(ScreenOrientation.SQUARE_PORTRAIT, ScreenOrientation.SQUARE_LANDSCAPE), v.map { it.orientation })
    }

    @Test
    fun `a non-rotating screen has only its natural viewport`() {
        assertEquals(listOf(Size(1248, 1972)), Screens.FOLD8_COVER.viewports().map { it.size })
    }

    @Test
    fun `the plain-image size for a rotating screen covers both orientations when center-cropped`() {
        assertEquals(Size(2448, 2448), Screens.FOLD8_INNER.plainImageSize())
        assertEquals(Size(1248, 1972), Screens.FOLD8_COVER.plainImageSize())
    }

    @Test
    fun `the Fold 8 inner screen qualifies for side-by-side portraits`() =
        assertTrue(Screens.FOLD8_INNER.supportsSideBySide())

    @Test
    fun `cover screens and phones do not`() {
        assertFalse(Screens.FOLD8_COVER.supportsSideBySide())
        assertFalse(Screen(ScreenKind.PHONE, Size(1080, 2400), smallestWidthDp = 411, rotates = true).supportsSideBySide())
    }

    @Test
    fun `a wide tablet whose halves are too wide does not qualify`() {
        // 2560x1600: each half is 1280x1600 = 0.8 -> qualifies; 3000x1200: half 1500x1200 = 1.25 -> landscape halves, no.
        assertTrue(Screen(ScreenKind.TABLET, Size(2560, 1600), smallestWidthDp = 800, rotates = true).supportsSideBySide())
        assertFalse(Screen(ScreenKind.TABLET, Size(3000, 1200), smallestWidthDp = 600, rotates = true).supportsSideBySide())
    }
}
