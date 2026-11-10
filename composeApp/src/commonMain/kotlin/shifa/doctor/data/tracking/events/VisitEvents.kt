package shifa.doctor.data.tracking.events

/**
 * Group B6: the visit workspace, video, the AI scribe and dictation (`ui/visit`, `speech`). Only this group's job
 * edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never notes, drafts, transcripts or dental values; durations as ranges, payments as a method and `amountBucket`.
 */
object VisitEvents
