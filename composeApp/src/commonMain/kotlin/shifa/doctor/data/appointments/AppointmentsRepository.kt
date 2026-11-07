package shifa.doctor.data.appointments

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.endpointMissing
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppointmentBriefing
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.BriefingStatus
import shifa.doctor.data.model.SignatureState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Serializable
private data class ChangeSlot(
    val startAt: String,
    val slotMinutes: Int,
    val locationId: Long? = null,
)

/** `POST /schedule/book` (03 §6). [location] only for older backends: the backend labels video and known locations. */
@Serializable
data class BookRequest(
    val startAt: String,
    val slotMinutes: Int,
    val patientId: Long,
    val isVideo: Boolean,
    val reason: String?,
    val location: String?,
    val locationId: Long? = null,
    val resourceDoctorId: Long? = null,
)

/**
 * Appointment actions from the calendar (CAL-6, 03 §6): cancel, move or resize, and the payment reminder. Every call's
 * status is checked (Flutter didn't); the caller refreshes the touched days through `CalendarRepository.changed`.
 */
class AppointmentsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** Books a range of free slots for a patient (CAL-7); 409 / 400 mean the range is no longer free. */
    suspend fun book(request: BookRequest): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.SCHEDULE_BOOK) {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
        }

    /**
     * Cancels a visit: `POST /appointments/{id}/cancel {reasonCode, note?}` with a reason (BE-10), else (no reason
     * picked, or a backend without the route: 404/405) the plain `DELETE /appointments/{id}`.
     */
    suspend fun cancel(
        id: Long,
        reason: CancelReason? = null,
        note: String? = null,
    ): Result<Unit> {
        if (reason != null) {
            val withReason =
                apiCall<Unit> {
                    client().post(ApiConfig.Endpoints.appointmentCancel(id)) {
                        contentType(ContentType.Application.Json)
                        setBody(CancelRequest(reason.name, note?.trim()?.takeIf { it.isNotEmpty() }))
                    }
                }
            if (withReason.exceptionOrNull()?.endpointMissing != true) return withReason
        }
        return apiCall<Unit> { client().delete(ApiConfig.Endpoints.appointment(id)) }
    }

    /**
     * `POST /appointments/{id}/no-show {notify}` (BE-10): a confirmed or started visit, from its start until a day after
     * its end, is recorded as missed; 409 `NO_SHOW_NOT_ALLOWED` otherwise.
     */
    suspend fun noShow(
        id: Long,
        notify: Boolean,
    ): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.appointmentNoShow(id)) {
                contentType(ContentType.Application.Json)
                setBody(NoShowRequest(notify))
            }
        }

    /**
     * `PUT /appointments/{id}/change {startAt, slotMinutes, locationId?}`: a new start, or the same start with a new
     * length; [locationId] when the new slot is at another clinic (Shifa's reschedule card, flow 03/15).
     */
    suspend fun change(
        id: Long,
        start: Instant,
        minutes: Int,
        locationId: Long? = null,
    ): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.appointmentChange(id)) {
                contentType(ContentType.Application.Json)
                setBody(ChangeSlot(start.toString(), minutes, locationId))
            }
        }

    /** `GET /appointments/{id}`: the visit as the doctor sees it (APPT-02, 05 §2.2). */
    suspend fun detail(id: Long): Result<AppointmentDetail> =
        apiCall<AppointmentDetail> { client().get(ApiConfig.Endpoints.appointment(id)) }

    /** `PUT /appointments/{id}/request-signature {}`: the patient app asks the patient to sign the visit. */
    suspend fun requestSignature(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.appointmentSignature(id)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }

    /**
     * The visit now, then again every [every] while its signature is awaited; the flow ends once it is signed or not
     * asked (05 §2.2: Flutter could start a second timer per request). Every read is emitted, failures too: a
     * passing one (no network, 5xx, 429) is tried again at the next tick, a lasting one (404, 403, 401…) ends it.
     * The screen's ViewModel owns the one collection and starts it again after a request.
     */
    fun signatureUpdates(
        id: Long,
        every: Duration = SIGNATURE_POLL,
    ): Flow<Result<AppointmentDetail>> =
        flow {
            while (true) {
                val read = detail(id)
                emit(read)
                val e = read.exceptionOrNull()
                val again =
                    if (e == null) {
                        read.getOrThrow().signature == SignatureState.Waiting
                    } else {
                        e is ApiException.NetworkError || e is ApiException.ServerError || e is ApiException.RateLimit
                    }
                if (!again) return@flow
                delay(every)
            }
        }

    suspend fun briefing(id: Long): Result<AppointmentBriefing> =
        apiCall<AppointmentBriefing> { client().get(ApiConfig.Endpoints.appointmentBriefing(id)) }

    /** `POST …/briefing/retry`: the backend makes the briefing again (a failed or stuck one). */
    suspend fun retryBriefing(id: Long): Result<AppointmentBriefing> =
        apiCall<AppointmentBriefing> {
            client().post(ApiConfig.Endpoints.appointmentBriefingRetry(id)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }

    /**
     * The briefing now, then every 3 s while it is being made; after [slowAfter] every 10 s (flow 05/02 §8: "still
     * generating"). The flow ends when it is ready, failed or not there, or on a failed read (the sheet offers Retry),
     * and stops with its collector (Flutter's delayed callbacks kept firing after the sheet closed).
     */
    fun briefingUpdates(
        id: Long,
        every: Duration = BRIEFING_POLL,
        slowAfter: Duration = BRIEFING_SLOW_AFTER,
        slow: Duration = BRIEFING_SLOW_POLL,
        time: TimeSource = TimeSource.Monotonic,
    ): Flow<Result<AppointmentBriefing>> =
        flow {
            // Slower once a minute has passed since the start, slow reads included.
            val started = time.markNow()
            while (true) {
                val read = briefing(id)
                emit(read)
                if (read.getOrNull()?.state != BriefingStatus.Pending) return@flow
                delay(if (started.elapsedNow() >= slowAfter) slow else every)
            }
        }

    /** `POST /appointments/{id}/notify-payment-reminder {}`. */
    suspend fun remindPayment(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().post(ApiConfig.Endpoints.appointmentPaymentReminder(id)) {
                contentType(ContentType.Application.Json)
                setBody(JsonObject(emptyMap()))
            }
        }
}

val appointmentsRepository by lazy { AppointmentsRepository() }

val SIGNATURE_POLL = 10.seconds
val BRIEFING_POLL = 3.seconds
val BRIEFING_SLOW_AFTER = 60.seconds
val BRIEFING_SLOW_POLL = 10.seconds

/** The reason codes of `POST /appointments/{id}/cancel` (BE-10), in the board's chip order. */
enum class CancelReason { DOCTOR_UNAVAILABLE, PATIENT_REQUEST, RESCHEDULED, OTHER }

@Serializable
private data class CancelRequest(
    val reasonCode: String,
    val note: String? = null,
)

@Serializable
private data class NoShowRequest(
    val notify: Boolean,
)
