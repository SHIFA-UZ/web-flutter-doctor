package shifa.doctor.data.tracking.events

/**
 * Stage A (A1b): reliability, sent by `apiCall` (`data/api/ApiHelper.kt`) for calls to our own backend's API only.
 * Same names and properties as the patient app, so the alert chart "API errors · both apps" reads both.
 * [ApiError.endpoint] / [ApiSlow.endpoint] are word-level labels (`patients/{id}/documents`, see `endpointLabel`),
 * never the path or its query.
 */
object ApiEvents {
    /**
     * A backend call that failed with a response: any non-2xx (a 401 that ends the session too, as in the patient
     * app) or a 2xx body that could not be read. [error]: `toFailureReason()`; [code]: the backend's error code
     * (`APPOINTMENT_LOCKED_24H`), only when it has that shape. Not sent without a response (no host to tell ours from a
     * file host, and offline polling would flood it), nor for a cancelled call.
     */
    data class ApiError(
        val endpoint: String,
        val method: String,
        val status: Int,
        val error: String,
        val code: String?,
    ) : TrackEvent(
            "api_error",
            "endpoint" to endpoint,
            "method" to method,
            "status" to status,
            "error" to error,
            "code" to code,
        )

    /** A backend call that succeeded but took longer than `SLOW_CALL` (5 s), body read included. */
    data class ApiSlow(
        val endpoint: String,
        val method: String,
        val ms: Long,
    ) : TrackEvent("api_slow", "endpoint" to endpoint, "method" to method, "ms" to ms)
}
