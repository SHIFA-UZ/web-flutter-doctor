# Localization Report — Shifa Doctor App

Generated for the Doctor Web App. The app uses a single in-code map in `lib/core/localization/app_localizations.dart` (`Map<String, Map<String, String>> _localizedValues`) for `en`, `uz`, and `ru`. There are no separate `en.json` / `uz.json` / `ru.json` files in the app source; exported JSON files are in `assets/localization/` for reference and possible future JSON-based loading.

---

## 1. Fixed hardcoded strings (this session)

**Translation status:** All items below are fully translated in **en**, **uz**, and **ru** in `app_localizations.dart`, and the UI uses them (via getters where added: `selectFormTemplate`, `otpResendHint`, `detecting`, `practiceTimezonePlaceholder`).

| File | Line | Original text | Suggested key | Status |
|------|------|---------------|---------------|--------|
| `lib/features/notifications/presentation/notifications_screen.dart` | 428 | `const Text('Open Calendar')` | `notificationOpenCalendar` | Fixed — now `Text(l10n.notificationOpenCalendar)` |
| `lib/features/patients/presentation/patients_screen.dart` | 769 | `'Select Form Template'` | `selectFormTemplate` | Fixed — key in en/uz/ru; uses `l10n.selectFormTemplate` |
| `lib/features/chat/presentation/chat_screen.dart` | 743, 797, 907 | `'Error: $err'` | `error` | Fixed — now `'${AppLocalizations.of(context)!.error}: $err'` |
| `lib/features/auth/presentation/phone_login/otp_screen.dart` | 118 | `'To get a new code, go back and tap Continue again.'` | `otpResendHint` | Fixed — key in en/uz/ru; uses `l10n.otpResendHint` |
| `lib/features/auth/presentation/create_account/account_information_screen.dart` | 228 | `'Detecting…'` | `detecting` | Fixed — key in en/uz/ru; uses `l10n.detecting` |
| `lib/features/auth/presentation/create_account/account_information_screen.dart` | 232 | `'Practice timezone (e.g. Europe/Berlin)'` | `practiceTimezonePlaceholder` | Fixed — key in en/uz/ru; uses `l10n.practiceTimezonePlaceholder` |
| `lib/features/auth/presentation/create_account/create_account_screen.dart` | 305 | `'Practice timezone (e.g. Europe/Berlin)'` | `practiceTimezonePlaceholder` | Fixed — same key; uses `l10n.practiceTimezonePlaceholder` |

---

## 2. Incorrect localization usage

Findings and recommendations:

### 2.1 Backend / API error messages shown directly

- **Where:** `lib/features/auth/presentation/phone_login/otp_screen.dart` (e.g. line 97: `String msg = e.message ?? ''`), `lib/features/auth/presentation/create_account/registration_otp_dialog.dart` (e.g. `e.message ?? l10n.invalidOtp`), `lib/features/home/presentation/home_screen.dart` (line 388: `_aiError = e is AiStreamException ? e.message : e.toString()`).
- **Issue:** Firebase or backend messages (e.g. `e.message`) can be shown as-is. They are often in English or technical and are not localized.
- **Recommendation:** Prefer mapping known error codes (e.g. `auth/invalid-verification-code`) to localized keys (already done for invalid OTP in some paths). For unknown errors, show a generic localized message (e.g. `somethingWentWrong`) instead of raw `e.message` or `e.toString()`. Use `sanitizeErrorMessage()` from `lib/core/utils/error_formatter.dart` where appropriate.

### 2.2 Admin / developer-facing exceptions with raw response body

- **Where:** `lib/state/admin/admin_actions.dart` — many `throw Exception('Failed to …: ${response.body}')`.
- **Issue:** Exception messages include raw response body and are not suitable for end-user display; if these ever surface in UI, they would be technical and unlocalized.
- **Recommendation:** Keep technical messages for logging/debug only. If the app shows an error to the user, use a short localized string (e.g. from `errors.*`) and log the full response separately.

### 2.3 Enum values displayed without translation

- **Where:** `lib/features/notifications/presentation/notifications_screen.dart` uses `NotificationFilter` (e.g. `NotificationFilter.all`, `NotificationFilter.appointments`, `NotificationFilter.tasks`, `NotificationFilter.messages`) for filter chips/tabs. `lib/features/notifications/presentation/notification_ui_helpers.dart` defines the enum and uses it in switch/case.
- **Issue:** If the enum name or a string derived from it is shown in the UI (e.g. “All”, “Appointments”, “Tasks”, “Messages”), it may be hardcoded or not passed through localization.
- **Recommendation:** Add localization keys for each filter (e.g. `notifications.filterAll`, `notifications.filterAppointments`, `notifications.filterTasks`, `notifications.filterMessages`) and use `AppLocalizations.of(context)!.translate(key)` or a getter when rendering filter labels. Ensure all filter labels come from the same localized source.

### 2.4 Notification body text

- **Where:** `lib/features/notifications/presentation/notifications_screen.dart` (e.g. line 358: `notification.message`).
- **Issue:** If `notification.message` is backend-provided text, it may not be localized.
- **Recommendation:** If notifications are sent from the backend with a type/code, map that to a localized string in the app where possible; otherwise document that notification body is shown as received.

---

## 3. Suggested localization key structure by feature

Current keys in `_localizedValues` are flat (e.g. `appName`, `login`, `patients`). For clarity and scaling, a namespaced structure by feature is recommended. Below is a suggested mapping from current-style keys to a feature-based structure (no keys were invented; names are suggestions for grouping existing keys).

| Feature / namespace | Example keys (current or suggested) |
|--------------------|--------------------------------------|
| **auth.***         | login, signIn, createAccount, verify, oneTimeKey, keyVerified, firstName, lastName, enterPhoneNumber, pleaseVerifyInvitationKeyFirst, invalidOtp, otpSent, otpResent, otpResendHint, passwordRequired, pleaseConfirmPassword, passwordsDoNotMatch, resetPassword, accountPending, accountBlocked, accessRestricted |
| **dashboard.***    | dashboard, today, noAppointmentsToday, scheduleIsClear, allPatients, todayAppointments, upcomingAppointments, recentPatients, analytics |
| **navigation.***   | chat, home, calendar, patients, tasks, profile, signOut, signOutConfirm |
| **patients.***     | patient, patientList, searchPatients, noPatientsFound, patientDetails, generalInformation, documents, chronicDisease, selectChronicDisease, address, birthDate, gender, male, female, other, createTask, startAppointment |
| **appointments.*** | appointments, freeSlots, date, time, duration, place, changeSlot, cancelAppointment, cancelConfirm, appointmentCancelled, slotChanged, selectDate, selectTime, availableSlots, noSlotsAvailable, noAppointments |
| **tasks.***        | remoteCareTasks, createRemoteCareTask, taskName, description, category, vital, exercise, medication, timesPerDay, startDate, endDate, taskCreated, taskUpdated, progress, pending, missed, checkIns, status, active, taskCompleted |
| **chat.***         | chat, messages, sendMessage, voiceMessage, imageMessage, etc. (any existing chat-related keys) |
| **notifications.***| notification filters and labels (e.g. filterAll, filterAppointments, filterTasks, filterMessages) and any other notification UI strings |
| **errors.***       | error, unauthorized, networkError, requestTimeout, accessDenied, notFound, serverError, somethingWentWrong, failedToLoad, failedToSaveDraft |
| **forms.***        | required, save, cancel, delete, edit, back, next, complete, submit, close, yes, no, ok, confirm, discard, search, filter, apply, refresh, noData |

Migration can be gradual: keep existing flat keys and add new ones under the feature prefix, or later refactor to load from JSON keyed by feature.

---

## 4. Refactoring suggestions: prefer getters over `translate('key') ?? 'Fallback'`

The app defines many getters on `AppLocalizations` (e.g. `appName`, `loading`, `next`, `createAccount`, `invalidOtp`, `otpResent`). Using getters gives type safety and avoids typos; using `translate('key') ?? 'Fallback'` duplicates the fallback and can drift from the real value.

### 4.1 Where getters already exist

- Prefer `AppLocalizations.of(context)!.keyName` (or `l10n.keyName`) instead of `AppLocalizations.of(context)!.translate('keyName') ?? 'Fallback'`.
- Example: `translate('next') ?? 'Next'` → `AppLocalizations.of(context)!.next`.
- Same for: cancel, save, createAccount, keyVerified, firstName, lastName, phoneNumber, emailOptional, password, confirmPassword, enterFirstName, enterLastName, enterPhoneNumber, pleaseVerifyInvitationKeyFirst, invalidOtp, tooManyRequests, otpResent, etc., whenever a getter exists.

### 4.2 Keys used with `translate(...)` that have no getter

**Done:** Getters were added for `detecting`, `practiceTimezonePlaceholder`, `otpResendHint`, and `selectFormTemplate`; the UI now uses `l10n.detecting`, `l10n.practiceTimezonePlaceholder`, `l10n.otpResendHint`, and `l10n.selectFormTemplate`.

Still no getter (optional follow-up):

| Key | File(s) | Recommendation |
|-----|---------|----------------|
| `passwordRequired` | create_account_screen.dart | Add getter and use `l10n.passwordRequired` (or keep `loc.translate('passwordRequired')` without fallback). |
| `pleaseConfirmPassword` | create_account_screen.dart | Add getter and use `l10n.pleaseConfirmPassword`. |

### 4.3 Dynamic keys

- For dynamic keys (e.g. `translate(r.l10nKey)` or `translate(errorKey)`), keep using `translate(key)` when the key is not known at compile time. Ensure those keys exist in `_localizedValues` and, if needed, add a small set of getters for the fixed set of validation/error keys used in forms.

### 4.4 error_formatter.dart

- `sanitizeErrorMessage()` uses `l10n.translate('unauthorized') ?? '...'`. Prefer `l10n.unauthorized` (and similar getters) once they exist, and remove the `?? '...'` fallbacks so the single source of truth is the map.

---

## 5. Exported JSON files

The same keys and values as in `_localizedValues` have been exported to:

- `assets/localization/en.json`
- `assets/localization/uz.json`
- `assets/localization/ru.json`

Key counts at export: **en** 752, **uz** 746, **ru** 672. Differences reflect keys present in the Dart map per locale (e.g. uz/ru may have fewer entries). No new keys were added; only existing entries were exported. These files can be used as reference or for a future JSON-based loader; the app still reads from `app_localizations.dart` only unless you change the loading mechanism.

---

*Report generated for the Shifa Doctor Web App (Doctor Web App repo).*
