- **P1 — Phone uploads escape cancellation.** [VoiceSession.kt:386](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/intelligence/VoiceSession.kt:386): with `viewModelScope`’s immediate dispatcher, phone `stop()` returns synchronously and `upload()` assigns its job to `work`. The enclosing `work = scope.launch` then overwrites that handle with the completed outer job. `leave()` cancels the wrong job, allowing transcription to finish and submit a question after leaving. Keep stopping and uploading in one tracked coroutine.

- **P2 — Stopping during microphone acquisition discards the take.** [VoiceRecorder.wasmJs.kt:120](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/VoiceRecorder.wasmJs.kt:120): `stop()` clears `s.starting` before awaiting it. The acquisition callback at line 106 consequently treats its own stream as obsolete, stops it, and returns false. Thus stopping while `getUserMedia()` is pending always returns null instead of waiting for startup as documented. Preserve the take’s identity until acquisition completes.

- **P1 — Restarting during stop loses audio and contaminates the next take.** [VoiceRecorder.wasmJs.kt:137](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/VoiceRecorder.wasmJs.kt:137): `cancel()` removes the `onstop` callback that resolves the pending stop promise, but leaves `ondataavailable` writing into shared `s.chunks`. Chat enables another recording immediately after Send, so a new `start()` can leave the previous send suspended indefinitely and append its delayed audio chunks to the new recording. Reproduced with the extracted JavaScript and mocked recorder events. Use per-take state and settle pending stops on cancellation.

- **P2 — Recorder initialization failure leaves the microphone open.** [VoiceRecorder.wasmJs.kt:116](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/wasmJsMain/kotlin/shifa/doctor/utils/VoiceRecorder.wasmJs.kt:116): after acquiring the stream, a failure in `new MediaRecorder(...)` or `rec.start()` reaches a catch that only returns false. It never releases the acquired tracks, while Kotlin has already reported successful startup. The microphone stays active until a later stop/cancel. Release that take’s stream on initialization failure.

---

Answers:
- P1 voice session upload escaping cancellation: fixed. `finish()`'s job now stops the take and transcribes in the
  same coroutine (`transcribeAndAsk`, a suspend function), so `work` is the job leave() cancels. New test "leaving
  while a take is being transcribed asks nothing", run on an immediately dispatching scope like `viewModelScope`; it
  fails with the old code and passes now.
- P2 stopping during microphone startup: fixed. Each take is its own object; stop() takes it and waits for its
  startup instead of marking it obsolete, so a stop right after start returns what was recorded.
- P1 restarting during stop: fixed. A stopped take keeps its own recorder and chunks and always settles its promise
  through its own onstop; a new start() only cancels the current take, never one being stopped. Checked in Chrome:
  record → stop → record → stop in quick succession gives the last take's own length.
- P2 recorder start failure leaving the microphone open: fixed. A MediaRecorder that can't be made or started releases
  that take's stream before answering false.
