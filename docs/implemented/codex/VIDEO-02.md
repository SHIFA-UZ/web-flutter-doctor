- **[P1] Joining can resume after Back or End.** [VideoVisit.kt:97](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:97): the token-request job is neither retained nor cancelled by `release()`. A late success calls `join()` on the released client; a late failure overwrites `Left`. Cancel the request and reject stale results before invoking the SDK.

- **[P1] Android ignores pre-join camera/mic choices.** [VideoVisit.kt:99](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:99) joins through [VideoCall.android.kt:114](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/androidMain/kotlin/shifa/doctor/utils/VideoCall.android.kt:114), which explicitly enables both inputs. Joining after disabling camera or microphone turns them back on, exposing media the doctor disabled.

- **[P2] Failed rejoins leak call clients and collectors.** [VideoVisit.kt:92](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:92): Rejoin allocates a client before fetching the token. Token failure restores `Left` without releasing it or cancelling its watcher; `release()` then skips cleanup because the stage is `Left`. Another Rejoin replaces that unreleased client.

- **[P2] Settings cannot recover denied permissions.** [VisitScreen.kt:374](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:374): permissions are checked only by `LaunchedEffect(v)`. Returning from Settings does not change `v` or restart this effect, so granting permissions leaves `allowed=false` and Join disabled until the screen is reopened.

- **[P2] Camera denial incorrectly blocks audio-only calls.** [VideoVisit.kt:83](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:83): one Boolean represents both permissions and gates Join. With microphone granted and camera denied, the screen disables Join and incorrectly reports microphone denial. Flow 10 §6 explicitly allows this case with camera off.

- **[P2] SDK disconnection leaves the UI stuck “in call.”** [VideoVisit.kt:67](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VideoVisit.kt:67): SDK failures are handled only during `Joining`; `Phase.Left` is never handled. A disconnect after joining leaves `stage=InCall`, keeps the screen awake, and provides no error/retry or Rejoin state.

- **[P2] Pre-join can hide the only way to leave with unsaved work.** [VisitScreen.kt:482](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:482): a failed SDK rejoin returns to PreJoin, whose early return skips the `askLeave` sheet at line 525. If the workspace has pending photos/payment or failed note saves, Cancel/Back sets `askLeave`, but the confirmation never appears and navigation stalls.

- **[P2] Back bypasses call-leave confirmation.** [VisitScreen.kt:397](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/visit/VisitScreen.kt:397): toolbar/system Back directly invokes `viewModel.leave()`. With saved notes and no pending work, it releases the active call immediately. Flow 11 §§7–9 requires the same leave-call confirmation used by hang-up.



---
**Answers (round 1, all fixed and tested; second round below):**
1. *A token answer could still join after Back or End:* fixed. The token request is kept and cancelled by Leave, Back
   and End, and a released call is never joined. Tested: Back while the token is on its way, nothing joins.
2. *The join turned the camera and microphone back on:* fixed. `VideoCall.join` takes the doctor's choices. Android
   joins with `Enable`/`Disable`; iOS sets the inputs, then joins. Tested: a microphone turned off before joining stays
   off.
3. *A refused Rejoin leaked a call:* fixed. The new call is made only once the token is there, and freeing doesn't
   depend on the stage (a `released` flag). Tested: a refused Rejoin makes no call.
4. *Permissions granted in Settings didn't count:* fixed. They are asked once on open and read again on every return
   to the app (read, not asked: the system's dialog pauses and resumes the app). Tested via the holder.
5. *A denied camera blocked audio-only calls:* fixed. Camera and microphone are tracked apart. Only the microphone is
   needed; without the camera, the call goes on with it off. Tested.
6. *A dropped call stayed "in call":* fixed. Daily leaving or failing in the call goes to "You left the call" with
   "Video call ended" or "Call error occurred", and Rejoin. Tested.
7. *Pre-join hid the leave sheet:* fixed. The pre-join page is drawn in the same flow as the workspace, so the sheets
   (unsaved work, leave call) still show over it.
8. *Back skipped the leave confirmation:* fixed. Back while joining or in the call asks "Leave call?", like the red
   button.
