package shifa.doctor.utils

import kotlinx.coroutines.await
import kotlin.js.Promise

/** Shifa's spoken replies (MP3, or WAV for Uzbek) from memory through an `<audio>` element. */
private class WebSpeechPlayer : SpeechPlayer {
    private var current: JsAny? = null

    override suspend fun play(audio: ByteArray): Boolean {
        stop()
        val wav = audio.size > 4 && audio[0] == 'R'.code.toByte() && audio[1] == 'I'.code.toByte()
        val url = blobUrl(audio, if (wav) "audio/wav" else "audio/mpeg")
        val clip = newClip(url)
        current = clip
        return try {
            playToEnd(clip).await<JsBoolean>().toBoolean()
        } finally {
            stopClip(clip)
            revoke(url)
            if (current === clip) current = null
        }
    }

    override fun stop() {
        current?.let(::stopClip)
        current = null
    }
}

actual fun createSpeechPlayer(): SpeechPlayer = WebSpeechPlayer()

private fun newClip(url: String): JsAny = js("new Audio(url)")

// Resolves true at the clip's end, false on an error, a refused play() or stopClip().
private fun playToEnd(a: JsAny): Promise<JsBoolean> =
    js(
        "new Promise(function (resolve) { a.onended = function () { resolve(true) };" +
            " a.onerror = function () { resolve(false) }; a.__stop = function () { resolve(false) };" +
            " a.play().catch(function () { resolve(false) }) })",
    )

private fun stopClip(a: JsAny) {
    js("a.pause(); if (a.__stop) a.__stop()")
}

private fun revoke(url: String) {
    js("URL.revokeObjectURL(url)")
}
