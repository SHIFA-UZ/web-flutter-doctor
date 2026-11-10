# PostHog in the doctor app: plan

Written 2026-11-10. Sources: `01-feature-inventory.md` (every feature of this app, with screens, actions and hooks) and
`02-patient-app-recipe.md` (how the patient app does it, its decisions and pitfalls).

Goal: product analytics for every feature of the doctor app on Android, iOS and web, built the way the patient app's is,
in chunks that can be worked on separately and mostly in parallel.

## 1. What carries over from the patient app

| Topic | Rule |
|---|---|
| Project | The existing PostHog project (EU, "Shifa", id 332380). Every event carries `app = doctor`. |
| Builds that report | Release builds talking to the production backend only. Debug builds, demo mode and anything on the development backend never send anything (section 3, decided). |
| Features of PostHog | Session replay off, autocapture off, automatic screen events off, surveys off. Lifecycle events and exception capture on. |
| Events | Written by hand, one per meaningful outcome, `snake_case`, defined as typed classes, sent from ViewModels. |
| Screens | One screen-view event per screen, from a fixed list of screen ids. |
| Failures | A reason code (`network`, `validation`, `forbidden`, …), never the message text. |
| Never sent | Names, phone numbers, e-mail addresses, free text (notes, chat, AI prompts, search text), document titles, tokens. |
| Admins | Not tracked. |
| Tests | A recording tracker in ViewModel tests; one test per outcome event; a catalogue test for naming and blocked keys. |

## 2. What is different here

1. **Web.** The patient app has no web build. The doctor web needs its own sink (posthog-js, bundled with the app, no
   CDN script) and `eu.i.posthog.com` added to the site's `connect-src`. The page is one canvas, so replay and
   autocapture stay off for a second reason.
2. **Two web sites, one bundle.** `doctor.shifa.uz` and `doctor-dev.shifa.uz` serve the same build and pick the backend
   at run time. Tracking is therefore decided at run time too: only the production hostname with the production
   backend reports. `doctor-dev.shifa.uz` never does (decided).
3. **Two kinds of user.** Doctors and clinic staff. Ids are `doctor_<userId>` and `staff_<userId>` (the JWT subject
   works for both). Person properties: role, tier, profession, practice time zone, language, whether in a clinic.
4. **Name clash.** `data/analytics` and `ui/analytics` are the Reports feature. The tracking layer lives in
   `data/tracking` with the names `Tracker`, `Tracking`, `TrackingSink`, `TrackEvent`.
5. **More patient data nearby.** Routes and web paths hold names, e-mail addresses and ids, so a screen view sends a
   fixed screen id and nothing from the route. A push open sends only its type.
6. **Size.** 70 ViewModels, about 190 real user actions, about 120 screens and sheets. Expect roughly 250–300 events
   against the patient app's 162.

## 3. Decisions

### Decided (the owner's call, 2026-11-10)

**Development is never tracked, on any platform.**

- Web: `doctor-dev.shifa.uz` sends nothing. Only `doctor.shifa.uz` on the production backend reports.
- Mobile: the app sends nothing while it talks to the development backend (`api-dev.shifa.uz`), whatever the build
  type. Debug builds send nothing at all, also when pointed at production.
- The rule is checked in one place in the tracking core (A1): tracking is on only when the build is a release build,
  demo mode is off, and the active backend is production. It is evaluated again whenever the backend changes, and a
  switch to development resets the PostHog identity so nothing queued is sent later.
- The debug menu's "would this build track?" row (A7) shows the answer and the reason.
- Tests cover each combination: release + production (on), release + development (off), debug + production (off),
  demo mode (off), `doctor-dev.shifa.uz` (off).

### Also decided (the owner's call, 2026-11-10)

| # | Question | Decision |
|---|---|---|
| D1 | May patient, appointment or document ids be sent as properties? | No ids at all (as in the patient app). Counts and types only. The catalogue test blocks any `*_id` key. |
| D2 | Money (prices, payments, debts)? | Ranges or nothing, never exact amounts. |
| D3 | Do doctors get an opt-out switch? | Yes, in Settings, on by default (opt-out), with one sentence in the privacy text. The patient app's legal sign-off covered patients only. |
| D4 | Clinic as a PostHog group? | No. No group analytics and no clinic id; a `has_clinic` and `clinic_role` person property only. |
| D5 | Do TestFlight and Play internal builds report? | Yes: they are release builds on the production backend, as in the patient app. |
| D6 | Native SDKs or one HTTP sink? | Native SDKs on Android and iOS (the patient app's proven setup), posthog-js on web. |

## 4. Before the doctor app sends anything (PostHog project)

**Done 2026-11-10 (through the PostHog API):** the patient side now excludes doctor data.

- The 8 "Patient · …" dashboards carry the filter: event property `app` is not `doctor`.
- The 5 charts behind the alerts carry the same filter (alerts ignore dashboard filters).
- The 15 patient cohorts carry one added condition: person property `app` is not `doctor`.
- The filter is "is not doctor" and not "equals patient", because the first events of a fresh install
  (`Application Installed`, the first `Application Opened`) arrive before the SDK has the `app` property.
- The earlier definitions are saved in `shifa/_claude_tmp/posthog-backup-2026-11-10/`.

**This makes two rules binding for the doctor app:**

1. **Every doctor event carries `app = doctor`, including the SDK's own first events.** Register the super property
   in the same step that starts the SDK, before lifecycle capture can fire (A2, A3, A4). If an SDK cannot do that,
   turn its automatic install/open events off and send them by hand after registering. Verify on a fresh install.
2. **Every doctor and staff person carries the person property `app = doctor`**, set at identify (A5). The patient
   cohorts rely on it.

Still the owner's:

- All five alert slots of the free plan are in use; doctor alerts need a decision (replace some, or upgrade).
- Add the GitHub secret `POSTHOG_API_KEY_PROD` to this repo (the same project key as the patient app).

### Alerts: five slots shared by both apps (the owner's call, 2026-11-10)

The free plan has five alerts, all used by the patient app today. They are re-balanced so the most critical failures
of both apps are covered. Three alerts cover both apps through one chart; this works because the doctor app sends
those failures **under the same event names as the patient app** (the `app` property tells them apart).

| # | Alert | Covers | Events |
|---|---|---|---|
| 1 | API errors spike | Both apps | `api_error` |
| 2 | Payments failing | Patient | `payment_failed` |
| 3 | Video visits failing | Both apps | `video_join_failed` |
| 4 | Booking failing | Both apps | `booking_failed` |
| 5 | Visit can't be completed | Doctor | `visit_end_failed` + `scribe_failed` |

"Shifa AI failing" loses its alert (its failures stay on the "Patient · Shifa AI" dashboard).

What the doctor app must therefore provide, with exactly these names:

- `api_error` (Stage A, chunk A1b below), with the patient app's property names: `endpoint` (a word-level label, never a
  path with ids), `method`, `status`, `error` (the failure reason), `code`.
- `video_join_failed(reason)` (job B6) and `booking_failed(reason)` (job B3; one name for every way an appointment is
  booked, with the entry point as a property).
- `visit_end_failed(reason)` and `scribe_failed(reason, stage)` (job B6).

**Done in PostHog 2026-11-10** (the charts already name the doctor events, so nothing is left to do there when the
events start arriving):

| Alert | Chart it watches | Limit per day |
|---|---|---|
| API errors spike | "Alerts · API errors · both apps" (`api_error`, one line per app) | 50 |
| Payments failing | "Patient · Payments · Failures" (unchanged) | 5 |
| Video visits failing | "Alerts · Video join failures · both apps" (`video_join_failed`, per app) | 5 |
| Booking failing | "Alerts · Booking failures · both apps" (`booking_failed`, per app) | 10 |
| Visit can't be completed | "Doctor · Visit · Cannot complete" (`visit_end_failed` + `scribe_failed`, `app = doctor`) | 5 |

The limit applies to each line, so to each app separately. The patient dashboards' own charts were left as they
were (patient only, split by reason). The limits for the doctor side are a first guess: review them after the first
weeks of real data.

## 5. Work breakdown

### Stage A: foundation (first, mostly one after another)

| Chunk | What | Depends on | Can run beside |
|---|---|---|---|
| A1 | Core in `data/tracking`: `Tracker`, `Tracking` object (inert without a sink), `TrackingSink`, event base type, `Throwable.toFailureReason()`, guards (debug, demo, development backend, opt-out), one empty event file per feature group, `RecordingTracker` and the catalogue test | — | — |
| A1b | A general `api_error` event fired from `apiCall` for every failed backend call (same name and property names as the patient app; the endpoint as a word-level label with ids masked, never the path), plus `api_slow` if the patient app's threshold logic copies cleanly | A1 | A2–A5 |
| A2 | Android sink (posthog-android), key from `BuildConfig`, `google_play_deploy.yml` | A1 | A3, A4, A5 |
| A3 | iOS sink in Swift behind a Kotlin interface (like `DailyVideoCall`), key from `Secrets.xcconfig`, privacy manifest, `ios_testflight.yml` | A1 | A2, A4, A5 |
| A4 | Web sink (posthog-js through JS interop), the production-hostname rule (`doctor-dev.shifa.uz` never reports), `connect-src` in `.htaccess`, `web_cpanel_deploy.yml` | A1 | A2, A3, A5 |
| A5 | Identity and session: identify after the profile loads, reset on every session end (seven reasons), super properties re-registered after reset, person properties | A1 | A2, A3, A4 |
| A6 | Screen views from the shell (`MainScreen`, `ShellViewModel`), signed-out screens in `App.kt`, tab and More navigation, push open | A1, A5 | — |
| A7 | Debug menu: recent-events list and a "would this build track?" row; the developer guide | A1 | A6 |

A2–A5 are four parallel jobs once A1 is merged. A6 and A7 follow.

### Stage B: features (nine parallel jobs, no shared files)

Each job instruments its own folders only, writes its events into its own file created in A1, adds its tests, and
writes its notes into its own file under `docs/implemented/tracking/`.

| Job | Folders | ViewModels | Notes |
|---|---|---|---|
| B1 Auth and lock | `ui/splash`, `login`, `emailcode`, `register`, `auth`, `applock` | 10 | Sign-in method, registration funnel, PIN |
| B2 Home, inbox, reports, tasks | `ui/home`, `notifications`, `analytics`, `tasks` | 9 | |
| B3 Calendar and schedule | `ui/calendar`, `schedule` | 8 | Booking funnel; "on behalf of a colleague" as a property |
| B4 Patients and care | `ui/patients`, `care` | 9 | Record tabs are screen ids here |
| B5 Documents and forms | `ui/documents`, `forms` | 6 | |
| B6 Visit, video, scribe, dictation | `ui/visit`, `speech` | 3 + 6 helpers | The densest logic; visit end pipeline |
| B7 Chat and Shifa Intelligence | `ui/chat`, `intelligence` | 5 + voice | Never the prompt or message text |
| B8 Clinic workspace | `ui/clinic` | 8 | Can be split in two (finance / plans) for a tenth worker |
| B9 Profile, settings, billing, legal | `ui/profile` | 11 | Owns the opt-out switch and its strings (D3) |

Files only Stage A may touch: `App.kt`, `ui/navigation/*`, `data/api/*`, build files, `index.html`, `.htaccess`,
workflows, the Xcode project. A feature job that needs an "opened from" property asks for it instead of editing the
shell.

### Stage C: finish

| Chunk | What |
|---|---|
| C1 | One event-catalogue document generated from the code (name, properties, where it fires), checked against section 1 |
| C2 | Device checks: Android and iOS release builds and the production web bundle against a test project key; the known iOS pitfalls (booleans as 1/0, identify needing a reset first) |
| C3 | PostHog: doctor dashboards and funnels (activation, booking, visit completion, scribe use, clinic), doctor cohorts |
| C4 | Store privacy forms (App Store, Play data safety) and the privacy text |

## 6. How the parallel work is organised in git

**Status and next steps: `04-overnight-runbook.md`.** A1 is built (uncommitted). The owner allowed local commits per
chunk on `feature/posthog-tracking` (2026-11-10); pushing still needs approval.

The workspace rule is one branch per repo and one pull request. So: one branch `feature/posthog-tracking`, with a
worktree per parallel job; the coordinator merges the job branches into it in order (A1, then A2–A5, then A6–A7, then
B1–B9). Because the folders are disjoint, conflicts can only come from the files listed as Stage A's.

If one large pull request is unwanted, the alternative is three: foundation, features, finish.

## 7. Risks

- **Patient data in properties.** The largest risk; the catalogue test blocks known keys and any `*_id`, and review of
  each job checks the property lists.
- **Web security policy.** The doctor site's policy is report-only today; the change is one allowed host.
- **iOS.** The Swift bridge and the Xcode project file are the only files two people could want at once; A3 alone
  touches them.
- **Dashboards mixing apps.** Section 4 must be done before the first release that tracks.
