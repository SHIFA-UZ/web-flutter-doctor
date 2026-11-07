- **[P1] Failed joins can crash preview recovery.** [VideoCall.wasmJs.kt:231](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/VideoCall.wasmJs.kt:231): `release()` returns before `destroy()` completes. On join failure, `VideoVisit.watch()` immediately calls `free()` then `renew()`, creating another Daily instance while the old one remains registered. Daily 0.94.0 defaults to strict single-instance mode and throws “Duplicate DailyIframe instances are not allowed.” Coordinate destruction with replacement creation.

- **[P1] The actual visit’s pre-join preview never starts.** [VideoCall.wasmJs.kt:214](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/VideoCall.wasmJs.kt:214): `VideoVisit.permissions()` initializes media through `setCamera()`/`setMic()`, whereas these wrappers only send Daily control messages. Neither loads Daily’s call machine nor starts capture; `startCamera()` is reached only by the debug spike. Consequently, the visit preview stays blank until joining, unlike Android’s `setInputsEnabled()` behavior.

- **[P2] Permission refresh unexpectedly acquires camera and microphone.** [VideoCall.wasmJs.kt:118](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/VideoCall.wasmJs.kt:118): the `ask` argument is discarded. `VisitScreen` calls `permissions(false)` on resume, so returning to the tab invokes `getUserMedia()` again—even during a call or with camera disabled—and can display permission prompts. Implement the interface’s non-prompting read path.

- **[P2] A pending wake-lock request leaks after disposal.** [KeepScreenOn.wasmJs.kt:16](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/KeepScreenOn.wasmJs.kt:16): if disposal occurs before the request resolves, `releaseWakeLock()` sees `null`; the later callback stores a live lock that nobody releases. Track disposal and immediately release any subsequently granted lock.

- **[P2] Switching tabs permanently loses the call’s wake lock.** [KeepScreenOn.wasmJs.kt:9](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/KeepScreenOn.wasmJs.kt:9): acquisition runs only on composition entry. Browsers release the lock when the document becomes hidden, but returning to an ongoing call does not recreate this effect, allowing the screen to sleep. Reacquire on visibility restoration and remove that listener on disposal. [Screen Wake Lock specification](https://www.w3.org/TR/screen-wake-lock/)

---

Answers:
- P1 duplicate Daily instances after a failed join: fixed. The call object is created with
  `allowMultipleCallInstances: true`, so the visit's new call can start while the failed one is still being destroyed.
- P1 preview never started: fixed. `setCamera`/`setMic` before joining start Daily's local media once (`startCamera`)
  and then set the input, as Android's `setInputsEnabled` does; after joining they only set it.
- P2 permission refresh prompting: fixed. `ask = false` only reads, through `enumerateDevices()` labels (named once
  access is granted), and never calls `getUserMedia`.
- P2 wake lock leaking after a quick dispose: fixed. A lock granted after disposal is released at once.
- P2 wake lock lost after a tab switch: fixed. It is taken again on `visibilitychange` while the call screen is shown;
  the listener is removed on dispose.
- Not checked in a browser: the picture itself (headless Chrome here has no working fake camera; `getUserMedia` for
  video fails even outside the app). The states, permissions and preview flow were checked with the debug "Camera
  preview" check; a real call needs a check on a machine with a camera.
