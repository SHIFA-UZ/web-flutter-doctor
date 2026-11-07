package shifa.doctor.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `CarePartnerSearchHit` (`GET /care-partnerships/partners/search`): a doctor who can be invited. */
@Serializable
data class CarePartnerHit(
    val id: Long = 0,
    val firstName: String = "",
    val lastName: String = "",
    val profession: String? = null,
    val clinic: String? = null,
    val locationCountry: String? = null,
    val locationCity: String? = null,
    val avatarUrl: String? = null,
) {
    val name: String get() = "$firstName $lastName".trim()

    /** "Cardiologist · Tashkent Med Center · Tashkent" (the city, else the country; empty parts left out). */
    val subtitle: String
        get() =
            listOfNotNull(profession, clinic, locationCity ?: locationCountry)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
}

/** A partnership's status (the backend's `CarePartnership.Status`); anything new reads as [Unknown]. */
enum class PartnershipStatus {
    Pending,
    Active,
    Declined,
    Completed,
    Cancelled,
    Unknown,
    ;

    val ended: Boolean get() = this == Declined || this == Completed || this == Cancelled

    companion object {
        fun of(raw: String?): PartnershipStatus =
            entries.firstOrNull { it != Unknown && it.name.equals(raw?.trim(), ignoreCase = true) } ?: Unknown
    }
}

/** `CarePartnershipDto`. [viewerRole] says which side the signed-in doctor is on. */
@Serializable
data class CarePartnership(
    val id: Long = 0,
    @SerialName("status") val statusRaw: String? = null,
    val patientId: Long = 0,
    val patientName: String? = null,
    val initiatingDoctorId: Long = 0,
    val initiatingDoctorName: String? = null,
    val partnerDoctorId: Long = 0,
    val partnerDoctorName: String? = null,
    val specialtyRequested: String? = null,
    val message: String? = null,
    val originatingAppointmentId: Long? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val viewerRole: String? = null,
) {
    val status: PartnershipStatus get() = PartnershipStatus.of(statusRaw)

    /**
     * True when the signed-in doctor sent the invite: the server's `viewerRole`, else [me] compared with the two ids;
     * null when neither tells (no guessing, flow 06/13 §8).
     */
    fun iInitiated(me: Long?): Boolean? =
        when (viewerRole?.uppercase()) {
            "INITIATOR" -> true
            "PARTNER" -> false
            else ->
                when (me) {
                    null -> null
                    initiatingDoctorId -> true
                    partnerDoctorId -> false
                    else -> null
                }
        }

    /** The doctor on the other side. */
    fun otherName(me: Long?): String? = if (iInitiated(me) == false) initiatingDoctorName else partnerDoctorName

    /** An invite waiting for the signed-in doctor's answer. */
    fun needsMyAnswer(me: Long?): Boolean = status == PartnershipStatus.Pending && iInitiated(me) == false
}

/** `CarePartnershipProgressDto`: one progress update. */
@Serializable
data class CareProgress(
    val id: Long = 0,
    val authorDoctorId: Long = 0,
    val authorDoctorName: String? = null,
    val body: String = "",
    val createdAt: String? = null,
)

/** `InviteRequest`. */
@Serializable
data class PartnerInvite(
    val patientId: Long,
    val partnerDoctorId: Long,
    val specialtyRequested: String? = null,
    val message: String? = null,
    val originatingAppointmentId: Long? = null,
)

@Serializable
data class ProgressBody(
    val body: String,
)
