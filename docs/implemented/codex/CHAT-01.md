- **[P2] Search-result photos bypass URL normalization** — [ChatRepository.kt:103](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/chat/ChatRepository.kt:103). `search()` returns `photoUrl` unchanged, although backend `MessageService.kt:320,337` returns stored avatar paths directly. Relative paths therefore remain unusable as image URLs, violating CHAT-01’s “normalize all URLs” fix. Apply `absoluteFileUrl` when mapping search results.

- **[P2] Demo doctor conversations collapse into one fictitious patient** — [DemoChat.kt:59](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/debug/DemoChat.kt:59). The start handler ignores `recipientDoctorId` and defaults the patient ID to `0`. Calling `start(doctorId = …)` creates “Patient 0”; starting with another doctor returns that same conversation. Handle both recipient types and key conversations by role and ID.

---
**Answers (round 1, both fixed; small and tested, so no second round):**
1. *Search photos weren't made absolute:* fixed. `search()` passes each result's photo through `absoluteFileUrl`.
   Tested.
2. *The demo started every colleague as "Patient 0":* fixed. The demo chat reads `recipientDoctorId` as well, keeps
   one conversation per person and role (a colleague is `isDoctorParticipant`), and its search also finds a demo
   colleague. Tested in `DemoBackendTest`.

