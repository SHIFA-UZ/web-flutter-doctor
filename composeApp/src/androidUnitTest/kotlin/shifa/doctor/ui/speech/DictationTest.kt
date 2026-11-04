package shifa.doctor.ui.speech

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.speech.TranscriptionRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiDailyLimitReached
import shifa.doctor.generated.resources.errorRecordingVoice
import shifa.doctor.generated.resources.noSpeechDetected
import shifa.doctor.generated.resources.speechToTextRequiresPro
import shifa.doctor.generated.resources.transcriptionAdded
import shifa.doctor.generated.resources.transcriptionReportFailed
import shifa.doctor.generated.resources.transcriptionReportThanks
import shifa.doctor.utils.VoiceRecorder

class DictationTest {
    private class FakeRecorder(
        var starts: Boolean = true,
        var audio: ByteArray? = byteArrayOf(1, 2, 3),
    ) : VoiceRecorder {
        var recording = false
        override var recordingId: String? = null
        override var lastDurationSeconds = 0

        override fun start(): Boolean {
            recording = starts
            if (starts) recordingId = "rec-1"
            return starts
        }

        override fun stop(): ByteArray? = audio.takeIf { recording }.also { recording = false }

        override fun level() = 0.5f
    }

    private var transcribed = 0
    private var answer = HttpStatusCode.OK to """{"text":" Pain in 36. ","language":"en"}"""
    private var reportAnswer = HttpStatusCode.BadGateway to """{"status":502}"""
    private var feedback = """{"transcriptionFeedbackEnabled":"true"}"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            if (req.url.encodedPath.endsWith("public/config")) {
                respond(feedback, HttpStatusCode.OK, json)
            } else if (req.url.encodedPath.endsWith("transcription-feedback")) {
                respond(reportAnswer.second, reportAnswer.first, json)
            } else {
                transcribed++
                respond(answer.second, answer.first, json)
            }
        }
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher())

    @After fun tearDown() = scope.cancel()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun dictation(recorder: VoiceRecorder = FakeRecorder()): Dictation {
        val client = { createHttpClient({ null }, engine) }
        return Dictation(scope, recorder, TranscriptionRepository(client), PublicConfigRepository(client), {
            AppLanguage.English
        })
    }

    @Test
    fun `one field records at a time, and its transcript is appended with a space`() {
        val classToTest = dictation()
        classToTest.start("free")
        classToTest.start("soap-plan")
        assertThat(classToTest.state.value.field).isEqualTo("free")
        var text = "Patient says"
        classToTest.finish { field, t ->
            assertThat(field).isEqualTo("free")
            text = appendTranscript(text, t)
        }
        settle { !classToTest.state.value.transcribing && classToTest.state.value.banner != null }
        assertThat(text).isEqualTo("Patient says Pain in 36.")
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.transcriptionAdded)
        assertThat(classToTest.state.value.field).isNull()
        // The backend collects reports: Report is offered for that field.
        assertThat(classToTest.state.value.reportable).isEqualTo("free")
    }

    @Test
    fun `no speech, no PRO plan and the daily limit say so, and nothing is added`() {
        var added = false
        for ((status, body, message) in listOf(
            Triple(HttpStatusCode.OK, """{"text":"(No speech detected)"}""", Res.string.noSpeechDetected),
            Triple(HttpStatusCode.Forbidden, """{"status":403}""", Res.string.speechToTextRequiresPro),
            Triple(HttpStatusCode.TooManyRequests, """{"status":429}""", Res.string.aiDailyLimitReached),
        )) {
            answer = status to body
            val classToTest = dictation()
            classToTest.start("free")
            classToTest.finish { _, _ -> added = true }
            settle { classToTest.state.value.banner != null }
            assertThat(classToTest.state.value.banner?.text).isEqualTo(message)
            assertThat(classToTest.state.value.reportable).isNull()
        }
        assertThat(added).isFalse()
    }

    @Test
    fun `a dropped recording isn't sent, and a recorder that won't start says so`() {
        val classToTest = dictation()
        classToTest.start("free")
        classToTest.cancel()
        assertThat(classToTest.state.value.field).isNull()
        assertThat(transcribed).isEqualTo(0)
        val broken = dictation(FakeRecorder(starts = false))
        broken.start("free")
        assertThat(broken.state.value.field).isNull()
        assertThat(broken.state.value.banner?.text).isEqualTo(Res.string.errorRecordingVoice)
    }

    @Test
    fun `a transcript joins the text with one space`() {
        assertThat(appendTranscript("", "Pain")).isEqualTo("Pain")
        assertThat(appendTranscript("Notes", "Pain")).isEqualTo("Notes Pain")
        assertThat(appendTranscript("Notes ", "Pain")).isEqualTo("Notes Pain")
        assertThat(appendTranscript("Notes\n", "Pain")).isEqualTo("Notes\nPain")
    }

    @Test
    fun `a failed report says so and keeps Report, a sent one thanks`() {
        val classToTest = dictation()
        classToTest.start("free")
        classToTest.finish { _, _ -> }
        settle { classToTest.state.value.reportable == "free" }
        classToTest.report()
        settle { classToTest.state.value.banner?.text == Res.string.transcriptionReportFailed }
        assertThat(classToTest.state.value.reportable).isEqualTo("free")
        reportAnswer = HttpStatusCode.OK to """{"id":"x"}"""
        classToTest.report()
        settle { classToTest.state.value.banner?.text == Res.string.transcriptionReportThanks }
        assertThat(classToTest.state.value.reportable).isNull()
    }
}
