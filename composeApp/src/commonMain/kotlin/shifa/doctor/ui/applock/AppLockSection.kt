package shifa.doctor.ui.applock

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.compose.resources.StringResource
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.applock.AppLockRepository
import shifa.doctor.data.applock.PinCheck
import shifa.doctor.data.applock.appLockRepository
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.AuthEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.appLock
import shifa.doctor.generated.resources.appLockChangeFailed
import shifa.doctor.generated.resources.appLockSubtitle
import shifa.doctor.generated.resources.biometricUnlock
import shifa.doctor.generated.resources.biometricsNotSetUp
import shifa.doctor.generated.resources.cancel
import shifa.doctor.generated.resources.changePin
import shifa.doctor.generated.resources.`continue`
import shifa.doctor.generated.resources.enterCurrentPin
import shifa.doctor.generated.resources.incorrectPin
import shifa.doctor.generated.resources.next
import shifa.doctor.generated.resources.pin
import shifa.doctor.generated.resources.pinLengthHint
import shifa.doctor.generated.resources.pinNotSaved
import shifa.doctor.generated.resources.pinsDontMatch
import shifa.doctor.generated.resources.repeatPin
import shifa.doctor.generated.resources.setPin
import shifa.doctor.generated.resources.turnOn
import shifa.doctor.generated.resources.unlockShifaDoctor
import shifa.doctor.generated.resources.usePin
import shifa.doctor.ui.widgets.ShifaDialogSheet
import shifa.doctor.ui.widgets.ShifaInputField
import shifa.doctor.ui.widgets.ShifaSection
import shifa.doctor.ui.widgets.ShifaSettingsItem
import shifa.doctor.ui.widgets.ShifaSwitch
import shifa.doctor.utils.rememberBiometrics
import shifa.doctor.utils.tr

/**
 * The PIN sheet (DaProfile `pin`, but 4–6 digits per D-09 instead of the board's fixed 6): set a new PIN (twice),
 * turn the lock off, or check the PIN before changing it.
 */
enum class PinSheetMode { Set, Off, Verify }

data class PinSheet(
    val mode: PinSheetMode,
    val value: String = "",
    // Set: the first entry, waiting for the repeat.
    val first: String? = null,
    val error: StringResource? = null,
)

data class AppLockSettingsUiState(
    val enabled: Boolean = false,
    val biometric: Boolean = false,
    val sheet: PinSheet? = null,
)

/**
 * LOCK-01 settings (flow 01/11, DaProfile Security → App lock): the switch, Face ID / fingerprint, Change PIN. SET-03
 * puts this section on the Security screen; until then the shell stand-in shows it.
 */
class AppLockSettingsViewModel(
    private val lock: AppLockRepository = appLockRepository,
    private val sessions: SessionRepository = sessionRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppLockSettingsUiState> = _state.asStateFlow()

    private fun read(sheet: PinSheet? = null) = AppLockSettingsUiState(lock.enabled, lock.biometricEnabled, sheet)

    fun toggleLock() = open(if (_state.value.enabled) PinSheetMode.Off else PinSheetMode.Set)

    fun changePin() = open(PinSheetMode.Verify)

    private fun open(mode: PinSheetMode) = _state.update { it.copy(sheet = PinSheet(mode)) }

    fun closeSheet() {
        _state.value.sheet?.let { tracker.track(AuthEvents.AppLockChangeCancelled(action(it))) }
        _state.update { it.copy(sheet = null) }
    }

    // A Set sheet with the lock already on is the second half of Change PIN.
    private fun action(sheet: PinSheet) =
        when {
            sheet.mode == PinSheetMode.Off -> "disable"
            sheet.mode == PinSheetMode.Verify || _state.value.enabled -> "change_pin"
            else -> "enable"
        }

    fun onPin(v: String) =
        _state.update { s ->
            s.copy(
                sheet =
                    s.sheet?.copy(
                        value = v.filter { it in '0'..'9' }.take(AppLockRepository.MAX_LENGTH),
                        error = null,
                    ),
            )
        }

    fun submit() {
        val sheet = _state.value.sheet ?: return
        if (!AppLockRepository.isValidPin(sheet.value)) return
        when (sheet.mode) {
            PinSheetMode.Set ->
                when {
                    sheet.first == null ->
                        _state.update {
                            it.copy(
                                sheet = sheet.copy(first = sheet.value, value = ""),
                            )
                        }
                    sheet.first != sheet.value -> {
                        tracker.track(AuthEvents.AppLockChangeFailed(action(sheet), "mismatch"))
                        _state.update { it.copy(sheet = PinSheet(PinSheetMode.Set, error = Res.string.pinsDontMatch)) }
                    }
                    lock.turnOn(sheet.value).isSuccess -> {
                        tracker.track(AuthEvents.AppLockChanged(action(sheet)))
                        _state.value = read()
                    }
                    // The secure store refused: say so and let the doctor try again (flow 01/11 §6).
                    else -> {
                        tracker.track(AuthEvents.AppLockChangeFailed(action(sheet), "store_failed"))
                        _state.update {
                            it.copy(
                                sheet = PinSheet(PinSheetMode.Set, error = Res.string.pinNotSaved),
                            )
                        }
                    }
                }
            PinSheetMode.Off, PinSheetMode.Verify -> checkCurrent(sheet)
        }
    }

    private fun checkCurrent(sheet: PinSheet) {
        val outcome =
            if (sheet.mode == PinSheetMode.Off) lock.turnOff(sheet.value) else Result.success(lock.check(sheet.value))
        // The PIN was right but the lock couldn't be removed: keep the sheet open and say so.
        val result =
            outcome.getOrElse {
                tracker.track(AuthEvents.AppLockChangeFailed(action(sheet), "store_failed"))
                return _state.update {
                        s ->
                    s.copy(sheet = sheet.copy(value = "", error = Res.string.appLockChangeFailed))
                }
            }
        when (result) {
            PinCheck.Unlocked -> {
                // Change PIN goes on to the new PIN; its outcome is the Set sheet's.
                if (sheet.mode == PinSheetMode.Off) tracker.track(AuthEvents.AppLockChanged("disable"))
                _state.value = read(PinSheet(PinSheetMode.Set).takeIf { sheet.mode == PinSheetMode.Verify })
            }
            is PinCheck.Wrong -> {
                tracker.track(AuthEvents.AppLockPinFailed(SOURCE, result.attemptsLeft))
                _state.update { it.copy(sheet = sheet.copy(value = "", error = Res.string.incorrectPin)) }
            }
            // Five wrong PINs here count like on the lock screen.
            PinCheck.LockedOut -> {
                tracker.track(AuthEvents.AppLockLockedOut(SOURCE))
                _state.value = read()
                sessions.session.value?.token?.let { sessions.end(SessionEndReason.PinLockout, it) }
            }
        }
    }

    /** Turning biometrics on follows a successful system prompt (flow 01/11 §5 target). */
    fun setBiometric(on: Boolean) {
        lock.setBiometric(on)
        tracker.track(AuthEvents.AppLockBiometricSet(on))
        _state.value = read(_state.value.sheet)
    }

    private companion object {
        const val SOURCE = "settings"
    }
}

/** The App lock card: switch, biometric row (only with the lock on), Change PIN; and the PIN sheet when open. */
@Composable
fun AppLockSection(
    modifier: Modifier = Modifier,
    viewModel: AppLockSettingsViewModel = viewModel { AppLockSettingsViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val biometrics = rememberBiometrics()
    val title = tr(Res.string.unlockShifaDoctor)
    val usePin = tr(Res.string.usePin)
    var asks by remember { mutableIntStateOf(0) }
    LaunchedEffect(asks) { if (asks > 0 && biometrics.authenticate(title, usePin)) viewModel.setBiometric(true) }
    AppLockSectionContent(
        state,
        biometrics.available,
        modifier,
        onToggleLock = viewModel::toggleLock,
        onToggleBiometric = { on -> if (on) asks++ else viewModel.setBiometric(false) },
        onChangePin = viewModel::changePin,
    )
    state.sheet?.let { PinSheetHost(it, viewModel::onPin, viewModel::submit, viewModel::closeSheet) }
}

@Composable
fun AppLockSectionContent(
    state: AppLockSettingsUiState,
    biometricsAvailable: Boolean,
    modifier: Modifier = Modifier,
    onToggleLock: () -> Unit = {},
    onToggleBiometric: (Boolean) -> Unit = {},
    onChangePin: () -> Unit = {},
) = ShifaSection(tr(Res.string.appLock), modifier) {
    ShifaSettingsItem(
        tr(Res.string.appLock),
        onToggleLock,
        subtitle = tr(Res.string.appLockSubtitle),
        divider = state.enabled,
        trailing = { ShifaSwitch(state.enabled, { onToggleLock() }) },
    )
    if (state.enabled) {
        // No face or finger enrolled: the row says so instead of disappearing (flow 01/11 §6 target).
        ShifaSettingsItem(
            tr(Res.string.biometricUnlock),
            { if (biometricsAvailable) onToggleBiometric(!state.biometric) },
            subtitle = if (biometricsAvailable) null else tr(Res.string.biometricsNotSetUp),
            trailing = if (biometricsAvailable) ({ ShifaSwitch(state.biometric, onToggleBiometric) }) else null,
        )
        ShifaSettingsItem(tr(Res.string.changePin), onChangePin, divider = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PinSheetHost(
    sheet: PinSheet,
    onPin: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) = ModalBottomSheet(onClose, shape = RectangleShape, containerColor = Color.Transparent, dragHandle = null) {
    PinSheetContent(sheet, onPin, onSubmit, onClose)
}

@Composable
fun PinSheetContent(
    sheet: PinSheet,
    onPin: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val setting = sheet.mode == PinSheetMode.Set
    ShifaDialogSheet(
        tr(
            when {
                !setting -> Res.string.enterCurrentPin
                sheet.first == null -> Res.string.setPin
                else -> Res.string.repeatPin
            },
        ),
        tr(
            when {
                !setting -> Res.string.`continue`
                sheet.first == null -> Res.string.next
                else -> Res.string.turnOn
            },
        ),
        onSubmit,
        confirmEnabled = AppLockRepository.isValidPin(sheet.value),
        cancelText = tr(Res.string.cancel),
        onCancel = onClose,
    ) {
        ShifaInputField(
            sheet.value,
            onPin,
            tr(Res.string.pin),
            Modifier.fillMaxWidth(),
            // The length rule shows until there is an error to show instead.
            supportingText = tr(sheet.error ?: Res.string.pinLengthHint),
            isError = sheet.error != null,
            clearable = false,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            visualTransformation = PasswordVisualTransformation(),
        )
    }
}
