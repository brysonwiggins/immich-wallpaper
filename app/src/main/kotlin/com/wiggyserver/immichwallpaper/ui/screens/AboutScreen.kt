package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import com.wiggyserver.immichwallpaper.AppInfo
import com.wiggyserver.immichwallpaper.ui.FormColumn
import com.wiggyserver.immichwallpaper.ui.Group
import com.wiggyserver.immichwallpaper.ui.ListRow
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold
import com.wiggyserver.immichwallpaper.ui.SectionTitle

/** Name, version, the not-affiliated notice, license, privacy and the flavor's support section. */
@Composable
fun AboutScreen(version: String, openUrl: (String) -> Unit, onBack: () -> Unit, support: @Composable () -> Unit) {
    ScreenScaffold("About", onBack = onBack) { padding ->
        FormColumn(padding) {
            Text(AppInfo.NAME, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
            Text("Version $version", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(AppInfo.DISCLAIMER, style = MaterialTheme.typography.bodyMedium)

            SectionTitle("Support development")
            Group { androidx.compose.foundation.layout.Box(Modifier.padding(16.dp)) { support() } }

            SectionTitle("Your privacy")
            Group {
                ListRow(
                    "Nothing leaves your phone",
                    "The app talks only to your own Immich server. No ads, analytics or tracking.",
                    onClick = { openUrl(AppInfo.PRIVACY_URL) },
                )
            }

            SectionTitle("Open source")
            Group {
                ListRow("Source code", "Free software under the GNU GPL v3", onClick = { openUrl(AppInfo.SOURCE_URL) })
            }
        }
    }
}
