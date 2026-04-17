# Build Scripts

## Available Scripts

### `build_android.bat` (Windows) — Android APK & AAB

Builds the Flutter Android app with your API base URL. **Use APK first to verify the app (and login) on a device, then build AAB for Google Play** to avoid long Play Store review cycles when something is wrong.

**Usage:**
```bash
.\scripts\build_android.bat API_BASE_URL [apk|aab] [GOOGLE_MAPS_API_KEY]
```

**Recommended workflow:**

1. **Test with APK** (install on a real device and confirm login works):
   ```bash
   .\scripts\build_android.bat https://your-app.up.railway.app apk
   ```
   - APK output: `build\app\outputs\flutter-apk\app-release.apk`
   - Install: `adb install build\app\outputs\flutter-apk\app-release.apk` or copy the file to your phone and open it.

2. **When APK works**, build AAB for Google Play:
   ```bash
   .\scripts\build_android.bat https://your-app.up.railway.app aab
   ```
   - AAB output: `build\app\outputs\bundle\release\app-release.aab`

**Notes:**
- `API_BASE_URL` must be your **Railway (or backend) base URL without `/api`** (e.g. `https://your-service.up.railway.app`). The app adds `/api` to paths automatically.
- If you see **"Application not found"** after building, the app was likely built without the correct `API_BASE_URL` (e.g. defaulting to localhost) or the URL is wrong. Always use this script and pass your real Railway URL.

---

### `build_staging.bat` (Windows)

Builds the Flutter web app for staging and deploys to Firebase.

**Usage:**
```bash
.\scripts\build_staging.bat API_BASE_URL [GOOGLE_MAPS_API_KEY]
```

**Examples:**
```bash
# Build and deploy with production backend
.\scripts\build_staging.bat https://shifa-doc-backend-mvp-production.up.railway.app

# With Google Maps API key (optional)
.\scripts\build_staging.bat https://shifa-doc-backend-mvp-production.up.railway.app YOUR_GOOGLE_MAPS_KEY
```

**What it does:**
1. Cleans previous build
2. Gets Flutter dependencies
3. Builds web release with staging config and given API base URL
4. Deploys to Firebase hosting (project: staging → `shifa-doctor-staging`)

**Note:** `API_BASE_URL` should NOT include `/api` — the app adds it automatically.

---

### `build_staging.sh` (Mac/Linux)

Builds the Flutter web app for staging (no auto-deploy). Run `firebase deploy --only hosting --project staging` manually after.
