package com.example.waveplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.data.ArtPalette
import com.example.waveplayer.data.ArtworkColors
import com.example.waveplayer.data.Library
import com.example.waveplayer.data.Song
import com.example.waveplayer.ui.GroupsScreen
import com.example.waveplayer.ui.HomeScreen
import com.example.waveplayer.ui.MiniPlayer
import com.example.waveplayer.ui.Motion
import com.example.waveplayer.ui.NowPlayingScreen
import com.example.waveplayer.ui.Screen
import com.example.waveplayer.ui.SettingsScreen
import com.example.waveplayer.ui.ShortcutBus
import com.example.waveplayer.ui.SongListScreen
import com.example.waveplayer.ui.SongsScreen
import com.example.waveplayer.ui.WaveNavBar
import com.example.waveplayer.ui.WaveTheme
import com.example.waveplayer.viewmodel.LibraryViewModel
import com.example.waveplayer.viewmodel.LyricsViewModel
import com.example.waveplayer.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val player: PlayerViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val lyricsVm: LyricsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent { App(player, library, settingsVm, lyricsVm) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        val a = i?.action
        if (a != null && a.startsWith(ShortcutBus.PREFIX)) ShortcutBus.action.value = a
    }
}

/** Artwork palette as a State that App itself never reads: only the theme (Dynamic mode) and Now Playing do. */
@Composable
private fun rememberArtPalette(player: PlayerViewModel): State<ArtPalette?> {
    val ctx = LocalContext.current.applicationContext
    return produceState<ArtPalette?>(null, player) {
        player.current.map { it?.mediaMetadata?.artworkUri }.distinctUntilChanged().collectLatest { uri ->
            value = if (uri == null) null else ArtworkColors.get(ctx, uri)
        }
    }
}

@Composable
private fun App(player: PlayerViewModel, library: LibraryViewModel, settingsVm: SettingsViewModel, lyricsVm: LyricsViewModel) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val palette = rememberArtPalette(player)
    WaveTheme(settings, artAccent = { palette.value?.accent }) {
        Root(player, library, settingsVm, lyricsVm, palette)
    }
}

@Composable
private fun Root(
    player: PlayerViewModel, library: LibraryViewModel, settingsVm: SettingsViewModel,
    lyricsVm: LyricsViewModel, palette: State<ArtPalette?>,
) {
    val ctx = LocalContext.current
    // Root does not read the current song or scan progress, so track changes and scans do not recompose the pages.
    val lib by library.library.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val favorites by settingsVm.favorites.collectAsStateWithLifecycle()
    val recentIds by settingsVm.recent.collectAsStateWithLifecycle()
    val currentId = player.currentId.collectAsStateWithLifecycle()

    val audioPerm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
    else Manifest.permission.READ_EXTERNAL_STORAGE
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res[audioPerm] == true) library.scan()
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(ctx, audioPerm) == PackageManager.PERMISSION_GRANTED) library.ensureLoaded()
        else launcher.launch(buildList {
            add(audioPerm)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray())
    }

    var screen by rememberSaveable { mutableStateOf(Screen.HOME) }
    val showNow = remember { mutableStateOf(false) }
    var startLyrics by remember { mutableStateOf(false) }

    // One scroll state per page, hoisted here: tab switches and Now Playing open/close keep scroll positions.
    val homeState = rememberLazyListState()
    val songsState = rememberLazyListState()
    val albumsState = rememberLazyListState()
    val artistsState = rememberLazyListState()
    val foldersState = rememberLazyListState()
    val favState = rememberLazyListState()
    val recentState = rememberLazyListState()

    BackHandler(enabled = showNow.value) { showNow.value = false; startLyrics = false }
    BackHandler(enabled = !showNow.value && screen != Screen.HOME) { screen = Screen.HOME }

    // Stable callbacks (remembered) so list rows can be skipped.
    val onPlay = remember(player) { { list: List<Song>, i: Int -> player.play(list, i); showNow.value = true } }
    val onPlayGroup = remember(player) { { g: com.example.waveplayer.data.Group -> player.play(g.songs, 0); showNow.value = true } }
    val onFav = remember(settingsVm) { { id: Long -> settingsVm.toggleFavorite(id) } }
    val onNavigate = remember { { s: Screen -> screen = s } }

    // Launcher shortcuts (long-press the app icon).
    val shortcut by ShortcutBus.action.collectAsStateWithLifecycle()
    LaunchedEffect(shortcut) {
        when (shortcut) {
            ShortcutBus.FAVORITES -> screen = Screen.FAVORITES
            ShortcutBus.RECENT -> screen = Screen.RECENT
            ShortcutBus.LYRICS -> if (player.currentId.value != null) { startLyrics = true; showNow.value = true }
        }
        if (shortcut != null) ShortcutBus.action.value = null
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer(player) { showNow.value = true }
                    WaveNavBar(screen) { screen = it }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(lib.songs, favorites, currentId, settings, homeState, onPlay, onFav, onNavigate)
                    Screen.SONGS -> SongsScreen(lib.songs, favorites, currentId, settings, songsState, onPlay, onFav)
                    Screen.ALBUMS -> GroupsScreen("Albums", lib.albums, true, settings, albumsState, onPlayGroup)
                    Screen.ARTISTS -> GroupsScreen("Artists", lib.artists, false, settings, artistsState, onPlayGroup)
                    Screen.FOLDERS -> GroupsScreen("Folders", lib.folders, false, settings, foldersState, onPlayGroup)
                    Screen.FAVORITES -> {
                        val favSongs = remember(lib, favorites) { lib.songs.filter { it.id in favorites } }
                        SongListScreen("Favorites", "Belum ada favorit. Ketuk ikon hati pada lagu.", favSongs, favorites, currentId, settings, favState, onPlay, onFav)
                    }
                    Screen.RECENT -> {
                        val recentSongs = remember(lib, recentIds) { recentIds.mapNotNull { lib.byId[it] } }
                        SongListScreen("Recently Played", "Belum ada riwayat putar.", recentSongs, favorites, currentId, settings, recentState, onPlay, onFav)
                    }
                    Screen.SETTINGS -> SettingsScreen(settings, settingsVm, library)
                }
            }
        }

        // Enter: rises from the mini player (anchored bottom) while scaling up and fading in. Exit mirrors it.
        // Playback is untouched by this animation; the pages below stay composed, so scroll state is preserved.
        AnimatedVisibility(
            visible = showNow.value,
            enter = slideInVertically(Motion.standard()) { it / 3 } +
                fadeIn(Motion.quick()) +
                scaleIn(Motion.standard(), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = slideOutVertically(Motion.quick()) { it / 3 } +
                fadeOut(Motion.quick()) +
                scaleOut(Motion.quick(), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            NowPlayingHost(
                player, lyricsVm, settingsVm, lib, settings, palette, favorites, startLyrics,
            ) { showNow.value = false; startLyrics = false }
        }
    }
}

/** Reads the current track itself, keeping that dependency out of Root. */
@Composable
private fun NowPlayingHost(
    player: PlayerViewModel, lyricsVm: LyricsViewModel, settingsVm: SettingsViewModel, lib: Library,
    settings: com.example.waveplayer.data.AppSettings, palette: State<ArtPalette?>,
    favorites: Set<Long>, startWithLyrics: Boolean, onClose: () -> Unit,
) {
    val item by player.current.collectAsStateWithLifecycle()
    val song = remember(item, lib) { item?.mediaId?.toLongOrNull()?.let { lib.byId[it] } }
    LaunchedEffect(song?.id) { lyricsVm.load(song) } // no-op when already loaded
    NowPlayingScreen(
        player, lyricsVm, song, settings, palette,
        favorite = song?.id in favorites,
        onFav = { song?.let { settingsVm.toggleFavorite(it.id) } },
        startWithLyrics = startWithLyrics,
        onClose = onClose,
    )
}
