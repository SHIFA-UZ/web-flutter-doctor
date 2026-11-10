package shifa.doctor.data.tracking.events

/**
 * Group B8: the clinic workspace: members, invitations, patients, finance, plans, services (`ui/clinic`). Only this
 * group's job edits this file.
 *
 * One `data class` (with properties) or `data object` (without) per event, nested in this object and extending
 * [TrackEvent]; allowed values listed in the KDoc above each class. Fire from the ViewModel after the outcome, a
 * `*_failed` event with `reason = e.toFailureReason()` or the feature's own fixed word.
 * Never a clinic, member or patient, an invited e-mail address, or an exact amount or share: `amountBucket` only.
 * Enum values are passed as their `trackName()` (the enums live in `ui/clinic`, which this package doesn't import).
 */
object ClinicEvents {
    // --- Workspace ---

    /** The switcher moved the workspace to another of the user's clinics. */
    data object ClinicSwitched : TrackEvent("clinic_switched")

    // --- Members, revenue share, invitations ---

    /** [target]: `member` (one doctor's share) / `default` (the clinic default); [isCleared]: set back to none. */
    data class RevenueShareSaved(
        val target: String,
        val isCleared: Boolean,
    ) : TrackEvent("clinic_revenue_share_saved", "target" to target, "is_cleared" to isCleared)

    /** [target] as in [RevenueShareSaved]; [reason]: a `toFailureReason()` word. */
    data class RevenueShareFailed(
        val target: String,
        val reason: String,
    ) : TrackEvent("clinic_revenue_share_failed", "target" to target, "reason" to reason)

    /** A receptionist invitation sent (never the address). */
    data object InvitationSent : TrackEvent("clinic_invitation_sent")

    /** [reason]: `invalid_email` / `already_pending` / a `toFailureReason()` word. */
    data class InvitationFailed(
        val reason: String,
    ) : TrackEvent("clinic_invitation_failed", "reason" to reason)

    data object InvitationRevoked : TrackEvent("clinic_invitation_revoked")

    /** [reason]: a `toFailureReason()` word. */
    data class InvitationRevokeFailed(
        val reason: String,
    ) : TrackEvent("clinic_invitation_revoke_failed", "reason" to reason)

    // --- Clinic patients ---

    /** A search's first page came back (never the text). [resultBucket]: `countBucket` of the total found. */
    data class PatientsSearched(
        val resultBucket: String,
    ) : TrackEvent("clinic_patients_searched", "result_bucket" to resultBucket)

    // --- Finance ---

    /** [kpi]: `revenue` / `outstanding` / `overdue` / `rate`. */
    data class FinanceKpiSelected(
        val kpi: String,
    ) : TrackEvent("clinic_finance_kpi_selected", "kpi" to kpi)

    /** [isAllTime]: "All time" chosen instead of a month. */
    data class FinanceMonthChanged(
        val isAllTime: Boolean,
    ) : TrackEvent("clinic_finance_month_changed", "is_all_time" to isAllTime)

    /**
     * A desk payment recorded. [source]: `ledger_full` (a ledger row's "Paid by …") / `ledger_custom` (the custom
     * amount sheet) / `plan` (the plan's Record payment); [method]: `cash` / `card` / `transfer` / `other`;
     * [amountBucket]: `amountBucket` of the amount.
     */
    data class PaymentRecorded(
        val source: String,
        val method: String,
        val amountBucket: String?,
    ) : TrackEvent(
            "clinic_payment_recorded",
            "source" to source,
            "method" to method,
            "amount_bucket" to amountBucket,
        )

    /** [source], [method] as in [PaymentRecorded]; [reason]: a `toFailureReason()` word. */
    data class PaymentFailed(
        val source: String,
        val method: String,
        val reason: String,
    ) : TrackEvent("clinic_payment_failed", "source" to source, "method" to method, "reason" to reason)

    /**
     * An installment's status set by hand. [status]: `paid` / `pending` / `waived` / `cancelled`; [method] only for
     * `paid` (as in [PaymentRecorded]); [amountBucket]: `amountBucket` of the installment.
     */
    data class InstallmentStatusChanged(
        val status: String,
        val method: String?,
        val amountBucket: String?,
    ) : TrackEvent(
            "clinic_installment_status_changed",
            "status" to status,
            "method" to method,
            "amount_bucket" to amountBucket,
        )

    /** [status] as in [InstallmentStatusChanged]; [reason]: a `toFailureReason()` word. */
    data class InstallmentStatusFailed(
        val status: String,
        val reason: String,
    ) : TrackEvent("clinic_installment_status_failed", "status" to status, "reason" to reason)

    data object InstallmentReminderSent : TrackEvent("clinic_installment_reminder_sent")

    /** [reason]: a `toFailureReason()` word. */
    data class InstallmentReminderFailed(
        val reason: String,
    ) : TrackEvent("clinic_installment_reminder_failed", "reason" to reason)

    /**
     * A financial record created. [recordType]: `invoice` / `receipt` / `estimate` / `credit_note`; [hasPlan]: linked
     * to a treatment plan (its totals come from the plan); [amountBucket]: `amountBucket` of the typed total, left out
     * with a plan.
     */
    data class FinancialRecordCreated(
        val recordType: String,
        val hasPlan: Boolean,
        val amountBucket: String?,
    ) : TrackEvent(
            "clinic_financial_record_created",
            "record_type" to recordType,
            "has_plan" to hasPlan,
            "amount_bucket" to amountBucket,
        )

    /** [recordType] as in [FinancialRecordCreated]; [reason]: a `toFailureReason()` word. */
    data class FinancialRecordFailed(
        val recordType: String,
        val reason: String,
    ) : TrackEvent("clinic_financial_record_failed", "record_type" to recordType, "reason" to reason)

    /** The view's CSV handed to the share sheet. [view]: `dashboard` / `ledger` / `installments` / `earnings` / … */
    data class FinanceCsvExported(
        val view: String,
    ) : TrackEvent("clinic_finance_csv_exported", "view" to view)

    /** The share sheet could not take the CSV. [view] as in [FinanceCsvExported]. */
    data class FinanceCsvExportFailed(
        val view: String,
    ) : TrackEvent("clinic_finance_csv_export_failed", "view" to view)

    // --- Treatment plans ---

    /**
     * [status]: the new one, `draft` / `active` / `in_progress` / `on_hold` / `completed` / `cancelled`; [source]:
     * `list` (the clinic's plans) / `detail` (the plan's screen).
     */
    data class PlanStatusChanged(
        val status: String,
        val source: String,
    ) : TrackEvent("clinic_plan_status_changed", "status" to status, "source" to source)

    /** [status], [source] as in [PlanStatusChanged]; [reason]: a `toFailureReason()` word. */
    data class PlanStatusFailed(
        val status: String,
        val source: String,
        val reason: String,
    ) : TrackEvent("clinic_plan_status_failed", "status" to status, "source" to source, "reason" to reason)

    /** The plan's PDF made. [renderer]: `server` (BE-01) / `app` (the fallback when the route is missing). */
    data class PlanPdfExported(
        val renderer: String,
    ) : TrackEvent("clinic_plan_pdf_exported", "renderer" to renderer)

    /** [reason]: `render_failed` (the app could not lay it out) / a `toFailureReason()` word of the server's answer. */
    data class PlanPdfExportFailed(
        val reason: String,
    ) : TrackEvent("clinic_plan_pdf_export_failed", "reason" to reason)

    /** The wizard moved on to [step] (2–5: basics, team & services, payment, review). */
    data class PlanWizardStepReached(
        val step: Int,
    ) : TrackEvent("clinic_plan_wizard_step_reached", "step" to step)

    /** The wizard closed with no plan made, at [step] (1–5). */
    data class PlanWizardAbandoned(
        val step: Int,
    ) : TrackEvent("clinic_plan_wizard_abandoned", "step" to step)

    /**
     * Every save request went through. [linesBucket], [doctorsBucket]: `countBucket`s; [totalBucket]: `amountBucket`
     * of the plan's total; [isFromPatient]: the wizard started from a patient's record.
     */
    data class PlanCreated(
        val linesBucket: String,
        val doctorsBucket: String,
        val totalBucket: String?,
        val hasInitialPayment: Boolean,
        val hasInstallments: Boolean,
        val isActivated: Boolean,
        val isFromPatient: Boolean,
    ) : TrackEvent(
            "clinic_plan_created",
            "lines_bucket" to linesBucket,
            "doctors_bucket" to doctorsBucket,
            "total_bucket" to totalBucket,
            "has_initial_payment" to hasInitialPayment,
            "has_installments" to hasInstallments,
            "is_activated" to isActivated,
            "is_from_patient" to isFromPatient,
        )

    /**
     * A save request failed (the plan stays a draft once it exists). [stage]: `plan` / `lines` / `payment` /
     * `installments` / `status`; [reason]: a `toFailureReason()` word.
     */
    data class PlanSaveFailed(
        val stage: String,
        val reason: String,
    ) : TrackEvent("clinic_plan_save_failed", "stage" to stage, "reason" to reason)

    // --- Services catalogue ---

    /** [isNew]: added, not edited; [isAllDoctors]: offered by every doctor of the clinic. */
    data class ServiceSaved(
        val isNew: Boolean,
        val isAllDoctors: Boolean,
    ) : TrackEvent("clinic_service_saved", "is_new" to isNew, "is_all_doctors" to isAllDoctors)

    /** [reason]: `invalid` (a field missing) / `code_taken` (checked in the app) / a `toFailureReason()` word. */
    data class ServiceSaveFailed(
        val isNew: Boolean,
        val reason: String,
    ) : TrackEvent("clinic_service_save_failed", "is_new" to isNew, "reason" to reason)

    /** Deactivated or reactivated; [isActive] is the new state. */
    data class ServiceToggled(
        val isActive: Boolean,
    ) : TrackEvent("clinic_service_toggled", "is_active" to isActive)

    /** [reason]: a `toFailureReason()` word. */
    data class ServiceToggleFailed(
        val reason: String,
    ) : TrackEvent("clinic_service_toggle_failed", "reason" to reason)

    /** "Discard changes?" answered with Discard in the service sheet. */
    data object ServiceEditDiscarded : TrackEvent("clinic_service_edit_discarded")
}
