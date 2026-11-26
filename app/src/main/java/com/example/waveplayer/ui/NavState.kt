package com.example.waveplayer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/** The four top-level destinations of the navigation panel. */
enum class Destination { SONGS, PLAYLIST, SCAN, SETTINGS }

/** Pages inside the Playlist destination. User-made playlists will be added here later. */
enum class PlaylistRoute { HUB, FAVORITES, RECENT }

/** What the system Back button should do right now. */
enum class BackTarget { NOW_PLAYING, PANEL, PLAYLIST_HUB, SONGS, NONE }

/**
 * Single source of truth for Back. Exactly one target applies at a time, so the BackHandlers that act on it
 * can never compete: Now Playing, then the open panel, then Playlist sub-page -> Hub, then any other page -> Songs.
 */
fun backTarget(
    nowPlayingOpen: Boolean,
    panelShown: Boolean,
    destination: Destination,
    route: PlaylistRoute,
): BackTarget = when {
    nowPlayingOpen -> BackTarget.NOW_PLAYING
    panelShown -> BackTarget.PANEL
    destination == Destination.PLAYLIST && route != PlaylistRoute.HUB -> BackTarget.PLAYLIST_HUB
    destination != Destination.SONGS -> BackTarget.SONGS
    else -> BackTarget.NONE
}

/**
 * Custom navigation state (no navigation library). Songs is the start page.
 * Only [destination] and [playlistRoute] are saved across rotation / process death; the panel starts closed and
 * Now Playing starts hidden, exactly like before.
 *
 * [panelOpen] is the logical open/closed flag. It is written only by [TopNavState], which animates the panel to match.
 */
@Stable
class NavState(
    initialDestination: Destination = Destination.SONGS,
    initialRoute: PlaylistRoute = PlaylistRoute.HUB,
) {
    var destination by mutableStateOf(initialDestination)
        private set
    var playlistRoute by mutableStateOf(initialRoute)
        private set
    var panelOpen by mutableStateOf(false)
        internal set
    var nowPlayingOpen by mutableStateOf(false)
        private set
    var startLyrics by mutableStateOf(false)
        private set

    /** Choosing Playlist always lands on the Hub, even when a sub-page was open. */
    fun select(d: Destination) {
        if (d == Destination.PLAYLIST) playlistRoute = PlaylistRoute.HUB
        destination = d
    }

    fun openPlaylistRoute(r: PlaylistRoute) {
        destination = Destination.PLAYLIST
        playlistRoute = r
    }

    fun backToHub() { playlistRoute = PlaylistRoute.HUB }

    fun openNowPlaying(withLyrics: Boolean) {
        startLyrics = withLyrics
        nowPlayingOpen = true
    }

    fun closeNowPlaying() {
        nowPlayingOpen = false
        startLyrics = false
    }

    companion object {
        /** Saved as "DESTINATION|ROUTE"; unknown or missing names fall back to Songs / Hub. */
        val Saver: Saver<NavState, String> = Saver(
            save = { "${it.destination.name}|${it.playlistRoute.name}" },
            restore = { saved ->
                val parts = saved.split('|')
                NavState(
                    Destination.values().firstOrNull { it.name == parts.getOrNull(0) } ?: Destination.SONGS,
                    PlaylistRoute.values().firstOrNull { it.name == parts.getOrNull(1) } ?: PlaylistRoute.HUB,
                )
            },
        )
    }
}

@Composable
fun rememberNavState(): NavState = rememberSaveable(saver = NavState.Saver) { NavState() }
