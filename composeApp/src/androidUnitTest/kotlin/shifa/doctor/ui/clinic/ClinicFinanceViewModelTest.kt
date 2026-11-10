@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.clinic

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
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
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.finance.FinanceMonth
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.model.ClinicPatient
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.ClinicEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicFinanceInstallStatusUpdated
import shifa.doctor.generated.resources.clinicFinancePaymentRecorded
import shifa.doctor.generated.resources.clinicRecordsFormSuccess
import shifa.doctor.stopAll
import shifa.doctor.utils.csv

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicFinanceViewModelTest {
    // "METHOD path?query" for reads, plus " body" for writes.
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var hold: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    private var ledgerFails = false
    private var writesFail = false
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/clinics/2/")
                val q = req.url.encodedQuery
                val body = req.body.toByteArray().decodeToString()
                calls += "${req.method.value} $path" + (if (q.isEmpty()) "" else "?$q") + (if (body.isEmpty()) "" else " $body")
                val json = headersOf(HttpHeaders.ContentType, "application/json")

                fun ok(s: String) = respond(s, HttpStatusCode.OK, json)
                when {
                    writesFail && req.method.value != "GET" ->
                        respond("""{"status":500,"code":"INTERNAL"}""", HttpStatusCode.InternalServerError, json)
                    path == "/api/treatment-plans" ->
                        ok(
                            """[{"id":501,"title":"Molar restoration","patientName":"Dilnoza","owedMinor":30000000,"currency":"UZS"},{"id":502,"title":"Paid off","patientName":"Jasur","owedMinor":0,"currency":"UZS"}]""",
                        )
                    path == "finance/records" && req.url.parameters["status"] == "OVERDUE" ->
                        ok(
                            """{"content":[{"id":701,"recordType":"INVOICE","recordNumber":"INV-14","status":"OVERDUE","totalMinor":9000000,"remainingMinor":9000000,"currency":"UZS","dueDate":"2026-10-20"}],"totalElements":1,"number":0}""",
                        )
                    path == "members" ->
                        ok(
                            """[{"doctorProfileId":21,"displayName":"Aziza","membershipRole":"OWNER"}]""",
                        )
                    path == "finance/dashboard" -> {
                        hold?.await()
                        ok(
                            """{"totalRevenueMinor":100000000,"outstandingMinor":30000000,"overdueCount":1,"collectionRate":0.75,"currency":"UZS","doctorEarningsTop":[{"doctorProfileId":21,"collectedMinor":100000000,"currency":"UZS"}]}""",
                        )
                    }
                    path == "finance/appointment-ledger" && ledgerFails ->
                        respond(
                            """{"status":500,"code":"INTERNAL","requestId":"req-led"}""",
                            HttpStatusCode.InternalServerError,
                            json,
                        )
                    path == "finance/appointment-ledger" ->
                        ok(
                            """{"content":[
                              {"appointmentId":101,"startAt":"2026-11-04T05:00:00Z","patientName":"Dilnoza","doctorName":"Aziza","treatmentPlanId":501,"services":[{"title":"Root canal","lineTotalMinor":80000000}],"visitTotalMinor":80000000,"visitCollectedMinor":50000000,"currency":"UZS","planSimplePaymentStatus":"PARTIAL"},
                              {"appointmentId":102,"startAt":"2026-11-05T05:00:00Z","patientName":"Malika","doctorName":"Bobur","treatmentPlanId":501,"services":[],"visitTotalMinor":30000000,"visitCollectedMinor":30000000,"currency":"UZS","planSimplePaymentStatus":"PAID"},
                              {"broken":}
                            ],"totalElements":3,"number":0}""".replace("{\"broken\":}", "{\"appointmentId\":\"x\"}"),
                        )
                    path == "finance/installment-items" ->
                        ok(
                            """[{"id":802,"sequenceNumber":2,"amountMinor":40000000,"currency":"UZS","dueDate":"2026-10-30","status":"OVERDUE","patientName":"Dilnoza","treatmentPlanTitle":"Molar"}]""",
                        )
                    path == "finance/records" && req.method.value == "GET" ->
                        ok(
                            """{"content":[],"totalElements":0,"number":0}""",
                        )
                    path == "patients" ->
                        ok(
                            """{"content":[{"patientId":15,"fullName":"Gulnora"}],"totalElements":1,"last":true}""",
                        )
                    else -> ok("{}")
                }
            },
        )
    private val clinic = MyClinic(2, "Dev", "Asia/Tashkent", currency = "UZS", membershipRole = "OWNER")
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private fun opened() =
        ClinicFinanceViewModel(
            clinic,
            LocalDate(2026, 11, 6),
            FinanceRepository { client },
            ClinicsRepository({ client }, MapSettings(), { "u1" }),
            shifa.doctor.data.treatmentplans.TreatmentPlansRepository { client },
        ).also { created += it }

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
    fun `the month is the clinic's, a month in Tashkent runs from 19 00 UTC, all time sends no range`() {
        val (from, to) = FinanceMonth(2026, 11).range("Asia/Tashkent")
        assertThat(from).isEqualTo(Instant.parse("2026-10-31T19:00:00Z"))
        assertThat(to).isEqualTo(Instant.parse("2026-11-30T19:00:00Z"))
        val ny = FinanceMonth(2026, 3).range("America/New_York")
        assertThat(ny.first).isEqualTo(Instant.parse("2026-03-01T05:00:00Z"))
        assertThat(ny.second).isEqualTo(Instant.parse("2026-04-01T04:00:00Z")) // DST begins in March
        assertThat(FinanceMonth(2026, 1).previous()).isEqualTo(FinanceMonth(2025, 12))

        val vm = opened()
        settle { vm.state.value.dashboard != null }
        assertThat(calls.first { it.startsWith("GET finance/dashboard") })
            .isEqualTo("GET finance/dashboard?from=2026-10-31T19%3A00%3A00Z&to=2026-11-30T19%3A00%3A00Z")
        assertThat(vm.state.value.dashboard!!.collectionPercent).isEqualTo(75.0)
        vm.month(null)
        settle { calls.count { it.startsWith("GET finance/dashboard") } == 2 }
        assertThat(calls.last { it.startsWith("GET finance/dashboard") }).isEqualTo("GET finance/dashboard")
        // All time, Outstanding lists the plans with a balance (what the KPI counts); Overdue: installments and invoices.
        vm.kpi(FinanceKpi.Outstanding)
        settle { vm.state.value.balances != null }
        assertThat(vm.state.value.balances!!.map { it.planId }).containsExactly(501L)
        assertThat(calls.last()).isEqualTo("GET /api/treatment-plans?clinicId=2&planKind=COMPREHENSIVE")
        vm.kpi(FinanceKpi.Overdue)
        settle { vm.state.value.overdue != null }
        assertThat(vm.state.value.overdue!!.map { it.amountMinor }).containsExactly(40000000L, 9000000L).inOrder()
        assertThat(vm.state.value.overdue!![1].recordType).isEqualTo("INVOICE")
        // A month's Outstanding is that month's visits with a balance, from every page.
        vm.month(FinanceMonth(2026, 11))
        vm.kpi(FinanceKpi.Outstanding)
        settle { vm.state.value.balances?.firstOrNull()?.view == FinanceView.Ledger }
        assertThat(vm.state.value.balances!!.map { it.amountMinor }).containsExactly(30000000L)
    }

    @Test
    fun `the ledger pages on the server, chips and search filter it, a full payment records the visit's remaining amount`() {
        val vm = opened()
        vm.view(FinanceView.Ledger)
        settle { vm.state.value.ledger != null }
        // The unreadable row is skipped; the total is the backend's.
        assertThat(vm.state.value.ledger!!.map { it.appointmentId }).containsExactly(101L, 102L).inOrder()
        assertThat(vm.state.value.ledgerTotal).isEqualTo(3)
        vm.chip(2) // Partial
        assertThat(vm.state.value.shownLedger.single().appointmentId).isEqualTo(101L)
        vm.chip(0)
        vm.query("malika")
        assertThat(vm.state.value.shownLedger.single().appointmentId).isEqualTo(102L)
        vm.payFull(vm.state.value.ledger!![0], PayMethod.Transfer)
        settle { vm.state.value.banner != null }
        assertThat(calls.single { it.startsWith("POST finance/payments") })
            .isEqualTo(
                """POST finance/payments {"treatmentPlanId":501,"amountMinor":30000000,"currency":"UZS","method":"TRANSFER","linkedAppointmentId":101}""",
            )
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.clinicFinancePaymentRecorded)
        // A custom amount needs a valid one, then sends it with the memo.
        vm.openCustomPay(vm.state.value.ledger!![0])
        assertThat(vm.state.value.pay!!.amount).isEqualTo("300000")
        vm.editPay { it.copy(amount = "abc") }
        vm.savePay()
        assertThat(vm.state.value.pay!!.tried).isTrue()
        vm.editPay { it.copy(amount = "100 000", method = PayMethod.Card, memo = " desk ") }
        vm.savePay()
        settle { vm.state.value.pay == null }
        assertThat(
            calls.last {
                it.startsWith("POST finance/payments")
            },
        ).contains(
            "\"amountMinor\":10000000,\"currency\":\"UZS\",\"method\":\"CARD_EXTERNAL\",\"linkedAppointmentId\":101,\"memo\":\"desk\"",
        )
    }

    @Test
    fun `installments filter on the server, mark paid sends the method and memo, statuses and reminders`() {
        val vm = opened()
        vm.view(FinanceView.Installments)
        settle { vm.state.value.installments != null }
        assertThat(calls).contains("GET finance/installment-items?filter=all")
        vm.chip(2)
        settle { calls.last() == "GET finance/installment-items?filter=overdue" }
        val row = vm.state.value.installments!!.single()
        vm.openMarkPaid(row)
        vm.editMarkPaid { it.copy(method = PayMethod.Card, memo = "terminal 2") }
        vm.markPaid()
        settle { vm.state.value.markPaid == null }
        assertThat(
            calls.any {
                it == """POST finance/installment-items/802/mark-paid {"method":"CARD_EXTERNAL","memo":"terminal 2"}"""
            },
        ).isTrue()
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.clinicFinanceInstallStatusUpdated)
        // OVERDUE and PAID aren't set by hand.
        vm.setStatus(row, "OVERDUE")
        vm.setStatus(row, "WAIVED")
        settle { calls.any { it.startsWith("PATCH") } && !vm.state.value.busy }
        assertThat(
            calls.filter {
                it.startsWith("PATCH")
            },
        ).containsExactly("""PATCH finance/installment-items/802 {"status":"WAIVED"}""")
        vm.remind(row)
        settle { calls.any { it.startsWith("POST finance/installment-items/802/notify") } && !vm.state.value.busy }
    }

    @Test
    fun `records ask the server by status, a new record needs a patient and an amount`() {
        val vm = opened()
        vm.view(FinanceView.Records)
        settle { vm.state.value.records != null }
        vm.chip(5)
        settle { calls.last().startsWith("GET finance/records?status=VOIDED") }
        vm.openRecord()
        assertThat(vm.state.value.record!!.due).isEqualTo(LocalDate(2026, 11, 20))
        vm.saveRecord()
        assertThat(vm.state.value.record!!.tried).isTrue()
        assertThat(calls.none { it.startsWith("POST finance/records") }).isTrue()
        vm.searchPatient("Gul")
        main.scheduler.advanceTimeBy(ClinicFinanceViewModel.SEARCH_DEBOUNCE.inWholeMilliseconds + 1)
        main.scheduler.runCurrent()
        settle { vm.state.value.record!!.matches != null }
        assertThat(vm.state.value.record!!.matches!!.single().fullName).isEqualTo("Gulnora")
        vm.editRecord { it.copy(patient = ClinicPatient(15, "Gulnora"), amount = "250 000", notes = " visit ") }
        vm.saveRecord()
        settle { vm.state.value.record == null }
        assertThat(calls.single { it.startsWith("POST finance/records") })
            .isEqualTo(
                """POST finance/records {"patientId":15,"recordType":"INVOICE","subtotalMinor":25000000,"currency":"UZS","discountMinor":0,"taxMinor":0,"dueDate":"2026-11-20","notes":"visit"}""",
            )
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.clinicRecordsFormSuccess)
    }

    @Test
    fun `a failed drill-down says so with its request id instead of loading forever, Retry reads again`() {
        ledgerFails = true // dev 2026-11-06: the month-scoped ledger answers 500 (BE-37)
        val vm = opened()
        settle { vm.state.value.dashboard != null }
        vm.kpi(FinanceKpi.Outstanding)
        settle { vm.state.value.error != null }
        assertThat(vm.state.value.error!!.requestId).isEqualTo("req-led")
        assertThat(vm.state.value.balances).isNull()
        ledgerFails = false
        vm.load()
        settle { vm.state.value.balances != null }
        assertThat(vm.state.value.error).isNull()
    }

    @Test
    fun `an answer for an older view is dropped`() {
        hold = kotlinx.coroutines.CompletableDeferred()
        val vm = opened()
        vm.view(FinanceView.Earnings)
        hold!!.complete(Unit)
        settle { vm.state.value.earnings != null }
        Thread.sleep(100)
        assertThat(vm.state.value.dashboard).isNull()
        assertThat(vm.state.value.view).isEqualTo(FinanceView.Earnings)
    }

    @Test
    fun `csv quotes commas, quotes and line breaks, with a BOM and CRLF`() {
        val bytes = csv(listOf(listOf("a", "b,c"), listOf("say \"hi\"", "x\ny")))
        assertThat(bytes.take(3)).containsExactly(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()).inOrder()
        assertThat(
            bytes.drop(3).toByteArray().decodeToString(),
        ).isEqualTo("a,\"b,c\"\r\n\"say \"\"hi\"\"\",\"x\ny\"\r\n")
    }

    @Test
    fun `payments, installments and records are tracked by outcome, amounts only as ranges`() {
        val tracker = RecordingTracker()
        val vm =
            ClinicFinanceViewModel(
                clinic,
                LocalDate(2026, 11, 6),
                FinanceRepository { client },
                ClinicsRepository({ client }, MapSettings(), { "u1" }),
                shifa.doctor.data.treatmentplans.TreatmentPlansRepository { client },
                tracker,
            ).also { created += it }
        vm.view(FinanceView.Ledger)
        settle { vm.state.value.ledger != null }
        val visit = vm.state.value.ledger!![0]
        vm.payFull(visit, PayMethod.Transfer)
        settle { tracker.events.size == 1 }
        writesFail = true
        vm.payFull(visit, PayMethod.Cash)
        settle { tracker.events.size == 2 }
        writesFail = false
        vm.view(FinanceView.Installments)
        settle { vm.state.value.installments != null }
        val row = vm.state.value.installments!!.single()
        vm.remind(row)
        settle { tracker.events.size == 3 }
        vm.setStatus(row, "WAIVED")
        settle { tracker.events.size == 4 }
        vm.openRecord()
        vm.editRecord { it.copy(patient = ClinicPatient(15, "Gulnora"), amount = "250 000") }
        vm.saveRecord()
        settle { tracker.events.size == 5 }
        assertThat(tracker.events)
            .containsExactly(
                ClinicEvents.PaymentRecorded("ledger_full", "transfer", "100k-500k"),
                ClinicEvents.PaymentFailed("ledger_full", "cash", "server"),
                ClinicEvents.InstallmentReminderSent,
                ClinicEvents.InstallmentStatusChanged("waived", null, "100k-500k"),
                ClinicEvents.FinancialRecordCreated("invoice", hasPlan = false, amountBucket = "100k-500k"),
            ).inOrder()
    }
}
