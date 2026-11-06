@file:Suppress("ktlint:standard:max-line-length")

package shifa.doctor.data.clinics

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.model.MembershipRole
import shifa.doctor.data.model.MyClinic
import shifa.doctor.data.model.mayEditRevenueShare
import shifa.doctor.data.model.mayManageFinance
import shifa.doctor.data.model.mayManageFinanceSettings
import shifa.doctor.data.model.role

class ClinicsRepositoryTest {
    private var body = """[{"clinicId":1,"name":"Shifa Demo Clinic","membershipRole":"DOCTOR"},{"clinicId":2,"name":"Second","membershipRole":"OWNER"}]"""
    private var status = HttpStatusCode.OK
    private val client =
        createHttpClient(
            { null },
            MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) },
        )
    private val settings = MapSettings()
    private var user: String? = "u1"

    private fun repo() = ClinicsRepository({ client }, settings, { user })

    @Test
    fun `the first clinic by default, a chosen one remembered per user across restarts`() =
        runTest {
            val classToTest = repo()
            assertThat(classToTest.state.value.known).isFalse()
            assertThat(classToTest.selected().getOrThrow()!!.clinicId).isEqualTo(1)
            classToTest.select(2)
            assertThat(classToTest.state.value.selected!!.clinicId).isEqualTo(2)
            // A restart: a new repository reads the choice back.
            assertThat(repo().selected().getOrThrow()!!.clinicId).isEqualTo(2)
            // Another user starts at the first.
            user = "u2"
            assertThat(repo().selected().getOrThrow()!!.clinicId).isEqualTo(1)
            // A clinic one isn't a member of can't be chosen.
            classToTest.select(99)
            assertThat(classToTest.state.value.selected!!.clinicId).isEqualTo(2)
        }

    @Test
    fun `a clinic the user left falls back to the first, a failed read keeps the list`() =
        runTest {
            val classToTest = repo()
            classToTest.mine()
            classToTest.select(2)
            body = """[{"clinicId":1,"name":"Shifa Demo Clinic"}]"""
            classToTest.mine()
            assertThat(classToTest.state.value.selected!!.clinicId).isEqualTo(1)
            status = HttpStatusCode.InternalServerError
            assertThat(classToTest.mine().isFailure).isTrue()
            assertThat(classToTest.state.value.hasWorkspace).isTrue()
            assertThat(classToTest.state.value.known).isTrue()
        }

    @Test
    fun `one permission helper for the clinic's roles`() {
        fun clinic(
            role: String?,
            flag: Boolean = false,
        ) = MyClinic(1, membershipRole = role, canManageFinanceSettings = flag)
        assertThat(clinic("clinic_admin").role).isEqualTo(MembershipRole.ClinicAdmin)
        assertThat(clinic("SOMETHING_NEW").role).isEqualTo(MembershipRole.Unknown)
        assertThat(clinic("OWNER").mayManageFinanceSettings).isTrue()
        assertThat(clinic("DOCTOR").mayManageFinanceSettings).isFalse()
        assertThat(clinic("DOCTOR", flag = true).mayManageFinanceSettings).isTrue()
        assertThat(clinic("RECEPTIONIST").mayManageFinance).isTrue()
        assertThat(clinic("NURSE").mayManageFinance).isFalse()
        assertThat(clinic(null).mayManageFinance).isFalse()
        assertThat(mayEditRevenueShare(MembershipRole.Doctor)).isTrue()
        assertThat(mayEditRevenueShare(MembershipRole.Receptionist)).isFalse()
    }

    @Test
    fun `another sign-in never sees the previous account's clinics, a late answer is dropped`() =
        runTest {
            val classToTest = repo()
            classToTest.mine()
            assertThat(classToTest.state.value.hasWorkspace).isTrue()
            user = "u2"
            assertThat(classToTest.isCurrent).isFalse()
            status = HttpStatusCode.InternalServerError
            classToTest.mine()
            // The failed read for u2 shows nothing of u1's.
            assertThat(classToTest.state.value.hasWorkspace).isFalse()
            assertThat(classToTest.state.value.known).isFalse()
            // An answer that arrives for an earlier user is dropped.
            status = HttpStatusCode.OK
            val late =
                ClinicsRepository(
                    {
                        createHttpClient(
                            { null },
                            MockEngine {
                                user = "u3"
                                respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                            },
                        )
                    },
                    settings,
                    { user },
                )
            user = "u2"
            late.mine()
            assertThat(late.state.value.known).isFalse()
        }
}
