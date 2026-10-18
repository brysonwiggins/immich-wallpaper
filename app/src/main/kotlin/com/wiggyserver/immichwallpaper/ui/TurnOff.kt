package com.wiggyserver.immichwallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.wiggyserver.immichwallpaper.AppInfo
import com.wiggyserver.immichwallpaper.presentation.AfterTurnOff

/** "Turn off" row: stops the app without uninstalling and asks which wallpaper to leave on. */
@Composable
fun TurnOffRow(onTurnOff: (AfterTurnOff) -> Unit) {
    var asking by rememberSaveable { mutableStateOf(false) }
    ListRow("Turn off", "Stop changing the wallpaper and choose what to leave on the screen", onClick = { asking = true }) {
        Icon(AppIcons.Power, contentDescription = null)
    }
    if (asking) {
        Dialog(onDismissRequest = { asking = false }) {
            TurnOffPanel(onCancel = { asking = false }) { asking = false; onTurnOff(it) }
        }
    }
}

/** The dialog's content (separate so screenshots can show it). */
@Composable
fun TurnOffPanel(onCancel: () -> Unit, onChoose: (AfterTurnOff) -> Unit) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.widthIn(max = 420.dp)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(AppIcons.Power, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Turn off ${AppInfo.NAME}?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text(
                "The wallpaper stops changing and nothing runs in the background. Android doesn't let apps save the wallpaper you had before, so choose what to show now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = { onChoose(AfterTurnOff.PHONE_DEFAULT) }, modifier = Modifier.fillMaxWidth()) { Text("Phone's default wallpaper") }
            OutlinedButton(onClick = { onChoose(AfterTurnOff.CHOOSE) }, modifier = Modifier.fillMaxWidth()) { Text("Choose a wallpaper…") }
            OutlinedButton(onClick = { onChoose(AfterTurnOff.KEEP_PHOTO) }, modifier = Modifier.fillMaxWidth()) { Text("Keep the current photo") }
            Text(
                "Turn it back on any time from Home. Your server, sources and offline photos stay saved.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onCancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
}
