package shifa.doctor.data.push

import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.session.ROLE_CLINIC_STAFF
import shifa.doctor.data.session.ROLE_DOCTOR
import shifa.doctor.data.session.Session
import java.util.concurrent.CopyOnWriteArrayList

class PushRegistrationTest {
    private val sent = CopyOnWriteArrayList<String>()
    private var status = HttpStatusCode.NoContent
    private val engine =
        MockEngine { req ->
            val body = req.body.toByteArray().decodeToString()
            sent += "${req.method.value} ${req.url.encodedPath} ${req.headers[HttpHeaders.Authorization]} $body".trim()
            respond("", status)
        }

    // No session token in the client (a test token is no JWT; the client checks expiry before sending).
    private val client = createHttpClient({ null }, engine)
    private val doctor = Session("session-b", ROLE_DOCTOR)
    private val device = Push.Device("fcm:1", "android")

    private fun classToTest() = PushRegistration({ client }, CoroutineScope(Dispatchers.IO))

    @Test
    fun `a device is registered once per session and token, again for a new token or session`() =
        runTest {
            val classToTest = classToTest()
            classToTest.register(doctor, device)
            classToTest.register(doctor, device)
            classToTest.register(doctor, device.copy(token = "fcm:2"))
            classToTest.register(doctor.copy(token = "session-c"), device.copy(token = "fcm:2"))
            assertThat(sent.filter { it.startsWith("POST") }).hasSize(3)
            assertThat(sent.first())
                .isEqualTo(
                    """POST /api/doctors/me/fcm-tokens null {"token":"fcm:1","platform":"android"}""",
                )
        }

    @Test
    fun `nothing is sent signed out, for clinic staff or without a token, and a failure is tried again`() =
        runTest {
            val classToTest = classToTest()
            classToTest.register(null, device)
            classToTest.register(Session("staff", ROLE_CLINIC_STAFF), device)
            classToTest.register(doctor, null)
            assertThat(sent).isEmpty()
            status = HttpStatusCode.InternalServerError
            assertThat(classToTest.register(doctor, device).isFailure).isTrue()
            status = HttpStatusCode.NoContent
            assertThat(classToTest.register(doctor, device).isSuccess).isTrue()
            assertThat(sent).hasSize(2)
        }

    @Test
    fun `signing out removes this device with the ending session's token, and the next sign-in registers it again`() =
        runTest {
            val classToTest = classToTest()
            classToTest.register(doctor, device)
            // The client's own session is already another (or none): the request carries the one that ended.
            classToTest.signedOut("session-old", device.token)
            repeat(200) { if (sent.none { it.startsWith("DELETE") }) Thread.sleep(10) }
            assertThat(sent.last()).isEqualTo("DELETE /api/doctors/me/fcm-tokens/fcm:1 Bearer session-old")
            classToTest.register(doctor, device)
            assertThat(sent.count { it.startsWith("POST") }).isEqualTo(2)
        }

    @Test
    fun `signing straight back in registers after the sign-out's removal, never before it`() =
        runTest {
            val classToTest = classToTest()
            classToTest.register(doctor, device)
            classToTest.signedOut(doctor.token, device.token)
            classToTest.register(doctor.copy(token = "session-new"), device)
            val order = sent.map { it.substringBefore(" ") }
            assertThat(order).containsExactly("POST", "DELETE", "POST").inOrder()
        }
}
