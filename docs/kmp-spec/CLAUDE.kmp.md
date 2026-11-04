# Project rules

> This file becomes the repo's `CLAUDE.md` on the `doc-app2.0` branch (ticket CORE-01). Until then it lives here so the
> Flutter `CLAUDE.md` keeps governing `main`. Values in `<angle brackets>` are still open; everything else was taken
> from the Flutter repo on 2026-10-24.

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
| Backend | `https://shifa-doc-backend-mvp-production.up.railway.app` (paths written without `/api`) |
| Design source | `docs/design/` (a verbatim copy of the Claude Design canvas: `tokens.json`, `shifa-ds.css`, 51 boards); read `docs/design/README.md` first. No Figma. |
| First `MARKETING_VERSION` | `1.1.0` (Flutter shipped `1.0.0+3`; build numbers were `1 + run_number`, set the offsets above the last Actions run) |
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
  gets the reduced shell described in `02-navigation-and-shell.md`.

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
  Do not create per-feature branches during the rewrite.
- After that merge, switch to the patient app's rule: before making any change, check the current branch; if the task
  has no branch yet, create one from `main` named `feature/<feature-name>` (kebab-case, e.g.
  `feature/schedule-management`) and do all the work there. Update this section when that happens.
- Never push to `main` directly or merge into it unless the user asks; changes reach `main` through a pull request.
- Never commit `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme`: the IDE rewrites it locally and
  drops the Archive action TestFlight builds need.
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
  SLF4J, which has no binding on Android, so nothing reaches Logcat. SSE (AI streams) uses Ktor's SSE plugin on the
  same client.
- kotlinx.serialization (JSON), kotlinx.coroutines, kotlinx.datetime
- Multiplatform Settings (`no-arg`) for local key/value; the token uses `secureSettings` (`KeychainSettings` on iOS,
  EncryptedSharedPreferences from `androidx.security:security-crypto` on Android)
- Kamel for async image loading
- JetBrains `lifecycle-viewmodel-compose` for ViewModels
- Firebase (google-services, Messaging for push, App Distribution for debug builds)
- Daily for video calls: `co.daily:client` on Android, Daily's Swift package on iOS behind
  `iosApp/iosApp/DailyVideoCall.swift` (it implements the Kotlin `NativeVideoCall`)
- Charts (home analytics, finance): `<decide in CORE-07: Vico multiplatform vs hand-drawn Canvas>`; PDF export:
  `<decide in CORE-07: backend-rendered PDF vs platform PDF APIs behind expect/actual>`
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
- `createHttpClient(tokenStorage, engine)` builds the Ktor client (engine defaults to `expect fun createHttpEngine()`);
  the app uses the shared `httpClient`. The token goes only to the backend host and never on `PUBLIC_AUTH_PATHS`.
- Calls go through `apiCall<T>(defaultMessage) { client.get(…) }` from `ApiHelper.kt`: returns `Result<T>`, non-2xx
  becomes an `ApiException` whose message is sanitized by `sanitizeError` (`ErrorMessages.kt`), no response becomes
  `NetworkError`. Error bodies are read leniently. **401 means logout. 403 logs out only when the JWT is expired or the
  body says `Account is disabled` / `Session invalid` / `Session expired or signed out`** (clinic staff legitimately get
  403 on doctor-only endpoints; see `01-architecture-and-core.md` §4).
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
  `HttpClient` on Android, the Xcode console on iOS). `apiCall` already does it: `FAILED <status> <method> <path>:
  <backend reason>`, `FAILED <message>, no response: <exception>`, or an unreadable body. Network code outside
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
- Icons and images are exports from the `docs/design/` boards (inline SVG icons of the design system) in
  `composeResources/drawable` (`ic_<name>_<size>`, `img_*`); reuse the patient app's exports where the icon is the same.
- Strings: `composeResources/values{,-ru,-uz}/strings.xml` (en is the fallback), keys in camelCase, placeholders as
  `%1$s`. CMP doesn't unescape `\'`, `\"` or `%%`, so write them literally; only `&`, `<`, `>` are XML-escaped.
  Screen-reader labels come from strings too. `StringsTest` checks keys and placeholders across locales. The in-app
  language override lives in `LanguageStorage`, is applied by `ProvideAppLocale` in `App.kt`, and is synced to the
  backend with `PUT /doctors/me/language`.
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
