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
    private var now = 1_800_000_000L
    private val valid = jwt(now + 90L * 24 * 3600)

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
    fun `an expired token on a cold start says why`() {
        tokens.token = jwt(now - 31)
        val r = repo()
        r.restore()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.Expired)
    }

    @Test
    fun `30 days without a touch ends the session on the next start, before the shell`() {
        val day = 24L * 3600
        repo().signIn(valid)
        now += 29 * day
        assertThat(repo().restore()).isNotNull() // 29 days: fine, and opening counts as activity
        now += 30 * day
        val r = repo()
        assertThat(r.restore()).isNull()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.Inactivity)
        assertThat(tokens.token).isNull()
    }

    @Test
    fun `touches keep the session alive, writes stay rare, coming back after 30 days ends it`() {
        val r = repo()
        r.signIn(valid)
        val first = plain.getLongOrNull(SessionRepository.KEY_LAST_ACTIVITY)
        now += 60
        r.touch()
        assertThat(plain.getLongOrNull(SessionRepository.KEY_LAST_ACTIVITY)).isEqualTo(first) // under 5 minutes
        now += 5 * 60
        r.touch()
        assertThat(plain.getLongOrNull(SessionRepository.KEY_LAST_ACTIVITY)).isEqualTo(now)
        now += 29L * 24 * 3600
        r.enforce()
        assertThat(r.session.value).isNotNull()
        now += 2L * 24 * 3600
        r.enforce()
        assertThat(r.session.value).isNull()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.Inactivity)
    }

    @Test
    fun `a touch with an expired token ends the session at once, unreadable activity never does`() {
        val r = repo()
        r.signIn(jwt(now + 100))
        plain.remove(SessionRepository.KEY_LAST_ACTIVITY)
        now += 200
        r.touch()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.Expired)
        val fresh = repo()
        tokens.token = valid
        plain.remove(SessionRepository.KEY_LAST_ACTIVITY)
        assertThat(fresh.restore()).isNotNull() // no recorded activity: no timeout (fail open)
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
        secure.putString("app_lock_pin_credential", "x")

        SessionEvents.end(SessionEndReason.AccountDisabled, valid)

        assertThat(r.session.value).isNull()
        assertThat(tokens.token).isNull()
        assertThat(plain.hasKey(SessionRepository.KEY_LAST_ACTIVITY)).isFalse()
        assertThat(plain.getStringOrNull("doctor_language")).isEqualTo("ru")
        assertThat(secure.hasKey("app_lock_pin_credential")).isTrue()
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

    @Test
    fun `coming back counts as activity, a late tap can't erase an overdue deadline`() {
        val day = 24L * 3600
        val r = repo()
        r.signIn(valid)
        now += 29 * day
        r.onForeground() // day 29: stays, and counts
        now += 2 * day
        r.onForeground() // day 31, but only 2 days since the last return
        assertThat(r.session.value).isNotNull()
        now += 31 * day
        r.touch() // left open 31 days: the tap ends it instead of refreshing it
        assertThat(r.session.value).isNull()
        assertThat(r.ended.replayCache).containsExactly(SessionEndReason.Inactivity)
    }
}
