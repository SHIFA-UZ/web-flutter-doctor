@file:Suppress("ktlint:standard:max-line-length") // the backend's JSON answers, kept on one line

package shifa.doctor.ui.profile

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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.account.AccountRepository
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt
import shifa.doctor.data.applock.AppLockRepository
import shifa.doctor.data.auth.OtpCooldown
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.AppLockStorage
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.dataExportOncePerDay
import shifa.doctor.generated.resources.dataExportSaved
import shifa.doctor.generated.resources.obligationAppointments
import shifa.doctor.generated.resources.obligationSubscription
import shifa.doctor.generated.resources.wrongOrExpiredCodeBackToCode
import shifa.doctor.stopAll

@OptIn(ExperimentalCoroutinesApi::class)
class PrivacyViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()
    private val calls = java.util.concurrent.CopyOnWriteArrayList<Pair<String, String>>()
    private val replies =
        mutableMapOf(
            "export" to (HttpStatusCode.OK to """{"exportedAt":"2026-11-07T10:00:00Z","profile":{"firstName":"Aziza"}}"""),
            "request" to (HttpStatusCode.OK to """{"maskedEmail":"a***@example.uz","expiresInSeconds":600}"""),
            "confirm" to (HttpStatusCode.OK to """{"success":true}"""),
        )
    private val client =
        createHttpClient(
            { null },
            MockEngine { req ->
                val name = req.url.encodedPath.substringAfterLast('/')
                calls += name to req.body.toByteArray().decodeToString()
                val (status, body) = replies.getValue(name)
                respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
            },
        )
    private val sessions =
        SessionRepository(TokenStorage(MapSettings()), MapSettings(), LanguageRepository { error("unused") })
    private val lockSettings = MapSettings()
    private val appLock = AppLockRepository({ AppLockStorage(lockSettings) })

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun vm() =
        PrivacyViewModel(
            AccountRepository {
                client
            },
            sessions,
            appLock,
            OtpCooldown(),
        ).also { created += it }

    private fun PrivacyViewModel.settle() {
        repeat(200) {
            val s = state.value
            if (!s.busy && !s.exporting) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `the export is saved as a file, and a second one the same day says once a day`() {
        val vm = vm()
        var saved: Pair<String, String>? = null
        vm.export { name, bytes -> Result.success(name).also { saved = name to bytes.decodeToString() } }
        vm.settle()
        assertThat(saved?.first).isEqualTo("shifa-doctor-data.json")
        assertThat(saved?.second).contains("Aziza")
        assertThat(vm.state.value.banner?.text).isEqualTo(Res.string.dataExportSaved)
        assertThat(vm.state.value.banner?.error).isFalse()

        replies["export"] = HttpStatusCode.TooManyRequests to """{"status":429,"code":"RATE_LIMITED","requestId":"r1"}"""
        vm.export { _, _ -> error("not saved") }
        vm.settle()
        assertThat(vm.state.value.banner?.text).isEqualTo(Res.string.dataExportOncePerDay)
    }

    @Test
    fun `open obligations are listed instead of sending a code`() {
        replies["request"] =
            HttpStatusCode.Conflict to
            """{"status":409,"code":"HAS_ACTIVE_OBLIGATIONS","details":["UPCOMING_APPOINTMENTS","ACTIVE_SUBSCRIPTION","SOMETHING_NEW"],"requestId":"r2"}"""
        val vm = vm()
        vm.askDelete()
        vm.continueDelete()
        vm.settle()
        assertThat(vm.state.value.step).isEqualTo(DeleteStep.Blocked)
        assertThat(vm.state.value.obligations)
            .containsExactly(Res.string.obligationAppointments, Res.string.obligationSubscription).inOrder()
    }

    @Test
    fun `a wrong code stays, the right one deletes, signs out and forgets the PIN`() =
        runBlocking {
            sessions.signIn(jwt(4_000_000_000L)).getOrThrow()
            appLock.turnOn("1234").getOrThrow()
            val vm = vm()
            vm.askDelete()
            vm.continueDelete()
            vm.settle()
            assertThat(vm.state.value.step).isEqualTo(DeleteStep.Code)
            assertThat(vm.state.value.maskedEmail).isEqualTo("a***@example.uz")

            replies["confirm"] = HttpStatusCode.BadRequest to """{"status":400,"code":"EMAIL_OTP_INVALID","requestId":"r3"}"""
            vm.onCode("000000")
            vm.settle()
            assertThat(vm.state.value.codeError).isEqualTo(Res.string.wrongOrExpiredCodeBackToCode)
            assertThat(sessions.session.value).isNotNull()

            replies["confirm"] = HttpStatusCode.OK to """{"success":true}"""
            vm.onCode("123456")
            vm.settle()
            assertThat(calls.last()).isEqualTo("confirm" to """{"code":"123456"}""")
            assertThat(sessions.session.value).isNull()
            assertThat(sessions.ended.first()).isEqualTo(SessionEndReason.AccountDeleted)
            assertThat(appLock.hasPin).isFalse()
        }
}
