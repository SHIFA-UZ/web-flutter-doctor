- **P2 — Diagnostics cannot identify the affected build or environment.** [AboutScreen.kt:74](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/AboutScreen.kt:74) copies only the marketing version, platform, language and timezone. Build number, OS/device details and API environment required by flow 08/13 are absent, so different builds/backends produce indistinguishable reports. [AboutTest.kt:8](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/androidUnitTest/kotlin/shifa/doctor/ui/profile/AboutTest.kt:8) claims to test build/environment but supplies neither.

- **P2 — Diagnostics lose the selected Uzbek script.** [AboutScreen.kt:69](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/AboutScreen.kt:69) reads the platform locale; [App.kt:88](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/App.kt:88) configures that using `language.resourceCode`, which is `uz` for both Latin and Cyrillic. Support therefore receives the same language for different UI scripts. Copy the selected `AppLanguage.tag`.

- **P2 — The content composable violates the required stateless screen split.** [AboutScreen.kt:95](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/profile/AboutScreen.kt:95) owns the gesture counter, and line 106 implements its transition inside `AboutContent`. Hoist the counter and version-tap callback into the screen state owner so the content receives state and emits actions, as required by the architecture spec.

---

Answers (2026-11-06):

- P2 diagnostics missing the build and environment: fixed. The line now carries the version with its build number
  (`appBuild`: Android versionCode, iOS CFBundleVersion), the platform with debug or release, the backend in use
  (production, development or demo), the app's language and the practice zone. Device model and OS version are left
  out (no platform reader exists; not needed to tell builds apart). Tested.
- P2 the Uzbek script lost: fixed. The app's own language tag (`uz-Cyrl` vs `uz`) instead of the platform locale.
- P2 state in the content composable: fixed. The tap counter lives in `AboutScreen`; `AboutContent` only emits
  `versionTap`.
