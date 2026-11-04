> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 01 · Auth / Shell / Core — Flutter → KMP analysis

Scope: bootstrap, auth, registration, splash, main shell, app lock, inactivity, language, subscription gating, core utilities.
All paths are relative to `lib/` unless noted. "UNVERIFIED" = inferred, not confirmed by reading code in scope.

---

## 1. Bootstrap sequence

### 1.1 `main()` → `runApp` (`main.dart:35-67`)
1. `WidgetsFlutterBinding.ensureInitialized()` (36).
2. **Web only**: `BrowserCompat.bootstrap()` (37-39) — keeps a CSS `--vh` var in sync with `visualViewport` (Safari toolbar) via `dart:html` (`core/layout/browser_compat_web.dart:15-30`). Non-web stub is a no-op (`browser_compat_stub.dart`).
3. `tz.initializeTimeZones()` (40) — full IANA db from `timezone` package.
4. `initializeDateFormatting('en'|'uz'|'ru')` in parallel (42-46).
5. `Firebase.initializeApp(DefaultFirebaseOptions.currentPlatform)` → `FirebaseMessaging.onBackgroundMessage(...)` → `PushNotificationService().initialize()` (47-61). `UnsupportedError` is swallowed silently (54-56): `firebase_options.dart` is web-only, so **on Android/iOS Firebase never initialises** in this codebase as committed (CLAUDE.md confirms). Everything gated on `Firebase.apps.isNotEmpty` in `app.dart` is therefore web-only today.
6. `runApp(ProviderScope(child: ShifaDoctorApp()))` (62-66).

### 1.2 `ShifaDoctorApp` (`app/app.dart:146-493`)
Widget tree (458-491): `AppLockLifecycleLayer` → `ActivityTracker` → `ScrollConfiguration(ShifaScrollBehavior)` → `MaterialApp`.

`MaterialApp` config (462-488):
- `navigatorKey` = global `navigatorKey` (34) used by FCM tap handling, language toggle, logout redirect.
- `locale: languageState.locale`; `localeResolutionCallback` **always returns the app locale** regardless of device (470-472) because Material has no bundled `uz`.
- `supportedLocales`: `en`, `uz`, `uz_Cyrl`, `ru` (473-478).
- delegates: `AppLocalizations.delegate` + Global Material/Widgets/Cupertino (479-484).
- `initialRoute`: `isAdminHost ? /admin/login : /` (487).

Side effects in `build()` (every rebuild, guarded by flags):
- `ref.listen(authProvider)` (187-203): `authenticated → unauthenticated` ⇒ `pushNamedAndRemoveUntil(adminLogin|login)` on next frame; `→ authenticated` (doctor host) ⇒ `_syncDoctorUiLanguage`.
- `ref.listen(languageProvider)` (205-210): refresh push-notification localisation cache; sync language to backend if authenticated.
- First authenticated build with `_syncedDoctorLanguage == null` ⇒ sync (212-221).
- FCM token upload once per auth session when `Firebase.apps.isNotEmpty` (225-262): `PUT /api/doctors/me/fcm-token {fcmToken}` on token ready and for an already-existing token; reset flag on logout (260-261).
- Push tap / foreground handlers registered once (265-446) — deep links documented in the notifications area; relevant here: they call `shellProvider.setTab(...)`, `navigatorKey.pushNamedAndRemoveUntil(AppRoutes.shell)`, and `ShellScope.pushIntoShell(...)`.
- Web tab title `"(N) SHIFA DOCTOR"` from `unreadCountProvider` (451-456) via `setWebTitle` (`core/util/set_web_title_web.dart` uses `dart:html`; stub no-op).

### 1.3 Router (`app/router.dart`)
- `AppRoutes` constants (35-63). Auth-area routes: `/` splash, `/verify`, `/login`, `/create`, `/create/receptionist`, `/create/account-info`, `/create/schedule`, `/app` shell, `/admin/login`, `/admin/forgot-password`, `/admin`.
- `onGenerateRoute` (68-229): plain `MaterialPageRoute` switch; unknown → `SplashScreen` (226-228).
- `shellOnGenerateRoute` (234-345): nested-navigator routes (returns `null` for unknown so the shell shows tab content).
- Note: many auth screens are pushed with raw `MaterialPageRoute` and never via a route name (login → email input, OTP, reset password, verify key).

### 1.4 Splash (`features/auth/presentation/splash_screen.dart`)
1. 1600 ms `AnimationController`, `easeInOutCubic` (26-30); two blurred "blob" transforms + SVG logo fade (56-121).
2. On `completed` + 200 ms delay (31-33) ⇒ `authProvider.notifier.restoreSession()` (36).
3. `pushReplacementNamed(restored ? /app : /login)` (38-41).
No timeout/offline handling; `restoreSession` is purely local so this cannot hang.

### 1.5 Platform branches summary
| Concern | Web | Native mobile |
|---|---|---|
| Admin detection | `Uri.base.host.contains('admin')` (`core/util/admin_host.dart:6-9`) | always `false` |
| Token storage | `SharedPreferences` (`core/utils/secure_token_storage.dart:19-23`) | `flutter_secure_storage` |
| Phone OTP | `signInWithPhoneNumber` + invisible reCAPTCHA, `appVerificationDisabledForTesting` in debug (`features/auth/data/phone_auth_repository.dart:35-53`) | `verifyPhoneNumber` 60 s timeout (55-71) |
| Browser timezone | `Intl.DateTimeFormat().resolvedOptions().timeZone` via `dart:js_util` (`core/localization/locale_detection_web.dart`) | stub `null` |
| App lock | `AppLockSettingsSection` hidden (`features/settings/presentation/app_lock_settings_section.dart:117`), lifecycle observer not registered (`core/services/app_lock_provider.dart:11-14`) | enabled |
| Inactivity timeout | 1 h (`core/services/inactivity_timer_service.dart:15`) | 30 days (16) |
| Clinic staff | full shell | `ClinicStaffWebOnlyScreen` blocks (`features/auth/presentation/clinic_staff_web_only_screen.dart:14-17`) |
| Patient briefing panel | shown | hidden (`main_shell.dart:720-729`) |
| Shell layout | sidebar ≥768 px (compact <1100), bottom nav for phones (`core/layout/responsive.dart:59-62`) | always bottom nav |

---

## 2. Auth flows

Shared pieces:
- `AuthController` (`state/auth/auth_controller.dart`), `authTokenProvider` (15), `AuthState` (17-23).
- All screens: `Scaffold(backgroundColor: AppColors.cardboard)`, transparent AppBar with back arrow, `LanguageMiniToggle` in actions, `ConstrainedBox(maxWidth: 520)`, `ShifaPrimaryButton(width: fill, isLoading)`, errors via `SnackBar(e.toString().replaceFirst('Exception: ', ''))`.
- Success path for every login variant: `api.setToken`, `authTokenProvider = token`, `state = AuthState(isAuthenticated: true, userId: 'doctor'|'admin', email: …)`, persist token, `ref.invalidate(profileAllProvider, meProfileProvider)`.

### 2.1 Login screen (username/password) — `features/auth/presentation/login_screen.dart`
Route `/login`. Entry point after splash. **Phone OTP is NOT reachable from here** (see 2.3).
1. `initState` → `languageProvider.applyRegionalDefaultIfUnset()` (41-43).
2. Inputs: `emailOrPhone` (required, non-empty, 151-153), `password` (≥6 chars, 173-175), obscure toggle.
3. Enter submits via `submitOnEnter` (160).
4. `_onSignIn` (53-91): `authProvider.login(user, pass)` → invalidate profile → if JWT role ≠ CLINIC_STAFF, `registrationProvider.applyOptionalProfileExtrasAfterLogin()` (non-blocking) → `pushReplacementNamed(/app)`.
5. Links: "Forgot password" → `EmailInputScreen(forForgotPassword: true)` (187-191); "Sign in with email" → `EmailInputScreen(false)` (212-218); "Create account" → `VerifyKeyScreen` (227-233). All raw `MaterialPageRoute`.
6. `login()` (`auth_controller.dart:40-136`): `POST /api/auth/login[?app=admin] {username,password}`; content-type check with HTML-page detection (52-77); 200 ⇒ parse `token`; non-200 ⇒ `Exception('Login failed (code): message|error|body')`; network errors rewrapped (129-133). After doctor login: `_applyRegionalLanguageDefault()` (102-104).

### 2.2 Email OTP login — `email_login/email_input_screen.dart`, `email_otp_screen.dart`
1. `EmailInputScreen`: email validated `contains('@') && contains('.')` (107-114), lower-cased (34). `sendLoginOtp(email)` → `POST /api/auth/send-login-otp {email}`; non-200 ⇒ `message` or "Failed to send verification code" (`auth_controller.dart:228-235`).
2. Push `EmailOtpScreen(email)`: 6-digit numeric field (`maxLength: 6`, digits stripped, 58-64), **resend cooldown 60 s** (`_startResendCooldown` 38-48, 1 s periodic timer), no expiry timer.
3. Verify: `loginWithEmailOtp(email, code)` → `POST /api/auth/verify-email-otp {email, code}` → token (`auth_controller.dart:238-258`) → `pushReplacementNamed(/app)`.
4. Resend calls `sendLoginOtp` again, restarts 60 s cooldown (95-117). No client-side rate limit for email.

### 2.3 Phone OTP (Firebase) login — `phone_login/phone_input_screen.dart`, `otp_screen.dart`
**Dead on the current nav graph**: `PhoneInputScreen(` is never constructed anywhere (grep), and `runRegistrationPhoneVerification` / `RegistrationOtpDialog` are never called. Documented for completeness; the rewrite should decide whether to keep it.
1. Country code dropdown (default `+998`, 19 codes, 21-41); phone digits ≥7 (212-215); full E.164 length ≥10 (81).
2. Client rate limit `OtpRateLimitService`: **max 3 requests / 10 min per phone**, timestamps in `SharedPreferences` key `otp_rate_<digits>` (`features/auth/data/otp_rate_limit_service.dart:7-9,39`).
3. `PhoneAuthRepository.verifyPhoneNumber` (web: `signInWithPhoneNumber`; mobile: `verifyPhoneNumber`, 60 s timeout) → push `OtpScreen`.
4. `OtpScreen`: 6 digits, 60 s resend cooldown (48-58); resend blocked on web ConfirmationResult (124-129) and by rate limit.
5. Verify: `signInWithPhoneCredential` → `getIdToken(true)` → `loginWithFirebaseToken(idToken, phoneCountryCode)` → `POST /api/auth/verify` with `Authorization: Bearer <firebaseIdToken>` (`postWithBearer`, `auth_controller.dart:194-225`). 403 ⇒ message thrown; OTP screen signs out of Firebase if message contains "Access restricted"/"blocked" (112-115).
6. `forForgotPassword` variant → `ResetPasswordScreen(idToken)` → `resetPasswordWithFirebaseToken` → `POST /api/auth/forgot-password-reset {idToken, app, newPassword}` (`auth_controller.dart:312-337`). Password rule here: ≥8, 1 upper, 1 digit (`reset_password_screen.dart:34-40`) — weaker than `PasswordValidation`.
7. Firebase user session is **never signed out on app logout** (`logout()` does not touch FirebaseAuth).

### 2.4 Password reset via email OTP — `email_input_screen.dart(forForgotPassword)`, `email_otp_screen.dart`, `email_reset_password_screen.dart`
1. `sendForgotPasswordOtp(identifier)` → `POST /api/auth/send-forgot-password-otp {identifier, app: 'doctor'|'admin'}` (`auth_controller.dart:261-272`).
2. OTP screen does **not** verify the code; it just forwards it (`email_otp_screen.dart:68-80`).
3. `EmailResetPasswordScreen`: new + confirm password (≥8, upper, digit; 39-45), mismatch snack.
4. `resetPasswordWithEmailOtp(email, code, newPassword)` → `POST /api/auth/forgot-password-reset {email, emailOtp, app, newPassword}` → returns a **token and logs the user in** (`auth_controller.dart:275-309`); on admin host a non-ADMIN role is rejected (288-294). Then `pushReplacementNamed(/app)`.

### 2.5 Verify invitation key — `verify_key_screen.dart`
Route `/verify` (also pushed raw from login).
1. Single text field, empty ⇒ snack `pleaseEnterOneTimeKey` (26-30).
2. `registrationProvider.verifyKey(key)` → `POST /api/auth/verify-key {key}`; needs 200 and `valid == true` else "Invalid or already used key" (`state/auth/registration_state.dart:114-124`).
3. Stores `key`, `invitePurpose`, `inviteClinicId`, `inviteClinicName`, `inviteTargetEmail` (`emailSentTo`); for purpose `CLINIC_RECEPTIONIST_INVITE` the email is pre-filled and locked (126-144). Key persisted to `SharedPreferences['shifa_verified_invite_key']` (145-150).
4. `pushReplacementNamed(purpose == CLINIC_RECEPTIONIST_INVITE ? /create/receptionist : /create)` (38-43).

### 2.6 Create account (doctor) — `create_account/create_account_screen.dart` → `account_information_screen.dart`
Step 1 `/create` (dots 1/3):
1. Guard: no key ⇒ snack + push `/verify` (80-84).
2. Fields: first/last name required (217-229), email (`@` and `.`), phone optional but if present ≥10 digits (231-239), password via `PasswordValidation.validate` (**min 8, max 128, upper, lower, digit, special** `core/utils/password_validation.dart:31-40`) with live checklist (563-590), confirm password.
3. `checkExistingPatient` → `POST /api/auth/check-existing-patient {firstName,lastName,email,phone?}` → `{found, fullName, photoUrl, email}` (`registration_state.dart:177-200`).
4. If `found`: switch to "existing patient" confirm view (261-420): avatar, info banner, detected timezone (`getDetectedTimeZone()` via `iana_time_zone`, fallback `UTC`), password + confirm; `_confirmExistingPatientRegistration` (152-215) → email OTP dialog → `submitRegistration()` → `setSessionFromToken(token)` or fallback `login(username, password)` → `applyOptionalProfileExtrasAfterLogin()` → `pushNamedAndRemoveUntil(/app)`.
5. Else `setBasicInfo` and `pushNamed(/create/account-info)`.

Step 2 `/create/account-info` (dots 2/3):
1. Optional: DOB (date picker, locale via `localeForMaterialIntl`, 63-73), gender `Male|Female|Other`, address, clinic (`SearchableClinicDropdown` → `GET /api/public/clinics?search=`), professions (multi, joined with `", "`), practice timezone (auto-detected, editable).
2. `_submit` (75-135): `setAccountInfo(...)`; email required; `runRegistrationEmailVerification` → `POST /api/auth/send-email-otp {email, purpose: 'REGISTRATION'}` then a dialog that **only collects the code and does not verify it** (`registration_email_verification.dart:86-106`; comment says verification happens on register — but `submitRegistration` never sends `emailOtp`). 60 s resend cooldown.
3. `submitRegistration()` → `POST /api/auth/register {firstName,lastName,email,password,key,phone?,timeZone?}` → 200/201 with `token` (`registration_state.dart:278-326`); clears persisted invite key.
4. `setSessionFromToken(token)` (`auth_controller.dart:434-441`) or legacy fallback `login()`.
5. `applyOptionalProfileExtrasAfterLogin()` → `PATCH /api/doctors/me/profile {dob(YYYY-MM-DD),gender,address,clinicId|clinic,profession,timeZone}` (`registration_state.dart:371-411`), then clears extras.
6. `pushNamedAndRemoveUntil(/app)`. The third dot (`/create/schedule`, `ScheduleScreen`) is never reached from this flow (comment at line 1: "goes straight to Login (no schedule)").

### 2.7 Receptionist / clinic staff account — `receptionist_create_account_screen.dart`
Route `/create/receptionist`.
1. Email is read-only, taken from `inviteTargetEmail ?? email` (87-94, 137).
2. Fields: first/last name required, timezone (auto-detect → registration tz → `UTC`), password (`PasswordValidation`) + confirm.
3. `setClinicStaffSignup(...)` → `submitClinicStaffRegistration()` → `POST /api/auth/register-clinic-staff {same payload as register}` (`registration_state.dart:329-365`).
4. `setSessionFromToken(token)` if present (no fallback login!), `shellProvider.setTab(1)`, `pushNamedAndRemoveUntil(/app)`.

### 2.8 Clinic-staff native gate — `clinic_staff_web_only_screen.dart`
`MainShell.build` returns `ClinicStaffWebOnlyScreen` when `PlatformLayout.isNativeMobile && role == CLINIC_STAFF` (`main_shell.dart:307-309`). Screen: icon, title/body, `Sign out` → `logout()`.

### 2.9 Session restore — `auth_controller.dart:446-479`
1. `migrateFromSharedPreferencesIfNeeded()` once (mobile: copies legacy prefs token into secure storage, 32-36; `secure_token_storage.dart:56-71`).
2. Read token (`forAdmin` picks key). Empty ⇒ false.
3. `isJwtExpired(token)` (30 s grace, `core/util/jwt_utils.dart:39-43`) ⇒ clear token(s), false.
4. Admin: role claim must be `ADMIN` else clear and false (458-464).
5. Set token on client + provider, `AuthState(isAuthenticated, userId: 'doctor'|'admin', email: null)`, invalidate profile. No network call.

### 2.10 Logout — `auth_controller.dart:383-414`
1. Doctor host: fire-and-forget `PUT /api/doctors/me/fcm-token {fcmToken: ''}` (389-400).
2. `apiClient.clearToken()`, `authTokenProvider = null`, clear persisted token (`adminOnly` ⇒ admin key only; else both keys).
3. Invalidate `profileAllProvider`, `meProfileProvider`, `clinicWorkspaceKnownProvider=null`, `myClinicsProvider`, appointment providers, `patientsProvider`, `patientByIdProvider`, `patientsForAssignmentProvider`.
4. `state = unauthenticated` ⇒ `app.dart` listener navigates to login. Shell logout buttons **also** navigate to `/login` themselves and invalidate `shellProvider` (`main_shell.dart:1867-1892`, 1941-1972, 2008-2044, 2191-2223) — four copies of the same dialog.
5. Triggers: user action, inactivity timeout, JWT expiry (ActivityTracker/ApiClient), 401/403 rules, app-lock 5 failed PIN attempts, admin role mismatch.
Not cleared: `registrationProvider` state, `workspaceModeProvider`, app-lock PIN, language, OTP rate-limit history, FirebaseAuth user.

### 2.11 Admin login hand-off (admin area out of scope; summary only)
`initialRoute` = `/admin/login` when host contains "admin" (`app.dart:487`). `AdminLoginScreen` restores with `restoreSession(forAdmin: true)` and probes `GET /api/admin/dashboard/stats` (`features/admin/presentation/admin_login_screen.dart:39-61`); 2-step login `requestAdminLoginOtp` → `POST /api/auth/admin/request-login-otp {username,password}` → `emailHint`; `verifyAdminLoginOtp` → `POST /api/auth/admin/verify-login-otp {username,password,code}` → token (`auth_controller.dart:139-190`). Admin and doctor tokens are stored under separate keys so one browser can hold both. Doctor-scoped `ApiClient` throws `StateError` on `/api/auth/admin/*` (`api_client.dart:95-97`). **Not needed for the Android/iOS rewrite** (`isAdminHost` is always false off-web).

---

## 3. `ApiClient` (`core/api/api_client.dart`) — full behaviour

- Construction: `ApiClient(baseUrl, scope: doctor|admin)` (18); providers `doctorApiClientProvider`, `adminApiClientProvider`, `apiClientProvider` (host-switched) in `core/api/api_providers.dart:53-66`. Base URL `AppConfig.resolvedApiBaseUrl` (`core/config/app_config.dart:19-24`): `--dart-define API_BASE_URL` → else production Railway URL in release/`ENVIRONMENT=production` → else `http://localhost:8080`.
- Token: `setToken/clearToken/getAuthToken` (20-22); provider seeds from `authTokenProvider` and `ref.listen`s for updates (`api_providers.dart:30-47`). The unauthorized callback is `authProvider.logout(adminOnly: isAdminHost)` (24-27).
- Headers (`_headers`, 85-89 and `buildHeaders` 285-291): `Content-Type: application/json`, `Authorization: Bearer <jwt>` when token non-null. No Accept, no locale, no device headers.
- Path scope assertion (`_assertAllowedPath` 91-113): doctor scope throws on `/api/auth/admin/*` and `/api/admin/*`; admin scope throws on anything not `/api/admin/*` except `/api/auth/*`.
- **Pre-flight expiry** (`_ensureTokenStillValid` 31-37): if a token is set and `isJwtExpired` (exp + 30 s grace < now) ⇒ invoke unauthorized callback and return a synthetic `401 {"error":"Session expired"}` without hitting the network. Applied to `get/post/put/patch/delete` only (not `postWithBearer`, `postNoUnauthorizedCheck`, `postMultipart`).
- **401/403 rules** (`_checkUnauthorized` 41-60, `_shouldLogoutOn403` 65-83), quoted conditions:
  - no callback ⇒ ignore; status not 401/403 ⇒ ignore.
  - `if (_jwt == null || _jwt!.isEmpty)` ⇒ "no token sent, skipping logout" (avoids 403 loop).
  - `if (statusCode == 401 || _shouldLogoutOn403(response))` ⇒ logout.
  - `_shouldLogoutOn403`: `isJwtExpired(_jwt)` ⇒ true; body contains `"error":"Account is disabled"` | `"error":"Session invalid"` | `"error":"Session expired or signed out"` ⇒ true; body contains `"status":403` **and** `"error":"Forbidden"` **and** basePath is `/api/doctors/me` or starts with `/api/doctors/me/` **and** JWT role is non-null and ≠ `CLINIC_STAFF` ⇒ true; otherwise false ("clinic staff legitimately get 403 here").
- Timeouts: `defaultTimeout = 30 s` applied only to `get` (128-135) and `post` (181-188), throwing `Exception('Request timed out after 30s')`. `put/patch/delete/multipart/postWithBearer/postNoUnauthorizedCheck` have **no timeout**.
- `postWithBearer(path, body, bearer)` (142-151): custom bearer (Firebase ID token), no stored JWT, no 401 handling, no timeout.
- `postNoUnauthorizedCheck` (155-160): normal headers, no 401 handling (video tokens).
- `postMultipart` (257-282): `MultipartRequest` with `Authorization` if token, `extraHeaders`, `fields`, `files`; returns `StreamedResponse`; no 401 handling, no timeout, no expiry pre-check.
- `buildHeaders` for SSE/streaming (285-291): same as `_headers`; callers do raw `http` streaming (AI API, out of scope).
- Logging: `print` (not `debugPrint`) of every GET path with "Authorization header present/WARNING", every POST's full URL/status/content-type/body preview (<500 chars or non-JSON preview) and first 20 chars of the JWT on token set (`api_providers.dart:33,42`). Runs in release too. HIPAA concern — do not copy.
- Public (unauthenticated) calls bypass `ApiClient`: `GET /api/public/config` (`core/network/public_backend_config.dart`, 5 s timeout, static cache, poll-waits 20×80 ms while another fetch is running), `GET /api/public/clinics`, `GET /api/public/professions` (`core/services/clinic_service.dart`, `profession_service.dart`, 5 s timeout, fall back to `localhost:8080` if no ref — bug on device).

---

## 4. Storage inventory

| Key | Store | Written by | Read by | Cleared on logout? |
|---|---|---|---|---|
| `shifa_doctor_auth_token` | secure storage (Android prefs file `shifa_doctor_auth_secure`); **web: SharedPreferences** | `AuthController._saveTokenToStorage` after any login/register/reset | `restoreSession` | yes (`clearToken(adminOnly:false)` removes both) |
| `shifa_admin_auth_token` | same | admin login / admin reset | `restoreSession(forAdmin)` | yes (adminOnly or full) |
| legacy same keys in SharedPreferences (mobile) | SharedPreferences | pre-migration builds | `migrateFromSharedPreferencesIfNeeded` (removed after copy) | n/a |
| `app_lock_pin` | secure storage (file `shifa_doctor_app_lock_prefs`) | `AppLockService.setPin` | `verifyPin/hasPin` | **no** |
| `biometric_enabled` | secure | `setBiometricEnabled` | `isBiometricEnabled` | **no** (only when user disables lock) |
| `app_lock_enabled` | secure | `setLockEnabled` | `isLockEnabled` | **no** |
| `doctor_language` | SharedPreferences | `LanguageNotifier._loadLanguage/applyRegionalDefaultIfUnset/setLanguage` | `_loadLanguage` | no |
| `doctor_language_explicit` (bool) | SharedPreferences | `setLanguage` | `_loadLanguage`, `applyRegionalDefaultIfUnset` | no |
| `shifa_doctor_last_activity_ms` (int) | SharedPreferences | `InactivityTimerService._persistLastActivity` (throttled 5 min) | `enforceTimeoutIfNeeded` | yes (`stopTimer` on provider dispose when auth → false) |
| `shifa_verified_invite_key` | SharedPreferences | `RegistrationController.verifyKey` | `restoreKeyIfMissing` (never called in scope — UNVERIFIED elsewhere) | removed on successful registration only |
| `otp_rate_<phone digits>` (JSON list of ISO timestamps) | SharedPreferences | `OtpRateLimitService.recordRequest` | `canRequestOtp/secondsUntilNextAllowed` | no |
| `active_location_id:<doctorId>` (int) | SharedPreferences | profile/locations UI (out of scope) | `MainShell._loadActiveLocationLabel` (`main_shell.dart:69-74,164-165`) | no |
| In-memory `authTokenProvider` | Riverpod | all login paths | `ApiClient` providers, `profileAllProvider`, `doctorAppJwtRoleProvider`, `unreadCountProvider`, `practiceSessionProvider` | yes |
| In-memory `ApiClient._jwt` | object | provider listen | requests | yes |
| `registrationProvider` state (incl. plaintext password) | Riverpod | registration screens | login screen `applyOptionalProfileExtrasAfterLogin` | **no** — survives logout for the process lifetime |
| `shellProvider` (tab index) | Riverpod | shell, push handlers | shell | only by shell logout buttons (`ref.invalidate`), not by `AuthController.logout` |
| `workspaceModeProvider` | Riverpod | shell | shell | no |
| `PublicBackendConfig._cache` | static | first fetch | `transcriptionFeedbackEnabled` | no |
| `LocalizationAssetLoader._cache` | static | delegate `load` | `translate` | n/a |
| `BrowserCompat` cached flags | static | lazy | scroll behaviour | n/a |
| `_syncedDoctorLanguage` | `_ShifaDoctorAppState` | language sync | dedupe | reset on logout (`app.dart:189`) |
| FirebaseAuth current user | Firebase SDK | phone OTP sign-in | `getIdToken` | **no** (only registration dialog and "Access restricted" path call `signOut`) |

---

## 5. JWT claims, roles, subscription, workspace

### 5.1 JWT claims read
- `exp` (seconds) → `jwtExpirationFromToken`, `isJwtExpired(grace 30 s)` (`core/util/jwt_utils.dart:4-43`).
- `role` → `jwtRoleFromToken` (25-36), duplicated in `auth_controller.dart:489-500` (`_extractJwtRole`, upper-cased) and `state/auth/doctor_jwt_role_provider.dart:13-24`.
No other claims (no user id, no tier) are read from the token. `AuthState.userId` is the literal string `'doctor'`/`'admin'`.

### 5.2 Roles
| Role string | Where it matters |
|---|---|
| `DOCTOR` (any value ≠ CLINIC_STAFF maps to `DoctorAppJwtRole.doctor`, `doctor_jwt_role_provider.dart:27-32`) | full shell; `profileAllProvider` fetches `/api/doctors/me` |
| `CLINIC_STAFF` | `profileAllProvider` throws `StateError` (`state/profile/profile_providers.dart:17-19`); shell 4-tab mode; blocked on native (`ClinicStaffWebOnlyScreen`); login skips profile-extras patch; `ApiClient` never logs out on `/api/doctors/me*` 403; start-tab preference ignored; sidebar location label suppressed |
| `ADMIN` | required for `restoreSession(forAdmin)` and admin password reset (`auth_controller.dart:288,458`) |
| Patient (backend) | `/api/auth/verify` returns 403 for non-doctors; `check-existing-patient` lets a patient upgrade to doctor account |

### 5.3 Subscription tiers / feature flags (`core/subscription/doctor_subscription.dart`, `state/subscription/doctor_subscription_provider.dart`)
- Tiers `BASIC < PRO < PREMIUM` parsed from `profileAll.subscription['tier']` (fallback `profile['subscriptionTier']`); **missing/unknown/loading ⇒ PREMIUM** (`parseDoctorTier` default, provider `orElse`). Clinic staff therefore always resolve to PREMIUM (profile throws).
- Feature → min tier: BASIC: calendar, documents, chat, patientManagement, basicAnalytics; PRO: videoConsultation, aiNotes, speechToText, patientBriefing, askShifaAi; PREMIUM: remoteCareTasks, advancedAnalytics, differentialDiagnosis.
- Gates in this area: `remoteCareTasks` → Tasks tab visibility, start-tab resolution, bounce to Home (`main_shell.dart:347-364,597-598,840-849`, `_ShellTabContent` 2080-2087); `askShifaAi` → Intelligence mode (`_openIntelligenceMode` 283-292, shows generic `error` snack when blocked).
- Tier label helper `doctorTierLabel` (English only, not localised).

### 5.4 Workspace mode / practice session
- `workspaceModeProvider`: `clinic | intelligence`, not persisted (`state/workspace/workspace_mode_provider.dart`). Toggled by logo tap / AI card / More-sheet; `_goToTab` always resets to clinic; leaving intelligence invalidates appointment providers (`main_shell.dart:294-303`).
- `practiceSessionProvider`: `GET /api/practice/me` → `PracticeSession{principalRole, clinicIds[], colleagues[{doctorId, displayName}]}` (`state/practice/practice_session_provider.dart`). Consumers are outside this scope (UNVERIFIED; not referenced by shell/auth files).
- Clinic workspace presence: `hasClinicWorkspaceProvider` from `GET /api/me/clinics` with `clinicWorkspaceKnownProvider` memo (`state/clinic/clinic_providers.dart:13-47`) drives the Clinic/Finance nav items.

---

## 6. Main shell (`features/shell/presentation/main_shell.dart`, `shell_scope.dart`, `domain/*`)

### 6.1 Tabs (`IndexedStack` order = `DoctorShellTab`, `domain/doctor_shell_tab.dart`)
| idx | Screen | Doctor desktop sidebar (592-602) | Doctor mobile (2383-2416 + More sheet 802-917) | Clinic staff (587-590 / 2308-2345) |
|---|---|---|---|---|
| 0 | ChatScreen | **absent** | bottom item 4 "Chat" `chat_bubble_outline`, Badge unread count | sidebar/bottom idx 0 (badge) |
| 1 | HomeScreen | `home_outlined` "Home" | bottom 1 | — (idx 1 = ClinicWorkspaceScreen) |
| 2 | CalendarScreen | `calendar_today_outlined` label `navAppointments` | bottom 2 label `calendar` | — (idx 2 = Notifications, badge) |
| 3 | PatientsScreen | `people_outline` | bottom 3 | — (idx 3 = Profile avatar) |
| 4 | ClinicWorkspaceScreen | `medical_services_outlined` `clinicNavClinic` **and** a second item `account_balance_wallet_outlined` `navFinance` (same index) — both only if `hasClinicWorkspace` | More sheet | — |
| 5 | TasksScreen | `task_alt_outlined` + active-count pill, only `canUseTasks` | More sheet | — |
| 6 | ReportsScreen | `analytics_outlined` `navReports` | More sheet | — |
| 7 | NotificationsScreen | **absent** from doctor sidebar (`_buildDarkNotificationsNavItem` only used for staff) | More sheet | idx 2 |
| 8 | ProfileScreen | `settings_outlined` `navSettings` + profile card at bottom | More sheet "Profile" | idx 3 |

Mobile More sheet (doctor): Shifa Intelligence, current location chip, Clinic, Tasks, Reports, Notifications, Profile, Language (→ `LanguageSettingsScreen` pushed into shell navigator), Logout.

### 6.2 Layout
- `PlatformLayout.useMobileShell` (`core/layout/responsive.dart:59-62`): native Android/iOS ⇒ always bottom nav; web ⇒ width <768 and shortestSide <600.
- Sidebar width 260 (≥1100 px) / 72 compact icon rail (768-1100) / 0 (`responsive.dart:68-76`). Gradient `brand → brand@0.88`, white selected pill.
- Sidebar extras: logo (tap toggles Intelligence; disabled for staff), `_SidebarSearchButtonDark` → `HomeSearchOverlay.show`, `_SidebarAiCard`, `_SidebarDoctorProfile` ("Dr. med. First Last" via `formatDoctorMedName`), `LanguageMiniToggle`, `_LogoutButtonDark` with confirm dialog.
- Content: `SafeArea(top)` → `Stack[ IntelligenceScreen | ShellScope(Navigator(key: shellNavigatorKey)) , PatientBriefingPanel (web/desktop, doctor, not intelligence), LanguageMiniToggle top-right on mobile/compact except doctor Home tab ]` (683-744).
- Nested navigator: `'/'` ⇒ `_ShellTabContent(IndexedStack + _KeepAlive)`; others via `AppRouter.shellOnGenerateRoute`; unknown ⇒ tab content (695-718). `ShellScope.pushNamed/push/pop/canPop/pushIntoShell(retries 5 frames)` helpers (`shell_scope.dart`).

### 6.3 Badges & polling
- Chat unread: `unreadCountProvider` `StreamProvider` loop — 10 s between polls, 5 s when no token, yields 0 on error (`state/chat/chat_providers.dart:22-43`). Desktop sidebar (doctor) has no chat item, so the badge shows only on mobile and on the web tab title.
- Notifications unread: `doctorNotificationsUnreadCountProvider` is `FutureProvider.autoDispose` — **no polling**; invalidated on app resume (`main_shell.dart:262-263`) and on foreground push (`app.dart:277-278`).
- Tasks: count of `tasksProvider` items with `status == active` (1004); `loadTasks()` called once in `initState` (239).
- Sidebar location chip: `Timer.periodic(1 min)` → `_loadActiveLocationLabel` which calls `calendarProvider.loadDay(today)` **and** `fetchDoctorLocations` (`GET /api/doctors/me/locations`) every minute plus on tab switch to Home/Calendar and on resume (124-191, 241-246, 273-281).

### 6.4 Start tab & role clamps
- `shellProvider` default `1` (Home) (`state/shell/shell_controller.dart:5`).
- `_applyPreferredStartTabIfNeeded` (193-226): doctors only; only if current tab is still factory default and no pending task/conversation deep link; reads `profileAll.settings['defaultStartTab']`; `DoctorStartTab.resolveShellIndex` normalises key (`chat|home|calendar|patients|clinic|tasks|reports|notifications|profile`), gates tasks by tier (`domain/doctor_start_tab.dart:71-87`). Staff keys allowed: `chat|clinic|notifications|profile` (29-34) but the preference is never applied for staff (198).
- Build-time clamps: staff index outside 0-3 ⇒ 1; doctor on Tasks without tier ⇒ Home (351-364, 2075-2088).
- Pending deep links: `notificationPendingTaskIdProvider` ⇒ push `taskDetails` into shell nav and clear (314-343); `notificationPendingConversationIdProvider` consumed by ChatScreen (out of scope).

### 6.5 Back behaviour
- `_goToTab` pops the shell navigator to root then sets the tab (273-281).
- `appBarBackLeading/appPop/appCanPop` (`core/widgets/app_page_back_button.dart`) pop the shell navigator first, else the root navigator.
- **No `PopScope`/`WillPopScope` in `MainShell`**: Android system back on a tab root pops the root route (exits app or returns to login/splash route if any remained). UNVERIFIED on device.

### 6.6 Global overlays & lifecycle
- `AppLockLifecycleLayer` wraps the whole `MaterialApp` (see §7) — the lock screen replaces the entire app tree, including dialogs.
- `ActivityTracker` `Listener(onPointerDown/Up)` resets the inactivity timer; `didChangeAppLifecycleState(resumed)` enforces timeout/JWT expiry (`core/widgets/activity_tracker.dart`).
- `MainShell.didChangeAppLifecycleState(resumed)`: invalidate notifications, `myClinicsProvider`, appointment providers; reload location label (256-270).
- Intelligence mode replaces the nested navigator entirely (690-692); Ask-Shifa panel is the `IntelligenceScreen` (out of scope).

---

## 7. App lock + inactivity — exact rules

### 7.1 App lock (`core/services/app_lock_service.dart`, `app_lock_provider.dart`, `core/widgets/app_lock_layer.dart`, `features/settings/presentation/app_lock_settings_section.dart`)
- PIN: 4-6 digits (`isValidPin` 23-27), stored **in plaintext** in secure storage key `app_lock_pin`; `verifyPin` is string equality (56-60).
- `maxPinAttempts = 5` ⇒ `onForceLogin` ⇒ `authProvider.logout()` + unlock (`app_lock_layer.dart:46-48,189-193`). Attempt counter is in widget state (resets if the lock screen is rebuilt).
- Auto-verify when entered length ≥4 (`_onDigit` 51-60) — a 5/6-digit PIN can never be entered because verification fires at 4 digits and clears on mismatch. **Bug.**
- Biometric: `local_auth`, `biometricOnly: true, stickyAuth: true`, reason hardcoded `'Unlock Shifa Doctor'` (69-81); attempted automatically after first frame of lock screen (`initState` 68-71); requires `isBiometricAvailable && isBiometricEnabled`.
- When to lock:
  - Cold start: after first frame / on auth becoming true, if `isLockEnabled && hasPin` ⇒ locked (`app_lock_layer.dart:174-182`, listener 199-203, `_checkedColdStart` guard means it is checked once per process).
  - Resume: `LockManager` (observer registered only on native mobile) records `paused|detached` time; on `resumed`, if elapsed ≥ **5 s** and authenticated and not `appLockTemporaryDisableProvider` and lock enabled with PIN ⇒ locked (`app_lock_provider.dart:50-85`).
- `appLockTemporaryDisableProvider` lets other features suppress the lock (e.g. during pickers) — no consumer in scope (UNVERIFIED).
- Settings UI (native only): switch enables (prompts PIN + confirm), disables (requires current PIN; clears PIN + biometric flag); biometric switch shown when available; `defaultInactivitySeconds = 30` constant unused.
- Haptic `HapticFeedback.vibrate()` on wrong PIN; error key `incorrectPin`; keypad 3×4 with `⌫`.

### 7.2 Inactivity (`core/services/inactivity_timer_service.dart`, `core/widgets/activity_tracker.dart`)
- Timeout: web **1 h**, native **30 days** (15-19). Provider recreates the service when `authProvider` becomes authenticated and disposes it (cancelling the timer and clearing the persisted key) when it becomes unauthenticated (120-143).
- `resetTimer()`: updates in-memory last activity, persists to `shifa_doctor_last_activity_ms` at most every **5 min**, restarts a single `Timer(timeout, logout)` (44-56).
- `enforceTimeoutIfNeeded(jwt)`: if JWT expired ⇒ logout; else compare `now - last` (memory or disk) ≥ timeout ⇒ logout (59-72). Called on app resume.
- `ActivityTracker._resetTimer` also logs out immediately if the JWT is expired on any tap (45-49).
- JWT grace: 30 s past `exp` (`jwt_utils.dart:39`).

---

## 8. Localization mechanism

- Locales: `en`, `uz` (Latin), `uz_Cyrl`, `ru`. `isSupported` checks languageCode ∈ {en, uz, ru} (`core/localization/app_localizations.dart:7355-7357`).
- Sources: `assets/localization/{en,uz,ru}.json` loaded once per language via `LocalizationAssetLoader` (`localization_asset_loader.dart`) plus a 6 200-line embedded `_localizedValues` map (`app_localizations.dart:24-6276`). JSON key counts: en 1431, uz 1745, ru 1663 — English relies on the embedded map for ~300 keys.
- Resolution order (`translate`, 6278-6300): asset(lang) → embedded(lang) → embedded(en) **only if lang == en** → the key itself. For `uz_Cyrl`: resolve `uz` then `transliterateUzbekLatinToCyrillicUi` (`uzbek_latin_to_cyrillic.dart`), which preserves `{x}` and `{{x}}` placeholders and maps digraphs `sh/ch/ng/o'/g'`.
- Placeholder styles found in `en.json`: `{name}` style ≈50 (e.g. `otpSentToEmail`: "…sent to {email}"), `{{name}}` style 17 (e.g. `resendCodeIn`: "Resend code in {{time}}", `clinicPatientNumber`: "Patient #{{id}}"), `%s` 2 (`minutesAgo`, `hoursAgo`). Substitution is manual `replaceAll` in typed helpers (e.g. 6544-6550, 7060-7064); Russian plural forms are hand-coded (7037-7047, 7080-7096).
- ~1 000 typed getters (`String get x => translate('x')`) wrap keys (6500-7348).
- Locale detection (`locale_detection.dart`): Uzbekistan if phone code `+998/998`, device locale country `UZ` or language `uz`, or browser IANA tz `Asia/Tashkent` ⇒ default `uz`, else `en`.
- Persistence (`core/providers/language_provider.dart`): `doctor_language` tag (`en|uz|uz-Cyrl|ru`) + `doctor_language_explicit`. On cold start a non-explicit saved value is re-evaluated against region detection and may be overwritten (71-77). `applyRegionalDefaultIfUnset(phoneCountryCode)` is called from login/phone screens and after every login.
- Backend sync: `PUT /api/doctors/me/language {language: en|uz|ru}` (`uz-Cyrl` → `uz`) whenever authenticated and the code changes, deduped by `_syncedDoctorLanguage` (`app.dart:158-179`). Profile screen also shows `settings['language']` (out of scope).
- Material widgets: Flutter ships no `uz` Material localisation; `localeResolutionCallback` forces the app locale anyway — Material strings for `uz` fall back to English at runtime (UNVERIFIED which fallback Flutter picks; the comment at `app.dart:468-469` says the tree would otherwise stay English). `localeForMaterialIntl` strips the script for `showDatePicker`/`intl`.
- UI pickers: `LanguageMiniToggle` (EN/UZ/ЎЗ/RU popup anchored via root overlay), `LanguageSettingsScreen` (radio list).

---

## 9. Endpoints used in this area

| METHOD path | Request | Response parsed | Errors handled |
|---|---|---|---|
| `POST /api/auth/login[?app=admin]` | `{username,password}` | `token` | non-JSON/HTML page detection; non-200 → `message`/`error`/body; network rewrap |
| `POST /api/auth/verify` (Bearer = Firebase ID token) | `{}` | `token` | 403 → `message`/`error`; other → generic |
| `POST /api/auth/send-login-otp` | `{email}` | — | non-200 → `message` |
| `POST /api/auth/verify-email-otp` | `{email,code}` | `token` | non-200 → `message` |
| `POST /api/auth/send-forgot-password-otp` | `{identifier, app}` | — | non-200 → `message` |
| `POST /api/auth/forgot-password-reset` | `{email,emailOtp,app,newPassword}` or legacy `{idToken,app,newPassword}` | `token` | non-200 → `message`; admin role check |
| `POST /api/auth/verify-key` | `{key}` | `valid, purpose, clinicId, clinicName, emailSentTo` | non-200 → `message`; `valid==false` |
| `POST /api/auth/check-existing-patient` | `{firstName,lastName,email,phone?}` | `found, fullName, photoUrl, email` | non-200 → `{found:false}` |
| `POST /api/auth/send-email-otp` | `{email, purpose:'REGISTRATION'}` | — | non-200 → generic |
| `POST /api/auth/register` | `{firstName,lastName,email,password,key,phone?,timeZone?}` | `token` (200/201) | non-200 → `message` |
| `POST /api/auth/register-clinic-staff` | same | `token` | same |
| `POST /api/auth/admin/request-login-otp` | `{username,password}` | `emailHint` | connection-reset rewrap |
| `POST /api/auth/admin/verify-login-otp` | `{username,password,code}` | `token` | `message` |
| `PATCH /api/doctors/me/profile` | subset of `{dob,gender,address,clinicId|clinic,profession,timeZone}` | — | non-200 → `message` |
| `PUT /api/doctors/me/language` | `{language}` | — | logged only |
| `PUT /api/doctors/me/fcm-token` | `{fcmToken}` ('' on logout) | — | logged / ignored |
| `GET /api/doctors/me` | — | `{profile, contact, billing, settings, subscription?}` → `ProfileAll` | non-200 throws; staff never calls |
| `GET /api/me/profile` | — | `MeProfile` (firstName, lastName, photoUrl…) | non-200 throws |
| `GET /api/me/clinics` | — | list → `hasClinicWorkspace` | out of scope detail |
| `GET /api/practice/me` | — | `principalRole, clinicIds[], colleagues[]` | non-200 → null |
| `GET /api/doctors/me/locations` | — | `DoctorLocationDto[]` (id, label, isPrimary) | caught → null label |
| `GET /api/public/config` | — | `transcriptionFeedbackEnabled` | any → `{}` |
| `GET /api/public/clinics?search=` | — | `[{id,name}]` | any → `[]` |
| `GET /api/public/professions?lang=&search=` | — | `[{english,uzbek}]` | any → local fallback list |
| `GET /api/chat/unread-count` (via `getUnreadCountWithClient`, UNVERIFIED path) | — | int | → 0 |
| `GET /api/admin/dashboard/stats` | — | 200 probe | non-200 → admin logout |

---

## 10. Models (fields, nullability)

- `AuthState` (`auth_controller.dart:17-23`): `isAuthenticated: bool` (req), `userId: String?` (always `'doctor'`/`'admin'`), `email: String?`. `static unauthenticated`.
- `RegistrationData` (`registration_state.dart:11-101`): all nullable `String?` `key, firstName, lastName, phone, email, password, gender, address, clinic, profession, timeZone, invitePurpose, inviteClinicName, inviteTargetEmail`; `DateTime? dob`; `int? clinicId, inviteClinicId`; `copyWith` (cannot null-out — `applyOptionalProfileExtrasAfterLogin` passes `null` expecting a reset, which `copyWith` ignores → extras are **re-sent on every subsequent login**, see §13).
- `ProfessionModel` (`core/models/profession_model.dart`): `english`, `uzbek` (non-null); `getDisplayText(locale)` (uz → uzbek, uz_Cyrl → transliterated, else english); `matches(query)`. `ProfessionData.allProfessions` ≈150 static entries, `search`, `findByEnglish`.
- `ClinicOption` (`core/models/clinic_option_model.dart`): `id: int` (required, `(json['id'] as num).toInt()` — throws if missing), `name: String` (`?? ''`), `matches`.
- `PracticeSession` / `PracticeColleague` (`state/practice/practice_session_provider.dart`): `principalRole: String` (`''` default), `clinicIds: List<int>`, `colleagues: List<{doctorId:int, displayName:String}>`.
- `LanguageState(locale)`; `DoctorAppJwtRole {doctor, clinicStaff}`; `DoctorTier {basic, pro, premium}`; `DoctorFeature` (13 values); `WorkspaceMode {clinic, intelligence}`; `OtpRequestResult` sealed (`OtpRequestSuccess{verificationId?, webConfirmationResult?}`, `OtpRequestFailure{message}`); `PasswordRequirementResult{l10nKey, satisfied}`; `CountryCode{code,label}`; `ProfileAll(profile, contact, billing, settings, subscription)` maps of `dynamic`.

---

## 11. Shared widgets in scope (`core/widgets/`)

| Widget | Purpose | Props / variants |
|---|---|---|
| `ShifaPrimaryButton` (`shifa_button.dart:18-112`) | filled teal CTA | `label`, `onPressed?`, `icon?`, `width: hug|fill`, `variant: normal|destructive`, `isLoading` (16 px spinner, disables). Hover bg, radius 8, padding 10, text 16 bold |
| `ShifaSecondaryButton` (118-212) | outlined 2 px | same props |
| `ShifaActionButton` (221-390) | compact (text 12, padding 8, shrinkWrap) | + `actionStyle: primary|secondary` |
| `ShifaIconButton` (399-495) | 40×40 icon tile | `icon`, `onPressed?`, `type: primary|secondary|action`, `variant`, `size`, `iconSize`, `tooltip?` |
| `LanguageMiniToggle` | pill showing EN/UZ/ЎЗ/RU, `showMenu` anchored through root navigator overlay; sets `languageProvider` | none |
| `ActivityTracker` | pointer listener + resume hook for inactivity | `child` |
| `AppLockLifecycleLayer` / `AppLockScreen` / `_PinPad` | lock overlay, PIN pad, biometric try | `onUnlock`, `onForceLogin?` |
| `AppPageBackButton`, `appBarBackLeading`, `appPop`, `appCanPop` | back controls aware of the shell navigator | `label?`, `onPressed?` |
| `MultiplePhoneFields` | dynamic list of phone `TextField`s with add/remove; `values` getter via `GlobalKey<MultiplePhoneFieldsState>` | `initialPhones`, `labelText?` |
| `PersonAvatar` | `CircleAvatar` with initials fallback, `NetworkImage` (not cached) | `name`, `photoUrl?`, `radius=20` |
| `SafariSafeDialog` | transparent `Dialog` shell (Safari centering fix) | `child`, `insetPadding`, `alignment` |
| `showScrollableFormBottomSheet[WithFooter]`, `scrollableBottomSheetContent`, `showScrollableFormDialog` (`scrollable_sheet_dialog.dart`) | keyboard-safe sheets; dialog becomes `Dialog.fullscreen` on mobile shell | `maxHeightFactor=0.92`, `includeBottomNavClearance`, `actionsBuilder` |
| `submitOnEnter(action)` | returns `onSubmitted` callback or null while busy | — |
| `SearchableClinicDropdown` (`features/auth/.../searchable_clinic_dropdown.dart`) | `InputDecorator` + search dialog, local filter then remote `?search=` | `value: int?`, `onChanged(ClinicOption?)`, `hintText`, `labelText` |
| `ResponsiveRowColumn`, `ResponsiveToolbar` (`core/layout/responsive.dart`) | row on wide, column on narrow | `row/column`, `gap`, `useMobileShell?` |

---

## 12. Platform-specific / third-party → KMP picks

| Flutter package / API | Used for | KMP (Android + iOS) pick |
|---|---|---|
| `flutter_riverpod` providers | state | Kotlin `StateFlow` in ViewModels (androidx.lifecycle multiplatform) + Koin for DI |
| `http` + hand-rolled `ApiClient` | REST | Ktor client (OkHttp/Darwin engines) with `Auth` bearer plugin, `HttpTimeout` (30 s), `ContentNegotiation` (kotlinx.serialization), custom plugin for the 401/403 rules; `Logging` only in debug and redact `Authorization` |
| `flutter_secure_storage` | JWT, PIN, flags | `multiplatform-settings` with `EncryptedSharedPreferences`/Keychain backends (or androidx `DataStore` + Keychain wrapper); store a **PIN hash + salt**, not plaintext |
| `shared_preferences` | language, activity, rate limit | `multiplatform-settings` (plain) or DataStore multiplatform |
| `firebase_auth` phone OTP | Firebase phone login | Keep as backend contract only: on Android use Firebase Auth Android SDK via `expect/actual`; iOS FirebaseAuth via cinterop/`gitlive/firebase-kotlin-sdk` (`dev.gitlive:firebase-auth`). Since the flow is currently unreachable, consider dropping it and keeping email OTP + password only (confirm with product) |
| `firebase_messaging` | FCM token / push | `gitlive/firebase-kotlin-sdk` messaging or native SDKs per platform; iOS needs APNs ↔ FCM |
| `local_auth` | biometrics | Android `androidx.biometric.BiometricPrompt`, iOS `LAContext` via `expect/actual` (or `moko-biometry`) |
| `iana_time_zone`, `timezone` | detect/convert tz | `kotlinx-datetime` (`TimeZone.currentSystemDefault().id`, `TimeZone.of(id)`); no tz db bundling needed |
| `intl` + manual JSON maps | i18n | `moko-resources` or `compose-multiplatform-resources` with `uz`, `uz-Cyrl` folders; keep JSON → generate; implement one `format(args)` supporting `{x}`/`{{x}}` and migrate `%s` keys; port transliterator as a pure-Kotlin fn |
| `flutter_svg` logo | splash/login | Compose `painterResource` (convert SVG to vector drawable / use `compose-multiplatform-resources` svg support) |
| `dart:html`/`dart:js` (title, --vh, Intl tz, Safari detection) | web only | drop |
| `Uri.base.host` admin detection | web only | drop; admin panel stays web |
| `WidgetsBindingObserver` lifecycle | lock/inactivity | `LifecycleOwner` (androidx multiplatform lifecycle) / `UIApplication` notifications via `expect/actual` |
| `Timer`, `Future.delayed` polling | unread count | coroutine `flow { while(true) { emit(); delay(10.s) } }` scoped to foreground lifecycle |
| `SharedPreferences`-based OTP rate limit | — | same with `multiplatform-settings`; or rely on backend/Firebase limits |
| `NavigatorState` + `GlobalKey` nested navigator | shell nav | Compose Navigation (multiplatform, `androidx.navigation`) with a shell graph and tab-scoped back stacks; use `BackHandler` |
| `SnackBar` | errors | `SnackbarHostState` |

---

## 13. Quirks, bugs, dead code, tech debt (do NOT copy)

1. **App-lock PIN auto-verify at 4 digits** makes 5/6-digit PINs unusable (`app_lock_layer.dart:57-59`). PIN is stored plaintext (`app_lock_service.dart:51-54`).
2. **Registration extras never cleared**: `copyWith(dob: null, …)` is a no-op for nulls (`registration_state.dart:402-410`), so `PATCH /api/doctors/me/profile` is re-sent on every password login (`login_screen.dart:68-75`) for the process lifetime; plaintext password stays in memory.
3. **Email OTP for registration is never verified client- or server-side by this client**: dialog pops `true` without a call (`registration_email_verification.dart:95-99`); `register` payload has no `emailOtp`.
4. **Phone OTP login and phone registration verification are unreachable** (`PhoneInputScreen`, `runRegistrationPhoneVerification`, `RegistrationOtpDialog`, `ResetPasswordScreen(idToken)`), yet they pull in `firebase_auth`. Decide: delete or re-wire.
5. **Dead shell widgets**: `_buildLabeledNavItem`, `_buildLabeledChatNavItem`, `_buildLabeledNotificationsNavItem`, `_buildLabeledNavAvatarItem`, `_buildNavItem`, `_buildChatNavItem`, `_buildNotificationsNavItem`, `_buildNavAvatarItem`, `_SidebarSearchButton`, `_LogoutButtonLabeled`, `_LogoutButton` (`main_shell.dart:1197-1631, 1895-2060`) — each defined once, never called. Four copies of the logout confirm dialog.
6. **Doctor desktop sidebar has no Chat and no Notifications items** while the data/badges exist (592-602). Mobile has them. Likely unintended drift; confirm with product before porting.
7. **Duplicate "Clinic" and "Finance" sidebar items point to the same tab index 4** (596, 601).
8. **Sidebar location ticker** loads the calendar day and the locations list every 60 s for the whole session (241-246) — wasteful; compute next-change time instead.
9. **Logout navigation happens twice** (app-level listener + shell buttons) and `shellProvider` is only reset by the shell buttons, not by timeout/401 logouts — next login may land on a stale tab.
10. **`print` logging in release** including JWT prefix, full POST bodies and response previews (`api_client.dart:124-126,172-208`, `api_providers.dart:33,42`) — HIPAA/security issue.
11. **Timeouts inconsistent**: 30 s only on GET/POST; PUT/PATCH/DELETE/multipart unbounded.
12. **Subscription default is PREMIUM** when missing/loading/for clinic staff (`doctor_subscription.dart:64-75`, provider) — fails open; the rewrite should fail closed or wait for the profile.
13. **Three JWT decoders** (`jwt_utils.dart`, `auth_controller._extractJwtRole`, `doctor_jwt_role_provider.jwtRoleClaimFromAccessToken`).
14. **Public services fall back to `http://localhost:8080`** when called without a `ref` (`clinic_service.dart:20`, `profession_service.dart:25`) — guaranteed failure on device.
15. **`PublicBackendConfig`** busy-waits (20×80 ms) and caches failures as `{}` forever; `transcriptionFeedbackEnabled` is the only consumer.
16. **Password rules diverge**: `PasswordValidation` (8-128, upper/lower/digit/special) vs reset screens (8, upper, digit) vs login (≥6).
17. **Error strings not localised**: many `Exception('…')` English messages surface in snackbars (`auth_controller.dart`, `registration_state.dart`), `'Opening appointment...'` (`app.dart:110`), `'Unlock Shifa Doctor'`, Firebase domain/too-many-requests messages (`phone_input_screen.dart:121-125`), `doctorTierLabel`.
18. **Language auto-override on cold start**: a non-explicit saved language is silently replaced by region detection (`language_provider.dart:71-77`); `_userChoseLanguage` is checked three times in one method.
19. **`localeResolutionCallback` ignores device locale** and Material has no `uz` — verify what Material strings render for Uzbek.
20. **`FirebaseAuth` user persists after logout**; next phone login would silently reuse a stale session (`getIdToken(true)` would still work for the old user).
21. **`restoreSession` is local-only** — an account disabled server-side stays "logged in" until the first API call returns 403 with the magic body strings.
22. **`AuthState.userId` is a constant string**; nothing stores the real doctor id — the shell derives it from `profile['id'] ?? profile['doctorId']` (`main_shell.dart:70-73`).
23. **`SplashScreen` always plays 1.8 s** before checking the token; cold start could skip the animation when a token exists.
24. `AppLockService.defaultInactivitySeconds`, `OtpRateLimitService.secondsUntilNextAllowed`, `ShellScope.pushReplacementNamed`, `ProfessionData.findByEnglish`, `TextCleaner`, `formatEnumValue` — unused or near-unused helpers.
25. `TimezoneDebugScreen` is self-labelled temporary and unreferenced (`features/debug/timezone_debug_screen.dart:1`).
26. `ClinicService`/`ProfessionService` each define their own `TimeoutException` class shadowing `dart:async`.
27. `PersonAvatar` uses raw `NetworkImage` contrary to CLAUDE.md rule (use cached images).
28. `ActivityTracker` resets the timer on **every pointer up/down app-wide** including inside the lock screen; fine, but on web it writes to disk at most every 5 min — port the throttle.

---

## 14. Proposed tickets (ordered; S≈½–1 d, M≈2–3 d, L≈4–6 d)

### CORE-01 · Project skeleton, DI, config [M]
Route — · Spec refs §1.1, §3 (base URL), §12 · API: —
Build: KMP module layout (shared `core`, `feature:auth`, `feature:shell`), Koin, `AppConfig` with build-time `API_BASE_URL`/`ENVIRONMENT` (no localhost fallback in release), coroutine scopes, Compose Multiplatform app entry on Android/iOS, theme tokens (teal primary, radius 12, cardboard bg).
Done when: both apps launch to a placeholder screen with injected config; CI builds debug for both.
Depends: —
Fix: no `print` logging; release logging off by default.

### CORE-02 · Secure + plain key-value storage [S]
Route — · Spec refs §4 · API: —
Build: `SecureStore` (Keychain / EncryptedSharedPreferences) and `PrefsStore` (`multiplatform-settings`) with the exact keys from §4; typed accessors; `clearSession()` that removes `shifa_doctor_auth_token` (keep app-lock keys, language).
Done when: unit tests round-trip every key; logout clears exactly the keys listed as "yes" in §4.
Depends: CORE-01
Fix: no SharedPreferences→secure migration needed (fresh app); PIN stored as salted hash (see LOCK-01).

### CORE-03 · JWT utilities + session state [S]
Route — · Spec refs §3, §5.1, §2.9 · API: —
Build: single `Jwt.decode(token)` → `exp`, `role`; `isExpired(grace = 30 s)`; `SessionRepository` holding `StateFlow<Session?>` (`token`, `role: DOCTOR|CLINIC_STAFF`, `isAuthenticated`); `restoreSession()` = read → expiry check → emit; persists on set.
Done when: tests for expired/valid/malformed tokens and role mapping (`CLINIC_STAFF` vs anything else).
Depends: CORE-02
Fix: one decoder (not three); store nothing but the token.

### CORE-04 · Ktor `ApiClient` with auth rules [M]
Route — · Spec refs §3, §9 · API: all
Build: Ktor client; bearer injection from `SessionRepository`; 30 s timeout on **all** verbs; pre-flight expiry check → force logout; response interceptor implementing the exact 401/403 rules in §3 (no token ⇒ ignore; 401 ⇒ logout; 403 ⇒ logout only if token expired, or body contains the three magic `"error"` strings, or `/api/doctors/me*` + `"status":403`+`"error":"Forbidden"` + role ≠ CLINIC_STAFF); `postWithBearer`, `postNoUnauthorizedCheck`, multipart helper, `sseHeaders()`; JSON content-type validation with HTML-page detection; `sanitizeErrorMessage` port.
Done when: interceptor unit tests cover each branch; debug logging redacts `Authorization` and bodies.
Depends: CORE-03
Fix: drop admin scope assertions (no admin on mobile); error messages localised via keys, not English literals.

### LANG-01 · Localization engine + resources [L]
Route — · Spec refs §8 · API: —
Build: import the three JSON files (plus embedded-only EN keys exported via `scripts/export_localization_json.js`) into KMP string resources for `en`, `uz`, `uz-Cyrl`, `ru`; `Strings.get(key, args)` supporting `{x}` and `{{x}}`; convert the 2 `%s` keys; port `transliterateUzbekLatinToCyrillicUi` and generate `uz-Cyrl` at build time (or at runtime with cache); Russian plural helpers for patients/visits/files; `Locale` state with `backendLanguageCode` mapping (`uz-Cyrl` → `uz`).
Done when: snapshot test: every key in uz/ru/en resolves without falling back to the key; transliteration golden tests for `o'`, `g'`, `sh`, `ch`, `ng`, placeholders preserved.
Depends: CORE-01
Fix: fallback order asset→embedded collapses to one source; missing-key fallback to EN for all languages (today uz/ru show the raw key).

### LANG-02 · Language provider, detection, persistence, backend sync [S]
Route — · Spec refs §8 · API: `PUT /api/doctors/me/language`
Build: `LanguageRepository` with `doctor_language` + `doctor_language_explicit`; region default (`+998`, device region `UZ`/`uz` → `uz`, else `en`) applied only when not explicit; `applyRegionalDefaultIfUnset(phoneCountryCode)`; sync to backend on auth and on change (deduped, doctors only); `LanguageMiniToggle` (EN/UZ/ЎЗ/RU menu) and `LanguageSettingsScreen` (radio list).
Done when: changing language re-renders the whole app; sync is issued once per change; non-explicit value is not overwritten after an explicit choice.
Depends: LANG-01, CORE-04
Fix: drop the cold-start auto-override of a previously saved non-explicit language (or make it explicit on first save).

### CORE-05 · Shared UI kit [M]
Route — · Spec refs §11 · API: —
Build: `ShifaPrimaryButton/Secondary/Action/IconButton` (props and states per §11), `PersonAvatar` (Coil/Kamel cached), `AppPageBackButton`, `submitOnEnter` equivalent (ImeAction.Done), scrollable form sheet/dialog (fullscreen on phones), `MultiplePhoneFields`, password-requirements checklist widget, 6-digit OTP field, 60 s resend-cooldown composable.
Done when: component previews for enabled/disabled/loading/destructive; used by AUTH tickets.
Depends: CORE-01, LANG-01
Fix: none.

### AUTH-01 · Splash + session restore [S]
Route `/` · Spec refs §1.4, §2.9 · API: —
Build: branded splash (logo fade, ≤1.6 s, skippable), `restoreSession()`, navigate to shell or login; no network.
Done when: expired token → login; valid token → shell; cold start < 2 s.
Depends: CORE-03
Fix: skip the fixed 1.8 s delay when a valid token exists.

### AUTH-02 · Login screen (password) [S]
Route `/login` · Spec refs §2.1 · API: `POST /api/auth/login`
Build: email-or-phone + password (≥6), show/hide, Enter submits, loading state, links to email-OTP login, forgot password, create account; `applyRegionalDefaultIfUnset()` on open; after success navigate to shell (clear back stack).
Done when: error body `message/error` shown via snackbar; HTML/non-JSON response yields `somethingWentWrong`.
Depends: CORE-04, CORE-05, LANG-02
Fix: do not re-send registration extras on every login (see AUTH-07).

### AUTH-03 · Email OTP login [S]
Route `/login/email`, `/login/email/otp` · Spec refs §2.2 · API: `POST /api/auth/send-login-otp`, `POST /api/auth/verify-email-otp`
Build: email validation (`@` and `.`), lowercase; OTP screen with 6-digit field, 60 s resend cooldown, resend re-sends; success → shell.
Done when: cooldown persists across rotation; wrong code shows backend `message`.
Depends: AUTH-02
Fix: none.

### AUTH-04 · Forgot password (email OTP) [S]
Route `/forgot`, `/forgot/otp`, `/forgot/reset` · Spec refs §2.4 · API: `POST /api/auth/send-forgot-password-otp`, `POST /api/auth/forgot-password-reset`
Build: identifier entry (`app: 'doctor'`), OTP collection (no verify call), new/confirm password using the **full** `PasswordValidation` rules, success logs the user in with returned token and goes to shell.
Done when: mismatch and weak password errors shown inline; token stored.
Depends: AUTH-03
Fix: unify password rules (8-128, upper, lower, digit, special) instead of the weaker reset rule.

### AUTH-05 · Verify invitation key [S]
Route `/create/verify` · Spec refs §2.5 · API: `POST /api/auth/verify-key`
Build: key field, `valid` check, store `key/purpose/clinicId/clinicName/emailSentTo` in `RegistrationState`; route to doctor or receptionist form by `purpose == CLINIC_RECEPTIONIST_INVITE`.
Done when: invalid key → snackbar; valid → correct next screen with invite email locked.
Depends: AUTH-02
Fix: do not persist the key in prefs (in-memory flow state is enough); backend decides reuse.

### AUTH-06 · Create account step 1 + existing-patient confirm [M]
Route `/create` · Spec refs §2.6 · API: `POST /api/auth/check-existing-patient`, `POST /api/auth/send-email-otp`, `POST /api/auth/register`
Build: form (names, email, optional phone ≥10 digits, password with live checklist, confirm), `checkExistingPatient`; if found show confirm view (avatar, timezone auto-detect via `kotlinx-datetime`, password) → email OTP dialog → register → session → shell; else go to step 2.
Done when: both branches register and land in the shell; progress dots 1/2.
Depends: AUTH-05, CORE-05
Fix: send the collected `emailOtp` to the backend or remove the fake dialog — align with backend (`register` currently ignores it).

### AUTH-07 · Create account step 2 (account info) + post-login extras [M]
Route `/create/info` · Spec refs §2.6 · API: `GET /api/public/clinics`, `GET /api/public/professions`, `POST /api/auth/register`, `PATCH /api/doctors/me/profile`
Build: DOB picker, gender, address, searchable clinic dropdown (local filter + remote search), multi-select profession (backend list with local fallback), timezone dropdown (detected default), email OTP dialog, register, `setSessionFromToken`, then `PATCH` extras once and clear them.
Done when: extras are sent exactly once; empty optional fields omitted; progress dots 2/2.
Depends: AUTH-06
Fix: clear extras after success (Flutter `copyWith` bug); drop the dead "schedule" third step; never fall back to `localhost`.

### AUTH-08 · Receptionist (clinic staff) registration + native gate [S]
Route `/create/receptionist` · Spec refs §2.7, §2.8 · API: `POST /api/auth/register-clinic-staff`
Build: locked email header (clinic name + email), names, timezone, password + confirm; on success set session → shell; `ClinicStaffBlockedScreen` shown on mobile for `CLINIC_STAFF` tokens with Sign out.
Done when: staff token on phone sees the blocked screen immediately after login/restore.
Depends: AUTH-05, SHELL-01
Fix: product decision whether mobile staff v1 is still blocked; if yes the 4-tab staff shell (SHELL-02) can be deferred.

### AUTH-09 · Logout + session invalidation [S]
Route — · Spec refs §2.10 · API: `PUT /api/doctors/me/fcm-token`
Build: `logout()` clears token/secure key, clears FCM token best-effort, resets all session-scoped repositories (profile, clinics, patients, appointments, shell tab, registration state, workspace mode), single navigation to login from the session observer; confirm dialog composable shared by sidebar/sheet.
Done when: after logout no stale data is visible on next login; one navigation event.
Depends: CORE-04, SHELL-01
Fix: one logout dialog, one navigation path; also sign out Firebase if AUTH-10 is kept.

### AUTH-10 · (Optional) Firebase phone OTP login/reset [L]
Route `/login/phone`, `/login/phone/otp` · Spec refs §2.3 · API: `POST /api/auth/verify`, `POST /api/auth/forgot-password-reset {idToken}`
Build: country-code picker (default +998), Firebase phone verification (Android/iOS SDKs via expect/actual), 6-digit OTP, 60 s cooldown, client rate limit 3/10 min per phone, bearer login with Firebase ID token, "Access restricted" sign-out path.
Done when: real SMS login works on both platforms; Firebase user signed out on app logout.
Depends: AUTH-02, CORE-02
Fix: currently unreachable in Flutter — ship only if product confirms; otherwise delete.

### SHELL-01 · Shell scaffold, tabs, nested back stacks (doctor) [L]
Route `/app` · Spec refs §6.1-6.6 · API: `GET /api/doctors/me`, `GET /api/me/clinics`
Build: bottom nav Home/Calendar/Patients/Chat(badge)/More; More sheet (Intelligence entry, location chip, Clinic if workspace, Tasks if tier, Reports, Notifications(badge), Profile, Language, Logout); tab-scoped back stacks with `BackHandler` (back on non-home tab → Home, then exit); `_goToTab` semantics (pop to root, reset workspace mode); start tab from `settings.defaultStartTab` with tier gating and pending-deep-link suppression; role clamps; placeholders for feature screens.
Done when: tab state survives rotation; deep-link `setTab` + push into tab stack works; `defaultStartTab` honoured once per session.
Depends: CORE-04, CORE-05, LANG-02, AUTH-01
Fix: define system-back behaviour explicitly (missing in Flutter); no 60 s calendar/location polling — compute next slot boundary.

### SHELL-02 · Clinic-staff shell variant [S]
Route `/app` · Spec refs §6.1 · API: `GET /api/me/profile`
Build: 4 tabs Chat(badge)/Clinic/Notifications(badge)/Profile(avatar), index clamp, start on Clinic, `meProfileProvider` for name/avatar.
Done when: staff token renders the 4-tab shell on tablets/web-like widths (if mobile staff is allowed) else only the blocked screen.
Depends: SHELL-01, AUTH-08
Fix: skip if AUTH-08 keeps staff blocked on mobile.

### SHELL-03 · Badges & polling (unread chat, notifications, tasks) [S]
Route — · Spec refs §6.3 · API: unread-count endpoint, notifications unread count, tasks list
Build: foreground-scoped flow polling chat unread every 10 s (5 s backoff when unauthenticated, 0 on error); notifications unread refreshed on resume and on push; tasks active count badge.
Done when: polling stops in background; badges update within one interval.
Depends: SHELL-01
Fix: consider server push/SSE later; keep interval constants in one place.

### SHELL-04 · Subscription tier gating [S]
Route — · Spec refs §5.3 · API: `GET /api/doctors/me` (`subscription.tier`)
Build: `DoctorTier`, `DoctorFeature`, `tierAllows`, `FeatureGate` flow from profile; gate Tasks tab/start tab and Intelligence entry; tier badge label localised.
Done when: tier change after profile refresh re-evaluates gates without restart; unknown tier handled per product decision.
Depends: SHELL-01
Fix: fail closed (or "loading") instead of defaulting to PREMIUM.

### SHELL-05 · Workspace mode (Intelligence toggle) + session observers [S]
Route — · Spec refs §5.4, §6.6 · API: `GET /api/practice/me`
Build: `WorkspaceMode` state, logo/AI-card/sheet toggles, invalidate appointment data on leaving Intelligence; `PracticeSessionRepository`; app-resume refresh hooks (notifications, clinics, appointments).
Done when: switching modes keeps tab state; resume refresh fires once per resume.
Depends: SHELL-01, SHELL-04
Fix: none.

### LOCK-01 · App lock (PIN + biometrics) [M]
Route overlay · Spec refs §7.1 · API: —
Build: PIN 4-6 digits stored as salted hash (secure store), settings section (enable → set+confirm PIN; disable → verify PIN; biometric toggle when available); lock overlay above the whole app: cold start when enabled, resume after ≥5 s in background, auto biometric prompt (`BiometricPrompt` / `LAContext`), keypad with explicit submit for 5/6-digit PINs, 5 failed attempts ⇒ logout; temporary-disable hook for pickers.
Done when: 6-digit PIN works; attempt counter survives recomposition; lock shows over dialogs/sheets.
Depends: CORE-02, AUTH-09, SHELL-01
Fix: fixes the 4-digit auto-submit bug and plaintext PIN; localise the biometric reason.

### LOCK-02 · Inactivity timeout + activity tracking [S]
Route — · Spec refs §7.2 · API: —
Build: 30-day inactivity timeout on native (constant), last-activity persisted at most every 5 min, reset on touch events (pointer down/up) and app foreground, enforce on resume including JWT-expiry check (30 s grace) ⇒ logout.
Done when: unit test with fake clock: timeout and expiry paths log out; no disk write per tap.
Depends: CORE-03, AUTH-09
Fix: single source of truth for "expired token ⇒ logout" (shared with CORE-04 interceptor).

### CORE-06 · Push bootstrap (FCM token upload) [S]
Route — · Spec refs §1.2 · API: `PUT /api/doctors/me/fcm-token`
Build: on authenticated doctor session upload current token and on refresh; clear on logout; wire tap payload router to `SHELL-01` deep-link API (`setTab`, push into tab stack). Payload routing table itself belongs to the notifications area.
Done when: token appears on backend after login on both platforms; cleared after logout.
Depends: SHELL-01, AUTH-09
Fix: Firebase must be configured for Android/iOS (today only web options exist).
