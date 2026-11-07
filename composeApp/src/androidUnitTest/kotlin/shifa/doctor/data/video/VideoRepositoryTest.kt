package shifa.doctor.data.video

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.callErrorOccurred
import shifa.doctor.generated.resources.videoCallJoinWindowClosedMessage
import shifa.doctor.generated.resources.videoCallNotYetAvailableMessage
import shifa.doctor.generated.resources.videoCallPaymentRequiredMessage
import kotlin.time.Duration.Companion.minutes

class VideoRepositoryTest {
    private var status = HttpStatusCode.OK
    private val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val classToTest =
        VideoRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    bodies += "${req.url.encodedPath} ${req.body.toByteArray().decodeToString()}"
                    if (status == HttpStatusCode.OK) {
                        respond("""{"token":"t","roomUrl":"https://shifa.daily.co/r1","roomName":"r1"}""", status, json)
                    } else {
                        // dev 2026-11-05: every refusal is a bare FORBIDDEN, the reason only in English text.
                        respond(
                            """{"error":403,"message":"Video call has ended.","status":403,"code":"FORBIDDEN"}""",
                            status,
                            json,
                        )
                    }
                },
            )
        }

    @Test
    fun `the room's token for the visit, a refusal as Forbidden`() =
        runTest {
            val t = classToTest.token(7).getOrThrow()
            assertThat(t.roomUrl).isEqualTo("https://shifa.daily.co/r1")
            assertThat(bodies.single()).isEqualTo("/api/video/token {\"appointmentId\":7}")
            status = HttpStatusCode.Forbidden
            assertThat(classToTest.token(7).exceptionOrNull()).isInstanceOf(ApiException.Forbidden::class.java)
        }

    @Test
    fun `a refusal is explained - 402 is payment, a 403 by the visit's window, 5 minutes before to 15 after`() {
        val start = Instant.parse("2026-11-05T10:00:00Z")
        val end = start + 30.minutes
        val forbidden = ApiException.Forbidden("FORBIDDEN", null)

        fun at(now: Instant) = videoJoinError(forbidden, start, end, now)
        assertThat(at(start - 6.minutes)).isEqualTo(Res.string.videoCallNotYetAvailableMessage)
        assertThat(at(end + 16.minutes)).isEqualTo(Res.string.videoCallJoinWindowClosedMessage)
        // Inside the window a 403 is someone else's visit: the generic error.
        assertThat(at(start - 5.minutes)).isEqualTo(Res.string.callErrorOccurred)
        assertThat(at(end + 15.minutes)).isEqualTo(Res.string.callErrorOccurred)
        assertThat(
            videoJoinError(ApiException.of(402, "PAYMENT_REQUIRED", null), start, end, start),
        ).isEqualTo(Res.string.videoCallPaymentRequiredMessage)
        assertThat(
            videoJoinError(ApiException.NetworkError(false), start, end, start),
        ).isEqualTo(Res.string.callErrorOccurred)
    }

    @Test
    fun `the backend's codes win over the guess from the window (BE-28)`() {
        val start = Instant.parse("2026-11-05T10:00:00Z")
        val end = start + 30.minutes
        // Inside the window by the app's clock, yet the backend says too early / too late: the backend is right.
        assertThat(videoJoinError(ApiException.Forbidden("VIDEO_TOO_EARLY", null), start, end, start))
            .isEqualTo(Res.string.videoCallNotYetAvailableMessage)
        assertThat(videoJoinError(ApiException.Forbidden("VIDEO_TOO_LATE", null), start, end, start))
            .isEqualTo(Res.string.videoCallJoinWindowClosedMessage)
        assertThat(videoJoinError(ApiException.of(402, "VIDEO_PAYMENT_REQUIRED", null), start, end, start))
            .isEqualTo(Res.string.videoCallPaymentRequiredMessage)
    }
}
