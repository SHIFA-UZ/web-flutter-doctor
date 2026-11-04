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

## AUTH-02 · Sign In

Built 2026-11-04. Flow `01-launch-auth-shell/02-login.md`; board `DaAuth.dc.html`, default branch (Sign In).

- **`AuthFrame`** (`ui/auth/`) is the frame every sign-in screen shares: a fixed bar (Back only when there is somewhere
  to go: none on Sign In; an optional bar title; the language pill), the banner slot under it, then the content
  centred while it fits and scrolling otherwise (keyboard, 200 % text; flow §3 item 9). `AuthLogo` (80/120),
  `AuthTitle` (28 centred or 24 left). The language pill (EN / UZ / ЎЗ / RU, flow 01/15) reads `LocalLanguageSwitch`,
  which `App` provides; picking one is an explicit choice and re-renders at once.
- **`LoginViewModel`:** the identifier must not be blank (sent trimmed); the password needs 6+ characters after
  trimming but goes **as typed** (Flutter trimmed what it sent, flow §12). `POST /auth/login?app=doctor`: the backend then
  issues a DOCTOR or CLINIC_STAFF token or refuses with 403 ("This account isn't a doctor or clinic staff account.",
  new `notDoctorAccount`); without it a doctor whose main role is PATIENT got a patient token. While signing in, the other actions are disabled. Errors are banners
  (not snackbars), kept as string keys so a language switch re-renders them: 401/400 → "Incorrect email or
  password.", timeout → "Request timed out", no network → "Network error", anything else → the code-mapped text or
  "Something went wrong" (never server text or HTML). The reason the last session ended (expired, 401, disabled)
  greets the doctor once (flow 01/14). `forcePasswordReset` is read and ignored (only doctor-created patient accounts
  carry it).
- **Navigation:** each top-level screen has its own ViewModel store, cleared when it leaves (a finished sign-in can't
  resurface after a forced logout); the secondary auth screens handle system Back. Sign In → the shell for both roles (the staff shell comes with SHELL-02); "Sign in with Email",
  "Forgot Password?" and "Create account" open a placeholder until AUTH-03…05. Debug builds have a "Debug menu" link.
- **Verified on the emulator against dev:** debug menu → Development backend, then `seed.doctor1@shifa.dev` /
  `Dev12345!` signs in and reaches the shell placeholder.
- **Kit changes:** `ShifaBanner` got an optional close button (`onClose`, the board's Dismiss); `ShifaInputField` got
  `fieldModifier` (focus requesters, later autofill). New strings `showPassword`, `hidePassword` (en/uz/ru).
- **Not built:** autofill content types (flow §12; needs a check that the content-type semantics work on iOS),
  `POST /auth/check-identifier` for an identifier hint (17 §4 optional), the inactivity and PIN-lockout banners (their
  reasons come with LOCK-01/02).
- **Tests:** `LoginViewModelTest` (5), `LoginScreenshotTest` (5: empty, errors, loading, failed, session expired).

## AUTH-03 · Sign in with an e-mail code

Built 2026-11-04. Flows `01/03-email-input.md`, `01/04-email-otp-code.md`; board `DaAuth.dc.html`, branches `email`
and `otp`.

- **Screens** (`ui/emailcode/`): `EmailEntryScreen` (title 24 left, the "Enter your email address…" line, the Email field,
  Continue) and `CodeScreen` (Enter OTP, "A 6-digit code has been sent to {email}", the Code field, the resend link,
  Verify), both on `AuthFrame` with Back and the language pill (flow 01/03 §3 asks for the pill everywhere). One
  `EmailCodeMode` (SignIn / Reset) drives both, so AUTH-04 reuses them.
- **E-mail:** required and checked against a full pattern (new copy "Please enter a valid email address") before a
  request. `POST /auth/send-login-otp {email}` (sign in) or `/auth/send-forgot-password-otp {identifier, app:"doctor"}`
  (reset). The address is trimmed and lower-cased first. A code sent less than a minute ago is still used: going back and
  pressing Continue again doesn't send a second e-mail (`OtpCooldown`, per address **and purpose**, in memory). Back
  from the code screen keeps the address.
- **Code:** digits only, 6; the sixth digit submits (flow target). Sign-in mode: `POST /auth/verify-email-otp
  {email, code}` → token → session → shell. A rejected code (400/401/404) shows "Invalid or expired OTP." and clears the
  field; other failures "Verification failed…" or the code-mapped text (429 → too many requests). Reset mode sends
  nothing and hands the code to the new-password step (flow §5 step 5).
- **Resend:** "Resend code in 42 s" while the 60 s cooldown runs (disabled), then "Resend code"; success shows the
  "Code resent." banner and restarts the wait.
- **Navigation:** the app's screens became a sealed type carrying data (address, mode); a completion only navigates
  while its screen is still current (`go(from, to)`), so a late reply can't undo Back. The code field is disabled
  during a request. System Back: code → e-mail →
  Sign In. **Correction:** the system-Back handlers reported fixed in AUTH-02 (`6a4b9fe`) had not applied (only the
  import landed); they are in this commit, for every secondary auth screen and the debug menu.
- **Not checked end to end against dev:** the seed accounts have no mailbox, so a real code can't be read; the flow is
  covered by `EmailCodeViewModelTest` against the backend's request and error shapes.
- **Deviation:** the envelope sits at the field's end (the kit field has a trailing slot only), not as a prefix.
- **Tests:** `EmailCodeViewModelTest` (5), `EmailCodeScreenshotTest` (5).

## AUTH-04 · Forgot password

Built 2026-11-04. Flows `01/03`, `01/04` (reset mode), `01/05-reset-password.md`; board `DaAuth.dc.html`, branches
`email`, `otp` (mode reset) and `reset`.

- **Path:** "Forgot Password?" → e-mail (title "Reset Password") → `POST /auth/send-forgot-password-otp {identifier,
  app:"doctor"}` → code screen (no request: the code is only checked with the new password, flow 01/04 §5) → new
  password → `POST /auth/forgot-password-reset {email, emailOtp, newPassword, app:"doctor"}` → token → session → shell.
- **New password** (`ResetPasswordScreen`): "Choose a new password for {email}." (board subtitle, new string), New
  Password with the live **`PasswordChecklist`** (`ui/auth/`, shared with registration: the backend's own rules from
  `PasswordPolicy`, ticked as typed, a polite live region; "At most 128 characters" past 128), Confirm password, each
  with its own eye toggle; Submit. Errors are inline only: the first unmet rule under the password, "Please confirm
  your password" / "Passwords do not match." under the confirmation (Flutter also showed a snackbar). Passwords go as
  typed.
- **A wrong or expired code** (401) is found only here: banner "The code is wrong or has expired. Request a new
  code."; Back returns to the code screen, where Resend works after its cooldown. A 400 is the backend refusing the password
  (its policy): "Could not reset the password…"; also timeout, network, code-mapped text. The fields are disabled
  while submitting; each checklist rule says "met" / "not met" to a screen reader.
- **Not built:** the success notice in the shell ("Password updated successfully", flow §5 step 5) comes with SHELL-01;
  the "if you already changed it, sign in with the new password" hint for a lost reply (flow §8) is not separate copy
  yet. Autofill `newPassword` hints wait for the autofill check (AUTH-02).
- **Tests:** `ResetPasswordViewModelTest` (4), `ResetPasswordScreenshotTest` (3), plus the reset cases in
  `EmailCodeViewModelTest`.
