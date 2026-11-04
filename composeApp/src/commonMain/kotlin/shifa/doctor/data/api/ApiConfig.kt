package shifa.doctor.data.api

/** 15 §1 and 17 §1. Paths are written without `/api/`; [BASE_URL] ends with it. */
object ApiConfig {
    const val BASE_URL = "https://api.shifa.uz/api/"
    const val DEV_BASE_URL = "https://api-dev.shifa.uz/api/"
    const val TIMEOUT_MS = 30_000L

    /** 01 §3 pre-flight: a token whose `exp` is less than this far ahead is treated as expired. */
    const val EXPIRY_GRACE_SECONDS = 30L

    object Endpoints {
        const val LOGIN = "auth/login"
        const val SEND_LOGIN_OTP = "auth/send-login-otp"
        const val VERIFY_EMAIL_OTP = "auth/verify-email-otp"
        const val SEND_FORGOT_PASSWORD_OTP = "auth/send-forgot-password-otp"
        const val FORGOT_PASSWORD_RESET = "auth/forgot-password-reset"
        const val RESET_PASSWORD = "auth/reset-password"
        const val DOCTOR_ME = "doctors/me"
        const val DOCTOR_LANGUAGE = "doctors/me/language"
        const val PUBLIC_CONFIG = "public/config"
    }

    /**
     * Whether a backend path gets the stored token (backend `SecurityConfig`): only the API under `/api/`, minus its
     * public part: `auth/` except `auth/reset-password` (changing the password while signed in), and `public/`. Files
     * (`/patientdocuments/…`, signed URLs) and `/v3/api-docs` never get it.
     */
    fun needsToken(encodedPath: String): Boolean {
        if (!encodedPath.startsWith("/api/")) return false
        val path = encodedPath.removePrefix("/api/")
        return !((path.startsWith("auth/") && path != Endpoints.RESET_PASSWORD) || path.startsWith("public/"))
    }
}
