package com.example.waveplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.waveplayer.data.ListStyle
import com.example.waveplayer.data.Song
import com.example.waveplayer.viewmodel.ScanState

fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
fun Art(model: Any?, size: Dp, shape: Shape = RoundedCornerShape(10.dp), modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.outline)
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun GroupRow(art: Any?, title: String, subtitle: String, style: ListStyle, onClick: () -> Unit) {
    val compact = style == ListStyle.COMPACT
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = if (compact) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(art, if (compact) 40.dp else 54.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SongRow(song: Song, style: ListStyle, favorite: Boolean, onFav: () -> Unit, onClick: () -> Unit) {
    val compact = style == ListStyle.COMPACT
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(start = 16.dp, end = 4.dp, top = if (compact) 2.dp else 6.dp, bottom = if (compact) 2.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(song.artUri, if (compact) 40.dp else 54.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${song.artist} • ${fmt(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onFav) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorit",
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ScanCard(scan: ScanState, songCount: Int, onScan: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$songCount lagu", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (scan.message.isBlank()) "Belum dipindai" else scan.message,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onScan, enabled = !scan.isScanning) {
                    Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Pindai ulang")
                }
            }
            if (scan.isScanning) {
                Spacer(Modifier.height(12.dp))
                if (scan.total > 0) LinearProgressIndicator(
                    progress = { scan.done.toFloat() / scan.total }, modifier = Modifier.fillMaxWidth(),
                ) else LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }
    }
}
