package shifa.doctor.data.dental

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.DentalRecord

/** A visit's dental record (DENTAL-02, 05 §4.3): `GET/PUT /appointments/{id}/dental-documentation`. */
class DentalRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * The saved record, null when there is none yet (the backend answers `{}`). Any other shape is a failure: an empty
     * record must never be saved over one that couldn't be read.
     */
    suspend fun record(appointmentId: Long): Result<DentalRecord?> =
        apiCall<JsonElement> { client().get(ApiConfig.Endpoints.dentalDocumentation(appointmentId)) }
            .mapCatching { body ->
                if (body is JsonObject && body.isEmpty()) {
                    null
                } else {
                    DentalRecord.read(body) ?: throw ApiException.ServerError(200, null, null)
                }
            }

    /** Saves the record as Flutter's payload (version 2, empty teeth left out). */
    suspend fun save(
        appointmentId: Long,
        record: DentalRecord,
    ): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.dentalDocumentation(appointmentId)) {
                contentType(ContentType.Application.Json)
                setBody(record.payload())
            }
        }
}

val dentalRepository = DentalRepository()
