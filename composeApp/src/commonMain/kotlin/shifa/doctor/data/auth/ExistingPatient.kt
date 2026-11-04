package shifa.doctor.data.auth

/** A patient account the new doctor account is added to (flow 01/07 §3b); the photo is shown, the name is not. */
data class ExistingPatient(
    val fullName: String?,
    val photoUrl: String?,
)
