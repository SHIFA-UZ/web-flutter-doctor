package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/**
 * A name in the engine's three languages (02 §5.2): Uzbek falls back to Russian, English to Russian, anything else is
 * Russian (Flutter's `forLocale`); a missing language reads as empty, never a crash.
 */
@Serializable
data class LocalizedName(
    val ru: String? = null,
    val uz: String? = null,
    val en: String? = null,
) {
    fun forLocale(locale: String): String {
        val lang = locale.substringBefore('-').substringBefore('_').lowercase()
        val ru = ru.orEmpty()
        return when (lang) {
            "uz" -> uz?.takeIf(String::isNotBlank) ?: ru
            "en" -> en?.takeIf(String::isNotBlank) ?: ru
            else -> ru
        }
    }
}

/** A disease group ("Caries", "Pulpitis"…). */
@Serializable
data class ClinicalGroup(
    val groupId: String = "",
    val sortOrder: Int = 0,
    val names: LocalizedName = LocalizedName(),
)

/** A disease in a group, with its ICD-10 codes; [chips] only on the detail read. */
@Serializable
data class ClinicalDisease(
    val diseaseId: String = "",
    val number: Int = 0,
    val groupId: String = "",
    val slug: String = "",
    val icdCodes: List<String> = emptyList(),
    val names: LocalizedName = LocalizedName(),
    val chips: List<ClinicalChip> = emptyList(),
)

/**
 * A phrase for one form field ([ClinicalField]); `[X]`-style [variables] are filled in by the doctor. Occlusion chips
 * (`occ.*`) add an Angle class and an ICD hint; shared templates come as chips with a `shared.` id.
 */
@Serializable
data class ClinicalChip(
    val chipId: String = "",
    val field: String = "",
    val labels: LocalizedName = LocalizedName(),
    val variables: List<String> = emptyList(),
    val priority: Int = 50,
    val angleClass: String? = null,
    val icdHint: String? = null,
)

/** A ready text for the oral cavity or the X-ray field (`/shared/{type}`), suggested for the groups it [correlates] with. */
@Serializable
data class ClinicalSharedTemplate(
    val templateId: String = "",
    val type: String = "",
    val field: String = "",
    val labels: LocalizedName = LocalizedName(),
    val priority: Int = 50,
    val correlates: List<String> = emptyList(),
) {
    /** As a chip for a selection: the backend knows it as `shared.<templateId>`. */
    val chipId: String get() = "shared.$templateId"
}

/** The doctor's most used diagnoses (`/doctors/me/top-diagnoses`). */
@Serializable
data class ClinicalTopDiagnosis(
    val diseaseId: String = "",
    val names: LocalizedName = LocalizedName(),
    val icdCodes: List<String> = emptyList(),
    val useCount: Int = 0,
    val lastUsedAt: String? = null,
)

@Serializable
data class SynthesizeRequest(
    val locale: String,
    val diseaseId: String? = null,
    val selections: List<ClinicalChipSelection> = emptyList(),
)

@Serializable
data class SynthesizeAnswer(
    val fields: Map<String, String> = emptyMap(),
)

/** The engine's field names and the 025-2 field each fills (02 §5.2, Flutter's `_onClinicalEngineApplied`). */
object ClinicalField {
    const val COMPLAINTS = "complaints"
    const val MORBI = "morbi"
    const val OBJECTIVE = "objective"
    const val OCCLUSION = "occlusion"
    const val ORAL_CAVITY = "oral_cavity"
    const val XRAY = "xray"
    const val TREATMENT_1 = "treatment_1"
    const val TREATMENT_2 = "treatment_2"
    const val RECOMMENDATIONS = "recommendations"

    val all =
        listOf(COMPLAINTS, MORBI, OBJECTIVE, OCCLUSION, ORAL_CAVITY, XRAY, TREATMENT_1, TREATMENT_2, RECOMMENDATIONS)
}
