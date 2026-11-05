package shifa.doctor.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement
import shifa.doctor.data.model.ApiException

/**
 * Each row of a JSON list decoded (and [map]ped; null means unusable) on its own: a row that can't be read is skipped and counted in the log
 * (no content), so one bad row never drops the whole list (project rule).
 */
fun <T, R : Any> List<JsonElement>.rows(
    serializer: KSerializer<T>,
    what: String,
    map: (T) -> R?,
): List<R> {
    var skipped = 0
    val parsed =
        mapNotNull { row ->
            val item = runCatching { map(apiJson.decodeFromJsonElement(serializer, row)) }.getOrNull()
            if (item == null) skipped++
            item
        }
    if (skipped > 0) httpLogger.log("FAILED parse $what: $skipped of $size rows skipped")
    return parsed
}

fun <T : Any> List<JsonElement>.rows(
    serializer: KSerializer<T>,
    what: String,
): List<T> = rows(serializer, what) { it }

/**
 * One object the backend answered but that can't be used (no id, no date…): a typed server error, logged without
 * content, as `apiCall` treats an unreadable body; failures pass through.
 */
fun <T, R : Any> Result<T>.usable(
    what: String,
    map: (T) -> R?,
): Result<R> =
    fold(
        { value ->
            runCatching { map(value) }.getOrNull()?.let { Result.success(it) } ?: run {
                httpLogger.log("FAILED unusable $what")
                Result.failure(ApiException.ServerError(HTTP_OK, null, null))
            }
        },
        { Result.failure(it) },
    )

private const val HTTP_OK = 200
