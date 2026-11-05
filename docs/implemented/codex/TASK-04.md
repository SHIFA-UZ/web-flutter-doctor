- **[P1] Task picker includes unrelated patients.** [CreateTaskViewModel.kt:193](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/CreateTaskViewModel.kt:193) uses `forAssignment()`, whose backend handler returns **every stored patient** (`PatientsController.kt:150`). Flow §3 restricts task assignment to the doctor’s patient list. Use that scoped list; retain the global directory for booking.

- **[P2] Missing end dates pass validation and silently change the schedule.** [TaskForm.kt:71](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/tasks/TaskForm.kt:71) accepts `useEndDate=true, endDate=null`; lines 96–97 then omit both end date and duration. This occurs initially and after moving the start beyond an existing end. Backend `RemoteCareTaskController.kt:492,532` silently generates 31 calendar days. Require an end date or explicitly populate the default before saving.

- **[P2] Duration sanitization converts invalid input into a different valid prescription.** [CreateTaskScreen.kt:594](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/CreateTaskScreen.kt:594) converts pasted `-3` to `3`, `1.5` to `15`, and `1000` to `100`. These pass validation instead of producing the ticket’s required invalid-duration error. Preserve input for validation or reject invalid edits without rewriting their numeric meaning.

- **[P2] Time selection excludes valid minute values.** [CreateTaskScreen.kt:393](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/CreateTaskScreen.kt:393) restricts both scheduling modes to quarter hours. Doctors cannot enter `08:10`, although §3.3 and the flow specify minute-resolution time pickers. A template’s non-quarter-hour value also disappears from the choices once changed. Provide minute-resolution selection.

---
**Answers (round 1, all fixed; second round run):**
1. *The picker offered every patient:* fixed. It lists the doctor's own patients (the Patients tab's directory,
   `refreshIfStale`, cached), not booking's `for-assignment` directory of everyone; booking keeps its list.
2. *A missing end date passed and the backend picked 31 days:* fixed. With "Use end date" on, an end date is required
   ("Pick an end date"), also after a later start cleared it. Tested.
3. *The duration field rewrote input into other numbers:* fixed. It keeps what was typed; "1.5", "-3", "" are errors
   ("Enter a number of days (at least 1)"). Tested.
4. *Only quarter hours:* fixed. Times are picked to the minute (a sheet with Material's 24-hour time entry; a
   template's 07:10 stays). The quarter-hour dropdowns are gone.
