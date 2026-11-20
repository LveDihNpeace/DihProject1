@file:OptIn(UnstableApi::class)

package com.example.waveplayer

import android.content.Context
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.waveplayer.audio.EqEngine
import com.example.waveplayer.audio.VocalProcessor
import com.example.waveplayer.data.SettingsRepository
import com.example.waveplayer.widget.WidgetState
import com.example.waveplayer.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The single player of the app. UI (MediaController), notification, lockscreen, Bluetooth and the home-screen
 * widget all talk to this one ExoPlayer, so there is exactly one audio session.
 */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var widgetJob: Job? = null
    private lateinit var settings: SettingsRepository

    private val sessionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) { EqEngine.attach(audioSessionId) }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.mediaId?.toLongOrNull()?.let { id -> scope.launch(Dispatchers.IO) { settings.pushRecent(id) } }
            scheduleWidget()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) = scheduleWidget()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = scheduleWidget()
    }

    override fun onCreate() {
        super.onCreate()
        settings = SettingsRepository(this)
        EqEngine.init(this)
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean,
            ): AudioSink = DefaultAudioSink.Builder(context)
                // 16-bit output so the vocal separator can always process the stream.
                .setEnableFloatOutput(false)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .setAudioProcessors(arrayOf<AudioProcessor>(VocalProcessor()))
                .build()
        }
        val player = ExoPlayer.Builder(this, renderers)
            .setAudioAttributes(AudioAttributes.DEFAULT, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo = player
        player.addListener(sessionListener)
        EqEngine.attach(player.audioSessionId)
        session = MediaSession.Builder(this, player).build()
    }

    /** Widget buttons arrive here as explicit service intents and drive the same player. */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val p = exo
        if (p != null) {
            when (intent?.action) {
                WidgetUpdater.ACTION_PLAY_PAUSE -> if (p.isPlaying) p.pause() else p.play()
                WidgetUpdater.ACTION_NEXT -> p.seekToNextMediaItem()
                WidgetUpdater.ACTION_PREV -> p.seekToPreviousMediaItem()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun scheduleWidget() {
        widgetJob?.cancel()
        widgetJob = scope.launch {
            delay(150) // coalesce bursts of player events
            val p = exo ?: return@launch
            val md = p.mediaMetadata
            WidgetState.save(
                this@PlaybackService,
                WidgetState.State(
                    title = md.title?.toString() ?: "",
                    artist = md.artist?.toString() ?: "",
                    artUri = md.artworkUri?.toString(),
                    playing = p.isPlaying,
                    hasQueue = p.mediaItemCount > 0,
                ),
            )
            WidgetUpdater.update(this@PlaybackService)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        exo?.removeListener(sessionListener)
        EqEngine.release()
        // Player is gone: the widget must not pretend something is queued.
        val app = applicationContext
        val last = WidgetState.load(app)
        WidgetState.save(app, last.copy(playing = false, hasQueue = false))
        CoroutineScope(Dispatchers.Default).launch { WidgetUpdater.update(app) }
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        exo = null
        super.onDestroy()
    }
}
