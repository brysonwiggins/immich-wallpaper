package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class FallbackRotatorTest {
    private class MemoryIndex(var value: Int = 0) : IndexStore {
        override fun get() = value
        override fun set(index: Int) { value = index }
    }

    private val store = MemoryIndex()
    private val all = setOf("a", "b", "c")

    private fun rotator(items: List<String>, available: Set<String> = all) =
        FallbackRotator(items, store) { it in available }

    @Test
    fun `rotates in order and wraps around`() {
        val r = rotator(listOf("a", "b", "c"))
        assertEquals(listOf("a", "b", "c", "a"), List(4) { r.next() })
    }

    @Test
    fun `persists the position so rotation survives a restart`() {
        rotator(listOf("a", "b", "c")).next()
        assertEquals("b", rotator(listOf("a", "b", "c")).next())
    }

    @Test
    fun `skips items whose files are missing`() {
        val r = rotator(listOf("a", "b", "c"), available = setOf("a", "c"))
        assertEquals(listOf("a", "c", "a"), List(3) { r.next() })
    }

    @Test
    fun `returns null when nothing is available`() {
        assertNull(rotator(listOf("a", "b"), available = emptySet()).next())
        assertNull(rotator(emptyList()).next())
    }

    @Test
    fun `a stored index past the end (set shrank) starts over`() {
        store.value = 7
        assertEquals("a", rotator(listOf("a", "b")).next())
    }

    @Test
    fun `a negative stored index starts over`() {
        store.value = -3
        assertEquals("a", rotator(listOf("a", "b")).next())
    }
}
