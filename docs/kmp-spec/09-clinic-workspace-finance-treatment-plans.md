> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 08 · Clinic workspace, finance, treatment plans — KMP rewrite analysis

Source root: `/Users/sheroziy.saidkhodjaev/Projects/Private/web-flutter-doctor`. All paths below are relative to `lib/` unless prefixed. Line refs are `path:line`. Anything I could not confirm from code is marked **UNVERIFIED**.

Scope read fully: `features/clinic/**` (16 files incl. `pdf/`), `state/clinic/**` (7 files), `state/workspace/workspace_mode_provider.dart`, `state/practice/practice_session_provider.dart`, `core/services/clinic_service.dart`, both tests, plus the shell/router/l10n glue that wires the workspace in.

---

## 1. Concept

### 1.1 What a "clinic workspace" is
A doctor account may belong to one or more clinics. `GET /api/me/clinics` returns the memberships (`MyClinicSummary`, `state/clinic/clinic_models.dart:6-52`). If the list is non-empty the shell exposes a **Clinic** tab (and a duplicate **Finance** nav item pointing at the same tab) and the `ClinicWorkspaceScreen` renders a clinic-scoped, multi-doctor back-office: members, patients, services catalog, treatment plans, finance, invitations, settings.

There is **no explicit "enter/leave workspace mode"** API call. The workspace is simply a shell tab whose visibility is `hasClinicWorkspaceProvider` (`state/clinic/clinic_providers.dart:41-47`). Selected clinic is a persisted `int?` (`selectedClinicIdProvider`, `clinic_providers.dart:49-102`) stored in `SharedPreferences` under `clinic_workspace_selected_id:<meId>`; it defaults to the first clinic and a dropdown in the identity bar switches it when >1 clinic (`features/clinic/presentation/clinic_workspace_screen.dart:247-261`).

### 1.2 `workspaceModeProvider` is NOT the clinic workspace
`state/workspace/workspace_mode_provider.dart:4` — `enum WorkspaceMode { clinic, intelligence }` toggles the **whole shell** between the normal dashboard ("clinic" here just means "non-AI") and the full-screen Shifa Intelligence chat. `main_shell.dart:274,291-299,420-421` use it; `_goToTab` always calls `showClinic()`. It carries no clinic id and has nothing to do with multi-doctor clinics. Do not conflate in KMP: name it `ShellSurface { dashboard, intelligence }`.

### 1.3 `practiceSessionProvider` (dead)
`state/practice/practice_session_provider.dart:56-69` fetches `GET /api/practice/me` → `{principalRole, clinicIds[], colleagues[{doctorId, displayName}]}`. **No consumer anywhere in `lib/`** (grep). Dead code; do not port.

### 1.4 `ClinicService` (signup only)
`core/services/clinic_service.dart` is the public clinic search for the create-account flow (`GET /api/public/clinics?search=` via raw `http`, 5 s timeout, returns `[]` on any failure). Used only by `features/auth/presentation/create_account/searchable_clinic_dropdown.dart`. Not part of the workspace. Quirk: defines its own `TimeoutException` class shadowing `dart:async` (`clinic_service.dart:75-81`) and falls back to `http://localhost:8080` when no ref (`:20`).

### 1.5 Roles
Two orthogonal role systems:

| Layer | Source | Values | Effect |
|---|---|---|---|
| JWT principal role | `state/auth/doctor_jwt_role_provider.dart:8-11,28-33` | `DOCTOR` (default) / `CLINIC_STAFF` | `CLINIC_STAFF` gets a **4-tab shell** `[Chat, Clinic, Notifications, Profile]` (`main_shell.dart:368-374`), is blocked on native mobile (`ClinicStaffWebOnlyScreen`, `features/auth/presentation/clinic_staff_web_only_screen.dart:14-17`), no Home/Calendar/Patients/Tasks, no start-tab prefs, no sidebar location chip. |
| Clinic membership role | `MyClinicSummary.membershipRole`, `ClinicMember.membershipRole` | `OWNER`, `CLINIC_ADMIN`, `RECEPTIONIST`, `DOCTOR`, `NURSE` (l10n `clinicMembershipRoleLabel`, `core/localization/app_localizations.dart:6387-6403`) | Per-clinic permissions below. |

Permission helpers (all client-side; server is authoritative):
- `canManageFinanceSettings` = backend flag `canManageFinanceSettings == true` OR role ∈ {OWNER, CLINIC_ADMIN} (`clinic_models.dart:3-4,46-49`; duplicated as `canManageClinicFinanceSettings` `clinic_providers.dart:410-416`). Gates: default revenue-share dialog, per-member share edit, hint banner on Doctors tab.
- `canEditMemberRevenueShare(member)` = member role ∈ {DOCTOR, OWNER} (`clinic_providers.dart:418-419`).
- `canManageFinanceProvider` = role ∈ {OWNER, CLINIC_ADMIN, RECEPTIONIST, DOCTOR} (`state/clinic/clinic_finance_providers.dart:209-214`) → New record button, ledger pay menu, installment status/notify.
- `canViewFinanceProvider` = above + NURSE (`:217-222`) — **defined but never watched** (dead).
- A receptionist is a `CLINIC_STAFF` JWT with membership `RECEPTIONIST` (**UNVERIFIED** mapping; inferred from invitation `role: 'RECEPTIONIST'` + staff shell).

### 1.6 Shell tabs that change
- Doctor shell indices (`features/shell/domain/doctor_shell_tab.dart`): chat 0, home 1, calendar 2, patients 3, **clinic 4**, tasks 5, reports 6, notifications 7, profile 8. Clinic + Finance nav items both `setTab(4)` and only render when `hasClinicWorkspace` (`main_shell.dart:595-601`).
- Staff shell: chat 0, **clinic 1**, notifications 2, profile 3 (`main_shell.dart:368-374,587-590`). `_clinicWorkspaceShellTabIndex()` in the workspace screen hardcodes `clinicStaff ? 1 : 4` (`clinic_workspace_screen.dart:105-106`).
- `myClinicsProvider` is invalidated on app resume (`main_shell.dart:264`).
- `hasClinicWorkspaceProvider` is sticky across reloads/errors via `clinicWorkspaceKnownProvider` so the nav item does not flicker (`clinic_providers.dart:12-47`).

### 1.7 Overview stats
`GET /api/clinics/{id}/overview-stats` → `ClinicOverviewStats{appointmentsToday, activeDoctors, patientsThisMonth, averageWaitingMinutes?, occupancyPercent?}` (`clinic_models.dart:54-78`). Only the first three are rendered as metric cards (`clinic_workspace_screen.dart:327-344`); the two optional fields are parsed but never shown.

---

## 2. Clinic workspace screen (`features/clinic/presentation/clinic_workspace_screen.dart`)

Layout: `_ClinicIdentityBar` (icon, clinic name, clinic switcher dropdown if >1, `timeZone · address`, "Your role: X (Primary practice)") → scrollable `TabBar` with **9 tabs** → `TabBarView`. Switching to tab 6 (Finance) calls `refreshClinicFinancialData` (`:79-86`). Empty state when no clinics: `clinicWorkspaceNoClinics`.

All table tabs use the shared `ClinicTableShell` / `clinicDataTable` / `ClinicTableSearchField` / `ClinicFilterChips` / `ClinicTableToolbar` / `ClinicHintBanner` from `clinic_table_shell.dart` (Material `DataTable` inside nested H/V scroll views, min width = viewport, compact density on phones). Sorting + search + filters are **client-side in-memory** everywhere unless noted.

| # | Tab (l10n key) | Data | Columns / layout | Filters | Actions / dialogs | Endpoints |
|---|---|---|---|---|---|---|
| 0 | Overview `clinicWorkspaceOverview` | `clinicOverviewStatsProvider(clinicId)`, `selectedClinicProvider` | phone/email list tiles; 3 `_MetricCard`s; "Quick actions" wrap: *Open calendar tab* (doctor → shell Calendar tab 2 with `resourceDoctorId=null`; staff → inner Calendar tab), *Calendar* (inner tab 2), *Patients* (inner tab 3) (`:350-376`) | — | — | `GET /api/clinics/{id}/overview-stats` |
| 1 | Doctors `clinicWorkspaceDoctors` (`_DoctorsTab` `:418-686`) | `clinicMembersProvider(clinicId)` | Name (avatar initial, sortable), Role (sortable), ProfileId `#` (numeric, hidden compact), UserId `#` (hidden compact), Revenue share (`formatRevenueShareLabel` → "Doctor X% / Clinic Y%" or "Not set"; tappable link if editable), Actions (percent icon → `showDoctorRevenueShareDialog`; calendar icon → `pushClinicDoctorScheduleForMember`) | search (name/role/profileId), role chips built from roles present in data | If `canManage`: hint banner + "Configure default share" → `showDefaultClinicRevenueShareDialog` | `GET /api/clinics/{id}/members` |
| 2 | Calendar `clinicWorkspaceCalendar` (`_CalendarTab` `:688-745`) | members | Hint text, "My schedule" primary button (no-op for staff `:96-102`), one secondary button per member "Calendar for doctor: NAME" → `ClinicDoctorScheduleRoute` | — | — | members |
| 3 | Patients `clinicWorkspacePatients` (`_PatientsTab` `:747-1036`) | `clinicPatientsFirstPageProvider(clinicId)` (page 0, size 50 only) | Master/detail: `#id` (numeric), Name, Phone, Email (hidden compact), Actions (open). Right pane `PatientDetailPanel` (from patients feature) with `clinicWorkspaceId`, upload options, form templates. Single-pane on phones with back button; wide (≥ splitPane-80) → Row 2:3. Auto-selects first patient on wide layouts. "Total {{count}}" from `totalElements`. | search (name/phone/email/id) | row tap / open icon → `fetchPatientWithClient(clinicId:)` → `GET /api/patients/{id}?clinicId=` (**UNVERIFIED** exact path; lives in patients feature) | `GET /api/clinics/{id}/patients?page=0&size=50` |
| 4 | Services `clinicWorkspaceServices` (`_ServicesTab` `:1043-1627`) | `planServicesProvider(PlanServicesKey(clinicId))` (unified list) + `clinicCatalogProvider(clinicId)` (for edit actions) | `#id` (or `·` for doctor-only), Title (grey italic if inactive), Code, Price (`/100` 2dp), Currency, Status pill (Active/Inactive), Source chip (Clinic teal / Doctor indigo), Doctors text ("All doctors" / names / "None"), Actions (popup Edit / Activate-Deactivate for clinic rows; info tooltip for doctor-owned rows) | search (title/code/id/doctor names), status chips ALL/ACTIVE/INACTIVE | "Add" → `_openEditor` dialog: Title*, Code, Price (major, decimal), Currency dropdown `UZS/USD/EUR/RUB`, "Applies to all doctors" switch, else checkbox list of members (must pick ≥1). Create sets `active:true`. Edit sends `code:''` when cleared. Invalidates both providers. | `GET /api/treatment-plans/plan-services?clinicId=`, `GET /api/treatment-plans/catalog-items?clinicId=`, `POST /api/treatment-plans/catalog-items`, `PATCH /api/treatment-plans/catalog-items/{id}` |
| 5 | Treatment plans `clinicWorkspaceTreatmentPlans` | `ClinicTreatmentPlansTab` | see §4.1 | | | |
| 6 | Finance `clinicWorkspaceFinance` | `ClinicFinanceTab` | see §3 | | | |
| 7 | Invitations `clinicWorkspaceInvitations` (`clinic_invitations_tab.dart`) | `clinicInvitationsProvider(clinicId)` | Pull-to-refresh list of Cards: email (or "Email —"), "Expires: {expiresAt raw}", icon+label Consumed/Pending; trailing revoke icon (enabled only if `pending`) | — | Full-width "Invite receptionist" button → dialog with single email `TextField` (autofocus, submit on enter; requires contains `@`) → `createClinicReceptionistInvitation` (lower-cased, `role: 'RECEPTIONIST'`). Snackbar on success; raw `e.toString()` on error. | `GET /api/clinics/{id}/invitations`, `POST /api/clinics/{id}/invitations {email, role:'RECEPTIONIST'}` (200/201), `DELETE /api/clinics/{id}/invitations/{invId}` (200/204) |
| 8 | Settings `clinicWorkspaceSettings` (`_SettingsTab` `:1629-1677`) | `MyClinicSummary` | Read-only list: name, timezone, phone, email, address, divider, "Default revenue share" label | — | none (editing happens in Doctors tab dialog) | — |

Helper `pushClinicDoctorScheduleForMember` (`:36-59`) pushes `ClinicDoctorScheduleRoute` as a root `fullscreenDialog` with clinic TZ (fallback `UTC`) and clinic street address.

---

## 3. Finance (`ClinicFinanceTab`, `features/clinic/presentation/clinic_finance_tab.dart`)

Six sub-tabs as `ChoiceChip`s over an `IndexedStack` (all six sub-views are built/mounted at once): 0 Dashboard, 1 By appointment (ledger), 2 Installments, 3 Doctor earnings, 4 Records, 5 Payments. **Every sub-tab switch calls `refreshClinicFinancialData`** (`:27-31`) which invalidates ~10 providers and bumps a tick → full refetch of all finance endpoints on each chip tap.

### 3.1 Month filter (`state/clinic/clinic_finance_month.dart`)
- `clinicFinanceMonthFilterProvider(clinicId)`: `StateProvider.family<({int year,int month})?, int>`; `null` = all time (`:10-11`).
- `financeMonthRangeIso(ref, clinicId)` → `(fromIso, toIso)` using `monthRangeUtcInTimezone(year, month, clinic.timeZone)` (`core/utils/timezone_utils.dart:142-156`): `[1st 00:00 clinic TZ, 1st of next month 00:00 clinic TZ)` converted to UTC ISO. Correct per the timezone rules.
- `financeRecentMonths(tz, count:18)`: last 18 months starting from "today in clinic TZ" (`:32-46`).
- `FinanceMonthFilterBar`: `InputDecorator`+`DropdownButton` with "All time" + 18 months; label via `DateFormat.yMMMM(locale)` except `uz` which uses a hardcoded Latin month list (`:48-74`). Shared by Dashboard, Ledger, Installments, Doctor earnings, Payments.
- `financeInstantInMonthRange(iso, fromUtc, toUtc)` client-side predicate (`:122-130`) used by Payments and Installments (which only have page-0 data!).

### 3.2 Dashboard (`finance_dashboard_view.dart`)
- Data: `clinicFinanceDashboardProvider(clinicId)` → `GET /api/clinics/{id}/finance/dashboard?from=&to=` (month-scoped when filter set; `clinic_finance_providers.dart:88-109`).
- Header row: title + `FinanceExportButton` (CSV/PDF). Then `FinanceMonthFilterBar`. Then `Wrap` of `FinanceKpiCard`s (selectable, 2-per-row on phones): Total revenue (green; subtitle hint differs all-time vs month), Outstanding (orange), Overdue count (red), Collection rate `%` (teal, `collectionRate*100` 1dp). If month selected AND `hasRevenueShareTotals`: Doctor share collected (blue, subtitle gross) and Clinic share collected (purple).
- Optional "Top doctor earnings" card list from `stats.doctorEarningsTop` (names resolved via `clinicMembersProvider`, gross / collected / outstanding, share %).
- **KPI detail drill-down** (`FinanceDashboardKpiDetail` `:300-963`) below, driven by selected KPI and month:
  - Revenue: all-time → `clinicPaymentHistoryProvider` sorted by `recordedAt` desc (payment tiles); month → `clinicFinanceMonthVisitsProvider` rows with `visitCollectedMinor>0`.
  - Outstanding: all-time → `treatmentPlansForClinicProvider(ClinicPlansFilter(clinicId))` plans with `owedMinor>0 && status!='CANCELLED'` + `clinicFinancialRecordsProvider` records with `remainingMinor>0 && status∉{VOIDED,PAID}`; month → visits with `visitTotal-visitCollected>0`.
  - Overdue: month → empty-state text (`clinicFinanceDashboardNoOverdueMonth`); all-time → `clinicInstallmentItemsProvider((id,'overdue'))` + records `status=='OVERDUE' && remaining>0`.
  - Collection: all-time → plans `total>0 && !CANCELLED` sorted by paid/total asc (color ≥100 green, ≥50 orange, else red); month → visits by collected/total.
- No charts (no fl_chart here; KPI cards + lists only).
- Export CSV: `Metric,Value` rows (labels localized, header hardcoded English), optional revenue-share rows, `Month,<label>`. Export PDF: `generateFinanceTablePdf` 2-col table, header `[Dashboard, 'Value']`.
- Pull-to-refresh → `refreshClinicFinancialData`.

### 3.3 Payments view (`finance_payments_view.dart`)
- Data: `clinicPaymentHistoryProvider(clinicId)` → `GET /api/clinics/{id}/finance/payments?page=0&size=50` (**hardcoded first 50**, response is a bare JSON array `clinic_finance_providers.dart:127-141`).
- Toolbar: month bar (applied **client-side** via `recordedAt`), search (method/memo/id/plan/patient/doctor/#ids), totals chip `count · sum` (currency from first filtered row else clinic currency), method chips ALL/CASH/CARD_EXTERNAL/TRANSFER/OTHER.
- Columns: `#id`, Date (`dd.MM.yyyy` from `recordedAt` **ignoring TZ**), Patient (name or "Patient #id"), Doctor, Plan ("title (#id)"), Method (icon+label; colors for CASH/CARD_EXTERNAL/TRANSFER/CLICK/STRIPE), Amount (green), Memo (max 280px, 2 lines). Default sort: date desc. Read-only (no row actions).

### 3.4 Records view (`finance_records_view.dart`) + record dialog (`clinic_finance_record_dialog.dart`)
- Data: `clinicFinancialRecordsProvider(clinicId)` → `GET /api/clinics/{id}/finance/records?page=0&size=50` (Spring page; only `content` read).
- Toolbar: search (id/number/type/notes), "New record" button if `canManageFinanceProvider`, status chips ALL/ISSUED/PAID/PARTIALLY_PAID/OVERDUE/VOID.
- Columns: Created (`dd.MM.yyyy`), Type (`clinicRecordTypeLabel`: INVOICE/RECEIPT/ESTIMATE/CREDIT_NOTE), Number (`recordNumber` or `#id`), Total, Paid (green), Remaining (red if >0), Status pill (shows **raw `uiPaymentStatus`** if non-empty else localized status), Due. Default sort created desc. Rich empty state (icon+title+body).
- **Record dialog** (`_ClinicFinanceRecordDialog`; fullscreen on mobile shell, `AlertDialog` otherwise): Patient dropdown (from `clinicPatientsFirstPageProvider` — first 50 patients only), optional Treatment plan dropdown (`treatmentPlansForPatientProvider([clinicId, patientId])`), Type dropdown, and if no plan: Subtotal / Discount / Tax text fields (major units, `[0-9.,-]`, `_majorUnitsToMinor` → `*100` rounded); if plan chosen: hint "totals come from plan" and amounts are omitted (defaults 0). Due date (`showDatePicker`, `yyyy-MM-dd`), Notes (2 lines, submit on enter). Submit → `createFinancialRecord` → `POST /api/clinics/{id}/finance/records` then `refreshClinicFinancialData`, success snackbar, pop. Errors show server `message`.

### 3.5 Appointment ledger ("By appointment", `finance_appointment_ledger_view.dart`)
- **Own pagination state** (not a provider): `fetchAppointmentLedgerPage` → `GET /api/clinics/{id}/finance/appointment-ledger?page=&size=20&from=&to=`; reads `content` + `totalElements`; "Load more" button appends pages (`:57-124`). Reloads on `clinicFinanceRefreshTickProvider` tick and on month filter change via `ref.listen` (`:618-629`). Initial load in `initState` post-frame → fires even when sub-tab hidden (IndexedStack).
- Toolbar: month bar, "Showing {{shown}} of {{total}}", refresh icon, export CSV/PDF (of *filtered* rows), search (patient/doctor/apptId/planId), status chips ALL/PAID/PARTIAL/UNPAID (on `planSimplePaymentStatus`).
- Columns: Date, Patient, Doctor, Plan `#id` (tooltip), Services (count), Total, Status pill (**pay menu** when `canManageFinanceProvider && visitTotal>0 && status∉{NONE,PAID}`: CASH / CARD_EXTERNAL / TRANSFER / OTHER quick-pay of full `visitTotalMinor`, or "Custom amount…" dialog with amount (prefilled), method chips, memo), Actions (view services dialog: list of `services[].title/lineTotal` + total).
- Quick pay → `recordClinicPayment(treatmentPlanId, amountMinor, currency, method, memo?, linkedAppointmentId)` → `POST /api/clinics/{id}/finance/payments`; then `refreshClinicFinancialData` + reload ledger.
- Note: `clinicAppointmentLedgerProvider`, `AppointmentLedgerQuery`, `appointmentLedgerQueryForClinic` exist in providers but are **not used by this view** (only invalidated) — dead.

### 3.6 Doctor earnings (`finance_doctor_earnings_view.dart`)
- Own state; loads only when `isActive` (sub-tab 3) via `fetchDoctorEarnings` → `GET /api/clinics/{id}/finance/doctor-earnings?from=&to=`; reloads on tick / month change when active.
- Toolbar: month bar, hint text (all-time vs month), refresh, export CSV/PDF (10 columns), search (name / profileId).
- Columns: Doctor (avatar, name from members, `#id`), Visits, Gross, Collected (green), Outstanding (red>0), Share %, Doctor share gross, Clinic share gross, Doctor share collected, Clinic share collected (last 5 unsortable, `—` when null). Default sort gross desc.
- Footer card "Split totals" summing the four share fields over rows with `revenueSharePercent != null`.
- `clinicDoctorEarningsProvider`/`DoctorEarningsQuery` exist but are unused by the view (dead).

### 3.7 Installments (`finance_installments_view.dart`)
- Data: `clinicInstallmentItemsProvider((clinicId, filter))` autoDispose → `GET /api/clinics/{id}/finance/installment-items?filter=all|pending|overdue|paid` (server filter; bare array of `InstallmentItemListRow`).
- Toolbar: month bar (client-side on `dueDate`), search (patient / plan title / "plan #id"), due-date range picker (`showDateRangePicker`, inclusive, client-side) + clear, status chips all/pending/overdue/paid.
- Totals strip chips: items count (+ "filtered" note), Scheduled sum, Paid sum (status PAID), Outstanding = scheduled − paid.
- Table (**raw `DataTable`**, not `clinicDataTable`; every header has an info tooltip): `#seq`, Patient, Plan (title or `#id`), Due (`dd.MM.yyyy`), Amount, Status pill (`FinanceStatusPill`, editable when `canManage && status != PAID`; selectable PENDING/PAID/WAIVED/CANCELLED, OVERDUE excluded as server-computed), Actions (bell "notify" when `canManage && status != PAID`).
- Status change: → PAID calls `markInstallmentItemPaid(method:'CASH')` (`POST .../installment-items/{id}/mark-paid`, **method hardcoded CASH, no memo**); other statuses `patchInstallmentItem(status:)` (`PATCH .../installment-items/{id}`). Notify → `POST .../installment-items/{id}/notify` (bool success only).
- Installment plan creation happens only in the treatment-plan wizard (§4.2): `createInstallmentPlan` → `POST /api/clinics/{id}/finance/installment-plans`.
- Statuses: `PENDING | PAID | OVERDUE | WAIVED | CANCELLED`; colors `:159-174`.

### 3.8 Finance settings & revenue share (`clinic_revenue_share_ui.dart`)
- Default clinic share: `showDefaultClinicRevenueShareDialog` — text field (0–100, `%` suffix) + slider + live preview "Doctor X% / Clinic Y%"; actions Clear (sends `null`), Cancel, Save. → `updateClinicFinanceSettings` `PATCH /api/clinics/{id}/finance-settings {defaultDoctorRevenueSharePercent: int?}` then `invalidate(myClinicsProvider)` + `refreshClinicFinancialData`.
- Per-member: `showDoctorRevenueShareDialog` — same UI, initial = `doctorRevenueSharePercent` (override) with preview falling back to `effectiveRevenueSharePercent`; Clear sends `null` → inherits default. → `updateMemberRevenueShare` `PATCH /api/clinics/{id}/members/{doctorProfileId}/revenue-share {doctorRevenueSharePercent: int?}` then `invalidate(clinicMembersProvider(clinicId))`.
- Semantics: `effectiveRevenueSharePercent` = override ?? clinic default; clinic share = 100 − doctor share. Only integer percentages.

### 3.9 Audit
`fetchFinanceAudit` → `GET /api/clinics/{id}/finance/audit?page=&size=50&recordId=` (`clinic_finance_actions.dart:250-265`) returns raw map. **No UI calls it** — dead. Also `clinicOverdueProvider` → `GET .../finance/overdue` is defined and invalidated but never watched (dead).

### 3.10 Export (`features/clinic/application/finance_export_service.dart`, `pdf/finance_report_pdf_service.dart`)
- CSV: hand-rolled quoting (`financeCsvCell`), amounts via `financeCsvAmount` (**UZS → integer `minor ~/ 100` (drops tiyin); others 2dp**). Filenames `finance_dashboard_<epochMs>.csv`, `appointment_ledger_…`, `doctor_earnings_…`.
- Delivery: `kIsWeb` → `downloadBytes` (blob + `<a download>`; `admin_pdf_downloader_web.dart`); PDF on web → `downloadPdfBytes` opens the blob in a **new tab** (no `download` attr) and revokes after 30 s. Non-web → `share_plus` `Share.shareXFiles(XFile.fromData)` share sheet. So finance exports work on all platforms; **treatment-plan PDF export does not** (see §4.4).
- PDF: `generateFinanceTablePdf(title, subtitle, headers, rows)` — A4 landscape, 32pt margins, DejaVuSans (loaded twice, same file used as "bold"), teal title, `TableHelper.fromTextArray`, footer `page / count`.

### 3.11 Visit charges catalog (`visit_charges_catalog_dialog.dart`)
`VisitChargesCatalogDialog(clinicId)` — picks active `ClinicCatalogItem`s with quantity; returns `[{catalogItemId, quantity}]` for `PUT /api/appointments/{id}/complete` `visitCharges` (per doc comment `:8`). Fullscreen on mobile shell. **No caller found in `lib/` by grep** — likely dead/legacy (**UNVERIFIED** if referenced dynamically). Uses `clinicCatalogProvider` (catalog only, not unified plan-services).

### 3.12 Invalidation fan-out
`invalidateClinicFinanceTabDataForClinic(ref, clinicId)` (`clinic_finance_providers.dart:243-254`): dashboard, records, payments, overdue, ledger family (all), month-visits, doctor-earnings family (all), installment items ×4 filters. `refreshClinicFinancialData` (`:257-262`) = that + `clinicFinanceRefreshTickProvider++` + invalidate `treatmentPlansForClinicProvider` and `treatmentPlansForPatientProvider` (whole families). Called from: finance sub-tab switch, workspace tab 6 selection, pull-to-refresh in 4 views, record dialog, revenue-share dialogs, ledger pay, installment status, plan status change, wizard save, and `appointment_invalidation.dart:95-104` (after appointment completion, using `clinicWorkspaceId ?? selectedClinicId`). Parameter is `dynamic ref` because callers pass both `Ref` and `WidgetRef`.

---

## 4. Treatment plans

### 4.1 List tab (`clinic_treatment_plans_tab.dart`)
- Data: `treatmentPlansForClinicProvider(ClinicPlansFilter(clinicId, planKind:'COMPREHENSIVE'))` → `GET /api/treatment-plans?clinicId=&planKind=COMPREHENSIVE` (`:783-787`). Status chips (`null/ACTIVE/DRAFT/COMPLETED/CANCELLED`; IN_PROGRESS/ON_HOLD not offered as chips) and debounced (300 ms) title/patient search are applied **client-side** (`:73-91`), so `ClinicPlansFilter.status/query` → `status=`/`q=` server params are never exercised from this tab.
- Toolbar: search, "New plan" → `TreatmentPlanWizardSheet.show(clinicId)`, status chips.
- Columns (12): `#id`, Title (link → detail; sub-line "progress done/total" from `linesCompletedCount/linesTotalCount` when >0), Visits (expander button with count label), Patient, Doctor (joined `attendingDoctors` names, fallback `attendingDoctorName`), Total, Paid, Outstanding (`/100` 2dp + currency — **no thousands grouping, differs from finance `formatFinanceMoney`**), Status pill (popup to ACTIVE/IN_PROGRESS/ON_HOLD/COMPLETED/DRAFT/CANCELLED; CANCELLED asks confirm), Payment pill (`PAID/PARTIAL/UNPAID/NONE`), Updated (`yyyy-MM-dd` from `updatedAt` **`.toLocal()` device TZ**), Actions (PDF icon → `exportTreatmentPlanPdf`; open → detail route).
- Expanded plan → `treatmentPlanVisitsProvider(planId)` → `GET /api/treatment-plans/{id}/visits`; child rows (grey) show "Visit #apptId" + timing pill (UPCOMING/PAST/CANCELLED), services joined, date, doctor, visit total/collected/owed, appointment status pill, visit payment pill; tap opens the appointment via `ShellScope.pushNamed(videoCall|inPerson, Appointment(...))` building a synthetic `Appointment` with `CalendarEntry.utcIsoToTimeOfDayInZone(startAt, clinicTz)` (`:380-412`). Cancelled visits not openable.
- Status change → `patchTreatmentPlanStatus` `PATCH /api/treatment-plans/{id}/status {status}`; then `refreshClinicFinancialData` + invalidate visits.
- Route: `AppRoutes.clinicTreatmentPlanDetail = '/app/clinic/treatment-plan'` with `int planId` argument (`app/router.dart:56,134-140,311-315`), registered both in root and shell routers.

### 4.2 Wizard (`treatment_plan_wizard_sheet.dart`, 2439 lines)
Entry points: list tab, `patients/presentation/patient_detail_panel.dart:141` (with `initialPatientId`), home quick action (`home/application/home_quick_action_navigation.dart:73`, uses first clinic). `showDialog(barrierDismissible:false)` → `Dialog.fullscreen` with "Step {current} of 2" header, Back / Save footer.

**Phase 0 — patient picker** (skipped when `initialPatientId`): live search ≥2 chars, 250 ms debounce, direct `GET /api/clinics/{id}/patients?page=0&size=40&q=` (`:186-207`), list tiles → `_selectPatient` → loads `fetchPatientAppointments` `GET /api/clinics/{id}/patients/{pid}/appointments`.

**Phase 1 — single combined form**, sections:
1. *Plan basics*: Title (required), Diagnosis (2 lines), Notes (2 lines), Payment reminder days dropdown 1..30 (default 3; "Every day"/"Every {n} days").
2. *Care team & visits*: `FilterChip` multi-select over members with `doctorProfileId > 0` (sorted by name). First selected id doubles as legacy `attendingDoctorId`. Per selected doctor a `_DoctorVisitsRow` with picked slot chips (× removes) and "Pick free slots" → `showDatePicker` (today..+365 d, device clock) → `_FreeSlotsSheet` bottom sheet: loads `fetchTreatmentPlanDoctorLocations` `GET /api/treatment-plans/doctor-locations?clinicId=&doctorId=` (preselect primary; dropdown if >1; legacy rows `id<=0` send no `locationId`), then `fetchTreatmentPlanFreeSlots` `GET /api/treatment-plans/free-slots?clinicId=&doctorId=&day=YYYY-MM-DD[&locationId=]`, hides already-picked starts, multi-select `FilterChip`s "HH:mm–HH:mm[ · location]" (**`.toLocal()` device TZ**), "Add (n)" returns `_PickedSlot{tempId, doctorId, doctorName, startAt, endAt, slotMinutes, locationId, locationLabel}`. Deselecting a doctor drops their slots and any line links to them; `_pruneStalePicks` awaits the refiltered services and removes picks no longer visible.
3. *Treatments / services*: if profession is dental (`isDentalDocumentationProfession(profileAll.profile['profession'])`) a `SegmentedButton` toggles **By tooth (FDI)** (`DentalPlanEditorPanel`, full clinic catalog `planServicesProvider(PlanServicesKey(clinicId))`) vs **Service list**. List mode uses `planServicesProvider(PlanServicesKey(clinicId, doctorIds:_selectedDoctorIds))` (filtered by care team; `GET /api/treatment-plans/plan-services?clinicId=&doctorIds=1,2`), active only; each row: checkbox, title, price (`/100` 0dp), source chips ("From {doctor}" / "Clinic catalog"), Qty field (enabled when picked), and when picked a "Link to visit" dropdown: none / `NEW · date · doctor` tentative slots / existing patient appointments. Empty-state text distinguishes "no catalog" from "none for selected doctors".
4. *Payment*: live summary (Plan total / Initial / Balance or "Over by"), **Initial payment at desk** (amount major units, method CASH/TRANSFER/CARD_EXTERNAL, memo; default memo "Initial payment at desk"), then **Remaining balance** radio: Leave unpaid / Pay in full now (method + memo) / Installments (custom schedule): summary (Balance due / Allocated / Remaining|Over by), ≥2 rows of (due date via picker `yyyy-MM-dd`, amount) with add/remove (min 2), new rows prefilled with remaining.

**Initial payment logic** (`:347-381`, mirrored by `test/treatment_plan_initial_payment_test.dart`): `initial = round(major*100)`; `-1` when invalid; balance = `max(0, total − initial)`; remaining for installments = `max(0, balance − allocated)`. Validation on save: initial ≥ 0; initial ≤ total when total>0; installments mode needs ≥2 valid rows.

**Save sequence** (`_save` `:436-824`) — sequential, non-transactional:
1. `POST /api/treatment-plans` `{clinicId, patientId, title, diagnosis?, notes?, paymentReminderDays, attendingDoctorId?, attendingDoctorIds?, dentalPlanDocumentation?}` → summary (null on non-200 → "Could not create plan", abort).
2. `POST /api/treatment-plans/{id}/lines` with array of `{catalogItemId?, title, quantity, unitPriceMinor, discountMinor, currency, sortOrder, linkedAppointmentId?}` (list mode) or dental lines `{…, status:'PLANNED', specialtyMetadata:'{"fdi":"11","dentition":"permanent","source":"PLAN_WIZARD"}'}` (`dental_plan_editor_panel.dart:255-317`, discount % spread across lines). Null → "Could not save services", abort (plan already exists as DRAFT).
3. If any list-mode links to existing appointments: `GET /{id}/detail`, map i-th service key → i-th line id (**order assumption**), `POST /{id}/link-appointments` `[{lineId, appointmentId}]` (errors swallowed).
4. If tentative slots: re-`GET detail`, build `[{doctorId, startAt, slotMinutes, locationId?, lineId?, notes:title}]`, `POST /{id}/book-slots` (error → snackbar, continue). In chart mode `serviceKeysByOrder` is empty so `lineId` is never attached.
5. `GET detail` for `owed`/`currency`.
6. If initial>0: `POST /api/clinics/{cid}/finance/payments` (method/memo), re-fetch detail for owed (fallback `owed-initial`).
7. Full mode & owed>0: another payment for `owed`. Installments mode & owed>0: client check `sum(rows) == owed` else snackbar and **skip**; else `POST /api/clinics/{cid}/finance/installment-plans` `{treatmentPlanId, totalAmountMinor:owed, currency, numInstallments, frequency:'MONTHLY', startDate:first due, scheduleItems:[{dueDate, amountMinor}]}` (server `message` surfaced).
8. `PATCH /{id}/status {status:'ACTIVE'}`; `refreshClinicFinancialData`; snackbar; pop.

### 4.3 Detail screen (`clinic_treatment_plan_detail_screen.dart`)
- Data: `treatmentPlanDetailProvider(planId)` (`GET /api/treatment-plans/{id}/detail`, autoDispose+`keepAlive` → cached until explicitly invalidated — **never invalidated by `refreshClinicFinancialData`**) + `treatmentPlanVisitsProvider(planId)`.
- AppBar: back, `#id · title`, PDF export action.
- Body: `_OverviewCard` (title, status pill, payment pill, progress chip, patient, doctor(s), diagnosis, symptoms, notes, total/paid/outstanding, updated `yyyy-MM-dd` `.toLocal()`), optional Dental chart section (`DentalPlanReadonlyView` when `dentalPlanDocumentation` present), Services `DataTable` (Title, Qty, Total, Line status PLANNED/SCHEDULED/IN_PROGRESS/COMPLETED/CANCELLED, Linked appt `#id`), Visits list (tiles → open appointment screen as in list tab).
- **Read-only**: no status change, no line editing, no fulfill here. Fulfillment lives in appointment screens: `in_person_appointment_screen.dart:466-471` and `video_call_screen.dart:423` call `fetchFulfillmentCandidates(planId, appointmentId)` → `GET /api/treatment-plans/{id}/fulfillment-candidates?appointmentId=` (`FulfillmentCandidateDto` with `toothMatch`), and `dental_visit_documentation_panel.dart:444` calls `fulfillTreatmentPlanLines` → `POST /api/treatment-plans/{id}/appointments/{apptId}/fulfill {lineIds}`; `appendTreatmentPlanLines` → `POST /{id}/lines/append {appointmentId?, lines}` (defined; caller **UNVERIFIED** — not in clinic feature). Treat these as part of the appointments spec, but the endpoints belong to this module's API table.

### 4.4 PDF export (`treatment_plan_pdf_export.dart`, `pdf/treatment_plan_pdf_*.dart`)
- Flow: blocking "Preparing…" dialog → `fetchTreatmentPlanDetail` → `TreatmentPlanPdfData.fromDetail(detail, generatedAt: DateTime.now(), languageCode, clinicDisplayName: selectedClinic.name if same clinicId)` → `generateTreatmentPlanPdf` → `downloadPdfBytes` (web new-tab). On non-web throws `UnsupportedError` → snackbar `clinicTreatmentPlanExportPdfWrongPlatform` (**web-only by design**; mobile should use share sheet like finance exports). Filename `treatment_plan_<id>_<yyyy-MM-dd>.pdf`.
- Translations: only `en` and `uz` (`treatment_plan_pdf_translations.dart:103-212`); `ru` and anything else fall back to `en`. Labels for plan status, payment status, line status, plan kind (COMPREHENSIVE/VISIT), installment item status, tooth section "Tooth {fdi} (FDI)", day/month names.
- Document (A4 portrait, 40pt margins, DejaVuSans, teal rule, logo `assets/branding/shifa_logo.png` if loadable):
  1. Header band: logo + "SHIFA"; right: Clinic name, `Plan ID: #id`, `Generated: <weekday, Month d, yyyy HH:mm>` (device local).
  2. Centered "Treatment Plan" + plan title subtitle.
  3. **Plan summary** two columns: Patient, Patient ID, Title, Diagnosis, Created, Updated | Attending doctor(s), Plan status, Payment status, Plan type, Total, Paid, Outstanding.
  4. **Symptoms** bullets (if any). 5. **Plan notes** (chunked 35 lines).
  6. **Procedures & line items**: if any line has `specialtyMetadata.fdi` → grouped per tooth section (sorted by label string) + "Other services" for the rest; else one table. Columns `# | Service | Qty | Unit price | Discount | Line total | Line status | Linked visit` (linked visit = `weekday, Month d, yyyy HH:mm · doctor · STATUS (#id)`).
  7. **Installment schedules**: summary table (Schedule ID, Status raw, Scheduled total, Count) + per-plan schedule table (# | Due date | Amount due | Part status).
  8. **Patient acknowledgement & signature**: note, "Patient: name", two underline fields (Patient signature / Date).
  9. Footer each page: "Generated by SHIFA…", "Page n / N", confidentiality line.
- Money in PDF: `minor/100` 2dp + currency (no grouping). Test `test/treatment_plan_pdf_service_test.dart` only asserts bytes > 800 for EN and UZ.

### 4.5 Plan status state machine (as implemented client-side)
Statuses: `DRAFT, ACTIVE, IN_PROGRESS, ON_HOLD, COMPLETED, CANCELLED` (`clinicPlanStatusLabel`). Client allows **any → any** via the pill menu (`_selectableStatuses` `:219-226`), CANCELLED requires confirm. Wizard creates DRAFT and immediately PATCHes ACTIVE. Server auto-completes a plan when an appointment completes and all non-cancelled lines are COMPLETED (comment `appointment_invalidation.dart:91-94`). Backend-enforced transitions **UNVERIFIED**. Payment status: `NONE, UNPAID, PARTIAL, PAID` (plan and per-visit). Line status: `PLANNED, SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED`. Plan kind: `COMPREHENSIVE` (listed) vs `VISIT` (auto per-visit plans; hidden from tab by `planKind` filter).

---

## 5. Clinic doctor schedule page & schedule-return round trip

`features/clinic/presentation/clinic_doctor_schedule_page.dart`:
- `ClinicDoctorScheduleRoute(doctorProfileId, doctorDisplayName, clinicScheduleTimeZone, clinicStreetAddress?)` wraps `ClinicDoctorScheduleScaffold` in a `ProviderScope` that **overrides `calendarProvider`** with `CalendarController(ref, initialResourceDoctorId: doctorProfileId)` (`:42-56`) so booking on behalf of a colleague does not pollute the global calendar cache or `resourceDoctorId`.
- UI reuses calendar widgets: `CalendarMonthPanel`, `CalendarDayEntriesList`, `CalendarSlotDetailsPanel` (with `clinicDoctorProfileId`, `clinicDoctorDisplayName`, `scheduleTimeZone`, `primaryClinicVenueLabel`). Mobile: doctor hint, header (title + date + filter), month grid, entries; selected entry replaces body. Desktop: entries left (3), month or slot details right (2) with `AnimatedSwitcher`. Filter dialog toggles appointments / free slots. Date picker 2020–2030. Loads `loadDay(day, doctorTimeZone: tz, forceRefresh: true)` and `loadMonth` through the overridden controller. Today computed via `getTodayInTimezone(tz)` (correct).
- Endpoints: whatever `CalendarController.loadDay/loadMonth` and the slot details panel call with `resourceDoctorId` (calendar module; **UNVERIFIED** here).

**Schedule-return round trip** (`clinic_schedule_return_info.dart` + callers):
1. From the slot details panel inside the root overlay (`ShellScope.of(context) == null`), opening a patient or a document pops the overlay and pushes into the shell Patients tab with args `{patientId, 'clinicScheduleReturn': {doctorProfileId, doctorDisplayName, clinicScheduleTimeZone, clinicStreetAddress?}}` (`calendar_screen.dart:3225-3250`, `:4245-4270`).
2. Router parses it via `ClinicScheduleReturnInfo.fromRouteArgs` for both root and shell patients routes (`router.dart:193,308`), passing `clinicScheduleReturn` + `clinicWorkspaceId` to `PatientsScreen`.
3. `PatientsScreen` shows a route back button when `clinicScheduleReturn != null` and on back pops, then post-frame re-pushes `ClinicDoctorScheduleRoute` with the stored info (`patients_screen.dart:804-806, 901-920`).
KMP: replace this serialized Map hop with a typed nav argument / result and a proper back-stack (push schedule → push patient detail → pop returns), no re-creation of the schedule screen.

---

## 6. State (every provider)

| Provider | Type | Holds | Param | Invalidated by |
|---|---|---|---|---|
| `clinicWorkspaceKnownProvider` | `StateProvider<bool?>` | last known "has clinics" | — | set inside `myClinicsProvider` |
| `myClinicsProvider` | `FutureProvider<List<MyClinicSummary>>` | `GET /api/me/clinics`; watches `authTokenProvider` (empty list when logged out) | — | `updateClinicFinanceSettings`, app resume (`main_shell.dart:264`) |
| `hasClinicWorkspaceProvider` | `Provider<bool>` | non-empty clinics, sticky fallback | — | derived |
| `selectedClinicIdProvider` | `StateNotifierProvider<SelectedClinicIdNotifier,int?>` | persisted selection, listens to `myClinicsProvider` | — | — |
| `selectedClinicProvider` | `Provider<MyClinicSummary?>` | selected or first | — | derived |
| `clinicOverviewStatsProvider` | `FutureProvider.family<ClinicOverviewStats,int>` | overview stats | clinicId | never |
| `clinicMembersProvider` | `FutureProvider.family<List<ClinicMember>,int>` | members | clinicId | `updateMemberRevenueShare` |
| `clinicPatientsFirstPageProvider` | `FutureProvider.family<ClinicPatientsPage,int>` | page 0 size 50 | clinicId | never |
| `clinicCatalogProvider` | `FutureProvider.family<List<ClinicCatalogItem>,int>` | clinic catalog | clinicId | catalog create/patch, Services tab |
| `planServicesProvider` | `FutureProvider.autoDispose.family<List<PlanServiceOption>,PlanServicesKey>` | unified services (key = clinicId + sorted dedup doctorIds) | key | Services tab after edit (clinic-wide key only) |
| `clinicInvitationsProvider` | `FutureProvider.family<List<ClinicInvitationRow>,int>` | invitations | clinicId | create/revoke, pull-to-refresh |
| `clinicFinanceMonthFilterProvider` | `StateProvider.family<({year,month})?,int>` | month filter | clinicId | — |
| `clinicFinanceRefreshTickProvider` | `StateProvider.family<int,int>` | manual refresh tick for stateful views | clinicId | `refreshClinicFinancialData` |
| `clinicFinanceDashboardProvider` | `FutureProvider.family<FinanceDashboardStats,int>` | watches month filter + selected clinic TZ | clinicId | finance fan-out |
| `clinicFinancialRecordsProvider` | `FutureProvider.family<List<FinancialRecordRow>,int>` | records page 0/50 | clinicId | fan-out |
| `clinicPaymentHistoryProvider` | `FutureProvider.family<List<PaymentHistoryItem>,int>` | payments page 0/50 | clinicId | fan-out |
| `clinicOverdueProvider` | `FutureProvider.family<Map,int>` | `/finance/overdue` raw | clinicId | fan-out (**never watched**) |
| `clinicInstallmentItemsProvider` | `FutureProvider.autoDispose.family<List<InstallmentItemListRow>,(int,String)>` | by filter | (clinicId, filter) | fan-out ×4 |
| `clinicAppointmentLedgerProvider` | `FutureProvider.family<Map,AppointmentLedgerQuery>` | ledger page raw | query | fan-out (**unused**) |
| `clinicFinanceMonthVisitsProvider` | `FutureProvider.family<List<AppointmentLedgerRowDto>,int>` | month ledger, size 500 | clinicId | fan-out |
| `clinicDoctorEarningsProvider` | `FutureProvider.family<List<DoctorEarningRow>,DoctorEarningsQuery>` | earnings | query | fan-out (**unused**) |
| `canManageFinanceProvider` / `canViewFinanceProvider` | `Provider<bool>` | role gates | — | derived (`canView` unused) |
| `clinicFinanceCurrencyProvider` | `Provider.family<String,int>` | clinic currency ?? dashboard currency ?? `UZS` | clinicId | derived |
| `treatmentPlansForPatientProvider` | `FutureProvider.autoDispose.family<List<TreatmentPlanSummaryDto>,List<int>>` | `[clinicId, patientId]` (**List key — identity equality, cache misses**) | ids | fan-out, appointment invalidation |
| `treatmentPlanDetailProvider` | `FutureProvider.autoDispose.family<TreatmentPlanDetailDto?,int>` + `keepAlive` | detail | planId | **never** |
| `treatmentPlanVisitsProvider` | same shape | visits | planId | plan status change, tab clinic switch |
| `treatmentPlansForClinicProvider` | `FutureProvider.autoDispose.family<…,ClinicPlansFilter>` | clinic plans | filter (value-equal) | fan-out, appointment invalidation |
| `workspaceModeProvider` | `StateNotifierProvider<WorkspaceModeController,WorkspaceMode>` | shell surface | — | — |
| `practiceSessionProvider` | `FutureProvider<PracticeSession?>` | unused | — | — |

Invalidation graph (edges = "A invalidates B"): `updateClinicFinanceSettings → myClinicsProvider → (listener) selectedClinicId`; `updateMemberRevenueShare → clinicMembersProvider`; catalog mutations → `clinicCatalogProvider` (+ UI also → `planServicesProvider(clinic-wide)`); invitations mutations → `clinicInvitationsProvider`; `refreshClinicFinancialData` → all finance + plans lists + tick → ledger/earnings views reload. Missing edges worth fixing in KMP: payments/installments should invalidate `treatmentPlanDetailProvider` and `treatmentPlanVisitsProvider`; wizard-created plans should invalidate `treatmentPlansForPatientProvider` (done only via the broad family invalidate).

---

## 7. Endpoints

All via `doctorApiClientProvider` (JWT, doctor scope). Status handling: unless noted, non-200 → `Exception('<label> <code>')` or `null`. `_extractServerMessage` reads `{message}` from error bodies for payments/records/installments.

| Method & path | Query | Request body | Response parsed as | Errors |
|---|---|---|---|---|
| GET `/api/me/clinics` | — | — | `MyClinicSummary[]` | throw on !200 |
| GET `/api/clinics/{id}/overview-stats` | — | — | `ClinicOverviewStats` | throw |
| GET `/api/clinics/{id}/members` | — | — | `ClinicMember[]` | throw |
| GET `/api/clinics/{id}/patients` | `page, size, q?` | — | Spring page `{content:ClinicPatientRow[], totalElements, totalPages, number, size}` | throw / wizard ignores |
| GET `/api/clinics/{id}/patients/{pid}/appointments` | — | — | `ClinicPatientAppointmentDto[]` | `[]` |
| GET `/api/clinics/{id}/invitations` | — | — | `ClinicInvitationRow[]` | throw |
| POST `/api/clinics/{id}/invitations` | — | `{email, role:'RECEPTIONIST'}` | ignored | 200/201 ok, else body text |
| DELETE `/api/clinics/{id}/invitations/{invId}` | — | — | — | 200/204 ok |
| PATCH `/api/clinics/{id}/finance-settings` | — | `{defaultDoctorRevenueSharePercent:int?}` | ignored | throw with body |
| PATCH `/api/clinics/{id}/members/{doctorProfileId}/revenue-share` | — | `{doctorRevenueSharePercent:int?}` | ignored | throw with body |
| GET `/api/treatment-plans/catalog-items` | `clinicId` | — | `ClinicCatalogItem[]` | throw |
| POST `/api/treatment-plans/catalog-items` | — | `{clinicId, code?, title, defaultPriceMinor, currency, active, sortOrder, appliesToAllDoctors, assignedDoctorProfileIds[]}` | `ClinicCatalogItem` | throw with body |
| PATCH `/api/treatment-plans/catalog-items/{id}` | — | partial of above | `ClinicCatalogItem` | throw |
| GET `/api/treatment-plans/plan-services` | `clinicId, doctorIds?` (csv) | — | `PlanServiceOption[]` | throw |
| GET `/api/treatment-plans` | `clinicId, patientId?, status?, planKind?, q?` | — | `TreatmentPlanSummaryDto[]` | throw |
| GET `/api/treatment-plans/{id}/detail` | — | — | `TreatmentPlanDetailDto` (`{summary, lines[], installmentPlans[], dentalPlanDocumentation?}`; tolerates flat summary) | `null` |
| GET `/api/treatment-plans/{id}/visits` | — | — | `TreatmentPlanVisitDto[]` | throw |
| POST `/api/treatment-plans` | — | `{clinicId, patientId, title?, diagnosis?, notes?, paymentReminderDays?, attendingDoctorId?, attendingDoctorIds?, symptoms?, dentalPlanDocumentation?}` | `TreatmentPlanSummaryDto` | `null` |
| PATCH `/api/treatment-plans/{id}` | — | `{title?, diagnosis?, notes?, paymentReminderDays?, attendingDoctorId?, symptoms?, dentalPlanDocumentation?}` | summary | `null` (no UI caller in clinic) |
| POST `/api/treatment-plans/{id}/lines` | — | `LineRequest[]` (replace) | summary | `null` |
| POST `/api/treatment-plans/{id}/lines/append` | — | `{appointmentId?, lines[]}` | detail | `null` |
| POST `/api/treatment-plans/{id}/link-appointments` | — | `[{lineId, appointmentId}]` | detail | `null` |
| PATCH `/api/treatment-plans/{id}/status` | — | `{status}` | summary | `null` |
| GET `/api/treatment-plans/doctor-locations` | `clinicId, doctorId` | — | `PlanDoctorLocationDto[]` | `[]` |
| GET `/api/treatment-plans/free-slots` | `clinicId, doctorId, day(YYYY-MM-DD), locationId?` | — | `FreeSlotDto[]` | `[]` |
| POST `/api/treatment-plans/{id}/book-slots` | — | `[{doctorId, startAt, slotMinutes, locationId?, lineId?, notes}]` | detail | `null` |
| GET `/api/treatment-plans/{id}/fulfillment-candidates` | `appointmentId?` | — | `FulfillmentCandidateDto[]` | `[]` |
| POST `/api/treatment-plans/{id}/appointments/{apptId}/fulfill` | — | `{lineIds:int[]}` | detail | `null` |
| GET `/api/clinics/{id}/finance/dashboard` | `from?, to?` (UTC ISO) | — | `FinanceDashboardStats` | throw |
| GET `/api/clinics/{id}/finance/records` | `page=0,size=50` | — | page → `FinancialRecordRow[]` | throw |
| POST `/api/clinics/{id}/finance/records` | — | `{patientId, recordType, subtotalMinor, discountMinor, taxMinor, currency, treatmentPlanId?, recordNumber?, dueDate?(YYYY-MM-DD), notes?}` | `FinancialRecordRow` | server message |
| GET `/api/clinics/{id}/finance/payments` | `page=0,size=50` | — | `PaymentHistoryItem[]` (bare array) | throw |
| POST `/api/clinics/{id}/finance/payments` | — | `{treatmentPlanId, amountMinor, currency, method, memo?, financialRecordId?, linkedAppointmentId?}` | `PaymentHistoryItem` | server message |
| GET `/api/clinics/{id}/finance/overdue` | — | — | raw map (unused) | throw |
| GET `/api/clinics/{id}/finance/installment-items` | `filter=all|pending|overdue|paid` | — | `InstallmentItemListRow[]` | throw |
| POST `/api/clinics/{id}/finance/installment-plans` | — | `{treatmentPlanId, totalAmountMinor, currency, numInstallments, frequency:'MONTHLY', startDate, notes?, scheduleItems?:[{dueDate, amountMinor}]}` | `InstallmentPlanSummary` | server message |
| POST `/api/clinics/{id}/finance/installment-items/{itemId}/mark-paid` | — | `{method, memo?}` | bool | server message |
| PATCH `/api/clinics/{id}/finance/installment-items/{itemId}` | — | `{status?, dueDate?}` | bool | server message |
| POST `/api/clinics/{id}/finance/installment-items/{itemId}/notify` | — | `{}` | bool (200) | silent |
| GET `/api/clinics/{id}/finance/appointment-ledger` | `page, size, from?, to?` | — | page `{content:AppointmentLedgerRowDto[], totalElements}` | throw |
| GET `/api/clinics/{id}/finance/doctor-earnings` | `from?, to?` | — | `DoctorEarningRow[]` | throw |
| GET `/api/clinics/{id}/finance/audit` | `page, size, recordId?` | — | raw map (unused) | throw |
| GET `/api/practice/me` | — | — | `PracticeSession` (unused) | `null` |
| GET `/api/public/clinics` | `search?` | — | `ClinicOption[]` (signup) | `[]` |

Payment methods seen: `CASH, CARD_EXTERNAL, TRANSFER, OTHER, CLICK, STRIPE` (UI only offers first four; CLICK/STRIPE display-only).

---

## 8. Models

Money: **every amount is an integer in minor units** (`*Minor`, 1/100 of the currency) with a sibling `currency` string (default `'UZS'`). Display divides by 100; UZS is shown as whole units with space grouping in finance (`formatFinanceMoney`), 2dp elsewhere. `collectionRate` is a `double` 0..1. Percentages are `int`. All dates/instants are ISO strings (instants UTC; `dueDate`/`startDate` are `YYYY-MM-DD`).

`clinic_models.dart`
- `MyClinicSummary{clinicId:int, name, timeZone(IANA, default 'UTC'), phone?, email?, address?, membershipRole(default 'DOCTOR'), isPracticeClinic:bool, currency('UZS'), defaultDoctorRevenueSharePercent:int?, canManageFinanceSettings:bool}`
- `ClinicOverviewStats{appointmentsToday, activeDoctors, patientsThisMonth:int, averageWaitingMinutes?, occupancyPercent?}`
- `ClinicMember{doctorProfileId:int, userId:int, displayName, membershipRole, doctorRevenueSharePercent?, effectiveRevenueSharePercent?}`
- `ClinicPatientRow{patientId:int, fullName, phone?, email?}`; `ClinicPatientsPage{content, totalElements, totalPages, number, size}` (`clinic_providers.dart:375-408`)
- `ClinicCatalogItem{id, clinicId, code?, title, defaultPriceMinor, currency, active, sortOrder, appliesToAllDoctors, assignedDoctorProfileIds:int[]}`
- `PlanServiceOption{key('catalog:42'|'doctor:7:service:123'), kind:'CLINIC_CATALOG'|'DOCTOR_SERVICE', catalogItemId?, doctorServiceId?, title, code?, defaultPriceMinor, currency, active, offeredByDoctorIds:int[], offeredByDoctorNames:String[], groupId?, groupName?, groupSortOrder(int, default MAX)}`
- `ClinicInvitationRow{id, emailSentTo?, consumed:bool, expiresAt?:String, pending:bool}`

`clinic_finance_models.dart`
- `DoctorEarningRow{doctorProfileId, visitCount, grossMinor, collectedMinor, outstandingMinor, revenueSharePercent?, doctorShareGrossMinor?, clinicShareGrossMinor?, doctorShareCollectedMinor?, clinicShareCollectedMinor?}`
- `FinanceDashboardStats{totalRevenueMinor, outstandingMinor, overdueCount, collectionRate:double, currency, doctorEarningsTop:DoctorEarningRow[], totalDoctorShareGrossMinor?, totalClinicShareGrossMinor?, totalDoctorShareCollectedMinor?, totalClinicShareCollectedMinor?}`
- `FinancialRecordRow{id, clinicId, patientId, treatmentPlanId?, recordType(INVOICE|RECEIPT|ESTIMATE|CREDIT_NOTE), recordNumber?, status(ISSUED|PAID|PARTIALLY_PAID|OVERDUE|VOID — dashboard also checks 'VOIDED'), uiPaymentStatus(UNPAID|PARTIAL|PAID|NONE|''), subtotalMinor, discountMinor, taxMinor, totalMinor, paidMinor, remainingMinor, currency, issuedAt?, dueDate?, notes?, createdAt}` — note **`as int` casts** for id/clinicId/patientId/treatmentPlanId (`:147-150`), crash on JSON doubles.
- `PaymentHistoryItem{id, treatmentPlanId, patientId?, patientName?, doctorProfileId?, doctorName?, treatmentPlanTitle?, amountMinor, currency, method, memo?, financialRecordId?, linkedAppointmentId?, recordedAt}` (`as int` casts `:205-216`).
- `InstallmentPlanSummary{id, treatmentPlanId, totalAmountMinor, currency, numInstallments, frequency('MONTHLY'), startDate, status, notes?, createdAt, items:InstallmentItemRow[]}` (returned by create; UI ignores).
- `InstallmentItemListRow{id, sequenceNumber, dueDate, amountMinor, currency, status(PENDING|PAID|OVERDUE|WAIVED|CANCELLED), paidAt?, notes?, installmentPlanId, treatmentPlanId, treatmentPlanTitle?, patientId, patientName}`; `InstallmentItemRow` (subset, unused).

`clinic_treatment_plan_models.dart`
- `PlanDoctorLocationDto{id(≤0 = legacy synthetic), label, clinic?, address?, isPrimary}`; `FreeSlotDto{startAt, endAt, slotMinutes(30), locationId?, locationLabel?}`
- `TreatmentPlanDoctorRef{id, name}`
- `TreatmentPlanSummaryDto{id, clinicId, patientId?, patientName?, attendingDoctorId?, attendingDoctorName?, attendingDoctors[], title?, diagnosis?, status, notes?, paymentReminderDays?, planKind('COMPREHENSIVE'|'VISIT'), symptoms:String[], totalMinor, paidMinor, owedMinor, currency, planPaymentStatus, visitCount, linesCompletedCount, linesTotalCount, createdAt?, updatedAt?}`
- `AppointmentSummaryDto{id, startAt, endAt, status, doctorProfileId, doctorName}` (= `ClinicPatientAppointmentDto`, duplicate class)
- `LineDetailDto{id, catalogItemId?, title, quantity(1), unitPriceMinor, discountMinor, currency, sortOrder, status, linkedAppointment?, lineTotalMinor, specialtyMetadata?(JSON string `{fdi, dentition, source}`), assignedDoctorId?, notes?}`
- `InstallmentScheduleItemDto{installmentItemId, sequenceNumber, dueDate, amountMinor, currency, status}`; `InstallmentPlanSummaryDto{installmentPlanId, status, totalAmountMinor, currency, numInstallments, scheduleRows[]}`
- `TreatmentPlanDetailDto{summary, lines[], installmentPlans[], dentalPlanDocumentation?:String(JSON)}`
- `FulfillmentCandidateDto{lineId, title, fdi?, dentition?, unitPriceMinor, quantity, discountMinor, currency, lineTotalMinor, status, toothMatch:bool}`
- `AppointmentLedgerRowDto{appointmentId, startAt, patientId, patientName, doctorProfileId, doctorName, treatmentPlanId, services:[{title, lineTotalMinor}], visitTotalMinor, visitCollectedMinor, currency, planPaymentStatus, planSimplePaymentStatus}`
- `TreatmentPlanVisitDto{appointmentId, startAt, endAt, status, doctorProfileId, doctorName, location, services:String[], visitTotalMinor, visitCollectedMinor, visitOwedMinor, currency, visitPaymentStatus, timing(UPCOMING|PAST|CANCELLED)}`; `isVideo` = location contains "video".
- `DoctorEarningRowDto` — duplicate of `DoctorEarningRow` without share fields, **unused**.

Enums to define in KMP: `MembershipRole`, `JwtPrincipalRole`, `PlanStatus`, `PlanPaymentStatus`, `LineStatus`, `PlanKind`, `VisitTiming`, `RecordType`, `RecordStatus`, `InstallmentStatus`, `PaymentMethod`, `ServiceKind`, `InstallmentFilter`, `FinanceSubTab`, `DashboardKpi`.

---

## 9. Platform / third-party → KMP

| Flutter | Used for | KMP (Android + iOS) recommendation |
|---|---|---|
| `pdf` ^3.10.8 (+ `rootBundle` DejaVuSans, logo PNG) | treatment-plan PDF, finance table PDFs | No mature Compose-MP PDF lib. Options: (a) **server-side rendering** `GET /api/treatment-plans/{id}/pdf?lang=` (recommended — single source of truth, fixes RU gap, removes 1.2k lines of layout code); (b) expect/actual with Android `PdfDocument` + Compose `drawIntoCanvas` and iOS `UIGraphicsPDFRenderer`/`CoreText`, rendering a shared "document model" (`TreatmentPlanPdfData` is already a pure, display-string DTO — keep it). Keep `TreatmentPlanPdfData.fromDetail` + translations in `commonMain` either way; add `ru`. |
| `dart:html` blob download / `share_plus` | export delivery | expect/actual `FileSharer.share(bytes, name, mime)`: Android `FileProvider` + `ACTION_SEND`/`ACTION_CREATE_DOCUMENT`; iOS `UIActivityViewController`. No "download" concept on mobile; always share/open. |
| Material `DataTable` + nested scroll views | all tables | Compose has no DataTable. Phone-first: render rows as cards/`ListItem`s with sort/filter chips; tablet/landscape: `LazyColumn` with fixed-weight columns + horizontal scroll (`Modifier.horizontalScroll`) and sticky header. Build one shared `ClinicTable` composable (search, chips, sort state, empty/loading/error) mirroring `clinic_table_shell.dart`. |
| `timezone` + `intl` | month ranges, labels | `kotlinx-datetime` `TimeZone.of(iana)`; `LocalDate(y,m,1).atStartOfDayIn(tz)`. Month labels via platform `DateTimeFormatter`/`NSDateFormatter` (expect/actual) — keep the uz Latin override. |
| `SharedPreferences` selected clinic | persistence | `multiplatform-settings` / DataStore, key `clinic_workspace_selected_id:<meId>`. |
| Riverpod families + fan-out | caching | One `ClinicWorkspaceRepository` with per-clinic in-memory cache + a `FinanceRefreshBus` (`SharedFlow<Unit>`) instead of 10 invalidations; ViewModels `StateFlow`. |
| `showDatePicker`/`showDateRangePicker` | due dates, slot day | Material3 `DatePicker`/`DateRangePicker` (Compose-MP 1.6+). |
| `DentalPlanEditorPanel` (appointments module) | wizard chart mode | Out of scope here; wizard must accept an injected "line source" interface (`LineRequest` list + documentation JSON + total/currency). |

PDF recommendation: **move to backend**. Client already needs a network round-trip for the detail; a `GET …/pdf` returning bytes removes font bundling, locale duplication, and the web-only download path. If backend cannot commit, do option (b) behind an `expect fun renderTreatmentPlanPdf(data): ByteArray` and ship only the treatment-plan document first; finance table PDFs can be CSV-only (nobody needs a landscape A4 table from a phone).

---

## 10. Quirks, bugs, dead code, tech debt (do not copy)

Correctness / data
1. **First-page-only datasets filtered client-side**: payments (`?page=0&size=50`) with month filter applied in memory (`finance_payments_view.dart:194-212`) → months older than the latest 50 payments show empty; same for records (`clinic_finance_providers.dart:111-125`), patients tab/record dialog (`clinic_providers.dart:146-159`), installments month filter. KMP: server-side `from/to`, real paging.
2. Record status constant mismatch: dashboard excludes `'VOIDED'` (`finance_dashboard_view.dart:573`) while records filter/l10n use `'VOID'` (`finance_records_view.dart:166`). Confirm with backend enum (**UNVERIFIED**).
3. Records status pill shows raw unlocalized `uiPaymentStatus` when present (`finance_records_view.dart:302-304`).
4. `as int` JSON casts in `FinancialRecordRow`, `PaymentHistoryItem`, `InstallmentPlanSummary`, `InstallmentItemRow` (`clinic_finance_models.dart:147-150,205-216,256-265,345-350`) — brittle vs `num`.
5. CSV UZS amounts truncate tiyin (`finance_export_service.dart:20-22`); money formatting is inconsistent across modules (finance grouped integer vs plans/PDF 2dp ungrouped vs services 2dp vs wizard 0dp). Define one `Money.format(minor, currency, locale)`.
6. Timezone rule violations: `.toLocal()` (device TZ) for plan dates (`clinic_treatment_plans_tab.dart:165`, detail `:165,456`), wizard slot times (`treatment_plan_wizard_sheet.dart:1951,2248`), PDF dates (`treatment_plan_pdf_data.dart:134,151`, service `:40`), `formatFinanceDate` parses ISO and prints components without TZ (`finance_shared.dart:106-113`), `DateTime.now()` for pickers (`:398,1660`). Free slots for a clinic in Asia/Tashkent viewed from a device in another zone show wrong times. KMP: always format in `selectedClinic.timeZone`.
7. Wizard "atomic" save is 6–9 sequential calls with partial-failure states (plan left DRAFT without lines; lines saved but slots/payment/installments failed; installment sum mismatch silently skipped yet plan set ACTIVE) (`:436-824`). Line→service mapping relies on response order (`:608-613,647-655`). Recommend one `POST /api/treatment-plans/compose` transaction or at least idempotent retry UI.
8. Installment "mark paid" from the Installments tab hardcodes `method:'CASH'` and no memo (`finance_installments_view.dart:207-213`).
9. Pay-in-full after an initial payment records two payments; "owed" refetch fallback arithmetic (`:715-720`).
10. `treatmentPlanDetailProvider` is `keepAlive` and never invalidated → detail/PDF can be stale after payments or fulfillment in the same session (`clinic_treatment_plan_providers.dart:19-25`).
11. `treatmentPlansForPatientProvider` keyed by `List<int>` (no value equality) → every watch with a fresh list is a cache miss/refetch (`:7-17`).
12. `_pruneStalePicks` races with the provider (`:1717-1739`); `_serviceQty` only set on checkbox toggle, qty typed before ticking is kept but `qty<1` lines silently dropped.
13. Ledger status pill disabled when `planSimplePaymentStatus=='PAID'` even if that specific visit is unpaid (plan-level vs visit-level) (`finance_appointment_ledger_view.dart:546-550`).
14. Overview quick actions: two buttons both open the calendar for staff; "My schedule" in Calendar tab is a no-op for staff (`clinic_workspace_screen.dart:96-102,354-370`).

Performance
15. `IndexedStack` mounts all 6 finance views; ledger view auto-loads on mount; every sub-tab switch and every workspace tab-6 selection calls `refreshClinicFinancialData` → ~10 requests (`clinic_finance_tab.dart:27-31`, `clinic_workspace_screen.dart:79-86`). Month visits fetch `size=500` (`clinic_finance_providers.dart:182-189`).
16. `ClinicTreatmentPlansTab` registers `ref.listen` per expanded plan inside `build` (`:789-793`).

Dead / duplicate code
17. Unused: `practiceSessionProvider`, `canViewFinanceProvider`, `clinicOverdueProvider`, `clinicAppointmentLedgerProvider`+`AppointmentLedgerQuery`+`appointmentLedgerQueryForClinic`, `clinicDoctorEarningsProvider`+`DoctorEarningsQuery`, `fetchFinanceAudit`, `DoctorEarningRowDto`, `InstallmentItemRow`, `patchTreatmentPlan` (no clinic caller), `VisitChargesCatalogDialog` (no caller found), `ClinicOverviewStats.averageWaitingMinutes/occupancyPercent`, `PlanServiceOption.group*` (parsed, never used), `PlanDoctorLocationDto.isLegacySynthetic` (logic duplicated inline `:2167-2171`), `ScrollableSheetDialog` import only for the slot sheet.
18. Duplicates: `AppointmentSummaryDto` ≡ `ClinicPatientAppointmentDto`; `_StatusPill`/`_pill` reimplemented in tab, detail, finance (`FinanceStatusPill`), services; `_money/_shortDate` copied in 3 files; `canManageClinicFinanceSettings` vs `_membershipRoleCanManageFinance`; `_shellTabIndex` hardcoded in workspace screen vs `DoctorShellTab`.
19. l10n: the wizard uses `tr(key, 'English fallback')` for ~60 strings (`:157-161`) — en.json has 25 `treatmentPlan*` keys vs ru.json 82, so EN relies on hardcoded fallbacks (violates project rule 1 of Localization). PDF translations live in Dart, EN/UZ only. CSV/PDF headers `'Value'`, `'Metric'`, `'Month'` hardcoded.
20. `FinanceStatusPill` tooltip has an English `?? 'Change status'` fallback (`finance_shared.dart:182-183`).
21. Installments view bypasses the shared `clinicDataTable` despite the "MUST" comment (`finance_installments_view.dart:430`), uses `MediaQuery` width instead of parent constraints.
22. Revenue-share dialogs: Clear pops before awaiting and ignores `saving` (`clinic_revenue_share_ui.dart:91-110`); duplicate 150-line dialogs differing only in the PATCH call.
23. `finance_report_pdf_service.dart:20-23` loads the same TTF twice as regular and bold.
24. Treatment-plan PDF export is web-only (`treatment_plan_pdf_export.dart:115-126`) while finance exports support mobile via share.
25. `_ServicesTab._openEditor` sends `code: ''` on edit to clear vs omitting on create; price parsed as `double*100` (float rounding).
26. `ClinicService` custom `TimeoutException`, localhost fallback.

---

## 11. Proposed tickets

Sizes: S≈½–1 day, M≈2–3 days, L≈4–6 days. "Spec refs" point to sections above.

### CLINIC-01 · Clinic membership model, repository & selected-clinic state [M]
Route · — · Spec refs §1, §6, §8 · API: `GET /api/me/clinics`
Build: `ClinicWorkspaceRepository` (Ktor) with `MyClinicSummary`, `MembershipRole` enum, `JwtPrincipalRole` from token claim, `selectedClinicId` persisted per user (`multiplatform-settings`), `hasClinicWorkspace` sticky flag, `selectedClinic` flow; refresh on app foreground.
Done when: switching clinics persists across restart; logged-out state clears; unit tests for sticky flag and default-first selection.
Depends: core auth/API client tickets.
Fix: quirk 17 (drop `practiceSessionProvider`), single permission helper (`canManageFinanceSettings`, `canManageFinance`, `canEditMemberShare`).

### CLINIC-02 · Shell integration: Clinic tab + CLINIC_STAFF shell variant [M]
Route · `/clinic` tab in shell · Spec refs §1.5–1.6 · API: —
Build: Clinic (and Finance alias) bottom-nav/sidebar entries visible when `hasClinicWorkspace`; staff shell `[Chat, Clinic, Notifications, Profile]`; identity bar with clinic switcher; 9-tab pager host (`ClinicWorkspaceScreen`). Decide product call: staff on native mobile — port `ClinicStaffWebOnlyScreen` block or allow (recommend allow; this is a mobile rewrite).
Done when: doctor with clinics sees tab; staff JWT gets the narrow shell; deep links to inner tabs work (`openClinicSubTab(index)`).
Depends: CLINIC-01, shell ticket.
Fix: quirk 14, 18 (`DoctorShellTab` single source).

### CLINIC-03 · Shared clinic table/list kit [M]
Route · — · Spec refs §2 (table shell), §9 · API: —
Build: `ClinicTableScaffold(toolbar, chips, sortState, empty/error)`; phone card-row renderer + tablet column renderer; `StatusPill`, `MoneyText`, `SearchField`, `FilterChips<T>`; one `Money.format(minor, currency)` and `ClinicDate.format(instant, tz)` utilities.
Done when: used by every list ticket below; snapshot tests for pill/money formatting (UZS grouping, 2dp others).
Depends: design system ticket.
Fix: quirks 5, 6, 18, 21.

### CLINIC-04 · Overview + Settings tabs [S]
Route · `/clinic/overview`, `/clinic/settings` · Spec refs §1.7, §2 rows 0 & 8 · API: `GET /api/clinics/{id}/overview-stats`
Build: contact tiles, 3 metric cards, quick actions (Calendar → inner calendar tab; Patients → inner tab; shell Calendar for doctors only), read-only settings list incl. default revenue share.
Done when: loading/error/data states; staff sees no dead buttons.
Depends: CLINIC-01, CLINIC-02.
Fix: quirk 14; drop unused optional stats.

### CLINIC-05 · Doctors (members) tab + revenue share dialogs [M]
Route · `/clinic/doctors` · Spec refs §2 row 1, §3.8 · API: `GET /api/clinics/{id}/members`, `PATCH /api/clinics/{id}/finance-settings`, `PATCH /api/clinics/{id}/members/{doctorProfileId}/revenue-share`
Build: member list (name/role/ids/effective share), role chips from data, search, open-schedule action; one reusable `RevenueShareDialog(initial, onSave(Int?), onClear)` for default and per-member; permission gating.
Done when: share edits refresh members/clinics; Clear sends null; 0–100 validation.
Depends: CLINIC-01, CLINIC-03.
Fix: quirk 22.

### CLINIC-06 · Clinic patients tab (master/detail) [M]
Route · `/clinic/patients` · Spec refs §2 row 3 · API: `GET /api/clinics/{id}/patients?page&size&q`, patient detail endpoint from patients module
Build: server-paged, server-searched (`q`) list with infinite scroll; tap → patient detail screen from patients module with `clinicWorkspaceId` context (uploads, forms). Phone: push; tablet: side pane.
Done when: >50 patients browsable; search hits server; detail refresh after upload.
Depends: CLINIC-03, patients-module detail ticket.
Fix: quirk 1.

### CLINIC-07 · Services tab (unified catalog) + catalog editor [M]
Route · `/clinic/services` · Spec refs §2 row 4 · API: `GET /api/treatment-plans/plan-services`, `GET/POST/PATCH /api/treatment-plans/catalog-items`
Build: unified list with source chip, status, doctors text; filters; editor sheet (title, code, price major→minor, currency, applies-to-all / doctor multi-select); activate/deactivate; invalidate catalog + plan-services.
Done when: doctor-owned rows read-only with hint; create/edit/toggle round-trip.
Depends: CLINIC-03, CLINIC-05 (members list).
Fix: quirk 25 (decimal parsing via `BigDecimal`-like or string→minor parser).

### CLINIC-08 · Invitations tab [S]
Route · `/clinic/invitations` · Spec refs §2 row 7 · API: `GET/POST /api/clinics/{id}/invitations`, `DELETE …/{invId}`
Build: list (email, expires in clinic TZ, pending/consumed), invite-receptionist dialog with email validation, revoke with confirm, pull-to-refresh.
Done when: success/error snackbars localized; revoke only for pending.
Depends: CLINIC-01.
Fix: format `expiresAt`; localize errors (`sanitizeErrorMessage`).

### CLINIC-09 · Clinic doctor schedule screen (book on behalf) [L]
Route · `/clinic/doctor-schedule/{doctorProfileId}` · Spec refs §5 · API: calendar module endpoints with `resourceDoctorId`
Build: dedicated ViewModel instance scoped to the route (replaces `ProviderScope` override) holding doctorId + clinic TZ + venue; reuse calendar month/day/slot-details composables; filters; proper back-stack to patient detail (typed args, no `ClinicScheduleReturnInfo` map).
Done when: booking a slot for a colleague never mutates the doctor's own calendar cache; returning from patient detail lands back on the same doctor/day.
Depends: calendar module tickets, CLINIC-05.
Fix: §5 round-trip hack.

### FIN-01 · Finance data layer: DTOs, repository, month filter, refresh bus [M]
Route · — · Spec refs §3.1, §3.12, §6, §7, §8 · API: all `/api/clinics/{id}/finance/**` GETs
Build: typed DTOs (num-safe), `FinanceMonth` value + `monthRangeUtc(tz)` via kotlinx-datetime, `FinanceRepository` with per-clinic cache and `refresh()` that re-emits only the active screen's flows (no 10-way fan-out), `FinanceRefreshBus` triggered by payments/installments/plan status/appointment completion.
Done when: unit tests for month range in Asia/Tashkent and America/New_York; DTO parsing tolerant of doubles.
Depends: CLINIC-01.
Fix: quirks 4, 10, 11, 15, 17 (drop dead providers/audit/overdue until a screen needs them).

### FIN-02 · Finance dashboard (KPIs + drill-downs) [M]
Route · `/clinic/finance/dashboard` · Spec refs §3.2 · API: `GET …/finance/dashboard?from&to`, `…/payments`, `…/appointment-ledger`, `…/installment-items?filter=overdue`, `…/records`, `GET /api/treatment-plans?clinicId`
Build: month picker, selectable KPI cards (revenue/outstanding/overdue/collection + share cards when month-scoped), top-doctor earnings list, drill-down list per KPI (all-time vs month variants as specified), pull-to-refresh.
Done when: all 8 drill-down variants render with empty states; month label localized (uz override).
Depends: FIN-01, CLINIC-03.
Fix: quirk 2 (confirm VOID vs VOIDED with backend), 16.

### FIN-03 · Payments & Records views + New record dialog [M]
Route · `/clinic/finance/payments`, `/clinic/finance/records` · Spec refs §3.3, §3.4 · API: `GET …/finance/payments?page&size[&from&to]`, `GET …/finance/records?page&size`, `POST …/finance/records`
Build: paged lists with server date range (ask backend for `from/to` on payments if absent — **UNVERIFIED** support), method/status chips, totals chip, record form (patient picker w/ search, optional plan, type, amounts or plan-derived, due date, notes).
Done when: paging past 50; localized status labels; validation errors shown via `sanitizeErrorMessage`.
Depends: FIN-01, CLINIC-06 (patient picker).
Fix: quirks 1, 3.

### FIN-04 · Appointment ledger with quick pay [M]
Route · `/clinic/finance/ledger` · Spec refs §3.5 · API: `GET …/finance/appointment-ledger?page&size&from&to`, `POST …/finance/payments`
Build: paged ledger (month filter server-side), search/status chips, services sheet, pay menu (CASH/CARD/TRANSFER/OTHER full amount; custom amount dialog with method + memo) gated by `canManageFinance`; refresh bus on success; CSV export via share sheet.
Done when: payment updates ledger, dashboard, plan totals; disabled state computed per visit (not per plan where backend allows).
Depends: FIN-01, CLINIC-03.
Fix: quirk 13 (clarify with backend), 24.

### FIN-05 · Installments view [M]
Route · `/clinic/finance/installments` · Spec refs §3.7 · API: `GET …/finance/installment-items?filter`, `POST …/installment-items/{id}/mark-paid`, `PATCH …/installment-items/{id}`, `POST …/installment-items/{id}/notify`
Build: filter chips (server), search, due-date range, totals strip, status pill editor (PENDING/PAID/WAIVED/CANCELLED), mark-paid dialog asking **method + memo**, notify action.
Done when: OVERDUE never user-selectable; PAID rows locked; totals recompute on filter.
Depends: FIN-01, CLINIC-03.
Fix: quirk 8, 21.

### FIN-06 · Doctor earnings view + CSV export [S]
Route · `/clinic/finance/earnings` · Spec refs §3.6, §3.10 · API: `GET …/finance/doctor-earnings?from&to`
Build: month filter, rows with share split columns (collapsible detail on phone), split-totals footer, search, CSV export through `FileSharer`.
Done when: names resolved from members; null share fields render `—`; CSV opens in share sheet on both OSes.
Depends: FIN-01, CLINIC-05.
Fix: quirk 5 (CSV tiyin), hardcoded English headers.

### FIN-07 · Export infrastructure (CSV share; PDF decision) [S]
Route · — · Spec refs §3.10, §9 · API: (optional) server PDF endpoints
Build: `expect/actual FileSharer`; CSV writer with RFC-4180 quoting; feature flag for PDF table exports (default off on mobile). Raise backend ticket for `GET …/finance/*/export.pdf` if product insists.
Done when: dashboard/ledger/earnings CSV share works on Android + iOS.
Depends: —.
Fix: quirk 23, 24.

### TP-01 · Treatment plan DTOs, enums, repository [M]
Route · — · Spec refs §4.5, §6, §7, §8 · API: all `/api/treatment-plans/**`
Build: `PlanStatus/PaymentStatus/LineStatus/PlanKind/VisitTiming` enums, DTOs (`Summary`, `Detail`, `Line`, `Visit`, `FulfillmentCandidate`, `FreeSlot`, `DoctorLocation`, `PlanServiceOption`), `TreatmentPlanRepository` with value-keyed caches (`clinicId+patientId`, `planId`) and invalidation on finance refresh bus; errors as typed results with server message (no `null` returns).
Done when: parsing tests for detail (nested and flat summary), lines with `specialtyMetadata`; cache hit on repeated patient key.
Depends: CLINIC-01.
Fix: quirks 10, 11, 18 (dedupe `AppointmentSummaryDto`), surface error bodies.

### TP-02 · Treatment plans list tab [M]
Route · `/clinic/treatment-plans` · Spec refs §4.1 · API: `GET /api/treatment-plans?clinicId&planKind=COMPREHENSIVE[&status&q]`, `GET /api/treatment-plans/{id}/visits`, `PATCH /api/treatment-plans/{id}/status`
Build: list (phone cards / tablet table) with title→detail, expandable visits (lazy), status pill menu with CANCELLED confirm, payment pill, search (server `q`) + status chips (server `status`), New plan CTA, PDF action (per TP-05).
Done when: status change refreshes list + finance; visit tap opens appointment screen with clinic-TZ times.
Depends: TP-01, CLINIC-03, appointments screens.
Fix: quirk 6 (dates in clinic TZ), 16.

### TP-03 · Treatment plan detail screen [S]
Route · `/clinic/treatment-plan/{planId}` · Spec refs §4.3 · API: `GET /api/treatment-plans/{id}/detail`, `…/visits`
Build: overview card, dental read-only chart (if dental module present), lines table, visits list, PDF action; re-fetch on resume / finance refresh.
Done when: stale-after-payment bug gone; not-found state.
Depends: TP-01, dental read-only view ticket (optional).
Fix: quirk 10.

### TP-04 · Treatment plan wizard [L]
Route · `/clinic/treatment-plans/new?patientId=` (full-screen) · Spec refs §4.2 · API: `GET /api/clinics/{id}/patients?q`, `…/patients/{pid}/appointments`, `GET /api/treatment-plans/plan-services`, `…/doctor-locations`, `…/free-slots`, `POST /api/treatment-plans`, `POST …/{id}/lines`, `…/link-appointments`, `…/book-slots`, `POST /api/clinics/{id}/finance/payments`, `POST …/finance/installment-plans`, `PATCH …/{id}/status`
Build: two-step flow (patient search → form) with sections Basics (title*, diagnosis, notes, reminder days 1–30), Care team (member chips, per-doctor day→free-slot picker with location, slot chips), Services (list mode with qty + link-to-visit; chart mode behind a `LineSource` interface for dental), Payment (initial desk payment, unpaid/full/installments with ≥2 rows and live balance math — port `balanceAfterInitialMinor` + unit test). Save orchestration as a resumable `SaveStep` state machine with per-step error surfacing and retry; prefer backend `compose` endpoint if available (raise ticket).
Done when: all validation messages localized in en/uz/ru (no fallbacks); slot times rendered in clinic TZ; installment sum mismatch blocks save rather than silently skipping; partial failure leaves a visible "incomplete plan" banner on detail.
Depends: TP-01, TP-03, CLINIC-05, CLINIC-07, FIN-01, dental editor ticket (chart mode only).
Fix: quirks 6, 7, 9, 12, 19.

### TP-05 · Treatment plan PDF (server-side preferred) [M]
Route · action on TP-02/TP-03 · Spec refs §4.4, §9 · API: new `GET /api/treatment-plans/{id}/pdf?lang=` (backend) — fallback client renderer
Build: keep `TreatmentPlanPdfData.fromDetail` + translations (add `ru`) in commonMain for parity/testing; download bytes → `FileSharer`; progress dialog; filename `treatment_plan_<id>_<date>.pdf`. If backend declines: `expect fun renderTreatmentPlanPdf(data: TreatmentPlanPdfData): ByteArray` with Android `PdfDocument` and iOS `UIGraphicsPDFRenderer` implementing sections 1–9 of §4.4.
Done when: EN/UZ/RU documents open on both OSes; golden test on `TreatmentPlanPdfData` mapping (tooth grouping, installment rows).
Depends: TP-01, FIN-07.
Fix: quirks 6, 19, 24.

### TP-06 · Fulfillment hooks for appointment screens [S]
Route · inside in-person/video appointment screens · Spec refs §4.3 · API: `GET /api/treatment-plans/{id}/fulfillment-candidates?appointmentId`, `POST …/appointments/{apptId}/fulfill`, `POST …/lines/append`
Build: repository methods + a small `PlanFulfillmentSection` composable (candidate checklist with `toothMatch` highlight, confirm) that the appointments module embeds; emits finance refresh on success.
Done when: completing an appointment with all lines fulfilled shows plan COMPLETED on next list load.
Depends: TP-01, appointments module.
Fix: moves plan logic out of the dental panel into a reusable unit.
