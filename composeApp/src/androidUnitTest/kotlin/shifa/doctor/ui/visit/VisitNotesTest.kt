@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.After
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.consultation.ConsultationRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.notesSaveFailed

class VisitNotesTest {
    private val calls = mutableListOf<String>()
    private var notes = """[{"id":1,"body":"Earlier notes","source":"MANUAL"},{"id":2,"body":"AI SOAP","source":"AI_DRAFT"}]"""
    private var drafts = """[{"id":"d1","body":"Draft SOAP"}]"""
    private var saveStatus = HttpStatusCode.OK
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            val body = req.body.toByteArray().decodeToString()
            calls += "${req.method.value} $path $body".trim()
            when {
                path.endsWith("consultation-notes") && req.method.value == "PUT" ->
                    respond("""{"status":${saveStatus.value}}""", saveStatus, json)
                path.endsWith("consultation-notes") -> respond(notes, HttpStatusCode.OK, json)
                path.endsWith("draft-notes") -> respond(drafts, HttpStatusCode.OK, json)
                else -> respond("""{"success":true}""", HttpStatusCode.OK, json)
            }
        }
    private val scheduler = TestCoroutineScheduler()
    private val scope = CoroutineScope(SupervisorJob() + UnconfinedTestDispatcher(scheduler))

    @After fun tearDown() = scope.cancel()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(): VisitNotes =
        VisitNotes(7, { 14 }, scope, ConsultationRepository({ createHttpClient({ null }, engine) })).also {
            it.labels = SoapLabels("S:", "O:", "A:", "P:")
            it.load()
            settle { it.state.value.notes != null }
        }

    private fun puts() = calls.filter { it.startsWith("PUT") }

    @Test
    fun `the doctor's earlier notes fill the empty free notes once, never over typed text`() {
        val classToTest = opened()
        assertThat(classToTest.state.value.text.free).isEqualTo("Earlier notes")
        assertThat(classToTest.state.value.dirty).isFalse()
        assertThat(classToTest.state.value.drafts?.single()?.id).isEqualTo("d1")
        classToTest.edit { it.copy(free = "") }
        notes = """[{"id":1,"body":"Changed elsewhere","source":"MANUAL"}]"""
        classToTest.load()
        settle { classToTest.state.value.notes?.size == 1 }
        assertThat(classToTest.state.value.text.free).isEmpty()
    }

    @Test
    fun `edits are saved once, 2 s after the last, as one body with the SOAP labels`() {
        notes = "[]"
        val classToTest = opened()
        classToTest.edit { it.copy(subjective = "Pain") }
        scheduler.advanceTimeBy(1_500)
        classToTest.edit { it.copy(plan = "Review", free = "Calm") }
        scheduler.advanceTimeBy(1_900)
        assertThat(puts()).isEmpty()
        scheduler.advanceTimeBy(200)
        settle { !classToTest.state.value.dirty }
        assertThat(puts()).containsExactly(
            """PUT appointments/7/consultation-notes {"body":"S:\nPain\n\nP:\nReview\n\nCalm"}""",
        )
    }

    @Test
    fun `leaving saves at once, a failure keeps the text unsaved, and blank text isn't sent`() {
        notes = "[]"
        val classToTest = opened()
        classToTest.edit { it.copy(free = "  ") }
        assertThat(runBlocking { classToTest.flush() }).isTrue()
        assertThat(puts()).isEmpty()
        saveStatus = HttpStatusCode.InternalServerError
        classToTest.append("From Shifa AI")
        assertThat(runBlocking { classToTest.flush() }).isFalse()
        assertThat(classToTest.state.value.saveError?.text).isEqualTo(Res.string.notesSaveFailed)
        saveStatus = HttpStatusCode.OK
        classToTest.append("Second")
        assertThat(classToTest.state.value.text.free).isEqualTo("From Shifa AI\n\nSecond")
        assertThat(runBlocking { classToTest.flush() }).isTrue()
        assertThat(classToTest.state.value.saveError).isNull()
        // The pending 2 s timer was cancelled by the flush: nothing more is sent.
        scheduler.advanceTimeBy(5_000)
        assertThat(puts()).hasSize(2)
    }

    @Test
    fun `a draft saved as a note reads the notes and drafts again`() {
        val classToTest = opened()
        drafts = "[]"
        classToTest.confirm(classToTest.state.value.drafts!!.single())
        settle { classToTest.state.value.drafts?.isEmpty() == true }
        assertThat(calls).contains("""POST ai/draft/d1/confirm {"patientId":14,"appointmentId":7}""")
        assertThat(classToTest.state.value.busy).isEmpty()
    }

    @Test
    fun `an edit during a save doesn't cancel it and is saved after it, in order`() {
        notes = "[]"
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        val slow =
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/")
                val body = req.body.toByteArray().decodeToString()
                calls += "${req.method.value} $path $body".trim()
                when {
                    path.endsWith("consultation-notes") && req.method.value == "PUT" -> {
                        if (calls.count { it.startsWith("PUT") } == 1) gate.await()
                        respond("", HttpStatusCode.OK, json)
                    }
                    path.endsWith("consultation-notes") -> respond(notes, HttpStatusCode.OK, json)
                    else -> respond(drafts, HttpStatusCode.OK, json)
                }
            }
        val classToTest = VisitNotes(7, { 14 }, scope, ConsultationRepository({ createHttpClient({ null }, slow) }))
        classToTest.edit { it.copy(free = "First") }
        scheduler.advanceTimeBy(2_100)
        settle { classToTest.state.value.saving }
        classToTest.edit { it.copy(free = "") }
        classToTest.edit { it.copy(free = "Second") }
        scheduler.advanceTimeBy(2_100)
        gate.complete(Unit)
        settle { puts().size == 2 && !classToTest.state.value.saving && !classToTest.state.value.dirty }
        assertThat(puts().map { it.substringAfter("{") }).containsExactly(
            """"body":"First"}""",
            """"body":"Second"}""",
        ).inOrder()
        assertThat(classToTest.state.value.saving).isFalse()
    }
}
