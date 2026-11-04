package shifa.doctor.data.session

import com.russhwolf.settings.Settings
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import shifa.doctor.data.api.ApiConfig
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.api.SessionEvents
import shifa.doctor.data.settings.LanguageRepository
import shifa.doctor.data.settings.languageRepository
import shifa.doctor.data.storage.TokenStorage
import shifa.doctor.data.storage.tokenStorage
import shifa.doctor.utils.Jwt
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** A signed-in doctor or clinic staff member. [role] decides the shell (01 §5.2). */
data class Session(
    val token: String,
    val role: String,
) {
    val isClinicStaff: Boolean get() = role == ROLE_CLINIC_STAFF
}

const val ROLE_DOCTOR = "DOCTOR"
const val ROLE_CLINIC_STAFF = "CLINIC_STAFF"

/**
 * The session (01 §2.9, §2.10, §4). [session] is null when signed out; the HTTP client takes its token from here, so
 * nothing is sent before this repository exists. A session ends in one place, [end]: the doctor's sign-out, or the
 * network ([SessionEvents], 17 §1) for the token it was about; the sign-in screen reads why from [ended]. Ending
 * clears only what 01 §4 marks "cleared on logout": the token and the last-activity time. The PIN, language, OTP
 * history and the active location stay. Every change runs under one lock: requests end sessions from any thread.
 */
@OptIn(ExperimentalTime::class)
class SessionRepository(
    private val tokens: TokenStorage = tokenStorage,
    private val settings: Settings = Settings(),
    private val language: LanguageRepository = languageRepository,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
) : SynchronizedObject() {
    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    private val _ended = MutableSharedFlow<SessionEndReason>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The last reason a session ended (sign-out included); the sign-in screen shows its banner once. */
    val ended: SharedFlow<SessionEndReason> = _ended.asSharedFlow()

    init {
        SessionEvents.listener = ::end
    }

    /**
     * Cold start (01 §2.9): the saved token, if it is still valid and a doctor-app role. No network call. A store
     * that can't be read counts as signed out.
     */
    fun restore(): Session? =
        synchronized(this) {
            val session = runCatching { tokens.token }.getOrNull()?.let(::sessionOf)
            if (session == null) clear()
            _session.value = session
            session
        }

    /**
     * After any successful sign-in, registration or password reset. Fails with [IllegalArgumentException] when the
     * token is expired or not a doctor-app role, or with the storage error when it can't be saved.
     */
    fun signIn(token: String): Result<Session> =
        synchronized(this) {
            val session =
                sessionOf(
                    token,
                ) ?: return@synchronized Result.failure(IllegalArgumentException("unusable token"))
            runCatching { tokens.token = token }.map {
                _ended.resetReplayCache()
                _session.value = session
                session
            }
        }

    /**
     * The doctor signed out (one dialog, one navigation: the shell reacts to [session] turning null; Sign In says "You've
     * signed out"). The app-lock PIN and the language stay (flow 08/15 §4). Unregistering the push token waits for the
     * Firebase cleanup (D-15, PUSH-01).
     */
    fun signOut() =
        synchronized(this) {
            if (_session.value == null) return@synchronized
            clear()
            _ended.tryEmit(SessionEndReason.SignedOut)
        }

    /** The network ended the session that sent [token]; a report about any other (earlier) session is ignored. */
    fun end(
        reason: SessionEndReason,
        token: String,
    ) = synchronized(this) {
        if (_session.value?.token != token) return@synchronized
        clear()
        _ended.tryEmit(reason)
    }

    private fun sessionOf(token: String): Session? {
        if (Jwt.isExpired(token, nowEpochSeconds(), ApiConfig.EXPIRY_GRACE_SECONDS)) return null
        val role = Jwt.role(token)?.takeIf { it == ROLE_DOCTOR || it == ROLE_CLINIC_STAFF } ?: return null
        return Session(token, role)
    }

    // Signed out even when the store fails to delete: the session is gone from memory either way.
    private fun clear() {
        runCatching { tokens.token = null }
        runCatching { settings.remove(KEY_LAST_ACTIVITY) }
        language.reset()
        _session.value = null
    }

    companion object {
        /** LOCK-02's inactivity clock (01 §7.2); cleared on logout. */
        const val KEY_LAST_ACTIVITY = "shifa_doctor_last_activity_ms"
    }
}

val sessionRepository by lazy { SessionRepository() }
