# Tracking: developer guide

For the jobs that instrument features (Stage B) and the ones that attach the platforms (A2–A7). The core is built
(chunk A1, `composeApp/src/commonMain/kotlin/shifa/doctor/data/tracking/`); the plan and its decisions are in
`00-plan.md`. Status: `docs/implemented/tracking/README.md`.

The name is `tracking`, not `analytics`: `data/analytics` and `ui/analytics` are the Reports feature.

## 1. The pieces

| Piece | File | What it is |
|---|---|---|
| `Tracker` | `data/tracking/Tracking.kt` | What a ViewModel gets: `track(event)` and `screen(screen, properties)` |
| `Tracking` | same | The real `Tracker`, an `object`. Does nothing until a platform attaches an SDK, then applies the on/off rule before every call |
| `TrackEvent` | `data/tracking/events/TrackEvent.kt` | The sealed base of every event |
| Your events | `data/tracking/events/<Group>Events.kt` | One file per group, each holding one `object` |
| `TrackScreen` | `data/tracking/TrackScreen.kt` | The fixed list of screen ids |
| `TrackScreenView` | `ui/widgets/TrackScreenView.kt` | The composable that sends a screen view once |
| Helpers | `data/tracking/TrackProperties.kt`, `data/api/ErrorMessages.kt` | `countBucket`, `amountBucket`, `Enum.trackName()`, `Throwable.toFailureReason()` |
| Test doubles | `androidUnitTest/.../data/tracking/TrackingTestDoubles.kt` | `RecordingTracker`, `RecordingSink` |

## 2. Instrumenting a ViewModel, end to end

The example is sign-in (group B1 decides the real events; this shows the shape).

**1. Define the events in your group's file**, nested in its object. Nobody else edits that file.

```kotlin
// data/tracking/events/AuthEvents.kt
object AuthEvents {
    /** [method]: `password` / `email_code` / `sms_code`; [role]: `doctor` / `staff`. */
    data class SignInSucceeded(
        val method: String,
        val role: TrackingRole,
    ) : TrackEvent("sign_in_succeeded", "method" to method, "role" to role)

    /** [reason]: `empty_fields` / `invalid_credentials` / `not_doctor_account` / a `toFailureReason()` word. */
    data class SignInFailed(
        val method: String,
        val reason: String,
    ) : TrackEvent("sign_in_failed", "method" to method, "reason" to reason)
}
```

- A `data class` when there are properties, a `data object` when there are none.
- Parameters are `String`, `Int`, `Long`, `Double`, `Boolean`, an enum, or a `List` of those; nullable when the value
  can be unknown. A null is left out of the event. An enum is sent as its `trackName()` (`PinLockout` → `pin_lockout`).
- A fixed set of words that is not already an enum stays a `String`, with the allowed words in the KDoc.
- Person properties ride on the event: `TrackEvent("x", …, personProperties = mapOf("has_booked" to true),
  personPropertiesSetOnce = mapOf("first_booking_at" to at))`.

**2. Take the tracker as the LAST constructor parameter, with the real one as the default.**

```kotlin
class LoginViewModel(
    private val auth: AuthRepository = AuthRepository(),
    private val sessions: SessionRepository = sessionRepository,
    private val tracker: Tracker = Tracking,
) : ViewModel() {
```

No call site changes: the 64 `viewModel { XxxViewModel() }` sites and the existing tests keep compiling, and the
default is inert in tests. The plain helper classes that track themselves (`VideoVisit`, `Dictation`, `VoiceSession`,
…) take it the same way.

**3. Fire on the outcome**, in the ViewModel, after the result is known. Never on the tap.

```kotlin
if (idError != null || pwError != null) {
    tracker.track(AuthEvents.SignInFailed("password", "empty_fields"))
    …
}
…
result.fold(
    onSuccess = { session ->
        tracker.track(AuthEvents.SignInSucceeded("password", if (session.isClinicStaff) Staff else Doctor))
        …
    },
    onFailure = { e ->
        tracker.track(
            AuthEvents.SignInFailed(
                "password",
                if (e is ApiException.Unauthorized) "invalid_credentials" else e.toFailureReason(),
            ),
        )
        …
    },
)
```

`toFailureReason()` gives `network`, `timeout`, `validation`, `unauthorized`, `forbidden`, `not_found`, `conflict`,
`rate_limited`, `server`, `client` (not a backend failure), and for the codes that mean the same on every route
`account_disabled`, `session_expired`, `maintenance`. A code only your feature knows (`NO_SHOW_NOT_ALLOWED`,
`PAYMENT_REQUIRED`) is yours to name: branch on `e.code` first, as the screen's message already does.

**4. Screen views.** The shell sends one for every tab and pushed route, and `App.kt` for the signed-out screens
(chunk A6), so a feature job adds none for those. A view inside one route is yours (a record tab, a clinic section,
a finance view; their ids are already in `TrackScreen`):

```kotlin
@Composable
fun PatientDetailScreen(…) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    TrackScreenView(TrackScreen.PatientMedical)                       // once, when it appears
    TrackScreenView(TrackScreen.Register, "kind" to kind, step = step) // again when `step` changes
    PatientDetailContent(state, …)
}
```

`TrackScreenView` goes in `XxxScreen`, never in `XxxScreenContent`: previews and screenshot tests draw the content
composables and must send nothing. A ViewModel that knows better when a view was really shown can call
`tracker.screen(TrackScreen.X, mapOf(…))` instead. A screen id missing from `TrackScreen` is a request to the
coordinator (the file is Stage A's), like an "opened from" property that only the shell knows.

**5. A tap with no ViewModel action** calls `Tracking.track(…)` in the `XxxScreen` callback, never in the content
composable.

**6. Test it** with `RecordingTracker`, one test per outcome event (success and the main failure), not every
property permutation.

```kotlin
private val tracker = RecordingTracker()

private fun vm() = LoginViewModel(auth, sessions, tracker).also { created += it }

@Test
fun `sign in is tracked by its outcome`() {
    val vm = vm()
    vm.submit()
    reply = { respondError(HttpStatusCode.Unauthorized) }
    fillAndSubmit(vm)
    reply = { respondToken() }
    fillAndSubmit(vm)

    assertThat(tracker.events)
        .containsExactly(
            AuthEvents.SignInFailed("password", "empty_fields"),
            AuthEvents.SignInFailed("password", "invalid_credentials"),
            AuthEvents.SignInSucceeded("password", TrackingRole.Doctor),
        ).inOrder()
}
```

`tracker.screens` holds the screen views as `TrackScreen to properties`. Events are data classes, so they compare by
value. Durations and timestamps come from the ViewModel's injected clock so the test can assert them.

**7. Nothing to register.** `TrackEventCatalogueTest` finds every `TrackEvent` subclass by reflection, builds it with
sample values and checks it. Run it with your tests:

```
./gradlew :composeApp:testDebugUnitTest --tests 'shifa.doctor.data.tracking.*' --tests 'shifa.doctor.ui.<folder>.*'
./gradlew :composeApp:ktlintFormat
```

**8. Write your notes** into your own file, `docs/implemented/tracking/<group>.md` (events, where each fires, what
was left out and why). Do not edit `docs/implemented/README.md` or `docs/implemented/tracking/README.md`.

## 3. Rules

- **Names:** `snake_case`, `object_action`, past tense for outcomes (`appointment_booked`). `_started` when the
  doctor begins a flow, `_failed` when the outcome did not happen, `_tapped` only when the tap has no outcome event.
  Names are unique across the whole app; a clash fails the catalogue test.
- **Properties:** `snake_case`; Booleans start with `is_` / `has_` or are a plain adjective.
- **Values:** our own fixed lowercase words, never backend or translated text. A backend code passed through keeps
  the backend's spelling (a notification `type`).
- **One type per property, forever.** PostHog fixes the type the first time it sees it. Leave a value out instead of
  sending a placeholder word in a number.
- **Never rename** an event or a value: add the new one, send both for a release, then remove the old one.
- **Send what the ViewModel knows.** Do not thread new state through the app for a property.
- **Counts and sizes** whose exact value says nothing more go through `countBucket`. A new range (duration, file
  size) gets one shared function in `TrackProperties.kt` through the coordinator, not a copy in a ViewModel.
- **Parameter order:** the tracker is last. No ViewModel test passes a trailing lambda to a constructor today; if
  yours does, keep the lambda last and put the tracker before it.
- **Shared pieces** (`Dictation`, `NotificationOpener`, the patient picker, the upload sheets, the map picker) are
  instrumented once by the group that owns the file; a host adds nothing.

## 4. Never sent

Ask of every property: could it identify a person, could it say something about someone's health, is it a record id?
Any yes means it is not sent.

- Names, phone numbers, e-mail addresses, birth dates, addresses, photos: the doctor's, a colleague's or a patient's.
- Free text: notes, visit drafts, chat messages, AI prompts and replies, transcripts, search text (send its length or
  the result count as a range), reasons, comments, task titles and instructions.
- Clinical data: diagnoses, ICD-10 codes, dental values, form answers, document titles and file names.
- **Any id** (D1): patient, appointment, document, conversation, task, clinic, member. The only id is the user's own
  distinct id, which `Tracking.identify` builds.
- **Exact money** (D2): prices, payments, debts, revenue shares. `amountBucket(minor, currency)` under a key ending
  in `_bucket`, or nothing.
- Tokens, codes, PINs, passwords, invitation keys, request bodies, backend messages, URLs, web paths, `toString()`
  of a route or a model, anything from a push's data except its `type`.

The catalogue test fails on: a name or key that is not `snake_case`; a duplicate name; a key that is, or ends with,
`name`, `email`, `phone`, `text`, `note(s)`, `comment`, `message`, `title`, `token`, `password`, `query`, `prompt`,
`value`, `answer`, `url`, `address`, `birth_date`, `diagnosis`, `code`, `pin` (unless its value is a Boolean:
`has_email` is a fact, not the address); a key that is `id` or ends in `_id` / `Id`; a money word (`amount`, `price`,
`sum`, `cost`, `fee`, `revenue`, `debt`, `balance`, `discount`, `salary`, `paid`) in a key that is not a `…_bucket`
String; a constructor parameter or value of any other type. It cannot judge values: review still reads every
property list.

## 5. When anything is sent

`trackingStatus()` in `TrackingStatus.kt` is the one rule (00-plan.md §3). Tracking is on only when all hold:

| Condition | Off reason (`Tracking.status.value.reason`) |
|---|---|
| A platform SDK is attached (`Tracking.start`) | `NoSink` |
| The build is a release build (`!isDebugBuild`) | `DebugBuild` |
| Demo data is off (`DemoMode.enabled`) | `DemoMode` |
| The backend is production (`BackendChoice.current`) | `DevelopmentBackend` |
| On the web, the page's host is `doctor.shifa.uz` | `NotProductionSite` |
| The doctor has not opted out (`TrackingSettings.optedOut`) | `OptedOut` |

`Tracking` evaluates it before every call, so a missed notification cannot leak an event; `Tracking.refresh()` makes
a change take effect at once. Turning off opts the SDK out and resets it (identity and queue); turning back on needs a
new `identify`. Feature code never checks any of this: it calls the tracker and nothing else.

Seeing events arrive needs a release build on production. In a debug build, the debug menu will list what would have
been sent (chunk A7).

## 6. For the platform and shell chunks

```kotlin
// A2–A4, after the SDK's own setup (autocapture, replay, automatic screen views and surveys off):
Tracking.start(sink)                                   // Android, iOS
Tracking.start(sink, webHost = window.location.hostname) // web

// A5, from the collector of sessionRepository.session / .ended and profileRepository.me:
Tracking.identify(TrackingRole.Doctor, Jwt.subject(token), mapOf("tier" to tier, "has_clinic" to hasClinic, …))
Tracking.track(SessionTrackEvents.SessionEnded(reason)); Tracking.reset()

// A5/A6, in App.kt:
LaunchedEffect(demo, backend) { Tracking.refresh() }
LaunchedEffect(language) { Tracking.register("app_language", language.tag) }

// B9, the Settings switch (inject both as lambdas so the ViewModel test needs no platform Settings):
Tracking.optedOut; Tracking.setOptOut(out)
```

- Start the SDK opted out and let `Tracking.start` opt it in: `start` calls `sink.optOut(...)` before anything else.
  Start it even in debug builds if a key is present; the rule keeps it silent. No key means no `start`, and the app
  runs with tracking off.
- `TrackingSink.reset()` must drop the identity and the queue. `Tracking` re-registers the super properties after
  every reset and resets before identifying a different user; the id last identified is kept in plain Settings
  (`tracking_identified`), because the SDK keeps its own across launches.
- iOS: turn every `KotlinBoolean` into a `Bool` in Swift, inside lists too, or it arrives as 1/0.
- An admin token is never identified and should not be tracked at all (A5 decides how: the simplest is not starting
  the session collector's events for a role that is neither doctor nor staff).
- `Tracking.status` is a `StateFlow<TrackingStatus>`: the debug menu's "would this build track?" row (A7), and A5's
  signal to identify again when it turns on.
