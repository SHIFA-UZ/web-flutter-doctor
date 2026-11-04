package shifa.doctor.data.model

import kotlinx.serialization.Serializable

/** A saved note of a visit (02 §7.1): the doctor's (`MANUAL`) or a confirmed Shifa AI draft (`AI_DRAFT`). */
@Serializable
data class ConsultationNote(
    val id: Long = 0,
    val body: String? = null,
    val subjective: String? = null,
    val assessment: String? = null,
    val plan: String? = null,
    val source: String = MANUAL,
    val createdAt: String = "",
) {
    val fromAi: Boolean get() = source == AI_DRAFT

    /** The body without its transcript, else subjective, assessment and plan joined by blank lines. */
    val displayText: String
        get() =
            body?.takeIf { it.isNotBlank() }?.let(::stripScribeTranscript)
                ?: listOfNotNull(subjective, assessment, plan).filter { it.isNotBlank() }.joinToString("\n\n")

    companion object {
        const val MANUAL = "MANUAL"
        const val AI_DRAFT = "AI_DRAFT"
    }
}

/** An ICD-10 code Shifa AI suggests with a draft. */
@Serializable
data class IcdSuggestion(
    val code: String = "",
    val title: String = "",
    val confidence: Double? = null,
    val isTop: Boolean = false,
)

/** A pending Shifa AI note (the scribe's): shown with Save as note / Discard until confirmed (02 §7.1). */
@Serializable
data class DraftNote(
    val id: String = "",
    val aiLabel: String = "Draft",
    val body: String = "",
    val createdAt: String = "",
    val icdSuggestions: List<IcdSuggestion> = emptyList(),
)

/** `PUT /appointments/{id}/complete`'s body; empty fields are left out. Visit charges join with APPT-07. */
@Serializable
data class CompleteRequest(
    val clinicId: Long? = null,
    val doctorNotes: String? = null,
    val awaitingScribe: Boolean? = null,
)

/** What completing a visit says about its documentation (the scribe may still be writing). */
@Serializable
data class CompleteResult(
    val status: String = "",
    val scribePending: Boolean = false,
    val hasDocumentation: Boolean = false,
    val hasScribeNote: Boolean = false,
)

/** `GET /appointments/{id}/scribe-status`. */
@Serializable
data class ScribeStatus(
    val status: String = "none",
    val hasDocumentation: Boolean = false,
    val hasScribeNote: Boolean = false,
)

private val TRANSCRIPT = Regex("^TRANSCRIPT:\\s*", setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))

/** The SOAP part of a scribe note: everything before a line starting "TRANSCRIPT:" (the raw dialogue). */
fun stripScribeTranscript(text: String): String =
    (TRANSCRIPT.find(text)?.let { text.substring(0, it.range.first) } ?: text).trim()

/** The doctor's own notes (not Shifa AI's), joined by blank lines. */
fun joinedManualNotes(notes: List<ConsultationNote>): String =
    notes.filterNot { it.fromAi }.map { it.displayText.trim() }.filter { it.isNotEmpty() }.joinToString("\n\n")

/**
 * A visit's documentation text (02 §7.1): Shifa AI's notes and drafts under [scribeHeading] first, then the doctor's
 * notes and [extraDoctorNotes] under [doctorHeading]; an empty block is left out.
 */
fun composeVisitDocumentationText(
    scribeHeading: String,
    doctorHeading: String,
    notes: List<ConsultationNote>,
    drafts: List<DraftNote> = emptyList(),
    extraDoctorNotes: String = "",
): String {
    val (ai, own) = notes.partition { it.fromAi }
    val scribe = (ai.map { it.displayText.trim() } + drafts.map { it.body.trim() }).filter { it.isNotEmpty() }
    val doctor = (own.map { it.displayText.trim() } + extraDoctorNotes.trim()).filter { it.isNotEmpty() }
    return listOfNotNull(
        scribe.takeIf { it.isNotEmpty() }?.let { "$scribeHeading\n\n${it.joinToString("\n\n")}" },
        doctor.takeIf { it.isNotEmpty() }?.let { "$doctorHeading\n\n${it.joinToString("\n\n")}" },
    ).joinToString("\n\n").trim()
}
