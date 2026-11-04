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
- **Session rule** (17 §1), only in `apiCall`: a 401 to a request that carried a token → `SessionEvents.end(Unauthorized)`;
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
