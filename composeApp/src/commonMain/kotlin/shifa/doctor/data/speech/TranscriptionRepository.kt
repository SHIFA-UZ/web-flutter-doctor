package shifa.doctor.data.speech

import io.ktor.client.HttpClient
import io.ktor.client.plugins.timeout
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.apiCall
import shifa.doctor.data.api.httpClient
import shifa.doctor.data.storage.AppLanguage

/** `POST /ai/transcribe`'s answer: the text and the language the backend heard (en, uz, ru…), if it said. */
@Serializable
data class Transcript(
    val text: String = "",
    val language: String? = null,
) {
    /** Nothing was said: blank, or the backend's "(No speech detected)" placeholder. */
    val empty: Boolean get() = text.isBlank() || text.trim() == NO_SPEECH

    companion object {
        const val NO_SPEECH = "(No speech detected)"
    }
}

/**
 * Speech to text for the doctor (AI-05, 02 §7.2). The backend answers 403 without the PRO plan
 * (`SPEECH_TO_TEXT`), 429 over the daily AI limit and 502 when recognition failed; each stays a typed
 * failure ([shifa.doctor.data.model.ApiException]) for the field to say so in the app's language (Flutter showed
 * "error: <message>").
 */
class TranscriptionRepository(
    private val client: () -> HttpClient = { httpClient },
) {
    /**
     * The recording ([audio], AAC `.m4a`) as text. [language] is the app's, sent as a hint in the one mapping the app
     * has (`backendCode`: en, uz or ru; Cyrillic Uzbek is uz); null lets the backend detect it.
     */
    suspend fun transcribe(
        audio: ByteArray,
        language: AppLanguage?,
        recordingId: String?,
    ): Result<Transcript> =
        apiCall<Transcript> {
            client().submitFormWithBinaryData(
                ApiConfig.Endpoints.AI_TRANSCRIBE,
                formData {
                    append("file", audio, audioPart(recordingId))
                    language?.let { append("languageHint", it.backendCode) }
                    recordingId?.let { append("recordingId", it) }
                },
            ) {
                timeout {
                    requestTimeoutMillis = TRANSCRIBE_TIMEOUT_MS
                    socketTimeoutMillis = TRANSCRIBE_TIMEOUT_MS
                }
            }
        }.map { it.copy(text = it.text.trim(), language = it.language?.trim()?.lowercase()) }

    /**
     * "Report" on a transcription (when the public config collects them): the text, the app's language and the
     * recording; 404 means the backend stopped collecting.
     */
    suspend fun report(
        transcript: String,
        language: AppLanguage?,
        audio: ByteArray?,
        recordingId: String?,
    ): Result<Unit> =
        apiCall<Unit> {
            client().submitFormWithBinaryData(
                ApiConfig.Endpoints.AI_TRANSCRIPTION_FEEDBACK,
                formData {
                    append("transcript", transcript)
                    language?.let { append("locale", it.backendCode) }
                    audio?.let { append("file", it, audioPart(recordingId)) }
                },
            )
        }

    private fun audioPart(recordingId: String?) =
        Headers.build {
            append(HttpHeaders.ContentType, "audio/mp4")
            append(HttpHeaders.ContentDisposition, "filename=\"${recordingId ?: "speech"}.m4a\"")
        }

    private companion object {
        const val TRANSCRIBE_TIMEOUT_MS = 90_000L // 02 §7.2
    }
}

val transcriptionRepository by lazy { TranscriptionRepository() }
