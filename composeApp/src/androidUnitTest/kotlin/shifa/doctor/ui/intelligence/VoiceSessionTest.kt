@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.intelligence

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.speech.TranscriptionRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.intelCouldNotStartMicMobile
import shifa.doctor.generated.resources.intelNoSpeech
import shifa.doctor.generated.resources.intelNoSpeechShort
import shifa.doctor.generated.resources.intelRecordingStoppedBackground
import shifa.doctor.generated.resources.intelSignOffThanks
import shifa.doctor.generated.resources.intelSpeechProOrDenied
import shifa.doctor.generated.resources.intelStopped
import shifa.doctor.generated.resources.intelVoiceSkipped
import shifa.doctor.ui.widgets.VoicePhase
import shifa.doctor.utils.SpeechPlayer
import shifa.doctor.utils.VoiceRecorder

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceSessionTest {
    // The microphone: its level by the time since the take started; start fails when [fails].
    private class FakeRecorder(
        val clock: () -> Long,
    ) : VoiceRecorder {
        var fails = false
        var started = 0L
        var takes = 0
        var stops = 0
        var level: (Long) -> Float = { 0.05f }

        override fun start(): Boolean {
            if (fails) return false
            started = clock()
            takes++
            return true
        }

        override suspend fun stop(): ByteArray {
            stops++
            return byteArrayOf(1, 2, 3)
        }

        override fun cancel() {
            stops++
        }

        override fun level() = level(clock() - started)

        override val recordingId = "rec-1"
        override val lastDurationSeconds = 0
    }

    private class FakePlayer : SpeechPlayer {
        var played = 0
        var stopped = 0
        var hold: CompletableDeferred<Unit>? = null
        var plays = true

        override suspend fun play(audio: ByteArray): Boolean {
            played++
            hold?.await()
            return plays
        }

        override fun stop() {
            stopped++
        }
    }

    // The server: the transcript to answer (status, JSON) and the speech clip.
    private var transcript: Pair<HttpStatusCode, String> = HttpStatusCode.OK to """{"text":"what is my day","language":"en"}"""
    private var transcribed = 0

    // Holds the transcription's answer until completed (a slow server).
    private var transcribeGate: CompletableDeferred<Unit>? = null
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                if (req.url.encodedPath.endsWith("ai/transcribe")) {
                    transcribed++
                    transcribeGate?.await()
                    respond(transcript.second, transcript.first, headersOf(HttpHeaders.ContentType, "application/json"))
                } else {
                    respond(byteArrayOf(9, 9), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "audio/mpeg"))
                }
            },
        )

    private val asked = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String?>>()
    private var cancelled = 0
    private var typedReply = false
    private val player = FakePlayer()

    private fun TestScope.session(
        recorder: FakeRecorder,
        scope: CoroutineScope = backgroundScope,
    ): VoiceSession =
        VoiceSession(
            scope,
            TranscriptionRepository { client },
            AiRepository { client },
            player = { player },
            ask = { text, language ->
                asked += text to language
                true
            },
            cancelTurn = { cancelled++ },
            busy = { typedReply },
            clock = { testScheduler.currentTime },
        ).also { it.attach(recorder) }

    private fun TestScope.recorder() = FakeRecorder { testScheduler.currentTime }

    // Real HTTP work runs off the test clock: let it finish.
    private fun TestScope.settle(check: () -> Boolean) {
        repeat(300) {
            runCurrent()
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `a hands-free take ends after a pause, is asked, spoken, and the mic opens again`() =
        runTest {
            val rec = recorder()
            // Speech for 1 s, then silence.
            rec.level = { t -> if (t in 300..1300) 0.6f else 0.05f }
            val classToTest = session(rec)
            classToTest.mic()
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)
            assertThat(classToTest.state.value.handsFree).isTrue()
            advanceTimeBy(1_000)
            assertThat(classToTest.state.value.heard).isTrue()
            // 1.9 s of silence after the speech sends.
            advanceTimeBy(2_400)
            settle { asked.isNotEmpty() }
            assertThat(asked.single()).isEqualTo("what is my day" to "EN")
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Thinking)

            classToTest.replied("You have two visits.", "EN")
            settle { player.played == 1 && classToTest.state.value.phase == VoicePhase.Idle }
            assertThat(classToTest.state.value.handsFree).isTrue()
            advanceTimeBy(VoiceSession.RELISTEN_MS + 10)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)
            assertThat(rec.takes).isEqualTo(2)
        }

    @Test
    fun `nothing heard for 20 seconds gives up and ends hands-free`() =
        runTest {
            val classToTest = session(recorder())
            classToTest.mic()
            advanceTimeBy(Vad.NO_SPEECH_MS + 100)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
            assertThat(classToTest.state.value.handsFree).isFalse()
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelNoSpeech)
            assertThat(transcribed).isEqualTo(0)
        }

    @Test
    fun `the mic while Shifa speaks interrupts her and listens, a double tap is ignored`() =
        runTest {
            val rec = recorder()
            val classToTest = session(rec)
            player.hold = CompletableDeferred()
            classToTest.replied("A long answer.", "EN")
            settle { player.played == 1 }
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Speaking)
            classToTest.mic()
            assertThat(player.stopped).isAtLeast(1)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)
            // A second tap within 400 ms of starting doesn't send an empty take.
            advanceTimeBy(100)
            classToTest.mic()
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)
            advanceTimeBy(VoiceSession.DOUBLE_TAP_MS)
            classToTest.mic()
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Thinking)
        }

    @Test
    fun `a dictation ignores pauses, is sent with the check, and doesn't reopen the mic`() =
        runTest {
            val rec = recorder()
            val classToTest = session(rec)
            classToTest.record()
            assertThat(classToTest.state.value.dictating).isTrue()
            assertThat(classToTest.state.value.handsFree).isFalse()
            // A minute of silence: still recording.
            advanceTimeBy(60_000)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)
            assertThat(classToTest.state.value.seconds).isAtLeast(59)
            classToTest.record()
            settle { asked.isNotEmpty() }
            classToTest.replied("Done.", "EN")
            settle { classToTest.state.value.phase == VoicePhase.Idle }
            advanceTimeBy(2_000)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
            assertThat(rec.takes).isEqualTo(1)
        }

    @Test
    fun `an empty transcript says so and listens again, a refused one ends hands-free`() =
        runTest {
            val rec = recorder()
            rec.level = { t -> if (t in 100..1200) 0.6f else 0.05f }
            transcript = HttpStatusCode.OK to """{"text":"(No speech detected)"}"""
            val classToTest = session(rec)
            classToTest.mic()
            advanceTimeBy(3_400)
            settle { classToTest.state.value.banner != null }
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelNoSpeechShort)
            assertThat(asked).isEmpty()
            advanceTimeBy(VoiceSession.NO_SPEECH_RELISTEN_MS + 10)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)

            transcript = HttpStatusCode.Forbidden to """{"status":403,"code":"FEATURE_NOT_AVAILABLE"}"""
            rec.started = testScheduler.currentTime
            advanceTimeBy(3_400)
            settle { classToTest.state.value.banner?.text == Res.string.intelSpeechProOrDenied }
            assertThat(classToTest.state.value.handsFree).isFalse()
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
        }

    @Test
    fun `record while Shifa reviews stops that reply, a mic that won't start says so, leaving drops the take`() =
        runTest {
            val rec = recorder()
            rec.level = { t -> if (t in 100..1200) 0.6f else 0.05f }
            val classToTest = session(rec)
            classToTest.mic()
            advanceTimeBy(3_400)
            settle { asked.isNotEmpty() }
            classToTest.record()
            assertThat(cancelled).isEqualTo(1)
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelStopped)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)

            rec.fails = true
            classToTest.mic()
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelCouldNotStartMicMobile)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)

            rec.fails = false
            classToTest.mic()
            val stops = rec.stops
            classToTest.leave()
            assertThat(rec.stops).isEqualTo(stops + 1)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
            assertThat(classToTest.state.value.handsFree).isFalse()
        }

    @Test
    fun `the end of a take follows Flutter's rules, the silence gate follows the recent peak`() {
        // Nothing heard: keep listening, give up at 20 s.
        val quiet = Vad(0)
        assertThat(quiet.onLevel(0.05f, 19_000)).isEqualTo(Vad.Outcome.Keep)
        assertThat(quiet.onLevel(0.05f, Vad.NO_SPEECH_MS)).isEqualTo(Vad.Outcome.NoSpeech)
        // 300 ms of speech, then quiet: sent 1.9 s into the quiet (the 700 ms count from the first word on, pause
        // included, as Flutter's did).
        val vad = Vad(0)
        var t = 0L
        while (t < 300) vad.onLevel(0.5f, t).also { t += 60 }
        var outcome = Vad.Outcome.Keep
        val quietFrom = t
        while (outcome == Vad.Outcome.Keep) outcome = vad.onLevel(0.05f, t).also { t += 60 }
        assertThat(outcome).isEqualTo(Vad.Outcome.Send)
        assertThat(t - 60 - quietFrom).isAtLeast(Vad.SILENCE_TO_SEND_MS)
        // A steady 0.2 hum after loud speech stops counting as silence once the peak decays: no send, the take runs on.
        val hum = Vad(0)
        t = 0
        while (t < 1_000) hum.onLevel(0.9f, t).also { t += 60 }
        while (t < 10_000) {
            assertThat(hum.onLevel(0.11f + 0.09f, t)).isEqualTo(Vad.Outcome.Keep)
            t += 60
        }
        // 45 s sends whatever was captured.
        assertThat(hum.onLevel(0.2f, Vad.MAX_LISTEN_MS)).isEqualTo(Vad.Outcome.Send)
    }

    @Test
    fun `a sign-off ends hands-free without asking Shifa`() =
        runTest {
            val rec = recorder()
            rec.level = { t -> if (t in 100..1200) 0.6f else 0.05f }
            transcript = HttpStatusCode.OK to """{"text":"Thank you, that's it."}"""
            val classToTest = session(rec)
            classToTest.mic()
            advanceTimeBy(3_400)
            settle { classToTest.state.value.banner != null }
            assertThat(asked).isEmpty()
            assertThat(classToTest.state.value.handsFree).isFalse()
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelSignOffThanks)
            advanceTimeBy(2_000)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
        }

    @Test
    fun `sign-off phrases as Flutter matched them`() {
        listOf("That's it.", "ok, thanks", "Rahmat", "хватит", "bye shifa", "yeah that's it thanks", "Thanks, bye")
            .forEach { assertThat(VoiceSession.isSignOff(it)).isTrue() }
        listOf("Show me the patient, thanks", "book Jasur thanks", "what is my day", "", "thanks ".repeat(20))
            .forEach { assertThat(VoiceSession.isSignOff(it)).isFalse() }
    }

    @Test
    fun `the mic during a typed reply stops it, record says Stopped`() =
        runTest {
            val classToTest = session(recorder())
            typedReply = true
            classToTest.mic()
            assertThat(cancelled).isEqualTo(1)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Listening)
            classToTest.leave()
            classToTest.record()
            assertThat(cancelled).isEqualTo(2)
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelStopped)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
        }

    @Test
    fun `a take whose upload failed is kept for Retry, a clip that can't play says so`() =
        runTest {
            val rec = recorder()
            transcript = HttpStatusCode.ServiceUnavailable to """{"status":503}"""
            val classToTest = session(rec)
            classToTest.record()
            advanceTimeBy(5_000)
            classToTest.record()
            settle { classToTest.state.value.canResend }
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
            transcript = HttpStatusCode.OK to """{"text":"what is my day","language":"en"}"""
            classToTest.resend()
            settle { asked.isNotEmpty() }
            assertThat(rec.takes).isEqualTo(1) // nothing recorded anew
            assertThat(transcribed).isEqualTo(2)

            player.plays = false
            classToTest.replied("Answer.", "EN")
            settle { classToTest.state.value.banner?.text == Res.string.intelVoiceSkipped }
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
        }

    @Test
    fun `leaving while a take is being transcribed asks nothing`() =
        runTest {
            val rec = recorder()
            // Dispatched at once, as viewModelScope's Main.immediate is: the order the bug needed.
            val classToTest =
                session(rec, CoroutineScope(backgroundScope.coroutineContext + UnconfinedTestDispatcher(testScheduler)))
            val gate = CompletableDeferred<Unit>()
            transcribeGate = gate
            classToTest.record()
            advanceTimeBy(2_000)
            classToTest.record()
            settle { transcribed == 1 }
            classToTest.leave()
            gate.complete(Unit)
            advanceTimeBy(5_000)
            repeat(20) {
                runCurrent()
                Thread.sleep(10)
            }
            assertThat(asked).isEmpty()
        }

    @Test
    fun `going to the background drops a take and says so`() =
        runTest {
            val rec = recorder()
            val classToTest = session(rec)
            classToTest.mic()
            classToTest.paused()
            assertThat(rec.stops).isEqualTo(1)
            assertThat(classToTest.state.value.phase).isEqualTo(VoicePhase.Idle)
            assertThat(classToTest.state.value.handsFree).isFalse()
            assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.intelRecordingStoppedBackground)
            advanceTimeBy(30_000)
            assertThat(transcribed).isEqualTo(0)
        }
}
