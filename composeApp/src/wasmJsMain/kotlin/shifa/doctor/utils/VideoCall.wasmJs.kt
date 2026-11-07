package shifa.doctor.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.WebElementView
import kotlinx.browser.document
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.w3c.dom.HTMLVideoElement
import shifa.doctor.data.api.httpLogger

@JsModule("@daily-co/daily-js")
external object DailyModule : JsAny

/**
 * WEB-08: Daily's JavaScript call object, as the phones use Daily's native SDKs. The browser asks for the camera and
 * microphone itself; the patient's sound plays through an `<audio>` element the call object keeps.
 */
internal class WebVideoCall : VideoCall {
    private val js: JsAny = newDailyCall(DailyModule) { event -> onEvent(event) }
    private val _state = MutableStateFlow(VideoCallState())
    override val state: StateFlow<VideoCallState> = _state.asStateFlow()

    private fun update(transform: VideoCallState.() -> VideoCallState) {
        _state.value = _state.value.transform()
    }

    // The SDK's messages can carry the room or the token: fixed log lines only.
    private fun onEvent(event: String): Unit =
        when (event) {
            "joined" ->
                update {
                    copy(phase = VideoCallState.Phase.Joined, patientJoined = participants(js) > 1, tracks = tracks + 1)
                }
            "left" -> update { copy(phase = VideoCallState.Phase.Left) }
            "participants", "tracks" -> update { copy(patientJoined = participants(js) > 1, tracks = tracks + 1) }
            "inputs" -> update { copy(cameraOn = localVideo(js), micOn = localAudio(js), tracks = tracks + 1) }
            "join-failed" -> {
                httpLogger.log("FAILED Daily join")
                update { copy(phase = VideoCallState.Phase.Preview, failed = true) }
            }
            "error" -> {
                httpLogger.log("FAILED Daily call")
                update { copy(failed = true) }
            }
            else -> Unit
        }

    /** The patient's (anyone but us) or our camera track, or null. */
    fun track(remote: Boolean): JsAny? = videoTrack(js, remote)

    override fun startPreview() {
        startCamera(js)
        update { copy(phase = VideoCallState.Phase.Preview) }
    }

    override fun join(
        roomUrl: String,
        token: String,
        camera: Boolean,
        mic: Boolean,
    ) {
        update { copy(phase = VideoCallState.Phase.Joining, failed = false) }
        joinCall(js, roomUrl, token, camera, mic)
    }

    override fun setCamera(on: Boolean) = setLocalVideo(js, on)

    override fun setMic(on: Boolean) = setLocalAudio(js, on)

    override fun flipCamera() = cycleCamera(js)

    override fun release() = leaveCall(js)
}

@Composable
actual fun rememberVideoCallFactory(): () -> VideoCall = remember { { WebVideoCall() } }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
actual fun VideoTile(
    call: VideoCall,
    remote: Boolean,
    modifier: Modifier,
) {
    // Previews and tests pass a fake: an empty tile.
    val daily = call as? WebVideoCall ?: return Box(modifier)
    val state by call.state.collectAsState()
    val track by rememberUpdatedState(state.tracks.let { daily.track(remote) })
    WebElementView(
        factory = {
            (document.createElement("video") as HTMLVideoElement).apply {
                autoplay = true
                muted = true // the sound plays through the call object's own audio element
                setAttribute("playsinline", "")
                style.width = "100%"
                style.height = "100%"
                style.setProperty("object-fit", "cover")
                if (!remote) style.transform = "scaleX(-1)" // our own camera, mirrored as on the phones
            }
        },
        modifier = modifier,
        update = { showTrack(it, track) },
    )
}

/** Both permissions at once (a call can go on with the microphone alone: then the camera reads as refused). */
@Composable
actual fun rememberCallPermissions(onResult: (camera: Boolean, mic: Boolean) -> Unit): (ask: Boolean) -> Unit {
    val result by rememberUpdatedState(onResult)
    return remember {
        {
                ask ->
            if (ask) askCallMedia { c, m -> result(c, m) } else readCallMedia { c, m -> result(c, m) }
        }
    }
}

private fun askCallMedia(done: (Boolean, Boolean) -> Unit) {
    js(
        """(function () {
    function stop(s) { s.getTracks().forEach(function (t) { t.stop() }) }
    navigator.mediaDevices.getUserMedia({ video: true, audio: true }).then(function (s) { stop(s); done(true, true) }, function () {
        navigator.mediaDevices.getUserMedia({ audio: true }).then(function (s) { stop(s); done(false, true) }, function () { done(false, false) });
    });
})()""",
    )
}

// Reading only (back to the tab): the browser names the devices once access was granted; nothing is asked.
private fun readCallMedia(done: (Boolean, Boolean) -> Unit) {
    js(
        """navigator.mediaDevices.enumerateDevices().then(function (ds) {
    function has(kind) { return ds.some(function (d) { return d.kind === kind && d.label }) }
    done(has('videoinput'), has('audioinput'));
}, function () { done(false, false) })""",
    )
}

// The call object with its events reduced to names the Kotlin side reads: joined, left, participants, tracks,
// inputs, join-failed, error. The patient's audio tracks play through one hidden <audio> element per participant.
private fun newDailyCall(
    module: JsAny,
    onEvent: (String) -> Unit,
): JsAny =
    js(
        """(function () {
    var Daily = module.default || module;
    // Several instances: after a failed join the visit makes a new call while the old one is still being destroyed.
    var call = Daily.createCallObject({ subscribeToTracksAutomatically: true, allowMultipleCallInstances: true });
    var audios = {};
    function playAudio(p) {
        if (!p || p.local) return;
        var t = p.tracks && p.tracks.audio && p.tracks.audio.persistentTrack;
        var a = audios[p.session_id];
        if (!t) { if (a) { a.srcObject = null } return; }
        if (!a) { a = document.createElement('audio'); a.autoplay = true; audios[p.session_id] = a; }
        if (!a.srcObject || a.srcObject.getAudioTracks()[0] !== t) { a.srcObject = new MediaStream([t]); a.play().catch(function () {}); }
    }
    call.on('joined-meeting', function () { onEvent('joined') });
    call.on('left-meeting', function () { onEvent('left') });
    ['participant-joined', 'participant-updated', 'participant-left'].forEach(function (e) {
        call.on(e, function (ev) {
            if (e === 'participant-left' && ev && ev.participant) {
                var a = audios[ev.participant.session_id]; if (a) { a.srcObject = null; delete audios[ev.participant.session_id]; }
            } else if (ev) { playAudio(ev.participant) }
            onEvent('participants');
        });
    });
    ['track-started', 'track-stopped'].forEach(function (e) {
        call.on(e, function (ev) { if (ev) playAudio(ev.participant); onEvent('tracks'); onEvent('inputs'); });
    });
    call.on('error', function () { onEvent('error') });
    call.on('camera-error', function () { onEvent('inputs') });
    var started = null;
    return {
        call: call,
        audios: audios,
        // The camera and microphone before joining (the visit's preview), started once.
        start: function () {
            if (!started) started = call.startCamera().catch(function () {});
            return started;
        },
        joinFailed: function () { onEvent('join-failed') },
        inputs: function () { onEvent('inputs') }
    };
})()""",
    )

private fun participants(d: JsAny): Int = js("Object.keys(d.call.participants() || {}).length")

private fun localVideo(d: JsAny): Boolean = js("!!d.call.localVideo()")

private fun localAudio(d: JsAny): Boolean = js("!!d.call.localAudio()")

private fun videoTrack(
    d: JsAny,
    remote: Boolean,
): JsAny? =
    js(
        """(function () {
    var ps = d.call.participants() || {};
    var p = remote ? Object.keys(ps).map(function (k) { return ps[k] }).filter(function (x) { return !x.local })[0] : ps.local;
    var v = p && p.tracks && p.tracks.video;
    return (v && v.state === 'playable' && v.persistentTrack) || null;
})()""",
    )

private fun startCamera(d: JsAny) {
    js("d.start().then(function () { d.inputs() })")
}

private fun joinCall(
    d: JsAny,
    url: String,
    token: String,
    camera: Boolean,
    mic: Boolean,
) {
    js(
        "d.call.join({ url: url, token: token, startVideoOff: !camera, startAudioOff: !mic }).then(function () { d.inputs() }, function () { d.joinFailed() })",
    )
}

private fun setLocalVideo(
    d: JsAny,
    on: Boolean,
) {
    js(
        "(d.call.meetingState() === 'joined-meeting' ? Promise.resolve() : d.start())" +
            ".then(function () { d.call.setLocalVideo(on); setTimeout(d.inputs, 0) })",
    )
}

private fun setLocalAudio(
    d: JsAny,
    on: Boolean,
) {
    js(
        "(d.call.meetingState() === 'joined-meeting' ? Promise.resolve() : d.start())" +
            ".then(function () { d.call.setLocalAudio(on); setTimeout(d.inputs, 0) })",
    )
}

private fun cycleCamera(d: JsAny) {
    js("d.call.cycleCamera().catch(function () {})")
}

private fun leaveCall(d: JsAny) {
    js(
        "Object.keys(d.audios).forEach(function (k) { d.audios[k].srcObject = null });" +
            " d.call.leave().catch(function () {}).then(function () { d.call.destroy() })",
    )
}

private fun showTrack(
    video: HTMLVideoElement,
    track: JsAny?,
) {
    js(
        "(function () { var cur = video.srcObject && video.srcObject.getVideoTracks()[0];" +
            " if (!track) { video.srcObject = null } else if (cur !== track) { video.srcObject = new MediaStream([track]) } })()",
    )
}
