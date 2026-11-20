package com.example.waveplayer

import android.Manifest
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
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.data.ArtPalette
import com.example.waveplayer.data.ArtworkColors
import com.example.waveplayer.ui.FavoritesScreen
import com.example.waveplayer.ui.GroupsScreen
import com.example.waveplayer.ui.HomeScreen
import com.example.waveplayer.ui.MiniPlayer
import com.example.waveplayer.ui.NowPlayingScreen
import com.example.waveplayer.ui.Screen
import com.example.waveplayer.ui.SettingsScreen
import com.example.waveplayer.ui.SongsScreen
import com.example.waveplayer.ui.WaveTheme
import com.example.waveplayer.viewmodel.LibraryViewModel
import com.example.waveplayer.viewmodel.LyricsViewModel
import com.example.waveplayer.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private val player: PlayerViewModel by viewModels()
    private val library: LibraryViewModel by viewModels()
    private val settingsVm: SettingsViewModel by viewModels()
    private val lyricsVm: LyricsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App(player, library, settingsVm, lyricsVm) }
    }
}

@Composable
private fun App(player: PlayerViewModel, library: LibraryViewModel, settingsVm: SettingsViewModel, lyricsVm: LyricsViewModel) {
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val item by player.current.collectAsStateWithLifecycle()
    val ctx = LocalContext.current.applicationContext
    val art = item?.mediaMetadata?.artworkUri
    var palette by remember { mutableStateOf<ArtPalette?>(null) }
    LaunchedEffect(art) { palette = if (art == null) null else ArtworkColors.get(ctx, art) }

    WaveTheme(settings, palette?.accent) {
        Root(player, library, settingsVm, lyricsVm, palette)
    }
}

private val bottomItems: List<Triple<Screen, String, ImageVector>> = listOf(
    Triple(Screen.HOME, "Home", Icons.Default.Home),
    Triple(Screen.SONGS, "Songs", Icons.Default.MusicNote),
    Triple(Screen.ALBUMS, "Albums", Icons.Default.Album),
    Triple(Screen.ARTISTS, "Artists", Icons.Default.Person),
    Triple(Screen.FOLDERS, "Folders", Icons.Default.Folder),
)

@Composable
private fun Root(
    player: PlayerViewModel, library: LibraryViewModel, settingsVm: SettingsViewModel,
    lyricsVm: LyricsViewModel, palette: ArtPalette?,
) {
    val ctx = LocalContext.current
    val songs by library.songs.collectAsStateWithLifecycle()
    val scan by library.scan.collectAsStateWithLifecycle()
    val settings by settingsVm.settings.collectAsStateWithLifecycle()
    val favorites by settingsVm.favorites.collectAsStateWithLifecycle()
    val item by player.current.collectAsStateWithLifecycle()
    val currentSong = remember(songs, item) { songs.firstOrNull { it.id.toString() == item?.mediaId } }

    LaunchedEffect(currentSong?.id) { lyricsVm.load(currentSong) }

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

    var screen by remember { mutableStateOf(Screen.HOME) }
    var showNow by remember { mutableStateOf(false) }
    BackHandler(enabled = showNow) { showNow = false }
    BackHandler(enabled = !showNow && screen != Screen.HOME) { screen = Screen.HOME }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0.dp),
            bottomBar = {
                Column {
                    MiniPlayer(player) { showNow = true }
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)) {
                        bottomItems.forEach { (s, label, icon) ->
                            NavigationBarItem(
                                selected = screen == s, onClick = { screen = s },
                                icon = { Icon(icon, label) }, label = { Text(label) },
                            )
                        }
                    }
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) {
                when (screen) {
                    Screen.HOME -> HomeScreen(songs, favorites, settings, player, settingsVm::toggleFavorite) { screen = it }
                    Screen.SONGS -> SongsScreen(songs, favorites, settings, player, settingsVm::toggleFavorite)
                    Screen.ALBUMS -> GroupsScreen(
                        "Albums", songs.groupBy { it.albumId }.values.map { it.first().album to it }.sortedBy { it.first.lowercase() },
                        { "${it.first().artist} • ${it.size} lagu" }, settings,
                    ) { player.play(it, 0) }
                    Screen.ARTISTS -> GroupsScreen(
                        "Artists", songs.groupBy { it.artist }.map { it.key to it.value }.sortedBy { it.first.lowercase() },
                        { "${it.size} lagu" }, settings,
                    ) { player.play(it, 0) }
                    Screen.FOLDERS -> GroupsScreen(
                        "Folders", songs.groupBy { it.folder }.map { it.key to it.value }.sortedBy { it.first.lowercase() },
                        { "${it.size} lagu" }, settings,
                    ) { player.play(it, 0) }
                    Screen.FAVORITES -> FavoritesScreen(songs, favorites, settings, player, settingsVm::toggleFavorite)
                    Screen.SETTINGS -> SettingsScreen(settings, settingsVm, scan, songs.size, library::scan)
                }
            }
        }
        AnimatedVisibility(
            visible = showNow,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
        ) {
            NowPlayingScreen(
                player, lyricsVm, currentSong, settings, palette,
                favorite = currentSong?.id in favorites,
                onFav = { currentSong?.let { settingsVm.toggleFavorite(it.id) } },
                onClose = { showNow = false },
            )
        }
    }
}
