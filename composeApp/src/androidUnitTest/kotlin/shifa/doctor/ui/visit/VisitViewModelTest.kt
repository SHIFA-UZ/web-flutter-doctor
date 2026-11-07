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
import shifa.doctor.generated.resources.aiDocumentationWillAppearShortly
import shifa.doctor.generated.resources.aiNotesUploaded
import shifa.doctor.generated.resources.appointmentPlanPaymentFailed
import shifa.doctor.generated.resources.dentalLoadFailed
import shifa.doctor.generated.resources.endFailed
import shifa.doctor.generated.resources.noForm0252Yet
import shifa.doctor.generated.resources.noItemsToSave
import shifa.doctor.generated.resources.noShowMarked
import shifa.doctor.generated.resources.noShowNotAllowed
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.generated.resources.photoLimit
import shifa.doctor.generated.resources.summaryNotSaved
import shifa.doctor.stopAll

class VisitViewModelTest {
    // Written from the mock engine's threads while the test reads it.
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var visit = """{"id":7,"patientId":14,"patientName":"Jasur Aliyev","startAt":"2026-11-04T05:00:00Z","endAt":"2026-11-04T05:30:00Z","location":"Shifa Dev Clinic","status":"CONFIRMED"}"""
    private var visitStatus = HttpStatusCode.OK
    private var notesStatus = HttpStatusCode.OK
    private var uploadStatus = HttpStatusCode.Accepted
    private var drafts = "[]"
    private var completeStatus = HttpStatusCode.OK
    private var completeBody = """{"status":"COMPLETED"}"""
    private var documentStatus = HttpStatusCode.OK
    private var scribePending = false
    private var notesList = "[]"
    private val completedDays = java.util.concurrent.CopyOnWriteArrayList<kotlinx.datetime.LocalDate>()
    private val summaries = java.util.concurrent.CopyOnWriteArrayList<shifa.doctor.data.consultation.VisitSummary>()
    private var notesReadFails = false
    private var dentalReadFails = false
    private var planOn = false
    private var payStatus = HttpStatusCode.OK

    // BE-10: the no-show answer and what was sent.
    private var noShowStatus = HttpStatusCode.OK
    private val noShowBodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val planSummary =
        """{"id":5,"clinicId":2,"patientId":14,"title":"Upper jaw","status":"ACTIVE","planKind":"COMPREHENSIVE","totalMinor":30000000,"paidMinor":0,"owedMinor":30000000,"currency":"UZS","linesCompletedCount":0,"linesTotalCount":1}"""
    private val planDetail =
        """{"summary":$planSummary,"lines":[{"id":1,"title":"Filling","status":"PLANNED","unitPriceMinor":30000000,"lineTotalMinor":30000000,"currency":"UZS","specialtyMetadata":"{\"fdi\":\"16\"}"}],"installmentPlans":[]}"""
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
                path == "appointments/7/complete" -> respond(completeBody, completeStatus, json)
                path == "appointments/7/no-show" -> {
                    noShowBodies += req.body.toByteArray().decodeToString()
                    if (noShowStatus == HttpStatusCode.OK) {
                        respond("{}", noShowStatus, json)
                    } else {
                        respond(
                            """{"status":409,"code":"NO_SHOW_NOT_ALLOWED","requestId":"r-ns"}""",
                            noShowStatus,
                            json,
                        )
                    }
                }
                path == "appointments/7/scribe-status" ->
                    ok("""{"status":"processing","hasScribeNote":${!scribePending}}""")
                path == "patients/14/documents" && method == "POST" ->
                    if (documentStatus == HttpStatusCode.OK) {
                        ok("""{"id":300,"title":"Appointment documentation","canView":true}""")
                    } else {
                        respond("""{"status":500}""", documentStatus, json)
                    }
                path == "appointments/7/request-signature" -> {
                    visit = visit.dropLast(1) + ""","signatureRequested":true}"""
                    ok("")
                }
                path == "appointments/7/consultation-notes" && method == "PUT" ->
                    respond("""{"status":${notesStatus.value}}""", notesStatus, json)
                path == "appointments/7/consultation-notes" && notesReadFails ->
                    respond("""{"status":500}""", HttpStatusCode.InternalServerError, json)
                path == "appointments/7/consultation-notes" -> ok(notesList)
                path == "appointments/7/draft-notes" -> ok(drafts)
                path == "me/clinics" && planOn -> ok("""[{"clinicId":2,"name":"Clinic"}]""")
                path == "treatment-plans" && planOn -> ok("[$planSummary]")
                path == "treatment-plans/5/detail" -> ok(planDetail)
                path == "treatment-plans/5/fulfillment-candidates" ->
                    ok(
                        """[{"lineId":1,"title":"Filling","fdi":"16"}]""",
                    )
                path == "treatment-plans/5/appointments/7/fulfill" -> ok(planDetail)
                path == "clinics/2/finance/payments" && method == "GET" -> ok("[]")
                path == "clinics/2/finance/payments" -> respond("{}", payStatus, json)
                path == "appointments/7/dental-documentation" && method == "GET" && dentalReadFails ->
                    respond("""{"status":500}""", HttpStatusCode.InternalServerError, json)
                path == "appointments/7/dental-documentation" && method == "GET" -> ok("{}")
                path == "doctors/me/services" ->
                    ok(
                        """[{"id":2,"title":"Filling","isActive":true,"prices":[{"amountMinor":60000000,"currency":"UZS"}]}]""",
                    )
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
            documents = shifa.doctor.data.documents.DocumentsRepository(client),
            summaryPdf = { summary, _ ->
                summaries += summary
                "%PDF summary".encodeToByteArray()
            },
            doctor = { "Dr. Karimov" to "Shifa Dev Clinic" },
            completedOn = { _, day -> completedDays += day },
            dentalRepo = shifa.doctor.data.dental.DentalRepository(client),
            services = shifa.doctor.data.services.ServicesRepository(client),
            plans = shifa.doctor.data.treatmentplans.TreatmentPlansRepository(client),
            finance = shifa.doctor.data.finance.FinanceRepository(client),
            clinics =
                shifa.doctor.data.clinics.ClinicsRepository(
                    client,
                    com.russhwolf.settings.MapSettings(),
                    { "u1" },
                ),
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

    private fun ended(classToTest: VisitViewModel) =
        settle {
            !classToTest.state.value.end.running && classToTest.state.value.end.at == null
        }

    @Test
    fun `ending saves the notes, completes the visit, and files the summary under the patient`() {
        val classToTest = opened()
        classToTest.notes.edit { it.copy(free = "Filling of 36") }
        notesList = """[{"id":1,"body":"Filling of 36","source":"MANUAL"}]"""
        classToTest.openEnd()
        classToTest.end()
        ended(classToTest)
        val end = classToTest.state.value.end
        assertThat(end.done).isTrue()
        assertThat(end.summary?.id).isEqualTo(300L)
        assertThat(end.steps).containsExactly(EndStep.Notes, EndStep.Complete, EndStep.Summary).inOrder()
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore(" {").substringBefore(" --") }
        assertThat(writes).containsExactly(
            "PUT appointments/7/consultation-notes",
            "PUT appointments/7/complete",
            "POST patients/14/documents",
        ).inOrder()
        val upload = calls.single { it.startsWith("POST patients/14/documents") }
        assertThat(upload).contains("APPOINTMENT_NOTE")
        assertThat(upload).contains("name=appointmentId")
        assertThat(classToTest.state.value.visit?.completed).isTrue()
        // The calendar's day of the visit is read again.
        settle { completedDays.isNotEmpty() }
        assertThat(completedDays.single()).isEqualTo(kotlinx.datetime.LocalDate(2026, 11, 4))
    }

    @Test
    fun `a dentist's End saves the dental record first, and its services alone make a summary`() {
        val classToTest = opened(profession = "Dentist")
        settle { classToTest.dentalRecord.state.value.let { it.loaded && it.services != null } }
        classToTest.dentalRecord.add("36", classToTest.dentalRecord.state.value.services!!.single())
        // Shifa AI's text goes into the dental notes in Dental mode, the mode stays.
        classToTest.addToNotes("Pulpitis of 36")
        assertThat(classToTest.state.value.mode).isEqualTo(VisitMode.Dental)
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore(" {").substringBefore(" --") }
        assertThat(writes).containsExactly(
            "PUT appointments/7/dental-documentation",
            "PUT appointments/7/complete",
            "POST patients/14/documents",
        ).inOrder()
        val dental = summaries.single().dental!!
        assertThat(dental.lines("36").single().title).isEqualTo("Filling")
        assertThat(dental.notes).isEqualTo("Pulpitis of 36")
    }

    @Test
    fun `a dental record that can't be read stops End before the visit is completed`() {
        dentalReadFails = true
        val classToTest = opened(profession = "Dentist")
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.error?.text).isEqualTo(Res.string.dentalLoadFailed)
        assertThat(classToTest.state.value.end.completed).isFalse()
        assertThat(calls.none { it.startsWith("PUT appointments/7/complete") }).isTrue()
        // Read again at the next End, then it goes through.
        dentalReadFails = false
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.completed).isTrue()
    }

    @Test
    fun `photos alone make a summary, one page each, and leaving with photos asks first`() {
        val classToTest = opened()

        fun take(
            kind: PhotoKind,
            name: String?,
            byte: Byte = 0,
        ) {
            classToTest.photo(kind)
            classToTest.pickingPhoto()
            classToTest.addPhoto(name?.let { shifa.doctor.utils.PickedImage(it, byteArrayOf(byte)) })
        }
        take(PhotoKind.Before, "IMG_1.jpg", 1)
        take(PhotoKind.After, null)
        // A picker on its way keeps its kind: a second sheet can't open meanwhile.
        classToTest.photo(PhotoKind.After)
        classToTest.pickingPhoto()
        classToTest.photo(PhotoKind.Before)
        assertThat(classToTest.state.value.photoSheet).isNull()
        classToTest.addPhoto(shifa.doctor.utils.PickedImage("IMG_2.jpg", byteArrayOf(2)))
        take(PhotoKind.After, "IMG_3.jpg", 3)
        assertThat(
            classToTest.state.value.photos.map {
                it.kind
            },
        ).containsExactly(PhotoKind.Before, PhotoKind.After, PhotoKind.After).inOrder()
        classToTest.removePhoto(1)
        assertThat(classToTest.state.value.photos.map { it.name }).containsExactly("IMG_1.jpg", "IMG_3.jpg").inOrder()
        // The photos live only in the open visit: leaving asks.
        classToTest.leave()
        settle { classToTest.state.value.askLeave != null }
        assertThat(classToTest.state.value.askLeave).isEqualTo(VisitExit.Back)
        assertThat(classToTest.state.value.left).isNull()
        classToTest.ui { it.copy(askLeave = null) }
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        assertThat(classToTest.state.value.end.noSummary).isNull()
        assertThat(
            summaries.single().photos.map { it.first },
        ).containsExactly("Before Treatment", "After Treatment").inOrder()
        assertThat(
            summaries.single().photos.map { it.second.single() },
        ).containsExactly(1.toByte(), 3.toByte()).inOrder()
        // Completed: the photos are in the summary, leaving no longer asks and lets them go.
        classToTest.leave()
        settle { classToTest.state.value.left != null }
        assertThat(classToTest.state.value.left).isEqualTo(VisitExit.Back)
        assertThat(classToTest.state.value.photos).isEmpty()
    }

    @Test
    fun `at most 20 photos, and a failed summary still asks before the photos are lost`() {
        val classToTest = opened()
        repeat(MAX_PHOTOS + 1) {
            classToTest.photo(PhotoKind.Before)
            classToTest.pickingPhoto()
            classToTest.addPhoto(shifa.doctor.utils.PickedImage("IMG_$it.jpg", byteArrayOf(1)))
        }
        assertThat(classToTest.state.value.photos).hasSize(MAX_PHOTOS)
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.photoLimit)
        documentStatus = HttpStatusCode.InternalServerError
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.summaryFailed).isTrue()
        classToTest.leave()
        settle { classToTest.state.value.askLeave != null }
        assertThat(classToTest.state.value.askLeave).isEqualTo(VisitExit.Back)
        // Opening the patient comes back to this visit: it doesn't ask.
        classToTest.ui { it.copy(askLeave = null) }
        classToTest.leave(VisitExit.Patient)
        settle { classToTest.state.value.left != null }
        assertThat(classToTest.state.value.left).isEqualTo(VisitExit.Patient)
        assertThat(classToTest.state.value.photos).hasSize(MAX_PHOTOS)
    }

    @Test
    fun `End applies the ticked plan lines, then the payment, before completing, and the summary shows both`() {
        planOn = true
        val classToTest = opened(profession = "Dentist")
        settle { classToTest.plan.state.value.candidates != null }
        classToTest.plan.toggle(1)
        classToTest.plan.amount("100 000")
        // The ticks and the payment live only here: going back asks.
        classToTest.leave()
        settle { classToTest.state.value.askLeave != null }
        classToTest.ui { it.copy(askLeave = null) }
        // A failed payment stops End before completion, the ticks already applied stay applied.
        payStatus = HttpStatusCode.InternalServerError
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.error?.text).isEqualTo(Res.string.appointmentPlanPaymentFailed)
        assertThat(calls.none { it.startsWith("PUT appointments/7/complete") }).isTrue()
        payStatus = HttpStatusCode.OK
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore(" {").substringBefore(" --") }
        assertThat(writes).containsExactly(
            "POST treatment-plans/5/appointments/7/fulfill",
            "POST clinics/2/finance/payments",
            "POST clinics/2/finance/payments",
            "PUT appointments/7/complete",
            "POST patients/14/documents",
        ).inOrder()
        val plan = summaries.single().plan!!
        assertThat(plan.lines.single().tooth).isEqualTo("16")
        assertThat(plan.payment?.first).isEqualTo(10_000_000L)
    }

    @Test
    fun `nothing written ends the visit without a summary`() {
        val classToTest = opened()
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        assertThat(classToTest.state.value.end.noSummary).isEqualTo(Res.string.noItemsToSave)
        assertThat(calls.none { it.startsWith("POST patients/14/documents") }).isTrue()
    }

    @Test
    fun `a failed complete leaves the visit open, a failed summary is retried alone`() {
        completeStatus = HttpStatusCode.InternalServerError
        completeBody = """{"status":500}"""
        val classToTest = opened()
        classToTest.notes.edit { it.copy(free = "Pain") }
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.completed).isFalse()
        assertThat(classToTest.state.value.end.error?.text).isEqualTo(Res.string.endFailed)
        completeStatus = HttpStatusCode.OK
        completeBody = """{"status":"COMPLETED"}"""
        documentStatus = HttpStatusCode.InternalServerError
        notesList = """[{"id":1,"body":"Pain","source":"MANUAL"}]"""
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.completed).isTrue()
        assertThat(classToTest.state.value.end.summaryFailed).isTrue()
        assertThat(classToTest.state.value.end.error?.text).isEqualTo(Res.string.summaryNotSaved)
        documentStatus = HttpStatusCode.OK
        val completes = calls.count { it.startsWith("PUT appointments/7/complete") }
        classToTest.retrySummary()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        assertThat(calls.count { it.startsWith("PUT appointments/7/complete") }).isEqualTo(completes)
    }

    @Test
    fun `with a recording at the scribe, End waits for its notes and can go on without them`() {
        scribePending = true
        completeBody = """{"status":"COMPLETED","scribePending":true}"""
        val classToTest = opened()
        runBlocking { classToTest.uploadScribe(byteArrayOf(1), "rec-1") }
        classToTest.end()
        settle { classToTest.state.value.end.at == EndStep.Scribe }
        assertThat(classToTest.state.value.end.steps).contains(EndStep.Scribe)
        assertThat(calls.any { it.contains("awaitingScribe") }).isTrue()
        classToTest.skipScribe()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        assertThat(classToTest.state.value.end.noSummary).isEqualTo(Res.string.aiDocumentationWillAppearShortly)
    }

    @Test
    fun `an unreadable re-read fails the summary instead of filing it blank, and closing keeps Retry summary`() {
        val classToTest = opened()
        classToTest.notes.edit { it.copy(free = "Typed only") }
        notesReadFails = true
        classToTest.end()
        ended(classToTest)
        assertThat(classToTest.state.value.end.summaryFailed).isTrue()
        assertThat(calls.none { it.startsWith("POST patients/14/documents") }).isTrue()
        classToTest.closeEnd()
        assertThat(classToTest.state.value.end.hidden).isTrue()
        assertThat(classToTest.state.value.end.summaryFailed).isTrue()
        // Read again, the server has no manual note yet: the typed text makes the summary.
        notesReadFails = false
        classToTest.retrySummary()
        ended(classToTest)
        assertThat(classToTest.state.value.end.done).isTrue()
        assertThat(classToTest.state.value.end.hidden).isFalse()
    }

    @Test
    fun `an applied AI code switches to the 025-2 and is kept for it`() {
        val classToTest = opened()
        classToTest.applyIcd(shifa.doctor.data.model.IcdSuggestion("K04.0", "Pulpitis", 0.8, isTop = true))
        assertThat(classToTest.state.value.mode).isEqualTo(VisitMode.Form0252)
        assertThat(classToTest.state.value.icd?.code).isEqualTo("K04.0")
        assertThat(classToTest.state.value.banner?.args).containsExactly("K04.0")
    }

    @Test
    fun `a no-show from the call tells the patient as chosen, goes back with a notice and reads the day again`() {
        val classToTest = opened()
        classToTest.askNoShow(true)
        classToTest.notifyNoShow(false)
        classToTest.noShow()
        settle { classToTest.state.value.left != null }
        assertThat(noShowBodies.single()).isEqualTo("""{"notify":false}""")
        assertThat(classToTest.state.value.left).isEqualTo(VisitExit.Back)
        assertThat(classToTest.state.value.visit?.status).isEqualTo("NO_SHOW")
        assertThat(shifa.doctor.ui.navigation.ShellNotice.pending.value?.text).isEqualTo(Res.string.noShowMarked)
        settle { completedDays.isNotEmpty() }
        assertThat(completedDays).isNotEmpty()
        shifa.doctor.ui.navigation.ShellNotice.pending.value = null
    }

    @Test
    fun `a refused no-show stays in the visit with its reason`() {
        noShowStatus = HttpStatusCode.Conflict
        val classToTest = opened()
        classToTest.askNoShow(true)
        classToTest.noShow()
        settle { classToTest.state.value.banner != null }
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.noShowNotAllowed)
        assertThat(classToTest.state.value.banner?.requestId).isEqualTo("r-ns")
        assertThat(classToTest.state.value.askNoShow).isFalse()
        assertThat(classToTest.state.value.left).isNull()
    }
}
