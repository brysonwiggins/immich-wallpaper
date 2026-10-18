package com.wiggyserver.immichwallpaper

import android.content.Context
import android.os.SystemClock
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import com.wiggyserver.immichwallpaper.data.LocalAddress
import com.wiggyserver.immichwallpaper.core.ScreenOrientation
import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.data.FailReason
import com.wiggyserver.immichwallpaper.data.ImmichClient
import com.wiggyserver.immichwallpaper.data.RefillResult
import com.wiggyserver.immichwallpaper.presentation.AppPort
import com.wiggyserver.immichwallpaper.presentation.AppSnapshot
import com.wiggyserver.immichwallpaper.presentation.Preview
import com.wiggyserver.immichwallpaper.service.SwapService
import kotlinx.coroutines.flow.StateFlow
import com.wiggyserver.immichwallpaper.core.PreparedSlide
import com.wiggyserver.immichwallpaper.core.ScreenKind
import com.wiggyserver.immichwallpaper.core.SlideOrigin
import com.wiggyserver.immichwallpaper.core.SlideQueue
import com.wiggyserver.immichwallpaper.core.SwapResult
import com.wiggyserver.immichwallpaper.core.WallpaperFlags
import com.wiggyserver.immichwallpaper.data.AppConfig
import com.wiggyserver.immichwallpaper.data.ApplyException
import com.wiggyserver.immichwallpaper.data.ConfigRepository
import com.wiggyserver.immichwallpaper.data.AppliedScreens
import com.wiggyserver.immichwallpaper.data.ConnectionResult
import com.wiggyserver.immichwallpaper.data.FileStore
import com.wiggyserver.immichwallpaper.data.JsonOfflineSetStore
import com.wiggyserver.immichwallpaper.data.JsonQueueStore
import com.wiggyserver.immichwallpaper.data.OfflineSetRepository
import com.wiggyserver.immichwallpaper.data.RenderContext
import com.wiggyserver.immichwallpaper.data.SlideApplier
import com.wiggyserver.immichwallpaper.data.WallpaperReset
import com.wiggyserver.immichwallpaper.data.SlotStatus
import com.wiggyserver.immichwallpaper.data.SwapEngine
import com.wiggyserver.immichwallpaper.platform.AndroidNetworkMonitor
import com.wiggyserver.immichwallpaper.platform.CapabilityStore
import com.wiggyserver.immichwallpaper.platform.DisplayTracker
import com.wiggyserver.immichwallpaper.platform.KeystoreCredentialStore
import com.wiggyserver.immichwallpaper.platform.PlatformWallpaperSetter
import com.wiggyserver.immichwallpaper.render.SlideComposer
import com.wiggyserver.immichwallpaper.render.android.AndroidBackend
import com.wiggyserver.immichwallpaper.work.Work
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

/**
 * Manual dependency wiring (instead of Hilt: one process, a handful of
 * singletons). Owns the swap path and the rules for when prepared slides become stale.
 */
class AppGraph(private val context: Context) : AppPort {
    private val stateDir = File(context.filesDir, "state")
    private val queueFiles = FileStore(File(context.filesDir, "queue"))
    private val offlineFiles = FileStore(File(context.filesDir, "offline"))

    private val configRepo = ConfigRepository(File(stateDir, "config.json"))
    val credentials = KeystoreCredentialStore(context)
    val display = DisplayTracker(context, File(stateDir, "displays.txt"))
    val capabilities = CapabilityStore(context, display.foldable)
    val api = ConfiguredImmichApi({ configRepo.config.value.serverUrl }, credentials::apiKey)

    /** Background work started from the UI or the service (never the swap path itself). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val renderer = SlideComposer(AndroidBackend())
    private val network = AndroidNetworkMonitor(context)

    fun renderContext(): RenderContext {
        val cfg = configRepo.config.value
        return RenderContext(display.screens(), display.active(), capabilities.current(), cfg.settings, cfg.filter)
    }

    val queue = com.wiggyserver.immichwallpaper.data.QueueRepository(
        api, queueFiles, SlideQueue(QUEUE_TARGET), JsonQueueStore(File(stateDir, "queue.json")), renderer, ::renderContext, network,
    )
    val offline = OfflineSetRepository(api, offlineFiles, JsonOfflineSetStore(File(stateDir, "offline.json")), renderer, ::renderContext)

    private val setter = PlatformWallpaperSetter(context)
    private val appliedScreens = AppliedScreens(File(stateDir, "applied-screens.txt"))
    private val swapExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "swap").apply { priority = Thread.MAX_PRIORITY } }

    @Volatile var lastSwap: String? = null
        private set

    // What's on the screen now, kept across restarts so Home can offer "Hide" for it.
    private val nowShowingFile = File(stateDir, "now-showing.txt")
    @Volatile private var nowShowing: List<String> = runCatching { nowShowingFile.readLines().filter { it.isNotBlank() } }.getOrDefault(emptyList())

    /** Why the last refill stopped short (null once one succeeds); drives the home screen's warnings. */
    @Volatile var lastRefillFailure: FailReason? = null
        private set

    val swap = SwapEngine(
        queue = queue,
        offline = offline,
        applier = SlideApplier(setter, queueFiles, appliedScreens),
        offlineApplier = SlideApplier(setter, offlineFiles, appliedScreens),
        activeScreenBit = { activeScreenBit(display.active().kind, capabilities.current().perScreenFlags) },
        refill = { Work.refill(context, configRepo.config.value.settings.wifiOnlyRefill) },
        clock = SystemClock::elapsedRealtime,
        cooldownMillis = { configRepo.config.value.settings.cooldownMillis },
        executor = swapExecutor::execute,
        onApplyError = ::onApplyError,
        onResult = ::onSwapResult,
    )

    /** Process start: reload the persisted queue, drop orphaned files, top up. */
    fun start() {
        // Installs from early test builds were set up on the old screen: don't send them through setup again.
        // Synchronous, so the first frame already shows the right flow.
        val cfg = configRepo.config.value
        if (!cfg.onboarded && cfg.serverUrl.isNotBlank() && hasApiKey()) configRepo.update { it.copy(onboarded = true) }
        scope.launch {
            if (display.observe()) AppLog.log("SCREENS", "new panel seen at start: ${display.current()}")
            queue.restore()
            offline.cleanup()
            AppLog.log("START", "queue=${queue.size} offline=${offlineSummary()} screens=${describeScreens()} caps=${capabilities.current()}")
            Work.refill(context, configRepo.config.value.settings.wifiOnlyRefill)
        }
    }

    /** Called at every screen-off (service) and app open; a new panel makes prepared slides stale. */
    fun observeDisplay() {
        if (display.observe()) {
            AppLog.log("SCREENS", "new panel ${display.current()}; screens now ${describeScreens()}")
            renderInputsChanged()
        }
    }

    override val config: StateFlow<AppConfig> get() = configRepo.config

    override fun updateConfig(change: (AppConfig) -> AppConfig) {
        val before = configRepo.config.value
        configRepo.update(change)
        val after = configRepo.config.value
        if (before != after) {
            AppLog.log("CONFIG", "settings=${after.settings} filter=${after.filter.describe()}")
            val newlyHidden = after.filter.excludedAssetIds - before.filter.excludedAssetIds.toSet()
            val onlyHiddenPhotosChanged = before.copy(filter = before.filter.copy(excludedAssetIds = emptyList())) ==
                after.copy(filter = after.filter.copy(excludedAssetIds = emptyList()))
            if (onlyHiddenPhotosChanged) {
                // Keep the rest of the queue: drop just the slides showing a newly hidden photo.
                if (newlyHidden.isNotEmpty()) {
                    queue.drop { slide -> slide.assetIds.any { it in newlyHidden } }
                    Work.refill(context, after.settings.wifiOnlyRefill)
                }
            } else if (before.settings.copy(cooldownMillis = 0, wifiOnlyRefill = false) != after.settings.copy(cooldownMillis = 0, wifiOnlyRefill = false) ||
                before.filter != after.filter || before.serverUrl != after.serverUrl
            ) {
                // New sources or server: show a photo from them as soon as one is ready (not at the next screen-off).
                if (before.filter != after.filter || before.serverUrl != after.serverUrl) applyWhenReady = true
                renderInputsChanged()
            }
        }
    }

    override fun hasApiKey() = credentials.apiKey() != null

    override fun testConnection(url: String, key: String?): ConnectionResult {
        val k = key ?: credentials.apiKey() ?: return ConnectionResult.Unauthorized
        val result = try {
            ImmichClient(url, k).testConnection()
        } catch (e: IllegalArgumentException) {
            ConnectionResult.Unreachable(e.message ?: "invalid address")
        }
        AppLog.log("CONNECTION", result.toString())
        return result
    }

    override fun saveServer(url: String, key: String?) {
        if (key != null) {
            credentials.setApiKey(key)
            AppLog.log("CONFIG", "API key saved")
        }
        val changedUrl = configRepo.config.value.serverUrl != url
        updateConfig { it.copy(serverUrl = url) }
        if (!changedUrl && key != null) renderInputsChanged() // a new key on the same server: retry now
    }

    override fun randomPhotoIds(count: Int): List<String> = api.randomAssets(configRepo.config.value.filter, count).map { it.id }

    override fun sideBySideAvailable() = Settings.sideBySideAvailable(display.screens())

    override fun localNetworkAllowed(): Boolean =
        Build.VERSION.SDK_INT < 37 || context.checkSelfPermission(ACCESS_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED

    override fun isLocalServer(url: String): Boolean = LocalAddress.isLocalServer(url)

    override fun rebuildQueue() {
        AppLog.log("REBUILD", "requested from the app")
        renderInputsChanged()
    }

    override fun resetCapabilities() {
        capabilities.reset()
        AppLog.log("CAPABILITY", "reset by the user; now ${capabilities.current()}")
        renderInputsChanged()
    }

    @Volatile private var applyWhenReady = false

    fun recordRefill(result: RefillResult) {
        when (result) {
            is RefillResult.Done -> lastRefillFailure = null
            is RefillResult.Failed -> lastRefillFailure = result.reason
            is RefillResult.Skipped -> Unit
        }
        if (applyWhenReady && queue.size > 0) {
            applyWhenReady = false
            if (SwapService.isWanted(context)) {
                AppLog.log("SOURCES", "applying a photo from the new sources")
                swapNow()
            }
        }
    }

    /** Queued and offline slides were built for other settings, screens or capabilities. */
    fun renderInputsChanged() {
        queue.invalidate()
        scope.launch {
            val n = offline.rerender()
            AppLog.log("RERENDER", "offline slides re-rendered=$n")
        }
        Work.refill(context, configRepo.config.value.settings.wifiOnlyRefill)
    }

    /** "Next wallpaper now" from the UI: same path as screen-off, cooldown included. */
    override fun hidePhotos(ids: List<String>) {
        AppLog.log("HIDE", "photos=${ids.size}")
        updateConfig { it.copy(filter = it.filter.copy(excludedAssetIds = (it.filter.excludedAssetIds + ids).distinct())) }
    }

    override fun unhidePhoto(id: String) = updateConfig { it.copy(filter = it.filter.copy(excludedAssetIds = it.filter.excludedAssetIds - id)) }

    override fun turnOff(resetWallpaper: Boolean) {
        SwapService.stop(context)
        Work.cancelAll(context)
        AppLog.log("OFF", "turned off resetWallpaper=$resetWallpaper")
        if (!resetWallpaper) return
        // On the swap thread, so a change already under way can't land after the reset.
        swapExecutor.submit {
            runCatching { WallpaperReset(setter, appliedScreens).reset(configRepo.config.value.settings.applyTarget) }
                .onSuccess {
                    nowShowing = emptyList()
                    nowShowingFile.delete()
                }
                .onFailure { AppLog.log("OFF", "reset failed ${it.javaClass.simpleName}: ${it.message}") }
        }.get()
    }

    override fun swapNow() {
        AppLog.log("SWAP_NOW", "decision=${swap.onScreenOff()}")
    }

    override fun snapshot(): AppSnapshot {
        val slots = offline.slots()
        val cfg = configRepo.config.value
        return AppSnapshot(
            configured = cfg.serverUrl.isNotBlank() && hasApiKey(),
            serviceOn = SwapService.isWanted(context),
            batteryExempt = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName),
            queued = queue.size,
            queueTarget = QUEUE_TARGET,
            upNext = queue.queued().mapNotNull(::preview),
            offlineReady = slots.count { it.status == SlotStatus.READY && it.slide != null },
            offlineTotal = slots.size,
            lastSwap = lastSwap,
            nowShowing = nowShowing,
            lastRefillFailure = lastRefillFailure,
            screens = display.screens().map { "${it.kind} ${it.naturalSize.width}x${it.naturalSize.height}" },
            capabilities = capabilities.current(),
            localNetworkAllowed = localNetworkAllowed(),
            poolSize = queue.smallPoolSize,
        )
    }

    /** The file and crop of a queued slide that the screen in use will show. */
    private fun preview(slide: PreparedSlide): Preview? {
        val bit = activeScreenBit(display.active().kind, capabilities.current().perScreenFlags)
        val file = slide.files.firstOrNull { bit != 0 && it.which and bit != 0 } ?: slide.files.firstOrNull() ?: return null
        val metrics = context.resources.displayMetrics
        val orientation = ScreenOrientation.of(Size(metrics.widthPixels, metrics.heightPixels))
        return Preview(queueFiles.file(file.fileName), file.cropHints?.get(orientation.key) ?: file.cropHints?.values?.firstOrNull())
    }

    private fun onApplyError(e: ApplyException) {
        when (e) {
            is ApplyException.PerScreenUnsupported -> capabilities.disablePerScreen()
            is ApplyException.CropsUnsupported -> capabilities.disableCrops()
        }
        AppLog.log("CAPABILITY", "${e.message} (${e.cause?.javaClass?.simpleName}: ${e.cause?.message}); falling back, now ${capabilities.current()}")
        renderInputsChanged()
    }

    private fun onSwapResult(result: SwapResult<PreparedSlide>) {
        val line = when (result) {
            is SwapResult.Applied -> "applied ${result.origin} ${result.slide.assetIds.size} photo(s)"
            is SwapResult.Failed -> "failed ${result.origin} ${result.error.javaClass.simpleName}: ${result.error.message}"
            SwapResult.NothingToShow -> "nothing to show (queue and offline set empty)"
        }
        if (result is SwapResult.Applied) {
            nowShowing = result.slide.assetIds
            runCatching { nowShowingFile.writeText(nowShowing.joinToString("\n")) }
        }
        val time = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
        lastSwap = "$time · " + when (result) {
            is SwapResult.Applied -> if (result.origin == SlideOrigin.QUEUE) "new photo from Immich" else "offline photo"
            is SwapResult.Failed -> "couldn't set the wallpaper"
            SwapResult.NothingToShow -> "nothing ready to show"
        }
        AppLog.log("SWAP", "$line queueLeft=${queue.size}")
    }

    private fun offlineSummary() = offline.slots().let { s -> "${s.count { it.status == SlotStatus.READY }}/${s.size}" }

    private fun describeScreens() = display.screens().joinToString { "${it.kind} ${it.naturalSize.width}x${it.naturalSize.height}" }

    private fun com.wiggyserver.immichwallpaper.data.SourceFilter.describe() =
        "albums=${albumIds.size} people=${personIds.size} favorites=$favoritesOnly " +
            "hiddenAlbums=${excludedAlbumIds.size} hiddenPeople=${excludedPersonIds.size} hiddenPhotos=${excludedAssetIds.size}"

    companion object {
        const val QUEUE_TARGET = 3
        const val ACCESS_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"

        /** One UI per-screen bit for the screen in use (applied first), or 0 when per-screen flags are off. */
        fun activeScreenBit(kind: ScreenKind, perScreenFlags: Boolean): Int = when {
            !perScreenFlags -> 0
            kind == ScreenKind.COVER -> WallpaperFlags.COVER
            kind == ScreenKind.INNER -> WallpaperFlags.INNER
            else -> 0
        }
    }
}
