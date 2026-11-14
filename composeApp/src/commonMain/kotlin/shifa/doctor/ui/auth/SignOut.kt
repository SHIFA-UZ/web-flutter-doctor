package shifa.doctor.ui.auth

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import shifa.doctor.data.applock.AppLockRepository
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.AuthEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.ic_logout_22
import shifa.doctor.generated.resources.pinStaysSet
import shifa.doctor.generated.resources.signOut
import shifa.doctor.generated.resources.signOutConfirm
import shifa.doctor.generated.resources.signOutTitle
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaSheetHost
import shifa.doctor.utils.tr

data class SignOutUiState(
    val asking: Boolean = false,
    // App lock on: the question says the PIN stays (flow 08/15 §3).
    val pinSet: Boolean = false,
)

/**
 * AUTH-09 (flow 08/15, 01 §2.10): the one sign-out question for every place that offers it (More sheet, Profile,
 * Security, the shell for now). Confirming ends the session; the shell's "session is null" effect does the single
 * navigation to Sign In, which says "You've signed out".
 */
class SignOutViewModel(
    private val sessions: SessionRepository = sessionRepository,
    private val appLock: AppLockRepository = AppLockRepository(),
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(SignOutUiState())
    val state: StateFlow<SignOutUiState> = _state.asStateFlow()

    fun ask() = _state.update { SignOutUiState(asking = true, pinSet = appLock.hasPin) }

    fun cancel() {
        if (!_state.value.asking) return
        tracker.track(AuthEvents.SignOutCancelled)
        _state.update { it.copy(asking = false) }
    }

    fun confirm() {
        if (!_state.value.asking) return // a second tap after the sheet closed
        _state.update { it.copy(asking = false) }
        sessions.signOut()
    }
}

/** The question as a sheet (DialogStates on phones); Cancel, a tap outside and Back all cancel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignOutSheet(
    state: SignOutUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    if (!state.asking) return
    ShifaSheetHost(onCancel) {
        SignOutSheetContent(state, onConfirm, onCancel)
    }
}

@Composable
fun SignOutSheetContent(
    state: SignOutUiState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) = ShifaDialogSheet(
    tr(Res.string.signOutTitle),
    tr(Res.string.signOut),
    onConfirm,
    modifier,
    body =
        listOfNotNull(
            tr(Res.string.signOutConfirm),
            tr(Res.string.pinStaysSet).takeIf {
                state.pinSet
            },
        ).joinToString("\n"),
    tone = DialogTone.Destructive,
    icon = Res.drawable.ic_logout_22,
    cancelText = tr(Res.string.cancel),
    onCancel = onCancel,
)
