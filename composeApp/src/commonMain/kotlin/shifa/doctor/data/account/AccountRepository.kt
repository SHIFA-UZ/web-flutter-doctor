package shifa.doctor.data.account

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient

/** Where the deletion code went (`POST /doctors/me/delete-account/request`). */
@Serializable
data class DeletionRequested(
    val maskedEmail: String,
    val expiresInSeconds: Long = 600,
)

@Serializable
private class ConfirmDeletion(
    val code: String,
)

/**
 * The doctor's own account (flow 08/12, BE-02): a copy of their data and deleting the account. Deletion is two steps:
 * [requestDeletion] e-mails a code (409 `HAS_ACTIVE_OBLIGATIONS` with `details` when something must be resolved first),
 * [confirmDeletion] with that code closes the account.
 */
class AccountRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /** `GET /doctors/me/export`: the JSON file, once a day (429 otherwise). */
    suspend fun exportData(): Result<ByteArray> = apiCall<ByteArray> { client().get(ApiConfig.Endpoints.DATA_EXPORT) }

    suspend fun requestDeletion(): Result<DeletionRequested> =
        apiCall<DeletionRequested> { client().post(ApiConfig.Endpoints.DELETE_ACCOUNT_REQUEST) }

    suspend fun confirmDeletion(code: String): Result<Unit> =
        apiCall<JsonObject> {
            client().post(ApiConfig.Endpoints.DELETE_ACCOUNT_CONFIRM) {
                contentType(ContentType.Application.Json)
                setBody(ConfirmDeletion(code))
            }
        }.map { }
}
