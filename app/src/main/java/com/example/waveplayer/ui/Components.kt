package com.example.waveplayer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.waveplayer.data.Group
import com.example.waveplayer.data.ListStyle
import com.example.waveplayer.data.Song
import com.example.waveplayer.viewmodel.ScanState

fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}

/** Lightweight placeholder (icon on a flat surface) is drawn under the image, so there is no empty frame while loading. */
@Composable
fun Art(model: Any?, size: Dp, shape: Shape = RoundedCornerShape(14.dp), modifier: Modifier = Modifier) {
    Box(
        modifier.size(size).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, Modifier.size(size / 2), tint = MaterialTheme.colorScheme.outline)
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
fun ScreenHeader(title: String, subtitle: String? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        actions()
    }
}

/** Small info line under the shell header. The header is the only place that shows the page title. */
@Composable
fun PageInfo(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

/**
 * All parameters are stable (Immutable Song, primitives, remembered lambdas), so rows are skipped during
 * recomposition. [currentId] is a State read here, so only rows whose "playing" status matters are redrawn.
 */
@Composable
fun SongRow(
    song: Song, index: Int, style: ListStyle, favorite: Boolean,
    currentId: State<Long?>, onPlay: (Int) -> Unit, onFav: (Long) -> Unit,
) {
    val compact = style == ListStyle.COMPACT
    val isCurrent = currentId.value == song.id
    val primary = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(index) }
            .padding(start = 16.dp, end = 4.dp, top = if (compact) 3.dp else 7.dp, bottom = if (compact) 3.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(song.artUri, if (compact) 42.dp else 56.dp, RoundedCornerShape(if (compact) 10.dp else 14.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(
                song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${song.artist} • ${fmt(song.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (isCurrent) Box(Modifier.padding(end = 4.dp).size(8.dp).clip(CircleShape).background(primary))
        IconButton({ onFav(song.id) }) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorit",
                tint = if (favorite) primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun GroupRow(group: Group, style: ListStyle, showArtist: Boolean, onPlay: (Group) -> Unit) {
    val compact = style == ListStyle.COMPACT
    val first = group.songs.first()
    Row(
        Modifier.fillMaxWidth().clickable { onPlay(group) }
            .padding(horizontal = 16.dp, vertical = if (compact) 3.dp else 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(first.artUri, if (compact) 42.dp else 56.dp, RoundedCornerShape(if (compact) 10.dp else 14.dp))
        Column(Modifier.padding(start = 14.dp).weight(1f)) {
            Text(group.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                (if (showArtist) "${first.artist} • " else "") + "${group.songs.size} lagu",
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun ScanCard(scan: ScanState, songCount: Int, onScan: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
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
