package shifa.doctor.utils

import android.media.AudioAttributes
import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import shifa.doctor.data.api.httpLogger

private class AndroidAudioPlayer(
    private val onError: () -> Unit,
) : AudioPlayer {
    override var playback: Playback? by mutableStateOf(null)
        private set

    private var player: MediaPlayer? = null

    // MediaPlayer can't start or pause while preparing: [playback]'s `playing` is what the doctor asked for, and
    // onPrepared honours it.
    private var prepared = false

    override fun toggle(url: String) {
        val current = playback
        val p = player
        if (p != null && current?.url == url) {
            val play = !current.playing
            if (prepared) {
                if (play) {
                    p.start()
                } else {
                    p.pause()
                }
            }
            playback = current.copy(playing = play)
            return
        }
        release()
        playback = Playback(url, playing = true)
        val mp = MediaPlayer()
        player = mp // owned before anything can fail, so release() frees it
        mp.setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
        mp.setOnPreparedListener {
            runCatching {
                prepared = true
                playback = playback?.copy(duration = it.duration / 1000)
                if (playback?.playing == true) it.start()
            }.onFailure { release() }
        }
        mp.setOnCompletionListener { release() }
        // HIPAA (CLAUDE.md "Logging"): the URL can be signed or name a file, so only the error codes are logged.
        mp.setOnErrorListener { _, what, extra ->
            httpLogger.log("FAILED to play audio: $what/$extra")
            release()
            onError()
            true
        }
        runCatching {
            mp.setDataSource(url)
            mp.prepareAsync()
        }.onFailure {
            httpLogger.log("FAILED to play audio: ${it::class.simpleName}")
            release()
            onError()
        }
    }

    // getCurrentPosition throws once the player left a playable state (an error, or just as it ends).
    override fun tick() {
        val p = player?.takeIf { prepared } ?: return
        runCatching { playback = playback?.copy(position = p.currentPosition / 1000) }.onFailure { release() }
    }

    override fun release() {
        player?.release()
        player = null
        prepared = false
        playback = null
    }
}

actual fun createAudioPlayer(onError: () -> Unit): AudioPlayer = AndroidAudioPlayer(onError)
