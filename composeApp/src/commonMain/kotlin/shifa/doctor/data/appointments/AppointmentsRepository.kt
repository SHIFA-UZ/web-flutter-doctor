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
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.AppointmentDetail
import shifa.doctor.data.model.SignatureState
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class ChangeSlot(
    val startAt: String,
    val slotMinutes: Int,
)

/** `POST /schedule/book` (03 §6). [location] only for older backends: the backend labels video and known locations. */
@Serializable
data class BookRequest(
    val startAt: String,
    val slotMinutes: Int,
    val patientId: Long,
    val isVideo: Boolean,
    val reason: String,
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

    /** `DELETE /appointments/{id}` (no reason yet: BE-10). */
    suspend fun cancel(id: Long): Result<Unit> = apiCall<Unit> { client().delete(ApiConfig.Endpoints.appointment(id)) }

    /** `PUT /appointments/{id}/change {startAt, slotMinutes}`: a new start, or the same start with a new length. */
    suspend fun change(
        id: Long,
        start: Instant,
        minutes: Int,
    ): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.appointmentChange(id)) {
                contentType(ContentType.Application.Json)
                setBody(ChangeSlot(start.toString(), minutes))
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
