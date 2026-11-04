@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.register

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
import shifa.doctor.data.api.jwt
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.doctorAccountExists
import shifa.doctor.generated.resources.doctorPhoneExists
import shifa.doctor.generated.resources.enterEmail
import shifa.doctor.generated.resources.enterFirstName
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.passwordRequired
import shifa.doctor.generated.resources.passwordsDoNotMatch
import shifa.doctor.generated.resources.registrationConflict
import shifa.doctor.generated.resources.timezoneDetectionFailed

@OptIn(ExperimentalCoroutinesApi::class)
class RegisterViewModelTest {
    private val sessions =
        SessionRepository(TokenStorage(MapSettings()), MapSettings(), LanguageRepository { error("unused") })
    private val calls = mutableListOf<Pair<String, String>>()

    // path → status and body; dev answers of 2026-11-04 (check-existing-doctor 404 for an unknown address).
    private val replies =
        mutableMapOf(
            "check-existing-doctor" to (HttpStatusCode.NotFound to """{"error":404,"status":404,"code":"NOT_FOUND","requestId":"r1"}"""),
            "check-existing-patient" to (HttpStatusCode.OK to """{"email":null,"found":false,"fullName":null,"photoUrl":null}"""),
            "register" to (HttpStatusCode.OK to """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""),
        )
    private var offline = false
    private val auth =
        AuthRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    if (offline) throw java.io.IOException("offline")
                    val name = req.url.encodedPath.substringAfterLast('/')
                    calls += name to req.body.toByteArray().decodeToString()
                    val (status, body) = replies.getValue(name)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        invite: Invite = Invite("KEY-1", false),
        zone: String? = "Asia/Tashkent",
    ) = RegisterViewModel(invite, auth, sessions) { zone }

    private fun RegisterViewModel.settle() {
        repeat(200) {
            if (!state.value.loading) return
            Thread.sleep(10)
        }
    }

    private fun RegisterViewModel.fill(email: String = " Doctor@Example.UZ ") {
        onFirstName(" Aziza ")
        onLastName("Karimova")
        onEmail(email)
        onPhone("90 123 45 67")
        onPassword(" Secret1! ")
        onConfirm(" Secret1! ")
    }

    @Test
    fun `an incomplete form shows every inline error and sends nothing`() {
        val vm = vm()
        vm.onEmail("no-at-sign")
        vm.onPhone("12345")
        vm.onConfirm("x")
        vm.next()
        val s = vm.state.value
        assertThat(s.firstNameError).isEqualTo(Res.string.enterFirstName)
        assertThat(s.emailError).isEqualTo(Res.string.enterEmail)
        assertThat(s.phoneError).isEqualTo(Res.string.invalidPhone)
        assertThat(s.passwordError).isEqualTo(Res.string.passwordRequired)
        assertThat(s.confirmError).isEqualTo(Res.string.passwordsDoNotMatch)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `an address with a doctor account stops on step 1`() {
        replies["check-existing-doctor"] = HttpStatusCode.OK to """{"firstName":"Nilufar","lastName":"Karimova"}"""
        val vm = vm()
        vm.fill()
        vm.next()
        vm.settle()
        assertThat(vm.state.value.emailError).isEqualTo(Res.string.doctorAccountExists)
        assertThat(vm.state.value.step).isEqualTo(RegisterStep.Form)
        assertThat(calls.map { it.first }).containsExactly("check-existing-doctor")
    }

    @Test
    fun `a doctor's phone stops too, and a failed doctor check never lets the doctor through`() {
        val vm = vm()
        vm.fill()
        replies["check-existing-doctor"] = HttpStatusCode.InternalServerError to """{"error":500,"status":500,"code":"INTERNAL","requestId":"r5"}"""
        vm.next()
        vm.settle()
        assertThat(vm.state.value.step).isEqualTo(RegisterStep.Form)
        assertThat(vm.state.value.banner?.requestId).isEqualTo("r5")
        assertThat(calls.map { it.first }).doesNotContain("check-existing-patient")
        // The e-mail is free, the phone belongs to a doctor (asked separately: the backend stops at a phone match).
        calls.clear()
        var doctorCalls = 0
        replies["check-existing-doctor"] = HttpStatusCode.NotFound to """{"error":404,"status":404,"code":"NOT_FOUND"}"""
        val phoneVm = RegisterViewModel(Invite("KEY-1", false), phoneAuth { doctorCalls++ == 1 }, sessions) { "UTC" }
        phoneVm.fill()
        phoneVm.next()
        phoneVm.settle()
        assertThat(phoneVm.state.value.phoneError).isEqualTo(Res.string.doctorPhoneExists)
        assertThat(phoneVm.state.value.step).isEqualTo(RegisterStep.Form)
    }

    // check-existing-doctor answers 200 when [doctor] says so for that call, 404 otherwise.
    private fun phoneAuth(doctor: () -> Boolean) =
        AuthRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    val body = req.body.toByteArray().decodeToString()
                    calls += "check-existing-doctor" to body
                    if (doctor()) {
                        respond(
                            """{"firstName":"N","lastName":"K"}""",
                            HttpStatusCode.OK,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    } else {
                        respond(
                            """{"code":"NOT_FOUND"}""",
                            HttpStatusCode.NotFound,
                            headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                },
            )
        }

    @Test
    fun `a new doctor goes to step 2 and registers exactly what was typed`() {
        val vm = vm()
        vm.fill()
        vm.next()
        vm.settle()
        assertThat(vm.state.value.step).isEqualTo(RegisterStep.Details)
        assertThat(vm.state.value.timeZone).isEqualTo("Asia/Tashkent")
        assertThat(
            calls.single { it.first == "check-existing-patient" }.second,
        ).isEqualTo(
            """{"firstName":"Aziza","lastName":"Karimova","email":"doctor@example.uz","phone":"+998901234567"}""",
        )
        vm.register()
        vm.settle()
        assertThat(calls.last().second).isEqualTo(
            """{"firstName":"Aziza","lastName":"Karimova","email":"doctor@example.uz","password":" Secret1! ","key":"KEY-1","phone":"+998901234567","timeZone":"Asia/Tashkent"}""",
        )
        assertThat(vm.state.value.signedIn).isNotNull()
        assertThat(vm.state.value.password).isEmpty()
    }

    @Test
    fun `an existing patient gets the confirmation, Back keeps the form, a dirty form asks before leaving`() {
        replies["check-existing-patient"] = HttpStatusCode.OK to """{"email":"doctor@example.uz","found":true,"fullName":"Aziza K","photoUrl":""}"""
        val vm = vm()
        vm.fill()
        vm.next()
        vm.settle()
        assertThat(vm.state.value.step).isEqualTo(RegisterStep.ExistingPatient)
        assertThat(vm.state.value.existing?.photoUrl).isNull()
        assertThat(vm.back()).isFalse()
        assertThat(vm.state.value.step).isEqualTo(RegisterStep.Form)
        assertThat(vm.state.value.firstName).isEqualTo(" Aziza ")
        assertThat(vm.back()).isFalse()
        assertThat(vm.state.value.askDiscard).isTrue()
        assertThat(vm.back()).isFalse() // Back on the question keeps editing
        assertThat(vm.state.value.askDiscard).isFalse()
        assertThat(vm().back()).isTrue() // an empty form just leaves
    }

    @Test
    fun `no detected timezone is flagged and blocks registration`() {
        val vm = vm(zone = null)
        vm.fill()
        vm.next()
        vm.settle()
        assertThat(vm.state.value.timeZone).isNull()
        assertThat(vm.state.value.timeZoneError).isEqualTo(Res.string.timezoneDetectionFailed)
        vm.register()
        assertThat(calls.map { it.first }).doesNotContain("register")
        vm.pickTimeZone("Europe/Berlin")
        assertThat(vm.state.value.timeZoneError).isNull()
    }

    @Test
    fun `a conflict keeps its request id, offline stays on the form, the invited address is locked`() {
        replies["register"] = HttpStatusCode.Conflict to """{"error":409,"status":409,"code":"CONFLICT","requestId":"req-9"}"""
        val vm = vm(Invite("KEY-1", false, email = "invited@shifa.uz"))
        assertThat(vm.state.value.email).isEqualTo("invited@shifa.uz")
        vm.fill(email = "other@shifa.uz")
        assertThat(vm.state.value.email).isEqualTo("invited@shifa.uz")
        vm.next()
        vm.settle()
        vm.register()
        vm.settle()
        assertThat(vm.state.value.banner?.text).isEqualTo(Res.string.registrationConflict)
        assertThat(vm.state.value.banner?.requestId).isEqualTo("req-9")
        val offlineVm = vm()
        offlineVm.fill()
        offline = true
        offlineVm.next()
        offlineVm.settle()
        assertThat(offlineVm.state.value.step).isEqualTo(RegisterStep.Form)
        assertThat(offlineVm.state.value.banner?.text).isEqualTo(Res.string.networkError)
    }
}
