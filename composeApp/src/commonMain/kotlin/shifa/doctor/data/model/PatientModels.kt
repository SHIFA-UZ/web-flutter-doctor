package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** A row of `GET /patients/for-assignment` (the light list for booking, 15 §patients). */
@Serializable
data class PatientOption(
    val id: Long? = null,
    val name: String? = null,
    val photoUrl: String? = null,
)

/** A Spring page; only what walking the pages needs. */
@Serializable
data class Page<T>(
    val content: List<T?> = emptyList(),
    val last: Boolean = true,
)
