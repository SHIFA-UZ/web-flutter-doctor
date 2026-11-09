package shifa.doctor.data.api

import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import shifa.doctor.data.model.ApiError
import shifa.doctor.data.model.ApiException

/**
 * Sends [request] and reads a [T] (CLAUDE.md "Data layer"). A non-2xx becomes an [ApiException] carrying the
 * backend's `code` and `requestId`; no response becomes [ApiException.NetworkError]. The session rule (17 §1) is
 * applied here, once, for every call: see [endsSession].
 */
suspend inline fun <reified T> apiCall(request: () -> HttpResponse): Result<T> {
    val response =
        try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            return Result.failure(e) // the pre-flight expiry check
        } catch (e: Throwable) {
            // not Exception: on the web a failed fetch is a kotlin.Error
            httpLogger.log("FAILED no response: ${e::class.simpleName}")
            return Result.failure(ApiException.NetworkError(timeout = isTimeout(e)))
        }
    if (!response.status.isSuccess()) return Result.failure(failure(response))
    return try {
        Result.success(response.body<T>())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        logFailure(response, null, "unreadable body")
        Result.failure(ApiException.ServerError(response.status.value, null, null))
    }
}

/**
 * A newer endpoint this backend doesn't have yet (404 or 405 on a route the app knows the object exists for): the
 * caller falls back to what it did before (BE-01: the PDFs it renders itself).
 */
val Throwable.endpointMissing: Boolean
    get() = this is ApiException && (status == 404 || status == 405)

/** Turns a non-2xx [response] into its [ApiException], ending the session when [endsSession] says so. */
@PublishedApi
internal suspend fun failure(response: HttpResponse): ApiException {
    val status = response.status.value
    // 15 §1: the backend can answer with an HTML error page; only JSON is read as an error body.
    val error = if (response.contentType()?.match(ContentType.Application.Json) == true) errorBody(response) else null
    val sentToken = response.call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")
    sentToken?.let { token -> endsSession(status, error?.code, sentToken = true)?.let { SessionEvents.end(it, token) } }
    logFailure(response, error, null)
    return ApiException.of(status, error?.code, error?.requestId).also { it.details = error?.details.orEmpty() }
}

/**
 * 17 §1, the whole rule: a 401 to a request that carried a token ends the session; `ACCOUNT_DISABLED` ends it with
 * its own reason; any other 403 never does (a role, tier or business refusal: the feature shows its own message).
 * The backend sends `ACCOUNT_DISABLED` as a 403 (`JwtAuthFilter`), so that code is the one 403 that ends it.
 */
@PublishedApi
internal fun endsSession(
    status: Int,
    code: String?,
    sentToken: Boolean,
): SessionEndReason? =
    when {
        code == "ACCOUNT_DISABLED" -> SessionEndReason.AccountDisabled
        status == 401 && sentToken -> SessionEndReason.Unauthorized
        else -> null
    }

@PublishedApi
internal fun isTimeout(e: Throwable) =
    e is HttpRequestTimeoutException || e is ConnectTimeoutException || e is SocketTimeoutException

// HIPAA (CLAUDE.md "Logging"): status, method, path with ids masked, code and requestId. Never the server's message
// (it can name a patient, e.g. "Appointment not found: 123"), headers or bodies. Debug builds only (httpLogger).
@PublishedApi
internal fun logFailure(
    response: HttpResponse,
    error: ApiError?,
    note: String?,
) {
    val request = response.call.request
    httpLogger.log(
        listOfNotNull(
            "FAILED ${response.status.value} ${request.method.value} ${maskIds(request.url.encodedPath)}",
            error?.code,
            error?.requestId?.let { "requestId=$it" },
            note,
        ).joinToString(" "),
    )
}

/** `/api/patients/123/documents/abc.pdf` → `/api/patients/{id}/documents/{id}`: any segment with a digit or a dot. */
@PublishedApi
internal fun maskIds(path: String): String =
    path.split('/').joinToString("/") { if (it.any { c -> c.isDigit() || c == '.' }) "{id}" else it }

@PublishedApi
internal suspend fun errorBody(response: HttpResponse): ApiError? =
    try {
        apiJson.decodeFromString<ApiError>(response.bodyAsText())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
