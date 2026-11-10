package shifa.doctor.ui.intelligence

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import shifa.doctor.data.intelligence.ChatSession
import shifa.doctor.data.intelligence.ChatSessionStore
import shifa.doctor.data.intelligence.ChatStoreState
import shifa.doctor.data.intelligence.chatSessionStore
import shifa.doctor.data.profile.ProfileRepository
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.ChatEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.intelDownloadEmpty
import shifa.doctor.generated.resources.intelDownloadFailed
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.utils.currentInstant

data class IntelligenceUiState(
    val store: ChatStoreState = ChatStoreState(),
    val history: Boolean = false,
    val query: String = "",
    // The open conversation's options (Download, Rename, Delete).
    val menu: Boolean = false,
    // The chat being renamed (the field's text), or deleted.
    val renaming: ChatSession? = null,
    val renameText: String = "",
    val deleting: ChatSession? = null,
    val banner: CodeBanner? = null,
    // A reply is streaming in the open chat (INTEL-04 sets it): deleting that chat asks to stop it first.
    val busy: Boolean = false,
    // The doctor's first and last name for the welcome's greeting; null until the profile is there.
    val doctorFirst: String? = null,
    val doctorLast: String? = null,
) {
    val active: ChatSession? get() = store.active

    /** A conversation is open (it has messages); otherwise the welcome shows. */
    val conversationOpen: Boolean get() = active?.messages?.isNotEmpty() == true

    /** The history list: every chat, newest first, those matching the search. */
    val listed: List<ChatSession> get() = store.sessions.filter { it.matchesQuery(query) }

    /** Save is off for a blank or unchanged title (flow 03/02 §9 target). */
    val canRename: Boolean get() = renameText.isNotBlank() && renameText.trim() != renaming?.displayTitleText()
}

/** A finished export for the share sheet. */
class ChatExport(
    val fileName: String,
    val bytes: ByteArray,
)

/**
 * Shifa Intelligence's frame (INTEL-02, 02 §4.1, flows 03/01 and 03/02): the open chat, the welcome, the history sheet,
 * rename, delete and export. The chats live in [ChatSessionStore]; the thread writes its turns there too.
 */
class IntelligenceViewModel(
    private val store: ChatSessionStore = chatSessionStore,
    private val now: () -> Instant = ::currentInstant,
    profile: ProfileRepository = profileRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(IntelligenceUiState())
    val state: StateFlow<IntelligenceUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { store.state.collect { s -> _state.update { it.copy(store = s) } } }
        viewModelScope.launch {
            profile.me.collect { me ->
                _state.update { it.copy(doctorFirst = me?.profile?.firstName, doctorLast = me?.profile?.lastName) }
            }
        }
    }

    /** On open (and on every return): the chats of the doctor signed in now. */
    fun load() = store.ensureLoaded()

    /** App bar back: from a conversation to the welcome; true when the welcome was showing (leave Intelligence). */
    fun back(): Boolean {
        if (!_state.value.conversationOpen) return true
        store.goToWelcome()
        return false
    }

    /**
     * New conversation: the empty chat if there is one, else a new one (flow 03/02 §12: repeated taps made a pile of
     * empty "New chat"s). Its id is the backend's new `conversationId` once something is sent.
     */
    fun newConversation() {
        store.goToWelcome()
        _state.update { it.copy(history = false, query = "", menu = false) }
    }

    fun history(open: Boolean) = _state.update { it.copy(history = open, query = if (open) it.query else "") }

    fun query(text: String) = _state.update { it.copy(query = text) }

    fun select(id: String) {
        store.select(id)
        _state.update { it.copy(history = false, query = "") }
    }

    fun menu(open: Boolean) = _state.update { it.copy(menu = open) }

    fun askRename(session: ChatSession?) =
        _state.update { it.copy(menu = false, renaming = session, renameText = session?.displayTitleText().orEmpty()) }

    fun renameText(text: String) = _state.update { it.copy(renameText = text) }

    fun rename() {
        val s = _state.value
        val target = s.renaming ?: return
        if (!s.canRename) return
        store.rename(target.id, s.renameText)
        tracker.track(ChatEvents.IntelligenceConversationRenamed)
        _state.update { it.copy(renaming = null, renameText = "") }
    }

    fun askDelete(session: ChatSession?) = _state.update { it.copy(menu = false, deleting = session) }

    fun delete() {
        val target = _state.value.deleting ?: return
        store.delete(target.id)
        tracker.track(ChatEvents.IntelligenceConversationDeleted)
        _state.update { it.copy(deleting = null) }
    }

    fun busy(on: Boolean) = _state.update { it.copy(busy = on) }

    /** The open chat as a `.txt` for the share sheet; null (with a message) when there is nothing to export yet. */
    fun export(): ChatExport? {
        _state.update { it.copy(menu = false) }
        val active = _state.value.active
        if (active == null || active.messages.isEmpty()) {
            tracker.track(ChatEvents.IntelligenceConversationExportFailed("empty"))
            _state.update { it.copy(banner = CodeBanner(Res.string.intelDownloadEmpty)) }
            return null
        }
        val at = now()
        val stamp = at.toString().replace(':', '-').replace('.', '-')
        val safeId = active.id.replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return ChatExport("shifa-intelligence-$safeId-$stamp.txt", active.exportText(at).encodeToByteArray())
    }

    /** The share sheet opened with the export. */
    fun exported() = tracker.track(ChatEvents.IntelligenceConversationExported)

    /** The share sheet couldn't open. */
    fun exportFailed() {
        tracker.track(ChatEvents.IntelligenceConversationExportFailed("share_failed"))
        _state.update { it.copy(banner = CodeBanner(Res.string.intelDownloadFailed)) }
    }

    fun dismissBanner() = _state.update { it.copy(banner = null) }
}

// The rename field starts with the title the doctor sees ("New chat" stays empty: it isn't a title to edit).
private fun ChatSession.displayTitleText(): String = displayTitle.takeUnless { it == ChatSession.UNTITLED }.orEmpty()
