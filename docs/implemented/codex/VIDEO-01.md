- **[P1] iOS logs raw Daily errors in release builds.** [DailyVideoCall.swift:51](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/iosApp/iosApp/DailyVideoCall.swift:51) and line 113 call `NSLog` without a debug guard and interpolate the complete SDK error. This violates debug-only logging and provides no protection against sensitive SDK error details. Gate logging behind `DEBUG` and emit only an allowlisted error category.

- **[P1] Android logs unsanitized SDK messages.** [VideoCall.android.kt:84](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/androidMain/kotlin/shifa/doctor/utils/VideoCall.android.kt:84) and line 121 pass raw `message` / `it.msg` into `httpLogger`. Its debug guard does not sanitize content; room URLs, tokens or patient details embedded in errors would be written verbatim. Replace SDK text with fixed categories or safe error codes.

- **[P2] Payment refusals cannot reach the payment message.** [VideoRepository.kt:63](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/web-kmp-doctor-app/composeApp/src/commonMain/kotlin/shifa/doctor/data/video/VideoRepository.kt:63) accepts only `Forbidden` (403), but the backend’s [VideoController.kt:126](/Users/sheroziy.saidkhodjaev/Projects/Private/shifa/backend-kotlin-shifa/src/main/kotlin/com/shifa/appointments/api/VideoController.kt:126) returns **402 PAYMENT_REQUIRED** for an unpaid consultation. `ApiException.of` maps 402 to `ServerError`, so the mapper returns the generic call error even with `unpaid = true`. Handle 402 explicitly and test that actual response instead of simulating payment refusal as 403.

---
**Answers (round 1, all fixed and tested; small, so no second round):**
1. *iOS logged raw Daily errors in release:* fixed. A fixed "FAILED Daily join / call" line, inside `#if DEBUG`.
2. *Android logged the SDK's text:* fixed. A fixed line only (`httpLogger` already logs in debug builds only).
3. *402 never reached the payment message:* fixed. The backend's `VideoController` answers 402 PAYMENT_REQUIRED for an
   unpaid paid visit; that is now the payment message. A 403 is explained by the window. Tested with the real status.
The patient app's copy has the same logging; recorded for that repo (overnight report, open questions).
