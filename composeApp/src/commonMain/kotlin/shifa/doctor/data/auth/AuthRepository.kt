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
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CheckExistingDoctorRequest
import shifa.doctor.data.model.CheckExistingPatientRequest
import shifa.doctor.data.model.CheckExistingPatientResponse
import shifa.doctor.data.model.ForgotPasswordResetRequest
import shifa.doctor.data.model.LoginRequest
import shifa.doctor.data.model.RegisterRequest
import shifa.doctor.data.model.SendForgotPasswordOtpRequest
import shifa.doctor.data.model.SendLoginOtpRequest
import shifa.doctor.data.model.TokenResponse
import shifa.doctor.data.model.VerifyEmailOtpRequest
import shifa.doctor.data.model.VerifyKeyRequest
import shifa.doctor.data.model.VerifyKeyResponse

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

    /** `POST /auth/forgot-password-reset` (01 §2.4): the e-mailed code and the new password, for a session token. */
    suspend fun resetForgottenPassword(
        email: String,
        code: String,
        newPassword: String,
    ): Result<String> =
        apiCall<TokenResponse> {
            client().post(ApiConfig.Endpoints.FORGOT_PASSWORD_RESET) {
                contentType(ContentType.Application.Json)
                setBody(ForgotPasswordResetRequest(email.trim(), code, newPassword, APP))
            }
        }.map { it.token }

    /** `POST /auth/verify-key` (01 §2.5). Null when the key is unknown or already used (`valid = false`). */
    suspend fun verifyKey(key: String): Result<Invite?> =
        apiCall<VerifyKeyResponse> {
            client().post(ApiConfig.Endpoints.VERIFY_KEY) {
                contentType(ContentType.Application.Json)
                setBody(VerifyKeyRequest(key))
            }
        }.map { r ->
            r.takeIf { it.valid }?.let {
                Invite(
                    key,
                    it.purpose == RECEPTIONIST_INVITE,
                    it.clinicId,
                    it.clinicName,
                    it.emailSentTo?.trim()?.ifEmpty { null },
                )
            }
        }

    /**
     * `POST /auth/check-existing-doctor`: true when a doctor account already uses this e-mail or phone, so registration
     * stops on step 1 instead of failing (or, worse, re-keying that account: BE-29) at the end (flow 01/07 §8). Ask
     * for one identifier per call: given both, the backend looks at the phone and stops there when it finds a patient.
     */
    suspend fun isDoctor(
        email: String? = null,
        phone: String? = null,
    ): Result<Boolean> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.CHECK_EXISTING_DOCTOR) {
                contentType(ContentType.Application.Json)
                setBody(CheckExistingDoctorRequest(email, phone))
            }
        }.map { true }.recoverCatching { if (it is ApiException.NotFound) false else throw it }

    /** `POST /auth/check-existing-patient` (01 §2.6): the patient this doctor account would be added to, or null. */
    suspend fun existingPatient(request: CheckExistingPatientRequest): Result<ExistingPatient?> =
        apiCall<CheckExistingPatientResponse> {
            client().post(ApiConfig.Endpoints.CHECK_EXISTING_PATIENT) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.map { r -> r.takeIf { it.found }?.let { ExistingPatient(it.fullName, it.photoUrl?.ifBlank { null }) } }

    /** `POST /auth/register` (01 §2.6): the new doctor's token. The password is sent exactly as typed. */
    suspend fun register(request: RegisterRequest): Result<String> =
        apiCall<TokenResponse> {
            client().post(ApiConfig.Endpoints.REGISTER) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.map { it.token }

    /**
     * `POST /auth/register-clinic-staff` (01 §2.7): a receptionist's token (CLINIC_STAFF), same body as [register]. The
     * backend refuses any address but the invited one when the invitation names it.
     */
    suspend fun registerClinicStaff(request: RegisterRequest): Result<String> =
        apiCall<TokenResponse> {
            client().post(ApiConfig.Endpoints.REGISTER_CLINIC_STAFF) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.map { it.token }

    private companion object {
        const val RECEPTIONIST_INVITE = "CLINIC_RECEPTIONIST_INVITE"
        const val APP = "doctor"
    }
}
