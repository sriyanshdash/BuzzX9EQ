package dev.sriyansh.buzzx9.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Accent = Color(0xFF7DF9C4)
private val AccentAlt = Color(0xFF4FD1FF)

private val DarkColors = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF00382A),
    secondary = AccentAlt,
    onSecondary = Color(0xFF00344A),
    background = Color(0xFF0B0E14),
    onBackground = Color(0xFFE3E6EC),
    surface = Color(0xFF131822),
    onSurface = Color(0xFFE3E6EC),
    surfaceVariant = Color(0xFF1C2330),
    onSurfaceVariant = Color(0xFFA9B2C1),
    outline = Color(0xFF39424F),
    error = Color(0xFFFF6B6B)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF00695C),
    secondary = Color(0xFF00639B),
    background = Color(0xFFF7F9FC),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun BuzzTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
