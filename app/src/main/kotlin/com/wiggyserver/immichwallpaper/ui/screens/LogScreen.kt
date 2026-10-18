package com.wiggyserver.immichwallpaper.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wiggyserver.immichwallpaper.ui.AppIcons
import com.wiggyserver.immichwallpaper.ui.ScreenScaffold

/** Newest first; Share sends the whole log as text (for bug reports). */
@Composable
fun LogScreen(lines: List<String>, onShare: () -> Unit, onBack: () -> Unit) {
    ScreenScaffold(
        "Diagnostics log",
        onBack = onBack,
        actions = { IconButton(onClick = onShare) { Icon(AppIcons.Share, contentDescription = "Share log") } },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) {
            items(lines.asReversed()) { line ->
                Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
