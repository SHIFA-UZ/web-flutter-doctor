package shifa.doctor.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Instant
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.chat.ChatRepository
import shifa.doctor.data.chat.chatRepository
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.Message
import shifa.doctor.data.model.SendMessageRequest
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.chatLoadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.currentInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** A text on its way, or one that failed (it keeps its text for Retry); shown at once, before the server answers. */
data class PendingMessage(
    val localId: Long,
    val text: String,
    val at: Instant,
    val failed: Boolean = false,
)

data class ThreadUiState(
    val conversation: Conversation? = null,
    // Oldest first; null until the first answer (a skeleton).
    val messages: List<Message>? = null,
    val pending: List<PendingMessage> = emptyList(),
    val draft: String = "",
    val error: CodeBanner? = null,
    // The newest message from the other side: the screen shows "New message" when it changes while scrolled up.
    val newestIncoming: Long? = null,
) {
    val canSend: Boolean get() = draft.isNotBlank() && messages != null
}

/**
 * One conversation (CHAT-03, 07 §1.2–1.3, flow 07/02): every message read on open and every 5 s after each answer
 * while in front (BE-14: no push append yet); marked read on open and whenever something new comes from the other
 * side (Flutter only on open). A text shows at once and is sent in order; a failure keeps it with "Tap to retry"
 * (Flutter: a SnackBar with the raw error).
 */
class ThreadViewModel(
    private val id: Long,
    private val repo: ChatRepository = chatRepository,
    private val every: Duration = 5.seconds,
    private val now: () -> Instant = { currentInstant() },
) : ViewModel() {
    private val _state = MutableStateFlow(ThreadUiState())
    val state: StateFlow<ThreadUiState> = _state.asStateFlow()
    private var reading: Job? = null
    private var polling: Job? = null
    private var lastLocalId = 0L

    // The newest message read on the server's side, so each new one from the other side is marked read once.
    private var readUpTo: Long? = null

    // Messages this screen sent that no read has returned yet.
    private val sentIds = mutableSetOf<Long>()

    /** Retry, and each poll; a read already out is left to finish. */
    fun refresh() {
        if (reading?.isActive == true) return
        reading = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        repo.thread(id).fold(
            { t ->
                val incoming = t.messages.lastOrNull { !it.isMine }?.id
                val fetched = t.messages.map { it.id }.toSet()
                // A read started before a send answered may not have it yet: the sent message stays until a read has it.
                sentIds.removeAll(fetched)
                _state.update { s ->
                    val keep = s.messages.orEmpty().filter { it.id in sentIds }
                    s.copy(
                        conversation = t.conversation ?: s.conversation,
                        messages = (t.messages + keep).sortedBy { it.sentAt },
                        error = null,
                        newestIncoming = incoming,
                    )
                }
                // Read on open and on each new message from them; a failed read is tried again at the next answer.
                val newest = t.messages.lastOrNull()?.id
                if (newest != null && (readUpTo == null || (incoming != null && incoming > (readUpTo ?: 0)))) {
                    repo.markRead(id).onSuccess { readUpTo = newest }
                }
            },
            {
                    e ->
                _state.update {
                    it.copy(
                        error = CodeBanner(e.toUserMessage(Res.string.chatLoadFailed), requestId = e.requestId),
                    )
                }
            },
        )
    }

    /** The app in front: read now and [every] after each answer; behind it: nothing. */
    fun foreground(inFront: Boolean) {
        polling?.cancel()
        if (!inFront) return
        polling =
            viewModelScope.launch {
                while (isActive) {
                    refresh()
                    reading?.join()
                    delay(every)
                }
            }
    }

    fun draft(text: String) = _state.update { it.copy(draft = text) }

    /**
     * Send: the trimmed text shows at once with its spinner and the field clears (a second tap has nothing to send);
     * on success the server's message takes its place.
     */
    fun send() {
        val text = _state.value.draft.trim()
        if (text.isEmpty() || _state.value.messages == null) return
        val pending = PendingMessage(++lastLocalId, text, now())
        _state.update { it.copy(draft = "", pending = it.pending + pending) }
        deliver(pending)
    }

    /** A failed text, tapped: sent again, once. */
    fun retry(localId: Long) {
        val p = _state.value.pending.firstOrNull { it.localId == localId && it.failed } ?: return
        _state.update {
                s ->
            s.copy(pending = s.pending.map { if (it.localId == localId) it.copy(failed = false) else it })
        }
        deliver(p.copy(failed = false))
    }

    // One send at a time, so texts arrive in the order they were written.
    private val sending = Mutex()

    private fun deliver(p: PendingMessage) {
        viewModelScope.launch {
            sending.withLock { repo.send(SendMessageRequest(id, "text", text = p.text)) }.fold(
                { sent ->
                    sentIds += sent.id
                    _state.update { s ->
                        val messages = s.messages.orEmpty()
                        s.copy(
                            pending = s.pending.filter { it.localId != p.localId },
                            messages = if (messages.any { it.id == sent.id }) messages else messages + sent,
                        )
                    }
                },
                {
                    _state.update {
                            s ->
                        s.copy(pending = s.pending.map { if (it.localId == p.localId) it.copy(failed = true) else it })
                    }
                },
            )
        }
    }
}
