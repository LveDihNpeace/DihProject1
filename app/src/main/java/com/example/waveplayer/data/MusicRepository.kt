package com.example.waveplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/** Reads the whole MediaStore audio index (all folders and volumes visible to the app). */
object MusicRepository {
    private val albumArtBase: Uri = Uri.parse("content://media/external/audio/albumart")
    private const val MIN_DURATION_MS = 30_000

    @Suppress("DEPRECATION") // DATA is only used to derive folder (API < 29) and the .lrc sidecar path.
    fun loadSongs(context: Context, onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }): List<Song> {
        val collection: Uri = if (Build.VERSION.SDK_INT >= 29)
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATA,
        )
        if (Build.VERSION.SDK_INT >= 30) projection += MediaStore.Audio.Media.GENRE
        if (Build.VERSION.SDK_INT >= 29) projection += MediaStore.Audio.Media.RELATIVE_PATH

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= $MIN_DURATION_MS"
        val order = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val unique = LinkedHashMap<String, Song>() // de-duplicate by title+artist+duration
        context.contentResolver.query(collection, projection.toTypedArray(), selection, null, order)?.use { c ->
            val total = c.count
            val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val durCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            val dataCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val genreCol = if (Build.VERSION.SDK_INT >= 30) c.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1
            val relCol = if (Build.VERSION.SDK_INT >= 29) c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH) else -1

            var done = 0
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val title = c.getString(titleCol) ?: "Unknown"
                val artist = c.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
                val duration = c.getLong(durCol)
                val path = c.getString(dataCol)
                val rel = if (relCol >= 0) c.getString(relCol)?.trimEnd('/')?.takeIf { it.isNotBlank() } else null
                val folder = rel
                    ?: path?.let { File(it).parent }?.removePrefix("/storage/emulated/0/")
                    ?: "Unknown folder"
                val key = "${title.lowercase()}|${artist.lowercase()}|${duration / 1000}"
                if (key !in unique) {
                    val albumId = c.getLong(albumIdCol)
                    unique[key] = Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = c.getString(albumCol) ?: "Unknown album",
                        albumId = albumId,
                        genre = (if (genreCol >= 0) c.getString(genreCol) else null)?.takeIf { it.isNotBlank() } ?: "Unknown genre",
                        folder = folder,
                        durationMs = duration,
                        dateAdded = c.getLong(addedCol),
                        uri = ContentUris.withAppendedId(collection, id),
                        artUri = ContentUris.withAppendedId(albumArtBase, albumId),
                        path = path,
                    )
                }
                done++
                if (done % 25 == 0 || done == total) onProgress(done, total)
            }
        }
        return unique.values.toList()
    }
}
