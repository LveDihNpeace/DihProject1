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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import com.example.waveplayer.ui.MiniPlayer
import com.example.waveplayer.ui.Motion
import com.example.waveplayer.ui.NowPlayingScreen
import com.example.waveplayer.ui.SettingsScreen
import com.example.waveplayer.ui.ShortcutBus
import com.example.waveplayer.ui.SongListScreen
import com.example.waveplayer.ui.SongsScreen
import com.example.waveplayer.ui.WaveTheme
import com.example.waveplayer.ui.rememberTopNavState
import com.example.waveplayer.ui.rememberNavState
import com.example.waveplayer.ui.backTarget
import com.example.waveplayer.ui.ScanScreen
import com.example.waveplayer.ui.PlaylistRoute
import com.example.waveplayer.ui.PlaylistHubScreen
import com.example.waveplayer.ui.NavShell
import com.example.waveplayer.ui.Destination
import com.example.waveplayer.ui.BackTarget
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

    val nav = rememberNavState()
    val top = rememberTopNavState(nav)

    // One scroll state per page, hoisted here so page changes and Now Playing keep scroll positions.
    val songsState = rememberLazyListState()
    val favState = rememberLazyListState()
    val recentState = rememberLazyListState()
    val settingsScroll = rememberScrollState() // Settings is a plain scrolling column; kept here so it survives page changes

    val favSongs = remember(lib, favorites) { lib.songs.filter { it.id in favorites } }
    val recentSongs = remember(lib, recentIds) { recentIds.mapNotNull { lib.byId[it] } }

    // Back: one derived target (Now Playing > panel > Playlist sub-page > other page > exit). Each handler is
    // enabled for exactly one target, so they never compete and the order they are registered in does not matter.
    val panelShown by remember(nav, top) { derivedStateOf { nav.panelOpen || top.progress.value > 0f } }
    val target by remember(nav) {
        derivedStateOf { backTarget(nav.nowPlayingOpen, panelShown, nav.destination, nav.playlistRoute) }
    }
    BackHandler(enabled = target == BackTarget.NOW_PLAYING) { nav.closeNowPlaying() }
    BackHandler(enabled = target == BackTarget.PANEL) { top.close() }
    BackHandler(enabled = target == BackTarget.PLAYLIST_HUB || target == BackTarget.SONGS) {
        if (target == BackTarget.PLAYLIST_HUB) nav.backToHub() else nav.select(Destination.SONGS)
    }

    // Stable callbacks (remembered) so list rows can be skipped.
    // Every way into Now Playing closes the panel first, so the panel can never stay open behind it.
    val openNowPlaying = remember(nav, top) { { withLyrics: Boolean -> top.snapClosed(); nav.openNowPlaying(withLyrics) } }
    val onPlay = remember(player, openNowPlaying) { { list: List<Song>, i: Int -> player.play(list, i); openNowPlaying(false) } }
    val onFav = remember(settingsVm) { { id: Long -> settingsVm.toggleFavorite(id) } }
    val onSelect = remember(nav, top) { { d: Destination -> nav.select(d); top.close() } }

    // Launcher shortcuts (long-press the app icon). Favorites / Recently Played now open the Playlist sub-pages.
    val shortcut by ShortcutBus.action.collectAsStateWithLifecycle()
    LaunchedEffect(shortcut) {
        when (shortcut) {
            ShortcutBus.FAVORITES -> {
                nav.closeNowPlaying(); top.snapClosed(); nav.openPlaylistRoute(PlaylistRoute.FAVORITES)
            }
            ShortcutBus.RECENT -> {
                nav.closeNowPlaying(); top.snapClosed(); nav.openPlaylistRoute(PlaylistRoute.RECENT)
            }
            ShortcutBus.LYRICS -> if (player.currentId.value != null) openNowPlaying(true)
        }
        if (shortcut != null) ShortcutBus.action.value = null
    }

    val title = when (nav.destination) {
        Destination.SONGS -> "Songs"
        Destination.PLAYLIST -> when (nav.playlistRoute) {
            PlaylistRoute.HUB -> "Playlist"
            PlaylistRoute.FAVORITES -> "Favorites"
            PlaylistRoute.RECENT -> "Recently Played"
        }
        Destination.SCAN -> "Scan Music"
        Destination.SETTINGS -> "Settings"
    }
    val showBack = nav.destination == Destination.PLAYLIST && nav.playlistRoute != PlaylistRoute.HUB

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                Column(Modifier.navigationBarsPadding()) {
                    MiniPlayer(player) { openNowPlaying(false) }
                }
            },
        ) { pad ->
            // The shell owns the status bar inset (inside the header); pages no longer pad themselves.
            Box(Modifier.padding(pad)) {
                NavShell(nav, top, title, showBack, onBack = nav::backToHub, onSelect = onSelect) {
                    when (nav.destination) {
                        Destination.SONGS ->
                            SongsScreen(lib.songs, favorites, currentId, settings, songsState, onPlay, onFav)
                        Destination.PLAYLIST -> when (nav.playlistRoute) {
                            PlaylistRoute.HUB -> PlaylistHubScreen(favSongs, recentSongs, nav::openPlaylistRoute)
                            PlaylistRoute.FAVORITES -> SongListScreen(
                                "Belum ada favorit. Ketuk ikon hati pada lagu.", favSongs, favorites, currentId,
                                settings, favState, onPlay, onFav,
                            )
                            PlaylistRoute.RECENT -> SongListScreen(
                                "Belum ada riwayat putar.", recentSongs, favorites, currentId,
                                settings, recentState, onPlay, onFav,
                            )
                        }
                        Destination.SCAN -> ScanScreen(library)
                        Destination.SETTINGS -> SettingsScreen(settings, settingsVm, settingsScroll)
                    }
                }
            }
        }

        // Enter: rises from the mini player (anchored bottom) while scaling up and fading in. Exit mirrors it.
        // Playback is untouched by this animation; the pages below stay composed, so scroll state is preserved.
        AnimatedVisibility(
            visible = nav.nowPlayingOpen,
            enter = slideInVertically(Motion.standard()) { it / 3 } +
                fadeIn(Motion.quick()) +
                scaleIn(Motion.standard(), initialScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)),
            exit = slideOutVertically(Motion.quick()) { it / 3 } +
                fadeOut(Motion.quick()) +
                scaleOut(Motion.quick(), targetScale = 0.92f, transformOrigin = TransformOrigin(0.5f, 1f)),
        ) {
            NowPlayingHost(
                player, lyricsVm, settingsVm, lib, settings, palette, favorites, nav.startLyrics,
            ) { nav.closeNowPlaying() }
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
