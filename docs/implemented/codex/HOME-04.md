- **[P2] Duplicate task loads violate HOME-04’s Fix line.** [HomeScreen.kt:195](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeScreen.kt:195) refreshes immediately and again when the initial schedule read increments `reads`. Pull-to-refresh also calls attention directly at line 217 before the successful schedule read triggers another refresh. Each invocation starts another task request; cancellation does not prevent an already-sent request.

- **[P2] Older unread notifications disappear from Attention.** [HomeAttentionViewModel.kt:82](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeAttentionViewModel.kt:82) fetches only page 0 (20 notifications), then filters unread locally. If those 20 are read but older unread notifications exist, Home incorrectly shows “All caught up!” Fetch enough pages to find four unread items or exhaust the list.

- **[P2] Refresh can resurrect successfully read notifications.** [HomeAttentionViewModel.kt:87](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeAttentionViewModel.kt:87) replaces the list without reconciling local read changes. A refresh started before `markRead` can return afterward with `read=false`, restoring the dismissed tile despite a successful PUT. The inbox already handles this race with `readHere` reconciliation.

- **[P2] Notification failure hides successfully loaded tasks.** [HomeAttention.kt:143](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/home/HomeAttention.kt:143) renders only the error whenever the first notification request fails, even when `state.tasks` contains active tasks. This carries forward the partial-data defect explicitly prohibited by the attention flow §6/§12; show available tasks alongside the retry error.

- **[P2] Home’s fallback notification target does nothing.** [NotificationOpener.kt:53](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationOpener.kt:53) returns `null` for `NotificationTarget.Inbox`. That was appropriate inside Notifications, but Home now shares this opener: tapping a notification without usable target IDs marks it read and removes it without opening anything. The attention flow §5 requires opening Notifications for this case.

---
**Answers (round 1, all fixed; second round run):**
1. *Task reads went out twice:* fixed. The ViewModel reads through a `RefreshGate` (one at a time, at most once a minute
   unless forced): Home's overlapping triggers (opening, the schedule's read) are gated, a pull and Retry force it,
   and the plan arriving or going forces one read so tasks don't wait a minute. Tested (one request for two calls).
2. *Older unread disappeared:* fixed. It reads pages newest first until four unread are found or the list ends (five
   pages at most); the feed still shows the eight newest. Tested.
3. *A refresh could bring a read row back:* fixed with the inbox's reconciliation: rows read here stay read in any
   answer that comes after. Tested.
4. *A notification failure hid the tasks:* fixed. The card shows "Couldn't load notifications" with Retry and the
   tasks that did load.
5. *A row with nothing to open did nothing:* fixed. On Home it opens the inbox (`NotificationGo.Inbox`, flow §5); the
   inbox itself never gets it. Tested.
