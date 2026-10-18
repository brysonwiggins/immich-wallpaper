package com.wiggyserver.immichwallpaper.platform

import android.content.Context
import androidx.core.content.edit
import com.wiggyserver.immichwallpaper.core.Capabilities

/**
 * Detected platform capabilities, minus anything the platform rejected at runtime
 * (the fallback half of the owner decision "B with fallback A").
 */
class CapabilityStore(context: Context, private val foldable: Boolean) {
    private val prefs = context.getSharedPreferences("capabilities", Context.MODE_PRIVATE)

    fun current() = Capabilities(
        perScreenFlags = foldable && PlatformWallpaperSetter.samsungPerScreenFlags && !prefs.getBoolean(PER_SCREEN_OFF, false),
        cropsApi = PlatformWallpaperSetter.cropsApiAvailable && !prefs.getBoolean(CROPS_OFF, false),
    )

    fun disablePerScreen() = prefs.edit { putBoolean(PER_SCREEN_OFF, true) }
    fun disableCrops() = prefs.edit { putBoolean(CROPS_OFF, true) }

    /** After an OS update the hidden APIs may work again; let the user retry from settings. */
    fun reset() = prefs.edit { clear() }

    private companion object {
        const val PER_SCREEN_OFF = "perScreenDisabled"
        const val CROPS_OFF = "cropsDisabled"
    }
}
