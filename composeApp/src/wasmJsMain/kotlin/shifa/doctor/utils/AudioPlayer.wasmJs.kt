package shifa.doctor.utils

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Voice messages through an `<audio>` element, one at a time; the URL needs no header (as on the phones). */
private class WebAudioPlayer(
    private val onError: () -> Unit,
) : AudioPlayer {
    override var playback: Playback? by mutableStateOf(null)
        private set

    private var audio: JsAny? = null

    override fun toggle(url: String) {
        val current = playback
        val a = audio
        if (a != null && current?.url == url) {
            if (current.playing) pause(a) else play(a)
            playback = current.copy(playing = !current.playing)
            return
        }
        release()
        val element = newAudio(url, ended = { release() }, failed = { release().also { onError() } })
        audio = element
        playback = Playback(url, playing = true)
        play(element)
    }

    override fun tick() {
        val a = audio ?: return
        playback = playback?.copy(position = currentSeconds(a), duration = durationSeconds(a))
    }

    override fun release() {
        audio?.let(::discard)
        audio = null
        playback = null
    }
}

actual fun createAudioPlayer(onError: () -> Unit): AudioPlayer = WebAudioPlayer(onError)

private fun newAudio(
    url: String,
    ended: () -> Unit,
    failed: () -> Unit,
): JsAny =
    js(
        "(function () { var a = new Audio(url); a.onended = function () { ended() };" +
            " a.onerror = function () { failed() }; return a })()",
    )

// A refused play() (autoplay rules, a broken file) also ends in onerror or never starts: report it the same way.
private fun play(a: JsAny) {
    js("a.play().catch(function (e) { if (e && e.name !== 'AbortError' && a.onerror) a.onerror() })")
}

private fun pause(a: JsAny) {
    js("a.pause()")
}

private fun discard(a: JsAny) {
    js("a.onended = null; a.onerror = null; a.pause(); a.removeAttribute('src'); a.load()")
}

private fun currentSeconds(a: JsAny): Int = js("Math.floor(a.currentTime || 0)")

private fun durationSeconds(a: JsAny): Int = js("isFinite(a.duration) ? Math.floor(a.duration) : 0")
