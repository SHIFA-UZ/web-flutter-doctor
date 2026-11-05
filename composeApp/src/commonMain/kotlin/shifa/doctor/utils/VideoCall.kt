package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.StateFlow

/** What the call screen shows. [tracks] changes whenever a video track does, so the tiles re-bind. */
data class VideoCallState(
    val phase: Phase = Phase.Idle,
    val cameraOn: Boolean = true,
    val micOn: Boolean = true,
    val patientJoined: Boolean = false,
    val tracks: Int = 0,
    val failed: Boolean = false,
) {
    enum class Phase { Idle, Preview, Joining, Joined, Left }
}

/**
 * One Daily call (VIDEO-01, 05 §3.1) for the visit's room and token: Daily's SDK on Android, a Swift wrapper of Daily's iOS SDK on
 * iOS (iosApp/iosApp/DailyVideoCall.swift). Call it from the main thread; after [release] it's done.
 */
interface VideoCall {
    val state: StateFlow<VideoCallState>

    /** Camera and microphone on, for the preview before joining. */
    fun startPreview()

    fun join(
        roomUrl: String,
        token: String,
    )

    fun setCamera(on: Boolean)

    fun setMic(on: Boolean)

    fun flipCamera()

    /** Leaves the call and frees it. */
    fun release()
}

/** Makes calls; the platform needs its context, so it comes from composition. */
@Composable
expect fun rememberVideoCallFactory(): () -> VideoCall

/** [call]'s patient ([remote]) or the doctor's own camera, cropped to fill; empty while there's no track. */
@Composable
expect fun VideoTile(
    call: VideoCall,
    remote: Boolean,
    modifier: Modifier = Modifier,
)

/** Asks for the camera and microphone; [onResult] gets whether both are granted. */
@Composable
expect fun rememberCallPermissions(onResult: (Boolean) -> Unit): () -> Unit
