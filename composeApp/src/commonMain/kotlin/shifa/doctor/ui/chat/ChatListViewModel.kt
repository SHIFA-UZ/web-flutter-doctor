package shifa.doctor.ui.chat

import androidx.compose.foundation.lazy.LazyListState
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
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.chat.ChatRepository
import shifa.doctor.data.chat.ConversationSort
import shifa.doctor.data.chat.chatRepository
import shifa.doctor.data.chat.sortedFor
import shifa.doctor.data.model.Conversation
import shifa.doctor.data.model.UserSearchResult
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.countBucket
import shifa.doctor.data.tracking.events.ChatEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.couldNotLoadConversations
import shifa.doctor.generated.resources.failedToStartConversation
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

data class ChatListUiState(
    // Null until the first answer (skeleton cards).
    val conversations: List<Conversation>? = null,
    val refreshing: Boolean = false,
    val error: CodeBanner? = null,
    val sort: ConversationSort = ConversationSort.Newest,
    val choosingSort: Boolean = false,
    // What is typed, and the people found for it (300 ms after the last keystroke); null while that read is out.
    val typed: String = "",
    val results: List<UserSearchResult>? = null,
    val searchFailed: Boolean = false,
    val starting: Boolean = false,
    // A conversation to open (the screen opens it, once).
    val open: Conversation? = null,
) {
    val searching: Boolean get() = typed.isNotBlank()
    val shown: List<Conversation> get() = conversations.orEmpty().sortedFor(sort)
}

/**
 * The Chat tab (CHAT-02, 07 §1.1, flow 07/01): conversations read on open, on pull, on coming back and every 5 s while
 * the app is in front (no push-driven refresh until BE-14; nothing polls in the background); user search after typing
 * stops; a search result opens its existing conversation or starts one.
 */
class ChatListViewModel(
    private val repo: ChatRepository = chatRepository,
    private val every: Duration = 5.seconds,
    private val debounce: Duration = 300.milliseconds,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatListUiState())

    // WEB-07e: the list's scroll, kept while the open conversation beside it changes (each change is a new route).
    val listState = LazyListState()
    val state: StateFlow<ChatListUiState> = _state.asStateFlow()
    private var reading: Job? = null
    private var polling: Job? = null
    private var searching: Job? = null

    /**
     * On open, pull-to-refresh ([pull] shows the indicator), Retry, coming back. A read already out is left to finish
     * (its answer serves this call too), so a slow backend is never cancelled into never loading.
     */
    fun refresh(pull: Boolean = false) {
        if (pull) _state.update { it.copy(refreshing = true) }
        if (reading?.isActive == true) return
        reading = viewModelScope.launch { load() }
    }

    private suspend fun load() {
        repo.conversations().fold(
            { list -> _state.update { it.copy(conversations = list, error = null, refreshing = false) } },
            { e ->
                // A failed refresh keeps the cards it has; the banner says so.
                val banner = CodeBanner(e.toUserMessage(Res.string.couldNotLoadConversations), requestId = e.requestId)
                _state.update { it.copy(error = banner, refreshing = false) }
            },
        )
    }

    /** The app in front: read now and [every] after each answer; behind it: nothing (flow 07/01 §12). */
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

    fun sort(sort: ConversationSort) {
        if (sort != _state.value.sort) tracker.track(ChatEvents.ChatListSorted(sort))
        _state.update { it.copy(sort = sort, choosingSort = false) }
    }

    fun choosingSort(open: Boolean) = _state.update { it.copy(choosingSort = open) }

    /** Typing: the people found 300 ms after the last keystroke (Flutter asked on each one); blank shows the list. */
    fun type(
        text: String,
        now: Boolean = false,
    ) {
        _state.update { it.copy(typed = text, results = null, searchFailed = false) }
        searching?.cancel()
        if (text.isBlank()) return
        searching =
            viewModelScope.launch {
                if (!now) delay(debounce)
                repo.search(text.trim()).fold(
                    { found ->
                        if (_state.value.typed == text) {
                            tracker.track(
                                ChatEvents.ChatUsersSearched(countBucket(found.size)),
                            )
                        }
                        _state.update { if (it.typed == text) it.copy(results = found) else it }
                    },
                    {
                        _state.update {
                            if (it.typed == text) {
                                it.copy(
                                    results = emptyList(),
                                    searchFailed = true,
                                )
                            } else {
                                it
                            }
                        }
                    },
                )
            }
    }

    /** A card: read on the server (failures ignored), its pill gone at once, then the thread. */
    fun open(c: Conversation) {
        _state.update { s ->
            s.copy(open = c, conversations = s.conversations?.map { if (it.id == c.id) it.copy(unread = 0) else it })
        }
        viewModelScope.launch { repo.markRead(c.id) }
    }

    /**
     * A search result: their conversation if there is one (same person and role), else a new one (no message is sent);
     * the search clears either way. Failure: "Failed to start conversation".
     */
    fun startWith(user: UserSearchResult) {
        if (_state.value.starting) return
        _state.value.conversations?.firstOrNull { it.participantId == user.id && it.isDoctor == user.isDoctor }?.let {
            tracker.track(ChatEvents.ChatConversationStarted(user.isDoctor))
            type("")
            return open(it)
        }
        _state.update { it.copy(starting = true) }
        viewModelScope.launch {
            val started = if (user.isDoctor) repo.start(doctorId = user.id) else repo.start(patientId = user.id)
            _state.update { it.copy(starting = false) }
            started.fold(
                { c ->
                    tracker.track(ChatEvents.ChatConversationStarted(user.isDoctor))
                    type("")
                    _state.update {
                            s ->
                        s.copy(conversations = listOf(c) + s.conversations.orEmpty().filter { it.id != c.id })
                    }
                    open(c)
                },
                { e ->
                    tracker.track(ChatEvents.ChatConversationStartFailed(e.toFailureReason(), "search"))
                    ShellNotice.pending.value = Notice(Res.string.failedToStartConversation, requestId = e.requestId)
                },
            )
        }
    }

    /** Retry after a failed search: the same words again, at once. */
    fun retrySearch() {
        val text = _state.value.typed
        if (text.isNotBlank()) type(text, now = true)
    }

    /** The thread was opened. */
    fun opened() = _state.update { it.copy(open = null) }
}
