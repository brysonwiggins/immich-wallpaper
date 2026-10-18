package com.wiggyserver.immichwallpaper.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwapCoordinatorTest {
    private var now = 100_000L
    private var cooldown = 2_000L
    private val queue = ArrayDeque<String>()
    private val offline = ArrayDeque<String>()
    private val applied = mutableListOf<String>()
    private val results = mutableListOf<SwapResult<String>>()
    private var refills = 0
    private var failNext = false
    private val pending = ArrayDeque<Runnable>()

    /** Swaps run on a manual "background thread" so tests can observe in-flight state. */
    private fun coordinator() = SwapCoordinator(
        queue = { queue.removeFirstOrNull() },
        offline = { offline.removeFirstOrNull() },
        applier = { slide ->
            if (failNext) { failNext = false; error("boom") }
            applied += slide
        },
        refill = { refills++ },
        clock = { now },
        cooldownMillis = { cooldown },
        executor = { pending.addLast(it) },
        onResult = { results += it },
    )

    private fun runPending() { while (pending.isNotEmpty()) pending.removeFirst().run() }

    @Test
    fun `applies the next queued slide and asks for a refill`() {
        queue += "q1"
        val c = coordinator()
        assertEquals(SwapDecision.Started, c.onScreenOff())
        runPending()
        assertEquals(listOf("q1"), applied)
        assertEquals(SwapResult.Applied("q1", SlideOrigin.QUEUE), results.single())
        assertEquals(1, refills)
    }

    @Test
    fun `falls back to the offline set when the queue is empty`() {
        offline += "o1"
        val c = coordinator()
        c.onScreenOff(); runPending()
        assertEquals(SwapResult.Applied("o1", SlideOrigin.OFFLINE), results.single())
        assertEquals(1, refills)
    }

    @Test
    fun `leaves the wallpaper alone when there is nothing to show`() {
        val c = coordinator()
        c.onScreenOff(); runPending()
        assertEquals(SwapResult.NothingToShow, results.single())
        assertTrue(applied.isEmpty())
        assertEquals(1, refills)
    }

    @Test
    fun `ignores a trigger while a swap is running`() {
        queue += listOf("q1", "q2")
        val c = coordinator()
        c.onScreenOff()
        now += 10_000
        assertEquals(SwapDecision.IgnoredBusy, c.onScreenOff())
        runPending()
        assertEquals(listOf("q1"), applied)
    }

    @Test
    fun `ignores a trigger inside the cooldown, measured from the previous start`() {
        queue += listOf("q1", "q2")
        val c = coordinator()
        c.onScreenOff(); runPending()
        now += 1_999
        assertEquals(SwapDecision.IgnoredCooldown, c.onScreenOff())
        now += 1
        assertEquals(SwapDecision.Started, c.onScreenOff())
        runPending()
        assertEquals(listOf("q1", "q2"), applied)
    }

    @Test
    fun `cooldown changes take effect immediately`() {
        queue += listOf("q1", "q2")
        val c = coordinator()
        c.onScreenOff(); runPending()
        cooldown = 0
        assertEquals(SwapDecision.Started, c.onScreenOff())
    }

    @Test
    fun `a failed apply is reported, frees the coordinator and still refills`() {
        queue += listOf("q1", "q2")
        failNext = true
        val c = coordinator()
        c.onScreenOff(); runPending()
        assertTrue(results.single() is SwapResult.Failed)
        assertEquals(1, refills)
        cooldown = 0
        assertEquals(SwapDecision.Started, c.onScreenOff())
        runPending()
        assertEquals(listOf("q2"), applied)
    }

    @Test
    fun `isBusy reflects an in-flight swap`() {
        queue += "q1"
        val c = coordinator()
        c.onScreenOff()
        assertTrue(c.isBusy)
        runPending()
        assertEquals(false, c.isBusy)
    }
}
