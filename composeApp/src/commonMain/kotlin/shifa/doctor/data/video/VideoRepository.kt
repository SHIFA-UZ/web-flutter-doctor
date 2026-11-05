package shifa.doctor.data.video

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.callErrorOccurred
import shifa.doctor.generated.resources.videoCallJoinWindowClosedMessage
import shifa.doctor.generated.resources.videoCallNotYetAvailableMessage
import shifa.doctor.generated.resources.videoCallPaymentRequiredMessage
import kotlin.time.Duration.Companion.minutes

@Serializable
private data class TokenRequest(
    val appointmentId: Long,
)

/** `VideoTokenResponse`: a Daily meeting token for the visit's room. */
@Serializable
data class VideoToken(
    val token: String,
    val roomUrl: String,
    val roomName: String = "",
)

/** The Daily room of a video visit (VIDEO-01, 05 §3.1): `POST /video/token {appointmentId}`. */
class VideoRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun token(appointmentId: Long): Result<VideoToken> =
        apiCall<VideoToken> {
            client().post(ApiConfig.Endpoints.VIDEO_TOKEN) {
                contentType(ContentType.Application.Json)
                setBody(TokenRequest(appointmentId))
            }
        }
}

val videoRepository = VideoRepository()

/**
 * Why a room can't be joined, in the doctor's words (the backend's `VideoController`): an unpaid paid visit is 402
 * PAYMENT_REQUIRED; a visit outside its window is a bare 403 FORBIDDEN, the reason only in English text, which isn't
 * shown (BE-28 asks for codes), so it comes from the visit's own window, which matches the backend's: from 5 minutes
 * before the start to 15 minutes after the end. A 403 inside the window (not this doctor's visit) and anything else
 * is the generic call error.
 */
fun videoJoinError(
    e: Throwable,
    start: Instant?,
    end: Instant?,
    now: Instant,
): StringResource {
    if (e !is ApiException) return Res.string.callErrorOccurred
    return when {
        e.status == 402 -> Res.string.videoCallPaymentRequiredMessage
        e !is ApiException.Forbidden -> Res.string.callErrorOccurred
        start != null && now < start - JOIN_BEFORE -> Res.string.videoCallNotYetAvailableMessage
        end != null && now > end + JOIN_AFTER -> Res.string.videoCallJoinWindowClosedMessage
        else -> Res.string.callErrorOccurred
    }
}

val JOIN_BEFORE = 5.minutes
val JOIN_AFTER = 15.minutes
