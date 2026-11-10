# PostHog tracking: runbook for the overnight run

Written 2026-11-10 for whoever (person or model) continues this work. Read `00-plan.md` first (decisions and chunks),
then `03-developer-guide.md` (how one ViewModel is instrumented), then this file. `01-feature-inventory.md` is the map
of every feature; `02-patient-app-recipe.md` is how the patient app does it.

## 1. State at hand-over

- Repo `web-kmp-doctor-app`, branch `feature/posthog-tracking`, cut from `main` at `08264ed`.
- **Nothing is committed yet.** The working tree holds `docs/posthog/` (plan, inventory, recipe, guide, this file) and
  chunk **A1** (the tracking core), built and checked:
  - `composeApp/src/commonMain/kotlin/shifa/doctor/data/tracking/**` (new)
  - `composeApp/src/commonMain/kotlin/shifa/doctor/ui/widgets/TrackScreenView.kt` (new)
  - `composeApp/src/commonMain/kotlin/shifa/doctor/data/api/ErrorMessages.kt` (`toFailureReason()` added)
  - `composeApp/src/androidUnitTest/kotlin/shifa/doctor/data/tracking/**` (new, 42 tests)
  - `docs/implemented/tracking/README.md` (new)
- A1 checks that passed: `ktlintCheck`; `testDebugUnitTest --tests 'shifa.doctor.data.tracking.*'` (42 tests);
  `compileKotlinIosSimulatorArm64`; `compileKotlinWasmJs`. The full test suite was not run.
- No SDK is added yet, no ViewModel is instrumented, no build file, workflow, `App.kt` or shell file is changed.

## 2. What the owner has allowed, and what not

- **Allowed (2026-11-10):** commit each finished chunk **locally** on `feature/posthog-tracking` (and on the per-job
  branches below) once its checks pass. This is what lets the jobs run in parallel.
- **Not allowed:** pushing, opening a pull request, merging into `main`, force-pushing, changing git config. The owner
  reviews the result in the morning and approves the push.
- Every commit is authored by the repo's git identity only. No `Co-Authored-By`, no "Generated with", no AI or agent
  attribution anywhere (messages, branch names, docs).
- Conventional Commits, lowercase, imperative, at most 72 characters, e.g. `feat(tracking): add the android sink`.
- Never print `~/.gradle/gradle.properties` (it holds secrets). Never put a PostHog key in the repo: the key comes from
  the GitHub secret `POSTHOG_API_KEY_PROD` and from gitignored local files.
- Never sign in to production. Development is never tracked (plan, section 3), so there is nothing to send while
  developing; use a recording sink and the debug menu's event list to see events.

## 3. Order of work

```
commit A1  ─►  A2, A3, A4, A5 in parallel  ─►  merge  ─►  A6  ─►  A7  ─►  B1 … B9 in parallel  ─►  merge  ─►  C1  ─►  final checks
```

Stage C2–C4 (device checks with a real key, PostHog dashboards, store privacy forms) need the owner and are **not**
part of the overnight run.

### Step 0: commit A1

In the main checkout: run the four A1 checks again, then one commit with everything in the working tree:
`feat(tracking): add the tracking core and the posthog plan`.

### Parallel jobs: one worktree per job

Gradle must never run twice at once in the same folder, so each parallel job gets its own worktree and branch:

```
git worktree add .worktrees/tracking-<job> -b feature/posthog-tracking-<job> feature/posthog-tracking
```

`local.properties` is gitignored: copy it from the main checkout into each worktree. Each job commits on its own
branch. When a wave is done, merge the job branches into `feature/posthog-tracking` in the order listed (`git merge
--no-ff`; no rebase, no force), run the checks of section 5 on the merged result, then remove the worktrees and delete
the job branches. The wasm link needs about 6 GB: do not run more than two wasm builds at the same time.

### Wave A (after A1)

Binding for A2, A3, A4 and A5 (plan, section 4): `app = doctor` must be on **every** event including the SDK's own
first install/open events (register it when the SDK starts, or send those events by hand), and every doctor and staff
person gets the person property `app = doctor` at identify. The patient dashboards and cohorts already filter on both.

| Job | Deliver | Files it owns | Checks |
|---|---|---|---|
| A2 Android | `PostHogSink` implementing `TrackingSink` with posthog-android (same version as the patient app: 3.75.1; add it to `gradle/libs.versions.toml`), started from `ShifaDoctorApp`; key from `BuildConfig.POSTHOG_API_KEY` (`-Pposthog.apiKey.prod` or `local.properties`; debug builds hard-wire an empty key and never start the SDK); host `https://eu.i.posthog.com`; replay, autocapture and automatic screens off; lifecycle events and exception capture on; `reset()` must also drop queued events; pass the key in `google_play_deploy.yml` | `androidMain/**/tracking/**`, `ShifaDoctorApp.kt`, `composeApp/build.gradle.kts`, `gradle/libs.versions.toml`, `google_play_deploy.yml` | unit tests for the mapping; `assembleDebug`; `assembleRelease` compile if signing allows, else `compileReleaseKotlinAndroid` |
| A3 iOS | Swift `PostHogAnalytics` implementing a Kotlin interface (the pattern of `DailyVideoCall` → `NativeVideoCall`), registered from `AppDelegate`; posthog-ios through SPM (patient app: 3.93.3); key from gitignored `Secrets.xcconfig` → `Info.plist`, written by `ios_testflight.yml`; booleans must reach PostHog as booleans, not 1/0 (patient-app pitfall, fixed in Swift there: copy it); `PrivacyInfo.xcprivacy` | `iosMain/**/tracking/**`, `iosApp/**`, `ios_testflight.yml` | `linkDebugFrameworkIosSimulatorArm64`; `xcodebuild` for the simulator. Never commit local edits of `iosApp.xcscheme` |
| A4 Web | Sink over posthog-js as an **npm dependency bundled with the app** (no CDN script, nothing inline in `index.html`), through JS interop in `wasmJsMain`; `Tracking.start(sink, webHost = window.location.hostname)`; replay and autocapture off; add `https://eu.i.posthog.com` to `connect-src` in `.htaccess` (the policy is report-only today; keep it that way); the key reaches the bundle at build time and `web_cpanel_deploy.yml` passes it; run `kotlinWasmUpgradeYarnLock` | `wasmJsMain/**/tracking/**`, `wasmJsMain/**/main.kt` (start call only), `.htaccess`, `kotlin-js-store/**`, `web_cpanel_deploy.yml`; the web part of `composeApp/build.gradle.kts` (coordinate with A2: A2 merges first, A4 merges second and resolves) | `compileKotlinWasmJs`; `wasmJsBrowserDistribution`; load the bundle on localhost in headless Chrome and confirm no request goes to PostHog (localhost is not the production host) |
| A5 Identity | In `App.kt`'s session collector: `Tracking.identify(role, userId, …)` when a session exists and the profile has loaded (id from the JWT subject; `TrackingRole.Doctor` / `Staff`; **an ADMIN token is never identified or tracked**), `Tracking.reset()` on every session end (all seven `SessionEndReason`s) with `SessionEnded(reason)` sent before the reset, `SessionStarted(role)`; person properties: role, tier, profession, practice time zone, language, `has_clinic`, `clinic_role` (no ids, decision D4); `Tracking.refresh()` when demo mode or the backend choice changes | `App.kt` (session part only), a new `data/tracking/TrackingSession.kt` | ViewModel-style tests with `RecordingSink` for each session end and for an id change |

Merge order: A2, A4, A3, A5, A1b.

### A1b (with wave A, its own worktree; merge after A5)

A general `api_error` event from `data/api/ApiHelper.kt` (`apiCall`), copying the patient app's `ApiError`
(`apps-flutter-patient/.../data/analytics/AnalyticsEvent.kt` and its `ENDPOINT_WORDS` labelling): properties `endpoint`
(word-level label, ids masked, never the raw path), `method`, `status`, `error` (from `toFailureReason()`), `code`.
Define it in a new `data/tracking/events/ApiEvents.kt`. It must not fire for a request that never carried the app's
own backend (file URLs, third parties), and never in a loop from the tracking calls themselves. Tests with MockEngine.
Owns `data/api/ApiHelper.kt` for this wave.
The catalogue test rejects a key named `code` (it guards against one-time codes and PINs). `api_error.code` is the
backend's error code, not a secret: add one explicit, commented exception for `api_error` in the test rather than
renaming the property, so the name matches the patient app's.

**Event names fixed by the alert plan (plan, "Alerts"): do not rename them.** `api_error` (A1b), `booking_failed(reason)`
in B3 for every booking path, `video_join_failed(reason)`, `visit_end_failed(reason)` and `scribe_failed(reason, stage)`
in B6. They share names with the patient app on purpose.

### A6 then A7 (one after the other, in the main checkout)

- **A6 screen views.** One hook in `ui/navigation/MainScreen.kt` beside `shellPath` mapping `ShellUiState` (tab × route)
  to a fixed `TrackScreen` id; signed-out screens in `App.kt`; `TabSelected`, `MoreOpened`, `TabRefused`; `PushOpened`
  in `ShellViewModel.openPush` with the push **type only**. Never send `route.toString()` or the web path: routes hold
  names, e-mail addresses and ids. Watch for double counting with the sub-view ids the B jobs send (for example the
  shell sends `patient`, B4 sends `patient_overview`): decide one owner per id and write it in the guide. Owns
  `ShellViewModel` and its test.
- **A7 debug tools.** In `ui/debug/`: a "Tracking" row showing `Tracking.status` (on, or off with the reason) and a
  list of the last events and screens (kept in memory in debug builds only). Update `03-developer-guide.md`.

### Wave B (after A7 is merged): nine jobs, no shared files

Each job: add `tracker: Tracker = Tracking` as the **last** default constructor parameter of each ViewModel in its
folders; define its events in its own file `data/tracking/events/<Group>Events.kt`; fire one event per meaningful
outcome (success, failure with `toFailureReason()`, cancel) from the ViewModel; add `TrackScreenView(...)` in
`XxxScreen` (never in `XxxScreenContent`) for its sub-views; add one test per outcome event with `RecordingTracker`;
write its notes to `docs/implemented/tracking/<group>.md`. Use section 2 of `01-feature-inventory.md` as the checklist
of actions and outcomes for its folders.

| Job | Folders it owns (`ui/…`) | Event file |
|---|---|---|
| B1 | `splash`, `login`, `emailcode`, `register`, `auth`, `applock` | `AuthEvents.kt` |
| B2 | `home`, `notifications`, `analytics` (Reports), `tasks` | `HomeEvents.kt` |
| B3 | `calendar`, `schedule` | `CalendarEvents.kt` |
| B4 | `patients`, `care` | `PatientsEvents.kt` |
| B5 | `documents`, `forms` | `DocumentsEvents.kt` |
| B6 | `visit`, `speech` | `VisitEvents.kt` |
| B7 | `chat`, `intelligence` | `ChatEvents.kt` |
| B8 | `clinic` | `ClinicEvents.kt` |
| B9 | `profile` | `ProfileEvents.kt` |

Rules for every B job:

- Touch only your folders, your event file, your tests (`androidUnitTest/.../ui/<folder>/`), your previews if a
  constructor changed, and your notes file. **Never** edit `App.kt`, `ui/navigation/*`, `data/api/*`,
  `data/tracking/*.kt` outside your event file, `TrackScreen.kt`, build files, strings, `docs/implemented/README.md`.
  If you need a screen id that is missing or an "opened from" property from the shell, write it under "Needs from the
  coordinator" in your notes file instead of editing the shared file.
- B9 is the one exception for strings: it builds the opt-out switch in Settings (decision D3) and adds its strings to
  `docs/kmp-spec/strings/{en,uz,ru}.json`, then runs `python3 scripts/strings_from_json.py`. Its ViewModel takes the
  opt-out getter and setter as injected lambdas so the test needs no platform storage.
- Never a name, phone, e-mail, free text, search text, prompt, message, document title, id or exact amount in a
  property. Money goes through `amountBucket`, counts through `countBucket`. The catalogue test enforces this; do not
  weaken it. A key named `code` is rejected: use `error_kind` or similar.
- Event names are `snake_case`, past tense for outcomes (`appointment_booked`, `appointment_book_failed`), unique across
  the whole app; prefix with the feature when in doubt.
- Shared classes used by several groups stay with the group that owns the folder: `Dictation` (B6), `NotificationOpener`
  (B2), `PatientPickerSheet` / `CreatePatientSheet` (B4).
- Checks before the job commits: `ktlintCheck`; `testDebugUnitTest --tests 'shifa.doctor.ui.<folder>.*'` for each of
  its folders and `--tests 'shifa.doctor.data.tracking.*'`; `compileDebugKotlinAndroid`.

Merge order: B1 … B9. After the merge the coordinator applies the "Needs from the coordinator" items (new screen ids,
shell properties) in one commit.

### C1 (after the merge)

Generate `docs/posthog/05-event-catalogue.md` from the code: one row per event (name, properties with types, the
ViewModel that fires it) and one per screen id. Update `docs/implemented/README.md` once (status table, one log line),
and `docs/implemented/tracking/README.md`.

## 4. Open points the run must not decide alone

- **Money ranges.** `amountBucket` uses UZS ranges 0, <100k, 100k–500k, 500k–1m, 1m–5m, 5m–20m, >20m and `foreign`
  for other currencies. The owner has seen them and not answered. Keep them, and list every event that uses them in
  the morning report so they can still be changed before release.
- **PostHog project:** the patient dashboards, alert charts and cohorts already exclude doctor data (done 2026-11-10).
  Doctor dashboards and the alert slots are the owner's, as is the GitHub secret.
- **Legal text** for the opt-out and the store privacy forms: the owner's. B9 builds the switch with a neutral label
  and marks the sentence as "wording to be confirmed" in its notes.
- Anything that would need a backend change: write it to `docs/backend-improvements.md`, do not work around it.

## 5. Checks on the merged branch (after wave A, and again at the end)

Run one at a time, in the main checkout:

1. `./gradlew :composeApp:ktlintCheck`
2. `./gradlew :composeApp:testDebugUnitTest` (the whole suite; it was 1148 tests before this work)
3. `./gradlew :composeApp:assembleDebug`
4. `./gradlew :composeApp:linkDebugFrameworkIosSimulatorArm64`
5. `./gradlew :composeApp:wasmJsBrowserDistribution -Pkotlin.daemon.jvmargs=-Xmx8g`
6. `./gradlew :composeApp:verifyRoborazziDebug` only if a screen's look changed (B9's Settings row does: record that
   one screenshot, on macOS).

Then an independent review of the whole diff (`git diff main...feature/posthog-tracking`) looking for: personal data in
properties, events fired from composables instead of ViewModels, tracking that could run in debug, demo or development,
a missing reset on a session end, and double screen counting. Fix what it finds, with a test each.

## 6. Morning report (what the owner expects)

- What was built per chunk, with the commit hashes on `feature/posthog-tracking`.
- Test counts and the result of each check in section 5.
- The number of events and screens, and the list of events that carry money ranges.
- Everything left open: section 4, "Needs from the coordinator" items that were not applied, review findings not
  fixed, anything not verified (device runs, a real key, the web policy on the real host).
- The compare link for the owner to open after approving the push:
  `https://github.com/SHIFA-UZ/web-flutter-doctor/compare/main...feature/posthog-tracking?expand=1`
