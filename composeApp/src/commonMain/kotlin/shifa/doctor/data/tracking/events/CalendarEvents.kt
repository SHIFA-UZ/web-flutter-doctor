package shifa.doctor.data.tracking.events

/**
 * Group B3: calendar, booking, blocked time, schedule and locations (`ui/calendar`, `schedule`). Only this group's
 * job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a patient, a reason or note, or an address; booking for a colleague is a Boolean property, not an id.
 */
object CalendarEvents
