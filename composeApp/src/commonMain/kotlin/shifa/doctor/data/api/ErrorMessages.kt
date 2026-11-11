package shifa.doctor.data.api

import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountDisabledBanner
import shifa.doctor.generated.resources.doctorAccountExists
import shifa.doctor.generated.resources.maintenanceBody
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.noShowNotAllowed
import shifa.doctor.generated.resources.registrationOwnershipRequired
import shifa.doctor.generated.resources.sessionExpiredBanner
import shifa.doctor.generated.resources.somethingWentWrong
import shifa.doctor.generated.resources.tooManyRequests
import shifa.doctor.generated.resources.wrongOrExpiredCodeBackToCode

/**
 * 17 §1: the text for a failure comes from the backend's `code`, never from its English message. Codes a feature
 * handles itself (a `CONFLICT` on booking, `PAYMENT_REQUIRED` on video) are branched on in that feature; everything
 * else gets the call's own [fallback] ("Couldn't load patients"), or "Something went wrong" without one.
 */
fun Throwable.toUserMessage(fallback: StringResource? = null): StringResource {
    if (this is ApiException.NetworkError) return Res.string.networkError
    if (this is ApiException) {
        CODE_MESSAGES[code]?.let { return it }
        if (this is ApiException.RateLimit) return Res.string.tooManyRequests
    }
    return fallback ?: Res.string.somethingWentWrong
}

private val CODE_MESSAGES: Map<String, StringResource> =
    mapOf(
        "RATE_LIMITED" to Res.string.tooManyRequests,
        "ACCOUNT_DISABLED" to Res.string.accountDisabledBanner,
        "SESSION_EXPIRED" to Res.string.sessionExpiredBanner,
        "SESSION_INVALID" to Res.string.sessionExpiredBanner,
        "TOKEN_INVALID" to Res.string.sessionExpiredBanner,
        "UNAVAILABLE" to Res.string.maintenanceBody,
        // Registration (BE-29, BE-03) and the e-mailed codes that confirm it or an account deletion (BE-02).
        "DOCTOR_EXISTS" to Res.string.doctorAccountExists,
        "OWNERSHIP_REQUIRED" to Res.string.registrationOwnershipRequired,
        "EMAIL_OTP_INVALID" to Res.string.wrongOrExpiredCodeBackToCode,
        "EMAIL_OTP_EXPIRED" to Res.string.wrongOrExpiredCodeBackToCode,
        // BE-10: a visit outside its no-show window (from its start to a day after its end) or not confirmed.
        "NO_SHOW_NOT_ALLOWED" to Res.string.noShowNotAllowed,
    )

/**
 * The failure as one of a few fixed words, for tracking (docs/posthog): never the message, the backend's text or
 * the request id. A feature puts its own words first (`slot_taken`, `wrong_code`) and falls back to this.
 */
fun Throwable.toFailureReason(): String =
    when (this) {
        is ApiException.NetworkError -> if (timeout) "timeout" else "network"
        is ApiException -> CODE_REASONS[code] ?: statusReason()
        else -> "client"
    }

private fun ApiException.statusReason(): String =
    when (this) {
        is ApiException.ValidationError -> "validation"
        is ApiException.Unauthorized -> "unauthorized"
        is ApiException.Forbidden -> "forbidden"
        is ApiException.NotFound -> "not_found"
        is ApiException.Conflict -> "conflict"
        is ApiException.RateLimit -> "rate_limited"
        else -> "server"
    }

// The codes that mean the same on every route; a feature's own codes are that feature's to name.
private val CODE_REASONS: Map<String?, String> =
    mapOf(
        "RATE_LIMITED" to "rate_limited",
        "ACCOUNT_DISABLED" to "account_disabled",
        "SESSION_EXPIRED" to "session_expired",
        "SESSION_INVALID" to "session_expired",
        "TOKEN_INVALID" to "session_expired",
        "UNAVAILABLE" to "maintenance",
    )

/** The backend's trace id of a failed call, for the error banner's detail (17 §1); null for network failures. */
val Throwable.requestId: String? get() = (this as? ApiException)?.requestId
