package com.wiggyserver.immichwallpaper.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Colors from the app icon (art/icon/README.md): navy #151A33, lavender #EDEAFF, indigo #2E3A70,
// violet #7B6CF6, gold #FFD27A. Where a brand color fails text contrast it is deepened toward the
// navy (light primary #675CCF: 5.2:1 with white) or lightened (dark primary #B0A7FA: 8:1 on navy).
// Every text/background pair here is at least 4.5:1.
//
// Fixed colors on purpose: dynamic color would follow the wallpaper, which this app changes at
// every screen-off, so the app's own colors would shift each time it is opened.
private val Navy = Color(0xFF151A33)
private val Lavender = Color(0xFFEDEAFF)
private val Indigo = Color(0xFF2E3A70)
private val Gold = Color(0xFFFFD27A)

private val Light = lightColorScheme(
    primary = Color(0xFF675CCF),
    onPrimary = Color.White,
    primaryContainer = Lavender,
    onPrimaryContainer = Navy,
    inversePrimary = Color(0xFFB0A7FA),
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE0F7),
    onSecondaryContainer = Navy,
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFE6B6),
    onTertiaryContainer = Navy,
    error = Color(0xFFBA1A1A),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFBFAFF),
    onBackground = Navy,
    surface = Color(0xFFFBFAFF),
    onSurface = Navy,
    surfaceVariant = Color(0xFFE6E3F7),
    onSurfaceVariant = Color(0xFF4A4E6A),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF6F4FF),
    surfaceContainer = Color(0xFFF1EEFF),
    surfaceContainerHigh = Lavender,
    surfaceContainerHighest = Color(0xFFE5E1FA),
    inverseSurface = Navy,
    inverseOnSurface = Lavender,
    outline = Color(0xFF777B96),
    outlineVariant = Color(0xFFCBC8E0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB0A7FA),
    onPrimary = Navy,
    primaryContainer = Color(0xFF524BA8),
    onPrimaryContainer = Lavender,
    inversePrimary = Color(0xFF675CCF),
    secondary = Color(0xFFCBCEDB),
    onSecondary = Navy,
    secondaryContainer = Indigo,
    onSecondaryContainer = Lavender,
    tertiary = Gold,
    onTertiary = Navy,
    tertiaryContainer = Color(0xFF5A4300),
    onTertiaryContainer = Color(0xFFFFE6B6),
    error = Color(0xFFFFB4AB),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Navy,
    onBackground = Lavender,
    surface = Navy,
    onSurface = Lavender,
    surfaceVariant = Indigo,
    onSurfaceVariant = Color(0xFFC3C2DD),
    surfaceContainerLowest = Color(0xFF0F1328),
    surfaceContainerLow = Color(0xFF1A1F3D),
    surfaceContainer = Color(0xFF1F2547),
    surfaceContainerHigh = Color(0xFF262D55),
    surfaceContainerHighest = Indigo,
    inverseSurface = Lavender,
    inverseOnSurface = Navy,
    outline = Color(0xFF8D8FAD),
    outlineVariant = Color(0xFF3B4370),
)

@Composable
fun ImmichWallTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
}
