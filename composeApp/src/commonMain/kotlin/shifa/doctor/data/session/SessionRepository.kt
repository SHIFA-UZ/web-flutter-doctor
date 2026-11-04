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

    // LOCK-02 (01 §7.2): the last touch, in memory, and when it was last written to the store (at most every 5 min).
    private var lastActivity: Long? = null
    private var lastSaved: Long? = null

    init {
        SessionEvents.listener = ::end
    }

    /**
     * Cold start (01 §2.9): the saved token, if it is still valid and a doctor-app role. No network call. A store
     * that can't be read counts as signed out.
     */
    fun restore(): Session? =
        synchronized(this) {
            val token = runCatching { tokens.token }.getOrNull()
            val session = token?.let(::sessionOf)
            val now = nowEpochSeconds()
            // Decided here, before the shell is shown, so no patient data flashes (flow 01/13 §5 target).
            val reason =
                when {
                    token != null &&
                        Jwt.isExpired(
                            token,
                            now,
                            ApiConfig.EXPIRY_GRACE_SECONDS,
                        )
                    -> SessionEndReason.Expired
                    session != null && inactive(now) -> SessionEndReason.Inactivity
                    else -> null
                }
            if (session == null || reason != null) {
                clear()
                reason?.let(_ended::tryEmit)
                return@synchronized null
            }
            _session.value = session
            record(now) // opening the app counts as activity
            session
        }

    /**
     * Any touch in the app (LOCK-02, flow 01/13 §2): remembers the time, written to the store at most every 5 minutes;
     * a token already past its expiry ends the session at once, before any request.
     */
    fun touch() =
        synchronized(this) {
            val session = _session.value ?: return@synchronized
            val now = nowEpochSeconds()
            // An overdue deadline is enforced first, so a late tap can't erase it.
            if (!expiredOrIdle(session, now)) record(now)
        }

    /**
     * The checks alone: an expired token or 30 days without a touch ends the session (01 §7.2). [onForeground] runs
     * them on coming back; the shell also runs them hourly while the app stays open (flow 01/13 §2 timer).
     */
    fun enforce() =
        synchronized(this) {
            val session = _session.value ?: return@synchronized
            expiredOrIdle(session, nowEpochSeconds())
        }

    /** Back in the foreground: the checks, and if the session stays, coming back counts as activity. */
    fun onForeground() =
        synchronized(this) {
            val session = _session.value ?: return@synchronized
            val now = nowEpochSeconds()
            if (!expiredOrIdle(session, now)) record(now)
        }

    // True when it ended the session.
    private fun expiredOrIdle(
        session: Session,
        now: Long,
    ): Boolean {
        val reason =
            when {
                Jwt.isExpired(session.token, now, ApiConfig.EXPIRY_GRACE_SECONDS) -> SessionEndReason.Expired
                inactive(now) -> SessionEndReason.Inactivity
                else -> return false
            }
        endNow(reason)
        return true
    }

    private fun record(now: Long) {
        lastActivity = now
        val saved = lastSaved
        if (saved == null || now - saved >= SAVE_EVERY_SECONDS) {
            runCatching { settings.putLong(KEY_LAST_ACTIVITY, now) }
            lastSaved = now
        }
    }

    // Memory first, then the store; nothing readable means no information, so no timeout (flow 01/13 §6: fail open).
    private fun inactive(now: Long): Boolean {
        val last = lastActivity ?: runCatching { settings.getLongOrNull(KEY_LAST_ACTIVITY) }.getOrNull() ?: return false
        return now - last >= INACTIVITY_SECONDS
    }

    private fun endNow(reason: SessionEndReason) {
        clear()
        _ended.tryEmit(reason)
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
                record(nowEpochSeconds())
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
        endNow(reason)
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
        lastActivity = null
        lastSaved = null
        language.reset()
        _session.value = null
    }

    companion object {
        /** LOCK-02's inactivity clock (01 §7.2), epoch seconds; cleared on logout. */
        const val KEY_LAST_ACTIVITY = "shifa_doctor_last_activity_s"

        /** Phones: 30 days without a touch (01 §7.2; the web's 1 hour is the Flutter web build's). */
        const val INACTIVITY_SECONDS = 30L * 24 * 60 * 60
        const val SAVE_EVERY_SECONDS = 5L * 60
    }
}

val sessionRepository by lazy { SessionRepository() }
