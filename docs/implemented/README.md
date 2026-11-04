# What's implemented

Read this before starting a task. Update it at the end of every job, in the same commit: the status table, the file
for the area you touched, and one line in the log.

Ticket IDs and order: `docs/kmp-spec/17-master-plan.md` §4 (then `16-implementation-plan.md`). What the app needs from
the backend team: [../backend-improvements.md](../backend-improvements.md). Codex reviews of each ticket:
[codex/](codex/).

| Area | File |
|---|---|
| Project, CI, HTTP client, storage, session, demo backend, utils, native spikes | [foundation.md](foundation.md) |
| Design tokens and the UI kit | [design-system.md](design-system.md) |
| Strings and localization | [strings.md](strings.md) |

## Decisions

Recorded so they are not re-litigated. IDs are from `17-master-plan.md` §3.

| ID | Decision | Whose call |
|---|---|---|
| D-00 | **One branch, `doc-app2.0`, for the whole rewrite; no per-ticket branches** (overrides §3's default of short PR branches per ticket group). Created from `origin/main` at `078af42` after the docs PR (#21) was merged. | The user's call, 2026-11-04 |
| D-01 | Design source is `docs/design/` (the vendored Claude Design canvas); the primary fill stays `teal-50` with white labels. | The user's call, 2026-11-04 (§3 default) |
| D-02 | Clinic staff use the mobile app with the 4-tab shell (`01/18`). | The user's call, 2026-11-04 (§3 default) |
| D-03 | Gated entries stay hidden until the profile has loaded; gate on `subscription.features`, never fail open. | The user's call, 2026-11-04 (§3 default) |
| D-04 | No registration e-mail code dialog until BE-03 ships; `send-email-otp` wiring kept ready. | The user's call, 2026-11-04 (§3 default) |
| D-05 | PDFs: backend rendering requested (BE-01); client `expect/actual` fallback only for the appointment summary and 025-2. | The user's call, 2026-11-04 (§3 default) |
| D-06 | Maps: address fields + coordinates first, native map behind `expect/actual` later, no Google key in the app (BE-15). | The user's call, 2026-11-04 (§3 default) |
| D-07 | Not ported: waiting room, typing indicator, doc-access approve/reject in the inbox, phone OTP. | The user's call, 2026-11-04 (§3 default) |
| D-08 | Flutter web keeps deploying from `main` for now; a KMP web target is built **after M2** (the user asked for KMP web too). | The user's call, 2026-11-04 (confirmed mid-run) |
| D-09 | App-lock PIN 4–6 digits, user's choice, explicit submit, salted hash. | The user's call, 2026-11-04 (§3 default) |
| D-10 | Version `1.0.1`; build numbers start at 10 on both stores: `VERSION_CODE_OFFSET` 1 (next Play run 9 → 10), `BUILD_NUMBER_OFFSET` 4 (next TestFlight run 6 → 10), same workflow files (last Flutter uploads: Play run 8 → versionCode 9, TestFlight run 5 → build 6; public Actions API, 2026-11-04). The shared `iosApp.xcscheme` is committed so fastlane can build. | The user's call, 2026-11-04 (§3 default) |
| D-11 | Doctor delete-account UI built; the action goes to "contact support" until BE-02 ships. | The user's call, 2026-11-04 (§3 default) |
| D-12 | Video gated on `VIDEO_CONSULTATION` in `features`, hidden while unresolved. | The user's call, 2026-11-04 (§3 default) |
| D-13 | Push language and per-device tokens: inherit the patient app's asks (BE-05, BE-06); single token until then. | The user's call, 2026-11-04 (§3 default) |
| D-14 | Booking is slot-first only; "Book for this patient" opens the calendar with the patient pre-filled. | The user's call, 2026-11-04 (§3 default) |
| D-15 | Push (FCM token, PUSH-01…03) moves to the very end, together with a clean-up of the Firebase project; the debug build's token refusal is part of that. | The user's call, 2026-11-04 |
| D-16 | Daily video may be tested against the production backend (`POST /video/token`), as the patient app does. | The user's call, 2026-11-04 |
| D-17 | The uz/ru texts written for the port stay as they are for now (no native-speaker pass before release prep). | The user's call, 2026-11-04 |

## Status

| Ticket | Status | Notes |
|---|---|---|
| CORE-01 Project skeleton, CI | Done | KMP project (`composeApp`, `shifa.doctor`, `com.shifa.doc` / `uz.shifa.doc`), the patient app's Gradle catalog and ktlint, `iosApp` host, `CLAUDE.md` from `CLAUDE.kmp.md`, `ci.yml` + the two deploy workflows ported. CI has never run. See foundation.md. |
| CORE-02 Design tokens | Done | Every `--*` property of `shifa-ds.css` in `ui/theme/`; doctor text colours use the 70-steps. Token gallery = previews + screenshots. See design-system.md. |
| CORE-03 UI kit | Done | One composable per `.sh-*` block plus the AuCodebase doctor components, phone layouts; every …States board state has a preview and a screenshot (51). Icons generated from the design library. See design-system.md. |
| CORE-04 Strings | Done | 2218 keys per locale generated from `docs/kmp-spec/strings/` (all gaps + flow NEW keys), 16 plurals, `tr()` with runtime Uzbek Cyrillic, `LanguageStorage` (en/uz/uz-Cyrl/ru). Backend language sync moves to CORE-05. See foundation.md. |
| CORE-05 HTTP client | Done | Ktor client + `apiCall` with the 17 §1 session rule (401 with token or `ACCOUNT_DISABLED` ends it, 403 never), `code`/`requestId` errors, no server text in logs, SSE reader, dev-host switch, `TokenStorage` + `Jwt` (from CORE-06), language sync call. See foundation.md. |
| CORE-06 Storage, session | Done | `SessionRepository` (restore without network, sign-in, sign-out, ends on `SessionEvents`; logout clears only the token and last activity), `AppLockStorage` with a salted iterated PIN hash (bug 1), language sync wired to the session. See foundation.md. |
| CORE-07 Native spikes | Done (Daily skipped, FCM blocked) | AAC record + play, PDF view and map pin work on the emulator and the simulator (osmdroid / MapKit, no Google key). FCM token: debug build rejected ("FCM Registration failed!"), credentials. Daily: skipped, needs an appointment room token + SDKs. Outcomes in `CLAUDE.md` and foundation.md. |
| CORE-08 Demo backend, debug menu | Done (fake pushes deferred) | `DemoBackend` (MockEngine) answers every endpoint called so far from a scrubbed dev fixture; `auth/` and unknown GETs go to the real backend, other writes are refused. Debug menu: demo data, development backend, native spikes. Fake pushes come with PUSH-01/NOTIF. See foundation.md. |
| CORE-09 Utils | Done | `PracticeClock` (practice-zone today/now, DST-safe conversions, mismatch flag, zone-less timestamps as UTC), `Money` (UZS whole sums, grouped), `PhoneE164`, `PasswordPolicy` (the backend's patterns), document picker / share / in-app browser / file saver. `sanitizeErrorMessage` not ported (17 §1). See foundation.md. |
| AUTH-01 Splash | Done | Session restore + BE-04 boot check in parallel (0.8 s for a returning doctor), force-update and maintenance frames, top-level navigation. See auth.md. |
| AUTH-02 Sign In | Done | Shared `AuthFrame` (bar, banner, language pill, centred scrolling content); validation, password sent as typed, banners by error type, forced-logout reason banner. Signed in against dev on the emulator. See auth.md. |
| AUTH-03 E-mail code sign-in | Done | E-mail and code screens (one mode for sign-in and reset), 60 s resend per address, sixth digit submits, wrong code clears the field. See auth.md. |
| AUTH-04 Forgot password | Done | Reset code by e-mail (app=doctor), new password with the shared live checklist (backend policy), wrong/expired code banner and Back to the code. See auth.md. |
| AUTH-05 Invitation key | Done | Key trimmed, one neutral invalid-key banner, purpose picks the doctor or receptionist registration, kept in memory only. See auth.md. |
| AUTH-06 Create account (doctor) | Done (no e-mail code: D-04) | Step 1 form, duplicate-doctor check, existing-patient confirmation, step 2 with the practice timezone only (AUTH-07 adds the rest), `register` → shell. Invited address locked. Found BE-29 (register re-keys an existing doctor). See auth.md. |
| AUTH-07 Registration step 2 | Done | Date of birth (Material date picker), gender, address, clinic search, professions (multi), timezone; PATCHed once after `register`, failure leaves a shell notice. Pickers built here are the ones PROF-05/06 reuse. See auth.md. |
| AUTH-08 Receptionist registration | Done | One-step staff form on the registration screen: clinic card, invited address read-only (editable only when the invitation has none, C-4), names, timezone, passwords; `register-clinic-staff` → session → shell (SHELL-02 gives staff their shell). See auth.md. |
| AUTH-09 Sign out | Done (push token: D-15) | One sign-out question (`SignOutSheet` + `SignOutViewModel`) for every place that offers it, PIN line when app lock is on, one navigation through the session, "You've signed out" on Sign In. Offered in the shell stand-in until SHELL-01. See auth.md. |
| LOCK-01 App lock | Done | Lock window over every screen and sheet after a restored/new session and after ≥ 5 s in the background (monotonic clock), PIN 4–6 digits with "Unlock" (D-09), attempts left, fifth wrong PIN → sign-out with its banner and the PIN forgotten, biometrics (BiometricPrompt / LAContext) with auto-prompt, "Sign out instead", Back blocked, suppress hook. Settings section with set/repeat/off/change sheets (shell stand-in until SET-03). See auth.md. |
| LOCK-02 Inactivity timeout | Done | 30 days without a touch or an expired token end the session on a cold start (decided in the splash, before the shell), on coming back, and on a tap with an expired token; activity written at most every 5 min; Sign In explains it. **M1 complete.** See auth.md. |
| PROF-01 Profile models + repository | Done | Typed `/doctors/me` (profile, contact, billing, settings, subscription) and `/me/profile`; shared `profileRepository` with the four PATCHes updating it in place; cleared when the session ends. Checked against dev. See profile.md. |
| SHELL-01 Doctor shell | Done (tabs are placeholders) | Floating bar Home/Calendar/Patients/Chat/More, per-tab stacks kept when switching, re-tap → root, bar hidden on pushed screens, Back pop → Home → exit, More sheet (Intelligence, Clinic if a member, Tasks with its plan, Reports, Notifications, Profile, Language, Sign Out), start tab from the profile once with clamps. See shell.md. |
| SHELL-02 Clinic-staff shell | Done | Chat / Clinic / Notifications / Profile (their photo), starts on Clinic, no saved start tab, never calls `/doctors/me`, Back → Clinic → exit; Language and Sign Out in their Profile. See shell.md. |
| SHELL-03 Badges | Done | Chat unread polled every 10 s only while the app is in front, notifications unread on each return, active tasks once (PREMIUM); bar badges (staff: chat + notifications) and More counts, "99+" cap, screen-reader "Chat, 3 unread". See shell.md. |
| SHELL-04 Plan gating | Done | `FeatureGate` (backend `SubscriptionFeature` codes and tiers), gating on `subscription.features`, unknown allows nothing (D-03); the shell uses it; a plan change later moves a doctor off Tasks with a note. Also SUB-01's source. See shell.md. |
| SHELL-05 Intelligence mode, resume refresh | Done (AI surface: area 03) | Shifa Intelligence replaces the tab content with the bar kept (tab tap or Back leaves it, the tab underneath untouched, gated on `ASK_SHIFA_AI`); every resume re-reads the profile, clinics and notifications. See shell.md. |
| PROF-02 Profile root | Done (sections are placeholders) | Identity card ("Dr. med. First Last", professions in the app language, timezone and plan chips), completeness card with fix-it chips, ten section rows with live previews, Sign Out, version; staff read-only variant; skeleton, error with Retry. See profile.md. |
| PROF-03 Personal information | Done | Names, date of birth, gender, professions (shared picker), changed-keys-only save, discard question; photo from gallery or camera uploads at once. Remove photo waits for BE-30. See profile.md. |
| PROF-04 Contact, invoicing, password | Done | Contact & clinic address (phone, e-mail, editable address fields, timezone row), Subscription & billing (current plan, invoicing card), Security (app lock + change password). Address search and map are PROF-07; plans and checkout SUB-02. See profile.md. |
| PROF-05 Profession catalogue | Done | Catalogue, fallback and picker came with AUTH-07 / PROF-03; dental detection (with Implantologist) ported with Flutter's tests. See profile.md. |
| PROF-06 Timezone picker | Done | Shared timezone list (city, zone, offset; offset search), practice-timezone sheet over Profile and Contact with the change warning. See profile.md. |
| PROF-07 … SET-03 | Not started | |

## Log

- 2026-11-04 · CORE-01 · KMP skeleton next to the Flutter code on `doc-app2.0`; both platforms compile; CI ported.
- 2026-11-04 · CORE-02 · Tokens from `docs/design/` in `ui/theme/`, Manrope fonts, token gallery screenshots.
- 2026-11-04 · CORE-03 · UI kit in `ui/widgets/` from `shifa-ds.css` and the boards; 52 screenshots; icon generator.
- 2026-11-04 · CORE-04 · Strings generated from the JSON (gaps filled, plurals, CMP escaping corrected), Uzbek Cyrillic via `tr()`, language storage.
- 2026-11-04 · CORE-05 · HTTP client with the new session rule and code-based errors; verified against dev (doctor tokens last 30 days there).
- 2026-11-04 · CORE-06 · Session repository and the hashed app-lock PIN.
- 2026-11-04 · CORE-07 · Native spikes on emulator and simulator: audio, PDF view, map pin work; FCM blocked by credentials; Daily skipped.
- 2026-11-04 · CORE-08 · Demo backend from real dev fixtures, debug menu.
- 2026-11-04 · CORE-09 · Practice clock, money, phone and password rules, platform helpers. M0 complete.
- 2026-11-04 · AUTH-01 · Splash with session restore, the BE-04 boot gate and the app's top-level navigation.
- 2026-11-04 · AUTH-02 · Sign In on the shared auth frame; real sign-in against dev verified.
- 2026-11-04 · AUTH-03 · Sign in with an e-mail code; system Back on the auth screens (missed in AUTH-02).
- 2026-11-04 · AUTH-04 · Forgot password with the shared password checklist.
- 2026-11-04 · AUTH-05 · Invitation key, routing to the doctor or receptionist registration.
- 2026-11-04 · AUTH-06 · Doctor registration step 1, existing patient, timezone step; picker sheet and pick field in the kit.
- 2026-11-04 · AUTH-07 · Registration step 2: birth date, gender, address, clinic and profession pickers, one profile PATCH.
- 2026-11-04 · AUTH-08 · Receptionist registration on the same screen (`register-clinic-staff`).
- 2026-11-04 · AUTH-09 · Sign-out question and neutral "You've signed out" on Sign In.
- 2026-11-04 · LOCK-01 · App lock: lock window, biometrics, PIN settings section; 4–6 digit PIN with explicit Unlock (D-09).
- 2026-11-04 · LOCK-02 · Inactivity timeout (30 days) and expiry checks on start, resume and touch. M1 done.
- 2026-11-04 · PROF-01 · Typed profile models and the shared profile repository (no screen).
- 2026-11-04 · SHELL-01 · Doctor shell: bar, per-tab stacks, Back, More sheet, start tab; placeholders per tab.
- 2026-11-04 · SHELL-02 · Clinic-staff shell (D-02: staff on mobile).
- 2026-11-04 · SHELL-03 · Unread badges and active-task count.
- 2026-11-04 · SHELL-04 · Feature gating on the plan's features (D-03).
- 2026-11-04 · SHELL-05 · Intelligence mode in the shell and the resume refresh.
- 2026-11-04 · PROF-02 · Profile root (doctor and staff), section routes as placeholders.
- 2026-11-04 · PROF-03 · Personal information panel, photo upload, shared profession picker; fragment 1.8.9 fixes picker crash.
- 2026-11-04 · PROF-04 · Contact & clinic address, invoicing details, Security with password change; shared profile form pieces.
- 2026-11-04 · PROF-05 · Dental documentation detection and the pinned profession catalogue tests.
- 2026-11-04 · PROF-06 · Practice timezone sheet with change warning; shared timezone list for registration and profile.
