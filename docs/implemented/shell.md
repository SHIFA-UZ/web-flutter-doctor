# Shell (M2)

Built so far on `doc-app2.0`. Flows `docs/ux-flows/01-launch-auth-shell/16`–`20`; spec `01` §6.

## SHELL-01 · Doctor shell

Built 2026-11-04. Flows `01/16-main-shell-doctor.md`, `01/17-more-sheet.md`; board `DaApp.dc.html` (`phoneTabs`,
`moreOpen`, `moreRows`).

- **Navigation** (`ui/navigation/ShellViewModel.kt`, no library): `ShellTab` (Home, Calendar, Patients, Chat in the
  bar; Clinic, Tasks, Reports, Notifications, Profile behind More; `key` = the backend's `defaultStartTab` value) and a
  `ShellRoute` stack per tab (`Root(tab)` plus pushed screens: Language, Intelligence, Debug so far; feature tickets
  add theirs).
  - Switching tabs keeps every tab's stack (flow §5 target; Flutter popped them); tapping the current tab goes back to
    its root.
  - Pushed screens hide the bar (flow §3 target).
  - System Back: closes More; pops a pushed screen; from another tab's root goes Home; on Home's root lets the system
    close the app (flow §7 target; Flutter didn't handle Back).
  - Start tab: after `GET /doctors/me` (PROF-01), once, unless the doctor already switched tabs or opened a screen;
    Tasks below PREMIUM and Clinic without a clinic (`GET /me/clinics`, new `ClinicsRepository`) fall back to Home
    (flow §8 target). Tasks or Clinic picked before their gate is known are remembered and opened once allowed.
  - Every route keeps its UI state (scroll, typed text) while another tab or screen is shown (a saveable state
    holder keyed by tab, depth and route).
- **Screen** (`ui/navigation/MainScreen.kt`): the current route over the canvas, the shell note (`ShellNoticeBanner`)
  on top, the kit's floating `ShifaNavBar` at the bottom (More is highlighted on the five More tabs).
  - **More sheet** (DaApp): Shifa Intelligence on its teal disc ("Talk with AI", or "Needs the Pro plan" with a
    "Pro" badge, not tappable, below PRO); Clinic (members only); Tasks (below PREMIUM: a "Premium" badge, not
    tappable; the board shows it, the flow hid it); Reports; Notifications; Profile; Language (current code); Sign
    Out (the AUTH-09 question). Rows with a plan wait until the plan is known (D-03). The current-location row waits
    for the calendar (M3).
  - **Placeholders:** every tab's root shows its name and icon until its ticket builds it. Profile holds the app-lock
    section (until SET-03) and, in debug builds, "Debug menu" (pushed, with its own back).
- **Checked on the emulator** (dev, seed doctor, PREMIUM with a clinic): shell after the splash, More with all rows,
  Profile from More (More highlighted), Back → Home.
- **Not built here:** the More sheet's current-location row (its source is today's calendar and the saved
  location: CAL/LOC in M3, computed at slot boundaries instead of Flutter's 60 s polling); badges and polling (SHELL-03), the staff shell (SHELL-02), full feature gating (SHELL-04), the
  Intelligence surface and resume refresh (SHELL-05), deep links (CAL-9, PUSH), the notification pre-prompt (PUSH).
- **Demo data:** `GET /me/clinics` answers one demo clinic.
- **Tests:** `ShellViewModelTest` (6: start tab, fallbacks, an early tap wins, an early push wins, a gated tab picked
  early opens once allowed, stacks/re-tap/Back), `ShellScreenshotTest`
  (4: Home, a More tab, More for PREMIUM with a clinic, More for BASIC).
