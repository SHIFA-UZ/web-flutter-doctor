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
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.catalog.Clinic
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.profile.Gender
import shifa.doctor.data.profile.ProfileRepository
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
import shifa.doctor.generated.resources.profileExtrasNotSaved
import shifa.doctor.generated.resources.registrationConflict
import shifa.doctor.generated.resources.registrationIncomplete
import shifa.doctor.generated.resources.staffEmailInUse
import shifa.doctor.generated.resources.timezoneDetectionFailed
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice

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
            "register-clinic-staff" to (
                HttpStatusCode.OK to """{"token":"${jwt(
                    4_000_000_000L,
                )}","forcePasswordReset":false}"""
            ),
            "register" to (HttpStatusCode.OK to """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""),
            "profile" to (HttpStatusCode.OK to """{}"""),
            "professions" to (HttpStatusCode.OK to """[{"id":59,"english":"Dentist","uzbek":"Stomatolog","name":"Dentist","category":"Dental","displayOrder":1},{"english":"Orthodontist","uzbek":"Ortodont"},{"english":"Cardiologist","uzbek":"Kardiolog"}]"""),
            "clinics" to (HttpStatusCode.OK to """[{"id":2,"name":"Shifa Dev Clinic"},{"id":1,"name":"Tashkent Med Center"}]"""),
        )
    private var offline = false
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                if (offline) throw java.io.IOException("offline")
                val name = req.url.encodedPath.substringAfterLast('/')
                val search = req.url.parameters["search"]?.let { "?search=$it" }.orEmpty()
                calls += name + search to req.body.toByteArray().decodeToString()
                val (status, body) = replies.getValue(name)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    private val auth = AuthRepository { client }

    private val main = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(main)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm(
        invite: Invite = Invite("KEY-1", false),
        zone: String? = "Asia/Tashkent",
    ) = RegisterViewModel(invite, auth, sessions, {
        zone
    }, CatalogRepository({ client }, { BUNDLED }), ProfileRepository { client })

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
        val phoneVm = RegisterViewModel(Invite("KEY-1", false), phoneAuth { doctorCalls++ == 1 }, sessions, { "UTC" })
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

    private fun RegisterViewModel.toStep2() {
        fill()
        next()
        settle()
    }

    private fun RegisterViewModel.waitPicker() {
        repeat(200) {
            if (!state.value.pickerLoading) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `step 2 details are PATCHed once after register, without the timezone again`() {
        val vm = vm()
        vm.toStep2()
        vm.pickDob(LocalDate(1990, 3, 12))
        vm.pickGender(Gender.Female)
        vm.onAddress("  Chilonzor 5 ")
        vm.pickClinic(Clinic(2, "Shifa Dev Clinic"))
        vm.toggleProfession(Profession("Dentist", "Stomatolog"))
        vm.toggleProfession(Profession("Orthodontist", "Ortodont"))
        vm.register()
        vm.settle()
        assertThat(calls.takeLast(2).map { it.first }).containsExactly("register", "profile").inOrder()
        assertThat(calls.last().second).isEqualTo(
            """{"dob":"1990-03-12","gender":"Female","address":"Chilonzor 5","clinicId":2,"profession":"Dentist, Orthodontist"}""",
        )
        assertThat(vm.state.value.signedIn).isNotNull()
    }

    @Test
    fun `nothing filled in means no PATCH, a failed PATCH still signs in and leaves a note for the shell`() {
        ShellNotice.pending.value = null
        val empty = vm()
        empty.toStep2()
        empty.register()
        empty.settle()
        assertThat(calls.map { it.first }).doesNotContain("profile")
        replies["profile"] = HttpStatusCode.InternalServerError to """{"status":500,"code":"INTERNAL"}"""
        val vm = vm()
        vm.toStep2()
        vm.onAddress("Chilonzor 5")
        vm.register()
        vm.settle()
        assertThat(vm.state.value.signedIn).isNotNull()
        assertThat(ShellNotice.pending.value).isEqualTo(Notice(Res.string.profileExtrasNotSaved))
        ShellNotice.pending.value = null
    }

    @Test
    fun `clinics list on open, filter locally under two characters, search the backend from two, retry after a failure`() {
        val vm = vm()
        vm.toStep2()
        vm.openPicker(RegisterPicker.Clinic)
        vm.waitPicker()
        assertThat(vm.state.value.clinics.map { it.name }).containsExactly("Shifa Dev Clinic", "Tashkent Med Center")
        vm.onQuery("t")
        vm.waitPicker()
        assertThat(vm.state.value.clinics.map { it.name }).containsExactly("Tashkent Med Center")
        vm.onQuery("sh")
        main.scheduler.advanceTimeBy(400) // the 300 ms debounce, on the test clock
        vm.waitPicker()
        assertThat(calls.last().first).isEqualTo("clinics?search=sh")
        replies["clinics"] = HttpStatusCode.InternalServerError to """{"status":500,"code":"INTERNAL"}"""
        vm.retryPicker()
        main.scheduler.advanceTimeBy(400)
        vm.waitPicker()
        assertThat(vm.state.value.pickerFailed).isTrue()
        vm.pickClinic(null)
        assertThat(vm.state.value.picker).isNull()
    }

    @Test
    fun `professions load once, match either language, and toggle`() {
        val vm = vm()
        vm.toStep2()
        vm.openPicker(RegisterPicker.Profession)
        vm.waitPicker()
        vm.onQuery("kardio")
        assertThat(vm.state.value.professionOptions.map { it.english }).containsExactly("Cardiologist")
        vm.toggleProfession(vm.state.value.professionOptions.single())
        vm.toggleProfession(Profession("Dentist", null))
        vm.toggleProfession(Profession("Dentist", null))
        assertThat(vm.state.value.professions.map { it.english }).containsExactly("Cardiologist")
        vm.closePicker()
        vm.openPicker(RegisterPicker.Profession)
        assertThat(calls.count { it.first == "professions" }).isEqualTo(1)
    }

    @Test
    fun `an unreachable catalogue falls back to the bundled one, and Cyrillic finds the Uzbek label`() {
        replies["professions"] = HttpStatusCode.ServiceUnavailable to """{"status":503,"code":"UNAVAILABLE"}"""
        val vm = vm()
        vm.toStep2()
        vm.openPicker(RegisterPicker.Profession)
        vm.waitPicker()
        assertThat(vm.state.value.pickerFailed).isTrue() // the bundled list shows, with Retry above it
        vm.onQuery("Стоматолог")
        assertThat(vm.state.value.professionOptions.map { it.english }).containsExactly("Dentist")
        replies["professions"] = HttpStatusCode.OK to """[{"english":"Cardiologist","uzbek":"Kardiolog"}]"""
        vm.retryPicker()
        vm.waitPicker()
        assertThat(vm.state.value.pickerFailed).isFalse()
        assertThat(vm.state.value.catalog.map { it.english }).containsExactly("Cardiologist")
    }

    @Test
    fun `a PATCH that ends the session does not open the shell`() {
        replies["profile"] = HttpStatusCode.Unauthorized to """{"status":401,"code":"SESSION_INVALID","requestId":"r7"}"""
        val vm =
            RegisterViewModel(Invite("KEY-1", false), auth, sessions, {
                "UTC"
            }, CatalogRepository({ client }, { BUNDLED }), ProfileRepository(tokenClient))
        vm.toStep2()
        vm.onAddress("Chilonzor 5")
        vm.register()
        vm.settle()
        assertThat(sessions.session.value).isNull()
        assertThat(vm.state.value.signedIn).isNull()
        assertThat(vm.state.value.banner?.requestId).isEqualTo("r7")
    }

    // Sends the session's token, so a 401 ends the session as in the app.
    private val tokenClient = {
        createHttpClient(
            { sessions.session.value?.token },
            MockEngine { req ->
                val (status, body) = replies.getValue(req.url.encodedPath.substringAfterLast('/'))
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    }

    @Test
    fun `a receptionist invitation opens the staff form with the invited address and registers as staff`() {
        val vm = vm(Invite("STAFF-1", true, 7, "Shifa Dental", "dilfuza@shifadental.uz"))
        val s = vm.state.value
        assertThat(s.step).isEqualTo(RegisterStep.Staff)
        assertThat(s.email).isEqualTo("dilfuza@shifadental.uz")
        assertThat(s.timeZone).isEqualTo("Asia/Tashkent")
        vm.register()
        assertThat(vm.state.value.firstNameError).isEqualTo(Res.string.enterFirstName)
        assertThat(vm.state.value.passwordError).isEqualTo(Res.string.passwordRequired)
        assertThat(calls).isEmpty()
        vm.onFirstName("Dilfuza ")
        vm.onLastName("Yusupova")
        vm.onPassword("Secret1!")
        vm.onConfirm("Secret1!")
        vm.register()
        vm.settle()
        assertThat(calls.single()).isEqualTo(
            "register-clinic-staff" to """{"firstName":"Dilfuza","lastName":"Yusupova","email":"dilfuza@shifadental.uz","password":"Secret1!","key":"STAFF-1","timeZone":"Asia/Tashkent"}""",
        )
        assertThat(vm.state.value.signedIn).isNotNull()
    }

    @Test
    fun `a staff invitation without an address asks for one, and a filled form asks before leaving`() {
        val vm = vm(Invite("STAFF-2", true), zone = null)
        assertThat(vm.state.value.emailLocked).isFalse()
        assertThat(vm.state.value.timeZoneError).isEqualTo(Res.string.timezoneDetectionFailed)
        vm.onEmail("not-an-address")
        vm.register()
        assertThat(vm.state.value.emailError).isEqualTo(Res.string.enterEmail)
        assertThat(vm.back()).isFalse()
        assertThat(vm.state.value.askDiscard).isTrue()
        assertThat(vm.state.value.step).isEqualTo(RegisterStep.Staff)
    }

    @Test
    fun `a typed staff address that already has an account is refused before registering`() {
        replies["check-existing-patient"] = HttpStatusCode.OK to """{"email":"someone@shifa.uz","found":true,"fullName":"S","photoUrl":null}"""
        val vm = vm(Invite("STAFF-3", true))
        vm.onEmail("Someone@shifa.uz")
        vm.onFirstName("A")
        vm.onLastName("B")
        vm.onPassword("Secret1!")
        vm.onConfirm("Secret1!")
        vm.register()
        vm.settle()
        assertThat(vm.state.value.emailError).isEqualTo(Res.string.staffEmailInUse)
        assertThat(calls.map { it.first }).containsExactly("check-existing-patient")
    }

    @Test
    fun `an account created without a usable token says so and only lets the user leave`() {
        replies["register-clinic-staff"] = HttpStatusCode.OK to """{"forcePasswordReset":false}"""
        val vm = vm(Invite("STAFF-4", true, email = "r@shifa.uz"))
        vm.onFirstName("A")
        vm.onLastName("B")
        vm.onPassword("Secret1!")
        vm.onConfirm("Secret1!")
        vm.register()
        vm.settle()
        assertThat(vm.state.value.banner?.text).isEqualTo(Res.string.registrationIncomplete)
        assertThat(vm.state.value.completed).isTrue()
        assertThat(vm.state.value.signedIn).isNull()
        vm.register()
        assertThat(calls.count { it.first == "register-clinic-staff" }).isEqualTo(1)
        assertThat(vm.back()).isTrue()
    }

    private companion object {
        const val BUNDLED = """[{"english":"Dentist","uzbek":"Stomatolog"},{"english":"Urologist","uzbek":"Urolog"}]"""
    }
}
