# Tracking: clinic workspace (B8)

Folder `ui/clinic`; events in `data/tracking/events/ClinicEvents.kt`; tests in
`androidUnitTest/.../ui/clinic/*ViewModelTest.kt` (one test per ViewModel, with `RecordingTracker`). Every ViewModel
in the folder takes `tracker: Tracker = Tracking` as its last constructor parameter (`PlanDetailViewModel`: after
`makePdf` and `language`; no test passes those as a trailing lambda).

No clinic, member or patient id, name, e-mail address, invitation key, memo, note, search text or share percentage
is sent. Enum values go out as their `trackName()` (`PayMethod.Card` → `card`, `PlanStatus.OnHold` → `on_hold`).

## Screen views

| Id | Sent from | When |
|---|---|---|
| `clinic_overview` … `clinic_settings` | `ClinicWorkspaceScreen` (`ClinicSection.trackScreen`) | Each time the section shown changes, and when the tab comes back; only while a clinic is shown (the shell sends `clinic` for the tab itself) |
| `finance_dashboard` … `finance_payments` | `FinanceSection` (`FinanceView.trackScreen`) | Each time the finance view changes, and when the Finance section comes back (sent beside `clinic_finance`) |

`plan_detail`, `plan_wizard` and `doctor_schedule` are the shell's routes; nothing here sends them.

## Events

| Event | Properties | Fires |
|---|---|---|
| `clinic_switched` | none | `ClinicWorkspaceViewModel.select`, only for a clinic other than the one shown |
| `clinic_revenue_share_saved` | `target` (`member` / `default`), `is_cleared` | `ClinicPeopleViewModel.saveShare` success |
| `clinic_revenue_share_failed` | `target`, `reason` | `saveShare` failure |
| `clinic_invitation_sent` | none | `sendInvite` success |
| `clinic_invitation_failed` | `reason` (`invalid_email` / `already_pending` / failure word) | `sendInvite`: refused in the app, or the backend failed |
| `clinic_invitation_revoked` / `clinic_invitation_revoke_failed` | — / `reason` | `revoke` |
| `clinic_patients_searched` | `result_bucket` (`countBucket` of the total) | `ClinicPatientsViewModel.load` answer for a non-empty search |
| `clinic_finance_kpi_selected` | `kpi` (`revenue` / `outstanding` / `overdue` / `rate`) | `ClinicFinanceViewModel.kpi` |
| `clinic_finance_month_changed` | `is_all_time` | `ClinicFinanceViewModel.month` |
| `clinic_payment_recorded` | `source` (`ledger_full` / `ledger_custom` / `plan`), `method`, `amount_bucket` | finance `payFull` / `savePay` success; `PlanDetailViewModel.savePay` success |
| `clinic_payment_failed` | `source`, `method`, `reason` | the same, on failure |
| `clinic_installment_status_changed` | `status` (`paid` / `pending` / `waived` / `cancelled`), `method` (paid only), `amount_bucket` | `markPaid` / `setStatus` success |
| `clinic_installment_status_failed` | `status`, `reason` | `markPaid` / `setStatus` failure |
| `clinic_installment_reminder_sent` / `_failed` | — / `reason` | `remind` |
| `clinic_financial_record_created` | `record_type` (`invoice` / `receipt` / `estimate` / `credit_note`), `has_plan`, `amount_bucket` (only without a plan) | `saveRecord` success |
| `clinic_financial_record_failed` | `record_type`, `reason` | `saveRecord` failure |
| `clinic_finance_csv_exported` / `clinic_finance_csv_export_failed` | `view` | `FinanceSection`'s export callback (no ViewModel action): the share sheet took the file / could not open |
| `clinic_plan_status_changed` | `status` (the new one), `source` (`list` / `detail`) | `ClinicPlansViewModel.setStatus` / `PlanDetailViewModel.setStatus` success |
| `clinic_plan_status_failed` | `status`, `source`, `reason` | the same, on failure |
| `clinic_plan_pdf_exported` | `renderer` (`server` / `app`) | `PlanDetailViewModel.exportPdf`, the PDF made |
| `clinic_plan_pdf_export_failed` | `reason` (`render_failed` / failure word) | `exportPdf`, no PDF |
| `clinic_plan_wizard_step_reached` | `step` (2–5) | `PlanWizardViewModel.next` moved on |
| `clinic_plan_wizard_abandoned` | `step` | `close()` let the wizard go, or "Discard changes?" → Discard (`abandoned()`, from `PlanWizardScreen`), with no plan made |
| `clinic_plan_created` | `lines_bucket`, `doctors_bucket` (`countBucket`), `total_bucket`, `has_initial_payment`, `has_installments`, `is_activated`, `is_from_patient` | `save` after every request went through |
| `clinic_plan_save_failed` | `stage` (`plan` / `lines` / `payment` / `installments` / `status`), `reason` | `save`, the request that failed (Retry goes on from it) |
| `clinic_service_saved` | `is_new`, `is_all_doctors` | `ClinicServicesViewModel.save` success |
| `clinic_service_save_failed` | `is_new`, `reason` (`invalid` / `code_taken` / failure word) | `save`: refused in the app, or the backend failed |
| `clinic_service_toggled` / `clinic_service_toggle_failed` | `is_active` (the new state) / `reason` | `toggle` |
| `clinic_service_edit_discarded` | none | `discard` |

`reason` is `toFailureReason()` everywhere unless the table names the app's own words.

## Money (D2): every event with an `amountBucket`

All through `amountBucket(minor, currency)` (`foreign` for a non-UZS currency), never the amount:

- `clinic_payment_recorded.amount_bucket`: the payment.
- `clinic_installment_status_changed.amount_bucket`: the installment's amount.
- `clinic_financial_record_created.amount_bucket`: the typed total (left out when a plan gives the totals).
- `clinic_plan_created.total_bucket`: the plan's total.

## Left out, and why

- Quick actions on Overview, member search and role chips, finance search and chips, Load more, expanding a plan's
  visits: navigation and filtering without an outcome; the section and finance screen views cover where people go.
- CSV "no rows": decided inside `FinanceContent` (the content composable), so it can't be tracked without moving
  logic; only the share outcome is tracked.
- Plan PDF share sheet not opening (`pdfShared(false)`): rare, a platform matter; the export outcome is tracked.
- The wizard's initial payment is `has_initial_payment` on `clinic_plan_created`, not a second payment event.
- The clinic doctor schedule (`ClinicSchedule.kt`) reuses the calendar ViewModels: B3's events (with their
  on-behalf property) cover it; the route is the shell's `doctor_schedule`.
- The patient opened from the clinic list is the shell's `patient` route.

## Test fix

`ClinicFinanceViewModelTest` "installments filter on the server…" asserted `calls.last()` right after the first
installments read. The ViewModel's init also reads `members`, and that answer can land after the installments one,
so in a busy full-suite run `last()` was `GET members`. It now asserts `calls.contains(...)`.

## Needs from the coordinator

None.
