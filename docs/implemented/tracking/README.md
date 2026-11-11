# Tracking (PostHog): status

Plan: `docs/posthog/00-plan.md`. How to instrument a feature: `docs/posthog/03-developer-guide.md`. Each chunk and
feature group adds its own file in this folder; the coordinator keeps this table and `docs/implemented/README.md`.
Every event, its properties and where it fires, and every screen id: `docs/posthog/05-event-catalogue.md`.

| Chunk | What | Status |
|---|---|---|
| A1 | Core in `data/tracking` | Done 2026-11-10 (`a987f8e`) |
| A1b | `api_error` / `api_slow` from `apiCall` (`ApiEvents.kt`, `ENDPOINT_WORDS`) | Done 2026-11-10 (`c845d42`) |
| A2 | Android sink (`PostHogSink`) | Done 2026-11-10 (`8aa3a89`; key read once for Android and web: `5986db2`) |
| A3 | iOS sink (Swift over posthog-ios, `TrackingBridge`) | Done 2026-11-10 (`c1833f0`) |
| A4 | Web sink (`PostHogWebSink`, posthog-js) | Done 2026-11-10 (`6c9a8e0`) |
| A5 | Identity and session (`TrackingSession`) | Done 2026-11-10 (`f26b6e0`; queue kept on a session end: `6451979`) |
| A6 | Screen views from the shell and `App.kt`; tab, More and push events | Done 2026-11-10 (`4b4249d`) |
| A7 | Debug menu: tracking status and recent events | Done 2026-11-10 (`e260cc0`) |
| B1 | Sign-in, registration, app lock: [auth.md](auth.md) | Done 2026-11-10 (`8a2d63b`) |
| B2 | Home, inbox, Reports, Tasks: [home.md](home.md) | Done 2026-11-10 (`69df016`) |
| B3 | Calendar, booking, schedule, locations: [calendar.md](calendar.md) | Done 2026-11-10 (`00d34ea`) |
| B4 | Patients and care: [patients.md](patients.md) | Done 2026-11-10 (`02132c2`) |
| B5 | Documents, forms, clinical engine: [documents.md](documents.md) | Done 2026-11-10 (`68c8664`) |
| B6 | Visit, video, scribe, dictation: [visit.md](visit.md) | Done 2026-11-10 (`0ef3e93`) |
| B7 | Chat and Shifa Intelligence: [chat.md](chat.md) | Done 2026-11-10 (`eaef87d`) |
| B8 | Clinic workspace: [clinic.md](clinic.md) | Done 2026-11-10 (`d0561db`) |
| B9 | Profile, billing, the usage-data switch: [profile.md](profile.md) | Done 2026-11-11 (`7d79ef9`); the feature jobs' shared requests (screen ids, shell properties): `b749a8a` |
| Review fixes | Independent review of the whole diff: real outcomes only, the visit alert, no exception messages or web addresses, catalogue professions, stale identities, start-tab screen views, `push_opened`; round 2: stale identity dropped at SDK start, known push and inbox types only | Done 2026-11-11 (`15f21fb`, `696c706`, `f56978c`, `dc9cbb5`, `fda6fb4`, `f5f0440`) |
| C1 | Event catalogue generated from the code: [05-event-catalogue.md](../../posthog/05-event-catalogue.md) (262 events, 71 screen ids) | Done 2026-11-11 |
| C2–C4 | Device checks with a real key, doctor dashboards, store privacy forms | Not started (the owner's) |

The three SDKs are attached when a key is present; only a release build on the production backend (and on the web
`doctor.shifa.uz`) sends. `Tracking` is synchronized (A1b calls it from repository dispatchers).

**Known limits:** iOS and the web cannot drop an offline retry backlog at an opt-out (only Android clears its queue);
anonymous splash and boot events are sent before a stored ADMIN token is rejected (admins are never identified).

## A1: the core (2026-11-10)

No design board and no flow file: tracking has no UI in this chunk.

**Built** (`composeApp/src/commonMain/kotlin/shifa/doctor/`):

- `data/tracking/TrackingSink.kt`: what a platform SDK does (`capture`, `screen`, `identify`, `reset`, `register`,
  `optOut`, `flush`), `POSTHOG_HOST`, `TRACKING_APP = "doctor"`, `PRODUCTION_WEB_HOST`.
- `data/tracking/Tracking.kt`: `Tracker` (for ViewModels), `TrackingRole`, and `object Tracking : Tracker`: inert
  without a sink; `start`, `refresh`, `track`, `screen`, `identify`, `reset`, `register`, `setOptOut`, `optedOut`,
  `flush`, `status`.
- `data/tracking/TrackingStatus.kt`: the one on/off rule as the pure `trackingStatus()`, `TrackingOffReason`,
  `TrackingEnvironment` and the app's real values (`isDebugBuild`, `DemoMode.enabled`, `BackendChoice.current`,
  `platformName`).
- `data/tracking/TrackingSettings.kt`: the opt-out flag (`tracking_opt_out`, default allowed) and the id last
  identified (`tracking_identified`), in plain Settings.
- `data/tracking/TrackScreen.kt`: 71 fixed screen ids (signed-out screens, tabs, pushed routes, profile sections,
  patient record tabs, clinic sections, finance views).
- `data/tracking/TrackProperties.kt`: `trackProperties`, `Enum.trackName()`, `countBucket`, `amountBucket`.
- `data/tracking/events/TrackEvent.kt`: the sealed base. `SessionTrackEvents.kt` (`session_started`,
  `session_ended`) and `ShellEvents.kt` (`tab_selected`, `more_opened`, `tab_refused`, `push_opened`) for Stage A.
  Empty group files: `AuthEvents`, `HomeEvents`, `CalendarEvents`, `PatientsEvents`, `DocumentsEvents`,
  `VisitEvents`, `ChatEvents`, `ClinicEvents`, `ProfileEvents`.
- `data/api/ErrorMessages.kt`: `Throwable.toFailureReason()` beside `toUserMessage()`.
- `ui/widgets/TrackScreenView.kt`: the composable that sends a screen view once.
- Tests (`androidUnitTest/.../data/tracking/`): `TrackingTestDoubles.kt` (`RecordingTracker`, `RecordingSink`),
  `TrackingStatusTest`, `TrackingTest`, `TrackPropertiesTest`, `TrackEventCatalogueTest`. 42 tests.

**Choices made here** (not spelled out in the plan):

- Events live in the package `shifa.doctor.data.tracking.events`, `TrackEvent` included: a sealed class takes
  subclasses only from its own package. Each group nests its events in one object (`AuthEvents.SignInFailed`), so two
  groups can use the same class name; the event name itself must still be unique.
- The rule is applied before every call, not only when someone reports a change, so nothing depends on a collector.
- `TrackingSink` has no `distinctId()`; `Tracking` remembers the id it last identified in Settings instead.
- `capture` carries the person properties as two more maps (the native SDKs take them separately).
- On the web an unknown hostname counts as not production.
- Money ranges are in sums (`0`, `<100k`, `100k-500k`, `500k-1m`, `1m-5m`, `5m-20m`, `>20m`); any other currency is
  `foreign`. Counts: `0`, `1`, `2-5`, `6-20`, `21-100`, `>100`.
- `toFailureReason()` words: `network`, `timeout`, `validation`, `unauthorized`, `forbidden`, `not_found`,
  `conflict`, `rate_limited`, `server`, `client`, plus `account_disabled`, `session_expired`, `maintenance` from the
  backend codes that mean the same on every route.

**Not built, and why:**

- The debug menu's recent-events list: chunk A7 (it adds the list to `Tracking`).
- A global `api_error` event from `apiCall`: not in the plan's chunks; polling would flood it while offline.
- The opt-out switch and its strings: group B9.
- `Tracking` takes its own lock on every entry point: `api_error` / `api_slow` (A1b) call it from repository
  dispatchers (`PushRegistration` on `Dispatchers.Default`).

## A6: screen views from the shell (2026-11-10)

- `shellScreen(ShellUiState)` (`ui/navigation/ShellViewModel.kt`, beside `shellPath`) maps what the shell shows (a
  tab, a pushed route, a profile section, Shifa Intelligence or a screen opened over it) to a fixed `TrackScreen`;
  null for the debug menu. `MainScreen` sends it through `TrackScreenView`, keyed on the stack under it: More or a
  sheet over a screen sends nothing, Back to it or another tab and back sends it again. Android keeps the activity on
  configuration changes (`configChanges`), so a rotation sends nothing.
- `App.kt` sends the signed-out screens (`splash`, `sign_in`, `email_entry` / `email_code` with `mode`,
  `reset_password`, `verify_key`) and `lock`. `register` is B1's (its kind and step live in `RegisterViewModel`).
- One owner per screen id: the list is in `docs/posthog/03-developer-guide.md` §2.4.
- `ShellViewModel` (tracker before its trailing `scheduleRefresh` lambda): `tab_selected(tab, source)` from
  `tapTab` (the bar, More, the sidebar; other `select` calls such as Home's cards are not the doctor's tab choice),
  `tab_refused(tab)` when the plan or the clinic membership turns a tab into Home (also a gated tab picked before the
  plan was known), `more_opened`, `push_opened(type, routed_to)` once a tapped push's target is found (the patient
  app's names; `type` only from its data, a backend code or `other`, left out without one; `routed_to` `home` when
  its appointment is gone). No `cold_start`: the shell can't tell a cold start's tap from one that waited behind
  sign-in or the lock.
- Tests: `ShellScreenTest` (the mapping), two `ShellViewModelTest` cases (tab/More/refused, push).

## A7: debug menu (2026-11-10)

- `Tracking.recent`: the last 50 events and screen views, newest first, in debug builds only, recorded before the
  on/off rule and the sink check (a debug build never sends). `TrackingTest` covers it.
- `ui/debug/DebugTrackingSection.kt` in the debug menu: a "Tracking" row (*On*, or *Off: <reason id>*; a tap applies
  the rule again) and the recent list. Not in the design (debug only, English, built from `ShifaSection` /
  `ShifaSettingsItem` like the push checks). No screenshot test exists for the debug menu.
- `docs/posthog/03-developer-guide.md`: the debug list (§5) and the coordinator's notes (§5a: `ENDPOINT_WORDS`,
  `TrackingSession` owns identity, `reset(dropQueue)`).
