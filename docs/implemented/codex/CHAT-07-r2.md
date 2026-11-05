- **[P2] Conversation-start failures discard the support request ID.** [PatientChat.kt:51](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/PatientChat.kt:51) reduces the exception to a `StringResource`; line 89 displays only that message. When `/start` returns an error with `requestId`, the required banner detail is lost. Preserve the ID in the failure state and display it alongside the localized message.

---
**Answers (round 2, fixed at the root; small, so no third round):**
1. *The request id was lost:* fixed for every shell banner, not just this one. `Notice` takes a `requestId`, and the
   shell's banner shows it as its detail (as `codeBanner` does); the patient opening keeps the whole `Notice` (text
   and id), and the list's "start" failure passes its id too. Tested.
