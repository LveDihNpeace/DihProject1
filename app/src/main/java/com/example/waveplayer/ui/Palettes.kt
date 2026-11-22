package com.example.waveplayer.ui

import androidx.compose.ui.graphics.Color
import com.example.waveplayer.data.SettingsRepository
import kotlin.math.pow

/**
 * Design tokens.
 * Approved dark values: Charcoal, Graphite, GraphiteRaised, Muted Sage.
 * Derived values (checked for WCAG contrast): text and outline colors, the light palette, accent adjustments.
 */
object WavePalette {
    // Dark: AMOLED Charcoal background, Dark Graphite surfaces
    val Black = Color(0xFF000000)
    val Charcoal = Color(0xFF0B0C0D)        // background
    val Graphite = Color(0xFF17191C)        // surface
    val GraphiteRaised = Color(0xFF212428)  // secondary surface
    val OnDark = Color(0xFFE8EAEC)
    val OnDarkVariant = Color(0xFFA9AEB4)
    val OutlineDark = Color(0xFF6B727A)     // >= 3:1 on every dark surface (UI component boundaries)
    val OutlineVariantDark = Color(0xFF2F3338) // decorative dividers only

    // Light
    val LightBackground = Color(0xFFF7F8F6)
    val LightSurface = Color(0xFFFFFFFF)
    val LightRaised = Color(0xFFE3E7E2)
    val OnLight = Color(0xFF15181A)
    val OnLightVariant = Color(0xFF444A4F)
    val OutlineLight = Color(0xFF70777C)
    val OutlineVariantLight = Color(0xFFC4C9C4)
}

class AccentPreset(val name: String, val color: Int)

/** Accent choices shown in Settings > Appearance. Muted Sage is the default (see SettingsRepository.DEFAULT_ACCENT). */
object Accents {
    val presets: List<AccentPreset> = listOf(
        AccentPreset("Muted Sage", SettingsRepository.DEFAULT_ACCENT),
        AccentPreset("Dusty Blue", 0xFF8AA4C8.toInt()),
        AccentPreset("Warm Sand", 0xFFC8B07A.toInt()),
        AccentPreset("Clay Rose", 0xFFC98F8B.toInt()),
        AccentPreset("Soft Lavender", 0xFFA394C9.toInt()),
        AccentPreset("Sea Teal", 0xFF7DB3AC.toInt()),
    )
}

// ---- WCAG 2.x contrast helpers (own implementation so results are identical on every device) ----

private fun channel(c: Float): Float = if (c <= 0.03928f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)

fun Color.wcagLuminance(): Float = 0.2126f * channel(red) + 0.7152f * channel(green) + 0.0722f * channel(blue)

fun contrastRatio(a: Color, b: Color): Float {
    val la = a.wcagLuminance()
    val lb = b.wcagLuminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Linear mix in sRGB component space (t = 0 -> a, t = 1 -> b). */
fun blend(a: Color, b: Color, t: Float): Color =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/**
 * Keeps the hue of [color] but darkens or lightens it until it reaches [min]:1 contrast on EVERY color in [against].
 * Returns [color] unchanged when it already passes (all dark-theme presets do).
 */
fun ensureContrast(color: Color, min: Float, vararg against: Color): Color {
    if (against.all { contrastRatio(color, it) >= min }) return color
    val avg = against.map { it.wcagLuminance() }.average().toFloat()
    val target = if (avg > 0.5f) Color.Black else Color.White
    for (i in 1..50) {
        val m = blend(color, target, i * 0.02f)
        if (against.all { contrastRatio(m, it) >= min }) return m
    }
    return target
}

/** Black or white text, whichever reads better on [background]. */
fun readableOn(background: Color): Color =
    if (contrastRatio(Color.Black, background) >= contrastRatio(Color.White, background)) Color.Black else Color.White
