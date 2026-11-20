@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.waveplayer.PlayerViewModel
import com.example.waveplayer.audio.EqEngine
import com.example.waveplayer.audio.VocalEngine
import com.example.waveplayer.audio.VocalPreset
import com.example.waveplayer.audio.VocalStatus
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.ArtPalette
import com.example.waveplayer.data.ArtShape
import com.example.waveplayer.data.Song
import com.example.waveplayer.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun DynamicBackground(palette: State<ArtPalette?>, artUri: Any?, blurLevel: Float) {
    val p = palette.value
    val top by animateColorAsState(p?.let { Color(it.top) } ?: Color(0xFF1B1530), tween(900), label = "top")
    val bottom by animateColorAsState(p?.let { Color(it.bottom) } ?: Color(0xFF07060C), tween(900), label = "bottom")
    val ctx = LocalContext.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(top, bottom)))) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && artUri != null && blurLevel > 0f) {
            // Tiny 96 px source: blurring a small image is cheap and looks identical once blurred.
            val req = remember(artUri) { ImageRequest.Builder(ctx).data(artUri).size(96).allowRgb565(true).build() }
            AsyncImage(
                req, null,
                Modifier.fillMaxSize().blur((12 + blurLevel * 60).dp).alpha(0.35f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.22f)))
    }
}

@Composable
fun NowPlayingScreen(
    vm: PlayerViewModel, lyricsVm: LyricsViewModel, song: Song?, settings: AppSettings,
    palette: State<ArtPalette?>, favorite: Boolean, onFav: () -> Unit,
    startWithLyrics: Boolean, onClose: () -> Unit,
) {
    // Only slow-changing state is read here. Position lives in ProgressSection / lyrics, never at this level.
    val item by vm.current.collectAsStateWithLifecycle()
    val playing by vm.isPlaying.collectAsStateWithLifecycle()
    val shuffle by vm.shuffle.collectAsStateWithLifecycle()
    val repeat by vm.repeat.collectAsStateWithLifecycle()
    var showLyrics by remember(startWithLyrics) { mutableStateOf(startWithLyrics) }
    var showVocal by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    val vocalGain by VocalEngine.vocalGain.collectAsStateWithLifecycle()
    val instGain by VocalEngine.instGain.collectAsStateWithLifecycle()
    val eqState by EqEngine.state.collectAsStateWithLifecycle()
    val white = Color.White
    val dim = Color.White.copy(alpha = 0.55f)

    // The disc turns only while audio is REALLY playing (Player.isPlaying: false while paused, buffering or
    // ended) and only while the disc is on screen. Cancelling the effect keeps the angle; resuming continues from it.
    // rotation.value is read in the graphics layer, so the animation never recomposes anything.
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(playing, showLyrics) {
        if (playing && !showLyrics) {
            while (isActive) {
                rotation.animateTo(rotation.value + 360f, tween(24_000, easing = LinearEasing))
                rotation.snapTo(rotation.value % 360f)
            }
        }
    }

    if (showVocal) VocalSheet { showVocal = false }
    if (showEq) EqualizerSheet { showEq = false }

    Box(Modifier.fillMaxSize()) {
        DynamicBackground(palette, item?.mediaMetadata?.artworkUri, settings.blur)
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClose) { Icon(Icons.Default.KeyboardArrowDown, "Tutup", tint = white) }
                Spacer(Modifier.weight(1f))
                IconButton(onFav) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorit", tint = white)
                }
            }
            val chipColors = FilterChipDefaults.filterChipColors(
                labelColor = white, selectedLabelColor = white, selectedContainerColor = white.copy(alpha = 0.25f),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                FilterChip(showLyrics, { showLyrics = !showLyrics }, { Text("Lirik") }, colors = chipColors)
                FilterChip(vocalGain != 1f || instGain != 1f, { showVocal = true }, { Text("Vokal") }, colors = chipColors)
                FilterChip(eqState.enabled, { showEq = true }, { Text("EQ") }, colors = chipColors)
            }
            Spacer(Modifier.height(8.dp))

            Box(Modifier.weight(1f).fillMaxWidth()) {
                Crossfade(showLyrics, animationSpec = tween(350), label = "lyr") { lyr ->
                    if (lyr) LyricsView(vm, lyricsVm, song, settings)
                    else ArtworkDisc(item, rotation, playing, settings.artShape)
                }
            }

            Crossfade(item, animationSpec = tween(450), label = "title") { it ->
                Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text(
                        it?.mediaMetadata?.title?.toString() ?: "", color = white, style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(it?.mediaMetadata?.artist?.toString() ?: "", color = dim, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                }
            }

            ProgressSection(vm, playing)

            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
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

/** Position is polled here (20 Hz while playing, 3 Hz paused) and only this small subtree recomposes. */
@Composable
private fun ProgressSection(vm: PlayerViewModel, playing: Boolean) {
    val dur by vm.duration.collectAsStateWithLifecycle()
    val pos = remember { mutableLongStateOf(vm.positionNow()) }
    var drag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(playing) {
        while (isActive) {
            pos.longValue = vm.positionNow()
            delay(if (playing) 50 else 300)
        }
    }
    val white = Color.White
    val dim = Color.White.copy(alpha = 0.55f)
    Slider(
        value = drag ?: pos.longValue.toFloat(),
        onValueChange = { drag = it },
        onValueChangeFinished = { drag?.let { vm.seekTo(it.toLong()) }; drag = null },
        valueRange = 0f..maxOf(dur, 1L).toFloat(),
        colors = SliderDefaults.colors(thumbColor = white, activeTrackColor = white, inactiveTrackColor = white.copy(alpha = 0.3f)),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(fmt((drag ?: pos.longValue.toFloat()).toLong()), color = dim, style = MaterialTheme.typography.labelMedium)
        Text(fmt(dur), color = dim, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun ArtworkDisc(item: MediaItem?, rotation: Animatable<Float, AnimationVector1D>, playing: Boolean, shape: ArtShape) {
    val scale by animateFloatAsState(if (playing) 1f else 0.94f, tween(450), label = "scale")
    val artUri = item?.mediaMetadata?.artworkUri
    val primary = MaterialTheme.colorScheme.primary
    val ring = remember(primary) {
        Brush.sweepGradient(listOf(Color.White.copy(alpha = 0.9f), primary, Color.White.copy(alpha = 0.25f), primary, Color.White.copy(alpha = 0.9f)))
    }
    val circle = shape == ArtShape.CIRCLE
    val outer: Shape = when (shape) {
        ArtShape.CIRCLE -> CircleShape
        ArtShape.ROUNDED -> RoundedCornerShape(30.dp)
        ArtShape.SQUARE -> RoundedCornerShape(6.dp)
    }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val side = minOf(maxWidth, maxHeight) * 0.92f
        Box(
            Modifier.size(side)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .shadow(28.dp, outer, clip = false)
                .clip(outer).background(ring).padding(5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.fillMaxSize().clip(outer).background(Color(0xFF111111))
                    .graphicsLayer { if (circle) rotationZ = rotation.value },
            ) {
                Crossfade(artUri, animationSpec = tween(450), label = "art") { uri ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MusicNote, null, Modifier.size(side / 3), tint = Color.White.copy(alpha = 0.35f))
                        AsyncImage(uri, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    }
                }
            }
            if (circle) {
                Box(
                    Modifier.size(side * 0.16f).clip(CircleShape).background(Color(0xFF0E0C14))
                        .border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape),
                )
            }
        }
    }
}

@Composable
private fun VocalSheet(onDismiss: () -> Unit) {
    val vg by VocalEngine.vocalGain.collectAsStateWithLifecycle()
    val ig by VocalEngine.instGain.collectAsStateWithLifecycle()
    val status by VocalEngine.status.collectAsStateWithLifecycle()
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
                when (status) {
                    VocalStatus.ACTIVE -> "Status: pemrosesan aktif pada audio yang diputar"
                    VocalStatus.IDLE -> "Status: menunggu lagu diputar"
                    VocalStatus.UNSUPPORTED -> "Status: TIDAK aktif, lagu ini mono atau bukan PCM 16-bit sehingga tidak bisa diproses"
                    VocalStatus.MONO_LIKE -> "Status: lagu hampir mono, vokal dan instrumen tidak bisa dipisahkan"
                },
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Pemisahan real-time berbasis spektrum stereo (bukan AI). Vokal yang berada di tengah campuran " +
                    "dipisahkan dari instrumen; sisa vokal, reverb, atau instrumen di tengah (misalnya snare) masih bisa " +
                    "terdengar. Hasil bergantung pada cara lagu di-mix. Pengaturan kembali ke Original saat aplikasi dibuka ulang.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
