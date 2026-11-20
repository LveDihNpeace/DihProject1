package com.example.waveplayer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import coil.compose.AsyncImage

private fun fmt(ms: Long): String {
    val s = (ms / 1000).toInt()
    return "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Art(model: Any?, size: Int, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.MusicNote, null, tint = MaterialTheme.colorScheme.outline)
        AsyncImage(model, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

@Composable
private fun MediaRow(art: Any?, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Art(art, 52)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun LibraryScreen(vm: PlayerViewModel, modifier: Modifier = Modifier) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }
    val titles = listOf("Songs", "Albums", "Artists")

    Column(modifier.fillMaxSize().statusBarsPadding()) {
        Text("WavePlayer", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp))
        TabRow(selectedTabIndex = tab) {
            titles.forEachIndexed { i, t -> Tab(tab == i, { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> LazyColumn {
                itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
                    MediaRow(s.artUri, s.title, "${s.artist} • ${fmt(s.durationMs)}") { vm.play(songs, i) }
                }
            }
            1 -> {
                val albums = remember(songs) { songs.groupBy { it.albumId }.values.toList() }
                LazyColumn {
                    itemsIndexed(albums) { _, a ->
                        MediaRow(a[0].artUri, a[0].album, "${a[0].artist} • ${a.size} songs") { vm.play(a, 0) }
                    }
                }
            }
            else -> {
                val artists = remember(songs) { songs.groupBy { it.artist }.values.toList() }
                LazyColumn {
                    itemsIndexed(artists) { _, a ->
                        MediaRow(a[0].artUri, a[0].artist, "${a.size} songs") { vm.play(a, 0) }
                    }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val item by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val md = item?.mediaMetadata ?: return

    Surface(tonalElevation = 3.dp, modifier = Modifier.navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Art(md.artworkUri, 44)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(md.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    md.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            IconButton(onClick = vm::togglePlay) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = vm::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

@Composable
fun NowPlayingScreen(vm: PlayerViewModel, onClose: () -> Unit) {
    val item by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val dur by vm.duration.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeat.collectAsStateWithLifecycle()
    val md = item?.mediaMetadata
    var drag by remember { mutableStateOf<Float?>(null) }

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.statusBarsPadding().navigationBarsPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth()) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Close") }
            }
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.outline)
                AsyncImage(md?.artworkUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Text(
                md?.title?.toString() ?: "", style = MaterialTheme.typography.headlineSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 24.dp),
            )
            Text(
                md?.artist?.toString() ?: "", style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            )

            Slider(
                value = drag ?: pos.toFloat(),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
                valueRange = 0f..maxOf(dur, 1L).toFloat(),
                modifier = Modifier.padding(top = 16.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((drag ?: pos.toFloat()).toLong()), style = MaterialTheme.typography.labelMedium)
                Text(fmt(dur), style = MaterialTheme.typography.labelMedium)
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(vm::toggleShuffle) {
                    Icon(
                        Icons.Default.Shuffle, "Shuffle",
                        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(vm::previous) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp)) }
                FilledIconButton(vm::togglePlay, Modifier.size(72.dp)) {
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(40.dp))
                }
                IconButton(vm::next) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp)) }
                IconButton(vm::cycleRepeat) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
