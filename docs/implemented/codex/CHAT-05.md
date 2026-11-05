- **[P2] Document-open failures are silent.** [ThreadScreen.kt:190](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:190) wires `openFile` directly to the browser helper, which returns no result and swallows Android launch exceptions. With no URL handler, tapping does nothing instead of showing the “Cannot open document URL” banner required by flow 07/06 §9.

- **[P2] Missing URLs leave active, nonfunctional document buttons.** [ThreadScreen.kt:517](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:517) silently skips opening when `fileUrl` is null, while `DocumentTile` remains unconditionally clickable. Received documents without a URL therefore look available but do nothing; flow §6 requires a disabled card with “File unavailable.”

- **[P2] Unreadable selections are silently treated as cancellation.** [ThreadScreen.kt:170](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/chat/ThreadScreen.kt:170) ignores null picker results, but both platform pickers also return null when reading a selected file fails. Selecting an unreadable document produces neither a message nor an error banner. Distinguish read failure from cancellation and show “Error uploading file” as required by flow §9.

---
**Answers (round 1, all fixed; small, so no second round):**
1. *A failure to open was silent:* fixed. `rememberInAppBrowser` now says whether it could open the page (Android: the
   activity started; iOS: an http(s) URL and a window, which SFSafariViewController needs); when not, "Cannot open
   document URL". The profile's caller wraps it.
2. *A document without a URL looked tappable:* fixed. Its card is off (the pressables' disabled look), not clickable,
   and says "File unavailable" in place of the size.
3. *An unreadable pick looked like a cancel:* fixed. For a chat attachment both pickers report a pick they couldn't
   read (`DocumentPickerResult.readFailed`), and the thread says "Error uploading file" without sending; the PDF
   pickers keep their null. Tested.

