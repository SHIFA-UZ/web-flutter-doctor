# Finance · Records list + Create financial record sheet

| | |
|---|---|
| Area | Clinic workspace · Finance |
| Who sees it | All members see the list. "New record" only for owner, clinic admin, receptionist, doctor (nurse read-only). No tier gate. |
| Reached from | Finance chips → "Records"; target: Dashboard → Outstanding / Overdue drill-down record rows |
| Screen type | Finance inner view (list) + full-screen form sheet |
| Current Flutter source | `lib/features/clinic/presentation/finance_records_view.dart`, `clinic_finance_record_dialog.dart`, `lib/state/clinic/clinic_finance_actions.dart` (`createFinancialRecord`) |
| Spec | `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md` §3.4, ticket FIN-03 |

## 1. Purpose
Invoices, receipts, estimates and credit notes issued by the clinic. Most records are created automatically (an invoice when an installment plan is set up, a receipt when a payment is recorded); staff can also create one by hand for a patient, optionally tied to a treatment plan. Used weekly by owners/receptionists; rarely by doctors. There is **no expense / income ledger** in the app — records are patient-facing documents only.

## 2. Entry points
- Finance chip "Records" (the month filter is **not** applied here).
- "New record" button (permitted roles) → create sheet.
- Target: tapping an outstanding/overdue record in the dashboard drill-down opens this view with the record highlighted.

## 3. Layout, top to bottom (phone portrait)
1. **Toolbar**: search "Search by number, type, notes…", and the filled **"New record"** button (permitted roles; target: FAB).
2. **Status chips**: All · Issued · Paid · Partially paid · Overdue · Void.
3. **Record list** (today 8 columns: Created, Type, Number, Total, Paid, Remaining, Status, Due date). Target card row:
   - Line 1: type label ("Invoice") + number ("INV-2026-014" or "#57") bold · status pill right (Issued blue, Paid green, Partially paid orange, Overdue red, Void grey).
   - Line 2: "Created 24.10.2026 · Due 15.11.2026" (due omitted when none).
   - Line 3: "Total 1 200 000 UZS · Paid 400 000 · Remaining 800 000" (remaining red when > 0).
   - Target: patient name line (the row carries patientId; names require a lookup — ask backend to include patientName).
4. Sort: newest first.
5. Infinite scroll, page size 50 (today first page only).
6. **Create financial record** sheet (full screen on phones): fields in order — Patient (dropdown of the clinic's first 50 patients today; target: searchable patient picker sheet reusing file 05 search), Treatment plan (optional, appears after a patient is chosen; options "—" + the patient's plans "title (#id)"), Record type (Invoice · Receipt · Estimate · Credit note; default Invoice), then **either** Subtotal / Discount / Tax amount fields (when no plan; numeric with decimals, default 0) **or** a hint "Amounts come from the linked treatment plan lines. Choose type and optional due date." (when a plan is chosen), Due date (optional; date picker, today −1 y … +5 y), Notes (optional, 2 lines). Footer: Cancel · **Create**. A live "Total = subtotal − discount + tax" line under the amount fields (target).

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Records page | `GET /api/clinics/{id}/finance/records?page=0&size=50` (Spring page; only `content` read today) | Target: paging + server filters |
| Row | id, patientId, treatmentPlanId?, recordType (INVOICE/RECEIPT/ESTIMATE/CREDIT_NOTE), recordNumber?, status (ISSUED/PAID/PARTIALLY_PAID/OVERDUE/VOID — "VOIDED" also seen), uiPaymentStatus (UNPAID/PARTIAL/PAID/NONE/""), subtotal/discount/tax/total/paid/remaining (minor), currency, issuedAt?, dueDate (YYYY-MM-DD)?, notes?, createdAt | Status pill: localised record status; today the raw `uiPaymentStatus` string is shown when present — never show raw enums |
| Dates | createdAt (instant) → dd.MM.yyyy clinic zone; dueDate (calendar date) → dd.MM.yyyy as-is | Due dates are zone-less |
| Money | minor + currency | shared formatter |
| Create payload | `POST /api/clinics/{id}/finance/records {patientId, recordType, subtotalMinor, discountMinor, taxMinor, currency, treatmentPlanId?, recordNumber?, dueDate?, notes?}` | With a plan the three amounts are sent as 0 and the server derives totals |
| Amount input | major units; accepts "1 200 000", "1200000.50", comma decimal | `round(value × 100)`; negative allowed for credit notes? UNVERIFIED — block negatives except for Credit note |

## 5. Primary flow (happy path)
1. Chip "Records" → skeleton rows (6).
2. List renders; receptionist taps "New record".
3. Picks patient (search sheet), leaves plan "—", type Invoice, subtotal 500 000, due date, notes → Create → spinner on button → 200 → sheet closes → banner "Record created" → list reloads with the new Issued invoice on top.

## 6. Screen states
- **First load**: toolbar; 6 skeleton rows.
- **Empty**: rich empty state (keep): icon, "No financial records yet", "Invoices are created when you set up an installment plan. Receipts are generated when you record a payment. You can also create a record manually." + "New record" button for permitted roles.
- **Empty after filter**: "Nothing matches your search or filters." + Clear.
- **Error on load**: banner "Couldn't load records." + Retry.
- **Offline**: cached list; New record disabled.
- **Refreshing**: pull-to-refresh; rows stay.
- **Partial data**: in the sheet, patient list loads but plans fail → inline "Couldn't load plans · Retry" under the plan field; the record can still be created without a plan.
- **Permission needed**: n/a.
- **Locked by tier**: n/a.
- **Role difference**: nurse: no "New record"; others: full. Server 403 → banner.

## 7. Interactions and secondary flows
- **Search** → by number, type, notes (target also patient name once available).
- **Status chip** → filter.
- **Tap row** → target: record detail sheet (all amounts, linked plan link, payments against it, "Void" action if the server supports it — UNVERIFIED; today no row actions at all).
- **New record** → sheet; **Patient** change clears the plan; **Plan** chosen hides amount fields; **Due date** tap → date picker; **Notes** keyboard Done → submit.
- **Cancel / swipe down** with input → "Discard this record?".
- **Create** → validation → POST → close → banner; finance data refresh.

## 8. Edge cases
- Patient picker: the clinic has 1 000 patients → the dropdown of 50 fails; must be a searchable sheet (server `q`).
- Patient with no plans → plan dropdown only "—".
- Plan chosen → amounts derived; if the plan total is 0 the server may reject — show its message.
- Subtotal 0 with discount 0 → allowed? Probably creates a 0 invoice; warn "Total is 0".
- Discount larger than subtotal → total negative → block unless Credit note (open question / UNVERIFIED).
- Huge amounts → grouped display; field must accept 13+ digits.
- Tax field: clinics in Uzbekistan rarely use it — keep but collapsed under "More" (open question).
- Due date in the past → allow (back-dated invoices) but show an "overdue immediately" hint.
- Record number: server-generated; the form never asks for it (payload allows it — keep hidden).
- Duplicate creation (double tap) → button disabled while busy.
- Status "VOID" vs "VOIDED" → one enum from the backend; chips must use the real value.
- 1 000+ records → paging.
- Two staff creating records for the same patient → independent; fine.
- 401 mid-form → logout; input lost (acceptable, short form).
- Rotation → sheet keeps input.
- 200 % font → sheet scrolls; buttons pinned.
- Language switch → type/status labels.
- Screen reader: row "Invoice INV-14, issued, total 1 200 000 UZS, remaining 800 000 UZS, due 15 November 2026".

## 9. Validation and messages
| Field/action | Rule | When checked | Message (EN) | Where shown |
|---|---|---|---|---|
| Patient | required | on Create | "Select a patient" (**NEW**; today the toast says "required") | inline under field |
| Amounts | numeric ≥ 0 (credit note may be negative — UNVERIFIED) | on blur + Create | "Enter a valid amount" (`clinicFinanceInvalidAmount`) | inline |
| Total | subtotal − discount + tax ≥ 0 | on Create | "Total can't be negative" (**NEW**) | inline summary |
| Create | HTTP 200/201 | on response | `clinicRecordsFormSuccess` "Record created" / `clinicRecordsFormFailed` "Could not create record" + server message | banner |
| Load | HTTP 200 | on load | "Couldn't load records." (**NEW**) | banner + Retry |

## 10. Navigation out
- Sheet closes to the list (refreshed on success).
- Tap plan link in a record (target) → plan detail (file 16).
- Chips → other finance views.

## 11. Copy
- `clinicFinanceRecords` "Records", `clinicRecordsSearchHint` "Search by number, type, notes…", `clinicRecordsNewRecord` "New record"
- Status: `clinicRecordStatusIssued` "Issued", `clinicRecordStatusPartiallyPaid` "Partially paid", `clinicRecordStatusOverdue` "Overdue", `clinicRecordStatusVoid` "Void", `clinicPaymentStatusPaid` "Paid"
- Types: `clinicRecordTypeInvoice` "Invoice", `clinicRecordTypeReceipt` "Receipt", `clinicRecordTypeEstimate` "Estimate", `clinicRecordTypeCreditNote` "Credit note"
- Columns: `clinicRecordsColCreated` "Created", `clinicRecordsColType` "Type", `clinicRecordsColNumber` "Number", `clinicRecordsColTotal` "Total", `clinicRecordsColPaid` "Paid", `clinicRecordsColRemaining` "Remaining", `clinicRecordsColStatus` "Status", `clinicRecordsColDue` "Due date"
- Empty: `clinicRecordsEmptyTitle` "No financial records yet", `clinicRecordsEmptyBody` "Invoices are created when you set up an installment plan. Receipts are generated when you record a payment. You can also create a record manually.", `clinicTableNoFilteredResults` "Nothing matches your search or filters."
- Form: `clinicRecordsFormTitle` "Create financial record", `clinicRecordsFormPatient` "Patient", `clinicRecordsFormPlanOptional` "Treatment plan (optional)", `clinicRecordsFormType` "Record type", `clinicRecordsFormSubtotal` "Subtotal", `clinicRecordsFormDiscount` "Discount", `clinicRecordsFormTax` "Tax", `clinicRecordsFormPlanTotalsHint` "Amounts come from the linked treatment plan lines. Choose type and optional due date.", `clinicRecordsFormDueDate` "Due date (optional)", `clinicRecordsFormNotes` "Notes (optional)", `clinicRecordsFormCreate` "Create", `cancel` "Cancel", `clinicPatientNumber` "Patient #{{id}}"
- **NEW**: "Select a patient", "Total can't be negative", "Couldn't load records.", "Couldn't load plans · Retry", "Discard this record?", "Total is 0", "Total = {{amount}}"

## 12. Known problems in the current app (do not carry over)
- Status pill shows the raw `uiPaymentStatus` enum when present.
- Patient dropdown contains only the first 50 clinic patients; no search.
- Only page 0 (50 records) is ever loaded; no month filter although every other finance view has one.
- "Required" toast without naming the field; red toast colour for failure.
- No record detail, no way to see which payments settled a record, no void action.
- VOID vs VOIDED inconsistency between views.

## 13. Open questions for design
- Should Records have the shared month filter (by created date)?
- Record detail sheet: what actions (void, share PDF, open plan)?
- Hide Tax behind "More options" for the Uzbek market?
