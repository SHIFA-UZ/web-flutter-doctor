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
