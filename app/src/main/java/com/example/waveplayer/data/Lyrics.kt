package com.example.waveplayer.data

import androidx.compose.runtime.Immutable

/** One word with its own start time (only exists when the source or the user provided real word timings). */
@Immutable
data class LyricWord(val startMs: Long, val text: String)

@Immutable
data class LyricLine(
    val timeMs: Long?,
    val text: String,
    val words: List<LyricWord>? = null,
    val endMs: Long? = null,
)

@Immutable
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val raw: String)

/**
 * Tolerant LRC parser.
 *  - [mm:ss], [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx]; several tags in front of one line
 *  - several [tag]text segments on ONE physical line (compressed LRC) become separate lines
 *  - enhanced word-level tags "<mm:ss.xxx>word" give real per-word timings (never invented)
 *  - [offset:ms], metadata tags ([ti:], [ar:], ...) are not shown
 *  - any line break style; BOM and NUL characters are dropped
 *  - plain (unsynced) text is kept line by line
 */
object LrcParser {
    private val lineBreak = Regex("\r\n|[\r\n\u2028\u2029\u0085]")
    private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val wordTag = Regex("""<(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?>""")
    private val metaTag = Regex("""^\[([A-Za-z#]+):(.*)]\s*$""")
    private val spaces = Regex("""\s+""")
    private const val BREAK_GAP_MS = 8_000L // keep an empty "instrumental" line only for gaps this long

    private fun toMs(m: MatchResult): Long {
        val min = m.groupValues[1].toLong()
        val sec = m.groupValues[2].toLong()
        val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
        return min * 60_000 + sec * 1_000 + frac
    }

    private fun clean(text: String): String {
        val sb = StringBuilder()
        var last = 0
        for (m in wordTag.findAll(text)) {
            sb.append(text, last, m.range.first)
            val before = sb.lastOrNull()
            val after = text.getOrNull(m.range.last + 1)
            if (before != null && after != null && before.isLetterOrDigit() && after.isLetterOrDigit()) sb.append(' ')
            last = m.range.last + 1
        }
        sb.append(text, last, text.length)
        return sb.toString().replace(spaces, " ").trim()
    }

    private fun isCjk(c: Char) = c.code in 0x2E80..0x9FFF || c.code in 0xAC00..0xD7AF

    /** Builds a line from its (still tagged) text. Word timings are kept only if the source really has them. */
    private fun makeLine(t: Long, raw: String): LyricLine {
        val tags = wordTag.findAll(raw).toList()
        if (tags.isEmpty()) return LyricLine(t, clean(raw))

        val parts = ArrayList<LyricWord>()
        val lead = raw.substring(0, tags[0].range.first)
        if (lead.isNotBlank()) parts += LyricWord(t, lead)
        for ((i, m) in tags.withIndex()) {
            val to = if (i + 1 < tags.size) tags[i + 1].range.first else raw.length
            parts += LyricWord(toMs(m), raw.substring(m.range.last + 1, to))
        }
        val merged = ArrayList<LyricWord>()
        for (w in parts) {
            if (w.text.isBlank()) {
                if (merged.isNotEmpty()) merged[merged.lastIndex] = merged.last().copy(text = merged.last().text + w.text)
            } else merged += w
        }
        if (merged.isEmpty()) return LyricLine(t, "")

        // Sources that put a tag between words but no space at all: the tag is the word separator (Latin scripts only).
        val tagIsSeparator = merged.size >= 2 &&
            merged.none { w -> w.text.any { it.isWhitespace() } } &&
            merged.all { w -> w.text.none { isCjk(it) } }
        val words = merged.mapIndexed { i, w ->
            var s = w.text
            if (i == 0) s = s.trimStart()
            if (i == merged.lastIndex) s = s.trimEnd() else if (tagIsSeparator) s += " "
            w.copy(text = s)
        }
        val text = words.joinToString("") { it.text }.replace(spaces, " ").trim()
        return LyricLine(t, text, if (words.size >= 2) words else null)
    }

    /** Timestamp tags followed by text on a single line. */
    private fun segments(line: String): List<Pair<List<Long>, String>> {
        val matches = timeTag.findAll(line).toList()
        if (matches.isEmpty()) return emptyList()
        val out = ArrayList<Pair<List<Long>, String>>()
        var pending = ArrayList<Long>()
        var cursor = 0
        for (m in matches) {
            val between = line.substring(cursor, m.range.first)
            if (pending.isNotEmpty() && between.isNotBlank()) {
                out += pending to between
                pending = ArrayList()
            }
            pending += toMs(m)
            cursor = m.range.last + 1
        }
        if (pending.isNotEmpty()) out += pending to line.substring(cursor)
        return out
    }

    fun parse(rawInput: String): Lyrics? {
        val raw = rawInput.replace("\uFEFF", "").replace("\u0000", "")
        if (raw.isBlank()) return null

        var offset = 0L
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<LyricLine>()

        for (rawLine in raw.split(lineBreak)) {
            val line = rawLine.trim()
            if (line.isEmpty()) { plain += LyricLine(null, ""); continue }

            val meta = metaTag.matchEntire(line)
            if (meta != null) {
                if (meta.groupValues[1].equals("offset", ignoreCase = true)) {
                    offset = meta.groupValues[2].trim().removePrefix("+").toLongOrNull() ?: 0L
                }
                continue
            }

            val segs = segments(line)
            if (segs.isNotEmpty()) {
                for ((times, text) in segs) for (t in times) timed += makeLine(t, text)
            } else {
                val firstWord = wordTag.find(line)
                if (firstWord != null) timed += makeLine(toMs(firstWord), line)
                else plain += LyricLine(null, clean(line))
            }
        }

        if (timed.isEmpty()) {
            val out = ArrayList<LyricLine>()
            for (l in plain) {
                if (l.text.isBlank() && (out.isEmpty() || out.last().text.isBlank())) continue
                out += l
            }
            while (out.isNotEmpty() && out.last().text.isBlank()) out.removeAt(out.lastIndex)
            return if (out.isEmpty()) null else Lyrics(out, synced = false, raw = raw)
        }

        // Positive [offset] makes lyrics appear earlier. Stable sort keeps translation lines in file order.
        val sorted = timed.map { l ->
            l.copy(
                timeMs = ((l.timeMs ?: 0L) - offset).coerceAtLeast(0L),
                words = l.words?.map { it.copy(startMs = (it.startMs - offset).coerceAtLeast(0L)) },
            )
        }.sortedBy { it.timeMs }

        // Drop empty "end of line" markers; keep an empty line only for a long instrumental gap.
        val cleaned = ArrayList<LyricLine>()
        for ((i, l) in sorted.withIndex()) {
            if (l.text.isNotBlank()) { cleaned += l; continue }
            val next = sorted.getOrNull(i + 1)?.timeMs
            val gap = if (next == null) Long.MAX_VALUE else next - (l.timeMs ?: 0L)
            if (gap >= BREAK_GAP_MS && cleaned.isNotEmpty() && cleaned.last().text.isNotBlank()) cleaned += l
        }
        if (cleaned.none { it.text.isNotBlank() }) return null

        // End time of each line = next later timestamp (needed to animate the last word).
        val times = cleaned.map { it.timeMs ?: 0L }
        val withEnd = cleaned.mapIndexed { i, l ->
            var j = i + 1
            while (j < times.size && times[j] <= times[i]) j++
            val end = if (j < times.size) times[j] else (l.words?.last()?.startMs ?: times[i]) + 3_000L
            l.copy(endMs = end)
        }
        return Lyrics(withEnd, synced = true, raw = raw)
    }
}
