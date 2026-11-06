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
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appointmentPlanApplyFailed
import shifa.doctor.generated.resources.appointmentPlanPaymentFailed
import shifa.doctor.generated.resources.plansLoadFailed

class VisitPlanTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()

    // What the server has: the paid amount (a payment moves it) and the lines it completed.
    private var paid = 10_000_000L
    private var payWrites = true
    private var completedOnServer = setOf<Long>()
    private var ours = listOf<Long>()
    private val ledger = java.util.concurrent.CopyOnWriteArrayList<String>()

    private fun summary(
        id: Long,
        title: String = "Plan $id",
    ) =
        """{"id":$id,"clinicId":2,"patientId":14,"title":"$title","status":"ACTIVE","planKind":"COMPREHENSIVE","totalMinor":30000000,"paidMinor":$paid,"owedMinor":${30_000_000 - paid},"currency":"UZS","linesCompletedCount":1,"linesTotalCount":4}"""

    private var plans = "[${summary(5)}]"
    private var plansStatus = HttpStatusCode.OK
    private var fulfilStatus = HttpStatusCode.OK
    private var payStatus = HttpStatusCode.OK
    private val meta = { fdi: String -> "\"{\\\"fdi\\\":\\\"$fdi\\\"}\"" }
    private val detail get() =
        """{"summary":${summary(5)},"lines":[
           {"id":1,"title":"Root canal","status":"${if (1L in completedOnServer) "COMPLETED" else "PLANNED"}","unitPriceMinor":8000000,"lineTotalMinor":8000000,"currency":"UZS","specialtyMetadata":${meta(
            "36",
        )}},
           {"id":2,"title":"Crown","status":"PLANNED","unitPriceMinor":15000000,"lineTotalMinor":15000000,"currency":"UZS","specialtyMetadata":${meta(
            "36",
        )}},
           {"id":3,"title":"Filling","status":"COMPLETED","unitPriceMinor":6000000,"lineTotalMinor":6000000,"currency":"UZS","specialtyMetadata":${meta(
            "46",
        )}},
           {"id":4,"title":"Cleaning","status":"PLANNED","unitPriceMinor":1000000,"lineTotalMinor":1000000,"currency":"UZS"},
           {"id":5,"title":"Dropped","status":"CANCELLED","specialtyMetadata":${meta("11")}}],
           "installmentPlans":[],"dentalPlanDocumentation":"{\"version\":2,\"dentition\":\"permanent\",\"teeth\":{}}"}"""
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val engine =
        MockEngine { req ->
            val path = req.url.encodedPath.removePrefix("/api/")
            calls += "${req.method.value} $path ${req.body.toByteArray().decodeToString()}".trim()

            fun ok(text: String) = respond(text, HttpStatusCode.OK, json)
            when {
                path == "me/clinics" -> ok("""[{"clinicId":2,"name":"Shifa Dev Clinic"}]""")
                path == "treatment-plans" -> respond(plans, plansStatus, json)
                path == "treatment-plans/5/detail" -> ok(detail)
                path == "treatment-plans/5/fulfillment-candidates" ->
                    ok(
                        """[{"lineId":1,"title":"Root canal","fdi":"36"},{"lineId":2,"title":"Crown","fdi":"36"},{"lineId":4,"title":"Cleaning"}]""",
                    )
                path.endsWith("/fulfill") -> respond(detail, fulfilStatus, json)
                path == "clinics/2/finance/payments" && req.method.value == "GET" -> ok("[${ledger.joinToString(",")}]")
                path == "clinics/2/finance/payments" -> {
                    // A write that happened although the answer was an error (a lost response).
                    if (payStatus == HttpStatusCode.OK || payWrites) {
                        paid += 15_000_000
                        ledger += """{"id":${ledger.size + 1},"treatmentPlanId":5,"amountMinor":15000000,"currency":"UZS","method":"CARD_EXTERNAL","linkedAppointmentId":7}"""
                    }
                    respond("{}", payStatus, json)
                }
                else -> respond("{}", HttpStatusCode.NotFound, json)
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

    private fun opened(
        linked: Long? = null,
        fulfilled: List<Long> = emptyList(),
    ): VisitPlan {
        val client = { createHttpClient({ null }, engine) }
        return VisitPlan(
            7,
            scope,
            TreatmentPlansRepository(client),
            FinanceRepository(client),
            ClinicsRepository(client, com.russhwolf.settings.MapSettings(), { "u1" }),
            fulfilledByVisit = { Result.success(ours) },
        ).also {
            it.load(14, linked, fulfilled)
            settle {
                it.state.value.let {
                        s ->
                    s.loadError != null || (s.plans != null && (s.selectedId == null || s.candidates != null))
                }
            }
        }
    }

    private fun writes() = calls.filter { !it.startsWith("GET") }

    @Test
    fun `the only plan is chosen, with its teeth, open lines and finances`() {
        val s = opened().state.value
        assertThat(
            calls.first {
                it.startsWith("GET treatment-plans ") || it.startsWith("GET treatment-plans")
            },
        ).contains("treatment-plans")
        assertThat(s.selectedId).isEqualTo(5L)
        assertThat(s.toothStates).containsExactly("36", ToothPlanState.Planned, "46", ToothPlanState.Done)
        assertThat(s.toothCounts).containsExactly("36", 2, "46", 1)
        assertThat(s.lines("__general__").map { it.title }).containsExactly("Cleaning")
        assertThat(s.currency).isEqualTo("UZS")
        assertThat(s.progress).isEqualTo(1 to 4)
    }

    @Test
    fun `of two plans the linked one is chosen, else none, and a failed read says so`() {
        plans = "[${summary(5)},${summary(6)}]"
        assertThat(opened(linked = 5).state.value.selectedId).isEqualTo(5L)
        assertThat(opened().state.value.selectedId).isNull()
        plansStatus = HttpStatusCode.InternalServerError
        assertThat(opened().state.value.loadError?.text).isEqualTo(Res.string.plansLoadFailed)
    }

    @Test
    fun `ticks only on open lines colour the tooth, and End applies them once`() {
        val classToTest = opened(fulfilled = listOf(2))
        // Line 2 was completed by this visit before; line 3 is completed on the plan; neither can be ticked.
        classToTest.toggle(2)
        classToTest.toggle(3)
        assertThat(classToTest.state.value.ticks).isEmpty()
        assertThat(classToTest.state.value.toothStates["36"]).isEqualTo(ToothPlanState.Partial)
        classToTest.toggle(1)
        classToTest.toggle(4)
        assertThat(classToTest.state.value.toothStates["36"]).isEqualTo(ToothPlanState.Done)
        assertThat(classToTest.state.value.hasPending).isTrue()
        fulfilStatus = HttpStatusCode.Conflict
        assertThat(runBlocking { classToTest.applyTicks() }).isEqualTo(Res.string.appointmentPlanApplyFailed)
        assertThat(classToTest.state.value.ticks).containsExactly(1L, 4L)
        fulfilStatus = HttpStatusCode.OK
        assertThat(runBlocking { classToTest.applyTicks() }).isNull()
        assertThat(writes().last()).isEqualTo("POST treatment-plans/5/appointments/7/fulfill {\"lineIds\":[1,4]}")
        assertThat(classToTest.state.value.ticks).isEmpty()
        assertThat(classToTest.state.value.fulfilled).containsExactly(1L, 2L, 4L)
        // Nothing left to apply.
        assertThat(runBlocking { classToTest.applyTicks() }).isNull()
        assertThat(writes().count { it.contains("fulfill") }).isEqualTo(2)
    }

    @Test
    fun `the payment is typed in major units, recorded once, and a failure keeps it`() {
        val classToTest = opened()
        classToTest.amount("-100")
        assertThat(classToTest.state.value.amountInvalid).isTrue()
        classToTest.amount("")
        assertThat(classToTest.state.value.pendingPayment).isFalse()
        classToTest.amount("0")
        assertThat(classToTest.state.value.amountInvalid).isTrue()
        classToTest.amount("150 000")
        classToTest.method(PayMethod.Card)
        assertThat(classToTest.state.value.amountMinor).isEqualTo(15_000_000L)
        assertThat(classToTest.state.value.overOutstanding).isFalse()
        classToTest.amount("250 000")
        assertThat(classToTest.state.value.overOutstanding).isTrue()
        classToTest.amount("150 000")
        payStatus = HttpStatusCode.InternalServerError
        payWrites = false
        assertThat(runBlocking { classToTest.recordPayment() }).isEqualTo(Res.string.appointmentPlanPaymentFailed)
        assertThat(classToTest.state.value.recorded).isNull()
        // Not recorded by the server: the field is held until End has checked, then the payment is posted.
        classToTest.amount("1")
        assertThat(classToTest.state.value.amount).isEqualTo("150 000")
        payStatus = HttpStatusCode.OK
        assertThat(runBlocking { classToTest.recordPayment() }).isNull()
        assertThat(classToTest.state.value.detail?.summary?.paidMinor).isEqualTo(25_000_000L)
        assertThat(writes().last()).isEqualTo(
            "POST clinics/2/finance/payments {\"treatmentPlanId\":5,\"amountMinor\":15000000,\"currency\":\"UZS\",\"method\":\"CARD_EXTERNAL\",\"linkedAppointmentId\":7}",
        )
        assertThat(classToTest.state.value.recorded).isEqualTo(15_000_000L)
        // Recorded once: End again doesn't post it again, and the field is locked.
        assertThat(runBlocking { classToTest.recordPayment() }).isNull()
        classToTest.amount("1")
        assertThat(writes().count { it.contains("payments") }).isEqualTo(2)
        assertThat(classToTest.state.value.hasPending).isFalse()
    }

    @Test
    fun `a payment the server recorded although its answer was lost is not posted again, and the plan stays`() {
        plans = "[${summary(5)},${summary(6)}]"
        val classToTest = opened(linked = 5)
        classToTest.amount("150 000")
        classToTest.method(PayMethod.Card)
        payStatus = HttpStatusCode.GatewayTimeout
        assertThat(runBlocking { classToTest.recordPayment() }).isEqualTo(Res.string.appointmentPlanPaymentFailed)
        // The payment belongs to this plan now, with its amount and method.
        classToTest.select(6)
        classToTest.method(PayMethod.Cash)
        assertThat(classToTest.state.value.selectedId).isEqualTo(5L)
        assertThat(classToTest.state.value.method).isEqualTo(PayMethod.Card)
        assertThat(runBlocking { classToTest.recordPayment() }).isNull()
        assertThat(writes().count { it.contains("payments") }).isEqualTo(1)
        assertThat(classToTest.state.value.recorded).isEqualTo(15_000_000L)
        assertThat(classToTest.state.value.detail?.summary?.paidMinor).isEqualTo(25_000_000L)
    }

    @Test
    fun `a clear refusal frees the amount and the plan again`() {
        plans = "[${summary(5)},${summary(6)}]"
        val classToTest = opened(linked = 5)
        classToTest.amount("150 000")
        payStatus = HttpStatusCode.BadRequest
        payWrites = false
        assertThat(runBlocking { classToTest.recordPayment() }).isEqualTo(Res.string.appointmentPlanPaymentFailed)
        assertThat(classToTest.state.value.attempt).isNull()
        classToTest.amount("")
        assertThat(classToTest.state.value.hasPending).isFalse()
        classToTest.select(6)
        assertThat(classToTest.state.value.selectedId).isEqualTo(6L)
    }

    @Test
    fun `ticks the server completed although the answer was lost are counted as applied`() {
        val classToTest = opened()
        classToTest.toggle(1)
        completedOnServer = setOf(1L)
        ours = listOf(1L)
        fulfilStatus = HttpStatusCode.GatewayTimeout
        assertThat(runBlocking { classToTest.applyTicks() }).isEqualTo(Res.string.appointmentPlanApplyFailed)
        assertThat(classToTest.state.value.ticks).isEmpty()
        assertThat(classToTest.state.value.fulfilled).contains(1L)
        // Nothing left to send at the next End.
        assertThat(runBlocking { classToTest.applyTicks() }).isNull()
        assertThat(writes().count { it.contains("fulfill") }).isEqualTo(1)
    }

    @Test
    fun `a ticked line another visit completed meanwhile is dropped, not counted as this visit's`() {
        val classToTest = opened()
        classToTest.toggle(1)
        completedOnServer = setOf(1L)
        fulfilStatus = HttpStatusCode.Conflict
        assertThat(runBlocking { classToTest.applyTicks() }).isEqualTo(Res.string.appointmentPlanApplyFailed)
        assertThat(classToTest.state.value.ticks).isEmpty()
        assertThat(classToTest.state.value.fulfilled).doesNotContain(1L)
        assertThat(runBlocking { classToTest.applyTicks() }).isNull()
    }

    @Test
    fun `choosing another plan drops the ticks and the payment typed for the first`() {
        plans = "[${summary(5)},${summary(6)}]"
        val classToTest = opened(linked = 5)
        classToTest.toggle(1)
        classToTest.amount("1000")
        classToTest.select(null)
        assertThat(classToTest.state.value.ticks).isEmpty()
        assertThat(classToTest.state.value.amount).isEmpty()
        assertThat(classToTest.state.value.hasPending).isFalse()
        // The fulfil sheet opens only on a tooth with lines.
        classToTest.select(5)
        settle { classToTest.state.value.candidates != null }
        assertThat(classToTest.open("21")).isFalse()
        assertThat(classToTest.open("36")).isTrue()
        assertThat(classToTest.state.value.sheet).isEqualTo("36")
    }
}
