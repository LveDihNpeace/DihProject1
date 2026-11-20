@file:OptIn(UnstableApi::class)

package com.example.waveplayer.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.flow.MutableStateFlow
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

enum class VocalPreset(val label: String, val vocal: Float, val inst: Float) {
    ORIGINAL("Original", 1f, 1f),
    INSTRUMENTAL("Instrumental", 0f, 1f),
    VOCAL("Vocal only", 1f, 0f),
}

/** Shared (same process) control surface between the UI and the audio processor. */
object VocalEngine {
    val vocalGain = MutableStateFlow(1f)
    val instGain = MutableStateFlow(1f)

    fun apply(p: VocalPreset) { vocalGain.value = p.vocal; instGain.value = p.inst }
    fun setVocal(v: Float) { vocalGain.value = v }
    fun setInst(v: Float) { instGain.value = v }
}

private class Biquad {
    private var b0 = 1.0; private var b1 = 0.0; private var b2 = 0.0
    private var a1 = 0.0; private var a2 = 0.0
    private var x1 = 0.0; private var x2 = 0.0; private var y1 = 0.0; private var y2 = 0.0

    fun lowpass(fs: Int, f: Double, q: Double = 0.7071) {
        val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q); val a0 = 1 + al
        set((1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    }

    fun highpass(fs: Int, f: Double, q: Double = 0.7071) {
        val w = 2 * PI * f / fs; val c = cos(w); val al = sin(w) / (2 * q); val a0 = 1 + al
        set((1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - al) / a0)
    }

    private fun set(nb0: Double, nb1: Double, nb2: Double, na1: Double, na2: Double) {
        b0 = nb0; b1 = nb1; b2 = nb2; a1 = na1; a2 = na2
        reset()
    }

    fun reset() { x1 = 0.0; x2 = 0.0; y1 = 0.0; y2 = 0.0 }

    fun process(x: Double): Double {
        val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1; x1 = x; y2 = y1; y1 = y
        return y
    }
}

/**
 * Real-time stereo source separation (NOT a neural network).
 * Vocal estimate V = band-pass(150 Hz..5 kHz) of the center (mid) channel.
 *   out = inst * (in - V) + vocal * V
 * Works on 16-bit stereo PCM; mono / other formats are passed through untouched.
 */
class VocalProcessor : BaseAudioProcessor() {
    private val hp = List(2) { Biquad() }
    private val lp = List(2) { Biquad() }

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            return AudioFormat.NOT_SET
        }
        hp.forEach { it.highpass(inputAudioFormat.sampleRate, 150.0) }
        lp.forEach { it.lowpass(inputAudioFormat.sampleRate, 5000.0) }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val vg = VocalEngine.vocalGain.value
        val ig = VocalEngine.instGain.value

        if (vg == 1f && ig == 1f) {
            out.put(inputBuffer)
        } else {
            while (inputBuffer.remaining() >= 4) {
                val l = inputBuffer.short.toDouble()
                val r = inputBuffer.short.toDouble()
                var v = (l + r) * 0.5
                for (f in hp) v = f.process(v)
                for (f in lp) v = f.process(v)
                out.putShort(clamp(ig * (l - v) + vg * v))
                out.putShort(clamp(ig * (r - v) + vg * v))
            }
            inputBuffer.position(inputBuffer.limit())
        }
        out.flip()
    }

    private fun clamp(x: Double): Short = x.roundToInt().coerceIn(-32768, 32767).toShort()

    override fun onFlush() { hp.forEach { it.reset() }; lp.forEach { it.reset() } }
    override fun onReset() { onFlush() }
}
