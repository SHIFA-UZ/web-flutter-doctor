- **[P2] Check-in refresh failures are swallowed once data exists.** [TaskDetailScreen.kt:204](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailScreen.kt:204) only displays the failure when `checks == null`. After one successful load, a failed refresh silently presents stale submissions without an error or Retry, violating TASK-03’s Fix #16.

- **[P2] Long check-in notes cannot be read fully.** [TaskDetailScreen.kt:422](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailScreen.kt:422) renders sheet content in a non-scrollable `Column`; neither enclosing wrapper adds scrolling. Long notes or large text push content and Close beyond the available height, contrary to flow §8.

- **[P2] Notification navigation returns to the wrong destination.** [MainScreen.kt:437](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/MainScreen.kt:437) pushes details onto the Notifications stack. Back and successful cancellation therefore return to the inbox instead of the refreshed Tasks list required by flow §§2, 10.

- **[P2] Missing patient timezone produces unmarked, potentially incorrect results.** [TaskDetailScreen.kt:141](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailScreen.kt:141) falls back to the phone zone when the practice zone is unavailable; line 185 silently uses that fallback. This can incorrectly mark a check-in Missed, while submitted timestamps are still labelled “patient time.” Flow §8 requires practice-zone fallback marked approximate and excludes phone-zone calculations.

- **[P2] Tier refusal has no gate state.** [TaskDetailViewModel.kt:80](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailViewModel.kt:80) handles a task’s 403 as an ordinary retryable load error. A BASIC/PRO doctor entering through the newly wired notification target gets an error/Retry screen instead of the tier gate required by flow §6.

- **[P2] An open check-in sheet stays stale after resume refresh.** [TaskDetailViewModel.kt:66](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/tasks/TaskDetailViewModel.kt:66) replaces `checkIns` but preserves the old `viewing` object. If the patient submits while the app is backgrounded, returning refreshes the list but the open sheet still reports Pending/Missed and omits the new submission until closed and reopened.

---
**Answers (round 1, all fixed; second round run):**
1. *A failed check-in refresh was silent over an older list:* fixed. A line "Couldn't load check-ins" with Retry sits
   above the list that stays.
2. *Long notes didn't scroll:* fixed. The sheet's content scrolls, Close included.
3. *From a notification, Back and cancel landed on the inbox:* fixed. `ShellViewModel.openTask` opens the task over the
   Tasks list in the Tasks tab (as Flutter's shell reset did), so both land on the list, which reads again. Tested.
4. *A missing patient zone gave unmarked results:* fixed. Without the patient's zone the times follow the practice
   zone (the phone's only if even that's unknown), the screen says so under "Check-ins", and "Submitted at" drops
   "(patient time)".
5. *No gate on a 403:* fixed. From a notification, a doctor without the plan gets the shell's "not on your plan" note
   instead of the screen; a 403 on the screen itself reads the profile again so the shell closes Tasks (as the
   list does). Tested.
6. *An open check-in sheet stayed stale after a refresh:* fixed. The sheet follows the check-in by id, so it shows the
   refreshed one. Tested.
