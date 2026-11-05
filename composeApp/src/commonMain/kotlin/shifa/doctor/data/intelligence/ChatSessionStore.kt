package shifa.doctor.data.intelligence

import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.Instant
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.storage.secureSettings
import shifa.doctor.utils.currentInstant

data class ChatStoreState(
    // Newest first.
    val sessions: List<ChatSession> = emptyList(),
    val activeId: String? = null,
    val loaded: Boolean = false,
) {
    val active: ChatSession? get() = sessions.firstOrNull { it.id == activeId }
}

/**
 * The doctor's Intelligence chats on this device (INTEL-01, 02 §3.9 and §4.2), under
 * `shifa_intelligence_chats_v1:<doctorId>` as Flutter kept them. Fix (ticket): stored in the encrypted settings
 * (Keychain / EncryptedSharedPreferences; chats name patients) instead of plain preferences, and capped at
 * [MAX_SESSIONS] chats (Flutter kept every chat forever); the oldest go first, never the open one. Every change is
 * written at once.
 */
class ChatSessionStore(
    private val settings: Settings = secureSettings,
    private val doctorId: () -> String = { profileRepository.me.value?.profile?.id?.toString() ?: "anonymous" },
    private val now: () -> Instant = ::currentInstant,
) {
    private val _state = MutableStateFlow(ChatStoreState())
    val state: StateFlow<ChatStoreState> = _state.asStateFlow()

    // The doctor the loaded chats belong to: another sign-in on this device reads its own.
    private var loadedFor: String? = null

    private fun key(id: String) = "$KEY_PREFIX$id"

    /**
     * Reads the chats once per doctor. A cold start opens on the welcome: an empty chat, made if there is none (the
     * history stays for the list). Within the app's life the open chat is kept, so leaving and coming back resumes it.
     */
    fun ensureLoaded() {
        val id = doctorId()
        if (_state.value.loaded && loadedFor == id) return
        val sessions =
            settings.getStringOrNull(key(id))?.let { ChatSession.decodeList(it, now()) }.orEmpty().toMutableList()
        val welcome = sessions.firstOrNull { it.messages.isEmpty() } ?: newSession().also { sessions.add(0, it) }
        loadedFor = id
        save(ChatStoreState(sessions, welcome.id, loaded = true))
    }

    /** A new, empty chat on top, opened (a new `conversationId` for the backend). */
    fun create(): ChatSession {
        ensureLoaded()
        val created = newSession()
        save(_state.value.copy(sessions = listOf(created) + _state.value.sessions, activeId = created.id))
        return created
    }

    /** The welcome: an empty chat if there is one, else a new one. */
    fun goToWelcome() {
        ensureLoaded()
        _state.value.sessions.firstOrNull { it.messages.isEmpty() }?.let { s ->
            _state.update { it.copy(activeId = s.id) }
        } ?: create()
    }

    fun select(id: String) {
        ensureLoaded()
        if (_state.value.sessions.any { it.id == id }) _state.update { it.copy(activeId = id) }
    }

    /** Deleting the last chat leaves a new empty one; deleting the open one opens the newest left. */
    fun delete(id: String) {
        ensureLoaded()
        val s = _state.value
        val left = s.sessions.filterNot { it.id == id }
        if (left.isEmpty()) {
            val created = newSession()
            save(s.copy(sessions = listOf(created), activeId = created.id))
            return
        }
        save(s.copy(sessions = left, activeId = if (s.activeId == id) left.first().id else s.activeId))
    }

    /** Keeps `updatedAt`, so a rename doesn't move the chat to the top; a blank title is ignored. */
    fun rename(
        id: String,
        title: String,
    ) {
        ensureLoaded()
        val trimmed = title.trim()
        if (trimmed.isEmpty() || _state.value.sessions.none { it.id == id }) return
        save(
            _state.value.copy(
                sessions =
                    _state.value.sessions.map {
                        if (it.id == id) {
                            it.copy(
                                title = trimmed,
                                titleCustom = true,
                            )
                        } else {
                            it
                        }
                    },
            ),
        )
    }

    /** After every finished turn: the chat's messages, now as its last change, on top, opened. */
    fun replaceMessages(
        id: String,
        messages: List<ChatMessage>,
    ) {
        ensureLoaded()
        // A turn that finishes after its chat was deleted, or after another doctor signed in, changes nothing.
        if (_state.value.sessions.none { it.id == id }) return
        val at = now()
        // A copy: the caller's list may change later, unseen by the state and the stored chats.
        val copy = messages.toList()
        val sessions =
            _state.value.sessions
                .map { if (it.id == id) it.copy(messages = copy, updatedAt = at) else it }
                .sortedByDescending { it.updatedAt }
        save(_state.value.copy(sessions = sessions, activeId = id))
    }

    private fun newSession(): ChatSession {
        val at = now()
        // Unique even for two chats made in the same millisecond (the id is the backend's conversation key).
        val id = "chat_${at.toEpochMilliseconds()}_${counter++}"
        return ChatSession(id, ChatSession.UNTITLED, at, at, emptyList())
    }

    private fun save(next: ChatStoreState) {
        val capped =
            if (next.sessions.size <= MAX_SESSIONS) {
                next
            } else {
                val keep =
                    next.sessions.sortedByDescending { it.updatedAt }.filter { it.id != next.activeId }.take(
                        MAX_SESSIONS - 1,
                    )
                val ids = keep.map { it.id }.toSet() + listOfNotNull(next.activeId)
                next.copy(sessions = next.sessions.filter { it.id in ids })
            }
        settings.putString(key(loadedFor ?: doctorId()), ChatSession.encodeList(capped.sessions))
        _state.value = capped
    }

    companion object {
        const val KEY_PREFIX = "shifa_intelligence_chats_v1:"
        const val MAX_SESSIONS = 50
        private var counter = 0
    }
}

val chatSessionStore by lazy { ChatSessionStore() }
