package shifa.doctor.ui.intelligence

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.ai.aiCode
import shifa.doctor.data.api.requestId
import shifa.doctor.data.intelligence.ChatTitles
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.speech.TranscriptionRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aiDailyLimitReached
import shifa.doctor.generated.resources.intelCouldNotStartMicMobile
import shifa.doctor.generated.resources.intelMicPermissionRetry
import shifa.doctor.generated.resources.intelNoSpeech
import shifa.doctor.generated.resources.intelNoSpeechRecording
import shifa.doctor.generated.resources.intelNoSpeechShort
import shifa.doctor.generated.resources.intelRecordingStoppedBackground
import shifa.doctor.generated.resources.intelSignOffReply
import shifa.doctor.generated.resources.intelSignOffThanks
import shifa.doctor.generated.resources.intelSpeechProOrDenied
import shifa.doctor.generated.resources.intelSpeechRequiresPro
import shifa.doctor.generated.resources.intelStopped
import shifa.doctor.generated.resources.intelTranscriptionFailed
import shifa.doctor.generated.resources.intelTranscriptionTimeout
import shifa.doctor.generated.resources.intelVoiceSkipped
import shifa.doctor.generated.resources.intelVoiceSlow
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.widgets.VoicePhase
import shifa.doctor.utils.SpeechPlayer
import shifa.doctor.utils.VoiceRecorder

/**
 * When a hands-free take ends (02 §3.5 B, flow 03/04 §4; Flutter's `_onMicLevel` and its timers, ported as they were):
 * a level of 0.12 or more is speech; silence is relative to a decaying peak (30 % of it, between 0.09 and 0.40); after
 * at least 700 ms since speech began, 1.9 s of silence sends; nothing heard in 20 s gives up; 45 s sends whatever was
 * captured. Times are milliseconds on one clock.
 */
class Vad(
    private val startedAt: Long,
) {
    enum class Outcome { Keep, Send, NoSpeech }

    var heard: Boolean = false
        private set

    private var peak = SPEECH
    private var speechStart: Long? = null
    private var silenceStart: Long? = null

    fun onLevel(
        level: Float,
        at: Long,
    ): Outcome {
        if (at - startedAt >= MAX_LISTEN_MS) return Outcome.Send
        val l = if (level.isFinite()) level else 0f
        peak = if (l > peak) l else peak * PEAK_DECAY + l * (1 - PEAK_DECAY)
        val gate = (peak * GATE_SHARE).coerceIn(SILENCE, GATE_MAX)
        if (l >= SPEECH) {
            heard = true
            if (speechStart == null) speechStart = at
            silenceStart = null
            return Outcome.Keep
        }
        if (!heard) return if (at - startedAt >= NO_SPEECH_MS) Outcome.NoSpeech else Outcome.Keep
        if (l <= gate) {
            val silent = silenceStart ?: at.also { silenceStart = it }
            val speaking = at - (speechStart ?: at)
            if (speaking >= MIN_SPEECH_MS && at - silent >= SILENCE_TO_SEND_MS) return Outcome.Send
        } else {
            silenceStart = null
        }
        return Outcome.Keep
    }

    companion object {
        const val SPEECH = 0.12f
        const val SILENCE = 0.09f
        const val GATE_SHARE = 0.30f
        const val GATE_MAX = 0.40f
        const val PEAK_DECAY = 0.92f
        const val MIN_SPEECH_MS = 700L
        const val SILENCE_TO_SEND_MS = 1_900L
        const val NO_SPEECH_MS = 20_000L
        const val MAX_LISTEN_MS = 45_000L
    }
}

data class VoiceUiState(
    val phase: VoicePhase = VoicePhase.Idle,
    val level: Float = 0f,
    // Speech heard in this take ("Listening… pause to send").
    val heard: Boolean = false,
    // The mic opens again after each spoken reply.
    val handsFree: Boolean = false,
    // The take is a dictation (no silence rules, sent with the check) rather than a hands-free one.
    val dictating: Boolean = false,
    val transcribing: Boolean = false,
    val seconds: Int = 0,
    val banner: CodeBanner? = null,
    // The last take's upload failed and it is kept: "Retry" sends it again (flow 03/05 target).
    val canResend: Boolean = false,
) {
    val recording: Boolean get() = phase == VoicePhase.Listening
}

/**
 * Shifa Intelligence's voice (AI-08, 02 §3.3–3.5, flows 03/04–06): the mic records a take, its end found by [Vad]
 * (hands-free) or the doctor's check (dictation, up to 10 minutes); the server transcribes it (language detected);
 * the words go to the thread as a question ([ask]); the reply is spoken ([replied]: `POST /ai/speak` and the
 * [player]); then, hands-free, the mic opens again after 450 ms. No wake word, no live caption (mobile decision). The
 * recorder belongs to the screen ([attach]); every way out ([leave]) drops the take and the voice.
 */
class VoiceSession(
    private val scope: CoroutineScope,
    private val transcription: TranscriptionRepository,
    private val ai: AiRepository,
    private val player: () -> SpeechPlayer,
    // The words as a question, in the reply language they were spoken in (null: the conversation's); false when the
    // thread can't take one now (a card's booking is running).
    private val ask: (text: String, language: String?) -> Boolean,
    // The reply being written stops (the mic tapped while "Reviewing…").
    private val cancelTurn: () -> Unit,
    // A typed question's reply is being written ("Reviewing…" without the voice).
    private val busy: () -> Boolean,
    private val clock: () -> Long,
) {
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var recorder: VoiceRecorder? = null
    private var take: Job? = null
    private var work: Job? = null
    private var takeStarted = 0L

    // The take whose upload failed: its audio, id, and whether it was a dictation.
    private var kept: Triple<ByteArray, String?, Boolean>? = null

    /** The screen's recorder; null when the screen leaves (the take is dropped). */
    fun attach(recorder: VoiceRecorder?) {
        if (recorder == null) leave()
        this.recorder = recorder
    }

    /** Below PRO the mic and the record button only say so (flow 03/04 §9). */
    fun locked() = banner(CodeBanner(Res.string.intelSpeechRequiresPro))

    /** The microphone was refused: say so, and hands-free stops. */
    fun denied() {
        _state.update { it.copy(handsFree = false) }
        banner(CodeBanner(Res.string.intelMicPermissionRetry))
    }

    /**
     * The mic button (flow 03/04 §7): idle → listen, hands-free on; listening → send what was captured (a tap within
     * 400 ms of starting is a double tap and is ignored); speaking → barge in; reviewing → that reply stops and the mic
     * opens.
     */
    fun mic() {
        val s = _state.value
        when (s.phase) {
            VoicePhase.Listening -> if (clock() - takeStarted >= DOUBLE_TAP_MS) finish(keepHandsFree = !s.dictating)
            VoicePhase.Speaking -> listen()
            VoicePhase.Thinking ->
                if (!s.transcribing) {
                    cancelTurn()
                    listen()
                }
            VoicePhase.Idle -> {
                // A typed question's reply is still coming: it stops, as on "Reviewing…" (board `DaIntel`).
                if (busy()) cancelTurn()
                listen()
            }
        }
    }

    /**
     * The record (dictation) button (flow 03/05 §7): idle or speaking → record until the check; recording → send, with
     * hands-free off; reviewing → that reply stops ("Stopped.").
     */
    fun record() {
        val s = _state.value
        when (s.phase) {
            VoicePhase.Listening -> finish(keepHandsFree = false)
            VoicePhase.Speaking -> dictate()
            VoicePhase.Idle ->
                if (busy()) {
                    cancelTurn()
                    banner(CodeBanner(Res.string.intelStopped, error = false))
                } else {
                    dictate()
                }
            VoicePhase.Thinking ->
                if (!s.transcribing) {
                    cancelTurn()
                    work?.cancel()
                    _state.update { VoiceUiState(banner = CodeBanner(Res.string.intelStopped, error = false)) }
                }
        }
    }

    /** The orb panel's Stop: sends what was captured (it never discards; flow 03/04 §3). */
    fun stopTake() {
        if (_state.value.recording) finish(keepHandsFree = !_state.value.dictating)
    }

    /** The dictation pill's ✕ (flow 03/05 target): the take is dropped, nothing is sent. */
    fun discard() {
        if (!_state.value.recording) return
        take?.cancel()
        recorder?.stop()
        _state.update { VoiceUiState() }
    }

    /** "Turn off" under the composer, or a typed message: the mic no longer opens by itself. */
    fun handsFreeOff() {
        if (_state.value.phase == VoicePhase.Idle) work?.cancel()
        _state.update { it.copy(handsFree = false) }
    }

    /** A typed question: hands-free ends and a reply being spoken stops (flow 03/04 §7). */
    fun typed() {
        if (_state.value.phase == VoicePhase.Speaking) {
            work?.cancel()
            player().stop()
            _state.update { it.copy(phase = VoicePhase.Idle) }
        }
        handsFreeOff()
    }

    /**
     * The thread finished the voice turn: [spoken] (the reply, or the card's line) is said in [language] (an AI code,
     * EN / UZ / RU); afterwards, hands-free, the mic opens again. Nothing to say: straight to that.
     */
    fun replied(
        spoken: String,
        language: String,
    ) {
        work?.cancel()
        if (spoken.isBlank()) return afterTurn(RELISTEN_MS)
        _state.update { it.copy(phase = VoicePhase.Speaking, transcribing = false) }
        work =
            scope.launch {
                val lang = AppLanguage.entries.firstOrNull { it.aiCode == language } ?: AppLanguage.English
                ai.speak(spoken, lang).fold(
                    { audio ->
                        // A clip that couldn't play (empty, undecodable) says so; a stopped one doesn't get here.
                        if (!player().play(audio.bytes)) banner(CodeBanner(Res.string.intelVoiceSkipped, false))
                    },
                    { e ->
                        val timeout = e is ApiException.NetworkError && e.timeout
                        banner(
                            CodeBanner(if (timeout) Res.string.intelVoiceSlow else Res.string.intelVoiceSkipped, false),
                        )
                    },
                )
                afterTurn(RELISTEN_MS)
            }
    }

    /** The voice turn's reply failed (an error row with Retry is in the thread): hands-free listens again. */
    fun turnFailed() = afterTurn(RELISTEN_MS)

    /** The thread retries a spoken question: no re-listen meanwhile; the answer is spoken as it would have been. */
    fun retrying() {
        if (_state.value.phase == VoicePhase.Idle) work?.cancel()
        _state.update { it.copy(phase = VoicePhase.Thinking, transcribing = false, banner = null) }
    }

    /** "Retry" on a failed upload: the kept take is sent again (nothing is recorded anew). */
    fun resend() {
        val (audio, id, dictation) = kept ?: return
        if (_state.value.phase != VoicePhase.Idle) return
        _state.update {
            it.copy(
                phase = VoicePhase.Thinking,
                transcribing = true,
                dictating = dictation,
                banner = null,
                canResend = false,
            )
        }
        upload(audio, id, dictation)
    }

    /**
     * The app went to the background (flows 03/04–05 §8 target): a take is stopped and dropped, Shifa stops speaking,
     * hands-free ends; a take that was recording says so.
     */
    fun paused() {
        val wasRecording = _state.value.recording
        leave()
        if (wasRecording) banner(CodeBanner(Res.string.intelRecordingStoppedBackground, error = false))
    }

    fun dismiss() = _state.update { it.copy(banner = null) }

    /** Leaving the chat or Intelligence: the take is dropped, the voice stops, hands-free ends. */
    fun leave() {
        take?.cancel()
        work?.cancel()
        kept = null
        if (_state.value.recording) recorder?.stop()
        player().stop()
        _state.update { VoiceUiState(banner = it.banner) }
    }

    private fun listen() = start(dictation = false)

    private fun dictate() = start(dictation = true)

    private fun start(dictation: Boolean) {
        val rec = recorder ?: return
        kept = null
        work?.cancel()
        take?.cancel()
        player().stop()
        if (!rec.start()) {
            _state.update { VoiceUiState() }
            return banner(CodeBanner(Res.string.intelCouldNotStartMicMobile))
        }
        val started = clock()
        takeStarted = started
        val vad = Vad(started)
        _state.update {
            // A mic take arms hands-free; a dictation turns it off (flow 03/05 §4).
            VoiceUiState(phase = VoicePhase.Listening, handsFree = !dictation, dictating = dictation)
        }
        take =
            scope.launch {
                while (isActive) {
                    delay(LEVEL_MS)
                    val now = clock()
                    val level = rec.level()
                    _state.update {
                        it.copy(
                            level = level,
                            seconds = ((now - started) / 1000).toInt(),
                            heard = it.heard || vad.heard,
                        )
                    }
                    if (dictation) {
                        if (now - started >= DICTATION_MAX_MS) return@launch finish(keepHandsFree = false)
                        continue
                    }
                    when (vad.onLevel(level, now)) {
                        Vad.Outcome.Send -> return@launch finish(keepHandsFree = true)
                        Vad.Outcome.NoSpeech -> return@launch giveUp()
                        Vad.Outcome.Keep -> _state.update { it.copy(heard = vad.heard) }
                    }
                }
            }
    }

    // Nothing heard in 20 s: the take goes, hands-free stops (flow 03/04 §5).
    private fun giveUp() {
        recorder?.stop()
        _state.update { VoiceUiState() }
        banner(CodeBanner(Res.string.intelNoSpeech))
    }

    /** The take ends: transcribed, then asked. */
    private fun finish(keepHandsFree: Boolean) {
        val rec = recorder ?: return
        take?.cancel()
        val dictation = _state.value.dictating
        val id = rec.recordingId
        val audio = rec.stop()
        _state.update {
            it.copy(
                phase = VoicePhase.Thinking,
                transcribing = true,
                level = 0f,
                handsFree = it.handsFree && keepHandsFree,
            )
        }
        if (audio == null) return noSpeech(emptyMessage(dictation))
        upload(audio, id, dictation)
    }

    private fun emptyMessage(dictation: Boolean) =
        if (dictation) Res.string.intelNoSpeechRecording else Res.string.intelNoSpeechShort

    private fun upload(
        audio: ByteArray,
        id: String?,
        dictation: Boolean,
    ) {
        work =
            scope.launch {
                // No language hint: the server detects it, and the reply follows (02 §3.5).
                val result = transcription.transcribe(audio, null, id)
                val transcript = result.getOrNull()
                when {
                    transcript == null -> {
                        // Kept for "Retry" unless the plan or the mic refuses it (flow 03/05 §6 target).
                        val e = result.exceptionOrNull()
                        kept = Triple(audio, id, dictation).takeUnless { e is ApiException.Forbidden }
                        failed(e)
                    }
                    transcript.empty -> noSpeech(emptyMessage(dictation))
                    isSignOff(transcript.text) -> signOff(transcript.text)
                    else -> {
                        _state.update { it.copy(transcribing = false) }
                        // The words decide the reply language, else what the server heard, else the conversation's.
                        val language =
                            ChatTitles.detectReplyLanguage(transcript.text)
                                ?: transcript.language?.uppercase()?.takeIf { it in setOf("EN", "UZ", "RU") }
                        if (!ask(transcript.text, language)) afterTurn(RELISTEN_MS)
                    }
                }
            }
    }

    private fun noSpeech(message: StringResource) {
        val handsFree = _state.value.handsFree
        _state.update { VoiceUiState(handsFree = handsFree) }
        banner(CodeBanner(message))
        if (handsFree) relisten(NO_SPEECH_RELISTEN_MS)
    }

    private fun failed(e: Throwable?) {
        val message =
            when {
                e is ApiException.Forbidden -> Res.string.intelSpeechProOrDenied
                e is ApiException.RateLimit -> Res.string.aiDailyLimitReached
                e is ApiException.NetworkError && e.timeout -> Res.string.intelTranscriptionTimeout
                else -> Res.string.intelTranscriptionFailed
            }
        _state.update { VoiceUiState(canResend = kept != null) }
        banner(CodeBanner(message, requestId = e?.requestId))
    }

    // "That's it", "rahmat", "хватит"…: the loop ends here, nothing goes to Shifa (flow 03/04 §7 target; Flutter ran
    // this on the web only and asked the doctor to say "Hey Shifa").
    private fun signOff(text: String) {
        _state.update { VoiceUiState() }
        val thanks = listOf("thank", "спасибо", "rahmat").any { text.lowercase().contains(it) }
        banner(CodeBanner(if (thanks) Res.string.intelSignOffThanks else Res.string.intelSignOffReply, error = false))
    }

    private fun afterTurn(delayMs: Long) {
        val handsFree = _state.value.handsFree
        _state.update { it.copy(phase = VoicePhase.Idle, transcribing = false, level = 0f, heard = false) }
        if (handsFree) relisten(delayMs)
    }

    private fun relisten(delayMs: Long) {
        work =
            scope.launch {
                delay(delayMs)
                if (_state.value.handsFree && _state.value.phase == VoicePhase.Idle) listen()
            }
    }

    private fun banner(banner: CodeBanner) = _state.update { it.copy(banner = banner) }

    companion object {
        /**
         * Whether a take only ends the conversation (Flutter's `_isSignOffPhrase`, ported as it was): a short phrase
         * from the list, with soft lead-ins ("ok", "yeah"…) stripped, and never one still asking for clinical work.
         */
        fun isSignOff(text: String): Boolean {
            var t = text.trim().lowercase().replace(Regex("[!.?…]+$"), "").trim()
            if (t.isEmpty() || t.length > SIGN_OFF_MAX) return false
            if (CLINICAL.containsMatchIn(t)) return false
            t = t.replaceFirst(Regex("^(no|nope|nah|yeah|yep|yes|ok|okay|alright|all right)[, ]+"), "").trim()
            if (t in SIGN_OFFS) return true
            return SIGN_OFF_PATTERNS.any { it.matches(t) }
        }

        private const val SIGN_OFF_MAX = 80
        private val CLINICAL =
            Regex(
                "\\b(open|show|view|book|schedule|appoint|patient|slot|document|diagram|chart|how many|who is|when|" +
                    "reschedule|cancel|открой|покажи|запис|пациент|документ|схем|och|korsat|ko.rsat|bemor|hujjat)\\b",
            )
        private val SIGN_OFFS =
            setOf(
                "that's it", "thats it", "that is it", "that'll be all", "that will be all", "we're done",
                "we are done",
                "nothing else", "stop listening", "goodbye", "good bye", "bye shifa", "bye", "thank you", "thanks",
                "thanks shifa", "thank you shifa", "ok that's it", "okay that's it", "alright that's it",
                "alright thank you", "all right thank you", "ok thank you", "okay thank you", "that's it thank you",
                "thats it thank you", "that's it thanks", "thank you that's all", "thank you that's it",
                "thanks that's all", "thanks that's it", "всё", "все", "всё спасибо", "все спасибо", "спасибо",
                "спасибо всё", "спасибо все", "хватит", "пока", "пока шифа", "до свидания", "bo'ldi", "boldi",
                "yetadi", "rahmat", "rahmat shifa",
            )
        private val SIGN_OFF_PATTERNS =
            listOf(
                Regex("^(ok|okay|alright|all right)[, ]+(that'?s it|that is it|thanks|thank you|we'?re done|done)$"),
                Regex("^that'?s it[, ]+(thanks|thank you)$"),
                Regex("^(thanks|thank you)[, ]+(that'?s (it|all)|bye|goodbye|shifa)$"),
                Regex("^(bye|goodbye)([, ]+shifa)?$"),
            )

        const val LEVEL_MS = 60L
        const val RELISTEN_MS = 450L
        const val NO_SPEECH_RELISTEN_MS = 650L
        const val DOUBLE_TAP_MS = 400L
        const val DICTATION_MAX_MS = 10 * 60_000L
    }
}
