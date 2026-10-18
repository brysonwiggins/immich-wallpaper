package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.AppInfo
import com.wiggyserver.immichwallpaper.presentation.AfterTurnOff
import com.wiggyserver.immichwallpaper.presentation.AppSnapshot
import com.wiggyserver.immichwallpaper.presentation.HomeState
import com.wiggyserver.immichwallpaper.presentation.Preview
import com.wiggyserver.immichwallpaper.presentation.Problem
import com.wiggyserver.immichwallpaper.presentation.canHideNowShowing
import com.wiggyserver.immichwallpaper.presentation.upNextNote
import com.wiggyserver.immichwallpaper.ui.AppIcons
import com.wiggyserver.immichwallpaper.ui.Group
import com.wiggyserver.immichwallpaper.ui.ListRow
import com.wiggyserver.immichwallpaper.ui.LocalPhotos
import com.wiggyserver.immichwallpaper.ui.NoticeCard
import com.wiggyserver.immichwallpaper.ui.SectionTitle
import com.wiggyserver.immichwallpaper.ui.SwitchRow
import com.wiggyserver.immichwallpaper.ui.Tone
import com.wiggyserver.immichwallpaper.ui.TurnOffRow

class HomeActions(
    val swapNow: () -> Unit,
    val hide: (String) -> Unit,
    val setService: (Boolean) -> Unit,
    val fix: (Problem) -> Unit,
    val turnOff: (AfterTurnOff) -> Unit,
)

@Composable
fun HomeScreen(state: HomeState?, wide: Boolean, actions: HomeActions) {
    Box(Modifier.fillMaxSize().safeDrawingPadding(), contentAlignment = Alignment.TopCenter) {
        if (state == null) return@Box
        if (wide) {
            Row(Modifier.widthIn(max = 1100.dp).fillMaxSize().padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1.1f).fillMaxSize()) {
                    Header(state.snapshot)
                    Spacer(Modifier.height(16.dp))
                    UpNext(state.snapshot, Modifier.weight(1f, fill = false))
                    Spacer(Modifier.height(16.dp))
                    NextButton(state.snapshot, actions)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Details(state, actions)
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Header(state.snapshot)
                Spacer(Modifier.height(8.dp))
                UpNext(state.snapshot, Modifier)
                Spacer(Modifier.height(8.dp))
                NextButton(state.snapshot, actions)
                Details(state, actions)
            }
        }
    }
}

@Composable
private fun Header(s: AppSnapshot) {
    Column {
        Text(AppInfo.NAME, style = MaterialTheme.typography.headlineMedium)
        Text(
            when {
                !s.configured -> "Not set up yet"
                s.serviceOn -> "A new photo every time the screen turns off"
                else -> "Paused"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The queued wallpapers as they'll appear on this screen, with empty spots while preparing. */
@Composable
private fun UpNext(s: AppSnapshot, modifier: Modifier) {
    Column(modifier) {
        Text("Up next", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        val note = upNextNote(s)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
            val ratio = s.upNext.firstOrNull()?.ratio() ?: DEFAULT_RATIO
            repeat(s.queueTarget) { i ->
                val p = s.upNext.getOrNull(i)
                val shape = RoundedCornerShape(16.dp)
                Box(Modifier.weight(1f).aspectRatio(ratio).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    if (p != null) {
                        LocalPhotos.current.Rendered(p, Modifier.fillMaxSize())
                    } else {
                        Text(
                            if (s.configured && note == null) "Preparing…" else "—",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.align(Alignment.Center).padding(8.dp),
                        )
                    }
                }
            }
        }
        if (note != null) {
            Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private const val DEFAULT_RATIO = 1248f / 1972f

private fun Preview.ratio(): Float = crop?.let { it.width.toFloat() / it.height } ?: DEFAULT_RATIO

@Composable
private fun NextButton(s: AppSnapshot, actions: HomeActions) {
    Button(onClick = actions.swapNow, enabled = s.queued > 0 || s.offlineReady > 0, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(AppIcons.Next, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Next photo now")
    }
}

@Composable
private fun ColumnScope.Details(state: HomeState, actions: HomeActions) {
    val s = state.snapshot
    state.problems.forEach { ProblemCard(it, actions) }
    if (s.nowShowing.isNotEmpty()) {
        SectionTitle("On screen now")
        Group {
            s.nowShowing.forEach { id ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LocalPhotos.current.Asset(id, Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)))
                    val hideable = canHideNowShowing(s)
                    Text(
                        if (hideable) "Don't want to see this one?" else "It's the only photo in your sources",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    )
                    TextButton(onClick = { actions.hide(id) }, enabled = hideable) { Text("Never show") }
                }
            }
        }
    }
    SectionTitle("Status")
    Group {
        SwitchRow("Change at screen-off", if (s.serviceOn) "On" else "Off", s.serviceOn, actions.setService)
        ListRow("Ready to show", "${s.queued} of ${s.queueTarget} photos prepared")
        ListRow("Offline photos", if (s.offlineTotal == 0) "None picked" else "${s.offlineReady} of ${s.offlineTotal} ready")
        ListRow("Last change", s.lastSwap ?: "None yet")
        TurnOffRow(actions.turnOff)
    }
}

@Composable
private fun ProblemCard(p: Problem, actions: HomeActions) {
    val fix = { actions.fix(p) }
    when (p) {
        Problem.NOT_CONFIGURED -> NoticeCard(AppIcons.Warning, "Not connected", "Add your Immich server to start.", Tone.ERROR, "Set up", fix)
        Problem.KEY_REJECTED -> NoticeCard(AppIcons.Warning, "API key rejected", "Immich no longer accepts the saved key, or it is missing a permission. New photos stop until it is fixed.", Tone.ERROR, "Update key", fix)
        Problem.SERVICE_OFF -> NoticeCard(AppIcons.Warning, "Wallpaper changes are off", "Turn them on to get a new photo every time the screen turns off.", Tone.WARNING, "Turn on", fix)
        Problem.LOCAL_NETWORK -> NoticeCard(AppIcons.CloudOff, "Home network blocked", "Android needs the Nearby devices permission for the app to reach your server.", Tone.WARNING, "Allow", fix)
        Problem.BATTERY_OPTIMIZED -> NoticeCard(AppIcons.Warning, "Battery optimization is on", "One UI may stop the app overnight. Allow it to run in the background.", Tone.WARNING, "Allow", fix)
        Problem.UNREACHABLE -> NoticeCard(AppIcons.CloudOff, "Server not reachable", "Showing your offline photos until Immich can be reached again.", Tone.INFO)
        Problem.NO_PHOTOS -> NoticeCard(AppIcons.Warning, "No photos in your sources", "Your chosen albums, people or favorites have no photos, or all of them are hidden. Videos aren't used. Choose other sources.", Tone.WARNING, "Change sources", fix)
        Problem.STORAGE_FULL -> NoticeCard(AppIcons.Warning, "Storage limit reached", "The photo cache is full. The app retries after the next change.", Tone.WARNING)
        Problem.NO_OFFLINE_SET -> NoticeCard(AppIcons.Offline, "No offline photos", "Pick up to 10 photos to show when your server can't be reached.", Tone.INFO, "Pick photos", fix)
    }
}
