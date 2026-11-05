# Home and analytics (M6)

Area: spec `02-home-ai-intelligence.md` §1–2, flows `docs/ux-flows/02-home-analytics/`, boards `DaHome` and
`HomeMobile`.

## HOME-01 · Home data layer

Built 2026-11-05. Spec 02 §1.4 and §1.10, ticket HOME-01, flow 02/02 §4.

- **Schedule** (`data/home/HomeRepository.kt`, `HomeSchedule`):
  - Today's visits that have a patient, by start time. Completed, cancelled and no-show visits are included: the
    "Today" metric counts them.
  - The **hero** is the visit in its slot now; else one still in progress past its slot; else the next one today;
    else the first one in the next 14 days (`heroDay` set then).
  - "Now" and "today" are on the practice clock, both taken from one instant, so a read that crosses midnight can't
    mix two dates.
  - Signed out (Home during sign-out), the schedule is empty, not an error (the ticket's Fix line).
- **Fixes** to Flutter's pick:
  - Cancelled and no-show visits are never the hero. Flutter made a cancelled visit in its slot "Now", 02/02 §8.
  - The rule is the same for today and the look-ahead.
- **One read path:** the backend has no range query (`GET /calendar?day` only, checked on dev's OpenAPI). Home reads
  through the calendar's day cache, shared with the Calendar tab, so switching tabs doesn't read again. The next days
  are read four at a time, in order, stopping at the first batch with a visit; Flutter read up to 14 days one by
  one. A look-ahead day that can't be read is skipped; only today failing is an error.
- **Refresh gate** (`RefreshGate`): coming back to the tab refreshes at most once a minute, and never while a refresh
  is running; pull-to-refresh always does. It is per Home, not a module global as in Flutter.
- **Tests:** `HomeRepositoryTest`:
  - the in-slot hero and the skipped cancelled one; the next one today;
  - the in-slot visit before an earlier in-progress one, before the next; signed out is empty and reads nothing;
  - the look-ahead with its day (no-show skipped, a failing day skipped, stopping at the hero's batch);
  - nothing in 14 days; today failing;
  - the gate (skipped while running and within a minute, forced always).

## HOME-02 · Home screen: header, glance, refresh

Built 2026-11-05. Spec 02 §1.2–1.4 and §1.10, ticket HOME-02 (Fix: one column, a visible search button), flow 02/01,
board `DaHome` (header, "Today at a glance").

- **Shell:** Home replaces the tab's placeholder (doctors; staff keep their Clinic home).
- **Header:**
  - The doctor's photo (opens Profile; read out as "Profile"), "Home" and their name.
  - Refresh, Search (the board's button; it opens the Patients tab until HOME-05's overlay), and the bell with a red
    dot when there are unread notifications. The bell's screen-reader label says how many.
- **Practice-time note** when the phone's zone differs from the practice's: "Times are in practice time
  (Asia/Tashkent). Your phone is set to Europe/Berlin."
- **Today at a glance:** Today (visits today, from HOME-01), Notifications (unread) and Follow-up (active and draft
  remote-care tasks, one `GET /tasks` counted, "1 follow-up task" / "2 follow-up tasks" as a new plural). The shell
  keeps the last two counts. Follow-up shows only with PREMIUM's remote-care tasks; Flutter's card bounced non-PREMIUM
  doctors back. Each card opens its tab. The cards keep one height. Each number is a skeleton until its count is
  read, never a 0 standing in for "not known"; "—" when its read failed with nothing known (a later failure keeps
  the last count). At 150% text size and up the cards stack, one per row.
- **Refresh:**
  - Nothing is read before the profile brings the practice zone (a read before would count the UTC day). A changed
    zone reads again at once.
  - Each time Home is shown or the app comes back: the schedule and both counts, through one gate: at most once a
    minute, never while a read is out. Pull-to-refresh and Refresh always read all three (unless a read is out; that
    one keeps the indicator).
  - Reads never overlap, even forced ones, so an older answer can't overwrite a newer one.
  - No profile (an offline start): "Could not load today's schedule" with Retry and "—" instead of the skeletons.
    Retry reads the profile again; with it come the schedule and, once the plan allows Tasks, the follow-up count.
  - What is shown stays during a refresh (Flutter dropped back to the skeleton). A failure shows "Could not load
    today's schedule" with Retry.
- **Not yet:** the next patient, quick actions and today's timeline (HOME-03, a skeleton stands in); attention and
  activity (HOME-04); analytics (ANALYTICS-*); the first-day checklist (flow 02/08).
- **Tests:** `HomeViewModelTest` (focus gated, refresh forced keeping content; a failure kept with the content; nothing
  read before the zone, a changed zone reads again; the counts follow the gate); `HomeRepositoryTest` (forced runs skip while one is running);
  screenshots `Home_loading`, `Home_loaded`, `Home_largeText`.
- **Emulator (demo):** the header, the practice-time note, Today 0 (no demo visits on odd days), 12 unread, Follow-up
  0. Demo mode is off again. Dev after the review fixes: Today 0, 46 unread, Follow-up 1 (dev has 1 ACTIVE task, no
  drafts), avatar read as "Profile". Offline cold start: "—" in both counts and "Could not load today's schedule";
  back online, Retry brought Today 0, 46 unread, Follow-up 1.

## HOME-03 · Next patient, quick actions, today's timeline

Built 2026-11-05. Spec 02 §1.4, ticket HOME-03, flow 02/02, board `DaHome` (hero, empty hero, "Quick Actions",
"Today's schedule").

- **Next patient card** (`HeroCard`):
  - "Next patient" today, "Next appointment" for a later day (the look-ahead, with its date).
  - Status pill: Now (in its slot or in progress), Waiting (requested), "In 12 min" within the hour, else Confirmed.
  - Photo, name (2 lines), time range, Online / In Clinic, the place (left out when it would only repeat "In Clinic"),
    "Age 36" once the patient's record is read (worked out from the birth date on the practice date at each draw, so
    a birthday turns it over), the reason.
  - Start Appointment / Start Video Call / Continue Appointment, full width; Open chart opens the patient in Home's
    stack. Start opens 15 minutes before a visit today (5 before a video call: the backend's join window), not for a
    requested one; while closed, "Opens 15 minutes before the visit (09:45)." or "Opens 5 minutes before the call
    (09:55)." The visit screen warns about a chronic disease itself, so Home doesn't ask twice; it now waits
    up to 3 s for the patient's record before its workspace opens, so the warning comes first (Codex HOME-03 P1).
  - Read out as "Next patient: <name>".
- **No hero:** "Done for today" when today had visits and nothing is booked for 14 days, else "No upcoming
  appointments"; both with New appointment (Calendar). Without a practice timezone: "Set your practice timezone to see
  today's schedule." with Add practice timezone (the profile's timezone sheet), Today "—", no timeline (flow 02/02 §6
  target).
- **Quick Actions:** New appointment (Calendar) and Video Call (off until the practice timezone is known): the first free slot not yet begun, today and the next
  29 days (`HomeRepository.firstFreeSlot`), opened as a booking set to video (`ShellRoute.Booking.video`; leaving it
  untouched asks nothing). None → "No slots available". Flutter sent the doctor to the Calendar to scan there.
- **Today's schedule:** visits and blocked times by start, a dot on a line, the status chip from the Calendar
  (`status`), "Now" in brand colours for the current visit. Finished ones (completed, no-show, ended) fold into "Show N
  earlier"; at most 8 still to come; "See all in Calendar". A row opens its Calendar panel (with Start), not just a
  highlight as in Flutter. Hidden when today has nothing.
- **Ticker:** every 30 s the pill, Start and the timeline move on without a read. When the hero isn't the one
  `pickHero` would choose now (it left its slot, or the next visit's slot began while it ran over), or the practice day
  turned, the schedule is read again through Home's gate (Flutter never ticked, flow §8).
- **Not built:**
  - The "Prescription" chip: it opens the remote-care task form, whose screen comes with the TASK tickets, and its
    label and destination disagree (flow 02/02 §13). It waits for both.
  - The board's "Medical record" flags (allergy, chronic disease, documents, briefing) on the hero: not in the spec.
- **Design notes:** the date of a later hero is `06.11.2026` (the app's date format), not the board's "Thu, 6 Nov".
- **Tests:** `HomeRulesTest` (Start window and pill; the 5-minute video window; in progress, requested, another day; the timeline split; when the
  schedule is stale), `HomeRepositoryTest` (the free-slot scan skipping failed days, null when none, failure when today
  fails; blocks and the day come along). Screenshots `Home_loaded`, `Home_heroLater`, `Home_done`, `Home_noZone`.
- **Emulator:**
  - Dev: "No upcoming appointments" (dev has only free slots for 30 days, checked against `/calendar`). Video Call
    opened the 09:00 slot as a video booking (07:23 in Tashkent); backed out without booking.
  - Demo: "Next appointment" Dilnoza Rahimova on 06.11 at 10:00, Online, Age 36, Start Video Call off with "Opens …
    (06.11.2026, 09:45)"; the Lunch row opened its block panel; Open chart opened her record. Demo mode is off again.

## HOME-05 · Patient search

Built 2026-11-05. Spec 02 §1.9, ticket HOME-05, flow 02/07, board `DaHome` (search page).

- **Entry:** the header's Search button (Flutter had only Cmd/Ctrl+K, unreachable on phones) pushes `ShellRoute.Search`
  in Home's stack: a full page (the flow's target), Back and the field with the keyboard up, the tab bar hidden.
- **List:** from the shared directory (`PatientsRepository`, read when stale like the Patients tab; nothing extra on
  the server). Blank: "Patients" and the first 8 by name (Flutter: server order). Typed: every match, counted ("2
  patients", read out as it changes), by name, id, display id or phone digits (3+ digits, no letters). The matcher
  moved from the Patients tab to `Patient.matches`, so both search the same way. Rows are the Patients tab's
  `PatientRow` (age, gender, phone, status).
- **Open:** a row (or the keyboard's Search with a single match; otherwise Search just hides the keyboard) closes the
  search and opens the patient in the Patients tab over its directory, so Back leads to the directory (flow §10;
  Flutter switched to the Patients tab without opening anyone). Each open starts with an empty query.
- **States:** skeleton while the directory is read the first time; "Could not load patients" with Retry; "No patients
  found" with "Check the spelling or search by phone number."; no patients at all: "No patients yet" with Add patient
  (the Patients tab).
- **Not built:** Latin/Cyrillic Uzbek normalisation and an "Add patient" button on no match (flow §8, §9 targets);
  highlighting the matched part of the name.
- **Tests:** `PatientSearchTest` (first 8 by name, name / display id / phone digits, letters never match phones);
  `ShellViewModelTest` (a result opens in the Patients tab, Back to the directory);
  `PatientsViewModelTest` still passes on the shared matcher. Screenshots `Home_search`, `Home_searchEmpty`.
- **Emulator (dev):** Search opened with the keyboard up and the 5 seed patients; "0103" found Sardor Ismoilov by phone;
  the keyboard's Search opened his record in the Patients tab; Back showed the directory. Reopened from Home: empty
  query; "zz" + Search: "No patients found", keyboard put away.

## ANALYTICS-01 · Analytics data

Built 2026-11-05. Spec 02 §2.1–2.3, ticket ANALYTICS-01, 15 §2.3.

- **`AnalyticsRepository`:** `GET /doctor/analytics/overview`, `appointments-trend?days`, `consultation-types`,
  `engagement`, `sms-usage?from&to`; five independent reads (one failing never hides the others), no cache. One
  refresh entry point, `all(range, withSms)`, reads them all in parallel into an `AnalyticsSnapshot` (one `Result` per
  block): Home and Reports use it on open and on refresh (Flutter never refreshed the trend, types or engagement).
- **Models** (`AnalyticsModels.kt`): every count defaults to 0 (missing or null), as Flutter's; a trend day with an
  unreadable date is skipped, not the whole trend (`rows`). `SmsUsage` keeps minor units and the currency for the
  money formatter.
- **`AnalyticsRange`:** inclusive days, never reversed; the default is the last 7 days ending on the **practice** date
  (Flutter: the phone's date). `trendDays` clamps to 1–90: the backend answers the last N days ending today, so a past
  range can't be honoured until BE-19's `from/to`; SMS takes `from/to` exactly.
- **Dev (2026-11-05, seed doctor):** all five checked against `/v3/api-docs` and a real GET: overview
  `{appointmentsToday 0, completedToday 0, cancelledToday 1, newPatientsToday 0}`, a 7-point trend, types
  `{video 4, inPerson 11}`, engagement `{activePatients 5, documentsReceived 1}`, SMS not allowed, 0 sent. The overview's
  cancelled visit doesn't show on Home's "Today" (the calendar leaves cancelled ones out): server numbers, labelled
  "Today" by ANALYTICS-02.
- **Demo:** today's counts and the trend come from the demo calendar (non-cancelled visits per day, the last N days);
  types 8/17, engagement 41/12, SMS 12 sent, 6 000 UZS, allowed. `DemoBackendTest` covers them.
- **Tests:** `AnalyticsRepositoryTest` (zeros for missing or null, a bad trend day skipped, the range maths, `days` 90
  for a year, SMS dates as given, one refresh reading every block).

## ANALYTICS-02 · Clinic performance card

Built 2026-11-05. Spec 02 §1.7–1.8, §2.4, ticket ANALYTICS-02, flows 02/05 and 02/06, board `DaHome` (analytics
card, range dialog).

- **Where:** last on Home (`AnalyticsSection`, `ui/analytics/`), shown once the profile is in (its practice date
  sets the default range). Its own ViewModel; each block is read and shown as it answers (a slow one never holds the
  others back). Home's pull-to-refresh and Refresh refresh all of it; each successful read of today's schedule (even
  an unchanged one) reads today's KPIs again and brings the practice date: on a new day the picker moves, and so does
  a range the doctor never changed. Reports will reuse it (REPORT-01).
- **Toolbar** (stacked full width): the range ("30 Oct – 5 Nov, 2026" with the app's month names; the year on both
  ends across a new year; one date for one day), read out as "Date range, …"; Export with its spinner.
- **Range dialog:** the system range picker (`DateRangePicker`) with the board's presets (Last 7 / 30 / 90 days) and
  hint in its title, bounds a year back to 30 days ahead of the **practice** date (Flutter: the phone's), Save with
  both ends picked. A new range reads only the trend (the KPIs are today's); the range isn't kept across launches.
- **KPIs:** today's four as the board's tiles (tinted icon, value, label), one per row: Appointments (opens Calendar),
  Completed, Cancelled / No-show, New patients (opens Patients). Skeleton values while loading.
- **PREMIUM** (`AdvancedAnalytics`): "Patient Engagement" (active patients, opens Patients; documents received).
  BASIC/PRO: the board's teaser "See trends, visit types and engagement" with See plans (Subscription). Unknown plan:
  neither (no flash).
- **Errors:** each block on its own: "Could not load" with Retry (Flutter printed "Error").
- **Export:** CSV in the documented layout (`dashboardCsv`, plain ISO dates in the period line), built from what is
  loaded (read again only when a block is missing); a failed block fails the export with "Could not export dashboard
  data". The file goes to the app cache (`cache/share/`, replaced each time) and the share sheet
  (`utils/FileShare`: Android `FileProvider`, iOS `UIActivityViewController`); no "check your downloads" message.
- **Not built here:** the trend and donut (ANALYTICS-03); SMS usage (Reports only, REPORT-01); one range shared with
  Reports (comes with REPORT-01).
- **Tests:** `AnalyticsViewModelTest` (open reads every block, a range reads only the trend, today's refresh reads only
  the KPIs; export from loaded data without reading, a failed block fails it with the notice; the CSV layout; a slow
  block doesn't hold the others; an export's read never replaces a new range's trend). Screenshots `Analytics_loaded`,
  `Analytics_loading`, `Analytics_teaser`.
- **Emulator (dev, PREMIUM seed doctor):** KPIs 0 / 0 / 1 / 0 and engagement 5 / 1, as dev answers; Export opened the
  share sheet with `shifa_dashboard_2026-10-30_2026-11-05.csv` (dev's numbers, documented layout, read from the app
  cache); the range dialog's "Last 30 days" + Save made it "7 Oct – 5 Nov, 2026". iOS: compiled and linked, not run.

## ANALYTICS-03 · Trend and visit types

Built 2026-11-05. Spec 02 §2.4, ticket ANALYTICS-03, flow 02/05 §3–§8, board `DaHome` (trend and donut blocks).

- **Where:** PREMIUM only (`AdvancedAnalytics`), between the KPIs and engagement in "Clinic performance"; BASIC/PRO
  keep ANALYTICS-02's teaser. Drawn with Compose `Canvas` (no chart library, CLAUDE.md).
- **Trend** (`TrendBlock`): a smooth line (control points half way, the board's path) in brand teal, white-ringed
  dots, grid lines with their counts (top 20 % over the highest day, 10 when all are 0; steps of 1 up to 5, else a
  fifth), "MM/DD" for about 7 days whatever the range (every n-th, the last always; kept inside the chart, and placed
  by measured width so none touch at large text sizes). The title names the days drawn (the requested range while
  loading, empty or failed): "Appointments (Last 7 Days)" or "Appointments (7 Oct – 5 Nov, 2026)" (Flutter always said
  "Last 7 Days"). A tap on a day shows "2 Nov · 3 visits" (tap again to hide). All zero or empty: "No data yet".
  Skeleton while the trend for a new range is read.
- **Visit types** (`TypesBlock`): a 160 ring, in-person in brand over video in brand at 25 %, the in-person share in
  the middle with the video share under it, and a legend with counts beside it (a `FlowRow`, so it goes below at large
  text sizes; from 130 % text the shares move from the ring's centre into the legend). Shares always add up to 100 (Flutter could show 101 %). Total 0: "No data yet".
- **Accessibility:** each chart reads out a summary: "Appointments per day, 30 Oct – 5 Nov, 2026. Highest 4." and
  "In-person 11, video 4".
- **Not built:** a draw animation (flow §8 asks for none under reduce-motion; none at all keeps it simple); donut
  segment taps (the counts are already shown).
- **Tests:** `AnalyticsChartsTest` (axis top and step, about seven labels for 1/7/30/90 points, shares adding up to
  100). Screenshots `Analytics_loaded` (7 days, 68/32 ring), `Analytics_charts` (30 days, no visit types),
  `Analytics_largeText` (14 days at 200 %: dates spaced, shares in the legend).
- **Emulator (dev):** trend 4, 1, 1, 3, 0, 0, 1 for 30 Oct – 5 Nov as dev answers; ring 73 % / 27 % (11 in person,
  4 video); tapping 2 Nov showed "2 Nov · 3 visits".

