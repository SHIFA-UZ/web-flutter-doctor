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
    val clinicManaged: Boolean = false,
    val groupId: Long? = null,
    val groupName: String? = null,
    val groupSortOrder: Int? = null,
    val prices: List<ServicePrice> = emptyList(),
)

/** A price in minor units; [locationId] null means every location without its own row. */
@Serializable
data class ServicePrice(
    val amountMinor: Long,
    val currency: String,
    val locationId: Long? = null,
)

/**
 * `POST /doctors/me/services` and `PATCH …/{id}` body. `paymentType` is never sent: the backend keeps it on PATCH and
 * defaults new services to PAY_NOW.
 */
@Serializable
data class UpsertService(
    val title: String,
    val description: String?,
    val isActive: Boolean,
    val isFreeConsultation: Boolean,
    val groupId: Long?,
    val prices: List<ServicePrice>,
)

/** `GET /doctors/me/service-groups`; also the POST / PATCH body without [id]. */
@Serializable
data class ServiceGroup(
    val id: Long? = null,
    val name: String = "",
    val sortOrder: Int = 0,
)
