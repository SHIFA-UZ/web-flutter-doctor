package shifa.doctor.data.tracking.events

import shifa.doctor.data.model.DocumentCategory
import shifa.doctor.data.model.DocumentFile

/**
 * Group B5: documents, forms (025-2) and the clinical engine (`ui/documents`, `forms`). Only this group's job edits
 * this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a document's title or file name, form answers or ICD-10 codes; kind, category, page count and size range are
 * fine.
 *
 * [kind] everywhere: `pdf` / `image` / `other`, from the file's real type ([documentKind]). [category] is the
 * document's type as the backend knows it (`blood_test` … `form_025_2`). [formType] is the form's template
 * (`025-2`, the only one today).
 */
object DocumentsEvents {
    /** [pagesBucket]: a scan's page count as a `countBucket` range, null for a single file. */
    data class DocumentUploaded(
        val kind: String,
        val category: DocumentCategory,
        val isScan: Boolean,
        val pagesBucket: String?,
        val isRetry: Boolean,
    ) : TrackEvent(
            "document_uploaded",
            "kind" to kind,
            "category" to category,
            "is_scan" to isScan,
            "pages_bucket" to pagesBucket,
            "is_retry" to isRetry,
        )

    /**
     * [reason]: `empty_file` / `too_large` (over 25 MB) / `unsupported_type` / `scan_failed` (refused before the type
     * is picked, so no [category]) or a `toFailureReason()` word. [kind] is null for an unsupported file.
     */
    data class DocumentUploadFailed(
        val kind: String?,
        val category: DocumentCategory?,
        val reason: String,
    ) : TrackEvent("document_upload_failed", "kind" to kind, "category" to category, "reason" to reason)

    /** A running upload stopped with Cancel. */
    data class DocumentUploadCancelled(
        val kind: String,
        val category: DocumentCategory,
    ) : TrackEvent("document_upload_cancelled", "kind" to kind, "category" to category)

    /** The viewer showed the file. */
    data class DocumentOpened(
        val kind: String,
    ) : TrackEvent("document_opened", "kind" to kind)

    /** [reason]: `no_access` (locked) / `gone` (deleted) / `empty_file` / a `toFailureReason()` word. */
    data class DocumentOpenFailed(
        val reason: String,
    ) : TrackEvent("document_open_failed", "reason" to reason)

    /** [from]: `list` (a locked row) / `viewer`. */
    data class DocumentAccessRequested(
        val from: String,
    ) : TrackEvent("document_access_requested", "from" to from)

    /** [reason]: `already_requested` (409) / a `toFailureReason()` word. */
    data class DocumentAccessRequestFailed(
        val from: String,
        val reason: String,
    ) : TrackEvent("document_access_request_failed", "from" to from, "reason" to reason)

    /** Save from the viewer. */
    data class DocumentSavedToDevice(
        val kind: String,
    ) : TrackEvent("document_saved_to_device", "kind" to kind)

    /** The platform could not write the file. */
    data class DocumentSaveToDeviceFailed(
        val kind: String,
    ) : TrackEvent("document_save_to_device_failed", "kind" to kind)

    /** The edit sheet saved; [category] is the new one. */
    data class DocumentEdited(
        val category: DocumentCategory,
        val isTitleChanged: Boolean,
        val isCategoryChanged: Boolean,
    ) : TrackEvent(
            "document_edited",
            "category" to category,
            "is_title_changed" to isTitleChanged,
            "is_category_changed" to isCategoryChanged,
        )

    /** [reason]: a `toFailureReason()` word. */
    data class DocumentEditFailed(
        val reason: String,
    ) : TrackEvent("document_edit_failed", "reason" to reason)

    /** Removed (also when it was already gone on the server). */
    data class DocumentDeleted(
        val category: DocumentCategory?,
    ) : TrackEvent("document_deleted", "category" to category)

    /** [reason]: a `toFailureReason()` word. */
    data class DocumentDeleteFailed(
        val reason: String,
    ) : TrackEvent("document_delete_failed", "reason" to reason)

    /** The whole save went through (form, PDF, document, link). [isNew]: a form created in this editor. */
    data class FormSaved(
        val formType: String,
        val isNew: Boolean,
    ) : TrackEvent("form_saved", "form_type" to formType, "is_new" to isNew)

    /**
     * [step]: where it stopped, `form` / `pdf` / `document` / `link`; null for `incomplete` (neither diagnosis nor
     * complaints). [reason]: `incomplete` / `pdf_not_made` / a `toFailureReason()` word.
     */
    data class FormSaveFailed(
        val formType: String,
        val isNew: Boolean,
        val step: String?,
        val reason: String,
    ) : TrackEvent(
            "form_save_failed",
            "form_type" to formType,
            "is_new" to isNew,
            "step" to step,
            "reason" to reason,
        )

    data class FormSignatureRequested(
        val formType: String,
    ) : TrackEvent("form_signature_requested", "form_type" to formType)

    /** [reason]: `incomplete` / a `toFailureReason()` word (the silent save first, or the request). */
    data class FormSignatureRequestFailed(
        val formType: String,
        val reason: String,
    ) : TrackEvent("form_signature_request_failed", "form_type" to formType, "reason" to reason)

    /** The clinical engine wrote its texts; [chipsBucket]: the picked chips as a `countBucket` range. */
    data class ClinicalEngineApplied(
        val chipsBucket: String,
    ) : TrackEvent("clinical_engine_applied", "chips_bucket" to chipsBucket)

    /** [reason]: a `toFailureReason()` word. */
    data class ClinicalEngineApplyFailed(
        val reason: String,
    ) : TrackEvent("clinical_engine_apply_failed", "reason" to reason)
}

/** A file's kind for the events: `pdf` / `image` / `other`. */
fun documentKind(mime: String): String =
    when {
        mime == DocumentFile.PDF -> "pdf"
        mime.startsWith("image/") -> "image"
        else -> "other"
    }
