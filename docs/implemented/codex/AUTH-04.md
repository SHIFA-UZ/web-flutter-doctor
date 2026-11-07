- **[P2] Password fields remain editable during submission.** [ResetPasswordScreen.kt:182](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/emailcode/ResetPasswordScreen.kt:182) never passes loading state to either field. The request captures the earlier password at line 118, so edits during a slow request can leave the doctor believing the displayed password was saved. Disable both fields while loading, as flow 05 §6 requires.

- **[P2] Checklist status is inaccessible to screen readers.** [PasswordChecklist.kt:73](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/auth/PasswordChecklist.kt:73) gives the check/cross icons null descriptions; satisfied rules change only icon, color, and weight. The live region exposes no met/unmet state to announce. Add localized status semantics to each rule.

- **[P2] Password-policy rejections are mislabeled as invalid codes.** [ResetPasswordScreen.kt:132](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/emailcode/ResetPasswordScreen.kt:132) maps every validation error to “Request a new code.” Backend `AuthController.kt:1225–1226` returns 400 for password-policy failures; invalid email codes return 401 at line 1245. Preserve the code guidance for 401 and show an appropriate localized validation/reset failure for 400.

- **[P2] Overlong passwords receive the opposite corrective instruction.** [ResetPasswordScreen.kt:102](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/ui/emailcode/ResetPasswordScreen.kt:102) maps `Rule.Length` unconditionally to “At least 8 characters” through `PasswordChecklist.kt:36`. Submitting 129 characters therefore shows that inline error despite exceeding the maximum. Select the maximum-length message when the password exceeds 128 characters.
---
**Answers (all fixed):**
1. *Fields editable while submitting:* both password fields are disabled while the reset request runs.
2. *Checklist status for screen readers:* each rule row merges its text with a localized state ("met" / "not met", new strings `requirementMet`, `requirementNotMet`).
3. *400 shown as a wrong code:* only a 401 (wrong or expired code, `AuthController`'s OTP check) says "Request a new code"; a 400 (the backend's password policy) shows "Could not reset the password…". Covered by a test.
4. *Over 128 characters:* `Rule.label(password)` gives "At most 128 characters" past the maximum, inline and in the checklist. Covered by a test.
These are small and covered by tests, so no second round.
