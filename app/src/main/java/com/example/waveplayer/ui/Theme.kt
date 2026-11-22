package com.example.waveplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
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

private fun waveDarkScheme(seed: Color, amoledBlack: Boolean): ColorScheme {
    // AMOLED Charcoal (default): Charcoal / Graphite / Graphite raised.  AMOLED Black: one step darker, true black base.
    val bg = if (amoledBlack) WavePalette.Black else WavePalette.Charcoal
    val surface = if (amoledBlack) WavePalette.Charcoal else WavePalette.Graphite
    val raised = if (amoledBlack) WavePalette.Graphite else WavePalette.GraphiteRaised
    val accent = ensureContrast(seed, 4.5f, bg, surface, raised)
    val onAccent = readableOn(accent)
    val container = blend(raised, accent, 0.28f)
    return darkColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = container, onPrimaryContainer = WavePalette.OnDark,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = container, onSecondaryContainer = WavePalette.OnDark,
        tertiary = accent, onTertiary = onAccent,
        tertiaryContainer = container, onTertiaryContainer = WavePalette.OnDark,
        background = bg, onBackground = WavePalette.OnDark,
        surface = surface, onSurface = WavePalette.OnDark,
        surfaceVariant = raised, onSurfaceVariant = WavePalette.OnDarkVariant,
        surfaceTint = accent,
        outline = WavePalette.OutlineDark, outlineVariant = WavePalette.OutlineVariantDark,
        surfaceDim = bg, surfaceBright = raised,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
    )
}

private fun waveLightScheme(seed: Color): ColorScheme {
    val bg = WavePalette.LightBackground
    val surface = WavePalette.LightSurface
    val raised = WavePalette.LightRaised
    // The accent is darkened (same hue) only as far as needed to read on every light surface.
    val accent = ensureContrast(seed, 4.5f, bg, surface, raised)
    val onAccent = readableOn(accent)
    val container = blend(raised, accent, 0.16f)
    return lightColorScheme(
        primary = accent, onPrimary = onAccent,
        primaryContainer = container, onPrimaryContainer = WavePalette.OnLight,
        secondary = accent, onSecondary = onAccent,
        secondaryContainer = container, onSecondaryContainer = WavePalette.OnLight,
        tertiary = accent, onTertiary = onAccent,
        tertiaryContainer = container, onTertiaryContainer = WavePalette.OnLight,
        background = bg, onBackground = WavePalette.OnLight,
        surface = surface, onSurface = WavePalette.OnLight,
        surfaceVariant = raised, onSurfaceVariant = WavePalette.OnLightVariant,
        surfaceTint = accent,
        outline = WavePalette.OutlineLight, outlineVariant = WavePalette.OutlineVariantLight,
        surfaceDim = bg, surfaceBright = surface,
        surfaceContainerLowest = bg, surfaceContainerLow = surface, surfaceContainer = surface,
        surfaceContainerHigh = raised, surfaceContainerHighest = raised,
    )
}

/** [artAccent] is read lazily and only in Dynamic mode, so artwork changes do not recompose the whole app otherwise. */
@Composable
fun WaveTheme(settings: AppSettings, artAccent: () -> Int?, content: @Composable () -> Unit) {
    val systemDark = isSystemInDarkTheme()
    val mode = settings.themeMode
    val target = if (mode == ThemeMode.DYNAMIC) (artAccent() ?: settings.accent) else settings.accent
    val seed by animateColorAsState(Color(target), Motion.ambient(), label = "accent")
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> systemDark
        else -> true
    }

    var scheme = if (dark) waveDarkScheme(seed, amoledBlack = mode == ThemeMode.AMOLED) else waveLightScheme(seed)
    if (settings.background != 0) {
        val bg = Color(settings.background)
        scheme = scheme.copy(background = bg, surface = bg)
    }
    MaterialTheme(colorScheme = scheme, typography = WaveTypography, shapes = WaveShapes, content = content)
}
