# Treatment plans list

| | |
|---|---|
| Area | Clinic workspace · Treatment plans |
| Who sees it | All members. Status change and "New plan" are open to every member today (server UNVERIFIED). No tier gate. |
| Reached from | Clinic workspace root → "Treatment plans" sub-tab; Dashboard Outstanding/Collection drill-down rows (target); patient detail "Treatment plans" section (area 06) |
| Screen type | Sub-tab (list with toolbar, expandable rows) |
| Current Flutter source | `lib/features/clinic/presentation/clinic_treatment_plans_tab.dart`, `lib/state/clinic/clinic_treatment_plan_providers.dart`, `clinic_treatment_plan_actions.dart` |
| Spec | `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md` §4.1, §4.5, tickets TP-01, TP-02 |

## 1. Purpose
Every comprehensive treatment plan of the clinic in one place: who, what, how far along, how much is paid, and its lifecycle status. Doctors open it daily to find a patient's plan before a visit; owners to spot unpaid plans. Per-visit "automatic" plans (kind VISIT) are hidden here.

## 2. Entry points
- Workspace sub-tab "Treatment plans" (filters reset on app restart).
- "New plan" button → wizard (file 15) without a preselected patient.
- Target: dashboard drill-down rows and finance rows carrying a plan id → this list scrolled to the plan, or directly to the detail.
- After the wizard saves, the list refreshes and the new plan appears first (sorted by updated).

## 3. Layout, top to bottom (phone portrait)
1. **Toolbar**: search "Search by patient or plan title" (300 ms debounce) and the filled **"New plan"** button (target: FAB).
2. **Status chips**: All · ACTIVE · DRAFT · COMPLETED · CANCELLED (IN PROGRESS and ON HOLD exist as statuses but are not offered as chips — target: add them or an "Open" grouping).
3. **Plan list** (today a 12-column table). Target card per plan:
   - Line 1: title (bold; "(untitled plan)" fallback) · **status pill** right (Draft grey, Active blue, In progress indigo, On hold amber, Completed green, Cancelled red) with a caret — tapping opens the status menu (file 17).
   - Line 2: patient name · attending doctor(s) (comma-joined, 2 lines max).
   - Line 3: progress "3/7 planned items completed" (only when the plan has lines) and the **payment pill** (Paid / Partial / Unpaid / None).
   - Line 4: "Total 2 400 000 · Paid 1 000 000 · **Outstanding 1 400 000**" (outstanding red when > 0) · "Updated 24.10.2026".
   - Visits toggle: "3 visits ▾" expands an inline list of visit rows; "No visits" when 0.
   - Trailing overflow: Open · Export PDF.
   - Tap card → plan detail (file 16).
4. **Expanded visit rows** (grey background): "Visit #881" + timing pill (Upcoming blue / Past grey / Cancelled red), services joined, "24.10.2026 · Dr Name", "Total · Collected · Owed", appointment status pill (Requested / Confirmed / In progress / Completed / Cancelled), visit payment pill. Tap opens the appointment screen (in-person or video) unless cancelled (then banner "This visit was cancelled.").
5. Default sort: updated desc; table allowed sorting by every column; phone sort menu: Updated · Patient · Outstanding · Status.
6. Server paging (target); today the full list for the clinic is loaded in one request.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Plans | `GET /api/treatment-plans?clinicId=&planKind=COMPREHENSIVE` (server also accepts `status`, `q`, `patientId` — unused today) → summaries {id, patientId/Name, attendingDoctorId/Name, attendingDoctors[{id,name}], title, diagnosis, status, notes, paymentReminderDays, planKind, symptoms[], totalMinor, paidMinor, owedMinor, currency, planPaymentStatus, visitCount, linesCompletedCount, linesTotalCount, createdAt, updatedAt} | Target: server `status` + `q` + paging |
| Visits | `GET /api/treatment-plans/{id}/visits` on expand (cached per plan until status change / clinic switch) | {appointmentId, startAt, endAt, status, doctorName, location, services[], visitTotal/Collected/Owed, visitPaymentStatus, timing} |
| Money | minor + currency | **Shared formatter** (UZS grouped, no decimals). Today this tab prints "24000.00 UZS" — different from finance |
| Dates | updatedAt / startAt instants | dd.MM.yyyy in **clinic zone** (today `yyyy-MM-dd` in the phone's zone) |
| Progress | linesCompleted/linesTotal | "{{done}}/{{total}} planned items completed"; hide when total = 0 |
| Status labels | DRAFT, ACTIVE, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED | Today labels are upper-case ("ACTIVE", "ON HOLD"); target sentence case |
| Doctor | attendingDoctors names joined; fallback attendingDoctorName; "—" | — |

## 5. Primary flow (happy path)
1. Tap sub-tab → skeleton cards (5).
2. Plans render newest-updated first.
3. Doctor types "Karim" → list narrows (client-side today; server target).
4. Taps "3 visits" → visit rows expand with a small inline loader "Loading visits…", then rows.
5. Taps a visit → appointment screen (area 05). Back → list, expansion kept.

## 6. Screen states
- **First load**: toolbar; 5 skeleton cards.
- **Empty**: "No treatment plans for this patient yet." (copy says "for this patient" even on the clinic-wide list — **NEW** copy needed: "No treatment plans in this clinic yet.") + "New plan" button.
- **Empty after filter**: "Nothing matches your search or filters." + Clear.
- **Error on load**: banner "Couldn't load treatment plans." + Retry.
- **Offline**: cached list; status change, New plan and PDF disabled.
- **Refreshing**: pull-to-refresh (target; none today); after a status change the list reloads — keep rows, update in place.
- **Partial data**: visits for one plan fail → that plan's expansion shows "Couldn't load visits · Retry"; others unaffected.
- **Permission needed**: n/a.
- **Locked by tier**: n/a.
- **Role difference**: none today. Target: staff can view and create plans (receptionist books visits) but status changes limited to doctors/owners? UNVERIFIED — design the pill as read-only when the server says so.

## 7. Interactions and secondary flows
- **Search / chips** → filter (target server-side with debounce).
- **Tap card / "Open"** → plan detail (file 16).
- **Tap status pill** → status menu (file 17); Cancelled asks for confirmation.
- **Tap visits toggle** → expand/collapse; lazy load once.
- **Tap visit row** → appointment screen; cancelled visit → banner only.
- **Overflow → Export PDF** → plan PDF (file 16 §7); today web-only, mobile shows "PDF download is available in the browser version…".
- **New plan** → wizard (file 15).
- **Pull-to-refresh** → reload (target).

## 8. Edge cases
- 0 plans → empty with CTA; 1 plan; 500 plans → paging; sort by updated keeps recent work on top.
- Plan with 0 lines → no progress line; total 0; payment "None"; status pill still editable; wizard prevents creating such plans (but a failed save can leave one — file 15 §12) → show a small "Incomplete" tag when linesTotal = 0 and status DRAFT.
- Untitled plan → "(untitled plan)".
- Patient name missing → "—"; plan still openable.
- 2+ attending doctors → "Dr A, Dr B"; 5 doctors → "Dr A, Dr B +3".
- Visit count says 3 but visits endpoint returns 0 (cancelled filtered?) → show "No visits linked to this plan yet."
- Visit at 23:30 clinic time on the 31st → shows the 31st (clinic zone), not the phone's date.
- Video visit vs in-person decided by location text containing "video" today — area 05 fixes; the list just passes the visit through.
- Status changed by another user meanwhile → our change may overwrite; after any change reload the row; 409 → banner.
- Plan cancelled while the wizard for the same patient is open → no conflict (new plan).
- Plan currency differs from clinic currency → each card shows its own currency.
- Huge totals → grouped; outstanding negative never (server floors).
- 401 → logout. 404 on open → "Treatment plan not found." banner and remove row.
- Rotation/tablet → the 12-column table is acceptable at ≥ 1 100 dp with expandable rows.
- 200 % font → cards 6–7 lines; pills wrap under text.
- Language switch → status/payment labels re-render; titles untouched.
- Screen reader: card "Dental restoration, active, Karimova, Dr Rahimov, 3 of 7 completed, partial, outstanding 1 400 000 UZS, button"; expansion announced.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (EN) | Where shown |
|---|---|---|---|---|
| Load | HTTP 200 | on load | "Couldn't load treatment plans." (**NEW**) | banner + Retry |
| Visits load | HTTP 200 | on expand | "Couldn't load visits · Retry" (**NEW**) | inline |
| Open cancelled visit | visit status ≠ CANCELLED | on tap | `clinicTreatmentPlansVisitCancelledOpen` "This visit was cancelled." | banner |
| Status change | see file 17 | — | — | — |

## 10. Navigation out
- → Plan detail (file 16), → appointment screen (area 05), → wizard (file 15), → PDF share.
- Returning from the wizard: list refreshed; from the appointment: visits of that plan refreshed (fulfilment may have changed progress).

## 11. Copy
- `clinicWorkspaceTreatmentPlans` "Treatment plans", `clinicTreatmentPlansFilterHint` "Search by patient or plan title", `clinicTreatmentPlansNew` "New plan", `clinicTreatmentPlansAll` "All"
- Status: `clinicPlanStatusDraft` "DRAFT", `clinicPlanStatusActive` "ACTIVE", `clinicPlanStatusInProgress` "IN PROGRESS", `clinicPlanStatusOnHold` "ON HOLD", `clinicPlanStatusCompleted` "COMPLETED", `clinicPlanStatusCancelled` "CANCELLED" (target: sentence case values)
- Payment: `clinicPaymentStatusPaid` "Paid", `clinicPaymentStatusPartial` "Partial", `clinicPaymentStatusUnpaid` "Unpaid", `clinicPaymentStatusNone` "None"
- Columns/labels: `clinicPlansColId` "ID", `clinicPlansColTitle` "Title", `clinicTreatmentPlansVisits` "Visits", `clinicTreatmentPlansPatient` "Patient", `clinicTreatmentPlansDoctor` "Attending doctor", `clinicTreatmentPlansTotal` "Total", `clinicTreatmentPlansPaid` "Paid", `clinicTreatmentPlansOutstanding` "outstanding", `clinicTreatmentPlansStatus` "Plan status", `clinicTreatmentPlansPaymentStatus` "Payment status", `clinicTreatmentPlansUpdated` "Updated", `clinicPlansColActions` "Actions", `clinicPlansViewTooltip` "View plan details", `clinicTreatmentPlanExportPdf` "Export PDF", `clinicTreatmentPlansUntitled` "(untitled plan)", `dentalPlanProgress` "{{done}}/{{total}} planned items completed"
- Visits: `clinicTreatmentPlansVisitCount` "{{count}} visits", `clinicTreatmentPlansNoVisitsShort` "No visits", `clinicTreatmentPlansVisitsLoading` "Loading visits…", `clinicTreatmentPlansNoVisits` "No visits linked to this plan yet.", `clinicTreatmentPlansVisitLabel` "Visit", `clinicTreatmentPlansVisitUpcoming` "Upcoming", `clinicTreatmentPlansVisitPast` "Past", `clinicTreatmentPlansVisitCancelled` "Cancelled", `clinicTreatmentPlansOpenVisit` "Open visit", `clinicTreatmentPlansVisitCancelledOpen` "This visit was cancelled.", appointment statuses `appointmentStatusRequested/Confirmed/Cancelled/Completed/InProgress`
- Empty: `clinicTreatmentPlansEmpty` "No treatment plans for this patient yet." (wrong context), `clinicTableNoFilteredResults`
- **NEW**: "No treatment plans in this clinic yet.", "Couldn't load treatment plans.", "Couldn't load visits · Retry", "Incomplete", "+{{n}}"

## 12. Known problems in the current app (do not carry over)
- 12-column spreadsheet; expanded visits are extra table rows.
- Money printed as "24000.00 UZS" (2 decimals, no grouping) unlike the finance views.
- Dates in the phone's time zone, `yyyy-MM-dd`.
- Search/status filters applied client-side although the server supports them; whole clinic list loaded at once.
- Empty copy talks about "this patient" on the clinic-wide list.
- Status labels are upper-case constants; In progress / On hold cannot be filtered.
- Plan PDF export is web-only.
- A listener per expanded plan is registered on every rebuild (performance).

## 13. Open questions for design
- Card density: is the 4-line card too heavy? Alternative: 2 lines + a detail sheet.
- Group by status (Open / Completed / Cancelled) with section headers instead of chips?
- Should visits expand inline or live only in the detail?
