- **[P1] Clinic changes cannot supersede an in-flight roster read.** [CalendarScreen.kt:259–270](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:259) uses sequential `collect`, suspends in `members()`, then publishes the captured clinic without checking whether it is still selected. Switching clinics while that request runs leaves the old chips actionable until it finishes, and its response can launch obsolete colleague reads. Use cancellation plus a clinic/profile generation check before publishing or loading colleagues.

- **[P2] Out-of-order day failures can replace Retry with an indefinite skeleton.** [CalendarScreen.kt:292–296](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:292) stores only one failed date per doctor. Start day A, switch to B, let B fail, then let A fail: A overwrites B’s failure. [StaffCalendars.kt:161–168](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/StaffCalendars.kt:161) consequently renders B as loading although no request remains. Track failures by day and reject obsolete request completions.

- **[P2] A late practice timezone can strand a colleague’s initial load.** The new colleague read at [CalendarScreen.kt:295](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:295) shares the repository cleared by `followOwn()` at lines 325–326. If the profile ID is already available and its timezone arrives while that colleague request runs, `clear()` advances the cache generation but retains `inFlight`; the replacement load joins the old request. Its successful response is discarded by the generation check, leaving no cached day or failure and therefore a permanent skeleton. Invalidate pending requests when clearing, or isolate colleague cache/loading state as CAL-10’s Fix requires.

---

- **P1 a clinic change can't supersede a roster read:** fixed. `collectLatest`: another clinic or doctor cancels the
  read still running, so an old roster is never published and starts no colleague reads.
- **P2 out-of-order day failures:** fixed. Failures are kept per (doctor, day) (`Set<DayKey>`): a late failure of
  another day never hides this one's Retry.
- **P2 a late practice zone strands a colleague's load:** fixed at the root: `CalendarRepository.clear()` forgets the
  reads in flight, so a read after it asks again and caches instead of joining one whose answer is discarded (this hit
  the doctor's own day too). Test.
