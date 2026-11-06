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
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.finance.FinanceRepository
import shifa.doctor.data.model.ClinicPatient
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class PlanWizardViewModelTest {
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var linesFail = false
    private var searchFail = false

    private val services =
        """[{"key":"c1","kind":"CATALOG","catalogItemId":1,"title":"Root canal","defaultPriceMinor":80000000,"currency":"UZS","offeredByDoctorIds":[21,22]},""" +
            """{"key":"c2","kind":"CATALOG","catalogItemId":2,"title":"Crown","defaultPriceMinor":150000000,"currency":"UZS","offeredByDoctorIds":[22]},""" +
            """{"key":"c3","kind":"CATALOG","catalogItemId":3,"title":"Whitening","defaultPriceMinor":5000,"currency":"USD","offeredByDoctorIds":[]},""" +
            """{"key":"c4","kind":"CATALOG","catalogItemId":4,"title":"Old","defaultPriceMinor":100,"currency":"UZS","active":false}]"""
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
                    path == "clinics/2/members" ->
                        ok(
                            """[{"doctorProfileId":22,"displayName":"Bobur"},{"doctorProfileId":21,"displayName":"Aziza"},{"userId":5,"displayName":"Desk","membershipRole":"RECEPTIONIST"}]""",
                        )
                    path == "treatment-plans/plan-services" -> ok(services)
                    path == "clinics/2/patients" && searchFail ->
                        respond("""{"status":500,"code":"INTERNAL_ERROR"}""", HttpStatusCode.InternalServerError, json)
                    path == "clinics/2/patients" ->
                        ok(
                            """{"content":[{"patientId":12,"fullName":"Jasur","phone":"+998901234567"}],"totalElements":1,"last":true}""",
                        )
                    path == "treatment-plans" ->
                        ok(
                            """{"id":601,"clinicId":2,"patientId":12,"title":"Molars","status":"DRAFT"}""",
                        )
                    path == "treatment-plans/601/lines" && linesFail ->
                        respond("""{"status":500,"code":"INTERNAL_ERROR"}""", HttpStatusCode.InternalServerError, json)
                    else -> ok("""{"id":601}""")
                }
            },
        )
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    // The search pause runs on the test clock.
    private fun pause() {
        main.scheduler.advanceTimeBy(PlanWizardViewModel.SEARCH_DEBOUNCE.inWholeMilliseconds + 1)
        main.scheduler.runCurrent()
    }

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

    private fun wizard(patient: ClinicPatient? = null) =
        PlanWizardViewModel(
            2,
            patient,
            "UZS",
            LocalDate(2026, 11, 6),
            ownDoctorId = 21,
            initialMemo = "Initial payment at desk",
            plans = TreatmentPlansRepository { client },
            clinics = ClinicsRepository({ client }, com.russhwolf.settings.MapSettings(), { "u1" }),
            finance = FinanceRepository { client },
        ).also { created += it }

    private fun posts() = calls.filter { !it.startsWith("GET") }

    @Test
    fun `each step checks its fields, the save runs the requests in order with the schedule of the balance`() {
        val vm = wizard()
        settle { vm.state.value.doctors != null && vm.state.value.services != null }
        val s0 = vm.state.value
        // Doctors only (the receptionist is not one), by name; the signed-in doctor starts on the team.
        assertThat(s0.doctors!!.map { it.displayName }).containsExactly("Aziza", "Bobur").inOrder()
        assertThat(s0.picked).containsExactly(21L)
        // Step 1: no patient, no Next; 2 letters search after the pause.
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(1)
        vm.search("j")
        pause()
        assertThat(calls.none { it.startsWith("GET clinics/2/patients") }).isTrue()
        vm.search("ja")
        pause()
        settle { vm.state.value.matches != null }
        assertThat(calls.single { it.startsWith("GET clinics/2/patients") }).contains("q=ja")
        vm.pickPatient(vm.state.value.matches!!.single())
        assertThat(vm.state.value.step).isEqualTo(2)
        // Step 2: the title is required.
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(2)
        assertThat(vm.state.value.tried).contains(2)
        vm.basics { it.copy(title = " Molars ", diagnosis = "K04.0", reminderDays = 7) }
        vm.next()
        // Step 3: Aziza (21) offers the root canal only; the inactive one never shows; USD needs no doctor.
        assertThat(vm.state.value.offered.map { it.key }).containsExactly("c1", "c3").inOrder()
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(3)
        vm.toggleDoctor(22)
        assertThat(vm.state.value.offered.map { it.key }).containsExactly("c1", "c2", "c3").inOrder()
        vm.tooth("19")
        vm.addLine()
        assertThat(vm.state.value.lines).isEmpty()
        assertThat(vm.state.value.toothTried).isTrue()
        vm.tooth("3a6")
        assertThat(vm.state.value.tooth).isEqualTo("36")
        vm.addLine()
        vm.pickService("c2")
        vm.tooth("85")
        vm.addLine()
        // Once the plan has a currency, only its services are offered (one currency per plan).
        assertThat(vm.state.value.offered.map { it.key }).containsExactly("c1", "c2").inOrder()
        vm.pickService("c1")
        vm.addLine()
        vm.removeLine(2)
        assertThat(
            vm.state.value.lines.map {
                it.service.key to it.tooth
            },
        ).containsExactly("c1" to "36", "c2" to "85").inOrder()
        assertThat(vm.state.value.total).isEqualTo(230_000_000)
        vm.next()
        // Step 4: the initial payment is at most the total; the schedule must cover the balance exactly.
        vm.payment { it.copy(initial = "3 000 000") }
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(4)
        vm.payment { it.copy(initial = "300 000", method = PayMethod.Transfer) }
        assertThat(vm.state.value.remaining).isEqualTo(200_000_000)
        vm.useInstallments(true)
        val rows = vm.state.value.installments
        assertThat(
            rows.map {
                it.due.toString() to it.amount
            },
        ).containsExactly("2026-12-06" to "1000000", "2027-01-05" to "1000000").inOrder()
        vm.editInstallment(1) { it.copy(amount = "900 000") }
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(4)
        vm.addInstallment()
        assertThat(vm.state.value.installments.last().amount).isEqualTo("100000")
        // Entered out of order: the schedule is sent by due date.
        vm.editInstallment(2) { it.copy(due = LocalDate(2026, 11, 20)) }
        vm.removeInstallment(0)
        vm.removeInstallment(0)
        assertThat(vm.state.value.installments).hasSize(2)
        vm.editInstallment(0) { it.copy(amount = "1 900 000") }
        vm.next()
        assertThat(vm.state.value.step).isEqualTo(5)
        vm.back()
        assertThat(vm.state.value.step).isEqualTo(4)
        vm.next()
        vm.next()
        settle { vm.state.value.saved }
        assertThat(posts())
            .containsExactly(
                """POST treatment-plans {"clinicId":2,"patientId":12,"title":"Molars","diagnosis":"K04.0","paymentReminderDays":7,"attendingDoctorId":21,"attendingDoctorIds":[21,22]}""",
                """POST treatment-plans/601/lines [{"catalogItemId":1,"title":"Root canal","quantity":1,"unitPriceMinor":80000000,"discountMinor":0,"currency":"UZS","sortOrder":0,"status":"PLANNED","specialtyMetadata":"{\"fdi\":\"36\",\"dentition\":\"permanent\",\"source\":\"PLAN_WIZARD\"}"},{"catalogItemId":2,"title":"Crown","quantity":1,"unitPriceMinor":150000000,"discountMinor":0,"currency":"UZS","sortOrder":1,"status":"PLANNED","specialtyMetadata":"{\"fdi\":\"85\",\"dentition\":\"primary\",\"source\":\"PLAN_WIZARD\"}"}]""",
                """POST clinics/2/finance/payments {"treatmentPlanId":601,"amountMinor":30000000,"currency":"UZS","method":"TRANSFER","memo":"Initial payment at desk"}""",
                """POST clinics/2/finance/installment-plans {"treatmentPlanId":601,"totalAmountMinor":200000000,"currency":"UZS","numInstallments":2,"startDate":"2026-11-20","scheduleItems":[{"dueDate":"2026-11-20","amountMinor":10000000},{"dueDate":"2027-01-05","amountMinor":190000000}],"frequency":"MONTHLY"}""",
                """PATCH treatment-plans/601/status {"status":"ACTIVE"}""",
            ).inOrder()
        assertThat(vm.state.value.planId).isEqualTo(601L)
    }

    @Test
    fun `a failed request keeps the draft, retry goes on from it, a general line has no tooth, a draft stays one`() {
        linesFail = true
        val vm = wizard(ClinicPatient(12, "Jasur"))
        settle { vm.state.value.doctors != null && vm.state.value.services != null }
        // From the patient's record: step 2 first, no way back to the patient.
        assertThat(vm.state.value.step).isEqualTo(2)
        vm.back()
        assertThat(vm.state.value.step).isEqualTo(2)
        vm.basics { it.copy(title = "Canal") }
        vm.next()
        vm.addLine()
        vm.next()
        // No payment: nothing to split, the switch does nothing.
        vm.next()
        vm.payment { it.copy(activate = false) }
        vm.next()
        settle { vm.state.value.error != null }
        assertThat(vm.state.value.planId).isEqualTo(601L)
        assertThat(vm.state.value.done).containsExactly(SaveStep.Plan)
        assertThat(vm.state.value.busy).isFalse()
        // Nothing can be changed now, and closing doesn't ask: the draft is kept.
        vm.basics { it.copy(title = "Other") }
        assertThat(vm.state.value.title).isEqualTo("Canal")
        assertThat(vm.close()).isTrue()
        linesFail = false
        vm.next()
        settle { vm.state.value.saved }
        assertThat(posts().map { it.substringBefore(" {").substringBefore(" [") })
            .containsExactly("POST treatment-plans", "POST treatment-plans/601/lines", "POST treatment-plans/601/lines")
            .inOrder()
        assertThat(posts().last()).isEqualTo(
            """POST treatment-plans/601/lines [{"catalogItemId":1,"title":"Root canal","quantity":1,"unitPriceMinor":80000000,"discountMinor":0,"currency":"UZS","sortOrder":0,"status":"PLANNED"}]""",
        )
    }

    @Test
    fun `a doctor off the team takes their own services, the default split stays in whole sums, a failed search says so`() {
        val vm = wizard(ClinicPatient(12, "Jasur"))
        settle { vm.state.value.doctors != null && vm.state.value.services != null }
        vm.toggleDoctor(22)
        vm.pickService("c2")
        vm.addLine()
        vm.pickService("c1")
        vm.addLine()
        vm.toggleDoctor(22)
        assertThat(vm.state.value.lines.map { it.service.key }).containsExactly("c1")
        // Nobody picked: the lines stay (Next asks for a doctor).
        vm.toggleDoctor(21)
        assertThat(vm.state.value.lines).hasSize(1)
        vm.toggleDoctor(21)
        // 800 000 less 1 UZS: 799 999 split as 400 000 + 399 999, adding up.
        vm.payment { it.copy(initial = "1") }
        vm.useInstallments(true)
        assertThat(vm.state.value.installments.map { it.amount }).containsExactly("400000", "399999").inOrder()
        assertThat(vm.state.value.scheduleValid).isTrue()

        searchFail = true
        val other = wizard()
        other.search("ja")
        pause()
        settle { other.state.value.searchError != null }
        assertThat(other.state.value.matches).isNull()
        searchFail = false
        other.search("jas")
        assertThat(other.state.value.searchError).isNull()
    }

    @Test
    fun `closing asks once something was entered, a search alone just closes`() {
        val vm = wizard()
        settle { vm.state.value.doctors != null }
        vm.search("ja")
        assertThat(vm.close()).isTrue()
        val other = wizard(ClinicPatient(12, "Jasur"))
        assertThat(other.close()).isTrue()
        other.basics { it.copy(title = "x") }
        assertThat(other.close()).isFalse()
        assertThat(other.state.value.askDiscard).isTrue()
        other.keepEditing()
        assertThat(other.state.value.askDiscard).isFalse()
    }
}
