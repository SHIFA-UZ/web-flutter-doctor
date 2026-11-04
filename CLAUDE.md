# Project rules

This repo is the Shifa doctor app. It is being rebuilt from the Flutter doctor app in Kotlin Multiplatform, in this
same repository, the way the patient app was (`SHIFA-UZ/apps-flutter-patient`, branch `shifa2.0` → `main` v1.2.0).
API and UI come from the spec in `docs/kmp-spec/`, the flows in `docs/ux-flows/` and the design in `docs/design/`; the tech stack, architecture and workflow below are
fixed and identical to the patient app's. Follow them for every new feature.

| Value | Doctor app |
|---|---|
| Repo | `SHIFA-UZ/web-flutter-doctor` (keeps its name and history) |
| Kotlin package | `shifa.doctor` (the patient app uses `shifa.uz`) |
| Android application id | `com.shifa.doc` (already on Google Play; `minSdk 24`) |
| iOS bundle id | `uz.shifa.doc`, team `NH97PGA9PT` |
| Firebase project | `shifa-doctor-staging` (production uses it too; `shifa-doctor-qa` exists) |
| Backend | `https://api.shifa.uz/api` in `ApiConfig.BASE_URL` (paths written without `/api`); debug builds can switch to `https://api-dev.shifa.uz` (seeded, `seed.doctor1@shifa.dev` / `Dev12345!`). The Railway host stays alive for old store builds. |
| Design source | `docs/design/` (a verbatim copy of the Claude Design canvas: `tokens.json`, `shifa-ds.css`, 51 boards); read `docs/design/README.md` first. No Figma. |
| First `MARKETING_VERSION` | `1.0.1` (Flutter shipped `1.0.0`). Build number = offset + `run_number` of the same workflow files, offsets Play 1 and iOS 4, so both start at 10: last Flutter uploads were Play run 8 (versionCode 9) and TestFlight run 5 (build 6) (D-10, 2026-11-04). |
| Locales | `en` (default), `uz` (+ runtime Latin→Cyrillic transliteration), `ru` |

## Sources of truth

- `docs/kmp-spec/` is the source of truth for API and features; `docs/kmp-spec/15-api-reference.md` wins for request
  and response shapes. Each feature doc ends with "Quirks / tech debt": fix bugs, keep only what backend compatibility
  needs. Do not reproduce bugs listed in `docs/kmp-spec/00-README.md`.
- The backend publishes its OpenAPI spec at `<BASE_URL>/v3/api-docs` (no token). Check it before claiming an endpoint
  or field is missing; it can lag real responses, so confirm with a real GET when it matters. Plain GETs against
  production are fine; never write to production data while developing (use the demo backend).
- UI comes from `docs/design/`: `tokens.json` and `shifa-ds.css` map into `ui/theme/`, the `.sh-*` component blocks
  into `ui/widgets/`, and each screen is a branch of a module board (`boards/Da*.dc.html`); `docs/design/README.md`
  names the board for every flow area. Light mode only; the theme has no dark scheme. Record the board and screen
  branch you implemented in `docs/implemented/`. There is no Figma file.
- `docs/implemented/` records what's already built. Read `docs/implemented/README.md` before starting a task, and
  update it (status table, the area file, one log line) at the end of every job, in the same commit.
- `docs/backend-improvements.md` lists what the app needs from the backend team: what happens now, the change, and
  what the app does until then. Add to it instead of working around a backend gap silently.
- The Flutter code stays readable on `main` until the merge; when the spec says UNVERIFIED, read the Dart file it
  cites (`git show main:lib/...`) before deciding.

## Scope decisions (the user's calls; update when they change)

- Targets: **Android + iOS** first, same as the patient app. The Flutter **web** build (doctor web + admin host) keeps
  running from `main` until a web target is decided (see `00-README.md` "Open decisions"). Do not design screens for
  desktop widths; the KMP app is a phone/tablet app.
- The **admin panel** (`/admin`, hostname-based) is **not** part of the KMP app (`ADMIN-*` tickets are parked); it
  stays a web product.
- Doctor roles in the JWT (`DOCTOR`, `CLINIC_STAFF`, …) and clinic workspace mode are in scope; a clinic-staff user
  gets the reduced shell described in `01-architecture-core-auth-shell.md` §6 and flow `01/18`.

## Rewrite in place: what stays, what goes

The rewrite happens inside the Flutter doctor app's repository, so GitHub history, settings, secrets and CI/CD are
reused, not recreated. Nothing is force-pushed or rewritten.

Keep, and reuse as they are:
- The GitHub repository, its history, branches, tags, releases and Actions secrets.
- `.github/workflows/` (`flutter_ci.yml` → `ci.yml`, `google_play_deploy.yml`, `ios_testflight.yml`): port them from
  the Flutter workflows the way the patient app did (same job shapes, same secret names, same version offsets), so the
  deploys keep working for the same Play app and the same TestFlight app.
- Firebase: the same project, `google-services.json` and `GoogleService-Info.plist` (from the same base64 secrets),
  the same push setup. Firebase Auth (phone OTP) is used by the Flutter doctor login; the KMP login keeps the backend
  OTP paths (see `05-auth.md`) and uses Firebase Auth only where the spec says so.
- Store identities: `com.shifa.doc` / `uz.shifa.doc`, team `NH97PGA9PT`, signing keystore, provisioning,
  App Store Connect key. The KMP app is an update of the Flutter app, not a new listing, so `versionCode`,
  the build number and `MARKETING_VERSION` continue above the Flutter app's last values.
- Backend: the same base URL, endpoints and auth; `docs/kmp-spec/` describes them.
- Anything else from the Flutter repo that is not Dart or design: store assets, legal texts (`docs/contracts`), the
  thesis docs under `docs/`, `scripts/` that still apply.

Delete when the KMP app replaces it: the Flutter code (`lib/`, `android/`, `ios/`, `windows/`, `web/`, `pubspec.*`,
`test/`, `firebase.json` hosting targets if web is dropped…), the Flutter design assets, themes, fonts and icons.
Design comes only from `docs/design/`; do not port Flutter styles, colours or widgets (the legacy tokens are
listed in `04-design-system-and-ui-kit.md` §A only for comparison).

## Git

- Remote: `SHIFA-UZ/web-flutter-doctor`. `main` holds the released Flutter app until the KMP app replaces it.
- The whole rewrite happens on ONE branch, `doc-app2.0`, created from `main`. Every feature, fix and doc change goes
  there until the full app is done and the user merges it into `main` through a pull request (the user decides when).
  Do not create per-feature branches during the rewrite (D-00, the user's call 2026-11-04, overriding the per-ticket
  branches `17-master-plan.md` §2 proposed).
- The Flutter code (`lib/`, `android/`, `ios/`, `web/`, `windows/`, `pubspec.*`, `test/`) stays on this branch until
  the KMP app replaces it, so `deploy_web.yml` keeps building the web app from `main`. Gradle only sees
  `composeApp/`; Xcode only `iosApp/`.
- After that merge, switch to the patient app's rule: before making any change, check the current branch; if the task
  has no branch yet, create one from `main` named `feature/<feature-name>` (kebab-case, e.g.
  `feature/schedule-management`) and do all the work there. Update this section when that happens.
- Never push to `main` directly or merge into it unless the user asks; changes reach `main` through a pull request.
- `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` is committed (the user's call, 2026-11-04) because
  fastlane needs it for TestFlight. The IDE rewrites it locally: never commit those local edits, and keep its
  `ArchiveAction` (Release).
- Never commit or push on your own. Show the user the finished work (what changed, screenshots, test results), and
  only commit and push once they approve. Standing permission for `doc-app2.0` is not approval for a given change.
- Commits are authored by the user only, with the repo's git identity (`git config user.name` / `user.email`). Never
  add `Co-Authored-By`, "Generated with" or any other Claude or agent attribution to commit messages or PR
  descriptions, and never set Claude or an agent as the author or committer.
- Commit messages follow Conventional Commits (`feat(calendar): …`, `fix(auth): …`), imperative, lowercase, ≤ 72 chars.
- Pull requests: push the branch, then hand over the compare URL plus a ready title and body for the user to paste.
  If `gh` is missing, say so and stop. Never control the user's browser (no Chrome tools), not even to open a PR.

## Tech stack (do not swap without asking)

- Kotlin Multiplatform (Android + iOS: `iosArm64`, `iosSimulatorArm64`), single module `composeApp`, package `shifa.doctor`
- Compose Multiplatform + Material 3 for all UI (shared in `commonMain`)
- Ktor Client (OkHttp on Android, Darwin on iOS) + ContentNegotiation, HttpTimeout; the Bearer token is added by an
  `HttpSend` interceptor (no Auth plugin, no refresh token). No Ktor `Logging` plugin: its default logger goes through
  SLF4J, which has no binding on Android, so nothing reaches Logcat. SSE (AI streams) goes through
  `HttpClient.streamEvents` (`data/api/Sse.kt`), a reader on the same client: Ktor 2.3's SSE plugin can't POST a body.
- kotlinx.serialization (JSON), kotlinx.coroutines, kotlinx.datetime
- Multiplatform Settings (`no-arg`) for local key/value; the token uses `secureSettings` (`KeychainSettings` on iOS,
  EncryptedSharedPreferences from `androidx.security:security-crypto` on Android)
- Kamel for async image loading
- JetBrains `lifecycle-viewmodel-compose` for ViewModels
- Firebase (google-services, Messaging for push, App Distribution for debug builds)
- Daily for video calls: `co.daily:client` on Android, Daily's Swift package on iOS behind
  `iosApp/iosApp/DailyVideoCall.swift` (it implements the Kotlin `NativeVideoCall`)
- Charts (home analytics, finance): hand-drawn Compose `Canvas` (no chart library; the boards draw bars, a line and a
  donut, all within a few dozen lines each). PDF: backend rendering is requested (BE-01, D-05); until then the
  appointment summary and 025-2 use the platform PDF APIs behind `expect/actual` (Android `PdfDocument`, iOS
  `UIGraphicsPDFRenderer`; generation not spiked yet). Viewing is `utils/PdfView` (Android `PdfRenderer`, iOS
  PDFKit), verified on both by the CORE-07 spike.
- Native pieces proven by the CORE-07 spikes (debug Spikes screen, `ui/debug/SpikesScreen.kt`;
  `docs/implemented/foundation.md`): voice is `utils/VoiceRecorder` (AAC-LC `.m4a`, 16 kHz mono, 32 kbps:
  `MediaRecorder` / `AVAudioRecorder`) and `utils/AudioPlayer` (`MediaPlayer` / `AVPlayer`, one at a time); maps are
  `utils/MapPin` (osmdroid with OpenStreetMap tiles on Android, MapKit on iOS; no Google key, D-06); the FCM token is
  `utils/currentPushToken()` (Android; iOS gets it in Swift with PUSH-01). Not spiked: Daily (needs a video
  appointment's room token and the SDKs) and push delivery (needs the release key / a device).
- Tests (`androidUnitTest`): JUnit4, Mockito-Kotlin, Turbine, Truth, kotlinx-coroutines-test, Roborazzi + Robolectric
  for screenshots, `multiplatform-settings-test` (`MapSettings`) for storage
- ktlint (`./gradlew ktlintFormat`); it fails the build. `.editorconfig` allows PascalCase `@Composable`s, trailing
  parameter comments, Figma-named theme files and on-demand `Res` imports.
- All versions live in `gradle/libs.versions.toml`; add deps there, never inline. Copy the catalog from the patient
  app and keep the two apps on the same versions.
- Known pin: kotlinx-datetime is skewed between Android and iOS (CMP pulls a newer one on iOS), so
  `kotlinx.datetime.Clock.System` fails on iOS. Use `utils/getCurrentInstant()` until the pin is bumped.
- No DI framework and no navigation library (deferred on purpose; ask before adding either).

## Architecture: MVVM + Repository

```
composeApp/src/
├── commonMain/kotlin/shifa/doctor/
│   ├── App.kt              # root: locale + theme + top-level screen switching (Splash → Login → shell)
│   ├── data/
│   │   ├── api/            # HttpClient.kt, ApiHelper.kt, ApiConfig.kt, ErrorMessages.kt, Sse.kt
│   │   ├── model/          # @Serializable DTOs, one file per domain (XxxModels.kt), ApiError.kt
│   │   ├── storage/        # TokenStorage, SecureSettings (expect/actual secure store), LanguageStorage, AppLockStorage
│   │   ├── debug/          # DemoBackend.kt (MockEngine) + DemoMode, debug builds only
│   │   └── <feature>/      # XxxRepository.kt
│   ├── ui/
│   │   ├── theme/          # Color.kt, Typography.kt, Dimens.kt, Theme.kt (tokens from docs/design/)
│   │   ├── widgets/        # reusable design-system components (ShifaButton, ShifaInputField…)
│   │   ├── navigation/     # MainScreen.kt (tab shell) + ShifaNavBar.kt
│   │   ├── debug/          # Debug menu (demo data, fake pushes)
│   │   └── <feature>/      # XxxScreen.kt, XxxViewModel.kt, XxxUiState.kt
│   └── utils/              # expect/actual platform helpers (pickers, files, push, video, audio, browser, dates, timezone)
├── commonMain/composeResources/   # values{,-ru,-uz}/strings.xml, drawable (design exports), font
├── androidMain/            # actuals, MainActivity, MessagingService, @Preview files (ui/<feature>/XxxPreviews.kt)
├── iosMain/                # actuals, MainViewController
└── androidUnitTest/        # ViewModel + repository tests, widget and screen screenshot tests
iosApp/                     # Xcode host app (Swift bridges for Daily and push, fastlane, Configuration/Config.xcconfig)
```

### Data layer
- `ApiConfig` holds `BASE_URL` (production; paths are written without `/api`), `TIMEOUT_MS` (30 s), `Endpoints`
  (consts, or functions for path params) and `PUBLIC_AUTH_PATHS`.
- `createHttpClient(token, engine)` builds the Ktor client (engine defaults to `expect fun createHttpEngine()`); the
  app uses the shared `httpClient` (production, or the development backend from the debug menu). The token goes only
  to the backend's `/api/` paths that need it (`ApiConfig.needsToken`: not `auth/**` except `auth/reset-password`, not
  `public/**`, never file URLs) and never over an Authorization header the caller set (Firebase bearer). A token more
  than 30 s past `exp` ends the session before the request is sent.
- Calls go through `apiCall<T> { client.get(…) }` from `ApiHelper.kt`: returns `Result<T>`; a non-2xx becomes an
  `ApiException` with the backend's `status`, `code` and `requestId` (no server text: it can name a patient); no
  response becomes `NetworkError`. HTML error pages and a numeric `error` are read safely. **Session rule (17 §1), applied
  only in `apiCall`: a 401 to a request that carried a token, or `code = ACCOUNT_DISABLED`, ends the session through
  `SessionEvents`; a 403 never does** (clinic staff legitimately get 403 on doctor-only routes; the feature shows its
  own message). Screens show `e.toUserMessage(Res.string.<featureFallback>)`, which maps the `code`, never English
  text; show `requestId` in the error banner's detail.
- The session lives in `SessionRepository` (`data/session/`): `restore()` on cold start, `signIn(token)`, `signOut()`;
  the shell reacts to `session` turning null. Nothing else writes the token, and the HTTP client reads it from
  `session`. A network session end names the token it was about, so a late 401 can't end a newer session. The app-lock PIN is stored only as a
  salted hash (`AppLockStorage`).
- Repositories take `HttpClient` (and `TokenStorage` if needed) via constructor and return `Result<T>`. Map HTTP
  errors to the sealed `ApiException` (ValidationError, Unauthorized, NotFound, Conflict, RateLimit, ServerError,
  NetworkError…). DTOs tolerate `null` in list fields and unknown keys; one bad item must not drop the whole list.
- **Times:** sent as UTC ISO-8601; **displayed in the doctor's practice time zone** (`doctor_profiles.practiceTimeZone`,
  IANA id), never in device-local time unless the spec says so. `utils/Dates.kt` takes the zone explicitly. Device zone
  is only used for the "your device differs from the schedule zone" hint.
- Debug builds have Profile → Debug menu: demo data (`data/debug/DemoBackend.kt`, a `MockEngine` behind
  `httpClient`, writes never reach production) and fake pushes of each type. A new endpoint gets a `DemoBackend`
  handler too; without one, demo GETs read production and other calls are refused. `DemoBackendTest` covers it.
- Logging: errors only, never successes. Every failed backend call is logged through `httpLogger` (Logcat tag
  `HttpClient` on Android, the Xcode console on iOS). `apiCall` already does it: `FAILED <status> <method> <path with ids
  masked> <code> requestId=<id>`, `FAILED no response: <exception class>`, or an unreadable body. Network code outside
  `apiCall` (SSE streams, polling) logs its failures the same way. Debug builds only (release logs nothing); never log
  tokens, passwords, headers, request bodies or patient data (HIPAA: patient ids only).
- Push: Firebase Messaging on both platforms; the token is sent (`PUT /doctors/me/fcm-token`) after login and on every
  refresh, logout sends `""`. Taps (foreground, background and cold start) route like the inbox tray. Real time is
  REST polling, no websockets.

### UI layer
- One `XxxUiState` `@Stable data class` per screen with defaults; derived flags as properties (`isInputValid`).
- ViewModel: constructor-injected deps with default values (no DI framework), `private val _state =
  MutableStateFlow(XxxUiState())`, `val state: StateFlow<…> = _state.asStateFlow()`, updates via
  `_state.value = _state.value.copy(...)`, async work in `viewModelScope.launch`, `result.fold(onSuccess, onFailure)`
  mapping the failure to a localized `StringResource` with `e.toUserMessage()`.
- Screen split in two:
  - `XxxScreen(viewModel = viewModel { XxxViewModel() }, onNavigate… callbacks)` — collects state, wires callbacks.
  - `internal fun XxxScreenContent(state, onXxx: () -> Unit, …)` — stateless, used by previews and screenshot tests.
- Navigation: simple state (`rememberSaveable`) in `App.kt` / `MainScreen` + `Scaffold` bottom bar. No nav library.
  Back goes page → first tab → exit.
- No raw hex/dp/sp in screens or widgets. Tokens live in `ui/theme/` and are named 1:1 after the tokens in
  `docs/design/tokens.json` and `shifa-ds.css` (the same names the patient app's theme uses):
  `ShifaColors.<Text|Icon|Bg|Border|Interactive|Alpha>.X` (`semantic/color/*`), `ShifaColors.Raw.<Component><Part>`
  for values the design sets without a token, `MaterialTheme.typography.*` + `ShifaTextStyles`,
  `ShifaSpacing.<Inset|Gap|Stack>.X` (`semantic/space/*`), `ShifaSize`, `ShifaRadius.*`, `ShifaShadow.*` (via
  `Modifier.dropShadow(shape, shadow)`), `ShifaDimens.<Component><Part>` for raw design sizes. Shared components come
  from `ui/widgets`. Use `Modifier.lineBox(style)` where text height must match the design's line boxes.
- Icons come from the design's icon library: reference `Res.drawable.ic_<name>_<size>` (`_filled`, `_bold`) and run
  `node scripts/design_icons.js`, which writes the vectors from the `ic` map in `docs/design/boards/DaApp.dc.html` and
  deletes unused ones. Images (`img_*`) are exports in `composeResources/drawable`.
- Strings: the source is `docs/kmp-spec/strings/{en,uz,ru}.json` + `plurals.json`; `python3 scripts/strings_from_json.py`
  generates `composeResources/values{,-ru,-uz}/strings.xml` and `plurals.xml` (never edit those by hand). Kit-only
  labels that are not in the JSON live in the hand-written `strings_kit.xml`. Keys are camelCase, placeholders become
  `%1$s` numbered in English order. CMP doesn't unescape `\'`, `\"` or `%%`, so the generator writes them literally;
  only `&`, `<`, `>` are XML-escaped. **Resolve strings with `tr(Res.string.x, args)` / `trPlural(Res.plurals.x, n,
  args)` from `utils/AppLocale.kt`, never `stringResource`**: they transliterate `uz` to Cyrillic when the doctor picked
  Ўзбекча (`LocalUzbekCyrillic`), template first, arguments after. Screen-reader labels come from strings too.
  `StringsTest` checks keys, plurals and placeholders across locales. The language (`LanguageStorage`: en, uz,
  uz-Cyrl, ru; Uzbekistan defaults to uz) is applied by `ProvideAppLocale` in `App.kt` and synced to the backend
  with `PUT /doctors/me/language` (wired with the HTTP client).
- Loading: every first load shows skeletons (`ShifaSkeleton`, `ShifaCardSkeleton`, `ShifaListSkeleton`…), not a
  spinner. Errors show inline in a `ShifaBanner`, never a SnackBar. Lists pull to refresh.
- Avatars without a photo, or whose photo fails to load, show the brand fallback picture (`ShifaAvatar`).
- Keyboard: `MainActivity` is `adjustResize` and screens pad for the keyboard themselves (`imePadding`).
- Trays and pop-ups (`ShifaTray`, `ShifaPopUp`) only draw; host them in `ModalBottomSheet` / `Dialog`.
- When the design boards lack a state or component the spec needs (lockout banner, empty state, error), build the
  smallest thing in the existing style and note "not in the design" in `docs/implemented/`.

### Tests
- Every ViewModel gets `XxxViewModelTest`: `StandardTestDispatcher` + `Dispatchers.setMain`, mocked repositories,
  `runTest` + `advanceUntilIdle`, assert with Turbine `state.test { awaitItem() }` + Truth. Class under test named
  `classToTest`, backtick test names.
- Every widget gets `XxxScreenshotTest` (`@RunWith(RobolectricTestRunner)`, `@GraphicsMode(NATIVE)`,
  `@Config(sdk = [33])`, `captureRoboImage("screenshots/Xxx_state.png")`). Previews live in
  `androidMain/.../ui/<feature>/XxxPreviews.kt` (widgets use `Gallery`, screens use `PreviewTheme`); both force
  inspection mode so Compose resources load under Robolectric. Screenshots are committed in `composeApp/screenshots/`.
- Pure logic ported from Dart tests (`consecutive_slot_range`, dental chart codec, treatment-plan initial payment,
  profession catalog, doctor display name) gets plain JUnit tests first; they are the behavioural contract.
- Commands: `./gradlew :composeApp:testDebugUnitTest`, `verifyRoborazziDebug`, `recordRoborazziDebug`.
- After a feature: compile both platforms (`assembleDebug`, `linkDebugFrameworkIosSimulatorArm64`), run only that
  feature's tests and record only its new screenshots, then report. Don't run the full screenshot suite unprompted;
  the user tests the UI and asks for a full run when they want one.

### CI/CD (GitHub Actions, ported from this repo's Flutter workflows like the patient app's; deploys are manual `workflow_dispatch`)
- `ci.yml`: runs on every pull request (and by hand): ktlint, unit tests, Roborazzi verify. The deploy workflows
  don't run tests.
- `google_play_deploy.yml`: signed `bundleRelease` → Google Play (`com.shifa.doc`). Signing via root
  `keystore.properties` written by CI (gitignored); versionCode = `VERSION_CODE_OFFSET + run_number` (set the offset
  above the Flutter app's last versionCode, which was `1 + run_number`). Keep the existing `track` input
  (internal / beta) and add alpha / production, defaulting to `internal`, so a run does not replace the Flutter app
  for every doctor before testers have it.
- `ios_testflight.yml`: builds the Kotlin framework (`linkReleaseFrameworkIosArm64`) in its own step so Kotlin errors
  show in full, then the fastlane `beta` lane in `iosApp/fastlane` → TestFlight (`uz.shifa.doc`, team `NH97PGA9PT`).
  Build number = `BUILD_NUMBER_OFFSET + run_number`; version from `MARKETING_VERSION`. Keep the "Prepare App Store
  Connect API key" whitespace-trimming step from the Flutter workflow.
- Version name lives in one place: `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`;
  `composeApp/build.gradle.kts` reads it for Android's `versionName`. Bump it there only.
- iOS: `ITSAppUsesNonExemptEncryption = false` in Info.plist (already set); the app icon has no alpha channel
  (App Store Connect rejects transparent corners). Keep the usage descriptions: camera, microphone, photo library
  (+ add), location when-in-use, Face ID; `UIBackgroundModes` as in the Flutter plist.
- Android permissions as in the Flutter manifest: INTERNET, CAMERA, RECORD_AUDIO, MODIFY_AUDIO_SETTINGS,
  POST_NOTIFICATIONS, USE_BIOMETRIC.
- Secrets: the ones already in this repo (note the `DOCTOR_` prefix on the Android/Firebase/Play ones):
  `DOCTOR_FIREBASE_ANDROID_CONFIG_BASE64`, `DOCTOR_FIREBASE_IOS_CONFIG_BASE64`, `DOCTOR_ANDROID_KEYSTORE_BASE64`,
  `DOCTOR_ANDROID_KEYSTORE_PASSWORD`, `DOCTOR_ANDROID_KEY_ALIAS`, `DOCTOR_ANDROID_KEY_PASSWORD`,
  `DOCTOR_GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64`, `IOS_CERTIFICATE_P12_BASE64`, `IOS_CERTIFICATE_PASSWORD`,
  `APP_STORE_CONNECT_KEY_ID`, `APP_STORE_CONNECT_ISSUER_ID`, `APP_STORE_CONNECT_API_KEY_P8`.

## Working with the user

- Read `docs/implemented/README.md`, `17-master-plan.md` (the milestone row), the flow files, the feature's spec doc,
  `15-api-reference.md`, `16-implementation-plan.md` (the ticket) and the design board before writing code.
  Implement one ticket group at a time.
- Decisions the user makes (a dropped feature, a chip removed, a flow changed) are recorded in
  `docs/implemented/README.md` with the date and "the user's call", so they aren't rebuilt later.
- End every job with: what changed, which design boards and flow files it covers, what's not built and why, the
  targeted test results, and the question whether to commit.
