package shifa.doctor.data.tracking.events

import shifa.doctor.data.model.VisitKind

/**
 * Group B6: the visit workspace, video, the AI scribe and dictation (`ui/visit`, `speech`). Only this group's job
 * edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never notes, drafts, transcripts or dental values; durations as ranges, payments as a method and `amountBucket`.
 *
 * `video_join_failed`, `visit_end_failed` and `scribe_failed` are watched by PostHog alerts and share their names with
 * the patient app: never rename them.
 */
object VisitEvents {
    // The visit workspace (VisitViewModel).

    /** The workspace loaded for the first time. */
    data class VisitOpened(
        val kind: VisitKind,
        val isDental: Boolean,
        val isCompleted: Boolean,
    ) : TrackEvent("visit_opened", "kind" to kind, "is_dental" to isDental, "is_completed" to isCompleted)

    /** [mode]: `general` / `dental` / `form0252`. */
    data class VisitModeChanged(
        val mode: String,
    ) : TrackEvent("visit_mode_changed", "mode" to mode)

    /** [helper]: `ai` / `form`; [isEmpty]: there was nothing to show (a banner said so). */
    data class VisitHelperOpened(
        val helper: String,
        val isEmpty: Boolean,
    ) : TrackEvent("visit_helper_opened", "helper" to helper, "is_empty" to isEmpty)

    /** An AI-suggested ICD-10 code applied (never the code). */
    data object VisitIcdApplied : TrackEvent("visit_icd_applied")

    /** [kind]: `before` / `after`. */
    data class VisitPhotoAdded(
        val kind: String,
    ) : TrackEvent("visit_photo_added", "kind" to kind)

    data object VisitSignatureRequested : TrackEvent("visit_signature_requested")

    /** The awaited signature arrived (waiting → signed while the visit was open). */
    data object VisitPatientSigned : TrackEvent("visit_patient_signed")

    /** [reason]: `notes_not_saved` / a `toFailureReason()` word. */
    data class VisitSignatureRequestFailed(
        val reason: String,
    ) : TrackEvent("visit_signature_request_failed", "reason" to reason)

    data class VisitNoShowMarked(
        val isPatientNotified: Boolean,
    ) : TrackEvent("visit_no_show_marked", "is_patient_notified" to isPatientNotified)

    /** [reason]: `not_allowed` (`NO_SHOW_NOT_ALLOWED`) / a `toFailureReason()` word. */
    data class VisitNoShowFailed(
        val reason: String,
    ) : TrackEvent("visit_no_show_failed", "reason" to reason)

    // End appointment (VisitViewModel.end).

    /** The End pipeline started, with the steps it will run. */
    data class VisitEndStarted(
        val kind: VisitKind,
        val hasPlanStep: Boolean,
        val hasPaymentStep: Boolean,
        val hasScribeStep: Boolean,
    ) : TrackEvent(
            "visit_end_started",
            "kind" to kind,
            "has_plan_step" to hasPlanStep,
            "has_payment_step" to hasPaymentStep,
            "has_scribe_step" to hasScribeStep,
        )

    /** End refused before it started: the payment typed at the visit is not above zero (input, not a failure). */
    data object VisitPaymentInvalid : TrackEvent("visit_payment_invalid")

    /**
     * End stopped before the visit was completed (it stays open). [step]: `notes` / `dental` / `plan` / `payment` /
     * `complete`; [reason]: `notes_not_saved` / `dental_not_loaded` / `dental_not_saved` / `plan_not_saved` /
     * `payment_not_recorded` / a `toFailureReason()` word (the complete call). Alert "Visit can't be completed".
     */
    data class VisitEndFailed(
        val reason: String,
        val step: String,
    ) : TrackEvent("visit_end_failed", "reason" to reason, "step" to step)

    /** The visit completed on the server. [mode]: as [VisitModeChanged]. */
    data class VisitCompleted(
        val kind: VisitKind,
        val mode: String,
        val hasScribe: Boolean,
        val hasPhotos: Boolean,
    ) : TrackEvent(
            "visit_completed",
            "kind" to kind,
            "mode" to mode,
            "has_scribe" to hasScribe,
            "has_photos" to hasPhotos,
        )

    /** Treatment-plan lines completed by this visit; [linesBucket] from `countBucket`. */
    data class VisitPlanLinesCompleted(
        val linesBucket: String,
    ) : TrackEvent("visit_plan_lines_completed", "lines_bucket" to linesBucket)

    /** A plan payment recorded at the visit. [method]: `cash` / `card` / `transfer`; [amountBucket] from `amountBucket`. */
    data class VisitPaymentRecorded(
        val method: String,
        val amountBucket: String?,
    ) : TrackEvent("visit_payment_recorded", "method" to method, "amount_bucket" to amountBucket)

    /** The summary PDF made and filed under the patient. [isRetry]: from Retry summary. */
    data class VisitSummarySaved(
        val isRetry: Boolean,
        val hasDental: Boolean,
        val hasPhotos: Boolean,
        val hasPlan: Boolean,
    ) : TrackEvent(
            "visit_summary_saved",
            "is_retry" to isRetry,
            "has_dental" to hasDental,
            "has_photos" to hasPhotos,
            "has_plan" to hasPlan,
        )

    /** Nothing to put in a summary. [reason]: `nothing_to_save` / `ai_later` (the scribe's notes come later). */
    data class VisitSummarySkipped(
        val reason: String,
    ) : TrackEvent("visit_summary_skipped", "reason" to reason)

    /**
     * The visit is completed but its summary isn't saved (Retry summary). [reason]: `notes_not_read` / `pdf_failed` /
     * `no_patient` / a `toFailureReason()` word (the upload).
     */
    data class VisitSummaryFailed(
        val reason: String,
        val isRetry: Boolean,
    ) : TrackEvent("visit_summary_failed", "reason" to reason, "is_retry" to isRetry)

    // Notes (VisitNotes).

    /** An autosave failed; once until a save succeeds again. */
    data class VisitNotesSaveFailed(
        val reason: String,
    ) : TrackEvent("visit_notes_save_failed", "reason" to reason)

    data object VisitAiDraftConfirmed : TrackEvent("visit_ai_draft_confirmed")

    data object VisitAiDraftDiscarded : TrackEvent("visit_ai_draft_discarded")

    /** [action]: `confirm` / `discard`. */
    data class VisitAiDraftFailed(
        val action: String,
        val reason: String,
    ) : TrackEvent("visit_ai_draft_failed", "action" to action, "reason" to reason)

    // The AI scribe (Dictation for the recording, VisitViewModel for the upload and the wait at End).

    data object ScribeRecordingStarted : TrackEvent("scribe_recording_started")

    data object ScribeRecordingCancelled : TrackEvent("scribe_recording_cancelled")

    /** The scribe's recording stopped and is about to be sent; [durationBucket] from `durationBucket`. */
    data class ScribeRecordingFinished(
        val durationBucket: String,
    ) : TrackEvent("scribe_recording_finished", "duration_bucket" to durationBucket)

    /** The recording reached the scribe. [isRetry]: "Upload the recording again". */
    data class ScribeUploaded(
        val isRetry: Boolean,
    ) : TrackEvent("scribe_uploaded", "is_retry" to isRetry)

    /**
     * [stage]: `record` (the recorder gave nothing) / `upload` / `notes` (End waited and got no notes);
     * [reason]: `recording_failed` / `timeout` / a `toFailureReason()` word (never `forbidden` / `rate_limited` at
     * `upload`: those are [ScribeBlocked]). Alert "Visit can't be completed".
     */
    data class ScribeFailed(
        val reason: String,
        val stage: String,
    ) : TrackEvent("scribe_failed", "reason" to reason, "stage" to stage)

    /**
     * The upload was refused by the plan's rules, not broken (kept off the alert). [reason]: `forbidden` (no PRO) /
     * `rate_limited` (the daily limit).
     */
    data class ScribeBlocked(
        val reason: String,
    ) : TrackEvent("scribe_blocked", "reason" to reason)

    /** End waited for the scribe and its notes arrived. */
    data object ScribeNotesReady : TrackEvent("scribe_notes_ready")

    /** "Continue without waiting". */
    data object ScribeWaitSkipped : TrackEvent("scribe_wait_skipped")

    // Video (VideoVisit).

    /** The camera and microphone answers, when they change. */
    data class VideoPermissionsResult(
        val isCameraGranted: Boolean,
        val isMicGranted: Boolean,
    ) : TrackEvent(
            "video_permissions_result",
            "is_camera_granted" to isCameraGranted,
            "is_mic_granted" to isMicGranted,
        )

    data class VideoJoined(
        val isRejoin: Boolean,
    ) : TrackEvent("video_joined", "is_rejoin" to isRejoin)

    /**
     * [reason]: `too_early` / `too_late` / `payment_required` / `daily_failed` (the SDK refused) / a
     * `toFailureReason()` word (the token). Alert "Video visits failing".
     */
    data class VideoJoinFailed(
        val reason: String,
    ) : TrackEvent("video_join_failed", "reason" to reason)

    /** [control]: `camera` / `mic` / `flip`; [isOn] null for `flip`. */
    data class VideoControlToggled(
        val control: String,
        val isOn: Boolean?,
    ) : TrackEvent("video_control_toggled", "control" to control, "is_on" to isOn)

    /**
     * The doctor's call ended. [endedBy]: `doctor` (Leave call) / `visit_closed` (Back or End from the call) /
     * `remote` (Daily left) / `error` (the call failed).
     */
    data class VideoCallEnded(
        val endedBy: String,
        val hasPatientJoined: Boolean,
        /** Since the doctor joined, through `durationBucket`; null when the join time is unknown. */
        val durationBucket: String? = null,
    ) : TrackEvent(
            "video_call_ended",
            "ended_by" to endedBy,
            "has_patient_joined" to hasPatientJoined,
            "duration_bucket" to durationBucket,
        )

    // Dictation (Dictation, every mic on a text field: visit notes, dental notes, 025-2 forms).

    /**
     * [fieldKind] for every dictation event: `soap` / `free_notes` / `dental_notes` / `form` (never the field's label);
     * `scribe` only in [DictationMicDenied] (the scribe's recording has its own events).
     */
    data class DictationStarted(
        val fieldKind: String,
    ) : TrackEvent("dictation_started", "field_kind" to fieldKind)

    data class DictationCancelled(
        val fieldKind: String,
    ) : TrackEvent("dictation_cancelled", "field_kind" to fieldKind)

    /** [hasSpeech]: false for "No speech detected" (nothing added). */
    data class DictationTranscribed(
        val fieldKind: String,
        val hasSpeech: Boolean,
    ) : TrackEvent("dictation_transcribed", "field_kind" to fieldKind, "has_speech" to hasSpeech)

    /** [reason]: `recording_failed` / a `toFailureReason()` word (`forbidden` = no plan, `rate_limited`, `timeout`). */
    data class DictationFailed(
        val fieldKind: String,
        val reason: String,
    ) : TrackEvent("dictation_failed", "field_kind" to fieldKind, "reason" to reason)

    data class DictationMicDenied(
        val fieldKind: String,
    ) : TrackEvent("dictation_mic_denied", "field_kind" to fieldKind)

    data object DictationReported : TrackEvent("dictation_reported")

    data class DictationReportFailed(
        val reason: String,
    ) : TrackEvent("dictation_report_failed", "reason" to reason)

    // The briefing sheet (BriefingViewModel).

    data object VisitBriefingOpened : TrackEvent("visit_briefing_opened")

    /** Once per sheet. */
    data object VisitBriefingReady : TrackEvent("visit_briefing_ready")

    /** Once until it recovers. [reason]: `generation_failed` (the backend's status) / a `toFailureReason()` word. */
    data class VisitBriefingFailed(
        val reason: String,
    ) : TrackEvent("visit_briefing_failed", "reason" to reason)

    data object VisitBriefingCopied : TrackEvent("visit_briefing_copied")

    // The scribe's visit facts (VisitFactsViewModel).

    /** "Document everything"; the buckets from `countBucket`. */
    data class VisitFactsDocumented(
        val factsBucket: String,
        val rejectedBucket: String,
        val tasksBucket: String,
    ) : TrackEvent(
            "visit_facts_documented",
            "facts_bucket" to factsBucket,
            "rejected_bucket" to rejectedBucket,
            "tasks_bucket" to tasksBucket,
        )

    /** [reason]: `notes_not_saved` / a `toFailureReason()` word. */
    data class VisitFactsDocumentFailed(
        val reason: String,
    ) : TrackEvent("visit_facts_document_failed", "reason" to reason)
}
