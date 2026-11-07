package shifa.doctor.utils

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// ponytail: video calls on the web are WEB-08 (Daily's JavaScript SDK); until then a call fails at once.
@Composable
actual fun rememberVideoCallFactory(): () -> VideoCall = { UnsupportedVideoCall() }

private class UnsupportedVideoCall : VideoCall {
    override val state: StateFlow<VideoCallState> = MutableStateFlow(VideoCallState(failed = true))

    override fun startPreview() = Unit

    override fun join(
        roomUrl: String,
        token: String,
        camera: Boolean,
        mic: Boolean,
    ) = Unit

    override fun setCamera(on: Boolean) = Unit

    override fun setMic(on: Boolean) = Unit

    override fun flipCamera() = Unit

    override fun release() = Unit
}

@Composable
actual fun VideoTile(
    call: VideoCall,
    remote: Boolean,
    modifier: Modifier,
) {
    Box(modifier)
}

@Composable
actual fun rememberCallPermissions(onResult: (camera: Boolean, mic: Boolean) -> Unit): (ask: Boolean) -> Unit =
    { onResult(false, false) }
