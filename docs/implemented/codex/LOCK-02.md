- **[P1] Lock-screen and sheet taps bypass expiry checks.** [App.kt:157–161](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/App.kt:157) attaches tracking only to the main layout. `LockScreen.kt:120` uses a separate `Dialog` window; modal sheets likewise receive their own pointer events. If the token expires while the lock is displayed, entering the PIN can unlock the shell without ending the expired session. Attach activity tracking to those windows too.

- **[P2] Successful resume does not reset inactivity.** [SessionRepository.kt:113–120](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/session/SessionRepository.kt:113) only checks deadlines. Resuming on day 29 without tapping, then again on day 31, incorrectly signs out despite the intervening foreground activity. LOCK-02 and the flow’s appendix require recording activity after a successful resume check.

- **[P2] Foreground inactivity never expires.** [SessionRepository.kt:101–120](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/session/SessionRepository.kt:101) has no scheduled timeout, and `touch()` overwrites the timestamp without checking inactivity. With a still-valid token, leaving the app foregrounded for 30 days retains the session; the next tap erases the overdue deadline. The flow explicitly requires a running timer; resume checks cannot cover this path.

- **[P2] Movement and scrolling incorrectly count as activity.** [App.kt:160–161](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/App.kt:160) calls `touch()` for every pointer event, including movement, scrolling and hover. The flow’s activity appendix explicitly excludes movement/scroll events. Filter for pointer down/up so those events cannot extend the session.
---
**Answers (all fixed):**
1. *Separate windows not tracked:* tracking is now `Modifier.trackActivity()` (fed by a `LocalActivity` composition local from `App.kt`), carried by the root, the shared sheets (`ShifaDialogSheet`, `ShifaPickerSheet`) and the lock window. A tap on the lock with an expired token ends the session before the PIN can open the shell.
2. *Resume not counted:* `onForeground()` records activity after its checks pass. Tested (day 29, then day 31).
3. *No foreground timer / late tap erasing the deadline:* `touch()` enforces an overdue deadline before recording; the shell runs `enforce()` hourly while the app stays open. Tested.
4. *Moves and scrolls counted:* only `Press` and `Release` events count.
Local and covered by tests; no second round.
