# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Shifa Doctor App** — A Flutter web/mobile application for doctors to manage appointments, patients, schedules, and consultations. Built with **Flutter Riverpod** (v2.5.1, manual providers) and integrates with a **Spring Boot (Kotlin)** backend via REST API.

**Dart SDK:** `^3.9.2` | **Package:** `shifa_doc_app_v1` | **Version:** `1.0.0+3`

**Platforms:** Web (primary, Firebase Hosting), Android, iOS, Windows

**Key Features:**
- Firebase phone authentication (OTP) for doctor login
- Video calling via Daily.co (daily_flutter on mobile, JS embed on web)
- Real-time chat with patients (text, voice recording, image/document uploads)
- Patient management with medical forms and document uploads
- Appointment scheduling with timezone-aware handling
- Calendar with table_calendar widget
- Admin panel (same build, hostname-based routing)
- AI-powered consultation notes
- Analytics dashboard with fl_chart
- Push notifications via FCM with deep linking
- PDF generation with Cyrillic font support (en/uz/ru)

## Git Workflow Rules

**NEVER commit or push directly to the `main` branch.** When the user is on `main` and wants to commit/push changes:

1. Create a new branch named after the feature or change (e.g., `feature/add-patient-search`, `fix/appointment-timezone-bug`).
2. Switch to that branch.
3. Stage and commit the changes on the new branch.
4. Push the new branch to the remote.
5. Suggest creating a PR from the new branch into `main`.

If the user is already on a non-main branch, commit and push normally on that branch.

### Commit Message Convention

Follow **Conventional Commits** format:

```
<type>(<scope>): <short description>

<optional body — explain WHY, not WHAT>
```

**Types:**
- `feat` — new feature or functionality
- `fix` — bug fix
- `refactor` — code restructuring without behavior change
- `style` — formatting, whitespace, missing semicolons (no logic change)
- `docs` — documentation changes
- `test` — adding or updating tests
- `chore` — build config, dependencies, CI/CD, tooling
- `perf` — performance improvement

**Scope** (optional): the feature area affected (e.g., `auth`, `appointments`, `chat`, `calendar`, `patients`, `admin`, `video`, `profile`, `tasks`, `schedule`, `notifications`, `shell`, `home`).

**Rules:**
- Subject line: imperative mood, lowercase, no period, max 72 characters (e.g., `feat(chat): add voice message playback`)
- Body: explain the motivation/context when the change is non-trivial
- One logical change per commit — don't mix unrelated changes
- Never use generic messages like "update", "fix", "changes", or "WIP"

## Development Commands

### Running the App

```bash
flutter run -d chrome                                                    # Dev (localhost:8080)
flutter run -d chrome --dart-define=API_BASE_URL=https://api.example.com # Custom backend
flutter run -d ios                                                       # iOS
flutter run -d android                                                   # Android
```

### Building

```bash
flutter build web                         # Dev build
flutter build web --release \             # Production build
  --dart-define=API_BASE_URL=https://api.example.com \
  --dart-define=ENVIRONMENT=production \
  --dart-define=GOOGLE_MAPS_API_KEY=your_key_here
flutter build ios --release               # iOS release
flutter build apk --release               # Android release
```

### Testing & Analysis

```bash
flutter test                # Run all tests
flutter test --coverage     # With coverage
flutter analyze             # Static analysis
```

### Firebase Deployment

```bash
flutter build web --release
firebase deploy --only hosting:doctor --project staging   # Doctor app
firebase deploy --only hosting:admin --project staging    # Admin panel
```

### Dependencies

```bash
flutter pub get       # Install
flutter pub upgrade   # Upgrade
flutter clean         # Clean build artifacts
```

## Code Architecture

### Project Structure

```
lib/
├── main.dart                    # Entry point (Firebase, timezones, i18n, FCM init)
├── firebase_options.dart        # Auto-generated Firebase config (web only)
├── app/
│   ├── app.dart                 # Root ConsumerStatefulWidget (MaterialApp + Riverpod)
│   ├── router.dart              # Named routes via onGenerateRoute + AppRoutes constants
│   └── theme.dart               # ThemeData (SF Pro Display, teal primary, 12dp radius)
├── core/
│   ├── api/                     # ApiClient (JWT injection, scope-based), AI API
│   ├── config/                  # AppConfig (reads --dart-define env vars)
│   ├── constants/               # Asset paths
│   ├── localization/            # AppLocalizations (manual i18n: en/uz/ru JSON files)
│   ├── models/                  # Shared models (profession)
│   ├── providers/               # languageProvider
│   ├── services/                # Timezone, Daily.co video, geocoding, FCM, inactivity timer
│   ├── theme/                   # AppColors (teal primary, semantic color system)
│   ├── utils/                   # Image, phone E.164, password, text cleaner, error formatter
│   └── widgets/                 # ShifaButton, PersonAvatar, analytics charts, AI panel
├── features/                    # 14 feature modules (see Feature Module Pattern)
│   ├── admin/                   # Admin panel (login, dashboard, users, tokens, audit logs)
│   ├── appointments/            # Appointments, video calls (Daily.co), PDF export
│   ├── auth/                    # Phone OTP login, email login, account creation
│   ├── calendar/                # Calendar view (table_calendar)
│   ├── chat/                    # Messaging (text, voice, image, document, typing indicator)
│   ├── debug/                   # Timezone debug screen
│   ├── home/                    # Dashboard with KPI cards and analytics charts
│   ├── notifications/           # Notification center
│   ├── patients/                # Patient records, forms, document viewer
│   ├── profile/                 # Doctor profile (location picker, timezone, profession)
│   ├── schedule/                # Doctor availability setup
│   ├── shell/                   # Main navigation shell (6 tabs: Chat, Home, Calendar, Patients, Tasks, Profile)
│   └── tasks/                   # Remote care task management (templates, assignments)
└── state/                       # Riverpod providers (one folder per feature)
    ├── auth/                    # AuthController, registration state
    ├── appointments/            # Appointment invalidation
    ├── calendar/                # Calendar controller
    ├── chat/                    # Conversations, messages, unread count
    ├── admin/                   # Admin providers and actions
    ├── notifications/           # Doctor notifications
    ├── patients/                # Patient list, documents, forms
    ├── profile/                 # ProfileAll (profile + contact + billing + settings)
    ├── shell/                   # Tab index state
    └── tasks/                   # Task list and actions
```

### Feature Module Pattern

Each feature follows a layered structure:

```
features/<feature_name>/
├── domain/              # Models and entities
├── presentation/        # Screens and widgets (UI)
├── application/         # Feature-specific providers (optional)
├── services/            # External integrations (optional)
└── data/                # Repositories (optional)
```

### State Management — Riverpod (v2.5.1, No Code Generation)

Providers live in `lib/state/<feature>/`. No `freezed` or `riverpod_generator` — all providers are manual.

**Provider types used in this project:**
- `StateProvider<T>` — simple reactive values (JWT token, language, tab index)
- `StateNotifierProvider<N, S>` — mutable state with controller logic (auth, patients, shell)
- `FutureProvider<T>` — async one-time fetches (profile, notifications)
- `FutureProvider.family<T, Arg>` — parameterized async (patient by ID, user search)
- `StreamProvider<T>` — continuous streams (unread chat count, polls every 10s)
- `Provider<T>` — computed/readonly (API client, services)

**Key providers:**

| Provider | Type | Purpose |
|----------|------|---------|
| `authTokenProvider` | StateProvider | JWT token storage |
| `authProvider` | StateNotifierProvider | Auth state & login/logout |
| `apiClientProvider` | Provider | Scoped HTTP client (doctor vs admin) |
| `profileAllProvider` | FutureProvider | Doctor profile (profile + contact + billing + settings) |
| `patientsProvider` | StateNotifierProvider | Patient list |
| `conversationsProvider` | FutureProvider | Chat conversations |
| `unreadCountProvider` | StreamProvider | Chat unread count (polls every 10s) |
| `shellProvider` | StateNotifierProvider | Current navigation tab |
| `languageProvider` | StateProvider | App language (en/uz/ru) |

**Consumption patterns:**
```dart
// Widgets extend ConsumerWidget or ConsumerStatefulWidget
final authState = ref.watch(authProvider);              // Reactive
ref.read(authProvider.notifier).login(user, pass);      // One-time action
ref.listen(authProvider, (prev, next) { ... });         // Side effects
ref.invalidate(profileAllProvider);                     // Force refresh
```

### API Client

**Location:** `lib/core/api/api_client.dart`

- Automatic JWT injection from `authTokenProvider`
- Scope-based access: `doctorApiClientProvider` (blocks `/api/admin/**`), `adminApiClientProvider` (only `/api/admin/**`)
- Auto-logout on 401/403 via callback
- Methods: `get`, `post`, `put`, `patch`, `delete`, `postMultipart`, `postWithBearer`, `postNoUnauthorizedCheck`

### Navigation

Named routes via `MaterialApp.onGenerateRoute` in `lib/app/router.dart`:
- `AppRoutes` — string constants for all routes
- `AppRouter.onGenerateRoute()` — main router with switch statement
- `ShellScope.pushNamed()` — nested navigation inside the authenticated shell
- `GlobalKey<NavigatorState>` — programmatic navigation from services (FCM tap handling)
- Arguments passed via `settings.arguments`

### Configuration

`lib/core/config/app_config.dart` reads `--dart-define` values:
- `API_BASE_URL` (default: `http://localhost:8080`)
- `ENVIRONMENT` (default: `development`)
- `GOOGLE_MAPS_API_KEY` (default: empty)

## Important Technical Details

### Timezone Handling (Critical)

"Shifa Global Time Architecture v2":
- **Backend:** UTC timestamps (`TIMESTAMPTZ` in PostgreSQL, `Instant` in Kotlin)
- **Doctor timezone:** IANA string in `doctor_profiles.practice_time_zone` (e.g., "Asia/Tashkent")
- **Frontend:** All display times converted to doctor's practice timezone via `timezone` package
- **Services:** `lib/core/services/timezone_service.dart`, `lib/core/utils/timezone_utils.dart`

**Rules when working with dates/times:**
1. Always use IANA timezone identifiers (never UTC offsets)
2. Convert to doctor's practice timezone for display
3. Send UTC timestamps to backend
4. Use `TZDateTime` from the `timezone` package, never raw `DateTime` for display

### Admin vs Doctor Entry Points

Same web build serves both interfaces:
- **Doctor:** Hostname without "admin" → splash → login → shell
- **Admin:** Hostname containing "admin" → admin login → admin shell
- **Detection:** `lib/core/util/admin_host.dart` (`isAdminHost`)

### Video Calling (Daily.co)

- **Mobile:** `daily_flutter` package (CallClient)
- **Web:** JavaScript embed via `daily_video_embed_web.dart`
- **Platform stubs:** `daily_flutter_stub.dart`, `daily_video_embed_stub.dart`
- **Token flow:** Backend `/api/video/token` → join Daily.co room

### Chat System

- Text, voice recording (`record` package), image uploads (`flutter_image_compress`), document sharing
- Audio playback via `audioplayers`
- Cached images via `cached_network_image`
- Unread count polled every 10 seconds via StreamProvider

### Localization

- **Languages:** English (en), Uzbek (uz), Russian (ru)
- **Implementation:** Manual maps in `lib/core/localization/app_localizations.dart`
- **Assets:** `assets/localization/{en,uz,ru}.json`
- **Usage:** `AppLocalizations.of(context)!.translate('key')`
- **No ARB files** — all strings in a Dart map with JSON file backing

### Firebase Services

- **Auth:** Phone OTP login
- **Cloud Messaging:** Push notifications with deep linking (appointments, patients, documents, tasks)
- **Config:** `lib/firebase_options.dart` (web only — Android/iOS throw UnsupportedError)
- **Projects:** staging (`shifa-doctor-staging`), QA (`shifa-doctor-qa`)

### PDF Generation

- `pdf` package with `DejaVuSans.ttf` font (Cyrillic support)
- Appointment consultation notes: `lib/features/appointments/services/appointment_pdf_service.dart`

## Flutter & Dart Best Practices

These rules apply to ALL new code written in this project. Follow them every time. When modifying existing code, follow these rules for the code you write — but do not refactor surrounding legacy code unless explicitly asked.

### Riverpod Rules

1. **Use manual providers only** — this project does NOT use `riverpod_generator`, `@riverpod`, or `freezed`. Never introduce code generation for providers.
2. **Provider placement follows a tiered pattern:**
   - Shared/cross-feature state → `lib/state/<feature>/` (auth, patients, chat, profile, shell, etc.)
   - Feature-specific application providers → `lib/features/<feature>/application/` (analytics, consultation notes, today's appointments)
   - Auth-specific providers → `lib/features/auth/presentation/providers/`
   - Core infrastructure → `lib/core/providers/` (language) and `lib/core/api/` (API client providers)
   - For new providers: prefer `lib/state/<feature>/`. Only place in `lib/features/<feature>/application/` if the provider is tightly coupled to a single feature's UI logic.
3. **Use `ref.watch()` in `build()` methods** for reactive UI. Use `ref.read()` only in callbacks, event handlers, and `initState`.
4. **Never call `ref.watch()` inside async functions, callbacks, or `onPressed`.** Always use `ref.read()` there.
5. **Cache invalidation:** Use `ref.invalidate(provider)` to discard and refetch. Use `ref.refresh(provider.future)` when you need to `await` the refreshed value. Both patterns are used in this codebase.
6. **Use `ConsumerWidget`** for stateless widgets needing providers. Use `ConsumerStatefulWidget` when you need `TextEditingController`, `AnimationController`, form state, or other lifecycle-dependent state. `setState()` is acceptable in `ConsumerStatefulWidget` for local UI state (selection, visibility toggles, form field values) — not every piece of state needs a provider.
7. **Keep providers small and focused.** One provider per concern. Compose them rather than building monolithic controllers.
8. **Use `.when(data:, loading:, error:)` on `AsyncValue`** for rendering async state. Always handle all three cases.
9. **Provider types in use:** `StateProvider`, `StateNotifierProvider`, `FutureProvider`, `FutureProvider.family`, `StreamProvider`, `Provider`. The project does NOT use `AsyncNotifierProvider` or `NotifierProvider` (newer Riverpod API) — do not introduce them.
10. **`autoDispose`:** Used selectively (~10% of providers) for data that should not persist when the widget is unmounted (e.g., notifications, analytics). Do not add `autoDispose` to providers that should retain state across navigation (auth, profile, patient list).

### Widget & UI Rules

1. **Prefer `ConsumerWidget` over `StatelessWidget`** when the widget reads providers. Use `StatelessWidget` only for pure UI components with no provider dependency.
2. **Use `const` constructors** wherever possible — the codebase has strong `const` adoption; maintain it.
3. **Extract reusable widgets into `lib/core/widgets/`** — never duplicate UI across features.
4. **Colors:** Use `AppColors` from `lib/core/theme/app_colors.dart` for the project's semantic colors (primary teal, destructive red, etc.). Material `Colors.*` (e.g., `Colors.grey.shade300`, `Colors.white`) is also used extensively throughout the codebase and is acceptable for standard Material colors. For new code: **never hardcode raw hex values** (e.g., `Color(0xFF1976D2)`) — use `AppColors` constants or standard `Colors.*` instead.
5. **Text styles:** The codebase predominantly uses inline `TextStyle(fontSize:, fontWeight:, color:)`. This is the established pattern. For new code, continue using inline `TextStyle` for consistency. Use `Theme.of(context).textTheme` only when the theme already defines the exact style you need.
6. **Buttons:** `ShifaButton` is used for primary actions. `TextButton` is used directly for secondary/tertiary actions (cancel, links, inline actions). For new code: use `ShifaButton` for prominent actions, `TextButton` for lightweight actions — avoid raw `ElevatedButton` or `OutlinedButton`.
7. **Forms:** Use `Form` + `GlobalKey<FormState>` + `TextFormField` with validators. Initialize controllers in `initState()`, dispose in `dispose()`.
8. **Never nest `Scaffold` widgets.** Each screen has exactly one Scaffold.
9. **Prefer `SizedBox` over `Container`** when you only need width/height spacing.
10. **Use `EdgeInsets.symmetric()` or `EdgeInsets.all()`** — keep spacing consistent (multiples of 4: 4, 8, 12, 16, 24, 32).

### Model & Data Rules

1. **Models are plain Dart classes** with manual `fromJson()` factory constructors. No `freezed`, no `json_serializable`, no code generation.
2. **Always provide null-safe parsing** in `fromJson()` — use `(json['field'] ?? defaultValue) as Type` pattern. Use helper methods like `_stringOrNull()` for safe type conversions when the backend contract is inconsistent.
3. **Implement `copyWith()`** on models that need immutable updates.
4. **Place models in `features/<feature>/domain/`** or `lib/core/models/` for shared models.
5. **Use enum types** for status fields (e.g., `AppointmentStatus`, `MessageType`, `TaskCategory`).

### API & Networking Rules

1. **Use the existing `ApiClient`** from `lib/core/api/api_client.dart` for all backend API calls. Access via `ref.read(apiClientProvider)` — never construct `ApiClient` directly. Exception: raw `http.get/post` is acceptable for external third-party APIs (Google Geocoding, blob downloads) and streaming SSE connections (AI API).
2. **Check response status codes explicitly:** handle 200, 4xx, 5xx distinctly.
3. **Validate response content-type before JSON parsing** — the backend may return HTML error pages. Check `response.headers['content-type']` for `application/json` before calling `jsonDecode()`. The auth controller (`lib/state/auth/auth_controller.dart`) has the reference implementation for this.
4. **Error messages:** Use `sanitizeErrorMessage()` from `lib/core/utils/error_formatter.dart` for user-facing error messages. The existing codebase has inconsistent error handling (most files do manual string manipulation), but all new code should use `sanitizeErrorMessage()`.
5. **Wrap API calls in try-catch** — catch network errors separately from API errors.
6. **Use `postWithBearer()`** for Firebase token verification, `postNoUnauthorizedCheck()` for video tokens.

### Navigation Rules

1. **Define all routes as constants in `AppRoutes`** in `lib/app/router.dart`.
2. **For new screens:** Use `Navigator.pushNamed()` or `ShellScope.pushNamed()` with route constants. Register the route in `AppRouter.onGenerateRoute()`. Note: some existing screens use `MaterialPageRoute` directly (auth flows, profile, chat image viewer) — this is legacy but functional.
3. **Pass data via `arguments` parameter** — never pass data through global state for navigation.
4. **Use `ShellScope.pushNamed()` for in-shell navigation** (screens within the authenticated shell's nested navigator). It falls back to `Navigator.pushNamed()` if no shell scope is found.
5. **For full-page transitions (auth flows), use `pushNamedAndRemoveUntil()`** to clear the stack.

### Error Handling Rules

1. **Use Riverpod's `FutureProvider` / `AsyncValue`** for async data — avoid `FutureBuilder` (one legacy usage exists in `calendar_screen.dart`).
2. **Always handle loading, data, and error states** — never show a blank screen on loading.
3. **Show `SnackBar` for user-facing errors** via `ScaffoldMessenger.of(context).showSnackBar()`. The codebase uses inline SnackBar construction — follow this pattern for consistency.
4. **Use `debugPrint()` for debug logging in new code** — do not add new `print()` statements (existing `print()` calls are legacy).
5. **Fire-and-forget operations** (like FCM token upload) can use `.then().catchError()` but must log failures.

### Localization Rules

1. **All user-facing strings must be localized** — use `AppLocalizations.of(context)!.translate('key')`. Do not add hardcoded English strings as fallback values in the `?? 'fallback'` pattern — if a key is missing, add it to the JSON files instead.
2. **Add new keys to all three JSON files** (`en.json`, `uz.json`, `ru.json`) in `assets/localization/`.
3. **Use the existing localization pattern** — don't introduce ARB files, `intl_utils`, or other i18n systems.
4. **Date/time formatting must respect locale** — use `intl` package with `initializeDateFormatting()`.

### Timezone Rules

1. **`DateTime.now()` usage:** Acceptable for non-display purposes (file naming, rate limiting, cache timestamps). **Never use `DateTime.now()` for displaying times to the user** — convert through `TimezoneService` / `timezone_utils.dart` to the doctor's practice timezone.
2. **Send UTC to backend, display in practice timezone** — this is the core invariant.
3. **Use `TZDateTime` from the `timezone` package** for timezone-aware display operations.
4. **Test timezone logic with non-UTC timezones** (e.g., "Asia/Tashkent", "America/New_York").

### File & Naming Conventions

1. **File names:** `snake_case.dart` — always.
2. **Suffixes:** `*_screen.dart` (full pages), `*_widget.dart` (reusable components), `*_provider.dart` / `*_providers.dart` (Riverpod providers), `*_controller.dart` (StateNotifiers), `*_service.dart` (business logic), `*_models.dart` (data classes), `*_actions.dart` (pure API action functions).
3. **Class names:** `PascalCase`. Private classes prefixed with `_`.
4. **Variables:** `camelCase`. Private fields prefixed with `_`.
5. **Provider names:** descriptive + `Provider` suffix (e.g., `patientsProvider`, `unreadCountProvider`).
6. **Imports:** Use absolute package imports (`package:shifa_doc_app_v1/...`). The codebase is predominantly absolute imports with some relative imports in older files — all new code must use absolute imports.

### Platform-Specific Code Rules

1. **Use conditional imports** for web vs mobile (see `daily_video_embed_web.dart` / `daily_video_embed_stub.dart` pattern).
2. **Never import `dart:html` or `dart:js` directly** — use stub files with conditional imports.
3. **Test on Chrome (web) first** — web is the primary platform.

### Image & Media Rules

1. **Always compress images before upload** using `flutter_image_compress`.
2. **Use `cached_network_image` for network images** — never load images with raw `Image.network` (the codebase follows this correctly).
3. **Use `image_utils.dart`** for URL normalization and image processing.
4. **Voice messages:** Record with `record` package, play with `audioplayers`.

### Security Rules

1. **Never hardcode API keys, tokens, or secrets** in Dart code — use `--dart-define` or environment config.
2. **JWT tokens are stored in `SharedPreferences`** — follow the existing `authTokenProvider` pattern. `SharedPreferences` is also used for language preference and OTP rate-limiting — this is acceptable for non-sensitive local data.
3. **API client handles 401 automatically** — don't add custom unauthorized handling in feature code.
4. **Validate all user input** before sending to the backend.
5. **Never log sensitive data** (tokens, passwords, patient data) even in debug mode.
6. **HIPAA awareness:** This is a healthcare app with patient data. Never expose patient information in logs, error messages, or analytics. Use patient IDs, not names, in debug output.

### Performance Rules

1. **Use `const` widgets** wherever possible to prevent unnecessary rebuilds.
2. **Keep `build()` methods lightweight** — move computation to providers or separate methods.
3. **Use `ref.watch()` on the narrowest provider possible** — don't watch a parent provider when you only need a child field.
4. **Compress images before upload** — never send full-resolution photos.
5. **Dispose controllers, subscriptions, and timers** in `dispose()` — prevent memory leaks. The codebase has good disposal discipline; maintain it.

## Common Tasks

### Adding a New Feature

1. Create `lib/features/<feature_name>/` with `domain/`, `presentation/` subfolders.
2. Add models in `domain/<feature>_models.dart`.
3. Create screen in `presentation/<feature>_screen.dart` extending `ConsumerWidget` or `ConsumerStatefulWidget`.
4. Add providers in `lib/state/<feature_name>/` — controller, actions, providers.
5. Register route in `lib/app/router.dart` (add constant to `AppRoutes`, add case to `onGenerateRoute`).
6. Add navigation from shell or related screens.
7. Add all user-facing strings to `en.json`, `uz.json`, `ru.json`.

### Adding a New API Endpoint

1. Use `ref.read(apiClientProvider)` to get the API client.
2. Create action function in `lib/state/<feature>/<feature>_actions.dart`.
3. Call from StateNotifier or directly from widget callbacks.
4. Handle response: check status code, parse JSON, show error on failure.
5. Invalidate related providers if data changed.

### Modifying Existing Feature

1. Read existing code in `lib/features/<feature>/` and `lib/state/<feature>/`.
2. Understand data flow: UI → Provider/Controller → ApiClient → Backend.
3. Follow existing patterns in that feature — don't introduce new patterns.
4. Test on Chrome first.

## Backend Integration

- **Backend:** Spring Boot (Kotlin) REST API
- **Database:** PostgreSQL (UTC timestamps with `TIMESTAMPTZ`)
- **Auth:** JWT tokens from `/api/auth/login`
- **API Base:** `--dart-define=API_BASE_URL` (default: `http://localhost:8080`)
- **Endpoints:** REST conventions (`/api/<resource>`)
- **Doctor endpoints:** `/api/doctors/**`, `/api/patients/**`, `/api/appointments/**`
- **Admin endpoints:** `/api/admin/**` (requires admin-scoped API client)

## Environments

| Environment | Firebase Project | API URL | Deploy Command |
|-------------|-----------------|---------|----------------|
| Development | — | `http://localhost:8080` | `flutter run -d chrome` |
| Staging | `shifa-doctor-staging` | Set via `--dart-define` | `firebase deploy --project staging` |
| QA | `shifa-doctor-qa` | Set via `--dart-define` | `firebase deploy --project qa` |
| Production | TBD | Set via `--dart-define` | `firebase deploy --project production` |
