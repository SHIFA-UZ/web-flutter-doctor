- **[P2] Video Start opens ten minutes too early.** [HomeRules.kt:36](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/home/HomeRules.kt:36) applies the 15-minute window to video appointments too. At ten minutes before a video visit, “Start Video Call” is enabled although the backend rejects joining until five minutes before. This contradicts `17-master-plan.md:219`. Select the window by visit type and update the helper text accordingly.

- **[P2] A timezone change during the age fetch loses its schedule refresh.** [HomeViewModel.kt:115](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:115) awaits `readAge` while holding the refresh gate, after the timezone consistency check at line 100. If the doctor changes timezone during that request, `practiceZone` updates `zone` but its refresh is skipped because the gate is running. Nothing rechecks afterward. When both zones have the same date, the ticker need not detect staleness, leaving the previous zone’s schedule under the new zone’s labels. Fetch age outside the schedule gate or recheck the zone afterward.

- **[P3] Birthday rollover leaves the hero’s age stale.** [HomeViewModel.kt:124](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:124) caches age solely by patient ID. If tomorrow’s hero remains the same patient across midnight on their birthday, the schedule refresh preserves the old age and `readAge` returns immediately; even manual refresh cannot correct it. Recalculate from cached birth date on the current practice date, or include that date in the cache key.

---
**Answers (round 2, all fixed and tested):**
1. *Video Start opened 10 minutes early:* fixed. A video call opens 5 minutes before (the backend's join window,
   `START_EARLY_VIDEO`), with "Opens 5 minutes before the call (09:55)."; in-person stays at 15. Tested, and
   `Home_heroLater` shows it.
2. *A timezone change during the age read was lost:* fixed. The patient read runs on its own, outside the schedule
   gate, so a zone change's forced read is never skipped because of it.
3. *The age didn't turn over on a birthday:* fixed. Home keeps the birth date and works out the age on the practice
   date at each draw (the ticker runs every 30 s).

