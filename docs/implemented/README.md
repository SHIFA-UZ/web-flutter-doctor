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
| AUTH-04 … LOCK-02 | Not started | |

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
