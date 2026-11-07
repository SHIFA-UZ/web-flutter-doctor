package shifa.doctor.data.profile

/** The backend's gender values (English on the wire; labels are localised: flow 01/08 §4). */
enum class Gender(
    val wire: String,
) {
    Male("Male"),
    Female("Female"),
    Other("Other"),
}
