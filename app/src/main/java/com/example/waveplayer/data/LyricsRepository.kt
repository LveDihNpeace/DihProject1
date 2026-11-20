package com.example.waveplayer.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
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
 *
 * Word timings made by the user in the editor are stored separately (overlay) and applied on top of any source.
 * Nothing is ever generated automatically: a line has word timings only if the file has them or the user set them.
 */
class LyricsRepository(context: Context) {
    private val ctx = context.applicationContext
    private val dir = File(ctx.filesDir, "lyrics").apply { mkdirs() }

    fun load(song: Song): Lyrics? {
        val base = loadRaw(song) ?: return null
        return applyOverlay(base, readOverlay(song.id))
    }

    private fun loadRaw(song: Song): Lyrics? {
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
        val embedded = EmbeddedLyrics.read { ctx.contentResolver.openInputStream(song.uri) }
        return embedded?.let { LrcParser.parse(it) }
    }

    fun parse(songId: Long, text: String): Lyrics? =
        LrcParser.parse(text)?.let { applyOverlay(it, readOverlay(songId)) }

    fun save(songId: Long, text: String) {
        dir.mkdirs()
        File(dir, "$songId.lrc").writeText(text)
    }

    fun delete(songId: Long) {
        File(dir, "$songId.lrc").delete()
        overlayFile(songId).delete()
    }

    // ---- word timing overlay -------------------------------------------------------------------------------

    private fun overlayFile(id: Long) = File(dir, "$id.words.json")
    private fun key(line: LyricLine) = "${line.timeMs}|${line.text}"

    private fun readOverlay(id: Long): JSONObject =
        runCatching { JSONObject(overlayFile(id).readText()) }.getOrDefault(JSONObject())

    fun saveWordTiming(songId: Long, line: LyricLine, times: List<Long>) {
        val o = readOverlay(songId)
        o.put(key(line), JSONArray(times))
        overlayFile(songId).writeText(o.toString())
    }

    fun clearWordTiming(songId: Long, line: LyricLine) {
        val o = readOverlay(songId)
        o.remove(key(line))
        overlayFile(songId).writeText(o.toString())
    }

    private val ws = Regex("""\s+""")

    private fun applyOverlay(lyrics: Lyrics, overlay: JSONObject): Lyrics {
        if (overlay.length() == 0) return lyrics
        val lines = lyrics.lines.map { line ->
            val arr = overlay.optJSONArray(key(line)) ?: return@map line
            val parts = line.text.trim().split(ws).filter { it.isNotEmpty() }
            val times = List(arr.length()) { arr.getLong(it) }
            val ascending = times.zipWithNext().all { (a, b) -> a <= b }
            if (parts.size < 2 || parts.size != times.size || !ascending) line
            else line.copy(words = parts.mapIndexed { i, p -> LyricWord(times[i], if (i < parts.lastIndex) "$p " else p) })
        }
        return lyrics.copy(lines = lines)
    }

    // ---- online ----------------------------------------------------------------------------------------------

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
