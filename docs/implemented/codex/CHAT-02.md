- **[P1] Polling continually cancels slow requests.** [ChatListViewModel.kt:91](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ChatListViewModel.kt:91) calls `refresh()` every five seconds; `refresh()` cancels the previous request at line 69. When responses consistently take longer than five seconds, the list never loads or updates, and cancellation prevents an error from appearing. Await completion or skip polling while a request is active.

- **[P2] Search failures appear as “No users found.”** [ChatListViewModel.kt:115](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ChatListViewModel.kt:115) converts failures into empty results and sets `searchFailed`, but [ChatListScreen.kt:194](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ChatListScreen.kt:194) ignores that flag. Network and permission failures therefore look like successful searches with no matches; refresh also only retries conversations. Render a localized search error and retry the current query.

- **[P2] Practice timezone is frozen before the profile may load.** [ChatListScreen.kt:118](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ChatListScreen.kt:118) reads `profileRepository.me.value` inside an unkeyed `remember`. Opening Chat before the profile finishes loading fixes the clock to its UTC fallback for that composition, even after the practice timezone arrives. Times and today/yesterday labels remain incorrect, violating CHAT-02’s Fix #4. Observe the profile and recreate the clock when its timezone changes.

---
**Answers (round 1, all fixed and tested; small, so no second round):**
1. *Polling cancelled slow reads:* fixed. A read already out is left to finish (a refresh during it shares its
   answer), and the poll waits for it before its next 5 s. Tested with a backend slower than the poll.
2. *A failed search looked like "No users found":* fixed. It shows "Could not load" with Retry, which asks the same
   words again at once. Tested.
3. *The clock froze before the profile came:* fixed. The list follows the profile's practice zone as it arrives.

