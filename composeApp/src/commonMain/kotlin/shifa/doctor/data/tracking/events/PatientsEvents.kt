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
object PatientsEvents {
    // --- Directory (PatientsViewModel) ---

    /** The directory search settled (300 ms after typing). [resultCount]: a `countBucket`; never the text. */
    data class PatientsSearched(
        val resultCount: String,
    ) : TrackEvent("patients_searched", "result_count" to resultCount)

    /**
     * The segment, status filter or order changed. [tab]: `all` / `favorites` / `follow_ups`; [status]: `active` /
     * `at_risk` / `follow_up` / …, null for all statuses.
     */
    data class PatientsFiltered(
        val tab: String,
        val status: String?,
        val isDescending: Boolean,
    ) : TrackEvent("patients_filtered", "tab" to tab, "status" to status, "is_descending" to isDescending)

    // --- Shared picker (PatientPickerSheet: booking, new task) ---

    /** A patient picked in the shared picker. [resultCount]: a `countBucket` of the list shown, only when searched. */
    data class PatientPicked(
        val searched: Boolean,
        val resultCount: String?,
    ) : TrackEvent("patient_picked", "searched" to searched, "result_count" to resultCount)

    // --- Create / edit sheet (CreatePatientViewModel) ---

    data class PatientCreated(
        val hasPhone: Boolean,
    ) : TrackEvent("patient_created", "has_phone" to hasPhone)

    /** [reason]: `invalid_fields` (not sent) / `conflict` (a known phone) / `validation` / a `toFailureReason()` word. */
    data class PatientCreateFailed(
        val reason: String,
    ) : TrackEvent("patient_create_failed", "reason" to reason)

    /** "Edit details" saved. */
    data object PatientDetailsEdited : TrackEvent("patient_details_edited")

    /** [reason]: `invalid_fields` / `conflict` / `not_found` / a `toFailureReason()` word. */
    data class PatientDetailsEditFailed(
        val reason: String,
    ) : TrackEvent("patient_details_edit_failed", "reason" to reason)

    // --- Record (PatientDetailViewModel) ---

    /**
     * One field of the record saved. [field]: `phones` / `allergies` / `chronic_disease` / `gender` / `blood_group` /
     * `sms_reminders` / `sms_reminder_hours`; [isEnabled]: only for `sms_reminders`, the new setting.
     */
    data class PatientFieldSaved(
        val field: String,
        val isEnabled: Boolean?,
    ) : TrackEvent("patient_field_saved", "field" to field, "is_enabled" to isEnabled)

    /** [field] as above; [reason]: `invalid_phone` (not sent) / `conflict` (a known phone) / a `toFailureReason()` word. */
    data class PatientFieldSaveFailed(
        val field: String,
        val reason: String,
    ) : TrackEvent("patient_field_save_failed", "field" to field, "reason" to reason)

    data class PatientFavoriteToggled(
        val isFavorite: Boolean,
    ) : TrackEvent("patient_favorite_toggled", "is_favorite" to isFavorite)

    data object PatientTestSmsSent : TrackEvent("patient_test_sms_sent")

    /** [reason]: a `toFailureReason()` word (`forbidden`: the plan has no SMS; `server`: the provider is down). */
    data class PatientTestSmsFailed(
        val reason: String,
    ) : TrackEvent("patient_test_sms_failed", "reason" to reason)

    /** A patient-app login created for the patient. */
    data object PatientAccountCreated : TrackEvent("patient_account_created")

    /** [reason]: `conflict` (they have one already) / a `toFailureReason()` word. */
    data class PatientAccountCreateFailed(
        val reason: String,
    ) : TrackEvent("patient_account_create_failed", "reason" to reason)

    // --- AI summary (PatientBriefingViewModel) ---

    /** [isRegenerate]: a briefing was already shown this session. */
    data class PatientBriefingGenerated(
        val isRegenerate: Boolean,
    ) : TrackEvent("patient_briefing_generated", "is_regenerate" to isRegenerate)

    /** [reason]: `empty` (nothing to brief on) / `rate_limited` (the daily limit) / a `toFailureReason()` word. */
    data class PatientBriefingFailed(
        val isRegenerate: Boolean,
        val reason: String,
    ) : TrackEvent("patient_briefing_failed", "is_regenerate" to isRegenerate, "reason" to reason)

    data object PatientBriefingCopied : TrackEvent("patient_briefing_copied")

    // --- Prophylaxis reminder (ProphylaxisViewModel) ---

    /** [intervalMonths]: 1–60, the reminder interval the clinic chose (a setting, not a clinical value). */
    data class ProphylaxisSaved(
        val isEnabled: Boolean,
        val intervalMonths: Int,
    ) : TrackEvent("prophylaxis_saved", "is_enabled" to isEnabled, "interval_months" to intervalMonths)

    data class ProphylaxisSaveFailed(
        val reason: String,
    ) : TrackEvent("prophylaxis_save_failed", "reason" to reason)

    // --- Care partnerships (ui/care) ---

    /**
     * A partner search typed by the doctor settled (not the list shown on open). [resultCount]: a `countBucket`;
     * [hasSpecialty]: the specialty filter was used. Never the text.
     */
    data class CarePartnerSearched(
        val resultCount: String,
        val hasSpecialty: Boolean,
    ) : TrackEvent("care_partner_searched", "result_count" to resultCount, "has_specialty" to hasSpecialty)

    /** [hasMessage]: the doctor wrote a message (never the message). */
    data class CareInviteSent(
        val hasMessage: Boolean,
    ) : TrackEvent("care_invite_sent", "has_message" to hasMessage)

    /** [reason]: `conflict` (already a partner for this patient) / a `toFailureReason()` word. */
    data class CareInviteFailed(
        val reason: String,
    ) : TrackEvent("care_invite_failed", "reason" to reason)

    /** [filter]: `all` / `needs_answer` / `active` / `sent` / `finished`. */
    data class CarePartnershipsFiltered(
        val filter: String,
    ) : TrackEvent("care_partnerships_filtered", "filter" to filter)

    /** [action]: `accept` / `decline` / `cancel` / `complete`. */
    data class CarePartnershipActed(
        val action: String,
    ) : TrackEvent("care_partnership_acted", "action" to action)

    data class CarePartnershipActFailed(
        val action: String,
        val reason: String,
    ) : TrackEvent("care_partnership_act_failed", "action" to action, "reason" to reason)

    data object CareProgressPosted : TrackEvent("care_progress_posted")

    data class CareProgressPostFailed(
        val reason: String,
    ) : TrackEvent("care_progress_post_failed", "reason" to reason)
}
