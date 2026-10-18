package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SlideQueueTest {
    private val queue = SlideQueue<String>(target = 3)

    @Test
    fun `starts empty and asks for a full refill`() {
        assertEquals(0, queue.size)
        assertEquals(3, queue.deficit)
        assertNull(queue.poll())
        assertNull(queue.peek())
    }

    @Test
    fun `is first in, first out`() {
        queue.offer("a"); queue.offer("b")
        assertEquals("a", queue.peek())
        assertEquals("a", queue.poll())
        assertEquals("b", queue.poll())
    }

    @Test
    fun `deficit shrinks as slides arrive and grows as they are used`() {
        queue.offer("a"); queue.offer("b")
        assertEquals(1, queue.deficit)
        queue.poll()
        assertEquals(2, queue.deficit)
    }

    @Test
    fun `refuses slides beyond the target`() {
        assertTrue(queue.offer("a")); assertTrue(queue.offer("b")); assertTrue(queue.offer("c"))
        assertFalse(queue.offer("d"))
        assertTrue(queue.isFull)
        assertEquals(0, queue.deficit)
    }

    @Test
    fun `clear empties the queue and returns what was dropped`() {
        queue.offer("a"); queue.offer("b")
        assertEquals(listOf("a", "b"), queue.clear())
        assertEquals(0, queue.size)
    }

    @Test
    fun `snapshot lists the queued slides in order`() {
        queue.offer("a"); queue.offer("b")
        assertEquals(listOf("a", "b"), queue.snapshot())
    }

    @Test
    fun `removeIf drops matching slides and keeps the order of the rest`() {
        val q = SlideQueue<String>(4)
        listOf("a", "bx", "c", "dx").forEach { q.offer(it) }
        assertEquals(listOf("bx", "dx"), q.removeIf { it.endsWith("x") })
        assertEquals(listOf("a", "c"), q.snapshot())
        assertEquals(2, q.deficit)
    }
}
