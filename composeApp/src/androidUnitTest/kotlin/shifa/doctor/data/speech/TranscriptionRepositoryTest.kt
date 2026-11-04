package shifa.doctor.data.speech

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.storage.AppLanguage

class TranscriptionRepositoryTest {
    private var bodies = mutableListOf<String>()
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun repo(
        status: HttpStatusCode,
        body: String,
    ) = TranscriptionRepository({
        createHttpClient(
            { null },
            MockEngine { req ->
                bodies += req.body.toByteArray().decodeToString()
                respond(body, status, json)
            },
        )
    })

    @Test
    fun `a recording goes as a file with the language hint and its id, the text comes back trimmed`() =
        runTest {
            val result =
                repo(HttpStatusCode.OK, """{"text":"  Pain in 36. ","language":"RU"}""")
                    .transcribe(
                        "audio".encodeToByteArray(),
                        AppLanguage.UzbekCyrillic,
                        "rec-0123456789abcdef",
                    ).getOrThrow()
            assertThat(result).isEqualTo(Transcript("Pain in 36.", "ru"))
            val sent = bodies.single()
            assertThat(sent).contains("name=file")
            assertThat(sent).contains("filename=\"rec-0123456789abcdef.m4a\"")
            assertThat(sent).contains("Content-Type: audio/mp4")
            assertThat(sent).contains("name=languageHint")
            // Cyrillic Uzbek goes as uz (the backend knows en, uz, ru).
            assertThat(
                sent.substringAfter("name=languageHint").substringAfter("\r\n\r\n").substringBefore("\r\n"),
            ).isEqualTo("uz")
            assertThat(sent).contains("name=recordingId")
            assertThat(Transcript("(No speech detected)").empty).isTrue()
            assertThat(Transcript(" ").empty).isTrue()
        }

    @Test
    fun `no PRO plan, the daily limit and a failed recognition stay typed`() =
        runTest {
            val audio = byteArrayOf(1)
            assertThat(
                repo(
                    HttpStatusCode.Forbidden,
                    """{"status":403}""",
                ).transcribe(audio, AppLanguage.English, null).exceptionOrNull(),
            )
                .isInstanceOf(ApiException.Forbidden::class.java)
            assertThat(
                repo(
                    HttpStatusCode.TooManyRequests,
                    """{"status":429}""",
                ).transcribe(audio, AppLanguage.English, null).exceptionOrNull(),
            )
                .isInstanceOf(ApiException.RateLimit::class.java)
            assertThat(
                repo(
                    HttpStatusCode.BadGateway,
                    """{"status":502}""",
                ).transcribe(audio, AppLanguage.English, null).exceptionOrNull(),
            )
                .isInstanceOf(ApiException.ServerError::class.java)
        }

    @Test
    fun `reports are offered when public config says true, as text or as a boolean`() =
        runTest {
            suspend fun enabled(body: String) =
                PublicConfigRepository(
                    { createHttpClient({ null }, MockEngine { respond(body, HttpStatusCode.OK, json) }) },
                )
                    .transcriptionFeedbackEnabled()
                    .getOrDefault(false)
            assertThat(enabled("""{"transcriptionFeedbackEnabled":"true"}""")).isTrue()
            assertThat(enabled("""{"transcriptionFeedbackEnabled":true}""")).isTrue()
            assertThat(enabled("""{"transcriptionFeedbackEnabled":"false"}""")).isFalse()
            assertThat(enabled("""{}""")).isFalse()
            assertThat(enabled("""<html>""")).isFalse()
            // A failure is kept for the run: no second request.
            var reads = 0
            val config =
                PublicConfigRepository({
                    createHttpClient(
                        { null },
                        MockEngine {
                            reads++
                            respond("<html>", HttpStatusCode.BadGateway)
                        },
                    )
                })
            assertThat(config.transcriptionFeedbackEnabled().isFailure).isTrue()
            assertThat(config.transcriptionFeedbackEnabled().isFailure).isTrue()
            assertThat(reads).isEqualTo(1)
        }
}
