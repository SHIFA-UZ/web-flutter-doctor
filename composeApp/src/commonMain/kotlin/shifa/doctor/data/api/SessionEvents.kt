package shifa.doctor.data.api

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Why the backend (or the pre-flight check) ended the session; the sign-in screen words its banner from it. */
enum class SessionEndReason { Expired, Unauthorized, AccountDisabled }

/**
 * The one place a session ends from the network (17 §1): [apiCall] reports here, the session repository (CORE-06)
 * clears the token and the app returns to sign-in. Screens never decide this themselves.
 */
object SessionEvents {
    private val _ended =
        MutableSharedFlow<SessionEndReason>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val ended: SharedFlow<SessionEndReason> = _ended.asSharedFlow()

    fun end(reason: SessionEndReason) {
        _ended.tryEmit(reason)
    }
}
