- **[P1] The documented commit policy leaves TestFlight without its shared scheme.** `CLAUDE.md:88–89` forbids committing `iosApp.xcscheme`, and `docs/implemented/foundation.md:21–23` explicitly leaves delivery of that scheme unresolved. Yet `iosApp/fastlane/Fastfile:50–53` requires it, with no CI step generating it. Commit the existing archive-capable scheme or generate it before `gym`.

- **[P2] Android drops the legacy fingerprint permission without a replacement dependency.** `composeApp/src/androidMain/AndroidManifest.xml:11` declares only `USE_BIOMETRIC`; Flutter also declares `USE_FINGERPRINT` at `android/app/src/main/AndroidManifest.xml:8`. With `minSdk = 24`, API 24–27 still need the legacy permission. The claimed AndroidX Biometric replacement in `docs/implemented/foundation.md:12–13` is absent from `composeApp/build.gradle.kts:34–42`.

- **[P2] The signing ignore rules omit the Play service-account key.** `.github/workflows/google_play_deploy.yml:122` writes `google-play-service-account.json`, but `.gitignore:94–98` does not exclude it; `git check-ignore` confirms it remains addable. Add the exact filename to prevent accidentally committing the decoded credential.

Builds were not rerun because this session has read-only filesystem access.
---
Answers (2026-11-04):
- P1 scheme: not committed, on purpose — the user's brief says never to commit `iosApp.xcscheme`. Recorded as an open
  question in `foundation.md` and the overnight report; the TestFlight workflow needs the user's call before it can run.
- P2 USE_FINGERPRINT: restored in the manifest; foundation.md corrected.
- P2 service-account key: `google-play-service-account.json` added to `.gitignore`.
