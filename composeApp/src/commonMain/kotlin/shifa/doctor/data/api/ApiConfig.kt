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
        const val VERIFY_KEY = "auth/verify-key"
        const val CHECK_EXISTING_DOCTOR = "auth/check-existing-doctor"
        const val CHECK_EXISTING_PATIENT = "auth/check-existing-patient"
        const val REGISTER = "auth/register"
        const val REGISTER_CLINIC_STAFF = "auth/register-clinic-staff"
        const val DOCTOR_PROFILE = "doctors/me/profile"
        const val DOCTOR_CONTACT = "doctors/me/contact"
        const val DOCTOR_BILLING = "doctors/me/billing"
        const val DOCTOR_SETTINGS = "doctors/me/settings"
        const val DOCTOR_SERVICES = "doctors/me/services"
        const val SERVICE_GROUPS = "doctors/me/service-groups"
        const val CALENDAR = "calendar"

        fun appointment(id: Long) = "appointments/$id"

        fun appointmentChange(id: Long) = "appointments/$id/change"

        fun appointmentPaymentReminder(id: Long) = "appointments/$id/notify-payment-reminder"

        const val SUBSCRIPTION_PLANS = "doctor/subscription/plans"
        const val SUBSCRIPTION_ME = "doctor/subscription/me"
        const val SUBSCRIPTION_CHECKOUT = "doctor/subscription/checkout"
        const val STRIPE_CONNECT_ONBOARD = "doctor/payments/stripe/connect/onboard"

        fun doctorService(id: Long) = "doctors/me/services/$id"

        fun serviceGroup(id: Long) = "doctors/me/service-groups/$id"

        const val DOCTOR_PHOTO = "doctors/me/photo"
        const val DOCTOR_CERTIFICATE = "doctors/me/certificate"
        const val DOCTOR_LOCATIONS = "doctors/me/locations"
        const val ME_PROFILE = "me/profile"
        const val ME_CLINICS = "me/clinics"
        const val CHAT_UNREAD = "messages/unread-count"
        const val NOTIFICATIONS_UNREAD = "notifications/unread/count"
        const val TASKS = "tasks"
        const val PUBLIC_PROFESSIONS = "public/professions"
        const val PUBLIC_CLINICS = "public/clinics"
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
