package shifa.doctor.data.appointments

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.SignatureState

class AppointmentsRepositoryTest {
    private val calls = mutableListOf<String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repo(vararg answers: String): AppointmentsRepository {
        val queue = ArrayDeque(answers.toList())
        val engine =
            MockEngine { req ->
                calls += "${req.method.value} ${req.url.encodedPath}"
                val next = queue.removeFirst()
                if (next == "500") {
                    respond("""{"status":500,"code":"X"}""", HttpStatusCode.InternalServerError, json)
                } else if (next == "404") {
                    respond("""{"status":404,"code":"NOT_FOUND"}""", HttpStatusCode.NotFound, json)
                } else {
                    respond(next, HttpStatusCode.OK, json)
                }
            }
        return AppointmentsRepository({ createHttpClient({ null }, engine) })
    }

    @Test
    fun `the signature is read every 10 s while awaited, a failed read retried, and the flow ends once signed`() =
        runTest {
            val classToTest =
                repo(
                    """{"id":7,"signatureRequested":true}""",
                    "500",
                    """{"id":7,"signatureRequested":true}""",
                    """{"id":7,"signatureRequested":true,"patientSignedAt":"2026-11-04T05:01:00Z"}""",
                )
            classToTest.signatureUpdates(7).test {
                assertThat(awaitItem().getOrThrow().signature).isEqualTo(SignatureState.Waiting)
                assertThat(awaitItem().exceptionOrNull()).isInstanceOf(ApiException.ServerError::class.java)
                assertThat(awaitItem().getOrThrow().signature).isEqualTo(SignatureState.Waiting)
                assertThat(awaitItem().getOrThrow().signature).isEqualTo(SignatureState.Signed)
                awaitComplete()
            }
            assertThat(calls).containsExactly(
                "GET /api/appointments/7",
                "GET /api/appointments/7",
                "GET /api/appointments/7",
                "GET /api/appointments/7",
            )
        }

    @Test
    fun `a visit whose signature isn't asked is read once, and a request is a PUT with an empty body`() =
        runTest {
            val classToTest = repo("""{"id":7}""", "")
            classToTest.signatureUpdates(7).test {
                assertThat(awaitItem().getOrThrow().signature).isEqualTo(SignatureState.None)
                awaitComplete()
            }
            assertThat(classToTest.requestSignature(7).isSuccess).isTrue()
            assertThat(calls.last()).isEqualTo("PUT /api/appointments/7/request-signature")
        }

    @Test
    fun `a lasting failure ends the polling instead of asking every 10 s`() =
        runTest {
            val classToTest = repo("404")
            classToTest.signatureUpdates(7).test {
                assertThat(awaitItem().exceptionOrNull()).isInstanceOf(ApiException.NotFound::class.java)
                awaitComplete()
            }
            assertThat(calls).hasSize(1)
        }
}
