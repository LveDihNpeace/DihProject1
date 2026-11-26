package com.example.waveplayer.ui

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.waveplayer.data.Song

private class SmartPlaylist(val route: PlaylistRoute, val title: String, val songs: List<Song>, val icon: ImageVector)

/**
 * Playlist hub: Smart Playlists (Favorites, Recently Played) as a two-column grid of cover tiles with song counts.
 * The cover is a 2x2 collage of the playlist's album art (one cover if fewer than four different albums, an icon
 * tile when the list is empty). User-made playlists are not available yet, so nothing for them is shown
 * (no create button, no placeholder); they will be added to this grid later.
 */
@Composable
fun PlaylistHubScreen(favorites: List<Song>, recent: List<Song>, onOpen: (PlaylistRoute) -> Unit) {
    val lists = remember(favorites, recent) {
        listOf(
            SmartPlaylist(PlaylistRoute.FAVORITES, "Favorites", favorites, Icons.Default.Favorite),
            SmartPlaylist(PlaylistRoute.RECENT, "Recently Played", recent, Icons.Default.History),
        )
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item(key = "label", span = { GridItemSpan(maxLineSpan) }) {
            Text(
                "Smart Playlists", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 4.dp),
            )
        }
        items(lists, key = { it.route.name }) { p -> SmartPlaylistTile(p, onOpen) }
    }
}

@Composable
private fun SmartPlaylistTile(p: SmartPlaylist, onOpen: (PlaylistRoute) -> Unit) {
    Column(
        Modifier.clip(MaterialTheme.shapes.large)
            .clickable(onClickLabel = "Buka ${p.title}", role = Role.Button) { onOpen(p.route) },
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
        ) {
            PlaylistCover(p.songs, p.icon)
            // Small badge so the playlist type is recognisable even when the cover is album art.
            Box(
                Modifier.align(Alignment.BottomStart).padding(10.dp).size(32.dp).clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) { Icon(p.icon, null, Modifier.size(18.dp), tint = Color.White) }
        }
        Text(
            p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 4.dp, top = 10.dp),
        )
        Text(
            "${p.songs.size} lagu", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PlaylistCover(songs: List<Song>, icon: ImageVector) {
    val arts = remember(songs) { songs.distinctBy { it.albumId }.take(4).map { it.artUri } }
    when {
        arts.size >= 4 -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                CoverImage(arts[0], Modifier.weight(1f).fillMaxHeight())
                CoverImage(arts[1], Modifier.weight(1f).fillMaxHeight())
            }
            Row(Modifier.weight(1f).fillMaxWidth()) {
                CoverImage(arts[2], Modifier.weight(1f).fillMaxHeight())
                CoverImage(arts[3], Modifier.weight(1f).fillMaxHeight())
            }
        }
        arts.isNotEmpty() -> CoverImage(arts[0], Modifier.fillMaxSize())
        else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CoverImage(uri: Uri, modifier: Modifier) {
    AsyncImage(uri, null, modifier, contentScale = ContentScale.Crop)
}
