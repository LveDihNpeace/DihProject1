@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.Group
import com.example.waveplayer.data.ListStyle
import com.example.waveplayer.data.Song

typealias PlayAction = (List<Song>, Int) -> Unit

/** Song rows with stable keys and content types; [play] is remembered by the caller so rows can be skipped. */
private fun LazyListScope.songItems(
    songs: List<Song>, favorites: Set<Long>, style: ListStyle,
    currentId: State<Long?>, play: (Int) -> Unit, onFav: (Long) -> Unit,
) {
    itemsIndexed(songs, key = { _, s -> s.id }, contentType = { _, _ -> "song" }) { i, s ->
        SongRow(s, i, style, s.id in favorites, currentId, play, onFav)
    }
}

/**
 * Shuffle / Play All for the Songs page. Always acts on the list it is given, so it follows the active filter.
 * Playback goes through [onPlay], exactly as before (play the list from index 0; shuffle = shuffled copy).
 */
@Composable
private fun PlayActionsRow(songs: List<Song>, onPlay: PlayAction) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            { onPlay(songs.shuffled(), 0) }, enabled = songs.isNotEmpty(),
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Shuffle")
        }
        FilledTonalButton(
            { onPlay(songs, 0) }, enabled = songs.isNotEmpty(),
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Text("Play All")
        }
    }
}

@Composable
fun HomeScreen(
    songs: List<Song>, favorites: Set<Long>, currentId: State<Long?>, settings: AppSettings,
    listState: LazyListState, onPlay: PlayAction, onFav: (Long) -> Unit, onNavigate: (Screen) -> Unit,
) {
    val play = remember(songs, onPlay) { { i: Int -> onPlay(songs, i) } }
    LazyColumn(Modifier.fillMaxSize(), state = listState) {
        item(key = "header", contentType = "header") {
            ScreenHeader("WavePlayer", "${songs.size} lagu") {
                IconButton({ onNavigate(Screen.RECENT) }) { Icon(Icons.Default.History, "Terakhir diputar") }
                IconButton({ onNavigate(Screen.FAVORITES) }) { Icon(Icons.Default.Favorite, "Favorit") }
                IconButton({ onNavigate(Screen.SETTINGS) }) { Icon(Icons.Default.Settings, "Pengaturan") }
            }
        }
        if (songs.isEmpty()) {
            item(key = "empty") {
                Text(
                    "Belum ada lagu. Buka Settings lalu ketuk \"Pindai ulang\".",
                    modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        songItems(songs, favorites, settings.listStyle, currentId, play, onFav)
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
    songs: List<Song>, favorites: Set<Long>, currentId: State<Long?>, settings: AppSettings,
    listState: LazyListState, onPlay: PlayAction, onFav: (Long) -> Unit,
) {
    var type by remember { mutableStateOf(FilterType.ALL) }
    var value by remember { mutableStateOf<String?>(null) }
    val options = remember(songs, type) {
        if (type == FilterType.ALL) emptyList() else songs.map(type.key).distinct().sorted()
    }
    val shown = remember(songs, type, value) {
        if (type == FilterType.ALL || value == null) songs else songs.filter { type.key(it) == value }
    }
    val play = remember(shown, onPlay) { { i: Int -> onPlay(shown, i) } }
    Column(Modifier.fillMaxSize()) {
        PageInfo("${shown.size} lagu")
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(FilterType.values().toList(), key = { it.name }) { t ->
                FilterChip(selected = type == t, onClick = { type = t; value = null }, label = { Text(t.label) })
            }
        }
        if (options.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(16.dp, 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(options, key = { it }) { o ->
                    FilterChip(selected = value == o, onClick = { value = if (value == o) null else o }, label = { Text(o) })
                }
            }
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            item(key = "actions", contentType = "actions") { PlayActionsRow(shown, onPlay) }
            songItems(shown, favorites, settings.listStyle, currentId, play, onFav)
        }
    }
}

@Composable
fun GroupsScreen(
    title: String, groups: List<Group>, showArtist: Boolean, settings: AppSettings,
    listState: LazyListState, onPlayGroup: (Group) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title, "${groups.size} item")
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(groups, key = { it.key }, contentType = { "group" }) { g ->
                GroupRow(g, settings.listStyle, showArtist, onPlayGroup)
            }
        }
    }
}

@Composable
fun SongListScreen(
    emptyText: String, songs: List<Song>, favorites: Set<Long>, currentId: State<Long?>,
    settings: AppSettings, listState: LazyListState, onPlay: PlayAction, onFav: (Long) -> Unit,
) {
    val play = remember(songs, onPlay) { { i: Int -> onPlay(songs, i) } }
    Column(Modifier.fillMaxSize()) {
        PageInfo("${songs.size} lagu")
        if (songs.isEmpty()) {
            Text(emptyText, modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f), state = listState) {
            songItems(songs, favorites, settings.listStyle, currentId, play, onFav)
        }
    }
}
