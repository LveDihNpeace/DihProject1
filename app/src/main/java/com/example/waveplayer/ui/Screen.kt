package com.example.waveplayer.ui

import kotlinx.coroutines.flow.MutableStateFlow

enum class Screen { HOME, SONGS, ALBUMS, ARTISTS, FOLDERS, FAVORITES, RECENT, SETTINGS }

/** Launcher shortcuts set an action here (MainActivity); the UI consumes and clears it. */
object ShortcutBus {
    const val PREFIX = "com.example.waveplayer.SHORTCUT_"
    const val FAVORITES = PREFIX + "FAVORITES"
    const val RECENT = PREFIX + "RECENT"
    const val LYRICS = PREFIX + "LYRICS"
    val action = MutableStateFlow<String?>(null)
}
