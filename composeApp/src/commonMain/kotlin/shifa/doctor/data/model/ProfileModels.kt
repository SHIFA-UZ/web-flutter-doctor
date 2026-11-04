package shifa.doctor.data.model

import kotlinx.serialization.Serializable
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.httpLogger

/**
 * `PATCH /doctors/me/profile`: only the keys that are set are sent (the client's `explicitNulls = false`) and the
 * backend ignores null ones (`DoctorController.patchProfile`), so this is a partial update; clearing a text field
 * means sending "". [dob] is `YYYY-MM-DD`; [gender] the English `Male` / `Female` / `Other`; [profession] the English
 * labels joined with ", "; [services] and [certificates] JSON arrays in a string (see [DoctorProfile.serviceTags]).
 */
@Serializable
data class ProfileUpdateRequest(
    val firstName: String? = null,
    val lastName: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val clinicId: Long? = null,
    val profession: String? = null,
    val timeZone: String? = null,
    val biography: String? = null,
    val services: String? = null,
    val certificates: String? = null,
    val telegram: String? = null,
    val instagram: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCountry: String? = null,
    val locationRegion: String? = null,
    val locationDistrict: String? = null,
    val locationCity: String? = null,
    val locationPostalCode: String? = null,
    val locationStreetAddress: String? = null,
)

/**
 * `GET /doctors/me` (08 §10–11): the doctor's whole profile in four blocks plus the plan. Every field tolerates a
 * missing or null value; [subscription] is null on old backends (tier unknown, D-03: never fail open).
 */
@Serializable
data class DoctorMe(
    val profile: DoctorProfile = DoctorProfile(),
    val contact: DoctorContact = DoctorContact(),
    val billing: DoctorBilling = DoctorBilling(),
    val settings: DoctorSettings = DoctorSettings(),
    val subscription: DoctorSubscription? = null,
)

@Serializable
data class DoctorProfile(
    val id: Long? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val dob: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val clinic: String? = null,
    val clinicId: Long? = null,
    // English catalogue labels joined with ", " (08 §4.1).
    val profession: String? = null,
    val avatarUrl: String? = null,
    val photoUrl: String? = null,
    val scheduleValidFrom: String? = null,
    val scheduleValidUntil: String? = null,
    val biography: String? = null,
    // JSON arrays in a string (08 §1.10).
    val services: String? = null,
    val certificates: String? = null,
    val telegram: String? = null,
    val instagram: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val locationCountry: String? = null,
    val locationRegion: String? = null,
    val locationDistrict: String? = null,
    val locationCity: String? = null,
    val locationPostalCode: String? = null,
    val locationStreetAddress: String? = null,
    // IANA id: every schedule time is shown in it.
    val timeZone: String? = null,
    val consultationPriceMinor: Long? = null,
    val consultationCurrency: String? = null,
    val smsRemindersAllowed: Boolean? = null,
) {
    /** [services] decoded (08 §1.10); a malformed value reads as none and is logged, never thrown. */
    val serviceTags: List<String> get() = stringList(services, "services")

    /** [certificates] decoded: file URLs. */
    val certificateUrls: List<String> get() = stringList(certificates, "certificates")
}

private fun stringList(
    json: String?,
    field: String,
): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return runCatching { apiJson.decodeFromString<List<String?>>(json).filterNotNull() }
        .onFailure { httpLogger.log("FAILED parse profile.$field") }
        .getOrDefault(emptyList())
}

/** Also the body of `PATCH /doctors/me/contact` (`phone` required there). */
@Serializable
data class DoctorContact(
    val phone: String? = null,
    val email: String? = null,
)

/** Also the body of `PATCH /doctors/me/billing`. */
@Serializable
data class DoctorBilling(
    val billingName: String? = null,
    val billingEmail: String? = null,
    val iban: String? = null,
    val taxId: String? = null,
    val stripeConnectAccountId: String? = null,
    val clickMerchantId: String? = null,
    val paymeMerchantId: String? = null,
)

/** Also the body of `PATCH /doctors/me/settings`, always sent whole (08 §10). */
@Serializable
data class DoctorSettings(
    val country: String? = null,
    val language: String? = null,
    val twoFA: Boolean = false,
    val encryptedDocs: Boolean = false,
    val defaultStartTab: String = "home",
)

@Serializable
data class DoctorSubscription(
    val tier: String? = null,
    val features: List<String> = emptyList(),
)

/** `GET /me/profile`: who is signed in, for both roles (clinic staff have no `/doctors/me`). */
@Serializable
data class MeProfile(
    val id: Long = 0,
    val role: String = "",
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val phone: String? = null,
    val photoUrl: String? = null,
    val timeZone: String? = null,
)
