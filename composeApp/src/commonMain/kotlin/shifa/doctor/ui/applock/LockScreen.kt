package shifa.doctor.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
import shifa.doctor.generated.resources.attemptsLeft
import shifa.doctor.generated.resources.delete
import shifa.doctor.generated.resources.enterPin
import shifa.doctor.generated.resources.ic_fingerprint_22
import shifa.doctor.generated.resources.incorrectPin
import shifa.doctor.generated.resources.pinDigitsEntered
import shifa.doctor.generated.resources.signOutInstead
import shifa.doctor.generated.resources.unlock
import shifa.doctor.generated.resources.unlockShifaDoctor
import shifa.doctor.generated.resources.useBiometrics
import shifa.doctor.generated.resources.usePin
import shifa.doctor.ui.auth.AuthLogo
import shifa.doctor.ui.auth.SignOutSheet
import shifa.doctor.ui.auth.SignOutViewModel
import shifa.doctor.ui.theme.ShifaColors
import shifa.doctor.ui.theme.ShifaSize
import shifa.doctor.ui.theme.ShifaSpacing
import shifa.doctor.ui.theme.ShifaTextStyles
import shifa.doctor.ui.widgets.PinDots
import shifa.doctor.ui.widgets.PinPad
import shifa.doctor.ui.widgets.ShifaLink
import shifa.doctor.ui.widgets.ShifaPrimaryButton
import shifa.doctor.ui.widgets.trackActivity
import shifa.doctor.utils.rememberBiometrics
import shifa.doctor.utils.tr
import shifa.doctor.utils.trPlural

data class LockUiState(
    val entry: String = "",
    val wrong: Boolean = false,
    val attemptsLeft: Int? = null,
    val biometric: Boolean = false,
)

/**
 * LOCK-01 (flow 01/12, DaApp lock): 4–6 digits submitted with "Unlock" (D-09: never checked mid-typing, which made
 * Flutter's 5- and 6-digit PINs unusable); a wrong PIN clears the dots and says how many tries are left; the fifth
 * ends the session with its own banner.
 */
class LockViewModel(
    private val lock: AppLockRepository = appLockRepository,
    private val sessions: SessionRepository = sessionRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow(LockUiState(biometric = lock.biometricEnabled))
    val state: StateFlow<LockUiState> = _state.asStateFlow()

    fun onDigit(d: Char) =
        _state.update { it.copy(entry = (it.entry + d).take(AppLockRepository.MAX_LENGTH), wrong = false) }

    fun submit() {
        val entry = _state.value.entry
        if (entry.length < AppLockRepository.MIN_LENGTH) return
        when (val result = lock.check(entry)) {
            PinCheck.Unlocked -> {
                tracker.track(AuthEvents.AppUnlocked("pin"))
                _state.update { LockUiState(biometric = it.biometric) }
            }
            is PinCheck.Wrong -> {
                tracker.track(AuthEvents.AppLockPinFailed(SOURCE, result.attemptsLeft))
                _state.update { it.copy(entry = "", wrong = true, attemptsLeft = result.attemptsLeft) }
            }
            PinCheck.LockedOut -> {
                tracker.track(AuthEvents.AppLockLockedOut(SOURCE))
                sessions.session.value?.token?.let { sessions.end(SessionEndReason.PinLockout, it) }
            }
        }
    }

    fun onBackspace() = _state.update { it.copy(entry = it.entry.dropLast(1), wrong = false) }

    fun onBiometricSuccess() {
        lock.unlockWithBiometrics()
        tracker.track(AuthEvents.AppUnlocked("biometric"))
    }

    private companion object {
        const val SOURCE = "lock_screen"
    }
}

/**
 * The lock over the whole app, in its own full-screen window so it also covers any sheet or dialog that was open
 * (they are windows too); Back does nothing here (flow §7).
 */
@Composable
fun LockScreen(viewModel: LockViewModel = viewModel { LockViewModel() }) =
    Dialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                // ponytail: Android dims the system bars around a dialog window; CMP 1.9's common DialogProperties has no
                // scrim or inset switch. Cosmetic; revisit with a newer CMP.
            ),
    ) { LockScreenWindow(viewModel) }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun LockScreenWindow(viewModel: LockViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signOut = viewModel { SignOutViewModel() }
    val question by signOut.state.collectAsStateWithLifecycle()
    val biometrics = rememberBiometrics()
    val haptics = LocalHapticFeedback.current
    val title = tr(Res.string.unlockShifaDoctor)
    val usePin = tr(Res.string.usePin)
    val bio = state.biometric && biometrics.available

    suspend fun prompt() {
        if (biometrics.authenticate(title, usePin)) viewModel.onBiometricSuccess()
    }
    BackHandler {}
    // The prompt opens by itself when the lock appears and again each time the app comes back (flow §7 target).
    LaunchedEffect(bio) { if (bio) prompt() }
    var resumed by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumed++ }
    LaunchedEffect(resumed) { if (bio && resumed > 1) prompt() }
    LaunchedEffect(state.wrong) { if (state.wrong) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
    var biometricTap by remember { mutableIntStateOf(0) }
    LaunchedEffect(biometricTap) { if (biometricTap > 0) prompt() }
    LockScreenContent(
        state,
        viewModel::onDigit,
        viewModel::onBackspace,
        viewModel::submit,
        onBiometric = if (bio) ({ biometricTap++ }) else null,
        onSignOut = signOut::ask,
    )
    SignOutSheet(question, signOut::confirm, signOut::cancel)
}

@Composable
fun LockScreenContent(
    state: LockUiState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onSubmit: () -> Unit,
    onBiometric: (() -> Unit)?,
    onSignOut: () -> Unit,
) {
    val digits = tr(Res.string.pinDigitsEntered, state.entry.length, AppLockRepository.MAX_LENGTH)
    Column(
        Modifier
            .fillMaxSize()
            .trackActivity()
            .background(ShifaColors.Bg.Canvas)
            // Nothing behind the lock can be touched.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .safeDrawingPadding()
            .padding(ShifaSpacing.Inset.Lg),
        verticalArrangement = Arrangement.spacedBy(ShifaSize.S20, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AuthLogo(ShifaSize.S64)
        Text(tr(Res.string.enterPin), style = MaterialTheme.typography.titleMedium, color = ShifaColors.Text.Primary)
        PinDots(state.entry.length, AppLockRepository.MAX_LENGTH, Modifier.semantics { contentDescription = digits })
        // DaApp: "Incorrect PIN. 4 attempts left."
        val left = state.attemptsLeft
        Text(
            if (!state.wrong) {
                ""
            } else {
                listOfNotNull(
                    tr(Res.string.incorrectPin),
                    left?.let {
                        trPlural(Res.plurals.attemptsLeft, it, it)
                    },
                ).joinToString(". ", postfix = ".")
            },
            Modifier.heightIn(min = ShifaSize.S20).semantics { liveRegion = LiveRegionMode.Polite },
            style = ShifaTextStyles.dialogBody(),
            color = ShifaColors.Text.Error,
            textAlign = TextAlign.Center,
        )
        PinPad(
            onDigit,
            onBackspace,
            tr(Res.string.delete),
            leftKey = if (onBiometric != null) Res.drawable.ic_fingerprint_22 else null,
            leftKeyDescription = tr(Res.string.useBiometrics),
            onLeftKey = onBiometric ?: {},
        )
        ShifaPrimaryButton(
            tr(Res.string.unlock),
            onSubmit,
            Modifier.widthIn(max = ShifaSize.S240).fillMaxWidth(),
            enabled = state.entry.length >= AppLockRepository.MIN_LENGTH,
        )
        ShifaLink(tr(Res.string.signOutInstead), onSignOut)
    }
}
