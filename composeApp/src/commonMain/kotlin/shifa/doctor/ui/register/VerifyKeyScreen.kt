package shifa.doctor.ui.register

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
import shifa.doctor.data.api.requestId
import shifa.doctor.data.api.toUserMessage
import shifa.doctor.data.auth.AuthRepository
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.model.ApiException
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.invalidInviteKey
import shifa.doctor.generated.resources.inviteKeyHint
import shifa.doctor.generated.resources.next
import shifa.doctor.generated.resources.oneTimeKey
import shifa.doctor.generated.resources.paste
import shifa.doctor.generated.resources.pleaseEnterOneTimeKey
import shifa.doctor.generated.resources.requestFailed
import shifa.doctor.generated.resources.requestTimeout
import shifa.doctor.generated.resources.verify
import shifa.doctor.ui.auth.AuthFrame
import shifa.doctor.ui.auth.AuthLogo
import shifa.doctor.ui.emailcode.CodeBanner
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaActionButton
import shifa.doctor.ui.widgets.ShifaActionVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.utils.tr

data class VerifyKeyUiState(
    val key: String = "",
    val error: StringResource? = null,
    val loading: Boolean = false,
    val banner: CodeBanner? = null,
    /** A valid key: the registration form for its purpose opens. */
    val invite: Invite? = null,
)

/** AUTH-05, flow 01/06: the invitation key decides between the doctor and the receptionist registration. */
class VerifyKeyViewModel(
    private val auth: AuthRepository = AuthRepository(),
) : ViewModel() {
    private val _state = MutableStateFlow(VerifyKeyUiState())
    val state: StateFlow<VerifyKeyUiState> = _state.asStateFlow()

    // The key is read-only while it is being checked (flow §6), so the answer always belongs to the shown key.
    fun onKey(value: String) =
        _state.update { if (it.loading) it else it.copy(key = value, error = null, invite = null) }

    fun dismissBanner() = _state.update { it.copy(banner = null) }

    fun submit() {
        val s = _state.value
        if (s.loading) return
        // Keys pasted from an e-mail often carry spaces or line breaks around them (flow §8).
        val key = s.key.trim()
        if (key.isEmpty()) return _state.update { it.copy(error = Res.string.pleaseEnterOneTimeKey) }
        _state.update { it.copy(loading = true, banner = null) }
        viewModelScope.launch {
            val result = auth.verifyKey(key)
            _state.update {
                result.fold(
                    onSuccess = { invite ->
                        if (invite == null) {
                            it.copy(loading = false, banner = CodeBanner(Res.string.invalidInviteKey))
                        } else {
                            it.copy(loading = false, invite = invite)
                        }
                    },
                    onFailure = {
                            e ->
                        it.copy(loading = false, banner = CodeBanner(error(e), requestId = e.requestId))
                    },
                )
            }
        }
    }

    private fun error(e: Throwable): StringResource =
        when {
            e is ApiException.NetworkError && e.timeout -> Res.string.requestTimeout
            else -> e.toUserMessage(Res.string.requestFailed)
        }
}

/** AUTH-05 (DaAuth "verify"): bar title "Verify", the 120 logo, the hint, the key with Paste, Next. */
@Composable
fun VerifyKeyScreen(
    onBack: () -> Unit,
    onVerified: (Invite) -> Unit,
    viewModel: VerifyKeyViewModel = viewModel { VerifyKeyViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val verified by rememberUpdatedState(onVerified)
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(state.invite) { state.invite?.let(verified) }
    VerifyKeyScreenContent(
        state,
        viewModel::onKey,
        onPaste = { clipboard.getText()?.text?.let(viewModel::onKey) },
        onSubmit = viewModel::submit,
        onDismissBanner = viewModel::dismissBanner,
        onBack = onBack,
    )
}

@Composable
fun VerifyKeyScreenContent(
    state: VerifyKeyUiState,
    onKey: (String) -> Unit,
    onPaste: () -> Unit,
    onSubmit: () -> Unit,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
) = AuthFrame(onBack = onBack, barTitle = tr(Res.string.verify), banner = codeBanner(state.banner, onDismissBanner)) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { AuthLogo(ShifaSize.S120) }
    Text(
        tr(Res.string.inviteKeyHint),
        Modifier.fillMaxWidth(),
        style = ShifaTextStyles.dialogBody(),
        color = ShifaColors.Text.Secondary,
        textAlign = TextAlign.Center,
    )
    ShifaInputField(
        state.key,
        onKey,
        tr(Res.string.oneTimeKey),
        Modifier.fillMaxWidth(),
        supportingText = state.error?.let { tr(it) },
        isError = state.error != null,
        enabled = !state.loading,
        // No autocorrect or capitalisation: keyboards "fix" keys (flow §8).
        keyboardOptions =
            KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = {
            ShifaActionButton(
                onPaste,
                text = tr(Res.string.paste),
                variant = ShifaActionVariant.Secondary,
                enabled = !state.loading,
            )
        },
    )
    ShifaPrimaryButton(tr(Res.string.next), onSubmit, Modifier.fillMaxWidth(), loading = state.loading)
}
