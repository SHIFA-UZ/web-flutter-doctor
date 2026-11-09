package shifa.doctor.data.push

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import shifa.doctor.utils.pushPlatform

/**
 * PUSH-01…03: what the platforms hand the shared code. Android's messaging service and activity, and iOS's AppDelegate
 * (Swift: `Push.shared`), report this device's FCM token, a push that arrived while the app was open, and a tapped push;
 * the web has no push. [PushRegistration] puts the token on the doctor's account; a tap waits in [tapped] until the shell
 * opens it (a cold start taps before anything is drawn, and a signed-out tap waits for the sign-in). Push data is never
 * logged: it names patients.
 */
object Push {
    data class Device(
        val token: String,
        // `android` or `ios`, as `POST /doctors/me/fcm-tokens` takes it.
        val platform: String,
    )

    private val _device = MutableStateFlow<Device?>(null)
    val device: StateFlow<Device?> = _device.asStateFlow()

    /** The last tapped push's `data`; the shell takes it (and sets it back to null) when it opens the target. */
    val tapped = MutableStateFlow<Map<String, String>?>(null)

    private val _received =
        MutableSharedFlow<Map<String, String>>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Pushes that arrived while the app was open (PUSH-02): counts, the inbox and the schedule read again. */
    val received: SharedFlow<Map<String, String>> = _received.asSharedFlow()

    fun onToken(token: String?) {
        _device.value = token?.trim()?.takeIf { it.isNotEmpty() }?.let { Device(it, pushPlatform) }
    }

    fun onTap(data: Map<String, String>) {
        tapped.value = data
    }

    fun onReceived(data: Map<String, String>) {
        _received.tryEmit(data)
    }
}
