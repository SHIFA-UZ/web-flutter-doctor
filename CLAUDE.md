# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Shifa Doctor App - A Flutter web/mobile application for doctors to manage appointments, patients, schedules, and consultations. Uses **Flutter Riverpod** for state management and integrates with a Spring Boot backend.

**Key Features:**
- Firebase phone authentication (OTP) for doctor login
- Video calling via Daily.co (daily_flutter)
- Real-time chat with patients (text, voice, images)
- Patient management with document uploads
- Appointment scheduling with timezone-aware handling
- Calendar with table_calendar widget
- Admin panel (separate entry point via hostname detection)
- AI-powered consultation notes
- Analytics dashboard with fl_chart

## Development Commands

### Running the App

```bash
# Development (local backend at localhost:8080)
flutter run -d chrome

# Run with custom API URL
flutter run -d chrome --dart-define=API_BASE_URL=https://api-staging.example.com

# iOS
flutter run -d ios

# Android
flutter run -d android
```

### Building

```bash
# Development build
flutter build web

# Production build with environment variables
flutter build web --release \
  --dart-define=API_BASE_URL=https://api.example.com \
  --dart-define=ENVIRONMENT=production \
  --dart-define=GOOGLE_MAPS_API_KEY=your_key_here

# iOS build
flutter build ios --release

# Android build
flutter build apk --release
```

### Testing

```bash
# Run all tests
flutter test

# Run with coverage
flutter test --coverage

# Analyze code
flutter analyze
```

### Firebase Deployment

```bash
# Build once
flutter build web --release

# Deploy doctor app (default site)
firebase deploy --only hosting:doctor --project staging

# Deploy admin panel (same build, different site)
firebase deploy --only hosting:admin --project staging

# First-time setup (if deploy targets not configured)
firebase target:apply hosting doctor shifa-doctor-staging
firebase target:apply hosting admin shifa-admin-staging
```

### Dependencies

```bash
# Install/update dependencies
flutter pub get

# Upgrade dependencies
flutter pub upgrade

# Clean build artifacts
flutter clean
```

## Code Architecture

### Project Structure

```
lib/
├── main.dart                    # Entry point (initializes Firebase, timezones, i18n)
├── app/                         # App-level configuration
│   ├── app.dart                 # Root widget (MaterialApp with Riverpod)
│   ├── router.dart              # Centralized route definitions
│   └── theme.dart               # App theme configuration
├── core/                        # Shared infrastructure
│   ├── api/                     # API clients (ApiClient, AI API)
│   ├── config/                  # AppConfig (reads --dart-define env vars)
│   ├── services/                # Core services (timezone, video, geocoding)
│   ├── widgets/                 # Reusable widgets
│   ├── localization/            # i18n (AppLocalizations)
│   └── utils/                   # Utilities (image, validation, text cleaning)
├── features/                    # Feature modules
│   ├── auth/                    # Authentication (login, verify, create account)
│   ├── home/                    # Dashboard with analytics
│   ├── appointments/            # Appointment management, video calls
│   ├── patients/                # Patient records, forms, documents
│   ├── calendar/                # Calendar view with table_calendar
│   ├── schedule/                # Doctor availability setup
│   ├── tasks/                   # Task management (remote care)
│   ├── chat/                    # Patient chat (messages, voice, images)
│   ├── notifications/           # Notification center
│   ├── admin/                   # Admin panel screens
│   ├── profile/                 # Doctor profile management
│   └── shell/                   # Main navigation shell
└── state/                       # Riverpod state management
    ├── auth/                    # AuthController (JWT, login/logout)
    ├── appointments/            # Appointment state
    ├── patients/                # Patient list state
    └── [other feature states]   # One state folder per feature
```

### Feature Module Pattern

Each feature follows a layered architecture:

```
features/<feature_name>/
├── domain/              # Models, entities, business logic
├── presentation/        # Screens and widgets (UI layer)
├── application/         # Application services (orchestration)
└── services/            # Feature-specific services
```

**Example:** `features/appointments/`
- `domain/appointment_models.dart` - Data models
- `presentation/video_call_screen.dart` - UI screens
- `application/doctor_analytics_service.dart` - Business logic
- `services/video_service.dart` - External integrations

### State Management with Riverpod

**Pattern:** Riverpod providers are centralized in `lib/state/<feature>/` folders.

**Key Providers:**
- `authTokenProvider` (StateProvider) - JWT token
- `authControllerProvider` (StateNotifierProvider) - Auth state
- `apiClientProvider` (Provider) - Configured ApiClient instance
- `doctorProfileProvider` (FutureProvider) - Current doctor profile

**Example State Controller:**

```dart
// lib/state/auth/auth_controller.dart
class AuthController extends StateNotifier<AuthState> {
  final Ref ref;

  Future<void> login(String username, String password) async {
    final api = ref.read(apiClientProvider);
    // ... API call, update state
  }
}

final authControllerProvider = StateNotifierProvider<AuthController, AuthState>((ref) {
  return AuthController(ref);
});
```

**Consuming providers:**
```dart
// In widgets
final authState = ref.watch(authControllerProvider);
ref.read(authControllerProvider.notifier).login(username, password);
```

### API Client

**Location:** `lib/core/api/api_client.dart`

**Key Features:**
- Automatic JWT token injection from `authTokenProvider`
- 401/403 handling with automatic logout callback
- Specialized methods: `postWithBearer` (Firebase auth), `postNoUnauthorizedCheck` (video tokens)

**Usage:**
```dart
final api = ref.read(apiClientProvider);
final response = await api.get('/api/doctors/profile');
final response = await api.post('/api/patients', patientData);
```

### Configuration & Environment

**Configuration file:** `lib/core/config/app_config.dart`

Reads build-time environment variables via `--dart-define`:
- `API_BASE_URL` - Backend API URL (default: `http://localhost:8080`)
- `ENVIRONMENT` - Environment name (development/staging/production)
- `GOOGLE_MAPS_API_KEY` - Google Maps API key for geocoding

**Access in code:**
```dart
import 'package:shifa_doc_app_v1/core/config/app_config.dart';

final apiUrl = AppConfig.apiBaseUrl;
final isProduction = AppConfig.isProduction;
```

## Important Technical Details

### Timezone Handling (Critical)

The app implements "Shifa Global Time Architecture v2":
- **Backend stores:** All timestamps as UTC (`TIMESTAMPTZ` in PostgreSQL, `Instant` in Kotlin)
- **Doctor practice timezone:** Stored in `doctor_profiles.practice_time_zone` (IANA timezone string, e.g., "Asia/Tashkent")
- **Frontend displays:** All times converted to doctor's practice timezone using `timezone` package
- **Service:** `lib/core/services/timezone_service.dart` handles conversions

**When working with dates/times:**
1. Always use IANA timezone identifiers (not UTC offsets)
2. Convert to doctor's practice timezone for display
3. Send UTC timestamps to backend
4. See `SHIFA_DATETIME_TIMEZONE_AUDIT.md` for detailed audit

### Firebase Authentication

- **Phone OTP login** for doctors via Firebase Auth
- **Setup:** Requires Firebase project configuration (`flutterfire configure`)
- **Login flow:** `VerifyKeyScreen` → Firebase phone auth → JWT from backend
- **Config file:** `lib/firebase_options.dart` (auto-generated)

### Admin vs Doctor Entry Points

The same web build serves both doctor and admin interfaces:
- **Doctor app:** Any hostname without "admin" → shows splash/verify screen
- **Admin panel:** Hostname containing "admin" → shows admin login directly
- **Logic:** `lib/features/shell/presentation/main_shell.dart` checks hostname

### Video Calling

- **Provider:** Daily.co via `daily_flutter` package
- **Flow:** Request video token from backend → join Daily.co room
- **Screens:** `waiting_room_screen.dart`, `video_call_screen.dart`
- **Service:** `lib/core/services/daily_video_service.dart`

### Chat System

- **Features:** Text messages, voice recording, image uploads
- **Location:** `lib/features/chat/`
- **Audio:** `audioplayers` package for playback, `record` package for recording
- **Images:** `cached_network_image` for display, `flutter_image_compress` for uploads

### Localization (i18n)

- **Languages:** English (en), Uzbek (uz), Russian (ru)
- **Implementation:** `lib/core/localization/app_localizations.dart`
- **Initialization:** `main.dart` calls `initializeDateFormatting` for calendar
- **Provider:** `languageProvider` in `lib/core/providers/language_provider.dart`

### PDF Generation

- **Package:** `pdf` for generating consultation notes
- **Font:** DejaVuSans.ttf (supports Cyrillic characters for Russian/Uzbek)
- **Location:** `assets/fonts/DejaVuSans.ttf`
- **Usage:** See patient consultation note generation features

## Common Patterns & Conventions

### Navigation

**Centralized routing:** `lib/app/router.dart`

```dart
// Navigate to route
Navigator.pushNamed(context, AppRoutes.patientForm, arguments: patientId);

// Go back
Navigator.pop(context);
```

### Error Handling

**API calls:**
```dart
try {
  final response = await api.get('/api/endpoint');
  if (response.statusCode == 200) {
    // Success
  } else {
    // Handle error (show snackbar)
  }
} catch (e) {
  // Network error
}
```

### Form Validation

- **Location:** `lib/core/utils/password_validation.dart`
- **Pattern:** Use validators in TextFormField widgets

### Image Handling

- **Utils:** `lib/core/utils/image_utils.dart`
- **Compression:** Always compress images before upload using `flutter_image_compress`
- **Picker:** `image_picker` package (web and mobile support)

## Testing Notes

- Basic widget test exists in `test/widget_test.dart`
- No comprehensive test suite currently (add tests for new features as needed)
- Follow Flutter testing best practices: widget tests, integration tests, unit tests

## Documentation Files

The repository contains extensive documentation:
- `README_DEPLOYMENT.md` - Deployment guide with environment configuration
- `FIREBASE_QUICKSTART.md` - Quick Firebase hosting setup
- `FIREBASE_SETUP.md` - Detailed Firebase configuration
- `SETUP_FIREBASE_PHONE.md` - Firebase phone auth setup
- `SHIFA_DATETIME_TIMEZONE_AUDIT.md` - Comprehensive timezone architecture
- `GOOGLE_MAPS_SETUP.md` - Google Maps API configuration
- `CYRILLIC_FONT_SETUP.md` - PDF font configuration for Cyrillic

## Common Tasks

### Adding a New Feature

1. Create feature folder in `lib/features/<feature_name>/`
2. Add domain models in `domain/`
3. Create screens in `presentation/`
4. Add Riverpod providers in `lib/state/<feature_name>/`
5. Update router in `lib/app/router.dart` if needed
6. Add navigation in shell or other screens

### Adding a New API Endpoint

1. Use `ApiClient` from `lib/core/api/api_client.dart`
2. Call from state controller or service
3. Handle responses and errors
4. Update UI based on state changes

### Modifying Existing Feature

1. Read existing code in `lib/features/<feature>/` and `lib/state/<feature>/`
2. Understand the data flow: UI → Controller → API → Backend
3. Make changes following existing patterns
4. Test in development environment first

## Backend Integration

- **Backend:** Spring Boot (Kotlin) REST API
- **Database:** PostgreSQL
- **Authentication:** JWT tokens (returned by `/api/auth/login`)
- **API Base:** Configurable via `API_BASE_URL` environment variable
- **Endpoints:** Follow REST conventions (`/api/<resource>`)
