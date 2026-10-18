package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.data.FailReason
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Things that stop the wallpaper changing or make it fall back, most important first. */
enum class Problem { NOT_CONFIGURED, KEY_REJECTED, LOCAL_NETWORK, SERVICE_OFF, BATTERY_OPTIMIZED, UNREACHABLE, NO_PHOTOS, STORAGE_FULL, NO_OFFLINE_SET }

fun problems(s: AppSnapshot): List<Problem> {
    if (!s.configured) return listOf(Problem.NOT_CONFIGURED)
    return buildList {
        if (s.lastRefillFailure == FailReason.AUTH) add(Problem.KEY_REJECTED)
        // An unreachable server while Android 17 blocks the home network is most likely that block.
        val localBlocked = s.lastRefillFailure == FailReason.UNREACHABLE && !s.localNetworkAllowed
        if (localBlocked) add(Problem.LOCAL_NETWORK)
        if (!s.serviceOn) add(Problem.SERVICE_OFF)
        if (!s.batteryExempt) add(Problem.BATTERY_OPTIMIZED)
        when (s.lastRefillFailure) {
            FailReason.UNREACHABLE -> if (!localBlocked) add(Problem.UNREACHABLE)
            FailReason.NO_PHOTOS -> add(Problem.NO_PHOTOS)
            FailReason.STORAGE_FULL -> add(Problem.STORAGE_FULL)
            else -> Unit
        }
        if (s.offlineReady == 0) add(Problem.NO_OFFLINE_SET)
    }
}

/** Shown under "Up next" when the selection can't fill the queue; null when it can. */
fun upNextNote(s: AppSnapshot): String? {
    val size = s.poolSize
    return when {
        size == 0 || s.lastRefillFailure == FailReason.NO_PHOTOS -> "No photos in your sources"
        size == 1 -> "Your sources have only 1 photo"
        size != null -> "Your sources have only $size photos, so they take turns"
        else -> null
    }
}

/** Hiding the last photo of the selection would leave nothing to show. */
fun canHideNowShowing(s: AppSnapshot): Boolean = s.poolSize.let { it == null || it > 1 }

/** What the screens show after turning the app off. Android doesn't let apps read the previous wallpaper. */
enum class AfterTurnOff { KEEP_PHOTO, PHONE_DEFAULT, CHOOSE }

data class HomeState(val snapshot: AppSnapshot, val problems: List<Problem>)

class HomeModel(private val app: AppPort, private val scope: CoroutineScope, private val io: CoroutineDispatcher) {
    private val _state = MutableStateFlow<HomeState?>(null)
    val state: StateFlow<HomeState?> = _state.asStateFlow()

    /** The screen calls this about once a second while visible. */
    fun refresh() {
        scope.launch {
            val snap = withContext(io) { app.snapshot() }
            _state.value = HomeState(snap, problems(snap))
        }
    }

    fun swapNow() = app.swapNow()

    /** [openPicker] opens the system wallpaper picker, for [AfterTurnOff.CHOOSE], once the app has stopped. */
    fun turnOff(after: AfterTurnOff, openPicker: () -> Unit) {
        scope.launch {
            withContext(io) { app.turnOff(resetWallpaper = after == AfterTurnOff.PHONE_DEFAULT) }
            if (after == AfterTurnOff.CHOOSE) openPicker()
            val snap = withContext(io) { app.snapshot() }
            _state.value = HomeState(snap, problems(snap))
        }
    }

    /** Hides the photo for good and replaces it right away. */
    fun hide(assetId: String) {
        app.hidePhotos(listOf(assetId))
        app.swapNow()
    }
}
