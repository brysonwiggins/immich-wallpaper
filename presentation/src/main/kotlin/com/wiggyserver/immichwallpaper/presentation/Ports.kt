package com.wiggyserver.immichwallpaper.presentation

import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.data.AppConfig
import com.wiggyserver.immichwallpaper.data.ConnectionResult
import com.wiggyserver.immichwallpaper.data.FailReason
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/** What the screens need from the running app (the app's `AppGraph` implements it). */
interface AppPort {
    val config: StateFlow<AppConfig>
    fun updateConfig(change: (AppConfig) -> AppConfig)

    fun hasApiKey(): Boolean

    /** Checks a server and key without saving them. A null key means "the saved key". Blocking. */
    fun testConnection(url: String, key: String?): ConnectionResult

    /** Saves the server URL and, when not null, a new API key. */
    fun saveServer(url: String, key: String?)

    /** Random photo ids from the configured sources (for "fill the rest randomly"). Blocking. */
    fun randomPhotoIds(count: Int): List<String>

    fun snapshot(): AppSnapshot
    fun sideBySideAvailable(): Boolean
    fun swapNow()

    /**
     * Stops everything (the screen-off listener and background refills) until turned on again.
     * With [resetWallpaper], the phone's default wallpaper goes back on the screens the app changed.
     */
    fun turnOff(resetWallpaper: Boolean)

    /** Never show these photos again (applies at once; prepared slides showing them are dropped). */
    fun hidePhotos(ids: List<String>)
    fun unhidePhoto(id: String)
    fun rebuildQueue()

    /** False on Android 17+ until the user grants "Nearby devices" (ACCESS_LOCAL_NETWORK). */
    fun localNetworkAllowed(): Boolean

    /** Whether the server address is on the home network (resolves DNS; blocking). */
    fun isLocalServer(url: String): Boolean
    fun resetCapabilities()
}

/** Everything the home screen shows, read in one go. */
data class AppSnapshot(
    val configured: Boolean,
    val serviceOn: Boolean,
    val batteryExempt: Boolean,
    val queued: Int,
    val queueTarget: Int,
    /** The queued slides as they will look on the screen in use, in order. */
    val upNext: List<Preview>,
    val offlineReady: Int,
    val offlineTotal: Int,
    val lastSwap: String?,
    /** The photo(s) on the screen now, by Immich asset id (empty before the first change). */
    val nowShowing: List<String>,
    val lastRefillFailure: FailReason?,
    val screens: List<String>,
    val capabilities: Capabilities,
    val localNetworkAllowed: Boolean = true,
    /** Photos in the selection when it is small (fewer than a search batch); null for large ones. */
    val poolSize: Int? = null,
)

/** A rendered slide file; [crop] is the part shown on the current screen (null: the whole image). */
data class Preview(val file: File, val crop: PixelRect?)
