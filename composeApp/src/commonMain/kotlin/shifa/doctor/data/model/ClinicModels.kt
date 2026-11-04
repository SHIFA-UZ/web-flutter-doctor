package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** `GET /me/clinics` item: a clinic the signed-in doctor or staff member belongs to (dev, 2026-11-04). */
@Serializable
data class MyClinic(
    val clinicId: Long,
    val name: String = "",
    val timeZone: String? = null,
    val currency: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val address: String? = null,
    val membershipRole: String? = null,
    val isPracticeClinic: Boolean = false,
    val defaultDoctorRevenueSharePercent: Int? = null,
    val canManageFinanceSettings: Boolean = false,
)
