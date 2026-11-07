package shifa.doctor.data.api

import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountDisabledBanner
import shifa.doctor.generated.resources.doctorAccountExists
import shifa.doctor.generated.resources.maintenanceBody
import shifa.doctor.generated.resources.networkError
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
    )

/** The backend's trace id of a failed call, for the error banner's detail (17 §1); null for network failures. */
val Throwable.requestId: String? get() = (this as? ApiException)?.requestId
