package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SettingsTest {
    private val fold = listOf(Screens.FOLD8_COVER, Screens.FOLD8_INNER)
    private val phone = listOf(Screen(ScreenKind.PHONE, Size(1080, 2400), smallestWidthDp = 411, rotates = true))

    @Test
    fun `defaults follow the owner decisions`() {
        val s = Settings()
        assertEquals(ApplyTarget.LOCK_AND_HOME, s.applyTarget)
        assertFalse(s.sideBySide)
        assertEquals(2_000L, s.cooldownMillis)
        assertTrue(s.faceAwareCrop)
        assertFalse(s.wifiOnlyRefill)
    }

    @Test
    fun `the side-by-side option is offered only when a screen supports it`() {
        assertTrue(Settings.sideBySideAvailable(fold))
        assertFalse(Settings.sideBySideAvailable(phone))
    }

    @Test
    fun `side-by-side is effective only when enabled and supported`() {
        assertTrue(Settings(sideBySide = true).effectiveSideBySide(fold))
        assertFalse(Settings(sideBySide = true).effectiveSideBySide(phone))
        assertFalse(Settings(sideBySide = false).effectiveSideBySide(fold))
    }

    @Test
    fun `cooldown cannot be negative`() {
        assertThrows<IllegalArgumentException> { Settings(cooldownMillis = -5) }
    }
}
