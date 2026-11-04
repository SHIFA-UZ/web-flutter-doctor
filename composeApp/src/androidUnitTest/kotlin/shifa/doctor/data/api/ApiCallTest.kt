package shifa.doctor.data.api

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Test
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountDisabledBanner
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.requestFailed
import shifa.doctor.generated.resources.sessionExpiredBanner
import shifa.doctor.generated.resources.somethingWentWrong
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

@Serializable
private data class Me(
    val id: String,
)

private const val NOW = 1_800_000_000L

@OptIn(ExperimentalEncodingApi::class)
internal fun jwt(
    exp: Long,
    role: String = "DOCTOR",
): String {
    val enc = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
    return "${enc.encode(
        "{}".encodeToByteArray(),
    )}.${enc.encode("""{"exp":$exp,"role":"$role"}""".encodeToByteArray())}.sig"
}

class ApiCallTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        token: String? = jwt(NOW + 3600),
        respond: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = createHttpClient(
        { token },
        MockEngine {
            requests += it
            respond(it)
        },
        "https://api.shifa.uz/api/",
        { NOW },
    )

    private fun error(
        status: HttpStatusCode,
        body: String,
    ): suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { respond(body, status, json) }

    @Test
    fun `a success decodes, numeric ids leniently`() =
        runTest {
            val result = apiCall<Me> { client { respond("""{"id":42}""", HttpStatusCode.OK, json) }.get("doctors/me") }
            assertThat(result.getOrThrow()).isEqualTo(Me("42"))
            assertThat(requests.single().headers[HttpHeaders.Authorization]).startsWith("Bearer ")
        }

    @Test
    fun `a 401 to a request with a token ends the session and keeps code and requestId`() =
        runTest {
            SessionEvents.ended.test {
                val body = """{"error":"Authentication required","code":"UNAUTHORIZED","requestId":"r-1"}"""
                val e =
                    apiCall<Me> {
                        client(
                            respond = error(HttpStatusCode.Unauthorized, body),
                        ).get("doctors/me")
                    }.exceptionOrNull()
                assertThat(e).isInstanceOf(ApiException.Unauthorized::class.java)
                assertThat((e as ApiException).code).isEqualTo("UNAUTHORIZED")
                assertThat(e.requestId).isEqualTo("r-1")
                assertThat(awaitItem()).isEqualTo(SessionEndReason.Unauthorized)
            }
        }

    @Test
    fun `a 401 on a public path (wrong password) does not end the session`() =
        runTest {
            SessionEvents.ended.test {
                val e =
                    apiCall<Me> {
                        client(
                            respond = error(HttpStatusCode.Unauthorized, """{"code":"UNAUTHORIZED"}"""),
                        ).post("auth/login")
                    }
                assertThat(e.exceptionOrNull()).isInstanceOf(ApiException.Unauthorized::class.java)
                assertThat(requests.single().headers[HttpHeaders.Authorization]).isNull()
                expectNoEvents()
            }
        }

    @Test
    fun `a 403 never ends the session`() =
        runTest {
            SessionEvents.ended.test {
                val e =
                    apiCall<Me> {
                        client(
                            respond = error(HttpStatusCode.Forbidden, """{"code":"FORBIDDEN"}"""),
                        ).get("doctors/me")
                    }
                assertThat(e.exceptionOrNull()).isInstanceOf(ApiException.Forbidden::class.java)
                expectNoEvents()
            }
        }

    @Test
    fun `ACCOUNT_DISABLED ends the session with its reason, whatever the status`() =
        runTest {
            SessionEvents.ended.test {
                apiCall<Me> {
                    client(
                        respond = error(HttpStatusCode.Forbidden, """{"code":"ACCOUNT_DISABLED"}"""),
                    ).get("doctors/me")
                }
                assertThat(awaitItem()).isEqualTo(SessionEndReason.AccountDisabled)
            }
        }

    @Test
    fun `an expired token ends the session before any request`() =
        runTest {
            SessionEvents.ended.test {
                val e =
                    apiCall<Me> {
                        client(
                            token = jwt(NOW - 31),
                        ) { error("not sent") }.get("doctors/me")
                    }.exceptionOrNull()
                assertThat(e).isInstanceOf(ApiException.Unauthorized::class.java)
                assertThat((e as ApiException).code).isEqualTo("SESSION_EXPIRED")
                assertThat(requests).isEmpty()
                assertThat(awaitItem()).isEqualTo(SessionEndReason.Expired)
            }
        }

    @Test
    fun `the token goes to the backend only, never over an explicit Authorization, never on public paths`() =
        runTest {
            val c = client { respond("{}", HttpStatusCode.OK, json) }
            c.get("https://files.example.com/x.pdf")
            c.get("https://api.shifa.uz/patientdocuments/x.pdf")
            c.post("auth/verify") { header(HttpHeaders.Authorization, "Bearer firebase") }
            c.get("public/config")
            c.post("auth/reset-password")
            assertThat(requests.map { it.headers[HttpHeaders.Authorization]?.take(15) })
                .containsExactly(null, null, "Bearer firebase", null, "Bearer ${jwt(NOW + 3600)}".take(15))
                .inOrder()
        }

    @Test
    fun `an HTML error page and a numeric error field are read safely`() =
        runTest {
            val html =
                apiCall<Me> {
                    client {
                        respond(
                            "<html>Bad gateway</html>",
                            HttpStatusCode.BadGateway,
                            headersOf(HttpHeaders.ContentType, "text/html"),
                        )
                    }
                        .get("doctors/me")
                }.exceptionOrNull() as ApiException
            assertThat(html).isInstanceOf(ApiException.ServerError::class.java)
            assertThat(html.code).isNull()

            val body =
                """{"error":404,"message":"Appointment not found: 9","code":"NOT_FOUND","requestId":"r-2"}"""
            val notFound =
                apiCall<Me> {
                    client(respond = error(HttpStatusCode.NotFound, body)).get("appointments/9")
                }.exceptionOrNull() as ApiException
            assertThat(notFound).isInstanceOf(ApiException.NotFound::class.java)
            assertThat(notFound.code).isEqualTo("NOT_FOUND")
            assertThat(notFound.message).doesNotContain("Appointment") // no server text: it can name a patient
        }

    @Test
    fun `no response is a NetworkError`() =
        runTest {
            val e = apiCall<Me> { client { throw IOException("offline") }.get("doctors/me") }.exceptionOrNull()
            assertThat(e).isInstanceOf(ApiException.NetworkError::class.java)
        }

    @Test
    fun `user messages come from the code, else the call's fallback`() {
        assertThat(ApiException.NetworkError(false).toUserMessage()).isEqualTo(Res.string.networkError)
        assertThat(
            ApiException.of(403, "ACCOUNT_DISABLED", null).toUserMessage(),
        ).isEqualTo(Res.string.accountDisabledBanner)
        assertThat(
            ApiException.of(401, "SESSION_EXPIRED", null).toUserMessage(),
        ).isEqualTo(Res.string.sessionExpiredBanner)
        assertThat(
            ApiException.of(400, "VALIDATION", null).toUserMessage(Res.string.requestFailed),
        ).isEqualTo(Res.string.requestFailed)
        assertThat(IllegalStateException().toUserMessage()).isEqualTo(Res.string.somethingWentWrong)
    }

    @Test
    fun `logged paths mask ids and file names`() {
        assertThat(maskIds("/api/patients/123/documents/scan.pdf")).isEqualTo("/api/patients/{id}/documents/{id}")
        assertThat(maskIds("/api/doctors/me/language")).isEqualTo("/api/doctors/me/language")
    }
}
