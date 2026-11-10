package shifa.doctor.data.tracking.events

import shifa.doctor.data.appointments.CancelReason

/**
 * Group B3: calendar, booking, blocked time, schedule and locations (`ui/calendar`, `schedule`). Only this group's
 * job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a patient, a reason or note, or an address; booking for a colleague is a Boolean property, not an id.
 *
 * [isOnBehalf]: the appointment, slot or block is a colleague's (the clinic doctor schedule, CAL-10).
 */
object CalendarEvents {
    /**
     * An appointment was booked. [entry]: `calendar` (calendar, Home's agenda, a colleague's schedule) / `patient`
     * (a patient's record or the visit) / `home_video` (Home's Video Call chip) / `intelligence` (a Shifa AI card).
     * [minutes]: the booked length.
     */
    data class AppointmentBooked(
        val entry: String,
        val isVideo: Boolean,
        val isOnBehalf: Boolean,
        val minutes: Int,
    ) : TrackEvent(
            "appointment_booked",
            "entry" to entry,
            "is_video" to isVideo,
            "is_on_behalf" to isOnBehalf,
            "minutes" to minutes,
        )

    /**
     * FIXED NAME, shared with the patient app and watched by the "Booking failing" alert: every way an appointment is
     * booked sends this one event when the booking did not happen. [reason]: `slot_taken` (400 / 409, as in the
     * patient app) / `past_day` (the day ended before Book) / a `toFailureReason()` word. [entry]: as
     * [AppointmentBooked].
     */
    data class BookingFailed(
        val reason: String,
        val entry: String,
        val isVideo: Boolean,
        val isOnBehalf: Boolean,
    ) : TrackEvent(
            "booking_failed",
            "reason" to reason,
            "entry" to entry,
            "is_video" to isVideo,
            "is_on_behalf" to isOnBehalf,
        )

    /** Moved to another free slot from the appointment panel. */
    data class AppointmentRescheduled(
        val isVideo: Boolean,
        val isOnBehalf: Boolean,
    ) : TrackEvent("appointment_rescheduled", "is_video" to isVideo, "is_on_behalf" to isOnBehalf)

    /** [reason]: a `toFailureReason()` word (`conflict` / `validation`: the slot was taken meanwhile). */
    data class AppointmentRescheduleFailed(
        val reason: String,
        val isOnBehalf: Boolean,
    ) : TrackEvent("appointment_reschedule_failed", "reason" to reason, "is_on_behalf" to isOnBehalf)

    /** "Adjust duration" applied; [minutes]: the new length. */
    data class AppointmentDurationChanged(
        val minutes: Int,
        val isOnBehalf: Boolean,
    ) : TrackEvent("appointment_duration_changed", "minutes" to minutes, "is_on_behalf" to isOnBehalf)

    /** [reason]: a `toFailureReason()` word. */
    data class AppointmentDurationChangeFailed(
        val reason: String,
        val isOnBehalf: Boolean,
    ) : TrackEvent("appointment_duration_change_failed", "reason" to reason, "is_on_behalf" to isOnBehalf)

    /**
     * [cancelReason]: the picked category (`doctor_unavailable` / `patient_request` / `rescheduled` / `other`), never
     * the "Other" text; null when none was picked.
     */
    data class AppointmentCancelled(
        val cancelReason: CancelReason?,
        val isVideo: Boolean,
        val isOnBehalf: Boolean,
    ) : TrackEvent(
            "appointment_cancelled",
            "cancel_reason" to cancelReason,
            "is_video" to isVideo,
            "is_on_behalf" to isOnBehalf,
        )

    /** [reason]: a `toFailureReason()` word. */
    data class AppointmentCancelFailed(
        val reason: String,
        val isOnBehalf: Boolean,
    ) : TrackEvent("appointment_cancel_failed", "reason" to reason, "is_on_behalf" to isOnBehalf)

    /** "Mark as no-show" from the appointment panel; [isPatientNotified]: the "tell the patient" switch. */
    data class AppointmentNoShowMarked(
        val isPatientNotified: Boolean,
        val isVideo: Boolean,
    ) : TrackEvent("appointment_no_show_marked", "is_patient_notified" to isPatientNotified, "is_video" to isVideo)

    /** [reason]: `not_allowed` (`NO_SHOW_NOT_ALLOWED`: outside the backend's window) / a `toFailureReason()` word. */
    data class AppointmentNoShowFailed(
        val reason: String,
    ) : TrackEvent("appointment_no_show_failed", "reason" to reason)

    /** The payment reminder for an unpaid video visit went out. */
    data object PaymentReminderSent : TrackEvent("payment_reminder_sent")

    /** [reason]: a `toFailureReason()` word. */
    data class PaymentReminderFailed(
        val reason: String,
    ) : TrackEvent("payment_reminder_failed", "reason" to reason)

    /**
     * Time blocked in the doctor's own calendar. [mode]: `day` / `hours` / `days`; [cancelsOverlapping]: the
     * "Cancel overlapping appointments" switch; [cancelledBucket]: how many the backend cancelled, as a `countBucket`.
     */
    data class TimeBlocked(
        val mode: String,
        val cancelsOverlapping: Boolean,
        val cancelledBucket: String,
    ) : TrackEvent(
            "time_blocked",
            "mode" to mode,
            "cancels_overlapping" to cancelsOverlapping,
            "cancelled_bucket" to cancelledBucket,
        )

    /** [reason]: a `toFailureReason()` word; [mode]: as [TimeBlocked]. */
    data class TimeBlockFailed(
        val reason: String,
        val mode: String,
    ) : TrackEvent("time_block_failed", "reason" to reason, "mode" to mode)

    data class TimeBlockRemoved(
        val isOnBehalf: Boolean,
    ) : TrackEvent("time_block_removed", "is_on_behalf" to isOnBehalf)

    /** [reason]: a `toFailureReason()` word. */
    data class TimeBlockRemoveFailed(
        val reason: String,
        val isOnBehalf: Boolean,
    ) : TrackEvent("time_block_remove_failed", "reason" to reason, "is_on_behalf" to isOnBehalf)

    /**
     * Working hours saved for one location. [hasHoursChanged]: the weekly template was replaced; [hasPeriod]: a
     * bookable period was added (the first setup's default 60 days included); [isFirstSetup]: there was no period
     * before; [workingDays]: weekdays with hours after the save (0–7).
     */
    data class ScheduleSaved(
        val hasHoursChanged: Boolean,
        val hasPeriod: Boolean,
        val isFirstSetup: Boolean,
        val workingDays: Int,
    ) : TrackEvent(
            "schedule_saved",
            "has_hours_changed" to hasHoursChanged,
            "has_period" to hasPeriod,
            "is_first_setup" to isFirstSetup,
            "working_days" to workingDays,
        )

    /** [reason]: a `toFailureReason()` word; [stage]: `hours` / `period` (the hours were saved, the period not). */
    data class ScheduleSaveFailed(
        val reason: String,
        val stage: String,
    ) : TrackEvent("schedule_save_failed", "reason" to reason, "stage" to stage)

    /** Unsaved schedule edits thrown away (leaving the screen or switching location). */
    data object ScheduleChangesDiscarded : TrackEvent("schedule_changes_discarded")

    /** Extra hours on dates added; [days]: how many dates the span covers. */
    data class ScheduleDateHoursAdded(
        val days: Int,
    ) : TrackEvent("schedule_date_hours_added", "days" to days)

    /** [reason]: a `toFailureReason()` word. */
    data class ScheduleDateHoursAddFailed(
        val reason: String,
    ) : TrackEvent("schedule_date_hours_add_failed", "reason" to reason)

    data object ScheduleDateHoursDeleted : TrackEvent("schedule_date_hours_deleted")

    /** [reason]: a `toFailureReason()` word. */
    data class ScheduleDateHoursDeleteFailed(
        val reason: String,
    ) : TrackEvent("schedule_date_hours_delete_failed", "reason" to reason)

    /** A practice location created ([isNew]) or edited; [hasPin]: a map pin is set. Never the label or address. */
    data class PracticeLocationSaved(
        val isNew: Boolean,
        val isPrimary: Boolean,
        val hasPin: Boolean,
    ) : TrackEvent(
            "practice_location_saved",
            "is_new" to isNew,
            "is_primary" to isPrimary,
            "has_pin" to hasPin,
        )

    /** [reason]: a `toFailureReason()` word. */
    data class PracticeLocationSaveFailed(
        val reason: String,
        val isNew: Boolean,
    ) : TrackEvent("practice_location_save_failed", "reason" to reason, "is_new" to isNew)

    data object PracticeLocationDeleted : TrackEvent("practice_location_deleted")

    /** [reason]: `in_use` (409: the only one, or it has hours or appointments) / a `toFailureReason()` word. */
    data class PracticeLocationDeleteFailed(
        val reason: String,
    ) : TrackEvent("practice_location_delete_failed", "reason" to reason)
}
