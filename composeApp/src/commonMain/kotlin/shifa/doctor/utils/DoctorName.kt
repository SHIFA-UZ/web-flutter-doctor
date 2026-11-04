package shifa.doctor.utils

/**
 * The doctor's display name (08 §4.4, Flutter `formatDoctorMedName`): "Dr. med. First Last", trimmed; without the
 * prefix, "First Last". No name gives "" (not a lone prefix), so the caller can show "Your name" / "Not set" (flow
 * 08/01 §4). The prefix is passed in (a string resource) so this stays plain.
 */
fun doctorDisplayName(
    first: String?,
    last: String?,
    prefix: String?,
): String {
    val name = listOfNotNull(first?.trim(), last?.trim()).filter { it.isNotEmpty() }.joinToString(" ")
    if (name.isEmpty()) return ""
    return listOfNotNull(prefix?.trim()?.ifEmpty { null }, name).joinToString(" ")
}
