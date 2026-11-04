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
 * Streams a server-sent-events response (the AI endpoints). Each event goes to [onEvent] as it arrives. A non-2xx
 * goes through the same path as [apiCall] (session rule, logging); an `error` event fails with a
 * [ApiException.ServerError] carrying its `code`. Not Ktor's SSE plugin: it can't send a POST body in 2.3.
 */
suspend fun HttpClient.streamEvents(
    build: HttpRequestBuilder.() -> Unit,
    onEvent: (SseEvent) -> Unit,
): Result<Unit> =
    try {
        prepareRequest {
            accept(ContentType.Text.EventStream)
            build()
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
 * The SSE wire format: a blank line ends an event, `data:` lines join with `\n`. An event the stream ends in the
 * middle of is dropped (WHATWG event-stream rules: only a blank line dispatches); the backend's emitter always ends
 * events with one.
 */
internal suspend fun readEvents(
    channel: ByteReadChannel,
    emit: (SseEvent) -> Unit,
) {
    var event = ""
    val data = mutableListOf<String>()

    fun flush() {
        if (data.isNotEmpty()) emit(SseEvent(event, data.joinToString("\n")))
        event = ""
        data.clear()
    }
    while (true) {
        val line = channel.readUTF8Line() ?: break
        when {
            line.isEmpty() -> flush()
            line.startsWith("event:") -> event = line.substring(6).trim()
            line.startsWith("data:") -> data += line.substring(5).removePrefix(" ")
        }
    }
}

private fun sseError(data: String): ApiException {
    val error = runCatching { apiJson.decodeFromString<shifa.doctor.data.model.ApiError>(data) }.getOrNull()
    httpLogger.log("FAILED stream error event ${error?.code.orEmpty()}")
    return ApiException.ServerError(500, error?.code, error?.requestId)
}
