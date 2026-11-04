package shifa.doctor.data.applock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import shifa.doctor.data.storage.AppLockStorage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What a PIN check found (flow 01/12 §5). */
sealed interface PinCheck {
    data object Unlocked : PinCheck

    data class Wrong(
        val attemptsLeft: Int,
    ) : PinCheck

    /** The fifth wrong PIN: the PIN is forgotten and the session must end (flow 01/12 §8 target). */
    data object LockedOut : PinCheck
}

/**
 * The app lock (01 §7.1, LOCK-01): its settings in the secure store, and whether the app is locked now. It locks on a
 * cold start or a new session when a PIN is set and the lock is on, and when the app comes back after at least
 * [backgroundThreshold] in the background (measured on the monotonic clock, so a changed device clock can't skip it).
 * Features that leave the app on purpose (a picker, the camera) can [suppress] it for that time. Storage failures read
 * as "no lock": the documented fallback (flow 01/12 §6).
 */
class AppLockRepository(
    private val storage: () -> AppLockStorage = { AppLockStorage() },
    private val clock: TimeSource = TimeSource.Monotonic,
    private val backgroundThreshold: Duration = 5.seconds,
) {
    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var backgroundedAt: TimeMark? = null
    private var suppressed = 0

    val hasPin: Boolean get() = read { it.hasPin } ?: false

    val enabled: Boolean get() = read { it.lockEnabled && it.hasPin } ?: false

    val biometricEnabled: Boolean get() = enabled && read { it.biometricEnabled } == true

    /** A session started (restored on a cold start, or a new sign-in): lock if the lock is on. */
    fun onSessionStarted() {
        if (enabled) _locked.value = true
    }

    fun onBackground() {
        backgroundedAt = clock.markNow()
    }

    fun onForeground() {
        val away = backgroundedAt?.elapsedNow() ?: return
        backgroundedAt = null
        if (away >= backgroundThreshold && suppressed == 0 && enabled) _locked.value = true
    }

    /** The temporary-disable hook (01 §7.1): while any caller holds it, coming back doesn't lock. */
    fun suppress(on: Boolean) {
        suppressed = (suppressed + if (on) 1 else -1).coerceAtLeast(0)
    }

    /** The session ended: nothing is locked any more (Sign In is never behind the lock). */
    fun onSessionEnded() {
        _locked.value = false
    }

    /**
     * Checks [pin]. A store that can't be read or answer counts as the fifth wrong PIN: the documented fallback is a
     * sign-out (flow 01/12 §6), never a lock nobody can pass.
     */
    fun check(pin: String): PinCheck {
        val outcome =
            runCatching {
                val store = storage()
                if (store.verifyPin(pin)) return@runCatching PinCheck.Unlocked
                val left = AppLockStorage.MAX_ATTEMPTS - store.failedAttempts
                if (left > 0) PinCheck.Wrong(left) else PinCheck.LockedOut
            }.getOrDefault(PinCheck.LockedOut)
        if (outcome == PinCheck.LockedOut) {
            // Forget the PIN so the next sign-in isn't locked by a PIN nobody knows (flow 01/12 §8).
            read { it.clear() }
        }
        if (outcome !is PinCheck.Wrong) _locked.value = false
        return outcome
    }

    /** The system confirmed the face or finger. */
    fun unlockWithBiometrics() {
        _locked.value = false
    }

    /** Turns the lock on with a new PIN (also "Change PIN"); a failure means it couldn't be saved. */
    fun turnOn(pin: String): Result<Unit> =
        runCatching {
            require(isValidPin(pin)) { "4–6 digits" }
            val store = storage()
            check(store.setPin(pin)) { "not saved" }
            store.lockEnabled = true
        }

    /**
     * Turns the lock off after the current PIN (flow 01/11 §5). Wrong PINs count like on the lock screen, so five of
     * them anywhere end the session. A failure means the right PIN was given but the lock couldn't be removed.
     */
    fun turnOff(pin: String): Result<PinCheck> {
        val result = check(pin)
        if (result != PinCheck.Unlocked) return Result.success(result)
        return runCatching { storage().clear() }.map { result }
    }

    fun setBiometric(on: Boolean) {
        read { it.biometricEnabled = on && it.hasPin }
    }

    private fun <T> read(block: (AppLockStorage) -> T): T? = runCatching { block(storage()) }.getOrNull()

    companion object {
        /** D-09 (the user's call): 4–6 digits, the doctor's choice, submitted explicitly. ASCII digits only. */
        const val MIN_LENGTH = 4
        const val MAX_LENGTH = 6

        fun isValidPin(pin: String) = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }
    }
}

/** The app's one lock (like `sessionRepository`). */
val appLockRepository by lazy { AppLockRepository() }
