# Clinic workspace, finance, treatment plans (M11)

Spec `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md`, flows `docs/ux-flows/09-clinic-workspace/`, board
`DaClinic` (`docs/design/boards/DaClinic.dc.html`). Everything in the workspace is in the **clinic's** time zone.

## CLINIC-01 · Memberships, selected clinic, permissions

Built 2026-11-06. Spec 09 §1, §6, §8, ticket CLINIC-01.
- `ClinicsRepository` (`data/clinics`) keeps `GET /me/clinics` in `state` (one shared instance: the shell, the visit's
  plan panel and the workspace see the same list). A failed read keeps the last list (sticky: the Clinic tab doesn't
  vanish on a bad connection). The chosen clinic is remembered per user under Flutter's key
  `clinic_workspace_selected_id:<user>` (the token's subject; `Jwt.subject` added), survives a restart, and a clinic
  the user left falls back to the first. `selected()` reads the list when it isn't known yet or belongs to another
  sign-in. The shell's `onResume` read refreshes it.
- One permission helper (Flutter had three copies): `MembershipRole` (unknown roles may do nothing),
  `MyClinic.mayManageFinanceSettings` (the backend's flag, or owner / clinic admin), `MyClinic.mayManageFinance`
  (owner, clinic admin, receptionist, doctor), `mayEditRevenueShare(member)` (doctor or owner).
- Codex review: another sign-in starts from nothing (the previous account's clinics never show) and a late answer
  for an earlier user is dropped; the shell's Clinic entry follows every read of the memberships.
- Not ported: `practiceSessionProvider` (dead in Flutter, 09 §1.3).
- Verified on dev 2026-11-06 (`GET /me/clinics` as the seed doctor: clinic 2, OWNER, `canManageFinanceSettings`).
- Tests: `ClinicsRepositoryTest` (default first, remembered per user across restarts, a left clinic, sticky failure,
  the permission helper).

## CLINIC-02/03/04 · Workspace, Overview, Settings

Built 2026-11-06. Spec 09 §1.5–1.7, §2, flows 09/01 and 09/20, tickets CLINIC-02, CLINIC-03, CLINIC-04. Board
`DaClinic` root, Overview and Settings.
- **Shell:** the Clinic tab (More for doctors with a clinic; a bar tab for staff, already in SHELL-02) shows the
  workspace (`ui/clinic/ClinicWorkspace.kt`).
- **Header** (board): "Clinic", the `.dr-context` block (name, "time zone · address", "Your role: Owner (primary
  practice)"), a switcher sheet with 2+ clinics (address under same-named ones, flow 09/01 §8) that every section
  follows, and the gear for Settings. Kit fix: `ContextSwitcher` puts the role line under the meta line, in the main
  column, as the board's `.dr-context__role` (it sat on the right and squeezed the name).
- **Sections** as the board groups them (Flutter had nine flat tabs): Overview (Overview · Calendar), People
  (Doctors · Invitations; no invitations for staff), Patients, Plans, Finance (Finance · Services), Settings behind the
  gear. Each later ticket fills its section (`ClinicSections.kt`).
- **Overview:** today's appointments, this month's patients, active doctors (`GET /clinics/{id}/overview-stats`;
  a tap opens Calendar / Patients / Doctors, the flow's target), quick actions (My calendar for doctors only — staff
  had a dead button —, Calendar, Patients), contact rows that dial, mail (and the address). Stats failing leave the
  rest with "Couldn't load the clinic's numbers. · Retry"; no clinic → the empty state; no list and a failure → the
  error with Retry; switching clinics clears the old numbers at once. Not shown: the board's revenue tile (FIN-02's data) and "Today at the clinic" (needs every
  doctor's day; the Calendar section has it).
- **Settings** (read-only, flow 09/20): the admin-managed note, name, time zone, phone, email, address, the default
  revenue share ("Doctor 60% · Clinic 40%" / "Not set"). The board's Contact admin, Primary practice switch and
  Leave clinic have no backend (BE-24) and aren't shown.
- **CLINIC-03 kit:** the design system's `ContextSwitcher`, `UnderlineTabs`, `SegmentedTabs`, `KpiCard`,
  `DataTableCard`, `PersonCell`, skeletons and `utils/Money` already cover the clinic lists; tickets add only what
  they need.
- New strings: `clinicGroupPeople`, `clinicYourClinics`, `clinicSettingsTitle`, `clinicLoadFailed`,
  `clinicStatsFailed` (en/ru/uz).
- **Checked:** emulator against dev (reads only): the seed doctor's clinic 2 — header, groups, the numbers (0 today,
  3 patients, 3 doctors), contact rows, Settings. iOS compiled, not run.
- **Tests:** `ClinicWorkspaceViewModelTest` (load and numbers, switching re-reads the other clinic, error and
  partial states, the board's groups); screenshots `Clinic_overview`, `Clinic_settings`, `Clinic_empty`,
  `Workspace_workspace_chrome` re-recorded (the role line).

## CLINIC-05 + CLINIC-08 · People: doctors, revenue share, invitations

Built 2026-11-06. Spec 09 §2 (rows 1 and 7), §3.8, flows 09/02, 09/03, 09/19, tickets CLINIC-05 and CLINIC-08. Board
`DaClinic` Doctors, Invitations and the "share" / "invite" sheets (`ui/clinic/ClinicPeople.kt`, one ViewModel per
clinic).
- **Doctors:** who may set shares (`mayManageFinanceSettings`) gets the hint and "Default share"; search (name, role,
  profile #), role chips built from the roles present, members sorted by name: avatar, name ("Doctor #12" when empty;
  Flutter showed nothing), role, and the share in force ("60% doctor / 40% clinic" / "Not set") for doctors and owners
  only — a link when it can be edited. The schedule button comes with CLINIC-09.
- **Revenue share sheet** (one for a doctor and the clinic default, the board's "share"): the doctor share as a number
  and the kit slider (the clinic keeps the rest), a whole number 0–100 only (Save disabled otherwise), "Use clinic default" (a
  doctor) / "Clear default" (the clinic), Save; `PATCH …/members/{doctorProfileId}/revenue-share` or
  `PATCH …/finance-settings` (a cleared share sends no field; the backend reads it as null). After saving the list
  reads again; a changed default re-reads the clinics (Settings and every share in force follow). Failures stay in
  the sheet.
- **Invitations:** "Invite receptionist", Pending and Used / expired groups (pending and past `expiresAt` counts as
  expired), "Expires 13.11.2026 06:59" in the clinic's time zone, Revoke after a confirmation
  (`DELETE …/invitations/{id}`). The invite sheet names the clinic, needs a valid email that isn't already pending,
  sends it trimmed and lower-cased with role `RECEPTIONIST`. Resend: no endpoint (hidden).
- **Codex review:** invitations are read with `?status=all` (the default hides used and expired ones); the share
  field keeps what was typed and saves only a whole number 0–100 (clearing is its own action); a doctor on the
  default sees its split; read failures show over kept lists; the content takes callbacks (`PeopleActions`).
- **Demo:** `DemoClinic` keeps the demo clinic's numbers, members with their shares and invitations in memory; the demo
  doctor owns the clinic so every action can be tried.
- New strings: invitation sheet and confirmation, groups, share sheet lines and results, members/invitations errors.
- **Checked:** emulator, demo mode: a doctor's share back to the clinic default ("Now using the clinic default."),
  the default to 50 % with the slider (the owner without an own share follows, Bobur keeps 70 %), validation (506 →
  "Enter a whole number from 0 to 100", Save off), an invitation sent and revoked. Dev: members read with curl (3
  members, shares null). iOS compiled, not run.
- **Tests:** `ClinicPeopleViewModelTest` (search, chips, sort; share save / clear / default and the failure; invite
  validation, duplicate, lower-casing, revoke); screenshots `Clinic_doctors`, `Clinic_invitations`.

## CLINIC-06 · Clinic patients, the patient in the clinic's scope

Built 2026-11-06. Spec 09 §2 row 3, 06 §1 (clinic scoping) and §2.9, flows 09/05 and 06/05 §3.2, ticket CLINIC-06. Board
`DaClinic` Patients (`ui/clinic/ClinicPatients.kt`); the prophylaxis card is not in the design (`ui/patients/Prophylaxis.kt`,
Medical info's card style).
- **List:** `GET /clinics/{id}/patients?page&size=50&q`: search on the server (name, phone or email; Flutter filtered
  one page on the device) once typing pauses (350 ms, trimmed, only when it changed; an answer for an older search is
  dropped), "Total: N" from `totalElements`, cards with avatar, name, "phone · email" (a dash without a phone), "Load
  more" while pages follow (no repeats; a failed next page keeps the rows). Rows without a patient id are skipped.
  Empty: "No patients match this clinic roster for your access level."
- **Patient in the clinic's scope:** `ShellRoute.Patient(id, clinicId)` → the detail reads `GET /patients/{id}?clinicId`;
    documents, the viewer, uploads, request-access and the 025-2 editor (its patient read and its PDF) carry the
  `clinicId`. A clinic read refreshes a patient of the doctor's own directory but never adds another doctor's patient
  to it. Phone only: a push, no tablet side pane (the app has no tablet layout yet).
- **Prophylaxis card** (Medical info, clinic scope only): `GET /prophylaxis/settings?patientId&clinicId`, interval
  1–60 months (default 12), "Send reminders" (default on), "Last sent" as a date in the clinic's zone, Save →
  `PUT /prophylaxis/settings {patientId, clinicId, intervalMonths, enabled}`. A failed read shows "Could not load
  prophylaxis settings" + Retry and no Save (Flutter fell back to the defaults, which Save would write over the
  server's values). Dev answers "no setting" with an empty 200 without a content type: read as text (BE-35).
- **Demo:** the demo directory as the clinic's patients (searched and paged like the backend); prophylaxis kept per
  patient in memory.
- New strings: `prophylaxisLoadFailed`, `prophylaxisSaveFailed`.
- **Checked:** emulator, dev (reads only): Clinic → Patients lists the 4 seeded patients ("Total: 4"), "Gul" finds
  Gulnora (Total: 1), her detail opens in clinic 2's scope, Medical info shows the prophylaxis card (12 months, on; the
  first build failed on the empty answer, fixed). Save not tried on dev (a write). iOS compiled, not run.
- **Codex review:** the form editor keeps the clinic scope; clinic reads stay out of the personal directory; a page
  of unreadable rows still offers the next page.
- **Tests:** `ClinicPatientsViewModelTest` (paging without repeats, debounced trimmed search, failures),
  `PatientsRepositoryTest` (+1 clinic read), `FormEditorViewModelTest` (+1 clinic scope),
  `ProphylaxisViewModelTest` (empty answer = defaults, Save body, failed read blocks Save, failed save),
  `DemoBackendTest` (+1); screenshots `Clinic_patients`, `Clinic_patients_empty`, `PatientDetail_prophylaxis`.

## CLINIC-07 · Services and the catalogue editor

Built 2026-11-06. Spec 09 §2 row 4, flow 09/04, ticket CLINIC-07. Board `DaClinic` Services and the "svc" / "menu"
sheets, phone layout (`ui/clinic/ClinicServices.kt`).
- **List:** `GET /treatment-plans/plan-services?clinicId` (the clinic catalogue, inactive items too, and the doctors'
  own services) with `GET /treatment-plans/catalog-items?clinicId` read beside it for editing. Search over title, code,
  catalogue number and doctors' names; All / Active / Inactive; by title. A card: title (grey italic when inactive),
  code chip, source chip (Clinic / Doctor), Inactive, the price (`Money.format`: "1 500 000 UZS", "25.50 USD"), "All
  doctors at this clinic" / the names / "No doctors assigned". Clinic rows: ⋮ → Edit · Deactivate / Reactivate (at
  once, no question; the banner says deactivated services stay on plans). A doctor's own row: ⓘ → "Defined by the
  doctor in their profile · read-only here" as a banner (Flutter's hover tooltip). A failed catalogue read keeps the
    list and says "Editing unavailable" + Retry (clinic rows lose their menu, also over a catalogue read before: it may be
  out of date); a failed list read keeps the old rows.
- **Editor sheet** ("Add clinic service" / "Edit clinic service"): name, code (upper-cased, unique among the clinic's
  items), currency (UZS · USD · EUR · RUB; a new service starts in the clinic's; the flow's picker, not on the board),
  price, Active, "Assign to all doctors at this clinic" or the clinic's doctors to tick (members with a doctor profile).
  Checks: name, a price of 0 or more (0 is a free service with a soft note — the flow and the backend, over the board's
  "> 0"), code not taken, a doctor when not for all. Price typed as whole sums for UZS (the app's money rule), "12.50"
  otherwise (`Money.parse`, no float maths — quirk 25); an edited service's price is kept as stored while its price and
  currency are untouched (UZS tiyin aren't shown). Create: `POST {clinicId, code?, title, defaultPriceMinor,
  currency, active, appliesToAllDoctors, assignedDoctorProfileIds, sortOrder: 0}`; edit: `PATCH` with every field,
  `code: ""` to clear (backend: blank → null), `appliesToAllDoctors: true` or the doctor ids. Then the banner ("Service
  added" / "Service saved") and both lists read again (rows kept meanwhile). Cancel / Back with changes ask "Discard
  changes?"; a drag or scrim tap closes only an untouched sheet.
- **Who edits:** every member, staff too: the backend checks only clinic access (`TreatmentPlanController`, 2026-11-06),
  as the board shows (`canEdit: owner || staff`).
- **Codex review:** free services, the currency picker and no editing from a stale catalogue were added; UZS decimals
  and the swipe veto were kept (answers in `codex/CLINIC-07.md`).
- **Shared fix:** `ProfileSheet` read the first `confirmHide` lambda forever (the sheet state remembers it), so every
  sheet's "busy / changed" check was stale; it now reads the latest (`rememberUpdatedState`).
- **Demo:** the demo clinic's catalogue lives in `DemoClinic` (plan services follow it, filtered by `doctorIds`; writes
  checked like the backend); the doctor service is offered by member 21.
- New strings: validation, results, load / save failures, "Editing unavailable", "Price ({{currency}})", the menu label.
- **Checked:** emulator, dev (reads): 9 doctor services, no catalogue items. Demo: added "Implant" for Bobur only
  ("Pick at least one doctor" first), deactivated it, edited it (prefilled, code cleared), the discard question on
  Back with changes. Not tried on dev: a created item can't be deleted (no DELETE) and is synced into doctor services.
  iOS compiled, not run.
- **Tests:** `ClinicServicesViewModelTest` (filters, create validation and body, edit prefill and PATCH bodies, toggle,
  discard, failures), `DemoBackendTest` (+1); screenshots `Clinic_services`, `Clinic_service_editor`.

## CLINIC-09 + CAL-10 (B) · A colleague's calendar

Built 2026-11-06. Spec 09 §5, 03 §1.12, flow 04/14 (B), tickets CLINIC-09 and CAL-10. Boards `DaClinic` Calendar and
`DaCalendar` calDoctor (`ui/clinic/ClinicSchedule.kt`, `CalendarScope` in `ui/calendar/CalendarScreen.kt`).
- **Calendar section:** "Book and manage each doctor's appointments. Times are in Asia/Tashkent (UTC+5)." and a card per
  doctor (doctors and owners with a profile, by name; a colour bar from the chart series by roster place, name, role,
  "Open schedule"). The Doctors section's calendar button opens the same screen. Staff see both (flow 04/14 B).
- **The colleague's calendar** is the Calendar screen with a `CalendarScope(doctorId, name, clinic zone, clinic
  address)`: titled with the doctor's name ("Doctor #12" without one), a back button, the hint "Book and manage this
  doctor's appointments. Times use this clinic timezone.", no Block button, the filter on appointments and free slots
  (blocks always show), the mismatch banner against the clinic's zone. Its days are read with `doctorId` and cached
  under that doctor (`DayKey(date, doctorId)`), so the doctor's own calendar is never touched; the 60 s throttle
  applies (Flutter forced every read).
- **On their behalf:** a free slot books with `resourceDoctorId` (the clinic's address as the venue when the slot has
  none); an appointment opens the panel in their calendar (reschedule reads their days; change / cancel need no doctor
  id, the backend reads it from the appointment) without Start / Continue (the visit is theirs); a block is removed
    with `DELETE …/blocks/{id}?doctorId=` (checked in `ScheduleBlockController`). Each change refreshes their days
  (`changed(…, doctorId)`), and `changed` now invalidates only that doctor's cached days (before: every doctor's on
  those dates). The panel's patient name opens the patient (in the clinic's scope for a colleague's).
- **Back stack:** pushes go over the colleague's calendar in the shell's stack, so Back from a booking, a panel or a
  patient comes back to it on the same day (Flutter re-created the page from serialized "return info" — §5's hack).
- **Demo:** Bobur (22) has a day of his own (10:00–14:00, a visit at 11:00, a meeting block at 12:00).
- New strings: `clinicCalendarIntro`, `clinicOpenSchedule`.
- **Checked:** emulator, dev (reads): Calendar section lists the 3 doctors; Dilnoza's calendar opens in Asia/Tashkent
  with her slots, no failures logged; Back returns to the workspace. Demo: booked Bobur's 10:00 for a patient (back on
  his calendar with "Patient assigned"), removed his meeting block ("Block removed"), his appointment's panel has no
  Start. iOS compiled, not run.
- **Tests:** `CalendarViewModelTest` (+1: colleague zone, `doctorId`, separate cache, blocks always shown),
  `BookingViewModelTest` (+1: `resourceDoctorId`, their day), `BlockViewModelTest` (+1: `?doctorId=`, their days);
  screenshots `Calendar_colleague`, `Clinic_calendar` (`Calendar_day` / `Calendar_details` unchanged).
- **Codex review:** `changed` scoped to the doctor, the reschedule month reads the colleague's days, the ViewModel is
  keyed by the whole scope, the patient link (answers in `codex/CLINIC-09.md`).
- **CAL-10 A** (the chip row in the doctor's own Calendar) is in calendar.md.

## FIN-01…07 · Clinic finance

Built 2026-11-06. Spec 09 §3, §6–8, flows 09/07…09/13, tickets FIN-01…07. Board `DaClinic` Finance (phone cards;
`ui/clinic/ClinicFinance.kt` the ViewModel, `ClinicFinanceViews.kt` the views and sheets).
- **Data (FIN-01):** `data/finance/FinanceRepository` (it already recorded a visit's payment) gained the dashboard,
  appointment ledger, payments, records (+ create), installment items (+ mark paid, status, notify) and doctor earnings;
  models in `data/model/FinanceModels.kt`, amounts as `Long` minor units, each row read on its own (quirk 4), pages read
  as `{content, totalElements}`. `FinanceMonth.range(zone)` gives the month's first instant and the next month's in the
  clinic's zone (tested for Asia/Tashkent and a DST month in New York). No refresh fan-out: each view reads itself, and
  again after its own writes (quirk 15); Flutter's unused providers weren't ported (quirk 17).
- **Views:** Dashboard, By appointment, Installments, Doctor earnings, Records, Payments (the board's order) as chips;
  the month (All time + 18 months, the clinic's current one first) where the backend filters by it (Dashboard, Ledger,
  Earnings; not Payments / Records: BE-36; not Installments, as on the board); Export CSV; search and chips per view.
  An answer for older filters is dropped (a generation counter). Instants (a visit's start, a payment) show as their
  day in the clinic's zone; due dates as sent.
- **Dashboard (FIN-02):** Total revenue, Outstanding, Overdue, Collection rate (the backend's 0–1 ratio as a percent)
    with their hints; the selected KPI picks the list: top doctors (names from the members) → Doctor earnings; balances,
  largest first — all time: the plans with a balance (`GET /treatment-plans?clinicId`, what the KPI counts), a month:
  that month's visits with a balance from every page (at most 10 × 50) → By appointment; overdue installments and
  overdue invoices (all time only, as the backend counts them); how the rate is calculated (collected and the backend's
  rate; nothing derived from the clamped ratio). A failed list shows the error with Retry instead of loading forever.
- **By appointment (FIN-04):** server pages (Load more), "Showing n of total", chips All / Paid / Partial / Unpaid on the
  visit's own status (the backend computes it per visit: quirk 13), search over patient, doctor and plan #. A row:
  date · doctor, patient, services count · plan #, the visit total and its status pill; tap → the services with total
  and paid. Who may (`mayManageFinance`) gets the pill's menu when something is owed: "Paid by cash / card / transfer /
  other" (the remainder) or "Custom amount…" (amount — `Money.parse`, the visit's currency —, method, memo);
  `POST …/finance/payments {treatmentPlanId, amountMinor, currency, method, linkedAppointmentId, memo}`.
- **Installments (FIN-05):** server filters All / Upcoming / Overdue / Paid, search, the totals strip (count, scheduled,
  paid, outstanding; waived and cancelled ones left out), a pill menu for anything not paid: Mark paid (method + memo;
  quirk 8: Flutter always sent CASH; not for a cancelled one, which the backend refuses), Pending / Waived / Cancelled
  (OVERDUE is never chosen), the bell for a reminder.
- **Records (FIN-03):** server chips by status (VOIDED, quirk 2), search over number, type and notes, the status always
    localized (quirk 3); "New record": patient (the clinic's patients searched on the server), their plan (optional: the
  totals then come from its lines, the backend's branch), type (invoice, receipt, estimate, credit note), total in the
  clinic's currency when there's no plan, due date (date picker, default +14 days), notes → `POST …/finance/records`
  (subtotal = total, no discount or tax lines, as the board).
- **Payments (FIN-03):** all time, paged, chips by method, search, the count and sum, a row's details sheet.
- **Doctor earnings (FIN-06):** visits, the share ("Share not set"), gross / collected / outstanding and the doctor /
  clinic split when the backend sends it; editing the share stays in Doctors (one share sheet).
- **Export (FIN-07):** the current view's rows as RFC 4180 CSV (`utils/Csv.kt`: quoting, CRLF, the UTF-8 BOM as bytes),
  amounts as major units with two decimals beside a currency column (quirk 5), earnings with the whole split, headers
  in the app's language, through the system share sheet
  (`rememberFileShare`); PDF stays off on mobile.
- **Shared:** `ShifaDatePickerDialog` (widgets) now serves the birth date picker and the record's due date.
- **Demo:** the demo clinic's finance follows the demo plan's payments (two visits, records, three installments,
  earnings); a desk payment or a paid installment moves the ledger and the dashboard.
- **Checked:** emulator, demo: dashboard numbers, the ledger, "Paid by cash" on Malika's partial visit → "Payment
  recorded", Paid, revenue 1 100 000 and outstanding 0 on the dashboard; an overdue installment marked paid by card →
  "Status updated". Dev (reads): every view loads empty ("Nothing for this period."), except the month's ledger:
  500 (BE-37, reproduced with curl; all time works). iOS compiled, not run.
- **Codex review:** paging after a reload, the dashboard lists matching the KPIs (plans, every page, invoices), no
  float "charged", waived installments out of the totals, no Mark paid on cancelled, records with a plan, CSV BOM /
  currency / earnings split (answers in `codex/FIN.md`).
- **Tests:** `ClinicFinanceViewModelTest` (month range, drill-downs and their failure, ledger chips / search / full and
  custom payment bodies, installments filters / mark paid / status / reminder, records status and the new record body,
  stale answers, CSV quoting), `DemoBackendTest` (+1); screenshots `Clinic_finance_dashboard`, `…_ledger`,
  `…_installments`.

## TP-02 + TP-03 · Treatment plans list and detail

Built 2026-11-06. Spec 09 §4.1, §4.3, flows 09/14, 09/16, 09/17, tickets TP-02 and TP-03. Board `DaClinic` Plans and the
plan detail (`ui/clinic/ClinicPlans.kt`).
- **Data:** `TreatmentPlansRepository.plans(clinicId, patientId?, status, kind, q)` (the clinic's plans when no patient),
  `visits(planId)`, `setStatus(planId, status)`; the detail now reads `installmentPlans`; `PlanVisit`, `InstallmentPlan`.
- **List (TP-02):** search (`q`, debounced, on the server) and status chips (All, Active, In progress, On hold, Draft,
  Completed, Cancelled — `status` on the server); a card per plan (the kit's `TreatmentPlanCard`, its amounts now wrap
  as whole phrases): title ("(untitled plan)"), patient · doctors, progress "1 of 3 done", paid / total / outstanding,
  the status badge → the status menu (the current one marked; Cancelled asks "Cancel this plan?"), "N visits" (read on
  first open, Retry on failure) and Open. A visit opens as an appointment of its doctor's calendar in the clinic's zone
  (quirk 6; `CalendarScope`), cancelled visits don't open. The list reads again on return. New plan comes with TP-04.
- **Detail (TP-03):** `ShellRoute.PlanDetail(planId, clinicId)` from the list and the finance dashboard's plan
  balances. "#501 · title" with ⋮ (Change status, Record payment); overview: status badge (menu), progress, the patient
  (opens them in the clinic's scope), doctors, diagnosis, notes, total / paid / outstanding (red when owed), updated
  (the clinic's day); Record payment (who may handle finance, something owed, not cancelled): amount (the outstanding
  by default), method, memo → `POST …/finance/payments {treatmentPlanId, …}`. The read-only dental chart when lines
  carry teeth (done / part done / planned with the count), the lines (tooth or General · × qty, total, line status;
  cancelled struck through), the visits, the installment schedule ("No installment schedule. Payments are taken per
    visit." — read as the backend sends it: `installmentPlanId`, `scheduleRows`, `installmentItemId`; the OpenAPI is
  wrong there). Read again on every return (quirk 10), an older answer never overwriting a newer one; a failed visits
  read has Retry; a 404 says "Treatment plan not found.". The finance section reads again on return too (a payment
  made here shows on its dashboard).
- **Codex review:** the real installment shape, visits refreshed on every reload, finance refreshed on return, visits
  failure with Retry, stale answers dropped (answers in `codex/TP-02-03.md`).
- **Not built:** the board's per-plan payments list (the payments endpoint has no plan filter: BE-36), "Add service"
  and cancelling a line from the detail (not in the spec's detail; the visit fulfils lines), Edit plan (comes with the
  wizard, TP-04), the PDF (TP-05).
- **Demo:** the demo plan's visits (Aziza's past one, Bobur's upcoming one), its status set by hand, `q`.
- **Checked:** emulator, demo: the plan listed with progress and money, its visits expanded, the detail, Record payment
  ("Payment recorded", outstanding 0), Cancelled after the question ("Status updated"). Dev: no plans in clinic 2 (curl,
  `[]`). iOS compiled, not run.
- **Tests:** `ClinicPlansViewModelTest` (server status / search params, visits read once, the cancel question and the
  PATCH body, the detail with lines / teeth / installments, the payment body, 404); screenshots `Clinic_plans`,
  `Clinic_plan_detail`.

