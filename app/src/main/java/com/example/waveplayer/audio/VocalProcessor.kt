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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

enum class VocalPreset(val label: String, val vocal: Float, val inst: Float) {
    ORIGINAL("Original", 1f, 1f),
    INSTRUMENTAL("Instrumental", 0f, 1f),
    VOCAL("Vocal", 1f, 0f),
}

enum class VocalStatus { IDLE, ACTIVE, UNSUPPORTED, MONO_LIKE }

/** Shared (same process) control surface between the UI and the audio processor. */
object VocalEngine {
    val vocalGain = MutableStateFlow(1f)
    val instGain = MutableStateFlow(1f)
    val status = MutableStateFlow(VocalStatus.IDLE)

    fun apply(p: VocalPreset) { vocalGain.value = p.vocal; instGain.value = p.inst }
    fun setVocal(v: Float) { vocalGain.value = v }
    fun setInst(v: Float) { instGain.value = v }
}

private class Fft(private val n: Int) {
    private val cosT = DoubleArray(n / 2) { cos(2 * PI * it / n) }
    private val sinT = DoubleArray(n / 2) { sin(2 * PI * it / n) }
    private val rev = IntArray(n).also { r ->
        val bits = Integer.numberOfTrailingZeros(n)
        for (i in 0 until n) r[i] = Integer.reverse(i) ushr (32 - bits)
    }

    fun transform(re: DoubleArray, im: DoubleArray, inverse: Boolean) {
        for (i in 0 until n) {
            val j = rev[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var size = 2
        while (size <= n) {
            val halfSize = size / 2
            val step = n / size
            var i = 0
            while (i < n) {
                var k = 0
                for (j in i until i + halfSize) {
                    val wr = cosT[k]
                    val wi = if (inverse) sinT[k] else -sinT[k]
                    val xr = re[j + halfSize]; val xi = im[j + halfSize]
                    val tr = xr * wr - xi * wi
                    val ti = xr * wi + xi * wr
                    re[j + halfSize] = re[j] - tr; im[j + halfSize] = im[j] - ti
                    re[j] += tr; im[j] += ti
                    k += step
                }
                i += size
            }
            size *= 2
        }
        if (inverse) {
            val scale = n.toDouble() // same value as dividing by the Int n, without mixing Double and Int in `/=`
            for (k in 0 until n) {
                re[k] = re[k] / scale
                im[k] = im[k] / scale
            }
        }
    }
}

/**
 * Real-time stereo source separation (spectral soft-mask, NOT a neural network).
 *
 * STFT (2048 / hop 512, Hann). For every frequency bin the "center-ness" s = 2*Re(L*conj(R)) / (|L|^2+|R|^2)
 * measures how similar L and R are in level and phase. Center-panned content (typically lead vocal) gets a
 * high mask; the mask is also zero below ~100 Hz and ramps up to 300 Hz so bass/kick stay in the instrumental.
 *   V = mask * (L+R)/2        (vocal estimate)
 *   out = inst * (in - V) + vocal * V
 * Original (1,1) is a bit-exact STFT reconstruction. Latency is a constant 1536 samples (~35 ms).
 * Only 16-bit stereo PCM is processed; anything else is passed through and reported as UNSUPPORTED.
 */
class VocalProcessor : BaseAudioProcessor() {
    private val n = 2048
    private val hop = 512
    private val half = n / 2
    private val window = DoubleArray(n) { 0.5 - 0.5 * cos(2 * PI * it / n) }
    private val norm = 1.0 / 1.5 // sum of squared periodic Hann windows at 75% overlap
    private val fft = Fft(n)

    private val re = DoubleArray(n)
    private val im = DoubleArray(n)
    private val inL = DoubleArray(n)
    private val inR = DoubleArray(n)
    private val accL = DoubleArray(n)
    private val accR = DoubleArray(n)
    private var fill = 0

    private val lr = DoubleArray(half + 1)
    private val li = DoubleArray(half + 1)
    private val rr = DoubleArray(half + 1)
    private val ri = DoubleArray(half + 1)
    private val raw = DoubleArray(half + 1)
    private val prevMask = DoubleArray(half + 1)
    private var freqWeight = DoubleArray(half + 1) { 1.0 }
    private var sideRatio = 1.0 // smoothed side/mid energy ratio, to detect near-mono material
    private var monoFlag = false
    private var wasBypass = true
    private var warm = 0

    override fun onConfigure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT || inputAudioFormat.channelCount != 2) {
            VocalEngine.status.value = VocalStatus.UNSUPPORTED
            return AudioFormat.NOT_SET
        }
        val fs = inputAudioFormat.sampleRate.toDouble()
        freqWeight = DoubleArray(half + 1) { k ->
            val f = k * fs / n
            when {
                f <= 100.0 -> 0.0
                f < 300.0 -> (f - 100.0) / 200.0
                f <= 9000.0 -> 1.0
                f < 16000.0 -> 1.0 - 0.5 * (f - 9000.0) / 7000.0
                else -> 0.5
            }
        }
        resetState()
        VocalEngine.status.value = VocalStatus.ACTIVE
        return inputAudioFormat
    }

    private fun resetState() {
        inL.fill(0.0); inR.fill(0.0); accL.fill(0.0); accR.fill(0.0); prevMask.fill(0.0)
        fill = 0; sideRatio = 1.0; monoFlag = false; wasBypass = true; warm = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val frames = inputBuffer.remaining() / 4
        if (frames == 0) { inputBuffer.position(inputBuffer.limit()); return }
        val completed = (fill + frames) / hop
        val out = if (completed > 0) replaceOutputBuffer(completed * hop * 4) else null
        repeat(frames) {
            val l = inputBuffer.short.toDouble()
            val r = inputBuffer.short.toDouble()
            feed(l, r, out)
        }
        out?.flip()
    }

    override fun onQueueEndOfStream() {
        // Push the pipeline's remaining samples out with silence.
        val zeros = (n - hop) + (hop - fill) % hop
        val completed = (fill + zeros) / hop
        val out = replaceOutputBuffer(completed * hop * 4)
        repeat(zeros) { feed(0.0, 0.0, out) }
        out.flip()
        resetState()
    }

    override fun onFlush() { resetState() }
    override fun onReset() { resetState() }

    private fun feed(l: Double, r: Double, out: ByteBuffer?) {
        inL[n - hop + fill] = l
        inR[n - hop + fill] = r
        fill++
        if (fill < hop) return
        val bypass = VocalEngine.vocalGain.value == 1f && VocalEngine.instGain.value == 1f
        if (bypass) {
            // Original mode costs no FFT: the oldest 'hop' samples of the window are exactly what the STFT path
            // would output, so latency stays identical and switching modes never clicks.
            for (i in 0 until hop) { out?.putShort(clamp(inL[i])); out?.putShort(clamp(inR[i])) }
            accL.fill(0.0); accR.fill(0.0)
            wasBypass = true
        } else {
            if (wasBypass) { warm = 3; wasBypass = false } // let the overlap-add settle before using its output
            processFrame()
            if (warm > 0) {
                warm--
                for (i in 0 until hop) { out?.putShort(clamp(inL[i])); out?.putShort(clamp(inR[i])) }
            } else {
                for (i in 0 until hop) { out?.putShort(clamp(accL[i])); out?.putShort(clamp(accR[i])) }
            }
            System.arraycopy(accL, hop, accL, 0, n - hop); accL.fill(0.0, n - hop, n)
            System.arraycopy(accR, hop, accR, 0, n - hop); accR.fill(0.0, n - hop, n)
        }
        System.arraycopy(inL, hop, inL, 0, n - hop)
        System.arraycopy(inR, hop, inR, 0, n - hop)
        fill = 0
    }

    private fun processFrame() {
        val vg = VocalEngine.vocalGain.value.toDouble()
        val ig = VocalEngine.instGain.value.toDouble()

        // Two real signals in one complex FFT: z = L + iR
        for (i in 0 until n) { re[i] = inL[i] * window[i]; im[i] = inR[i] * window[i] }
        fft.transform(re, im, inverse = false)
        var midE = 1e-9
        var sideE = 0.0
        for (k in 0..half) {
            val k2 = (n - k) % n
            val a = re[k]; val b = im[k]; val c = re[k2]; val d = im[k2]
            lr[k] = (a + c) / 2; li[k] = (b - d) / 2
            rr[k] = (b + d) / 2; ri[k] = (c - a) / 2
            val sr = lr[k] - rr[k]; val si = li[k] - ri[k]
            val mr = lr[k] + rr[k]; val mi = li[k] + ri[k]
            sideE += sr * sr + si * si
            midE += mr * mr + mi * mi
        }

        // Near-mono detection (side energy < -30 dB of mid): separation cannot work on such material.
        sideRatio = 0.95 * sideRatio + 0.05 * (sideE / midE)
        val mono = sideRatio < 0.001
        if (mono != monoFlag) {
            monoFlag = mono
            VocalEngine.status.value = if (mono) VocalStatus.MONO_LIKE else VocalStatus.ACTIVE
        }

        for (k in 0..half) {
            val denom = lr[k] * lr[k] + li[k] * li[k] + rr[k] * rr[k] + ri[k] * ri[k] + 1e-9
            val s = (2 * (lr[k] * rr[k] + li[k] * ri[k]) / denom).coerceIn(0.0, 1.0)
            raw[k] = s.pow(3) * freqWeight[k]
        }

        // Output spectrum Y = OL + i*OR (both outputs are real, so Hermitian symmetry is restored below).
        for (k in 0..half) {
            val m0 = raw[maxOf(k - 1, 0)]; val m2 = raw[minOf(k + 1, half)]
            val sm = 0.25 * m0 + 0.5 * raw[k] + 0.25 * m2
            val m = 0.65 * sm + 0.35 * prevMask[k]
            prevMask[k] = m
            val mr = (lr[k] + rr[k]) / 2; val mi = (li[k] + ri[k]) / 2
            val vr = m * mr; val vi = m * mi
            val olr = ig * (lr[k] - vr) + vg * vr; val oli = ig * (li[k] - vi) + vg * vi
            val orr = ig * (rr[k] - vr) + vg * vr; val ori = ig * (ri[k] - vi) + vg * vi
            if (k == 0 || k == half) {
                re[k] = olr; im[k] = orr
            } else {
                re[k] = olr - ori; im[k] = oli + orr
                re[n - k] = olr + ori; im[n - k] = orr - oli
            }
        }
        fft.transform(re, im, inverse = true)
        for (i in 0 until n) {
            accL[i] += re[i] * window[i] * norm
            accR[i] += im[i] * window[i] * norm
        }
    }

    private fun clamp(x: Double): Short = x.roundToInt().coerceIn(-32768, 32767).toShort()
}
