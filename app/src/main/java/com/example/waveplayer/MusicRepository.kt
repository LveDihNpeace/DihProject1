package com.example.waveplayer

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

object MusicRepository {
    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")

    fun loadSongs(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 30000"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, order
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val albumId = c.getLong(albumIdCol)
                songs += Song(
                    id = id,
                    title = c.getString(titleCol) ?: "Unknown",
                    artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                    album = c.getString(albumCol) ?: "Unknown album",
                    albumId = albumId,
                    durationMs = c.getLong(durCol),
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id),
                    artUri = ContentUris.withAppendedId(albumArtBase, albumId),
                )
            }
        }
        return songs
    }
}
