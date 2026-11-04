- **P2 — Failed default-period creation disables retry.** [ScheduleScreen.kt:438](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/schedule/ScheduleScreen.kt:438) marks the hours saved before creating the implicit 60-day period. If that PATCH fails, `week == saved` and `newUntil == null`, so `dirty` becomes false and Save disables. Availability remains unpublished; retain the failed period as pending so Save can retry it.

- **P2 — Extra-hours edits during submission are discarded.** [ScheduleScreen.kt:470](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/schedule/ScheduleScreen.kt:470) allows editing while `dateBusy` is true. Change dates or times after pressing Add: the POST uses the earlier snapshot, then line 498 clears the edited draft. Disable these edits during submission or preserve subsequent changes.

- **P2 — Extra-hours submission errors appear behind the modal.** [ScheduleScreen.kt:504](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/schedule/ScheduleScreen.kt:504) stores failures in `state.banner`, rendered only on the underlying screen at line 630. The extra-hours sheet stays open and renders no backend-error banner, so overlap/network errors and their request IDs are hidden while correcting or retrying the form. Render the failure inside the sheet.
---
**Answers (round 2, all fixed):**
1. *A failed default period disables retry:* a failed period (the first setup's default too) stays as the pending new period, so the screen stays dirty and Save tries it again. Tested.
2. *Extra-hours edits during the request:* the form ignores edits while the hours are being sent.
3. *Extra-hours errors hidden behind the sheet:* the error (with its request id) shows inside the sheet, over the form being retried.
