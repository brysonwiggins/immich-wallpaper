package com.wiggyserver.immichwallpaper.core

import java.util.concurrent.atomic.AtomicBoolean

enum class SlideOrigin { QUEUE, OFFLINE }

/** Immediate answer to a screen-off trigger. */
enum class SwapDecision { Started, IgnoredBusy, IgnoredCooldown }

/** Outcome of a started swap, reported after it finishes. */
sealed interface SwapResult<out T> {
    data class Applied<T>(val slide: T, val origin: SlideOrigin) : SwapResult<T>
    data object NothingToShow : SwapResult<Nothing>
    data class Failed<T>(val slide: T, val origin: SlideOrigin, val error: Throwable) : SwapResult<T>
}

/**
 * Handles screen-off: ignore while busy or within the cooldown (measured from the previous start),
 * otherwise run a swap on [executor]: queued slide → offline set → leave the wallpaper unchanged.
 * Always asks for a refill afterwards. Never touches the network.
 */
class SwapCoordinator<T : Any>(
    private val queue: () -> T?,
    private val offline: () -> T?,
    private val applier: (T) -> Unit,
    private val refill: () -> Unit,
    private val clock: () -> Long,
    private val cooldownMillis: () -> Long,
    private val executor: (Runnable) -> Unit,
    private val onResult: (SwapResult<T>) -> Unit = {},
) {
    private val busy = AtomicBoolean(false)
    private var lastStart: Long? = null

    val isBusy: Boolean get() = busy.get()

    @Synchronized
    fun onScreenOff(): SwapDecision {
        if (busy.get()) return SwapDecision.IgnoredBusy
        val now = clock()
        val last = lastStart
        if (last != null && now - last < cooldownMillis()) return SwapDecision.IgnoredCooldown
        busy.set(true)
        lastStart = now
        executor(Runnable { swap() })
        return SwapDecision.Started
    }

    private fun swap() {
        try {
            onResult(applyNext())
        } finally {
            busy.set(false)
            refill()
        }
    }

    private fun applyNext(): SwapResult<T> {
        val (slide, origin) = queue()?.let { it to SlideOrigin.QUEUE }
            ?: offline()?.let { it to SlideOrigin.OFFLINE }
            ?: return SwapResult.NothingToShow
        return try {
            applier(slide)
            SwapResult.Applied(slide, origin)
        } catch (e: Exception) {
            SwapResult.Failed(slide, origin, e)
        }
    }
}
