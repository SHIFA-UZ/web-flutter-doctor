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

## AUTH-05 · Verify the invitation key

Built 2026-11-04. Flow `01/06-verify-invite-key.md`; board `DaAuth.dc.html`, branch `verify`.

- **Screen** (`ui/register/VerifyKeyScreen.kt`): bar title "Verify" with Back and the language pill, the 120 logo, "Enter
  the invitation key from your e-mail." (flow target), the "One-time key" field with a Paste action (flow target), Next.
  The field has no autocorrect or capitalisation (keyboards "fix" keys, flow §8).
- **Behaviour:** the key is trimmed (keys pasted from e-mail carry spaces and line breaks); empty → "Please enter your
  one-time key." inline, no request. `POST /auth/verify-key {key}`. `valid = false` → one neutral banner "This key is
  invalid or has already been used." (typo and used key alike); other failures → "Request failed…", timeout, network.
  A valid key opens registration with an `Invite` (purpose, clinic, invited address) carried in the navigation state,
  **memory only** (Flutter also stored it in device prefs and never read it back): `CLINIC_RECEPTIONIST_INVITE` →
  receptionist form (AUTH-08), anything else → Create account (AUTH-06). Back from registration returns to Sign In.
- **Checked on dev:** an unknown key returns `200 {"valid":false,…}`; an empty one `400 VALIDATION` (never sent).
- **While checking** the field and Paste are read-only (flow §6), so the answer always belongs to the key shown.
- **Request id (all auth screens):** an error banner shows the failed call's `requestId` on a second line ("Request ID:
  …", `ShifaBanner(detail)`, 17 §1); Login, e-mail, code, reset and key screens carry it. Not in the design: the
  smallest addition in the banner's style.
- **Demo data:** `auth/verify-key` is answered locally: `DEMO-DOCTOR` → doctor registration, `DEMO-RECEPTION` →
  receptionist registration ("Demo Clinic", locked address); anything else is invalid.
- **Tests:** `VerifyKeyViewModelTest` (4), `VerifyKeyScreenshotTest` (3: empty, invalid, failed), `DemoBackendTest` (+1).

## AUTH-06 · Create account (doctor)

Built 2026-11-04. Flow `01/07-create-account-step1.md` (and `01/08` for the timezone); board `DaAuth.dc.html`, branches
`reg1` (form and its `existing` view) and `reg2` (timezone row only). D-04: no e-mail code dialog until BE-03.

- **Screen** (`ui/register/RegisterScreen.kt`, `RegisterViewModel.kt`): one screen with three steps, so Back from the
  confirmation or step 2 returns to the form with every field kept.
  - *Form:* "Create account", the "Key verified" chip (`ShifaTag` Primary with a check; the board's chip also has a
    teal outline), first and last name, e-mail, "Phone Number (Optional)", password with the live checklist, confirm,
    two progress dots (Flutter had three), Next. Top-aligned like the board (`justify: flex-start`).
  - *Existing patient (3b):* the patient's photo (brand fallback without one), the teal info card, the practice
    timezone, the two passwords (prefilled, editable), "Confirm registration". Centred.
  - *Step 2:* only the practice timezone with "Your schedule and bookings use this timezone." and "Create account";
    AUTH-07 adds date of birth, gender, address, clinic and professions above it.
- **Next:** inline errors per flow §9 (`newPasswordErrors`, shared with the reset screen: "Password is required", "…at
  least 8…", "…at most 128…", the first unmet rule, "Please confirm your password", "Passwords do not match"). The
  e-mail is trimmed and lower-cased (consistent with the e-mail sign-in, flow §12); the phone, when given, must
  normalise to E.164 (`PhoneE164`) and is sent that way; passwords are compared and sent exactly as typed.
  Then `POST /auth/check-existing-doctor`, once with the e-mail and once with the phone (given both, the backend
  checks the phone only and stops at a patient): 200 → "A doctor account with this email (phone number) already
  exists. Sign in instead." under that field (dev: 404 for an unknown address, 200 for `seed.doctor1`). This check
  **fails closed**: any failure shows a banner and stays, because `register` would replace an existing doctor's
  password (BE-29). Then `POST /auth/check-existing-patient`: found → 3b, otherwise step 2; offline shows the network
  banner, any other failure counts as "not found" (flow §8).
- **Invited address:** the backend refuses any other address when the invitation names one (`emailSentTo`), so that
  address is filled in and the field is read-only.
- **Timezone:** the device's IANA zone (`TimeZones.device()`; an offset-only or unreadable zone counts as not
  detected) is preselected. Not detected → the field is empty, red, "We could not detect your timezone. Please choose
  it." and registration waits for a choice (never a silent UTC). The picker (`ShifaPickerSheet` in a
  `ModalBottomSheet`) lists the detected zone first ("Detected on this device"), then the board's 19 zones; typing
  searches every zone the platform knows.
- **Register:** `POST /auth/register {firstName, lastName, email, password, key, phone?, timeZone}` → session → shell;
  the typed passwords are cleared after success. 409 → "This email or phone number belongs to another account…"; other
  failures → "Could not create the account…" (or the code's own text: disabled account, rate limit); all with the
  request id. The Flutter "no token → password login" fallback is not ported: `TokenResponse.token` is required.
- **Back:** confirmation / step 2 → form; a filled-in form asks "Discard registration?" (`ShifaDialogSheet`,
  destructive, "Keep editing"); Back on the question keeps editing; nothing moves while a request runs (also system
  Back, flow §8 target).
- **Not built:** "This is not me" (flow §8 needs a product decision; the backend merges anyway); the e-mail code
  dialog (D-04, BE-03).
- **Backend:** BE-29 recorded: `register` resets the password of an existing doctor account; the duplicate-doctor
  check only protects honest users.
- **Demo data:** the checks answer locally (`demo.doctor@shifa.dev` has a doctor account, `demo.patient@shifa.dev` is
  an existing patient); `register` is a write and is refused.
- **Kit:** `ShifaPickField` and `ShifaPickerSheet` (board picker), `OptionRow` with a `sub` line extracted from
  `ShifaOptionList`; `PasswordField` moved to `ui/auth`; `isEmailAddress` / `normalizedEmail` in `utils`.
- **Tests:** `RegisterViewModelTest` (7), `TimeZonesTest` (2), `DemoBackendTest` (+1), `RegisterScreenshotTest` (6:
  form, errors, existing patient, step 2, no timezone, picker).

## AUTH-07 · Registration step 2 (account information)

Built 2026-11-04. Flow `01/08-account-information-step2.md`; board `DaAuth.dc.html`, branch `reg2` and its picker
sheets (`clinic`, `prof` multi). Built on AUTH-06's screen (`ui/register/RegisterDetails.kt`).

- **Fields** (all optional except the timezone), title "Account Information": date of birth (pick field → Material
  `DatePickerDialog`, 1900 to today, opens 25 years back, shown `dd.MM.yyyy`, sent `YYYY-MM-DD`; the picker works in UTC
  midnights so the date never shifts), gender (`ShifaDropdown`: Not set / Male / Female / Other, sent in English),
  address (trimmed, empty omitted), clinic, professions (chips inside the field), practice timezone (AUTH-06).
- **Clinic sheet:** `GET /public/clinics` on open; under two characters the list is filtered here, from two the
  backend's `?search=` runs 300 ms after the last keystroke (flow §8 target); "Searching…", "Couldn't load. Retry",
  "No results"; "No clinic" clears a choice. Dev has 2 clinics.
- **Profession sheet:** `GET /public/professions` once (138 on dev and production, each with `english` and `uzbek`);
  when the backend can't be reached the copy bundled in `files/catalog/professions.json` (production's list of
  2026-11-04) is shown with "Couldn't load. Retry" above it, so a doctor can always pick and can retry; search matches English, Uzbek Latin and its Cyrillic form; several can be picked; "Clear all" and "Done". Labels: Uzbek (Latin, or transliterated for Ўзбекча) in uz,
  English otherwise (the catalogue has no Russian). The English label is what is stored (08 §4.1), joined with ", ".
- **Create account:** `register` (with the timezone), then **one** `PATCH /doctors/me/profile` with only the filled-in
  keys (no timezone again, flow §12); nothing filled in → no PATCH. Registration is final either way: a failed PATCH
  sets `ShellNotice.pending` = "Some profile details could not be saved. You can edit them in Profile.", shown as a
  closable warning banner at the top of the shell (`ShellNoticeBanner`; SHELL-01 moves it under its bar), cleared when
  the session ends. If the session ended meanwhile (401, disabled account) nothing opens: `App.kt` sends a shell
  without a session back to Sign In on every screen change. Nothing is kept for later logins (Flutter re-PATCHed on every login, flow §12).
- **Back** from step 2 returns to step 1 with step 2's entries kept (flow §2 target).
- **Not built:** dental detection and the `Implantologist` fix are PROF-05's (profile side); the board's checkbox column
  in the multi picker is a tick on the right like the single pickers (`OptionRow`).
- **Demo data:** six professions, three demo clinics (searchable), and the profile PATCH merges into the demo doctor.
- **Kit:** `ShifaPickField(chips)`, `ShifaPickerSheet(loadingText, onRetry)`.
- **Tests:** `RegisterViewModelTest` (+6: bundled fallback and Cyrillic search, a session-ending PATCH, PATCH body, no PATCH / failed PATCH note, clinic search with debounce and
  retry, professions), `DemoBackendTest` (+1), `RegisterScreenshotTest` (+3: clinic, clinic failed, professions; step 2
  re-recorded with all fields).

## AUTH-08 · Receptionist registration

Built 2026-11-04. Flow `01/10-receptionist-create-account.md`; board `DaAuth.dc.html`, branch `staffReg`.

- **Screen:** a fourth step of the registration screen (`RegisterStep.Staff`, `ui/register/RegisterStaff.kt`), which a
  `CLINIC_RECEPTIONIST_INVITE` key opens instead of the doctor form, so it shares names, passwords, the timezone, the
  banner and the discard question. Same transparent bar as every auth screen (flow §12: Flutter's differed). Top-aligned.
  - A canvas card with the clinic name and the invited address (only the lines that exist), the address read-only
    (labelled "Email", not Flutter's "Enter email address"), first and last name, the practice timezone with "Your own
    notifications use this timezone. Clinic times always show in the clinic's timezone.", password with the checklist,
    confirmation, "Create account".
  - No invited address (C-4 still open): the e-mail field is editable instead of Flutter's dead end. Because the
    backend would then re-key whoever owns a typed address (BE-29), the app first asks `check-existing-patient`
    (which matches any existing user, case-insensitively) and refuses an address that has an account: "An account with
    this email already exists. Ask the clinic to send the invitation to that address." Fails closed.
- **Submit:** names required, e-mail valid, the shared password rules, a timezone (detected at once; not detected →
  red field, must be chosen). `POST /auth/register-clinic-staff {firstName, lastName, email, password, key, timeZone}`
  (same body as `register`, no phone field on this form) → session (CLINIC_STAFF) → shell. Staff are allowed on mobile
  (the user's call); their reduced shell and Clinic start tab are SHELL-02. Once the account exists, a response without a
  usable token (missing, or not a staff/doctor token) shows "Your account was created but sign-in failed. Please sign in
  with your new password.", disables Create account and lets Back go straight to Sign In (flow §8–9; the same for the
  doctor's registration) instead of Flutter's silent dead end or a pointless retry.
- **Backend:** `register-clinic-staff` also sets the password of an existing account with that address (added to
  BE-29's note).
- **Demo data:** a write, so refused like `register` (try the form with `DEMO-RECEPTION`).
- **Tests:** `RegisterViewModelTest` (+4), `RegisterScreenshotTest` (+2: staff, staff without an address).

## AUTH-09 · Sign out

Built 2026-11-04. Flow `08/15-logout-confirmation.md` (and `01` §2.10); board `DaApp.dc.html`, the `signOutOpen`
question.

- **One question for every entry** (`ui/auth/SignOut.kt`): `SignOutViewModel` (`ask`, `cancel`, `confirm`; the PIN
  state comes from the new `data/applock/AppLockRepository`, which LOCK-01 grows) and
  `SignOutSheet` (a `ShifaDialogSheet` in a `ModalBottomSheet`, the DialogStates form on phones): the logout disc,
  "Sign out?", "Are you sure you want to sign out?" plus "Your PIN stays set on this device." when app lock has a PIN,
  "Sign Out" (destructive) and "Cancel". Cancel, a tap outside and Back cancel; a second confirm does nothing. The
  More sheet, Profile and Security (SHELL-01, PROF, SET) reuse it instead of Flutter's four copies.
- **Confirm:** `SessionRepository.signOut()` clears the token, last activity and the language-sync memory (the PIN
  and the language stay, flow §4) and records `SessionEndReason.SignedOut`; the shell's "no session → Sign In" effect
  is the single navigation, and every shell ViewModel goes with the shell's store. Sign In shows a neutral "You've
  signed out." (flow §5, NEW) in the design's calm teal banner (the design has no info banner). Works offline: nothing is sent.
- **Not built:** `PUT /doctors/me/fcm-token ""` waits for PUSH-01 and the Firebase cleanup (D-15). The extra lines
  for a running upload, recording or call (flow §3, §7) come with the features that own those (chat, documents,
  voice, video).
- **Where it is now:** the shell stand-in offers it (debug menu row "Sign Out" in debug builds, a link in release)
  until SHELL-01 builds the More sheet.
- **Tests:** `SignOutViewModelTest` (2), `LoginViewModelTest` (+1), `SessionRepositoryTest` (sign-out now records
  its reason), `SignOutScreenshotTest` (1).

## LOCK-01 · App lock

Built 2026-11-04. Flows `01/11-app-lock-set-pin.md`, `01/12-app-lock-enter-pin.md`; boards `DaApp.dc.html` (the lock
over the app, `showLock`) and `DaProfile.dc.html` (Security → App lock and its `pin` sheet).

- **PIN: 4–6 ASCII digits, the doctor's choice, submitted with "Unlock"** (D-09, the user's call). The board fixes six
  digits and checks on the sixth; D-09 wins, so the set sheet says "Set PIN" with "4 to 6 digits" under the field, the
  lock shows six dot slots and an "Unlock" button (enabled from four digits). Nothing is checked mid-typing (fixes
  Flutter's check at four digits, which made 5- and 6-digit PINs unusable). Digits from other scripts are dropped, as
  the keypad can only type 0–9.
- **State** (`data/applock/AppLockRepository.kt`, app-wide `appLockRepository`): PIN as the salted iterated hash
  (`AppLockStorage`, CORE-06), lock on/off, biometrics. Locks when a session starts (restored on a cold start, or a new
  sign-in on a phone where the lock is on: flow 01/12 §2) and when the app returns after ≥ 5 s in the background,
  measured on the monotonic clock (`App.kt` lifecycle ON_STOP / ON_START; a changed device clock can't skip it).
  `suppress(on)` is the temporary-disable hook for pickers and the camera (no user yet). Storage failures read as
  "no lock" (flow 01/12 §6).
- **Lock screen** (`ui/applock/LockScreen.kt`, DaApp): canvas background, the 64 logo, "Enter PIN", six dot slots
  (screen reader: "3 of 6 digits entered"), "Incorrect PIN. 4 attempts left." (live region, haptic), the kit's
  `PinPad` with the fingerprint key when biometrics are on, "Unlock", "Sign out instead" (the AUTH-09 question). It is
  its own full-screen dialog window, so it also covers any sheet or dialog that was open (they are windows too); not
  over the splash; what was on screen stays underneath; system Back does nothing. On Android the system bars around
  it are dimmed (CMP 1.9's common `DialogProperties` has no scrim switch; cosmetic).
  - A secure store or credential that can't be read counts as the fifth wrong PIN (sign-out, the flow's documented
    fallback), never a lock nobody can pass. Salt and hash are stored as one value, so a failed write can't leave a
    PIN nobody can enter. A PIN that can't be saved says "Couldn't save the PIN. Try again."; a lock that can't be
    removed says "Couldn't change the app lock. Try again." and stays on.
  - Wrong PINs count in the secure store, so a rotation or rebuild doesn't reset them (Flutter kept them in the
    widget). The fifth ends the session with `SessionEndReason.PinLockout` → Sign In says "Too many incorrect PIN
    attempts. Please sign in again." and the **PIN is forgotten**, so the next sign-in isn't locked by a PIN nobody
    knows (flow §8 target).
  - Biometrics (`utils/Biometrics`: `BiometricPrompt` strong or weak on Android, `MainActivity` is now a
    `FragmentActivity`; `LAContext` on iOS): the prompt opens by itself when the lock appears and again on every
    return while locked; the fingerprint key re-opens it; a cancel leaves the PIN pad.
- **Settings** (`ui/applock/AppLockSection.kt`, DaProfile Security → App lock): "App lock" switch ("Require PIN when
  reopening the app"); with the lock on, "Biometric unlock" (turning it on runs the system prompt once first, flow
  §5 target; no face or finger enrolled → the row says "No Face ID or fingerprint is set up on this phone." instead
  of hiding) and "Change PIN". The PIN sheet: "Set PIN" (4 to 6 digits) → "Repeat your PIN" → "Turn on"; a mismatch starts
  again with "PINs don't match. Start again."; turning off and changing ask "Enter your current PIN" first, and
  wrong PINs there count towards the same five. Lives in the shell stand-in (debug menu, release placeholder) until
  SET-03 puts it on the Security screen.
- **Not built:** "Or use Face ID" hint line under the dots (board); the shake animation (a haptic instead); clearing
  the PIN when a *different* user signs in (flow 01/11 §8 open question); the extra sign-out lines for running
  uploads/calls.
- **Checked on the emulator** (Pixel 9 Pro, dev session): set a PIN twice → lock on; Home for 7 s and back → the lock;
  Home for 2 s and back → no lock; with the Change PIN sheet open, 7 s away → the lock covers the sheet; a cold start
  with the lock on → locked right after the splash; 1234 + Unlock opens it; turning off with the PIN works. Biometrics not tried (no
  enrolled finger on the emulator: the row shows the "not set up" line).
- **Dependency:** `androidx.biometric:biometric` 1.1.0.
- **Tests:** `AppLockRepositoryTest` (5: session lock and the 4–6 ASCII rule, 5 s on a test clock and suppress,
  countdown and lockout, turning off, an unreadable store signs out), `AppLockViewModelsTest` (4: check only on Unlock from four digits, lockout ends the session, set twice and mismatch,
  off/change need the PIN), `AppLockScreenshotTest` (5).

## LOCK-02 · Inactivity timeout

Built 2026-11-04. Flow `01/13-inactivity-timeout.md`; `01` §7.2. No screen of its own (not in the design).

- **Where** (`data/session/SessionRepository.kt`): `touch()` on every press and release (not moves, scrolls or
  hovers: flow appendix) via `Modifier.trackActivity()` (`ui/widgets/Activity.kt`, fed by `LocalActivity`), which the
  app's root, the shared sheets (`ShifaDialogSheet`, `ShifaPickerSheet`) and the lock window each carry, since
  separate windows don't pass through the root. It writes to the store at most every 5 minutes
  (`shifa_doctor_last_activity_s`, epoch seconds; cleared when the session ends) and enforces an overdue deadline
  first, so a late tap can't erase it. `onForeground()` runs the checks each time the app comes back (before the app
  lock asks for a PIN) and, if the session stays, counts as activity; `enforce()` (checks only) also runs hourly
  while the app stays open (the flow's foreground timer).
- **Rules:** 30 days without a touch → the session ends with `SessionEndReason.Inactivity` → Sign In says "You were
  signed out after a long period of inactivity." (flow §3 target). A token past its expiry (30 s grace) ends it with
  `Expired` → "session expired" banner, on a cold start, on coming back, and on a tap (before any request). Nothing
  readable about the last activity means no timeout (flow §6: fail open).
- **Cold start:** decided in `restore()` during the splash, so the shell never shows for a frame (flow §5 target);
  opening the app counts as activity.
- **Not built:** the web's 1-hour limit (the Flutter web build keeps it).
- **Tests:** `SessionRepositoryTest` (+5: coming back counts and a late tap can't erase an overdue deadline, expired token on a cold start says why, 30 days on the next start, rare
  writes and the resume check, an expired token on a tap and fail-open), `LoginViewModelTest` (+1).

