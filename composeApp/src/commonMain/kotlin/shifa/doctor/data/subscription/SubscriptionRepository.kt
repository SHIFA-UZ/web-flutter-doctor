package shifa.doctor.data.subscription

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient

/** `GET /doctor/subscription/plans` (dev 2026-11-04: BASIC and PRO, monthly, EUR); PREMIUM is set by admins only. */
@Serializable
data class SubscriptionPlan(
    val code: String = "",
    val name: String = "",
    val priceMinor: Long = 0,
    val currency: String = "",
    val interval: String = "MONTH",
) {
    val tier: DoctorTier? get() = DoctorTier.entries.firstOrNull { it.name.equals(code, ignoreCase = true) }
}

/** `GET /doctor/subscription/me`: the paid subscription, if any (dev 2026-11-04: an empty answer for admin-set plans). */
@Serializable
data class DoctorSubscriptionInfo(
    val planCode: String? = null,
    val status: String? = null,
    val currentPeriodEnd: String? = null,
)

@Serializable
private data class CheckoutRequest(
    val planCode: String,
    val gateway: String = "STRIPE",
)

@Serializable
private data class CheckoutAnswer(
    val checkoutUrl: String,
)

@Serializable
private class OnboardRequest

@Serializable
private data class OnboardAnswer(
    val onboardingUrl: String,
)

/** SUB-02 (08 §1.7, §5.3): the plans, a Stripe checkout for one, and Stripe Connect onboarding; both pages are hosted. */
class SubscriptionRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun plans(): Result<List<SubscriptionPlan>> =
        apiCall<List<SubscriptionPlan>> { client().get(ApiConfig.Endpoints.SUBSCRIPTION_PLANS) }

    /** `POST /doctor/subscription/checkout {planCode, gateway: STRIPE}` → the Stripe page to open. */
    suspend fun checkout(planCode: String): Result<String> =
        apiCall<CheckoutAnswer> {
            client().post(ApiConfig.Endpoints.SUBSCRIPTION_CHECKOUT) {
                contentType(ContentType.Application.Json)
                setBody(CheckoutRequest(planCode))
            }
        }.mapCatching { it.checkoutUrl.ifBlank { error("empty checkout URL") } }

    /** `POST /doctor/payments/stripe/connect/onboard {}` → the Stripe onboarding page (new or resumed). */
    suspend fun connectOnboarding(): Result<String> =
        apiCall<OnboardAnswer> {
            client().post(ApiConfig.Endpoints.STRIPE_CONNECT_ONBOARD) {
                contentType(ContentType.Application.Json)
                setBody(OnboardRequest())
            }
        }.mapCatching { it.onboardingUrl.ifBlank { error("empty onboarding URL") } }

    /** The paid subscription; null when there is none (the backend answers with an empty body). */
    suspend fun mine(): Result<DoctorSubscriptionInfo?> =
        apiCall<kotlinx.serialization.json.JsonElement?> { client().get(ApiConfig.Endpoints.SUBSCRIPTION_ME) }.map {
                json ->
            json?.takeIf { it is kotlinx.serialization.json.JsonObject }?.let {
                shifa.doctor.data.api.apiJson.decodeFromJsonElement(DoctorSubscriptionInfo.serializer(), it)
            }
        }
}
