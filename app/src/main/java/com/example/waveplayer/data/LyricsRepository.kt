package com.example.waveplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Lyrics sources, in order:
 *  1. lyrics saved by the user / fetched earlier (app storage, never searched twice)
 *  2. sidecar .lrc / .txt next to the audio file (only readable where the OS allows direct file access)
 *  3. lyrics embedded in the file tags (MP3 / FLAC)
 *  4. online lookup on LRCLIB (manual action, needs internet)
 */
class LyricsRepository(context: Context) {
    private val ctx = context.applicationContext
    private val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }

    fun load(song: Song): Lyrics? {
        val saved = File(dir, "${song.id}.lrc")
        if (saved.exists()) return LrcParser.parse(saved.readText())
        val path = song.path
        if (path != null) {
            val base = path.substringBeforeLast('.', path)
            for (ext in listOf("lrc", "txt")) {
                val text = runCatching { File("$base.$ext").takeIf { it.canRead() }?.readText() }.getOrNull()
                if (text != null) return LrcParser.parse(text)
            }
        }
        // Lyrics stored inside the audio tags (MP3 USLT, FLAC LYRICS).
        val embedded = EmbeddedLyrics.read { ctx.contentResolver.openInputStream(song.uri) }
        return embedded?.let { LrcParser.parse(it) }
    }

    fun save(songId: Long, text: String) {
        dir.mkdirs()
        File(dir, "$songId.lrc").writeText(text)
    }

    fun delete(songId: Long) {
        File(dir, "$songId.lrc").delete()
    }

    suspend fun fetchOnline(song: Song): String? = withContext(Dispatchers.IO) {
        runCatching {
            fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
            val query = "artist_name=${enc(song.artist)}&track_name=${enc(song.title)}" +
                "&album_name=${enc(song.album)}&duration=${song.durationMs / 1000}"
            val conn = URL("https://lrclib.net/api/get?$query").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "WavePlayer/1.0")
            try {
                if (conn.responseCode != 200) null
                else {
                    val o = JSONObject(conn.inputStream.bufferedReader().readText())
                    fun JSONObject.str(k: String): String? = if (isNull(k)) null else getString(k).takeIf { it.isNotBlank() }
                    o.str("syncedLyrics") ?: o.str("plainLyrics")
                }
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }
}
