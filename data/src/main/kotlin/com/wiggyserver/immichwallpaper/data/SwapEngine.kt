package com.wiggyserver.immichwallpaper.data

import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.SlideOrigin
import com.wiggyserver.immichwallpaper.core.SwapCoordinator
import com.wiggyserver.immichwallpaper.core.SwapDecision
import com.wiggyserver.immichwallpaper.core.SwapResult

/**
 * The screen-off path: [SwapCoordinator] over the queue (then the offline set), applying with
 * [SlideApplier]. Queue slides are deleted once used; offline slides are kept for the next rotation.
 */
class SwapEngine(
    private val queue: QueueRepository,
    offline: OfflineSetRepository,
    private val applier: SlideApplier,
    private val offlineApplier: SlideApplier,
    private val activeScreenBit: () -> Int,
    refill: () -> Unit,
    clock: () -> Long,
    cooldownMillis: () -> Long,
    executor: (Runnable) -> Unit,
    private val onApplyError: (ApplyException) -> Unit = {},
    private val onResult: (SwapResult<PreparedSlide>) -> Unit = {},
) {
    private val offlineIds = mutableSetOf<String>()

    private val coordinator = SwapCoordinator(
        queue = { queue.take() },
        offline = { offline.next()?.also { offlineIds += it.id } },
        applier = { slide ->
            val a = if (slide.id in offlineIds) offlineApplier else applier
            a.apply(slide, activeScreenBit())
        },
        refill = refill,
        clock = clock,
        cooldownMillis = cooldownMillis,
        executor = executor,
        onResult = ::handle,
    )

    val isBusy: Boolean get() = coordinator.isBusy

    fun onScreenOff(): SwapDecision = coordinator.onScreenOff()

    private fun handle(result: SwapResult<PreparedSlide>) {
        when (result) {
            is SwapResult.Applied -> if (result.origin == SlideOrigin.QUEUE) queue.discard(result.slide)
            is SwapResult.Failed -> {
                if (result.origin == SlideOrigin.QUEUE) queue.discard(result.slide)
                (result.error as? ApplyException)?.let(onApplyError)
            }
            SwapResult.NothingToShow -> Unit
        }
        offlineIds.clear()
        onResult(result)
    }
}
