# Launch, auth and app lock (M1)

Branch `doc-app2.0`. Each section names the flow file (behaviour) and the board and screen branch (looks).

## AUTH-01 · Splash and session restore

Built 2026-11-04. Flow `docs/ux-flows/01-launch-auth-shell/01-splash.md`; board `DaApp.dc.html`, branches `showSplash`
(teal splash) and `showBoot` (force-update / maintenance frame).

- **`SplashViewModel`** runs the session restore (local, 01 §2.9) and the boot check in parallel with the animation;
  a returning doctor leaves after 0.8 s instead of Flutter's fixed 1.8 s (flow §5 step 2, §12), sooner with a tap (§7). `Finished(session)`
  goes to the shell, `Finished(null)` to sign-in.
- **Boot check** (`data/config/PublicConfigRepository`, `GET /public/config`, no token): reads BE-04's `doctorApp` block
  leniently. Maintenance (`active`) wins and shows the backend's message in the app language, else the app's copy;
  an older build than `minVersion.<platform>` shows "Update required" and opens `storeUrl`. No block, a failure or no
  answer within 5 s (Ktor request timeout) means no gate, so launch is never blocked (flow §6). Dev today has no block
  (`{googleMapsApiKey, transcriptionFeedbackEnabled}`); `googleMapsApiKey` is ignored (D-06). The version check ignores
  a `-debug` suffix.
- **Frames:** `SplashScreenContent(state)`: teal splash with two drifting blurred blobs, the glass "S" tile and
  "Shifa Doctor" (one screen-reader label, "Shifa Doctor, Loading..."); the boot frame with the solid "S" tile, title,
  body and one button ("Update", or "Retry" with a busy state). Neither frame has a way past it. "Update" re-checks on
  return. Screenshots: `Splash_{loading,update,maintenance,maintenance_retrying}.png`.
- **Top-level navigation** (`App.kt`): Splash → SignIn or Shell; a session that ends anywhere (sign-out, 401, expiry,
  disabled account) sends Shell back to SignIn in one place. SignIn and Shell are placeholders until AUTH-02 and
  SHELL-01; debug builds show the debug menu there. Everything restarts from the splash when the debug menu switches
  demo data or the backend (`ScopedViewModels` drops the ViewModels).
- **Debug menu → Splash gate** (demo data on) makes the demo `public/config` answer with an update or maintenance
  block, so both frames can be tried (BE-04 "App until then").
- **Not built:** the soft "update available" banner (`latestVersion`, flow §13 open question), app lock over the shell
  (LOCK-01), the deep-link parking (CAL-9/PUSH).
- **Tests:** `PublicConfigRepositoryTest` (5), `SplashViewModelTest` (6), `DemoBackendTest` (+1),
  `SplashScreenshotTest` (4).
