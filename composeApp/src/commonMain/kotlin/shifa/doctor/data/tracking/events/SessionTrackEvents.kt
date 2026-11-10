package shifa.doctor.data.tracking.events

import shifa.doctor.data.api.SessionEndReason
import shifa.doctor.data.tracking.TrackingRole

/**
 * Stage A (A5): the session's start and end, sent by the one collector of `sessionRepository.session` and `.ended`.
 * How the doctor signed in (password, e-mail code, SMS code) is group B1's, in `AuthEvents`: only its ViewModels
 * know it. Named `SessionTrackEvents` because `data/api/SessionEvents` is the session's own channel.
 */
object SessionTrackEvents {
    /** A session exists: after a sign-in or a restore at launch. [role]: `doctor` / `staff`. */
    data class SessionStarted(
        val role: TrackingRole,
    ) : TrackEvent("session_started", "role" to role)

    /**
     * [reason]: `expired` / `unauthorized` / `account_disabled` / `signed_out` / `pin_lockout` / `inactivity` /
     * `account_deleted`. Send it before `Tracking.reset()`, so it still belongs to the user.
     */
    data class SessionEnded(
        val reason: SessionEndReason,
    ) : TrackEvent("session_ended", "reason" to reason)
}
