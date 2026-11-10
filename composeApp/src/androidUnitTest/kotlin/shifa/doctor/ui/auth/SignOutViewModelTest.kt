package shifa.doctor.ui.auth

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

class SignOutViewModelTest {
    private val tokens = TokenStorage(MapSettings())
    private val sessions = SessionRepository(tokens, MapSettings(), LanguageRepository { error("unused") })

    @Test
    fun `the question mentions the PIN only when one is set, Cancel keeps the session`() {
        sessions.signIn(jwt(4_000_000_000L))
        val classToTest = SignOutViewModel(sessions, pinRepo(true))
        classToTest.ask()
        assertThat(classToTest.state.value).isEqualTo(SignOutUiState(asking = true, pinSet = true))
        classToTest.cancel()
        assertThat(classToTest.state.value.asking).isFalse()
        assertThat(sessions.session.value).isNotNull()
        classToTest.confirm() // no question open: nothing happens
        assertThat(sessions.session.value).isNotNull()
    }

    @Test
    fun `confirming ends the session once, clears the stored token and tells Sign In why`() {
        sessions.signIn(jwt(4_000_000_000L))
        val classToTest = SignOutViewModel(sessions, pinRepo(false))
        classToTest.ask()
        classToTest.confirm()
        classToTest.confirm()
        assertThat(sessions.session.value).isNull()
        assertThat(tokens.token).isNull()
        assertThat(sessions.ended.replayCache).containsExactly(SessionEndReason.SignedOut)
    }

    private fun pinRepo(pin: Boolean) =
        AppLockStorage(MapSettings()).let { store ->
            if (pin) store.setPin("123456")
            AppLockRepository(storage = { store })
        }

    @Test
    fun `cancelling the question is tracked once`() {
        val tracker = RecordingTracker()
        val classToTest = SignOutViewModel(sessions, pinRepo(false), tracker)
        classToTest.ask()
        classToTest.cancel()
        classToTest.cancel() // a second dismissal of a closed sheet
        assertThat(tracker.events).containsExactly(AuthEvents.SignOutCancelled)
    }
}
