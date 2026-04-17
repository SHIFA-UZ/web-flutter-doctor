# Deployment Guide - Phase 1: Environment Configuration

## Overview

This app now uses environment-aware configuration. The API URL and other settings can be changed without modifying code.

## 🚀 Quick Start: Firebase Staging

**For quick staging deployment, see:** [FIREBASE_QUICKSTART.md](FIREBASE_QUICKSTART.md)

**For detailed setup, see:** [FIREBASE_SETUP.md](FIREBASE_SETUP.md)

### Doctor vs Admin – separate URLs

The same web build is used for both the **doctor app** and the **admin panel**. The app chooses the entry point from the **host**:

- **Doctor app**: e.g. `https://shifa-doctor-staging.web.app` → splash → verify → doctor login (no admin link).
- **Admin panel**: e.g. `https://shifa-admin-staging.web.app` → admin login directly.

**One-time setup:** In [Firebase Console](https://console.firebase.google.com) → your project → Hosting → add another site (e.g. `shifa-admin-staging`). The hostname must contain `admin` so the app opens the admin login.

**Deploy:**

```bash
# Build once
flutter build web --release

# Doctor app (default site)
firebase deploy --only hosting:doctor --project staging

# Admin panel (separate site, same build)
firebase deploy --only hosting:admin --project staging
```

If you see *Deploy target doctor not configured*, run once (then deploy again):

```bash
firebase target:apply hosting doctor shifa-doctor-staging
firebase target:apply hosting admin shifa-admin-staging
```

## How It Works

### Flutter App Configuration

The app reads configuration from build-time environment variables using `--dart-define` flags.

**Current Configuration:**
- **Development (default)**: `http://localhost:8080`
- **Production**: Set via build command

### Backend Configuration

The backend uses Spring Boot profiles:
- **Development**: `application.yml` (default)
- **Production**: `application-prod.yml` (uses environment variables)

## Building for Different Environments

### Development (Local)
```bash
# Flutter - uses default localhost
flutter run -d chrome

# Backend - uses application.yml
./gradlew bootRun
```

### Production Build

#### Flutter Web
```bash
cd shifa_doc_app_v1

# Build with production API URL
flutter build web --release \
  --dart-define=API_BASE_URL=https://api.yourdomain.com \
  --dart-define=ENVIRONMENT=production

# Output: build/web/
```

#### Backend
```bash
cd shifa-doctor-backend

# Set environment variables
export DATABASE_URL=jdbc:postgresql://your-db-host:5432/shifa
export DB_USERNAME=your_db_user
export DB_PASSWORD=your_secure_password
export JWT_SECRET=your_very_long_random_secret
export PUBLIC_BASE_URL=https://api.yourdomain.com
export OPENAI_API_KEY=your_openai_key
export OPENAI_PROJECT_ID=your_project_id

# Build JAR
./gradlew clean build -x test

# Run with production profile
java -jar build/libs/shifa-backend-*.jar --spring.profiles.active=prod
```

## Environment Variables Reference

### Required for Production

| Variable | Description | Example |
|----------|-------------|---------|
| `DATABASE_URL` | PostgreSQL connection string | `jdbc:postgresql://db.example.com:5432/shifa` |
| `DB_USERNAME` | Database username | `shifa_user` |
| `DB_PASSWORD` | Database password | `secure_password_123` |
| `JWT_SECRET` | Secret key for JWT tokens (min 64 chars) | `openssl rand -base64 64` |
| `PUBLIC_BASE_URL` | Public API URL for CORS | `https://api.yourdomain.com` |
| `OPENAI_API_KEY` | OpenAI API key | `sk-...` |
| `OPENAI_PROJECT_ID` | OpenAI project ID | `proj_...` |

### Optional

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8080` | Backend server port |
| `STORAGE_ROOT` | `./public-storage/images` | File storage path |
| `JWT_ACCESS_TOKEN_MINUTES` | `60` | JWT token expiration |
| `LOG_FILE_PATH` | `./logs/shifa-backend.log` | Log file location |

## Security Notes

1. **Never commit `.env` files** to version control
2. **Use strong JWT secrets** - generate with: `openssl rand -base64 64`
3. **Use secure database passwords** - minimum 16 characters
4. **Enable HTTPS** in production
5. **Set proper CORS origins** in production

## Testing Configuration

### Verify Flutter Config
```dart
// In your app, you can check:
print(AppConfig.description);
// Output: "Environment: production | API: https://api.yourdomain.com"
```

### Verify Backend Config
```bash
# Check which profile is active
curl http://localhost:8080/actuator/env  # If actuator is enabled

# Or check logs on startup
# Should see: "The following profiles are active: prod"
```

## Next Steps

After Phase 1 is complete:
1. ✅ Environment configuration system
2. ✅ Secure secret management
3. ⏭️ Phase 2: Database setup and migration
4. ⏭️ Phase 3: Backend deployment
5. ⏭️ Phase 4: Frontend deployment
