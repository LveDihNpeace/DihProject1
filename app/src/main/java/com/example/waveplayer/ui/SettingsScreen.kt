@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

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
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ArtShape
import com.example.waveplayer.data.ListStyle
import com.example.waveplayer.data.ThemeMode
import com.example.waveplayer.viewmodel.ScanState
import com.example.waveplayer.viewmodel.SettingsViewModel

private val accents = listOf(0xFF7C5CFF, 0xFFFF4D6D, 0xFF2DD4BF, 0xFFFFB020, 0xFF4C9AFF, 0xFF8BC34A).map { it.toInt() }
private val backgrounds = listOf(0L, 0xFF000000, 0xFF101418, 0xFF14102A, 0xFF1F1A24, 0xFFF4EFE6).map { it.toInt() }

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(8.dp))
        content()
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
fun SettingsScreen(
    settings: AppSettings, vm: SettingsViewModel, scan: ScanState, songCount: Int, onScan: () -> Unit,
) {
    var showEq by remember { mutableStateOf(false) }
    if (showEq) EqualizerSheet { showEq = false }
    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))

        Section("Tema") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.values().forEach { m ->
                    FilterChip(settings.themeMode == m, { vm.setTheme(m) }, { Text(m.label) })
                }
            }
        }
        Section("Warna aksen") { ColorDots(accents, settings.accent) { vm.setAccent(it) } }
        Section("Warna background (0 = default tema)") { ColorDots(backgrounds, settings.background) { vm.setBackground(it) } }
        Section("Bentuk artwork pemutar") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ArtShape.values().forEach { s -> FilterChip(settings.artShape == s, { vm.setArtShape(s) }, { Text(s.label) }) }
            }
        }
        Section("Tampilan daftar lagu") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ListStyle.values().forEach { s -> FilterChip(settings.listStyle == s, { vm.setListStyle(s) }, { Text(s.label) }) }
            }
        }
        Section("Blur background pemutar (efek blur gambar: Android 12+)") {
            Slider(settings.blur, { vm.setBlur(it) }, valueRange = 0f..1f)
        }
        Section("Ukuran teks lirik") {
            Slider(settings.lyricSize, { vm.setLyricSize(it) }, valueRange = 16f..40f)
            Text("Contoh baris lirik", fontSize = settings.lyricSize.sp)
        }
        Section("Equalizer") { FilledTonalButton({ showEq = true }) { Text("Buka Equalizer") } }
        Section("Pemindai musik") { ScanCard(scan, songCount, onScan) }
        Spacer(Modifier.height(24.dp))
    }
}
