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
