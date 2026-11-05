package shifa.doctor.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.UIKitView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.requestAccessForMediaType
import platform.UIKit.UIView
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Daily's iOS SDK is Swift only, so iosApp implements this (iosApp/iosApp/DailyVideoCall.swift) and registers it
 * in [VideoCalls] at launch. The views keep their own tracks.
 */
interface NativeVideoCall {
    fun setListener(listener: NativeVideoCallListener)

    fun startPreview()

    fun join(
        roomUrl: String,
        token: String,
    )

    fun setCamera(on: Boolean)

    fun setMic(on: Boolean)

    fun flipCamera()

    /** Leaves and frees the call (`release` is NSObject's in Swift). */
    fun leave()

    fun localView(): UIView

    fun remoteView(): UIView
}

interface NativeVideoCallListener {
    fun onChange(
        joined: Boolean,
        left: Boolean,
        cameraOn: Boolean,
        micOn: Boolean,
        patientJoined: Boolean,
        failed: Boolean,
    )
}

/** Set by iosApp (iOSApp.swift) before the first screen. */
object VideoCalls {
    var factory: (() -> NativeVideoCall)? = null
}

private class IosVideoCall(
    private val native: NativeVideoCall?,
) : VideoCall,
    NativeVideoCallListener {
    private val _state = MutableStateFlow(VideoCallState())
    override val state: StateFlow<VideoCallState> = _state.asStateFlow()

    init {
        native?.setListener(this)
    }

    override fun onChange(
        joined: Boolean,
        left: Boolean,
        cameraOn: Boolean,
        micOn: Boolean,
        patientJoined: Boolean,
        failed: Boolean,
    ) {
        val old = _state.value
        val phase =
            when {
                left -> VideoCallState.Phase.Left
                joined -> VideoCallState.Phase.Joined
                failed && old.phase == VideoCallState.Phase.Joining -> VideoCallState.Phase.Preview
                else -> old.phase
            }
        _state.value = old.copy(phase, cameraOn, micOn, patientJoined, old.tracks + 1, failed)
    }

    override fun startPreview() {
        native?.startPreview()
        _state.value = _state.value.copy(phase = VideoCallState.Phase.Preview)
    }

    override fun join(
        roomUrl: String,
        token: String,
    ) {
        _state.value = _state.value.copy(phase = VideoCallState.Phase.Joining, failed = native == null)
        native?.join(roomUrl, token) ?: run { _state.value = _state.value.copy(phase = VideoCallState.Phase.Preview) }
    }

    override fun setCamera(on: Boolean) {
        native?.setCamera(on)
    }

    override fun setMic(on: Boolean) {
        native?.setMic(on)
    }

    override fun flipCamera() {
        native?.flipCamera()
    }

    override fun release() {
        native?.leave()
    }

    fun view(remote: Boolean) = native?.let { if (remote) it.remoteView() else it.localView() }
}

@Composable
actual fun rememberVideoCallFactory(): () -> VideoCall = remember { { IosVideoCall(VideoCalls.factory?.invoke()) } }

@Composable
actual fun VideoTile(
    call: VideoCall,
    remote: Boolean,
    modifier: Modifier,
) {
    val view = (call as? IosVideoCall)?.view(remote) ?: return Box(modifier)
    UIKitView(factory = { view }, modifier = modifier)
}

@Composable
actual fun rememberCallPermissions(onResult: (Boolean) -> Unit): () -> Unit {
    val result by rememberUpdatedState(onResult)
    return remember {
        {
            AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { video ->
                AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { audio ->
                    dispatch_async(dispatch_get_main_queue()) { result(video && audio) }
                }
            }
        }
    }
}
