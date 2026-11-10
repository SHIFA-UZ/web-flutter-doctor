package shifa.doctor.ui.applock

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import org.junit.Test
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.jwt
import shifa.doctor.data.applock.AppLockRepository
import shifa.doctor.data.session.SessionRepository
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.AppLockStorage
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.data.tracking.RecordingTracker
import shifa.doctor.data.tracking.events.AuthEvents
import shifa.doctor.generated.resources.Res
import shifa.doctor.generated.resources.incorrectPin
import shifa.doctor.generated.resources.pinsDontMatch

class AppLockViewModelsTest {
    private val sessions =
        SessionRepository(TokenStorage(MapSettings()), MapSettings(), LanguageRepository { error("unused") })
    private val store = AppLockStorage(MapSettings())
    private val lock = AppLockRepository({ store })

    private fun LockViewModel.type(pin: String) {
        pin.forEach(::onDigit)
        submit()
    }

    @Test
    fun `the lock checks only on Unlock, from four digits, and says how many tries are left`() {
        lock.turnOn("12345")
        lock.onSessionStarted()
        val classToTest = LockViewModel(lock, sessions)
        "123".forEach(classToTest::onDigit)
        classToTest.submit() // three digits: nothing to check
        assertThat(classToTest.state.value.entry).isEqualTo("123")
        "4567890".forEach(classToTest::onDigit)
        assertThat(classToTest.state.value.entry).isEqualTo("123456") // at most six
        classToTest.submit()
        assertThat(classToTest.state.value).isEqualTo(LockUiState(wrong = true, attemptsLeft = 4))
        classToTest.type("12345")
        assertThat(lock.locked.value).isFalse()
    }

    @Test
    fun `the fifth wrong PIN ends the session with its own reason`() {
        sessions.signIn(jwt(4_000_000_000L))
        lock.turnOn("123456")
        lock.onSessionStarted()
        val classToTest = LockViewModel(lock, sessions)
        repeat(5) { classToTest.type("000000") }
        assertThat(sessions.session.value).isNull()
        assertThat(sessions.ended.replayCache).containsExactly(SessionEndReason.PinLockout)
    }

    @Test
    fun `setting a PIN asks twice and starts again on a mismatch`() {
        val classToTest = AppLockSettingsViewModel(lock, sessions)
        classToTest.toggleLock()
        classToTest.onPin("12a3٤456789")
        assertThat(classToTest.state.value.sheet?.value).isEqualTo("123456")
        classToTest.submit()
        classToTest.onPin("654321")
        classToTest.submit()
        assertThat(
            classToTest.state.value.sheet,
        ).isEqualTo(PinSheet(PinSheetMode.Set, error = Res.string.pinsDontMatch))
        classToTest.onPin("123456")
        classToTest.submit()
        classToTest.onPin("123456")
        classToTest.submit()
        assertThat(classToTest.state.value).isEqualTo(AppLockSettingsUiState(enabled = true))
    }

    @Test
    fun `turning off or changing the PIN needs the current one`() {
        lock.turnOn("123456")
        val classToTest = AppLockSettingsViewModel(lock, sessions)
        classToTest.changePin()
        classToTest.onPin("111111")
        classToTest.submit()
        assertThat(classToTest.state.value.sheet?.error).isEqualTo(Res.string.incorrectPin)
        classToTest.onPin("123456")
        classToTest.submit()
        assertThat(classToTest.state.value.sheet).isEqualTo(PinSheet(PinSheetMode.Set))
        assertThat(lock.enabled).isTrue() // checking for a change keeps the lock on
        classToTest.closeSheet()
        classToTest.toggleLock()
        classToTest.onPin("123456")
        classToTest.submit()
        assertThat(classToTest.state.value).isEqualTo(AppLockSettingsUiState(enabled = false))
    }

    @Test
    fun `unlocking and the lockout are tracked by source`() {
        sessions.signIn(jwt(4_000_000_000L))
        lock.turnOn("123456")
        lock.onSessionStarted()
        val tracker = RecordingTracker()
        val classToTest = LockViewModel(lock, sessions, tracker)
        classToTest.type("000000")
        classToTest.type("123456")
        classToTest.onBiometricSuccess()
        repeat(5) { classToTest.type("000000") }
        assertThat(tracker.events)
            .containsExactly(
                AuthEvents.AppLockPinFailed("lock_screen", 4),
                AuthEvents.AppUnlocked("pin"),
                AuthEvents.AppUnlocked("biometric"),
                AuthEvents.AppLockPinFailed("lock_screen", 4),
                AuthEvents.AppLockPinFailed("lock_screen", 3),
                AuthEvents.AppLockPinFailed("lock_screen", 2),
                AuthEvents.AppLockPinFailed("lock_screen", 1),
                AuthEvents.AppLockLockedOut("lock_screen"),
            ).inOrder()
    }

    @Test
    fun `turning the lock on, changing the PIN and turning it off are tracked`() {
        val tracker = RecordingTracker()
        val classToTest = AppLockSettingsViewModel(lock, sessions, tracker)
        classToTest.toggleLock()
        classToTest.onPin("123456")
        classToTest.submit()
        classToTest.onPin("654321")
        classToTest.submit() // mismatch
        classToTest.onPin("123456")
        classToTest.submit()
        classToTest.onPin("123456")
        classToTest.submit()
        classToTest.changePin()
        classToTest.onPin("123456")
        classToTest.submit()
        classToTest.closeSheet()
        classToTest.setBiometric(true)
        classToTest.toggleLock()
        classToTest.onPin("123456")
        classToTest.submit()
        assertThat(tracker.events)
            .containsExactly(
                AuthEvents.AppLockChangeFailed("enable", "mismatch"),
                AuthEvents.AppLockChanged("enable"),
                AuthEvents.AppLockChangeCancelled("change_pin"),
                AuthEvents.AppLockBiometricSet(isOn = true),
                AuthEvents.AppLockChanged("disable"),
            ).inOrder()
    }
}
