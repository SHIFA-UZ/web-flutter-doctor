# Overnight run 2026-11-04: milestone M0 (CORE-01 … CORE-09)

Branch `doc-app2.0` (a worktree at `../web-kmp-doctor-app`, created from `origin/main` after
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

## M3 · Calendar & schedule (done on the phone; CAL-5, CAL-9, CAL-10 open)

| Ticket | Commit | What it is |
|---|---|---|
| TZ-1, TZ-2 | `925cce4` | Shared date / time formats; DST tests for the practice clock (the list came with PROF-06) |
| CAL-1 | `8080c44` | Calendar entries and the cached day repository (atomic cache, 60 s throttle, month batch) |
| CAL-2 | `5977cb1` | Today / next-appointment selectors and one refresh + event per appointment change |
| CAL-3 | `3198023` | Month picker with the board's occupancy shading |
| CAL-4 | `f4c5954` | Calendar tab: day agenda, filters, timezone-mismatch banner (checked on the emulator against dev) |
| CAL-6 | `b1c4db1` | Appointment panel: reschedule (month + free slots where the visit fits), adjust duration, payment reminder, main button by time window, cancel |
| CAL-7 | `aca31ee` | Booking a free slot: patient picker, clinic or video, multi-slot range on instants (DST-safe), reason; BE-33 |
| CAL-8 | `cfa82fc` | Block time (day / hours / several days, debounced overlap count) and the blocked-row panel with Remove block |
| LOC-1 | `d05c88d` | Profile → Locations: list, editor, delete; one shared location list; BE-34 |
| SCHED-1 | `3fd0578` | Schedule data: weekly template per location, periods, date-specific hours, the backend's checks first |
| SCHED-2 | `b12c2fa` | Working hours: week overview, a sheet per day, the bookable period, extra hours on dates |
| LOC-2 | `6adb114` | Location map pin with the shared picker (geocoding waits for BE-15) |

Checked against dev on the emulator, dev left as it was:
- CAL-6/7: a one-hour booking, moved and then cancelled.
- CAL-8: an 11:00–17:00 block, created and then removed.
- LOC-1: a location, added and then deleted.
- SCHED-2 and LOC-2: the working hours and the map pin, read only.

The M3 "done when" (set up a week, book an hour over two slots, move it, block an afternoon, all in Asia/Tashkent while
the phone is on Berlin time) holds on the phone.

Not built yet, on purpose:
- **CAL-9** (push and quick-book intents): its only callers are push (D-15, at the end) and Home (M6). It lands with
  them rather than as unused code.
- **CAL-10** (staff calendars): needs the clinic workspace members (CLINIC-05, M11).
- **CAL-5** (the tablet N-day grid): the master plan leaves it to you: build it now, or in a later tablet pass. The
  phone layout doesn't need it, so I went on to M4. **Your call.**

Shared fixes made on the way:
- ktlint failed on lines from earlier tickets; they are wrapped now.
- The "Dispatchers.Main is used concurrently" test flake: tests now stop their ViewModels before Main is reset.
- Dropdowns scroll to their value.
- Sheets open at full height.
- The calendar refresh reads four days at a time.
- Profile actions are passed by name: a positional list had sent Reports to the new Locations row.

## M4 · Patients, documents, forms (done on Android; iOS compiled and linked, not run)

| Ticket | Commit | What it is |
|---|---|---|
| PAT-01 | `8a37f93` | Patient models: display id, age on the practice date, status, phones, location; lenient reads |
| PAT-02 | `08b7b65` | Patients repository: one directory flow (60 s TTL), one patient, create, update, account, test SMS, prophylaxis |
| PAT-03 | `54c1638` | Patients tab: search (name, id, phone digits), All / Favorites / Follow-ups, status filter, pages |
| PAT-04 | `4c735eb` | Create patient sheet (directory and booking): E.164 phones, birth date, language as codes |
| PAT-05 | `f4d45d4` | Patient detail: header, stats, pinned record tabs, clinical summary, patient-app login shown once, SMS reminders |
| PAT-06 | `237f751` | Medical info: per-field saves with rollback, localised chronic diseases, Edit details (the create sheet, PATCH of changes) |
| PAT-07 | `4bd57cd` | Appointments and Activity tabs on the practice clock; New appointment → free-slot sheet → CAL-7 booking |
| DOCS-01 | `62b002a` | Document models (27 categories) and repository: list, download typed by bytes, request access |
| DOCS-02 | `2156b69` | Photos to one PDF, in shared code: 48 KB for two camera photos (Android's PdfDocument made MBs a page) |
| DOCS-05 | `806123e` | Viewer: zoomable image or PDF, "can't preview" with Save, 403 / 404 / Retry; frees the file on leaving |
| DOCS-04 | `3736db6` | Documents tab: search, cards (lock, chip, shared mark, byline), open, request access |
| DOCS-03 | `1f44453` | Upload: PDF, photo, gallery, multi-page scan → title and required type → upload with progress, Cancel, Retry |
| FORM-01 | `843e004` | 025-2 form models and the dental-chart codec (ported, with tests Flutter never had) |
| FORM-02 | `51610cb` | Forms repository: cached list, save, signature request, PDF link, file replacement, ICD-10 search |
| FORM-03 | `2e5c600` | 025-2 PDF made in the app: one shared layout, platform text drawing (Cyrillic renders), landscape A4 |
| CLIN-01 | `f18d9d5` | Clinical engine API: groups, diseases, chips (one bad chip no longer drops a disease), synthesize, usage |
| CLIN-02 | `3689dbb` | Clinical engine panel in the form: group → disease → chips with values, search, explicit Apply |
| FORM-04 | `5ab03ad` | 025-2 editor: prefill and carried-over chart, ICD-10, engine, chart editor, return visits, signature; one save pipeline that resumes at the failed step |
| FORM-05 | last M4 commit | Forms tab with signature / PDF badges, template sheet, Edit form on a form's PDF |

Checked against dev on the emulator, dev left as it was: the patients list, a patient's record (last visit, tabs,
Medical info, Edit details opened), the favourite star set and cleared, booking from the record up to the booking
screen (nothing booked), the Documents tab and dev's real PDF in the viewer. Clearing a field and the phone rules were
checked with direct requests and put back. Writes that can't be undone on dev (create patient, upload, request
access) were checked in demo mode only.

The milestone's check, on the Android emulator: create a patient, upload a scanned multi-page document and open it
(DOCS-03/05, demo mode), and fill a 025-2 from the Forms tab, save it, then see its PDF in the documents list with
Edit form, and open it (FORM-05, demo mode). The signature request is covered by unit tests, not tapped on the
device. **iOS:** every ticket compiles and links for the simulator, but nothing of M4 ran on iOS yet; the PDF writer's
iOS side (`UIGraphicsPDFRenderer`) is the part most worth a run.

Your calls / open points from M4:
- **Patient language is stored as codes** (uz, ru, en…; Flutter stored words). The detail reads both. Confirm.
- **Prophylaxis card** moved from PAT-06 to CLINIC-06: only the clinic workspace opens a patient with a clinic id.
- **Tablet two-pane** for patients: the same open call as CAL-5.
- **025-2 PDF (FORM-03):** built in the app (platform text drawing, one shared layout); BE-01 (backend rendering) is
  still the better long-term answer.
- **Clinical engine:** its language codes and whether it stays open to every plan (the board says PRO) are in the
  README's decisions to confirm. Apply is explicit, as the board shows.
- **025-2 chart:** history rows now remember which teeth they were written for (a key of ours, `…_HIST_{i}_dentition`,
  which Flutter's reader skips), so switching adult / child teeth no longer puts old findings under other teeth.
- **Template sheet** kept with one template, as the board shows (the flow left skipping it open).
- **Dev facts:** a locked document's download answers 404, not 403; the backend sets `phone` from `phones`; an empty
  string clears a patient field.

## M5 · Appointments, scribe, dental, video (done)

| Ticket | Commit | What it is |
|---|---|---|
| APPT-01 | `5ba04d0` | The doctor's appointment detail as the server really sends it (patient, signature, plan; no video flag, BE-09 corrected), the visit route from Start / Continue, no Start for a no-show |
| APPT-02 | `b5d0490` | Detail, signature request, and a read every 10 s only while the signature is awaited |
| AI-11 | `4870a26` | Consultation notes, AI drafts, complete, scribe status (a failed read is a failure, not Flutter's empty list) |
| APPT-03 | `eee8959` | The visit's notes saved 2 s after the last edit and on leaving (Flutter: only on leave), saves in order |
| APPT-04 | `ebce636` | The visit workspace: header and visit clock, patient alerts, notes with modes, Shifa AI and last-025-2 helpers, documents with upload, signature footer; leaving saves first |
| APPT-06 | `2353feb` | The visit briefing sheet from the appointment details |
| AI-03 | `0df16b4` | The recorder's level, recording id and duration |
| AI-05 | `6a90d23` | Speech to text with the app's language; typed 403 / 429 / 502 |
| AI-06 | `6bc9065` | Dictation in the visit notes and the 025-2 form's note fields (PRO) |
| SCRIBE-01 | `5898e35` | Recording the consultation for the AI scribe; keeps recording in the background on Android (a microphone foreground service); a failed upload is kept to send again |
| PDF-01 | `8854274` | The visit summary PDF made in the app (en, uz, ru) until the backend renders it (BE-01) |
| APPT-07 + SCRIBE-02 | `aea1c0c` | End appointment: checklist, then notes → complete → wait for the AI notes (Continue without waiting) → summary filed under the patient; after completion only the summary is retried |
| APPT-08 | `cca43a4` | Apply on an AI ICD suggestion carries the code into a new 025-2 |
| DENTAL-01 | `816e0a3` | The FDI tooth map and the dental record codec (legacy keys, totals per currency) |
| DENTAL-02 | `8727e58` | The dental visit record: services per tooth and general, discount, notes, totals per currency, 1200 ms autosave; End reads and saves it first, the summary lists it by tooth |
| APPT-05 | `7f3d2e0` | Before/after treatment photos: camera or gallery, chips, kept until End (leaving asks), a page each in the summary |
| TP-01 | `abc90d8` | Treatment plan data the visit needs (plans, detail with teeth, candidates, fulfil); a demo plan |
| DENTAL-03 + TP-06 | `0e275e3` | The plan in the visit: picker, plan-mode chart and ticks, Plan finances; End applies ticks, then records the payment (never twice, lost answers checked in the ledger), then completes; the summary's plan section |
| DENTAL-04 | `642cedc` | The dental plan editor (the visit's record UI and draft reused) and plan lines with the discount spread per currency; read-only plan chart (hosts in M11) |
| VIDEO-01 | `e210617` | The Daily room token and the call client on both platforms (the patient app's, copied; the iOS app builds with Daily's Swift package) |
| VIDEO-02 + VIDEO-03 | `3ef8cfe` | The video visit: pre-join page, the call on top of the workspace, leave/rejoin, End leaves the call first; no waiting room (D-07) |
| PDF-02 | `8992700` | Summary PDF content tests (general, dental, plan, photos, Russian) and a golden of the full layout |
| Follow-up (05/14) | `0e650c6` | Book follow-up from the ended visit: the slot picker, then booking with the patient pre-filled |

Checked on the Android emulator in demo mode: start a visit from the calendar, type and dictate notes (the emulator's
microphone records silence; the demo answers with a fixed sentence), ask for the signature (signed 15 s later), record
for the scribe and see its draft under From Shifa AI, record with the app in the background, end the visit and open
the filed summary; as a dentist (the demo doctor's profession switched in the profile), add services to a tooth,
discount and notes, leave and reopen (all read back), end the visit and see the dental section in the summary. Nothing
in M5 has run on iOS yet (everything compiles and links).

Notes from M5:
- **One commit (`b5d0490`, APPT-02) has a lint-only issue** (an orphaned comment) fixed in the next commit; the branch
  head is clean. My lint check filtered for "error", which ktlint's style failures don't print; fixed since.
- **Lint-only issue in `8727e58` (DENTAL-02):** one line over 120 characters, from the last review fix. It compiles;
  the next commit (APPT-05) wraps it. My check looked for "FAILED", but Gradle prints "FAILURE"; the check now matches
  both.
- **Lint-only issue in `abc90d8` (TP-01):** a demo line over 120 characters. I ran the commit in the same command as
  the check instead of after reading its result. It compiles; fixed in the next commit. Commits now come only after a
  separate, read check.
- **A real bug caught by a test:** a retry ran the upload inside a state update, which can run twice; it uploaded the
  recording twice. Fixed before commit (SCRIBE-01).
- **Backend facts:** the doctor's appointment detail has no video flag (only the patient side has it); notes are one
  manual note per visit, updated, and blank text is ignored, so notes can't be emptied; a second scribe recording for a
  visit is accepted and ignored.
- **M5 is done.** Not built inside it, by decision or for later: the waiting room (D-07), the visit facts AI panel,
  the follow-up's quick picks and flag-only mode, a "Record now" plan payment, extra lines outside a plan, and the
  plan wizard and plan detail that host DENTAL-04 (M11).
- **Video not run live:** a real call needs a dev video visit inside its window (minting a token creates a Daily room,
  which can't be undone there). Everything builds on both platforms, including the whole iOS app with Daily.
- **Backend:** dev has no treatment plans, so plan ticks and payments ran in demo mode only. A plan payment has no
  idempotency key (BE-13); the app looks for a lost payment in the ledger before sending it again.

## M6 · Home & analytics (done)

| Ticket | Commit | What it is |
|---|---|---|
| HOME-01 | `8de3aaa` | Home's schedule and hero from the calendar cache: the visit in its slot, else the next, else the next 14 days |
| HOME-02 | `069ebdd` | The Home tab: header (photo, Refresh, Search, bell), practice-time note, "Today at a glance"; nothing read before the practice zone; skeletons, never a false 0 |
| HOME-03 | `d6da7da` | The next patient (Start 15 min before, 5 for video; Continue; Open chart; age), empty / done / no-timezone cards, New appointment and Video Call (first free slot booked as video), today's timeline opening the Calendar panels; the visit screen now waits up to 3 s for the patient so the chronic warning always comes first |
| HOME-05 | `abb9005` | Patient search from Home's header (Flutter: keyboard shortcut only): the Patients tab's matcher, a result opens in the Patients tab |
| HOME-04 | `90fb903` | The attention centre (unread with Mark as read, then follow-ups) and the activity feed, after the M8/M9 data they needed; targets through the inbox's shared opener |
| ANALYTICS-01 | `14570e1` | The five analytics reads, the range (last 7 practice days, trend 1–90), one refresh entry point; demo answers |
| ANALYTICS-02 | `62ca057` | "Clinic performance": range picker with presets, CSV export through the share sheet, today's KPIs, PREMIUM engagement or a teaser; each block loads and fails on its own |
| ANALYTICS-03 | `5686100` | PREMIUM trend line and visit-type ring (Canvas), titled by the days drawn, tap a day for its visits, large-text safe |

Every ticket had two Codex rounds; all findings were fixed. Checked on the Android emulator against dev (the seed
doctor is PREMIUM and has no appointments for 30 days, only free slots: the hero and timeline were checked in demo
mode) and offline (the offline start shows "—" and Retry). iOS: everything compiles and links, nothing run.

Not built in M6:
- The "Prescription" quick action (opens the task form, TASK tickets; its label and destination disagree, flow 02/02
  §13) and the board's "Medical record" flags on the hero (not in the spec).
- SMS usage and one range shared with Reports: with REPORT-01.

## M7 · Chat (done)

| Ticket | Commit | What it is |
|---|---|---|
| CHAT-01 | `d1dc860` | Chat models and repository (conversations, thread, start, read, send, search, upload), every attachment URL absolute, an in-memory demo chat |
| CHAT-02 | `e408ac8` | The Chat tab's list: cards with unread pills, newest/oldest and unread-only filter, people search that opens or starts a conversation; read every 5 s only while in front |
| CHAT-03 | `8287ff0` | The thread: practice-day dividers, board bubbles, ticks, links; optimistic send in order with "Not sent. Tap to retry"; read on open and on each new incoming; "New message" pill |
| CHAT-04 | `5ec94c8` | Photos: attach sheet, camera or library, upload then send (a retry only re-sends), tiles, a full-screen viewer with 0.5–4× zoom |
| CHAT-05 | `1200620` | Documents: pdf, doc, docx (jpg/png go as photos), document cards opened outside the app, "File unavailable" without a URL |
| CHAT-06 | `a1ddde8` | Voice: the shared recorder pill with a counter, < 1 s refused, upload and send with the length; going to the background holds the clip for Send or Discard; one note plays at a time; a failed stream says so |
| CHAT-07 | `bb44bd5` | Patient Overview "Quick Actions" (new appointment, send message, create document); Send message opens the patient's thread through the backend's find-or-create; the Chat badge drops on read |

Every ticket had a Codex review, most two rounds; all findings were fixed. Chat writes were checked in demo mode only:
the dev seed doctor has no conversations, and a started conversation can't be deleted. Reads and request shapes were
checked against dev's `/v3/api-docs` and the backend code. iOS: compiles and links, nothing run.

Not built in M7:
- The chat push and its deep link (PUSH-03, at the end with the rest of push, D-15).
- Home's hero "Message" (not on the `DaHome` hero; Flutter's caller is dead code) and "Message patient" on the
  appointment detail (a NEW item in flow 07/07).
- Seeking, speed and a 5-minute cap for voice notes; a loading state while a voice stream prepares; the list's
  preview of an attachment shows the backend's `lastMessage` text (flow 07/01 open question).

## M8 · Notifications (done except push)

| Ticket | Commit | What it is |
|---|---|---|
| NOTIF-01 | `83ccaac` | The paged inbox data, every backend type (35 on dev) mapped to a kind with its chip, tap target, board look and a body in the app's language from the structured fields (the backend keeps rows English); a demo inbox |
| NOTIF-02 | `1eb8a6e` | The inbox: chips, practice-day sections, board cards, paging, read on tap (rolled back if refused) opening the appointment's Calendar panel, the patient, the approved document or Chat; Open Calendar on the appointment's day |
| NOTIF-03 | `9270ddb` | The bell's count read again on every trigger (inbox open/refresh, a read, resume, Home), never polled |

Not built: NOTIF-04 (approve/decline access inline, D-07), the push trigger and the chat thread target (no
conversation id in a row, BE-06): with push at the end (D-15).

## M9 · Remote-care tasks (done)

| Ticket | Commit | What it is |
|---|---|---|
| TASK-01 | `7f1e929` | Task data and rules: filters, check-ins, templates, create, cancel; the day's times in the backend's order (legacy times included); missed by the patient's clock; unusable 200s typed; the in-memory demos answer 404 for ids they never made instead of reaching the real backend |
| TASK-02 | `a78776a` | The Tasks tab: summary cards that filter, search and localised dropdowns, board cards, the docked Create Task |
| TASK-03 | `388f7e7` | Details: the info card and progress, check-ins newest first with values as written, the check-in sheet in patient time, Cancel; task notifications and View Result open it in the Tasks tab |
| TASK-04 | `39e5571` | The create form: the doctor's own patients (the picker now shared with booking), even spacing with the day's slots or custom times to the minute, an end date or whole days, the payload with the count that fits, Back asks |
| TASK-05 | `6ec7331` | Templates (the doctor's earlier tasks' settings) opening the form pre-filled; one labelled switch row for the form, Settings and Services |

Every ticket had a Codex review, most two rounds; all findings were fixed or answered in `docs/implemented/codex/`.
Reads were checked against dev (46 notifications, 3 tasks, 6 check-ins, 3 templates); every write (mark read,
create, cancel) only in demo mode, since none can be undone on dev. iOS: compiles and links, nothing run.

Not built in M9: a task started from the patient record (the record's ⋮ sheet isn't built), the patient-app hint (no
backend field), template editing (no endpoint exists).

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
   Also its Daily call logs the SDK's error text (`VideoCall.android.kt`, `DailyVideoCall.swift`, unguarded on iOS); the
   doctor app's copy logs a fixed line in debug builds only.
5. **Workspace notes:** the workspace `CLAUDE.md` says doctor tokens last 12 hours. The backend (and dev) say 30 days. Worth correcting there.
6. **Web target (D-08):** see above.

## Commands used per ticket
- `./gradlew ktlintFormat` then `ktlintCheck`.
- `:composeApp:testDebugUnitTest --tests '<ticket packages>'`.
- `recordRoborazziDebug --tests 'shifa.doctor.ui.widgets.*'` for CORE-03; `verifyRoborazziDebug --tests 'shifa.doctor.ui.*'` after the `tr()` switch in CORE-04.
- `:composeApp:assembleDebug` and `:composeApp:linkDebugFrameworkIosSimulatorArm64`.
- The full screenshot suite was not run.
