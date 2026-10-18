package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontFamily
import com.wiggyserver.immichwallpaper.data.REQUIRED_PERMISSIONS
import com.wiggyserver.immichwallpaper.ui.Group
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.presentation.ServerState
import com.wiggyserver.immichwallpaper.presentation.ServerStatus
import com.wiggyserver.immichwallpaper.ui.AppIcons
import com.wiggyserver.immichwallpaper.ui.FormColumn
import com.wiggyserver.immichwallpaper.ui.NoticeCard
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold
import com.wiggyserver.immichwallpaper.ui.Tone

class ServerActions(val setUrl: (String) -> Unit, val setKey: (String) -> Unit, val test: () -> Unit, val allowLocalNetwork: () -> Unit)

@Composable
fun ServerScreen(state: ServerState, actions: ServerActions, onBack: () -> Unit) {
    ScreenScaffold("Immich server", onBack = onBack) { padding -> FormColumn(padding) { ServerForm(state, actions) } }
}

/** Address + key + connect. Also the first onboarding step. */
@Composable
fun ColumnScope.ServerForm(state: ServerState, actions: ServerActions) {
    Text(
        "Use the address you open Immich with in a browser. Away from home, the phone needs a way to reach it, such as a VPN or a reverse proxy.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = state.url,
        onValueChange = actions.setUrl,
        label = { Text("Server address") },
        placeholder = { Text("https://photos.example.com") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    ApiKeyHelp(expandedAtFirst = !state.hasSavedKey)
    OutlinedTextField(
        value = state.key,
        onValueChange = actions.setKey,
        label = { Text(if (state.hasSavedKey) "API key (saved)" else "API key") },
        placeholder = { if (state.hasSavedKey) Text("Leave empty to keep the saved key") },
        supportingText = { Text("Stored encrypted on this phone.") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { actions.test() }),
        modifier = Modifier.fillMaxWidth(),
    )
    val testing = state.status == ServerStatus.Testing
    Button(onClick = actions.test, enabled = !testing, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (testing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Connecting…")
            } else {
                Text("Connect")
            }
        }
    }
    when (val s = state.status) {
        is ServerStatus.Connected -> NoticeCard(AppIcons.Check, "Connected", "Immich ${s.version}. The server and key are saved.", Tone.INFO)
        is ServerStatus.Failed -> NoticeCard(AppIcons.Warning, "Couldn't connect", s.message, Tone.ERROR)
        ServerStatus.NeedsLocalNetwork -> NoticeCard(
            AppIcons.Warning,
            "Allow access to your home network",
            "Your server is on your home network. Android asks for the Nearby devices permission before apps can reach it.",
            Tone.WARNING,
            "Allow",
            actions.allowLocalNetwork,
        )
        else -> Spacer(Modifier.padding(0.dp))
    }
}

/** How to make a key with only the permissions the app needs (Immich's phone app can't make keys). */
@Composable
private fun ApiKeyHelp(expandedAtFirst: Boolean) {
    var open by rememberSaveable { mutableStateOf(expandedAtFirst) }
    Group {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How to make an API key", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(if (open) AppIcons.ExpandLess else AppIcons.ExpandMore, contentDescription = if (open) "Hide steps" else "Show steps")
        }
        if (open) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                val steps = listOf(
                    "Open Immich in a web browser. The Immich phone app can't make API keys.",
                    "Go to Account Settings → API Keys → New API Key.",
                    "Name it (for example \"Wallpapers\") and, under Permissions, tick only these:",
                )
                steps.forEachIndexed { i, step -> Step(i + 1, step) }
                Text(
                    REQUIRED_PERMISSIONS.joinToString("\n"),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(start = 28.dp, top = 2.dp, bottom = 6.dp),
                )
                Step(4, "Create the key, copy it and paste it below. The app only reads: it can't change, delete or upload anything.")
            }
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("$n.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(28.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
