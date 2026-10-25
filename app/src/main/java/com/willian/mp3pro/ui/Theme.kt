package com.willian.mp3pro.ui

import android.app.Activity
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.willian.mp3pro.data.AppPrefs

private val DarkScheme = darkColorScheme(
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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0E7C93),
    onPrimary = Color(0xFFFFFFFF),
    secondary = Color(0xFF6D4AD6),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFF4F7FA),
    onBackground = Color(0xFF0E1620),
    surface = Color(0xFFF4F7FA),
    onSurface = Color(0xFF0E1620),
    surfaceVariant = Color(0xFFDDE5EE),
    onSurfaceVariant = Color(0xFF4A5B6D),
    surfaceContainer = Color(0xFFE9EFF5),
    surfaceContainerHigh = Color(0xFFDDE5EE),
    outline = Color(0xFF8A9BAD),
    error = Color(0xFFC62828)
)

/** Guarda o visual escolhido (escuro ou claro) e avisa as telas quando ele muda. */
object ThemeState {
    var dark by mutableStateOf(true)
        private set

    fun init(context: Context) {
        dark = AppPrefs.darkTheme(context)
    }

    fun set(context: Context, value: Boolean) {
        dark = value
        AppPrefs.setDarkTheme(context, value)
    }
}

@Composable
fun Mp3ProTheme(content: @Composable () -> Unit) {
    val dark = ThemeState.dark
    val scheme = if (dark) DarkScheme else LightScheme

    // Barras do sistema acompanham o visual escolhido.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = scheme.background.toArgb()
                window.navigationBarColor = scheme.background.toArgb()
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !dark
                controller.isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
