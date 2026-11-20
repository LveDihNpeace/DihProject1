@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.waveplayer.PlayerViewModel
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.Song
import com.example.waveplayer.viewmodel.ScanState

@Composable
fun HomeScreen(
    songs: List<Song>, favorites: Set<Long>, settings: AppSettings,
    vm: PlayerViewModel, onFav: (Long) -> Unit, onNavigate: (Screen) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize().statusBarsPadding()) {
        item {
            Row(Modifier.padding(start = 16.dp, top = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("WavePlayer", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
                IconButton({ onNavigate(Screen.FAVORITES) }) { Icon(Icons.Default.Favorite, "Favorit") }
                IconButton({ onNavigate(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, "Pengaturan") }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Button({ vm.play(songs.shuffled(), 0) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Putar acak")
                }
                FilledTonalButton({ vm.play(songs, 0) }, enabled = songs.isNotEmpty()) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Putar semua")
                }
                Text("${songs.size} lagu", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (songs.isEmpty()) {
            item {
                Text(
                    "Belum ada lagu. Buka Settings lalu ketuk \"Pindai ulang\".",
                    modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            SongRow(s, settings.listStyle, s.id in favorites, { onFav(s.id) }) { vm.play(songs, i) }
        }
    }
}

private enum class FilterType(val label: String, val key: (Song) -> String) {
    ALL("Semua", { "" }),
    ARTIST("Artis", { it.artist }),
    ALBUM("Album", { it.album }),
    GENRE("Genre", { it.genre }),
    FOLDER("Folder", { it.folder }),
}

@Composable
fun SongsScreen(
    songs: List<Song>, favorites: Set<Long>, settings: AppSettings,
    vm: PlayerViewModel, onFav: (Long) -> Unit,
) {
    var type by remember { mutableStateOf(FilterType.ALL) }
    var value by remember { mutableStateOf<String?>(null) }
    val options = remember(songs, type) {
        if (type == FilterType.ALL) emptyList() else songs.map(type.key).distinct().sorted()
    }
    val shown = remember(songs, type, value) {
        if (type == FilterType.ALL || value == null) songs else songs.filter { type.key(it) == value }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Songs", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FilterType.values().toList()) { t ->
                FilterChip(selected = type == t, onClick = { type = t; value = null }, label = { Text(t.label) })
            }
        }
        if (options.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options) { o ->
                    FilterChip(selected = value == o, onClick = { value = if (value == o) null else o }, label = { Text(o) })
                }
            }
        }
        Text("${shown.size} lagu", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp, 4.dp))
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(shown, key = { _, s -> s.id }) { i, s ->
                SongRow(s, settings.listStyle, s.id in favorites, { onFav(s.id) }) { vm.play(shown, i) }
            }
        }
    }
}

@Composable
fun GroupsScreen(
    title: String, groups: List<Pair<String, List<Song>>>, subtitle: (List<Song>) -> String,
    settings: AppSettings, onPlay: (List<Song>) -> Unit,
) {
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(groups, key = { it.first + it.second.first().id }) { (name, list) ->
                GroupRow(list.first().artUri, name, subtitle(list), settings.listStyle) { onPlay(list) }
            }
        }
    }
}

@Composable
fun FavoritesScreen(
    songs: List<Song>, favorites: Set<Long>, settings: AppSettings,
    vm: PlayerViewModel, onFav: (Long) -> Unit,
) {
    val favSongs = remember(songs, favorites) { songs.filter { it.id in favorites } }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Favorites", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        if (favSongs.isEmpty()) {
            Text("Belum ada favorit. Ketuk ikon hati pada lagu.", modifier = Modifier.padding(16.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f)) {
            itemsIndexed(favSongs, key = { _, s -> s.id }) { i, s ->
                SongRow(s, settings.listStyle, true, { onFav(s.id) }) { vm.play(favSongs, i) }
            }
        }
    }
}
