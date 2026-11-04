package shifa.doctor.data.debug

import com.google.common.truth.Truth.assertThat
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.createHttpClient
import shifa.doctor.data.api.jwt
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.config.BootGate
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CheckExistingPatientRequest
import shifa.doctor.data.model.RegisterRequest
import shifa.doctor.data.settings.LanguageRepository
import java.io.File

// Every endpoint the app calls has a handler (CLAUDE.md "Debug builds"); this test lists them, so a new endpoint
// without one is noticed here.
class DemoBackendTest {
    private val forwarded = mutableListOf<String>()
    private var gate = DemoBootGate.None
    private val demo =
        DemoBackend(
            bootGate = { gate },
            passthrough = {
                HttpClient(
                    MockEngine {
                        forwarded += "${it.method.value} ${it.url.encodedPath}"
                        respond("{}", HttpStatusCode.OK)
                    },
                )
            },
            fixture = { File("src/commonMain/composeResources/files/demo/$it").readText() },
        )
    private val client = createHttpClient({ jwt(4_000_000_000L) }, demo.engine(), ApiConfig.BASE_URL)

    @Test
    fun `doctors_me answers from the scrubbed fixture`() =
        runTest {
            val me = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.DOCTOR_ME) }.getOrThrow()
            assertThat(me["profile"]!!.jsonObject["firstName"]!!.jsonPrimitive.content).isEqualTo("Aziza")
            assertThat(me["contact"]!!.jsonObject["email"]!!.jsonPrimitive.content).isEqualTo("demo.doctor@shifa.dev")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the language sync is stored and shows in doctors_me`() =
        runTest {
            assertThat(LanguageRepository { client }.sync("ru").isSuccess).isTrue()
            val me = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.DOCTOR_ME) }.getOrThrow()
            assertThat(me["settings"]!!.jsonObject["language"]!!.jsonPrimitive.content).isEqualTo("ru")
        }

    @Test
    fun `sign-in and unknown GETs reach the real backend, every other write is refused`() =
        runTest {
            val reset =
                apiCall<Unit> {
                    client.post(ApiConfig.Endpoints.RESET_PASSWORD) {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat((reset.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
            client.post(ApiConfig.Endpoints.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            client.get("public/professions")
            val write =
                apiCall<Unit> {
                    client.put("doctors/me/profile") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat(forwarded).containsExactly("POST /api/auth/login", "GET /api/public/professions").inOrder()
            assertThat((write.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
        }

    @Test
    fun `public_config carries the splash gate the debug menu picked`() =
        runTest {
            val config = PublicConfigRepository({ client }, "1.0.1", "android")
            assertThat(config.bootGate("en").getOrNull()).isEqualTo(BootGate.None)
            gate = DemoBootGate.Maintenance
            assertThat(config.bootGate("en").getOrNull()).isEqualTo(BootGate.Maintenance(null))
            gate = DemoBootGate.Update
            assertThat(config.bootGate("en").getOrNull()).isInstanceOf(BootGate.Update::class.java)
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `verify-key opens both registrations from the demo keys and refuses others`() =
        runTest {
            val auth = AuthRepository { client }
            assertThat(auth.verifyKey(DemoBackend.DEMO_DOCTOR_KEY).getOrThrow()).isEqualTo(Invite("DEMO-DOCTOR", false))
            val reception = auth.verifyKey("demo-reception").getOrThrow()!!
            assertThat(reception.receptionist).isTrue()
            assertThat(reception.email).isEqualTo("demo.reception@shifa.dev")
            assertThat(auth.verifyKey("NOPE").getOrThrow()).isNull()
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the registration checks answer locally, register itself is refused`() =
        runTest {
            val auth = AuthRepository { client }
            assertThat(auth.isDoctor(email = "DEMO.DOCTOR@shifa.dev").getOrThrow()).isTrue()
            assertThat(auth.isDoctor(phone = "90 000 00 00").getOrThrow()).isTrue()
            assertThat(auth.isDoctor(email = "new@shifa.dev").getOrThrow()).isFalse()
            val patient = CheckExistingPatientRequest("A", "B", DemoBackend.DEMO_PATIENT_EMAIL)
            assertThat(auth.existingPatient(patient).getOrThrow()).isNotNull()
            assertThat(auth.existingPatient(patient.copy(email = "new@shifa.dev")).getOrThrow()).isNull()
            val register = auth.register(RegisterRequest("A", "B", "new@shifa.dev", "Secret1!", "DEMO-DOCTOR"))
            assertThat((register.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
            assertThat(forwarded).isEmpty()
        }
}
