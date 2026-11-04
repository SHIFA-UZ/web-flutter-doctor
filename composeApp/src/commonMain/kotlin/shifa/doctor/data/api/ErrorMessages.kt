package shifa.doctor.data.api

import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountDisabledBanner
import shifa.doctor.generated.resources.maintenanceBody
import shifa.doctor.generated.resources.networkError
import shifa.doctor.generated.resources.sessionExpiredBanner
import shifa.doctor.generated.resources.somethingWentWrong
import shifa.doctor.generated.resources.tooManyRequests

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
    )
