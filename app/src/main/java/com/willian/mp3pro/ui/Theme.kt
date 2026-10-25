package com.willian.mp3pro.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Color(0xFF22D3EE),
    onPrimary = Color(0xFF00363D),
    secondary = Color(0xFFA78BFA),
    onSecondary = Color(0xFF2A1A5E),
    background = Color(0xFF0E1620),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF0E1620),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1B2735),
    onSurfaceVariant = Color(0xFF9FB1C3),
    surfaceContainer = Color(0xFF142030),
    surfaceContainerHigh = Color(0xFF1B2735),
    outline = Color(0xFF3A4A5C),
    error = Color(0xFFFF6B6B)
)

@Composable
fun Mp3ProTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
