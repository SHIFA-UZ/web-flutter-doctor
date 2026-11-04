package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** What's playing: [position] and [duration] in whole seconds (0 until the stream is known). */
@Stable
data class Playback(
    val url: String,
    val playing: Boolean,
    val position: Int = 0,
    val duration: Int = 0,
)

/** Streams one voice message at a time from its URL (08 §5.5.3; Flutter let several play at once). */
interface AudioPlayer {
    /** Compose state: null when nothing was started or the last one finished. */
    val playback: Playback?

    /** Plays [url], pauses it if it's playing, or resumes it if it's paused. */
    fun toggle(url: String)

    /** Refreshes [playback]'s position. */
    fun tick()

    fun release()
}

expect fun createAudioPlayer(): AudioPlayer

/** A player for this screen; leaving the screen stops it. */
@Composable
fun rememberAudioPlayer(): AudioPlayer {
    val player = remember { createAudioPlayer() }
    val playing = player.playback?.playing == true
    LaunchedEffect(playing) {
        while (playing) {
            delay(TICK)
            player.tick()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}

private val TICK = 250.milliseconds
