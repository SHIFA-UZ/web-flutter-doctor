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
