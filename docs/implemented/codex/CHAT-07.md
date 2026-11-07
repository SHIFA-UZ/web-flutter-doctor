- **[P2] A stale poll can restore an already-cleared unread badge.** [ShellViewModel.kt:233](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/navigation/ShellViewModel.kt:233) refreshes independently of the poll at line 375. If a pre-read poll returns after the read-triggered refresh, its older count overwrites the corrected badge until the next poll. Coordinate both paths with cancellation or response versioning.

- **[P2] Abandoned chat openings can display errors on unrelated screens.** [PatientChat.kt:44](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/PatientChat.kt:44) launches resolution in a shell-scoped ViewModel, retained after Back removes the route. A delayed failure still writes the global banner at line 48—even after another patient’s chat opens. Cancel abandoned resolutions or restrict their effects to the active opening.

- **[P2] The loading composer silently discards typing.** [PatientChat.kt:83](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/PatientChat.kt:83) renders `ThreadContent` with an empty draft and the default no-op `draft` callback. Its `BasicTextField` remains enabled ([ThreadScreen.kt:819](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:819)), so typing while `/start` is pending is discarded. Disable the placeholder input or preserve and transfer its draft.

---
**Answers (round 1, all fixed; second round run):**
1. *A stale poll could put the old count back:* fixed. The poll and a read's refresh go through one
   `readChatUnread()`; each ask is numbered and only the newest ask's answer is shown.
2. *An abandoned opening could show its error elsewhere:* fixed. The ViewModel only keeps what failed; the screen
   shows the banner and goes back, so an opening left with Back (its screen gone) says nothing. Tested.
3. *Typing while it resolved was dropped:* fixed. The placeholder's composer keeps its draft (saveable), handed to the
   thread's ViewModel when the conversation arrives (unless that thread already has a draft of its own, which wins).
