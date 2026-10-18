package com.wiggyserver.immichwallpaper.ui

import android.app.Application
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.wiggyserver.immichwallpaper.core.Asset
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.PixelRect
import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.core.Size
import com.wiggyserver.immichwallpaper.data.Album
import com.wiggyserver.immichwallpaper.data.Person
import com.wiggyserver.immichwallpaper.data.SlotStatus
import com.wiggyserver.immichwallpaper.data.SourceFilter
import com.wiggyserver.immichwallpaper.presentation.AppSnapshot
import com.wiggyserver.immichwallpaper.presentation.HomeState
import com.wiggyserver.immichwallpaper.presentation.OfflineSetState
import com.wiggyserver.immichwallpaper.presentation.OnboardingStep
import com.wiggyserver.immichwallpaper.presentation.PickerSource
import com.wiggyserver.immichwallpaper.presentation.PickerState
import com.wiggyserver.immichwallpaper.presentation.Preview
import com.wiggyserver.immichwallpaper.presentation.Problem
import com.wiggyserver.immichwallpaper.presentation.ServerState
import com.wiggyserver.immichwallpaper.presentation.ServerStatus
import com.wiggyserver.immichwallpaper.presentation.SlotView
import com.wiggyserver.immichwallpaper.presentation.SourcesMode
import com.wiggyserver.immichwallpaper.presentation.SourcesState
import com.wiggyserver.immichwallpaper.presentation.Tab
import androidx.compose.material3.Text
import com.wiggyserver.immichwallpaper.ui.screens.AboutScreen
import com.wiggyserver.immichwallpaper.ui.screens.HomeActions
import com.wiggyserver.immichwallpaper.ui.screens.HomeScreen
import com.wiggyserver.immichwallpaper.ui.screens.OfflineSetActions
import com.wiggyserver.immichwallpaper.ui.screens.OfflineSetScreen
import com.wiggyserver.immichwallpaper.ui.screens.OnboardingActions
import com.wiggyserver.immichwallpaper.ui.screens.OnboardingScreen
import com.wiggyserver.immichwallpaper.ui.screens.OnboardingView
import com.wiggyserver.immichwallpaper.ui.screens.PickerActions
import com.wiggyserver.immichwallpaper.ui.screens.PickerScreen
import com.wiggyserver.immichwallpaper.ui.screens.ServerActions
import com.wiggyserver.immichwallpaper.ui.screens.SettingsActions
import com.wiggyserver.immichwallpaper.ui.screens.SettingsScreen
import com.wiggyserver.immichwallpaper.ui.screens.SettingsView
import com.wiggyserver.immichwallpaper.ui.screens.SourcesActions
import com.wiggyserver.immichwallpaper.ui.screens.SourcesScreen
import com.wiggyserver.immichwallpaper.ui.theme.ImmichWallTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Screenshot goldens for each screen at the Fold 8's sizes and a regular phone.
 * Recorded and verified in CI with Roborazzi: `recordRoborazziDebug` / `verifyRoborazziDebug`.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class) // plain Application: no app graph or WorkManager
abstract class ScreenshotTest(private val device: String) {
    private fun capture(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        captureRoboImage("src/test/screenshots/$device/$name.png") {
            ImmichWallTheme(dark = dark) {
                CompositionLocalProvider(LocalPhotos provides PlaceholderPhotos) { Surface { content() } }
            }
        }

    private fun tab(tab: Tab, content: @Composable () -> Unit) = @Composable {
        MainChrome(tab, isWide(), {}) { modifier -> androidx.compose.foundation.layout.Box(modifier) { content() } }
    }

    @Test fun home() = capture("home", content = tab(Tab.HOME) { HomeScreen(Fixtures.home(), isWide(), Fixtures.homeActions) })

    @Test fun homeDark() = capture("home-dark", dark = true, content = tab(Tab.HOME) { HomeScreen(Fixtures.home(), isWide(), Fixtures.homeActions) })

    @Test fun homeSinglePhoto() = capture("home-single-photo", content = tab(Tab.HOME) { HomeScreen(Fixtures.homeSinglePhoto(), isWide(), Fixtures.homeActions) })

    @Test fun homeProblems() = capture("home-problems", content = tab(Tab.HOME) { HomeScreen(Fixtures.homeWithProblems(), isWide(), Fixtures.homeActions) })

    @Test fun offlineSet() = capture("offline-set", content = tab(Tab.OFFLINE_SET) { OfflineSetScreen(Fixtures.offline, isWide(), Fixtures.offlineActions) })

    @Test fun offlineSetSelecting() = capture("offline-set-selecting", content = tab(Tab.OFFLINE_SET) {
        OfflineSetScreen(Fixtures.offline.copy(selected = setOf(1, 3)), isWide(), Fixtures.offlineActions)
    })

    @Test fun picker() = capture("picker") { PickerScreen(Fixtures.picker, Fixtures.pickerActions) }

    @Test fun sources() = capture("sources", content = tab(Tab.SOURCES) { SourcesScreen(Fixtures.sources, emptyList(), Fixtures.sourcesActions) })

    @Test fun sourcesHiddenFromChosenSources() = capture("sources-never-show-chosen", content = tab(Tab.SOURCES) { SourcesScreen(Fixtures.sourcesHidingChosen, Fixtures.hiddenPhotos, Fixtures.sourcesActions) })

    @Test fun includedPhotos() = capture("included-photos") { PickerScreen(Fixtures.included, Fixtures.pickerActions, hiding = true) }

    @Test fun sourcesFavorites() = capture("sources-favorites", content = tab(Tab.SOURCES) { SourcesScreen(Fixtures.sourcesFavorites, emptyList(), Fixtures.sourcesActions) })

    @Test fun sourcesHidden() = capture("sources-never-show", content = tab(Tab.SOURCES) { SourcesScreen(Fixtures.sourcesHiding, Fixtures.hiddenPhotos, Fixtures.sourcesActions) })

    @Test fun settings() = capture("settings", content = tab(Tab.SETTINGS) { SettingsScreen(Fixtures.settings, Fixtures.settingsActions) })

    @Test fun about() = capture("about") {
        AboutScreen("0.6.0 (foss)", {}, {}) {
            Text("This app is free and open source. If it brightens your day, you can support its development.")
        }
    }

    @Test fun turnOff() = capture("turn-off") {
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.padding(24.dp), contentAlignment = androidx.compose.ui.Alignment.Center) { TurnOffPanel({}, {}) }
    }

    @Test fun onboarding() = capture("onboarding-server") { OnboardingScreen(Fixtures.onboarding(isWide()), Fixtures.onboardingActions) }
}

@Config(qualifiers = "w411dp-h914dp-port-420dpi")
class PhoneScreenshots : ScreenshotTest("phone")

/** Galaxy Z Fold 8 cover screen: 1248×1972 px at 420 dpi. */
@Config(qualifiers = "w475dp-h751dp-port-420dpi")
class CoverScreenshots : ScreenshotTest("fold-cover")

/** Galaxy Z Fold 8 inner screen, upright: 1848×2448 px at 420 dpi. */
@Config(qualifiers = "w704dp-h932dp-port-420dpi")
class InnerPortraitScreenshots : ScreenshotTest("fold-inner-portrait")

/** Galaxy Z Fold 8 inner screen, sideways. */
@Config(qualifiers = "w932dp-h704dp-land-420dpi")
class InnerLandscapeScreenshots : ScreenshotTest("fold-inner-landscape")

private object Fixtures {
    private fun snapshot(problems: Boolean) = AppSnapshot(
        configured = true,
        serviceOn = !problems,
        batteryExempt = !problems,
        queued = if (problems) 1 else 3,
        queueTarget = 3,
        upNext = (1..(if (problems) 1 else 3)).map { Preview(File("slide-$it.jpg"), PixelRect(0, 0, 1248, 1972)) },
        offlineReady = if (problems) 0 else 10,
        offlineTotal = if (problems) 0 else 10,
        lastSwap = "10:16 · new photo from Immich",
        nowShowing = listOf("now-1"),
        lastRefillFailure = null,
        screens = listOf("COVER 1248x1972", "INNER 2448x1848"),
        capabilities = Capabilities(perScreenFlags = true, cropsApi = true),
    )

    fun home() = HomeState(snapshot(problems = false), emptyList())

    fun homeSinglePhoto() = HomeState(snapshot(problems = false).let { it.copy(queued = 1, upNext = it.upNext.take(1), poolSize = 1) }, emptyList())

    fun homeWithProblems() = HomeState(snapshot(problems = true), listOf(Problem.SERVICE_OFF, Problem.BATTERY_OPTIMIZED, Problem.NO_OFFLINE_SET))

    val homeActions = HomeActions({}, {}, {}, {}, {})

    val offline = OfflineSetState(
        slots = List(10) { i ->
            when {
                i < 6 -> SlotView(i, "offline-$i", SlotStatus.READY)
                i == 6 -> SlotView(i, "offline-$i", SlotStatus.PENDING)
                else -> SlotView(i, null, null)
            }
        },
    )

    val offlineActions = OfflineSetActions({}, {}, {}, {}, {}, {}, {})

    private val albums = listOf(Album("a1", "Summer 2024", 182, "c1"), Album("a2", "Family", 64, "c2"), Album("a3", "Hiking", 23, "c3"), Album("a4", "Wedding", 412, "c4"))
    private val people = listOf(Person("p1", "Ana"), Person("p2", "Ben"), Person("p3", "Grandma"))

    val picker = PickerState(
        sources = listOf(PickerSource.All, PickerSource.Favorites) + albums.map { PickerSource.Album(it.id, it.name) } + people.map { PickerSource.Person(it.id, it.name) },
        source = PickerSource.All,
        photos = (1..40).map { Asset("photo-$it", Size(3, 4), null) },
        selected = listOf("photo-3", "photo-7"),
        maxSelection = 3,
        alreadyChosen = setOf("photo-1"),
    )

    val pickerActions = PickerActions({}, {}, {}, {}, {})

    val sources = SourcesState(albums, people, draft = SourceFilter(albumIds = listOf("a1", "a3"), notInAlbum = true), saved = SourceFilter(albumIds = listOf("a1")))

    // a2 alone chosen with no favorites: the warning shows; a4 is still being counted.
    val sourcesFavorites = SourcesState(
        albums, people,
        draft = SourceFilter(albumIds = listOf("a2"), favoritesOnly = true), saved = SourceFilter(albumIds = listOf("a2")),
        favoriteCounts = mapOf("a1" to 12, "a2" to 0, "a3" to 1), favoriteTotal = 248,
    )

    val sourcesActions = SourcesActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    val sourcesHiding = SourcesState(
        albums, people,
        draft = SourceFilter(excludedAlbumIds = listOf("a4"), excludedPersonIds = listOf("p2")),
        saved = SourceFilter(excludedAlbumIds = listOf("a4"), excludedPersonIds = listOf("p2")),
        mode = SourcesMode.HIDE,
    )

    val sourcesHidingChosen = sourcesHiding.copy(
        draft = SourceFilter(albumIds = listOf("a1"), personIds = listOf("p1", "p3"), excludedPersonIds = listOf("p2")),
        saved = SourceFilter(albumIds = listOf("a1"), personIds = listOf("p1", "p3"), excludedPersonIds = listOf("p2")),
    )

    val included = PickerState(
        sources = listOf(PickerSource.Album("a1", "Summer 2024"), PickerSource.NotInAlbum, PickerSource.Person("p1", "Ana"), PickerSource.Person("p3", "Grandma")),
        source = PickerSource.Album("a1", "Summer 2024"),
        photos = (1..30).map { Asset("pool-$it", Size(3, 4), null) },
        selected = listOf("pool-4"),
        maxSelection = 100,
    )

    val hiddenPhotos = listOf("hidden-1", "hidden-2", "hidden-3")

    val settings = SettingsView(Settings(), "https://photos.example.com", true, serviceOn = true, batteryExempt = false, Capabilities(true, true), "0.5.0")

    val settingsActions = SettingsActions({}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})

    fun onboarding(wide: Boolean) = OnboardingView(
        step = OnboardingStep.SERVER,
        server = ServerState("https://photos.example.com", "", hasSavedKey = false, status = ServerStatus.Failed("The server rejected this API key.")),
        sources = SourcesState(),
        hiddenPhotos = emptyList(),
        offline = OfflineSetState(),
        serviceOn = false,
        batteryExempt = false,
        wide = wide,
    )

    val onboardingActions = OnboardingActions(ServerActions({}, {}, {}, {}), sourcesActions, offlineActions, {}, {}, {}, {})
}
