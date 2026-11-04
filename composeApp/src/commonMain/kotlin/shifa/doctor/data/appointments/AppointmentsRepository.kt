package shifa.doctor.data.appointments

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient

@Serializable
private data class ChangeSlot(
    val startAt: String,
    val slotMinutes: Int,
)

/**
 * Appointment actions from the calendar (CAL-6, 03 §6): cancel, move or resize, and the payment reminder. Every call's
 * status is checked (Flutter didn't); the caller refreshes the touched days through `CalendarRepository.changed`.
 */
class AppointmentsRepository(
    private val client: () -> HttpClient = { httpClient },
) {
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
