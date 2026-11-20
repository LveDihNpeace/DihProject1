@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import com.example.waveplayer.PlayerViewModel
import com.example.waveplayer.audio.VocalEngine
import com.example.waveplayer.audio.VocalPreset
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ArtPalette
import com.example.waveplayer.data.ArtShape
import com.example.waveplayer.data.Song
import com.example.waveplayer.viewmodel.LyricsViewModel

@Composable
fun DynamicBackground(palette: ArtPalette?, artUri: Any?, blurLevel: Float) {
    val top by animateColorAsState(palette?.let { Color(it.top) } ?: Color(0xFF1B1530), tween(900), label = "top")
    val bottom by animateColorAsState(palette?.let { Color(it.bottom) } ?: Color(0xFF07060C), tween(900), label = "bottom")
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && artUri != null && blurLevel > 0f) {
            AsyncImage(
                artUri, null,
                Modifier.fillMaxSize().blur((12 + blurLevel * 60).dp).alpha(0.35f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
    }
}

@Composable
fun MiniPlayer(vm: PlayerViewModel, onOpen: () -> Unit) {
    val item by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val md = item?.mediaMetadata ?: return
    Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f), tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Art(md.artworkUri, 44.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(md.title?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(md.artist?.toString() ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
            }
            IconButton(vm::togglePlay) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause") }
            IconButton(vm::next) { Icon(Icons.Default.SkipNext, "Next") }
        }
    }
}

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel, lyricsVm: LyricsViewModel, song: Song?, settings: AppSettings,
    palette: ArtPalette?, favorite: Boolean, onFav: () -> Unit, onClose: () -> Unit,
) {
    val item by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    val dur by vm.duration.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeat.collectAsStateWithLifecycle()
    var showLyrics by remember { mutableStateOf(false) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var showVocal by remember { mutableStateOf(false) }
    val vocalGain by VocalEngine.vocalGain.collectAsStateWithLifecycle()
    val instGain by VocalEngine.instGain.collectAsStateWithLifecycle()
    val white = Color.White
    val dim = Color.White.copy(alpha = 0.5f)
    if (showVocal) VocalSheet { showVocal = false }

    Box(Modifier.fillMaxSize()) {
        DynamicBackground(palette, item?.mediaMetadata?.artworkUri, settings.blur)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Tutup", tint = white) }
                Spacer(Modifier.weight(1f))
                FilterChip(
                    selected = showLyrics, onClick = { showLyrics = !showLyrics }, label = { Text("Lirik") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = white, selectedLabelColor = white, selectedContainerColor = white.copy(alpha = 0.25f),
                    ),
                )
                Spacer(Modifier.width(8.dp))
                FilterChip(
                    selected = vocalGain != 1f || instGain != 1f, onClick = { showVocal = true }, label = { Text("Vokal") },
                    colors = FilterChipDefaults.filterChipColors(
                        labelColor = white, selectedLabelColor = white, selectedContainerColor = white.copy(alpha = 0.25f),
                    ),
                )
                IconButton(onFav) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorit", tint = white)
                }
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(showLyrics, animationSpec = tween(350), label = "lyr") { lyr ->
                    if (lyr) LyricsView(vm, lyricsVm, song, settings.lyricSize)
                    else ArtworkView(item, playing, settings.artShape)
                }
            }

            Crossfade(item, animationSpec = tween(450), label = "title") { it ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(it?.mediaMetadata?.title?.toString() ?: "", color = white, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(it?.mediaMetadata?.artist?.toString() ?: "", color = dim, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }

            Slider(
                value = drag ?: pos.toFloat(),
                onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
                valueRange = 0f..maxOf(dur, 1L).toFloat(),
                colors = SliderDefaults.colors(thumbColor = white, activeTrackColor = white, inactiveTrackColor = white.copy(alpha = 0.3f)),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(fmt((drag ?: pos.toFloat()).toLong()), color = dim, style = MaterialTheme.typography.labelMedium)
                Text(fmt(dur), color = dim, style = MaterialTheme.typography.labelMedium)
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(vm::toggleShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) white else dim) }
                IconButton(vm::previous) { Icon(Icons.Default.SkipPrevious, "Previous", Modifier.size(36.dp), tint = white) }
                FilledIconButton(
                    vm::togglePlay, Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = white, contentColor = Color.Black),
                ) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", Modifier.size(40.dp)) }
                IconButton(vm::next) { Icon(Icons.Default.SkipNext, "Next", Modifier.size(36.dp), tint = white) }
                IconButton(vm::cycleRepeat) {
                    Icon(
                        if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, "Repeat",
                        tint = if (repeat != Player.REPEAT_MODE_OFF) white else dim,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtworkView(item: MediaItem?, playing: Boolean, shape: ArtShape) {
    val scale by animateFloatAsState(if (playing) 1f else 0.88f, tween(450), label = "scale")
    Crossfade(item, animationSpec = tween(450), label = "art") { it ->
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight)
            val clipShape = when (shape) {
                ArtShape.ROUNDED -> RoundedCornerShape(28.dp)
                ArtShape.SQUARE -> RoundedCornerShape(4.dp)
                ArtShape.CIRCLE -> CircleShape
            }
            Box(
                Modifier.size(side).graphicsLayer { scaleX = scale; scaleY = scale }
                    .clip(clipShape).background(Color.White.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MusicNote, null, Modifier.size(side / 3), tint = Color.White.copy(alpha = 0.4f))
                AsyncImage(it?.mediaMetadata?.artworkUri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
private fun LyricsView(vm: PlayerViewModel, lyricsVm: LyricsViewModel, song: Song?, sizeSp: Float) {
    val st by lyricsVm.state.collectAsStateWithLifecycle()
    val pos by vm.position.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && song != null) lyricsVm.importUri(song, uri)
    }
    val white = Color.White

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Box {
                IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Menu lirik", tint = white) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("Cari lirik online") }, { menu = false; song?.let { lyricsVm.fetchOnline(it) } })
                    DropdownMenuItem({ Text("Pilih file .lrc / .txt") }, { menu = false; picker.launch(arrayOf("*/*")) })
                    DropdownMenuItem({ Text("Tulis / edit manual") }, { menu = false; editing = true })
                    if (st.lyrics != null) DropdownMenuItem({ Text("Hapus lirik") }, { menu = false; song?.let { lyricsVm.delete(it) } })
                }
            }
        }
        val lyrics = st.lyrics
        when {
            st.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = white) }
            lyrics == null -> Column(
                Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Lirik belum tersedia", color = white, style = MaterialTheme.typography.titleMedium)
                st.message?.let { Text(it, color = white.copy(alpha = 0.6f), textAlign = TextAlign.Center, modifier = Modifier.padding(8.dp)) }
                Spacer(Modifier.height(12.dp))
                Button({ song?.let { lyricsVm.fetchOnline(it) } }, enabled = song != null) { Text("Cari online") }
                OutlinedButton({ picker.launch(arrayOf("*/*")) }, enabled = song != null) { Text("Pilih file lirik") }
                TextButton({ editing = true }, enabled = song != null) { Text("Tulis manual", color = white) }
            }
            else -> {
                val lines = lyrics.lines
                val idx = if (lyrics.synced) lines.indexOfLast { (it.timeMs ?: Long.MAX_VALUE) <= pos } else -1
                val listState = rememberLazyListState()
                LaunchedEffect(idx) {
                    if (idx >= 0 && !listState.isScrollInProgress) listState.animateScrollToItem(maxOf(idx - 2, 0))
                }
                LazyColumn(state = listState, contentPadding = PaddingValues(vertical = 120.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(lines) { i, line ->
                        val active = !lyrics.synced || i == idx
                        val a by animateFloatAsState(if (active) 1f else 0.4f, tween(300), label = "a")
                        val s by animateFloatAsState(if (lyrics.synced && i == idx) 1f else 0.92f, tween(300), label = "s")
                        Text(
                            line.text.ifBlank { "♪" },
                            color = white.copy(alpha = a), fontSize = sizeSp.sp, fontWeight = FontWeight.Bold, lineHeight = (sizeSp * 1.3f).sp,
                            modifier = Modifier.fillMaxWidth()
                                .clickable(enabled = line.timeMs != null) { line.timeMs?.let(vm::seekTo) }
                                .padding(vertical = 8.dp)
                                .graphicsLayer { scaleX = s; scaleY = s; transformOrigin = TransformOrigin(0f, 0.5f) },
                        )
                    }
                }
            }
        }
    }

    if (editing && song != null) {
        var text by remember(song.id) { mutableStateOf(st.lyrics?.raw ?: "") }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Lirik: ${song.title}") },
            text = {
                OutlinedTextField(
                    text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp),
                    placeholder = { Text("Tempel lirik (format .lrc dengan [mm:ss.xx] atau teks biasa)") },
                )
            },
            confirmButton = { TextButton({ lyricsVm.saveText(song, text); editing = false }) { Text("Simpan") } },
            dismissButton = { TextButton({ editing = false }) { Text("Batal") } },
        )
    }
}


@Composable
private fun VocalSheet(onDismiss: () -> Unit) {
    val vg by VocalEngine.vocalGain.collectAsStateWithLifecycle()
    val ig by VocalEngine.instGain.collectAsStateWithLifecycle()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Pemisah vokal / instrumental", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VocalPreset.values().forEach { p ->
                    FilterChip(
                        selected = vg == p.vocal && ig == p.inst,
                        onClick = { VocalEngine.apply(p) },
                        label = { Text(p.label) },
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Volume vokal: ${(vg * 100).toInt()}%")
            Slider(vg, { VocalEngine.setVocal(it) }, valueRange = 0f..1f)
            Text("Volume instrumen: ${(ig * 100).toInt()}%")
            Slider(ig, { VocalEngine.setInst(it) }, valueRange = 0f..1f)
            Spacer(Modifier.height(8.dp))
            Text(
                "Diproses langsung (real-time) dengan pemisahan stereo, bukan AI. Paling baik untuk lagu stereo " +
                    "dengan vokal di tengah; sisa vokal atau instrumen masih bisa terdengar. File mono tidak terpengaruh. " +
                    "Pengaturan kembali ke Original saat aplikasi dibuka ulang.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
