@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.forms

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinical.ClinicalEngineRepository
import shifa.doctor.data.documents.DocumentsRepository
import shifa.doctor.data.forms.DentalChartCodec
import shifa.doctor.data.forms.Dentition
import shifa.doctor.data.forms.FormsRepository
import shifa.doctor.data.forms.Icd10Hit
import shifa.doctor.data.model.ClinicalChipSelection
import shifa.doctor.data.model.ClinicalDisease
import shifa.doctor.data.model.LocalizedName
import shifa.doctor.data.patients.PatientsRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.formDocumentFailed
import shifa.doctor.generated.resources.formLoadFailed
import shifa.doctor.generated.resources.patientSignatureRequestSent
import shifa.doctor.generated.resources.previousChartUnavailable
import shifa.doctor.stopAll
import shifa.doctor.utils.PracticeClock

@OptIn(ExperimentalCoroutinesApi::class)
class FormEditorViewModelTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var linkStatus = HttpStatusCode.OK

    // BE-01's `pdf-document`: a backend without it (404, today's production) unless a test says otherwise.
    private var serverPdf = HttpStatusCode.NotFound
    private var latest = true
    private var formsFail = false
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            val method = req.method.value
            val body = req.body.toByteArray().decodeToString()
            calls += "$method $path?${req.url.encodedQuery}"

            fun ok(text: String) = respond(text, HttpStatusCode.OK, json)
            when {
                path == "patients/14" ->
                    ok(
                        """{"id":14,"name":"Jasur Aliyev","gender":"male","birthDate":"1985-01-11","address":"Chilonzor 9","chronicDisease":"Asthma"}""",
                    )
                path == "patients/14/forms" && method == "GET" && formsFail ->
                    respond(
                        """{"status":500,"code":"X","requestId":"req-f"}""",
                        HttpStatusCode.InternalServerError,
                        json,
                    )
                path == "patients/14/forms" && method == "GET" ->
                    ok(
                        if (latest) {
                            """[{"id":3,"patientId":14,"templateId":"025-2","date":"2026-10-01","fullName":"Jasur Aliyev","dentalChart":{"TOP_0_2":"C","16":"P"}},
                               {"id":5,"patientId":14,"templateId":"025-2","date":"2026-10-20","fullName":"Jasur Aliyev","documentId":77,"diagnosis":"Caries","doctorName":"Dr. Rahimova","dentalChart":{"UR1":"K","TOP_0_1":"R"}}]"""
                        } else {
                            "[]"
                        },
                    )
                path == "patients/14/forms" -> ok(body.dropLast(1) + ""","id":9,"doctorName":"Dr. Karimov"}""")
                path == "patients/14/forms/9/link-document" ->
                    if (linkStatus == HttpStatusCode.OK) {
                        ok(
                            """{"id":9,"patientId":14,"templateId":"025-2","date":"2026-11-04","fullName":"Jasur Aliyev","documentId":101,"diagnosis":"Pulpit"}""",
                        )
                    } else {
                        respond("""{"status":500,"code":"X","requestId":"req-l"}""", linkStatus, json)
                    }
                path.endsWith("/pdf-document") ->
                    if (serverPdf == HttpStatusCode.OK) {
                        ok("""{"documentId":202,"url":"https://api/patientdocuments/14/form.pdf"}""")
                    } else {
                        respond("""{"status":${serverPdf.value},"code":"X","requestId":"req-p"}""", serverPdf, json)
                    }
                path.endsWith(
                    "/request-signature",
                ) ->
                    ok(
                        """{"id":9,"patientId":14,"templateId":"025-2","date":"2026-11-04","fullName":"Jasur Aliyev","diagnosis":"Pulpit","signatureRequested":true}""",
                    )
                path.startsWith("patients/14/forms/") -> ok(body)
                path == "patients/14/documents" -> ok("""{"id":101,"title":"Form","canView":true}""")
                path.startsWith("patients/14/documents/") -> ok("""{"id":77,"title":"Form","canView":true}""")
                path == "icd10/search" && req.url.parameters["q"] == "fail" ->
                    respond("""{"status":500,"code":"X"}""", HttpStatusCode.InternalServerError, json)
                path == "icd10/search" -> ok("""[{"code":"K04.0","title":"Pulpitis","subtitle":"K04"}]""")
                path == "clinical-engine/record-usage" -> respond("", HttpStatusCode.OK)
                else -> respond("[]", HttpStatusCode.OK, json)
            }
        }
    private val scheduler = TestCoroutineScheduler()
    private val created = mutableListOf<FormEditorViewModel>()
    private val clock =
        PracticeClock("Asia/Tashkent", { Instant.parse("2026-11-04T05:00:00Z") }, { TimeZone.of("Asia/Tashkent") })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher(scheduler))

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
        formId: Long? = null,
        warned: MutableSet<Long> = mutableSetOf(),
        icd: Icd10Hit? = null,
        clinicId: Long? = null,
    ): FormEditorViewModel {
        val client = { createHttpClient({ null }, engine) }
        return FormEditorViewModel(
            14,
            formId,
            FormsRepository(client),
            DocumentsRepository(client),
            PatientsRepository(client = client),
            ClinicalEngineRepository(client),
            { clock },
            { "Dr. Karimov" },
            { "en" },
            { "%PDF-1.4 form".encodeToByteArray() },
            warned,
            icd,
            clinicId,
        ).also {
            created += it
            it.load()
            settle { !it.state.value.loading }
        }
    }

    @Test
    fun `a new form starts from the patient and the latest 025-2's chart, and warns about a chronic disease once`() {
        val warned = mutableSetOf<Long>()
        val classToTest = opened(warned = warned)
        val f = classToTest.state.value.form!!
        assertThat(f.date).isEqualTo("2026-11-04")
        assertThat(f.fullName to f.gender).isEqualTo("Jasur Aliyev" to "male")
        assertThat(f.age).isEqualTo(41)
        assertThat(f.address).isEqualTo("Chilonzor 9")
        // The latest (20 Oct) 025-2: its legacy tooth migrated; the older one's rows are not used.
        assertThat(f.dentalChart).containsEntry("11", "K")
        assertThat(f.dentalChart.keys.none { it.startsWith("TOP_0_") }).isTrue()
        // Its rows are history dated and signed by that form, not today's visit.
        assertThat(f.dentalChart).containsEntry("TOP_HIST_0_1", "R")
        assertThat(f.dentalChart).containsEntry("TOP_HIST_0_date", "2026-10-20")
        assertThat(f.dentalChart).containsEntry("TOP_HIST_0_doctor", "Dr. Rahimova")
        assertThat(classToTest.state.value.dirty).isFalse()
        assertThat(classToTest.state.value.chronicWarning).isTrue()
        assertThat(opened(warned = warned).state.value.chronicWarning).isFalse()
    }

    @Test
    fun `saving needs a diagnosis or complaints, then form, PDF, document, link and usage in one go`() {
        val classToTest = opened()
        classToTest.save()
        assertThat(classToTest.state.value.tried).isTrue()
        assertThat(calls.none { it.startsWith("POST") }).isTrue()
        classToTest.edit { it.copy(diagnosis = "Pulpit", clinicalDiseaseId = "dis.010") }
        classToTest.save()
        settle { classToTest.state.value.done }
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore('?') }
        // Today's production has no `pdf-document` (404): the app renders, uploads and links as before.
        assertThat(writes).containsExactly(
            "POST patients/14/forms",
            "POST patients/14/forms/9/pdf-document",
            "POST patients/14/documents",
            "POST patients/14/forms/9/link-document",
            "POST clinical-engine/record-usage",
        ).inOrder()
        assertThat(classToTest.state.value.form?.documentId).isEqualTo(101L)
    }

    @Test
    fun `a backend that files the PDF itself replaces the app's render, upload and link`() {
        serverPdf = HttpStatusCode.OK
        val classToTest = opened()
        classToTest.edit { it.copy(diagnosis = "Pulpit", clinicalDiseaseId = "dis.010") }
        classToTest.save()
        settle { classToTest.state.value.done }
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore('?') }
        assertThat(writes).containsExactly(
            "POST patients/14/forms",
            "POST patients/14/forms/9/pdf-document",
            "POST clinical-engine/record-usage",
        ).inOrder()
        assertThat(classToTest.state.value.form?.documentId).isEqualTo(202L)
    }

    @Test
    fun `a failure filing on the server stops at the PDF step, not the app's fallback`() {
        serverPdf = HttpStatusCode.InternalServerError
        val classToTest = opened()
        classToTest.edit { it.copy(diagnosis = "Pulpit") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.failedAt).isEqualTo(SaveStep.Pdf)
        assertThat(calls.none { it.startsWith("POST patients/14/documents") }).isTrue()
    }

    @Test
    fun `in a clinic's scope the patient is read and the form's PDF stored with the clinic`() {
        val classToTest = opened(clinicId = 2)
        assertThat(calls.first { it.startsWith("GET patients/14?") }).isEqualTo("GET patients/14?clinicId=2")
        classToTest.edit { it.copy(diagnosis = "Pulpit", clinicalDiseaseId = "dis.010") }
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(calls).contains("POST patients/14/documents?clinicId=2")
    }

    @Test
    fun `a failed link is retried alone, the form and its PDF are not sent twice`() {
        linkStatus = HttpStatusCode.InternalServerError
        val classToTest = opened()
        classToTest.edit { it.copy(complaints = "Pain") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.failedAt).isEqualTo(SaveStep.Link)
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.formDocumentFailed)
        assertThat(classToTest.state.value.form?.id).isEqualTo(9L)
        linkStatus = HttpStatusCode.OK
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(calls.count { it.startsWith("POST patients/14/forms?") }).isEqualTo(1)
        assertThat(calls.count { it.startsWith("POST patients/14/documents?") }).isEqualTo(1)
        assertThat(calls.count { it.contains("link-document") }).isEqualTo(2)
    }

    @Test
    fun `a change after a failed step saves the form again and replaces the uploaded file, no second upload`() {
        linkStatus = HttpStatusCode.InternalServerError
        val classToTest = opened()
        classToTest.edit { it.copy(complaints = "Pain") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        assertThat(classToTest.state.value.failedAt).isEqualTo(SaveStep.Link)
        linkStatus = HttpStatusCode.OK
        classToTest.edit { it.copy(complaints = "Pain at night") }
        classToTest.save()
        settle { classToTest.state.value.done }
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore('?') }
        assertThat(writes).containsExactly(
            "POST patients/14/forms",
            "POST patients/14/forms/9/pdf-document",
            "POST patients/14/documents",
            "POST patients/14/forms/9/link-document",
            "PUT patients/14/forms/9",
            "PUT patients/14/documents/101",
            "POST patients/14/forms/9/link-document",
        ).inOrder()
    }

    @Test
    fun `a signature request after a failed save makes Save remake the PDF and replace the uploaded file`() {
        linkStatus = HttpStatusCode.InternalServerError
        val classToTest = opened()
        classToTest.edit { it.copy(complaints = "Pain") }
        classToTest.save()
        settle { !classToTest.state.value.saving }
        linkStatus = HttpStatusCode.OK
        classToTest.edit { it.copy(complaints = "Pain at night") }
        classToTest.requestSignature()
        settle { !classToTest.state.value.signing }
        assertThat(classToTest.state.value.failedAt).isEqualTo(SaveStep.Pdf)
        calls.clear()
        classToTest.save()
        settle { classToTest.state.value.done }
        assertThat(calls.filter { !it.startsWith("GET") }.map { it.substringBefore('?') })
            .containsExactly("PUT patients/14/documents/101", "POST patients/14/forms/9/link-document").inOrder()
    }

    @Test
    fun `a new form opens without the previous chart when the forms list fails, an existing one doesn't`() {
        formsFail = true
        val fresh = opened()
        assertThat(fresh.state.value.error).isNull()
        assertThat(fresh.state.value.form?.dentalChart).isEmpty()
        assertThat(fresh.state.value.banner?.text).isEqualTo(Res.string.previousChartUnavailable)
        assertThat(opened(formId = 5).state.value.error?.text).isEqualTo(Res.string.formLoadFailed)
    }

    @Test
    fun `an existing form is put and its linked document's file replaced`() {
        val classToTest = opened(formId = 5)
        assertThat(classToTest.state.value.form?.dentalChart).containsEntry("11", "K")
        classToTest.edit { it.copy(treatment = "Filling") }
        assertThat(classToTest.state.value.dirty).isTrue()
        classToTest.save()
        settle { classToTest.state.value.done }
        val writes = calls.filter { !it.startsWith("GET") }.map { it.substringBefore('?') }
        assertThat(writes)
            .containsExactly(
                "PUT patients/14/forms/5",
                "POST patients/14/forms/5/pdf-document",
                "PUT patients/14/documents/77",
            )
            .inOrder()
    }

    @Test
    fun `the engine's texts go into their fields and its disease becomes the diagnosis with its code`() {
        val classToTest = opened()
        classToTest.edit { it.copy(complaints = "typed") }
        classToTest.applyEngine(
            EngineResult(
                ClinicalDisease("dis.010", icdCodes = listOf("K04.0"), names = LocalizedName(en = "Acute pulpitis")),
                mapOf(
                    "complaints" to "Night pain",
                    "morbi" to "3 days",
                    "treatment_1" to "Pulp removal",
                    "treatment_2" to "",
                    "xray" to "Wide canal",
                ),
                listOf(ClinicalChipSelection("dis.010.complaints.1")),
            ),
        )
        val f = classToTest.state.value.form!!
        assertThat(f.complaints).isEqualTo("Night pain")
        assertThat(f.moreDetails).isEqualTo("3 days")
        assertThat(f.treatment).isEqualTo("Pulp removal")
        assertThat(f.treatmentResult).isNull()
        assertThat(f.xrayLabData).isEqualTo("Wide canal")
        assertThat(f.diagnosis to f.diagnosisCode).isEqualTo("Acute pulpitis" to "K04.0")
        assertThat(f.diagnosisSystem).isEqualTo("ICD10")
        assertThat(f.clinicalDiseaseId).isEqualTo("dis.010")
        assertThat(f.clinicalChipSelections.single().chipId).isEqualTo("dis.010.complaints.1")
    }

    @Test
    fun `ICD search waits 250 ms and a pick fills code, title and system`() {
        val classToTest = opened()
        classToTest.searchIcd("K04")
        scheduler.advanceTimeBy(200)
        assertThat(calls.none { it.startsWith("GET icd10/search") }).isTrue()
        scheduler.advanceTimeBy(100)
        settle { classToTest.state.value.icdResults != null }
        assertThat(calls.last()).isEqualTo("GET icd10/search?q=K04&limit=20&lang=en")
        classToTest.pickIcd(Icd10Hit("K04.0", "Pulpitis"))
        val f = classToTest.state.value.form!!
        assertThat(
            listOf(f.diagnosisCode, f.diagnosisDisplay, f.diagnosisSystem),
        ).containsExactly("K04.0", "Pulpitis", "ICD10").inOrder()
        classToTest.clearIcd()
        assertThat(classToTest.state.value.form?.diagnosisCode).isNull()
        // A failed search says so (not "No matches").
        classToTest.searchIcd("fail")
        scheduler.advanceTimeBy(300)
        settle { !classToTest.state.value.icdBusy }
        assertThat(classToTest.state.value.icdFailed).isTrue()
        assertThat(classToTest.state.value.icdResults).isNull()
    }

    @Test
    fun `a signature request needs a valid form, saves a new one first, then asks the patient`() {
        val classToTest = opened()
        classToTest.requestSignature()
        assertThat(classToTest.state.value.tried).isTrue()
        assertThat(classToTest.state.value.signing).isFalse()
        assertThat(calls.none { !it.startsWith("GET") }).isTrue()
        classToTest.edit { it.copy(diagnosis = "Pulpit") }
        classToTest.requestSignature()
        settle { !classToTest.state.value.signing }
        assertThat(calls.filter { !it.startsWith("GET") }.map { it.substringBefore('?') })
            .containsExactly("POST patients/14/forms", "PUT patients/14/forms/9/request-signature").inOrder()
        assertThat(classToTest.state.value.form?.signatureRequested).isTrue()
        assertThat(classToTest.state.value.banner?.text).isEqualTo(Res.string.patientSignatureRequestSent)
    }

    @Test
    fun `chart cells, rows and dentition edit the chart, and leaving with changes asks first`() {
        latest = false
        val classToTest = opened()
        assertThat(classToTest.state.value.topRows).containsExactly(0)
        classToTest.setCell(DentalChartCodec.TOP, 0, 3, "C")
        classToTest.addRow(DentalChartCodec.TOP)
        classToTest.setCell(DentalChartCodec.TOP, 1, 0, "П")
        assertThat(classToTest.state.value.topRows).containsExactly(0, 1).inOrder()
        classToTest.removeRow(DentalChartCodec.TOP)
        assertThat(classToTest.state.value.form?.dentalChart).containsExactly("TOP_0_3", "C")
        // Switching the teeth clears the editable rows (their cells would land on other teeth).
        classToTest.addRow(DentalChartCodec.BOTTOM)
        classToTest.setDentition(Dentition.Primary)
        assertThat(classToTest.state.value.dentition).isEqualTo(Dentition.Primary)
        assertThat(classToTest.state.value.form?.dentalChart).containsExactly("__dentition__", "primary")
        assertThat(classToTest.state.value.bottomRows).containsExactly(0)
        assertThat(classToTest.back()).isFalse()
        assertThat(classToTest.state.value.askDiscard).isTrue()
        assertThat(classToTest.back()).isTrue()
        assertThat(classToTest.state.value.today).isEqualTo(LocalDate(2026, 11, 4))
    }

    @Test
    fun `a code applied in the visit fills a new form's ICD-10, unsaved, and leaves an existing form alone`() {
        val fresh = opened(icd = Icd10Hit("K04.0", "Pulpitis"))
        val f = fresh.state.value.form!!
        assertThat(
            listOf(f.diagnosisCode, f.diagnosisDisplay, f.diagnosisSystem),
        ).containsExactly("K04.0", "Pulpitis", "ICD10").inOrder()
        assertThat(fresh.state.value.dirty).isTrue()
        val existing = opened(formId = 5, icd = Icd10Hit("K04.0", "Pulpitis"))
        assertThat(existing.state.value.form?.diagnosisCode).isNull()
        assertThat(existing.state.value.dirty).isFalse()
    }
}
