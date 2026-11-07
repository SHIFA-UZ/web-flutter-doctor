- **P1 — A failed PATCH drops later queued edits.** [SettingsScreen.kt:150](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/SettingsScreen.kt:150): Toggle 2FA, then encrypted documents while the first request is pending. If the first fails, `settings = confirmed` erases both edits. The queued write reads that reverted state and loses the encryption change. Flow §8 explicitly requires only the failed switch to revert.

- **P2 — Disallowed start tabs are never repaired on save.** [SettingsScreen.kt:141](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/SettingsScreen.kt:141): With stored `defaultStartTab = "tasks"` and Tasks unavailable, line 229 displays Home, but the PATCH only overrides `language`. Changing country or either switch therefore resends `"tasks"` instead of `"home"`, violating flow §8’s silent-repair requirement.

- **P2 — Task availability stays stale after a profile refresh.** [SettingsScreen.kt:127](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/SettingsScreen.kt:127): `tasksAllowed` is sampled only by `open()`, invoked through `LaunchedEffect(Unit)`. If the app resumes on Settings after a subscription downgrade, the shell refreshes the profile but Settings retains its previous gate. Tasks remains selectable and can be saved until the screen is reopened.

- **P2 — Pending saves have no visible status.** [SettingsScreen.kt:137](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/SettingsScreen.kt:137): Writes expose no pending state, and the controls render no progress indicator. During a slow PATCH, an optimistic change appears saved throughout the request. Flow §§3, 5 and 8 require an inline ring until completion, including slow requests.---
**Answers (all fixed):**
1. *A failed PATCH drops later edits (P1):* a failure now reverts only the fields whose value is still what that request carried; a change made after it stays and goes out with the next queued write. Tested.
2. *A disallowed start tab is resent:* the body repairs a start tab the plan doesn't allow to Home. Tested.
3. *Tasks gate stale after a refresh:* the ViewModel follows the shared profile, so a downgrade on resume takes Tasks away at once.
4. *No pending state:* a spinner shows above the settings while any PATCH is in flight.
Local; no second round.
