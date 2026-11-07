package shifa.doctor.data.badges

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
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

    /** Active remote-care tasks: `GET /tasks?status=ACTIVE`, counted (no count endpoint exists). */
    suspend fun activeTasks(): Result<Int> =
        apiCall<List<JsonElement>> {
            client().get(ApiConfig.Endpoints.TASKS) { parameter("status", "ACTIVE") }
        }.map { it.size }

    /**
     * Home's follow-up count (flow 02/02 §4): active and draft remote-care tasks, from one `GET /tasks` (no count
     * endpoint, and the status filter takes one value).
     */
    suspend fun followUps(): Result<Int> =
        apiCall<List<JsonElement>> { client().get(ApiConfig.Endpoints.TASKS) }.map { rows ->
            rows.count { row ->
                val status = ((row as? JsonObject)?.get("status") as? JsonPrimitive)?.contentOrNull?.uppercase()
                status == "ACTIVE" || status == "DRAFT"
            }
        }
}
