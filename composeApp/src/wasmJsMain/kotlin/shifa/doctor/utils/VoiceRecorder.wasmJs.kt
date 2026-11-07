package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.await
import kotlin.js.Promise

/**
 * The browser's MediaRecorder in AAC `.m4a` (what the backend and the phones' players expect). The microphone opens
 * when a take starts and closes when it ends; [start] answers at once and the take begins when the browser hands over
 * the microphone, which [stop] waits for. Browsers that can't record AAC (Firefox) can't start a take.
 */
private class WebVoiceRecorder : VoiceRecorder {
    private val js = newRecorder()
    private var startedAt = 0L
    private var recording = false
    override var recordingId: String? = null
    override var lastDurationSeconds = 0

    override fun start(): Boolean {
        cancel()
        if (!aacRecordingSupported()) return false
        recorderStart(js)
        recording = true
        recordingId = newRecordingId()
        startedAt = currentInstant().toEpochMilliseconds()
        return true
    }

    override suspend fun stop(): ByteArray? {
        if (!recording) return null
        recording = false
        lastDurationSeconds = ((currentInstant().toEpochMilliseconds() - startedAt) / 1000).toInt()
        return recorderStop(js).await<JsAny?>()?.let(::toByteArray)?.takeIf { it.isNotEmpty() }
    }

    override fun cancel() {
        recording = false
        recorderCancel(js)
    }

    override fun level(): Float =
        if (recording) {
            recorderLevel(
                js,
            ).let { if (it.isNaN()) 0f else levelFromDb(it) }
        } else {
            0f
        }
}

@Composable
actual fun rememberVoiceRecorder(): VoiceRecorder {
    val recorder = remember { WebVoiceRecorder() }
    DisposableEffect(recorder) { onDispose { recorder.cancel() } }
    return recorder
}

/** The browser's microphone prompt (once per site); the microphone is closed again until a take starts. */
@Composable
actual fun rememberMicPermission(onResult: (Boolean) -> Unit): () -> Unit {
    val result = rememberUpdatedState(onResult)
    return remember { { if (aacRecordingSupported()) askMicrophone { result.value(it) } else result.value(false) } }
}

private fun aacRecordingSupported(): Boolean =
    js(
        "!!(navigator.mediaDevices && window.MediaRecorder &&" +
            " MediaRecorder.isTypeSupported('audio/mp4;codecs=mp4a.40.2'))",
    )

private fun askMicrophone(done: (Boolean) -> Unit) {
    js(
        "navigator.mediaDevices.getUserMedia({ audio: true }).then(function (s) {" +
            " s.getTracks().forEach(function (t) { t.stop() }); done(true) }, function () { done(false) })",
    )
}

private fun recorderStart(r: JsAny) {
    js("r.start()")
}

private fun recorderStop(r: JsAny): Promise<JsAny?> = js("r.stop()")

private fun recorderCancel(r: JsAny) {
    js("r.cancel()")
}

private fun recorderLevel(r: JsAny): Double = js("r.level()")

// Each take keeps its own stream, recorder and chunks, so a take being stopped finishes untouched while the next one
// starts. stop() resolves the take's .m4a bytes, or null; cancel() drops the current take.
private fun newRecorder(): JsAny =
    js(
        """(function () {
    var cur = null, s = {};
    function release(t) {
        if (t.stream) t.stream.getTracks().forEach(function (k) { k.stop() });
        if (t.ctx) t.ctx.close();
        t.stream = null; t.ctx = null; t.analyser = null;
    }
    s.start = function () {
        s.cancel();
        var t = { rec: null, stream: null, chunks: [], ctx: null, analyser: null, buf: null, dead: false };
        cur = t;
        t.ready = navigator.mediaDevices.getUserMedia({ audio: true }).then(function (stream) {
            t.stream = stream;
            if (t.dead) { release(t); return false; }
            try {
                var rec = new MediaRecorder(stream, { mimeType: 'audio/mp4;codecs=mp4a.40.2', audioBitsPerSecond: 32000 });
                rec.ondataavailable = function (e) { if (e.data && e.data.size) t.chunks.push(e.data) };
                rec.start(); t.rec = rec;
            } catch (e) { release(t); return false; }
            try {
                t.ctx = new AudioContext(); t.analyser = t.ctx.createAnalyser(); t.analyser.fftSize = 1024;
                t.ctx.createMediaStreamSource(stream).connect(t.analyser); t.buf = new Float32Array(1024);
            } catch (e) { t.analyser = null }
            return true;
        }, function () { return false });
    };
    s.stop = function () {
        var t = cur; cur = null;
        if (!t) return Promise.resolve(null);
        return t.ready.then(function (ok) {
            if (!ok || !t.rec) { release(t); return null; }
            return new Promise(function (resolve) {
                t.rec.onstop = function () {
                    var blob = new Blob(t.chunks, { type: 'audio/mp4' }); t.chunks = []; release(t);
                    if (!blob.size) return resolve(null);
                    blob.arrayBuffer().then(function (b) { resolve(new Uint8Array(b)) }, function () { resolve(null) });
                };
                try { t.rec.stop() } catch (e) { release(t); resolve(null) }
            });
        });
    };
    s.cancel = function () {
        var t = cur; cur = null;
        if (!t) return;
        t.dead = true;
        if (t.rec) { t.rec.ondataavailable = null; t.rec.onstop = null; try { t.rec.stop() } catch (e) {} }
        t.chunks = []; release(t);
    };
    s.level = function () {
        var t = cur;
        if (!t || !t.analyser) return NaN;
        t.analyser.getFloatTimeDomainData(t.buf);
        var sum = 0; for (var i = 0; i < t.buf.length; i++) sum += t.buf[i] * t.buf[i];
        var rms = Math.sqrt(sum / t.buf.length);
        return rms > 0 ? 20 * Math.log10(rms) : -160;
    };
    return s;
})()""",
    )
