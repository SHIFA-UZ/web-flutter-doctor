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
import shifa.doctor.generated.resources.otpResent
import shifa.doctor.generated.resources.tooManyRequests

@OptIn(ExperimentalCoroutinesApi::class)
class EmailCodeViewModelTest {
    private var now = 1_000_000L
    private val cooldown = OtpCooldown { now }
    private val sessions =
        SessionRepository(TokenStorage(MapSettings()), MapSettings(), LanguageRepository { error("unused") })
    private val calls = mutableListOf<String>()
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

    @After fun tearDown() = Dispatchers.resetMain()

    private fun waitFor(done: () -> Boolean) {
        repeat(200) {
            if (done()) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the address is checked before anything is sent`() {
        val vm = EmailEntryViewModel(EmailCodeMode.SignIn, auth = auth, cooldown = cooldown)
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.enterEmail)
        vm.onEmail("doctor@shifa")
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.invalidEmail)
        assertThat(calls).isEmpty()
    }

    @Test
    fun `a code goes out once a minute per address, by mode`() {
        val vm = EmailEntryViewModel(EmailCodeMode.SignIn, auth = auth, cooldown = cooldown)
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
        val reset = EmailEntryViewModel(EmailCodeMode.Reset, auth = auth, cooldown = cooldown)
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
        val vm = EmailEntryViewModel(EmailCodeMode.SignIn, auth = auth, cooldown = cooldown)
        vm.onEmail("a@b.cd")
        vm.submit()
        waitFor { !vm.state.value.loading }
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.tooManyRequests))
    }

    @Test
    fun `the sixth digit signs in, a wrong code clears the field`() {
        cooldown.start("a@b.cd", "SignIn")
        val vm = CodeViewModel("a@b.cd", EmailCodeMode.SignIn, auth, sessions, cooldown)
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
        val vm = CodeViewModel("a@b.cd", EmailCodeMode.Reset, auth, sessions, cooldown)
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
}
