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
import shifa.doctor.data.catalog.CatalogRepository
import shifa.doctor.data.config.BootGate
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CheckExistingPatientRequest
import shifa.doctor.data.model.DoctorContact
import shifa.doctor.data.model.DoctorSettings
import shifa.doctor.data.model.ProfileUpdateRequest
import shifa.doctor.data.model.RegisterRequest
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.utils.PickedImage
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
                    client.post("doctors/me/certificate") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat((reset.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
            client.post(ApiConfig.Endpoints.LOGIN) {
                contentType(ContentType.Application.Json)
                setBody("{}")
            }
            client.get("public/doctors")
            val write =
                apiCall<Unit> {
                    client.put("doctors/me/profile") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat(forwarded).containsExactly("POST /api/auth/login", "GET /api/public/doctors").inOrder()
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

    @Test
    fun `professions, clinics and the profile PATCH answer locally`() =
        runTest {
            val catalog = CatalogRepository({ client })
            assertThat(catalog.professions().getOrThrow().map { it.english }).contains("Dentist")
            assertThat(catalog.clinics("dental").getOrThrow().map { it.name }).containsExactly("Tashkent Demo Dental")
            assertThat(
                ProfileRepository { client }.update(ProfileUpdateRequest(address = "Chilonzor 5")).isSuccess,
            ).isTrue()
            val me = apiCall<JsonObject> { client.get(ApiConfig.Endpoints.DOCTOR_ME) }.getOrThrow()
            assertThat(me["profile"]!!.jsonObject["address"]!!.jsonPrimitive.content).isEqualTo("Chilonzor 5")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `contact, billing and settings are merged into the demo doctor, me_profile follows`() =
        runTest {
            val profile = ProfileRepository({ client })
            profile.updateSettings(DoctorSettings(defaultStartTab = "calendar"))
            profile.updateContact(DoctorContact(phone = "+998901112233", email = "new@shifa.dev"))
            val me = profile.refresh().getOrThrow()
            assertThat(me.settings.defaultStartTab).isEqualTo("calendar")
            assertThat(me.contact.email).isEqualTo("new@shifa.dev")
            assertThat(profile.meProfile().getOrThrow().firstName).isEqualTo("Aziza")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `a photo upload answers locally and shows in doctors_me`() =
        runTest {
            val profile = ProfileRepository({ client })
            val url = profile.uploadPhoto(PickedImage("photo.jpg", byteArrayOf(1, 2, 3))).getOrThrow()
            assertThat(url).contains("demo-photo.jpg?v=")
            assertThat(profile.refresh().getOrThrow().profile.photoUrl).endsWith("demo-photo.jpg")
            assertThat(forwarded).isEmpty()
        }

    @Test
    fun `the password change answers locally - the seed password is the current one`() =
        runTest {
            val auth = AuthRepository({ client })
            assertThat(auth.changePassword(DEMO_PASSWORD, "Abcdef1!").isSuccess).isTrue()
            assertThat(
                (auth.changePassword("wrong", "Abcdef1!").exceptionOrNull() as ApiException).status,
            ).isEqualTo(400)
            assertThat(forwarded).isEmpty()
        }
}
