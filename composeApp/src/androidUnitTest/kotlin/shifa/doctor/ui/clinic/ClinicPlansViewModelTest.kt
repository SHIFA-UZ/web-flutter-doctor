@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.clinic

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.model.PlanStatus
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.ClinicEvents
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicTreatmentPlanExportPdfFailed
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicPlansViewModelTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var detailMissing = false
    private var statusFails = false
    private val plan = """{"id":501,"clinicId":2,"patientId":12,"patientName":"Jasur","title":"Molars","status":"ACTIVE","totalMinor":160000000,"paidMinor":100000000,"owedMinor":60000000,"currency":"UZS","visitCount":2,"linesCompletedCount":1,"linesTotalCount":3}"""

    // BE-01's `GET /treatment-plans/{id}/pdf`: missing (404, today's production) unless a test serves it.
    private var serverPdf: String? = null

    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/")
                val q = req.url.encodedQuery
                val body = req.body.toByteArray().decodeToString()
                calls += "${req.method.value} $path" + (if (q.isEmpty()) "" else "?$q") + (if (body.isEmpty()) "" else " $body")
                val json = headersOf(HttpHeaders.ContentType, "application/json")

                fun ok(s: String) = respond(s, HttpStatusCode.OK, json)
                when {
                    path == "treatment-plans" -> ok("[$plan]")
                    path == "treatment-plans/501/visits" ->
                        ok(
                            """[{"appointmentId":101,"startAt":"2026-11-04T05:00:00Z","endAt":"2026-11-04T05:30:00Z","status":"COMPLETED","doctorProfileId":21,"doctorName":"Aziza","services":["Root canal"],"timing":"PAST"}]""",
                        )
                    path == "treatment-plans/501/status" && statusFails ->
                        respond("""{"status":500,"code":"INTERNAL"}""", HttpStatusCode.InternalServerError, json)
                    path == "treatment-plans/501/status" -> ok(plan)
                    path == "treatment-plans/501/detail" && detailMissing ->
                        respond(
                            """{"status":404,"code":"NOT_FOUND"}""",
                            HttpStatusCode.NotFound,
                            json,
                        )
                    path == "treatment-plans/501/detail" ->
                        ok(
                            """{"summary":$plan,"lines":[{"id":1,"title":"Root canal","lineTotalMinor":80000000,"status":"COMPLETED","specialtyMetadata":"{\"fdi\":\"36\"}"}],"installmentPlans":[{"installmentPlanId":61,"status":"ACTIVE","totalAmountMinor":30000000,"currency":"UZS","numInstallments":1,"scheduleRows":[{"installmentItemId":801,"sequenceNumber":1,"amountMinor":30000000,"currency":"UZS","dueDate":"2026-12-01","status":"PENDING"}]}]}""",
                        )
                    path == "clinics/2/finance/payments" -> ok("""{"id":1}""")
                    path == "treatment-plans/501/pdf" ->
                        serverPdf?.let {
                            respond(
                                it,
                                HttpStatusCode.OK,
                                headersOf(HttpHeaders.ContentType, "application/pdf"),
                            )
                        }
                            ?: respond("""{"status":404,"code":"NOT_FOUND"}""", HttpStatusCode.NotFound, json)
                    else -> ok("{}")
                }
            },
        )
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun settle(check: () -> Boolean) {
        repeat(500) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the list asks the server by status and search, visits read once when a plan opens, cancelling asks first`() {
        val vm = ClinicPlansViewModel(2, TreatmentPlansRepository { client }).also { created += it }
        settle { vm.state.value.plans != null }
        assertThat(calls.first()).isEqualTo("GET treatment-plans?clinicId=2&planKind=COMPREHENSIVE")
        vm.status(PlanStatus.OnHold)
        settle { calls.size == 2 }
        assertThat(calls.last()).isEqualTo("GET treatment-plans?clinicId=2&status=ON_HOLD&planKind=COMPREHENSIVE")
        vm.status(null)
        vm.type(" jas ")
        // The search waits for the pause on the test clock.
        main.scheduler.advanceTimeBy(ClinicPlansViewModel.DEBOUNCE.inWholeMilliseconds + 1)
        main.scheduler.runCurrent()
        settle { calls.any { it.endsWith("&q=jas") } && vm.state.value.plans != null }
        assertThat(calls.last { it.startsWith("GET treatment-plans?") })
            .isEqualTo("GET treatment-plans?clinicId=2&planKind=COMPREHENSIVE&q=jas")
        val p = vm.state.value.plans!!.single()
        vm.toggle(p)
        settle { vm.state.value.visits[501L] != null }
        vm.toggle(p)
        vm.toggle(p)
        assertThat(calls.count { it.startsWith("GET treatment-plans/501/visits") }).isEqualTo(1)
        // Cancelling asks first; the same status does nothing.
        vm.statusSheet(StatusSheet(p))
        vm.setStatus(PlanStatus.Active)
        assertThat(vm.state.value.statusSheet).isNull()
        vm.statusSheet(StatusSheet(p))
        vm.setStatus(PlanStatus.Cancelled)
        assertThat(vm.state.value.statusSheet!!.confirmCancel).isTrue()
        assertThat(calls.none { it.startsWith("PATCH") }).isTrue()
        vm.setStatus(PlanStatus.Cancelled)
        settle { vm.state.value.banner != null }
        assertThat(
            calls.single {
                it.startsWith("PATCH")
            },
        ).isEqualTo("""PATCH treatment-plans/501/status {"status":"CANCELLED"}""")
    }

    @Test
    fun `the detail reads the plan and its visits, a missing plan says so, a payment goes to the plan`() {
        val vm =
            PlanDetailViewModel(
                501,
                2,
                TreatmentPlansRepository {
                    client
                },
                FinanceRepository { client },
            ).also { created += it }
        vm.load()
        settle { vm.state.value.detail != null && vm.state.value.visits != null }
        val d = vm.state.value.detail!!
        assertThat(d.lines.single().tooth).isEqualTo("36")
        assertThat(d.installmentPlans.single().items.single().id).isEqualTo(801L)
        vm.openPay()
        assertThat(vm.state.value.pay!!.amount).isEqualTo("600000")
        vm.editPay { it.copy(amount = "x") }
        vm.savePay()
        assertThat(vm.state.value.pay!!.tried).isTrue()
        vm.editPay { it.copy(amount = "200 000", method = PayMethod.Transfer) }
        vm.savePay()
        settle { vm.state.value.pay == null }
        assertThat(calls.single { it.startsWith("POST clinics/2/finance/payments") })
            .isEqualTo(
                """POST clinics/2/finance/payments {"treatmentPlanId":501,"amountMinor":20000000,"currency":"UZS","method":"TRANSFER"}""",
            )

        detailMissing = true
        val gone =
            PlanDetailViewModel(
                501,
                2,
                TreatmentPlansRepository {
                    client
                },
                FinanceRepository { client },
            ).also { created += it }
        gone.load()
        settle { gone.state.value.notFound }
        assertThat(gone.state.value.error).isNull()
    }

    @Test
    fun `export makes the plan's PDF with its file name, a failed one says so, a share that didn't open too`() {
        var made: shifa.doctor.data.treatmentplans.TreatmentPlanPdfData? = null
        var bytes: ByteArray? = byteArrayOf(37, 80)
        val vm =
            PlanDetailViewModel(501, 2, TreatmentPlansRepository { client }, FinanceRepository { client }, { d, _ ->
                made = d
                bytes
            }, language = { "en" }).also { created += it }
        // Nothing read yet: nothing to export.
        vm.exportPdf({ error("not called") }, shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels(), "2026-11-06")
        assertThat(vm.state.value.exporting).isFalse()
        vm.load()
        settle { vm.state.value.detail != null }
        vm.menu(true)
        vm.exportPdf(
            {
                    d ->
                shifa.doctor.data.treatmentplans.TreatmentPlanPdfData(d, "Clinic", "06.11.2026 10:00", null, null, null)
            },
            shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels(),
            "2026-11-06",
        )
        settle { vm.state.value.pdf != null }
        assertThat(vm.state.value.menu).isFalse()
        assertThat(vm.state.value.pdf!!.name).isEqualTo("treatment_plan_501_2026-11-06.pdf")
        assertThat(made!!.plan.summary.id).isEqualTo(501L)
        vm.pdfShared(true)
        assertThat(vm.state.value.pdf).isNull()
        assertThat(vm.state.value.banner).isNull()
        vm.pdfShared(false)
        assertThat(
            vm.state.value.banner!!.text,
        ).isEqualTo(Res.string.clinicTreatmentPlanExportPdfFailed)
        bytes = null
        vm.exportPdf(
            { d -> made!!.copy(plan = d) },
            shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels(),
            "2026-11-06",
        )
        settle { !vm.state.value.exporting }
        assertThat(vm.state.value.pdf).isNull()
        assertThat(
            vm.state.value.banner!!.text,
        ).isEqualTo(Res.string.clinicTreatmentPlanExportPdfFailed)
    }

    @Test
    fun `a backend that renders the plan's PDF is asked first, in the app's language`() {
        serverPdf = "%PDF-server"
        val vm =
            PlanDetailViewModel(
                501,
                2,
                TreatmentPlansRepository { client },
                FinanceRepository { client },
                { _, _ -> error("the app doesn't render it") },
                language = { "ru" },
            ).also { created += it }
        vm.load()
        settle { vm.state.value.detail != null }
        vm.exportPdf({ error("not needed") }, shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels(), "2026-11-07")
        settle { vm.state.value.pdf != null }
        assertThat(vm.state.value.pdf!!.bytes.decodeToString()).isEqualTo("%PDF-server")
        assertThat(calls).contains("GET treatment-plans/501/pdf?lang=ru")
    }

    @Test
    fun `a status change is tracked by its outcome and where it was made`() {
        val tracker = RecordingTracker()
        val vm = ClinicPlansViewModel(2, TreatmentPlansRepository { client }, tracker).also { created += it }
        settle { vm.state.value.plans != null }
        val p = vm.state.value.plans!!.single()
        vm.statusSheet(StatusSheet(p))
        vm.setStatus(PlanStatus.OnHold)
        settle { tracker.events.size == 1 && !vm.state.value.busy }
        statusFails = true
        vm.statusSheet(StatusSheet(p))
        vm.setStatus(PlanStatus.Completed)
        settle { tracker.events.size == 2 }
        assertThat(tracker.events)
            .containsExactly(
                ClinicEvents.PlanStatusChanged("on_hold", "list"),
                ClinicEvents.PlanStatusFailed("completed", "list", "server"),
            ).inOrder()
    }

    @Test
    fun `the plan's payment and PDF are tracked by outcome, the amount as a range`() {
        val tracker = RecordingTracker()
        var bytes: ByteArray? = byteArrayOf(37, 80)
        val vm =
            PlanDetailViewModel(
                501,
                2,
                TreatmentPlansRepository { client },
                FinanceRepository { client },
                { _, _ -> bytes },
                language = { "en" },
                tracker = tracker,
            ).also { created += it }
        vm.load()
        settle { vm.state.value.detail != null }
        vm.openPay()
        vm.editPay { it.copy(amount = "200 000", method = PayMethod.Transfer) }
        vm.savePay()
        settle { vm.state.value.pay == null }
        val data = { d: shifa.doctor.data.model.TreatmentPlanDetail ->
            shifa.doctor.data.treatmentplans.TreatmentPlanPdfData(d, "Clinic", "06.11.2026 10:00", null, null, null)
        }
        vm.exportPdf(data, shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels(), "2026-11-06")
        settle { vm.state.value.pdf != null }
        vm.pdfShared(true)
        bytes = null
        vm.exportPdf(data, shifa.doctor.data.treatmentplans.TreatmentPlanPdfLabels(), "2026-11-06")
        settle { tracker.events.size == 3 }
        assertThat(tracker.events)
            .containsExactly(
                ClinicEvents.PaymentRecorded("plan", "transfer", "100k-500k"),
                ClinicEvents.PlanPdfExported("app"),
                ClinicEvents.PlanPdfExportFailed("render_failed"),
            ).inOrder()
    }
}
