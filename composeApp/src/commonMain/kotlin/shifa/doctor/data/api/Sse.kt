package shifa.doctor.data.api

import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.accept
import io.ktor.client.request.prepareRequest
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.CancellationException
import shifa.doctor.data.model.ApiException

/** One server-sent event: [event] is empty for the default `message` type. */
data class SseEvent(
    val event: String,
    val data: String,
)

/**
 * Streams a server-sent-events response (the AI endpoints). Each event goes to [onEvent] as it arrives; cancelling the
 * calling coroutine closes the connection. A non-2xx goes through the same path as [apiCall] (session rule, logging);
 * an `error` event fails with the [ApiException] its `code` names. Not Ktor's SSE plugin: it can't send a POST body
 * in 2.3.
 */
suspend fun HttpClient.streamEvents(
    // Not `build`: inside the builder that name is HttpRequestBuilder.build(), and the request went out as a bare GET.
    configure: HttpRequestBuilder.() -> Unit,
    onEvent: suspend (SseEvent) -> Unit,
): Result<Unit> =
    try {
        prepareRequest {
            accept(ContentType.Text.EventStream)
            configure()
        }.execute { response ->
            if (!response.status.isSuccess()) throw failure(response)
            readEvents(response.bodyAsChannel()) { e ->
                if (e.event == "error") throw sseError(e.data)
                onEvent(e)
            }
        }
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: ApiException) {
        Result.failure(e)
    } catch (e: Exception) {
        httpLogger.log("FAILED stream, no response: ${e::class.simpleName}")
        Result.failure(ApiException.NetworkError(timeout = isTimeout(e)))
    }

/**
 * The backend's SSE wire format (02 §3.2, Spring's `SseEmitter`): a blank line ends an event; `data:` lines join with
 * `\n` and are kept verbatim: Spring writes `data:<token>` with no space, so a leading space is part of the token
 * (WHATWG's one-space strip would glue words together). `id:`, `retry:` and `:` comments are ignored. An event the
 * stream ends in the middle of is still delivered, with its type (Flutter flushed it as a token; an `error` must
 * still fail).
 */
internal suspend fun readEvents(
    channel: ByteReadChannel,
    emit: suspend (SseEvent) -> Unit,
) {
    var event = ""
    val data = mutableListOf<String>()

    suspend fun flush() {
        if (data.isNotEmpty()) emit(SseEvent(event, data.joinToString("\n")))
        event = ""
        data.clear()
    }
    while (true) {
        val line = channel.readUTF8Line() ?: break
        when {
            line.isEmpty() -> flush()
            line.startsWith("event:") -> event = line.substring(6).trim()
            line.startsWith("data:") -> data += line.substring(5)
        }
    }
    flush()
}

private fun sseError(data: String): ApiException {
    val error = runCatching { apiJson.decodeFromString<shifa.doctor.data.model.ApiError>(data) }.getOrNull()
    val code = error?.code
    httpLogger.log("FAILED stream error event ${code.orEmpty()}")
    // The backend's AiStreamException codes (VALIDATION, AI_UNAVAILABLE…) on a 200 stream: typed like the HTTP ones.
    return when (code) {
        "VALIDATION" -> ApiException.ValidationError(400, code, error.requestId)
        "RATE_LIMIT", "RATE_LIMITED" -> ApiException.RateLimit(code, error.requestId)
        else -> ApiException.ServerError(500, code, error?.requestId)
    }
}
