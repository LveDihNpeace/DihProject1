@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.waveplayer.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.waveplayer.PlayerViewModel
import com.example.waveplayer.data.AppSettings
import com.example.waveplayer.data.LyricLine
import com.example.waveplayer.data.LyricWord
import com.example.waveplayer.data.Lyrics
import com.example.waveplayer.data.Song
import com.example.waveplayer.viewmodel.LyricsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlin.math.abs

@Composable
fun LyricsView(vm: PlayerViewModel, lyricsVm: LyricsViewModel, song: Song?, settings: AppSettings) {
    val st by lyricsVm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var timingLine by remember { mutableStateOf<LyricLine?>(null) }
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
            else -> LyricList(vm, lyrics, settings) { timingLine = it }
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

    val tl = timingLine
    if (tl != null && song != null) {
        WordTimingDialog(
            line = tl,
            posNow = vm::positionNow,
            onSeek = vm::seekTo,
            onSave = { times -> lyricsVm.saveWordTiming(song, tl, times); timingLine = null },
            onClear = { lyricsVm.clearWordTiming(song, tl); timingLine = null },
            onDismiss = { timingLine = null },
        )
    }
}

@Composable
private fun LyricList(vm: PlayerViewModel, lyrics: Lyrics, settings: AppSettings, onEditLine: (LyricLine) -> Unit) {
    val lines = lyrics.lines
    val posState = vm.position.collectAsStateWithLifecycle()
    val playing = vm.isPlaying.collectAsStateWithLifecycle()
    val posNow = remember(vm) { { vm.positionNow() } }
    val onSeek = remember(vm) { { ms: Long -> vm.seekTo(ms) } }
    val listState = rememberLazyListState()
    val fraction = settings.lyricsPosition.fraction

    // Derived: lines recompose only when the ACTIVE line changes, not on every position tick.
    // Works identically while paused, after resume and after a seek (it is a pure function of the position).
    val activeTime = remember(lyrics) {
        derivedStateOf {
            if (!lyrics.synced) null
            else lines.lastOrNull { (it.timeMs ?: Long.MAX_VALUE) <= posState.value + 150 }?.timeMs
        }
    }
    val firstActive = remember(lyrics) {
        derivedStateOf { activeTime.value?.let { t -> lines.indexOfFirst { it.timeMs == t } } ?: -1 }
    }

    LaunchedEffect(lyrics, settings.lyricsAutoScroll, fraction) {
        var first = true
        snapshotFlow { firstActive.value }.collectLatest { idx ->
            if (settings.lyricsAutoScroll && idx >= 0) {
                while (listState.isScrollInProgress) delay(100) // never fight the user's finger
                listState.centerOn(idx, fraction, animate = !first)
            }
            first = false
        }
    }

    val blurOn = settings.lyricBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val h = maxHeight
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = h * fraction, bottom = h * (1f - fraction)),
            modifier = Modifier.fillMaxSize(),
        ) {
            itemsIndexed(lines, key = { i, l -> "$i|${l.timeMs}" }, contentType = { _, _ -> "lyric" }) { i, line ->
                LyricRow(
                    line, i, lyrics.synced, firstActive, activeTime, settings.lyricSize, blurOn,
                    settings.lyricBlurIntensity, settings.wordHighlight, playing, posState, posNow, onSeek, onEditLine,
                )
            }
        }
    }
}

/** Puts the item's center at [fraction] of the viewport height (0.5 = exact center). */
private suspend fun LazyListState.centerOn(index: Int, fraction: Float, animate: Boolean) {
    var info = layoutInfo
    var item = info.visibleItemsInfo.firstOrNull { it.index == index }
    if (item == null) {
        scrollToItem(index)
        withFrameNanos { } // let the jump lay out
        info = layoutInfo
        item = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    }
    val viewport = info.viewportEndOffset - info.viewportStartOffset
    val target = info.viewportStartOffset + viewport * fraction
    val delta = item.offset + item.size / 2f - target
    if (abs(delta) < 1f) return
    if (animate) animateScrollBy(delta, tween(450, easing = FastOutSlowInEasing)) else scrollBy(delta)
}

@Composable
private fun LyricRow(
    line: LyricLine, index: Int, synced: Boolean,
    firstActive: State<Int>, activeTime: State<Long?>,
    sizeSp: Float, blurOn: Boolean, blurIntensity: Float, wordHighlight: Boolean,
    playing: State<Boolean>, posState: State<Long>, posNow: () -> Long,
    onSeek: (Long) -> Unit, onEdit: (LyricLine) -> Unit,
) {
    if (line.text.isBlank() && !synced) { Spacer(Modifier.height((sizeSp * 0.8f).dp)); return }

    val at = activeTime.value
    val active = !synced || (at != null && line.timeMs == at)
    val dist = if (!synced) 0 else firstActive.value.let { fa -> if (fa < 0) index + 1 else abs(index - fa) }

    val targetAlpha = when {
        !synced -> 0.92f
        active -> 1f
        dist == 1 -> 0.55f
        dist == 2 -> 0.4f
        else -> 0.28f
    }
    val lineAlpha by animateFloatAsState(targetAlpha, tween(300), label = "alpha")
    val lineScale by animateFloatAsState(if (synced && !active) 0.9f else 1f, tween(300), label = "scale")
    // Blur only around the active line (cost grows with radius); the focused line is always sharp.
    val blurTarget = if (blurOn && synced && !active) blurIntensity * 12f * (minOf(dist, 3) / 3f) else 0f
    val blurDp by animateFloatAsState(blurTarget, tween(300), label = "blur")

    var mod = Modifier.fillMaxWidth()
        .pointerInput(line) {
            detectTapGestures(
                onLongPress = { if (line.timeMs != null) onEdit(line) },
                onTap = { line.timeMs?.let(onSeek) },
            )
        }
        .padding(vertical = 8.dp, horizontal = 8.dp)
        .graphicsLayer { scaleX = lineScale; scaleY = lineScale; alpha = lineAlpha }
    if (blurDp > 0.4f) mod = mod.blur(blurDp.dp, BlurredEdgeTreatment.Unbounded)

    val words = line.words
    if (active && synced && wordHighlight && words != null) {
        WordLine(line, words, sizeSp, playing, posState, posNow, mod)
    } else {
        Text(
            line.text, modifier = mod, color = Color.White, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold,
            lineHeight = (sizeSp * 1.3f).sp, textAlign = TextAlign.Center,
        )
    }
}

/** Encodes (active word index + 1) * 16 + progress step (0..15); recomposes only when this key changes. */
private fun wordKey(words: List<LyricWord>, end: Long, now: Long): Int {
    var idx = -1
    for (i in words.indices) { if (words[i].startMs <= now) idx = i else break }
    if (idx < 0) return 0
    if (now >= end) return (words.size + 1) * 16 // every word sung
    val start = words[idx].startMs
    val next = if (idx + 1 < words.size) words[idx + 1].startMs else end
    val p = if (next > start) ((now - start).toFloat() / (next - start)).coerceIn(0f, 1f) else 1f
    return (idx + 1) * 16 + (p * 15f).toInt()
}

@Composable
private fun WordLine(
    line: LyricLine, words: List<LyricWord>, sizeSp: Float,
    playing: State<Boolean>, posState: State<Long>, posNow: () -> Long, modifier: Modifier,
) {
    val end = line.endMs ?: (words.last().startMs + 2_000L)
    val now = remember(line) { mutableLongStateOf(posNow()) }

    // Real playback clock while playing (frame-accurate, only this one line is recomposed)...
    LaunchedEffect(line, playing.value) {
        if (playing.value) {
            while (isActive) { withFrameNanos { }; now.longValue = posNow() }
        }
    }
    // ...and the polled position while paused, so seeks/pauses stay in sync without a running animation loop.
    LaunchedEffect(line) {
        snapshotFlow { posState.value }.collect { if (!playing.value) now.longValue = it }
    }

    val key by remember(line) { derivedStateOf { wordKey(words, end, now.longValue) } }
    val text = remember(key, line) {
        buildAnnotatedString {
            val idx = key / 16 - 1
            val step = key % 16
            words.forEachIndexed { i, w ->
                val a = when {
                    i < idx -> 1f
                    i == idx -> 0.5f + 0.5f * (step / 15f)
                    else -> 0.5f
                }
                withStyle(SpanStyle(color = Color.White.copy(alpha = a))) { append(w.text) }
            }
        }
    }
    Text(
        text, modifier = modifier, fontSize = sizeSp.sp, fontWeight = FontWeight.Bold,
        lineHeight = (sizeSp * 1.3f).sp, textAlign = TextAlign.Center,
    )
}

private fun fmtMs(ms: Long) = "%d:%02d.%03d".format(ms / 60_000, (ms / 1000) % 60, ms % 1000)

/**
 * Manual word-timing editor for one line. Timings are entered by the user (tap while the line is sung, then
 * fine-tune in 0.1 s steps); nothing is generated automatically.
 */
@Composable
private fun WordTimingDialog(
    line: LyricLine, posNow: () -> Long, onSeek: (Long) -> Unit,
    onSave: (List<Long>) -> Unit, onClear: () -> Unit, onDismiss: () -> Unit,
) {
    val parts = remember(line) { line.text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() } }
    val times = remember(line) {
        mutableStateListOf<Long?>().also { l ->
            val w = line.words
            if (w != null && w.size == parts.size) w.forEach { l.add(it.startMs) } else parts.forEach { _ -> l.add(null) }
        }
    }
    val valid = parts.size >= 2 && times.all { it != null } &&
        times.filterNotNull().zipWithNext().all { (a, b) -> a <= b }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timing kata") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Putar baris ini lalu ketuk \"Tandai kata berikutnya\" tepat saat tiap kata dinyanyikan. " +
                        "Halus-kan dengan tombol -0.1 / +0.1.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ line.timeMs?.let(onSeek) }) { Text("Ulang baris") }
                    Button({
                        val i = times.indexOfFirst { it == null }
                        if (i >= 0) times[i] = posNow()
                    }) { Text("Tandai kata berikutnya") }
                }
                parts.forEachIndexed { i, p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(times[i]?.let(::fmtMs) ?: "—", style = MaterialTheme.typography.labelMedium)
                        TextButton({ times[i] = ((times[i] ?: posNow()) - 100).coerceAtLeast(0L) }) { Text("-0.1") }
                        TextButton({ times[i] = (times[i] ?: posNow()) + 100 }) { Text("+0.1") }
                    }
                }
                if (!valid && times.all { it != null }) {
                    Text("Urutan waktu harus naik dari kata pertama ke terakhir.", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = { TextButton({ onSave(times.map { it!! }) }, enabled = valid) { Text("Simpan") } },
        dismissButton = {
            Row {
                TextButton(onClear) { Text("Hapus timing") }
                TextButton(onDismiss) { Text("Batal") }
            }
        },
    )
}
