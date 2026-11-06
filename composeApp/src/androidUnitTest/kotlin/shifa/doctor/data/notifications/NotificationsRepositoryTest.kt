@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.notifications

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.Test
import shifa.doctor.data.api.createHttpClient

class NotificationsRepositoryTest {
    private val asked = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val classToTest =
        NotificationsRepository({
            createHttpClient(
                { null },
                MockEngine { req ->
                    asked += req.method.value + " " +
                        req.url.encodedPath.substringAfter(
                            "/api/",
                        ) +
                        req.url.parameters.entries().joinToString("") {
                            "&${it.key}=${it.value.first()}"
                        }
                    val body =
                        if (req.url.encodedPath.endsWith("notifications/page")) {
                            // One row without an id and one without a time are skipped, not the page.
                            """{"content":[{"id":95,"type":"appointment_cancelled_by_patient","title":"T","message":"M","appointmentId":24,"patientName":"  ","appointmentStartAt":"2026-11-02T04:00:00Z","createdAt":"2026-10-31T05:38:43.451829Z","readAt":null,"newField":1},{"type":"GENERAL","createdAt":"2026-10-31T05:00:00Z"},{"id":3,"type":"GENERAL"},{"id":2,"type":"GENERAL","createdAt":"2026-10-30T05:00:00Z","readAt":"2026-10-30T06:00:00Z"}],"number":0,"last":false}"""
                        } else {
                            ""
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        })

    @Test
    fun `a page is read row by row, newest first as the backend sends it`() =
        runTest {
            val page = classToTest.page(0).getOrThrow()
            assertThat(asked).containsExactly("GET notifications/page&page=0&size=20")
            assertThat(page.items.map { it.id }).containsExactly(95L, 2L).inOrder()
            assertThat(page.last).isFalse()
            val first = page.items.first()
            assertThat(first.kind).isEqualTo(NotificationKind.Cancelled)
            assertThat(first.read).isFalse()
            assertThat(first.patientName).isNull()
            assertThat(first.appointmentStartAt).isEqualTo(Instant.parse("2026-11-02T04:00:00Z"))
            assertThat(page.items.last().read).isTrue()
        }

    @Test
    fun `reading one or all goes to their paths`() =
        runTest {
            classToTest.markRead(95).getOrThrow()
            classToTest.markAllRead().getOrThrow()
            assertThat(asked).containsExactly("PUT notifications/95/read", "PUT notifications/read-all").inOrder()
        }
}
