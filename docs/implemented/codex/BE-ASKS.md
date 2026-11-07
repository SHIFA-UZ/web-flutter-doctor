- **[P2] Phone-only existing patients cannot complete registration.** [AuthModels.kt:93](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/data/model/AuthModels.kt:93) adds `emailOtp` but omits `currentPassword`. When the backend finds a patient by phone whose account has no email, a code sent to the newly entered email cannot prove ownership; the backend requires their current password. The app cannot submit it, leaving this registration path permanently rejected with `OWNERSHIP_REQUIRED`.

- **[P2] Registration failures are hidden behind the code sheet.** [RegisterViewModel.kt:576](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/register/RegisterViewModel.kt:576) keeps `code` open but writes general failures to the parent `banner`. The sheet renders only `code.banner`. A timeout, rate limit, ownership rejection, or server error therefore stops the spinner without displaying the explanation inside the sheet. Put the failure in the sheet or close it before displaying the parent banner.

- **[P2] Closing during resend allows duplicate code requests.** [RegisterViewModel.kt:421](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-flutter-doctor-app2/composeApp/src/commonMain/kotlin/shifa/doctor/ui/register/RegisterViewModel.kt:421) checks only `loading`, while resend sets only `code.busy` (line 426). Start a resend, close the sheet, then tap Create account before the response arrives: the cooldown has not started yet, so `sendCode()` sends another OTP. Guard dismissal and submission against the in-flight resend.

---

Answers:
- P2 phone-only existing account: fixed. `RegisterRequest` carries `currentPassword`; when `register` with a valid code
  still answers `OWNERSHIP_REQUIRED`, the sheet asks that account's current password under the code and sends both.
- P2 failures hidden behind the sheet: fixed. While the sheet is open a refusal shows in the sheet's banner (with its
  request id); the page banner only when no sheet is open.
- P2 closing during a resend: fixed. The sheet can't be closed while a resend (or `register`) is in flight.
