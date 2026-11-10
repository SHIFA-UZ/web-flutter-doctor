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
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.clinics.ClinicsRepository
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.ClinicEvents
import shifa.doctor.data.treatmentplans.TreatmentPlansRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicServiceAdded
import shifa.doctor.generated.resources.clinicServiceDeactivated
import shifa.doctor.generated.resources.serviceSaved
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicServicesViewModelTest {
    private val writes = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var catalogFails = false
    private var servicesFail = false
    private var reads = 0
    private var writesFail = false
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/")
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                if (writesFail && req.method.value != "GET") {
                    return@MockEngine respond("""{"status":500}""", HttpStatusCode.InternalServerError, json)
                }
                if (req.method.value != "GET") {
                    writes += "${req.method.value} $path ${req.body.toByteArray().decodeToString()}"
                    return@MockEngine respond("""{"id":7,"title":"x"}""", HttpStatusCode.OK, json)
                }
                when (path) {
                    "treatment-plans/plan-services" -> {
                        reads++
                        if (servicesFail) {
                            respond("""{"status":500}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond(
                                """[{"key":"catalog:1","kind":"CLINIC_CATALOG","catalogItemId":1,"title":"Root canal","code":"D3310","defaultPriceMinor":80000000,"currency":"UZS","active":true,"offeredByDoctorIds":[21,22],"offeredByDoctorNames":["Aziza Karimova","Bobur Aliyev"]},
                                   {"key":"catalog:2","kind":"CLINIC_CATALOG","catalogItemId":2,"title":"Ceramic crown","defaultPriceMinor":150050,"currency":"UZS","active":false,"offeredByDoctorIds":[21],"offeredByDoctorNames":["Aziza Karimova"]},
                                   {"key":"doctor:22:service:3","kind":"DOCTOR_SERVICE","doctorServiceId":3,"title":"Cleaning","defaultPriceMinor":2550,"currency":"USD","active":true,"offeredByDoctorIds":[22],"offeredByDoctorNames":["Bobur Aliyev"]}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        }
                    }
                    "treatment-plans/catalog-items" ->
                        if (catalogFails) {
                            respond("""{"status":500}""", HttpStatusCode.InternalServerError, json)
                        } else {
                            respond(
                                """[{"id":1,"clinicId":2,"code":"D3310","title":"Root canal","defaultPriceMinor":80000000,"currency":"UZS","active":true,"appliesToAllDoctors":true,"assignedDoctorProfileIds":[]},
                                   {"id":2,"clinicId":2,"title":"Ceramic crown","defaultPriceMinor":150050,"currency":"UZS","active":false,"appliesToAllDoctors":false,"assignedDoctorProfileIds":[21]}]""",
                                HttpStatusCode.OK,
                                json,
                            )
                        }
                    "clinics/2/members" ->
                        respond(
                            """[{"doctorProfileId":22,"displayName":"Bobur Aliyev","membershipRole":"DOCTOR"},{"doctorProfileId":21,"displayName":"Aziza Karimova","membershipRole":"OWNER"},{"displayName":"Dilfuza","membershipRole":"RECEPTIONIST"}]""",
                            HttpStatusCode.OK,
                            json,
                        )
                    else -> respond("[]", HttpStatusCode.OK, json)
                }
            },
        )
    private val classToTest by lazy {
        ClinicServicesViewModel(
            2,
            "UZS",
            TreatmentPlansRepository {
                client
            },
            ClinicsRepository({ client }, MapSettings(), { "u1" }),
        ).also { created += it }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    // Answers come back on the engine's threads: stop the ViewModels before the next test sets Main.
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

    private fun loaded() =
        classToTest.also { vm ->
            settle {
                vm.state.value.services != null && vm.state.value.catalog != null
            }
        }

    @Test
    fun `both lists load, status chips and search filter (doctor names, code, number), sorted by title`() {
        val vm = loaded()
        assertThat(
            vm.state.value.shown().map { it.title },
        ).containsExactly("Ceramic crown", "Cleaning", "Root canal").inOrder()
        vm.status(ServiceStatusFilter.Inactive)
        assertThat(vm.state.value.shown().single().title).isEqualTo("Ceramic crown")
        vm.status(ServiceStatusFilter.Active)
        vm.query("bobur")
        assertThat(vm.state.value.shown().map { it.title }).containsExactly("Cleaning", "Root canal").inOrder()
        vm.query("d3310")
        assertThat(vm.state.value.shown().single().title).isEqualTo("Root canal")
        vm.clearFilters()
        assertThat(vm.state.value.shown()).hasSize(3)
    }

    @Test
    fun `a new service is checked first, then sent with the clinic's currency, upper-cased code and the doctors`() {
        val vm = loaded()
        vm.openAdd()
        settle { vm.state.value.doctors != null }
        // Doctors only (no receptionist), by name.
        assertThat(vm.state.value.doctors!!.map { it.doctorProfileId }).containsExactly(21L, 22L).inOrder()
        vm.save()
        assertThat(vm.state.value.editor!!.tried).isTrue()
        vm.edit { it.copy(title = " Implant ", code = "d3310", price = "0", all = false) }
        assertThat(vm.state.value.codeTaken(vm.state.value.editor!!)).isTrue()
        vm.save()
        vm.edit { it.copy(code = "imp1", price = "1 500 000") }
        vm.save()
        assertThat(writes).isEmpty() // no doctor picked yet
        vm.edit { it.copy(doctorIds = setOf(22L)) }
        vm.save()
        settle { vm.state.value.editor == null }
        assertThat(writes.single()).isEqualTo(
            """POST treatment-plans/catalog-items {"clinicId":2,"code":"IMP1","title":"Implant","defaultPriceMinor":150000000,"currency":"UZS","active":true,"appliesToAllDoctors":false,"assignedDoctorProfileIds":[22],"sortOrder":0}""",
        )
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.clinicServiceAdded)
    }

    @Test
    fun `editing starts from the catalogue item, clears the code with an empty one, and switches to all doctors`() {
        val vm = loaded()
        val crown = vm.state.value.services!!.first { it.catalogItemId == 2L }
        vm.openEdit(crown)
        val e = vm.state.value.editor!!
        // 1 500.50 sums show as the whole 1 501 but are kept as stored while untouched; the doctors as assigned.
        assertThat(e.price).isEqualTo("1501")
        assertThat(e.all).isFalse()
        assertThat(e.doctorIds).containsExactly(21L)
        vm.edit { it.copy(doctorIds = setOf(21L, 22L)) }
        vm.save()
        settle { vm.state.value.editor == null }
        assertThat(writes.last()).isEqualTo(
            """PATCH treatment-plans/catalog-items/2 {"code":"","title":"Ceramic crown","defaultPriceMinor":150050,"currency":"UZS","active":false,"assignedDoctorProfileIds":[21,22]}""",
        )
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.serviceSaved)
        vm.openEdit(vm.state.value.services!!.first { it.catalogItemId == 1L })
        vm.edit { it.copy(all = true) }
        vm.save()
        settle { writes.size == 2 }
        assertThat(writes.last()).contains("\"code\":\"D3310\"")
        assertThat(writes.last()).contains("\"appliesToAllDoctors\":true")
        assertThat(writes.last()).doesNotContain("assignedDoctorProfileIds")
    }

    @Test
    fun `a clinic row deactivates at once, a doctor's own row has no menu, closing with changes asks first`() {
        val vm = loaded()
        val before = reads
        vm.toggle(vm.state.value.services!!.first { it.catalogItemId == 1L })
        settle { vm.state.value.banner != null }
        assertThat(writes.single()).isEqualTo("""PATCH treatment-plans/catalog-items/1 {"active":false}""")
        assertThat(vm.state.value.banner!!.text).isEqualTo(Res.string.clinicServiceDeactivated)
        settle { reads > before }
        // A doctor's service isn't edited here.
        vm.openEdit(vm.state.value.services!!.first { !it.isCatalog })
        assertThat(vm.state.value.editor).isNull()
        vm.openAdd()
        vm.close()
        assertThat(vm.state.value.editor).isNull()
        vm.openAdd()
        vm.edit { it.copy(title = "x") }
        vm.close()
        assertThat(vm.state.value.editor!!.askDiscard).isTrue()
        vm.keepEditing()
        assertThat(vm.state.value.editor!!.askDiscard).isFalse()
        vm.close()
        vm.discard()
        assertThat(vm.state.value.editor).isNull()
    }

    @Test
    fun `without the catalogue clinic rows can't be edited, a failed reload keeps the rows`() {
        catalogFails = true
        val vm = classToTest
        settle { vm.state.value.services != null }
        settle { vm.state.value.catalogFailed }
        vm.openEdit(vm.state.value.services!!.first { it.isCatalog })
        assertThat(vm.state.value.editor).isNull()
        servicesFail = true
        vm.load()
        settle { vm.state.value.error != null }
        assertThat(vm.state.value.services).hasSize(3)
        assertThat(priceText(2550, "USD")).isEqualTo("25.50")
        assertThat(priceText(2500, "USD")).isEqualTo("25")
    }

    @Test
    fun `a catalogue that read before but failed now can't be edited from, prices may be 0 and follow the currency`() {
        val vm = loaded()
        val canal = vm.state.value.services!!.first { it.catalogItemId == 1L }
        assertThat(vm.state.value.item(canal)).isNotNull()
        catalogFails = true
        vm.load()
        settle { vm.state.value.catalogFailed }
        // The kept catalogue may be out of date: no edit from it.
        assertThat(vm.state.value.item(canal)).isNull()
        vm.openEdit(canal)
        assertThat(vm.state.value.editor).isNull()
        // A free service is allowed; an empty or unreadable price isn't; UZS takes whole sums.
        assertThat(ServiceEditor(price = "0").priceMinor).isEqualTo(0L)
        assertThat(ServiceEditor(price = "").priceMinor).isNull()
        assertThat(ServiceEditor(price = "1500.50").priceMinor).isNull()
        assertThat(ServiceEditor(price = "1500.50", currency = "USD").priceMinor).isEqualTo(150050L)
        // The stored price stands only while both the field and the currency are untouched.
        val kept = ServiceEditor(price = "1501", currency = "UZS", storedMinor = 150050, storedCurrency = "UZS")
        assertThat(kept.priceMinor).isEqualTo(150050L)
        assertThat(kept.copy(currency = "USD").priceMinor).isEqualTo(150100L)
    }

    @Test
    fun `saves, toggles and a discard are tracked by their outcome`() {
        val tracker = RecordingTracker()
        val vm =
            ClinicServicesViewModel(
                2,
                "UZS",
                TreatmentPlansRepository { client },
                ClinicsRepository({ client }, MapSettings(), { "u1" }),
                tracker,
            ).also { created += it }
        settle { vm.state.value.services != null && vm.state.value.catalog != null }
        vm.openAdd()
        settle { vm.state.value.doctors != null }
        vm.save()
        vm.edit { it.copy(title = "Implant", code = "d3310", price = "0") }
        vm.save()
        vm.edit { it.copy(code = "imp1") }
        vm.save()
        settle { vm.state.value.editor == null }
        val root = vm.state.value.services!!.first { it.catalogItemId == 1L }
        writesFail = true
        vm.toggle(root)
        settle { tracker.events.size == 4 && !vm.state.value.toggling }
        writesFail = false
        vm.toggle(root)
        settle { tracker.events.size == 5 }
        vm.openAdd()
        vm.edit { it.copy(title = "x") }
        vm.close()
        vm.discard()
        assertThat(tracker.events)
            .containsExactly(
                ClinicEvents.ServiceSaveFailed(isNew = true, reason = "invalid"),
                ClinicEvents.ServiceSaveFailed(isNew = true, reason = "code_taken"),
                ClinicEvents.ServiceSaved(isNew = true, isAllDoctors = true),
                ClinicEvents.ServiceToggleFailed("server"),
                ClinicEvents.ServiceToggled(isActive = false),
                ClinicEvents.ServiceEditDiscarded,
            ).inOrder()
    }
}
