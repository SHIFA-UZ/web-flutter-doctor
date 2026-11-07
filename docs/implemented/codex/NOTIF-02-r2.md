- **[P2] A failed count refresh can still disable “Mark all” incorrectly.** [MainScreen.kt:413](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:413) treats `notificationsKnown` as sufficient, while [ShellViewModel.kt:426](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:426) preserves it on failure. If the previous count was zero, a subsequent count request fails, and unread rows exist only beyond the loaded page, the action remains disabled. Pass an unknown count when `notificationsFailed` is true.

- **[P2] An in-flight page can undo a successful mark-all locally.** [NotificationsViewModel.kt:193](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationsViewModel.kt:193) marks only currently loaded rows and does not invalidate pending page requests. A refresh or next-page request started before mark-all can subsequently replace/append unread snapshots at lines 110 or 142. The inbox then shows unread cards despite the successful write and zero badge. Reconcile pending page results with successful read mutations, or invalidate and reload them.

---
**Answers (round 2, both fixed; small, so no third round):**
1. *A failed count could keep Mark all off:* fixed. A count whose last read failed reaches the inbox as unknown, so
   Mark all stays on.
2. *A page in flight could undo a mark-all:* fixed. A successful mark-all starts a new generation (pages asked for
   before it are dropped, loading stops); a row read here stays read in any page that answers later. Tested.
