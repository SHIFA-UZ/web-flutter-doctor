- **[P1] Forced logout reuses a completed login and navigates back to Shell.** [LoginScreen.kt:62](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/login/LoginScreen.kt:62) obtains its ViewModel from the app-wide store. Leaving Login never clears it, so `signedIn` and the plaintext password survive. After session invalidation, reopening Login reruns line 67 with the old session and immediately returns to Shell; the new logout-reason banner is never initialized. Scope/reset the login ViewModel across authenticated sessions.

- **[P2] Password validation accepts fewer than six non-padding characters.** [LoginViewModel.kt:64](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/login/LoginViewModel.kt:64) checks raw length, so `"     a"` passes and triggers a request. Flow §9 requires six characters **after trimming for validation**; the request must still send the original password verbatim.

- **[P2] Android system Back exits instead of returning to Login.** [App.kt:94](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/App.kt:94) implements secondary auth destinations using screen state and an on-screen back callback, but registers no system-back handler. Opening Forgot Password, Email Sign In, or Create Account and pressing system Back therefore exits the activity, contrary to flow §7.

- **[P2] Forgot Password remains exposed as enabled while loading.** [LoginScreen.kt:144](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/login/LoginScreen.kt:144) only guards the callback. `ShifaLink` still renders an enabled clickable button, so assistive technology announces an available action that silently does nothing. Pass a real disabled state through the shared link component.

- **[P2] The Content split retains password-visibility state internally.** [LoginScreen.kt:93](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/login/LoginScreen.kt:93) owns `visible` with `remember`, violating the required stateless `XxxScreenContent` contract. Visibility cannot be supplied by previews/tests and resets when Content leaves composition. Hoist visibility and its toggle callback into the screen state/owner.

- **[P2] Error banners are vertically centered with the form rather than placed under the app bar.** [AuthFrame.kt:116](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/auth/AuthFrame.kt:116) applies centered arrangement to both `banner` and content. On taller screens, substantial blank space appears above the banner. Flow §3 and DaAuth’s shared frame require a separate banner directly below the bar, with only the form centered.
---
**Answers (all fixed):**
1. *Stale login ViewModel:* each top-level screen (Splash, SignIn) gets its own `ScopedViewModels` store, cleared when the screen leaves, so a finished sign-in and the typed password are gone after a forced logout, and the new reason banner is read fresh.
2. *Six characters after trim:* validated on `password.trim()`; the raw password is still sent. Covered by a test.
3. *System Back:* `BackHandler` on the secondary auth screens and the debug menu returns to Sign In.
4. *Disabled link:* `ShifaLink` got `enabled` (greyed out, not clickable); the forgot-password link uses it while signing in.
5. *Stateless Content:* password visibility lives in `LoginUiState` with a `togglePasswordVisible` callback.
6. *Banner placement:* `AuthFrame` puts the banner directly under the bar, outside the centred form.
Also, found while planning AUTH-03: login now sends `?app=doctor`, and a 403 shows the new `notDoctorAccount` text. These are small and covered by tests, so no second round.

**Correction (2026-11-04, AUTH-03):** answer 3 was wrong when committed: the `BackHandler` edit did not apply in `6a4b9fe` (only the import landed, and ktlint then removed it). The handlers are in the AUTH-03 commit.
