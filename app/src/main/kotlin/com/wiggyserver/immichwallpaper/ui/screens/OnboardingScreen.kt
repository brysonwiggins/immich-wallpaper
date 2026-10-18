package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.presentation.OfflineSetState
import com.wiggyserver.immichwallpaper.presentation.OnboardingStep
import com.wiggyserver.immichwallpaper.presentation.ServerState
import com.wiggyserver.immichwallpaper.presentation.ServerStatus
import com.wiggyserver.immichwallpaper.presentation.SourcesState
import com.wiggyserver.immichwallpaper.ui.FormColumn
import com.wiggyserver.immichwallpaper.ui.Group
import com.wiggyserver.immichwallpaper.ui.ListRow
import com.wiggyserver.immichwallpaper.ui.MaxContentWidth
import com.wiggyserver.immichwallpaper.ui.SwitchRow

class OnboardingView(
    val step: OnboardingStep,
    val server: ServerState,
    val sources: SourcesState,
    val hiddenPhotos: List<String>,
    val offline: OfflineSetState,
    val serviceOn: Boolean,
    val batteryExempt: Boolean,
    val wide: Boolean,
)

class OnboardingActions(
    val server: ServerActions,
    val sources: SourcesActions,
    val offline: OfflineSetActions,
    val setService: (Boolean) -> Unit,
    val requestBattery: () -> Unit,
    val next: () -> Unit,
    val back: () -> Unit,
)

@Composable
fun OnboardingScreen(view: OnboardingView, actions: OnboardingActions) {
    val step = view.step
    val canContinue = when (step) {
        OnboardingStep.SERVER -> view.server.status is ServerStatus.Connected || (view.server.hasSavedKey && view.server.url.isNotBlank() && view.server.status == ServerStatus.Idle)
        else -> !view.offline.busy
    }
    val nextLabel = when (step) {
        OnboardingStep.OFFLINE_SET -> if (view.offline.ready == 0 && view.offline.free == 10) "Skip" else "Next"
        OnboardingStep.BACKGROUND -> "Finish"
        else -> "Next"
    }
    Scaffold(
        modifier = Modifier.safeDrawingPadding(),
        topBar = {
            Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Column(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth()) {
                    Text("Step ${step.number} of ${OnboardingStep.entries.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    LinearProgressIndicator(progress = { step.number / OnboardingStep.entries.size.toFloat() }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
                    Text(step.title(), style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                    Row(Modifier.widthIn(max = MaxContentWidth).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (step != OnboardingStep.SERVER) TextButton(onClick = actions.back) { Text("Back") } else Spacer(Modifier)
                        Button(onClick = actions.next, enabled = canContinue) { Text(nextLabel) }
                    }
                }
            }
        },
    ) { padding ->
        when (step) {
            OnboardingStep.SERVER -> FormColumn(padding) { ServerForm(view.server, actions.server) }
            OnboardingStep.SOURCES -> SourcesContent(view.sources, view.hiddenPhotos, actions.sources, padding)
            OnboardingStep.OFFLINE_SET -> OfflineSetContent(view.offline, view.wide, actions.offline, padding)
            OnboardingStep.BACKGROUND -> FormColumn(padding) {
                Text(
                    "The app swaps the photo the moment the screen turns off, so it's ready the next time you look. " +
                        "For that it runs in the background with a silent notification, and One UI must not put it to sleep.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Group {
                    SwitchRow("Change at screen-off", "Shows a silent notification while on", view.serviceOn, actions.setService)
                    if (view.batteryExempt) {
                        ListRow("Background running", "Allowed")
                    } else {
                        ListRow("Background running", "Not allowed yet") { TextButton(onClick = actions.requestBattery) { Text("Allow") } }
                    }
                }
            }
        }
    }
}

private fun OnboardingStep.title() = when (this) {
    OnboardingStep.SERVER -> "Connect to Immich"
    OnboardingStep.SOURCES -> "Choose your photos"
    OnboardingStep.OFFLINE_SET -> "Pick offline photos"
    OnboardingStep.BACKGROUND -> "Run in the background"
}
