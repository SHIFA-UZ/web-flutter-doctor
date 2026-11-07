package shifa.doctor.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.js.Js
import shifa.doctor.utils.isDebugBuild

actual fun createHttpEngine(): HttpClientEngine = Js.create()

// Errors only, debug builds only (the same rule as Android and iOS): the browser console.
actual val httpLogger: HttpLogger = if (isDebugBuild) HttpLogger { consoleLog("HttpClient: $it") } else HttpLogger { }

private fun consoleLog(message: String): Unit = js("console.log(message)")
