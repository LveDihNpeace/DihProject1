package com.example.waveplayer.audio

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.roundToInt

data class EqState(
    val available: Boolean = false,
    val enabled: Boolean = false,
    val preset: String = "Normal",
    val bandFreqsHz: List<Int> = emptyList(),
    val levels: List<Int> = emptyList(), // millibel
    val minLevel: Int = 0,
    val maxLevel: Int = 0,
    val bassSupported: Boolean = false,
    val bass: Int = 0,                   // 0..1000
    val virtSupported: Boolean = false,
    val virt: Int = 0,                   // 0..1000
)

/**
 * Wraps android.media.audiofx effects attached to the ExoPlayer audio session.
 * Lives in the playback service process (same as the UI), effects are released with the service.
 */
object EqEngine {
    val presetNames = listOf("Normal", "Rock", "Pop", "Classical", "Jazz", "Bass Boost", "Treble Boost", "Vocal")

    // (frequency Hz, gain dB) control points; interpolated on a log-frequency axis for any number of device bands.
    private val curves: Map<String, List<Pair<Double, Double>>> = mapOf(
        "Rock" to listOf(60.0 to 5.0, 230.0 to 3.0, 910.0 to -1.0, 3600.0 to 3.0, 14000.0 to 5.0),
        "Pop" to listOf(60.0 to -1.0, 230.0 to 2.0, 910.0 to 4.0, 3600.0 to 2.0, 14000.0 to -1.0),
        "Classical" to listOf(60.0 to 3.0, 230.0 to 2.0, 910.0 to -1.0, 3600.0 to 2.0, 14000.0 to 3.0),
        "Jazz" to listOf(60.0 to 3.0, 230.0 to 2.0, 910.0 to -2.0, 3600.0 to 2.0, 14000.0 to 4.0),
        "Bass Boost" to listOf(60.0 to 7.0, 230.0 to 5.0, 910.0 to 0.0, 3600.0 to 0.0, 14000.0 to 0.0),
        "Treble Boost" to listOf(60.0 to 0.0, 230.0 to 0.0, 910.0 to 0.0, 3600.0 to 4.0, 14000.0 to 7.0),
        "Vocal" to listOf(60.0 to -3.0, 230.0 to -1.0, 910.0 to 3.0, 3600.0 to 4.0, 14000.0 to 0.0),
    )

    private val _state = MutableStateFlow(EqState())
    val state: StateFlow<EqState> = _state.asStateFlow()

    private var prefs: SharedPreferences? = null
    private var eq: Equalizer? = null
    private var bassEffect: BassBoost? = null
    private var virtEffect: Virtualizer? = null

    // Wanted settings survive re-attaching to a new audio session.
    private var wantEnabled = false
    private var wantPreset = "Normal"
    private var wantLevels: List<Int>? = null
    private var wantBass = 0
    private var wantVirt = 0

    @Synchronized
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("equalizer", Context.MODE_PRIVATE)
        prefs = p
        wantEnabled = p.getBoolean("enabled", false)
        wantPreset = p.getString("preset", "Normal") ?: "Normal"
        wantLevels = p.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }?.takeIf { it.isNotEmpty() }
        wantBass = p.getInt("bass", 0)
        wantVirt = p.getInt("virt", 0)
    }

    private fun persist() {
        prefs?.edit()
            ?.putBoolean("enabled", wantEnabled)
            ?.putString("preset", wantPreset)
            ?.putString("levels", wantLevels?.joinToString(","))
            ?.putInt("bass", wantBass)
            ?.putInt("virt", wantVirt)
            ?.apply()
    }

    @Synchronized
    fun attach(sessionId: Int) {
        releaseEffects()
        if (sessionId == 0) { _state.value = EqState(); return }
        val e = try { Equalizer(0, sessionId) } catch (t: Throwable) { null }
        if (e == null) { _state.value = EqState(); return } // device does not support the Equalizer effect
        eq = e
        try {
            val n = e.numberOfBands.toInt()
            val range = e.bandLevelRange
            val min = range[0].toInt()
            val max = range[1].toInt()
            val freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            val levels = wantLevels?.takeIf { it.size == n }?.map { it.coerceIn(min, max) }
                ?: presetLevels(wantPreset, freqs, min, max)
            wantLevels = levels

            bassEffect = try { BassBoost(0, sessionId) } catch (t: Throwable) { null }
            virtEffect = try { Virtualizer(0, sessionId) } catch (t: Throwable) { null }
            val bassOk = bassEffect?.strengthSupported == true
            val virtOk = virtEffect?.strengthSupported == true

            e.setEnabled(wantEnabled)
            levels.forEachIndexed { i, l -> e.setBandLevel(i.toShort(), l.toShort()) }
            if (bassOk) { bassEffect?.setStrength(wantBass.toShort()); bassEffect?.setEnabled(wantEnabled) }
            if (virtOk) { virtEffect?.setStrength(wantVirt.toShort()); virtEffect?.setEnabled(wantEnabled) }

            _state.value = EqState(
                available = true, enabled = wantEnabled, preset = wantPreset,
                bandFreqsHz = freqs, levels = levels, minLevel = min, maxLevel = max,
                bassSupported = bassOk, bass = wantBass, virtSupported = virtOk, virt = wantVirt,
            )
        } catch (t: Throwable) {
            releaseEffects()
            _state.value = EqState()
        }
    }

    @Synchronized
    fun release() {
        releaseEffects()
        _state.value = EqState()
    }

    private fun releaseEffects() {
        runCatching { eq?.release() }
        runCatching { bassEffect?.release() }
        runCatching { virtEffect?.release() }
        eq = null; bassEffect = null; virtEffect = null
    }

    @Synchronized
    fun setEnabled(on: Boolean) {
        wantEnabled = on
        runCatching { eq?.setEnabled(on) }
        runCatching { if (_state.value.bassSupported) bassEffect?.setEnabled(on) }
        runCatching { if (_state.value.virtSupported) virtEffect?.setEnabled(on) }
        _state.value = _state.value.copy(enabled = on)
        persist()
    }

    @Synchronized
    fun applyPreset(name: String) {
        val s = _state.value
        if (!s.available) return
        val levels = presetLevels(name, s.bandFreqsHz, s.minLevel, s.maxLevel)
        wantPreset = name; wantLevels = levels
        levels.forEachIndexed { i, l -> runCatching { eq?.setBandLevel(i.toShort(), l.toShort()) } }
        _state.value = s.copy(preset = name, levels = levels)
        persist()
    }

    @Synchronized
    fun setBand(index: Int, level: Int) {
        val s = _state.value
        if (!s.available || index !in s.levels.indices) return
        val l = level.coerceIn(s.minLevel, s.maxLevel)
        runCatching { eq?.setBandLevel(index.toShort(), l.toShort()) }
        val levels = s.levels.toMutableList().also { it[index] = l }
        wantPreset = "Custom"; wantLevels = levels
        _state.value = s.copy(preset = "Custom", levels = levels)
        persist()
    }

    @Synchronized
    fun setBass(strength: Int) {
        val s = _state.value
        if (!s.bassSupported) return
        wantBass = strength.coerceIn(0, 1000)
        runCatching { bassEffect?.setStrength(wantBass.toShort()) }
        _state.value = s.copy(bass = wantBass)
        persist()
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        val s = _state.value
        if (!s.virtSupported) return
        wantVirt = strength.coerceIn(0, 1000)
        runCatching { virtEffect?.setStrength(wantVirt.toShort()) }
        _state.value = s.copy(virt = wantVirt)
        persist()
    }

    @Synchronized
    fun reset() {
        applyPreset("Normal")
        setBass(0)
        setVirtualizer(0)
    }

    private fun presetLevels(name: String, freqs: List<Int>, min: Int, max: Int): List<Int> {
        val pts = curves[name] ?: return List(freqs.size) { 0.coerceIn(min, max) }
        return freqs.map { f -> (interpolate(pts, f.toDouble()) * 100).roundToInt().coerceIn(min, max) }
    }

    private fun interpolate(pts: List<Pair<Double, Double>>, freq: Double): Double {
        val x = log10(max(freq, 1.0))
        val xs = pts.map { log10(it.first) }
        if (x <= xs.first()) return pts.first().second
        if (x >= xs.last()) return pts.last().second
        for (i in 0 until pts.size - 1) {
            if (x <= xs[i + 1]) {
                val t = (x - xs[i]) / (xs[i + 1] - xs[i])
                return pts[i].second + t * (pts[i + 1].second - pts[i].second)
            }
        }
        return 0.0
    }
}
