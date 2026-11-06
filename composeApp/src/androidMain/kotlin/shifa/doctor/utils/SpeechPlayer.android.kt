package shifa.doctor.utils

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import shifa.doctor.data.api.httpLogger
import kotlin.coroutines.resume

private class AndroidSpeechPlayer : SpeechPlayer {
    private var player: MediaPlayer? = null
    private var waiting: CancellableContinuation<Boolean>? = null

    override suspend fun play(audio: ByteArray): Boolean {
        stop()
        if (audio.isEmpty()) return false
        return suspendCancellableCoroutine { cont ->
            waiting = cont
            cont.invokeOnCancellation { stop() }
            val mp = MediaPlayer()
            player = mp // owned before anything can fail, so stop() frees it
            runCatching {
                mp.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                mp.setDataSource(BytesSource(audio))
                mp.setOnPreparedListener { it.start() }
                mp.setOnCompletionListener { finish(true) }
                // HIPAA: only the player's codes, never the text or the audio.
                mp.setOnErrorListener { _, what, extra ->
                    httpLogger.log("FAILED to play speech: $what/$extra")
                    finish(false)
                    true
                }
                mp.prepareAsync()
            }.onFailure {
                httpLogger.log("FAILED to play speech: ${it::class.simpleName}")
                finish(false)
            }
        }
    }

    override fun stop() = finish(false)

    private fun finish(ended: Boolean) {
        player?.let { runCatching { it.release() } }
        player = null
        val cont = waiting
        waiting = null
        if (cont?.isActive == true) cont.resume(ended)
    }
}

/** The clip's bytes as the player reads them (no temp file). */
private class BytesSource(
    private val bytes: ByteArray,
) : MediaDataSource() {
    override fun readAt(
        position: Long,
        buffer: ByteArray,
        offset: Int,
        size: Int,
    ): Int {
        if (position >= bytes.size) return -1
        val n = minOf(size, bytes.size - position.toInt())
        System.arraycopy(bytes, position.toInt(), buffer, offset, n)
        return n
    }

    override fun getSize(): Long = bytes.size.toLong()

    override fun close() = Unit
}

actual fun createSpeechPlayer(): SpeechPlayer = AndroidSpeechPlayer()
