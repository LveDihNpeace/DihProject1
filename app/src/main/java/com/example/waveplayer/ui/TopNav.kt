package com.example.waveplayer.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val HeaderHeight = 56.dp

/**
 * Animation and gesture state of the top panel. [progress] is 0 (closed) .. 1 (open).
 * It is read only in layout / draw / graphics-layer lambdas, so moving the panel never recomposes pages.
 * Always open/close through these functions: they keep [NavState.panelOpen] and the animation in step.
 */
@Stable
class TopNavState(private val nav: NavState, private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    var panelHeightPx by mutableFloatStateOf(0f)
    var flingThresholdPx = 1500f

    private var job: Job? = null
    private var live = 0f // progress during a drag, updated synchronously so fast drags never lose deltas

    private fun animateTo(target: Float) {
        job?.cancel()
        job = scope.launch { progress.animateTo(target, if (target > 0f) Motion.standard() else Motion.quick()) }
    }

    fun open() { nav.panelOpen = true; animateTo(1f) }
    fun close() { nav.panelOpen = false; animateTo(0f) }
    fun toggle() { if (nav.panelOpen) close() else open() }

    /** Immediate close without animation (used when Now Playing opens, so the panel can never stay behind it). */
    fun snapClosed() {
        nav.panelOpen = false
        job?.cancel()
        job = scope.launch { progress.snapTo(0f) }
    }

    fun onDragStart() {
        job?.cancel()
        live = progress.value
    }

    fun dragBy(deltaPx: Float) {
        val h = panelHeightPx
        if (h <= 0f) return
        live = (live + deltaPx / h).coerceIn(0f, 1f)
        val v = live
        scope.launch { progress.snapTo(v) }
    }

    fun onDragEnd(velocityPxPerSec: Float) {
        val shouldOpen = when {
            velocityPxPerSec > flingThresholdPx -> true
            velocityPxPerSec < -flingThresholdPx -> false
            else -> live >= 0.5f
        }
        if (shouldOpen) open() else close()
    }
}

@Composable
fun rememberTopNavState(nav: NavState): TopNavState {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    return remember(nav, scope) {
        TopNavState(nav, scope).also { it.flingThresholdPx = with(density) { 700.dp.toPx() } }
    }
}

/** Vertical drag that moves the panel. Used on the header, the panel itself and the content catcher. */
@Composable
private fun Modifier.panelDrag(top: TopNavState): Modifier {
    val dragState = rememberDraggableState { top.dragBy(it) }
    return draggable(
        state = dragState,
        orientation = Orientation.Vertical,
        onDragStarted = { top.onDragStart() },
        onDragStopped = { velocity -> top.onDragEnd(velocity) },
    )
}

private class NavEntry(val destination: Destination, val label: String, val icon: ImageVector)

private val navEntries = listOf(
    NavEntry(Destination.SONGS, "Songs", Icons.Default.MusicNote),
    NavEntry(Destination.PLAYLIST, "Playlist", Icons.Default.Album),
    NavEntry(Destination.SCAN, "Scan Music", Icons.Default.Folder),
    NavEntry(Destination.SETTINGS, "Settings", Icons.Default.Settings),
)

/**
 * App shell: header (56 dp below the status bar) + expandable panel + the page.
 * The panel pushes the page down: the page is only shifted with offset{} (layout phase, no re-measure, no
 * recomposition) and clipped to its own area, so it never draws over the Mini Player. The panel is not an overlay.
 * The header is the only place that shows the page title.
 */
@Composable
fun NavShell(
    nav: NavState,
    top: TopNavState,
    title: String,
    showBack: Boolean,
    onBack: () -> Unit,
    onSelect: (Destination) -> Unit,
    content: @Composable () -> Unit,
) {
    val headerTotal = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HeaderHeight
    val shown by remember(nav, top) { derivedStateOf { nav.panelOpen || top.progress.value > 0f } }
    val surface = MaterialTheme.colorScheme.surface

    Box(Modifier.fillMaxSize()) {
        // ---- page area (below the header), pushed down by the panel ----
        Box(Modifier.fillMaxSize().padding(top = headerTotal).clipToBounds()) {
            Box(
                Modifier.fillMaxSize()
                    .offset { IntOffset(0, (top.progress.value * top.panelHeightPx).roundToInt()) },
            ) { content() }

            if (shown) {
                // While the panel is out, taps on the page close it (and cannot reach the list underneath).
                Box(
                    Modifier.fillMaxSize()
                        .drawBehind { drawRect(Color.Black.copy(alpha = 0.28f * top.progress.value)) }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = "Tutup menu navigasi",
                            onClick = top::close,
                        )
                        .panelDrag(top),
                )
            }
        }

        // ---- panel: slides out from behind the header ----
        Column(
            Modifier.align(Alignment.TopStart).fillMaxWidth().padding(top = headerTotal)
                .offset { IntOffset(0, (-(1f - top.progress.value) * top.panelHeightPx).roundToInt()) }
                .graphicsLayer { alpha = if (top.progress.value > 0f) 1f else 0f }
                .onSizeChanged { top.panelHeightPx = it.height.toFloat() }
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .then(if (shown) Modifier else Modifier.clearAndSetSemantics { }) // hidden items are not read by TalkBack
                .panelDrag(top)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            navEntries.forEach { e ->
                NavItem(e.icon, e.label, selected = nav.destination == e.destination, enabled = shown) { onSelect(e.destination) }
            }
        }

        // ---- header: title, back (sub-pages), chevron. Whole bar toggles on tap and opens on drag down ----
        val open = nav.panelOpen
        Row(
            Modifier.fillMaxWidth().background(surface).statusBarsPadding().height(HeaderHeight).panelDrag(top),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                IconButton(onBack) { Icon(Icons.Default.ArrowBack, "Kembali") }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Row(
                Modifier.weight(1f).fillMaxHeight()
                    .clickable(
                        onClickLabel = if (open) "Tutup menu navigasi" else "Buka menu navigasi",
                        role = Role.Button,
                        onClick = top::toggle,
                    )
                    .semantics { stateDescription = if (open) "Menu terbuka" else "Menu tertutup" }
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.KeyboardArrowDown, null, Modifier.graphicsLayer { rotationZ = top.progress.value * 180f })
            }
        }
    }
}

@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primaryContainer else Color.Transparent)
            .selectable(selected = selected, enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) scheme.primary else scheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(
            label, style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.weight(1f),
        )
        // Selection is shown by weight + check mark + container, not by color alone.
        if (selected) Icon(Icons.Default.Check, null, tint = scheme.primary)
    }
}
