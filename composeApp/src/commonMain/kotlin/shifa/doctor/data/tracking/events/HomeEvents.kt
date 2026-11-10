package shifa.doctor.data.tracking.events

/**
 * Group B2: Home, the notifications inbox, Reports and Tasks (`ui/home`, `notifications`, `analytics`, `tasks`).
 * Only this group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never search text, a task's title or instructions, or a notification's text; a notification's type is fine.
 */
object HomeEvents
