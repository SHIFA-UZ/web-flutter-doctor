- **[P2] Notifications refresh on start, not resume.** [MainScreen.kt:149](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:149) wires refresh to `ON_START`. Returning from a pause that never stopped the activity—such as a translucent activity—emits `ON_RESUME` without `ON_START`, leaving the notification badge stale. Wire the notification refresh separately to `ON_RESUME`.

- **[P2] Overlapping notification refreshes can restore an outdated count.** [ShellViewModel.kt:180](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:180) launches an untracked request on every foreground entry; `onBackground()` cancels only chat polling. If the user backgrounds and returns while a notification request is pending, the older response can arrive after the newer response and overwrite it. Cancel the previous refresh or reject responses from superseded refreshes.
---
**Answers (both fixed):**
1. *Start vs resume:* the notifications count now refreshes on `ON_RESUME` (`refreshNotifications()`), which also follows a pause that never stopped the activity; chat polling stays tied to `ON_START`/`ON_STOP`.
2. *Overlapping refreshes:* each refresh cancels the previous one still in flight, so an older answer can't overwrite a newer count.
Small and local; no second round.
