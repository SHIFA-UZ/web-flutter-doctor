> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 09 · Admin panel — analysis for KMP/CMP rewrite

Scope: `lib/features/admin/**`, `lib/state/admin/**`, `lib/core/util/admin_host.dart`, plus the admin paths of `lib/state/auth/auth_controller.dart`, `lib/core/api/api_client.dart`, `lib/core/api/api_providers.dart`, `lib/core/utils/secure_token_storage.dart`, `docs/ADMIN_LOGOUT_ON_USERS_MENU_ANALYSIS.md`. All paths relative to repo root. Anything not confirmed from code is marked UNVERIFIED.

**Headline:** the admin panel is a desktop-web product that *cannot even start* on Android/iOS today (`isAdminHost` is hard-wired to `false` off-web). See §6 for the recommendation: keep it out of the mobile KMP scope; share only the data layer.

---

## 1. Entry

### 1.1 Host detection
- `lib/core/util/admin_host.dart:6-9` — `isAdminHost` = `kIsWeb && Uri.base.host.toLowerCase().contains('admin')`. On any non-web build it returns `false`, so the admin UI is unreachable on mobile.
- `lib/app/app.dart:487` — `initialRoute: isAdminHost ? AppRoutes.adminLogin : AppRoutes.splash`.
- `lib/app/app.dart:159,198,207,212,225,451` — on admin host the app skips: doctor language sync, FCM setup, inactivity timer, deep-link handling.
- `lib/app/app.dart:191-196` — auth listener: authenticated → unauthenticated pushes `adminLogin` (admin host) or `login` and clears the stack.
- Routes (`lib/app/router.dart:60-62, 217-224`): `/admin/login` → `AdminLoginScreen`, `/admin/forgot-password` → `AdminForgotPasswordScreen`, `/admin` → `AdminShell`. All use `MaterialPageRoute`.
- API client scope (`lib/core/api/api_providers.dart:53-66`): `adminApiClientProvider` (scope `admin`) may only call `/api/admin/**` and `/api/auth/**`; anything else throws `StateError` (`lib/core/api/api_client.dart:94-112`). `apiClientProvider` resolves to the admin client when `isAdminHost`.

### 1.2 Admin login (`lib/features/admin/presentation/admin_login_screen.dart`)
Two-step credential + email-OTP flow, single `Form`, max width 420.

| Step | UI | Call |
|---|---|---|
| Session restore (`initState`, :35-61) | spinner "loading" | `authProvider.restoreSession(forAdmin: true)` then probe `GET /api/admin/dashboard/stats`; 200 → `pushReplacementNamed('/admin')`; failure → `logout(adminOnly: true)`. If nothing restored: `adminApiClient.clearToken()` and `authTokenProvider = null` (:44-47) so a doctor token on the same origin is never reused. |
| Step 1 credentials (:72-93) | `emailOrPhone` (required), `password` (required, obscure toggle), "Continue", "Forgot password" link, "Go to doctor login" link (:314-332, `pushReplacementNamed(AppRoutes.login)`) | `requestAdminLoginOtp(user, pass)` → `POST /api/auth/admin/request-login-otp` `{username,password}`; 200 → `emailHint` (masked email) (`auth_controller.dart:139-165`). Network errors are rewritten as "Cannot reach the Shifa API …" (:155-162). |
| Step 2 OTP (:95-125) | text `adminEmailVerificationSent(hint)`, OTP field (digits only, max 6; verify requires `length >= 6` :97), "Verify and sign in", "Resend code", "Change account" (:146-152 clears OTP + hint) | `verifyAdminLoginOtp(user, pass, code)` → `POST /api/auth/admin/verify-login-otp` `{username,password,code}`; 200 → `{token}` (`auth_controller.dart:168-191`). Then a second privilege probe `GET /api/admin/dashboard/stats`; non-200 → `logout(adminOnly:true)` + "Access denied. Admin privileges required." (:109-114). |

- **Timers:** none in login. Resend has no cooldown (:127-144). The 60 s cooldown exists only in forgot-password.
- Password is re-sent in plain in step 2 (`verify-login-otp` body carries `password` again) — backend contract, keep as is.
- Errors shown via red `SnackBar` with `Exception: ` prefix stripped (:154-158) — not localized.

### 1.3 Forgot password (`admin_forgot_password_screen.dart`)
- Steps enum `_Step { email, otp, newPassword }` (:11). AppBar back button walks steps backwards (:151-162).
- Email step: validator = non-empty, contains `@` and `.` (:208-213) → `sendForgotPasswordOtp(email.lower)` → `POST /api/auth/send-forgot-password-otp` `{identifier, app:'admin'}` (`auth_controller.dart:261-272`; `app` is `'admin'` iff `isAdminHost`). Starts **60 s resend cooldown** `Timer.periodic(1s)` (:43-53); resend button disabled while `>0`, label shows `(N s)` (:252-257).
- OTP step: `_onVerifyCode` only checks 6 digits locally and moves on (:94-103) — **no backend verification until final submit**.
- New password step: validator ≥8 chars, one uppercase, one digit (:135-141); confirm must match. Submit → `resetPasswordWithEmailOtp(email, code, newPassword)` → `POST /api/auth/forgot-password-reset` `{email, emailOtp, app, newPassword}` → `{token}` (`auth_controller.dart:275-306`). If `isAdminHost` and JWT `role != ADMIN` → clears admin token and throws "Reset succeeded but account is not an admin account" (:288-293). On success the token **is saved as an admin session** (:297-303) but the screen just `Navigator.pop`s back to the login screen (:126-127), which does not auto-advance (its restore check ran once in `initState`). User must log in again (UNVERIFIED at runtime; inferred from code).

### 1.4 Token storage — admin vs doctor
- `lib/core/utils/secure_token_storage.dart:7-8` — keys `shifa_doctor_auth_token` and `shifa_admin_auth_token`.
- Web → `SharedPreferences` (= `localStorage`); mobile → `flutter_secure_storage` (`AndroidOptions(sharedPreferencesName:'shifa_doctor_auth_secure')`) (:17-34). One-time migration from legacy prefs on mobile (:55-72).
- `saveToken(forAdmin)`, `readToken(forAdmin)`, `clearToken(adminOnly)` — `adminOnly:false` clears **both** keys (:36-53).
- `restoreSession(forAdmin:true)` (`auth_controller.dart:446-479`): reads the admin key only; rejects expired JWT (`isJwtExpired`, 30 s grace, `lib/core/util/jwt_utils.dart:39-43`) and any token whose `role` claim ≠ `ADMIN` (:458-464).
- In-memory: a single `authTokenProvider` is shared by both `ApiClient` instances (`api_providers.dart:30-47`), so whichever session was set last wins in memory; the per-key storage only matters across reloads. `AuthState.userId` is the literal string `'admin'` for admin sessions (`auth_controller.dart:181`).

### 1.5 Logout
- Sidebar "Logout" → confirm dialog (`admin_shell.dart:232-255`) → `logout(adminOnly: true)` → `pushReplacementNamed('/admin/login')`.
- `logout(adminOnly:true)` (`auth_controller.dart:383-414`): skips the doctor FCM-token clear, clears client token + `authTokenProvider`, removes only the admin storage key, still invalidates a pile of **doctor** providers (profile, clinics, appointments, patients) — harmless but noisy.
- Automatic logout: `api_providers.dart:24-27` → `logout(adminOnly: isAdminHost)` on the unauthorized callback. `api_client.dart:41-59`: fires on 401 only if a token was actually sent; on 403 only if JWT is expired or body signals `Account is disabled` / `Session invalid` / `Session expired or signed out` (:65-82). A proactive pre-request expiry check also logs out (:31-37).
- `docs/ADMIN_LOGOUT_ON_USERS_MENU_ANALYSIS.md` describes the *older* behaviour ("any 401/403 → global logout → doctor login") and recommends exactly the 401-only + admin-login-redirect that is now implemented. The doc is **stale**; treat it as history, not spec.

### 1.6 Shell re-verification
`AdminShell.initState` repeats restore + `GET /api/admin/dashboard/stats` (`admin_shell.dart:41-64`) and shows a spinner until done. Net effect: dashboard stats is fetched three times on a cold login (login probe, shell probe, dashboard provider).

---

## 2. Admin shell and screens

### 2.1 Layout (`admin_shell.dart`)
- `Scaffold > Row[ Container(width:240, grey900 sidebar), Expanded(_screens[_selectedIndex]) ]` (:113-198). Not responsive; no drawer/rail variant. Each child screen has its **own** `Scaffold`+`AppBar` (nested Scaffolds, contrary to project rule).
- Tab switching swaps the child widget (`:195`), not an `IndexedStack` → screen state (filters, page, selection) is lost on every tab change.
- Sidebar header: logo + hardcoded "Admin Panel". Nav items (hardcoded English labels, :79-90), **in order**:
  1. Dashboard (`AdminDashboardScreen`)
  2. Doctor Activity (`AdminDoctorActivityScreen`)
  3. Payments Ops (`AdminPaymentsScreen`)
  4. Tokens (`AdminTokensScreen`)
  5. Users (`AdminUsersScreen`)
  6. Clinics (`AdminClinicsScreen`)
  7. Create admin (`AdminCreateAdminScreen`)
  8. Audit Logs (`AdminAuditLogsScreen`)
  9. Settings (`AdminConfigScreen`)
  10. Deleted Patients (`AdminDeletedPatientsScreen`)
- Extra list tile "Command Palette · Ctrl/Cmd + K" (:161-172) and keyboard `Shortcuts` for Ctrl+K / Cmd+K (:99-112). Palette = dialog with text filter over nav labels; each row shows `Alt+{n}` (:300) but **no Alt+n shortcut is registered** — decorative only.
- Logout tile at bottom (localized `logout`).
- There is **no Activity Logs screen** even though `activityLogsProvider`/`ActivityLogEntry`/`getActivityLogs` exist (see §7).

### 2.2 Dashboard (`admin_dashboard_screen.dart`)
- Watches `dashboardStatsProvider` → `DashboardStats {totalDoctors, activeDoctors, totalPatients, totalUsers, activeTokens}`.
- Five `_ModernStatCard`s (width 245, `Wrap`) with title/value/delta/subtitle/icon.
- **Deltas are fabricated** (:44-47): `doctorDelta = activeRate>=80 ? 8 : -4`, `patientDelta = total>=30 ? 6 : -2`, `tokenDelta = active>=10 ? 5 : -3`, `usersDelta = total>=50 ? 4 : -1`. Subtitles "healthy workforce", "growth signal", "invitation lifecycle" are static.
- Period `SegmentedButton` 7d/30d/90d is fixed to `{'30d'}` with a no-op handler (:73-81).
- "Alerts" list derived by thresholds (:49-53); "Needs Attention" todo list (:55-59) always includes "Review audit logs…".
- "Quick actions" buttons (`generateToken`, `viewUsers`, `viewLogs`) have empty `onPressed` (:174,179,184).
- Do not copy any of the above heuristics; a rebuilt dashboard should show the five raw counts only (or get real deltas from the backend).

### 2.3 Users (`admin_users_screen.dart`, 1173 lines) + stats panel (`admin_user_stats_panel.dart`)
**Data sources:** `userManagementStatsProvider` (header panel) and either `adminUsersProvider(UsersProviderParams)` or `adminPatientProfilesWithoutAppProvider(PatientProfilesListParams)` depending on `_viewMode` (:13, :110-112).

**Local state** (:23-31): `_currentPage`, `_viewMode {appUsers, profileOnly}`, `_filterRole`, `_filterEnabled`, `_filterDeviceRegistered`, `_searchQuery`, search controller + focus node, 400 ms debounce timer (:82-87). Page size fixed 20.

**Header stats panel** (`AdminUserStatsPanel`): `_RateBadge` deviceActivationRate (green ≥50, orange ≥25, else red :115) and six `_StatTile`s (width 210) that act as quick filters (:48-65 in users screen):
| Tile | Value | Tap → |
|---|---|---|
| totalUsers | `totalUsers` (sub: `totalAdmins` admins) | none |
| patientAppUsers | `patientAppUsers` (sub: `activePatientUsers` enabled) | role=PATIENT |
| totalPatientProfiles | `totalPatientProfiles` (sub: with/without app account) | profile-only view |
| patientsWithDevice | `patientsWithDevice` | role=PATIENT, device=true |
| patientsWithoutDevice | `patientAppUsersWithoutDevice` | role=PATIENT, device=false |
| totalDoctors | `totalDoctors` (sub: active · with device) | role=DOCTOR |
Plus `_EngagementBar` stacked bar with-device / without-device and legend incl. `patientsNeverLoggedIn` (panel :232-312).

**Filter bar** (:164-306): search field (submit, clear, debounce) + Search button; dropdowns: view mode (`appAccounts` / `profileOnlyNoApp`), role (All/DOCTOR/PATIENT/ADMIN), status (All/enabled/disabled), device (All/withDevice/withoutDevice) — the last three hidden in profile-only mode; Refresh button (`_refreshUsers` invalidates current list + stats :67-74). "Showing {count} of {total}" line (:311-329). Any filter change resets page to 0.

**Rows:** this is a `SliverList` of **cards**, not a table.
- `_UserCard` (:559-925): avatar initial, `displayName`, role label, email/`noEmail`, phone/`noPhone`, `lastLogin` or `neverLoggedIn` (PATIENT/DOCTOR only), `joined` (createdAt); chips: device (PATIENT/DOCTOR), `locked` (if `isLocked`), subscription tier (non-ADMIN; BASIC blueGrey / PRO indigo / PREMIUM deepPurple), enabled/disabled.
- `_ProfileOnlyCard` (:437-557): name, "patient", email, phone, `createdByDoctor: name`, `profileCreated: yyyy-MM-dd`, `profileId`, chips `profileOnlyNoApp` + `noAppAccount`. No actions.

**Per-user actions (PopupMenu, :724-918)** — all `ref.read(adminActionsProvider)` then `ref.invalidate(adminUsersProvider(usersParams))`:
| Action | Condition | Endpoint | After |
|---|---|---|---|
| Enable / Disable | always | `POST /users/{id}/enable {enabled}` | invalidate list |
| Unlock | `user.isLocked` | `POST /users/{id}/unlock` | invalidate list |
| Reset password | always | `POST /users/{id}/reset-password` → `temporaryPassword` | dialog with `SelectableText` temp password + "share securely" |
| Change subscription tier | role ≠ ADMIN | `PATCH /users/{id}/subscription-tier {tier}` | radio dialog; PATIENT may pick PRO/PREMIUM, others BASIC/PRO/PREMIUM (:969-971); no-op if unchanged |
| Force logout | always | `POST /users/{id}/force-logout` | snackbar `userLoggedOut` (no invalidate) |
| Delete user | role ≠ ADMIN | `DELETE /users/{id}` | reason dialog (required) — **reason is only echoed in the snackbar, never sent** (:1108-1113) |
| Reset doctor calendar | role == DOCTOR | `POST /doctors/{profile.doctorId}/reset-calendar` | reason dialog; same — reason not sent (:1073-1079). If `profile.doctorId` missing → warning icon + snackbar `doctorProfileIdNotFound` |
`_collectActionReason` (:1124-1172) is a shared confirm dialog with a required free-text "Reason (required)" (hardcoded label) and a destructive `ShifaPrimaryButton`.

**Create admin** is a separate nav item (§2.9); **patient profiles without account** is the profile-only view above (`GET /users/patient-profiles/without-app-account`).

### 2.4 Doctor activity (`admin_doctor_activity_screen.dart`) + detail panel (`admin_doctor_activity_detail_panel.dart`)
- **No provider**: list is loaded imperatively into `setState` (`_listLoading/_listError/_listData`, :50-52, :133-162) via `adminActionsProvider.listDoctorActivity(...)`. `DoctorActivityParams` is built (:119-131) only as a value bag; `DoctorActivityDetailParams` is never used.
- Filters: preset chips 7 / 30 / 90 days / All time / Custom (`showDateRangePicker`, :191-210); dates are **date-only UTC `yyyy-MM-dd`** (:87-117); search "doctor / clinic" with 400 ms debounce; Refresh; Export CSV.
- Sort (server side, `sort` + `dir`): `name` (asc default), `appointments` (default, desc), `completed`, `patientscreated`, `airequests`, `lastactive` (:164-189, :538-561). Page size 25 (:36); pager "Page x of y (N doctors)".
- **Table**: a `DataTable` inside nested horizontal+vertical `SingleChildScrollView`s with two `Scrollbar`s (:508-648), 24 columns in order: Doctor*, Clinic, Date joined, Trial (inline `DropdownButton` 1–12 mo), Monthly $ (dropdown 15/20/25/30/35/40/45/50), Total debt, SMS on, SMS sent, SMS owed, Booked*, Done*, Cncl %, Act pt, Pt new*, Docs, Plans, Tasks, Notes, Forms, AI req*, Drafts, Video, Last act*, Contract (number + PDF icon). `*` = sortable.
- Inline edits call `PATCH /doctors/{id}/subscription-billing {trialPeriodMonths | monthlyChargeUsd}` then reload the whole list (:241-264); one in-flight edit at a time (`_billingUpdateLoadingDoctorId`).
- **Early-partner contract** (:212-234, `services/admin_early_partner_contract_pdf.dart`): `POST /doctors/{id}/early-partner-contract/issue` → JSON (`contractNumber`, `effectiveDate`, `termMonths`, partner name/clinic/phone/email, role flags, `newAllocation`) → `earlyPartnerContractPdfDataFromIssue` (`lib/features/legal/pdf/early_partner_contract_from_issue.dart:16-32`) → `generateEarlyPartnerContractPdf` (client-side `pdf` package + DejaVuSans, `lib/features/legal/pdf/early_partner_contract_pdf_service.dart:25`) → `dl.downloadPdfBytes` (opens new tab, web only). Filename `{contractNumber}_{doctor_slug}.pdf` (fallback slug `hamkor`). The PDF layout itself is in the `legal` feature (another area; not analysed here).
- **CSV export** (:327-381): client-side, **current page only**, 26 columns, `downloadBytes(... 'text/csv')` web-only.
- Row tap → `endDrawer` `Drawer(width: 440)` with `AdminDoctorActivityDetailPanel` (:414-430). The panel loads `GET /doctors/{id}/activity?from&to` on `refreshToken` change (:58-90); `refreshToken` increments after every list load (:153), so in practice it loads on open. Shows: name, clinic, contract number, **SMS reminders toggle** (`_AdminSmsRemindersToggle`, `PATCH /doctors/{id}/sms-reminders-allowed {allowed}`, optimistic with revert; text "Billed at {price} {currency} per SMS", "Period: N sent · M owed"), "Partnership contract PDF" button, pills Booked/Completed/AI req/Video, then seven `fl_chart` line charts from `dailySeries` keys `appointments, completed, aiRequests, documents, treatmentPlans, remoteTasks, consultationNotes` (:253-259).
- Note the SMS toggle builds `AdminActions(apiClient: ref.read(apiClientProvider))` by hand (:318) instead of `adminActionsProvider`.
- All strings on this screen are hardcoded English.

### 2.5 Clinics (`admin_clinics_screen.dart`)
- Master–detail `Row`: 360 px `ListView.separated` of `AdminClinicSummary` (name, `adminClinicDoctorCount: n`) + pager, `VerticalDivider`, detail pane (:296-354). List via `adminClinicsProvider(ClinicsListParams(page, size:50))` (server `sort=name`); detail via `adminClinicDetailProvider(clinicId)`.
- Create / Edit dialog (:31-119): fields name, timeZone (free text, default `Asia/Tashkent`), phone, email, address (2 lines). **No validation** — empty name is sent. `POST /clinics` or `PUT /clinics/{id}`; empty optional fields are omitted from the body (`admin_actions.dart:454-460, 475-481`).
- Detail (`_ClinicDetailView`, :361-472): name, Edit, Assign doctor; timezone/phone/email/address lines; doctors heading + owner hint; per member `Card(ListTile)` with subtitle `doctorProfileId=… · userId=…`, role chip opening a `PopupMenuButton` of `OWNER / CLINIC_ADMIN / DOCTOR` (`PATCH /clinics/{id}/doctors/{pid}/role {membershipRole}`), remove icon (confirm → `DELETE /clinics/{id}/doctors/{pid}`).
- Assign doctor (:121-190): reads `adminUsersProvider(role:'DOCTOR', page:0, size:100).future`, keeps users whose `profile.doctorId > 0`, shows a dropdown "{displayName} (#pid)" → `POST /clinics/{id}/doctors/{pid}`. **Caps at the first 100 doctors** and ignores enabled filter.
- No delete-clinic action, no clinic search.

### 2.6 Tokens (`admin_tokens_screen.dart`)
- What they are: **doctor onboarding invitation keys** (`purpose` defaults to `DOCTOR_ONBOARDING`, `admin_models.dart:7,31`; `AdminActions.generateToken` purpose default :38, regenerate hardcodes it :148). A doctor registers with `keyCode`; `consumed` flips when used (UNVERIFIED on backend; inferred from model/labels).
- List: `adminTokensProvider(TokensProviderParams(consumed, page, size:20))`; filter dropdown All / active (`consumed=false`) / consumed (`consumed=true`); Refresh. `purpose` filter exists in params/API but has no UI.
- Card: `keyCode` monospace 18, status chip consumed (grey) / expired (orange) / active (green), `expires: <DateTime.toLocal()>` raw, notes. Menu: Copy key (not consumed), Revoke (not consumed; `POST /tokens/{id}/revoke`), Regenerate (always, even when consumed; `POST /tokens/{id}/regenerate {expiresInDays:null, purpose}`).
- Generate dialog (+ in AppBar): `expiresInDays` optional int (hint 30), notes optional → `POST /tokens/generate {expiresInDays, purpose:'DOCTOR_ONBOARDING', notes, sendEmail:false, emailTo:null}`. `sendEmail/emailTo` are supported by the action (`admin_actions.dart:36-49`) but have no UI.
- Error state renders the full exception **and stack trace** into the page (:118-130).

### 2.7 Config / "Settings" (`admin_config_screen.dart`)
- `systemConfigProvider` → `GET /api/admin/config` → `Map<String,String>`; rendered as cards "Title Cased key / value" with an edit icon → dialog single text field → `PUT /api/admin/config/{key} {value}` → invalidate.
- The set of keys is backend-defined; **none are known client-side** (UNVERIFIED which keys exist). Key formatting helper handles `snake_case` and `camelCase` (:13-45).

### 2.8 Audit logs (`admin_audit_logs_screen.dart`) and activity logs
- `auditLogsProvider(AuditLogsProviderParams(page, size:50))` — only `page` is ever set from UI; `adminUserId/entityType/entityId/actionType` server filters are **never used**.
- Client-side search (`actionType entityType adminUserId entityId` haystack) and client-side sort (`actionType / entityType / adminUserId / createdAt`) over the **current page only** (:119-144).
- `DataTable` columns: Action (Title-cased), Entity `#id`, Admin ID, Created (`DateTime.parse(..).toLocal().toString()`), Details (eye icon → dialog with `SelectableText` of action/entity/ids/IP/UA/created/details map).
- Activity logs: `activityLogsProvider`, `ActivityLogEntry`, `getActivityLogs` exist (`admin_providers.dart:77-85`, `admin_models.dart:353-393`, `admin_actions.dart:671-699`) but **no screen uses them** — dead code.

### 2.9 Create admin (`admin_create_admin_screen.dart`)
- Form (max width 480): email (label is `emailOrPhone` but validator requires `@` and `.`), password (≥8, `passwordMinLength`), confirm, first name, last name, admin level dropdown `READ_ONLY / ADMIN / SUPER_ADMIN` (hardcoded labels) → `POST /api/admin/users/create-admin {email,password,firstName,lastName,adminLevel}`.
- Success: invalidates `adminUsersProvider(UsersProviderParams(page:0,size:20))` only (default params; other filtered lists stay stale), clears fields, green snackbar `adminUserCreated`.

### 2.10 Deleted patients — legal/GDPR export (`admin_deleted_patients_screen.dart`)
- Inputs: phone (original), email (original), userId (optional int); at least one required (:66-73). → `POST /api/admin/patients/deleted/search {phone,email,userId}` → `matches[]` of `DeletedPatientMatch` (`userId, patientProfileId, deletedAt, matchedBy, maskedPhone, maskedEmail`).
- Per match: "Export" → `GET /patients/deleted/{patientProfileId}/export` → JSON pretty-printed into a `SelectableText` card; "Download PDF" → `GET …/export/pdf` bytes → `downloadPdfBytes` (guarded by `kIsWeb`, else snackbar "PDF download is supported on web only.").
- All strings hardcoded English. Matches show `patientProfileId`, `matchedBy`, `deletedAt`; masked phone/email are parsed but not displayed.

### 2.11 Payments ops (`admin_payments_screen.dart`)
- `failedStripeWebhooksProvider` → `GET /api/admin/payments/webhooks/stripe/failed` → `List<FailedWebhookEvent>`.
- Header: select-all checkbox, "selected N", "Retry selected" (disabled while any retry in flight). Cards: checkbox, `eventType`, status chip `processed ? statusFailed : statusUnprocessed`, `eventId` (selectable), created (`yyyy-MM-dd HH:mm:ss` local), retry meta (`retryCount`, `lastRetryAt`, `retriedByAdminUserId`), `failureReason` in red, Retry button (confirm dialog) → `POST …/failed/{id}/retry` → `{success}`.
- Bulk retry is **sequential** per item (:310-341) and reports success/fail counts. Uses `FilledButton` rather than `ShifaButton`.

### 2.12 Cross-cutting per-doctor admin switches
| Feature | Where | Endpoint |
|---|---|---|
| SMS reminders allowed | Doctor Activity detail drawer | `PATCH /api/admin/doctors/{id}/sms-reminders-allowed {allowed}` |
| Reset calendar | Users → doctor popup | `POST /api/admin/doctors/{id}/reset-calendar` |
| Subscription billing (trial months, monthly USD) | Doctor Activity table inline dropdowns | `PATCH /api/admin/doctors/{id}/subscription-billing` |
| Subscription tier (BASIC/PRO/PREMIUM) | Users popup | `PATCH /api/admin/users/{id}/subscription-tier {tier}` |
| Early-partner contract | Doctor Activity row / drawer | `POST /api/admin/doctors/{id}/early-partner-contract/issue` |
Note `doctorId` in `/api/admin/doctors/**` is the **doctor profile id** (`profile.doctorId` / `AdminDoctorActivityRow.doctorId`), not the user id.

---

## 3. State (`lib/state/admin/`)

All providers are plain (non-autoDispose) and keyed by hand-written param classes with `==`/`hashCode` (`admin_provider_params.dart`). Paged results are returned as **untyped** `Map<String,dynamic> {content: List<T>, totalElements, totalPages, number}` — screens cast `data['content'] as List<T>`.

| Provider (`admin_providers.dart`) | Type | Holds | Param / paging | Invalidated by |
|---|---|---|---|---|
| `adminActionsProvider` :8 | `Provider<AdminActions>` | action object over `adminApiClientProvider` | — | — |
| `dashboardStatsProvider` :13 | `FutureProvider<DashboardStats>` | 5 counts | — | never (only re-created on provider rebuild) |
| `adminTokensProvider` :18 | `FutureProvider.family<Map, TokensProviderParams>` | tokens page | `consumed?, purpose?, page=0, size=20` | Tokens screen refresh/generate (current params); revoke/regenerate (`consumed:null,page:0`) |
| `userManagementStatsProvider` :28 | `FutureProvider<UserManagementStats>` | 18 counts | — | Users Refresh |
| `adminPatientProfilesWithoutAppProvider` :33 | `family<Map, PatientProfilesListParams>` | profile-only rows | `search?, page=0, size=20` | Users Refresh (profile mode) |
| `adminUsersProvider` :43 | `family<Map, UsersProviderParams>` | `AdminUser` page | `role?, enabled?, search?, deviceRegistered?, page=0, size=20` | every user action (current params), create-admin (default params), Users Refresh |
| `adminClinicsProvider` :55 | `family<Map, ClinicsListParams>` | `AdminClinicSummary` page | `page=0, size=50` | Clinics `_refreshLists` |
| `adminClinicDetailProvider` :60 | `family<AdminClinicDetail, int>` | one clinic + members | clinicId | Clinics `_refreshLists` (selected id) |
| `auditLogsProvider` :65 | `family<Map, AuditLogsProviderParams>` | `AuditLogEntry` page | `adminUserId?, entityType?, entityId?, actionType?, page=0, size=50` | never from UI |
| `activityLogsProvider` :77 | `family<Map, ActivityLogsProviderParams>` | `ActivityLogEntry` page | `userId?, activityType?, page=0, size=50` | **unused** |
| `systemConfigProvider` :87 | `FutureProvider<Map<String,String>>` | config map | — | Config save |
| `failedStripeWebhooksProvider` :92 | `FutureProvider<List<FailedWebhookEvent>>` | failed events | — | Payments refresh / after retry |

Param classes with **no provider** (value bags only): `DoctorActivityParams {fromIso?, toIso?, search?, sort='appointments', dir='desc', page=0, size=25}` (:178-219) and `DoctorActivityDetailParams {doctorId, fromIso?, toIso?}` (:221-243, unused).

Screen-local state that a rewrite should keep as ViewModel state: Users (filters/page/viewMode/search), Doctor Activity (preset, custom range, search, sort, page, selected doctor, loading ids, list data), Clinics (page, selected id), Tokens (consumed filter, page), Audit (page, search, sort), Payments (`_retryingIds`, `_selectedIds`), Deleted patients (inputs, matches, export JSON, loading flags).

---

## 4. Endpoints (`lib/state/admin/admin_actions.dart` unless noted)

Base: `AppConfig.resolvedApiBaseUrl`; all through `adminApiClientProvider` with `Authorization: Bearer <admin JWT>`. Unless stated, "errors" = any non-200 → `Exception('… : ${body}')` (raw body surfaced to UI).

| # | Method · path | Query | Request body | Response parsed | Errors handled | Src |
|---|---|---|---|---|---|---|
| A1 | `POST /api/auth/admin/request-login-otp` | — | `{username,password}` | `emailHint?` | non-200 → `message`/`error`; connection errors rewritten | auth_controller:139-165 |
| A2 | `POST /api/auth/admin/verify-login-otp` | — | `{username,password,code}` | `{token}` | non-200 → `message`/`error` | auth_controller:168-191 |
| A3 | `POST /api/auth/send-forgot-password-otp` | — | `{identifier, app:'admin'}` | — | non-200 → `message` | auth_controller:261-272 |
| A4 | `POST /api/auth/forgot-password-reset` | — | `{email, emailOtp, app:'admin', newPassword}` | `{token}`; client checks JWT `role==ADMIN` | non-200 → `message` | auth_controller:275-306 |
| 1 | `GET /api/admin/dashboard/stats` | — | — | `DashboardStats` | generic | :14-22 (also used as privilege probe) |
| 2 | `GET /api/admin/users/stats` | — | — | `UserManagementStats` | generic | :24-32 |
| 3 | `POST /api/admin/tokens/generate` | — | `{expiresInDays?, purpose, notes?, sendEmail, emailTo?}` | `AdminToken` | generic | :36-56 |
| 4 | `GET /api/admin/tokens` | `page,size,consumed?,purpose?` | — | Spring page → `content[AdminToken]`, `totalElements`, `totalPages`, `number`; per-item parse failures skipped | 401/403 → "Authentication failed…"; non-200; missing/invalid `content` | :58-133 |
| 5 | `POST /api/admin/tokens/{id}/revoke` | — | `{}` | `AdminToken` | generic | :135-143 |
| 6 | `POST /api/admin/tokens/{id}/regenerate` | — | `{expiresInDays?, purpose:'DOCTOR_ONBOARDING'}` | `AdminToken` | generic | :145-156 |
| 7 | `GET /api/admin/users` | `page,size,role?,enabled?,search?,deviceRegistered?` | — | page of `AdminUser` (`totalElements/totalPages/number` cast `as int`, non-null) | generic | :160-192 |
| 8 | `GET /api/admin/users/patient-profiles/without-app-account` | `page,size,search?` | — | page of `AdminPatientProfileRow` | generic | :194-225 |
| 9 | `GET /api/admin/users/{id}` | — | — | `AdminUser` | generic | :227-235 (**unused**) |
| 10 | `POST /api/admin/users/{id}/enable` | — | `{enabled}` | `AdminUser` | generic | :237-245 |
| 11 | `POST /api/admin/users/{id}/reset-password` | — | `{}` | `temporaryPassword` | generic | :247-256 |
| 12 | `POST /api/admin/users/{id}/force-logout` | — | `{}` | — | generic | :258-264 |
| 13 | `POST /api/admin/users/{id}/unlock` | — | `{}` | `AdminUser` | generic | :266-274 |
| 14 | `PATCH /api/admin/users/{id}/subscription-tier` | — | `{tier}` (upper) | `AdminUser` | non-200 → JSON `message`/`error` else body (backend rejects BASIC for PATIENT, forces logout) | :279-298 |
| 15 | `POST /api/admin/users/create-admin` | — | `{email,password,firstName,lastName,adminLevel}` | `AdminUser` | non-200 → JSON `message`/`error` | :301-329 |
| 16 | `DELETE /api/admin/users/{id}` | — | — | — | 403 "no permission", 404 "User not found", 400 → `message`/`error`, other | :333-356 |
| 17 | `POST /api/admin/doctors/{doctorProfileId}/reset-calendar` | — | `{}` | — | 404 "Doctor not found", other | :360-369 |
| 18 | `POST /api/admin/patients/deleted/search` | — | `{phone?,email?,userId?}` | `matches[DeletedPatientMatch]` | generic | :373-396 |
| 19 | `GET /api/admin/patients/deleted/{patientProfileId}/export` | — | — | raw JSON map | generic | :398-404 |
| 20 | `GET /api/admin/patients/deleted/{patientProfileId}/export/pdf` | — | — | `bodyBytes` (PDF) | generic | :406-412 |
| 21 | `GET /api/admin/clinics` | `page,size,sort=name` | — | page of `AdminClinicSummary` (null-safe totals) | generic | :416-437 |
| 22 | `GET /api/admin/clinics/{id}` | — | — | `AdminClinicDetail` | generic | :439-445 |
| 23 | `POST /api/admin/clinics` | — | `{name, phone?, email?, address?, timeZone?}` | `AdminClinicDetail` | generic | :447-465 |
| 24 | `PUT /api/admin/clinics/{id}` | — | same as 23 | `AdminClinicDetail` | generic | :467-487 |
| 25 | `POST /api/admin/clinics/{id}/doctors/{doctorProfileId}` | — | `{}` | `AdminClinicDetail` | generic | :489-498 |
| 26 | `PATCH /api/admin/clinics/{id}/doctors/{doctorProfileId}/role` | — | `{membershipRole}` (upper) | `AdminClinicDetail` | non-200 → `message`/`error` | :500-521 |
| 27 | `DELETE /api/admin/clinics/{id}/doctors/{doctorProfileId}` | — | — | `AdminClinicDetail` | generic | :523-532 |
| 28 | `GET /api/admin/doctors/activity` | `page,size,sort,dir,from?,to?,search?` (dates `yyyy-MM-dd`) | — | page of `AdminDoctorActivityRow` (num-safe totals; defaults `totalPages=1`) | generic | :536-571 |
| 29 | `GET /api/admin/doctors/{doctorProfileId}/activity` | `from?,to?` | — | `AdminDoctorActivityDetail {row, dailySeries}` | generic | :573-589 |
| 30 | `PATCH /api/admin/doctors/{doctorProfileId}/sms-reminders-allowed` | — | `{allowed}` | — | non-2xx | :591-602 |
| 31 | `PATCH /api/admin/doctors/{doctorProfileId}/subscription-billing` | — | `{trialPeriodMonths?, monthlyChargeUsd?}` (skips call if both null) | — | non-2xx | :604-621 |
| 32 | `POST /api/admin/doctors/{doctorProfileId}/early-partner-contract/issue` | — | `{}` | raw response; JSON fields `contractNumber, effectiveDate, termMonths, partnerFullName/FirstName/LastName, partnerClinic, roleDoctor/rolePatient/roleBoth, partnerPhone, partnerEmail, newAllocation` | generic | :624-633; legal/pdf/early_partner_contract_from_issue.dart:16-32 |
| 33 | `GET /api/admin/audit-logs` | `page,size,adminUserId?,entityType?,entityId?,actionType?` | — | page of `AuditLogEntry` | generic | :637-669 |
| 34 | `GET /api/admin/activity-logs` | `page,size,userId?,activityType?` | — | page of `ActivityLogEntry` | generic | :671-699 (**unused**) |
| 35 | `GET /api/admin/config` | — | — | `Map<String,String>` | generic | :703-711 |
| 36 | `PUT /api/admin/config/{key}` | — | `{value}` | `Map<String,String>` | generic | :713-721 |
| 37 | `GET /api/admin/payments/webhooks/stripe/failed` | — | — | `List<FailedWebhookEvent>` (top-level JSON array) | generic | :723-732 |
| 38 | `POST /api/admin/payments/webhooks/stripe/failed/{paymentEventId}/retry` | — | `{}` | `success: bool` | generic | :734-744 |

Spring page envelope everywhere: `{content:[…], totalElements, totalPages, number}`. Endpoints 7, 8, 33, 34 cast totals with `as int` (crash on null); 4, 21, 28 are null-safe.

---

## 5. Models (`lib/features/admin/domain/admin_models.dart`) — all plain classes with `fromJson`, none has `copyWith`

| Model | Fields (type · nullability · default) | Derived | Lines |
|---|---|---|---|
| `AdminToken` | `id:int`, `keyCode:String`, `consumed:bool`, `expiresAt:String?`, `purpose:String` (default `'DOCTOR_ONBOARDING'`), `notes:String?`, `emailSentTo:String?`, `emailSentAt:String?`, `createdAt:String` | `isExpired` (`DateTime.parse(expiresAt) < now`), `isValid = !consumed && !isExpired` | :2-46 |
| `AdminUser` | `id:int`, `email:String?`, `phone:String?`, `role:String` (DOCTOR/PATIENT/ADMIN as strings), `enabled:bool`, `lastLoginAt:String?`, `failedLoginAttempts:int` (non-null cast), `lockedUntil:String?`, `profile:Map<String,dynamic>?`, `subscriptionTier:String` (upper, default `'PREMIUM'`), `deviceRegistered:bool=false`, `hasProfile:bool=false`, `createdAt:String?`, `emailVerified:bool=false` | `isLocked` (`lockedUntil > now`), `displayName` (profile `fullName` → `firstName lastName` → email → phone → `User {id}`), `doctorProfileId()` (profile `doctorId` as int/num/String) | :48-138 |
| `AdminPatientProfileRow` | `patientProfileId:int` (0 if missing), `fullName:String` (''), `phone:String?`, `email:String?`, `createdAt:String?`, `createdByDoctorId:int?`, `createdByDoctorName:String?` | — | :140-177 |
| `AdminClinicSummary` | `id:int`, `name:String`, `phone:String?`, `email:String?`, `address:String?`, `timeZone:String` (default `'Asia/Tashkent'`), `doctorCount:int`, `updatedAt:String` ('') | — | :179-219 |
| `AdminClinicDoctorMember` | `doctorProfileId:int`, `userId:int`, `displayName:String`, `membershipRole:String` (default `'DOCTOR'`; values OWNER/CLINIC_ADMIN/DOCTOR) | — | :221-249 |
| `AdminClinicDetail` | summary fields minus `doctorCount` + `createdAt:String`, `doctors:List<AdminClinicDoctorMember>` | — | :251-297 |
| `AuditLogEntry` | `id:int`, `adminUserId:int` (0 if missing), `actionType:String`, `entityType:String`, `entityId:int?`, `details:Map?`, `ipAddress:String?`, `userAgent:String?`, `createdAt:String` | — | :299-351 |
| `ActivityLogEntry` (unused) | `id:int`, `userId:int`, `activityType:String`, `ipAddress:String?`, `userAgent:String?`, `success:bool`, `failureReason:String?`, `createdAt:String` | — | :353-393 |
| `UserManagementStats` | 18 ints, all tolerant-parsed: `totalUsers, totalDoctors, activeDoctors, disabledDoctors, patientAppUsers, activePatientUsers, totalAdmins, totalPatientProfiles, profilesWithoutAppAccount, profilesWithAppAccount, patientsWithDevice, patientAppUsersWithoutDevice, doctorsWithDevice, doctorsWithoutDevice, patientsNeverLoggedIn, patientsLoggedIn, doctorsNeverLoggedIn, deviceActivationRate` (percent) | — | :395-464 |
| `DashboardStats` | `totalDoctors, activeDoctors, totalPatients, totalUsers, activeTokens` : int (strict casts) | — | :466-490 |
| `DeletedPatientMatch` | `userId:int`, `patientProfileId:int`, `deletedAt:String?`, `matchedBy:String`, `maskedPhone:String?`, `maskedEmail:String?` | — | :492-519 |
| `FailedWebhookEvent` | `id:int`, `eventId:String`, `eventType:String`, `processed:bool`, `processedAt:String?`, `failureReason:String?`, `retryCount:int=0`, `lastRetryAt:String?`, `retriedByAdminUserId:int?`, `createdAt:String` | — | :521-560 |
| `AdminDoctorActivityDailyPoint` | `date:String`, `count:int` | — | :562-577 |
| `AdminDoctorActivityRow` | `doctorId:int` (profile id), `doctorName:String`, `email:String?`, `clinicId:int?`, `clinicName:String?`, `appointmentsBooked, appointmentsCompleted, appointmentsCancelled:int`, `cancellationRate:double` (0–1), `videoAppointments, activePatients, patientsCreated, documentsUploaded, treatmentPlans, remoteTasks, consultationNotes, patientForms, aiRequests, aiDraftNotes:int`, `lastActiveAt:String?`, `earlyPartnerContractNumber:String?`, `smsRemindersAllowed:bool=false`, `smsSentCount:int=0`, `smsOwedMinor:int=0`, `smsCurrency:String='UZS'`, `smsPricePerUnitMinor:int=500`, `dateJoinedAt:String?`, `trialPeriodMonths:int=6`, `monthlyChargeUsd:int=30`, `monthsAfterTrial:int=0`, `totalDebtUsd:int=0` | — | :579-681 |
| `AdminDoctorActivityDetail` | `row:AdminDoctorActivityRow`, `dailySeries:Map<String,List<AdminDoctorActivityDailyPoint>>` (keys seen: appointments, completed, aiRequests, documents, treatmentPlans, remoteTasks, consultationNotes) | — | :683-711 |

Implicit enums to make real in Kotlin: `Role {DOCTOR, PATIENT, ADMIN}`, `SubscriptionTier {BASIC, PRO, PREMIUM}`, `AdminLevel {READ_ONLY, ADMIN, SUPER_ADMIN}`, `ClinicMembershipRole {OWNER, CLINIC_ADMIN, DOCTOR}`, `TokenPurpose {DOCTOR_ONBOARDING, …}` (other values UNVERIFIED), `DoctorActivitySort {name, appointments, completed, patientscreated, airequests, lastactive}`. All timestamps are ISO-8601 strings parsed ad hoc with `DateTime.parse(...).toLocal()` — the admin panel ignores the doctor-timezone architecture entirely (browser local time).

---

## 6. Platform assessment and recommendation

**Web-only code**
- `admin_pdf_downloader_web.dart` (`dart:html` Blob + `AnchorElement`; PDF opened in a new tab, CSV forced download) with `admin_pdf_downloader_stub.dart` throwing `UnsupportedError` off-web. Used by Doctor Activity (contract PDF, CSV) and Deleted Patients (PDF, with an explicit `kIsWeb` guard).
- `isAdminHost` itself is web-only (§1.1) — the admin panel has never run on a phone.
- Keyboard shortcuts (Ctrl/Cmd+K), `Drawer(width: 440)`, fixed 240 px sidebar, 360 px master list, `DataTable`s with 24 columns and horizontal scrollbars, `showDateRangePicker`, `SelectableText` JSON dumps, clipboard copy of keys.

**Is this a desktop-web product?** Yes, unambiguously:
- Information density: 24-column activity table with inline dropdown edits, two-pane clinics, stat grids of 5–6 fixed-width cards (210–245 px), audit `DataTable`.
- Bulk/administrative ops: bulk webhook retry, token lifecycle, GDPR exports, contract issuance with locally generated PDFs, free-text config editing.
- No mobile affordances anywhere (no responsive breakpoints, no bottom nav, nested Scaffolds inside a `Row`).
- Rare-use, high-privilege, audited actions (delete user, reset calendar, force logout) are better done on a large screen with a keyboard, and the legal export flow produces files for lawyers, not for a phone.

**Does it make sense on Android/iOS phones?** No for the product as-is. A phone admin app would at most need a handful of "on-call" toggles (disable user, unlock, force logout, retry webhook, SMS toggle). Nothing in the code or docs shows demand for that.

**Recommendation: (b) keep the admin panel as a separate web app, out of the mobile KMP/CMP scope**, with one cheap hedge toward (a):
- Put the admin **data layer** (models in §5, endpoints in §4, admin-token session rules in §1.4) in its own KMP module `:admin:data` with no UI deps. It is ~40 endpoints and 15 DTOs — a day or two — and it is the only part that is platform-neutral.
- Do **not** build (c) a reduced mobile admin now: zero evidence of need, and every per-user action here is destructive/audited, which argues for a deliberate desktop context, not a phone.
- When the team wants to retire the Flutter web build, (a) rebuild the UI in Compose Multiplatform targeting **desktop/JVM and/or Wasm**, reusing `:admin:data`. Until then the existing Flutter web admin keeps shipping from `firebase deploy --only hosting:admin`.

If the caller nonetheless mandates CMP now, the tickets in §8 are ordered for a desktop/web CMP target, not phones.

---

## 7. Quirks, bugs, dead code, tech debt — do not copy

Bugs / correctness
1. Fabricated dashboard deltas, dead period switch, no-op quick actions (`admin_dashboard_screen.dart:44-47,73-81,174-184`).
2. Delete-user and reset-calendar "reason" is required in the dialog but **never sent** to the backend (`admin_users_screen.dart:1073-1079,1108-1113`) — audit trail loses it. Either add a `reason` field to the API or drop the dialog.
3. Audit-log search/sort act on the current page only; server filter params exist but are unused (`admin_audit_logs_screen.dart:119-144`).
4. Token revoke/regenerate invalidate `TokensProviderParams(consumed:null,page:0)` instead of the active params → list can stay stale when a filter/page is active (`admin_tokens_screen.dart:373-379,406-412`). Regenerate is offered even for consumed tokens.
5. Create-admin invalidates only the default `UsersProviderParams(page:0,size:20)` (`admin_create_admin_screen.dart:68`).
6. Assign-doctor dropdown reads at most the first 100 doctors (`admin_clinics_screen.dart:124`); clinic create/edit has no validation (:93).
7. `listUsers/getAuditLogs/getActivityLogs/listPatientProfiles` cast `totalElements/totalPages/number` with `as int` — crash if the backend omits them (`admin_actions.dart:188-190,221-223,665-667,695-697`).
8. `AdminUser.fromJson` requires `failedLoginAttempts` non-null (`admin_models.dart:96`); `DashboardStats.fromJson` strict casts (:483-487).
9. Forgot-password success saves an admin session but pops back to login without navigating into the shell (`admin_forgot_password_screen.dart:126-127`).
10. After enable/disable/unlock/tier change only the list is invalidated, not `userManagementStatsProvider` → header counters go stale until Refresh.
11. Shell swaps screens instead of `IndexedStack`; every tab switch resets filters/page/selection (`admin_shell.dart:195`).
12. `isLocked`/`isExpired` use `DateTime.now()` vs parsed ISO — fine functionally, but all date display is browser-local, bypassing the project's timezone rules.

Security / privacy hygiene
13. `listTokens` `debugPrint`s the first 500 chars of the response body (contains invitation key codes) and full bodies on parse errors (`admin_actions.dart:73-77,128-130`).
14. Tokens error view dumps exception + stack trace into the UI (`admin_tokens_screen.dart:118-130`).
15. `_UserCard` logs user ids, roles and profile keys on every popup build (`admin_users_screen.dart:880,913,1042,1046`).
16. `api_providers.dart:33,42` prints the first 20 chars of the JWT.
17. Admin privilege is verified only by probing `GET /dashboard/stats` (login :51,:110; shell :50) rather than a dedicated `/me`/claims check; `AdminLevel` (READ_ONLY/ADMIN/SUPER_ADMIN) is never read client-side, so read-only admins see all destructive buttons and only learn on 403.

Dead code
18. `activityLogsProvider`, `ActivityLogsProviderParams`, `ActivityLogEntry`, `AdminActions.getActivityLogs` — no screen (`admin_providers.dart:77-85`, `admin_models.dart:353-393`, `admin_actions.dart:671-699`).
19. `AdminActions.getUser` (:227-235) unused. `DoctorActivityDetailParams` unused; `DoctorActivityParams` has no provider. `TokensProviderParams.purpose` and `generateToken(sendEmail, emailTo)` have no UI.
20. Command palette shows `Alt+n` hints with no such shortcuts (`admin_shell.dart:300`). `_scaffoldKey` on Doctor Activity is only used to open the end drawer.
21. `docs/ADMIN_LOGOUT_ON_USERS_MENU_ANALYSIS.md` is stale (describes pre-fix 401/403 handling).

Tech debt / inconsistency
22. Paged results are `Map<String,dynamic>` casted in widgets (every list screen) — type them (`Page<T>`).
23. `_UserCard` and `_TokenCard` are `ConsumerWidget`s that also take `WidgetRef ref` as a constructor field (`admin_users_screen.dart:559-564`, `admin_tokens_screen.dart:278-282`).
24. `_AdminSmsRemindersToggle` constructs `AdminActions(apiClient: ref.read(apiClientProvider))` by hand (`admin_doctor_activity_detail_panel.dart:318`).
25. Doctor Activity, Deleted Patients, Audit Logs, shell nav, Dashboard subtitles and Create-admin level labels are **hardcoded English**; the rest uses `l10n.translate`. Placeholder styles differ: `{count}` (users) vs `{{count}}` (payments).
26. Error strings concatenate raw backend bodies (`'${l10n.error}: $e'`) instead of `sanitizeErrorMessage` (used only once, `admin_tokens_screen.dart:422`).
27. Nested `Scaffold`s inside the shell `Row`; `FilledButton` in Payments vs `ShifaButton` elsewhere.
28. Triple fetch of `/dashboard/stats` on login (§1.6).
29. `logout(adminOnly:true)` still invalidates doctor-only providers (`auth_controller.dart:405-413`).

---

## 8. Proposed tickets (CMP desktop/web target; shared KMP data layer first)

Sizes: S ≈ ½–1 day, M ≈ 2–3 days, L ≈ 4–6 days. Specs = sections of this file.

### ADMIN-01 · Admin data module: DTOs, page envelope, API client scope [M]
Route — · Spec refs §3, §4, §5 · API: all 38 `/api/admin/**` endpoints + A1–A4
Build: `:admin:data` KMP module. Kotlinx-serialization DTOs for every model in §5 (nullable exactly as listed, defaults as listed), `Page<T>(content, totalElements, totalPages, number)` with null-tolerant totals, enums from §5, `AdminApi` with one suspend fun per endpoint in §4, admin-scoped Ktor client that rejects non-`/api/admin/**`,`/api/auth/**` paths, typed `AdminApiException(status, message)` extracting `message`/`error` from JSON bodies.
Done when: unit tests deserialize a sample payload per DTO (incl. missing-optional cases); path-scope guard test; all §4 endpoints compile against the interface.
Depends: shared core HTTP/config module (outside this area).
Fix: #7, #8, #22 (typed pages, tolerant totals); never log response bodies (#13).

### ADMIN-02 · Admin session: separate token store, role/expiry checks, logout rules [M]
Route `/admin/login` bootstrap · Spec refs §1.4, §1.5, §1.6 · API: `GET /api/admin/dashboard/stats` (probe) or a dedicated claims endpoint if backend offers one (UNVERIFIED)
Build: `AdminSessionRepository` with its own storage key (`shifa_admin_auth_token`; secure storage on JVM/desktop, localStorage on Wasm), JWT `exp` (30 s grace) and `role == ADMIN` validation, `restore()`, `logoutAdminOnly()`; 401 → logout; 403 → logout only on expired JWT or the three body markers in `api_client.dart:68-72`; expose `AdminLevel` from the JWT if present (UNVERIFIED claim name).
Done when: restoring a doctor token on the admin app fails closed; expired token triggers one logout; 403 on a valid token shows an error and keeps the session; single privilege probe per cold start.
Depends: ADMIN-01.
Fix: #17 (one probe), #28, #29.

### ADMIN-03 · Admin login (password + email OTP) [M]
Route `/admin/login` · Spec refs §1.2 · API: A1, A2, probe from ADMIN-02
Build: two-step form (identifier+password → 6-digit code with masked `emailHint`), resend, "change account", error snackbars, link to doctor login only if the doctor web app exists in the new stack (else drop). Add a 60 s resend cooldown to match forgot-password.
Done when: happy path lands in the shell; wrong code shows backend message; non-admin account is rejected with "Access denied"; refresh restores the session without re-login.
Depends: ADMIN-02.
Fix: none beyond consistency of resend cooldown.

### ADMIN-04 · Admin forgot password [S]
Route `/admin/forgot-password` · Spec refs §1.3 · API: A3, A4
Build: email → OTP (60 s cooldown) → new password (≥8, uppercase, digit, confirm). On success navigate straight into the shell (session is already set).
Done when: non-admin account surfaces the "not an admin account" error; success lands in the shell.
Depends: ADMIN-02.
Fix: #9.

### ADMIN-05 · Admin shell: sidebar, routing, command palette, logout [M]
Route `/admin/*` · Spec refs §2.1 · API: —
Build: persistent left nav with the 10 items in §2.1 order (localized labels), each section a real route so per-screen state survives switching, Ctrl/Cmd+K palette over nav items, logout confirm → ADMIN-02 logout. Single top-level Scaffold; sections render content only. Make the sidebar collapse to a rail below ~1100 px (desktop windows), no phone layout required.
Done when: deep links to each section work; switching sections and back preserves filters; palette filters and navigates.
Depends: ADMIN-02.
Fix: #11, #20, #27.

### ADMIN-06 · Dashboard stats [S]
Route `/admin` · Spec refs §2.2 · API: 1
Build: five stat cards from `DashboardStats` only; "Quick actions" that actually navigate to Tokens/Users/Audit. No deltas, no period switch, no synthetic alerts unless the backend provides them.
Done when: numbers match API; actions navigate.
Depends: ADMIN-05.
Fix: #1.

### ADMIN-07 · Users list, filters, search, stats panel, profile-only view [L]
Route `/admin/users` · Spec refs §2.3 (list + panel), §3 · API: 2, 7, 8
Build: stats panel with six quick-filter tiles + activation badge + engagement bar; filter bar (view mode, role, status, device, debounced search 400 ms, refresh); paged list (20/page) as a dense **table** on desktop (name, role, contact, last login, joined, tier, device, status, actions) with the card layout as the narrow fallback; profile-only table (name, contact, created by doctor, created, profile id). ViewModel owns `UsersQuery` and reloads stats together with the list.
Done when: every filter maps to the query params in §4 #7/#8; "showing x of y"; empty states; paging.
Depends: ADMIN-05.
Fix: #10 (stats refresh with list), #22.

### ADMIN-08 · User actions: enable/disable, unlock, reset password, force logout, tier, delete, reset calendar [M]
Route `/admin/users` (row menu) · Spec refs §2.3 actions table, §2.12 · API: 10, 11, 12, 13, 14, 16, 17
Build: row action menu with the same visibility rules (unlock only when locked; tier/delete hidden for ADMIN; reset calendar only for DOCTOR with `profile.doctorId`); tier dialog restricting PATIENT to PRO/PREMIUM; temp-password dialog with copy; confirm dialogs for destructive ops. Hide/disable destructive actions when `AdminLevel == READ_ONLY` (from ADMIN-02) if the claim is available.
Done when: each action calls its endpoint, refreshes list+stats, and surfaces backend `message` on 400/403/404.
Depends: ADMIN-07.
Fix: #2 — either send `reason` (needs backend field; UNVERIFIED) or remove the reason prompt; #15, #26.

### ADMIN-09 · Create admin user [S]
Route `/admin/create-admin` · Spec refs §2.9 · API: 15
Build: form email/password/confirm/first/last/admin level (READ_ONLY, ADMIN, SUPER_ADMIN, localized); success toast; invalidate the users query cache globally.
Done when: validation parity (§2.9); created admin appears in Users with role ADMIN.
Depends: ADMIN-07.
Fix: #5; label should say "Email", not "Email or phone".

### ADMIN-10 · Onboarding tokens [M]
Route `/admin/tokens` · Spec refs §2.6 · API: 3, 4, 5, 6
Build: filter All/active/consumed (+ optional purpose), paged list (20), card/table with key (monospace), status chip, expiry (formatted), notes, email-sent info; generate dialog (expiresInDays, notes, optional sendEmail/emailTo since API supports it); copy key; revoke (not consumed); regenerate (not consumed only — confirm product rule, UNVERIFIED).
Done when: mutations refresh the *current* query; errors show a short message, never a stack trace; keys are never logged.
Depends: ADMIN-05.
Fix: #4, #13, #14.

### ADMIN-11 · Clinics master–detail and membership [M]
Route `/admin/clinics`, `/admin/clinics/{id}` · Spec refs §2.5 · API: 21–27, 7 (doctor picker)
Build: list (50/page, sorted by name) + detail pane; create/edit dialog with required name, IANA timezone picker (validate against tz database) instead of free text, phone/email/address; members list with role chip menu (OWNER/CLINIC_ADMIN/DOCTOR) and remove-with-confirm; assign-doctor picker with **server-side search** (`GET /users?role=DOCTOR&search=`) rather than loading 100 rows.
Done when: CRUD round-trips; role change and removal refresh detail + list counts; empty-name is blocked client-side.
Depends: ADMIN-07 (doctor search).
Fix: #6.

### ADMIN-12 · Doctor activity table: ranges, search, sort, paging, CSV [L]
Route `/admin/doctor-activity` · Spec refs §2.4 (list), §3 (`DoctorActivityParams`) · API: 28
Build: ViewModel with preset/custom UTC date range (`yyyy-MM-dd`), debounced search, server sort (six fields) and 25/page paging; virtualized horizontally scrollable table with the 24 columns in §2.4; CSV export of the current page via platform file-save (desktop: file dialog; Wasm: blob download). Inline billing dropdowns are in ADMIN-13.
Done when: sort/paging/range match query params; CSV header matches §2.4; row selection opens ADMIN-13 pane.
Depends: ADMIN-05.
Fix: #25 (localize), keep the per-page-only CSV explicit in the UI label or add server CSV (UNVERIFIED availability).

### ADMIN-13 · Doctor detail pane: trends, SMS toggle, billing, tier of service switches [M]
Route `/admin/doctor-activity?doctor={id}` · Spec refs §2.4 (detail), §2.12 · API: 29, 30, 31
Build: right-side pane (440 px) with header, contract number, pills, SMS-reminders toggle (optimistic, revert on error, shows price/sent/owed), trial-months (1–12) and monthly-USD (15–50) selectors, seven daily line charts from `dailySeries`. Reload row + series after each mutation without reloading the whole table (patch the row in the list state).
Done when: toggles and selectors persist and reflect server values after refresh; charts render for empty series ("No data").
Depends: ADMIN-12.
Fix: #24; avoid full-list reload on every inline edit.

### ADMIN-14 · Early-partner contract issue + PDF [M]
Route `/admin/doctor-activity` (row/pane action) · Spec refs §2.4 contract, §4 #32 · API: 32
Build: call issue endpoint, map JSON (§4 #32 fields) to the contract PDF data, generate the PDF with the shared KMP PDF generator from the legal area (Cyrillic font), save/open via platform file API, show "created/updated {contractNumber}" and refresh the row's contract number. Filename `{contractNumber}_{slug}.pdf`.
Done when: new and existing contract numbers both produce a PDF; one in-flight generation per doctor.
Depends: ADMIN-13, legal PDF generator ticket (other area).
Fix: none; keep PDF layout owned by the legal module.

### ADMIN-15 · Payments ops: failed Stripe webhooks [S]
Route `/admin/payments` · Spec refs §2.11 · API: 37, 38
Build: list with status chip, event id (copyable), created, retry meta, failure reason; single retry with confirm; multi-select + bulk retry (sequential, summary toast); refresh.
Done when: `success=false` shows "still failing"; list refreshes after retries.
Depends: ADMIN-05.
Fix: #27 (consistent buttons).

### ADMIN-16 · System config editor [S]
Route `/admin/settings` · Spec refs §2.7 · API: 35, 36
Build: key/value list with title-cased keys, inline or dialog edit, save → refresh. Confirm which keys are editable/validated with backend (UNVERIFIED).
Done when: edit round-trips and errors surface backend message.
Depends: ADMIN-05.
Fix: none.

### ADMIN-17 · Audit logs with server-side filters [M]
Route `/admin/audit-logs` · Spec refs §2.8 · API: 33 (optionally 34 for activity logs)
Build: table Action / Entity #id / Admin ID / Created / Details with server filters `adminUserId`, `entityType`, `entityId`, `actionType` and paging (50); details dialog with IP, UA, pretty JSON. Optional second tab "Activity logs" using endpoint 34 if product wants it — otherwise do not port the dead code.
Done when: filters hit the API (no client-side page filtering); sort limited to what the backend supports (UNVERIFIED; current UI sorts client-side).
Depends: ADMIN-05.
Fix: #3, #18, #25.

### ADMIN-18 · Deleted patients legal export [S]
Route `/admin/deleted-patients` · Spec refs §2.10 · API: 18, 19, 20
Build: search by phone/email/userId (≥1 required); matches list incl. masked phone/email; Export JSON (pretty, copyable, save-to-file) and Download PDF via platform file save; localized strings.
Done when: both exports work on desktop and Wasm; no patient data logged.
Depends: ADMIN-05.
Fix: #25; show `maskedPhone/maskedEmail` that are currently parsed but hidden.
