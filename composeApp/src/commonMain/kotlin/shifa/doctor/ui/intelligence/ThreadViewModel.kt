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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import shifa.doctor.data.ai.AiRepository
import shifa.doctor.data.ai.aiCode
import shifa.doctor.data.ai.aiRepository
import shifa.doctor.data.ai.toAiMessage
import shifa.doctor.data.api.requestId
import shifa.doctor.data.calendar.calendarRepository
import shifa.doctor.data.calendar.changed
import shifa.doctor.data.intelligence.ChatHistory
import shifa.doctor.data.intelligence.ChatMessage
import shifa.doctor.data.intelligence.ChatSessionStore
import shifa.doctor.data.intelligence.ChatTitles
import shifa.doctor.data.intelligence.chatSessionStore
import shifa.doctor.data.model.AiRequest
import shifa.doctor.data.model.AiStreamEvent
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.intelEmptyReply
import shifa.doctor.generated.resources.somethingWentWrong
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.PracticeClock
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
) {
    val busy: Boolean get() = asking != null && error == null
    val canSend: Boolean get() = draft.isNotBlank() && !busy
}

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
    // A booking or change made by Shifa: today's calendar reads again and every screen showing visits hears of it.
    private val scheduleChanged: suspend () -> Unit = {
        calendarRepository.changed(PracticeClock(profileRepository.me.value?.profile?.timeZone))
    },
) : ViewModel() {
    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()

    private var turn: Job? = null

    // The reply language of each conversation (so a Russian chat doesn't carry over into a new one).
    private val languages = mutableMapOf<String, String>()
    private var lastQuestion: String? = null

    fun draft(text: String) = _state.update { it.copy(draft = text) }

    /** The composer's Send (or a starter prompt): the text as typed. */
    fun send(text: String = _state.value.draft) {
        if (_state.value.busy) return
        val question = ChatTitles.stripWakePhrase(text).ifBlank { return }
        _state.update { it.copy(draft = "") }
        ask(question)
    }

    /** The error row's Retry: the same question again (it was never saved, so it isn't asked twice). */
    fun retry() {
        val q = lastQuestion ?: return
        _state.update { it.copy(error = null) }
        ask(q)
    }

    /** Leaving the chat (back, another chat, deleting it): the running turn stops and its question goes. */
    fun stop() {
        turn?.cancel()
        turn = null
        _state.update { ThreadUiState(draft = it.draft) }
    }

    private fun ask(question: String) {
        turn?.cancel()
        store.ensureLoaded()
        val chat = store.state.value.active ?: return
        val stored = chat.messages
        val reply = ChatTitles.detectReplyLanguage(question) ?: languages[chat.id] ?: appLanguage()
        languages[chat.id] = reply
        lastQuestion = question
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
                                if (e.payload.flag("booked")) viewModelScope.launch { scheduleChanged() }
                                _state.update { it.copy(toolName = e.name, card = e.payload) }
                            }
                            is AiStreamEvent.Draft -> Unit
                        }
                    }
                result.fold({ finish(chat.id, stored, question, askedAt, text.toString().trim()) }, ::fail)
            }
    }

    private fun finish(
        chatId: String,
        stored: List<ChatMessage>,
        question: String,
        askedAt: Instant,
        reply: String,
    ) {
        // The doctor moved to another chat meanwhile (the turn is normally stopped first): never reopen this one.
        if (store.state.value.activeId != chatId) return
        val s = _state.value
        val card = s.card
        if (reply.isEmpty() && card == null) {
            return _state.update {
                it.copy(
                    error = CodeBanner(Res.string.intelEmptyReply),
                )
            }
        }
        // A booking Shifa made says so; a reply without words falls back to the card's spoken line (02 §3.3 step 8).
        val said =
            when {
                card?.flag("booked") == true -> card.text("statusMessage") ?: reply
                reply.isEmpty() -> card?.text("spokenHint").orEmpty()
                else -> reply
            }
        store.replaceMessages(
            chatId,
            stored +
                ChatMessage(ChatMessage.ROLE_USER, question, askedAt) +
                ChatMessage(ChatMessage.ROLE_ASSISTANT, said, now(), s.toolName, card),
        )
        _state.update { ThreadUiState(draft = it.draft) }
    }

    private fun fail(e: Throwable) =
        _state.update {
            it.copy(
                error = CodeBanner(e.toAiMessage(Res.string.somethingWentWrong), requestId = e.requestId),
            )
        }
}

private fun JsonObject.flag(key: String): Boolean = (get(key) as? JsonPrimitive)?.booleanOrNull == true

private fun JsonObject.text(key: String): String? =
    (get(key) as? JsonPrimitive)?.contentOrNull?.takeIf {
        it.isNotBlank()
    }
