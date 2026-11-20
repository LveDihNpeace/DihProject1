package com.example.waveplayer.data

import androidx.compose.runtime.Immutable

@Immutable
data class Group(val key: String, val name: String, val songs: List<Song>)

/** Everything the UI needs, computed once off the main thread when the scan result changes. */
@Immutable
data class Library(
    val songs: List<Song> = emptyList(),
    val albums: List<Group> = emptyList(),
    val artists: List<Group> = emptyList(),
    val folders: List<Group> = emptyList(),
    val byId: Map<Long, Song> = emptyMap(),
) {
    companion object {
        fun build(list: List<Song>): Library = Library(
            songs = list,
            albums = list.groupBy { it.albumId }.values
                .map { Group("al${it.first().albumId}", it.first().album, it) }.sortedBy { it.name.lowercase() },
            artists = list.groupBy { it.artist }
                .map { Group("ar${it.key}", it.key, it.value) }.sortedBy { it.name.lowercase() },
            folders = list.groupBy { it.folder }
                .map { Group("fo${it.key}", it.key, it.value) }.sortedBy { it.name.lowercase() },
            byId = list.associateBy { it.id },
        )
    }
}
