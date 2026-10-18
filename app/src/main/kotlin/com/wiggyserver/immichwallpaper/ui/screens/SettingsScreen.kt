package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.core.ApplyTarget
import com.wiggyserver.immichwallpaper.core.Capabilities
import com.wiggyserver.immichwallpaper.core.Settings
import com.wiggyserver.immichwallpaper.presentation.AfterTurnOff
import com.wiggyserver.immichwallpaper.ui.TurnOffRow
import com.wiggyserver.immichwallpaper.ui.FormColumn
import com.wiggyserver.immichwallpaper.ui.Group
import com.wiggyserver.immichwallpaper.ui.ListRow
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold
import com.wiggyserver.immichwallpaper.ui.SectionTitle
import com.wiggyserver.immichwallpaper.ui.SwitchRow

/** Settings plus the system state shown alongside them. */
data class SettingsView(
    val settings: Settings,
    val serverUrl: String,
    val sideBySideAvailable: Boolean,
    val serviceOn: Boolean,
    val batteryExempt: Boolean,
    val capabilities: Capabilities,
    val version: String,
)

class SettingsActions(
    val setApplyTarget: (ApplyTarget) -> Unit,
    val setSideBySide: (Boolean) -> Unit,
    val setFaceAware: (Boolean) -> Unit,
    val setWifiOnly: (Boolean) -> Unit,
    val setCooldownSeconds: (Int) -> Unit,
    val setService: (Boolean) -> Unit,
    val requestBattery: () -> Unit,
    val openServer: () -> Unit,
    val openLog: () -> Unit,
    val openAbout: () -> Unit,
    val retryDisabledFeatures: () -> Unit,
    val rebuildQueue: () -> Unit,
    val turnOff: (AfterTurnOff) -> Unit,
)

@Composable
fun SettingsScreen(view: SettingsView, actions: SettingsActions) {
    val s = view.settings
    ScreenScaffold("Settings") { padding ->
        FormColumn(padding) {
            SectionTitle("Wallpaper")
            Group {
                Text("Change the wallpaper on", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp, end = 16.dp))
                ApplyTarget.entries.forEach { t ->
                    Row(
                        Modifier.fillMaxWidth().selectable(s.applyTarget == t, role = Role.RadioButton) { actions.setApplyTarget(t) }.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(s.applyTarget == t, onClick = null, modifier = Modifier.padding(8.dp))
                        Text(t.label(), style = MaterialTheme.typography.bodyLarge)
                    }
                }
                if (view.sideBySideAvailable) {
                    SwitchRow("Two portraits side by side", "On the unfolded screen held sideways, pair two portrait photos instead of one landscape", s.sideBySide, actions.setSideBySide)
                }
                SwitchRow("Keep faces in frame", "Crop around the people in the photo", s.faceAwareCrop, actions.setFaceAware)
            }

            SectionTitle("Changing")
            Group {
                SwitchRow("Change at screen-off", "Runs in the background with a silent notification", view.serviceOn, actions.setService)
                var cooldown by remember(s.cooldownMillis) { mutableFloatStateOf(s.cooldownMillis / 1000f) }
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text("Minimum time between changes", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (cooldown < 0.5f) "Every time the screen turns off" else "${cooldown.toInt()} s, so a quick off-and-on keeps the same photo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(cooldown, { cooldown = it }, valueRange = 0f..10f, steps = 9, onValueChangeFinished = { actions.setCooldownSeconds(cooldown.toInt()) })
                }
                SwitchRow("Download on Wi-Fi only", "Prepare new photos only on unmetered networks", s.wifiOnlyRefill, actions.setWifiOnly)
                TurnOffRow(actions.turnOff)
                if (view.batteryExempt) {
                    ListRow("Battery optimization", "Off for this app. It can run overnight.")
                } else {
                    ListRow("Battery optimization", "On. One UI may stop the app overnight.") { TextButton(onClick = actions.requestBattery) { Text("Allow") } }
                }
            }

            SectionTitle("Server")
            Group {
                ListRow("Immich server", view.serverUrl.ifBlank { "Not set" }, onClick = actions.openServer) { TextButton(onClick = actions.openServer) { Text("Change") } }
            }

            SectionTitle("Troubleshooting")
            Group {
                ListRow("Diagnostics log", "What the app did, without keys or addresses", onClick = actions.openLog)
                ListRow("Prepare photos again", "Discard the prepared photos and make new ones", onClick = actions.rebuildQueue)
                ListRow(
                    "Display features",
                    "Separate cover and inner wallpapers: ${view.capabilities.perScreenFlags.yesNo()} · Per-orientation crops: ${view.capabilities.cropsApi.yesNo()}",
                ) { TextButton(onClick = actions.retryDisabledFeatures) { Text("Retry") } }
                ListRow("About", "Version ${view.version} · support, privacy, source code", onClick = actions.openAbout)
            }
        }
    }
}

private fun Boolean.yesNo() = if (this) "on" else "off"

private fun ApplyTarget.label() = when (this) {
    ApplyTarget.LOCK -> "Lock screen"
    ApplyTarget.HOME -> "Home screen"
    ApplyTarget.LOCK_AND_HOME -> "Lock and home screens"
}
