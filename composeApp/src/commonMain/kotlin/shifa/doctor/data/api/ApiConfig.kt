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
        const val SCHEDULE_BOOK = "schedule/book"
        const val SCHEDULE_RULES = "schedule/rules"
        const val SCHEDULE_VALIDITY_PERIODS = "schedule/validity-periods"
        const val SCHEDULE_VALID_RANGE = "schedule/valid-range"
        const val SCHEDULE_DATE_SPECIFIC = "schedule/date-specific"

        fun scheduleDateSpecific(id: Long) = "schedule/date-specific/$id"

        const val SCHEDULE_BLOCKS = "schedule/blocks"
        const val SCHEDULE_BLOCKS_OVERLAP = "schedule/blocks/overlapping-count"

        fun scheduleBlock(id: Long) = "schedule/blocks/$id"

        const val PATIENTS_FOR_ASSIGNMENT = "patients/for-assignment"
        const val PATIENTS = "patients"
        const val PROPHYLAXIS_SETTINGS = "prophylaxis/settings"

        fun patient(id: Long) = "patients/$id"

        fun patientAppointments(id: Long) = "patients/$id/appointments"

        fun patientCreateAccount(id: Long) = "patients/$id/create-account"

        fun patientTestSms(id: Long) = "patients/$id/send-test-sms"

        fun patientDocuments(id: Long) = "patients/$id/documents"

        fun patientDocument(
            id: Long,
            documentId: Long,
        ) = "patients/$id/documents/$documentId"

        fun patientForms(id: Long) = "patients/$id/forms"

        fun patientForm(
            id: Long,
            formId: Long,
        ) = "patients/$id/forms/$formId"

        const val ICD10_SEARCH = "icd10/search"
        const val CLINICAL_ENGINE = "clinical-engine"

        fun patientDocumentDownload(
            id: Long,
            documentId: Long,
        ) = "patients/$id/documents/$documentId/download"

        fun patientDocumentRequestAccess(
            id: Long,
            documentId: Long,
        ) = "patients/$id/documents/$documentId/request-access"

        fun appointment(id: Long) = "appointments/$id"

        fun appointmentChange(id: Long) = "appointments/$id/change"

        fun appointmentPaymentReminder(id: Long) = "appointments/$id/notify-payment-reminder"

        fun appointmentSignature(id: Long) = "appointments/$id/request-signature"

        const val AI_TRANSCRIBE = "ai/transcribe"
        const val SCRIBE_UPLOAD = "consultations/upload-recording"
        const val AI_TRANSCRIPTION_FEEDBACK = "ai/transcription-feedback"

        fun appointmentBriefing(id: Long) = "appointments/$id/briefing"

        fun appointmentBriefingRetry(id: Long) = "appointments/$id/briefing/retry"

        fun consultationNotes(id: Long) = "appointments/$id/consultation-notes"

        fun dentalDocumentation(id: Long) = "appointments/$id/dental-documentation"

        const val VIDEO_TOKEN = "video/token"
        const val TREATMENT_PLANS = "treatment-plans"
        const val PLAN_SERVICES = "treatment-plans/plan-services"

        fun clinicPayments(clinicId: Long) = "clinics/$clinicId/finance/payments"

        fun treatmentPlanDetail(id: Long) = "treatment-plans/$id/detail"

        fun fulfillmentCandidates(id: Long) = "treatment-plans/$id/fulfillment-candidates"

        fun fulfillPlan(
            planId: Long,
            appointmentId: Long,
        ) = "treatment-plans/$planId/appointments/$appointmentId/fulfill"

        fun draftNotes(id: Long) = "appointments/$id/draft-notes"

        fun appointmentComplete(id: Long) = "appointments/$id/complete"

        fun scribeStatus(id: Long) = "appointments/$id/scribe-status"

        fun draftConfirm(id: String) = "ai/draft/$id/confirm"

        fun draftDiscard(id: String) = "ai/draft/$id/discard"

        const val SUBSCRIPTION_PLANS = "doctor/subscription/plans"
        const val SUBSCRIPTION_ME = "doctor/subscription/me"
        const val SUBSCRIPTION_CHECKOUT = "doctor/subscription/checkout"
        const val STRIPE_CONNECT_ONBOARD = "doctor/payments/stripe/connect/onboard"

        fun doctorService(id: Long) = "doctors/me/services/$id"

        fun serviceGroup(id: Long) = "doctors/me/service-groups/$id"

        const val DOCTOR_PHOTO = "doctors/me/photo"
        const val DOCTOR_CERTIFICATE = "doctors/me/certificate"
        const val DOCTOR_LOCATIONS = "doctors/me/locations"

        fun doctorLocation(id: Long) = "doctors/me/locations/$id"

        const val ME_PROFILE = "me/profile"
        const val ME_CLINICS = "me/clinics"
        const val CHAT_UNREAD = "messages/unread-count"
        const val NOTIFICATIONS_UNREAD = "notifications/unread/count"
        const val TASKS = "tasks"

        // CHAT-01 (15 §2.8).
        const val CONVERSATIONS = "messages/conversations"
        const val CONVERSATION_START = "messages/conversations/start"
        const val MESSAGE_SEND = "messages/send"
        const val CHAT_SEARCH = "messages/search"
        const val CHAT_UPLOAD = "messages/upload-attachment"

        fun conversation(id: Long) = "messages/conversations/$id"

        fun conversationRead(id: Long) = "messages/conversations/$id/read"

        // NOTIF-01 (15 §2.9; the paged list, newest first, is on dev and prod).
        const val NOTIFICATIONS_PAGE = "notifications/page"
        const val NOTIFICATIONS_READ_ALL = "notifications/read-all"

        fun notificationRead(id: Long) = "notifications/$id/read"

        // TASK-01 (15 §2.10; `TASKS` is above).
        const val TASK_TEMPLATES = "tasks/templates"

        fun task(id: Long) = "tasks/$id"

        fun taskCheckIns(id: Long) = "tasks/$id/check-ins"

        fun taskCancel(id: Long) = "tasks/$id/cancel"

        // ANALYTICS-01 (15 §2.3).
        const val ANALYTICS_OVERVIEW = "doctor/analytics/overview"
        const val ANALYTICS_TREND = "doctor/analytics/appointments-trend"
        const val ANALYTICS_TYPES = "doctor/analytics/consultation-types"
        const val ANALYTICS_ENGAGEMENT = "doctor/analytics/engagement"
        const val ANALYTICS_SMS = "doctor/analytics/sms-usage"
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
