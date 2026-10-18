package com.wiggyserver.immichwallpaper.support

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/** Donation pages; blank links are hidden. */
object DonationLinks {
    const val GITHUB_SPONSORS = ""
    const val KO_FI = "https://ko-fi.com/brysonwiggins"
}

fun createSupport(activity: ComponentActivity): Support = LinkSupport(activity, DonationLinks.GITHUB_SPONSORS, DonationLinks.KO_FI)

class LinkSupport(private val activity: ComponentActivity, private val sponsors: String, private val koFi: String) : Support {
    @Composable
    override fun Section() {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "This app is free and open source. If it brightens your day, you can support its development.",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (sponsors.isBlank() && koFi.isBlank()) {
                Text("Donation links are coming soon.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sponsors.isNotBlank()) OutlinedButton(onClick = { open(sponsors) }) { Text("GitHub Sponsors") }
                    if (koFi.isNotBlank()) OutlinedButton(onClick = { open(koFi) }) { Text("Ko-fi") }
                }
            }
        }
    }

    private fun open(url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
