package com.example.waveplayer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.PlayerViewModel

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val item by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    // States (not values): progress is drawn in the draw phase, so it never triggers recomposition.
    val pos = vm.position.collectAsStateWithLifecycle()
    val dur = vm.duration.collectAsStateWithLifecycle()
    val md = item?.mediaMetadata ?: return
    val accent = MaterialTheme.colorScheme.primary

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 8.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp).fillMaxWidth(),
    ) {
        Row(
            Modifier.clickable(onClick = onOpen)
                .drawBehind {
                    val d = dur.value
                    if (d > 0) {
                        val f = (pos.value.toFloat() / d).coerceIn(0f, 1f)
                        val h = 3.dp.toPx()
                        drawRect(accent, Offset(0f, size.height - h), Size(size.width * f, h))
                    }
                }
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(md.artworkUri, 46.dp, RoundedCornerShape(14.dp))
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(md.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(
                    md.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(vm::togglePlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause") }
            IconButton(vm::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

private val navItems: List<Triple<Screen, String, ImageVector>> = listOf(
    Triple(Screen.HOME, "Home", Icons.Default.Home),
    Triple(Screen.SONGS, "Songs", Icons.Default.MusicNote),
    Triple(Screen.ALBUMS, "Albums", Icons.Default.Album),
    Triple(Screen.ARTISTS, "Artists", Icons.Default.Person),
    Triple(Screen.FOLDERS, "Folders", Icons.Default.Folder),
)

@Composable
fun WaveNavBar(selected: Screen, onSelect: (Screen) -> Unit) {
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 10.dp,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp).fillMaxWidth(),
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            navItems.forEach { (s, label, icon) ->
                val sel = s == selected
                val bg by animateColorAsState(if (sel) primary.copy(alpha = 0.2f) else Color.Transparent, label = "nav")
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(bg)
                        .clickable { onSelect(s) }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, label, tint = if (sel) primary else muted)
                    Text(label, style = MaterialTheme.typography.labelSmall, color = if (sel) primary else muted)
                }
            }
        }
    }
}
