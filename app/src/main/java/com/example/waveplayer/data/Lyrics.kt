package com.example.waveplayer.data

data class LyricLine(val timeMs: Long?, val text: String)
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val raw: String)

object LrcParser {
    private val timeTag = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val metaTag = Regex("""^\[[A-Za-z#]+:.*]\s*$""")

    /** Parses .lrc (synced, multiple timestamps per line supported) or plain text. */
    fun parse(raw: String): Lyrics? {
        if (raw.isBlank()) return null
        val timed = mutableListOf<LyricLine>()
        val plain = mutableListOf<LyricLine>()
        for (line in raw.lines()) {
            val matches = timeTag.findAll(line).toList()
            if (matches.isEmpty()) {
                if (metaTag.matches(line.trim())) continue
                plain += LyricLine(null, line.trim())
            } else {
                val text = timeTag.replace(line, "").trim()
                for (m in matches) {
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].toLong()
                    val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
                    timed += LyricLine(min * 60_000 + sec * 1_000 + frac, text)
                }
            }
        }
        return if (timed.isNotEmpty()) {
            Lyrics(timed.sortedBy { it.timeMs }, synced = true, raw = raw)
        } else {
            val trimmed = plain.dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
            if (trimmed.isEmpty()) null else Lyrics(trimmed, synced = false, raw = raw)
        }
    }
}
