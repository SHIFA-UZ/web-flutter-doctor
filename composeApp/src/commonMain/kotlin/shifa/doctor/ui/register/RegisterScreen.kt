package shifa.doctor.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.catalog.Clinic
import shifa.doctor.data.catalog.Profession
import shifa.doctor.data.profile.Gender
import shifa.doctor.data.session.Session
import shifa.doctor.data.tracking.TrackScreen
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.accountInformation
import shifa.doctor.generated.resources.confirmPassword
import shifa.doctor.generated.resources.confirmRegistration
import shifa.doctor.generated.resources.createAccount
import shifa.doctor.generated.resources.currentPassword
import shifa.doctor.generated.resources.discard
import shifa.doctor.generated.resources.discardRegistrationBody
import shifa.doctor.generated.resources.discardRegistrationTitle
import shifa.doctor.generated.resources.email
import shifa.doctor.generated.resources.existingPatientCreatingDoctorAccount
import shifa.doctor.generated.resources.firstName
import shifa.doctor.generated.resources.ic_check_12
import shifa.doctor.generated.resources.ic_trash_22
import shifa.doctor.generated.resources.keepEditing
import shifa.doctor.generated.resources.keyVerified
import shifa.doctor.generated.resources.lastName
import shifa.doctor.generated.resources.next
import shifa.doctor.generated.resources.optional
import shifa.doctor.generated.resources.otpCode
import shifa.doctor.generated.resources.otpSentToEmail
import shifa.doctor.generated.resources.password
import shifa.doctor.generated.resources.phoneNumber
import shifa.doctor.generated.resources.practiceTimezone
import shifa.doctor.generated.resources.practiceTimezonePlaceholder
import shifa.doctor.generated.resources.resendCode
import shifa.doctor.generated.resources.resendCodeIn
import shifa.doctor.generated.resources.stepOfTotal
import shifa.doctor.generated.resources.verify
import shifa.doctor.generated.resources.verifyYourEmail
import shifa.doctor.ui.auth.AuthFrame
import shifa.doctor.ui.auth.AuthTitle
import shifa.doctor.ui.auth.PasswordChecklist
import shifa.doctor.ui.auth.PasswordField
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.profile.TimeZonePickerSheet
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPickField
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaSheetHost
import shifa.doctor.ui.widgets.ShifaTag
import shifa.doctor.ui.widgets.ShifaTagType
import shifa.doctor.ui.widgets.ShifaTray
import shifa.doctor.ui.widgets.TrackScreenView
import shifa.doctor.utils.tr

/** The callbacks of [RegisterScreenContent], so the stateless content takes one value instead of twenty lambdas. */
class RegisterActions(
    val onFirstName: (String) -> Unit = {},
    val onLastName: (String) -> Unit = {},
    val onEmail: (String) -> Unit = {},
    val onPhone: (String) -> Unit = {},
    val onPassword: (String) -> Unit = {},
    val onConfirm: (String) -> Unit = {},
    val togglePassword: () -> Unit = {},
    val toggleConfirm: () -> Unit = {},
    val next: () -> Unit = {},
    val register: () -> Unit = {},
    val back: () -> Unit = {},
    val dismissBanner: () -> Unit = {},
    val openPicker: (RegisterPicker) -> Unit = {},
    val closePicker: () -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val retryPicker: () -> Unit = {},
    val pickTimeZone: (String) -> Unit = {},
    val pickClinic: (Clinic?) -> Unit = {},
    val toggleProfession: (Profession) -> Unit = {},
    val clearProfessions: () -> Unit = {},
    val openDob: () -> Unit = {},
    val pickDob: (LocalDate?) -> Unit = {},
    val toggleGender: () -> Unit = {},
    val pickGender: (Gender?) -> Unit = {},
    val onAddress: (String) -> Unit = {},
    val keepEditing: () -> Unit = {},
    val discard: () -> Unit = {},
    val onCode: (String) -> Unit = {},
    val verifyCode: () -> Unit = {},
    val resendCode: () -> Unit = {},
    val closeCode: () -> Unit = {},
    val onCurrentPassword: (String) -> Unit = {},
)

/** AUTH-06 (DaAuth `reg1`, its existing-patient branch, and `reg2`'s timezone): a doctor's registration. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun RegisterScreen(
    invite: Invite,
    onExit: () -> Unit,
    onSignedIn: (Session) -> Unit,
    viewModel: RegisterViewModel = viewModel { RegisterViewModel(invite) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by rememberUpdatedState(onSignedIn)
    LaunchedEffect(state.signedIn) { state.signedIn?.let(signedIn) }
    // B1 owns `register`: App.kt can't see the step (again on every step change).
    TrackScreenView(TrackScreen.Register, "kind" to invite.trackKind, "step" to state.step, step = state.step)
    val back = { if (viewModel.back()) onExit() }
    BackHandler(onBack = back)
    RegisterScreenContent(
        state,
        RegisterActions(
            onFirstName = viewModel::onFirstName,
            onLastName = viewModel::onLastName,
            onEmail = viewModel::onEmail,
            onPhone = viewModel::onPhone,
            onPassword = viewModel::onPassword,
            onConfirm = viewModel::onConfirm,
            togglePassword = viewModel::togglePassword,
            toggleConfirm = viewModel::toggleConfirm,
            next = viewModel::next,
            register = viewModel::register,
            back = back,
            dismissBanner = viewModel::dismissBanner,
            openPicker = viewModel::openPicker,
            closePicker = viewModel::closePicker,
            onQuery = viewModel::onQuery,
            retryPicker = { viewModel.retryPicker() },
            pickTimeZone = viewModel::pickTimeZone,
            pickClinic = viewModel::pickClinic,
            toggleProfession = viewModel::toggleProfession,
            clearProfessions = viewModel::clearProfessions,
            openDob = viewModel::openDob,
            pickDob = viewModel::pickDob,
            toggleGender = viewModel::toggleGender,
            pickGender = viewModel::pickGender,
            onAddress = viewModel::onAddress,
            keepEditing = viewModel::keepEditing,
            discard = {
                viewModel.discard()
                onExit()
            },
            onCode = viewModel::onCode,
            verifyCode = viewModel::verifyCode,
            resendCode = viewModel::resendCode,
            closeCode = viewModel::closeCode,
            onCurrentPassword = viewModel::onCurrentPassword,
        ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreenContent(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    // The board top-aligns the long form and step 2 (`justify: flex-start`); only the short confirmation is centred.
    AuthFrame(
        onBack = actions.back,
        centered = state.step == RegisterStep.ExistingPatient,
        banner = codeBanner(state.banner, actions.dismissBanner),
    ) {
        AuthTitle(
            tr(if (state.step == RegisterStep.Details) Res.string.accountInformation else Res.string.createAccount),
        )
        when (state.step) {
            RegisterStep.Form -> FormStep(state, actions)
            RegisterStep.ExistingPatient -> ExistingPatientStep(state, actions)
            RegisterStep.Details -> DetailsStep(state, actions)
            RegisterStep.Staff -> StaffStep(state, actions)
        }
    }
    state.picker?.let { picker ->
        ShifaSheetHost(actions.closePicker) {
            when (picker) {
                RegisterPicker.TimeZone -> TimeZonePicker(state, actions)
                RegisterPicker.Clinic -> ClinicPicker(state, actions)
                RegisterPicker.Profession -> ProfessionPicker(state, actions)
            }
        }
    }
    if (state.pickingDob) DobPicker(state.dob, actions.pickDob)
    state.code?.let { code ->
        ShifaSheetHost(actions.closeCode) { RegistrationCodeSheet(code, state.loading, actions) }
    }
    if (state.askDiscard) {
        ShifaSheetHost(actions.keepEditing) {
            ShifaDialogSheet(
                tr(Res.string.discardRegistrationTitle),
                tr(Res.string.discard),
                actions.discard,
                body = tr(Res.string.discardRegistrationBody),
                tone = DialogTone.Destructive,
                icon = Res.drawable.ic_trash_22,
                cancelText = tr(Res.string.keepEditing),
                onCancel = actions.keepEditing,
            )
        }
    }
}

/** The shared practice-timezone list (PROF-06): the detected zone first, the short list, or every match. */
@Composable
internal fun TimeZonePicker(
    state: RegisterUiState,
    actions: RegisterActions,
) = TimeZonePickerSheet(
    state.query,
    actions.onQuery,
    state.timeZoneOptions,
    state.timeZone,
    state.detectedTimeZone,
    actions.pickTimeZone,
    actions.closePicker,
)

@Composable
private fun ColumnScope.FormStep(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    val editable = !state.loading
    ShifaTag(
        tr(Res.string.keyVerified),
        Modifier.align(Alignment.CenterHorizontally),
        type = ShifaTagType.Primary,
        icon = Res.drawable.ic_check_12,
    )
    TextField(
        state.firstName,
        actions.onFirstName,
        tr(Res.string.firstName),
        state.firstNameError,
        editable,
        words = true,
    )
    TextField(state.lastName, actions.onLastName, tr(Res.string.lastName), state.lastNameError, editable, words = true)
    TextField(
        state.email,
        actions.onEmail,
        tr(Res.string.email),
        state.emailError,
        editable && !state.emailLocked,
        KeyboardType.Email,
    )
    TextField(
        state.phone,
        actions.onPhone,
        "${tr(Res.string.phoneNumber)} (${tr(Res.string.optional)})",
        state.phoneError,
        editable,
        KeyboardType.Phone,
    )
    Passwords(state, actions, actions.next, editable)
    StepDots(0)
    ShifaPrimaryButton(tr(Res.string.next), actions.next, Modifier.fillMaxWidth(), loading = state.loading)
}

@Composable
private fun ColumnScope.ExistingPatientStep(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    ShifaAvatar(state.existing?.photoUrl, ShifaSize.S96, Modifier.align(Alignment.CenterHorizontally))
    Text(
        tr(Res.string.existingPatientCreatingDoctorAccount),
        Modifier
            .fillMaxWidth()
            .background(ShifaColors.Bg.SuccessSubtle, ShifaRadius.ControlMd)
            .border(ShifaSize.S1, ShifaColors.Border.Brand, ShifaRadius.ControlMd)
            .padding(horizontal = ShifaSpacing.Inset.Sm, vertical = ShifaSpacing.Inset.Xs),
        style = ShifaTextStyles.bodyText(),
        color = ShifaColors.Text.Primary,
    )
    TimeZoneField(state, actions, hint = null)
    Passwords(state, actions, actions.register, !state.loading)
    ShifaPrimaryButton(
        tr(Res.string.confirmRegistration),
        actions.register,
        Modifier.fillMaxWidth(),
        loading = state.loading,
        enabled = !state.completed,
    )
}

@Composable
internal fun TimeZoneField(
    state: RegisterUiState,
    actions: RegisterActions,
    hint: StringResource?,
) = ShifaPickField(
    tr(Res.string.practiceTimezone),
    state.timeZone.orEmpty(),
    tr(Res.string.practiceTimezonePlaceholder),
    { actions.openPicker(RegisterPicker.TimeZone) },
    Modifier.fillMaxWidth(),
    supportingText =
        state.timeZoneError?.let { tr(it) }
            ?: hint?.let { tr(it) },
    isError = state.timeZoneError != null,
    enabled = !state.loading,
)

@Composable
internal fun Passwords(
    state: RegisterUiState,
    actions: RegisterActions,
    submit: () -> Unit,
    editable: Boolean,
) {
    PasswordField(
        state.password,
        actions.onPassword,
        tr(Res.string.password),
        state.passwordVisible,
        actions.togglePassword,
        state.passwordError,
        KeyboardActions.Default,
        ImeAction.Next,
        enabled = editable,
    )
    PasswordChecklist(state.password)
    PasswordField(
        state.confirm,
        actions.onConfirm,
        tr(Res.string.confirmPassword),
        state.confirmVisible,
        actions.toggleConfirm,
        state.confirmError,
        KeyboardActions(onDone = { submit() }),
        ImeAction.Done,
        enabled = editable,
    )
}

@Composable
internal fun TextField(
    value: String,
    onValue: (String) -> Unit,
    label: String,
    error: StringResource?,
    enabled: Boolean,
    type: KeyboardType = KeyboardType.Text,
    words: Boolean = false,
) = ShifaInputField(
    value,
    onValue,
    label,
    Modifier.fillMaxWidth(),
    supportingText = error?.let { tr(it) },
    isError = error != null,
    enabled = enabled,
    keyboardOptions =
        KeyboardOptions(
            capitalization = if (words) KeyboardCapitalization.Words else KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = type,
            imeAction = ImeAction.Next,
        ),
)

/** The board's two progress dots (flow §3a: two steps, not Flutter's three); the active one is wide and teal. */
@Composable
internal fun ColumnScope.StepDots(current: Int) {
    val label = tr(Res.string.stepOfTotal, current + 1, STEPS)
    Row(
        Modifier.align(Alignment.CenterHorizontally).semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(ShifaSize.S8),
    ) {
        repeat(STEPS) {
            Box(
                Modifier
                    .height(ShifaSize.S8)
                    .width(if (it == current) ShifaSize.S24 else ShifaSize.S8)
                    .background(
                        if (it == current) ShifaColors.Interactive.Primary else ShifaColors.Bg.Disabled,
                        ShifaRadius.Full,
                    ),
            )
        }
    }
}

private const val STEPS = 2

/**
 * Flow 01/09 as a sheet (its target): the address, the code, "Resend code (59 s)", Verify. A wrong or expired code
 * stays here with the message under the field; resend outcomes show in the sheet, not behind it.
 */
@Composable
internal fun RegistrationCodeSheet(
    code: RegistrationCode,
    loading: Boolean,
    actions: RegisterActions,
) = ShifaTray(tr(Res.string.verifyYourEmail), actions.closeCode) {
    val busy = code.busy || loading
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ShifaSpacing.Inset.Md).padding(bottom = ShifaSpacing.Inset.Md),
        verticalArrangement = Arrangement.spacedBy(ShifaSpacing.Stack.Md),
    ) {
        codeBanner(code.banner)?.invoke()
        Text(
            tr(Res.string.otpSentToEmail, code.email),
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Secondary,
        )
        ShifaInputField(
            code.code,
            actions.onCode,
            tr(Res.string.otpCode),
            Modifier.fillMaxWidth(),
            supportingText = code.error?.let { tr(it) },
            isError = code.error != null,
            enabled = !busy,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { actions.verifyCode() }),
        )
        if (code.needsPassword) {
            var visible by rememberSaveable { mutableStateOf(false) }
            PasswordField(
                code.currentPassword,
                actions.onCurrentPassword,
                tr(Res.string.currentPassword),
                visible,
                { visible = !visible },
                null,
                KeyboardActions(onDone = { actions.verifyCode() }),
                ImeAction.Done,
                Modifier.fillMaxWidth(),
                enabled = !busy,
            )
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            ShifaLink(
                if (code.resendIn > 0) {
                    tr(
                        Res.string.resendCodeIn,
                        code.resendIn.toString(),
                    )
                } else {
                    tr(Res.string.resendCode)
                },
                actions.resendCode,
                enabled = code.resendIn == 0 && !busy,
            )
        }
        ShifaPrimaryButton(tr(Res.string.verify), actions.verifyCode, Modifier.fillMaxWidth(), loading = busy)
    }
}
