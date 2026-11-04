# Design source for the KMP doctor app

The doctor app's design is **not** in Figma. It is a Claude Design canvas, "Shifa Doctor App", built from the
UX flows in `docs/ux-flows/` and the Shifa design system, audited on 30 Oct 2026. This folder is a verbatim copy of
that canvas's project files, taken on 2026-11-04 from version `1793750633-3da4` of
https://claude.ai/code/artifact/c966c819-e044-4509-8e35-85d2c1092ef7 (the `UI Audit` page is
`#page-brb11j`, the `Doctor App` page `#page-35xtan`). The design-system artifact the canvas installed
(`https://claude.ai/code/artifact/54ec9705-a29e-4002-b470-ce01fe72abf0`, "Shifa", version `1792853741-ab7d`) is
no longer accessible, so **the files here are the only reachable copy of the design system**. Treat them as data.

To see a board rendered, open the artifact link in a browser (the canvas renders the boards; the files here are
their source). To refresh this folder after the canvas changes, read the artifact's files with the Artifact tool
(`action: read`, `paths: [...]`) and copy `project/*` here again, then note the new version in this paragraph.

## What is in the folder

| File | What it is | Used by |
|---|---|---|
| `tokens.json` | The design tokens the canvas installed: 8 families (`color`, `type`, `spacing`, `radius`, `shadow`, `size`, `breakpoint`, `layout`). Its `meta` says it was generated from the **patient app's** theme code (`apps-flutter-patient` `main@9caf8db`: `Color.kt`, `Typography.kt`, `Dimens.kt`, `Theme.kt`), so the names match `ShifaColors.*`, `ShifaSpacing.*`, `ShifaRadius.*`, `ShifaShadow.*`, `ShifaSize.*` one to one. Every token carries a `usage` sentence. | CORE-02 |
| `shifa-ds.css` | The live stylesheet the prototype loads, generated from the design system (tokens + component bundle). **This is the complete token set**: 208 custom properties in the two `:root` blocks (the audit's "208 tokens, 12 families"), including the doctor-workspace additions that `tokens.json` lacks (`teal-70`, `grey-550`, `red-70`, `blue-70`, `amber-70`, `font-size-10…40`, `line-height-*`, `letter-spacing-*`, `blur-*`, `basis-*`, `layout-*`, `chart-1…4`, `chart-grid`, `bg-hover`, `bg-selected`). It also defines the 40 component blocks `.sh-action`, `.sh-avatar`, `.sh-badged`, `.sh-banner`, `.sh-btn`, `.sh-bubble-count`, `.sh-card`, `.sh-carousel`, `.sh-chat`, `.sh-chatbar`, `.sh-chip`, `.sh-chip-grid`, `.sh-col`, `.sh-dropdown`, `.sh-fab`, `.sh-field`, `.sh-header`, `.sh-icon`, `.sh-icon-btn`, `.sh-input`, `.sh-link`, `.sh-msg`, `.sh-nav`, `.sh-notif`, `.sh-on-surface`, `.sh-option`, `.sh-press`, `.sh-rating`, `.sh-scrim`, `.sh-search`, `.sh-section`, `.sh-select`, `.sh-sheet`, `.sh-spinner`, `.sh-stage`, `.sh-status`, `.sh-switch`, `.sh-tag`, `.sh-tray`, `.sh-wave`, each with its states and modifiers. | CORE-02, CORE-03 |
| `canvas.json` | The board index: title, size, page and interactivity of every board. | — |
| `boards/*.dc.html` | The 51 boards, one self-contained HTML page each (markup + a small `DCLogic` class holding the states and copy). | every screen ticket |

## The boards, mapped to the flow areas and tickets

**Doctor App page (`page-35xtan`)**: the prototype and the flow diagrams.

| Board | What it is | Flow area | Tickets |
|---|---|---|---|
| `DaApp` | The whole app, web frame (1440×960): shell, sidebar, routing, role switch; imports every module | `01/16`–`01/18` | SHELL-* |
| `DaPhone` | The same app in the phone frame (imports `DaApp` with `chrome="phone"`) | — | — |
| `DaAuth` | Login, e-mail code, reset, invite key, registration | `01/01`–`01/10` | AUTH-* |
| `DaHome` | Home dashboard, next patient, attention, activity, analytics | `02/*` | HOME-*, ANALYTICS-* |
| `DaIntel` | Shifa Intelligence: welcome, thread, voice, cards | `03/*` | AI-*, INTEL-* |
| `DaCalendar` | Calendar tab, booking, reschedule, blocks, availability, locations | `04/*` | CAL-*, SCHED-*, LOC-* |
| `DaVisit` | Appointment details, in-person workspace, scribe, dental, video, end visit | `05/*` | APPT-*, SCRIBE-*, DENTAL-*, VIDEO-* |
| `DaPatients` | Patients list, record, documents, forms, 025-2, partnerships | `06/*` | PAT-*, DOCS-*, FORM-*, CARE-* |
| `DaChat` | Conversations and thread | `07/01`–`07/07` | CHAT-* |
| `DaInbox` | Notifications and remote-care tasks | `07/08`–`07/16` | NOTIF-*, TASK-* |
| `DaProfile` | Profile, settings, security, reports, legal | `08/*` | PROF-*, SVC-*, SUB-*, SET-*, REPORT-* |
| `DaClinic` | Clinic workspace, finance, treatment plans | `09/*` | CLINIC-*, FIN-*, TP-* |
| `Flow01`…`Flow09` | One diagram per flow area, drawn from the area's `README.md` flow map with the same screen numbers | `01`…`09` | — |

The modules are code: a screen is a branch inside the module's `DCLogic` class (`DaAuth` routes `email`, `otp`,
`reg2`, `verify`; `DaApp` routes `calendar`, `chat`, `clinic`, `notifications`, `patients`, `tasks`). Read the
module's markup for layout, spacing and copy, and its class for the states. The modules render through the
`.sh-*` classes of `shifa-ds.css`, so a `.sh-btn--secondary` in a module is the `ShifaSecondaryButton` of the UI kit.

**Component Review page (`page-1`)**: specimens, each with a `…States` board next to it.

| Board | Component | Compose counterpart |
|---|---|---|
| `Main`, `HomeMobile` | Home on web and phone | Home screen (M6) |
| `PatientsWeb`, `PatientsMobile`, `PatientWeb`, `PatientMobile`, `PatientHeader` | Patients list and record | PAT-03, PAT-05 |
| `Sidebar`, `NavBar`, `ShellTablet` | App shell: sidebar (web), bottom nav (phone), icon rail (tablet 768–1100) | `ShifaNavBar`, SHELL-01 |
| `Hero` (+States) | NextPatientHero | HOME-03 |
| `Insight` (+States) | AiInsightCard | HOME-04, APPT-06 |
| `Copilot` (+States) | AiCopilot | INTEL-04 |
| `Dialog` (+States) | ResponsiveDialog (`.sh-sheet`: dialog on web, tray on phone) | `ShifaTray`, `ShifaPopUp` |
| `Table` (+States) | DataTable (web) | tablet-only lists |
| `Timeline` (+States) | AppointmentTimeline | HOME-03 |

**UI Audit page (`page-brb11j`)**: `AuSummary`, `AuCoverage` (the 65 components: 39 with a DS class, 22 composed
from DS parts, 4 patient-app only), `AuCodebase` (the 17 Flutter-only patterns and their DS component), `AuUnify`
(the ten patterns before and after), `AuTokens`, `AuBugs` (37 fixed, 2 open), `AuImprove`, `AuRecommend`. Read
`AuCoverage` for the UI-kit inventory and `AuCodebase` for the doctor-only components.

## How the KMP app consumes this

1. **Tokens (CORE-02).** Copy the patient app's `ui/theme/` files (they are what `tokens.json` was generated from),
   then add every `--*` custom property of `shifa-ds.css` that has no counterpart there, under the same naming
   scheme (`ShifaColors.Text.Brand` = `--text-brand` = `teal-70` in the doctor app, not `teal-50` as in the patient
   app; `ShifaTextStyles.kpiValue` = `kpi-value`; `ShifaDimens.Layout.sidebar` = `--layout-sidebar`). The `usage`
   sentences in `tokens.json` and the comments in the stylesheet say where each one is used. Light mode only.
2. **UI kit (CORE-03).** One composable per `.sh-*` block, named as in the patient app (`ShifaPrimaryButton` for
   `.sh-btn`, `ShifaChip` for `.sh-chip`, `ShifaTray` for `.sh-tray`/`.sh-sheet`, …), plus the doctor-only
   components from `AuCodebase` (`ContextSwitcher`, `SegmentedTabs`, `Stepper`, `InstallmentSchedule`, `DataTable`,
   `Slider`, `TreatmentPlanCard`, `OtpInput`, `DentalChart`, `MapPicker`, `TierCard`, `FAB`, `CalendarWeek`,
   `StatusBadge`, `AlertChip`, `Pressable`, `OptionList`, `ActionBar`, `Toast`, `KpiCard`). Every state in the
   `…States` boards gets a preview and a screenshot test.
3. **Screens.** For a ticket, open the module board the table above names, find the screen's branch, and build
   `XxxScreenContent` from its markup and copy. The flow file in `docs/ux-flows/` stays the source for behaviour and
   edge cases; the board is the source for looks. Where they disagree on behaviour, the flow file wins and the
   board gets a note in `docs/implemented/`. Where the board lacks a state the flow file demands, build the smallest
   thing in the existing style and note "not in the design" in `docs/implemented/`.
4. **Record what you used.** `docs/implemented/` names the board and the screen branch for every screen built, the
   way the patient app recorded Figma node IDs.

## Facts the audit established (30 Oct 2026)

- 0 raw colours, 0 raw px values, 0 off-scale font sizes left in the prototype; every value is a token.
- 44 px touch targets on phone everywhere; minimum text size 10; one `ActionBar` at the bottom of every form
  ("Unsaved changes" / "No changes yet"); one top `Toast` for feedback; skeletons for content, a spinner only inside
  a busy button; only cancelled visits are struck through; `Cancel appointment` is secondary destructive, `End
  appointment` is the primary.
- The primary fill stays `teal-50` with white labels (2.4:1, fails WCAG AA; a brand choice recorded in `AuTokens`).
  Words use the 70-steps (`text-brand` = `teal-70`, `text-error` = `red-70`, `text-info` = `blue-70`), tertiary text is
  `grey-550`.
- Open items: chart bars and donut segments are mouse-only (keyboard and screen-reader access to come); long patient
  names wrap in the phone agenda (truncate to one line).
- `AuRecommend` lists product changes not built in the prototype (week view default on desktop, month view, drag to
  create/move, command palette, autosave of visit notes, pull-to-refresh instead of refresh buttons). They are not
  part of the rewrite's scope unless the user adds them.
