@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.login

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountDisabledBanner
import shifa.doctor.generated.resources.enterEmailOrPhone
import shifa.doctor.generated.resources.invalidCredentials
import shifa.doctor.generated.resources.minimum6Characters
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.signedOutInactivity
import shifa.doctor.generated.resources.youveSignedOut

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {
    private val tokens = TokenStorage(MapSettings())
    private val sessions = SessionRepository(tokens, MapSettings(), LanguageRepository { error("unused") })
    private var reply: suspend (
        HttpRequestData,
    ) -> Pair<HttpStatusCode, String> = {
        HttpStatusCode.OK to """{"token":"${jwt(4_000_000_000L)}","forcePasswordReset":false}"""
    }
    private val sent = mutableListOf<String>()
    private val urls = mutableListOf<String>()
    private val auth =
        AuthRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    sent += req.body.toByteArray().decodeToString()
                    urls += req.url.toString()
                    val (status, body) = reply(req)
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun vm() = LoginViewModel(auth, sessions)

    // MockEngine answers on another thread: wait for the request to finish.
    private fun LoginViewModel.settle() {
        repeat(200) {
            if (!state.value.loading) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `empty identifier and a short password are caught before any request`() {
        val vm = vm()
        vm.onPassword("12345")
        vm.submit()
        assertThat(vm.state.value.identifierError).isEqualTo(Res.string.enterEmailOrPhone)
        assertThat(vm.state.value.passwordError).isEqualTo(Res.string.minimum6Characters)
        assertThat(sent).isEmpty()
        vm.onIdentifier("a")
        assertThat(vm.state.value.identifierError).isNull()
    }

    @Test
    fun `six characters are counted after trimming, but the password is sent as typed`() {
        val vm = vm()
        vm.onIdentifier("a@b.c")
        vm.onPassword("     a")
        vm.submit()
        assertThat(vm.state.value.passwordError).isEqualTo(Res.string.minimum6Characters)
        assertThat(sent).isEmpty()
    }

    @Test
    fun `the password goes as typed, the identifier trimmed, and the session starts`() {
        val vm = vm()
        vm.onIdentifier("  seed.doctor1@shifa.dev ")
        vm.onPassword(" Dev12345! ")
        vm.submit()
        vm.settle()
        assertThat(urls.single()).contains("app=doctor")
        val body = Json.parseToJsonElement(sent.single()).jsonObject
        assertThat(body["username"]!!.jsonPrimitive.content).isEqualTo("seed.doctor1@shifa.dev")
        assertThat(body["password"]!!.jsonPrimitive.content).isEqualTo(" Dev12345! ")
        assertThat(vm.state.value.signedIn?.role).isEqualTo("DOCTOR")
        assertThat(tokens.token).isNotNull()
    }

    @Test
    fun `wrong credentials and no network show localized banners, never server text`() {
        val vm = vm()
        vm.onIdentifier("x@y.z")
        vm.onPassword("wrong123")
        reply = { HttpStatusCode.Unauthorized to """{"error":"Bad credentials","code":"UNAUTHORIZED"}""" }
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.banner).isEqualTo(LoginBanner(Res.string.invalidCredentials, error = true))
        reply = { throw IOException("offline") }
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.banner).isEqualTo(LoginBanner(Res.string.networkError, error = true))
        assertThat(vm.state.value.signedIn).isNull()
    }

    @Test
    fun `the reason the last session ended greets the doctor once`() {
        sessions.signIn(jwt(4_000_000_000L))
        sessions.end(SessionEndReason.AccountDisabled, sessions.session.value!!.token)
        val vm = vm()
        assertThat(vm.state.value.banner).isEqualTo(LoginBanner(Res.string.accountDisabledBanner, error = true))
        vm.dismissBanner()
        assertThat(vm.state.value.banner).isNull()
    }

    @Test
    fun `signing out yourself gets a neutral note, not an error`() {
        sessions.signIn(jwt(4_000_000_000L))
        sessions.signOut()
        assertThat(
            vm().state.value.banner,
        ).isEqualTo(LoginBanner(Res.string.youveSignedOut, error = false, neutral = true))
    }

    @Test
    fun `an inactivity sign-out explains itself`() {
        sessions.signIn(jwt(4_000_000_000L))
        sessions.end(SessionEndReason.Inactivity, sessions.session.value!!.token)
        assertThat(vm().state.value.banner).isEqualTo(LoginBanner(Res.string.signedOutInactivity, error = false))
    }
}
