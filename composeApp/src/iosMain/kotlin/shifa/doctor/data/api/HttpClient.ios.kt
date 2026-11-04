package shifa.doctor.data.api

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import shifa.doctor.utils.isDebugBuild

actual fun createHttpEngine(): HttpClientEngine = Darwin.create()

actual val httpLogger: HttpLogger = if (isDebugBuild) HttpLogger { println("HttpClient: $it") } else HttpLogger { }
