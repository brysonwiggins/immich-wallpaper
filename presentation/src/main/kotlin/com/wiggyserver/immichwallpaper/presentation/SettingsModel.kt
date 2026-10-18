package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Settings

/** Settings apply immediately; the app decides which changes make prepared photos stale. */
class SettingsModel(private val app: AppPort) {
    val config get() = app.config
    val sideBySideAvailable: Boolean get() = app.sideBySideAvailable()

    fun setApplyTarget(target: ApplyTarget) = edit { it.copy(applyTarget = target) }
    fun setSideBySide(on: Boolean) = edit { it.copy(sideBySide = on) }
    fun setFaceAware(on: Boolean) = edit { it.copy(faceAwareCrop = on) }
    fun setWifiOnly(on: Boolean) = edit { it.copy(wifiOnlyRefill = on) }
    fun setCooldownSeconds(seconds: Int) = edit { it.copy(cooldownMillis = seconds * 1_000L) }
    fun retryDisabledFeatures() = app.resetCapabilities()

    private fun edit(change: (Settings) -> Settings) = app.updateConfig { it.copy(settings = change(it.settings)) }
}
