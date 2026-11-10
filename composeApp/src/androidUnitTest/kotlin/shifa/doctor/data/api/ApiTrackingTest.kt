package shifa.doctor.data.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.ApiEvents
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class Row(
    val id: String,
)

/** A1b: `api_error` / `api_slow` from `apiCall`, for our backend's API only. */
class ApiTrackingTest {
    private val tracker = RecordingTracker()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    @Before
    fun setUp() {
        apiTracker = tracker
        SessionEvents.listener = { _, _ -> }
    }

    @After
    fun tearDown() {
        apiTracker = Tracking
        SessionEvents.listener = { _, _ -> }
    }

    private fun client(respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        createHttpClient({ jwt(1_800_003_600) }, MockEngine { respond(it) }, ApiConfig.BASE_URL, { 1_800_000_000 })

    private fun status(
        status: HttpStatusCode,
        body: String = "{}",
    ) = client { respond(body, status, json) }

    @Test
    fun `a failed backend call is api_error with a word-level endpoint and the backend code`() =
        runTest {
            apiCall<Row> {
                status(HttpStatusCode.NotFound, """{"code":"PATIENT_NOT_FOUND","message":"Patient Ali not found"}""")
                    .get("patients/123/documents/9f1c2d3e-aaaa-bbbb-cccc-1234567890ab?q=Ali")
            }

            assertThat(tracker.events)
                .containsExactly(
                    ApiEvents.ApiError("patients/{id}/documents/{id}", "GET", 404, "not_found", "PATIENT_NOT_FOUND"),
                )
        }

    @Test
    fun `a 401 that ends the session is counted too, as in the patient app`() =
        runTest {
            var ended = false
            SessionEvents.listener = { _, _ -> ended = true }

            apiCall<Row> { status(HttpStatusCode.Unauthorized).get(ApiConfig.Endpoints.DOCTOR_PROFILE) }

            assertThat(ended).isTrue()
            assertThat(tracker.events)
                .containsExactly(ApiEvents.ApiError("doctors/me/profile", "GET", 401, "unauthorized", null))
        }

    @Test
    fun `a code that is not a backend code is left out`() =
        runTest {
            apiCall<Row> {
                status(
                    HttpStatusCode.BadRequest,
                    """{"code":"bad value: +998901234567"}""",
                ).post("patients")
            }

            assertThat(tracker.events).containsExactly(ApiEvents.ApiError("patients", "POST", 400, "validation", null))
        }

    @Test
    fun `an unreadable 2xx body is api_error with error server`() =
        runTest {
            apiCall<Row> { status(HttpStatusCode.OK, "<html>").get("calendar") }

            assertThat(tracker.events).containsExactly(ApiEvents.ApiError("calendar", "GET", 200, "server", null))
        }

    @Test
    fun `file URLs, other hosts, no response and cancellation send nothing`() =
        runTest {
            val failing = status(HttpStatusCode.InternalServerError)
            apiCall<Row> { failing.get("https://api.shifa.uz/patientdocuments/7/scan.pdf?exp=1&sig=x") }
            apiCall<Row> { failing.get("https://files.example.com/api/x") }
            apiCall<Row> { client { throw IOException("offline") }.get("calendar") }
            runCatching { apiCall<Row> { throw CancellationException("left the screen") } }
            apiCall<Row> { status(HttpStatusCode.OK, """{"id":"1"}""").get("calendar") }

            assertThat(tracker.events).isEmpty()
        }

    @Test
    fun `a successful backend call over five seconds is api_slow`() =
        runTest {
            val response = status(HttpStatusCode.OK).get(ApiConfig.Endpoints.CALENDAR)
            trackApiSlow(response, 5.seconds)
            trackApiSlow(response, 6.seconds)
            trackApiSlow(status(HttpStatusCode.OK).get("https://files.example.com/api/x"), 9.seconds)

            assertThat(tracker.events).containsExactly(ApiEvents.ApiSlow("calendar", "GET", 6_000))
        }

    @Test
    fun `labels keep only the API's words`() {
        assertThat(endpointLabel("/api/doctors/me/fcm-tokens/dQw4w9WgXcQ%3AAPA91b"))
            .isEqualTo("doctors/me/fcm-tokens/{id}")
        assertThat(
            endpointLabel("/api/ai/draft/9f1c2d3e-aaaa-bbbb-cccc-1234567890ab/confirm"),
        ).isEqualTo("ai/draft/{id}/confirm")
        assertThat(endpointLabel("/api/patients/ali")).isEqualTo("patients/{id}")
    }

    @Test
    fun `every fixed endpoint labels without an id`() {
        val paths =
            ApiConfig.Endpoints::class.java.declaredFields
                .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .map { it.get(null) as String }
        assertThat(paths).isNotEmpty()
        paths.forEach { assertThat(endpointLabel("/api/$it")).isEqualTo(it) }
    }
}
