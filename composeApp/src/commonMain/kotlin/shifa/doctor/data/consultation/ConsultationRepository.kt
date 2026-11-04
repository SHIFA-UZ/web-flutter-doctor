package shifa.doctor.data.consultation

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CompleteRequest
import shifa.doctor.data.model.CompleteResult
import shifa.doctor.data.model.ConsultationNote
import shifa.doctor.data.model.DraftNote
import shifa.doctor.data.model.ScribeStatus
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Serializable
private data class NotesBody(
    val body: String,
)

@Serializable
private data class DraftLink(
    val patientId: Long? = null,
    val appointmentId: Long? = null,
)

/**
 * A visit's notes and Shifa AI's part in them (AI-11, 02 §7.1): saved notes, pending drafts, completing the visit and
 * the scribe's status. Every failure is a failure (Flutter turned a non-200 into an empty list, so a failed read looked
 * like a visit without notes).
 */
class ConsultationRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    suspend fun notes(appointmentId: Long): Result<List<ConsultationNote>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.consultationNotes(appointmentId)) }
            .map { it.rows(ConsultationNote.serializer(), "consultation notes") }

    /** `PUT …/consultation-notes {body}`, trimmed; blank text saves nothing (and succeeds). */
    suspend fun saveNotes(
        appointmentId: Long,
        text: String,
    ): Result<Unit> {
        val body = text.trim()
        if (body.isEmpty()) return Result.success(Unit)
        return apiCall<Unit> {
            client().put(ApiConfig.Endpoints.consultationNotes(appointmentId)) {
                contentType(ContentType.Application.Json)
                setBody(NotesBody(body))
            }
        }
    }

    suspend fun drafts(appointmentId: Long): Result<List<DraftNote>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.draftNotes(appointmentId)) }
            .map { it.rows(DraftNote.serializer(), "draft notes") }

    /** A draft saved as the visit's note (the backend then refreshes the visit summary). */
    suspend fun confirmDraft(
        draftId: String,
        patientId: Long?,
        appointmentId: Long?,
    ): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.draftConfirm(draftId)) {
                contentType(ContentType.Application.Json)
                setBody(DraftLink(patientId, appointmentId))
            }
        }

    suspend fun discardDraft(draftId: String): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.draftDiscard(draftId)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }

    /** `PUT …/complete`: blank notes are left out, `awaitingScribe` only when true. */
    suspend fun complete(
        appointmentId: Long,
        clinicId: Long? = null,
        doctorNotes: String? = null,
        awaitingScribe: Boolean = false,
    ): Result<CompleteResult> =
        apiCall<CompleteResult> {
            client().put(ApiConfig.Endpoints.appointmentComplete(appointmentId)) {
                contentType(ContentType.Application.Json)
                setBody(
                    CompleteRequest(
                        clinicId,
                        doctorNotes?.trim()?.ifEmpty { null },
                        awaitingScribe.takeIf { it },
                    ),
                )
            }
        }

    suspend fun scribeStatus(appointmentId: Long): Result<ScribeStatus> =
        apiCall<ScribeStatus> { client().get(ApiConfig.Endpoints.scribeStatus(appointmentId)) }

    /**
     * The scribe's status now, then every [every] until its note is there or [timeout] has passed since the start
     * (02 §7.1: 3 s, 120 s; a slow read counts too, and is cut at the deadline). Cancelled with its collector
     * (Flutter's wait couldn't be). Every read is emitted; a passing failure (no network, 5xx, 429) is tried again, a
     * lasting one ends it.
     */
    fun scribeUpdates(
        appointmentId: Long,
        every: Duration = SCRIBE_POLL,
        timeout: Duration = SCRIBE_TIMEOUT,
    ): Flow<Result<ScribeStatus>> =
        flow {
            val deadline = TimeSource.Monotonic.markNow() + timeout
            while (true) {
                val read = withTimeoutOrNull(-deadline.elapsedNow()) { scribeStatus(appointmentId) } ?: return@flow
                emit(read)
                val e = read.exceptionOrNull()
                val again =
                    if (e == null) {
                        !read.getOrThrow().hasScribeNote
                    } else {
                        e is ApiException.NetworkError || e is ApiException.ServerError || e is ApiException.RateLimit
                    }
                if (!again || -deadline.elapsedNow() <= every) return@flow
                delay(every)
            }
        }
}

val SCRIBE_POLL = 3.seconds
val SCRIBE_TIMEOUT = 120.seconds

val consultationRepository by lazy { ConsultationRepository() }
