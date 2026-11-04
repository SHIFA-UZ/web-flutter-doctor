@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.visit

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.consultation.ConsultationRepository
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.model.SignatureState
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiDailyLimitReached
import shifa.doctor.generated.resources.aiNotesUploaded
import shifa.doctor.generated.resources.noForm0252Yet
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.stopAll

class VisitViewModelTest {
    private val calls = mutableListOf<String>()
    private var visit = """{"id":7,"patientId":14,"patientName":"Jasur Aliyev","startAt":"2026-11-04T05:00:00Z","endAt":"2026-11-04T05:30:00Z","location":"Shifa Dev Clinic","status":"CONFIRMED"}"""
    private var visitStatus = HttpStatusCode.OK
    private var notesStatus = HttpStatusCode.OK
    private var uploadStatus = HttpStatusCode.Accepted
    private var drafts = "[]"
    private var forms = """[{"id":3,"patientId":14,"templateId":"025-2","date":"2026-10-20","complaints":"Pain","diagnosis":"Caries"}]"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            val method = req.method.value
            calls += "$method $path ${req.body.toByteArray().decodeToString()}".trim()

            fun ok(text: String) = respond(text, HttpStatusCode.OK, json)
            when {
                path == "appointments/7" && visitStatus != HttpStatusCode.OK ->
                    respond("""{"status":404,"code":"NOT_FOUND"}""", visitStatus, json)
                path == "appointments/7" -> ok(visit)
                path == "appointments/7/request-signature" -> {
                    visit = visit.dropLast(1) + ""","signatureRequested":true}"""
                    ok("")
                }
                path == "appointments/7/consultation-notes" && method == "PUT" ->
                    respond("""{"status":${notesStatus.value}}""", notesStatus, json)
                path == "appointments/7/consultation-notes" -> ok("[]")
                path == "appointments/7/draft-notes" -> ok(drafts)
                path == "consultations/upload-recording" ->
                    if (uploadStatus == HttpStatusCode.Accepted) {
                        respond("""{"message":"ok"}""", HttpStatusCode.Accepted, json)
                    } else {
                        respond("""{"status":${uploadStatus.value}}""", uploadStatus, json)
                    }
                path == "patients/14" ->
                    ok(
                        """{"id":14,"name":"Jasur Aliyev","birthDate":"1985-01-11","chronicDisease":"Asthma","allergies":"Penicillin"}""",
                    )
                path == "patients/14/appointments" ->
                    ok(
                        """[{"id":7,"startAt":"2026-11-04T05:00:00Z","reason":"Toothache"},
                           {"id":5,"startAt":"2026-10-20T06:00:00Z","status":"COMPLETED"},
                           {"id":6,"startAt":"2026-10-25T06:00:00Z","status":"CANCELLED"},
                           {"id":9,"startAt":"2026-11-10T06:00:00Z","status":"COMPLETED"}]""",
                    )
                path == "patients/14/forms" -> ok(forms)
                else -> ok("[]")
            }
        }
    private val created = mutableListOf<VisitViewModel>()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    private fun opened(
        profession: String? = "General practitioner",
        warned: MutableSet<Long> = mutableSetOf(),
    ): VisitViewModel {
        val client = { createHttpClient({ null }, engine) }
        return VisitViewModel(
            7,
            AppointmentsRepository(client),
            PatientsRepository(client = client),
            FormsRepository(client),
            ConsultationRepository(client),
            language = { shifa.doctor.data.storage.AppLanguage.English },
            profession = { profession },
            warned = warned,
        ).also {
            created += it
            it.load()
            settle { it.state.value.patient != null || it.state.value.error != null }
        }
    }

    @Test
    fun `the visit opens with its patient, reason, last completed visit and forms, and warns once`() {
        val warned = mutableSetOf<Long>()
        val classToTest = opened(warned = warned)
        val s = classToTest.state.value
        assertThat(s.visit?.patientName).isEqualTo("Jasur Aliyev")
        assertThat(s.reason).isEqualTo("Toothache")
        // The last completed visit before this one: not the cancelled one, not a later one.
        assertThat(s.lastVisit).isEqualTo(Instant.parse("2026-10-20T06:00:00Z"))
        assertThat(s.forms.single().id).isEqualTo(3L)
        assertThat(s.mode).isEqualTo(VisitMode.General)
        assertThat(s.dental).isFalse()
        assertThat(s.chronicWarning).isTrue()
        assertThat(opened(warned = warned).state.value.chronicWarning).isFalse()
    }

    @Test
    fun `a dentist starts in the dental record, and a missing visit says it's gone`() {
        assertThat(opened(profession = "Dentist").state.value.mode).isEqualTo(VisitMode.Dental)
        visitStatus = HttpStatusCode.NotFound
        val gone = opened()
        assertThat(gone.state.value.notFound).isTrue()
        assertThat(gone.state.value.visit).isNull()
    }

    @Test
    fun `asking for the signature saves the notes first, then waits for it`() {
        val classToTest = opened()
        classToTest.notes.edit { it.copy(free = "Filling of 36") }
        classToTest.requestSignature()
        settle { classToTest.state.value.signature == SignatureState.Waiting && !classToTest.state.value.signing }
        val writes = calls.filter { !it.startsWith("GET") }
        assertThat(writes).containsExactly(
            """PUT appointments/7/consultation-notes {"body":"Filling of 36"}""",
            "PUT appointments/7/request-signature {}",
        ).inOrder()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.patientSignatureRequestSent)
    }

    @Test
    fun `helpers say when there is nothing, add their text to the notes, and modes save the notes`() {
        forms = "[]"
        val classToTest = opened()
        classToTest.helper(VisitHelper.Form)
        assertThat(classToTest.state.value.helper).isNull()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.noForm0252Yet)
        classToTest.mode(VisitMode.Form0252)
        classToTest.addToNotes("Complaints: Pain")
        assertThat(classToTest.state.value.mode).isEqualTo(VisitMode.General)
        assertThat(classToTest.notes.state.value.text.free).isEqualTo("Complaints: Pain")
        classToTest.mode(VisitMode.Form0252)
        settle { calls.any { it.startsWith("PUT appointments/7/consultation-notes") } }
        assertThat(calls.last { it.startsWith("PUT") }).contains("Complaints: Pain")
    }

    @Test
    fun `leaving saves the notes, and asks when they couldn't be saved`() {
        notesStatus = HttpStatusCode.InternalServerError
        val classToTest = opened()
        classToTest.notes.edit { it.copy(free = "Unsaved") }
        classToTest.leave(VisitExit.Patient)
        settle { classToTest.state.value.askLeave != null }
        assertThat(classToTest.state.value.askLeave).isEqualTo(VisitExit.Patient)
        assertThat(classToTest.state.value.left).isNull()
        // Nor is the patient asked to sign notes that aren't saved.
        classToTest.requestSignature()
        settle { !classToTest.state.value.signing }
        assertThat(calls.none { it.contains("request-signature") }).isTrue()
        classToTest.leave(VisitExit.Patient, force = true)
        assertThat(classToTest.state.value.left).isEqualTo(VisitExit.Patient)
        notesStatus = HttpStatusCode.OK
        val saved = opened()
        saved.notes.edit { it.copy(free = "Saved") }
        saved.leave()
        settle { saved.state.value.left != null }
        assertThat(saved.state.value.left).isEqualTo(VisitExit.Back)
        assertThat(saved.state.value.askLeave).isNull()
    }

    @Test
    fun `a scribe recording is uploaded and awaited, and one over the limit is kept to send again`() {
        val classToTest = opened()
        uploadStatus = HttpStatusCode.TooManyRequests
        val limited = runBlocking { classToTest.uploadScribe(byteArrayOf(1, 2, 3), "rec-1") }
        assertThat(limited.text).isEqualTo(Res.string.aiDailyLimitReached)
        assertThat(classToTest.state.value.scribeRetry).isTrue()
        assertThat(classToTest.state.value.awaitingScribe).isFalse()
        uploadStatus = HttpStatusCode.Accepted
        classToTest.retryScribe()
        settle { classToTest.state.value.awaitingScribe }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.aiNotesUploaded)
        assertThat(classToTest.state.value.scribeRetry).isFalse()
        assertThat(calls.count { it.startsWith("POST consultations/upload-recording") }).isEqualTo(2)
    }

    @Test
    fun `From Shifa AI reads the drafts again, so the scribe's note shows once it is there`() {
        val classToTest = opened()
        drafts = """[{"id":"d1","body":"SOAP"}]"""
        classToTest.helper(VisitHelper.Ai)
        settle { classToTest.state.value.helper == VisitHelper.Ai }
        assertThat(classToTest.notes.state.value.drafts?.single()?.id).isEqualTo("d1")
    }
}
