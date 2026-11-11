package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** `GET /doctors/me/services` row (08 §2; dev 2026-11-04). [clinicManaged] rows come from the clinic catalogue. */
@Serializable
data class DoctorService(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    val isActive: Boolean = true,
    val isFreeConsultation: Boolean = false,
    // PAY_NOW (online at booking) or PAY_LATER (in clinic, in-person visits only); ignored when free.
    val paymentType: String = PAY_NOW,
    val clinicManaged: Boolean = false,
    val groupId: Long? = null,
    val groupName: String? = null,
    val groupSortOrder: Int? = null,
    val prices: List<ServicePrice> = emptyList(),
) {
    val isPayLater: Boolean get() = !isFreeConsultation && paymentType.equals(PAY_LATER, ignoreCase = true)

    companion object {
        const val PAY_NOW = "PAY_NOW"
        const val PAY_LATER = "PAY_LATER"
    }
}

/** A price in minor units; [locationId] null means every location without its own row. */
@Serializable
data class ServicePrice(
    val amountMinor: Long,
    val currency: String,
    val locationId: Long? = null,
)

/**
 * `POST /doctors/me/services` and `PATCH …/{id}` body. A free service sends PAY_NOW: the backend refuses a free
 * PAY_LATER (400).
 */
@Serializable
data class UpsertService(
    val title: String,
    val description: String?,
    val isActive: Boolean,
    val isFreeConsultation: Boolean,
    val groupId: Long?,
    val prices: List<ServicePrice>,
    val paymentType: String,
)

/** `GET /doctors/me/service-groups`; also the POST / PATCH body without [id]. */
@Serializable
data class ServiceGroup(
    val id: Long? = null,
    val name: String = "",
    val sortOrder: Int = 0,
)
