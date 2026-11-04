# Overnight run 2026-11-04: milestone M0 (CORE-01 … CORE-09)

Branch `doc-app2.0` (a worktree at `../web-flutter-doctor-app2`, created from `origin/main` after
`git pull --ff-only`). Nothing was pushed, and `main` was not touched. One commit per ticket, plus one release-config
commit you asked for mid-run. Every commit carries the repo's git identity only, with no attribution lines. Status per
ticket: `docs/implemented/README.md`. Detail: `foundation.md` and `design-system.md`. Codex output:
`docs/implemented/codex/<TICKET>[-r2].md`, with the answers appended to each file.

M0 is complete. All nine tickets are committed, and the branch compiles at every commit (Android `assembleDebug`, iOS
`linkDebugFrameworkIosSimulatorArm64`).

## Summary

| Ticket | Commit | Result | Codex |
|---|---|---|---|
| CORE-01 Skeleton, CI | `7225b00` | Done | 1 round: 3 findings, 2 fixed, 1 (scheme) was your rule and is now resolved by `764edb1` |
| CORE-02 Tokens | `6f65967` | Done | 1 round: no defects |
| (release config) | `764edb1` | Version 1.0.1, build numbers start at 10 on both stores, shared iOS scheme committed (your decisions) | not reviewed (configuration only) |
| CORE-03 UI kit | `84c5849` | Done | 2 rounds: 7 + 6 findings, all fixed |
| CORE-04 Strings | `84de8da` | Done | 1 round: 3 findings, all fixed |
| CORE-05 HTTP client | `0d43378` | Done | 2 rounds: 6 + 1; 6 fixed, 1 answered with backend evidence |
| CORE-06 Storage, session | `dc85e6d` | Done | 2 rounds: 3 + 2; 4 fixed, 1 answered with backend evidence |
| CORE-07 Native spikes | `8a9152a` | Done; Daily skipped, FCM blocked by credentials | 2 rounds: 7 + 4; 10 fixed, 1 declined (debug harness) |
| CORE-08 Demo backend, debug menu | `3a5583a` | Done; fake pushes deferred | 1 round: 3 findings, all fixed |
| CORE-09 Utils | `19d4620` | Done; `sanitizeErrorMessage` not ported (17 §1) | 1 round: 9 findings, all fixed |

Tests at the end of the run: the full `testDebugUnitTest` task is green, with 59 unit tests outside the screenshot suite, all passing. The CORE-03 kit
has 52 screenshots, plus 4 theme images from CORE-02.

## Per ticket

### CORE-01 · Project skeleton and CI (`7225b00`)
- **Built:**
  - The KMP project (`composeApp`, package `shifa.doctor`, app ids `com.shifa.doc` / `uz.shifa.doc`), with the patient app's catalog minus PostHog and Ktor logging.
  - The `iosApp` host.
  - `CLAUDE.md` from `CLAUDE.kmp.md`.
  - `ci.yml`, `google_play_deploy.yml` and `ios_testflight.yml` ported.
- **Tests:** `assembleDebug` and `linkDebugFrameworkIosSimulatorArm64` pass. The Xcode app built and launched on the iPhone 16 Pro simulator (iOS 18.6).
- **Codex:** USE_FINGERPRINT restored and the Play key added to `.gitignore`. The scheme finding waited for your call.

### CORE-02 · Design tokens (`6f65967`)
- **Built:** every `--*` property of `shifa-ds.css` in `ui/theme/`. The doctor app uses the 70-step text colours. Four gallery screenshots replace a token screen.
- **Tests:** `recordRoborazziDebug` for `ShifaThemeScreenshotTest`; both platforms build. Codex: no defects.

### Release configuration (`764edb1`, your answers of 2026-11-04)
- `MARKETING_VERSION` is 1.0.1.
- Play keeps offset 1: next run #9 gives versionCode 10.
- TestFlight goes from offset 1 to 4: next run #6 gives build 10.
- The run numbers were read from the public Actions API.
- `iosApp.xcscheme` is committed, with an Archive action; `CLAUDE.md` now says only Xcode's local rewrites stay out.
- A failed or re-run workflow still uses up a run number, so the first successful upload can land above 10.

### CORE-03 · UI kit (`84c5849`)
- **Built:** one composable per `.sh-*` block, plus the AuCodebase doctor components, in their phone layouts. Every state on the HeroStates, InsightStates, CopilotStates, TimelineStates, TableStates and DialogStates boards has a preview and a screenshot (52).
- **Icon generator:** `scripts/design_icons.js` writes the icons from the design library on demand.
- **Changed by looking at the screenshots:**
  - An empty input no longer shows the clear button.
  - The slot chip has no check icon.
  - The pager shows 3 pages so it fits a phone.
  - The month legend is tied to the cell tints.
  - The map preview clips to its card.
- **Not built:** web and tablet layouts (`.dr-week`, sidebar, table columns), per D-08.
- **Tests:** `recordRoborazziDebug --tests 'shifa.doctor.ui.widgets.*'` (52 images), `ktlintCheck`, both builds.
- **Codex round 1:** OTP field input, copilot scrolling, scrollable dialog body, 44 dp rating radios, calendar day labels, nav tab role, the phone-tray preview.
- **Codex round 2:** avatar label, option and toggle selected state, tier "not included" state, read-only rating label, icon badge click.

### CORE-04 · Strings (`84de8da`)
- **Built:**
  - **Generator:** `docs/kmp-spec/strings/{en,uz,ru}.json` plus `plurals.json` are the source; `scripts/strings_from_json.py` generates the CMP resources.
  - **Gaps filled:** 2218 keys per locale. That covers the 1 `en`, 10 `uz`, 92 `ru` and 41 nowhere-defined keys, plus 257 strings and 7 plurals of NEW keys from the flow files.
  - **Plurals:** 16 `<plurals>`, covering the Russian plural sites and the hand-made `{visitWord}` in `intelCancelPreview`.
  - **Uzbek Cyrillic:** a runtime `UzbekCyrillic` transliterator behind `tr()`, with golden tests.
  - **Language:** `LanguageStorage` (en / uz / uz-Cyrl / ru).
- **Corrections:**
  - CMP 1.9.3 doesn't unescape `\'`, so the strings README's escaping rule was wrong.
  - Placeholders are numbered in English order, because Uzbek reorders them.
  - `visitFactsCommittedWithTasks` was reworded, since a sentence with two counts can't take plural forms (flow 03/21 updated).
- **Translations:** the uz/ru texts for new keys were written for the port. A native speaker should check them.
- **Tests:** `StringsTest` (3), `UzbekCyrillicTest` (5), `LanguageStorageTest` (7), both builds, widget screenshots verified unchanged.
- **Codex:**
  - A saved language now wins over region detection (LANG-02's Fix line).
  - `ye` after a consonant becomes `ъе` (Объектив).
  - The Russian plural `one` form no longer uses a singular pronoun.

### CORE-05 · HTTP client (`0d43378`)
- **Built:**
  - The Ktor client, with 30 s timeouts on every verb.
  - The token goes only to the backend's `/api/` paths that need it, and never overrides a caller's header.
  - `apiCall` with your session rule: a 401 to a request that carried a token, or `ACCOUNT_DISABLED`, ends the session; a 403 never does.
  - Errors carry `code` and `requestId` and no server text.
  - Logs mask ids and never include server messages.
  - An SSE reader, the dev-host switch, `TokenStorage`, the `Jwt` reader, and `LanguageRepository` (`PUT /doctors/me/language`).
- **Verified on dev** as `seed.doctor1@shifa.dev`:
  - Login returns 200, and `GET doctors/me` returns 200.
  - A missing or invalid token gets 401 with `code: UNAUTHORIZED`. The plan expected `TOKEN_INVALID`; 15 §1 is corrected.
  - The language endpoint is in the OpenAPI spec (204).
  - **Doctor tokens live 30 days, not the 12 h in the workspace notes.**
- **Tests:** `ApiCallTest` (11), `SseTest` (3), `JwtTest` (3), `LanguageRepositoryTest` (2).
- **Codex:**
  - Fixed: token kept off file URLs, the expiry grace direction (`exp + 30 s < now`), malformed claims, SSE partial events, and races in the language sync.
  - `ACCOUNT_DISABLED` as a 403 ends the session on purpose: the backend only sends it from `JwtAuthFilter`, as a 403.
- `CLAUDE.md`'s old 401/403 paragraph is replaced.

### CORE-06 · Storage and session (`dc85e6d`)
- **Built:**
  - **`SessionRepository`:**
    - Restore works without the network, and only `DOCTOR` and `CLINIC_STAFF` tokens count.
    - `signIn` returns a `Result`.
    - Logout clears only the token and the last-activity time.
    - A network session end names its token, so a late 401 can't end a newer session.
    - Every change runs under one lock (atomicfu, added directly).
    - The HTTP client reads its token from the session.
  - **`AppLockStorage`** (the Fix line):
    - The PIN is stored as a salted, 10 000-round SHA-256 hash.
    - 4–6 digit PINs are verified at their full length.
    - The attempt counter is saved.
  - **Language sync** is wired to the session.
- **Tests:** `SessionRepositoryTest` (6), `AppLockStorageTest` (5), all CORE-05 tests, both builds.
- **Codex:**
  - Round 1 fixed: late-401 identity, the sign-in/end race, events lost at startup.
  - Round 2 fixed: errors escaping the session repository are now contained.
  - Round 2 answered: a tokenless `ACCOUNT_DISABLED` can't happen.

### CORE-07 · Native spikes (`8a9152a`)
Run on the Pixel 9 Pro emulator and the iPhone 16 Pro simulator (iOS 18.6).

| Spike | Result |
|---|---|
| AAC record / play | **Works on both:** 1 ch, 16 kHz, AAC, 32 kbps `.m4a`; playback advances |
| PDF view | **Works on both** (`PdfRenderer`, PDFKit). On-device PDF generation is not spiked (D-05: backend first) |
| Map pin | **Works on both, no Google key:** osmdroid with OSM tiles, and MapKit (D-06) |
| FCM token | **Blocked by credentials.** Firebase starts, but the debug build is refused ("FCM Registration failed!"); likely the API key only allows the release certificate, or the image needs a signed-in Play account |
| Daily | **Skipped:** needs a video appointment's room token and the SDKs (VIDEO-01) |

- The outcomes are written into `CLAUDE.md`.
- The iOS run uses a debug-only `-spikeAutoRun` launch argument, because there is no UI driver.
- **Codex** found, in the code copied from the patient app:
  - URL logging;
  - a player leak and a prepare/pause state bug;
  - PDF loading on the main thread, missing cleanup, a stale iOS document and closed-renderer reads;
  - an auto-run timer issue.
- All of those are fixed here. **The patient app still has the same code**: worth a follow-up in that repo.

### CORE-08 · Demo backend and debug menu (`3a5583a`)
- **Built:**
  - `DemoMode` and `DemoBackend` (MockEngine) answer `GET doctors/me` and `PUT doctors/me/language` from a fixture scrubbed out of the real dev response.
  - Only `auth/login` and unhandled GETs reach the real backend; every other write is refused.
  - A debug menu: demo data, development backend (signs out on switch), native spikes.
- **Skipped:** fake pushes; their payloads come with PUSH-01 and NOTIF-*.
- **Tests:** `DemoBackendTest` (3), both builds, the menu and Back checked on the emulator.
- **Codex:**
  - `auth/` writes (password reset) could reach production. Now refused.
  - The spike screen had no way back. Fixed with a Back header.
  - Stateless `DebugMenuScreenContent` split out.

### CORE-09 · Utils (`19d4620`)
- **Built:**
  - `PracticeClock`: practice-zone today and now, DST-safe conversions, the mismatch flag, a zone-missing flag, zone-less timestamps read as UTC, and `currentInstant()`.
  - `Money`: UZS in whole units, others to 2 decimals, grouped with a narrow no-break space, plus parsing.
  - `PhoneE164`, and `PasswordPolicy` with the backend's own patterns.
  - Document picker, text share, in-app browser and file saver from the patient app.
- **Skipped:**
  - `sanitizeErrorMessage`: 17 §1 forbids matching English text, and CORE-05 maps codes instead. The plan row is corrected.
  - The camera/gallery picker: DOCS-02.
- **Tests:** `PracticeClockTest` (4), `MoneyTest` (3), `PhoneE164Test` (3), `PasswordPolicyTest` (1), both builds.
- **Codex:** fixed exception logging in the pickers (no file names), the iPad share-sheet crash, money overflow and "." parsed as zero, junk phone input accepted, a picker stream leak, a saver reporting success without writing, and a padded zone id.

## M1 · Launch and auth (after your "continue with all rest tickets")

All eleven M1 tickets are committed, one commit each, each with a Codex review (findings and answers in
`docs/implemented/codex/`). Details per ticket in `docs/implemented/auth.md`.

| Ticket | Commit | What it is |
|---|---|---|
| AUTH-01 | `ee3c7a7` | Splash, session restore, BE-04 boot gate (force update / maintenance) |
| AUTH-02 | `6a4b9fe` | Sign In on the shared auth frame (`?app=doctor`), reason banners |
| AUTH-03 | `fa7bcaa` | Sign in with an e-mail code |
| AUTH-04 | `485d828` | Forgot password with the live password checklist |
| AUTH-05 | `ace20a0` | Invitation key; request ids in every error banner |
| AUTH-06 | `069d299` | Doctor registration, duplicate-doctor check, existing-patient branch, timezone |
| AUTH-07 | `93c7365` | Registration step 2: birth date, gender, address, clinic and profession pickers, one profile PATCH |
| AUTH-08 | `9d39d2e` | Receptionist registration (`register-clinic-staff`) |
| AUTH-09 | `c5318ea` | One shared sign-out question, "You've signed out" |
| LOCK-01 | `0c47979` | App lock: lock window, 4–6 digit PIN (D-09), biometrics, settings section |
| LOCK-02 | `2ae2edf` | 30-day inactivity and token-expiry sign-out |

Checked on the emulator: real sign-in against dev; the app lock (set, lock after 7 s away, not after 2 s, covers an
open sheet, cold start locked, unlock, turn off). Not tried: biometrics on a device with an enrolled finger, iOS on
the simulator beyond building the framework.

**For you to look at:**
1. **Security, production backend (BE-29, High):** `POST /auth/register` and `/auth/register-clinic-staff` set a new
   password on an *existing* account for anyone with an unused invitation key. A clinic inviter gets the key in the
   API response, so they can take over the invited person's account; with a key that has no `emailSentTo`, anyone
   with the key can take over any doctor or patient by e-mail. `check-existing-doctor` also matches e-mails
   case-sensitively while `register` doesn't. The app only guards honest users. Details and the proposed fix in
   `docs/backend-improvements.md` (BE-29). The backend repo was not touched.
2. **PIN length:** the design board fixes six digits; D-09 (your default) says 4–6 with an explicit submit. D-09 is
   built ("Unlock" button). Say if you prefer the board's fixed six.
3. **Lock screen on Android:** the system bars around it are dimmed (it is a dialog window so it can cover sheets;
   CMP 1.9 has no switch for that). Cosmetic.
4. **Registration e-mail code:** still waiting on BE-03 (D-04); the doctor and staff registrations go straight in.
5. **The shell is still a stand-in** (debug menu with sign-out and the app-lock section) until SHELL-01, next.

## M2 · Shell and profile (done)

One commit per ticket, each with a Codex review (`docs/implemented/codex/`). Details in `shell.md` and `profile.md`.
Your calls D-15 (push at the end), D-16 (Daily may use production) and D-17 (translations as they are) are recorded.

| Ticket | Commit | What it is |
|---|---|---|
| SHELL-01…05 | `2d44a11`, `cc35d13`, `b1adf8e`, `9ee6fc7`, `8486464` | Tab shell, staff shell, badges, plan gating, Intelligence mode, resume refresh |
| PROF-01, PROF-02 | `1434bb6`, `4b97c0d` | Profile data layer; Profile root for doctors and staff |
| PROF-03 | `30949f6` | Personal information, photo from gallery or camera |
| PROF-04 | `c0417b2` | Contact & clinic address, invoicing details, Security (password change + app lock) |
| PROF-05 | `f96faa2` | Dental documentation detection (Implantologist included) |
| PROF-06 | `657cfbf` | Practice timezone sheet with the change warning |
| PROF-07 | `aa384fd` | Clinic location on a map (coordinates saved; geocoding waits for BE-15) |
| PROF-08 | `0f514ad` | Extended profile: biography, socials, service tags, certificates |
| SVC-01 | `28c3b7b` | Services & pricing: grouped list, editor with prices per currency and location |
| SVC-02 | `469d5e9` | Service groups editor (rename, reorder, add, delete) |
| SUB-02 | `520ab11` | Plans with prices, Stripe checkout and Connect onboarding, plan re-read on return |
| SET-01 | `8c46320` | Settings: country, starting screen, 2FA / encrypted-documents flags (queued PATCH) |
| SET-02 | `9b30ba9` | Language screen |
| SET-03 | `9b84c0f` | App-lock preview on the Settings security row |

Checked on the emulator against dev (and put back afterwards): gender, postal code, billing name, a service tag, a
service (created and deleted), the language (ru, then en);
photo upload from the gallery and the camera; the camera refusal banner; the wrong-current-password path; the map;
the timezone warning (cancelled).

**For you to look at:**
1. **Picker crash fixed:** `androidx.biometric` 1.1.0 pulled `androidx.fragment` 1.2.5, which crashed every activity
   result launcher (gallery, camera, file). `fragment` 1.8.9 is now a direct dependency.
2. **Square crop before a photo upload** (flow 08/02): Android has no platform cropper, so it needs a library (uCrop)
   or a custom screen. Not built; say if you want it.
3. **Dev seed doctor (`seed.doctor1`) now has a profile photo** (from the upload test); there is no endpoint to remove
   it (BE-30).
4. **New backend items:** BE-30 (remove photo), BE-31 (a new photo needs a new URL; the app versions it meanwhile),
   BE-32 (a distinct code for a wrong current password).
5. **iOS:** the photo picker, camera, downsampling and the map tap compile and link; none ran on a simulator yet.
6. **Stripe on dev was not touched:** the checkout and Connect buttons create Stripe sessions, so they are covered by
   tests and the demo backend only. Dev has BASIC and PRO plans; PREMIUM has no plan (admins set it).
7. **D-08 (KMP web) needs your call before it can start:** the pinned libraries have no `wasmJs` build: Ktor 2.3.7
   (web arrives in Ktor 3), kotlinx-datetime 0.5.0, multiplatform-settings 1.1.1 and Kamel 0.9.5. A web target means
   upgrading those four (and, by the "same versions in both apps" rule, the patient app too), plus web versions of 24
   platform declarations in 17 files (secure storage, HTTP engine, pickers, PDF view, maps, audio, voice, biometrics,
   push…), several with no browser equivalent. I started M3 instead. Options: (a) upgrade both apps' libraries in one
   `chore/` change, then add `wasmJs`; (b) keep Flutter web until the mobile app ships, then decide; (c) a compile-only
   web target later. My recommendation is (b).
8. **A flaky test pattern:** "Dispatchers.Main is used concurrently with setting it" once, when a previous test class
   left a ViewModel coroutine running; it passes on rerun.

## Decisions

- **D-00:** one branch, `doc-app2.0`.
- **D-08 (web):** KMP web stays after M2, as in the plan; Flutter web keeps deploying. You asked about this mid-run, and I recommended keeping D-08, optionally adding a compile-only `wasmJs` target early. Waiting for your answer.
- **D-10:** version 1.0.1, build numbers start at 10 on both stores. Your call, made mid-run.
- **The rest:** the §3 defaults, recorded in `docs/implemented/README.md`.

## Open questions and follow-ups

1. **FCM:**
   - Does the Firebase Android API key restrict app certificates?
   - The doctor app's debug SHA-1 may need adding in the Firebase console, or push gets tested only with release builds. PUSH-01 needs this.
2. **Daily:** VIDEO-01 needs a dev video appointment to take a room token from. Check the seed data or add one to the backend's dev seed.
3. **Translations:** a native-speaker pass over the uz and ru texts for the about 300 new keys, especially medical terms in the visit flows.
4. **Patient app follow-up:** the player, recorder and PDF view defects listed under CORE-07 are still in that repo.
5. **Workspace notes:** the workspace `CLAUDE.md` says doctor tokens last 12 hours. The backend (and dev) say 30 days. Worth correcting there.
6. **Web target (D-08):** see above.

## Commands used per ticket
- `./gradlew ktlintFormat` then `ktlintCheck`.
- `:composeApp:testDebugUnitTest --tests '<ticket packages>'`.
- `recordRoborazziDebug --tests 'shifa.doctor.ui.widgets.*'` for CORE-03; `verifyRoborazziDebug --tests 'shifa.doctor.ui.*'` after the `tr()` switch in CORE-04.
- `:composeApp:assembleDebug` and `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- The full screenshot suite was not run.
