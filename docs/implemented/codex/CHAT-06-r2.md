- **P2 — Starting another recording silently discards the held clip.** [ThreadScreen.kt:231](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:231) clears `held` whenever recording starts. After backgrounding and returning, the composer remains available beneath the interruption strip, so Attach → Record Voice Message permanently loses the previous clip without Send or Discard. Block new recording until that choice is resolved, or explicitly confirm replacement.

---
**Answers (round 2, fixed; small, so no third round):**
1. *A new recording dropped the held clip:* fixed. While the strip waits for Send or Discard, the attach sheet leaves
   out "Record Voice Message" (`AttachSheet(canRecord)`); the clip can't be replaced without the doctor's choice.
