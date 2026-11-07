- **P2 — Dismissing an initial load error displays a false empty state.** [TasksViewModel.kt:83](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksViewModel.kt:83) converts an unknown list into `emptyList()` on failure; `dismissError()` at line 89 clears the only failure indicator. [TasksScreen.kt:171](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TasksScreen.kt:171) consequently shows “No tasks found” and “Create your first remote care task” without any successful response. Track whether loading succeeded separately from banner visibility, and show the empty state only after a successful empty response.

---
**Answers (round 2, fixed; small, so no third round):**
1. *Closing a first-read error showed a false "No tasks found":* fixed. A failure never turns the list into an empty
   one: it stays unknown with `failed`, and the screen shows "Couldn't load tasks", the request id and Retry in the
   list's place (no closable banner there); the empty card comes only from a successful empty answer. Tested;
   screenshot `Tasks_failed`.
