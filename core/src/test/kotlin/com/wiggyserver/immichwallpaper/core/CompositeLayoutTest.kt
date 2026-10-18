package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class CompositeLayoutTest {
    @Test
    fun `places each viewport's zone left to right at full resolution`() {
        val viewports = Screens.FOLD8_INNER.viewports()
        val c = CompositeLayout.of(viewports)
        assertEquals(Size(1848 + 2448, 2448), c.canvas)
        assertEquals(PixelRect(0, 0, 1848, 2448), c.zones[viewports[0]])
        assertEquals(PixelRect(1848, 0, 4296, 1848), c.zones[viewports[1]])
    }

    @Test
    fun `crop hints are keyed by AOSP orientation`() {
        val c = CompositeLayout.of(Screens.FOLD8_INNER.viewports())
        assertEquals(setOf(2, 3), c.cropHints.keys)
        assertEquals(PixelRect(1848, 0, 4296, 1848), c.cropHints[3])
    }

    @Test
    fun `a single viewport is just its own size`() {
        val c = CompositeLayout.of(Screens.FOLD8_COVER.viewports())
        assertEquals(Size(1248, 1972), c.canvas)
        assertEquals(mapOf(0 to PixelRect(0, 0, 1248, 1972)), c.cropHints)
    }

    @Test
    fun `needs at least one viewport`() {
        assertThrows<IllegalArgumentException> { CompositeLayout.of(emptyList()) }
    }
}
