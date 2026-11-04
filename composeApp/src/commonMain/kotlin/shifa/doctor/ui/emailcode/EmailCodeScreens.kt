package shifa.doctor.ui.emailcode

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.painterResource
import shifa.doctor.data.session.Session
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.`continue`
import shifa.doctor.generated.resources.email
import shifa.doctor.generated.resources.enterEmailForOtp
import shifa.doctor.generated.resources.enterOtp
import shifa.doctor.generated.resources.ic_alert_circle_16
import shifa.doctor.generated.resources.ic_check_circle_16
import shifa.doctor.generated.resources.ic_mail_20
import shifa.doctor.generated.resources.otpCode
import shifa.doctor.generated.resources.otpSentToEmail
import shifa.doctor.generated.resources.resendCode
import shifa.doctor.generated.resources.resendCodeIn
import shifa.doctor.generated.resources.resetPassword
import shifa.doctor.generated.resources.signInWithEmail
import shifa.doctor.generated.resources.verify
import shifa.doctor.ui.auth.AuthFrame
import shifa.doctor.ui.auth.AuthTitle
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaFontSize
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.ShifaBanner
import shifa.doctor.ui.widgets.ShifaBannerType
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.utils.tr

@Composable
internal fun codeBanner(
    banner: CodeBanner?,
    onDismiss: () -> Unit,
): (@Composable () -> Unit)? =
    banner?.let { b ->
        {
            ShifaBanner(
                tr(b.text),
                if (b.error) Res.drawable.ic_alert_circle_16 else Res.drawable.ic_check_circle_16,
                if (b.error) ShifaBannerType.Error else ShifaBannerType.Success,
                onClose = onDismiss,
            )
        }
    }

/** AUTH-03/04, flow 01/03 (DaAuth "email"): the address the code goes to. */
@Composable
fun EmailEntryScreen(
    mode: EmailCodeMode,
    onBack: () -> Unit,
    onSent: (email: String) -> Unit,
    initialEmail: String = "",
    viewModel: EmailEntryViewModel = viewModel { EmailEntryViewModel(mode, initialEmail) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sent by rememberUpdatedState(onSent)
    LaunchedEffect(state.sentTo) {
        state.sentTo?.let {
            viewModel.consumeSent()
            sent(it)
        }
    }
    EmailEntryScreenContent(mode, state, viewModel::onEmail, viewModel::submit, viewModel::dismissBanner, onBack)
}

@Composable
fun EmailEntryScreenContent(
    mode: EmailCodeMode,
    state: EmailEntryUiState,
    onEmail: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
) = AuthFrame(onBack = onBack, banner = codeBanner(state.banner, onDismissBanner)) {
    AuthTitle(
        tr(if (mode == EmailCodeMode.Reset) Res.string.resetPassword else Res.string.signInWithEmail),
        size = ShifaFontSize.F24,
        align = TextAlign.Start,
    )
    Text(tr(Res.string.enterEmailForOtp), style = ShifaTextStyles.dialogBody(), color = ShifaColors.Text.Secondary)
    ShifaInputField(
        state.email,
        onEmail,
        tr(Res.string.email),
        Modifier.fillMaxWidth(),
        supportingText = state.error?.let { tr(it) },
        isError = state.error != null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        trailingIcon = { Icon(painterResource(Res.drawable.ic_mail_20), null, tint = ShifaColors.Icon.Secondary) },
    )
    ShifaPrimaryButton(tr(Res.string.`continue`), onSubmit, Modifier.fillMaxWidth(), loading = state.loading)
}

/** AUTH-03/04, flow 01/04 (DaAuth "otp"): the 6-digit code. */
@Composable
fun CodeScreen(
    email: String,
    mode: EmailCodeMode,
    onBack: () -> Unit,
    onSignedIn: (Session) -> Unit,
    onResetCode: (email: String, code: String) -> Unit,
    viewModel: CodeViewModel = viewModel { CodeViewModel(email, mode) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    val reset by rememberUpdatedState(onResetCode)
    LaunchedEffect(state.signedIn) { state.signedIn?.let(signedIn) }
    LaunchedEffect(state.resetCode) { state.resetCode?.let { reset(state.email, it) } }
    CodeScreenContent(state, viewModel::onCode, viewModel::verify, viewModel::resend, viewModel::dismissBanner, onBack)
}

@Composable
fun CodeScreenContent(
    state: CodeUiState,
    onCode: (String) -> Unit,
    onVerify: () -> Unit,
    onResend: () -> Unit,
    onDismissBanner: () -> Unit,
    onBack: () -> Unit,
) = AuthFrame(onBack = onBack, banner = codeBanner(state.banner, onDismissBanner)) {
    AuthTitle(tr(Res.string.enterOtp), size = ShifaFontSize.F24, align = TextAlign.Start)
    Text(
        tr(Res.string.otpSentToEmail, state.email),
        style = ShifaTextStyles.dialogBody(),
        color = ShifaColors.Text.Secondary,
    )
    ShifaInputField(
        state.code,
        onCode,
        tr(Res.string.otpCode),
        Modifier.fillMaxWidth(),
        supportingText = state.error?.let { tr(it) },
        isError = state.error != null,
        enabled = !state.loading, // a code typed during a request would be cleared by its failure (flow §6)
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onVerify() }),
    )
    // "Resend code (59 s)": disabled with the countdown, a link once it's 0.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        ShifaLink(
            if (state.resendIn > 0) tr(Res.string.resendCodeIn, "${state.resendIn} s") else tr(Res.string.resendCode),
            onResend,
            enabled = state.resendIn == 0 && !state.loading,
        )
    }
    ShifaPrimaryButton(tr(Res.string.verify), onVerify, Modifier.fillMaxWidth(), loading = state.loading)
}
