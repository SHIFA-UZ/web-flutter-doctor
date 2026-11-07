- **[P2] Out-of-slot `IN_PROGRESS` visits override the current visit.** [HomeRepository.kt:43](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/home/HomeRepository.kt:43) treats status alone as eligibility for the first pass. An unfinished 08:00–08:30 visit therefore beats a 09:00–09:30 visit at 09:15. Spec §1.4 and flow §4 require the in-slot visit first; status affects its badge, not that priority.

- **[P2] Signed-out calls return an error instead of an empty schedule.** [HomeRepository.kt:61](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/home/HomeRepository.kt:61) unconditionally reads the protected calendar endpoint and propagates its failure. With no session and an empty cache, the resulting 401 becomes `Result.failure`, violating HOME-01’s explicit Fix line. Check authentication before reading and return an empty successful schedule when unauthenticated.

- **[P2] Crossing midnight during the fetch mixes two practice dates.** [HomeRepository.kt:60–64](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/home/HomeRepository.kt:60) captures `today` before the suspended request but `now` afterward. A request starting at 23:59:59 and finishing after midnight returns yesterday’s count; a visit starting on the new day is treated as a look-ahead hero with `heroNow=false`, even when already in-slot. Revalidate the practice date after fetching and reload when it changes.

- **[P2] The refresh gate permits overlapping refreshes.** [HomeRepository.kt:105–109](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/home/HomeRepository.kt:105) tracks only elapsed time, with no in-flight state or completion hook. If a look-ahead spans multiple slow batches and exceeds 60 seconds, another tab-focus refresh is admitted while it is running. Spec §1.10 requires tab-focus refresh to skip while in flight as well as during cooldown.

---
**Answers (round 1, all fixed and tested; small, so no second round):**
1. *An earlier in-progress visit beat the in-slot one:* fixed. The visit in its slot comes first, then one still in
   progress past its slot, then the next. Tested.
2. *Signed out was an error:* fixed. With no session the schedule is empty and nothing is read (the ticket's Fix
   line). Tested.
3. *A read across midnight mixed two dates:* fixed. "Today" comes from the same instant as "now", taken before the
   read.
4. *The gate let refreshes overlap:* fixed. `RefreshGate.run` also skips while one is running; forced runs always.
   Tested.
