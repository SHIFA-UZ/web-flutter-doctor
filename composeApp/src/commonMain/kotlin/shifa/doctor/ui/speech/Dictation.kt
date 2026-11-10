package shifa.doctor.ui.speech

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.config.publicConfigRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.speech.TranscriptionRepository
import shifa.doctor.data.speech.transcriptionRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.durationBucket
import shifa.doctor.data.tracking.events.VisitEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiDailyLimitReached
import shifa.doctor.generated.resources.errorRecordingVoice
import shifa.doctor.generated.resources.microphonePermissionDenied
import shifa.doctor.generated.resources.noSpeechDetected
import shifa.doctor.generated.resources.speechToTextRequiresPro
import shifa.doctor.generated.resources.transcriptionAdded
import shifa.doctor.generated.resources.transcriptionFailed
import shifa.doctor.generated.resources.transcriptionReportFailed
import shifa.doctor.generated.resources.transcriptionReportThanks
import shifa.doctor.generated.resources.transcriptionTimedOut
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.VoiceRecorder
import shifa.doctor.utils.currentInstant
import kotlin.time.Duration.Companion.milliseconds

data class DictationState(
    // The field recording or transcribing now (one at a time: other mics wait, flow 05/05 §7).
    val field: String? = null,
    val transcribing: Boolean = false,
    val level: Float = 0f,
    val seconds: Int = 0,
    val banner: CodeBanner? = null,
    // The field whose last transcription can be reported (the public config collects reports).
    val reportable: String? = null,
)

/**
 * Speech to text for text fields (AI-06, spec 02 §7.3, flow 05/05): record, then the transcript is appended to the
 * field that asked. One recording at a time, a live level and timer for the pill, and messages in the app's language
 * (Flutter showed "error: <message>"). Owned by a screen, in its [scope].
 */
class Dictation(
    private val scope: CoroutineScope,
    private val recorder: VoiceRecorder,
    private val repo: TranscriptionRepository = transcriptionRepository,
    private val config: PublicConfigRepository = publicConfigRepository,
    private val language: () -> AppLanguage = { languageStorage.language.value },
    private val now: () -> Instant = ::currentInstant,
    private val tracker: Tracker = Tracking,
) {
    private val _state = MutableStateFlow(DictationState())
    val state: StateFlow<DictationState> = _state.asStateFlow()

    private var ticking: Job? = null
    private var last: Pair<String, ByteArray>? = null
    private var lastId: String? = null

    /** The microphone was refused for [field] (flow 05/05 §6). */
    fun denied(field: String) {
        tracker.track(VisitEvents.DictationMicDenied(fieldKind(field)))
        _state.update { it.copy(banner = CodeBanner(Res.string.microphonePermissionDenied)) }
    }

    /** Starts recording for [field] (the microphone is allowed); a second field waits for the first. */
    fun start(field: String) {
        if (_state.value.field != null) return
        if (!recorder.start()) {
            tracker.track(
                if (field == SCRIBE_FIELD) {
                    VisitEvents.ScribeFailed(RECORDING_FAILED, "record")
                } else {
                    VisitEvents.DictationFailed(fieldKind(field), RECORDING_FAILED)
                },
            )
            return _state.update { it.copy(banner = CodeBanner(Res.string.errorRecordingVoice)) }
        }
        tracker.track(
            when (field) {
                SCRIBE_FIELD -> VisitEvents.ScribeRecordingStarted
                else -> VisitEvents.DictationStarted(fieldKind(field))
            },
        )
        val started = now()
        _state.update {
            it.copy(field = field, transcribing = false, banner = null, reportable = null, level = 0f, seconds = 0)
        }
        ticking =
            scope.launch {
                while (true) {
                    _state.update {
                        it.copy(
                            level = recorder.level(),
                            seconds = (now() - started).inWholeSeconds.toInt(),
                        )
                    }
                    delay(TICK)
                }
            }
    }

    /** ✕: the recording is dropped, nothing is added. */
    fun cancel() {
        val field = _state.value.field
        if (_state.value.transcribing) return
        if (field != null) {
            tracker.track(
                when (field) {
                    SCRIBE_FIELD -> VisitEvents.ScribeRecordingCancelled
                    else -> VisitEvents.DictationCancelled(fieldKind(field))
                },
            )
        }
        ticking?.cancel()
        recorder.cancel()
        _state.update { DictationState() }
    }

    /** ✓: the recording is transcribed and [append]ed to its field; the messages say how it went. */
    fun finish(append: (field: String, text: String) -> Unit) {
        val s = _state.value
        val field = s.field ?: return
        if (s.transcribing) return
        ticking?.cancel()
        val id = recorder.recordingId
        _state.update { it.copy(transcribing = true, level = 0f) }
        scope.launch {
            val audio = recorder.stop()
            if (audio == null) {
                // The recorder gave nothing back: a failed recording, not silence (chat says the same).
                tracker.track(VisitEvents.DictationFailed(fieldKind(field), RECORDING_FAILED))
                return@launch _state.update { DictationState(banner = CodeBanner(Res.string.errorRecordingVoice)) }
            }
            val result = repo.transcribe(audio, language(), id)
            val transcript = result.getOrNull()
            tracker.track(
                if (transcript == null) {
                    VisitEvents.DictationFailed(
                        fieldKind(field),
                        result.exceptionOrNull()?.toFailureReason() ?: "client",
                    )
                } else {
                    VisitEvents.DictationTranscribed(fieldKind(field), !transcript.empty)
                },
            )
            if (transcript != null && !transcript.empty) {
                append(field, transcript.text)
                last = transcript.text to audio
                lastId = id
            }
            val reportable =
                transcript != null && !transcript.empty &&
                    config.transcriptionFeedbackEnabled().getOrDefault(
                        false,
                    )
            _state.update {
                DictationState(
                    banner =
                        when {
                            transcript == null -> failure(result.exceptionOrNull())
                            transcript.empty -> CodeBanner(Res.string.noSpeechDetected, error = false)
                            else -> CodeBanner(Res.string.transcriptionAdded, error = false)
                        },
                    reportable = field.takeIf { reportable },
                )
            }
        }
    }

    /**
     * ✓ for a recording that isn't text for a field (the AI scribe, SCRIBE-01): [process] gets the audio and its id
     * and says how it went; the pill shows a spinner meanwhile.
     */
    fun finishWith(process: suspend (audio: ByteArray, recordingId: String?) -> CodeBanner?) {
        val s = _state.value
        if (s.field == null || s.transcribing) return
        ticking?.cancel()
        val id = recorder.recordingId
        _state.update { it.copy(transcribing = true, level = 0f) }
        scope.launch {
            val audio = recorder.stop()
            if (audio == null) {
                // The recorder gave nothing back: a failed recording, not silence (chat says the same).
                tracker.track(VisitEvents.ScribeFailed(RECORDING_FAILED, "record"))
                return@launch _state.update { DictationState(banner = CodeBanner(Res.string.errorRecordingVoice)) }
            }
            tracker.track(VisitEvents.ScribeRecordingFinished(durationBucket(recorder.lastDurationSeconds.toLong())))
            val banner = process(audio, id)
            _state.update { DictationState(banner = banner) }
        }
    }

    /**
     * "Report": the last transcript and its recording go to the backend's QA collection. A failure says so and keeps
     * Report for another try; 404 (no longer collected) only hides it.
     */
    fun report() {
        val (text, audio) = last ?: return
        val field = _state.value.reportable ?: return
        _state.update { it.copy(reportable = null) }
        scope.launch {
            val result = repo.report(text, language(), audio, lastId)
            val e = result.exceptionOrNull()
            when {
                e == null -> tracker.track(VisitEvents.DictationReported)
                e !is ApiException.NotFound -> tracker.track(VisitEvents.DictationReportFailed(e.toFailureReason()))
            }
            _state.update {
                when {
                    e == null -> it.copy(banner = CodeBanner(Res.string.transcriptionReportThanks, error = false))
                    e is ApiException.NotFound -> it
                    else ->
                        it.copy(
                            reportable = field,
                            banner =
                                CodeBanner(
                                    if (e is ApiException.RateLimit) {
                                        Res.string.aiDailyLimitReached
                                    } else {
                                        Res.string.transcriptionReportFailed
                                    },
                                    requestId = e.requestId,
                                ),
                        )
                }
            }
        }
    }

    fun dismiss() = _state.update { it.copy(banner = null, reportable = null) }

    private fun failure(e: Throwable?): CodeBanner =
        CodeBanner(
            when (e) {
                is ApiException.Forbidden -> Res.string.speechToTextRequiresPro
                is ApiException.RateLimit -> Res.string.aiDailyLimitReached
                is ApiException.NetworkError -> if (e.timeout) Res.string.transcriptionTimedOut else FAILED
                else -> FAILED
            },
            requestId = e?.requestId,
        )

    private companion object {
        val TICK = 60.milliseconds
        val FAILED = Res.string.transcriptionFailed
        const val RECORDING_FAILED = "recording_failed"
    }
}

/** The field the visit's AI scribe records under, in a Dictation of its own (its events are the scribe's). */
const val SCRIBE_FIELD = "scribe"

/**
 * What kind of field [field] is, for tracking: the screens' field keys are fixed words except a form's, which is its
 * label (never sent).
 */
private fun fieldKind(field: String): String =
    when {
        field == SCRIBE_FIELD -> "scribe"
        field.startsWith("soap-") -> "soap"
        field == "free" -> "free_notes"
        field == "dental-notes" -> "dental_notes"
        else -> "form"
    }

/** A transcript added to [text] (02 §7.2): a space between, unless the text is empty or already ends in one. */
fun appendTranscript(
    text: String,
    transcript: String,
): String =
    when {
        text.isEmpty() -> transcript
        text.last().isWhitespace() -> text + transcript
        else -> "$text $transcript"
    }
