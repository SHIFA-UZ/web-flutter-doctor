package shifa.doctor.data.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.plugin
import io.ktor.client.request.bearerAuth
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.encodedPath
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import shifa.doctor.data.debug.BackendChoice
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.storage.tokenStorage
import shifa.doctor.utils.Jwt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

expect fun createHttpEngine(): HttpClientEngine

/** Where failed calls are logged (CLAUDE.md "Logging"): Logcat / Xcode console in debug builds, nothing in release. */
fun interface HttpLogger {
    fun log(message: String)
}

expect val httpLogger: HttpLogger

// isLenient reads numeric ids into String fields and the numeric `error` of an error body (15 §1).
internal val apiJson =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        explicitNulls = false // optional keys are omitted, not sent as null
        coerceInputValues = true // a null list reads as the default, not a dropped item
    }

// One client per backend (Debug menu → Development backend), built on first use. CORE-08 adds the demo clients.
private val realClients =
    BackendChoice.Backend.entries.associateWith {
        lazy {
            createHttpClient(
                { tokenStorage.token },
                baseUrl = it.baseUrl,
            )
        }
    }

/** The app-wide client for the chosen backend. */
val httpClient: HttpClient get() = realClients.getValue(BackendChoice.current.value).value

/**
 * The Ktor client (CLAUDE.md "Data layer"). [token] is read per request. It goes only to the backend host, never on
 * public paths, and never over an Authorization header the caller set (the Firebase bearer of `auth/verify`).
 * Pre-flight (01 §3): an expired token ends the session here, without a request.
 */
@OptIn(ExperimentalTime::class)
fun createHttpClient(
    token: () -> String?,
    engine: HttpClientEngine = createHttpEngine(),
    baseUrl: String = ApiConfig.BASE_URL,
    nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
): HttpClient {
    val apiHost = Url(baseUrl).host
    return HttpClient(engine) {
        install(ContentNegotiation) { json(apiJson) }
        // 15 §1: 30 s on every verb (Flutter only had it on GET/POST).
        install(HttpTimeout) {
            requestTimeoutMillis = ApiConfig.TIMEOUT_MS
            connectTimeoutMillis = ApiConfig.TIMEOUT_MS
            socketTimeoutMillis = ApiConfig.TIMEOUT_MS
        }
        defaultRequest { url(baseUrl) }
    }.apply {
        plugin(HttpSend).intercept { request ->
            val stored = token()
            if (request.url.host == apiHost &&
                ApiConfig.needsToken(request.url.encodedPath) &&
                !request.headers.contains(HttpHeaders.Authorization) &&
                stored != null
            ) {
                if (Jwt.isExpired(stored, nowEpochSeconds(), ApiConfig.EXPIRY_GRACE_SECONDS)) {
                    SessionEvents.end(SessionEndReason.Expired)
                    throw ApiException.Unauthorized(SESSION_EXPIRED_LOCALLY, null)
                }
                request.bearerAuth(stored)
            }
            execute(request)
        }
    }
}

/** The `code` of the pre-flight failure; the backend's own is `SESSION_EXPIRED`, so the banner reads the same. */
const val SESSION_EXPIRED_LOCALLY = "SESSION_EXPIRED"
