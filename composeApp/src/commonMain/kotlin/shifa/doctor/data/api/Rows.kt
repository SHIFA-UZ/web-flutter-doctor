package shifa.doctor.data.api

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.JsonElement

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
