@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.emailcode

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
import shifa.doctor.data.auth.OtpCooldown
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.enterEmail
import shifa.doctor.generated.resources.invalidEmail
import shifa.doctor.generated.resources.invalidOtp
import shifa.doctor.generated.resources.invalidPhone
import shifa.doctor.generated.resources.noAccountForEmail
import shifa.doctor.generated.resources.noAccountForPhone
import shifa.doctor.generated.resources.otpResent
import shifa.doctor.generated.resources.phoneNumberRequired
import shifa.doctor.generated.resources.smsSignInUzbekOnly
import shifa.doctor.generated.resources.smsUnavailableUseEmail
import shifa.doctor.generated.resources.tooManyRequests
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class EmailCodeViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private var now = 1_000_000L
    private val cooldown = OtpCooldown { now }
    private val sessions =
        SessionRepository(TokenStorage(MapSettings()), MapSettings(), LanguageRepository { error("unused") })
    private val calls = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var status = HttpStatusCode.OK
    private var body = """{"sent":true}"""
    private val auth =
        AuthRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    calls += "${req.url.encodedPath} ${req.body.toByteArray().decodeToString()}"
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun waitFor(done: () -> Boolean) {
        repeat(200) {
            if (done()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the address is checked before anything is sent`() {
        val vm = EmailEntryViewModel(EmailCodeMode.SignIn, auth = auth, cooldown = cooldown).also { created += it }
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.enterEmail)
        vm.onEmail("doctor@shifa")
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.invalidEmail)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a code goes out once a minute per address, by mode`() {
        val vm = EmailEntryViewModel(EmailCodeMode.SignIn, auth = auth, cooldown = cooldown).also { created += it }
        vm.onEmail(" Doctor@Shifa.dev ")
        vm.submit()
        waitFor { vm.state.value.sentTo != null }
        assertThat(vm.state.value.sentTo).isEqualTo("doctor@shifa.dev") // trimmed and lower-cased
        vm.consumeSent()
        vm.submit() // back and Continue again within the minute: no second e-mail
        assertThat(vm.state.value.sentTo).isEqualTo("doctor@shifa.dev")
        assertThat(calls).hasSize(1)
        assertThat(calls.single()).startsWith("/api/auth/send-login-otp")

        // A reset code for the same address is a different code: it is sent.
        val reset = EmailEntryViewModel(EmailCodeMode.Reset, auth = auth, cooldown = cooldown).also { created += it }
        reset.onEmail("doctor@shifa.dev")
        reset.submit()
        waitFor { reset.state.value.sentTo != null }
        assertThat(calls.last()).contains("send-forgot-password-otp")
        assertThat(calls.last()).contains("\"app\":\"doctor\"")
    }

    @Test
    fun `a rate-limited send says so`() {
        status = HttpStatusCode.TooManyRequests
        body = """{"code":"RATE_LIMITED"}"""
        val vm = EmailEntryViewModel(EmailCodeMode.SignIn, auth = auth, cooldown = cooldown).also { created += it }
        vm.onEmail("a@b.cd")
        vm.submit()
        waitFor { !vm.state.value.loading }
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.tooManyRequests))
    }

    @Test
    fun `the sixth digit signs in, a wrong code clears the field`() {
        cooldown.start("a@b.cd", "SignIn")
        val vm = CodeViewModel("a@b.cd", EmailCodeMode.SignIn, auth, sessions, cooldown).also { created += it }
        assertThat(vm.state.value.resendIn).isEqualTo(60)
        status = HttpStatusCode.BadRequest
        body = """{"code":"VALIDATION"}"""
        vm.onCode("12a345x6")
        waitFor { !vm.state.value.loading }
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.invalidOtp))
        assertThat(vm.state.value.code).isEmpty()
        status = HttpStatusCode.OK
        body = """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""
        vm.onCode("123456")
        waitFor { vm.state.value.signedIn != null }
        assertThat(vm.state.value.signedIn?.role).isEqualTo("DOCTOR")
    }

    @Test
    fun `reset mode sends nothing on the code, and resend waits for the cooldown`() {
        cooldown.start("a@b.cd", "Reset")
        val vm = CodeViewModel("a@b.cd", EmailCodeMode.Reset, auth, sessions, cooldown).also { created += it }
        vm.onCode("654321")
        assertThat(vm.state.value.resetCode).isEqualTo("654321")
        vm.resend()
        assertThat(calls).isEmpty()
        now += 60_000
        vm.resend()
        waitFor { vm.state.value.banner != null }
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.otpResent, error = false))
        assertThat(calls.single()).contains("send-forgot-password-otp")
    }

    @Test
    fun `sms sign-in takes an uzbek mobile only, sends it in e164, and says when the code went by e-mail`() {
        val vm = EmailEntryViewModel(EmailCodeMode.SmsSignIn, auth = auth, cooldown = cooldown).also { created += it }
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.phoneNumberRequired)
        vm.onEmail("12ab")
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.invalidPhone)
        vm.onEmail("+49 151 1234 5678")
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.smsSignInUzbekOnly)
        assertThat(calls).isEmpty()

        body = """{"sent":true,"channel":"sms"}"""
        vm.onEmail("90 123 45 67")
        vm.submit()
        waitFor { vm.state.value.sentTo != null }
        assertThat(vm.state.value.sentTo).isEqualTo("+998901234567")
        assertThat(cooldown.hint("+998901234567", "SmsSignIn")).isNull()
        assertThat(calls.single()).isEqualTo("/api/auth/send-login-sms-otp {\"phone\":\"+998901234567\"}")

        // The SMS couldn't go out: the code went to the account's e-mail, and the code screen says where.
        body = """{"sent":true,"channel":"email","smsFallbackUsed":true,"emailHint":"d***@shifa.dev"}"""
        val other =
            EmailEntryViewModel(
                EmailCodeMode.SmsSignIn,
                auth = auth,
                cooldown = cooldown,
            ).also { created += it }
        other.onEmail("+998 93 000 00 00")
        other.submit()
        waitFor { other.state.value.sentTo != null }
        // The code screen (opened now, or again after Back within the minute) says where it went.
        val code =
            CodeViewModel(
                "+998930000000",
                EmailCodeMode.SmsSignIn,
                auth,
                sessions,
                cooldown,
            ).also { created += it }
        assertThat(code.state.value.emailHint).isEqualTo("d***@shifa.dev")
        // A resend that goes by SMS again is described so too, and is what a later code screen shows.
        now += 60_000
        body = """{"sent":true,"channel":"sms"}"""
        code.resend()
        waitFor { code.state.value.banner != null }
        assertThat(code.state.value.emailHint).isNull()
        assertThat(cooldown.hint("+998930000000", "SmsSignIn")).isNull()
    }

    @Test
    fun `sms unavailable without an e-mail to fall back to says to use e-mail`() {
        status = HttpStatusCode.ServiceUnavailable
        body = """{"status":503}"""
        val vm = EmailEntryViewModel(EmailCodeMode.SmsSignIn, auth = auth, cooldown = cooldown).also { created += it }
        vm.onEmail("+998901234567")
        vm.submit()
        waitFor { !vm.state.value.loading && vm.state.value.banner != null }
        assertThat(vm.state.value.banner?.text).isEqualTo(Res.string.smsUnavailableUseEmail)
    }

    @Test
    fun `the sms code signs in through the sms route`() {
        cooldown.start("+998901234567", "SmsSignIn")
        val vm =
            CodeViewModel("+998901234567", EmailCodeMode.SmsSignIn, auth, sessions, cooldown).also { created += it }
        assertThat(vm.state.value.bySms).isTrue()
        body = """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""
        vm.onCode("123456")
        waitFor { vm.state.value.signedIn != null }
        assertThat(calls.single())
            .isEqualTo("/api/auth/verify-login-sms-otp {\"phone\":\"+998901234567\",\"code\":\"123456\"}")
    }

    @Test
    fun `an address or number without an account says so, in every mode`() {
        status = HttpStatusCode.NotFound
        body = """{"status":404,"message":"No account found"}"""
        for (mode in listOf(EmailCodeMode.Reset, EmailCodeMode.SignIn)) {
            val vm = EmailEntryViewModel(mode, auth = auth, cooldown = cooldown).also { created += it }
            vm.onEmail("nobody+${mode.name}@shifa.dev")
            vm.submit()
            waitFor { !vm.state.value.loading && vm.state.value.banner != null }
            assertThat(vm.state.value.banner?.text).isEqualTo(Res.string.noAccountForEmail)
            assertThat(vm.state.value.sentTo).isNull()
        }
        val sms = EmailEntryViewModel(EmailCodeMode.SmsSignIn, auth = auth, cooldown = cooldown).also { created += it }
        sms.onEmail("+998901112233")
        sms.submit()
        waitFor { !sms.state.value.loading && sms.state.value.banner != null }
        assertThat(sms.state.value.banner?.text).isEqualTo(Res.string.noAccountForPhone)
    }
}
