package shifa.doctor.data.auth

import io.ktor.client.HttpClient
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.LoginRequest
import shifa.doctor.data.model.TokenResponse

/** The sign-in calls (01 §2). They go without a token (`auth/` is public); the session repository keeps the result. */
class AuthRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * `POST /auth/login?app=doctor` (01 §2.1). With `app=doctor` the backend issues a DOCTOR or CLINIC_STAFF token, or
     * refuses with 403 when the account has neither; without it, a doctor whose main role is PATIENT would get a
     * patient token. The password is sent exactly as typed (Flutter trimmed it).
     */
    suspend fun login(
        username: String,
        password: String,
    ): Result<String> =
        apiCall<TokenResponse> {
            client().post(ApiConfig.Endpoints.LOGIN) {
                parameter("app", APP)
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username.trim(), password))
            }
        }.map { it.token }

    private companion object {
        const val APP = "doctor"
    }
}
