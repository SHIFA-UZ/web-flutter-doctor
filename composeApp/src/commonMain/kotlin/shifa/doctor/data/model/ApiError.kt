package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/**
 * The backend's error body (17 §1): `{error, message, status, code, requestId}`. `error` is sometimes the status
 * number (read leniently). [code] (`shared/ErrorCode.kt`) is the only thing the app branches on.
 */
@Serializable
data class ApiError(
    val message: String? = null,
    val error: String? = null,
    val code: String? = null,
    val requestId: String? = null,
)

/**
 * A failed call. [code] is the backend's error code, [requestId] its trace id (shown in the error banner's detail).
 * The text shown to the doctor comes from `toUserMessage()`; the exception message is for logs and holds no server
 * text, so it never carries patient data.
 */
sealed class ApiException(
    val status: Int,
    val code: String?,
    val requestId: String?,
) : Exception("HTTP $status ${code.orEmpty()} ${requestId.orEmpty()}".trim()) {
    class ValidationError(status: Int, code: String?, requestId: String?) : ApiException(status, code, requestId)

    class Unauthorized(code: String?, requestId: String?) : ApiException(401, code, requestId)

    class Forbidden(code: String?, requestId: String?) : ApiException(403, code, requestId)

    class NotFound(code: String?, requestId: String?) : ApiException(404, code, requestId)

    class Conflict(code: String?, requestId: String?) : ApiException(409, code, requestId)

    class RateLimit(code: String?, requestId: String?) : ApiException(429, code, requestId)

    class ServerError(status: Int, code: String?, requestId: String?) : ApiException(status, code, requestId)

    /** No response at all; [timeout] when the 30 s limit hit. */
    class NetworkError(val timeout: Boolean) : ApiException(0, null, null)

    companion object {
        fun of(
            status: Int,
            code: String?,
            requestId: String?,
        ): ApiException =
            when (status) {
                400, 422 -> ValidationError(status, code, requestId)
                401 -> Unauthorized(code, requestId)
                403 -> Forbidden(code, requestId)
                404 -> NotFound(code, requestId)
                409 -> Conflict(code, requestId)
                429 -> RateLimit(code, requestId)
                else -> ServerError(status, code, requestId)
            }
    }
}
