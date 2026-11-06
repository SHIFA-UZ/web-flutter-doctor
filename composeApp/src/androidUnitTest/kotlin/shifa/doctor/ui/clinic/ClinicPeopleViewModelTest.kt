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
import shifa.doctor.data.model.MembershipRole
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.clinicDefaultShareCleared
import shifa.doctor.generated.resources.clinicInviteInviteSent
import shifa.doctor.generated.resources.clinicShareFailed
import shifa.doctor.generated.resources.clinicShareSaved
import shifa.doctor.generated.resources.clinicShareUsesDefault

@OptIn(ExperimentalCoroutinesApi::class)
class ClinicPeopleViewModelTest {
    private val calls = mutableListOf<Pair<String, String>>()
    private var shareStatus = HttpStatusCode.OK
    private val statuses = mutableListOf<String>()
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val path = req.url.encodedPath.removePrefix("/api/")
                calls += "${req.method.value} $path" to req.body.toByteArray().decodeToString()
                req.url.parameters["status"]?.let { statuses += it }
                val json = headersOf(HttpHeaders.ContentType, "application/json")
                when {
                    path == "clinics/2/members" ->
                        respond(
                            """[{"doctorProfileId":21,"displayName":"Aziza Karimova","membershipRole":"OWNER","effectiveRevenueSharePercent":60},{"doctorProfileId":22,"displayName":"Bobur Aliyev","membershipRole":"DOCTOR","doctorRevenueSharePercent":70,"effectiveRevenueSharePercent":70},{"displayName":"Dilfuza","membershipRole":"RECEPTIONIST"}]""",
                            HttpStatusCode.OK,
                            json,
                        )
                    path.endsWith(
                        "revenue-share",
                    ) || path.endsWith("finance-settings") -> respond("{}", shareStatus, json)
                    path == "clinics/2/invitations" && req.method.value == "GET" ->
                        respond(
                            """[{"id":5,"emailSentTo":"anna@clinic.uz","pending":true,"expiresAt":"2999-01-01T00:00:00Z"}]""",
                            HttpStatusCode.OK,
                            json,
                        )
                    path.startsWith("clinics/2/invitations") -> respond("{}", HttpStatusCode.OK, json)
                    path == "me/clinics" -> respond("[]", HttpStatusCode.OK, json)
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
    fun `members load, search and role chips filter, sorted by name`() {
        val classToTest = ClinicPeopleViewModel(2, repo)
        classToTest.loadMembers()
        settle { classToTest.state.value.members != null }
        val state = classToTest.state.value
        assertThat(
            state.roles,
        ).containsExactly(MembershipRole.Owner, MembershipRole.Receptionist, MembershipRole.Doctor).inOrder()
        assertThat(
            state.shown {
                it.name
            }.map { it.displayName },
        ).containsExactly("Aziza Karimova", "Bobur Aliyev", "Dilfuza").inOrder()
        classToTest.role(MembershipRole.Doctor)
        assertThat(classToTest.state.value.shown { it.name }.single().displayName).isEqualTo("Bobur Aliyev")
        classToTest.role(null)
        classToTest.query("recep")
        assertThat(
            classToTest.state.value.shown {
                if (it == MembershipRole.Receptionist) "Receptionist" else it.name
            }.single().displayName,
        ).isEqualTo("Dilfuza")
    }

    @Test
    fun `a doctor's share saves, clears back to the default, the clinic default re-reads the clinics, a failure stays in the sheet`() {
        val classToTest = ClinicPeopleViewModel(2, repo)
        classToTest.loadMembers()
        settle { classToTest.state.value.members != null }
        val bobur = classToTest.state.value.members!![1]
        // A doctor on the clinic default sees its split while their own field stays empty.
        classToTest.openShare(classToTest.state.value.members!![0], clinicDefault = 60)
        assertThat(classToTest.state.value.share!!.text).isEmpty()
        assertThat(classToTest.state.value.share!!.preview).isEqualTo(60)
        classToTest.closeShare()
        classToTest.openShare(bobur, clinicDefault = 60)
        assertThat(classToTest.state.value.share!!.text).isEqualTo("70")
        // Kept as typed and refused: never turned into 105, 5 or an empty (clearing) share.
        for (bad in listOf("1x05", "-5", "10.5", "abc", "101")) {
            classToTest.shareText(bad)
            assertThat(classToTest.state.value.share!!.text).isEqualTo(bad)
            assertThat(classToTest.state.value.share!!.valid).isFalse()
        }
        classToTest.shareText("")
        assertThat(classToTest.state.value.share!!.showError).isFalse()
        assertThat(classToTest.state.value.share!!.valid).isFalse()
        classToTest.saveShare()
        assertThat(calls.none { it.first.startsWith("PATCH") }).isTrue()
        classToTest.shareText("55")
        classToTest.saveShare()
        settle { classToTest.state.value.share == null }
        assertThat(
            calls.last {
                it.first.startsWith("PATCH")
            },
        ).isEqualTo("PATCH clinics/2/members/22/revenue-share" to """{"doctorRevenueSharePercent":55}""")
        assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.clinicShareSaved)

        classToTest.openShare(bobur, 60)
        classToTest.saveShare(clear = true)
        settle { classToTest.state.value.share == null }
        // No percent: the backend reads the missing field as null (back to the clinic default).
        assertThat(calls.last { it.first.startsWith("PATCH") }.second).isEqualTo("{}")
        assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.clinicShareUsesDefault)

        classToTest.openShare(null, 60)
        classToTest.saveShare(clear = true)
        settle { calls.any { it.first == "GET me/clinics" } }
        assertThat(calls.any { it.first == "PATCH clinics/2/finance-settings" }).isTrue()
        assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.clinicDefaultShareCleared)

        shareStatus = HttpStatusCode.Forbidden
        classToTest.openShare(null, 60)
        classToTest.saveShare()
        settle { classToTest.state.value.share?.error != null }
        assertThat(classToTest.state.value.share!!.busy).isFalse()
        assertThat(classToTest.state.value.share!!.error).isNotNull()
        assertThat(Res.string.clinicShareFailed).isNotNull()
    }

    @Test
    fun `an invitation needs a valid new email, is sent lower-cased, revoke asks then deletes`() {
        val classToTest = ClinicPeopleViewModel(2, repo)
        classToTest.loadInvitations()
        settle { classToTest.state.value.invitations != null }
        // Every invitation, not only the live ones (the backend's default).
        assertThat(statuses).contains("all")
        classToTest.openInvite()
        classToTest.inviteEmail("not-an-email")
        classToTest.sendInvite()
        assertThat(classToTest.state.value.invite!!.tried).isTrue()
        classToTest.inviteEmail("Anna@Clinic.uz")
        classToTest.sendInvite()
        assertThat(calls.none { it.first == "POST clinics/2/invitations" }).isTrue() // already pending
        classToTest.inviteEmail(" New.Person@Clinic.UZ ")
        classToTest.sendInvite()
        settle { classToTest.state.value.invite == null }
        assertThat(
            calls.last {
                it.first == "POST clinics/2/invitations"
            }.second,
        ).isEqualTo("""{"email":"new.person@clinic.uz","role":"RECEPTIONIST"}""")
        assertThat(classToTest.state.value.banner!!.text).isEqualTo(Res.string.clinicInviteInviteSent)

        classToTest.askRevoke(classToTest.state.value.invitations!!.first())
        classToTest.revoke()
        settle { classToTest.state.value.revoking == null }
        assertThat(calls.any { it.first == "DELETE clinics/2/invitations/5" }).isTrue()
    }
}
