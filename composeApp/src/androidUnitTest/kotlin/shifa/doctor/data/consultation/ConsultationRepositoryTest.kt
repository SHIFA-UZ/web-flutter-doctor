@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.consultation

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.storage.AppLanguage
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

class ConsultationRepositoryTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repo(vararg answers: Pair<HttpStatusCode, String>): ConsultationRepository {
        val queue = ArrayDeque(answers.toList())
        val engine =
            MockEngine { req ->
                calls += "${req.method.value} ${req.url.encodedPath} ${req.body.toByteArray().decodeToString()}".trim()
                val (status, body) = queue.removeFirst()
                respond(body, status, json)
            }
        return ConsultationRepository({ createHttpClient({ null }, engine) })
    }

    @Test
    fun `a failed read is a failure, not an empty list, and one bad note doesn't drop the rest`() =
        runTest {
            val classToTest =
                repo(
                    HttpStatusCode.InternalServerError to """{"status":500,"code":"X"}""",
                    HttpStatusCode.OK to """[{"id":1,"body":"Pain","source":"MANUAL"},{"id":"x"},null]""",
                    HttpStatusCode.OK to """[{"id":"6f1c","body":"SOAP","icdSuggestions":[{"code":"K04.0","title":"Pulpitis"}]}]""",
                )
            assertThat(classToTest.notes(7).exceptionOrNull()).isInstanceOf(ApiException.ServerError::class.java)
            assertThat(classToTest.notes(7).getOrThrow().map { it.id }).containsExactly(1L)
            assertThat(classToTest.drafts(7).getOrThrow().single().icdSuggestions.single().code).isEqualTo("K04.0")
        }

    @Test
    fun `notes are saved trimmed, blank ones not at all, and complete leaves out what's empty`() =
        runTest {
            val classToTest =
                repo(
                    HttpStatusCode.OK to "",
                    HttpStatusCode.OK to """{"status":"COMPLETED","scribePending":true}""",
                    HttpStatusCode.OK to """{}""",
                )
            assertThat(classToTest.saveNotes(7, "   ").isSuccess).isTrue()
            assertThat(classToTest.saveNotes(7, " Pain ").isSuccess).isTrue()
            assertThat(
                classToTest.complete(
                    7,
                    clinicId = 3,
                    doctorNotes = " ",
                    awaitingScribe = true,
                ).getOrThrow().scribePending,
            )
                .isTrue()
            assertThat(classToTest.complete(7).getOrThrow().hasDocumentation).isFalse()
            assertThat(calls).containsExactly(
                """PUT /api/appointments/7/consultation-notes {"body":"Pain"}""",
                """PUT /api/appointments/7/complete {"clinicId":3,"awaitingScribe":true}""",
                """PUT /api/appointments/7/complete {}""",
            ).inOrder()
        }

    // The polls run on real time with short steps: runTest's virtual clock jumps while the mock engine answers on
    // another thread, which would cut every read at once.
    @Test
    fun `the scribe is asked again until its note is there, a passing failure in between`() =
        runBlocking {
            val classToTest =
                repo(
                    HttpStatusCode.OK to """{"status":"processing"}""",
                    HttpStatusCode.BadGateway to """{"status":502}""",
                    HttpStatusCode.OK to """{"status":"ready","hasScribeNote":true,"hasDocumentation":true}""",
                )
            classToTest.scribeUpdates(7, every = 20.milliseconds).test {
                assertThat(awaitItem().getOrThrow().hasScribeNote).isFalse()
                assertThat(awaitItem().isFailure).isTrue()
                assertThat(awaitItem().getOrThrow().hasScribeNote).isTrue()
                awaitComplete()
            }
            assertThat(calls).hasSize(3)
        }

    @Test
    fun `the scribe wait gives up at its deadline, a read that hangs included`() =
        runBlocking {
            val pending = HttpStatusCode.OK to """{"status":"processing"}"""
            val classToTest = repo(*Array(50) { pending })
            val reads = classToTest.scribeUpdates(7, every = 100.milliseconds, timeout = 350.milliseconds).toList()
            assertThat(reads.size).isIn(3..4)
            val hanging =
                MockEngine {
                    delay(60_000)
                    respond("{}", HttpStatusCode.OK, json)
                }
            val started = TimeSource.Monotonic.markNow()
            val none =
                ConsultationRepository(
                    { createHttpClient({ null }, hanging) },
                ).scribeUpdates(7, timeout = 300.milliseconds).toList()
            assertThat(none).isEmpty()
            assertThat(started.elapsedNow()).isLessThan(5.seconds)
        }

    @Test
    fun `a scribe recording goes as audio with the visit and the app's language`() =
        runTest {
            val classToTest = repo(HttpStatusCode.Accepted to """{"message":"ok"}""")
            assertThat(
                classToTest.uploadRecording(7, byteArrayOf(1, 2), AppLanguage.UzbekCyrillic, "rec-1").isSuccess,
            ).isTrue()
            val sent = calls.single()
            assertThat(sent).startsWith("POST /api/consultations/upload-recording")
            assertThat(sent).contains("name=audio")
            assertThat(sent).contains("filename=\"ai_notes_rec-1.m4a\"")
            assertThat(
                sent.substringAfter("name=appointmentId").substringAfter("\r\n\r\n").substringBefore("\r\n"),
            ).isEqualTo("7")
            assertThat(
                sent.substringAfter("name=language").substringAfter("\r\n\r\n").substringBefore("\r\n"),
            ).isEqualTo("uz")
        }
}
