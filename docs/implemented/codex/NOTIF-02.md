- **[P2] Refresh can skip entire pages.** [NotificationsViewModel.kt:125](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationsViewModel.kt:125): `refresh()` neither cancels nor invalidates an in-flight `loadMore()`. If page 2 completes after refresh replaces the list with page 0, it appends page 2 and advances the cursor to 2; page 1 disappears and subsequent pagination skips it. Serialize requests or discard responses from earlier refresh generations.

- **[P2] Opening and refreshing the inbox leave the unread count stale.** [NotificationsScreen.kt:149](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationsScreen.kt:149): refresh only calls the inbox ViewModel, which fetches page 0. Neither this callback nor initial loading refreshes the shell count; `NotificationReads` fires only after writes. New notifications therefore appear without updating the title/badge, contrary to flow §§2 and 7.

- **[P2] “Mark all as read” is disabled while unread notifications remain on unloaded pages.** [NotificationsScreen.kt:192](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationsScreen.kt:192) and [NotificationsViewModel.kt:175](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationsViewModel.kt:175): both gates use `anyUnread`, calculated exclusively from loaded rows. When the newest page is read but older notifications are unread, the global action cannot run until the user scrolls far enough. Use the global unread count, accounting for an unknown count.

- **[P2] “Open Calendar” can reopen an unrelated detail screen instead of today’s calendar.** [MainScreen.kt:423](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:423): the callback only selects the Calendar tab. `ShellViewModel.select()` preserves another tab’s existing stack, and the Calendar ViewModel retains its selected date. Returning from Notifications can therefore expose a previously opened appointment or an old day, violating flow §7’s explicit destination. Reset the Calendar stack and select today.

---
**Answers (round 1, all fixed; second round run):**
1. *A page from before a refresh could be appended:* fixed. Each refresh starts a new generation; a page (first or
   next) answering for an older one is dropped, and a refresh ends any "loading more". Tested with a held page.
2. *The count went stale on open and refresh:* fixed. Every refresh of the inbox (opening it, coming back, pull,
   Refresh) asks the shell to read the count again (`NotificationReads`), as 07 §2.4 says. Tested.
3. *Mark all was off while unread rows sat on later pages:* fixed. It's on when a loaded row is unread, the count is
   above 0, or the count isn't known; the ViewModel no longer second-guesses it. Tested.
4. *Open Calendar could land on an old screen or day:* fixed. It resets the Calendar tab to its root and sends it to
   the appointment's practice day (from the row), else today (`CalendarFocus`); flow §12 lists "always today" as a
   Flutter bug. Tested (the stack).
