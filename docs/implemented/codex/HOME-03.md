- **[P1] Start does not await the chronic-disease check.** [MainScreen.kt:363](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:363) immediately pushes the visit. The destination enables its workspace before fetching the patient ([VisitViewModel.kt:285](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitViewModel.kt:285)); the warning is set only after patient, history and forms finish loading. A slow request therefore lets the doctor begin before acknowledgment, violating HOME-03’s Fix line. Gate entry or visit actions until the check completes.

- **[P2] An overdue in-progress hero never yields to the next current visit.** [HomeRules.kt:82](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/home/HomeRules.kt:82) always considers an `IN_PROGRESS` hero fresh until midnight. If its slot ends and another appointment begins while Home stays open, the ticker never reselects the hero, despite `pickHero` prioritizing an in-slot appointment. Recompute selection when another appointment starts.

- **[P2] Video booking can scan using an unknown practice timezone.** [HomeViewModel.kt:134](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:134) checks only `findingSlot`; the chip remains enabled during profile loading and when the timezone is missing. `clockFor(null)` falls back to UTC, so near midnight the scan can start on the wrong practice day and skip the earliest available slot. Require a loaded, valid practice timezone before scanning.

---
**Answers (round 1, all fixed and tested; the visit-loading change gets a second round):**
1. *Start didn't wait for the chronic-disease check:* fixed at the root, in `VisitViewModel.load()`, so Start from
   Calendar is covered too. The visit stays on its loading skeleton until the patient's record is read (at most 3 s,
   flow 02/02 §8), and the warning comes in the same update that opens the workspace. Only a record slower than 3 s
   warns later, as before. `VisitViewModelTest` (18) and the visit screenshots (20) still pass.
2. *An overdue in-progress hero never yielded:* fixed. `stale` now asks whether `pickHero` would choose another visit
   now, so a visit whose slot begins while the hero runs over takes over at the next tick (read gated). Tested.
3. *Video Call could scan on a UTC day:* fixed. The chip is off until the profile is in with a practice timezone, and
   `findVideoSlot` refuses without one (`Home_noZone` shows it off).

