# 15 — HTTP API Reference (Shifa doctor app)

Consolidated index of every backend call the Flutter doctor app makes (grep of `lib/` on 2026-10-24: 194 distinct
paths), grouped by feature. Request and response shapes, error handling and quirks per endpoint are in the feature
doc named in the last column (each has an "Endpoints" table with `file:line` citations). **This file wins for the
list and the method; the feature doc wins for payload detail; `<BASE_URL>/v3/api-docs` wins over both when it
disagrees and a real call confirms it.**

Legend: `{id}` = path parameter. `multipart` = `multipart/form-data`. `SSE` = server-sent events stream.
`no-401` = the Flutter client deliberately does not log out on 401/403 for this call. `pub` = called without a token.

---

## 1. Global conventions

| Topic | Rule |
|---|---|
| Base URL | Production `https://api.shifa.uz` (17 §1; the Railway host stays alive for old store builds), development `https://api-dev.shifa.uz`; paths below include the `/api` prefix as the Flutter code writes them. In Kotlin, `ApiConfig.BASE_URL` ends with `/api` and `Endpoints` omit it. |
| Auth | `Authorization: Bearer <jwt>`. No refresh token. JWT claims read: `exp`, `role` (`DOCTOR` \| `CLINIC_STAFF` \| `ADMIN`). |
| Public paths | `/api/auth/**` (login, OTP, register, verify-key, check-existing-patient, forgot-password…), `/api/public/**`. `POST /api/auth/reset-password` (change password while signed in) needs the token. |
| 401 / 403 | **Superseded by 17 §1 (built in CORE-05):** a 401 to a request that carried a token, or `code = ACCOUNT_DISABLED`, ends the session; a 403 never does. Verified on dev 2026-11-04: a missing or invalid token gets `401 {"error":"Authentication required","code":"UNAUTHORIZED","requestId":…}` (not `TOKEN_INVALID`). The old Flutter rule (403 on English body strings) is in 01 §3 for reference only. |
| Pre-flight expiry | Flutter rejects requests locally when `exp + 30 s < now` and logs out. Keep. |
| Timeout | 30 s (Flutter applied it only to GET/POST; apply to all). |
| Content type | JSON. The backend can return HTML error pages: check `Content-Type` before decoding. Error bodies (dev, 2026-11-04): `{error, message?, status?, code, requestId}`; `error` is the status number on some routes (`{"error":404,"message":"Appointment not found: 9",…}`), and `message` can name records, so it is never logged or shown. |
| Times | Instants are ISO-8601 UTC with `Z`. Dates are `YYYY-MM-DD`. Times of day are `HH:mm`. Display in the doctor's practice time zone (`profile.timeZone`), clinic time zone for clinic-scoped screens, patient time zone for task check-ins. |
| Money | Integer minor units (`*Minor`) + `currency` (`UZS`, `USD`, `EUR`, `RUB`). |
| IDs | Numeric on the wire; the Flutter app stringifies patient ids. Tolerate `int`/`double`/`String`. |
| Pagination | Spring page `{content, totalElements, totalPages, number, size}` on `/api/patients`, `/api/clinics/{id}/patients`, finance ledger/records; bare arrays elsewhere. |
| Language | `lang` query (`en`\|`uz`\|`ru`) on public professions and ICD search; `PUT /api/doctors/me/language` syncs the UI language (`uz-Cyrl` → `uz`). |
| Media URLs | Relative `photoUrl`/`fileUrl` are resolved against the backend origin (Flutter only did it for images: doc 07 §1.5). |
| Polling | Chat list + open conversation 5 s, chat unread 10 s, task details 15 s, scribe status 3 s (max 120 s), signature status 10 s, briefing 3 s, payment/notification unread: on resume/push only. No websockets. |

---

## 2. Endpoint index

### 2.1 Auth and session (doc 01)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | `{username,password}` → `{token}`; `?app=admin` on the admin host |
| POST | `/api/auth/send-login-otp` | pub, `{email}` |
| POST | `/api/auth/verify-email-otp` | pub, `{email,code}` → `{token}` |
| POST | `/api/auth/send-login-sms-otp` | pub, `{phone}` (E.164, +998 only; DOCTOR accounts) → `{sent, channel:"sms"\|"email", smsFallbackUsed, emailHint?}`: when the SMS can't go out the code goes to the account's e-mail (`emailHint` masked); 404 unknown number, 403 not a doctor / disabled / locked, 503 no SMS and no e-mail (AUTH-10, backend 2026-11-07, unmerged) |
| POST | `/api/auth/verify-login-sms-otp` | pub, `{phone,code}` → `{token}` (DOCTOR); accepts the SMS code or the e-mailed fallback; 401 wrong or expired |
| POST | `/api/auth/send-forgot-password-otp` | pub, `{identifier, app}` |
| POST | `/api/auth/forgot-password-reset` | pub, `{email,emailOtp,app,newPassword}` → `{token}` (legacy `{idToken,…}`) |
| POST | `/api/auth/reset-password` | **token required**, `{currentPassword,newPassword}` (change password) |
| POST | `/api/auth/verify` | pub, Bearer = Firebase ID token (phone OTP; unreachable in Flutter today) |
| POST | `/api/auth/verify-key` | pub, `{key}` → `{valid,purpose,clinicId,clinicName,emailSentTo}` |
| POST | `/api/auth/check-existing-patient` | pub, `{firstName,lastName,email,phone?}` → `{found,fullName,photoUrl,email}` |
| POST | `/api/auth/send-email-otp` | pub, `{email, purpose:'REGISTRATION'}` |
| POST | `/api/auth/register` | pub → `{token}`; optional `emailOtp` (the REGISTRATION code) and `currentPassword`. Codes: 409 `DOCTOR_EXISTS` (never re-keys a doctor), 409 `OWNERSHIP_REQUIRED` (an existing non-doctor account needs its code or current password), 400 `EMAIL_OTP_INVALID` / `EMAIL_OTP_EXPIRED` (BE-29, BE-03; backend 2026-11-07) |
| POST | `/api/auth/register-clinic-staff` | pub → `{token}`; same `emailOtp`, `currentPassword` and codes |
| POST | `/api/auth/admin/request-login-otp` | admin only (doc 10) |
| POST | `/api/auth/admin/verify-login-otp` | admin only (doc 10) |
| GET | `/api/public/legal?lang=en\|uz\|ru` | pub → `{terms, privacy, licences, version, updatedAt}` (nullable strings; BE-16, backend 2026-11-07, `legal.*` in system config) |
| GET | `/api/public/config` | pub, `{transcriptionFeedbackEnabled, googleMapsApiKey, doctorApp:{minVersion,latestVersion,storeUrl:{android,ios}, maintenance:{active,message:{en,uz,ru},until}}}`; unset values are `null` (BE-04, backend 2026-11-07, admin keys `doctor_app.*`) |
| GET | `/api/public/clinics?search=` | pub, `[{id,name}]` |
| GET | `/api/public/professions?lang=&search=` | pub, `[{english,uzbek}]` |

### 2.2 Doctor profile, settings, device (doc 08, doc 01)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/doctors/me` | `{profile, contact, billing, settings, subscription?}` — the central profile call |
| GET | `/api/me/profile` | clinic-staff profile (`MeProfile`) |
| GET | `/api/me/clinics` | memberships → clinic workspace (doc 09) |
| GET | `/api/practice/me` | **unused** (dead provider) |
| PATCH | `/api/doctors/me/profile` | partial: profession, timeZone, biography, telegram, instagram, services, certificates, 8 location keys, (dob, gender, address, clinic from registration) |
| PATCH | `/api/doctors/me/contact` | `{phone,email}` |
| PATCH | `/api/doctors/me/billing` | billing + echoed gateway ids |
| PATCH | `/api/doctors/me/settings` | full `{country,language,twoFA,encryptedDocs,defaultStartTab}` |
| PUT | `/api/doctors/me/language` | `{language}` |
| PUT | `/api/doctors/me/fcm-token` | `{fcmToken}` (`""` on logout); the Flutter build's single token |
| POST | `/api/doctors/me/fcm-tokens` | `{token, platform: android\|ios\|web}` → 204; one per device, idempotent (BE-05, backend 2026-11-07; the app sends it with PUSH-01) |
| DELETE | `/api/doctors/me/fcm-tokens/{token}` | → 204 at sign-out; idempotent |
| GET | `/api/doctors/me/export` | the doctor's data as a JSON download (every section of `GET /doctors/me` + locations), once a day (429) (BE-02) |
| POST | `/api/doctors/me/delete-account/request` | → `{maskedEmail, expiresInSeconds}`, e-mails a code; 409 `HAS_ACTIVE_OBLIGATIONS` with `details` `UPCOMING_APPOINTMENTS` / `ACTIVE_SUBSCRIPTION` (BE-02) |
| POST | `/api/doctors/me/delete-account/confirm` | `{code}` → `{success}`; closes and anonymises the account, signs out every session; 400 `EMAIL_OTP_INVALID` / `EMAIL_OTP_EXPIRED` |
| POST | `/api/doctors/me/photo` | multipart `file` → `{photoUrl}` |
| POST | `/api/doctors/me/certificate` | multipart `file` → `{photoUrl}` |
| GET/POST | `/api/doctors/me/locations` | `DoctorLocationDto` |
| PATCH/DELETE | `/api/doctors/me/locations/{id}` | |
| GET/POST | `/api/doctors/me/services` | doctor price list |
| PATCH/DELETE | `/api/doctors/me/services/{id}` | |
| GET/POST | `/api/doctors/me/service-groups` | |
| PATCH/DELETE | `/api/doctors/me/service-groups/{id}` | |
| POST | `/api/doctor/payments/stripe/connect/onboard` | `{}` → `{onboardingUrl}` |
| POST | `/api/doctor/subscription/checkout` | `{planCode, gateway:'STRIPE'}` → `{checkoutUrl}` |

### 2.3 Analytics (doc 02, doc 08 §7)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/doctor/analytics/overview` | |
| GET | `/api/doctor/analytics/appointments-trend?days=` | |
| GET | `/api/doctor/analytics/consultation-types` | |
| GET | `/api/doctor/analytics/engagement` | |
| GET | `/api/doctor/analytics/sms-usage?from=&to=` | |

### 2.4 Calendar, schedule, appointments (doc 03, doc 05)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/calendar?day=YYYY-MM-DD[&doctorId=]` | entries `FREE_SLOT` \| `APPOINTMENT` \| `BLOCKED`; appointment rows carry `isVideo` (BE-09, backend 2026-11-07) |
| POST | `/api/schedule/book` | `{startAt,slotMinutes,patientId,location,reason,isVideo,locationId?,resourceDoctorId?}` |
| GET/PUT | `/api/schedule/rules[?locationId=]` | weekly rules |
| PATCH | `/api/schedule/valid-range` | `{validUntil, validFrom?}` |
| GET | `/api/schedule/validity-periods` | |
| GET/POST | `/api/schedule/date-specific[?locationId=]` | |
| DELETE | `/api/schedule/date-specific/{id}` | |
| POST | `/api/schedule/blocks` | `{startAt,endAt,cancelOverlappingAppointments,reason?,resourceDoctorId?}` |
| GET | `/api/schedule/blocks/overlapping-count?startAt=&endAt=[&doctorId=]` | |
| DELETE | `/api/schedule/blocks/{id}[?doctorId=]` | |
| GET | `/api/appointments/{id}` | signature + linked plan fields |
| DELETE | `/api/appointments/{id}` | cancel (no reason) |
| POST | `/api/appointments/{id}/cancel` | `{reasonCode: DOCTOR_UNAVAILABLE\|PATIENT_REQUEST\|RESCHEDULED\|OTHER, note? ≤500}` → 200, same rules as DELETE; the patient's notification carries the reason (BE-10) |
| POST | `/api/appointments/{id}/no-show` | `{notify?: Boolean = true}` → the doctor's `AppointmentDto`; CONFIRMED or IN_PROGRESS, from the start to 24 h after the end, else 409 `NO_SHOW_NOT_ALLOWED`; the patient gets `APPOINTMENT_NO_SHOW` unless `notify:false` (BE-10) |
| GET | `/api/doctors/me/contract` | `{contractNumber, effectiveDate, endDate, termMonths, issuedAt, status: ACTIVE\|EXPIRED}` or 404 `NO_CONTRACT` (BE-16) |
| GET | `/api/doctors/me/contract/pdf` | `application/pdf`, the early-partner contract rendered by the backend; 404 `NO_CONTRACT` |
| PUT | `/api/appointments/{id}/change` | `{startAt, slotMinutes}` |
| PUT | `/api/appointments/{id}/complete` | `{clinicId?, doctorNotes?, awaitingScribe?}` → `{scribePending,…}` |
| PUT | `/api/appointments/{id}/request-signature` | |
| POST | `/api/appointments/{id}/notify-payment-reminder` | |
| GET/PUT | `/api/appointments/{id}/consultation-notes` | |
| GET | `/api/appointments/{id}/draft-notes` | AI drafts |
| GET | `/api/appointments/{id}/scribe-status` | poll 3 s |
| GET/PUT | `/api/appointments/{id}/dental-documentation` | payload v2 (doc 05 §4.1) |
| GET | `/api/appointments/{id}/briefing` | poll 3 s while PENDING/GENERATING |
| POST | `/api/appointments/{id}/briefing/retry` | |
| POST | `/api/consultations/upload-recording` | multipart `audio` + `appointmentId`, `language`; 202 / 429 |
| POST | `/api/video/token` | no-401, `{appointmentId, roomName?}` → `{token,roomUrl,roomName}`; refusals carry `code`: 403 `VIDEO_TOO_EARLY` / `VIDEO_TOO_LATE` with `joinWindow:{from,to}`, 402 `VIDEO_PAYMENT_REQUIRED` (BE-28). The doctor's `GET /api/appointments/{id}` gains `isVideoConsultation`, `cancelReasonCode`, `cancelNote`, `joinWindow`. |
| GET | `/api/video/appointments/{id}/presence` | the visit's own doctor → `{patientInRoom, patientJoinedAt?}` (Daily's room presence); 403 anyone else, 503 when Daily can't be asked (backend 2026-11-07, unmerged; the pre-join page asks every 5 s inside the join window) |
| POST | `/api/appointments/{id}/summary-document?lang=` | re-renders and files the server's (notes-only) summary → `{documentId, url}`; 409 without notes (BE-01; the app keeps its own richer summary) |

### 2.5 AI (doc 02)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/ai/stream` | SSE, Shifa AI chat / Intelligence |
| POST | `/api/ai/morning/stream` | SSE, Intelligence chat; body now also carries `conversationId` (local session id, since `4d05e61`) |
| POST | `/api/ai/transcribe` | multipart audio → text; optional `recordingId` field (`rec_<epochMs>`, since `4d05e61`) |
| POST | `/api/ai/transcription-feedback` | multipart, gated by `/api/public/config` |
| POST | `/api/ai/draft/{draftId}/confirm` | `{patientId?, appointmentId?}` |
| POST | `/api/ai/draft/{draftId}/discard` | |
| GET | `/api/ai/visit-facts/appointment/{id}` | video screen `VisitFactsPanel` |
| POST | `/api/ai/visit-facts/commit` | |
| GET | `/api/icd10/search?q=&limit=20&lang=` | |
| GET | `/api/clinical-engine/groups` | |
| GET | `/api/clinical-engine/groups/{id}/diseases` | |
| GET | `/api/clinical-engine/diseases/{id}` | |
| GET | `/api/clinical-engine/chips/search` | |
| GET | `/api/clinical-engine/occlusion/chips` | |
| GET | `/api/clinical-engine/doctors/me/top-diagnoses` | |
| GET | `/api/clinical-engine/shared/{id}` | |
| POST | `/api/clinical-engine/synthesize` | |

### 2.6 Patients, documents, forms (doc 06)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/patients?page=&size=100&sort=fullName,asc` | list (Flutter walks all pages) |
| GET | `/api/patients/for-assignment?page=&size=500&sort=` | light list for booking |
| POST | `/api/patients` | create |
| GET | `/api/patients/{id}[?clinicId=]` | |
| PATCH | `/api/patients/{id}` | per-field edits, SMS reminder fields |
| GET | `/api/patients/{id}/appointments` | history with this doctor |
| POST | `/api/patients/{id}/create-account` | → `{username, oneTimePassword}` |
| POST | `/api/patients/{id}/send-test-sms` | 403 / 503 handled |
| GET | `/api/patients/{id}/documents[?clinicId=]` | |
| POST | `/api/patients/{id}/documents[?clinicId=]` | multipart `file`, `title`, `category?`, `appointmentId?` |
| PUT | `/api/patients/{id}/documents/{docId}[?clinicId=]` | multipart replace (form PDF) |
| GET | `/api/patients/{id}/documents/{docId}/download[?clinicId=]` | bytes + Content-Disposition |
| POST | `/api/patients/{id}/documents/{docId}/request-access[?clinicId=]` | |
| POST | `/api/document-access-requests/{id}/approve` | the owner (NOTIF-04) → `{id, status, …}`; twice is fine; after a rejection 400 |
| POST | `/api/document-access-requests/{id}/reject` | the owner (NOTIF-04); the row's `documentAccessRequestStatus` is `pending\|approved\|rejected` |
| GET/POST | `/api/patients/{id}/forms` | 025-2 |
| PUT | `/api/patients/{id}/forms/{formId}` | |
| PUT | `/api/patients/{id}/forms/{formId}/request-signature` | |
| POST | `/api/patients/{id}/forms/{formId}/link-document?documentId=` | |
| GET/PUT | `/api/prophylaxis/settings` | `?patientId&clinicId` / `{patientId,clinicId,intervalMonths,enabled}` |
| GET | `/api/patients/{patientId}/forms/{formId}/pdf` | the 025-2 rendered by the server (`application/pdf`), read-only (BE-01, backend 2026-11-07) |
| POST | `/api/patients/{patientId}/forms/{formId}/pdf-document` | renders, files as `FORM_025_2`, links → `{documentId, url}`; idempotent; 404 on older backends → the app renders |

### 2.7 Care partnerships (doc 06 §5)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/care-partnerships/partners/search?country=&profession=&q=` | |
| GET/POST | `/api/care-partnerships` | |
| GET | `/api/care-partnerships/{id}` | |
| POST | `/api/care-partnerships/{id}/accept` \| `decline` \| `cancel` \| `complete` | `{}` |
| GET/POST | `/api/care-partnerships/{id}/progress` | |

### 2.8 Chat (doc 07 §1)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/messages/conversations` | |
| GET | `/api/messages/conversations/{id}[?after=<messageId>&limit=≤200]` | all messages; with `after` only newer ones, oldest first, `limit` default 100 (BE-14). The list takes `?since=<instant>` |
| POST | `/api/messages/conversations/start` | `{recipientDoctorId}` or `{recipientPatientId}` |
| POST | `/api/messages/conversations/{id}/read` | |
| POST | `/api/messages/send` | text / image / voice / document |
| GET | `/api/messages/search?q=` | users |
| GET | `/api/messages/unread-count` | `{count}` |
| POST | `/api/messages/upload-attachment` | multipart `file` → `{url}` |

### 2.9 Notifications (doc 07 §2)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/notifications` | full list; rows carry `conversationId` (chat) and `carePartnershipId` (partnership) since BE-06 (backend 2026-11-07); push `data` keys and every `type`: backend `docs/api/push-notifications.md` |
| GET | `/api/notifications/unread/count` | |
| PUT | `/api/notifications/{id}/read` | |
| PUT | `/api/notifications/read-all` | |

### 2.10 Remote-care tasks (doc 07 §3)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/tasks[?patientId=&status=]` | |
| GET | `/api/tasks/templates` | |
| POST | `/api/tasks` | |
| GET | `/api/tasks/{id}` | |
| GET | `/api/tasks/{id}/check-ins` | |
| GET | `/api/tasks/{id}/progress` | unused by UI |
| PATCH | `/api/tasks/{id}/cancel` | |

### 2.11 Clinic workspace, finance, treatment plans (doc 09)

| Method | Path | Notes |
|---|---|---|
| GET | `/api/clinics/{id}/overview-stats` | |
| GET | `/api/clinics/{id}/members` | |
| GET | `/api/clinics/{id}/patients?page=&size=&q=` | Spring page |
| GET | `/api/clinics/{id}/patients/{pid}/appointments` | |
| GET/POST | `/api/clinics/{id}/invitations` | |
| DELETE | `/api/clinics/{id}/invitations/{invId}` | |
| PATCH | `/api/clinics/{id}/finance-settings` | `{defaultDoctorRevenueSharePercent}` |
| PATCH | `/api/clinics/{id}/members/{doctorProfileId}/revenue-share` | |
| GET | `/api/clinics/{id}/finance/dashboard?from=&to=` | |
| GET/POST | `/api/clinics/{id}/finance/records` | page / create |
| GET/POST | `/api/clinics/{id}/finance/payments` | page (bare array) / record payment |
| GET | `/api/clinics/{id}/finance/appointment-ledger?page=&size=&from=&to=` | |
| GET | `/api/clinics/{id}/finance/doctor-earnings?from=&to=` | |
| GET | `/api/clinics/{id}/finance/installment-items?filter=` | |
| POST | `/api/clinics/{id}/finance/installment-plans` | |
| PATCH | `/api/clinics/{id}/finance/installment-items/{itemId}` | |
| POST | `/api/clinics/{id}/finance/installment-items/{itemId}/mark-paid` | |
| POST | `/api/clinics/{id}/finance/installment-items/{itemId}/notify` | |
| GET | `/api/clinics/{id}/finance/overdue` | unused |
| GET | `/api/clinics/{id}/finance/audit` | unused |
| GET/POST | `/api/treatment-plans` | `?clinicId&patientId&status&planKind&q` |
| GET | `/api/treatment-plans/{id}/detail` | |
| GET | `/api/treatment-plans/{id}/visits` | |
| PATCH | `/api/treatment-plans/{id}` | no clinic caller |
| PATCH | `/api/treatment-plans/{id}/status` | |
| POST | `/api/treatment-plans/{id}/lines` | replace |
| POST | `/api/treatment-plans/{id}/lines/append` | |
| POST | `/api/treatment-plans/{id}/link-appointments` | |
| POST | `/api/treatment-plans/{id}/book-slots` | |
| GET | `/api/treatment-plans/{id}/fulfillment-candidates?appointmentId=` | |
| POST | `/api/treatment-plans/{id}/appointments/{apptId}/fulfill` | `{lineIds}` |
| GET/POST | `/api/treatment-plans/catalog-items` | `?clinicId` |
| PATCH | `/api/treatment-plans/catalog-items/{id}` | |
| GET | `/api/treatment-plans/plan-services?clinicId=&doctorIds=` | |
| GET | `/api/treatment-plans/doctor-locations?clinicId=&doctorId=` | |
| GET | `/api/treatment-plans/free-slots?clinicId=&doctorId=&day=&locationId=` | |
| GET | `/api/treatment-plans/{id}/pdf?lang=en\|uz\|ru` | the plan rendered by the server (BE-01, backend 2026-11-07); 404 on older backends → the app renders |

### 2.12 Admin (doc 10, parked)

38 endpoints under `/api/admin/**` plus the two `/api/auth/admin/*` calls. Listed in doc 10 §4. Not part of the
mobile app; the doctor-scoped client must refuse them.

### 2.13 Third-party

| Service | Call | Notes |
|---|---|---|
| Google Geocoding | `GET https://maps.googleapis.com/maps/api/geocode/json?latlng=&key=&language=en` | key from `--dart-define` or `/api/public/config`; move behind a backend proxy |
| OpenStreetMap tiles | `https://tile.openstreetmap.org/{z}/{x}/{y}.png` | Flutter map picker; replaced by native maps |
| Daily.co | room URL + token from `/api/video/token` | native SDKs |
| Stripe | hosted onboarding / checkout URLs returned by the backend | open in Custom Tab / SFSafariViewController |
| Firebase | FCM token; Firebase Auth phone OTP (dead path) | |

---

## 3. Endpoints the Flutter app defines but never calls

Keep out of the KMP app until a screen needs them: `GET /api/practice/me`, `GET /api/tasks/{id}/progress`,
`GET /api/clinics/{id}/finance/overdue`, `GET /api/clinics/{id}/finance/audit`, `PATCH /api/treatment-plans/{id}`,
`POST /api/schedule/blocks` duplicates
in `schedule_actions.dart`.

## 4. Backend requests to raise (collect in `docs/backend-improvements.md`)

| Need | Why | Owner doc |
|---|---|---|
| Server-rendered PDFs: appointment summary, 025-2 form, treatment plan, finance tables | removes three client PDF engines, fixes missing `ru`, works on both OSes | 05, 06, 09 |
| `from`/`to` + real paging on `/finance/payments`, `/finance/records`, `/clinics/{id}/patients` | Flutter filters page 0 in memory (data loss) | 09 |
| `since`/cursor on chat conversation, or push-driven refresh | 5 s full refetch today | 07 |
| Explicit `consultationType` / `isVideo` on appointment and calendar entries | video detected by `location.contains("video")` | 03, 05 |
| Structured `patientName` / `appointmentStartAt` in every notification payload | client reverse-parses English copy | 07 |
| `POST /api/treatment-plans/compose` (transactional create) | wizard does 6–9 non-atomic calls | 09 |
| Reverse-geocoding proxy | Google key currently shipped to clients | 08 |
| `firstName`/`lastName`/`dob` accepted on `PATCH /api/doctors/me/profile` (confirm) | profile name edit never saved in Flutter | 08 |
| Registration `emailOtp` actually verified | dialog collects a code nobody checks | 01 |
| Tool hints as codes + params (e.g. `CANCEL_PREVIEW {count, names, skipped}`) instead of English sentences | the client now regex-matches ~10 English sentences to localise them (`intelligence_card_text.dart`); any backend wording change silently breaks uz/ru | 02 |
| Status/date/period fields in tool cards as ISO + enum, not English words (`"Next week"`, `"Tue 24 Oct"`) | same reverse-parsing problem for card labels | 02 |
