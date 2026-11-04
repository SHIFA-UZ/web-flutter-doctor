@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.treatmentplans

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
import shifa.doctor.data.model.LineStatus
import shifa.doctor.data.model.PlanKind
import shifa.doctor.data.model.PlanStatus

class TreatmentPlansRepositoryTest {
    private val calls = mutableListOf<String>()
    private val summary =
        """{"id":5,"clinicId":2,"patientId":14,"title":"Upper jaw","status":"IN_PROGRESS","planKind":"COMPREHENSIVE","totalMinor":300,"paidMinor":100,"owedMinor":200,"currency":"UZS","planPaymentStatus":"PARTIAL","linesCompletedCount":1,"linesTotalCount":3,"attendingDoctors":[{"id":4,"name":"Dr. A"},{"id":"bad"}],"symptoms":[]}"""
    private val lines =
        """[{"id":1,"title":"Filling","status":"COMPLETED","unitPriceMinor":100,"lineTotalMinor":100,"currency":"UZS","specialtyMetadata":"{\"fdi\":\"16\",\"dentition\":\"permanent\",\"source\":\"PLAN_WIZARD\"}"},
           {"id":2,"title":"Cleaning","status":"PLANNED","specialtyMetadata":null},
           {"id":3,"title":"Odd","status":"NEW_STATUS","specialtyMetadata":"not json"},
           {"title":"no id"}]"""
    private var detail = """{"summary":$summary,"lines":$lines,"installmentPlans":[],"dentalPlanDocumentation":"{\"version\":2}"}"""
    private var plansStatus = HttpStatusCode.OK
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            calls += "${req.method.value} $path?${req.url.encodedQuery} ${req.body.toByteArray().decodeToString()}".trim()
            when {
                path == "treatment-plans" && plansStatus != HttpStatusCode.OK ->
                    respond(
                        """{"status":500}""",
                        plansStatus,
                        json,
                    )
                path == "treatment-plans" -> respond("[$summary, {\"bad\":true}]", HttpStatusCode.OK, json)
                path == "treatment-plans/5/detail" -> respond(detail, HttpStatusCode.OK, json)
                path == "treatment-plans/5/fulfillment-candidates" ->
                    respond(
                        """[{"lineId":2,"title":"Cleaning","fdi":null,"status":"PLANNED","toothMatch":false,"currency":"UZS"}]""",
                        HttpStatusCode.OK,
                        json,
                    )
                path == "treatment-plans/5/appointments/7/fulfill" -> respond(detail, HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.NotFound, json)
            }
        }
    private val classToTest = TreatmentPlansRepository { createHttpClient({ null }, engine) }

    @Test
    fun `a patient's active plans, an unreadable plan or doctor skipped, a failure not empty`() =
        runTest {
            val first = classToTest.plans(2, 14, status = "ACTIVE").getOrThrow()
            assertThat(first.single().status).isEqualTo(PlanStatus.InProgress)
            assertThat(first.single().kind).isEqualTo(PlanKind.Comprehensive)
            assertThat(first.single().attendingDoctors.map { it.name }).containsExactly("Dr. A")
            assertThat(calls.first()).contains("clinicId=2&patientId=14&status=ACTIVE&planKind=COMPREHENSIVE")
            // A failure is a failure, not "no plans".
            plansStatus = HttpStatusCode.InternalServerError
            assertThat(classToTest.plans(2, 14, status = "ACTIVE").isFailure).isTrue()
        }

    @Test
    fun `the detail reads its lines with their tooth, nested or flat summary, and fulfil sends the line ids`() =
        runTest {
            val d = classToTest.detail(5).getOrThrow()
            assertThat(d.summary.title).isEqualTo("Upper jaw")
            assertThat(d.lines.map { it.id }).containsExactly(1L, 2L, 3L).inOrder()
            assertThat(d.lines.map { it.tooth }).containsExactly("16", null, null).inOrder()
            assertThat(
                d.lines.map {
                    it.status
                },
            ).containsExactly(LineStatus.Completed, LineStatus.Planned, LineStatus.Unknown).inOrder()
            assertThat(d.dentalPlanDocumentation).isEqualTo("{\"version\":2}")
            // An older backend's flat summary.
            detail = summary.dropLast(1) + ""","lines":$lines}"""
            assertThat(classToTest.detail(5).getOrThrow().summary.owedMinor).isEqualTo(200L)
            detail = "[]"
            assertThat(classToTest.detail(5).isFailure).isTrue()
            assertThat(classToTest.candidates(5, 7).getOrThrow().single().fdi).isNull()
            assertThat(calls.last()).contains("fulfillment-candidates?appointmentId=7")
            detail = """{"summary":$summary,"lines":[]}"""
            classToTest.fulfill(5, 7, listOf(2, 3)).getOrThrow()
            assertThat(calls.last()).isEqualTo("POST treatment-plans/5/appointments/7/fulfill? {\"lineIds\":[2,3]}")
        }
}
