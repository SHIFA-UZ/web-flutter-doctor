# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Shifa Doctor App** - A Flutter application for doctors to manage appointments, patients, and schedules. This is a doctor-facing medical app with features for video consultations, in-person appointments, patient management, and scheduling.

## Development Commands

### Running the App
```bash
# Run in debug mode (default: localhost:4000 API)
flutter run

# Run with custom API base URL
flutter run --dart-define=API_BASE=https://api.example.com

# Run on specific device
flutter run -d chrome  # Web
flutter run -d macos   # macOS
flutter run -d <device-id>
```

### Testing
```bash
# Run all tests
flutter test

# Run specific test file
flutter test test/widget_test.dart

# Run tests with coverage
flutter test --coverage
```

### Code Quality
```bash
# Static analysis
flutter analyze

# Format code
flutter format lib/ test/

# Check formatting without making changes
flutter format --set-exit-if-changed lib/ test/
```

### Dependencies
```bash
# Get dependencies
flutter pub get

# Update dependencies
flutter pub upgrade

# Check for outdated packages
flutter pub outdated
```

### Build
```bash
# Build APK (Android)
flutter build apk

# Build iOS
flutter build ios

# Build web
flutter build web

# Build macOS
flutter build macos
```

## Architecture

This project follows a **feature-based clean architecture** pattern with clear separation of concerns:

### Directory Structure
```
lib/
├── main.dart                    # App entry point
├── core/                        # Shared utilities and services
│   ├── constants/              # App-wide constants (assets, etc.)
│   └── services/               # Shared services (ApiClient, Session)
├── app/                        # App-level configuration
│   ├── app.dart                # Main app widget with bootstrap logic
│   ├── router.dart             # Centralized routing (AppRouter, AppRoutes)
│   └── theme.dart              # App theme configuration
└── features/                   # Feature modules
    └── <feature>/
        ├── domain/             # Business models and entities
        ├── data/               # Repositories and API clients
        └── presentation/       # UI screens and widgets
```

### Key Features
- **auth**: Authentication flow (splash → verify → login → create account → setup schedule)
- **shell**: Main app shell with sidebar navigation (5 tabs: chat, home, calendar, patients, profile)
- **appointments**: In-person, video call, and waiting room screens
- **calendar**: Schedule and calendar management
- **patients**: Patient list and management
- **chat**: Doctor-patient messaging
- **home**: Dashboard/home screen
- **schedule**: Doctor schedule setup
- **profile**: Doctor profile management

### Core Services

**Session** (`lib/core/services/session.dart`):
- Singleton pattern for managing global auth state
- Stores: `token`, `doctorId`, `doctorName`
- Use `Session().isAuthenticated` to check login status
- Call `Session().clear()` on logout

**ApiClient** (`lib/core/services/api_client.dart`):
- Singleton Dio client with automatic Bearer token injection
- Base URL defaults to `http://localhost:4000`
- Override via: `--dart-define=API_BASE=<url>`
- 10-second timeout for connect and receive
- Automatically adds Authorization header from Session

### Routing

All routes are defined in `lib/app/router.dart`:
- Use `AppRoutes.*` constants for navigation (e.g., `AppRoutes.login`)
- Pass arguments via `Navigator.pushNamed(context, route, arguments: data)`
- Route arguments are type-cast in `AppRouter.onGenerateRoute`
- Example: Appointment screens expect `Appointment` object as argument

### Authentication Flow
1. **Splash**: Attempts token restoration via `AuthRepositoryHttp().tryRestore()`
2. If restored → direct to **MainShell** (app home)
3. If not → **VerifyKeyScreen** → **LoginScreen** → **CreateAccountScreen** → **AccountInformationScreen** → **SetupScheduleScreen** → **MainShell**

### Data Layer Pattern
Features follow a repository pattern:
- `*_models.dart` in `domain/` - Business entities
- `*_api.dart` in `data/` - Raw API calls (if needed)
- `*_repository.dart` or `*_repository_http.dart` in `data/` - Repository interface/implementation
- Repositories use `ApiClient().dio` for HTTP requests
- Use `shared_preferences` for local persistence (see auth token restoration)

### UI Patterns
- Screens are StatefulWidget in `presentation/` directories
- MainShell uses indexed stack pattern with sidebar navigation
- Material design with custom theme in `lib/app/theme.dart`
- Brand colors: Primary teal (#17C3B2), darker teal (#13A89E)

## Important Notes

### API Configuration
- Backend defaults to `http://localhost:4000`
- Change via: `flutter run --dart-define=API_BASE=<your-url>`
- ApiClient is a singleton - access via `ApiClient().dio`

### State Management
- Currently using StatefulWidget with setState
- Session is global singleton for auth state
- No state management library (Provider, Riverpod, Bloc) is used yet

### Adding New Features
1. Create feature directory under `lib/features/<feature_name>/`
2. Add `domain/` for models (`<feature>_models.dart`)
3. Add `data/` for repository (`<feature>_repository_http.dart`)
4. Add `presentation/` for screens (`<feature>_screen.dart`)
5. Register routes in `lib/app/router.dart` if needed
6. Follow existing naming conventions

### Navigation
- Use named routes via `AppRoutes` constants
- Don't hardcode route strings
- Pass data through `arguments` parameter, not constructor (except for simple data)

### Code Style
- Use `flutter_lints` rules (enforced via `analysis_options.yaml`)
- Run `flutter analyze` before committing
- Format with `flutter format` (2-space indentation)
