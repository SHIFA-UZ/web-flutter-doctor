package shifa.doctor.data.schedule

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.api.rows
import shifa.doctor.data.model.DateSpecificRuleDto
import shifa.doctor.data.model.RuleDto
import shifa.doctor.data.model.ValidityPeriodDto

@Serializable
private data class ValidRange(
    val validFrom: String,
    val validUntil: String,
)

/**
 * The schedule (SCHED-1, 03 §2): the weekly template per location, the validity periods and date-specific hours. One
 * location scope everywhere: `?locationId=` on the request and `locationId` in the rows (Flutter mixed the two).
 */
class ScheduleRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * Always for one location: without one the backend answers every location's rules, and saving those back would move
     * them all to the primary.
     */
    suspend fun rules(locationId: Long): Result<WeeklySchedule> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.SCHEDULE_RULES) { parameter("locationId", locationId) }
        }.map { WeeklySchedule.of(it.rows(RuleDto.serializer(), "schedule rules")) }

    /** Replaces the location's whole weekly template. */
    suspend fun saveRules(
        locationId: Long,
        schedule: WeeklySchedule,
    ): Result<Unit> =
        apiCall<Unit> {
            client().put(ApiConfig.Endpoints.SCHEDULE_RULES) {
                parameter("locationId", locationId)
                contentType(ContentType.Application.Json)
                setBody(schedule.toRules(locationId))
            }
        }

    suspend fun periods(): Result<List<ValidityPeriodDto>> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.SCHEDULE_VALIDITY_PERIODS) }
            .map { it.rows(ValidityPeriodDto.serializer(), "validity periods") }

    /**
     * `PATCH /schedule/valid-range`: adds a validity period (one already inside an existing period changes nothing).
     * No start means "from today" ([today], the practice date): the start is always sent, because without one the
     * backend makes a one-day period ending on [until].
     */
    suspend fun addPeriod(
        from: LocalDate?,
        until: LocalDate,
        today: LocalDate,
    ): Result<Unit> =
        apiCall<Unit> {
            client().patch(ApiConfig.Endpoints.SCHEDULE_VALID_RANGE) {
                contentType(ContentType.Application.Json)
                setBody(ValidRange((from ?: today).toString(), until.toString()))
            }
        }

    suspend fun dateSpecific(locationId: Long): Result<List<DateSpecificRuleDto>> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.SCHEDULE_DATE_SPECIFIC) { parameter("locationId", locationId) }
        }.map { it.rows(DateSpecificRuleDto.serializer(), "date-specific hours") }

    suspend fun addDateSpecific(rule: DateSpecificRuleDto): Result<DateSpecificRuleDto> =
        apiCall<DateSpecificRuleDto> {
            client().post(ApiConfig.Endpoints.SCHEDULE_DATE_SPECIFIC) {
                contentType(ContentType.Application.Json)
                setBody(rule)
            }
        }

    suspend fun deleteDateSpecific(id: Long): Result<Unit> =
        apiCall<Unit> { client().delete(ApiConfig.Endpoints.scheduleDateSpecific(id)) }
}

val scheduleRepository by lazy { ScheduleRepository() }
