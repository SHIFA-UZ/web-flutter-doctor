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
    // Missing reads as "": the call succeeded but there is nothing to sign in with (flow 01/10 §8).
    val token: String = "",
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

@Serializable
data class ForgotPasswordResetRequest(
    val email: String,
    val emailOtp: String,
    val newPassword: String,
    val app: String,
)

@Serializable
data class VerifyKeyRequest(
    val key: String,
)

@Serializable
data class VerifyKeyResponse(
    val valid: Boolean = false,
    val purpose: String? = null,
    val clinicId: Long? = null,
    val clinicName: String? = null,
    val emailSentTo: String? = null,
)

@Serializable
data class CheckExistingDoctorRequest(
    val email: String? = null,
    val phone: String? = null,
)

@Serializable
data class CheckExistingPatientRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val phone: String? = null,
)

@Serializable
data class CheckExistingPatientResponse(
    val found: Boolean = false,
    val fullName: String? = null,
    val photoUrl: String? = null,
    val email: String? = null,
)

@Serializable
data class RegisterRequest(
    val firstName: String,
    val lastName: String,
    val email: String,
    val password: String,
    val key: String,
    val phone: String? = null,
    val timeZone: String? = null,
    /** The registration code sent to [email] (BE-03); it also proves an existing account at that address is yours. */
    val emailOtp: String? = null,
    /** An existing account's current password: its proof when the code can't be (an account found by phone, BE-29). */
    val currentPassword: String? = null,
)

/** `POST /auth/send-email-otp`. */
@Serializable
data class SendEmailOtpRequest(
    val email: String,
    val purpose: String,
)
