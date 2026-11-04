package shifa.doctor.data.api

/**
 * Why a session ended; the sign-in screen words its banner from it. [SignedOut] is the doctor's own choice (AUTH-09),
 * [PinLockout] five wrong app-lock PINs (LOCK-01), [Inactivity] 30 days untouched (LOCK-02), the others come from the
 * backend or the expiry checks.
 */
enum class SessionEndReason { Expired, Unauthorized, AccountDisabled, SignedOut, PinLockout, Inactivity }

/**
 * The one place a session ends from the network (17 §1): [apiCall] reports here with the token the failing request
 * carried, and the session repository ends that session only if it is still the current one (a late 401 from a
 * previous session must not sign out the next). Delivery is synchronous, so nothing is lost before a collector starts.
 */
object SessionEvents {
    /** Set by the session repository when it is created, before any request can carry a token. */
    var listener: (reason: SessionEndReason, token: String) -> Unit = { _, _ -> }

    /** Never throws into the request that reported it: a failing listener is logged and the call fails as usual. */
    fun end(
        reason: SessionEndReason,
        token: String,
    ) {
        runCatching {
            listener(
                reason,
                token,
            )
        }.onFailure { httpLogger.log("FAILED session end: ${it::class.simpleName}") }
    }
}
