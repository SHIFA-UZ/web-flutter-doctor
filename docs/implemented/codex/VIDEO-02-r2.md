- **[P1] iOS can join after Leave or End.** [DailyVideoCall.swift:45](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/iosApp/iosApp/DailyVideoCall.swift:45): the asynchronous input-update completion unconditionally calls `joinRoom`. If `leave()` runs before that completion, the retained native object still joins afterward; cancelling the Kotlin token job cannot cancel this callback. Invalidate pending native joins on leave and check that invalidation before joining.

- **[P1] An SDK error hides controls without leaving the call.** [VideoVisit.kt:87](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:87): `failed=true` changes the UI to `Left` without releasing the client. Both bridges can report failure while their phase remains `Joined`, so media can continue while the screen says “You left the call.” Rejoin also reuses this client because `released` remains false. Tear down the call when treating an error as terminal.

- **[P2] Granting a permission resets the other device’s chosen state.** [VideoVisit.kt:116](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:116): any newly granted permission calls `startPreview()`, which enables both camera and microphone on both platforms. For example, grant camera, deny microphone, switch camera off, then grant microphone in Settings: returning turns the camera back on, and Join publishes it. Preserve existing toggle choices when initializing newly permitted inputs.

---
**Answers (round 2, all fixed and tested; the iOS app rebuilt):**
1. *iOS could join after Leave:* fixed. `DailyVideoCall` drops a join still waiting for its inputs once `leave()` ran
   (a `leaving` flag checked before joining).
2. *An SDK error showed "left" with the call still running:* fixed. A refused join or an error or drop in the call
   now frees the client. Rejoin then makes a new one, and on the pre-join page a fresh call shows the preview with
   the doctor's choices. Tested both.
3. *A newly granted device switched the other back on:* fixed. The doctor's choices (the switches) are kept in the
   holder and applied per device as far as the permissions allow. `startPreview` (which turned both on) is no longer
   used. Tested: camera off, microphone granted later, the camera stays off and the join publishes it off.
