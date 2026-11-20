package com.example.waveplayer.data

data class LyricLine(val timeMs: Long?, val text: String)
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val raw: String)

/**
 * Tolerant LRC parser.
 *  - [mm:ss], [mm:ss.xx], [mm:ss.xxx], [mm:ss:xx]; several tags in front of one line
 *  - several [tag]text segments on ONE physical line (compressed LRC) become separate lines
 *  - enhanced / word-level tags <mm:ss.xxx> are removed from the text (the first one is used as line time
 *    when a line has no [..] tag)
 *  - [offset:ms], metadata tags ([ti:], [ar:], ...) are not shown
 *  - any line break style (\r\n, \n, \r, U+2028/2029/0085); BOM and NUL characters are dropped
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

    /** Removes word-level <..> tags; inserts a space when the tag sits between two letters/digits. */
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

    /** [tags...]text [tags...]text ... on a single line. Empty list when the line has no [mm:ss] tag. */
    private fun segments(line: String): List<Pair<List<Long>, String>> {
        val matches = timeTag.findAll(line).toList()
        if (matches.isEmpty()) return emptyList()
        val out = ArrayList<Pair<List<Long>, String>>()
        var pending = ArrayList<Long>()
        var cursor = 0
        for (m in matches) {
            val between = line.substring(cursor, m.range.first)
            if (pending.isNotEmpty() && between.isNotBlank()) {
                out += pending to clean(between)
                pending = ArrayList()
            }
            pending += toMs(m)
            cursor = m.range.last + 1
        }
        if (pending.isNotEmpty()) out += pending to clean(line.substring(cursor))
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
                for ((times, text) in segs) for (t in times) timed += LyricLine(t, text)
            } else {
                val firstWord = wordTag.find(line)
                val text = clean(line)
                if (firstWord != null) timed += LyricLine(toMs(firstWord), text)
                else plain += LyricLine(null, text)
            }
        }

        if (timed.isEmpty()) {
            // Unsynchronized text: trim blank edges and collapse repeated blank lines.
            val out = ArrayList<LyricLine>()
            for (l in plain) {
                if (l.text.isBlank() && (out.isEmpty() || out.last().text.isBlank())) continue
                out += l
            }
            while (out.isNotEmpty() && out.last().text.isBlank()) out.removeAt(out.lastIndex)
            return if (out.isEmpty()) null else Lyrics(out, synced = false, raw = raw)
        }

        // Positive [offset] makes lyrics appear earlier. Stable sort keeps translation lines in file order.
        val sorted = timed.map { LyricLine(((it.timeMs ?: 0L) - offset).coerceAtLeast(0L), it.text) }
            .sortedBy { it.timeMs }

        // Drop empty "end of line" markers; keep an empty line only for a long instrumental gap.
        val cleaned = ArrayList<LyricLine>()
        for ((i, l) in sorted.withIndex()) {
            if (l.text.isNotBlank()) { cleaned += l; continue }
            val next = sorted.getOrNull(i + 1)?.timeMs
            val gap = if (next == null) Long.MAX_VALUE else next - (l.timeMs ?: 0L)
            if (gap >= BREAK_GAP_MS && cleaned.isNotEmpty() && cleaned.last().text.isNotBlank()) cleaned += l
        }
        return if (cleaned.none { it.text.isNotBlank() }) null else Lyrics(cleaned, synced = true, raw = raw)
    }
}
