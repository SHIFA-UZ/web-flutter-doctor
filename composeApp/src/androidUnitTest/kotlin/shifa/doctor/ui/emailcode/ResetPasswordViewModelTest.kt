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
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.TrackingRole
import shifa.doctor.data.tracking.events.AuthEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.passwordRequirementSpecialChar
import shifa.doctor.generated.resources.passwordTooLong
import shifa.doctor.generated.resources.passwordsDoNotMatch
import shifa.doctor.generated.resources.pleaseConfirmPassword
import shifa.doctor.generated.resources.resetPasswordFailed
import shifa.doctor.generated.resources.wrongOrExpiredCodeBackToCode
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class ResetPasswordViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val sessions =
        SessionRepository(TokenStorage(MapSettings()), MapSettings(), LanguageRepository { error("unused") })
    private val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var status = HttpStatusCode.OK
    private var body = """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""
    private val auth =
        AuthRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    bodies += req.body.toByteArray().decodeToString()
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun vm() = ResetPasswordViewModel("doctor@shifa.dev", "123456", auth, sessions).also { created += it }

    private fun ResetPasswordViewModel.settle() {
        repeat(200) {
            if (!state.value.loading) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the backend's policy and the confirmation are checked first, inline only`() {
        val vm = vm()
        vm.onPassword("Abcdefg1")
        vm.submit()
        assertThat(vm.state.value.passwordError).isEqualTo(Res.string.passwordRequirementSpecialChar)
        assertThat(vm.state.value.confirmError).isEqualTo(Res.string.pleaseConfirmPassword)
        vm.onPassword("Abcdefg1!")
        vm.onConfirm("Abcdefg1?")
        vm.submit()
        assertThat(vm.state.value.passwordError).isNull()
        assertThat(vm.state.value.confirmError).isEqualTo(Res.string.passwordsDoNotMatch)
        assertThat(bodies).isEmpty()
    }

    @Test
    fun `a valid reset sends code, password and app, then signs in`() {
        val vm = vm()
        vm.onPassword(" Abcdefg1! ")
        vm.onConfirm(" Abcdefg1! ")
        vm.submit()
        vm.settle()
        assertThat(bodies.single()).contains("\"emailOtp\":\"123456\"")
        assertThat(bodies.single()).contains("\"newPassword\":\" Abcdefg1! \"")
        assertThat(bodies.single()).contains("\"app\":\"doctor\"")
        assertThat(vm.state.value.signedIn).isNotNull()
    }

    @Test
    fun `a wrong or expired code says to request a new one, a refused password does not`() {
        status = HttpStatusCode.Unauthorized
        body = """{"code":"UNAUTHORIZED"}"""
        val vm = vm()
        vm.onPassword("Abcdefg1!")
        vm.onConfirm("Abcdefg1!")
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.wrongOrExpiredCodeBackToCode))
        assertThat(vm.state.value.signedIn).isNull()
        status = HttpStatusCode.BadRequest
        body = """{"code":"VALIDATION"}"""
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.resetPasswordFailed))
    }

    @Test
    fun `a password over 128 characters is told the maximum`() {
        val vm = vm()
        vm.onPassword("Aa1!" + "x".repeat(130))
        vm.submit()
        assertThat(vm.state.value.passwordError).isEqualTo(Res.string.passwordTooLong)
    }

    @Test
    fun `a reset is tracked as a sign-in by password reset`() {
        val tracker = RecordingTracker()
        val vm = ResetPasswordViewModel("doctor@shifa.dev", "123456", auth, sessions, tracker).also { created += it }
        vm.onPassword("Abcdefg1!")
        vm.onConfirm("Abcdefg1!")
        status = HttpStatusCode.Unauthorized
        body = """{"code":"UNAUTHORIZED"}"""
        vm.submit()
        vm.settle()
        status = HttpStatusCode.OK
        body = """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""
        vm.submit()
        vm.settle()
        assertThat(tracker.events)
            .containsExactly(
                AuthEvents.SignInFailed("password_reset", "wrong_code"),
                AuthEvents.SignInSucceeded("password_reset", TrackingRole.Doctor),
            ).inOrder()
    }
}
