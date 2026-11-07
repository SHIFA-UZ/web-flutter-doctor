- **[P2] Phone-zone fallback remains and is mislabeled as practice time.** [TaskDetailScreen.kt:144](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailScreen.kt:144) uses the device zone when the practice zone is missing or invalid. Without a patient zone, this determines Pending/Missed and submitted timestamps, while line 210 displays “times follow the practice time zone.” This still violates TASK-03 Fix #4 and flow §8.

- **[P2] A refresh returning 404 leaves the deleted task actionable.** [TaskDetailViewModel.kt:88](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailViewModel.kt:88) sets `notFound` but retains the previous task. [TaskDetailScreen.kt:173](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailScreen.kt:173) only shows “Task not found” when `task == null`. After deletion elsewhere, resume/pull therefore retains stale details and Cancel instead of the required not-found state with Back.

---
**Answers (round 2, both fixed; small, so no third round):**
1. *The phone's zone still stood in:* fixed. The fallback is the practice zone or nothing: with neither zone the
   check-ins keep the server's status (no local "missed"), "Submitted at" is labelled UTC, and the practice-zone note
   shows only when the practice zone is the one used. Tested (`effectiveStatus` without zones).
2. *A 404 on refresh left the deleted task actionable:* fixed. The task is dropped, so "Task not found" with Back
   replaces the details and Cancel. Tested.
