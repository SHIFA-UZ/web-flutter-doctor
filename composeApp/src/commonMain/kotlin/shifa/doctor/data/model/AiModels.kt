package shifa.doctor.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One turn of the conversation sent back with each question: `user`, `assistant` or `system`. */
@Serializable
data class AiMessage(
    val role: String,
    val content: String,
)

/**
 * The body of both AI streams (`DoctorAiRequest`): [language] is `EN`, `RU` or `UZ`; [conversationId] (≤ 128 chars)
 * is the local chat session's id, which the backend keys its memory and pending confirmations on, so a new session
 * needs a new one.
 */
@Serializable
data class AiRequest(
    val messages: List<AiMessage>,
    val language: String,
    val question: String? = null,
    val patientId: Long? = null,
    val consultationId: Long? = null,
    val conversationId: String? = null,
)

/** What an AI stream delivers, in order: text pieces, tool cards (morning stream), and the saved draft (`/ai/stream`). */
sealed interface AiStreamEvent {
    data class Token(
        val text: String,
    ) : AiStreamEvent

    data class Tool(
        val name: String,
        val payload: JsonObject,
    ) : AiStreamEvent

    @Serializable
    data class Draft(
        val draftId: String = "",
        val draftLabel: String = "Draft",
        val canSave: Boolean = true,
    ) : AiStreamEvent
}

/** `POST /ai/speak`: the audio and its type (Uzbek comes back as `audio/wav`, English and Russian as `audio/mpeg`). */
class SpokenAudio(
    val bytes: ByteArray,
    val mimeType: String,
)

/** `POST /ai/patient-briefing/{id}`: a short summary from the documents and visits the doctor may see. */
@Serializable
data class PatientBriefing(
    val briefing: String = "",
    val documentCount: Int = 0,
    val appointmentCount: Int = 0,
)

/** A fact the AI drew from a visit's recording (`VisitFactDto`): a draft until the doctor documents it. */
@Serializable
data class VisitFact(
    val id: Long,
    val patientId: Long,
    val factType: String,
    val text: String,
    val status: String,
    val appointmentId: Long? = null,
    val quote: String? = null,
    val scheduleHint: String? = null,
    val reflectionKept: Boolean = true,
    val dropReason: String? = null,
    val remoteTaskId: Long? = null,
    val createdAt: String = "",
    val committedAt: String? = null,
) {
    val isDraft: Boolean get() = status == "DRAFT"
    val isDropped: Boolean get() = status == "DROPPED" || !reflectionKept
    val isCommitted: Boolean get() = status == "COMMITTED"
}

/** How often a kind of fact shows up across the doctor's patients (`ClinicPatternDto`). */
@Serializable
data class ClinicPattern(
    val key: String = "",
    val label: String = "",
    val factType: String = "",
    val count: Int = 0,
)

/** A visit's facts (or a patient's timeline) with the doctor's patterns. */
data class VisitFacts(
    val facts: List<VisitFact> = emptyList(),
    val patterns: List<ClinicPattern> = emptyList(),
) {
    val keptDrafts: List<VisitFact> get() = facts.filter { it.isDraft && it.reflectionKept }
    val dropped: List<VisitFact> get() = facts.filter { it.isDropped }
}

/** `POST /ai/visit-facts/commit`'s answer: what was documented and the medication tasks drafted from it. */
data class VisitFactsCommit(
    val committedFacts: List<VisitFact>,
    val createdTaskIds: List<Long>,
    val patterns: List<ClinicPattern>,
)

@Serializable
data class CommitVisitFactsRequest(
    val appointmentId: Long,
    val acceptFactIds: List<Long>? = null,
    val rejectFactIds: List<Long>? = null,
)
