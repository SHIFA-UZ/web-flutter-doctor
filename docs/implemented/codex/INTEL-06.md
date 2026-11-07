- **[P1] Reopening a chat permits duplicate bookings while the first request runs.** [ThreadViewModel.kt:133](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:133) guards only the transient `cards[index].busy`. `stop()` clears that map at line 226 without stopping the booking coroutine. Start booking, leave, reopen before completion, then choose another offered time: both requests can succeed, creating two appointments while the card records only the last result. Preserve pending operations by conversation and card across navigation.

- **[P1] A new AI turn can race an unfinished card booking.** [ThreadViewModel.kt:141](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:141) marks only the card busy; `send()` at line 108 checks only the streaming-turn flag. Consequently, “use 11:00 instead” can reach the backend while the 10:00 booking is pending, with history that still describes an unbooked proposal. The new turn can offer or execute another booking without resolving the first. Serialize booking mutations and conversation turns.

- **[P2] Successful bookings and moves leave future calendar dates cached.** [ThreadViewModel.kt:170](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:170) invokes `scheduleChanged()` without the affected dates; its default at lines 90–92 refreshes today only. [CalendarSelectors.kt:37](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/calendar/CalendarSelectors.kt:37) invalidates only supplied dates plus today. Booking tomorrow, or moving between future days, therefore leaves recently cached calendars showing the old availability until refresh or cache expiry. Pass the destination and original appointment dates.

---

- **P1 duplicate booking after leaving and reopening:** fixed. `ThreadViewModel` keeps the running booking per chat
  (`bookings`, not cleared by `stop()`); a second booking in that chat is refused until it ends, and back in the chat
  the card shows it still running. Test: "while a card books, leaving and coming back neither books again nor lets a
  question through".
- **P1 a new turn racing a card booking:** fixed. `send()` (and so the cancellation buttons) refuses while the chat
  books, `canSend` is false (`ThreadUiState.booking`), and a card can't book while Shifa answers. Same test.
- **P2 future days left cached:** fixed. `scheduleChanged` now takes the touched days: the visit's old day (from
  `currentDateLabel`) and the new slot's start for the app's bookings, the payload's `date` for the backend's
  `booked` / `moved`, and the cancelled visits' `startAt` for a cancellation; `CalendarRepository.changed` reads
  them with today. Tests in `ThreadViewModelTest`.
