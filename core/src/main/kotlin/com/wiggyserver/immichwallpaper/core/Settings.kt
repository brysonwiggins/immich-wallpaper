package com.wiggyserver.immichwallpaper.core

/** User settings and their defaults. */
data class Settings(
    val applyTarget: ApplyTarget = ApplyTarget.LOCK_AND_HOME,
    val sideBySide: Boolean = false,
    val cooldownMillis: Long = 2_000,
    val faceAwareCrop: Boolean = true,
    val wifiOnlyRefill: Boolean = false,
) {
    init {
        require(cooldownMillis >= 0) { "cooldown must not be negative" }
    }

    /** Side-by-side only applies when the user enabled it and a screen supports it. */
    fun effectiveSideBySide(screens: List<Screen>) = sideBySide && sideBySideAvailable(screens)

    companion object {
        /** Whether to show the side-by-side option at all. */
        fun sideBySideAvailable(screens: List<Screen>) = screens.any { it.supportsSideBySide() }
    }
}
