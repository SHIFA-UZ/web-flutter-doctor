package shifa.doctor.data.config

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.get
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.utils.appVersion
import shifa.doctor.utils.platformName

/**
 * `GET /public/config` (no token). Today it holds `googleMapsApiKey` (never used: D-06) and
 * `transcriptionFeedbackEnabled`; the `doctorApp` block is the BE-04 proposal (`docs/backend-improvements.md`),
 * read leniently so the app gates itself as soon as the backend sends it.
 */
@Serializable
data class PublicConfig(
    val doctorApp: AppGateConfig? = null,
    // "true" or true (Flutter read both); anything else is off.
    val transcriptionFeedbackEnabled: JsonElement? = null,
) {
    val feedbackEnabled: Boolean
        get() = (transcriptionFeedbackEnabled as? JsonPrimitive)?.content?.lowercase() == "true"
}

@Serializable
data class AppGateConfig(
    val minVersion: PerPlatform? = null,
    val latestVersion: PerPlatform? = null,
    val storeUrl: PerPlatform? = null,
    val maintenance: Maintenance? = null,
)

@Serializable
data class PerPlatform(
    val android: String? = null,
    val ios: String? = null,
) {
    fun of(platform: String) = if (platform == "ios") ios else android
}

@Serializable
data class Maintenance(
    val active: Boolean = false,
    val message: Map<String, String> = emptyMap(),
)

/** What the splash must show before anything else (flow 01/01 §3, items 6–7). */
sealed interface BootGate {
    data object None : BootGate

    data class Update(
        val storeUrl: String?,
    ) : BootGate

    /** [message] is the backend's text in the app language when it sent one; else the app's own copy shows. */
    data class Maintenance(
        val message: String?,
    ) : BootGate
}

class PublicConfigRepository(
    private val client: () -> HttpClient = { httpClient },
    private val version: String = appVersion,
    private val platform: String = platformName,
    private val timeoutMs: Long = TIMEOUT_MS,
) {
    /**
     * The gate for this build: no `doctorApp` block means [BootGate.None]. A failure, including no answer within
     * [timeoutMs] (5 s), is returned as such; the splash decides it must not block launch (flow 01/01 §6).
     */
    suspend fun bootGate(language: String): Result<BootGate> =
        apiCall<PublicConfig> {
            client().get(ApiConfig.Endpoints.PUBLIC_CONFIG) { timeout { requestTimeoutMillis = timeoutMs } }
        }.map { gateOf(it.doctorApp, language) }

    private var feedback: Result<Boolean>? = null

    /**
     * Whether the backend collects transcription reports (AI-05, 02 §7.2: the "Report" action on a transcription).
     * Read once per run, a failure included (no second 5 s wait); the caller offers Report only on a success that
     * says true.
     */
    suspend fun transcriptionFeedbackEnabled(): Result<Boolean> =
        feedback ?: apiCall<PublicConfig> {
            client().get(ApiConfig.Endpoints.PUBLIC_CONFIG) { timeout { requestTimeoutMillis = timeoutMs } }
        }.map { it.feedbackEnabled }.also { feedback = it }

    private fun gateOf(
        gate: AppGateConfig?,
        language: String,
    ): BootGate {
        if (gate == null) return BootGate.None
        gate.maintenance?.takeIf { it.active }?.let { m ->
            return BootGate.Maintenance(
                m.message[language]?.takeIf(String::isNotBlank) ?: m.message["en"]?.takeIf(String::isNotBlank),
            )
        }
        val min = gate.minVersion?.of(platform)
        if (min != null && Versions.isOlder(version, min)) return BootGate.Update(gate.storeUrl?.of(platform))
        return BootGate.None
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L // flow 01/01 §6
    }
}

/** The app's one instance: what it reads once per run is kept here. */
val publicConfigRepository by lazy { PublicConfigRepository() }

/** Dotted numeric versions ("1.0.1"); anything after a dash ("-debug") is ignored, missing parts count as 0. */
object Versions {
    fun isOlder(
        current: String,
        required: String,
    ): Boolean {
        val a = parts(current)
        val b = parts(required)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x < y
        }
        return false
    }

    private fun parts(version: String) = version.substringBefore('-').trim().split('.').map { it.toIntOrNull() ?: 0 }
}
