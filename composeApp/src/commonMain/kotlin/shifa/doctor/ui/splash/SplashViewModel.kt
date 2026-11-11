package shifa.doctor.ui.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import shifa.doctor.data.api.toFailureReason
import shifa.doctor.data.config.BootGate
import shifa.doctor.data.config.PublicConfigRepository
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.tracking.Tracker
import shifa.doctor.data.tracking.Tracking
import shifa.doctor.data.tracking.events.AuthEvents

sealed interface SplashUiState {
    /** The brand animation; the checks run underneath. */
    data object Loading : SplashUiState

    data class UpdateRequired(
        val storeUrl: String?,
    ) : SplashUiState

    data class Maintenance(
        val message: String?,
        val retrying: Boolean = false,
    ) : SplashUiState

    /** Done: [session] null means Login. */
    data class Finished(
        val session: Session?,
    ) : SplashUiState
}

/**
 * AUTH-01 (flow 01/01, 01 §2.9). The session restore (local) and the force-update / maintenance check (BE-04) run in
 * parallel with the animation; a returning doctor leaves when it ends ([skip]), or after [splashLimitMs] if it never
 * plays. The gate check never blocks launch for more than its own 5 s timeout.
 */
class SplashViewModel(
    private val bootGate: suspend (language: String) -> Result<BootGate> = PublicConfigRepository()::bootGate,
    private val sessions: SessionRepository = sessionRepository,
    private val language: () -> String = { languageStorage.language.value.backendCode },
    private val splashLimitMs: Long = SPLASH_LIMIT_MS,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
    private val _state = MutableStateFlow<SplashUiState>(SplashUiState.Loading)
    val state: StateFlow<SplashUiState> = _state.asStateFlow()

    // Declared before init: on Main.immediate, check() runs its first part inside the constructor.
    private val skipped = CompletableDeferred<Unit>()

    init {
        check(firstRun = true)
    }

    /** The animation's end, or a tap that skips what's left of it; never the checks (flow §7). */
    fun skip() {
        skipped.complete(Unit)
    }

    /** Maintenance → "Try again", and the return from the store after "Update". */
    fun retry() {
        val current = _state.value
        if (current is SplashUiState.Maintenance && current.retrying) return
        if (current is SplashUiState.Maintenance) _state.value = current.copy(retrying = true)
        check(firstRun = false)
    }

    private fun check(firstRun: Boolean) {
        viewModelScope.launch {
            val gate =
                coroutineScope {
                    // Launch is never blocked by the check: a failure or timeout counts as no gate (flow §6).
                    val gate =
                        async {
                            bootGate(language())
                                .onFailure { tracker.track(AuthEvents.BootGateFailed(it.toFailureReason())) }
                                .getOrDefault(BootGate.None)
                        }
                    if (firstRun) withTimeoutOrNull(splashLimitMs) { skipped.await() }
                    gate.await()
                }
            _state.value =
                when (gate) {
                    is BootGate.Update -> SplashUiState.UpdateRequired(gate.storeUrl)
                    is BootGate.Maintenance -> SplashUiState.Maintenance(gate.message)
                    BootGate.None -> SplashUiState.Finished(sessions.restore())
                }
            val shown = if (gate is BootGate.Update) "update" else "maintenance"
            if (gate != BootGate.None) tracker.track(AuthEvents.BootGateShown(shown, isRetry = !firstRun))
        }
    }
}

// The animation waits for the web's fonts and icons (resourcesReady), so the backstop is as long as index.html's.
private const val SPLASH_LIMIT_MS = 10_000L
