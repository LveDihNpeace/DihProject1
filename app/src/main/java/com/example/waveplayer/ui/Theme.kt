package com.example.waveplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ThemeMode

private val Sans = FontFamily.SansSerif

private val WaveTypography = Typography(
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, letterSpacing = (-0.8).sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val WaveShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

/** [artAccent] is read lazily and only in Dynamic mode, so artwork changes do not recompose the whole app otherwise. */
@Composable
fun WaveTheme(settings: AppSettings, artAccent: () -> Int?, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val mode = settings.themeMode
    val target = if (mode == ThemeMode.DYNAMIC) (artAccent() ?: settings.accent) else settings.accent
    val accent by animateColorAsState(Color(target), tween(700), label = "accent")
    val onAccent = if (accent.luminance() > 0.5f) Color.Black else Color.White
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        else -> true
    }

    var scheme = if (dark) darkColorScheme(primary = accent, onPrimary = onAccent, secondary = accent)
    else lightColorScheme(primary = accent, onPrimary = onAccent, secondary = accent)

    if (dark) {
        scheme = scheme.copy(
            background = Color(0xFF0E0C14), surface = Color(0xFF0E0C14), surfaceVariant = Color(0xFF1D1927),
            onSurfaceVariant = Color(0xFFB9B3C9),
        )
    }
    if (mode == ThemeMode.AMOLED) {
        scheme = scheme.copy(background = Color.Black, surface = Color.Black, surfaceVariant = Color(0xFF161616))
    }
    if (settings.background != 0) {
        val bg = Color(settings.background)
        scheme = scheme.copy(background = bg, surface = bg)
    }
    MaterialTheme(colorScheme = scheme, typography = WaveTypography, shapes = WaveShapes, content = content)
}
