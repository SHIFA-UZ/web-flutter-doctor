# Tracking (PostHog): status

Plan: `docs/posthog/00-plan.md`. How to instrument a feature: `docs/posthog/03-developer-guide.md`. Each chunk and
feature group adds its own file in this folder; the coordinator keeps this table and `docs/implemented/README.md`.

| Chunk | What | Status |
|---|---|---|
| A1 | Core in `data/tracking` | Built 2026-11-10, not committed |
| A2 | Android sink | Not started |
| A3 | iOS sink | Not started |
| A4 | Web sink | Not started |
| A5 | Identity and session | Not started |
| A6 | Screen views from the shell | Not started |
| A7 | Debug menu | Not started |
| B1–B9 | Feature events | Not started |
| C1–C4 | Catalogue document, device checks, dashboards, store forms | Not started |

Nothing is sent by any build yet: no SDK is attached, so `Tracking` is inert.

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
- No lock in `Tracking`: every caller is on the main thread today.
