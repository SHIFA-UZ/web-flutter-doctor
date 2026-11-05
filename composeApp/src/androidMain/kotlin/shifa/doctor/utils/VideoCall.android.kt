package shifa.doctor.utils

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import co.daily.CallClient
import co.daily.CallClientListener
import co.daily.model.CallJoinData
import co.daily.model.CallState
import co.daily.model.MediaStreamTrack
import co.daily.model.MeetingToken
import co.daily.model.Participant
import co.daily.model.ParticipantLeftReason
import co.daily.model.RequestListenerWithData
import co.daily.model.RequestResultWithData
import co.daily.settings.CameraInputSettingsUpdate
import co.daily.settings.ClientSettingsUpdate
import co.daily.settings.Enable
import co.daily.settings.FacingModeUpdate
import co.daily.settings.InputSettings
import co.daily.settings.InputSettingsUpdate
import co.daily.settings.VideoMediaTrackSettingsUpdate
import co.daily.view.VideoView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import shifa.doctor.data.api.httpLogger

/** Daily's Android SDK. Errors are logged; the screen shows its own text. */
internal class DailyVideoCall(
    context: Context,
) : VideoCall {
    private val client = CallClient(context.applicationContext)
    private val _state = MutableStateFlow(VideoCallState())
    override val state: StateFlow<VideoCallState> = _state.asStateFlow()
    var front = true
        private set

    private fun update(transform: VideoCallState.() -> VideoCallState) {
        _state.value = _state.value.transform()
    }

    private val listener =
        object : CallClientListener {
            override fun onCallStateUpdated(state: CallState) {
                when (state) {
                    CallState.joined -> update { copy(phase = VideoCallState.Phase.Joined) }
                    CallState.left -> update { copy(phase = VideoCallState.Phase.Left) }
                    else -> Unit
                }
            }

            override fun onInputsUpdated(inputs: InputSettings) =
                update {
                    copy(
                        cameraOn = inputs.camera.isEnabled,
                        micOn = inputs.microphone.isEnabled,
                        tracks = tracks + 1,
                    )
                }

            override fun onParticipantJoined(participant: Participant) = refresh()

            override fun onParticipantUpdated(participant: Participant) = refresh()

            override fun onParticipantLeft(
                participant: Participant,
                reason: ParticipantLeftReason,
            ) = refresh()

            override fun onError(message: String) {
                // The SDK's text can carry the room or the token: a fixed line only.
                httpLogger.log("FAILED Daily call")
                update { copy(failed = true) }
            }
        }

    init {
        client.addListener(listener)
    }

    private fun refresh() = update { copy(patientJoined = client.participants().all.size > 1, tracks = tracks + 1) }

    /** The patient's (anyone but us) or our camera track. */
    fun track(remote: Boolean): MediaStreamTrack? {
        val participants = client.participants()
        val who =
            if (remote) participants.all.values.firstOrNull { it.id != participants.local.id } else participants.local
        return who?.media?.camera?.track
    }

    override fun startPreview() {
        client.setInputsEnabled(camera = true, microphone = true)
        update { copy(phase = VideoCallState.Phase.Preview) }
    }

    override fun join(
        roomUrl: String,
        token: String,
    ) {
        update { copy(phase = VideoCallState.Phase.Joining, failed = false) }
        val inputs = InputSettingsUpdate(camera = Enable(), microphone = Enable())
        client.join(
            roomUrl,
            MeetingToken(token),
            ClientSettingsUpdate(inputSettings = inputs),
            object : RequestListenerWithData<CallJoinData> {
                override fun onRequestResult(result: RequestResultWithData<CallJoinData>) {
                    result.error?.let {
                        httpLogger.log("FAILED Daily join")
                        update { copy(phase = VideoCallState.Phase.Preview, failed = true) }
                    } ?: preferSpeaker()
                }
            },
        )
    }

    // The loudspeaker, not the earpiece (as in the patient app).
    private fun preferSpeaker() {
        client.availableDevices().audio.firstOrNull { it.deviceId.contains("SPEAKER", ignoreCase = true) }
            ?.let { client.setAudioDevice(it.deviceId) }
    }

    override fun setCamera(on: Boolean) = client.setInputsEnabled(camera = on)

    override fun setMic(on: Boolean) = client.setInputsEnabled(microphone = on)

    override fun flipCamera() {
        front = !front
        val facing = if (front) FacingModeUpdate.user else FacingModeUpdate.environment
        client.updateInputs(
            InputSettingsUpdate(
                camera = CameraInputSettingsUpdate(settings = VideoMediaTrackSettingsUpdate(facingMode = facing)),
            ),
        )
    }

    override fun release() {
        client.removeListener(listener)
        client.leave { client.release() }
    }
}

@Composable
actual fun rememberVideoCallFactory(): () -> VideoCall {
    val context = LocalContext.current.applicationContext
    return remember(context) { { DailyVideoCall(context) } }
}

@Composable
actual fun VideoTile(
    call: VideoCall,
    remote: Boolean,
    modifier: Modifier,
) {
    // Previews and tests pass a fake: an empty tile.
    val daily = call as? DailyVideoCall ?: return Box(modifier)
    val state by call.state.collectAsState()
    AndroidView(
        factory = { VideoView(it).apply { videoScaleMode = VideoView.VideoScaleMode.FILL } },
        modifier = modifier,
        update = {
            state.tracks // re-bind on every track change
            it.track = daily.track(remote)
            it.mirrorHorizontally = !remote && daily.front
        },
    )
}

@Composable
actual fun rememberCallPermissions(onResult: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val result by rememberUpdatedState(onResult)
    val permissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            result(granted.values.all { it })
        }
    return {
        if (permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }) {
            result(true)
        } else {
            launcher.launch(permissions)
        }
    }
}
