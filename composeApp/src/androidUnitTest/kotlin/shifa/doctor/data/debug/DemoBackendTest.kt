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
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.settings.LanguageRepository
import java.io.File

// Every endpoint the app calls has a handler (CLAUDE.md "Debug builds"); this test lists them, so a new endpoint
// without one is noticed here.
class DemoBackendTest {
    private val forwarded = mutableListOf<String>()
    private val demo =
        DemoBackend(
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
            client.get("public/config")
            val write =
                apiCall<Unit> {
                    client.put("doctors/me/profile") {
                        contentType(ContentType.Application.Json)
                        setBody("{}")
                    }
                }
            assertThat(forwarded).containsExactly("POST /api/auth/login", "GET /api/public/config").inOrder()
            assertThat((write.exceptionOrNull() as ApiException).code).isEqualTo("DEMO_WRITE_REFUSED")
        }
}
