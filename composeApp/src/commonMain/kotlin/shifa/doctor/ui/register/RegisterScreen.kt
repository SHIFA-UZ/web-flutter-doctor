package shifa.doctor.ui.register

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.auth.Invite
import shifa.doctor.data.session.Session
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.confirmPassword
import shifa.doctor.generated.resources.confirmRegistration
import shifa.doctor.generated.resources.createAccount
import shifa.doctor.generated.resources.detectedOnThisDevice
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
import shifa.doctor.generated.resources.password
import shifa.doctor.generated.resources.phoneNumber
import shifa.doctor.generated.resources.practiceTimezone
import shifa.doctor.generated.resources.practiceTimezonePlaceholder
import shifa.doctor.generated.resources.searchTimezone
import shifa.doctor.generated.resources.stepOfTotal
import shifa.doctor.generated.resources.timezoneScheduleHint
import shifa.doctor.ui.auth.AuthFrame
import shifa.doctor.ui.auth.AuthTitle
import shifa.doctor.ui.auth.PasswordChecklist
import shifa.doctor.ui.auth.PasswordField
import shifa.doctor.ui.emailcode.codeBanner
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaRadius
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.DialogTone
import shifa.doctor.ui.widgets.OptionItem
import shifa.doctor.ui.widgets.ShifaAvatar
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaPickField
import shifa.doctor.ui.widgets.ShifaPickerSheet
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.ShifaTag
import shifa.doctor.ui.widgets.ShifaTagType
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
    val openTimeZones: () -> Unit = {},
    val closeTimeZones: () -> Unit = {},
    val onTimeZoneQuery: (String) -> Unit = {},
    val pickTimeZone: (String) -> Unit = {},
    val keepEditing: () -> Unit = {},
    val discard: () -> Unit = {},
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
    val back = { if (viewModel.back()) onExit() }
    BackHandler(onBack = back)
    RegisterScreenContent(
        state,
        RegisterActions(
            viewModel::onFirstName,
            viewModel::onLastName,
            viewModel::onEmail,
            viewModel::onPhone,
            viewModel::onPassword,
            viewModel::onConfirm,
            viewModel::togglePassword,
            viewModel::toggleConfirm,
            viewModel::next,
            viewModel::register,
            back,
            viewModel::dismissBanner,
            viewModel::openTimeZones,
            viewModel::closeTimeZones,
            viewModel::onTimeZoneQuery,
            viewModel::pickTimeZone,
            viewModel::keepEditing,
            onExit,
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
        AuthTitle(tr(Res.string.createAccount))
        when (state.step) {
            RegisterStep.Form -> FormStep(state, actions)
            RegisterStep.ExistingPatient -> ExistingPatientStep(state, actions)
            RegisterStep.Details -> DetailsStep(state, actions)
        }
    }
    if (state.pickingTimeZone) {
        ModalBottomSheet(
            actions.closeTimeZones,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RectangleShape,
            containerColor = Color.Transparent,
            dragHandle = null,
        ) { TimeZonePicker(state, actions) }
    }
    if (state.askDiscard) {
        ModalBottomSheet(
            actions.keepEditing,
            shape = RectangleShape,
            containerColor = Color.Transparent,
            dragHandle = null,
        ) {
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

/** The picker's list: the detected zone first (marked), the board's short list, or every match while typing. */
@Composable
internal fun TimeZonePicker(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    val zones = state.timeZoneOptions
    val detected = tr(Res.string.detectedOnThisDevice)
    ShifaPickerSheet(
        tr(Res.string.practiceTimezone),
        state.timeZoneQuery,
        actions.onTimeZoneQuery,
        tr(Res.string.searchTimezone),
        zones.map {
            OptionItem(
                it,
                selected = it == state.timeZone,
                sub =
                    detected.takeIf {
                            _ ->
                        it == state.detectedTimeZone
                    },
            )
        },
        onPick = { actions.pickTimeZone(zones[it]) },
        onClose = actions.closeTimeZones,
    )
}

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
    TimeZoneField(state, actions, hint = false)
    Passwords(state, actions, actions.register, !state.loading)
    ShifaPrimaryButton(
        tr(Res.string.confirmRegistration),
        actions.register,
        Modifier.fillMaxWidth(),
        loading = state.loading,
    )
}

// Step 2 (flow 01/08). AUTH-07 adds date of birth, gender, address, clinic and professions above the timezone.
@Composable
private fun ColumnScope.DetailsStep(
    state: RegisterUiState,
    actions: RegisterActions,
) {
    TimeZoneField(state, actions, hint = true)
    StepDots(1)
    ShifaPrimaryButton(tr(Res.string.createAccount), actions.register, Modifier.fillMaxWidth(), loading = state.loading)
}

@Composable
private fun TimeZoneField(
    state: RegisterUiState,
    actions: RegisterActions,
    hint: Boolean,
) = ShifaPickField(
    tr(Res.string.practiceTimezone),
    state.timeZone.orEmpty(),
    tr(Res.string.practiceTimezonePlaceholder),
    actions.openTimeZones,
    Modifier.fillMaxWidth(),
    supportingText =
        state.timeZoneError?.let { tr(it) }
            ?: tr(Res.string.timezoneScheduleHint).takeIf { hint },
    isError = state.timeZoneError != null,
    enabled = !state.loading,
)

@Composable
private fun Passwords(
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
private fun TextField(
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
private fun ColumnScope.StepDots(current: Int) {
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
