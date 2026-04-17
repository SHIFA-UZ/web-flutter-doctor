# Doctor App – Full i18n + Reactive UI Audit

**App stack:** Flutter (Dart), Riverpod, custom `AppLocalizations` (no react-i18next; no React).

---

# PART 1 — FULL TRANSLATION AUDIT (i18n)

## STEP 1 – Scan Results: Hardcoded / Untranslated Strings

### 1.1 Backend-sourced strings (show in Uzbek mode as English)

| Source | Backend sends | Where shown |
|--------|----------------|-------------|
| **DocumentAccessService.kt** | `title = "Document access request"` | Notifications list (`notification.title`) |
| **DocumentAccessService.kt** | `title = "Document access granted"` | Notifications list |
| **DocumentAccessService.kt** | `title = "Document access denied"` | Notifications list |
| **Notification model** | `message` from API | Notifications list (`notification.message`) |

**Location:** `lib/features/notifications/presentation/notifications_screen.dart` lines 205–220 display `notification.title` and `notification.message` directly. No translation.

### 1.2 Frontend hardcoded strings (no translation call)

| File | String | Line (approx) |
|------|--------|----------------|
| **image_message_bubble.dart** | `'Image not available'` | 80 |
| **image_message_bubble.dart** | `'Failed to load image'` | 163 |
| **document_message_bubble.dart** | `'Document'` (fallback for fileName) | 99 |
| **schedule setup_schedule_screen.dart** | `'Failed to load schedule'`, `'Error'`, `'Error while saving'` (fallbacks) | 124, 387, 454 |
| **schedule setup_schedule_screen.dart** | `SnackBar(content: Text('${e.toString().replaceFirst('Exception: ', '')}'))` — raw exception text | 373 |
| **patients_screen.dart** | `' sent.'` in `'${l10n.requestAccess} sent.'` — “ sent.” is hardcoded | 1403, 1504 (video_call) |
| **app.dart** | `title: 'Shifa Doctor'` | 41 |

### 1.3 Fallback pattern (English in code as fallback)

Many places use `translate('key') ?? 'English fallback'`. If a key is missing in `uz`/`ru`, the fallback shows in Uzbek/Russian mode. All such fallbacks are effectively hardcoded English. Examples:

- **document_message_bubble.dart:** `'Error opening document'`, `'Cannot open document'`
- **profile_screen.dart:** `'Password updated'`, `'Extended profile saved'`, `'Upload failed'`, `'Profile Information'`, `'Contact Details'`, `'Payment and Invoicing'`, `'Two-factor Authentication'`, `'Schedule'`, `'Password'`, `'Full Name'`, `'Date of birth'`, `'Country'`, `'Billing Name'`, `'Billing Email'`, `'Current Password'`, `'New Password'`, `'Confirm New Password'`, etc.
- **patients_screen.dart:** `'Document'`, `'uploaded'`, `'Upload error'`, `'Pages scanned'`, `'Uploaded'`, `'page document'`, `'Scan failed'`, `'Create failed'`, `' sent.'`
- **schedule:** `'Failed to load schedule'`, `'Error'`, `'Error while saving'`
- **chat_screen.dart:** Uses l10n getters (e.g. `l10n.failedToSendMessage`) — OK if keys exist in all locales.

### 1.4 Snackbars / toasts / errors

- Most SnackBars use `l10n.*` or `AppLocalizations.of(context)!.translate('...')` with fallbacks.
- Error UIs often use `'${l10n.error}: $e'` — the `: $e` part is raw backend/exception text (can be English).
- **Recommendation:** For known error types, map to translation keys; for unknown, show a generic “Something went wrong” and log `e`.

---

## STEP 2 – i18n Setup (Doctor App)

| Question | Answer |
|----------|--------|
| **Library** | Custom: **AppLocalizations** (no react-i18next; this is Flutter). |
| **Translation storage** | Single file: **`lib/core/localization/app_localizations.dart`**. One big `_localizedValues` map with keys `'en'`, `'uz'`, `'ru'` (no separate JSON files). |
| **Language detection** | **`languageProvider`** (Riverpod) + **`languageProvider.notifier`**; locale persisted (e.g. SharedPreferences). App uses `locale: languageState.locale` in `MaterialApp`. |
| **Fallback** | `translate(key)` returns value for current locale or falls back to `'en'` if key missing. Many widgets also use `?? 'English text'` in code. |
| **Namespaces** | No; flat keys (e.g. `'documentAccessApproved'`, `'profileInformation'`). |
| **Usage** | `AppLocalizations.of(context)!.translate('key')` or getters like `l10n.documentAccessApproved`. |

---

## STEP 3 – Fix Architecture (What Must Change)

1. **No hardcoded user-facing strings**
   - Replace every `'Image not available'`, `'Failed to load image'`, `'Document'`, etc. with `AppLocalizations.of(context)!.translate('key')` (and add keys in en/uz/ru).
   - Remove English fallbacks in UI (e.g. `?? 'Error opening document'`). Ensure keys exist in all three locales so fallback is never needed in production.

2. **Notifications: stop showing backend text as-is**
   - **Option A (preferred):** Backend sends `messageKey` (and optionally `titleKey`). Frontend uses only `translate(messageKey)` / `translate(titleKey)` for known types (e.g. `DOCUMENT_ACCESS_APPROVED` → `documentAccessApproved`).
   - **Option B:** Frontend maps `notification.type` + `documentAccessRequestStatus` to title/message keys and **does not** display `notification.title` / `notification.message` for these types. Only show backend message for unknown types (with a generic “Notification” label).

3. **Backend (DocumentAccessService.kt)**
   - **Option A:** Send `titleKey` / `messageKey` instead of (or in addition to) `title` / `message`. Frontend translates by key.
   - **Option B:** Keep sending human-readable text only for logging/admin; frontend never shows it for document-access notifications and uses only localized strings keyed by type/status.

4. **Add missing keys**
   - In `app_localizations.dart`, add for **en**, **uz**, **ru** at least:
     - `imageNotAvailable`, `failedToLoadImage`
     - `document` (for “Document” as default filename)
     - `requestAccessSent` (for “Request access sent.” so “ sent.” is not hardcoded)
     - Any key currently used only as fallback in `?? '...'` — ensure they exist in uz/ru so fallbacks are never shown.

---

## STEP 4 – Backend Messages

- **Current:** Backend sends `title` and `message` in English (e.g. “Document access granted”).
- **Required:** Either:
  - **Option A:** Backend sends `messageKey` (e.g. `documentAccessGranted`); frontend uses `t(messageKey)` only. No display of backend `message` for these.
  - **Option B:** Frontend ignores backend `title`/`message` for known notification types and uses only `notification.type` + status to choose translation keys (e.g. `DOCUMENT_ACCESS_APPROVED` → `documentAccessApproved`).

---

## STRICT RULE

- No visible English in Uzbek (or Russian) mode.
- Every user-visible string must come from `AppLocalizations` (translate/getter) with keys present in en, uz, and ru.

---

# PART 2 — REACTIVE UI / AUTO REFRESH

## STEP 1 – State Management

| Question | Answer |
|----------|--------|
| **State management** | **Riverpod** (Flutter). No Redux, no React Query. |
| **API layer** | Direct `ApiClient` (HTTP) + **FutureProvider** / **StateNotifier** in `state/`. |
| **Caching** | FutureProviders hold result until invalidated. No explicit TTL or refetchInterval on most providers. |
| **Invalidation** | Used: `ref.invalidate(provider)` after mutations (e.g. after send message, mark read). No automatic refetch on window focus. |
| **WebSockets** | **None** found. No real-time push. |

---

## STEP 2 – Current Data Strategy

- **Chat:** `conversationProvider`, `conversationsProvider`. **Chat screen** runs a **periodic refresh every 5 seconds** via `_startPeriodicRefresh()` (delay 5s then invalidate conversations + selected conversation, then reschedule). So chat list and messages do refresh without user action.
- **Unread count (chat):** `unreadCountProvider` is a **StreamProvider** that polls every **10 seconds**.
- **Home analytics:** `doctorAnalyticsOverviewProvider` uses **Timer.periodic(30 seconds)** and **ref.invalidateSelf()** to auto-refresh. Also **ref.watch(todayAppointmentsProvider)** so it refreshes when today’s appointments change.
- **Today appointments:** `todayAppointmentsProvider` — no built-in polling; refreshed when invalidated (e.g. from home when navigating).
- **Notifications:** **No polling.** Only refreshed on:
  - Screen init (`ref.read(doctorNotificationsControllerProvider).refresh()` in post-frame callback)
  - Pull-to-refresh (RefreshIndicator)
  - After mark-as-read / approve / reject
  So notifications **do not** auto-update; user must pull to refresh or re-enter screen.
- **Calendar:** Uses `calendarController` / calendar providers. No periodic refresh found; user must open screen or trigger refresh.
- **Homepage:** Has 30s refresh for analytics overview; today’s appointments depend on `todayAppointmentsProvider` invalidation (e.g. when returning to home or after actions).
- **Schedule:** No periodic refresh; data loaded when screen is built / invalidated.

---

## STEP 3 – Notifications

- **Current:** Manual refresh only (pull-to-refresh + refresh on init).
- **Needed:** Either:
  - **Option A:** WebSocket from backend for new notifications; on message, call `ref.invalidate(doctorNotificationsProvider)` and unread count provider.
  - **Option B:** Polling every 10–30s while notifications screen is mounted (or app in foreground): e.g. `Timer.periodic` in the screen or a dedicated provider that invalidates `doctorNotificationsProvider` and `doctorNotificationsUnreadCountProvider`.

---

## STEP 4 – Calendar & Homepage

- **After appointment create/update/delete:** Call `ref.invalidate(todayAppointmentsProvider)`, and any calendar provider (e.g. `calendarProvider` or equivalent used by calendar screen). Home analytics already watches `todayAppointmentsProvider` and has 30s self-invalidation.
- **Recommendation:** Centralize “appointment changed” invalidation in one place (e.g. after any appointment mutation) and invalidate: `todayAppointmentsProvider`, calendar provider, and home analytics provider so calendar and homepage stay in sync without manual refresh.

---

## STEP 5 – Manual Refresh

- **No `window.location.reload()`** (Flutter app).
- **Manual refresh patterns:** RefreshIndicator on notifications; chat has no pull-to-refresh but has 5s periodic invalidation. No other full-page reload found.
- **Recommendation:** Keep RefreshIndicator where useful; add periodic invalidation for notifications (and optionally calendar) so data updates without user action.

---

# DELIVERABLES SUMMARY

## 1. List of untranslated / hardcoded strings

- **Backend (shown in UI):** “Document access request”, “Document access granted”, “Document access denied”, and any `message` body from notifications API.
- **Frontend:** “Image not available”, “Failed to load image”, “Document” (fileName fallback), “ sent.”, raw exception in schedule SnackBar, “Failed to load schedule”, “Error”, “Error while saving”, and all `?? '...'` fallbacks listed in 1.3 (ensure keys exist in uz/ru or remove fallbacks).

## 2. Confirmation

- **Before fixes:** Not all strings are translated; notifications and several fallbacks show English in Uzbek mode.
- **After full refactor:** All user-visible strings should go through `AppLocalizations` with keys present in en, uz, ru; notification title/message for known types should come from frontend translation only (or backend messageKey).

## 3. Current state management

- **Riverpod** (FutureProvider, StateNotifier, Provider, StreamProvider).
- **No React Query.** Caching is “last result until invalidated”.
- **No WebSockets.** All updates via HTTP + invalidation or polling.

## 4. WebSocket

- **Does not exist.** Notifications and list updates are request/response only.

## 5. Reactive strategy implemented today

- **Chat:** 5s periodic invalidation of conversations + selected conversation.
- **Unread count:** 10s polling (StreamProvider).
- **Home analytics overview:** 30s self-invalidation + watch today appointments.
- **Notifications:** No automatic refresh (only on init and pull-to-refresh).

## 6. Pages that auto-update (without user refresh)

- **Chat:** Yes (5s).
- **Home (analytics KPIs):** Yes (30s + dependency on today appointments).
- **Unread badge (chat):** Yes (10s).
- **Notifications:** No.
- **Calendar:** No (only when opened/invalidated).
- **Schedule:** No (only when opened/invalidated).
- **Patients list:** No (only when opened/invalidated).

## 7. Remaining limitations

- Notifications require manual or periodic refresh (no WebSocket, no polling yet).
- Calendar and schedule do not auto-refresh in background.
- Backend notification titles/messages are English and shown as-is; need frontend (or backend key) translation.
- Some error messages still append raw exception/backend text (`$e`) which may be English.
- Many `?? 'English'` fallbacks; if a key is missing in uz/ru, English appears.

---

# RECOMMENDED NEXT STEPS

1. **i18n**
   - Add missing keys to `app_localizations.dart` (en/uz/ru) for: `imageNotAvailable`, `failedToLoadImage`, `document`, `requestAccessSent`, and any key used as fallback.
   - Replace all hardcoded strings and remove English fallbacks (or add uz/ru for every fallback key).
   - Notifications: map `type` + status to localized title/body (or use backend messageKey) and stop showing backend `title`/`message` for document-access types.
   - Optionally: backend sends `titleKey`/`messageKey` for notifications; frontend translates by key only.

2. **Reactive UI**
   - Add **notifications polling** (e.g. every 15–30s) when app is in foreground or when notifications screen is mounted.
   - After any **appointment mutation**, invalidate: `todayAppointmentsProvider`, calendar provider, and home analytics provider.
   - Optionally: add short-interval polling or WebSocket for notifications for near–real-time updates.

3. **Consistency**
   - Centralize “invalidate after appointment change” in one place (e.g. appointments controller or a single “afterMutation” callback).
   - Consider a small “refetch on resume” (e.g. when app returns to foreground) for notifications and calendar so returning users see fresh data.

---

*End of audit. Paste this into your thread and we can optimize further for Railway + Firebase.*
