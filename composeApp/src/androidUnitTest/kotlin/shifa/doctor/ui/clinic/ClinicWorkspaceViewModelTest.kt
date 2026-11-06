@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.ui.clinic

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicLoadFailed
import shifa.doctor.generated.resources.clinicStatsFailed

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicWorkspaceViewModelTest {
    private var clinics = HttpStatusCode.OK to """[{"clinicId":2,"name":"Shifa Dev Clinic","membershipRole":"OWNER"},{"clinicId":5,"name":"Second"}]"""
    private var statsOk = true
    private val statsFor = mutableListOf<String>()
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    path.endsWith("me/clinics") -> respond(clinics.second, clinics.first, json)
                    path.endsWith("overview-stats") -> {
                        statsFor += path
                        if (statsOk) {
                            respond(
                                """{"appointmentsToday":4,"activeDoctors":3,"patientsThisMonth":12}""",
                                HttpStatusCode.OK,
                                json,
                            )
                        } else {
                            respond("{}", HttpStatusCode.InternalServerError, json)
                        }
                    }
                    else -> respond("{}", HttpStatusCode.NotFound, json)
                }
            },
        )
    private val repo = ClinicsRepository({ client }, MapSettings(), { "u1" })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun settle(check: () -> Boolean) {
        repeat(300) {
            if (check()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the clinics and the selected one's numbers load, switching reads the other clinic's`() {
        val classToTest = ClinicWorkspaceViewModel(repo)
        classToTest.load()
        settle { classToTest.state.value.stats != null }
        assertThat(classToTest.state.value.clinic!!.clinicId).isEqualTo(2)
        assertThat(classToTest.state.value.stats!!.patientsThisMonth).isEqualTo(12)
        assertThat(statsFor.last()).endsWith("clinics/2/overview-stats")
        classToTest.switcher(true)
        statsOk = false
        classToTest.select(5)
        // The other clinic's numbers are gone at once, never shown under this clinic's name.
        settle { classToTest.state.value.statsError != null }
        assertThat(classToTest.state.value.stats).isNull()
        statsOk = true
        classToTest.readStats()
        settle { statsFor.last().endsWith("clinics/5/overview-stats") }
        assertThat(classToTest.state.value.clinic!!.clinicId).isEqualTo(5)
        assertThat(classToTest.state.value.switcher).isFalse()
        classToTest.section(ClinicSection.Finance)
        assertThat(classToTest.state.value.section).isEqualTo(ClinicSection.Finance)
    }

    @Test
    fun `no list yet and a failure is the error state, failing numbers leave the rest`() {
        clinics = HttpStatusCode.InternalServerError to "{}"
        val classToTest = ClinicWorkspaceViewModel(repo)
        classToTest.load()
        settle { classToTest.state.value.error != null }
        assertThat(classToTest.state.value.error!!.text).isEqualTo(Res.string.clinicLoadFailed)

        clinics = HttpStatusCode.OK to """[{"clinicId":2,"name":"Shifa Dev Clinic"}]"""
        statsOk = false
        classToTest.load()
        settle { classToTest.state.value.statsError != null }
        assertThat(classToTest.state.value.error).isNull()
        assertThat(classToTest.state.value.statsError!!.text).isEqualTo(Res.string.clinicStatsFailed)
        assertThat(classToTest.state.value.clinic).isNotNull()
    }

    @Test
    fun `staff don't get invitations, sections fall in the board's groups`() {
        assertThat(clinicGroups(staff = true).flatMap { it.second }).doesNotContain(ClinicSection.Invitations)
        assertThat(clinicGroups(staff = false).flatMap { it.second })
            .containsExactly(
                ClinicSection.Overview,
                ClinicSection.Calendar,
                ClinicSection.Doctors,
                ClinicSection.Invitations,
                ClinicSection.Patients,
                ClinicSection.Plans,
                ClinicSection.Finance,
                ClinicSection.Services,
            ).inOrder()
    }
}
