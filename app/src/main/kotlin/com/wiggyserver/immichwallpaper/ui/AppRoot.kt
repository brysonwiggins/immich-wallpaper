package com.wiggyserver.immichwallpaper.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.wiggyserver.immichwallpaper.AppLog
import com.wiggyserver.immichwallpaper.data.OfflineSlots
import com.wiggyserver.immichwallpaper.data.PhotoLibrary
import com.wiggyserver.immichwallpaper.presentation.AppPort
import com.wiggyserver.immichwallpaper.presentation.Destination
import com.wiggyserver.immichwallpaper.presentation.HomeModel
import com.wiggyserver.immichwallpaper.presentation.Navigator
import com.wiggyserver.immichwallpaper.presentation.OfflineSetModel
import com.wiggyserver.immichwallpaper.presentation.OnboardingModel
import com.wiggyserver.immichwallpaper.presentation.OnboardingStep
import com.wiggyserver.immichwallpaper.presentation.PickerModel
import com.wiggyserver.immichwallpaper.presentation.Problem
import com.wiggyserver.immichwallpaper.presentation.ServerModel
import com.wiggyserver.immichwallpaper.presentation.SettingsModel
import com.wiggyserver.immichwallpaper.presentation.SourcesModel
import com.wiggyserver.immichwallpaper.presentation.Tab
import com.wiggyserver.immichwallpaper.ui.screens.HomeActions
import com.wiggyserver.immichwallpaper.ui.screens.HomeScreen
import com.wiggyserver.immichwallpaper.ui.screens.LogScreen
import com.wiggyserver.immichwallpaper.ui.screens.OfflineSetActions
import com.wiggyserver.immichwallpaper.ui.screens.OfflineSetScreen
import com.wiggyserver.immichwallpaper.ui.screens.OnboardingActions
import com.wiggyserver.immichwallpaper.ui.screens.OnboardingScreen
import com.wiggyserver.immichwallpaper.ui.screens.OnboardingView
import com.wiggyserver.immichwallpaper.ui.screens.PickerActions
import com.wiggyserver.immichwallpaper.ui.screens.PickerScreen
import com.wiggyserver.immichwallpaper.ui.screens.ServerActions
import com.wiggyserver.immichwallpaper.ui.screens.ServerScreen
import com.wiggyserver.immichwallpaper.ui.screens.SettingsActions
import com.wiggyserver.immichwallpaper.ui.screens.SettingsScreen
import com.wiggyserver.immichwallpaper.ui.screens.SettingsView
import com.wiggyserver.immichwallpaper.ui.screens.SourcesActions
import com.wiggyserver.immichwallpaper.ui.screens.SourcesScreen
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay

/** Things only the Activity can do (permissions, system settings, sharing). */
class SystemActions(
    val setService: (Boolean) -> Unit,
    val requestBattery: () -> Unit,
    val shareLog: () -> Unit,
    val openUrl: (String) -> Unit,
    /** Shows the system "Nearby devices" prompt (Android 17+ home-network access). */
    val requestLocalNetwork: ((Boolean) -> Unit) -> Unit,
    /** Opens the system wallpaper picker. */
    val pickWallpaper: () -> Unit,
    val support: com.wiggyserver.immichwallpaper.support.Support,
    val version: String,
)

/** The screen models for one Activity; they outlive configuration changes (the Activity handles them). */
class Models(val app: AppPort, val library: PhotoLibrary, val slots: OfflineSlots, val scope: CoroutineScope, val io: CoroutineDispatcher) {
    val navigator = Navigator()
    val home = HomeModel(app, scope, io)
    val sources = SourcesModel(app, library, scope, io)
    val offline = OfflineSetModel(app, slots, scope, io)
    val settings = SettingsModel(app)
    val onboarding = OnboardingModel(app)

    fun server() = ServerModel(app, scope, io)
    fun picker(target: Destination.Picker): PickerModel = if (target.hide) {
        // "Included photos": what the saved sources can show, minus everything hidden.
        PickerModel(library, MAX_HIDE_PER_PICK, app.config.value.filter.excludedAssetIds.toSet(), scope, io, pool = app.config.value.filter)
    } else {
        offline.state.value.let { s -> PickerModel(library, if (target.slots.isNotEmpty()) target.slots.size else s.free, s.chosenIds, scope, io) }
    }

    private companion object {
        const val MAX_HIDE_PER_PICK = 100
    }
}

/** Compact widths (phones, the Fold's cover screen) get a bottom bar; wider ones a side rail. */
@Composable
fun isWide(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun AppRoot(models: Models, system: SystemActions) {
    val config by models.app.config.collectAsState()
    LaunchedEffect(Unit) {
        while (true) {
            models.home.refresh()
            delay(1_000)
        }
    }
    if (config.onboarded) MainFlow(models, system) else OnboardingFlow(models, system)
}

@Composable
private fun MainFlow(m: Models, system: SystemActions) {
    val nav = m.navigator
    val stack by nav.stack.collectAsState()
    val dest = stack.last()
    val wide = isWide()
    BackHandler(enabled = stack.size > 1 || stack.first() != Destination.Home) { nav.back() }

    MainChrome(if (stack.size == 1) nav.tab else null, wide, nav::select) { modifier ->
        Box(modifier) { DestinationContent(dest, m, system, wide) }
    }
}

/** Bottom bar (compact) or side rail (wide) around [content]; no navigation when [selected] is null. */
@Composable
fun MainChrome(selected: Tab?, wide: Boolean, onSelect: (Tab) -> Unit, content: @Composable (Modifier) -> Unit) {
    if (wide) {
        Row(Modifier.fillMaxSize()) {
            if (selected != null) {
                NavigationRail {
                    Tab.entries.forEach { t ->
                        NavigationRailItem(selected = selected == t, onClick = { onSelect(t) }, icon = { Icon(t.icon(), null) }, label = { Text(t.label()) })
                    }
                }
            }
            content(Modifier.weight(1f))
        }
    } else {
        Scaffold(
            bottomBar = {
                if (selected != null) {
                    NavigationBar {
                        Tab.entries.forEach { t ->
                            NavigationBarItem(selected = selected == t, onClick = { onSelect(t) }, icon = { Icon(t.icon(), null) }, label = { Text(t.label()) })
                        }
                    }
                }
            },
        ) { padding -> content(Modifier.padding(padding).consumeWindowInsets(padding)) }
    }
}

@Composable
private fun DestinationContent(dest: Destination, m: Models, system: SystemActions, wide: Boolean) {
    val nav = m.navigator
    val snapshot = m.home.state.collectAsState().value?.snapshot
    when (dest) {
        Destination.Home -> HomeScreen(
            m.home.state.collectAsState().value,
            wide,
            HomeActions(
                swapNow = m.home::swapNow,
                hide = m.home::hide,
                setService = system.setService,
                fix = { p ->
                    when (p) {
                        Problem.NOT_CONFIGURED, Problem.KEY_REJECTED -> nav.push(Destination.Server)
                        Problem.SERVICE_OFF -> system.setService(true)
                        Problem.BATTERY_OPTIMIZED -> system.requestBattery()
                        Problem.LOCAL_NETWORK -> system.requestLocalNetwork { granted -> if (granted) m.app.rebuildQueue() }
                        Problem.NO_PHOTOS -> nav.select(Tab.SOURCES)
                        Problem.NO_OFFLINE_SET -> nav.select(Tab.OFFLINE_SET)
                        Problem.UNREACHABLE, Problem.STORAGE_FULL -> Unit
                    }
                },
                turnOff = { m.home.turnOff(it, system.pickWallpaper) },
            ),
        )
        Destination.OfflineSet -> {
            LaunchedEffect(Unit) {
                while (true) {
                    m.offline.refresh()
                    delay(2_000) // pending photos turn ready as they download
                }
            }
            OfflineSetScreen(m.offline.state.collectAsState().value, wide, offlineActions(m) { slots -> nav.push(Destination.Picker(slots)) })
        }
        is Destination.Picker -> PickerFor(dest, m) { nav.back() }
        Destination.Sources -> {
            LaunchedEffect(Unit) { if (m.sources.state.value.albums.isEmpty()) m.sources.load() }
            val config by m.app.config.collectAsState()
            SourcesScreen(m.sources.state.collectAsState().value, config.filter.excludedAssetIds, sourcesActions(m) { nav.push(Destination.Picker(hide = true)) })
        }
        Destination.Settings -> {
            val config by m.app.config.collectAsState()
            val s = m.settings
            SettingsScreen(
                SettingsView(
                    settings = config.settings,
                    serverUrl = config.serverUrl,
                    sideBySideAvailable = s.sideBySideAvailable,
                    serviceOn = snapshot?.serviceOn ?: false,
                    batteryExempt = snapshot?.batteryExempt ?: true,
                    capabilities = snapshot?.capabilities ?: com.wiggyserver.immichwallpaper.core.Capabilities(false, false),
                    version = system.version,
                ),
                SettingsActions(
                    setApplyTarget = s::setApplyTarget,
                    setSideBySide = s::setSideBySide,
                    setFaceAware = s::setFaceAware,
                    setWifiOnly = s::setWifiOnly,
                    setCooldownSeconds = s::setCooldownSeconds,
                    setService = system.setService,
                    requestBattery = system.requestBattery,
                    openServer = { nav.push(Destination.Server) },
                    openLog = { nav.push(Destination.Log) },
                    openAbout = { nav.push(Destination.About) },
                    retryDisabledFeatures = s::retryDisabledFeatures,
                    rebuildQueue = m.app::rebuildQueue,
                    turnOff = { m.home.turnOff(it, system.pickWallpaper) },
                ),
            )
        }
        Destination.Server -> {
            val server = remember { m.server() }
            ServerScreen(server.state.collectAsState().value, ServerActions(server::setUrl, server::setKey, server::test) { system.requestLocalNetwork(server::localNetworkResult) }) { nav.back() }
        }
        Destination.About -> com.wiggyserver.immichwallpaper.ui.screens.AboutScreen(system.version, system.openUrl, { nav.back() }) { system.support.Section() }
        Destination.Log -> LogScreen(AppLog.lines.collectAsState().value, system.shareLog) { nav.back() }
    }
}

@Composable
private fun PickerFor(target: Destination.Picker, m: Models, close: () -> Unit) {
    val picker = remember(target) { m.picker(target).also { it.loadSources() } }
    val state by picker.state.collectAsState()
    BackHandler { close() }
    PickerScreen(
        state,
        PickerActions(
            setSource = picker::setSource,
            toggle = picker::toggle,
            loadMore = picker::loadMore,
            done = {
                val picked = state.selected
                when {
                    target.hide -> m.sources.hidePhotos(picked)
                    target.slots.isNotEmpty() -> m.offline.replace(target.slots, picked)
                    else -> m.offline.add(picked)
                }
                close()
            },
            back = close,
        ),
        hiding = target.hide,
    )
}

private fun offlineActions(m: Models, openPicker: (List<Int>) -> Unit) = OfflineSetActions(
    pick = { openPicker(emptyList()) },
    replace = { openPicker(it) },
    remove = { m.offline.remove(it) },
    replaceRandomly = m.offline::replaceRandomly,
    fillRandomly = m.offline::fillRandomly,
    toggleSelected = m.offline::toggleSelected,
    clearSelection = m.offline::clearSelection,
)

private fun sourcesActions(m: Models, pickPhotosToHide: () -> Unit) = SourcesActions(
    setMode = m.sources::setMode,
    toggleAlbum = m.sources::toggleAlbum,
    togglePerson = m.sources::togglePerson,
    setFavoritesOnly = m.sources::setFavoritesOnly,
    setNotInAlbum = m.sources::setNotInAlbum,
    clear = m.sources::clear,
    save = m.sources::save,
    retry = m.sources::load,
    unhidePhoto = m.sources::unhidePhoto,
    pickPhotosToHide = pickPhotosToHide,
)

@Composable
private fun OnboardingFlow(m: Models, system: SystemActions) {
    val step by m.onboarding.step.collectAsState()
    val server = remember { m.server() }
    var picking by remember { mutableStateOf<Destination.Picker?>(null) }
    picking?.let { target ->
        PickerFor(target, m) { picking = null }
        return
    }
    BackHandler(enabled = step != OnboardingStep.SERVER) { m.onboarding.back() }
    LaunchedEffect(step) {
        when (step) {
            OnboardingStep.SOURCES -> m.sources.load()
            OnboardingStep.OFFLINE_SET -> while (true) {
                m.offline.refresh()
                delay(2_000)
            }
            else -> Unit
        }
    }
    val snapshot = m.home.state.collectAsState().value?.snapshot
    OnboardingScreen(
        OnboardingView(
            step = step,
            server = server.state.collectAsState().value,
            sources = m.sources.state.collectAsState().value,
            hiddenPhotos = m.app.config.collectAsState().value.filter.excludedAssetIds,
            offline = m.offline.state.collectAsState().value,
            serviceOn = snapshot?.serviceOn ?: false,
            batteryExempt = snapshot?.batteryExempt ?: false,
            wide = isWide(),
        ),
        OnboardingActions(
            server = ServerActions(server::setUrl, server::setKey, server::test) { system.requestLocalNetwork(server::localNetworkResult) },
            sources = sourcesActions(m) { picking = Destination.Picker(hide = true) },
            offline = offlineActions(m) { slots -> picking = Destination.Picker(slots) },
            setService = system.setService,
            requestBattery = system.requestBattery,
            next = {
                if (step == OnboardingStep.SOURCES && m.sources.state.value.dirty) m.sources.save()
                m.onboarding.next()
            },
            back = { m.onboarding.back() },
        ),
    )
}

private fun Tab.label() = when (this) {
    Tab.HOME -> "Home"
    Tab.OFFLINE_SET -> "Offline"
    Tab.SOURCES -> "Sources"
    Tab.SETTINGS -> "Settings"
}

private fun Tab.icon() = when (this) {
    Tab.HOME -> AppIcons.Home
    Tab.OFFLINE_SET -> AppIcons.Offline
    Tab.SOURCES -> AppIcons.Sources
    Tab.SETTINGS -> AppIcons.Settings
}
