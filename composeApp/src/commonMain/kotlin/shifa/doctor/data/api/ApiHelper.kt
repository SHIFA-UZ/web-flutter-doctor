package shifa.doctor.data.api

import io.ktor.client.call.body
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequest
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.Url
import io.ktor.http.contentType
import io.ktor.http.encodedPath
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import shifa.doctor.data.model.ApiError
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.ApiEvents
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Sends [request] and reads a [T] (CLAUDE.md "Data layer"). A non-2xx becomes an [ApiException] carrying the
 * backend's `code` and `requestId`; no response becomes [ApiException.NetworkError]. The session rule (17 §1) is
 * applied here, once, for every call: see [endsSession].
 */
suspend inline fun <reified T> apiCall(request: () -> HttpResponse): Result<T> {
    val started = TimeSource.Monotonic.markNow()
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
    val body =
        try {
            response.body<T>()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logFailure(response, null, "unreadable body")
            val failure = ApiException.ServerError(response.status.value, null, null)
            trackApiError(response, failure)
            return Result.failure(failure)
        }
    trackApiSlow(response, started.elapsedNow())
    return Result.success(body)
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
    return ApiException.of(status, error?.code, error?.requestId).also {
        it.details = error?.details.orEmpty()
        trackApiError(response, it)
    }
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

/**
 * `api_error` / `api_slow` (docs/posthog, A1b) go here; tests swap it for a `RecordingTracker`. Tracking never calls
 * the API (the SDKs send on their own), so these can't loop. `apiCall` runs on its caller's dispatcher, a background
 * one too (`PushRegistration`): `Tracking` locks for that.
 */
@PublishedApi
internal var apiTracker: Tracker = Tracking

/** A call that took longer than this, and still succeeded, is `api_slow` (the patient app's threshold). */
private val SLOW_CALL = 5.seconds

/**
 * A failed call to our backend's API, with a response. A 401 that ends the session counts too (as in the patient app:
 * the alert should see a wave of them). Nothing for another host or a file URL (no `/api/`): [backendEndpoint].
 */
@PublishedApi
internal fun trackApiError(
    response: HttpResponse,
    failure: ApiException,
) {
    val request = response.call.request
    val endpoint = backendEndpoint(request) ?: return
    val code = failure.code?.takeIf { BACKEND_CODE.matches(it) }
    apiTracker.track(
        ApiEvents.ApiError(endpoint, request.method.value, failure.status, failure.toFailureReason(), code),
    )
}

@PublishedApi
internal fun trackApiSlow(
    response: HttpResponse,
    elapsed: Duration,
) {
    if (elapsed <= SLOW_CALL) return
    val request = response.call.request
    val endpoint = backendEndpoint(request) ?: return
    apiTracker.track(ApiEvents.ApiSlow(endpoint, request.method.value, elapsed.inWholeMilliseconds))
}

// The backend's error codes look like `APPOINTMENT_LOCKED_24H`; anything else isn't sent.
private val BACKEND_CODE = Regex("[A-Z][A-Z0-9_]{1,39}")

private val BACKEND_HOSTS = setOf(Url(ApiConfig.BASE_URL).host, Url(ApiConfig.DEV_BASE_URL).host)

/** [endpointLabel] of a call to our backend's API; null for anything else (a file URL, a signed URL, another host). */
internal fun backendEndpoint(request: HttpRequest): String? {
    val url = request.url
    val ours = url.host in BACKEND_HOSTS && url.encodedPath.startsWith("/api/")
    return if (ours) endpointLabel(url.encodedPath) else null
}

/**
 * `/api/patients/123/documents?x=1` → `patients/{id}/documents`: only the words the API is made of survive; any other
 * segment (an id, a uuid, a token, a file name) becomes `{id}`, so the label never carries data. The query is not part
 * of an encoded path.
 */
internal fun endpointLabel(path: String): String =
    path
        .removePrefix("/api/")
        .split('/')
        .joinToString("/") { if (it in ENDPOINT_WORDS || it.isEmpty()) it else "{id}" }

// The literal segments of ApiConfig.Endpoints and the words callers pass to its functions (finance paths, partnership
// actions, clinical-engine paths). A new endpoint's words go here, or its label shows `{id}` (ApiTrackingTest).
private val ENDPOINT_WORDS =
    setOf(
        "accept", "ai", "analytics", "appointment", "appointment-ledger", "appointments", "appointments-trend",
        "approve", "auth", "billing", "blocks", "book", "briefing", "calendar", "cancel", "care-partnerships",
        "catalog-items", "certificate", "change", "check-existing-doctor", "check-existing-patient", "check-ins",
        "checkout", "chips", "clinical-engine", "clinics", "commit", "complete", "config", "confirm", "connect",
        "consultation-notes", "consultation-types", "consultations", "contact", "content", "contract", "conversations",
        "count", "create-account", "dashboard", "date-specific", "decline", "delete-account", "dental-documentation",
        "detail", "discard", "diseases", "doctor", "doctor-earnings", "doctors", "document-access-requests",
        "documents", "download", "draft", "draft-notes", "engagement", "export", "fcm-tokens", "finance",
        "finance-settings", "for-assignment", "forgot-password-reset", "forms", "fulfill", "fulfillment-candidates",
        "geocode",
        "groups", "icd10", "installment-items", "installment-plans", "installments", "invitations", "language",
        "ledger", "legal", "lines", "locations", "login", "mark-paid", "me", "members", "messages", "morning", "mucosa",
        "no-show", "notifications", "notify", "notify-payment-reminder", "occlusion", "onboard", "overlapping-count",
        "overview", "overview-stats", "page", "partners", "patient", "patient-briefing", "patients", "payments", "pdf",
        "photo", "plan-services", "plans", "presence", "professions", "profile", "progress", "prophylaxis", "public",
        "read", "read-all", "record-usage", "records", "register", "register-clinic-staff", "reject", "request",
        "request-access", "request-signature", "reset-password", "retry", "revenue-share", "reverse", "rules",
        "schedule",
        "scribe-status", "search", "send", "send-email-otp", "send-forgot-password-otp", "send-login-otp",
        "send-login-sms-otp", "send-test-sms", "service-groups", "services", "settings", "shared", "sms-usage", "speak",
        "start", "status", "stream", "stripe", "subscription", "synthesize", "tasks", "templates", "test-notification",
        "timeline", "token", "top-diagnoses", "transcribe", "transcription-feedback", "treatment-plans", "unread",
        "unread-count", "upload-attachment", "upload-recording", "valid-range", "validity-periods", "verify-email-otp",
        "verify-key", "verify-login-sms-otp", "video", "visit-facts", "visits", "xray",
    )
