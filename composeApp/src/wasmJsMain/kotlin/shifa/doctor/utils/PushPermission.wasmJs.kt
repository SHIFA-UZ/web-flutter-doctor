package shifa.doctor.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import shifa.doctor.data.push.Push

/**
 * PUSH on the web: index.html's `shifaPush` (Firebase Messaging, loaded now) asks on the doctor's first click or key,
 * hands this device's token, pushes while the tab is open and taps (from the service worker, or the `#push=` an
 * opened app came with) to [Push], as the phones' platform code does.
 */
@Composable
actual fun AskPushPermission() {
    LaunchedEffect(Unit) {
        startWebPush(
            { token -> Push.onToken(token) },
            { json -> pushData(json)?.let(Push::onReceived) },
            { json -> pushData(json)?.let(Push::onTap) },
        )
    }
}

private fun startWebPush(
    onToken: (String?) -> Unit,
    onReceived: (String) -> Unit,
    onTapped: (String) -> Unit,
): Unit = js("window.shifaPush && window.shifaPush.start(onToken, onReceived, onTapped)")

// A push's data: string keys and values (FCM sends strings; anything else is dropped).
private fun pushData(json: String): Map<String, String>? =
    runCatching {
        Json
            .parseToJsonElement(json)
            .jsonObject
            .mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it.isString }?.let { k to it.content } }
            .toMap()
    }.getOrNull()
