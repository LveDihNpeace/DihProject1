@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.audio.EqEngine
import kotlin.math.roundToInt

private fun freqLabel(hz: Int) = if (hz >= 1000) "%.1f kHz".format(hz / 1000f) else "$hz Hz"
private fun dbLabel(mb: Int) = "%+.1f dB".format(mb / 100f)

@Composable
fun EqualizerSheet(onDismiss: () -> Unit) {
    val st by EqEngine.state.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Equalizer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Switch(st.enabled, { EqEngine.setEnabled(it) }, enabled = st.available)
            }
            if (!st.available) {
                Text(
                    "Equalizer belum tersedia. Pastikan layanan pemutar aktif (putar sebuah lagu), " +
                        "atau perangkat ini tidak mendukung efek Equalizer.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                return@Column
            }

            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqEngine.presetNames.forEach { name ->
                    FilterChip(st.preset == name, { EqEngine.applyPreset(name) }, { Text(name) }, enabled = st.enabled)
                }
                if (st.preset == "Custom") FilterChip(true, {}, { Text("Custom") }, enabled = false)
            }

            st.bandFreqsHz.forEachIndexed { i, hz ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(freqLabel(hz), Modifier.width(72.dp), style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = st.levels[i].toFloat(),
                        onValueChange = { EqEngine.setBand(i, it.roundToInt()) },
                        valueRange = st.minLevel.toFloat()..st.maxLevel.toFloat(),
                        enabled = st.enabled,
                        modifier = Modifier.weight(1f),
                    )
                    Text(dbLabel(st.levels[i]), Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                }
            }

            Spacer(Modifier.height(8.dp))
            if (st.bassSupported) {
                Text("Bass Boost: ${st.bass / 10}%")
                Slider(st.bass.toFloat(), { EqEngine.setBass(it.roundToInt()) }, valueRange = 0f..1000f, enabled = st.enabled)
            } else {
                Text("Bass Boost tidak didukung perangkat ini", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (st.virtSupported) {
                Text("Virtualizer: ${st.virt / 10}%")
                Slider(st.virt.toFloat(), { EqEngine.setVirtualizer(it.roundToInt()) }, valueRange = 0f..1000f, enabled = st.enabled)
            } else {
                Text("Virtualizer tidak didukung perangkat ini", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton({ EqEngine.reset() }, enabled = st.enabled) { Text("Reset") }
        }
    }
}
