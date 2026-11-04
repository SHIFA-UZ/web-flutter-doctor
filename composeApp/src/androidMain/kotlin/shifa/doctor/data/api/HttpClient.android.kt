package shifa.doctor.data.api

import android.util.Log
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import shifa.doctor.utils.isDebugBuild

actual fun createHttpEngine(): HttpClientEngine = OkHttp.create()

// Logcat tag `HttpClient`; plain JVM unit tests only have android.jar's stub Log, which throws, so they print.
actual val httpLogger: HttpLogger =
    if (isDebugBuild) {
        HttpLogger { message -> runCatching { Log.d("HttpClient", message) }.onFailure { println(message) } }
    } else {
        HttpLogger { }
    }
