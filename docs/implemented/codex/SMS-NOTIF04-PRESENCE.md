- **P2 — Refresh can undo a successful access decision.** [NotificationsViewModel.kt:204](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/notifications/NotificationsViewModel.kt:204) updates only the current list. If an inbox refresh captures `pending`, then completes after Approve succeeds, it overwrites the approved status and restores both buttons. `reconcile()` preserves reads but not decisions. Preserve completed decisions across outstanding page responses, or invalidate those responses.

- **P2 — Presence polling continues while the app is backgrounded.** [VideoPane.kt:110](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoPane.kt:110) stops polling only when the composable leaves composition. Pressing Home or locking the device leaves that composition and the ViewModel scope alive, so requests continue every five seconds while execution is permitted. Bind polling to the screen’s active lifecycle as well as composition.

- **P2 — Presence failures silently discard the error and request ID.** [VideoVisit.kt:251](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:251) converts every failure into `patientInRoom = null`. A 403 or 503 merely removes the presence label and keeps retrying; no banner explains the failure or exposes its request ID, contrary to the requested error-handling rule. Retain and display the mapped failure separately from unknown presence.

- **P2 — Back after resend restores the wrong delivery channel.** [App.kt:213](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/App.kt:213) carries the original route’s `hint`, although resend updates only `CodeViewModel.emailHint`. If the first code goes by SMS and a resend falls back to email, Back → Continue within the cooldown sends nothing and incorrectly says the current code went by SMS. Preserve the latest delivery metadata with the phone’s cooldown, or pass the updated hint back.

Static review only; tests were not run in the read-only environment.
---

Answers:
- P2 a refresh undoing a decision: fixed. Decisions made here are kept (`decidedHere`) like reads, so a page asked
  before the answer can't bring the buttons back; tested.
- P2 presence polling in the background: fixed. The pre-join page asks only while resumed (`LifecycleResumeEffect`,
  as the chat thread's polling), so Home or the lock screen stops it.
- P2 presence failures without a banner: by design. Presence is a hint next to the patient's name, asked every 5 s; a
  banner for each 503 would cover the page. Unknown shows nothing (never a guessed "not here"), and every failure is
  still logged by `apiCall` with its request id. A 403 can't happen for the visit's own doctor (staff don't run visits).
- P2 Back after a resend restoring the wrong channel: fixed. Where the last code went now lives with the resend
  cooldown (`OtpCooldown.hint`), which both screens read, so a resend that changes the channel is what a later code
  screen shows; the route no longer carries it. Tested.
