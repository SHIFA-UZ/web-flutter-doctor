# Treatment plan detail

| | |
|---|---|
| Area | Clinic workspace · Treatment plans |
| Who sees it | All members. Read-only today (status and payments are changed elsewhere). No tier gate. |
| Reached from | Treatment plans list (card tap / Open); finance rows and dashboard drill-downs (target); push notification about a plan (area 07, UNVERIFIED type); patient detail plans section (area 06) |
| Screen type | Full screen pushed inside the workspace (tab bar hidden); route `/app/clinic/treatment-plan` with the plan id |
| Current Flutter source | `lib/features/clinic/presentation/clinic_treatment_plan_detail_screen.dart`, `treatment_plan_pdf_export.dart`, `pdf/treatment_plan_pdf_*.dart`, `lib/state/clinic/clinic_treatment_plan_providers.dart` |
| Spec | `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md` §4.3, §4.4, tickets TP-03, TP-05 |

## 1. Purpose
The single source of truth for one plan: overview and status, the dental chart (if any), every line with its status and linked visit, the visits, the money (total / paid / outstanding, installment schedule) and a shareable PDF. Doctors open it before and after visits; receptionists when the patient asks "what do I still owe".

## 2. Entry points
- List card tap / Open icon → pushed with the plan id.
- Target: ledger/payments/installments rows, dashboard drill-downs, patient detail, wizard success banner "Open plan".
- Deep link by id works even if the list is not loaded (route takes only the id).

## 3. Layout, top to bottom (phone portrait)
1. **App bar**: back, title "#412 · Dental restoration" (ellipsis), action PDF icon "Export PDF". Target: overflow with Change status (file 17), Record payment, Export PDF.
2. **Overview card**: title (headline), pills row: status, payment status, progress chip "3/7 planned items completed" (check icon when complete); key/value rows: Patient, Attending doctor(s), Diagnosis, Symptoms (if any), Notes; divider; Total, Paid, **Outstanding** (bold red when > 0), Updated. Target: patient row tappable → patient detail; a primary button "Record payment" when outstanding > 0 and the user may act (opens the payment sheet of file 08 with plan context).
3. **Dental chart card** (only when the plan carries dental documentation): read-only chart with per-tooth counts and line states (planned / partially completed / completed legend).
4. **Services card**: list of lines sorted by order (today a 5-column table): title, "× qty", line total, line status pill (Planned / Scheduled / In progress / Completed / Cancelled), linked visit "#881" (target: tappable, shows date). Target also: tooth tag "16" for dental lines.
5. **Installments card** (target; the PDF already renders it, the screen does not): schedule rows "#1 · 15.11.2026 · 700 000 UZS · Pending/Paid/Overdue…" with the same status pill as file 11.
6. **Payments card** (target): payments recorded against the plan (date, method, amount, memo) — from the payments endpoint filtered by plan.
7. **Visits card**: per visit: "Visit #881" (bold), "24.10.2026 · Dr Name", services joined (2 lines), "Total: 600 000 UZS", trailing timing pill + open icon; tap opens the appointment (cancelled visits are not openable → banner).
8. Pull-to-refresh on the whole screen (target; none today).

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Detail | `GET /api/treatment-plans/{id}/detail` → {summary, lines[], installmentPlans[{installmentPlanId, status, totalAmountMinor, currency, numInstallments, scheduleRows[{installmentItemId, sequenceNumber, dueDate, amountMinor, currency, status}]}], dentalPlanDocumentation? (JSON)} | Cached per plan; **must refresh** on return from a visit, after payments/installment changes and on pull — today it is cached forever in the session |
| Visits | `GET /api/treatment-plans/{id}/visits` | same shape as the list expansion |
| Payments (target) | `GET /api/clinics/{id}/finance/payments?treatmentPlanId=` (parameter UNVERIFIED) | — |
| Money | shared formatter (today "24000.00 UZS") | — |
| Dates | updatedAt / visit startAt (instants) → dd.MM.yyyy HH:mm clinic zone; dueDate → dd.MM.yyyy | today `yyyy-MM-dd` phone zone |
| Line status | PLANNED / SCHEDULED / IN_PROGRESS / COMPLETED / CANCELLED | Planned / Scheduled / In progress / Completed / Cancelled |
| Tooth | `specialtyMetadata.fdi` | "Tooth 16" tag |
| PDF | today: built on device from the detail (A4 portrait; EN/UZ only, RU falls back to EN; filename `treatment_plan_<id>_<yyyy-MM-dd>.pdf`); web download only | Target: `GET /api/treatment-plans/{id}/pdf?lang=` → share sheet (file 13 rules) |

## 5. Primary flow (happy path)
1. Tap a plan → screen pushes with the title from the list immediately; body skeleton (overview card lines, 3 line rows, 2 visit rows).
2. Detail arrives → cards fill; visits arrive separately (own skeleton inside the card).
3. Doctor taps a visit → appointment screen → fulfils lines → back → detail **refreshes**: progress 4/7, line statuses updated.
4. Receptionist taps "Record payment" → sheet (file 08 variant prefilled with outstanding) → Save → banner → Outstanding and payment pill update.
5. Tap PDF → "Preparing PDF…" (non-blocking banner) → share sheet.

## 6. Screen states
- **First load**: title known; skeleton cards.
- **Empty**: plan with 0 lines → Services card shows "No services on this plan yet" + "Incomplete plan" tag in the overview (and a button "Add services" once file 17 editing exists); no visits → "No visits linked to this plan yet."; no installments → card hidden.
- **Error on load**: banner "Couldn't load the plan." + Retry; 404 → "Treatment plan not found." with a Back button.
- **Offline**: cached detail if seen before; actions disabled; PDF disabled.
- **Refreshing**: pull-to-refresh and automatic refresh on return; cards keep height.
- **Partial data**: detail ok, visits fail → Visits card inline error + Retry; detail fails but visits ok → treat as error (detail is required).
- **Permission needed**: n/a.
- **Locked by tier**: n/a.
- **Role difference**: nurse: no Record payment / status actions; others: full (server UNVERIFIED).

## 7. Interactions and secondary flows
- **Tap visit** → appointment screen (video or in-person); cancelled → banner "This visit was cancelled."
- **Tap linked visit "#881" on a line** → scroll to that visit / open it.
- **Tap patient** (target) → patient detail (clinic context).
- **Record payment** (target) → payment sheet with plan id, outstanding, methods, memo; `linkedAppointmentId` empty.
- **Change status** (target overflow) → file 17 menu.
- **Export PDF** → progress → share sheet; today on mobile: toast "PDF download is available in the browser version of the clinic workspace." (do not carry over).
- **Pull-to-refresh** → reload detail + visits.
- **System back** → list (or wherever it came from); the list refreshes the plan row if status/progress changed.

## 8. Edge cases
- 0 lines / 1 line / 40 lines (full-mouth dental) → lines card grows; consider collapsing lines per tooth group with counts.
- Long title (80 chars) → app bar ellipsis, full title in the overview card wrapping to 3 lines.
- Diagnosis/notes very long → show 4 lines + "More".
- Plan in a second currency → every amount shows its own currency; "Record payment" uses the plan currency.
- Payment larger than outstanding → outstanding 0, payment pill Paid; overpayment not surfaced (no credit concept) — show "Overpaid by …" if paid > total (UNVERIFIED whether server allows).
- Partial payments → Paid grows, pill Partial; the Payments card lists each.
- Installment schedule with some items paid and some overdue → status per row; plan-level pill Partial.
- Plan cancelled → status pill red, "Record payment" hidden, lines stay; a banner "This plan is cancelled. Recorded payments and installments are kept."
- Plan completed by the server after the last fulfilment → status Completed on refresh; progress 7/7.
- Two staff editing the same plan → no editing here today; after file 17 actions, use the server response as truth and refresh.
- Member who created the plan left the clinic → attending doctor shows the stored name; fine.
- Deleted/404 plan → not-found state.
- Visit at 00:10 clinic time → date in clinic zone.
- 401 → logout.
- Rotation/tablet → two columns: overview + chart left, lines/visits right.
- 200 % font → key/value rows stack label over value (already done under 360 dp).
- Language switch (on return) → labels re-render; PDF language = app language at export time.
- Screen reader: title "Plan 412, Dental restoration"; pills announced; visits as buttons.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (EN) | Where shown |
|---|---|---|---|---|
| Load detail | HTTP 200 | on open / refresh | "Couldn't load the plan." (**NEW**) / `clinicTreatmentPlanDetailNotFound` "Treatment plan not found." | banner + Retry / body |
| Load visits | HTTP 200 | on open | "Couldn't load visits · Retry" (**NEW**) | inline card |
| Export PDF | detail available, request ok | on tap | `clinicTreatmentPlanExportPdfPreparing` "Preparing PDF…", `clinicTreatmentPlanExportPdfFailed` "Could not export treatment plan PDF", `clinicTreatmentPlanExportPdfNoDetail` "Could not load plan details." | banner |
| Open cancelled visit | — | on tap | `clinicTreatmentPlansVisitCancelledOpen` "This visit was cancelled." | banner |
| Record payment | see file 08 §9 | — | — | — |

## 10. Navigation out
- Back → caller; the plans list refreshes that row.
- → Appointment screen; on return refresh detail + visits.
- → Payment sheet; on save refresh detail, finance.
- → PDF share sheet.
- → Patient detail (target).

## 11. Copy
- `clinicTreatmentPlanDetailTitle` "Treatment plan", `clinicTreatmentPlansUntitled` "(untitled plan)", `clinicTreatmentPlanDetailNotFound` "Treatment plan not found.", `clinicTreatmentPlanDetailDentalChart` "Dental chart", `treatmentPlanWizardSectionServices` "Treatments / services" (card title), `clinicTreatmentPlansVisits` "Visits"
- Overview: `clinicTreatmentPlansPatient` "Patient", `clinicTreatmentPlansDoctor` "Attending doctor", `treatmentPlanDiagnosis` "Diagnosis", `treatmentPlanWizardSymptoms` "Symptoms (comma separated)" (label reused — target "Symptoms"), `treatmentPlanNotes` "Notes", `clinicTreatmentPlansTotal` "Total", `clinicTreatmentPlansPaid` "Paid", `clinicTreatmentPlansOutstanding` "outstanding" (target "Outstanding"), `clinicTreatmentPlansUpdated` "Updated", `dentalPlanProgress` "{{done}}/{{total}} planned items completed", status and payment labels as in file 14
- Lines: `clinicPlansColTitle` "Title", `treatmentPlanWizardQty` "Qty", `clinicTreatmentPlanLineStatus` "Line status", `clinicTreatmentPlanLineStatusPlanned` "Planned", `clinicTreatmentPlanLineStatusScheduled` "Scheduled", `clinicPlanStatusInProgress`/`Completed`/`Cancelled` reused, `treatmentPlanWizardLineAppt` "Link to visit (optional)" (column header — target "Visit")
- Dental legend: `dentalPlanLegendPlanned` "Planned", `dentalPlanLegendPartial` "Partially completed", `dentalPlanLegendCompleted` "Completed"
- Visits: `clinicTreatmentPlansVisitLabel` "Visit", `clinicTreatmentPlansVisitsLoading` "Loading visits…", `clinicTreatmentPlansNoVisits` "No visits linked to this plan yet.", `clinicTreatmentPlansOpenVisit` "Open visit", timing labels as in file 14
- PDF: `clinicTreatmentPlanExportPdf` "Export PDF", `clinicTreatmentPlanExportPdfPreparing`, `clinicTreatmentPlanExportPdfFailed`, `clinicTreatmentPlanExportPdfNoDetail`, `clinicTreatmentPlanExportPdfWrongPlatform` (drop)
- **NEW**: "Couldn't load the plan.", "Couldn't load visits · Retry", "Record payment", "Installments", "Payments", "No services on this plan yet", "Incomplete plan", "Tooth {{fdi}}", "This plan is cancelled. Recorded payments and installments are kept.", "Overpaid by {{amount}}", "More"

## 12. Known problems in the current app (do not carry over)
- The detail is cached for the whole session and never refreshed after payments, fulfilment or status changes → stale totals and a stale PDF.
- Completely read-only: no status change, no payment, no editing, although the list offers status changes.
- Installment schedule and payments are rendered in the PDF but not on the screen.
- Money "24000.00 UZS", dates in the phone's zone.
- PDF is built on the device, English/Uzbek only, and cannot be exported on mobile at all ("browser version" toast).
- Lines in a 5-column horizontally scrolling table.

## 13. Open questions for design
- Tabs inside the detail (Overview · Services · Visits · Money) or one long scroll with cards?
- Should the dental chart be the hero for dental plans (above the overview)?
- Share the PDF directly to the patient's chat (area 07) from here?
