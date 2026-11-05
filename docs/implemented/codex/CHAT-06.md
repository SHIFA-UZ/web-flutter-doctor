- **P1 — Backgrounding leaves the microphone recording.** [ThreadScreen.kt:182](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:182) only stops polling on pause; the recorder and ticker remain active. Android’s recorder explicitly starts a foreground recording service. Pressing Home or locking the phone therefore continues capturing audio instead of auto-stopping and preserving the clip as flow §7 requires.

- **P2 — Failed stops silently discard the recording.** [ThreadScreen.kt:251](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:251) closes the bar and ignores a null `recorder.stop()` result. Android returns null when stopping too soon or when native stop fails, so these paths bypass both “Recording too short” and any interruption/error notice. The new ViewModel test supplies bytes directly and does not exercise this failure.

- **P1 — Playback remains enabled during recording.** [ThreadScreen.kt:255](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:255) forwards every bubble tap directly to the player, including while the recorder is active. Starting another note on iOS switches the shared audio session to playback-only in [AudioPlayer.ios.kt:46](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/iosMain/kotlin/shifa/doctor/utils/AudioPlayer.ios.kt:46), removing recording capability while the recording bar and timer continue. Guard playback during recording or explicitly finish recording first.

---
**Answers (round 1, all fixed; second round run):**
1. *Backgrounding kept the microphone on:* fixed. `ON_STOP` (Home, the lock, a call; not a permission dialog, which only
   pauses) stops the recorder and keeps the clip; a strip says "Recording interrupted — send or discard?" with Send and
   Discard (flow §7–8 target). Under a second there's nothing to keep; a failed stop says "Error recording voice".
   Checked on the emulator.
2. *A failed stop was dropped silently:* fixed. The bar's ✓ hands the recorder's result as it is to
   `sendVoice(audio?, seconds)`: under a second "Recording too short", no file "Error recording voice". Tested.
3. *Playback during recording:* fixed. Bubble taps do nothing while the bar is up (and starting a recording already
   pauses a playing note).
