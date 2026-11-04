package shifa.doctor.data.api

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.url
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.model.ApiException

class SseTest {
    private fun client(
        status: HttpStatusCode,
        body: String,
    ) = createHttpClient(
        { jwt(4_000_000_000L) },
        MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "text/event-stream")) },
    )

    @Test
    fun `events split on blank lines, data lines join, an unterminated event at EOF is dropped`() =
        runTest {
            val events = mutableListOf<SseEvent>()
            readEvents(ByteReadChannel("data: Hel\ndata: lo\n\nevent: done\ndata: {}\n\ndata: tail")) { events += it }
            assertThat(
                events,
            ).containsExactly(SseEvent("", "Hel\nlo"), SseEvent("done", "{}")).inOrder()
        }

    @Test
    fun `a stream delivers its events`() =
        runTest {
            val events = mutableListOf<String>()
            val result =
                client(HttpStatusCode.OK, "data: a\n\ndata: b\n\n").streamEvents({
                    method = HttpMethod.Post
                    url("ai/stream")
                }) { events += it.data }
            assertThat(result.isSuccess).isTrue()
            assertThat(events).containsExactly("a", "b").inOrder()
        }

    @Test
    fun `an error event fails with its code, a non-2xx like apiCall`() =
        runTest {
            val error =
                client(
                    HttpStatusCode.OK,
                    "data: a\n\nevent: error\ndata: {\"code\":\"RATE_LIMITED\"}\n\n",
                ).streamEvents({
                    url("ai/stream")
                }) {}
            assertThat((error.exceptionOrNull() as ApiException).code).isEqualTo("RATE_LIMITED")

            val status =
                client(HttpStatusCode.PaymentRequired, "{\"code\":\"PAYMENT_REQUIRED\"}").streamEvents({
                    url("ai/stream")
                }) {}
            assertThat((status.exceptionOrNull() as ApiException).status).isEqualTo(402)
        }
}
