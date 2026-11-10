package shifa.doctor.data.tracking.events

/**
 * Group B9: profile, settings, subscription and billing, security, legal, privacy (`ui/profile`). Only this group's
 * job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never profile values: the names of the changed fields only. Owns the usage-data switch (`Tracking.setOptOut`, D3).
 */
object ProfileEvents
