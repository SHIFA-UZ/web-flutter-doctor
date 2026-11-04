package shifa.doctor.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shifa.doctor.data.session.Session
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.createAccount
import shifa.doctor.generated.resources.emailOrPhone
import shifa.doctor.generated.resources.forgotPassword
import shifa.doctor.generated.resources.hidePassword
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_eye_20
import shifa.doctor.generated.resources.ic_eye_off_20
import shifa.doctor.generated.resources.ic_info_16
import shifa.doctor.generated.resources.ic_mail_20
import shifa.doctor.generated.resources.password
import shifa.doctor.generated.resources.showPassword
import shifa.doctor.generated.resources.signIn
import shifa.doctor.generated.resources.signInWithEmail
import shifa.doctor.ui.auth.AuthFrame
import shifa.doctor.ui.auth.AuthLogo
import shifa.doctor.ui.auth.AuthTitle
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaIconButton
import shifa.doctor.ui.widgets.ShifaIconButtonVariant
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.utils.tr

/** AUTH-02: Sign In, the hub of the signed-out screens (DaAuth board, default branch). */
@Composable
fun LoginScreen(
    onSignedIn: (Session) -> Unit,
    onEmailSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    viewModel: LoginViewModel = viewModel { LoginViewModel() },
    debugMenu: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.signedIn) { state.signedIn?.let(signedIn) }
    LoginScreenContent(
        state,
        onIdentifier = viewModel::onIdentifier,
        onPassword = viewModel::onPassword,
        onSubmit = viewModel::submit,
        onDismissBanner = viewModel::dismissBanner,
        onTogglePassword = viewModel::togglePasswordVisible,
        onEmailSignIn = onEmailSignIn,
        onForgotPassword = onForgotPassword,
        onCreateAccount = onCreateAccount,
        debugMenu = debugMenu,
    )
}

@Composable
fun LoginScreenContent(
    state: LoginUiState,
    onIdentifier: (String) -> Unit,
    onPassword: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissBanner: () -> Unit,
    onTogglePassword: () -> Unit,
    onEmailSignIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    debugMenu: (@Composable () -> Unit)? = null,
) {
    val visible = state.passwordVisible
    val passwordFocus = remember { FocusRequester() }

    // Leaving for another screen is off while signing in (flow §6 "Loading").
    fun guard(action: () -> Unit): () -> Unit = { if (!state.loading) action() }
    AuthFrame(
        banner =
            state.banner?.let { b ->
                {
                    ShifaBanner(
                        tr(b.text),
                        if (b.error) Res.drawable.ic_alert_circle_16 else Res.drawable.ic_info_16,
                        if (b.error) ShifaBannerType.Error else ShifaBannerType.Warning,
                        onClose = onDismissBanner,
                    )
                }
            },
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) { AuthLogo() }
        AuthTitle(tr(Res.string.signIn))
        ShifaInputField(
            state.identifier,
            onIdentifier,
            tr(Res.string.emailOrPhone),
            Modifier.fillMaxWidth(),
            supportingText = state.identifierError?.let { tr(it) },
            isError = state.identifierError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
        )
        ShifaInputField(
            state.password,
            onPassword,
            tr(Res.string.password),
            Modifier.fillMaxWidth(),
            supportingText = state.passwordError?.let { tr(it) },
            isError = state.passwordError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                ShifaIconButton(
                    if (visible) Res.drawable.ic_eye_off_20 else Res.drawable.ic_eye_20,
                    tr(if (visible) Res.string.hidePassword else Res.string.showPassword),
                    onTogglePassword,
                    variant = ShifaIconButtonVariant.Ghost,
                )
            },
            fieldModifier = Modifier.focusRequester(passwordFocus),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
            ShifaLink(tr(Res.string.forgotPassword), onForgotPassword, enabled = !state.loading)
        }
        Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
            ShifaPrimaryButton(tr(Res.string.signIn), onSubmit, Modifier.fillMaxWidth(), loading = state.loading)
            ShifaSecondaryButton(
                tr(Res.string.signInWithEmail),
                guard(onEmailSignIn),
                Modifier.fillMaxWidth(),
                icon = Res.drawable.ic_mail_20,
                enabled = !state.loading,
            )
            ShifaSecondaryButton(
                tr(Res.string.createAccount),
                guard(onCreateAccount),
                Modifier.fillMaxWidth(),
                enabled = !state.loading,
            )
        }
        debugMenu?.invoke()
    }
}
