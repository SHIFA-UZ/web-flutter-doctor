# PostHog in the patient app: the recipe (for porting to the doctor app)

Read from `apps-flutter-patient` at `main` = `3ae92d0` (v1.2.5), 2026-11-10. Read-only; nothing was changed.

**Branch state.** Every analytics branch is fully merged into `main` (0 commits ahead): `feature/analytics-foundation`
(PR #17), `docs/posthog-tracking-plan` (#16, #18), `feature/analytics-journey-events` (#19),
`feature/analytics-engagement-events` (#20), `feature/analytics-dashboards-flags` (#21), `docs/analytics-counts` (#22),
`fix/analytics-super-properties-at-start` (#23), `docs/postpone-personalisation` (#24), `fix/posthog-simulator-check`
(#25). `fix/overnight-audit-2026-11-10` is 38 commits ahead of `main` but touches no analytics file. So `main` is the
whole truth. The working tree on `main` has two uncommitted Xcode files (`project.pbxproj`, `iosApp.xcscheme`), unrelated.

Paths below are relative to `/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/apps-flutter-patient/`.
`S` = `composeApp/src/commonMain/kotlin/shifa/uz`.

**Doc drift to know about.** The docs were written as a plan and only partly updated after the build:
- `docs/posthog/README.md` still says "Nothing is built yet" and "Status: Not started". The built state is in
  `docs/implemented/analytics.md` (the reliable file).
- `docs/posthog/setup.md` §4 sketches `Analytics.setEnabled(on, profile)` and a `Flags` object. The built facade has
  `start(optedOut, demo)` / `setOptedOut` / `setDemo` instead, and **`Flags` does not exist**.
- `docs/posthog/development.md` §3 says `RecordingTracker` is in `ui/RecordingTracker.kt`; it is actually at the bottom
  of `composeApp/src/androidUnitTest/kotlin/shifa/uz/data/analytics/AnalyticsEventTest.kt`. §5 lists Debug-menu rows
  "reload flags" and "Crash now" that were never built.
- `docs/posthog/events.md` lists properties and values the code does not send (the left-outs are recorded in
  `docs/implemented/analytics.md`). Example: doc says `logged_out.source` = `profile / session_expired / account_deleted`;
  code says `profile / account_deleted / backend_switch`. **Code (`AnalyticsEvent.kt`) wins.**

---

## 1. Architecture

### 1.1 Files

| File | What it does |
|---|---|
| `S/data/analytics/Analytics.kt` (209 lines) | `POSTHOG_HOST`, `ANALYTICS_APP = "patient"`, `interface AnalyticsSink` (what a platform SDK does), `interface AnalyticsTracker { fun track(event) }` (what ViewModels get), `object Analytics : AnalyticsTracker` (the facade), `List<Pair<String, Any?>>.clean()` (drops nulls) |
| `S/data/analytics/AnalyticsEvent.kt` (883 lines) | The event catalogue: `sealed class AnalyticsEvent`, one `data class` / `data object` per event, plus the shared helpers (`analyticsReason()`, buckets, `hoursAhead`, …) |
| `S/data/analytics/AnalyticsScreen.kt` (69 lines) | `enum class AnalyticsScreen(val id: String)` with 28 screens, plus pure helpers for screen properties (`analyticsName()`, `paymentBadge()`, `appointmentState()`, `fileType()`) |
| `S/data/analytics/AnalyticsSettings.kt` (19 lines) | The opt-out flag `analytics_opt_out` in plain Multiplatform Settings |
| `S/data/analytics/PaymentReports.kt` (14 lines) | A dedup guard so `payment_completed` is sent once per booking (two ViewModels can both see PAID) |
| `S/ui/widgets/TrackScreen.kt` (22 lines) | `@Composable TrackScreen(screen, vararg properties, step)`: `LaunchedEffect(screen, step) { Analytics.screen(...) }` |
| `composeApp/src/androidMain/kotlin/shifa/uz/data/analytics/PostHogSink.kt` (95 lines) | `fun setUpAnalytics(app: Application)` (SDK config and setup) and `internal class PostHogSink : AnalyticsSink` |
| `composeApp/src/androidMain/kotlin/shifa/uz/ShifaApp.kt` | The `Application` class; `onCreate` calls `setUpAnalytics(this)` (it was created for this, and also took `initSecureSettings` from `MainActivity`) |
| `composeApp/src/iosMain/kotlin/shifa/uz/data/analytics/AnalyticsBridge.kt` (14 lines) | `analyticsStartsOptedOut()` and `registerAnalytics(sink: AnalyticsSink)`, called from Swift as `AnalyticsBridgeKt.*` |
| `iosApp/iosApp/PostHogAnalytics.swift` (71 lines) | `final class PostHogAnalytics: NSObject, AnalyticsSink`, forwarding to `PostHogSDK.shared`; holds the `KotlinBoolean` → `Bool` fix |
| `iosApp/iosApp/AppDelegate.swift` | `setUpAnalytics()`: the iOS SDK config and setup |
| `iosApp/iosApp/Info.plist` | `PostHogApiKey = $(POSTHOG_API_KEY)` |
| `iosApp/Configuration/Config.xcconfig` | Key wiring per build configuration (see 1.5) |
| `S/data/api/ApiHelper.kt` | `api_error` / `api_slow` from `apiCall`; `endpointTemplate()`; the swappable `internal var apiAnalytics: AnalyticsTracker = Analytics` |
| `composeApp/src/androidUnitTest/kotlin/shifa/uz/data/analytics/AnalyticsEventTest.kt` (248) | Whole-catalogue test (names, blocked keys, buckets) and the `RecordingTracker` fake |
| `…/AnalyticsTest.kt` (227) | Facade tests with a `RecordingSink` (identify, reset, opt-out, demo, super properties) |
| `…/AnalyticsScreenTest.kt` (59) | Tests for the screen-property helpers |
| `docs/posthog/{README,setup,events,privacy,development,dashboards,personalisation}.md` | Plan, decisions D1–D12, catalogue, privacy rules, how-to, dashboards |
| `docs/implemented/analytics.md` | What was actually built, and every deliberate left-out with its reason |

### 1.2 The platform split: an interface, not expect/actual

There is **no `expect`/`actual` for analytics**. The pattern (copied from the app's Daily video bridge) is:

- `commonMain` declares `interface AnalyticsSink` and holds `Analytics.platform: AnalyticsSink?` (null by default).
- Android: `PostHogSink` implements it in `androidMain` and `setUpAnalytics` assigns `Analytics.platform`.
- iOS: a **Swift** class implements the Kotlin interface and is handed in through `registerAnalytics(sink:)`.
  The PostHog iOS SDK is a Swift package in the Xcode project, not a Kotlin dependency.

The only `expect`/`actual` involved is the existing `isDebugBuild` (`S/utils/PlatformUtils.kt`), used for the Debug
menu's event list.

`AnalyticsSink` signature (exact, load-bearing for a port):

```kotlin
interface AnalyticsSink {
    fun capture(event: String, properties: Map<String, Any>, userProperties: Map<String, Any>, userPropertiesSetOnce: Map<String, Any>)
    fun screen(name: String, properties: Map<String, Any>)
    fun identify(distinctId: String, userProperties: Map<String, Any>, userPropertiesSetOnce: Map<String, Any>)
    fun register(key: String, value: Any)
    fun reset()
    fun distinctId(): String
    fun optOut(out: Boolean)
    fun isFeatureEnabled(key: String): Boolean
    fun featureFlagPayloadJson(key: String): String?
    fun reloadFeatureFlags()
}
```

Facade state: `platform` (the SDK, null in tests, previews, debug builds and builds without a key), private `optedOut`
and `demo`, and `sink = platform.takeIf { !optedOut && !demo }`. Events, screens, identify and flag reads go through
`sink`. `reset()` and `register()` go through `platform` on purpose (identity must be dropped even while opted out;
super properties must be there when tracking comes back).

### 1.3 SDK versions

| Platform | SDK | Version | Where pinned |
|---|---|---|---|
| Android | `com.posthog:posthog-android` | `3.75.1` | `gradle/libs.versions.toml` (`posthog = "3.75.1"`, library `posthog-android`), used as `implementation(libs.posthog.android)` in `androidMain.dependencies` of `composeApp/build.gradle.kts` |
| iOS | `https://github.com/PostHog/posthog-ios.git` (SPM, product `PostHog`) | `3.93.3` | `iosApp/iosApp.xcodeproj/project.xcworkspace/xcshareddata/swiftpm/Package.resolved` |
| Web | none | - | The patient app has no web target |

`posthog-kmp` (0.10.0 on 2026-11-03) was rejected in D2: 0.x, and its iOS linking needed Kotlin 2.4.0+ while the app
was on 2.3.0. The patient app is now on Kotlin `2.4.21`, and so is the doctor app, so that blocker is gone, but the
decision was not revisited. Never use `3.+` (reproducible builds).

### 1.4 Init flow

Android (`ShifaApp.onCreate` → `setUpAnalytics(app)`):
1. `if (BuildConfig.DEBUG) return`; `if (BuildConfig.POSTHOG_API_KEY.isBlank()) return`.
2. Read `optedOut = AnalyticsSettings().optedOut`, `demo = DemoMode.enabled.value`.
3. `PostHogAndroidConfig(projectToken = key, host = POSTHOG_HOST)` with:
   `captureApplicationLifecycleEvents = true`, `captureScreenViews = false`, `captureDeepLinks = false`,
   `captureElementInteractions = false`, `capturePushNotificationSubscriptions = false`,
   `capturePushNotificationOpened = false`, `sessionReplay = false`, `personProfiles = PersonProfiles.IDENTIFIED_ONLY`,
   `errorTrackingConfig.autoCapture = true`, `preloadFeatureFlags = !(optedOut || demo)`, `optOut = optedOut || demo`.
4. `PostHogAndroid.setup(app, config)`; `Analytics.platform = PostHogSink()`; `Analytics.start(optedOut, demo)`.

iOS (`AppDelegate.setUpAnalytics()`, after `FirebaseApp.configure()`):
1. `#if DEBUG return`; read `PostHogApiKey` from `Info.plist`, return if empty.
2. `PostHogConfig(projectToken: key, host: "https://eu.i.posthog.com")` with the same switches, plus
   `config.surveys = false` (the iOS default is on).
3. `let off = AnalyticsBridgeKt.analyticsStartsOptedOut()`; `preloadFeatureFlags = !off`; `optOut = off`.
4. `PostHogSDK.shared.setup(config)`; `AnalyticsBridgeKt.registerAnalytics(sink: PostHogAnalytics())`, which sets
   `Analytics.platform` and calls `Analytics.start(...)`.

`Analytics.start(optedOut, demo)` applies both guards at once (so a demo launch never opts the SDK in for a moment),
then registers the super properties `app = "patient"` and `app_language`.

After launch, in `S/App.kt`: `LaunchedEffect(language) { Analytics.register("app_language", language.code) }` and
`LaunchedEffect(demo) { Analytics.setDemo(demo) }`.

### 1.5 Key injection

The key is the PostHog **Project API key** (`phc_…`, write-only). One secret name everywhere: `POSTHOG_API_KEY_PROD`.

| Build | Android | iOS |
|---|---|---|
| Debug | `buildConfigField("String", "POSTHOG_API_KEY", "\"\"")` in the `debug` build type; setup also returns early on `BuildConfig.DEBUG` | `POSTHOG_API_KEY[config=Debug]=` (empty) in `Config.xcconfig`; setup also returns under `#if DEBUG` |
| Release, CI | `google_play_deploy.yml` line 118: `./gradlew :composeApp:bundleRelease -PversionBuild=… -Pposthog.apiKey.prod=${{ secrets.POSTHOG_API_KEY_PROD }} --no-daemon` → `buildConfigField("String", "POSTHOG_API_KEY", "\"$postHogProdKey\"")` | `ios_testflight.yml` step "Write PostHog key": `echo "POSTHOG_API_KEY_PROD = $POSTHOG_API_KEY_PROD" > iosApp/Configuration/Secrets.xcconfig`, before the Kotlin framework build and fastlane |
| Release, local | `posthog.apiKey.prod=phc_…` in root `local.properties` (gitignored). The Gradle property from CI wins when both are set. Without `keystore.properties` the release type is debug-signed so it installs (`signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")`) | The same line in `iosApp/Configuration/Secrets.xcconfig` (gitignored), Release configuration |

`Config.xcconfig` (exact):

```
POSTHOG_API_KEY_PROD=
#include? "Secrets.xcconfig"
POSTHOG_API_KEY[config=Debug]=
POSTHOG_API_KEY[config=Release]=$(POSTHOG_API_KEY_PROD)
```

`build.gradle.kts` (exact):

```kotlin
val postHogProdKey =
    project.findProperty("posthog.apiKey.prod")?.toString()
        ?: localProperties.getProperty("posthog.apiKey.prod").orEmpty()
```

No key means `Analytics.platform` stays null and the app runs with analytics off: a fresh clone needs no setup.
`ci.yml` (PR checks) passes no key. `.gitignore` holds `local.properties` and `iosApp/Configuration/Secrets.xcconfig`.
`CLAUDE.md` lists `POSTHOG_API_KEY_PROD` in its secrets list.

---

## 2. Rules and decisions

Decisions D1–D12 are the table in `docs/posthog/README.md` ("Decisions", dated 2026-11-03).

| Topic | Rule | Id / where |
|---|---|---|
| Hosting | PostHog Cloud **EU**, ingestion host `https://eu.i.posthog.com` (note the `.i.`; `eu.posthog.com` is the web app). Legal sign-off 2026-11-03 | D1; `privacy.md` §5 |
| SDK | Native SDKs behind our own facade in `commonMain`, not `posthog-kmp` | D2; `setup.md` §3 |
| Identity | `distinct_id = patient_<P1 id>`, identified after login; anonymous before (PostHog merges the anonymous history) | D3; `events.md` §1.2 |
| Projects, debug vs release | One organisation, **one project "Shifa"**, fed by **release builds only**. Debug builds have no key and return early. The doctor app shares the project (`app = doctor`, `doctor_<id>`) | D4; `setup.md` §1.2, §1.7, §2 |
| Session replay | **Off** (Compose screens hold health data; iOS Compose is one Metal view and can't be masked) | D5; `privacy.md` §6 |
| Autocapture | Lifecycle events on; automatic screen views off; element autocapture off; deep links off; SDK push capture off | D6; `setup.md` §5 |
| Consent | On by default with an **opt-out** switch (Profile → Settings → "Share usage data"). Legal sign-off 2026-11-03. No ATT prompt needed | D7; `privacy.md` §4 |
| Demo data | Analytics off while Debug → Demo data is on (facade guard and SDK `optOut()`) | D8 |
| Dev backend | Nothing to do: only debug builds can switch, and they never send. No `backend` super property | D9 |
| Feature flags | Sink methods exist (`isFeatureEnabled`, `featureFlagPayloadJson`, `reloadFeatureFlags`), flags preloaded at launch unless off. **No flag is read anywhere; the `Flags` object was never built.** Flags must be read in ViewModels through `Analytics.sink` (null → default) | D10; `personalisation.md` §4 |
| Error tracking | PostHog exception autocapture **on** in both SDK configs and in the project (Crashlytics was never wired) | D11; `privacy.md` §8 |
| Surveys | Off (`config.surveys = false` on iOS); fixed-choice only if ever used | D12; `privacy.md` §7 |
| Alias | Not used anywhere. Only `identify` (which merges the anonymous id) | - |
| Groups | Not used anywhere (no `group()` call, no group analytics) | - |
| Person profiles | `IDENTIFIED_ONLY` / `.identifiedOnly` in the SDK config | `setup.md` §1.3 |

### Naming (`events.md` §1.1)

- Events: `snake_case`, `object_action`, past tense for outcomes (`booking_completed`, `checkin_submitted`).
  `_started` = user begins a flow, `_failed` = outcome didn't happen, `_tapped` only when the tap has no outcome event.
- Properties: `snake_case`; booleans start with `is_` / `has_` or are plain adjectives (`granted`).
- Values: our own fixed `snake_case` enums (`reason = "slot_taken"`), never backend or localised text. Backend codes
  passed through keep the backend's spelling (`category = "VITAL"`).
- Outcome events fire from the ViewModel **after** the result, never on the tap.
- PostHog's own events keep their names (`Application Opened`, `$screen`, `$identify`, `$exception`).
- One type per property, forever (PostHog fixes the type on first sight). Leave a value out rather than send a
  placeholder string in a numeric property.
- Never rename an event or value: add the new one, send both for one release, move insights, then remove
  (`development.md` §6).

### Identify / reset (where called)

- **Identify:** `HomeViewModel.show(patient)` after the profile loads (`identify: (PatientProfile) -> Unit = Analytics::identify`
  is a constructor param). Again after the Profile language dialog and after opt-in (`Analytics.setOptedOut(false, profile)`).
- `Analytics.identify` sends `patient_<id>` with person properties `app`, `language`, `subscription_tier`,
  `shifa_ai_enabled`, `has_photo`, `has_email`, `has_phone`, and set-once `first_platform`. It returns if the id is
  blank or `sink` is null. It **resets first** if the SDK still holds a different `patient_…` id.
- **Reset:** `AuthRepository.logout(source)` (sends `logged_out` first, then resets), the session-restore 401, and the
  iOS reinstall wipe (only when a token was actually wiped). `AuthRepository` takes
  `resetAnalytics: () -> Unit = Analytics::reset`. `Analytics.reset()` re-registers the super properties, because the
  SDK's reset drops them.

### Super properties (`events.md` §1.3)

`app = patient` and `app_language`, registered in `Analytics.start`. The SDK adds `$os`, `$app_version`, `$app_build`,
`$device_type`, `$lib`, `$session_id`. `Application Installed` / `Updated` are sent inside the SDK's setup and carry
neither of ours; `docs/implemented/analytics.md` notes that **once the doctor app shares the project, a properties
sanitizer in both SDK configs would be needed to add `app` to those two events**.

### Consent / opt-out (built)

`ProfileViewModel.setShareUsageData(on)`: saves `AnalyticsSettings.optedOut`; turning off sends `analytics_opted_out`
and then `Analytics.setOptedOut(true)`; turning on calls `setOptedOut(false, profile)` (re-identify) and then sends
`analytics_opted_in`. The facade guard matters because the SDK's `optOut()` alone still answers flag reads.
Known and accepted: the SDK's `reset()` reloads flags regardless, so a logout while opted out makes one anonymous
flags request.

### Never sent (`privacy.md` §1–§3)

The three-question test for every property: could it say something about health, could it identify a person, is it a
record id other than the user's own `distinct_id`? Any yes = not sent.

- Identity: names, phone, email, birth date or age, gender, address, photo URL.
- Free text: searches (send `query_length`), reasons, comments, chat text, AI questions and replies, notes, titles.
- Health data: check-in values, task names, doctor names and specialties, diagnoses, document titles and file names,
  **the exact price of a service** (send `amount_bucket`).
- Other people: doctor ids and names, clinic names.
- Record ids: appointment, document, task, chat, message, notification, payment ids.
- Secrets and transport: tokens, OTPs, headers, bodies, backend error messages, full URLs, query strings.
- Location: coordinates; IP is discarded in the project settings.

Allowed: our enums, counts, durations, lengths, buckets, reviewed coarse categories, usage-fact person properties,
exception types and stack traces from our own code.

Enforced by `AnalyticsEventTest`: every name and key matches `[a-z][a-z0-9]*(_[a-z0-9]+)*`, no key is in the blocked
set (`name, first_name, last_name, phone, email, text, comment, message, query, notes, value, answer, title, url,
amount, address, birth_date`), and no key ends in `_id`.

### Legal / privacy notes

- Uzbekistan's personal data law (ZRU-547, Article 27¹) has a localisation rule; the owner's legal review cleared
  PostHog Cloud EU with pseudonymous ids on 2026-11-03 (`privacy.md` §5).
- Store labels: App Store "Product Interaction" and "Crash Data", linked to the user, not used for tracking; Play
  "App interactions", "Crash logs", "Diagnostics".
- Account deletion: delete the person in PostHog by hand (runbook step; not automated).
- Still open on the go-live checklist (`setup.md` §8): privacy policy text naming PostHog, the store privacy labels,
  the team's ids in the internal-users filter, the deletion runbook step.

---

## 3. The event catalogue

### 3.1 Shape in code

```kotlin
sealed class AnalyticsEvent(
    val name: String,
    vararg props: Pair<String, Any?>,
    /** Person properties set with the event (personalisation.md §2): `has_booked = true`, … */
    val userProperties: Map<String, Any> = emptyMap(),
    /** Person properties set only the first time: `first_booking_at`, … */
    val userPropertiesSetOnce: Map<String, Any> = emptyMap(),
) {
    val properties: Map<String, Any> = props.toList().clean()

    /** For the Debug menu's list. (Not toString: data objects generate their own.) */
    val label: String get() = if (properties.isEmpty()) name else "$name $properties"
```

- One nested `data class` (with properties) or `data object` (without) per event. Being data classes, tests compare
  them with `contains(...)` / `containsExactly(...)`.
- Constructor parameters are typed Kotlin (`Boolean`, `Int`, `Long`, `String`, nullable where unknown). Enum-like
  values are plain `String`s whose allowed values are listed in the KDoc above the class, not Kotlin enums.
- Nulls are dropped by `clean()`, so an unknown value is absent, never `"null"`.
- Person properties ride on the event (`userProperties`, `userPropertiesSetOnce`); timestamps for set-once
  properties are passed in as an ISO string `at` from the ViewModel's injected clock.
- Sections in the file follow `events.md` §3–§13: launch and auth, home/doctors/booking, payments, live visit/video/
  signing, tasks and check-ins, documents, messages, Shifa AI, notifications and push, reliability, profile.

### 3.2 Counts (grep on `main`)

- **162** event classes in `AnalyticsEvent.kt` (nested `data class` / `data object`).
- **28** screens in `AnalyticsScreen`; **25** `TrackScreen(...)` call sites (24 screens plus one in `MainScreen` for
  the five tabs via `ShifaTab.analyticsScreen`).
- **28** files in `commonMain` reference `AnalyticsTracker` (ViewModels, `AuthRepository`, `ApiHelper`, the facade).
- **23** direct `Analytics.track(...)` calls in `ui/` (pure-UI taps with no ViewModel action).
- **28** test files reference `RecordingTracker`.

### 3.3 Screens

`enum class AnalyticsScreen(val id: String)`, ids in `snake_case` (`Home("home")`, `CreateBooking("create_booking")`,
`AiChat("ai_chat")`). Rules:

- `TrackScreen(AnalyticsScreen.X, "key" to value, …, step = …)` goes in `XxxScreen`, **never** in `XxxScreenContent`,
  so previews and Roborazzi screenshot tests send nothing.
- Sent once when the screen appears, with the properties as they are then; re-sent only when `step` changes
  (wizards: Register, Forgot password).
- Tabs are sent from `MainScreen`; a tab shown again after an overlay closes is not re-sent.
- Trays and dialogs are not screens; their events cover them.
- Screen properties come from pure, unit-tested helpers (`analyticsName()` turns an enum constant into `snake_case`).

Examples (exact):

```kotlin
TrackScreen(AnalyticsScreen.Task, "category" to task.category, "input_type" to task.inputType)
TrackScreen(AnalyticsScreen.ForgotPassword, "step" to state.step.analyticsName(), step = state.step)
TrackScreen(AnalyticsScreen.BookingsList, "list" to list.analyticsName())
```

### 3.4 Failures: the `analyticsReason` pattern

Every `*_failed` event has a `reason: String`. It is never `e.message`. The shared mapping (exact):

```kotlin
/** The failure as a fixed reason (privacy.md: never the message). */
fun Throwable.analyticsReason(): String =
    when (this) {
        is ApiException.NetworkError -> "network"
        is ApiException.RateLimit -> "rate_limit"
        is ApiException.Unauthorized -> "unauthorized"
        is ApiException.Forbidden -> "forbidden"
        is ApiException.NotFound -> "not_found"
        is ApiException.Conflict -> "conflict"
        is ApiException.ValidationError -> "validation"
        else -> "server"
    }
```

A ViewModel adds its own domain reasons on top (`"empty_fields"`, `"slot_taken"`, `"locked_24h"`) and falls back to
`e.analyticsReason()`. Reliability events: `api_error` carries the **templated** endpoint (`endpointTemplate()`:
any path segment not in the `ENDPOINT_WORDS` allow-list becomes `{id}`), method, status (0 = no response), the
`ApiException` class name and the backend `code` (only if it matches a safe pattern); `api_slow` for a successful call
over 5 s. A new endpoint's literal words must be added to `ENDPOINT_WORDS`, or its path shows as `{id}`s.

Other shared helpers in `AnalyticsEvent.kt`: `amountBucket`, `sizeKbBucket`, `durationSBucket`, `charsBucket`,
`replyCharsBucket`, `ageHoursBucket`, `hoursAhead`, `daysAhead`, `hourOfDay`, `paymentFailureReason`. One function
per bucket so every event buckets the same way.

### 3.5 Ten representative examples (copied exactly)

```kotlin
/** `signed_in` / `no_token` / `expired` / `offline` / `reinstall_wipe`. */
data class SessionRestored(val result: String) : AnalyticsEvent("session_restored", "result" to result)

/** `email` / `phone`. */
data class LoginSucceeded(val identifierType: String) :
    AnalyticsEvent("login_succeeded", "identifier_type" to identifierType)

/** `empty_fields` / `invalid_credentials` / `locked_out` / `network` / `server`. */
data class LoginFailed(val reason: String) : AnalyticsEvent("login_failed", "reason" to reason)

data object LoginLockedOut : AnalyticsEvent("login_locked_out")

/** `profile` / `account_deleted` / `backend_switch`. */
data class LoggedOut(val source: String) : AnalyticsEvent("logged_out", "source" to source)

/** [section]: `live` / `upcoming` / `tasks` / `my_doctors`; [target]: `card` / `arrow` / `view`. */
data class HomeSectionTapped(val section: String, val target: String) :
    AnalyticsEvent("home_section_tapped", "section" to section, "target" to target)

data class DoctorSearchPerformed(val queryLength: Int, val resultsCount: Int, val chip: String) :
    AnalyticsEvent(
        "doctor_search_performed",
        "query_length" to queryLength,
        "results_count" to resultsCount,
        "chip" to chip,
    )

data class BookingCompleted(
    val isVideo: Boolean,
    val daysAhead: Int?,
    val hourOfDay: Int?,
    val paymentKind: String,
    val hasReason: Boolean,
    val attachments: Int,
    val at: String,
) : AnalyticsEvent(
        "booking_completed",
        "is_video" to isVideo,
        "days_ahead" to daysAhead,
        "hour_of_day" to hourOfDay,
        "payment_kind" to paymentKind,
        "has_reason" to hasReason,
        "attachments" to attachments,
        userProperties = bookedProperties(isVideo),
        userPropertiesSetOnce = mapOf("first_booking_at" to at),
    )

data class CheckinSubmitted(
    val inputType: String,
    val category: String,
    val hasNotes: Boolean,
    val minutesFromScheduled: Long?,
    val hourOfDay: Int,
    val at: String,
) : AnalyticsEvent(
        "checkin_submitted",
        "input_type" to inputType,
        "category" to category,
        "has_notes" to hasNotes,
        "minutes_from_scheduled" to minutesFromScheduled,
        "hour_of_day" to hourOfDay,
        userProperties = mapOf("has_checked_in" to true),
        userPropertiesSetOnce = mapOf("first_checkin_at" to at),
    )

/** [endpoint]: the path template with ids replaced (`patients/me/appointments/{id}`); null when no request was made. */
data class ApiError(
    val endpoint: String?,
    val method: String?,
    val status: Int,
    val error: String,
    val code: String?,
) :
    AnalyticsEvent(
            "api_error",
            "endpoint" to endpoint,
            "method" to method,
            "status" to status,
            "error" to error,
            "code" to code,
        )
```

### 3.6 The catalogue document

`docs/posthog/events.md` (472 lines) mirrors the code: §1 conventions, §2 screens table, §3–§13 one table per journey
(`Event | When | Properties | Sets | Where`), **§14 coverage matrix** (`Screen | Control | Event`: "if a user can
see it or tap it, there's a row for it"; a control with no decision value is listed as "not tracked"), §15 how the
journey stitches together.

---

## 4. Instrumenting a feature end to end (Login)

Process (`development.md` §1): decide the questions → write rows in `events.md` → add controls to the §14 matrix →
privacy test on every property → `AnalyticsScreen` + `TrackScreen` → event classes → fire from the ViewModel → test →
dashboard insight → update `docs/implemented/analytics.md`.

1. **Screen.** `S/ui/login/LoginScreen.kt`: `TrackScreen(AnalyticsScreen.Login)` inside `LoginScreen`.
2. **Events.** `LoginSucceeded`, `LoginFailed`, `LoginLockedOut` in `AnalyticsEvent.kt` (shown in 3.5).
3. **ViewModel.** `S/ui/login/LoginViewModel.kt` takes the tracker as a constructor parameter with the real one as
   the default, like every other dependency:

```kotlin
class LoginViewModel(
    private val repository: AuthRepository = AuthRepository(),
    private val attempts: LoginAttempts = LoginAttempts(),
    private val analytics: AnalyticsTracker = Analytics,
) : ViewModel() {
```

   and fires after each outcome:

```kotlin
if (s.identifier.isBlank() || s.password.isEmpty()) {
    return analytics.track(AnalyticsEvent.LoginFailed("empty_fields"))
}
…
onSuccess = {
    attempts.clear()
    analytics.track(AnalyticsEvent.LoginSucceeded(if ('@' in s.identifier) "email" else "phone"))
    …
},
onFailure = { e ->
    if (e !is ApiException.NetworkError) attempts.recordFailure()
    analytics.track(
        AnalyticsEvent.LoginFailed(
            if (e is ApiException.Unauthorized) "invalid_credentials" else e.analyticsReason(),
        ),
    )
    if (attempts.lockedMinutes() > 0) analytics.track(AnalyticsEvent.LoginLockedOut)
    …
},
```

4. **Facade.** `Analytics.track(event)`: in debug builds prepends `event.label` to `Analytics.recent` (last 50, shown
   in Debug menu → Analytics), then `sink?.capture(event.name, event.properties, event.userProperties, event.userPropertiesSetOnce)`.
5. **SDK.** Android `PostHogSink.capture` → `PostHog.capture(event = …, properties = …, userProperties = ….ifEmpty { null }, userPropertiesSetOnce = ….ifEmpty { null })`.
   iOS `PostHogAnalytics.capture` → `PostHogSDK.shared.capture(event, properties: properties.json, …)`, where `.json`
   converts every `KotlinBoolean` to a Swift `Bool`.
6. **Test.** `composeApp/src/androidUnitTest/kotlin/shifa/uz/ui/login/LoginViewModelTest.kt`:

```kotlin
private val tracker = RecordingTracker()
…
classToTest = LoginViewModel(repository, attempts, tracker)

@Test
fun `sign in is tracked by its outcome`() =
    runTest {
        classToTest.signIn()
        loginReturns(Result.failure(ApiException.Unauthorized("Invalid credentials")))
        fillAndSignIn()
        advanceUntilIdle()
        loginReturns(Result.success(LoginResponse(token = "t")))
        fillAndSignIn()
        advanceUntilIdle()

        assertThat(tracker.events)
            .containsExactly(
                AnalyticsEvent.LoginFailed("empty_fields"),
                AnalyticsEvent.LoginFailed("invalid_credentials"),
                AnalyticsEvent.LoginSucceeded("email"),
            ).inOrder()
    }
```

   The fake (exact, in `AnalyticsEventTest.kt`):

```kotlin
/** For ViewModel tests: the events a ViewModel sent, in order. */
class RecordingTracker : AnalyticsTracker {
    val events = mutableListOf<AnalyticsEvent>()

    override fun track(event: AnalyticsEvent) {
        events += event
    }
}
```

7. **Catalogue test.** Add one instance of each new event to the `catalog` list in `AnalyticsEventTest` so the
   name and blocked-key checks cover it.

Test rules: one test per outcome event (success and the main failure), not every property permutation. Facade tests
use `Analytics.clearForTests()` in `@Before` / `@After` (the object is a singleton) and set
`Analytics.languageProvider = { "en" }` (the real one needs a platform `Settings`).

Other code rules (`development.md` §2):
- Where a ViewModel also takes `clock: () -> Instant`, the tracker goes **before `clock`** (tests pass the clock as a
  trailing lambda).
- Pure UI taps with no ViewModel action call `Analytics.track(...)` in the `XxxScreen` callback. Never from
  `XxxScreenContent`.
- Durations come from the injected clock so tests can assert them.
- A screen used in two places gets its `source` through the ViewModel constructor.
- Checking by hand: debug build → Profile → Debug menu → Analytics (your analytics id and the session's last 50
  events). Seeing events arrive in PostHog needs a release build (local recipe in `setup.md` §7.1).

---

## 5. Work breakdown the patient app used

Ticket table: `docs/posthog/README.md` "Rollout plan". All of it was done on 2026-11-03.

| Ticket | What | Size (merge diff) | Status |
|---|---|---|---|
| ANL-01 | PostHog account, org, project, settings (through the API), the key, the GitHub secret, legal sign-off | no code | Done |
| ANL-02 | Foundation: both SDKs, facade, `ShifaApp`, Swift bridge, key plumbing, identify/reset, super properties, opt-out switch, demo off, Debug menu section | PR #17: 75 files, +1228 / −14 (includes ANL-03 screens; the first commit alone was 48 files, +952) | Done |
| ANL-03 | Screen tracking: `AnalyticsScreen`, `TrackScreen` in every screen, property helpers with tests | inside PR #17 | Done |
| ANL-04 | Journey events (auth, home, doctors, booking, payments, live visit, video, signing) | PR #19: 46 files, +1857 / −114 | Done |
| ANL-05 | Engagement events (tasks, documents, chat, Shifa AI, notifications and push, profile) | PR #20: 59 files, +1793 / −113 (includes ANL-06) | Done |
| ANL-06 | Reliability: `api_error`, `api_slow`, `ai_stream_error`, `webview_error`, exception autocapture | inside PR #20 | Done |
| ANL-07 | Dashboards, actions, cohorts, alerts in PostHog (API scripts, not in the repo) plus the local-release Gradle tweak | PR #21: 6 files, +71 / −8 | Done |
| ANL-08 | Personalisation round 1 (flags, experiment, survey) | built, then discarded uncommitted | **Postponed by the owner; do not rebuild unless asked** |
| fix | Super properties at start; keep anonymous id on a plain first launch | PR #23: 7 files, +62 / −14 | Done |
| fix | iOS simulator check: booleans, second-patient identify, `has_slots` | PR #25: 12 files, +93 / −13 | Done |

### Pitfalls that were hit

1. **iOS booleans arrive as 1/0.** A Kotlin `Boolean` crosses into Swift as `KotlinBoolean`; posthog-ios's
   `JSONSerialization` writes it as `1` / `0`, so `= true` filters and `has_*` cohorts missed iOS, and one early event
   typed a property as Numeric in PostHog (had to be reset to Boolean by hand). Fix is in Swift only
   (`mapValues { ($0 as? KotlinBoolean)?.boolValue ?? $0 }`); Kotlin can't fix it. Values must stay flat for this to work.
2. **Identify with a new id is ignored once identified** (both SDKs). Login can show without a logout, so the next
   user's events landed on the previous one. Fix: `Analytics.identify` resets first when `sink.distinctId()` is a
   different `patient_…`. This is why `distinctId()` is on the sink.
3. **`reset()` drops super properties.** The facade keeps them in a map and re-registers after every reset.
4. **Super properties registered too late.** Registering from the UI meant `Application Opened` lacked `app` and
   `app_language`. Moved into `Analytics.start`, right after SDK setup. `Application Installed` / `Updated` still lack
   them (sent inside setup).
5. **Reset on a plain first launch split the anonymous id**, so `Application Installed` never joined the signup.
   Reset now happens only when a token was really wiped (iOS Keychain survives reinstall).
6. **One property, one type.** `days_to_first_slot` was a number or the string `none`; now numeric only plus a
   separate `has_slots`.
7. **Opt-out alone doesn't stop flags.** The SDK's `optOut()` still answers flag reads; the facade's null `sink` is
   the guard. `preloadFeatureFlags` must be false when starting opted out, or setup still calls PostHog.
8. **Eager `Settings()` as a constructor default NPEs in plain JUnit repository tests** (use lazy). Seen in the
   discarded ANL-08 work.
9. **PostHog refuses flags that target event-based cohorts** (`behavioral_cohort_found`); target person properties.
10. **Free plan limits:** five alerts, daily checks, fixed bounds only (no ratios, no "2× average").
11. **GeoIP city still lands in person `$set`** through PostHog's server-side transformation even with IP discarding
    on. Open; the owner has not been asked.
12. **Release simulator build needs ad-hoc signing** (`CODE_SIGN_IDENTITY=- CODE_SIGN_STYLE=Manual DEVELOPMENT_TEAM=`).
    With `CODE_SIGNING_ALLOWED=NO` there is no Keychain, so login and `session_restored` never happen.
13. **Not verified on iOS:** identify, logout and opt-out with a real login. Open, low: a double `logged_out` on
    concurrent 401s.
14. **Many planned properties could not be sent** because the ViewModel doesn't know them (`source` of navigation,
    `seconds_on_page`, `cold_start`, …). Listed with reasons in `docs/implemented/analytics.md`; dashboards skip the
    matching rows. Plan the doctor catalogue from what ViewModels actually know.
15. Review rounds found a lot: the docs branch took 3 Codex rounds and 22 findings; foundation round 1 had 5.

---

## 6. PostHog project facts

| Fact | Value |
|---|---|
| Cloud | PostHog Cloud **EU**: web app `https://eu.posthog.com`, ingestion `https://eu.i.posthog.com` |
| Organisation | **SHIFA** (id `01a1a096-a7dc-0000-ed00-9a2fb132b586`) |
| Project | **Shifa**, project/team id **332380**, free plan (1 project, 1M events, 1M flag requests, 100k exceptions a month, 1-year retention) |
| Settings (applied by API) | Timezone `Asia/Tashkent`, client IPs discarded, session replay off, autocapture / heatmaps / web vitals / console logs / performance off, surveys off, exception autocapture on (since ANL-06), internal-users filter on by default for new insights, week starts Monday |
| Internal-users filter | Still PostHog's default cohort 308105; the team's ids were never added |
| Key | GitHub secret `POSTHOG_API_KEY_PROD` set in `SHIFA-UZ/apps-flutter-patient` (2026-11-03). The owner's personal API key (for the management API) is stored nowhere; ask for it when needed (scopes `project:write`, `organization:read`) |

### What the docs decided about the doctor app (`setup.md` §1.7, README D4)

**Same project "Shifa", told apart by data**, until a paid plan makes a second project worthwhile:

| Mechanism | Patient app | Doctor app must mirror |
|---|---|---|
| Distinct id | `patient_<id>` | `doctor_<id>`; clinic staff `staff_<id>` |
| Super property | `app = patient` | `app = doctor` |
| Person property | `app = patient` | `app = doctor` |
| Event names | `events.md` | Its own catalogue, same conventions; a shared name (`appointment_cancelled`) is fine |
| Admin panel | - | **Not tracked at all** (owner's call, 2026-11-03) |

Consequences written down: every dashboard gets a dashboard-level filter on `app` and an app prefix in its name
("Doctor · Schedule"); cohorts and flags target the person property `app` or `distinct_id` matching `^doctor_`; both
apps share the 1M free events; everyone with project access sees both apps. A doctor who is also a patient is two
persons. A later split means a new project "Shifa Doctor" on the paid plan ($0 base); events can't be moved between
projects, so "decide before the doctor app's first tracked release if that matters".

Things the doctor app will force on the shared project:
- The patient dashboards may not all carry the `app = patient` dashboard filter yet (the docs prescribe it; I did
  not verify it in PostHog). Check before doctor events arrive, or patient charts will mix.
- `Application Installed` / `Updated` carry no `app` in either app (see 2, super properties).
- The 16 cohorts ("Activated", "Lapsed", "Android", "iOS", …) are defined without an `app` condition.

### What exists in PostHog

- **Dashboards (8 live):** Patient · Journey, Booking, Payments, Video visit, Tasks, Shifa AI, Notifications chat and
  documents, Reliability; 122 insights were created across nine, named `<dashboard> · <title>`. The ninth,
  Personalisation (id 1081945), is archived (soft-deleted, restorable).
- **Actions:** "Booking done" (181187) and "Came back" (181188).
- **Cohorts (16):** New this week, Activated, Booked never visited, Paying, Payment drop-offs, Task-active,
  Task-lapsing, AI users, AI-eligible never tried, Chat users, Lapsed, Video-preferring, Push-reachable, Android, iOS
  (definitions in `personalisation.md` §3).
- **Alerts (5, the free-plan limit):** daily `booking_failed` > 10, `payment_failed` > 5, `video_join_failed` > 5,
  `api_error` > 50, `ai_reply_failed` > 10, mailed to the owner. **No alert slot is left for the doctor app.**
- **Flags:** seven round-1 flags, archived. None active.
- The API scripts that created all this were in a job temp dir, not in the repo.

---

## 7. Not reusable for the doctor app as-is

Per the workspace rule, the doctor app gets its **own copy** of the pattern, never a shared module.

**Reusable as a pattern (copy and adapt):** the `AnalyticsSink` / `AnalyticsTracker` / `Analytics` facade, the
`sealed class AnalyticsEvent` shape with `clean()`, `TrackScreen`, `AnalyticsSettings`, `PostHogSink`,
`PostHogAnalytics.swift` (with the boolean fix), `AnalyticsBridge.kt`, the key plumbing (Gradle property,
`Secrets.xcconfig`, both workflows), `analyticsReason()` and the bucket helpers, `api_error` / `api_slow` in
`apiCall`, `AnalyticsEventTest` and `RecordingTracker`, the Debug-menu event list, the naming and privacy rules.

**Patient-specific, must be redone:**

1. **The whole event catalogue.** All 162 events and 28 screens describe patient flows (signup wizard, doctor search,
   booking, Click/Stripe checkout, check-ins, Shifa AI chat). The doctor app needs its own `events.md` and coverage
   matrix (schedule, calendar, patients list, visits, dental chart, treatment plans, forms, documents, tasks assigned,
   chat, AI, clinic and staff management, reports).
2. **Identity.** `ANALYTICS_APP = "patient"`, the `patient_` prefix (also hard-coded in the identify-reset check
   `current.startsWith("patient_")`), and `identify(profile: PatientProfile)`. The doctor app needs `doctor_<id>` and
   `staff_<id>` by role, and a rule for admins signing in to the doctor app (admins are "not tracked").
3. **Person properties.** `shifa_ai_enabled`, `has_photo`, `has_email`, `has_phone`, and the behavioural ones
   (`has_booked`, `has_paid`, `last_visit_type`, `has_checked_in`, …) are patient facts. Candidates for doctors:
   role, tier, clinic membership as a boolean. Not specialty: `privacy.md` treats a doctor's specialty as
   health-revealing for the patient side; the doctor side needs its own ruling.
4. **Privacy rules need a doctor-side review.** `privacy.md` is written about the patient as the user. In the doctor
   app the user is a professional, but every screen shows **other people's** health data, so the "other people" and
   "record ids" rows bite harder (patient ids, patient names in searches, diagnosis text, dental chart values, form
   answers, AI prompts and transcripts). The consent sign-off (D7) and the store labels were given for the patient app;
   the doctor app needs its own privacy-policy line and its own check with the owner.
5. **Web target.** The doctor app ships Kotlin/Wasm web (`wasmJsMain`, `web_cpanel_deploy.yml`); the patient app has
   no web. There is **no web sink to copy**: a third `AnalyticsSink` (posthog-js through JS interop, or
   `posthog-kmp` if it supports wasmJs — unchecked) is new work, including key injection in the web deploy workflow,
   the "debug never tracks" rule for web builds, and a decision on what replaces `Application Opened` lifecycle
   events. Project settings have autocapture, heatmaps and web vitals off, which suits a canvas-rendered Compose web
   app. Session replay on web would record the canvas, so D5 still applies.
6. **Name collision.** The doctor app already has `data/analytics/` (`AnalyticsRepository.kt`, `AnalyticsCsv.kt`)
   and `ui/analytics/` (`AnalyticsViewModel`, `ReportsScreen`) for the doctor's **business analytics feature**.
   The PostHog package and class names (`Analytics`, `AnalyticsEvent`, `AnalyticsScreen`) will clash or confuse;
   pick another package/name (for example `data/tracking/`).
7. **`PaymentReports`**, `paymentBadge()`, `appointmentState()`, `paymentFailureReason()`, `amountBucket` (UZS
   checkout buckets) are tied to the patient payment flow.
8. **`ENDPOINT_WORDS`** in `ApiHelper.kt` is the patient app's endpoint vocabulary; the doctor app has far more
   routes and needs its own list (or a different templating rule).
9. **Hooks.** Identify lives in the patient `HomeViewModel`; reset in the patient `AuthRepository` (with the iOS
   reinstall-wipe logic). The doctor app has different auth (12-hour tokens so far more `expired` restores, SMS
   sign-in, app lock, a `session` package) and needs its own hook points. `DemoMode` and `isDebugBuild` exist in the
   doctor app too, so D8 ports directly. The doctor app already has an `Application` class (`ShifaDoctorApp.kt`).
10. **Dashboards, cohorts, alerts, flags** are all patient ones. Doctor dashboards must be built from scratch, and
    the five free alert slots are used up.
11. **Cost model.** `setup.md` §1.6 assumes about 300 events per monthly active patient. Doctors use the app all day,
    so events per user will be much higher even though users are few; both apps share the 1M free events.
12. **Docs.** `docs/posthog/*` and `docs/implemented/analytics.md` belong to the patient repo. The doctor app needs
    its own set (workspace rule: the doctor app's contract docs live in its own repo).

Decisions the owner still has to make for the doctor app: same project or a new one before the first tracked release;
whether clinic staff are tracked as `staff_<id>`; whether web is tracked in round one; opt-out switch placement;
whether to revisit `posthog-kmp` now that Kotlin is 2.4.21.
