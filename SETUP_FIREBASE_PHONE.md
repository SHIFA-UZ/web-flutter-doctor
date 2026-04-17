# Firebase & Phone OTP Setup (Doctor App)

## 1. Generate `firebase_options.dart` (one-time)

The app needs a generated `lib/firebase_options.dart` so Firebase (and Phone Auth) can connect to your project.

**Run in a terminal from the project root:**

```powershell
cd C:\shifa_doc_app_v1
dart pub global run flutterfire_cli:flutterfire configure --project=shifa-doctor-staging
```

- If asked **"reuse values in existing firebase.json?"** → press **y** and Enter.
- If asked **"Which platforms?"** → select **android, ios, web, windows** (space to toggle, Enter to confirm).
- If asked **"override existing firebase_options.dart?"** → press **y** and Enter.

This creates/overwrites `lib/firebase_options.dart` with your project’s API keys and project ID.

**If `flutterfire` is not found:** ensure Dart global bins are on PATH, or use:

```powershell
dart pub global activate flutterfire_cli
# Then add to PATH, e.g.: %LOCALAPPDATA%\Pub\Cache\bin
```

---

## 2. Enable Phone sign-in and Authorized domains (required for web)

1. Open [Firebase Console](https://console.firebase.google.com/) → select project **shifa-doctor-staging**.
2. Go to **Build → Authentication**.
3. Open the **Sign-in method** tab → click **Phone** → turn **Enable** ON → Save.
4. **Authorized domains (required for web sign-in):** Go to **Authentication → Settings → Authorized domains**. Add:
   - `localhost` (for local dev)
   - `shifa-doctor-staging.web.app` (for staging)
   - `shifa-doctor-staging.firebaseapp.com` (if you use this URL)
   If your domain is missing, phone sign-in will fail with "unauthorized domain" or a generic error.
5. (Optional) Under **Phone** → **Phone numbers for testing**: add a test number (e.g. +1 650-555-1234) and code (e.g. 123456) to test without real SMS.

---

## 3. Backend on Railway: `FIREBASE_SERVICE_ACCOUNT_JSON`

The backend must verify Firebase ID tokens. **On Railway** you set the **entire JSON** as an environment variable (no file upload):

1. In [Firebase Console](https://console.firebase.google.com/) → **shifa-doctor-staging** → gear icon → **Project settings**.
2. Open **Service accounts** → **Generate new private key** → save the JSON file.
3. In **Railway** → your backend service → **Variables**:
   - Add a variable: **Name:** `FIREBASE_SERVICE_ACCOUNT_JSON`
   - **Value:** Paste the **entire contents** of the JSON file (one line is fine; multiline is also OK). Do not set `GOOGLE_APPLICATION_CREDENTIALS` on Railway — the app uses this variable and creates a temp file at startup.
4. Redeploy the backend (or let Railway auto-redeploy). The backend will log: `Firebase: Using FIREBASE_SERVICE_ACCOUNT_JSON from environment (Railway)` when it works.

**Local only (optional):** For local runs you can instead set `GOOGLE_APPLICATION_CREDENTIALS` to the **full path** of the JSON file (e.g. `C:\path\to\your-project-firebase-adminsdk-xxxxx.json`). If both are set, `GOOGLE_APPLICATION_CREDENTIALS` takes precedence.

---

## 4. Lint/IDE (if Firebase packages show as missing)

1. In the Doctor App folder run: `flutter pub get`.
2. In the editor press **Ctrl+Shift+P** → run **"Dart: Restart Analysis Server"** (do not type this in the terminal).

**Web reCAPTCHA:** Phone sign-in on web uses **invisible reCAPTCHA** ([Firebase doc](https://firebase.google.com/docs/auth/web/phone-auth)): no container is passed to `RecaptchaVerifier`, so verification runs in the background; a modal is shown only if a challenge is needed. reCAPTCHA cannot be fully turned off. If you see "Error" or "captcha-check-failed", add your domain to **Authorized domains** (step 4 above).

**"Blocked due to unusual activity":** Firebase temporarily blocks devices that make too many phone-auth requests (e.g. during testing). Wait a while (e.g. 30–60 minutes) or try from another network/device. For development, use **Phone numbers for testing** (step 5) so no real SMS is sent and limits are less likely to trigger.

After this, Phone OTP login and Forgot Password (phone flow) should work end-to-end.
