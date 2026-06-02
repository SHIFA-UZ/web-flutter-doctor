# Shifa Doctor App

A Flutter application for doctors to manage appointments, patients, schedules, and real-time consultations. Built for web (primary), Android, and iOS.

## Tech Stack

- **Framework:** Flutter (Dart SDK ^3.9.2)
- **State Management:** Flutter Riverpod 2.5.1 (manual providers, no code generation)
- **Backend:** Spring Boot (Kotlin) REST API with PostgreSQL
- **Auth:** Firebase Phone OTP + JWT tokens
- **Video Calls:** Daily.co (daily_flutter on mobile, JS embed on web)
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Hosting:** Firebase Hosting (SPA)
- **Localization:** English, Uzbek, Russian

## Features

- Phone OTP authentication for doctors
- Video and in-person appointment management
- Patient records with medical forms and document uploads
- Real-time chat (text, voice messages, images, documents)
- Calendar with availability scheduling
- AI-powered consultation notes
- Analytics dashboard with KPI cards and charts
- PDF export of consultation notes (Cyrillic font support)
- Push notifications with deep linking
- Admin panel (same build, hostname-based routing)
- Multi-timezone support (Shifa Global Time Architecture v2)

## Prerequisites

- [Flutter SDK](https://docs.flutter.dev/get-started/install) (stable channel)
- [Firebase CLI](https://firebase.google.com/docs/cli) (for deployment)
- Chrome browser (for web development)
- A running backend instance (Spring Boot) or access to staging API

## Getting Started

### 1. Clone and install dependencies

```bash
git clone <repository-url>
cd web-flutter-doctor
flutter pub get
```

### 2. Run in development

```bash
# Web (default — connects to localhost:8080)
flutter run -d chrome

# With custom backend URL
flutter run -d chrome --dart-define=API_BASE_URL=https://your-api.example.com

# Mobile
flutter run -d ios
flutter run -d android
```

### 3. Environment variables

Set via `--dart-define` at build time:

| Variable | Default | Description |
|----------|---------|-------------|
| `API_BASE_URL` | `http://localhost:8080` | Backend API URL |
| `ENVIRONMENT` | `development` | Environment name (development/staging/production) |
| `GOOGLE_MAPS_API_KEY` | — | Google Maps API key for geocoding |

## Building

```bash
# Development
flutter build web

# Production (--pwa-strategy=none avoids stale JS from Flutter’s default offline-first service worker)
flutter build web --release \
  --pwa-strategy=none \
  --dart-define=API_BASE_URL=https://api.example.com \
  --dart-define=ENVIRONMENT=production \
  --dart-define=GOOGLE_MAPS_API_KEY=your_key

# Mobile
flutter build ios --release
flutter build apk --release
```

## Deployment

The app is deployed to Firebase Hosting with two targets from the same build:

- **Doctor app** — any hostname without "admin"
- **Admin panel** — hostname containing "admin"

```bash
# Build
flutter build web --release --pwa-strategy=none --dart-define=API_BASE_URL=https://api.example.com

# Deploy doctor app
firebase deploy --only hosting:doctor --project staging

# Deploy admin panel
firebase deploy --only hosting:admin --project staging
```

### Firebase projects

| Environment | Project ID |
|-------------|------------|
| Staging | `shifa-doctor-staging` |
| QA | `shifa-doctor-qa` |

### First-time hosting setup

```bash
firebase target:apply hosting doctor shifa-doctor-staging
firebase target:apply hosting admin shifa-admin-staging
```

# Mobile (Android / iOS)

The same Flutter codebase ships as native apps for doctors.

| Platform | Bundle ID | App name |
|----------|-----------|----------|
| Android | `com.shifa.doctorapp` | Shifa Doctor |
| iOS | `com.shifa.doctorapp` | Shifa Doctor |

```bash
# Production mobile build
flutter build apk --release \
  --dart-define=ENVIRONMENT=production \
  --dart-define=API_BASE_URL=https://shifa-doc-backend-mvp-production.up.railway.app

flutter build appbundle --release \
  --dart-define=ENVIRONMENT=production \
  --dart-define=API_BASE_URL=https://shifa-doc-backend-mvp-production.up.railway.app
```

CI workflows: `.github/workflows/flutter_ci.yml`, `google_play_deploy.yml`, `ios_testflight.yml`.

**Note:** Register `com.shifa.doctorapp` in Firebase (`shifa-doctor-staging` / production) and provide `google-services.json` / `GoogleService-Info.plist` via CI secrets before store upload.

Clinic staff (`CLINIC_STAFF` role) are blocked on native mobile in v1 — web portal only.

## Project Structure

```
lib/
├── main.dart                 # Entry point (Firebase, timezone, i18n, FCM init)
├── app/
│   ├── app.dart              # Root widget (MaterialApp + Riverpod)
│   ├── router.dart           # Named routes and onGenerateRoute
│   └── theme.dart            # ThemeData (SF Pro Display, teal primary)
├── core/
│   ├── api/                  # ApiClient (JWT, scoped access, AI API)
│   ├── config/               # AppConfig (--dart-define env vars)
│   ├── localization/         # AppLocalizations (en/uz/ru JSON)
│   ├── services/             # Timezone, video, geocoding, FCM, inactivity timer
│   ├── theme/                # AppColors
│   ├── utils/                # Image, phone, password, error formatting
│   └── widgets/              # ShifaButton, PersonAvatar, charts, AI panel
├── features/
│   ├── admin/                # Admin panel (dashboard, users, tokens, audit)
│   ├── appointments/         # Appointments, video calls, PDF export
│   ├── auth/                 # Phone OTP login, email login, registration
│   ├── calendar/             # Calendar view (table_calendar)
│   ├── chat/                 # Messaging (text, voice, image, document)
│   ├── home/                 # Dashboard with analytics
│   ├── notifications/        # Notification center
│   ├── patients/             # Patient records, forms, documents
│   ├── profile/              # Doctor profile management
│   ├── schedule/             # Availability setup
│   ├── shell/                # Main navigation shell (6 tabs)
│   └── tasks/                # Remote care task management
└── state/                    # Riverpod state management (one folder per feature)
    ├── auth/                 # AuthController, JWT, registration
    ├── chat/                 # Conversations, messages, unread count
    ├── patients/             # Patient list, documents, forms
    ├── profile/              # Doctor profile
    └── ...                   # Other feature state
```

### Feature module pattern

```
features/<feature>/
├── domain/          # Models and entities
├── presentation/    # Screens and widgets
├── application/     # Feature-specific providers (optional)
├── services/        # External integrations (optional)
└── data/            # Repositories (optional)
```

## Architecture

### Data flow

```
UI (ConsumerWidget) → ref.watch/read → Riverpod Provider → ApiClient → Backend API → PostgreSQL
```

### State management

Riverpod with manual providers (no code generation):

- `StateProvider` — simple reactive values (JWT token, language, tab index)
- `StateNotifierProvider` — mutable state with controller logic (auth, patients)
- `FutureProvider` / `FutureProvider.family` — async data fetching
- `StreamProvider` — continuous streams (chat unread count)
- `Provider` — computed/readonly (API client)

### API client

Centralized HTTP client (`lib/core/api/api_client.dart`) with:
- Automatic JWT token injection
- Scope-based access control (doctor vs admin endpoints)
- 401/403 auto-logout
- Multipart uploads

### Timezone handling

All timestamps follow the "Shifa Global Time Architecture v2":
- Backend stores UTC (`TIMESTAMPTZ` in PostgreSQL)
- Doctor's practice timezone stored as IANA string (e.g., "Asia/Tashkent")
- Frontend converts to practice timezone for display via `timezone` package

## Testing

```bash
flutter test              # Run all tests
flutter test --coverage   # With coverage report
flutter analyze           # Static analysis
```

## Key Dependencies

| Package | Purpose |
|---------|---------|
| `flutter_riverpod` | State management |
| `firebase_core` / `firebase_auth` / `firebase_messaging` | Auth & push notifications |
| `http` | HTTP client |
| `daily_flutter` | Video calling (Daily.co) |
| `table_calendar` | Calendar widget |
| `fl_chart` | Analytics charts |
| `audioplayers` / `record` | Voice message playback & recording |
| `cached_network_image` | Network image caching |
| `flutter_image_compress` | Image compression before upload |
| `pdf` | PDF generation |
| `timezone` / `iana_time_zone` | Timezone-aware date handling |
| `intl` | Internationalization & date formatting |
| `flutter_map` / `geocoding` | Maps & geocoding |
| `shared_preferences` | Local key-value storage |
| `image_picker` / `file_picker` | File & image selection |

## Scripts

Build scripts are in the `scripts/` directory:

| Script | Platform | Description |
|--------|----------|-------------|
| `build_staging.sh` | macOS/Linux | Build web for staging |
| `build_staging.bat` | Windows | Build + deploy to staging |
| `build_android.bat` | Windows | Build Android APK/AAB |
| `build_qa.bat` | Windows | Build for QA environment |
| `export_localization_json.js` | Node.js | Export i18n strings |

## License

Private and proprietary. All rights reserved.
