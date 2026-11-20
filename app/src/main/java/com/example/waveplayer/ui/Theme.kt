package com.example.waveplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ThemeMode

@Composable
fun WaveTheme(settings: AppSettings, artAccent: Int?, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val mode = settings.themeMode
    val targetAccent = if (mode == ThemeMode.DYNAMIC && artAccent != null) artAccent else settings.accent
    val accent by animateColorAsState(Color(targetAccent), tween(700), label = "accent")
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        else -> true
    }

    var scheme = if (dark) darkColorScheme(primary = accent, onPrimary = onAccent, secondary = accent)
    else lightColorScheme(primary = accent, onPrimary = onAccent, secondary = accent)

    if (mode == ThemeMode.AMOLED) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF161616))
    }
    if (settings.background != 0) {
        val bg = Color(settings.background)
        scheme = scheme.copy(background = bg, surface = bg)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
