package com.example.waveplayer

import android.app.Application
import android.content.ComponentName
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.waveplayer.data.Song
import com.example.waveplayer.data.toMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** Thin client of the one and only player, which lives in PlaybackService (reached through a MediaController). */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {

    private val _current = MutableStateFlow<MediaItem?>(null)
    val current: StateFlow<MediaItem?> = _current.asStateFlow()

    val currentId: StateFlow<Long?> = _current
        .map { it?.mediaId?.toLongOrNull() }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _shuffle = MutableStateFlow(false)
    val shuffle: StateFlow<Boolean> = _shuffle.asStateFlow()

    private val _repeat = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeat: StateFlow<Int> = _repeat.asStateFlow()

    private var controller: MediaController? = null

    /**
     * Playback position. The polling loop only runs while somebody collects it with an active lifecycle
     * (mini player / lyrics / now playing on screen) and stops 2 s after the last collector leaves.
     */
    val position: StateFlow<Long> = flow {
        while (true) {
            emit(controller?.currentPosition ?: 0L)
            delay(200)
        }
    }.distinctUntilChanged().stateIn(viewModelScope, SharingStarted.WhileSubscribed(2000), 0L)

    /** Exact position right now (for per-frame consumers such as the word highlighter). */
    fun positionNow(): Long = controller?.currentPosition ?: 0L

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = sync(player)
    }

    init {
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            val c = future.get()
            controller = c
            c.addListener(listener)
            sync(c)
        }, ContextCompat.getMainExecutor(app))
    }

    private fun sync(p: Player) {
        _current.value = p.currentMediaItem
        _isPlaying.value = p.isPlaying
        _duration.value = p.duration.coerceAtLeast(0L)
        _shuffle.value = p.shuffleModeEnabled
        _repeat.value = p.repeatMode
    }

    fun play(list: List<Song>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        c.prepare()
        c.play()
    }

    fun togglePlay() { controller?.let { if (it.isPlaying) it.pause() else it.play() } }
    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    /** Live duration straight from the controller (0 while unknown); does not depend on a player event having fired. */
    fun durationNow(): Long = controller?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    /** Seeks the real player (through the session); the target is kept inside [0, duration]. */
    fun seekTo(ms: Long) {
        val c = controller ?: return
        val d = c.duration
        c.seekTo(if (d != C.TIME_UNSET && d > 0) ms.coerceIn(0L, d) else maxOf(ms, 0L))
    }
    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        super.onCleared()
    }
}
