package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/**
 * `PATCH /doctors/me/profile`: only the keys that are set are sent (the client's `explicitNulls = false`), the backend
 * leaves the others alone. [dob] is `YYYY-MM-DD`; [gender] the English `Male` / `Female` / `Other`; [profession] the
 * English labels joined with ", ".
 */
@Serializable
data class ProfileUpdateRequest(
    val dob: String? = null,
    val gender: String? = null,
    val address: String? = null,
    val clinicId: Long? = null,
    val profession: String? = null,
)
