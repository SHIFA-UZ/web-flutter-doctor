package shifa.doctor.data.settings

import io.ktor.client.HttpClient
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient

@Serializable
data class LanguageRequest(
    val language: String,
)

/** `PUT /doctors/me/language` (01 §8, 204): the backend's copy of the UI language, for pushes and e-mails. */
class LanguageRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    // The session number and the language synced in it change together (CAS), so a reply that lands after
    // reset() can never mark the next session as synced.
    private data class Synced(
        val session: Int,
        val code: String?,
    )

    private val mutex = Mutex()
    private val state = MutableStateFlow(Synced(0, null))

    /** Sends [backendCode] (`en`, `uz`, `ru`) once per change; a failure is retried on the next call. One at a time. */
    suspend fun sync(backendCode: String): Result<Unit> =
        mutex.withLock {
            val started = state.value
            if (started.code == backendCode) return@withLock Result.success(Unit)
            apiCall<Unit> {
                client().put(ApiConfig.Endpoints.DOCTOR_LANGUAGE) {
                    contentType(ContentType.Application.Json)
                    setBody(LanguageRequest(backendCode))
                }
            }.onSuccess { state.update { if (it.session == started.session) it.copy(code = backendCode) else it } }
        }

    /** Logout: the next session syncs again. */
    fun reset() {
        state.update { Synced(it.session + 1, null) }
    }
}
