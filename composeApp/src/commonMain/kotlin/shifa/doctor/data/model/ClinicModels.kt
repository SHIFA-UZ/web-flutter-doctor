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

/** A member's role in a clinic (09 §1.5); anything new reads as [Unknown], which may do nothing. */
enum class MembershipRole {
    Owner,
    ClinicAdmin,
    Receptionist,
    Doctor,
    Nurse,
    Unknown,
    ;

    companion object {
        fun of(raw: String?): MembershipRole =
            when (raw?.trim()?.uppercase()) {
                "OWNER" -> Owner
                "CLINIC_ADMIN" -> ClinicAdmin
                "RECEPTIONIST" -> Receptionist
                "DOCTOR" -> Doctor
                "NURSE" -> Nurse
                else -> Unknown
            }
    }
}

val MyClinic.role: MembershipRole get() = MembershipRole.of(membershipRole)

/**
 * The clinic's permissions in one place (CLINIC-01; Flutter had three copies, 09 §1.5). Client-side only, the backend
 * decides: the default and per-doctor revenue share need the backend's flag or an owner / clinic admin; finance actions
 * (new record, pay, installments) any role but a nurse.
 */
val MyClinic.mayManageFinanceSettings: Boolean
    get() = canManageFinanceSettings || role == MembershipRole.Owner || role == MembershipRole.ClinicAdmin

val MyClinic.mayManageFinance: Boolean
    get() =
        role in
            setOf(
                MembershipRole.Owner,
                MembershipRole.ClinicAdmin,
                MembershipRole.Receptionist,
                MembershipRole.Doctor,
            )

/** A member whose revenue share can be set: a doctor or the owner (who also treats). */
fun mayEditRevenueShare(member: MembershipRole): Boolean =
    member == MembershipRole.Doctor || member == MembershipRole.Owner

/** `GET /clinics/{id}/overview-stats` (09 §1.7; dev 2026-11-06). The two optional figures are never shown (09 §1.7). */
@Serializable
data class ClinicOverviewStats(
    val appointmentsToday: Int = 0,
    val activeDoctors: Int = 0,
    val patientsThisMonth: Int = 0,
)

/** `GET /clinics/{id}/members` item (dev 2026-11-06): a doctor or staff member and their revenue share. */
@Serializable
data class ClinicMember(
    val doctorProfileId: Long? = null,
    val userId: Long? = null,
    val displayName: String? = null,
    val membershipRole: String? = null,
    // The member's own override (null: the clinic default applies), and the share in force.
    val doctorRevenueSharePercent: Int? = null,
    val effectiveRevenueSharePercent: Int? = null,
) {
    val role: MembershipRole get() = MembershipRole.of(membershipRole)
}

/** `GET /clinics/{id}/invitations` item (OpenAPI `InvitationListItemDto`): a receptionist invitation. */
@Serializable
data class ClinicInvitation(
    val id: Long = 0,
    val emailSentTo: String? = null,
    val expiresAt: String? = null,
    val consumed: Boolean = false,
    val pending: Boolean = false,
)

@Serializable
internal data class MemberShareBody(
    val doctorRevenueSharePercent: Int?,
)

@Serializable
internal data class DefaultShareBody(
    val defaultDoctorRevenueSharePercent: Int?,
)

@Serializable
internal data class InvitationBody(
    val email: String,
    val role: String = "RECEPTIONIST",
)
