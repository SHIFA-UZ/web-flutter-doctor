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

## SHELL-02 · Clinic-staff shell

Built 2026-11-04. Flow `01/18-main-shell-clinic-staff.md`; board `DaApp.dc.html` (`phoneTabs` for staff). D-02:
staff use the mobile app (Flutter showed a "web only" block screen).

- Same shell, by role: a `CLINIC_STAFF` token opens `MainScreen(staff = true)`. Four tabs: Chat, Clinic,
  Notifications, Profile, whose slot shows the person's photo (`GET /me/profile`; brand fallback without one; the
  kit's `NavBarItem` gained `avatar`/`avatarUrl`). No More sheet.
- Starts on Clinic, which is also "home" for Back (nested pop → Clinic → exit). The saved start tab is never applied
  and `/doctors/me` is never called (it answers 403 for staff: flow §4). Doctor-only tabs can't be selected.
- Language and Sign Out sit in the staff Profile (placeholder until PROF-02's staff variant) since there is no More.
- **Not built here:** badges (SHELL-03), the Clinic workspace (area 09), deep-link mapping for staff (flow §8, PUSH).
- **Tests:** `ShellViewModelTest` (+1), `ShellScreenshotTest` (+1: the staff bar). The staff Profile placeholder has
  no screenshot: it hosts the real app-lock section, whose secure store Robolectric can't open.

## SHELL-03 · Badges

Built 2026-11-04. Flow `01/16` §4, §8; spec `01` §6.3; board `DaApp.dc.html` (bar `count`, More `hasCount`).

- **Data** (`data/badges/BadgesRepository.kt`): `GET /messages/unread-count` and `GET /notifications/unread/count`
  (both `{count}` on dev, 2026-11-04); active tasks = `GET /tasks?status=ACTIVE` counted (there is no count endpoint).
- **When** (`ShellViewModel`): chat polled every 10 s **only while the app is in front** (`onForeground` /
  `onBackground` from the screen's lifecycle; Flutter polled always); notifications refreshed on every `ON_RESUME`
  (also a pause that never stopped the app), a newer refresh cancelling an older one still out (on a foreground push
  with PUSH-03); active tasks once per shell after the start tab, PREMIUM
  only. A failed refresh keeps the last value (flow §6: badges freeze offline).
- **Where:** bar badge on Chat (doctors) and on Chat and Notifications (staff), teal; the More sheet shows
  Notifications and Tasks counts in DaApp's teal pill. Past 99: "99+" (flow §8). Screen readers hear "Chat, 3
  unread"; the badge itself is hidden from them.
- **Demo data:** fixed counts (chat 3, notifications 12), no tasks.
- **Tests:** `ShellViewModelTest` (+1: polling only in front on a test clock, notifications on return, tasks once),
  screenshots re-recorded with counts (Home, More PREMIUM, staff).

## SHELL-04 · Plan gating (also SUB-01's source)

Built 2026-11-04. Spec `01` §5.3, `08` §5; flow `01/16` §6–7; D-03 (the user's call).

- **Gate** (`data/subscription/FeatureGate.kt`): `DoctorTier` and `DoctorFeature` mirror the backend's
  `SubscriptionTier` / `SubscriptionFeature` (`auth/domain/SubscriptionTier.kt`: BASIC calendar, documents, chat,
  patients, basic analytics; PRO + video, AI notes, speech-to-text, briefing, Ask Shifa AI; PREMIUM + remote-care
  tasks, advanced analytics, differential diagnosis). `allows(feature)` reads `subscription.features` whenever it is
  there, also when empty (nothing included); only an old backend that leaves the list out falls back to the tier. **Unknown allows nothing** (Flutter treated
  unknown as PREMIUM: fail open). The backend remains the source of truth for every gated call.
- **Shell:** `ShellUiState.gate` replaces SHELL-01's ad-hoc plan: Tasks (`REMOTE_CARE_TASKS`) and Shifa Intelligence
  (`ASK_SHIFA_AI`) rows, the start-tab clamp and the tasks count use it; the Intelligence row and the rows with a plan
  badge appear only once the plan is known. The shell watches the shared profile: a later read with a new plan (resume refresh, a Profile save)
  updates the gates, and a doctor on Tasks who lost it goes Home with "Tasks are not available on your plan." (flow
  §7 target; Flutter bounced silently).
- **Tests:** `FeatureGateTest` (2: codes, unknown/list/tier), `ShellViewModelTest` (+1: losing Tasks; the mock now
  lists features per tier like the backend).

## SHELL-05 · Intelligence mode and resume refresh

Built 2026-11-04. Spec `01` §5.4, §6.6; flow `01/16` §2, §7.

- **Intelligence mode:** More → Shifa Intelligence (only with `ASK_SHIFA_AI`) sets `intelligence`, which replaces the
  content area while the bar stays (Flutter's `workspaceMode`); the tab underneath keeps its stack and state. Any tab
  tap leaves it (tapping the tab underneath just closes it), so does system Back; losing the feature with a new plan
  closes it too. The surface itself is a placeholder until area 03 (AI-01…); there it will refresh appointment data
  on leaving (the AI may have booked).
- **Resume** (`onResume`, `ON_RESUME`): notifications count, clinic membership (`/me/clinics`) and the profile
  (`/doctors/me`, so a changed plan reaches the gates through SHELL-04's watcher) are read again. A newer resume
  cancels an older refresh still out, and `ProfileRepository` drops any read that started before a newer read or
  save answered, so a late old answer can't restore a lost clinic or a downgraded plan. Feature screens
  refresh their own data; the 60 s location ticker is not carried over (M3).
- **Tests:** `ShellViewModelTest` (+2: the mode with gate, tab tap and Back; resume re-reads), `ShellScreenshotTest`
  (+1: Intelligence over Calendar).

