package shifa.doctor.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.aboutOpenFailed
import shifa.doctor.generated.resources.aboutPrivacyPolicy
import shifa.doctor.generated.resources.appPermissions
import shifa.doctor.generated.resources.appPermissionsHint
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.confirmItsYou
import shifa.doctor.generated.resources.`continue`
import shifa.doctor.generated.resources.deleteAccount
import shifa.doctor.generated.resources.deleteAccountBody
import shifa.doctor.generated.resources.deleteAccountQuestion
import shifa.doctor.generated.resources.deleteAccountQuestionBody
import shifa.doctor.generated.resources.deleteBlocked
import shifa.doctor.generated.resources.deleteCodeSent
import shifa.doctor.generated.resources.downloadMyData
import shifa.doctor.generated.resources.downloadMyDataHint
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.ok
import shifa.doctor.generated.resources.otpCode
import shifa.doctor.generated.resources.privacyAndData
import shifa.doctor.generated.resources.resendCode
import shifa.doctor.generated.resources.resendCodeIn
import shifa.doctor.generated.resources.yourData
import shifa.doctor.generated.resources.yourDataBody
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.navigation.Notice
import shifa.doctor.ui.navigation.ShellNotice
import shifa.doctor.ui.navigation.openPermissionSettings
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSecondaryButton
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSubHeader
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.utils.rememberFileSaver
import shifa.doctor.utils.rememberInAppBrowser
import shifa.doctor.utils.tr

class PrivacyActions(
    val back: () -> Unit = {},
    val openPrivacy: () -> Unit = {},
    val export: () -> Unit = {},
    val permissions: () -> Unit = {},
    val dismissBanner: () -> Unit = {},
    val askDelete: () -> Unit = {},
    val continueDelete: () -> Unit = {},
    val closeDelete: () -> Unit = {},
    val onCode: (String) -> Unit = {},
    val confirm: () -> Unit = {},
    val resend: () -> Unit = {},
)

/** Settings → Privacy & data (flow 08/12, board `DaProfile` setPrivacy), doctors only (BE-02 is DOCTOR-scoped). */
@Composable
fun PrivacyScreen(
    onClose: () -> Unit,
    viewModel: PrivacyViewModel = viewModel { PrivacyViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saver = rememberFileSaver()
    val browser = rememberInAppBrowser()
    PrivacyContent(
        state,
        PrivacyActions(
            back = onClose,
            openPrivacy = { if (!browser(PRIVACY_URL)) ShellNotice.pending.value = Notice(Res.string.aboutOpenFailed) },
            export = { viewModel.export(saver::saveFile) },
            permissions = ::openPermissionSettings,
            dismissBanner = viewModel::dismissBanner,
            askDelete = viewModel::askDelete,
            continueDelete = viewModel::continueDelete,
            closeDelete = viewModel::closeDelete,
            onCode = viewModel::onCode,
            confirm = viewModel::confirm,
            resend = viewModel::resend,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyContent(
    state: PrivacyUiState,
    actions: PrivacyActions,
) {
    Column(Modifier.fillMaxSize().background(ShifaColors.Bg.Canvas)) {
        ShifaSubHeader(tr(Res.string.privacyAndData), Modifier.statusBarsPadding(), onBack = actions.back)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ShifaSpacing.Inset.Md),
            verticalArrangement = Arrangement.spacedBy(ShifaSize.S16),
        ) {
            codeBanner(state.banner, actions.dismissBanner)?.invoke()
            ShifaSection(tr(Res.string.yourData)) {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
                    Text(
                        tr(Res.string.yourDataBody),
                        style = ShifaTextStyles.bodyText(),
                        color = ShifaColors.Text.Secondary,
                    )
                    ShifaLink(tr(Res.string.aboutPrivacyPolicy), actions.openPrivacy)
                }
            }
            ShifaSection(null) {
                Column {
                    ShifaSettingsItem(
                        tr(Res.string.downloadMyData),
                        actions.export,
                        subtitle = tr(Res.string.downloadMyDataHint),
                    )
                    ShifaSettingsItem(
                        tr(Res.string.appPermissions),
                        actions.permissions,
                        subtitle = tr(Res.string.appPermissionsHint),
                        divider = false,
                    )
                }
            }
            ShifaSection(tr(Res.string.deleteAccount)) {
                Column(verticalArrangement = Arrangement.spacedBy(ShifaSize.S12)) {
                    Text(
                        tr(Res.string.deleteAccountBody),
                        style = ShifaTextStyles.bodyText(),
                        color = ShifaColors.Text.Primary,
                    )
                    ShifaSecondaryButton(
                        tr(Res.string.deleteAccount),
                        actions.askDelete,
                        Modifier.fillMaxWidth(),
                        destructive = true,
                        loading = state.busy && state.step == null,
                    )
                }
            }
        }
    }
    state.step?.let { step ->
        ModalBottomSheet(
            actions.closeDelete,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RectangleShape,
            containerColor = Color.Transparent,
            dragHandle = null,
        ) {
            when (step) {
                DeleteStep.Ask ->
                    ShifaDialogSheet(
                        tr(Res.string.deleteAccountQuestion),
                        tr(Res.string.`continue`),
                        actions.continueDelete,
                        body = tr(Res.string.deleteAccountQuestionBody),
                        tone = DialogTone.Destructive,
                        icon = Res.drawable.ic_trash_22,
                        cancelText = tr(Res.string.cancel),
                        onCancel = actions.closeDelete,
                        confirmLoading = state.busy,
                    )
                DeleteStep.Code -> DeleteCodeSheet(state, actions)
                DeleteStep.Blocked -> {
                    val reasons = state.obligations.map { "• " + tr(it) }
                    ShifaDialogSheet(
                        tr(Res.string.deleteBlocked),
                        tr(Res.string.ok),
                        actions.closeDelete,
                        body = reasons.joinToString("\n"),
                        icon = Res.drawable.ic_trash_22,
                    )
                }
            }
        }
    }
}

/** The second step: the code e-mailed to the account; a wrong or expired one stays here with its message. */
@Composable
internal fun DeleteCodeSheet(
    state: PrivacyUiState,
    actions: PrivacyActions,
) = ShifaTray(tr(Res.string.confirmItsYou), actions.closeDelete) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShifaSpacing.Inset.Md).padding(bottom = ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        codeBanner(state.codeBanner)?.invoke()
        Text(
            tr(Res.string.deleteCodeSent, state.maskedEmail.orEmpty()),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Secondary,
        )
        ShifaInputField(
            state.code,
            actions.onCode,
            tr(Res.string.otpCode),
            Modifier.fillMaxWidth(),
            supportingText = state.codeError?.let { tr(it) },
            isError = state.codeError != null,
            enabled = !state.busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.confirm() }),
        )
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShifaLink(
                if (state.resendIn > 0) {
                    tr(
                        Res.string.resendCodeIn,
                        state.resendIn.toString(),
                    )
                } else {
                    tr(Res.string.resendCode)
                },
                actions.resend,
                enabled = state.resendIn == 0 && !state.busy,
            )
        }
        ShifaPrimaryButton(
            tr(Res.string.deleteAccount),
            actions.confirm,
            Modifier.fillMaxWidth(),
            loading = state.busy,
            destructive = true,
        )
    }
}
