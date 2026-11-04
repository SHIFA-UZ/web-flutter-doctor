package shifa.doctor.data.model

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/**
 * `TokenResponse`. [forcePasswordReset] is set only on patient accounts a doctor created (backend
 * `PatientAccountService`); the doctor app reads it but has nothing to do with it, as in Flutter.
 */
@Serializable
data class TokenResponse(
    val token: String,
    val forcePasswordReset: Boolean = false,
)

@Serializable
data class SendLoginOtpRequest(
    val email: String,
)

@Serializable
data class VerifyEmailOtpRequest(
    val email: String,
    val code: String,
)

@Serializable
data class SendForgotPasswordOtpRequest(
    val identifier: String,
    val app: String,
)
