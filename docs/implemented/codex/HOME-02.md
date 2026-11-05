- **[P2] Initial schedule can use the wrong day.** [HomeViewModel.kt:38](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:38) reads the profile before it necessarily loads, so `PracticeClock(null)` uses UTC. Profile arrival does not trigger another read. Near practice midnight, Today can show yesterday’s appointments until another refresh. Wait for the practice timezone and reload when it changes.

- **[P2] Refresh leaves two metrics stale.** [HomeViewModel.kt:59](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:59) refreshes only the schedule. Notifications refresh on shell resume; tasks load once per shell. Neither returning to Home nor pulling refresh updates those counts, contrary to flow §6–7. Include their repositories in Home’s refresh.

- **[P2] Follow-up excludes draft tasks.** [MainScreen.kt:341](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:341) supplies `shell.activeTasks`, whose repository requests only `status=ACTIVE` (`BadgesRepository.kt:32`). HOME-02 requires active **and draft** tasks; doctors with drafts receive an understated count.

- **[P2] Overlapping refreshes can restore stale results.** [HomeViewModel.kt:53](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:53) forces the gate, bypassing its in-flight guard. Repeated Refresh taps or pulling during initial loading starts concurrent reads; each unconditionally updates state and clears the indicator. An older response can overwrite a newer schedule. Preserve the in-flight guard independently of cooldown.

- **[P2] Foregrounding Home does not refresh its schedule.** [HomeScreen.kt:104](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:104) runs only when entering composition. Resuming with Home still composed does not call `focus()`, and shell resume does not refresh the schedule. Returning after practice midnight therefore retains yesterday’s count. Trigger the gated refresh on resume.

- **[P2] Unloaded counts appear as confirmed zeroes.** [HomeScreen.kt:262](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:262) and line 273 always render shell integers. Their initial values are zero, including while requests are pending or after an initial failure. Flow §6 explicitly requires skeleton values while loading. Pass independent loading/error states for these metrics.

- **[P2] Profile shortcut has no accessible name.** [HomeScreen.kt:171](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:171) makes the avatar clickable without a label; `ShifaAvatar` also gives its images null descriptions. Screen readers encounter an unnamed button. Add the localized “Profile” description.

- **[P2] Metrics cannot reflow at large text sizes.** [HomeScreen.kt:241](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:241) always uses equal-width columns, while labels and subtitles are restricted to one line at lines 310 and 324. At 200% text size, labels truncate instead of cards stacking as required by flow §8. Use an adaptive layout that allows one card per row.

---
**Answers (round 1, all fixed):**
1. *The first read could use the UTC day:* fixed. Home reads nothing until the profile has brought the practice zone,
   and a changed zone reads again at once (a read in flight re-runs on the new zone). Tested.
2. *Refresh left two counts stale:* fixed. Showing Home, coming back to the app, pull-to-refresh and Refresh also read
   the notifications and follow-up counts through the shell (`refreshHomeCounts`).
3. *Follow-up left out drafts:* fixed. `BadgesRepository.followUps()` counts ACTIVE and DRAFT from one `GET /tasks`
   (the status filter takes one value). Checked on dev: 1, matching its one ACTIVE task.
4. *Overlapping refreshes:* fixed. `RefreshGate` skips any run while one is in flight, forced or not. Tested. The
   follow-up count read also cancels an older one, like the notifications count.
5. *Coming back didn't refresh:* fixed. `LifecycleResumeEffect` runs the gated read and the counts.
6. *Unloaded counts showed as 0:* fixed. Both counts are null until read and show the skeleton (`Home_loading`).
7. *The avatar had no name:* fixed. It is read out as "Profile".
8. *Metrics at large text:* fixed. At 150% text size and up the cards stack one per row (`Home_largeText`).

