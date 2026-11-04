package shifa.doctor.ui.emailcode

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.model.ApiException
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.chooseNewPasswordFor
import shifa.doctor.generated.resources.confirmPassword
import shifa.doctor.generated.resources.hidePassword
import shifa.doctor.generated.resources.ic_eye_20
import shifa.doctor.generated.resources.ic_eye_off_20
import shifa.doctor.generated.resources.newPassword
import shifa.doctor.generated.resources.passwordMismatch
import shifa.doctor.generated.resources.pleaseConfirmPassword
import shifa.doctor.generated.resources.requestTimeout
import shifa.doctor.generated.resources.resetPassword
import shifa.doctor.generated.resources.resetPasswordFailed
import shifa.doctor.generated.resources.showPassword
import shifa.doctor.generated.resources.submit
import shifa.doctor.generated.resources.wrongOrExpiredCodeBackToCode
import shifa.doctor.ui.auth.AuthFrame
import shifa.doctor.ui.auth.AuthTitle
import shifa.doctor.ui.auth.PasswordChecklist
import shifa.doctor.ui.auth.label
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaFontSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.utils.PasswordPolicy
import shifa.doctor.utils.tr

data class ResetPasswordUiState(
    val email: String,
    val password: String = "",
    val confirm: String = "",
    val passwordVisible: Boolean = false,
    val confirmVisible: Boolean = false,
    val passwordError: StringResource? = null,
    val confirmError: StringResource? = null,
    val loading: Boolean = false,
    val banner: CodeBanner? = null,
    val signedIn: Session? = null,
)

/** AUTH-04, flow 01/05: the new password for the code from the previous step, then signed in. */
class ResetPasswordViewModel(
    email: String,
    private val code: String,
    private val auth: AuthRepository = AuthRepository(),
    private val sessions: SessionRepository = sessionRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ResetPasswordUiState(email))
    val state: StateFlow<ResetPasswordUiState> = _state.asStateFlow()

    fun onPassword(value: String) = _state.update { it.copy(password = value, passwordError = null) }

    fun onConfirm(value: String) = _state.update { it.copy(confirm = value, confirmError = null) }

    fun togglePassword() = _state.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun toggleConfirm() = _state.update { it.copy(confirmVisible = !it.confirmVisible) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        // Flow §9: the backend's policy (the first unmet rule, under the field), then the confirmation, inline only.
        val pwError = PasswordPolicy.missing(s.password).firstOrNull()?.label(s.password)
        val confirmError =
            when {
                s.confirm.isEmpty() -> Res.string.pleaseConfirmPassword
                s.confirm != s.password -> Res.string.passwordMismatch
                else -> null
            }
        if (pwError != null || confirmError != null) {
            return _state.update { it.copy(passwordError = pwError, confirmError = confirmError) }
        }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result =
                auth.resetForgottenPassword(
                    s.email,
                    code,
                    s.password,
                ).mapCatching { sessions.signIn(it).getOrThrow() }
            _state.update {
                result.fold(
                    onSuccess = { session -> it.copy(loading = false, signedIn = session) },
                    onFailure = { e -> it.copy(loading = false, banner = CodeBanner(error(e))) },
                )
            }
        }
    }

    // The code is only checked now: a wrong or expired one (401) sends the doctor back for a new code (flow §8). A 400 is
    // the backend refusing the password itself (AuthController's policy check), not the code.
    private fun error(e: Throwable): StringResource =
        when {
            e is ApiException.Unauthorized -> Res.string.wrongOrExpiredCodeBackToCode
            e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
            else -> e.toUserMessage(Res.string.resetPasswordFailed)
        }
}

/** AUTH-04 (DaAuth "reset"). Back returns to the code screen, where a new code can be asked for. */
@Composable
fun ResetPasswordScreen(
    email: String,
    code: String,
    onBack: () -> Unit,
    onSignedIn: (Session) -> Unit,
    viewModel: ResetPasswordViewModel = viewModel { ResetPasswordViewModel(email, code) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.signedIn) { state.signedIn?.let(signedIn) }
    ResetPasswordScreenContent(
        state,
        viewModel::onPassword,
        viewModel::onConfirm,
        viewModel::togglePassword,
        viewModel::toggleConfirm,
        viewModel::submit,
        viewModel::dismissBanner,
        onBack,
    )
}

@Composable
fun ResetPasswordScreenContent(
    state: ResetPasswordUiState,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onTogglePassword: () -> Unit,
    onToggleConfirm: () -> Unit,
    onSubmit: () -> Unit,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
) {
    val confirmFocus = remember { FocusRequester() }
    AuthFrame(onBack = onBack, banner = codeBanner(state.banner, onDismissBanner)) {
        AuthTitle(tr(Res.string.resetPassword), size = ShifaFontSize.F24, align = TextAlign.Start)
        Text(
            tr(Res.string.chooseNewPasswordFor, state.email),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Secondary,
        )
        PasswordField(
            state.password,
            onPassword,
            tr(Res.string.newPassword),
            state.passwordVisible,
            onTogglePassword,
            state.passwordError,
            KeyboardActions(onNext = { confirmFocus.requestFocus() }),
            ImeAction.Next,
            enabled = !state.loading, // what is shown is what was sent (flow §6)
        )
        PasswordChecklist(state.password)
        PasswordField(
            state.confirm,
            onConfirm,
            tr(Res.string.confirmPassword),
            state.confirmVisible,
            onToggleConfirm,
            state.confirmError,
            KeyboardActions(onDone = { onSubmit() }),
            ImeAction.Done,
            Modifier.focusRequester(confirmFocus),
            enabled = !state.loading,
        )
        ShifaPrimaryButton(tr(Res.string.submit), onSubmit, Modifier.fillMaxWidth(), loading = state.loading)
    }
}

/** A password field with its own eye toggle (shared by the reset and registration forms). */
@Composable
internal fun PasswordField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggle: () -> Unit,
    error: StringResource?,
    actions: KeyboardActions,
    ime: ImeAction,
    fieldModifier: Modifier = Modifier,
    enabled: Boolean = true,
) = ShifaInputField(
    value,
    onValue,
    label,
    Modifier.fillMaxWidth(),
    supportingText = error?.let { tr(it) },
    isError = error != null,
    enabled = enabled,
    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ime),
    keyboardActions = actions,
    visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
    trailingIcon = {
        ShifaIconButton(
            if (visible) Res.drawable.ic_eye_off_20 else Res.drawable.ic_eye_20,
            tr(if (visible) Res.string.hidePassword else Res.string.showPassword),
            onToggle,
            variant = ShifaIconButtonVariant.Ghost,
        )
    },
    fieldModifier = fieldModifier,
)
