package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** `GET /public/professions` item. The English label is the stored value (08 §4.1: no id or slug on the wire). */
@Serializable
data class ProfessionDto(
    val english: String = "",
    val uzbek: String? = null,
    val category: String? = null,
)

/** `GET /public/clinics` item. */
@Serializable
data class PublicClinicDto(
    val id: Long,
    val name: String = "",
)
