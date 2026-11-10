package shifa.doctor.data.badges

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient

@Serializable
private data class CountDto(
    val count: Long = 0,
)

/** The shell's counts (SHELL-03, 01 §6.3); each feature's own screen owns the lists behind them. */
class BadgesRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /messages/unread-count` → `{count}` (dev, 2026-11-04). */
    suspend fun chatUnread(): Result<Int> =
        apiCall<CountDto> { client().get(ApiConfig.Endpoints.CHAT_UNREAD) }.map { it.count.toInt() }

    /** `GET /notifications/unread/count` → `{count}`. */
    suspend fun notificationsUnread(): Result<Int> =
        apiCall<CountDto> { client().get(ApiConfig.Endpoints.NOTIFICATIONS_UNREAD) }.map { it.count.toInt() }

    /**
     * The Tasks badge (active remote-care tasks) and Home's follow-up count (active and draft, flow 02/02 §4), both
     * from one `GET /tasks`: no count endpoint exists, and the status filter takes one value.
     */
    suspend fun taskCounts(): Result<TaskCounts> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.TASKS) }.map { rows ->
            val statuses =
                rows.map { ((it as? JsonObject)?.get("status") as? JsonPrimitive)?.contentOrNull?.uppercase() }
            val active = statuses.count { it == "ACTIVE" }
            TaskCounts(active = active, followUps = active + statuses.count { it == "DRAFT" })
        }
}

data class TaskCounts(
    val active: Int,
    val followUps: Int,
)
