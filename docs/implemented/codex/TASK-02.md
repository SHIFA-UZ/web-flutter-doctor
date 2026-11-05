- **P2 — First-load requests can race.** [TasksViewModel.kt:64](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksViewModel.kt:64): `refreshing` stays false while `tasks == null`, so tapping Refresh during initial loading launches additional requests. Responses update state unconditionally; an older response can overwrite newer data. Track the in-flight request independently of skeleton visibility.

- **P2 — Tier revocation receives no gate handling.** [TasksViewModel.kt:69](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksViewModel.kt:69): a tasks 403 becomes a generic load-error banner while cached cards and actions remain available. Flow §8 requires the tier gate when access is revoked server-side; this path neither refreshes entitlement nor requests that gate.

- **P2 — Offline actions remain enabled.** [TasksScreen.kt:167](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:167), [TasksScreen.kt:204](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:204): Create and Use Templates remain callable after a network failure. No offline flag reaches either control, contrary to flow §6’s requirement to disable both while showing cached tasks.

- **P2 — Error-banner controls are incomplete.** [TasksScreen.kt:156](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:156): `codeBanner(e) {}` renders a close button whose callback does nothing, and supplies no Retry action. Wire dismissal and the retry required by flow §§5–6.

- **P3 — Empty lists retain irrelevant filters.** [TasksScreen.kt:155](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:155): filters render unconditionally, including after a successful empty response. Flow §6 explicitly requires hiding them when there are no tasks, while retaining them for filtered-empty results.

- **P3 — Patient initials are never displayed.** [TasksScreen.kt:337](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:337): every card passes `null` to `ShifaAvatar`, producing the same fallback image for every patient. Both §3.1 and the board require initials derived from the patient’s name.

---
**Answers (round 1: four fixed, two answered; second round run):**
1. *First-load reads could race:* fixed. One read at a time: a new one cancels the one out (the newest wins). Tested.
2. *A revoked plan got only a banner:* fixed. A 403 reads the profile again; the shell's existing rule then closes the
   Tasks tab and shows "not on your plan" (the gate of flow 07/16 for a doctor who never had it is the shell's).
   Tested.
3. *Create and Use Templates stayed on offline:* kept, on purpose. One failed read doesn't mean offline (the app has
   no connectivity state), and disabling on a stale error would leave them off until a refresh; both screens report
   a failed call themselves.
4. *The banner's close did nothing and there was no Retry:* fixed. Close clears it; Retry sits under it (as in Chat).
5. *Filters showed with no tasks at all:* fixed. Hidden when there are none; kept when a filter hides everything.
6. *No initials:* kept, on purpose. CLAUDE.md: avatars without a photo show the brand fallback picture
   (`ShifaAvatar`); the board's initials circle predates that rule. Commented in the code.
