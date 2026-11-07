package shifa.doctor.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.chat.ChatRepository
import shifa.doctor.data.model.Conversation
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.failedToStartConversation
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.utils.PracticeClock

data class PatientChatUiState(
    val conversation: Conversation? = null,
    // The backend couldn't find or start it: what to say, and the screen goes back to the list (flow 07/07 §6).
    val failed: Notice? = null,
)

/**
 * "Send message" on a patient (CHAT-07, flow 07/07): `POST /messages/conversations/start` finds the doctor's
 * conversation with the patient or starts one (`findOrCreateConversation`, no message sent), so the app doesn't read
 * the whole list to look for it (flow §12). One request per opening.
 */
class PatientChatViewModel(
    private val patientId: Long,
    private val repo: ChatRepository = ChatRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(PatientChatUiState())
    val state: StateFlow<PatientChatUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repo.start(patientId = patientId).fold(
                { c -> _state.update { it.copy(conversation = c) } },
                { e ->
                    val text = e.toUserMessage(Res.string.failedToStartConversation)
                    _state.update { it.copy(failed = Notice(text, requestId = e.requestId)) }
                },
            )
        }
    }
}

/**
 * The thread with a patient, from their id: the header with the name the caller knows and the thread's skeleton while
 * the conversation resolves (flow 07/07 §3: no list flash), then the thread itself. Failure: the banner, and [onBack]
 * to the list.
 */
@Composable
fun PatientChatScreen(
    patientId: Long,
    name: String,
    photoUrl: String?,
    // Each opening resolves again (a new key), so a conversation started elsewhere since is found.
    opened: Long,
    onBack: () -> Unit,
    onOpenPatient: (Long) -> Unit,
    viewModel: PatientChatViewModel =
        viewModel(key = "patient-chat-$patientId-$opened") { PatientChatViewModel(patientId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // What the doctor types while it resolves goes on to the thread's draft.
    var draft by rememberSaveable { mutableStateOf("") }
    val conversation = state.conversation
    if (conversation != null) {
        val thread: ThreadViewModel = viewModel(key = "thread-${conversation.id}") { ThreadViewModel(conversation.id) }
        LaunchedEffect(thread) {
            if (draft.isNotBlank() && thread.state.value.draft.isBlank()) thread.draft(draft)
            draft = ""
        }
        return ThreadScreen(conversation.id, conversation, onBack, onOpenPatient, thread)
    }
    // Said only while this opening is on screen: one abandoned with Back stays quiet.
    LaunchedEffect(state.failed) {
        state.failed?.let {
            ShellNotice.pending.value = it
            onBack()
        }
    }
    val known =
        remember(patientId, name, photoUrl) {
            Conversation(0, patientId, name, photoUrl, isDoctor = false, null, null, 0)
        }
    ThreadContent(
        ThreadUiState(conversation = known, draft = draft),
        ThreadActions(back = onBack, openPatient = onOpenPatient, draft = { draft = it }),
        PracticeClock(null),
    )
}
