package shifa.doctor.data.tracking

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.clinics.ClinicsState
import shifa.doctor.data.clinics.clinicsRepository
import shifa.doctor.data.model.DoctorMe
import shifa.doctor.data.model.role
import shifa.doctor.data.profile.profileRepository
import shifa.doctor.data.session.ROLE_CLINIC_STAFF
import shifa.doctor.data.session.ROLE_DOCTOR
import shifa.doctor.data.session.Session
import shifa.doctor.data.session.sessionRepository
import shifa.doctor.data.storage.AppLanguage
import shifa.doctor.data.storage.languageStorage
import shifa.doctor.data.storage.tokenStorage
import shifa.doctor.data.subscription.FeatureGate
import shifa.doctor.data.tracking.events.SessionTrackEvents
import shifa.doctor.utils.Jwt

/**
 * A5 (docs/posthog/00-plan.md §2.3, D4): who is tracked, and the session's start and end. [run] is started once from
 * `App()`; the instance lives as long as the process, so composing `App()` again sends nothing twice.
 *
 * - `session_started` once per session, `restored = true` for the session saved at launch (sent at every launch with
 *   one, like the patient app's `session_restored`), false after a sign-in.
 * - `session_ended(reason)` for every [SessionEndReason], then [Tracking.reset]: the event still belongs to the user.
 * - `identify` (`doctor_<sub>` / `staff_<sub>`, the JWT subject) once a doctor's profile has loaded, at once for clinic
 *   staff (they have no doctor profile), and again whenever a person property changes or tracking turns back on.
 * - Only `DOCTOR` and `CLINIC_STAFF` sessions: any other role (an admin's) is never identified or tracked.
 */
class TrackingSession(
    private val session: StateFlow<Session?>,
    private val ended: SharedFlow<SessionEndReason>,
    private val profile: Flow<DoctorMe?>,
    private val zone: Flow<String?>,
    // The signed-in user's memberships once read, else null (never another user's).
    private val clinics: Flow<ClinicsState?>,
    private val language: Flow<AppLanguage>,
    // The token saved when the process started: a session with it was restored, any other is a new sign-in.
    private val launchToken: String?,
) {
    // The token `session_started` was sent for.
    private var started: String? = null

    suspend fun run(): Unit =
        coroutineScope {
            // A reason already in the replay was handled by an earlier run, or ended a session of another launch
            // before this one could watch.
            launch { ended.drop(ended.replayCache.size).collect(::end) }
            launch { session.collect { it?.let(::start) } }
            launch {
                combine(identity(), Tracking.status) { who, status -> who.takeIf { status.on } }
                    .distinctUntilChanged()
                    .collect { who -> who?.let { Tracking.identify(it.role, it.userId, it.properties) } }
            }
        }

    private fun start(session: Session) {
        if (session.token == started) return
        val role = trackingRole(session) ?: return
        started = session.token
        Tracking.track(SessionTrackEvents.SessionStarted(role, restored = session.token == launchToken))
    }

    private fun end(reason: SessionEndReason) {
        Tracking.track(SessionTrackEvents.SessionEnded(reason))
        Tracking.reset()
        started = null
    }

    private fun identity(): Flow<Identity?> =
        combine(session, profile, zone, clinics, language) { session, me, zone, clinics, language ->
            val role = session?.let(::trackingRole) ?: return@combine null
            val userId = Jwt.subject(session.token) ?: return@combine null
            if (role == TrackingRole.Doctor && me == null) return@combine null
            Identity(
                role,
                userId,
                mapOf(
                    "role" to role,
                    "tier" to FeatureGate.of(me?.subscription).tier,
                    "profession" to me?.profile?.profession,
                    "time_zone" to zone,
                    "language" to language.tag,
                    "has_clinic" to if (role == TrackingRole.Staff) true else clinics?.hasWorkspace,
                    "clinic_role" to clinics?.selected?.role,
                ),
            )
        }

    private data class Identity(
        val role: TrackingRole,
        val userId: String,
        val properties: Map<String, Any?>,
    )
}

private fun trackingRole(session: Session): TrackingRole? =
    when (session.role) {
        ROLE_DOCTOR -> TrackingRole.Doctor
        ROLE_CLINIC_STAFF -> TrackingRole.Staff
        else -> null
    }

val trackingSession by lazy {
    TrackingSession(
        session = sessionRepository.session,
        ended = sessionRepository.ended,
        profile = profileRepository.me,
        zone = profileRepository.zone,
        // Read again on every session change: the state may still hold the previous user's list.
        clinics =
            combine(sessionRepository.session, clinicsRepository.state) { _, state ->
                state.takeIf { it.known && clinicsRepository.isCurrent }
            },
        language = languageStorage.language,
        launchToken = runCatching { tokenStorage.token }.getOrNull(),
    )
}
