package shifa.doctor.data.catalog

/**
 * Which visit documentation a doctor's profession opens by default (08 §4.3): the dental template (tooth chart,
 * services, totals) or free-text notes. New specialty templates join here, not in the consult screens.
 */
enum class DocumentationTemplate { General, Dental }

/** [profession] is the stored value: English catalogue labels joined with ", " (any dental one decides). */
fun documentationTemplateFor(profession: String?): DocumentationTemplate =
    if (isDentalProfession(profession)) DocumentationTemplate.Dental else DocumentationTemplate.General

/**
 * Ported from Flutter's `isDentalDocumentationProfession` with its English, Uzbek and Russian keys, plus the
 * catalogue's Implantologist (Flutter missed it: no "dental" or "odont" in the name) and the Uzbek labels of the
 * catalogue's oral and maxillofacial surgeons.
 */
fun isDentalProfession(profession: String?): Boolean {
    val p = profession?.trim()?.lowercase().orEmpty()
    if (p.isEmpty()) return false
    return ROOTS.any { it in p } || KEYS.any { it in p }
}

// "odont" covers orthodontist, endodontist, periodontist, prosthodontist; "stomat" the Uzbek stomatolog (the Cyrillic
// стоматолог is a key); "tish" the Uzbek "tish shifokori".
private val ROOTS = listOf("dental", "odont", "stomat", "tish", "implant")

private val KEYS =
    listOf(
        "dentist",
        "oral surgeon",
        "oral therapist",
        "oral therapy",
        "maxillofacial surgeon",
        "maxillo-facial",
        "og'iz bo'shlig'i jarrohi",
        "yuz-jag' jarrohi",
        "стоматолог",
        "ортодонт",
        "пародонтолог",
    )
