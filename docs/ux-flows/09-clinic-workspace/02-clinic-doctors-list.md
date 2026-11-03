# Clinic doctors list

| | |
|---|---|
| Area | Clinic workspace |
| Who sees it | All members (doctor, staff). Revenue-share actions only for users with finance-settings permission (owner, clinic admin, or backend flag). No tier gate. |
| Reached from | Clinic workspace root → "Doctors" sub-tab; Services editor and wizard reuse the same member list but not this screen |
| Screen type | Sub-tab of the workspace (list with toolbar) |
| Current Flutter source | `lib/features/clinic/presentation/clinic_workspace_screen.dart` (`_DoctorsTab`), `lib/state/clinic/clinic_providers.dart` |
| Spec | `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md` §2 row 1, §3.8, ticket CLINIC-05 |

## 1. Purpose
See who works at the clinic, in which role, what revenue split applies to each doctor, and jump to a colleague's schedule to book on their behalf. Owners open it when setting up revenue sharing (rare) and to reach a doctor's calendar (daily for receptionists).

## 2. Entry points
- Workspace sub-tab "Doctors" (replaces sub-tab content; filters reset when the workspace is re-entered).
- Finance → Doctor earnings has a hint "Set the clinic default share or tap a doctor's share % to override per doctor." pointing here (no link today; target: tapping the hint opens this tab).

## 3. Layout, top to bottom (phone portrait)
1. **Permission hint banner** (only when the user may manage finance settings): info icon + "Tap revenue share or use % to edit" + trailing outlined button "Default share" → opens the default revenue-share dialog (file 03).
2. **Search field** "Search doctor or role…" (debounced, filters name, role label and profile number).
3. **Role filter chips**, single-select: "All" + one chip per role actually present in the list (e.g. Owner, Doctor, Receptionist). Chips are built from data, so a clinic with only doctors shows "All · Doctor".
4. **Member list** (today a wide table with Name / Role / Profile # / User # / Revenue share / Actions; on phones the two id columns are hidden). Target phone layout: one **card row** per member:
   - Leading avatar (initial of display name in a teal circle; "?" when the name is empty).
   - Title: display name (bold, 1 line). Subtitle: role label.
   - Second line: revenue share text — "60% doctor / 40% clinic" or "Not set"; underlined/teal when the current user may edit it (role of the member is Doctor or Owner).
   - Trailing: calendar icon button "Open this doctor's schedule" (always), and a percent icon button "Edit revenue share" (only when editable).
   - Tap on the row body = open schedule; tap on the share text = edit share.
5. Sort: default by name A→Z. Table headers were sortable (name, role, ids); on the phone offer a sort menu (Name, Role) in the toolbar overflow.
6. No FAB. Adding doctors is not possible here (invitations only cover receptionists — see file 19).

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Members | `GET /api/clinics/{id}/members` → list of {doctorProfileId, userId, displayName, membershipRole, doctorRevenueSharePercent?, effectiveRevenueSharePercent?} | One request, no paging; whole list in memory |
| Display name | member | As stored; fallback when empty: "Doctor #<profileId>" (today: empty text with "?" avatar) |
| Role | membershipRole | Localised label; unknown values shown raw |
| Revenue share | effectiveRevenueSharePercent (override ?? clinic default) | Integer %; text `{{doctor}}% doctor / {{clinic}}% clinic` with clinic = 100 − doctor; null → "Not set" |
| Override marker | doctorRevenueSharePercent non-null | Target: small "custom" tag so an owner can tell an override from the default (today indistinguishable) |
| Profile # / User # | ids | Hidden on phones; show in a long-press detail sheet if ever needed |
| Editable share | current user may manage finance settings AND member role ∈ {Doctor, Owner} | Receptionist/nurse/clinic-admin members never get a share |

## 5. Primary flow (happy path)
1. Tap "Doctors" → skeleton rows (avatar circle + two text lines) × 6.
2. List arrives → rows render, chips built from roles present.
3. Owner taps a doctor's share text → revenue-share dialog (file 03) → Save → list refreshes, new share visible, banner "Saved".
4. Receptionist taps the calendar icon → book-on-behalf schedule (file 06) pushed full screen.

```mermaid
flowchart TD
  R[Row for member M] --> Q{Current user may manage finance settings?}
  Q -- no --> V[Share read-only · schedule button only]
  Q -- yes --> W{M role is Doctor or Owner?}
  W -- no --> V2[Share "—" · no percent button]
  W -- yes --> E[Share tappable + percent button]
  E --> D[03 dialog] --> S{Saved?}
  S -- yes --> L[Reload members · banner Saved]
  S -- no --> B[Banner with reason · dialog stays]
```

## 6. Screen states
- **First load**: toolbar visible immediately; 6 skeleton rows.
- **Empty**: "No doctors listed for this clinic." (illustration optional). Also used when search/filters match nothing — target: distinguish with "Nothing matches your search or filters." + "Clear filters".
- **Error on load**: top banner "Couldn't load clinic doctors." + Retry in body (today raw exception text).
- **Offline**: last list from cache, share edits disabled.
- **Refreshing**: pull-to-refresh; rows keep height.
- **Partial data**: clinic summary missing (permission unknown) → hide the hint banner and share editing until it arrives.
- **Permission needed**: n/a.
- **Locked by tier**: n/a.
- **Role difference**: owner/clinic admin (or backend flag): hint banner + "Default share" + editable shares for Doctor/Owner members. Member doctor / staff: read-only shares, no banner; schedule button for everyone. Target: a plain member doctor should not see other doctors' share percentages at all (open question — today everyone sees them).

## 7. Interactions and secondary flows
- **Type in search** → list filters live (contains, case-insensitive) on name, role label, profile number.
- **Tap role chip** → filter; "All" clears.
- **Tap share text / percent icon** (editable) → file 03 dialog → on success the member list reloads and finance data refreshes.
- **Tap "Default share"** → file 03 dialog (default variant) → on success the clinic summary reloads (identity bar unaffected) and finance refreshes.
- **Tap calendar icon / row** → push book-on-behalf schedule for that member (file 06), full screen with its own app bar; back returns here with filters intact.
- **Pull-to-refresh** → reload members.
- **Long-press row** → target: sheet with Profile #, User #, role, share (debug-ish detail; optional).

## 8. Edge cases
- 1 member (owner only): single row, chips "All · Owner"; hint banner still useful.
- 40 doctors: list scrolls; search is essential; consider alphabetical section headers.
- Member with revenue share 0 % → "0% doctor / 100% clinic" (valid, not "Not set"). 100 % → "100% doctor / 0% clinic".
- Share override equal to the default → looks like default; show "custom" tag anyway.
- Clinic default not set and member has no override → "Not set" for everyone; Finance earnings will show "—" share columns.
- Member whose role is Receptionist appears in this "Doctors" list (it is really a members list) — keep the tab name "Doctors" but the role chip makes it clear; open question whether to rename to "Team".
- Two members with the same display name → add profile # in the subtitle only when duplicates exist.
- Very long name (40+ chars) → 2 lines max then ellipsis.
- Member removed by an admin between load and tap → schedule push gets 403/404 → banner "This doctor is no longer a member." and reload list.
- Share edit while another owner changes the default → the dialog saves the override regardless; list reload shows the final effective value.
- 401 mid-edit → dialog closes, logout flow.
- Rotation/tablet: list may become the old table (name, role, share, actions) — allowed at ≥ 840 dp.
- 200 % font: card rows grow to 3–4 lines; trailing icons wrap under the text.
- Language switch: role labels and share sentence re-render; chips rebuild.
- Screen reader: row = "Dr Name, Doctor, 60 percent doctor 40 percent clinic, button edit revenue share, button open schedule".
- Backend flag `canManageFinanceSettings` true for a plain Doctor member → editing appears for them; the hint banner must then not say "owner" anywhere.
- Member list cached from a previous clinic → when the user switches clinic in the identity bar the list must show a skeleton, never the old clinic's doctors.

## 9. Validation and messages
| Field/action | Rule | When checked | Message (EN) | Where shown |
|---|---|---|---|---|
| Load members | HTTP 200 | on load | "Couldn't load clinic doctors." (**NEW**) | banner + Retry |
| Open schedule | member still exists | on push | "This doctor is no longer a member." (**NEW**) | banner |
| Share edit | see file 03 | — | — | — |

## 10. Navigation out
- → Revenue-share dialog (file 03): modal, returns here; list refreshes on save.
- → Book-on-behalf schedule (file 06): full-screen push; returns here.
- Workspace sub-tab change: search and chip state are kept while the workspace stays mounted; reset when the app restarts.

## 11. Copy
- `clinicWorkspaceDoctors` "Doctors", `clinicDoctorsSearchHint` "Search doctor or role…", `clinicTreatmentPlansAll` "All" (reused for the All chip), `clinicDoctorsRevenueShareHint` "Tap revenue share or use % to edit", `clinicFinanceConfigureDefaultShare` "Default share"
- Columns (table variant): `clinicDoctorsColName` "Name", `clinicDoctorsColRole` "Role", `clinicDoctorsColProfileId` "Profile #", `clinicDoctorsColUserId` "User #", `clinicDoctorsColRevenueShare` "Revenue share", `clinicDoctorsColActions` "Actions"
- Share text: `clinicDoctorRevenueShareSummary` "{{doctor}}% doctor / {{clinic}}% clinic", `clinicDoctorRevenueShareNotSet` "Not set"
- Buttons: `clinicDoctorRevenueShareEdit` "Edit revenue share", `clinicDoctorOpenSchedule` "Open this doctor's schedule"
- Empty: `clinicWorkspaceNoDoctors` "No doctors listed for this clinic.", `clinicTableNoFilteredResults` "Nothing matches your search or filters."
- **NEW**: "Couldn't load clinic doctors.", "This doctor is no longer a member.", "Custom" (override tag), "Clear filters", "Sort"

## 12. Known problems in the current app (do not carry over)
- Rendered as a spreadsheet-style table with horizontal scrolling on phones; two columns are simply hidden.
- Same empty text for "no members" and "nothing matches filters".
- No visual difference between an inherited default share and a per-doctor override.
- Everyone, including a plain member doctor, sees every colleague's revenue split (confirm with product).
- Raw exception text on load error; centred spinner on load.
- "Doctors" list actually contains all members including receptionists.

## 13. Open questions for design
- Rename the tab to "Team" and group by role?
- Should the share be shown to non-owners at all?
- Is a per-row overflow menu (Edit share / Open schedule) better than two trailing icons?
- Do we show the clinic default share as a pinned first card ("Clinic default: 60/40 · Change") instead of a hint banner?
