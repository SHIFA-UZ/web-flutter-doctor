package shifa.doctor.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.changePassword
import shifa.doctor.generated.resources.confirmNewPassword
import shifa.doctor.generated.resources.currentPassword
import shifa.doctor.generated.resources.currentPasswordIncorrect
import shifa.doctor.generated.resources.currentPasswordIsRequired
import shifa.doctor.generated.resources.failedToChangePassword
import shifa.doctor.generated.resources.newPassword
import shifa.doctor.generated.resources.newPasswordConfirmationMismatchError
import shifa.doctor.generated.resources.password
import shifa.doctor.generated.resources.passwordUpdatedSuccessfully
import shifa.doctor.generated.resources.passwordsDoNotMatch
import shifa.doctor.generated.resources.pleaseConfirmNewPasswordError
import shifa.doctor.generated.resources.pleaseConfirmPassword
import shifa.doctor.generated.resources.security
import shifa.doctor.ui.applock.AppLockSection
import shifa.doctor.ui.auth.PasswordChecklist
import shifa.doctor.ui.auth.PasswordField
import shifa.doctor.ui.auth.newPasswordErrors
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.utils.tr

data class SecurityUiState(
    val current: String = "",
    val new: String = "",
    val confirm: String = "",
    val showCurrent: Boolean = false,
    val showNew: Boolean = false,
    val showConfirm: Boolean = false,
    val currentError: StringResource? = null,
    val newError: StringResource? = null,
    val confirmError: StringResource? = null,
    val saving: Boolean = false,
    val banner: CodeBanner? = null,
    val askDiscard: Boolean = false,
    val done: Boolean = false,
) {
    val dirty: Boolean get() = current.isNotEmpty() || new.isNotEmpty() || confirm.isNotEmpty()

    // Enabled only once the request can succeed (flow 08/11 §3.3): the checklist and the confirm line say what's missing.
    val canChange: Boolean get() = current.isNotEmpty() && newPasswordErrors(new, confirm) == (null to null)

    // Shown while typing once the confirmation is as long as the password (the button is off until it matches).
    val confirmMismatch: Boolean get() = confirm.isNotEmpty() && confirm.length >= new.length && confirm != new
}

/**
 * PROF-04 password card (flow 08/11 §3.3, DaProfile `profSecurity`) with the app lock (LOCK-01's section) under it.
 * Doctors and clinic staff both use it. Passwords are sent exactly as typed (never trimmed); the new one must meet the
 * backend's policy, shown live by the registration checklist.
 */
class SecurityViewModel(
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(SecurityUiState())
    val state: StateFlow<SecurityUiState> = _state.asStateFlow()

    fun ui(change: (SecurityUiState) -> SecurityUiState) = _state.update(change)

    fun type(change: (SecurityUiState) -> SecurityUiState) =
        _state.update { change(it).copy(currentError = null, newError = null, confirmError = null) }

    fun back(): Boolean {
        val s = _state.value
        return when (backStep(s.saving, s.askDiscard, s.dirty)) {
            // Nothing survives a close, not even an eye left open or a banner.
            BackStep.Close -> true.also { _state.value = SecurityUiState() }
            BackStep.Ask -> false.also { _state.update { it.copy(askDiscard = true) } }
            BackStep.CloseQuestion -> false.also { _state.update { it.copy(askDiscard = false) } }
            BackStep.Stay -> false
        }
    }

    /** Nothing here survives a close: the next open starts empty. */
    fun discard() = _state.update { SecurityUiState(done = true) }

    fun closed() = _state.update { SecurityUiState() }

    fun change() {
        val s = _state.value
        if (s.saving) return
        val (newError, confirmError) = newPasswordErrors(s.new, s.confirm)
        val currentError = Res.string.currentPasswordIsRequired.takeIf { s.current.isEmpty() }
        if (currentError != null || newError != null || confirmError != null) {
            return _state.update {
                it.copy(
                    currentError = currentError,
                    newError = newError,
                    // This form's own wording (flow 08/11 §9).
                    confirmError =
                        when (confirmError) {
                            Res.string.pleaseConfirmPassword -> Res.string.pleaseConfirmNewPasswordError
                            Res.string.passwordsDoNotMatch -> Res.string.newPasswordConfirmationMismatchError
                            else -> confirmError
                        },
                )
            }
        }
        _state.update { it.copy(saving = true, banner = null) }
        viewModelScope.launch {
            auth.changePassword(s.current, s.new).fold(
                onSuccess = {
                    _state.value =
                        SecurityUiState(
                            banner = CodeBanner(Res.string.passwordUpdatedSuccessfully, error = false),
                        )
                },
                onFailure = { e ->
                    _state.update {
                        // The policy was checked here, so a 400 means the current password was wrong (§8).
                        if (e is ApiException.ValidationError && e.status == 400) {
                            it.copy(saving = false, current = "", currentError = Res.string.currentPasswordIncorrect)
                        } else {
                            it.copy(
                                saving = false,
                                banner =
                                    CodeBanner(
                                        e.toUserMessage(Res.string.failedToChangePassword),
                                        requestId = e.requestId,
                                    ),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun SecurityScreen(
    onClose: () -> Unit,
    viewModel: SecurityViewModel = viewModel { SecurityViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val back = { if (viewModel.back()) onClose() }
    FormExitEffects(state.done, viewModel::closed, onClose, back)
    SecurityContent(
        state,
        SecurityActions(back, viewModel::change, viewModel::type, viewModel::ui, viewModel::discard),
    ) {
        AppLockSection()
    }
}

class SecurityActions(
    val back: () -> Unit = {},
    val change: () -> Unit = {},
    val type: ((SecurityUiState) -> SecurityUiState) -> Unit = {},
    val ui: ((SecurityUiState) -> SecurityUiState) -> Unit = {},
    val discard: () -> Unit = {},
)

/** [appLock] is the app lock section; previews leave it out (Robolectric can't open the Android keystore). */
@Composable
fun SecurityContent(
    state: SecurityUiState,
    actions: SecurityActions,
    appLock: @Composable () -> Unit = {},
) {
    val enabled = !state.saving
    ProfileForm(
        tr(Res.string.security),
        actions.back,
        onSave = null,
        askDiscard = state.askDiscard,
        onDiscard = actions.discard,
        onKeep = { actions.ui { it.copy(askDiscard = false) } },
    ) {
        codeBanner(state.banner) { actions.ui { it.copy(banner = null) } }?.invoke()
        appLock()
        ShifaSection(tr(Res.string.password)) {
            Column(verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Gap.Md)) {
                PasswordField(
                    state.current,
                    { v -> actions.type { it.copy(current = v) } },
                    tr(Res.string.currentPassword),
                    state.showCurrent,
                    { actions.ui { it.copy(showCurrent = !it.showCurrent) } },
                    state.currentError,
                    KeyboardActions.Default,
                    ImeAction.Next,
                    enabled = enabled,
                )
                PasswordField(
                    state.new,
                    { v -> actions.type { it.copy(new = v) } },
                    tr(Res.string.newPassword),
                    state.showNew,
                    { actions.ui { it.copy(showNew = !it.showNew) } },
                    state.newError,
                    KeyboardActions.Default,
                    ImeAction.Next,
                    enabled = enabled,
                )
                PasswordChecklist(state.new)
                PasswordField(
                    state.confirm,
                    { v -> actions.type { it.copy(confirm = v) } },
                    tr(Res.string.confirmNewPassword),
                    state.showConfirm,
                    { actions.ui { it.copy(showConfirm = !it.showConfirm) } },
                    state.confirmError ?: Res.string.newPasswordConfirmationMismatchError.takeIf { state.confirmMismatch },
                    KeyboardActions(onDone = { if (state.canChange) actions.change() }),
                    ImeAction.Done,
                    enabled = enabled,
                )
                ShifaPrimaryButton(
                    tr(Res.string.changePassword),
                    actions.change,
                    Modifier.fillMaxWidth(),
                    enabled = state.canChange,
                    loading = state.saving,
                )
            }
        }
    }
}
