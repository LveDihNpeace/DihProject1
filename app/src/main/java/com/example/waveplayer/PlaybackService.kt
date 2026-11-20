@file:OptIn(UnstableApi::class)

package com.example.waveplayer

import android.content.Context
import androidx.media3.common.AudioAttributes
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

/** Background playback + notification + lockscreen/Bluetooth controls via Media3. */
class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private var exo: ExoPlayer? = null

    private val sessionListener = object : Player.Listener {
        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            EqEngine.attach(audioSessionId)
        }
    }

    override fun onCreate() {
        super.onCreate()
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

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        exo?.removeListener(sessionListener)
        EqEngine.release()
        session?.run {
            player.release()
            release()
        }
        session = null
        exo = null
        super.onDestroy()
    }
}
