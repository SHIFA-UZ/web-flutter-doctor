package shifa.doctor.ui.intelligence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.ai.aiCode
import shifa.doctor.data.ai.aiRepository
import shifa.doctor.data.ai.toAiMessage
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.appointments.AppointmentsRepository
import shifa.doctor.data.appointments.BookRequest
import shifa.doctor.data.appointments.appointmentsRepository
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.intelligence.CardSlot
import shifa.doctor.data.intelligence.ChatHistory
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSessionStore
import shifa.doctor.data.intelligence.ChatTitles
import shifa.doctor.data.intelligence.IntelCard
import shifa.doctor.data.intelligence.chatSessionStore
import shifa.doctor.data.model.AiRequest
import shifa.doctor.data.model.AiStreamEvent
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.model.CommitVisitFactsRequest
import shifa.doctor.data.model.VisitKind
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.speech.TranscriptionRepository
import shifa.doctor.data.speech.transcriptionRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.CalendarEvents
import shifa.doctor.data.tracking.events.ChatEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.intelBookingFailed
import shifa.doctor.generated.resources.intelBookingFailedNetwork
import shifa.doctor.generated.resources.intelDocumentFailed
import shifa.doctor.generated.resources.intelEmptyReply
import shifa.doctor.generated.resources.intelMissingAppointmentOrSlot
import shifa.doctor.generated.resources.intelMissingPatientOrSlot
import shifa.doctor.generated.resources.intelRescheduleFailed
import shifa.doctor.generated.resources.intelRescheduleFailedNetwork
import shifa.doctor.generated.resources.intelSlotConflict
import shifa.doctor.generated.resources.somethingWentWrong
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.PracticeClock
import shifa.doctor.utils.SpeechPlayer
import shifa.doctor.utils.createSpeechPlayer
import shifa.doctor.utils.currentInstant

data class ThreadUiState(
    // The question being answered (shown as the doctor's bubble until the turn is saved) and when it was asked.
    val asking: String? = null,
    val askedAt: Instant? = null,
    // The reply so far, and the card its tool event brought (it often comes before the text).
    val streaming: String = "",
    val toolName: String? = null,
    val card: JsonObject? = null,
    val error: CodeBanner? = null,
    val draft: String = "",
    // The booking cards' passing state, by message index in the open chat (INTEL-06); outcomes are saved in the card.
    val cards: Map<Int, CardUi> = emptyMap(),
    // A card's booking is running in this chat: no new question until it ends (its history would be stale).
    val booking: Boolean = false,
    // INTEL-08: the visit the answer just opened (`open_appointment`), for the screen to open once; never from history.
    val autoOpen: IntelCard.OpenVisit? = null,
) {
    val busy: Boolean get() = asking != null && error == null
    val canSend: Boolean get() = draft.isNotBlank() && !busy && !booking
}

/** A booking card while the doctor works it: the call running, its failure, the other times shown. */
data class CardUi(
    val busy: Boolean = false,
    val error: CodeBanner? = null,
    val showOther: Boolean = false,
)

/**
 * One conversation's turns (INTEL-04, 02 §3.3, flow 03/03): a typed question goes to the morning stream with the
 * chat's history (tags and all, [ChatHistory]), the focused patient and the chat id as `conversationId`; tokens and
 * the card show as they come; the finished turn is saved to the store. One turn at a time: a new send or leaving
 * cancels the one running (Flutter left the stream open). The reply language follows what the doctor wrote, else the
 * chat's, starting from the app's.
 */
class ThreadViewModel(
    private val store: ChatSessionStore = chatSessionStore,
    private val ai: AiRepository = aiRepository,
    private val appLanguage: () -> String = { languageStorage.language.value.aiCode },
    private val now: () -> Instant = ::currentInstant,
    // A booking, move or cancellation made in Intelligence: today and the touched days (given as practice dates or as
    // instants) are read again, and every screen showing visits hears of it.
    private val scheduleChanged: suspend (dates: List<LocalDate>, starts: List<Instant>) -> Unit = { dates, starts ->
        val clock = PracticeClock(profileRepository.me.value?.profile?.timeZone)
        calendarRepository.changed(clock, *(dates + starts.map { clock.local(it).date }).distinct().toTypedArray())
    },
    private val appointments: AppointmentsRepository = appointmentsRepository,
    transcription: TranscriptionRepository = transcriptionRepository,
    speechPlayer: () -> SpeechPlayer = ::createSpeechPlayer,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    // The player is made on first use (a voice reply), not with the screen.
    private val speech by lazy(speechPlayer)

    /** Shifa's voice in this conversation (AI-08 / INTEL-09): the mic, dictation, the spoken replies, hands-free. */
    val voice =
        VoiceSession(
            viewModelScope,
            transcription,
            ai,
            player = { speech },
            ask = ::askVoice,
            cancelTurn = ::cancelTurn,
            busy = { _state.value.busy && !voiceTurn },
            clock = { now().toEpochMilliseconds() },
            tracker = tracker,
        )

    // The turn running now came from the voice: its reply is spoken, in [replyLanguage].
    private var voiceTurn = false
    private var replyLanguage = "EN"

    // How the last question was asked, for Retry: spoken (and in which language) or typed.
    private var lastSpoken: String? = null
    private var lastWasVoice = false

    // How the question was asked (`typed` / `starter` / `voice` / `card_answer`), for the turn's tracking event.
    private var lastInput = "typed"
    private var turnTrack = TurnTrack("typed", false, false)

    private var turn: Job? = null

    // The reply language of each conversation (so a Russian chat doesn't carry over into a new one).
    private val languages = mutableMapOf<String, String>()
    private var lastQuestion: String? = null

    // The card each chat is booking from, kept when the doctor leaves and comes back: one booking per chat at a time.
    private val bookings = mutableMapOf<String, Int>()

    // Visits an answer opened by itself in this app run.
    private val openedVisits = mutableSetOf<String>()

    // Draft-fact cards being documented ("chat id" to index), kept the same way: one commit per card.
    private val committing = mutableSetOf<Pair<String, Int>>()

    fun draft(text: String) = _state.update { it.copy(draft = text) }

    /** The composer's Send (no [text]: the draft) or a starter prompt ([text]): the text as typed. */
    fun send(text: String? = null) = submit(text ?: _state.value.draft, if (text == null) "typed" else "starter")

    private fun submit(
        text: String,
        input: String,
    ) {
        if (_state.value.busy || store.state.value.activeId in bookings) return
        val question = ChatTitles.stripWakePhrase(text).ifBlank { return }
        _state.update { it.copy(draft = "") }
        voice.typed()
        ask(question, input = input)
    }

    /** A transcribed take as the question, in the language it was spoken in; false when a question can't go now. */
    private fun askVoice(
        text: String,
        language: String?,
    ): Boolean {
        if (_state.value.busy || store.state.value.activeId in bookings) return false
        val question = ChatTitles.stripWakePhrase(text).ifBlank { return false }
        ask(question, language, voice = true, input = "voice")
        return true
    }

    /** The error row's Retry: the same question again (it was never saved, so it isn't asked twice). */
    fun retry() {
        val q = lastQuestion ?: return
        _state.update { it.copy(error = null) }
        // A spoken question is retried as one: no re-listen meanwhile, and its answer is spoken.
        if (lastWasVoice) voice.retrying()
        ask(q, lastSpoken, voice = lastWasVoice, input = lastInput, retry = true)
    }

    /**
     * A booking card's Confirm or slot button (INTEL-06, flows 03/14–15; cards without the backend's workflow only):
     * `POST /schedule/book` or `PUT /appointments/{id}/change`. Success is saved into the card (so a reopened chat
     * shows it booked, not bookable) and the calendar hears of it.
     */
    fun book(
        index: Int,
        slot: CardSlot,
    ) {
        val chat = store.state.value.active ?: return
        val message = chat.messages.getOrNull(index) ?: return
        val card = message.cardPayload?.let { IntelCard.of(message.toolName, it) } as? IntelCard.Proposal ?: return
        // Not while Shifa answers (the reply may book too), nor while this chat books from a card.
        if (card.done || card.workflow || _state.value.busy || chat.id in bookings) return
        val start = slot.startAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val target = if (card.move) card.appointmentId else card.patientId
        if (start == null || target == null) {
            val missing =
                if (card.move) Res.string.intelMissingAppointmentOrSlot else Res.string.intelMissingPatientOrSlot
            return cardUi(index) { it.copy(error = CodeBanner(missing)) }
        }
        bookings[chat.id] = index
        cardUi(index) { it.copy(busy = true, error = null) }
        _state.update { it.copy(booking = true) }
        viewModelScope.launch {
            // Video visits and known locations are labelled by the backend: no English "Video Consultation" sent.
            val location = slot.locationId.takeUnless { card.isVideo }
            val result =
                if (card.move) {
                    appointments.change(target, start, slot.minutes, location)
                } else {
                    appointments.book(
                        BookRequest(
                            start.toString(),
                            slot.minutes,
                            target,
                            card.isVideo,
                            card.reason,
                            slot.locationLabel.takeUnless { card.isVideo },
                            location,
                        ),
                    )
                }
            bookings.remove(chat.id)
            if (store.state.value.activeId == chat.id) _state.update { it.copy(booking = false) }
            result.fold(
                {
                    tracker.track(
                        if (card.move) {
                            CalendarEvents.AppointmentRescheduled(
                                card.isVideo,
                                isOnBehalf = false,
                                entry = "intelligence",
                            )
                        } else {
                            CalendarEvents.AppointmentBooked("intelligence", card.isVideo, false, slot.minutes)
                        },
                    )
                    val key = if (card.move) IntelCard.KEY_MOVED else IntelCard.KEY_BOOKED
                    store.updateCard(chat.id, index) {
                        JsonObject(
                            it + (key to JsonPrimitive(true)) + (IntelCard.KEY_BOOKED_TIME to JsonPrimitive(slot.time)),
                        )
                    }
                    cardUi(index, chat.id) { CardUi() }
                    scheduleChanged(listOfNotNull(card.currentDate), listOf(start))
                },
                { e ->
                    tracker.track(
                        if (card.move) {
                            CalendarEvents.AppointmentRescheduleFailed(
                                e.toFailureReason(),
                                isOnBehalf = false,
                                entry = "intelligence",
                            )
                        } else {
                            // As the booking form: 400 / 409 is the slot taken meanwhile.
                            val taken = e is ApiException && e.status in setOf(400, HTTP_CONFLICT)
                            val reason = if (taken) "slot_taken" else e.toFailureReason()
                            CalendarEvents.BookingFailed(reason, "intelligence", card.isVideo, isOnBehalf = false)
                        },
                    )
                    cardUi(index, chat.id) { it.copy(busy = false, error = bookingError(e, card.move)) }
                },
            )
        }
    }

    /**
     * The draft-facts card's "Document everything" (INTEL-07, flow 03/17): every listed fact committed to the patient's
     * timeline (`POST /ai/visit-facts/commit`); the count is saved into the card so a reopened chat doesn't offer it
     * again.
     */
    fun documentFacts(index: Int) {
        val chat = store.state.value.active ?: return
        val message = chat.messages.getOrNull(index) ?: return
        val card = message.cardPayload?.let { IntelCard.of(message.toolName, it) } as? IntelCard.DraftFacts ?: return
        val appointment = card.appointmentId ?: return
        val ids = card.facts.mapNotNull { it.id }
        val key = chat.id to index
        if (card.documented != null || ids.isEmpty() || key in committing) return
        committing += key
        cardUi(index) { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val result = ai.commit(CommitVisitFactsRequest(appointment, acceptFactIds = ids))
            committing -= key
            result.fold(
                { done ->
                    tracker.track(ChatEvents.IntelligenceFactsDocumented(countBucket(done.committedFacts.size)))
                    store.updateCard(chat.id, index) {
                        JsonObject(it + (IntelCard.KEY_DOCUMENTED to JsonPrimitive(done.committedFacts.size)))
                    }
                    cardUi(index, chat.id) { CardUi() }
                },
                { e ->
                    tracker.track(ChatEvents.IntelligenceFactsDocumentFailed(e.toFailureReason()))
                    val error =
                        if (e is ApiException && e !is ApiException.NetworkError) {
                            CodeBanner(e.toUserMessage(Res.string.intelDocumentFailed), requestId = e.requestId)
                        } else {
                            CodeBanner(Res.string.intelDocumentFailed)
                        }
                    cardUi(index, chat.id) { it.copy(busy = false, error = error) }
                },
            )
        }
    }

    /**
     * The draft-facts card's "Review on the visit": the visit opens as what it is (a video visit with its call), read
     * from the appointment since the card doesn't say; in the clinic if that can't be read (the screen reads it again).
     */
    fun openVisit(
        appointmentId: Long,
        open: (VisitKind) -> Unit,
    ) {
        viewModelScope.launch {
            open(appointments.detail(appointmentId).getOrNull()?.kind ?: VisitKind.InPerson)
        }
    }

    /** The screen opened [ThreadUiState.autoOpen]. */
    fun autoOpened() = _state.update { it.copy(autoOpen = null) }

    /** "Other times" / "Hide other times" under a proposal's Confirm. */
    fun toggleOtherTimes(index: Int) = cardUi(index) { it.copy(showOther = !it.showOther) }

    /** A proposal's Cancel: the card goes (nothing is cancelled; saying "no" to Shifa does the same), and stays gone. */
    fun dismiss(index: Int) {
        val chat = store.state.value.active ?: return
        tracker.track(ChatEvents.IntelligenceProposalDismissed)
        store.updateCard(chat.id, index) { JsonObject(it + (IntelCard.KEY_DISMISSED to JsonPrimitive(true))) }
    }

    /**
     * The cancellation card's "Yes, cancel N" / "Keep them" (flow 03/16 target): the doctor's answer sent as if typed,
     * in the conversation's language, with words the backend takes as a confirmation (or not). The app never cancels.
     */
    fun answerCancel(yes: Boolean) {
        val chat = store.state.value.active ?: return
        val language = languages[chat.id] ?: appLanguage()
        tracker.track(ChatEvents.IntelligenceCancelAnswered(yes))
        submit((if (yes) CONFIRM else DECLINE)[language] ?: if (yes) "yes" else "no", "card_answer")
    }

    private fun cardUi(
        index: Int,
        chatId: String? = null,
        change: (CardUi) -> CardUi,
    ) {
        // A call that ends after the doctor opened another chat leaves that chat's cards alone.
        if (chatId != null && store.state.value.activeId != chatId) return
        _state.update { it.copy(cards = it.cards + (index to change(it.cards[index] ?: CardUi()))) }
    }

    private fun bookingError(
        e: Throwable,
        move: Boolean,
    ): CodeBanner =
        when {
            e is ApiException.NetworkError || e !is ApiException ->
                CodeBanner(if (move) Res.string.intelRescheduleFailedNetwork else Res.string.intelBookingFailedNetwork)
            e.status == HTTP_CONFLICT -> CodeBanner(Res.string.intelSlotConflict, requestId = e.requestId)
            else ->
                CodeBanner(
                    if (move) Res.string.intelRescheduleFailed else Res.string.intelBookingFailed,
                    requestId = e.requestId,
                    args = listOf(e.status.toString()),
                )
        }

    /** Leaving the chat (back, another chat, deleting it): the running turn stops and its question goes. */
    fun stop() {
        voice.leave()
        cancelTurn()
    }

    // The running turn stops and its question goes (the voice's mic tap while "Reviewing…" too).
    private fun cancelTurn() {
        if (_state.value.busy) tracker.track(ChatEvents.IntelligenceReplyStopped(turnTrack.input))
        turn?.cancel()
        turn = null
        voiceTurn = false
        // A card's booking or commit runs on (it can't be taken back halfway); back in its chat, the card shows it.
        val active = store.state.value.activeId
        val booking = bookings[active]
        val running = listOfNotNull(booking) + committing.filter { it.first == active }.map { it.second }
        _state.update {
            ThreadUiState(
                draft = it.draft,
                cards = running.associateWith { CardUi(busy = true) },
                booking = booking != null,
            )
        }
    }

    private fun ask(
        question: String,
        spokenIn: String? = null,
        voice: Boolean = false,
        input: String,
        retry: Boolean = false,
    ) {
        turn?.cancel()
        store.ensureLoaded()
        val chat = store.state.value.active ?: return
        val stored = chat.messages
        val reply = spokenIn ?: ChatTitles.detectReplyLanguage(question) ?: languages[chat.id] ?: appLanguage()
        languages[chat.id] = reply
        voiceTurn = voice
        replyLanguage = reply
        lastWasVoice = voice
        lastSpoken = spokenIn
        lastQuestion = question
        lastInput = input
        turnTrack = TurnTrack(input, retry, ChatHistory.focusPatientId(stored) != null)
        val askedAt = now()
        _state.update {
            it.copy(asking = question, askedAt = askedAt, streaming = "", toolName = null, card = null, error = null)
        }
        val request =
            AiRequest(
                ChatHistory.messages(stored, question),
                reply,
                patientId = ChatHistory.focusPatientId(stored),
                conversationId = chat.id,
            )
        turn =
            viewModelScope.launch {
                val text = StringBuilder()
                val result =
                    ai.stream(request, morning = true) { e ->
                        when (e) {
                            is AiStreamEvent.Token -> {
                                text.append(e.text)
                                _state.update { it.copy(streaming = text.toString()) }
                            }
                            is AiStreamEvent.Tool -> {
                                if (e.payload.changesSchedule()) {
                                    val (dates, starts) = e.payload.touchedDays()
                                    viewModelScope.launch { scheduleChanged(dates, starts) }
                                }
                                _state.update { it.copy(toolName = e.name, card = e.payload) }
                            }
                            is AiStreamEvent.Draft -> Unit
                        }
                    }
                result.fold({ finish(chat.id, question, askedAt, text.toString().trim()) }, ::fail)
            }
    }

    private fun finish(
        chatId: String,
        question: String,
        askedAt: Instant,
        reply: String,
    ) {
        // The doctor moved to another chat meanwhile (the turn is normally stopped first): never reopen this one.
        if (store.state.value.activeId != chatId) return
        val s = _state.value
        val card = s.card
        val track = turnTrack
        if (reply.isEmpty() && card == null) {
            tracker.track(ChatEvents.IntelligenceReplyFailed("empty", track.input, track.isRetry))
            _state.update { it.copy(error = CodeBanner(Res.string.intelEmptyReply)) }
            if (voiceTurn.also { voiceTurn = false }) voice.turnFailed()
            return
        }
        tracker.track(
            ChatEvents.IntelligenceReplyReceived(
                track.input,
                card?.let { IntelCard.typeOf(s.toolName, it) },
                card?.changesSchedule() == true,
                (now() - askedAt).inWholeSeconds.toInt(),
                track.isRetry,
                track.hasPatientContext,
            ),
        )
        // A booking Shifa made says so; a reply without words falls back to the card's spoken line (02 §3.3 step 8).
        val said =
            when {
                card?.flag(IntelCard.KEY_BOOKED) == true || card?.flag(IntelCard.KEY_MOVED) == true ->
                    card.text("statusMessage") ?: reply.ifEmpty { card.text("spokenHint").orEmpty() }
                reply.isEmpty() -> card?.text("spokenHint").orEmpty()
                else -> reply
            }
        // Onto the chat as it is now: a card booked or dismissed during the turn keeps that.
        val current = store.state.value.active?.messages.orEmpty()
        store.replaceMessages(
            chatId,
            current +
                ChatMessage(ChatMessage.ROLE_USER, question, askedAt) +
                ChatMessage(ChatMessage.ROLE_ASSISTANT, said, now(), s.toolName, card),
        )
        // Once per visit (id, kind, status, start) in this app run (flow 03/19 §4): asking again doesn't open it again.
        val open =
            card?.let { IntelCard.of(s.toolName, it) as? IntelCard.OpenVisit }
                ?.takeIf { it.openable && openedVisits.add(it.openKey) }
        _state.update { ThreadUiState(draft = it.draft, cards = it.cards, autoOpen = open) }
        // A spoken question gets a spoken answer (02 §3.3 step 8); a visit opening stops the voice (flow 03/19 §7).
        if (voiceTurn.also { voiceTurn = false }) {
            if (open != null) voice.leave() else voice.replied(said, replyLanguage)
        }
    }

    private fun fail(e: Throwable) {
        val reason = if (e is ApiException && e.code == "AI_UNAVAILABLE") "ai_unavailable" else e.toFailureReason()
        tracker.track(ChatEvents.IntelligenceReplyFailed(reason, turnTrack.input, turnTrack.isRetry))
        _state.update {
            it.copy(
                error = CodeBanner(e.toAiMessage(Res.string.somethingWentWrong), requestId = e.requestId),
            )
        }
        if (voiceTurn.also { voiceTurn = false }) voice.turnFailed()
    }
}

private const val HTTP_CONFLICT = 409

// What the running turn's tracking event says about how it was asked (never the question).
private data class TurnTrack(
    val input: String,
    val isRetry: Boolean,
    val hasPatientContext: Boolean,
)

// Words the backend's `AppointmentActionPhrases` takes as a confirmation, by reply language; the "no"s are anything else.
private val CONFIRM = mapOf("EN" to "yes", "UZ" to "ha", "RU" to "да")
private val DECLINE = mapOf("EN" to "no", "UZ" to "yo'q", "RU" to "нет")

// Shifa booked, moved or cancelled visits (02 §3.3 step 8; flow 03/16 target: refresh at once, not on leaving).
private fun JsonObject.changesSchedule(): Boolean =
    flag(IntelCard.KEY_BOOKED) || flag(IntelCard.KEY_MOVED) ||
        text("cardType") == "cancel_result" && (get("cancelled") as? JsonArray)?.isNotEmpty() == true

// The days a booking, move or cancellation touched: the new date, and the cancelled visits' starts.
private fun JsonObject.touchedDays(): Pair<List<LocalDate>, List<Instant>> =
    listOfNotNull(text("date")?.let { runCatching { LocalDate.parse(it.take(10)) }.getOrNull() }) to
        (get("cancelled") as? JsonArray).orEmpty().mapNotNull { row ->
            ((row as? JsonObject)?.get("startAt") as? JsonPrimitive)?.contentOrNull?.let {
                runCatching { Instant.parse(it) }.getOrNull()
            }
        }

private fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf {
        it.isNotBlank()
    }
