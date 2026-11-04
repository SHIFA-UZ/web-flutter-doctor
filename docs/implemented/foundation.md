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
