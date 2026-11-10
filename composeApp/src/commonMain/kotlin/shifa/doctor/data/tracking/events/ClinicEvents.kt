package shifa.doctor.data.tracking.events

/**
 * Group B8: the clinic workspace: members, invitations, patients, finance, plans, services (`ui/clinic`). Only this
 * group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a clinic, member or patient, an invited e-mail address, or an exact amount or share: `amountBucket` only.
 */
object ClinicEvents
