# Running App with Production API

## Quick Start

```bash
flutter run -d chrome --dart-define=API_BASE_URL=https://shifa-doc-backend-mvp-production.up.railway.app
```

## What This Does

- Runs the Flutter web app in Chrome
- Connects to Railway production backend
- Hot reload enabled (can make changes while running)
- Debug logging enabled (check browser console)

## Environment Configuration

**API Base URL:** https://shifa-doc-backend-mvp-production.up.railway.app
**Environment:** Development mode (debug build)
**Target:** Chrome browser

## Testing Checklist

### 1. Login
- Navigate to the app (should open automatically)
- Login with your doctor credentials
- Check that authentication works with production backend

### 2. Check Timezone Consistency
- Open browser console (F12 → Console tab)
- Look for debug logs:
  ```
  === TODAY APPOINTMENTS PROVIDER DEBUG ===
  Doctor timezone from profile: <timezone>
  Converting appointment: startAt=..., timezone=<timezone>
  ```

- **Verify:** Home screen and Calendar show **identical** times
- **Expected:** Both should show times in doctor's practice timezone (not UTC)

### 3. Check Calendar
- Go to Calendar tab
- Should see: "CalendarScreen: Profile loaded with timezone <timezone>"
- Verify calendar slots appear (not empty)
- Verify times match home screen

### 4. Test Appointment Operations
- Book an appointment
- Check that calendar updates (doesn't go empty)
- Check that home screen updates
- Verify times are consistent

## Debug Logging

### What to Look For

**Good Output (✅):**
```
=== TODAY APPOINTMENTS PROVIDER DEBUG ===
Profile loaded: YES
Doctor timezone from profile: Europe/Berlin
Query date (doctor's today): 2024-03-15
Converting appointment: startAt=2024-03-15T08:00:00Z, timezone=Europe/Berlin
utcIsoToTimeOfDayInZone: input=2024-03-15T08:00:00Z, targetZone=Europe/Berlin
utcIsoToTimeOfDayInZone: output=9:0
Converted to: 9:0 - 10:0
```

**Bad Output (❌):**
```
Doctor timezone from profile: null
  → Timezone null/empty, returning UTC
```
→ Means profile doesn't have timezone set

**Bad Output (❌):**
```
Invalid timezone "Europe/Berlin", falling back to UTC
```
→ Means timezone package issue or invalid timezone string

## Stopping the App

Press `q` in the terminal where Flutter is running, or press `Ctrl+C`.

## Building for Production

If you want to build (not run) for production deployment:

```bash
flutter build web --release \
  --dart-define=API_BASE_URL=https://shifa-doc-backend-mvp-production.up.railway.app \
  --dart-define=ENVIRONMENT=production
```

Output will be in: `build/web/`

## Common Issues

### Issue: "Failed to load appointments"
**Cause:** Backend API not responding or CORS issue
**Check:**
- Is backend URL correct?
- Is backend running?
- Check Network tab in DevTools

### Issue: "Profile load failed"
**Cause:** Not logged in or JWT expired
**Solution:** Logout and login again

### Issue: Times still showing UTC
**Cause:** Profile doesn't have timezone set
**Solution:**
1. Go to Profile tab
2. Set your practice timezone
3. Refresh the app

### Issue: CORS errors
**Cause:** Backend not allowing requests from localhost
**Solution:** Backend needs CORS configuration for development

## Alternative: Different Environments

### Local Backend
```bash
flutter run -d chrome --dart-define=API_BASE_URL=http://localhost:8080
```

### Staging Backend (if you have one)
```bash
flutter run -d chrome --dart-define=API_BASE_URL=https://your-staging-api.com
```

## Hot Reload

While the app is running:
- Press `r` in terminal to hot reload
- Press `R` in terminal to hot restart
- Press `q` to quit

Changes to Dart code will hot reload automatically!

## Current Session

The app is currently running in background with:
- **API:** https://shifa-doc-backend-mvp-production.up.railway.app
- **Platform:** Chrome
- **Mode:** Debug
- **Task ID:** Check running tasks with `/tasks` command

Check the browser - it should have opened automatically!
