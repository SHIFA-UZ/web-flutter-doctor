- **[P2] Failed counts remain loading forever.** [ShellViewModel.kt:313](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:313) and line 331 handle only success. An initial notifications/tasks failure leaves the metric as a permanent skeleton; subsequent failures silently retain stale counts. Round-one fix 6 needs independent failure states and the error/Retry UI required by flow §6.

- **[P2] Profile failure now prevents Home from leaving loading.** [ShellViewModel.kt:323](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:323) discards the profile retry result. When `/doctors/me` fails, `practiceZone()` never runs, so Today and the large skeleton remain indefinitely without an error. Propagate the prerequisite failure to Home while retaining the timezone guard.

- **[P2] Count refreshes bypass the cooldown and in-flight guard.** [HomeScreen.kt:118](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:118) calls `refreshCounts()` even when `focus()` skips its refresh. Rapid tab switching repeatedly cancels and restarts both count requests, contrary to §1.10 and flow §7. Apply the shared refresh gate to the counts too.

- **[P2] A skipped refresh clears another refresh’s indicator.** [HomeViewModel.kt:64](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeViewModel.kt:64) sets `refreshing` before entering the gate and clears it unconditionally afterward. Tap Refresh twice during a slow request: the second run is rejected immediately and hides the indicator while the first remains active. Only the admitted run should own the indicator; count requests also need to participate in completion.

- **[P2] Successful profile recovery can leave Follow-up permanently unread.** [ShellViewModel.kt:324](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:324) attempts the task read before the asynchronous profile retry resolves, so `canTasks` is still false and line 328 skips it. When the recovered profile enables Tasks, `onPlan()` only updates the gate (lines 233–240). The newly visible card stays a skeleton until another refresh. Trigger its initial read when task access becomes available.

---
**Answers (round 2, all fixed; checked on the emulator):**
1. *Failed counts stayed skeletons:* fixed. A failed read with nothing known shows "—". A later failure keeps the last
   count (flow 02/02 §6: "Follow-up shows the last known count … with no error"), so the counts have no banner of
   their own; Home's Retry reads them again.
2. *A profile failure kept Home loading:* fixed. The shell records it (`profileFailed`, only while no profile is
   known); Home shows "Could not load today's schedule" with Retry and "—" for Today. Retry reads the profile again.
   Checked offline on a cold start, then online with Retry.
3. *The counts bypassed the gate:* fixed. `focus(counts)` and `refresh(counts)` run them inside the same gate as the
   schedule: at most once a minute on focus, never while a read is out. Tested.
4. *A skipped refresh cleared the indicator:* fixed. Only the admitted run sets and clears `refreshing`. The counts
   are fire-and-forget reads in the shell (each cancels its older one), so the indicator follows the schedule only.
5. *Recovered profile left Follow-up unread:* fixed. `onPlan` reads the follow-up count when the plan gains Tasks.
   Checked: after Retry, Follow-up showed 1.

