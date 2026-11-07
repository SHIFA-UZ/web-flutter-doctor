- **P2 — Chat cursor can skip incoming messages.** [ThreadViewModel.kt:112](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadViewModel.kt:112): `after` uses the highest displayed ID, including successful local sends. If messages 101–250 arrive and the doctor’s send returns 251, polling requests `after=251`, skipping the unseen messages. The same happens when a capped page is followed by a send. They remain missing until a successful full refresh. Track the fetched cursor separately and drain full pages.

- **P2 — Cross-month weeks omit appointments.** [WeekGrid.kt:103](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/WeekGrid.kt:103): The grid reads seven cached dates, but loading still fetches only the selected month (`monthCells` excludes neighboring months). Opening November 1, 2026 with a fresh cache displays October 26–31 as empty indefinitely. Explicitly load every displayed week date.

- **P2 — Calendar failures look like empty schedules.** [CalendarScreen.kt:515](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:515): The default wide-screen branch bypasses `day()`, which contains the loading error and Retry controls. With an offline or failed initial request, `WeekGrid` converts missing cache entries to empty lists and presents an apparently empty calendar without an error or retry action.

- **P2 — Selected colleagues disappear in week mode.** [CalendarScreen.kt:514](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/CalendarScreen.kt:514): Staff chips remain visible, but colleague sections render only inside the bypassed `day()` branch. Selecting a colleague on a wide screen updates the chip and fetches their appointments, yet none appear. Either render those calendars in week mode or switch to day mode when colleagues are selected.

- **P2 — An attended video visit can be marked as a no-show.** [VideoVisit.kt:116](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:116): `patientJoined` reflects current presence, not whether the patient ever attended. After a patient participates and disconnects, the waiting clock restarts and offers no-show after five minutes. Also, an already-open confirmation remains actionable if the patient joins; `VisitViewModel.noShow()` checks only whether a request is running. Track attendance and revalidate before submission—the backend’s status/time checks permit this incorrect transition.

- **P2 — System Back skips About when viewing the contract.** [AboutScreen.kt:109](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/AboutScreen.kt:109): The PDF replaces About locally without installing a `BackHandler`. Android Back therefore reaches the shell and pops the About route, whereas the visible back button correctly closes only the PDF. Handle system Back while `viewing` is true.

- **P3 — Midnight endpoints display as 23:00.** [WeekGrid.kt:268](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/calendar/WeekGrid.kt:268): End-of-day is represented as minute 1440, but `clockTime` clamps hour 24 to 23. A practice-zone appointment from 23:30 to midnight consequently displays `23:30–23:00`. Format midnight explicitly or use the original endpoint’s local time.

Static review only; tests were not run in the read-only environment.
---

Answers (all fixed):
- P2 chat cursor skipping messages: the cursor is now the newest id a read returned (`fetchedUpTo`), never a send, and a
  newer-only read that comes back full (100) reads on; tested with 150 incoming messages and a send in between.
- P2 cross-month weeks: the prefetch adds the selected week's seven days to the month's.
- P2 failures looking empty: the week view shows the day view's error and Retry above the grid.
- P2 colleagues in week mode: with colleagues shown the toggle hides and the day view (with their sections) is used.
- P2 attended visit marked no-show: `patientSeen` stays true once the patient has been in the call (no waiting clock or
  button after a drop), and the visit's `noShow()` re-checks it before sending; tested.
- P2 system Back on the contract: a BackHandler closes the viewer.
- P3 midnight as 23:00: minute 1440 reads 00:00.
