package shifa.doctor.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryPlayback
import platform.AVFAudio.setActive
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemDidPlayToEndTimeNotification
import platform.AVFoundation.currentItem
import platform.AVFoundation.currentTime
import platform.AVFoundation.duration
import platform.AVFoundation.pause
import platform.AVFoundation.play
import platform.CoreMedia.CMTimeGetSeconds
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL

@OptIn(ExperimentalForeignApi::class)
private class IosAudioPlayer : AudioPlayer {
    override var playback: Playback? by mutableStateOf(null)
        private set

    private var player: AVPlayer? = null
    private var ended: Any? = null

    override fun toggle(url: String) {
        val current = playback
        val p = player
        if (p != null && current?.url == url) {
            if (current.playing) p.pause() else p.play()
            playback = current.copy(playing = !current.playing)
            return
        }
        release()
        val nsUrl = NSURL.URLWithString(url) ?: return
        // The recorder leaves the session on PlayAndRecord, which plays through the earpiece.
        AVAudioSession.sharedInstance().apply {
            setCategory(AVAudioSessionCategoryPlayback, error = null)
            setActive(true, error = null)
        }
        val item = AVPlayerItem(uRL = nsUrl)
        ended =
            NSNotificationCenter.defaultCenter.addObserverForName(
                AVPlayerItemDidPlayToEndTimeNotification,
                item,
                NSOperationQueue.mainQueue,
            ) { release() }
        player = AVPlayer(playerItem = item).also { it.play() }
        playback = Playback(url, playing = true)
    }

    override fun tick() {
        val p = player ?: return
        val duration = p.currentItem?.duration?.let { CMTimeGetSeconds(it) }?.takeIf { !it.isNaN() } ?: 0.0
        playback = playback?.copy(position = CMTimeGetSeconds(p.currentTime()).toInt(), duration = duration.toInt())
    }

    override fun release() {
        ended?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        ended = null
        player?.pause()
        player = null
        playback = null
    }
}

actual fun createAudioPlayer(): AudioPlayer = IosAudioPlayer()
