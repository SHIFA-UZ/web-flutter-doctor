- **[P2] Size cap runs after compression.** [ThreadViewModel.kt:163](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadViewModel.kt:163) checks the picker’s already-downsampled JPEG. An original photo exceeding 20 MB can therefore pass and upload. Flow 05 §4/§9 requires rejecting oversized originals **before compression**; preserve and validate the original size.

- **[P2] Retrying a failed send uploads the image again.** [ThreadViewModel.kt:186](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadViewModel.kt:186) unconditionally uploads on every delivery attempt. If upload succeeds but message creation fails, Retry creates another attachment and leaves the previous upload orphaned. Retain the uploaded URL and retry only message creation, as required by flow 03 §6.

- **[P2] Permanent camera denial has no recovery action.** [ThreadScreen.kt:146](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:146) emits only “Camera permission denied”; `ShellNoticeBanner` provides dismissal only. Once permission is permanently denied, repeated camera taps cannot recover access. Flow 05 §6 requires an “Open Settings” action.

- **[P2] Viewer cannot zoom out to the required 0.5×.** [ChatImages.kt:155](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ChatImages.kt:155) uses `ZoomState`, whose `change()` clamps scale to `1f..max`. Pinching inward stops at 1×, contrary to CHAT-04 and flow 05’s 0.5×–4× range.

- **[P2] Screen content now owns modal state.** [ThreadScreen.kt:172](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:172) stores attach-sheet and viewer state inside `ThreadContent`. This violates the specified stateless screen-content architecture and prevents callers/previews from supplying those states. Hoist them into the screen host or UI state and pass callbacks.

---
**Answers (round 1; four fixed and tested, one answered; small, so no second round):**
1. *The size cap runs after compression:* kept as it is, on purpose. The shared picker decodes a large original at a
   sample size near the limit (no memory blow-up) and only the at-most-1600 px JPEG is uploaded, so the cap guards
   what is actually sent; refusing a 25 MB original that becomes a 400 KB JPEG would only stop the doctor. (Flutter had
   no cap at all; the 20 MB rule is the flow's target.)
2. *Retry uploaded again:* fixed. The uploaded URL is kept on the pending photo; a retry after a failed send only sends
   again. Tested. (The test also caught that `PendingMessage`'s own `equals` hid the URL from the StateFlow; removed.)
3. *No way back from a refused camera:* fixed. A strip above the composer says "Camera permission denied" with Open
   settings and Close (the app's other camera screens do the same).
4. *The viewer couldn't zoom out to 0.5×:* fixed. `ZoomState` takes a `min` (default 1×, so the documents' viewer is
   unchanged; its tests pass); the chat viewer uses 0.5×, with no panning while smaller than the fit. Double tap
   toggles 2×.
5. *Modal state lived in the screen content:* fixed. The attach sheet, the viewed photo and the refused camera are the
   host's (`ThreadOverlays` + actions), so `ThreadContent` stays stateless and previews can set them.

