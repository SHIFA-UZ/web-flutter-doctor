# Shifa Doctor App

The Shifa app for doctors and clinic staff: calendar and schedule, patients and their records, visits (in person and
video, dental chart, notes, PDFs), chat, notifications, remote-care tasks, Shifa Intelligence, the clinic workspace
(finance, treatment plans), care partnerships and reports. Kotlin Multiplatform with Compose Multiplatform, for
Android and iOS; a web build from the same code is milestone M14 (`docs/kmp-spec/18-web-milestone.md`).

This repository held the Flutter doctor app until the rewrite; that code was removed on `doc-app2.0` (WEB-01) and stays
readable on `main` until the merge. The admin panel (`admin.shifa.uz`) is a separate web product.

## Tech stack

- Kotlin Multiplatform, one module `composeApp` (package `shifa.doctor`), Compose Multiplatform + Material 3
- MVVM + Repository, no DI framework, no navigation library
- Ktor client, kotlinx.serialization, kotlinx.coroutines, kotlinx.datetime
- Multiplatform Settings (secure storage for the token), Kamel for images
- Firebase (Messaging), Daily for video calls
- Tests: JUnit4, Truth, Turbine, Roborazzi + Robolectric screenshots; ktlint

Versions live in `gradle/libs.versions.toml`, shared with the patient app (`SHIFA-UZ/apps-flutter-patient`).

| | |
|---|---|
| Android application id | `com.shifa.doc` |
| iOS bundle id | `uz.shifa.doc` (team `NH97PGA9PT`) |
| Backend | `https://api.shifa.uz/api` (debug builds can switch to `https://api-dev.shifa.uz`) |
| Locales | English, Uzbek (Latin and Cyrillic), Russian |

## Getting started

Requirements: JDK 17, Android SDK, Xcode (for iOS).

```bash
./gradlew :composeApp:assembleDebug                       # Android debug APK
./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64 # iOS framework; then open iosApp/ in Xcode
./gradlew :composeApp:testDebugUnitTest                   # unit tests
./gradlew :composeApp:verifyRoborazziDebug                # unit tests + screenshot comparison
./gradlew :composeApp:ktlintCheck                         # style (ktlintFormat fixes)
```

`composeApp/google-services.json` and `iosApp/iosApp/GoogleService-Info.plist` are not committed; CI writes them from
secrets. Debug builds have a debug menu (Profile → Debug) with demo data and the development backend.

## Strings and icons

- Strings: edit `docs/kmp-spec/strings/{en,ru,uz}.json`, then `python3 scripts/strings_from_json.py` writes
  `composeApp/src/commonMain/composeResources/values*/strings.xml`.
- Icons: referenced as `Res.drawable.ic_<name>_<size>`; `node scripts/design_icons.js` writes them from the design.

## CI/CD

- `.github/workflows/ci.yml`: ktlint, unit tests and screenshots on pull requests.
- `google_play_deploy.yml`: signed bundle to Google Play (`workflow_dispatch`, track input).
- `ios_testflight.yml`: Kotlin framework, then fastlane `beta` to TestFlight (`workflow_dispatch`).
- Version name: `MARKETING_VERSION` in `iosApp/Configuration/Config.xcconfig`.

## Documentation

- `CLAUDE.md`: the rules for this repository (architecture, conventions, workflow).
- `docs/kmp-spec/`: the specification (API in `15-api-reference.md`, the plan in `16`/`17`, web in `18`).
- `docs/ux-flows/`: the flows; `docs/design/`: the design canvas (tokens, boards).
- `docs/implemented/`: what is built, per ticket; `docs/backend-improvements.md`: what the app needs from the backend.

## License

Private and proprietary. All rights reserved.
