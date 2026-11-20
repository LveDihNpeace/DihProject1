package com.example.waveplayer.data

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.Charset

/** Reads lyrics stored inside the audio file: MP3 (ID3v2 USLT) and FLAC (Vorbis comment LYRICS). */
object EmbeddedLyrics {

    fun read(open: () -> InputStream?): String? = runCatching {
        open()?.use { raw ->
            val ins = DataInputStream(BufferedInputStream(raw))
            val magic = ByteArray(4)
            ins.readFully(magic)
            when {
                magic[0] == 'I'.code.toByte() && magic[1] == 'D'.code.toByte() && magic[2] == '3'.code.toByte() ->
                    readId3(ins, magic[3].toInt())
                String(magic, Charsets.ISO_8859_1) == "fLaC" -> readFlac(ins)
                else -> null
            }
        }
    }.getOrNull()?.replace("\r\n", "\n")?.replace('\r', '\n')?.takeIf { it.isNotBlank() }

    private fun skipFully(ins: InputStream, count: Long) {
        var n = count
        while (n > 0) {
            val s = ins.skip(n)
            if (s <= 0) { if (ins.read() < 0) throw EOFException(); n-- } else n -= s
        }
    }

    private fun synchsafe(b: ByteArray, o: Int) =
        (b[o].toInt() and 0x7F shl 21) or (b[o + 1].toInt() and 0x7F shl 14) or
            (b[o + 2].toInt() and 0x7F shl 7) or (b[o + 3].toInt() and 0x7F)

    private fun be32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF shl 24) or (b[o + 1].toInt() and 0xFF shl 16) or
            (b[o + 2].toInt() and 0xFF shl 8) or (b[o + 3].toInt() and 0xFF)

    // The first 4 bytes ("ID3" + major version) are already consumed.
    private fun readId3(ins: DataInputStream, major: Int): String? {
        val rest = ByteArray(6) // minor, flags, size(4)
        ins.readFully(rest)
        val flags = rest[1].toInt()
        var remaining = synchsafe(rest, 2).toLong()

        if (major >= 3 && (flags and 0x40) != 0) { // extended header
            val ext = ByteArray(4)
            ins.readFully(ext)
            val extSize = if (major == 4) synchsafe(ext, 0) else be32(ext, 0) + 4
            val toSkip = if (major == 4) extSize - 4 else extSize - 4
            skipFully(ins, toSkip.toLong().coerceAtLeast(0))
            remaining -= extSize
        }

        val headerLen = if (major == 2) 6 else 10
        var uslt: String? = null
        var sylt: String? = null
        while (remaining > headerLen) {
            val h = ByteArray(headerLen)
            ins.readFully(h)
            if (h[0].toInt() == 0) break // padding
            val id: String
            val size: Int
            if (major == 2) {
                id = String(h, 0, 3, Charsets.ISO_8859_1)
                size = (h[3].toInt() and 0xFF shl 16) or (h[4].toInt() and 0xFF shl 8) or (h[5].toInt() and 0xFF)
            } else {
                id = String(h, 0, 4, Charsets.ISO_8859_1)
                size = if (major == 4) synchsafe(h, 4) else be32(h, 4)
            }
            remaining -= headerLen + size
            if (size < 0 || remaining < 0) break
            if ((id == "USLT" || id == "ULT" || id == "SYLT" || id == "SLT") && size in 1..2_000_000) {
                val data = ByteArray(size)
                ins.readFully(data)
                if (id == "USLT" || id == "ULT") { if (uslt == null) uslt = decodeUslt(data) }
                else if (sylt == null) sylt = decodeSylt(data)
                if (sylt != null) break
            } else {
                skipFully(ins, size.toLong())
            }
        }
        return sylt ?: uslt
    }

    /** Synchronized lyrics frame (millisecond timestamps only) -> LRC text. */
    private fun decodeSylt(d: ByteArray): String? {
        if (d.size < 8) return null
        val enc = d[0].toInt()
        val format = d[4].toInt() // 1 = MPEG frames, 2 = milliseconds
        if (format != 2) return null
        val charset: Charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        val wide = enc == 1 || enc == 2
        fun terminatorAt(i: Int) = if (wide) i + 1 < d.size && d[i].toInt() == 0 && d[i + 1].toInt() == 0 else d[i].toInt() == 0
        var i = 6 // skip encoding, language, format, content type
        while (i < d.size && !terminatorAt(i)) i += if (wide) 2 else 1 // content descriptor
        i += if (wide) 2 else 1
        val sb = StringBuilder()
        while (i < d.size) {
            val start = i
            while (i < d.size && !terminatorAt(i)) i += if (wide) 2 else 1
            if (i >= d.size) break
            val text = String(d, start, i - start, charset).replace("\n", " ").trim()
            i += if (wide) 2 else 1
            if (i + 4 > d.size) break
            val ms = be32(d, i).toLong().coerceAtLeast(0)
            i += 4
            val min = ms / 60_000; val sec = (ms / 1000) % 60; val cs = (ms % 1000) / 10
            sb.append("[%02d:%02d.%02d]%s\n".format(min, sec, cs, text))
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun decodeUslt(d: ByteArray): String? {
        if (d.size < 5) return null
        val enc = d[0].toInt()
        val charset: Charset = when (enc) {
            1 -> Charsets.UTF_16
            2 -> Charsets.UTF_16BE
            3 -> Charsets.UTF_8
            else -> Charsets.ISO_8859_1
        }
        var i = 4 // skip encoding byte + 3-byte language
        if (enc == 1 || enc == 2) {
            while (i + 1 < d.size && !(d[i].toInt() == 0 && d[i + 1].toInt() == 0)) i += 2
            i += 2
        } else {
            while (i < d.size && d[i].toInt() != 0) i++
            i += 1
        }
        if (i >= d.size) return null
        return String(d, i, d.size - i, charset)
    }

    // "fLaC" marker already consumed.
    private fun readFlac(ins: DataInputStream): String? {
        while (true) {
            val h = ByteArray(4)
            ins.readFully(h)
            val last = (h[0].toInt() and 0x80) != 0
            val type = h[0].toInt() and 0x7F
            val len = (h[1].toInt() and 0xFF shl 16) or (h[2].toInt() and 0xFF shl 8) or (h[3].toInt() and 0xFF)
            if (type == 4 && len < 4_000_000) {
                val data = ByteArray(len)
                ins.readFully(data)
                return parseVorbisComment(data)
            }
            skipFully(ins, len.toLong())
            if (last) return null
        }
    }

    private fun le32(b: ByteArray, o: Int) =
        (b[o].toInt() and 0xFF) or (b[o + 1].toInt() and 0xFF shl 8) or
            (b[o + 2].toInt() and 0xFF shl 16) or (b[o + 3].toInt() and 0xFF shl 24)

    private fun parseVorbisComment(b: ByteArray): String? {
        var p = 0
        val vendor = le32(b, p); p += 4 + vendor
        val count = le32(b, p); p += 4
        repeat(count) {
            if (p + 4 > b.size) return null
            val n = le32(b, p); p += 4
            if (n < 0 || p + n > b.size) return null
            val s = String(b, p, n, Charsets.UTF_8); p += n
            val eq = s.indexOf('=')
            if (eq > 0) {
                val key = s.substring(0, eq).uppercase()
                if (key == "LYRICS" || key == "UNSYNCEDLYRICS" || key == "SYNCEDLYRICS") return s.substring(eq + 1)
            }
        }
        return null
    }
}
