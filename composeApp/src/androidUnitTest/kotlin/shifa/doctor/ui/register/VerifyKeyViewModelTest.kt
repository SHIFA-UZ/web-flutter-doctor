@file:Suppress("ktlint:standard:max-line-length") // JSON fixtures

package shifa.doctor.ui.register

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.AuthEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.invalidInviteKey
import shifa.doctor.generated.resources.pleaseEnterOneTimeKey
import shifa.doctor.stopAll
import shifa.doctor.ui.emailcode.CodeBanner

@OptIn(ExperimentalCoroutinesApi::class)
class VerifyKeyViewModelTest {
    private val created = mutableListOf<androidx.lifecycle.ViewModel>()

    private val sent = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var status = HttpStatusCode.OK
    private var hold = CompletableDeferred(Unit)
    private var body = """{"valid":false,"purpose":null,"clinicId":null,"clinicName":null,"emailSentTo":null}""" // dev, 2026-11-04
    private val auth =
        AuthRepository {
            createHttpClient(
                { null },
                MockEngine { req ->
                    sent += req.body.toByteArray().decodeToString()
                    hold.await()
                    respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
                },
            )
        }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        created.stopAll()
        Dispatchers.resetMain()
    }

    private fun VerifyKeyViewModel.settle() {
        repeat(200) {
            if (!state.value.loading) return
            Thread.sleep(10)
        }
    }

    @Test
    fun `an empty key is caught inline, a pasted one is trimmed`() {
        val vm = VerifyKeyViewModel(auth).also { created += it }
        vm.onKey("  \n ")
        vm.submit()
        assertThat(vm.state.value.error).isEqualTo(Res.string.pleaseEnterOneTimeKey)
        assertThat(sent).isEmpty()
        vm.onKey("\n SHIFA-DOC-2026 \n")
        vm.submit()
        vm.settle()
        assertThat(sent.single()).isEqualTo("""{"key":"SHIFA-DOC-2026"}""")
    }

    @Test
    fun `an unknown or used key gets one neutral banner`() {
        val vm = VerifyKeyViewModel(auth).also { created += it }
        vm.onKey("NOT-A-REAL-KEY")
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.banner).isEqualTo(CodeBanner(Res.string.invalidInviteKey))
        assertThat(vm.state.value.invite).isNull()
    }

    @Test
    fun `the purpose picks the form`() {
        body = """{"valid":true,"purpose":"CLINIC_RECEPTIONIST_INVITE","clinicId":7,"clinicName":"Shifa Dental","emailSentTo":" dilfuza@shifadental.uz "}"""
        val vm = VerifyKeyViewModel(auth).also { created += it }
        vm.onKey("K1")
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.invite).isEqualTo(Invite("K1", true, 7, "Shifa Dental", "dilfuza@shifadental.uz"))
        body = """{"valid":true,"purpose":"DOCTOR_REGISTRATION","clinicId":null,"clinicName":null,"emailSentTo":""}"""
        vm.onKey("K2")
        vm.submit()
        vm.settle()
        assertThat(vm.state.value.invite).isEqualTo(Invite("K2", false))
    }

    @Test
    fun `the key cannot change while it is checked, and a failure keeps its request id`() {
        hold = CompletableDeferred()
        status = HttpStatusCode.InternalServerError
        body = """{"error":"Internal Server Error","status":500,"code":"INTERNAL","requestId":"req-42"}"""
        val vm = VerifyKeyViewModel(auth).also { created += it }
        vm.onKey("K1")
        vm.submit()
        assertThat(vm.state.value.loading).isTrue()
        vm.onKey("K2")
        assertThat(vm.state.value.key).isEqualTo("K1")
        hold.complete(Unit)
        vm.settle()
        assertThat(vm.state.value.banner?.requestId).isEqualTo("req-42")
    }

    @Test
    fun `a key is tracked by its kind or why it failed, never the key`() {
        val tracker = RecordingTracker()
        val vm = VerifyKeyViewModel(auth, tracker).also { created += it }
        vm.onKey("NOT-A-REAL-KEY")
        vm.submit()
        vm.settle()
        body = """{"valid":true,"purpose":"CLINIC_RECEPTIONIST_INVITE","clinicId":7,"clinicName":"x","emailSentTo":null}"""
        vm.onKey("K1")
        vm.submit()
        vm.settle()
        assertThat(tracker.events)
            .containsExactly(
                AuthEvents.InviteKeyFailed("invalid_key"),
                AuthEvents.InviteKeyVerified("receptionist"),
            ).inOrder()
    }
}
