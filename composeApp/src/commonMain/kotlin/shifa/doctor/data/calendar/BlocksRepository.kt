package shifa.doctor.data.calendar

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient

/** `POST /schedule/blocks` body and answer (03 §6); the answer carries how many appointments were cancelled. */
@Serializable
data class BlockDto(
    val startAt: String,
    val endAt: String,
    val cancelOverlappingAppointments: Boolean,
    val reason: String? = null,
    val id: Long? = null,
    val cancelledAppointmentCount: Int? = null,
    val resourceDoctorId: Long? = null,
)

@Serializable
private data class OverlapCount(
    val count: Int = 0,
)

/** Time blocks (CAL-8, 03 §1.8): how many appointments a range would hit, create, remove. */
class BlocksRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /schedule/blocks/overlapping-count?startAt=&endAt=`: the appointments inside the range. */
    suspend fun overlapping(
        start: Instant,
        end: Instant,
    ): Result<Int> =
        apiCall<OverlapCount> {
            client().get(ApiConfig.Endpoints.SCHEDULE_BLOCKS_OVERLAP) {
                parameter("startAt", start.toString())
                parameter("endAt", end.toString())
            }
        }.map { it.count }

    /** Blocks [start]..[end]; the result is how many appointments the backend cancelled. */
    suspend fun create(
        start: Instant,
        end: Instant,
        reason: String?,
        cancelOverlapping: Boolean,
    ): Result<Int> =
        apiCall<BlockDto> {
            client().post(ApiConfig.Endpoints.SCHEDULE_BLOCKS) {
                contentType(ContentType.Application.Json)
                setBody(BlockDto(start.toString(), end.toString(), cancelOverlapping, reason))
            }
        }.map { it.cancelledAppointmentCount ?: 0 }

    suspend fun remove(id: Long): Result<Unit> =
        apiCall<Unit> {
            client().delete(
                ApiConfig.Endpoints.scheduleBlock(id),
            )
        }
}

val blocksRepository by lazy { BlocksRepository() }
