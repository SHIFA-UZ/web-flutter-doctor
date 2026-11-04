package shifa.doctor.data.session

import com.google.common.truth.Truth.assertThat
import com.russhwolf.settings.MapSettings
import org.junit.Test
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.SessionEvents
import shifa.doctor.data.api.jwt
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.storage.TokenStorage

class SessionRepositoryTest {
    private val secure = MapSettings()
    private val plain = MapSettings()
    private val tokens = TokenStorage(secure)
    private val now = 1_800_000_000L
    private val valid = jwt(now + 3600)

    private fun repo() =
        SessionRepository(
            tokens,
            plain,
            LanguageRepository { error("no network in this test") },
            { now },
        )

    @Test
    fun `restore keeps a valid doctor token and needs no network`() {
        tokens.token = valid
        assertThat(repo().restore()).isEqualTo(Session(valid, "DOCTOR"))
    }

    @Test
    fun `restore drops an expired token, an admin token and garbage`() {
        listOf(jwt(now - 31), jwt(now + 3600, role = "ADMIN"), "garbage").forEach { token ->
            tokens.token = token
            assertThat(repo().restore()).isNull()
            assertThat(tokens.token).isNull()
        }
    }

    @Test
    fun `sign-in saves the token, clinic staff is recognised`() {
        val staff = jwt(now + 3600, role = "CLINIC_STAFF")
        val r = repo()
        assertThat(r.signIn(staff).getOrNull()?.isClinicStaff).isTrue()
        assertThat(tokens.token).isEqualTo(staff)
        assertThat(r.signIn(jwt(now - 31)).isFailure).isTrue()
    }

    @Test
    fun `a network session end clears only the token and last activity, and says why`() {
        val r = repo()
        r.signIn(valid)
        plain.putLong(SessionRepository.KEY_LAST_ACTIVITY, 1L)
        plain.putString("doctor_language", "ru")
        secure.putString("app_lock_pin_hash", "x")

        SessionEvents.end(SessionEndReason.AccountDisabled, valid)

        assertThat(r.session.value).isNull()
        assertThat(tokens.token).isNull()
        assertThat(plain.hasKey(SessionRepository.KEY_LAST_ACTIVITY)).isFalse()
        assertThat(plain.getStringOrNull("doctor_language")).isEqualTo("ru")
        assertThat(secure.hasKey("app_lock_pin_hash")).isTrue()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.AccountDisabled)
    }

    @Test
    fun `sign-out says so once, a later report about that token is ignored`() {
        val r = repo()
        r.signIn(valid)
        r.signOut()
        r.signOut()
        SessionEvents.end(SessionEndReason.Unauthorized, valid)
        assertThat(r.session.value).isNull()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.SignedOut)
    }

    @Test
    fun `a late 401 from the previous session does not end the new one`() {
        val r = repo()
        r.signIn(valid)
        val next = jwt(now + 7200, role = "CLINIC_STAFF")
        r.signIn(next)
        SessionEvents.end(SessionEndReason.Unauthorized, valid)
        assertThat(r.session.value?.token).isEqualTo(next)
        assertThat(tokens.token).isEqualTo(next)
    }
}
