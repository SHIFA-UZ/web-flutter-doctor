# Foundation (M0)

## CORE-01 · Project skeleton, CI

Built 2026-11-04 on `doc-app2.0`, copied from the patient app's `main` (`dc2eae4`) and renamed; nothing is shared
between the two repos.

- **Gradle:** `settings.gradle.kts` (`rootProject.name = "ShifaDoctor"`, only `:composeApp`), the patient app's
  `gradle/libs.versions.toml` minus PostHog and `ktor-client-logging` (CLAUDE.md: no Ktor Logging plugin),
  `gradle.properties`, the wrapper, `.editorconfig`, ktlint 1.1.1 failing the build.
- **Android:** namespace `shifa.doctor`, `applicationId = "com.shifa.doc"`, `minSdk 24`, the Flutter app's
  permissions (including `USE_FINGERPRINT` for API 24–27), its launcher
  bitmaps and `google-services.json` (the file the Flutter app already tracks). `allowBackup="false"`: the token
  store is EncryptedSharedPreferences, whose key does not survive a restore. Cleartext traffic off (every host is
  https). Version name from `MARKETING_VERSION`, version code from `-PversionBuild`.
- **iOS:** `iosApp/` from the patient app without PostHog and Daily (Daily comes back with VIDEO-01 or CORE-07),
  `PRODUCT_BUNDLE_IDENTIFIER = uz.shifa.doc`, team `NH97PGA9PT`, product `ShifaDoctor`, display name "Shifa Doctor",
  `MARKETING_VERSION = 1.0.1`, the Flutter app's usage descriptions, background modes and 1024 px icon (RGB, no
  alpha), its `GoogleService-Info.plist`, Firebase Messaging via SPM. The AppDelegate only configures Firebase; the
  push bridge comes with PUSH-01.
- **Shared scheme:** `iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` exists locally (copied, with the
  product renamed) but is **not committed** (CLAUDE.md rule, repeated in the overnight brief). fastlane `gym` needs a
  shared scheme on CI (Codex CORE-01 P1), so `ios_testflight.yml` cannot succeed until the user decides how the
  scheme gets into the repo: commit it once (as the patient app's `main` does) or generate it in CI. Open question.
- **CI:** `flutter_ci.yml` → `ci.yml` (ktlint, unit tests, Roborazzi verify on every pull request).
  `google_play_deploy.yml` keeps the Flutter workflow's name, secrets (`DOCTOR_*`), keystore checks and `track` input
  (internal default, plus alpha and production); `ios_testflight.yml` keeps the whitespace-trimming App Store Connect
  key step and builds the Kotlin framework in its own step. `deploy_web.yml` is untouched (Flutter web from `main`).
- **App:** `App()` shows a placeholder until AUTH-01.

## CORE-04 · Strings (`composeResources/values*/`, `utils/AppLocale.kt`, `utils/UzbekCyrillic.kt`, `data/storage/LanguageStorage.kt`)

Built 2026-11-04 on `doc-app2.0`.

- **Source → resources.** `docs/kmp-spec/strings/{en,uz,ru}.json` + `plurals.json` are the source;
  `python3 scripts/strings_from_json.py` writes `values{,-uz,-ru}/strings.xml` (2206 each) and `plurals.xml` (16 each).
  Placeholders `{x}`, `{{x}}`, `%s`, `%d` become `%1$s`/`%1$d`, numbered in the English order and mapped by name, so a
  translation that reorders them keeps the argument numbers. Only `& < >`, backslash and newline are escaped: CMP 1.9.3
  unescapes nothing else (`strings/README.md` rule 2 said `\'`, which would have shown the backslash; corrected).
- **Gaps filled** (2218 keys per locale): 1 `en`, 10 `uz`, 92 `ru`, 41 defined nowhere (English from the Flutter
  fallbacks where they exist), and the NEW keys of the flow files' §11 (257 strings + 7 plurals). Dropped 19 names the
  extraction picked up that are data fields or formats (`createdAt`, `taskId`, `visitTimer`, …). Translations were
  written for the port and follow `uz.json`/`ru.json` terminology; a native-speaker pass is still worth doing.
- **Plurals** (`plurals.json`, each lists the JSON keys it replaces): `consultationUploadSuccess`, `patientsCount`,
  `visitsCount`, `patientsPagination` (Russian genitive after "из"), `aiInsightNotifications`, `customTimesCount`,
  `minutesAgo`, `hoursAgo`, `intelCancelPreview` (its `{visitWord}` was a hand-made plural and the uz/ru copies had
  dropped it), and the NEW `attemptsLeft`, `visitBriefingMeta`, `dentalHiddenLines`, `dentalSwitchToPlanConfirm`,
  `toothA11y`, `discardTicks`, `visitFactsCommitted`. `visitFactsCommittedWithTasks` has two counts, so it became
  "Facts documented: {n}. Medication tasks drafted: {m}." (flow 03/21 updated).
- **Uzbek Cyrillic.** `tr()` / `trPlural()` (`utils/AppLocale.kt`) replace `stringResource`: they fetch the template,
  transliterate it when `LocalUzbekCyrillic` is on, then fill the arguments, so names and numbers stay as typed.
  `UzbekCyrillic.fromLatin` ports the Dart function and adds the orthography it lacked: `ya/yu/yo/ye` → я/ю/ё/е,
  word-initial `e` → э, `ye` after a consonant → ъе (Объектив), the separating apostrophe → ъ, `%1$s` placeholders kept (the Dart only kept `{x}`). All kit
  widgets use `tr`. Golden tests: `UzbekCyrillicTest`.
- **Language.** `AppLanguage` en / uz / uz-Cyrl / ru (`backendCode` maps Cyrillic to `uz`); `LanguageStorage` keeps
  the Flutter keys `doctor_language` + `doctor_language_explicit`: a saved language wins (LANG-02 Fix: no cold-start
  override of a saved non-explicit value), else Uzbekistan (device language `uz`, region `UZ`, zone Asia/Tashkent)
  means Uzbek, else English. `applyRegionalDefault(phone)` (login screens) saves Uzbek for a +998 number while
  nothing is saved. `App.kt` applies it with `ProvideAppLocale` (copied from the patient app).
  **Not here:** `PUT /doctors/me/language` needs `apiCall`, so the sync is wired in CORE-05.
- **Tests:** `StringsTest` (keys, plurals and placeholders equal across locales; no `\'`/`\"`),
  `UzbekCyrillicTest`, `LanguageStorageTest`.

## CORE-05 · HTTP client (`data/api/`, `data/model/ApiError.kt`, `data/storage/TokenStorage.kt`, `utils/Jwt.kt`)

Built 2026-11-04 on `doc-app2.0`, copied from the patient app's `data/api` and changed where 17 §1 says so.

- **Client** (`HttpClient.kt`): OkHttp / Darwin, 30 s request/connect/socket timeouts on every verb, lenient JSON. An
  `HttpSend` interceptor adds the token only to the backend's `/api/` paths that need it (not `auth/**` except
  `auth/reset-password`, not `public/**`, never the file paths or `/v3/api-docs`; checked against the backend's
  `SecurityConfig`), and never over a header the caller set (`auth/verify` sends a Firebase bearer). Pre-flight
  (Flutter's rule): a token more than 30 s past `exp` ends the session and fails with `Unauthorized(SESSION_EXPIRED)`
  without a request.
- **Session rule** (17 §1), only in `apiCall`: a 401 to a request that carried a token → `SessionEvents.end(Unauthorized, token)`;
  `code = ACCOUNT_DISABLED` → `AccountDisabled`; any other 403 never ends the session. The backend sends
  `ACCOUNT_DISABLED` as a 403 (`JwtAuthFilter`), so that code is the one 403 that does. CORE-06's session repository
  listens.
- **Errors:** `ApiException` carries `status`, `code`, `requestId`, and no server text (dev's 404 message names the
  record: "Appointment not found: 9"). `toUserMessage(fallback)` maps codes (`RATE_LIMITED`, `ACCOUNT_DISABLED`,
  `SESSION_*`/`TOKEN_INVALID`, `UNAVAILABLE`) and otherwise uses the screen's own fallback string. The patient app's
  English-message table is not copied (17 §1: never match English text). HTML error pages are not decoded.
- **Logging** (debug only): `FAILED <status> <method> <path, ids masked> <code> requestId=<id>`; never tokens,
  headers, bodies or server messages.
- **SSE:** `HttpClient.streamEvents` reads `text/event-stream` on the same client; non-2xx and `event: error` fail like
  `apiCall`; an event cut off by the end of the stream is dropped (WHATWG rules; the patient app kept it).
- **Dev host switch:** `data/debug/BackendChoice` (debug builds; production otherwise). The menu itself is CORE-08.
- **Moved in from CORE-06** because the interceptor needs them: `TokenStorage` (`shifa_doctor_auth_token` in
  `shifa_doctor_auth_secure`, Keychain / EncryptedSharedPreferences) and the single `Jwt` reader (`exp`, `role`).
- **Language sync:** `LanguageRepository.sync(code)` → `PUT /doctors/me/language` (204), deduped under a mutex, and a
  reply landing after logout doesn't count for the next session; called on sign-in
  and on change once the session exists (CORE-06).
- **Verified on dev 2026-11-04** as `seed.doctor1@shifa.dev`: `POST auth/login` 200 `{token, forcePasswordReset}`;
  `GET doctors/me` 200 `{billing, contact, profile, settings, subscription}`; missing / invalid token 401
  `code: UNAUTHORIZED` (not `TOKEN_INVALID`); `PUT doctors/me/language` is in the OpenAPI spec (204). The JWT carries
  `exp, iat, iss, jti, principal, role, sub`, and **a doctor token lives 30 days** on dev, not the 12 h the workspace
  notes say.
- **Tests:** `ApiCallTest` (11: session rule cases, pre-flight, token placement, HTML and numeric error bodies,
  messages, masking), `SseTest` (3), `JwtTest` (3), `LanguageRepositoryTest` (2).

## CORE-06 · Storage and session (`data/session/SessionRepository.kt`, `data/storage/AppLockStorage.kt`, `utils/Crypto.kt`)

Built 2026-11-04 on `doc-app2.0`. `TokenStorage`, the secure store and the `Jwt` reader came with CORE-05.

- **`SessionRepository`:** `session: StateFlow<Session?>` (`Session(token, role)`, `isClinicStaff`). `restore()` is
  01 §2.9 without the network: a saved token counts only if it is not 30 s past `exp` and its role is `DOCTOR` or
  `CLINIC_STAFF` (an `ADMIN` token is dropped: the admin panel is web). `signIn(token): Result<Session>` after any login, registration
  or reset. `signOut()` for the doctor's own sign-out. `end(reason, token)` comes from `SessionEvents` (17 §1) and
  ends the session only if `token` is the current one (a late 401 from a previous session can't sign out the next);
  it replays the reason on `ended` for the sign-in banner. Every change runs under one lock (`SynchronizedObject`,
  atomicfu 0.27.0 added directly): requests end sessions from any thread. `SessionEvents` is a synchronous listener
  the repository sets when created, and the HTTP client takes its token from `session`, so no request carries a token
  before the listener exists (Codex CORE-06 round 1).
- **What a logout clears** (01 §4, "cleared on logout: yes"): the token and `shifa_doctor_last_activity_ms`, and the
  language-sync dedupe. Kept: app-lock PIN and flags, language, OTP history, active location. Sending an empty FCM
  token on logout (01 §2.10 step 1) comes with PUSH-01; dropping cached data comes with each feature's repository.
- **`AppLockStorage`** (Fix line, bug 1): the PIN is never stored. It keeps a 16-byte random salt and a 10 000-round
  salted SHA-256 (`app_lock_pin_salt`, `app_lock_pin_hash`), compared in constant time; 4–6 digits are valid and
  verified at their full length (Flutter auto-checked at 4, so 5- and 6-digit PINs never worked). The failed-attempt
  count is stored (`app_lock_failed_attempts`, max 5) so a rebuilt lock screen doesn't reset it. Flags:
  `app_lock_enabled`, `biometric_enabled`. `clear()` when the lock is turned off. Nothing here is cleared on logout.
  SHA-256 and the salt come from the platform (`MessageDigest`/`SecureRandom`, CommonCrypto/`SecRandomCopyBytes`).
- **Language sync wired:** `App.kt` calls `languageRepository.sync` when a session exists and on every language change.
- **Keys** follow 01 §4; the Flutter store isn't migrated (the KMP app is a fresh install of the same listing; doctors
  sign in again once).
- **Tests:** `SessionRepositoryTest` (6: restore, expired/admin/garbage tokens, sign-in, what a session end clears,
  sign-out then a late report, a late 401 after a new sign-in), `AppLockStorageTest` (5).

## CORE-07 · Native spikes (`ui/debug/SpikesScreen.kt`, `utils/{VoiceRecorder,AudioPlayer,PdfView,MapPin,PushToken,CacheFile}`)

Run 2026-11-04 on the Pixel 9 Pro emulator (Android 16 image, arm64) and the iPhone 16 Pro simulator (iOS 18.6).
Debug builds open the Spikes screen until AUTH-01 (the debug menu keeps it after CORE-08); release builds never show it.

| Spike | Android | iOS | Outcome |
|---|---|---|---|
| AAC record | `MediaRecorder` → `cache/spike.m4a`: 1 ch, 16 kHz, AAC, 32 kbps, 5.0 s (`afinfo`) | `AVAudioRecorder` → `tmp/spike.m4a`: 1 ch, 16 kHz, AAC, 3.1 s | **Works.** The patient app's recorder is copied as is; the backend already takes `.m4a`. |
| AAC play | `MediaPlayer`: position advanced 2 s / 5 s | `AVPlayer`: 0 s → 1 s / 3 s, then finished | **Works**, file URLs and streamed URLs alike. |
| PDF render | `PdfRenderer` drew the bundled page | PDFKit drew it | **Works** for viewing. Generating PDFs on device (summary, 025-2) is not spiked; D-05 asks the backend first. |
| Map pin | osmdroid 6.1.20 + OSM tiles, pin at Amir Temur square, tap moves the pin | MapKit, same pin | **Works without a Google key** (D-06). Needed `clipToBounds()` on Android (the MapView drew over the card above). iOS tap-to-move waits for PROF-07. |
| FCM token | Firebase initialises; `FirebaseMessaging.token` fails: "FCM Registration failed!" | n/a in Kotlin (APNs → Swift) | **Blocked by credentials**: the debug-signed build isn't accepted (the API key is likely restricted to the release certificate, or the image needs a signed-in Play account). Re-check in PUSH-01 on a device with the release key. |
| Daily video | — | — | **Skipped**: joining needs a room token for a video appointment and the Daily SDKs on both platforms (credentials + SDK work). VIDEO-01 does it. |

- The iOS run has no UI driver, so `xcrun simctl launch … uz.shifa.doc -spikeAutoRun` records 3 s, stops and plays
  (debug builds only; `MainViewController.kt`). Android was driven with `adb` taps (`RECORD_AUDIO` granted with
  `pm grant`).
- Copied from the patient app (`main@dc2eae4`): `VoiceRecorder`, `AudioPlayer`, `PdfView` (+ `NSData` helpers). New:
  `MapPin`, `currentPushToken`, `writeCacheFile`.
- The sample page is `composeResources/files/spike.pdf` (hand-written, 636 bytes).
- Codex fixed seven defects in the copied code (URL logging, player state machine and leak, PDF threading, cleanup and
  iOS refresh, the auto-run timer). **The patient app still has the same code:** worth a follow-up there.

## CORE-08 · Demo backend and debug menu (`data/debug/DemoBackend.kt`, `ui/debug/DebugMenuScreen.kt`)

Built 2026-11-04 on `doc-app2.0`, after the patient app's `DemoBackend`.

- **`DemoMode`** (debug builds, kept across launches) switches the shared `httpClient` to a `MockEngine` client per
  backend. **`DemoBackend`** answers every endpoint the app calls so far: `GET doctors/me` (fixture) and
  `PUT doctors/me/language` (204; stored, so `doctors/me` shows it). `auth/login` and any unhandled GET go to the real
  backend (sign-in works; reads are safe); every other write is refused, other `auth/` calls included (registration,
  OTP and password reset write: Codex CORE-08), with the backend's error shape
  (`403 code DEMO_WRITE_REFUSED`), so demo mode never changes production. "One handler per endpoint" grows with each
  feature ticket (CLAUDE.md "Debug builds"); `DemoBackendTest` lists the handled endpoints.
- **Fixtures come from real development responses:** `composeResources/files/demo/doctors-me.json` is the dev
  `GET /doctors/me` for `seed.doctor1@shifa.dev` (2026-11-04) with the name, contacts, bio and coordinates replaced
  ("Aziza Karimova", `demo.doctor@shifa.dev`). It shows the shape the app must read: `consultationPriceMinor` 15 000 000
  = 150 000 UZS, `timeZone` Asia/Tashkent, `subscription.features` the 13 backend feature names.
- **Debug menu** (debug builds; App shows it until AUTH-01 adds the shell, then Profile → Debug): Demo data, Development
  backend (switching signs out, since a token from one backend is invalid on the other) and Native spikes.
  **Not built:** fake pushes; their payloads are defined by PUSH-01 and NOTIF-*, which add them here.
- **Tests:** `DemoBackendTest` (3: fixture answer, language write and read-back, `auth`/unknown GET passthrough and
  write refusal).

## CORE-09 · Cross-cutting utils (`utils/`)

Built 2026-11-04 on `doc-app2.0`.

- **`PracticeClock(zoneId)`** (flow 04 README §4.2, 03 §4): `today()`, `now()`, `local(instant)`, `instant(date, time)`
  (DST-correct: 09:00 Berlin is 08:00Z before the March switch, 07:00Z after), `startOfDay`, `deviceDiffers()` for the
  mismatch banner, `zoneMissing` when the profile zone is empty or unknown (UTC fallback, so the screen can ask). The
  clinic screens build one with the clinic's zone. `parseInstant` reads a zone-less backend timestamp as UTC (Flutter
  read it as device time, 03 §4.3 item 6). `currentInstant()` reads the stdlib clock, since kotlinx-datetime 0.5's
  `Clock` doesn't resolve on iOS with Kotlin 2.3.
- **`Money`** (flow 09 README "Money formatting"): `format(minor, currency)`: UZS whole sums ("1 250 000 UZS", tiyin
  rounded away, never shown), others two decimals ("1 250.50 USD"), grouped with a narrow no-break space (the spec's
  thin space, but unbreakable), "0 UZS" for zero, U+2212 for negatives. `parse(text, currency)` takes "1 250 000",
  "1250.50" or "1250,5"; UZS refuses decimals.
- **`PhoneE164`**: `normalize` ("90 123 45 67", "998…", "+998 …", "00998…" → "+998901234567"; other countries need
  their code), `display` groups Uzbek numbers.
- **`PasswordPolicy`**: the backend's rule with its own patterns (8–128, ASCII upper, lower, digit, a non-space
  special character); `missing()` lists what the form still needs.
- **Platform helpers** (from the patient app): `rememberDocumentPickerLauncher` (PDF), `rememberTextShare`,
  `rememberInAppBrowser` (open-URL: Custom Tab / `SFSafariViewController`), `FileSaver`. Clipboard needs nothing:
  Compose's `LocalClipboardManager` is multiplatform. The camera/gallery picker and image compression come with DOCS-02
  (they need a FileProvider and the compression rules).
- **Not built: `sanitizeErrorMessage` port.** It matched English server text, which 17 §1 forbids ("never match English
  text"); CORE-05's `toUserMessage(fallback)` maps the backend's `code` instead. The plan row is corrected.
- **Tests:** `PracticeClockTest` (4), `MoneyTest` (3), `PhoneE164Test` (3), `PasswordPolicyTest` (1).

## iOS release build memory (2026-11-07)

The TestFlight workflow's "Build Kotlin framework" (`linkReleaseFrameworkIosArm64`) ran out of memory (6 GB heap, 1 h 13 min,
2026-11-06). Measured with GC logs: on Kotlin 2.3.0 the release link holds about 6.3 GB after collections (peaks near
12.5 GB; it passed with a 14 GB heap in 13 min). On **Kotlin 2.4.21** it passes inside a **5 GB** heap in 16 min, about
3–4.3 GB live after full collections, so it fits GitHub's standard macOS runner (7 GB in all). The user's call: shrink
before paying for a larger runner; the Kotlin upgrade alone was enough (the unused-strings and debug-code levers were
not needed). `ios_testflight.yml` passes `-Xmx5g` to that step (the project's 6 GB would leave too little for the native
compiler and the system). The patient app moves to the same Kotlin (the two catalogs stay equal).

