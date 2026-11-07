- **[P1] Backgrounding leaves recording and auto-send active.** [IntelligenceScreen.kt:206](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/IntelligenceScreen.kt:206) has an empty `onPauseOrDispose`; cleanup only happens on composition disposal. Pressing Home retains the recorder and VAD jobs, allowing background audio to be recorded and uploaded. Flows 04/05 require stopping and discarding on background.

- **[P1] An iOS audio interruption leaves Speaking stuck indefinitely.** [SpeechPlayer.ios.kt:22](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/iosMain/kotlin/shifa/doctor/utils/SpeechPlayer.ios.kt:22) handles completion and decode errors but no interruption notification. Apple explicitly states that [completion is not called on interruption](https://developer.apple.com/documentation/avfaudio/avaudioplayerdelegate/audioplayerdidfinishplaying%28_%3Asuccessfully%3A%29?language=objc). An incoming call therefore leaves `play()` suspended, retaining the player and blocking automatic recovery.

- **[P2] Mic/record during a typed reply records a question that can be silently discarded.** [VoiceSession.kt:158](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/VoiceSession.kt:158) sees Idle during typed streaming, so it starts recording without cancelling that stream. If transcription finishes first, [ThreadViewModel.kt:164](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:164) rejects it as busy; the transcript disappears. Synchronize these controls with thread state so “Reviewing” interruption cancels the existing turn.

- **[P2] Retrying a voice question loses its voice origin and overlaps recording.** [ThreadViewModel.kt:172](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/ThreadViewModel.kt:172) calls `ask(q)` with `voice=false`, without stopping the re-listen scheduled by `turnFailed()`. The retry answer is never spoken, and the microphone can reopen during its stream. Preserve the failed turn’s voice metadata and suspend re-listening during Retry.

- **[P2] Sign-off phrases do not end hands-free.** [VoiceSession.kt:339](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/VoiceSession.kt:339) forwards every nonempty transcript to `ask`, including “that's it”, “rahmat”, and “хватит”. Hands-free remains armed and reopens the microphone after the answer. INTEL-09 and flow 04 §7 require local sign-off handling that terminates the loop.

- **[P2] Failed uploads permanently lose dictation.** [VoiceSession.kt:319](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/VoiceSession.kt:319) stops the recorder, whose actuals delete the file; audio survives only in the upload coroutine. On failure, `failed()` resets state without retaining audio or offering Retry. A network failure loses up to ten minutes of dictation, contrary to flow 05’s explicit retention requirement.

- **[P2] Playback failures are silently treated as successful completion.** [VoiceSession.kt:234](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/VoiceSession.kt:234) ignores `SpeechPlayer.play()`’s Boolean result. Empty or undecodable audio returns `false`, but no “Voice skipped” banner appears and hands-free resumes. Flow 06 requires surfacing these failures.

---

- **P1 background:** fixed. `VoiceSession.paused()` from the screen's pause: the take is stopped and dropped (with the
  flow's "Recording stopped while the app was in the background."), the voice stops, hands-free ends. Test: "going to
  the background drops a take and says so".
- **P1 iOS interruption:** fixed. `IosSpeechPlayer` observes `AVAudioSessionInterruptionNotification` while a clip
  plays and ends it (play returns false; the loop goes on).
- **P2 mic/record during a typed reply:** fixed. The session asks the thread (`busy`): the mic stops that reply and
  listens; record stops it with "Stopped." (board `DaIntel`). Test.
- **P2 Retry of a spoken question:** fixed. The thread remembers how the last question was asked; Retry of a spoken
  one tells the voice (no re-listen meanwhile) and its answer is spoken.
- **P2 sign-off:** fixed. Flutter's phrase list and patterns are ported (`VoiceSession.isSignOff`); a sign-off ends
  hands-free locally with the flow's line as a banner and nothing goes to Shifa. Not spoken: the strings resolve in
  the UI. Tests.
- **P2 failed uploads lose the take:** fixed. The take is kept (unless the plan refuses it) and the banner offers
  Retry, which sends it again without recording. Test.
- **P2 playback failure:** fixed. A clip that can't play says "Voice skipped — caption is above.". Test.
