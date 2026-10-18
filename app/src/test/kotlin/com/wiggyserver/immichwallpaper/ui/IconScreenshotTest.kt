package com.wiggyserver.immichwallpaper.ui

import android.app.Application
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.wiggyserver.immichwallpaper.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The launcher icon (full-bleed, round, themed) and the status-bar icon, as Android draws the vectors. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], application = Application::class, qualifiers = "w600dp-h200dp-420dpi")
class IconScreenshotTest {
    @Test
    fun icons() = captureRoboImage("src/test/screenshots/icons.png") {
        Row(Modifier.background(Color(0xFF8A8F99)).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Layered(Modifier.clip(RoundedCornerShape(28.dp)))
            Layered(Modifier.clip(CircleShape))
            // Themed icon (Android 13+): the system tints the monochrome layer on a tonal background.
            Box(Modifier.size(108.dp).clip(CircleShape).background(Color(0xFFDDE1FF))) {
                Image(painterResource(R.drawable.ic_launcher_monochrome), null, Modifier.size(108.dp), colorFilter = ColorFilter.tint(Color(0xFF2638A0)))
            }
            Image(painterResource(R.drawable.ic_stat_wallpaper), null, Modifier.size(24.dp), colorFilter = ColorFilter.tint(Color.White))
            Image(painterResource(R.drawable.ic_stat_wallpaper), null, Modifier.size(48.dp), colorFilter = ColorFilter.tint(Color.White))
        }
    }

    @androidx.compose.runtime.Composable
    private fun Layered(shape: Modifier) {
        Box(Modifier.size(108.dp).then(shape)) {
            Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.size(108.dp))
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(108.dp))
        }
    }
}
