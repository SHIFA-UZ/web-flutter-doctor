@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.ai

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.writeStringUtf8
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt
import shifa.doctor.data.model.AiMessage
import shifa.doctor.data.model.AiRequest
import shifa.doctor.data.model.AiStreamEvent
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CommitVisitFactsRequest
import shifa.doctor.data.storage.AppLanguage

class AiRepositoryTest {
    private val calls = mutableListOf<String>()
    private val accepts = mutableListOf<String?>()

    private fun repo(
        status: HttpStatusCode,
        body: String,
        type: String = "application/json",
    ) = AiRepository({
        createHttpClient(
            { jwt(4_000_000_000L) },
            MockEngine { req ->
                calls += "${req.method.value} ${req.url.encodedPath} ${req.body.toByteArray().decodeToString()}".trim()
                accepts += req.headers[HttpHeaders.Accept]
                respond(body, status, headersOf(HttpHeaders.ContentType, type))
            },
        )
    })

    private val ask =
        AiRequest(
            listOf(AiMessage("user", "What is my day?")),
            AppLanguage.UzbekCyrillic.aiCode,
            conversationId = "s-1",
        )

    // A recorded morning answer: tokens with their leading spaces, a card, a nameless card, an empty data line,
    // a draft, a two-line token, ignored id/retry/comment lines, and a token the stream ends without a blank line.
    private val fixture =
        "data:Good\n\ndata: morning\n\n" +
            "event:tool\ndata:{\"name\":\"get_today_schedule\",\"payload\":{\"cardType\":\"schedule\",\"count\":2}}\n\n" +
            "event:tool\ndata:{\"name\":\"\",\"payload\":{}}\n\n" +
            "data:\n\n" +
            "event:draft\ndata:{\"draftId\":\"7\",\"draftLabel\":\"AI draft\",\"canSave\":true}\n\n" +
            "id: 4\nretry: 1000\n: ping\ndata:two\ndata: lines\n\n" +
            "data: tail"

    @Test
    fun `a stream replays tokens verbatim, cards and the draft, and flushes the tail`() =
        runTest {
            val events = mutableListOf<AiStreamEvent>()
            val result =
                repo(
                    HttpStatusCode.OK,
                    fixture,
                    "text/event-stream",
                ).stream(ask, morning = true) { events += it }
            assertThat(result.isSuccess).isTrue()
            assertThat(events)
                .containsExactly(
                    AiStreamEvent.Token("Good"),
                    AiStreamEvent.Token(" morning"),
                    AiStreamEvent.Tool(
                        "get_today_schedule",
                        JsonObject(mapOf("cardType" to JsonPrimitive("schedule"), "count" to JsonPrimitive(2))),
                    ),
                    AiStreamEvent.Draft("7", "AI draft", true),
                    AiStreamEvent.Token("two\n lines"),
                    AiStreamEvent.Token(" tail"),
                ).inOrder()
            // The body the backend validates: UZ for Cyrillic Uzbek, the session id, no null keys.
            assertThat(calls.single())
                .isEqualTo(
                    "POST /api/ai/morning/stream {\"messages\":[{\"role\":\"user\",\"content\":\"What is my day?\"}],\"language\":\"UZ\",\"conversationId\":\"s-1\"}",
                )
            assertThat(accepts.single()).isEqualTo("text/event-stream")
        }

    @Test
    fun `the patient stream goes to ai_stream, an error event and a non-2xx fail with their codes`() =
        runTest {
            val error =
                repo(
                    HttpStatusCode.OK,
                    "data:Hi\n\nevent:error\ndata:{\"code\":\"AI_UNAVAILABLE\",\"message\":\"x\"}\n\n",
                    "text/event-stream",
                )
                    .stream(ask.copy(patientId = 11, consultationId = 101), morning = false) {}
            assertThat(calls.single()).startsWith("POST /api/ai/stream ")
            assertThat(calls.single()).contains("\"patientId\":11,\"consultationId\":101")
            assertThat((error.exceptionOrNull() as ApiException).code).isEqualTo("AI_UNAVAILABLE")

            val invalid =
                repo(
                    HttpStatusCode.OK,
                    "event:error\ndata:{\"code\":\"VALIDATION\"}\n\n",
                    "text/event-stream",
                ).stream(ask, true) {
                }
            assertThat(invalid.exceptionOrNull()).isInstanceOf(ApiException.ValidationError::class.java)

            val limited =
                repo(
                    HttpStatusCode.TooManyRequests,
                    "{\"status\":429,\"code\":\"RATE_LIMITED\"}",
                ).stream(ask, true) {}
            assertThat(limited.exceptionOrNull()).isInstanceOf(ApiException.RateLimit::class.java)
        }

    @Test
    fun `cancelling the caller closes the connection`() =
        runTest {
            val channel = ByteChannel()
            val classToTest =
                AiRepository({
                    createHttpClient(
                        { jwt(4_000_000_000L) },
                        MockEngine {
                            respond(
                                channel,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "text/event-stream"),
                            )
                        },
                    )
                })
            val events = mutableListOf<AiStreamEvent>()
            withContext(Dispatchers.Default) {
                val job = launch { classToTest.stream(ask, true) { events += it } }
                channel.writeStringUtf8("data:one\n\n")
                channel.flush()
                withTimeout(5_000) { while (events.isEmpty()) delay(10) }
                job.cancelAndJoin()
            }
            assertThat(events).containsExactly(AiStreamEvent.Token("one"))
            assertThat(channel.isClosedForWrite).isTrue()
        }

    @Test
    fun `speak sends the cut text and keeps the audio type`() =
        runTest {
            val classToTest = repo(HttpStatusCode.OK, "RIFF", "audio/wav; charset=UTF-8")
            val audio = classToTest.speak("  Hello there.  ", AppLanguage.Russian).getOrThrow()
            assertThat(audio.mimeType).isEqualTo("audio/wav")
            assertThat(audio.bytes.decodeToString()).isEqualTo("RIFF")
            assertThat(calls.single()).isEqualTo("POST /api/ai/speak {\"text\":\"Hello there.\",\"language\":\"RU\"}")
        }

    @Test
    fun `speech text is cut at the last sentence end past 40, else the last space past 40`() {
        val sentence = "A".repeat(50) + ". " + "b ".repeat(300)
        assertThat(speechText(sentence)).isEqualTo("A".repeat(50) + ".")
        // A full stop before index 40 doesn't count: the last space wins.
        val early = "Hi. " + "word ".repeat(200)
        assertThat(speechText(early)).hasLength(558)
        assertThat(speechText(early)).endsWith("word")
        // One long word: a hard cut at 560.
        assertThat(speechText("x".repeat(600))).hasLength(560)
        assertThat(speechText(" short ")).isEqualTo("short")
    }

    @Test
    fun `a briefing posts the app language and reads the counts`() =
        runTest {
            val classToTest =
                repo(HttpStatusCode.OK, """{"briefing":"**Summary**","documentCount":2,"appointmentCount":3}""")
            val brief = classToTest.patientBriefing(11, AppLanguage.UzbekCyrillic).getOrThrow()
            assertThat(brief.documentCount).isEqualTo(2)
            assertThat(brief.appointmentCount).isEqualTo(3)
            assertThat(calls.single()).isEqualTo("POST /api/ai/patient-briefing/11 {\"language\":\"uz\"}")

            val denied =
                repo(
                    HttpStatusCode.Forbidden,
                    """{"status":403,"code":"FORBIDDEN"}""",
                ).patientBriefing(11, AppLanguage.English)
            assertThat(denied.exceptionOrNull()).isInstanceOf(ApiException.Forbidden::class.java)
        }

    @Test
    fun `visit facts skip a bad row, group kept and dropped, and the timeline and commit read their lists`() =
        runTest {
            val facts =
                repo(
                    HttpStatusCode.OK,
                    """{"facts":[{"id":1,"patientId":11,"factType":"MEDICATION","text":"Ibuprofen","status":"DRAFT","reflectionKept":true,"createdAt":"x"},""" +
                        """{"id":2,"patientId":11,"factType":"DIAGNOSIS","text":"Pulpitis","status":"DRAFT","reflectionKept":false,"createdAt":"x"},{"text":"no id"}],""" +
                        """"patterns":[{"key":"medication","label":"Medication","factType":"MEDICATION","count":3}]}""",
                ).visitFacts(101).getOrThrow()
            assertThat(facts.facts.map { it.id }).containsExactly(1L, 2L)
            assertThat(facts.keptDrafts.map { it.id }).containsExactly(1L)
            assertThat(facts.dropped.map { it.id }).containsExactly(2L)
            assertThat(facts.patterns.single().count).isEqualTo(3)
            assertThat(calls.single()).isEqualTo("GET /api/ai/visit-facts/appointment/101")

            val timeline =
                repo(
                    HttpStatusCode.OK,
                    """{"timeline":[{"id":5,"patientId":11,"factType":"SYMPTOM","text":"Pain","status":"COMMITTED"}],"patterns":[]}""",
                ).timeline(11).getOrThrow()
            assertThat(timeline.facts.single().isCommitted).isTrue()

            val commit =
                repo(
                    HttpStatusCode.OK,
                    """{"committedFacts":[{"id":1,"patientId":11,"factType":"MEDICATION","text":"Ibuprofen","status":"COMMITTED","remoteTaskId":701}],"createdTaskIds":[701],"patterns":[]}""",
                ).commit(CommitVisitFactsRequest(101, rejectFactIds = listOf(2))).getOrThrow()
            assertThat(commit.createdTaskIds).containsExactly(701L)
            assertThat(commit.committedFacts.single().remoteTaskId).isEqualTo(701L)
            assertThat(
                calls.last(),
            ).isEqualTo("POST /api/ai/visit-facts/commit {\"appointmentId\":101,\"rejectFactIds\":[2]}")
        }
}
