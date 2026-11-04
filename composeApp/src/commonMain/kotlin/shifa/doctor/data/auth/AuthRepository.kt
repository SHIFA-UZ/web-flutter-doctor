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
import shifa.doctor.data.model.SendForgotPasswordOtpRequest
import shifa.doctor.data.model.SendLoginOtpRequest
import shifa.doctor.data.model.TokenResponse
import shifa.doctor.data.model.VerifyEmailOtpRequest

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

    /** `POST /auth/send-login-otp` (01 §2.2): a 6-digit code by e-mail. */
    suspend fun sendLoginOtp(email: String): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.SEND_LOGIN_OTP) {
                contentType(ContentType.Application.Json)
                setBody(SendLoginOtpRequest(email.trim()))
            }
        }

    /** `POST /auth/verify-email-otp` (01 §2.2): the code for a session token. */
    suspend fun verifyEmailOtp(
        email: String,
        code: String,
    ): Result<String> =
        apiCall<TokenResponse> {
            client().post(ApiConfig.Endpoints.VERIFY_EMAIL_OTP) {
                contentType(ContentType.Application.Json)
                setBody(VerifyEmailOtpRequest(email.trim(), code))
            }
        }.map { it.token }

    /**
     * `POST /auth/send-forgot-password-otp` (01 §2.4) with `app=doctor`, so the code is for the doctor account and not a
     * patient account on the same address.
     */
    suspend fun sendForgotPasswordOtp(email: String): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.SEND_FORGOT_PASSWORD_OTP) {
                contentType(ContentType.Application.Json)
                setBody(SendForgotPasswordOtpRequest(email.trim(), APP))
            }
        }

    private companion object {
        const val APP = "doctor"
    }
}
