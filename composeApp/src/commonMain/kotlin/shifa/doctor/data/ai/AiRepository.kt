package shifa.doctor.data.ai

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import shifa.doctor.data.api.ApiConfig.Endpoints
import shifa.doctor.data.api.SseEvent
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.apiJson
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.httpLogger
import shifa.doctor.data.api.rows
import shifa.doctor.data.api.streamEvents
import shifa.doctor.data.model.AiRequest
import shifa.doctor.data.model.AiStreamEvent
import shifa.doctor.data.model.ClinicPattern
import shifa.doctor.data.model.CommitVisitFactsRequest
import shifa.doctor.data.model.PatientBriefing
import shifa.doctor.data.model.SpokenAudio
import shifa.doctor.data.model.VisitFact
import shifa.doctor.data.model.VisitFacts
import shifa.doctor.data.model.VisitFactsCommit
import shifa.doctor.data.storage.AppLanguage

/** The language code the AI streams and `/ai/speak` take (`EN`, `RU`, `UZ`; Cyrillic Uzbek is `UZ`). */
val AppLanguage.aiCode: String get() = backendCode.uppercase()

@Serializable
private data class SpeakBody(
    val text: String,
    val language: String,
)

@Serializable
private data class BriefingBody(
    val language: String,
)

// The three visit-fact answers share their lists; each row is read on its own.
@Serializable
private data class FactsWire(
    val facts: List<JsonElement> = emptyList(),
    val timeline: List<JsonElement> = emptyList(),
    val committedFacts: List<JsonElement> = emptyList(),
    val patterns: List<JsonElement> = emptyList(),
    val createdTaskIds: List<Long> = emptyList(),
) {
    fun facts(list: List<JsonElement>) = list.rows(VisitFact.serializer(), "visit facts")

    fun patterns() = patterns.rows(ClinicPattern.serializer(), "clinic patterns")
}

/**
 * Shifa AI over HTTP (AI-01, 02 §3.2 and §8), on the shared client, so a 401 ends the session like any call (Flutter
 * sent raw requests past its 401 handling). Draft confirm and discard live in
 * [shifa.doctor.data.consultation.ConsultationRepository]. Nothing here logs AI text: only failures, by code.
 */
class AiRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * One AI answer, streamed: the morning stream ([morning], Intelligence: text and tool cards) or the patient-aware
     * `/ai/stream` (text, then the saved [AiStreamEvent.Draft]). Events reach [onEvent] as they arrive; the whole turn
     * gets 75 s (02 §3.3) and fails as a timeout [shifa.doctor.data.model.ApiException.NetworkError] after that.
     * Cancelling the caller closes the connection.
     */
    suspend fun stream(
        request: AiRequest,
        morning: Boolean,
        onEvent: suspend (AiStreamEvent) -> Unit,
    ): Result<Unit> =
        client().streamEvents({
            method = HttpMethod.Post
            url(if (morning) Endpoints.AI_MORNING_STREAM else Endpoints.AI_STREAM)
            contentType(ContentType.Application.Json)
            setBody(request)
            timeout {
                requestTimeoutMillis = STREAM_TIMEOUT_MS
                socketTimeoutMillis = STREAM_TIMEOUT_MS
            }
        }) { e -> aiEvent(e)?.let { onEvent(it) } }

    /** The server's voice for [text] (cut to what [speechText] keeps), within 25 s. */
    suspend fun speak(
        text: String,
        language: AppLanguage,
    ): Result<SpokenAudio> {
        var type: ContentType? = null
        return apiCall<ByteArray> {
            client().post(Endpoints.AI_SPEAK) {
                contentType(ContentType.Application.Json)
                setBody(SpeakBody(speechText(text), language.aiCode))
                timeout { requestTimeoutMillis = SPEAK_TIMEOUT_MS }
            }.also { type = it.contentType() }
        }.map { SpokenAudio(it, type?.withoutParameters()?.toString() ?: "audio/mpeg") }
    }

    /** A briefing on [patientId] in the app's [language]; 400/403/404/429 stay typed failures with their codes. */
    suspend fun patientBriefing(
        patientId: Long,
        language: AppLanguage,
    ): Result<PatientBriefing> =
        apiCall {
            client().post(Endpoints.patientBriefing(patientId)) {
                contentType(ContentType.Application.Json)
                setBody(BriefingBody(language.backendCode))
            }
        }

    suspend fun visitFacts(appointmentId: Long): Result<VisitFacts> =
        apiCall<FactsWire> { client().get(Endpoints.visitFacts(appointmentId)) }
            .map { VisitFacts(it.facts(it.facts), it.patterns()) }

    /** Everything documented for [patientId] across visits. */
    suspend fun timeline(patientId: Long): Result<VisitFacts> =
        apiCall<FactsWire> { client().get(Endpoints.visitFactsTimeline(patientId)) }
            .map { VisitFacts(it.facts(it.timeline), it.patterns()) }

    /** "Document everything": the kept drafts (or the ones named) into the chart, medication facts into task drafts. */
    suspend fun commit(request: CommitVisitFactsRequest): Result<VisitFactsCommit> =
        apiCall<FactsWire> {
            client().post(Endpoints.VISIT_FACTS_COMMIT) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }.map { VisitFactsCommit(it.facts(it.committedFacts), it.createdTaskIds, it.patterns()) }

    private companion object {
        const val STREAM_TIMEOUT_MS = 75_000L
        const val SPEAK_TIMEOUT_MS = 25_000L
    }
}

/**
 * An SSE event as the app reads it (02 §3.2): `draft` → the draft (dropped without an id: nothing to confirm),
 * `tool` → a card (dropped without a name or an object payload), anything else with data → a text piece.
 */
internal fun aiEvent(e: SseEvent): AiStreamEvent? =
    when (e.event) {
        "draft" ->
            runCatching { apiJson.decodeFromString(AiStreamEvent.Draft.serializer(), e.data) }
                .getOrNull()
                ?.takeIf { it.draftId.isNotBlank() }
                .also { if (it == null) httpLogger.log("FAILED parse draft event") }
        "tool" -> {
            val o = runCatching { apiJson.parseToJsonElement(e.data).jsonObject }.getOrNull()
            val name = o?.get("name")?.let { runCatching { it.jsonPrimitive.contentOrNull }.getOrNull() }.orEmpty()
            val payload = o?.get("payload") as? JsonObject
            if (name.isEmpty() || payload == null) {
                httpLogger.log("FAILED parse tool event")
                null
            } else {
                AiStreamEvent.Tool(name, payload)
            }
        }
        else -> e.data.takeIf { it.isNotEmpty() }?.let { AiStreamEvent.Token(it) }
    }

/**
 * What `/ai/speak` is sent (Flutter's `_truncateForSpeech`): trimmed; past 560 characters, cut after the last `.`, `!`
 * or `?` at index 40 or later, else at the last space past index 40, else hard at 560.
 */
internal fun speechText(
    text: String,
    max: Int = 560,
): String {
    val t = text.trim()
    if (t.length <= max) return t
    val cut = t.substring(0, max)
    val end = maxOf(cut.lastIndexOf('.'), cut.lastIndexOf('!'), cut.lastIndexOf('?'))
    if (end >= 40) return cut.substring(0, end + 1).trim()
    val space = cut.lastIndexOf(' ')
    return (if (space > 40) cut.substring(0, space) else cut).trim()
}

val aiRepository by lazy { AiRepository() }
