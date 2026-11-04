package shifa.doctor.data.auth

/**
 * A verified invitation key (01 §2.5) and what it is for. Kept in memory only, in the navigation state (Flutter also
 * wrote it to device storage and never read it back, flow 01/06 §12).
 */
data class Invite(
    val key: String,
    /** `CLINIC_RECEPTIONIST_INVITE`: the receptionist form with the invited address locked (01 §2.7). */
    val receptionist: Boolean,
    val clinicId: Long? = null,
    val clinicName: String? = null,
    /** The address the invitation went to; may be empty (backend C-4), so the forms handle "none". */
    val email: String? = null,
)
