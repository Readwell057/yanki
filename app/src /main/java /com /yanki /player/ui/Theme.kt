package com.yanki.player.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Cyan = Color(0xFF4FD8E8)
val Violet = Color(0xFF9B8CFF)
val Bg = Color(0xFF0A0F1E)
val Surf = Color(0xFF121A2E)
val SurfHi = Color(0xFF1B2540)

private val Scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF00363D),
    primaryContainer = Color(0xFF004F58),
    onPrimaryContainer = Color(0xFF9CF0FF),
    secondary = Violet,
    onSecondary = Color(0xFF1E1470),
    background = Bg,
    onBackground = Color(0xFFE3E8F5),
    surface = Surf,
    onSurface = Color(0xFFE3E8F5),
    surfaceVariant = SurfHi,
    onSurfaceVariant = Color(0xFFA9B4D0),
    surfaceContainer = Surf,
    surfaceContainerHigh = SurfHi,
    surfaceContainerHighest = SurfHi,
    outline = Color(0xFF47536F)
)

@Composable
fun YankiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
