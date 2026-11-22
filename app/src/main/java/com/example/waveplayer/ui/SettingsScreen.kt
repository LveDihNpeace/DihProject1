@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ArtShape
import com.example.waveplayer.data.ListStyle
import com.example.waveplayer.data.LyricsPosition
import com.example.waveplayer.data.ThemeMode
import com.example.waveplayer.viewmodel.LibraryViewModel
import com.example.waveplayer.viewmodel.SettingsViewModel

private val accents = Accents.presets.map { it.color }
private val backgrounds = listOf(0L, 0xFF000000, 0xFF101418, 0xFF14102A, 0xFF1F1A24, 0xFFF4EFE6).map { it.toInt() }

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange, enabled = enabled)
    }
}

@Composable
private fun ColorDots(colors: List<Int>, selected: Int, onPick: (Int) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { c ->
            val isDefault = c == 0
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (isDefault) MaterialTheme.colorScheme.surfaceVariant else Color(c))
                    .border(
                        BorderStroke(if (c == selected) 3.dp else 1.dp, if (c == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline),
                        CircleShape,
                    )
                    .clickable { onPick(c) },
                contentAlignment = Alignment.Center,
            ) { if (isDefault) Text("A", style = MaterialTheme.typography.labelLarge) }
        }
    }
}

@Composable
fun SettingsScreen(settings: AppSettings, vm: SettingsViewModel, library: LibraryViewModel) {
    // Scan progress is collected here only, so scanning never recomposes the song lists.
    val scan by library.scan.collectAsStateWithLifecycle()
    val lib by library.library.collectAsStateWithLifecycle()
    var showEq by remember { mutableStateOf(false) }
    if (showEq) EqualizerSheet { showEq = false }
    val blurSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        ScreenHeader("Settings")

        Section("Pemindai musik") { ScanCard(scan, lib.songs.size, library::scan) }

        Text("Appearance", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
        Section("Tema") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m -> FilterChip(settings.themeMode == m, { vm.setTheme(m) }, { Text(m.label) }) }
            }
        }
        Section("Warna aksen") { ColorDots(accents, settings.accent) { vm.setAccent(it) } }
        Section("Warna background (A = default tema)") { ColorDots(backgrounds, settings.background) { vm.setBackground(it) } }
        Section("Album art di pemutar") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtShape.values().forEach { s -> FilterChip(settings.artShape == s, { vm.setArtShape(s) }, { Text(s.label) }) }
            }
        }
        Section("Tampilan daftar lagu") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListStyle.values().forEach { s -> FilterChip(settings.listStyle == s, { vm.setListStyle(s) }, { Text(s.label) }) }
            }
        }
        Section(if (blurSupported) "Blur background pemutar" else "Blur background pemutar (butuh Android 12+)") {
            Slider(settings.blur, { vm.setBlur(it) }, valueRange = 0f..1f, enabled = blurSupported)
        }

        Section("Lirik") {
            Text("Posisi lirik", style = MaterialTheme.typography.bodyLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LyricsPosition.values().forEach { p -> FilterChip(settings.lyricsPosition == p, { vm.setLyricsPosition(p) }, { Text(p.label) }) }
            }
            Spacer(Modifier.height(8.dp))
            SwitchRow("Auto scroll lirik", "Lirik mengikuti lagu secara otomatis", settings.lyricsAutoScroll) { vm.setLyricsAutoScroll(it) }
            SwitchRow(
                "Highlight kata demi kata", "Hanya untuk lirik yang punya timing per kata (atau yang Anda atur sendiri)",
                settings.wordHighlight,
            ) { vm.setWordHighlight(it) }
            SwitchRow(
                "Efek blur lirik", if (blurSupported) "Baris di luar baris aktif diburamkan" else "Butuh Android 12+",
                settings.lyricBlur && blurSupported, enabled = blurSupported,
            ) { vm.setLyricBlur(it) }
            Text("Intensitas blur", style = MaterialTheme.typography.bodyMedium)
            Slider(settings.lyricBlurIntensity, { vm.setLyricBlurIntensity(it) }, valueRange = 0f..1f,
                enabled = blurSupported && settings.lyricBlur)
            Text("Ukuran teks lirik", style = MaterialTheme.typography.bodyMedium)
            Slider(settings.lyricSize, { vm.setLyricSize(it) }, valueRange = 16f..40f)
            Text("Contoh baris lirik", fontSize = settings.lyricSize.sp)
        }

        Section("Equalizer") { FilledTonalButton({ showEq = true }) { Text("Buka Equalizer") } }
        Spacer(Modifier.height(24.dp))
    }
}
