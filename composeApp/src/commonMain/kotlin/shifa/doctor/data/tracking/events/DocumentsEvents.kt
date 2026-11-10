package shifa.doctor.data.tracking.events

/**
 * Group B5: documents, forms (025-2) and the clinical engine (`ui/documents`, `forms`). Only this group's job edits
 * this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a document's title or file name, form answers or ICD-10 codes; kind, category, page count and size range are
 * fine.
 */
object DocumentsEvents
