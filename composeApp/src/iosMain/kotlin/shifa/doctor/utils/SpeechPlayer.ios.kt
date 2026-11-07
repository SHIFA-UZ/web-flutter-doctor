package shifa.doctor.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioPlayerDelegateProtocol
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.AVAudioSessionInterruptionNotification
import platform.AVFAudio.setActive
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.darwin.NSObject
import shifa.doctor.data.api.httpLogger
import kotlin.coroutines.resume

@OptIn(ExperimentalForeignApi::class)
private class IosSpeechPlayer : SpeechPlayer {
    private var player: AVAudioPlayer? = null
    private var waiting: CancellableContinuation<Boolean>? = null

    // An interruption (a call, Siri) never reaches the delegate's "finished": it ends the clip here.
    private var interruption: Any? = null

    // Kept while playing: AVAudioPlayer holds its delegate weakly.
    private val delegate =
        object : NSObject(), AVAudioPlayerDelegateProtocol {
            override fun audioPlayerDidFinishPlaying(
                player: AVAudioPlayer,
                successfully: Boolean,
            ) = finish(successfully)

            override fun audioPlayerDecodeErrorDidOccur(
                player: AVAudioPlayer,
                error: platform.Foundation.NSError?,
            ) {
                httpLogger.log("FAILED to play speech: decode")
                finish(false)
            }
        }

    override suspend fun play(audio: ByteArray): Boolean {
        stop()
        if (audio.isEmpty()) return false
        return suspendCancellableCoroutine { cont ->
            waiting = cont
            cont.invokeOnCancellation { stop() }
            // The recorder leaves the session on PlayAndRecord, which plays through the earpiece.
            AVAudioSession.sharedInstance().apply {
                setCategory(AVAudioSessionCategoryPlayback, error = null)
                setActive(true, error = null)
            }
            interruption =
                NSNotificationCenter.defaultCenter.addObserverForName(
                    AVAudioSessionInterruptionNotification,
                    null,
                    NSOperationQueue.mainQueue,
                ) { finish(false) }
            val p = AVAudioPlayer(data = audio.toNSData(), error = null)
            player = p
            p.delegate = delegate
            if (!p.play()) {
                httpLogger.log("FAILED to play speech")
                finish(false)
            }
        }
    }

    override fun stop() = finish(false)

    private fun finish(ended: Boolean) {
        interruption?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        interruption = null
        player?.stop()
        player?.delegate = null
        player = null
        val cont = waiting
        waiting = null
        if (cont?.isActive == true) cont.resume(ended)
    }
}

actual fun createSpeechPlayer(): SpeechPlayer = IosSpeechPlayer()
