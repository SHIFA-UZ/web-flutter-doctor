package shifa.doctor.data.tracking.events

/**
 * Group B4: the patients directory, the patient record and care partnerships (`ui/patients`, `care`). Only this
 * group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a patient's name, phone, medical values or search text: which field was saved, never what it holds.
 */
object PatientsEvents
