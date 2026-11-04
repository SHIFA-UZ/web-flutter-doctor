# Backend improvements (doctor app)

What the KMP doctor app needs from the backend, found by checking every flow in `docs/ux-flows/` and every endpoint
in `docs/kmp-spec/15-api-reference.md` against the backend contract (`backend-kotlin-shifa/docs/api/openapi.json`,
2026-11-01, 291 paths; source checked on 2026-11-04). Each item says what happens now, what to change, what the app
does until then, and which flow and ticket need it. Same format as the patient app's
`apps-flutter-patient/docs/backend-improvements.md`; items the patient app already requested are referenced, not
repeated.

Rules for the backend side (workspace `CLAUDE.md`): additive and backward compatible, own `feature/` branch per item,
Flyway migration only, regenerate `docs/api/openapi.json`, then add a "Done (date)" line here.

| # | Item | Priority | First needed by | App change after |
|---|---|---|---|---|
| BE-01 | Server-rendered PDFs (appointment summary, 025-2, treatment plan) | High | M4 FORM-03, M5 PDF-01, M11 TP-05 | Remove the fallback renderers |
| BE-02 | Doctor account deletion and data export | High | M2 SET-01 | Small |
| BE-03 | Registration e-mail code actually verified | High | M1 AUTH-06 | Small (show the dialog) |
| BE-04 | Force-update and maintenance in `/public/config` | High | M1 AUTH-01 | None (fields parsed already) |
| BE-05 | Per-device push tokens | High | M8 PUSH-01 | Small |
| BE-06 | Pushes in the doctor's language, structured `data` | High | M8 PUSH-02 | None or small |
| BE-07 | Intelligence hints and card fields as codes | Medium | M10 INTEL-05 | Delete the localiser |
| BE-08 | Standardised SSE errors (`ErrorCode`, `requestId`) | Medium | M10 AI-01 | Small |
| BE-09 | `isVideo` on calendar entries | Medium | M3 CAL-1 | Delete one heuristic |
| BE-10 | Cancel reason; mark no-show | Medium | M3 CAL-6, M5 APPT-01 | Small (unhide) |
| BE-11 | `from/to` on finance payments and records | Medium | M11 FIN-03 | Delete the page walker |
| BE-12 | Transactional treatment-plan compose | Medium | M11 TP-04 | Medium (simplify the wizard) |
| BE-13 | Idempotency keys on payments and visit charges | Medium | M5 DENTAL-03 | Small |
| BE-14 | Chat cursor and push-driven refresh | Medium | M7 CHAT-03 | Small |
| BE-15 | Reverse-geocoding proxy; no Google key in clients | Medium | M2 PROF-07 | Small |
| BE-16 | Doctor-facing legal documents and own contract | Medium | M2 SET-01, M12 LEGAL-01 | Small |
| BE-17 | Notification preferences | Medium (product first) | M8 | Medium (new screen) |
| BE-18 | PREMIUM checkout; Stripe Connect onboarding state | Medium | M2 SUB-02 | Small |
| BE-19 | `from/to` on analytics overview, types, engagement | Low | M6 | Small |
| BE-20 | `followUpOf` on booking | Low | M5 | Small |
| BE-21 | Document delete/rename/recategorise; HEIC conversion | Low | M4 | Small |
| BE-22 | `createdAt` on forms | Low | M4 | None |
| BE-23 | Installment `lastNotifiedAt`; ledger `doctorId` filter | Low | M11 | Small |
| BE-24 | Clinic self-service and line-level plan edits | Low (product first) | M11 | Medium |
| BE-25 | Task thresholds; template CRUD | Low (product first) | M9 | Medium |
| BE-26 | Pre-verify the forgot-password code | Low | M1 | Small |
| BE-27 | Rolling validity; bulk shift on timezone change | Low | M3 | Small |
| BE-28 | Coded `/video/token` errors; explicit join window | Low | M5 | Small |
| BE-29 | `register` must not re-key an existing doctor account | **High (security)** | M1 AUTH-06 | Small |
| BE-30 | Remove the profile photo | Low | M2 PROF-03 | Small |

Then: **Confirm with the backend** (questions, C-1…C-10) and **Already satisfied** (do not request again).

---

## BE-01 Server-rendered PDFs

**Needed by:** flows `05/13` (appointment summary), `06/10` (025-2 form), `09/16` and `09/13` (treatment plan);
tickets PDF-01, FORM-03, TP-05. Spec `15` §4 row 1.

**Now:** the Flutter app renders three PDFs on the client with the `pdf` package and DejaVuSans; `ru` is missing, the
summary is created *after* the visit is completed (a failure strands a completed visit without its document, bug 5),
and the 025-2 PDF is uploaded as a document through `PUT /patients/{id}/documents/{docId}`.

**Change:** three endpoints that return `application/pdf` with `Content-Disposition`, rendered in the requester's
language (`lang` query, default the doctor's profile language), and that also file the document for the patient:
- `POST /api/appointments/{id}/summary-document` → renders and attaches the summary (category `APPOINTMENT_NOTE`),
  returns `{documentId, url}`; idempotent (re-render replaces). Called by the backend itself at `complete` when
  `awaitingScribe` is false, so the app never has to.
- `GET /api/patients/{patientId}/forms/{formId}/pdf` → renders the 025-2 form; `link-document` becomes implicit.
- `GET /api/treatment-plans/{planId}/pdf`.
Fonts with Cyrillic coverage on the server; the same layout for `en`, `uz`, `ru`.

**App until then:** `expect/actual` renderers for the appointment summary and the 025-2 form only (PdfDocument on
Android, PDFKit on iOS), with `ru` strings; the treatment-plan PDF is not built (share the detail as CSV/text). The
end-visit orchestrator shows "completed, summary pending" with retry whenever the fallback fails after `complete`.

## BE-02 Doctor account deletion and data export

**Needed by:** flow `08/12`; ticket SET-01. Apple App Store Review 5.1.1(v) and Google Play's account-deletion policy
require in-app deletion for apps with account creation (the doctor app has invite-key registration).

**Now:** only patients have `POST /patients/me/delete-account/request` and `/confirm`. Doctors have nothing; admins
can delete users in the web panel.

**Change:** mirror the patient flow for doctors and clinic staff:
- `POST /api/doctors/me/delete-account/request` → sends an e-mail code (purpose `ACCOUNT_DELETION`); 409 with
  `code: HAS_ACTIVE_OBLIGATIONS` and a `details` list when the doctor has upcoming confirmed appointments or an active
  subscription, so the app can explain what to resolve first.
- `POST /api/doctors/me/delete-account/confirm { code }` → soft-deletes the account, revokes every session, keeps the
  clinical records the clinic is legally required to keep, anonymises the doctor in them. Idempotent.
- `GET /api/doctors/me/export` → 202 and an e-mail with a download link (JSON + the doctor's own documents); or a
  synchronous JSON if small. Rate-limited to once per day.
Both for `DOCTOR` and `CLINIC_STAFF` principals (product to confirm staff may self-delete).

**App until then:** the Privacy & data screen is built with the data summary and the two rows; "Request export" and
"Delete account" open a sheet that explains the request goes to support (`support@shifa.uz` mail intent). No fake
deletion.

## BE-03 Registration e-mail code actually verified

**Needed by:** flows `01/09`, `01/08`; ticket AUTH-06. Spec bug 4 ("UNVERIFIED" there; **confirmed** on 2026-11-04:
`RegisterRequest` has `firstName, lastName, phone, email, password, key, timeZone` and no `emailOtp`; the controller
never calls `emailOtpService.verify` on the register path).

**Now:** `POST /auth/send-email-otp {email, purpose: REGISTRATION}` sends a code; the Flutter dialog collects it and
throws it away; `register` creates the account for any e-mail.

**Change:** add optional `emailOtp` to `RegisterRequest`. When present, verify it with the `REGISTRATION` purpose and
reject with 400 `code: EMAIL_OTP_INVALID` / `EMAIL_OTP_EXPIRED`. After a date the user chooses, make it required
(the Flutter build never sends it, so keep it optional until the Flutter app is retired at REL-05).

**App until then:** per decision D-04 the code dialog is not shown; registration goes straight from step 2 to the
shell. When BE-03 ships, AUTH-06 adds the dialog (`01/09`) and sends `emailOtp`.

## BE-04 Force-update and maintenance in `/public/config`

**Needed by:** flow `01/01` §3 (force-update frame, maintenance frame, soft "update available" banner); ticket
AUTH-01.

**Now:** `GET /api/public/config` returns `{googleMapsApiKey, transcriptionFeedbackEnabled}` (`ConfigController.kt`),
both as strings. A breaking backend change cannot be communicated to installed apps.

**Change:** add, keeping the two existing keys:
```json
{
  "doctorApp": {
    "minVersion": { "android": "1.0.1", "ios": "1.0.1" },
    "latestVersion": { "android": "1.1.3", "ios": "1.1.3" },
    "storeUrl": { "android": "https://play.google.com/store/apps/details?id=com.shifa.doc", "ios": "https://apps.apple.com/app/id<ID>" },
    "maintenance": { "active": false, "message": { "en": "", "uz": "", "ru": "" }, "until": null }
  }
}
```
Served from the admin config table (`/api/admin/config/{key}` already exists), cached 60 s. The same block for
`patientApp` would serve the patient app.

**App until then:** the splash parses the block leniently; absent block = no gate. Both frames are built and testable
from the Debug menu.

## BE-05 Per-device push tokens

**Needed by:** flow `07/10`; ticket PUSH-01. Same as the patient app's item 2.

**Now:** `PUT /api/doctors/me/fcm-token {fcmToken}` stores one token per account; a second device or a logout on one
device silences the other. During the migration, signing in to the KMP app silences the Flutter app on the same account.

**Change:** `POST /api/doctors/me/fcm-tokens {token, platform}` (add, idempotent), `DELETE /api/doctors/me/fcm-tokens/{token}`
(logout), fan-out to every token, drop tokens FCM reports as `UNREGISTERED`. Keep the `PUT` for the Flutter build.

**App until then:** single token as today.

## BE-06 Pushes in the doctor's language, structured `data` payload

**Needed by:** flows `07/10`, `01/20`, `02/03`; tickets PUSH-02, PUSH-03, NOTIF-01. Same as the patient app's item 1.

**Now:** push `notification.title/body` are English; the Flutter client reverse-parses English copy for the inbox
(`NotificationDto` now carries `patientName`, `appointmentStartAt` and every linked id, which solves the inbox; the
push `data` block is **unverified**, see C-7).

**Change:** option A (recommended): render `title`/`body` in the doctor's `language` (`PUT /doctors/me/language`
already syncs it). Option B: data-only pushes with `mutable-content`. In both: `data` carries `type`,
`notificationId`, and the linked ids (`appointmentId`, `patientId`, `documentId`, `taskId`, `treatmentPlanId`,
`patientFormId`, `documentAccessRequestId`) plus `appointmentStartAt` and `patientName`, exactly as `NotificationDto`
(`notifications/api/dto/NotificationDto.kt`). Also add `conversationId` (chat) and `carePartnershipId` (partnership) to
both `NotificationDto` and the push `data`: neither exists today, so those two pushes can only land on the list screens.
Publish the full `type` list.

**App until then:** foreground pushes are re-rendered from `data` in the app language; background pushes show the
backend text; the router falls back to the Notifications tab when an id is missing.

## BE-07 Intelligence tool hints and card fields as codes

**Needed by:** flows `03/07`–`03/16`; tickets INTEL-05, INTEL-06, INTEL-10. Spec `15` §4 rows 10–11.

**Now:** the SSE stream carries English sentences for confirmations ("I found 3 appointments… say yes to cancel") and
English words in card fields ("Next week", "Tue 24 Oct", "Confirmed"). The Flutter client regex-matches about ten
sentences to localise them; any wording change silently breaks `uz`/`ru`.

**Change:** in the tool events, add `hint: {code, params}` next to the sentence (`CANCEL_PREVIEW {count, names[],
skipped}`, `ASK_WHICH_PATIENT {candidates[]}`, `BOOKING_CONFIRM_REQUIRED {workflowId}` …) and send card fields as
ISO dates/instants and enums (`status: CONFIRMED`, `period: {from, to}`); keep the sentence for old clients.

**App until then:** INTEL-10's card-text localiser (ported golden test) as the stop-gap; free AI text is shown as sent.

## BE-08 Standardised SSE errors

**Needed by:** flow `03` README §4; tickets AI-01, AI-05. Same as the patient app's item 11.

**Now:** `/ai/morning/stream` and `/ai/stream` already emit `event: error` with a `{code, message}` payload
(`ai/api/DoctorAiController.kt:133`, `:267`), but the codes are the AI controller's own, there is no `requestId`, and
rejections before the stream opens are not guaranteed to use the standard API body. The Flutter client bypasses the
shared client, so a 401 does not log out.

**Change:** keep `event: error`; use `ErrorCode` names plus AI-specific codes (`AI_QUOTA_EXCEEDED`, `AI_UPSTREAM`,
`AI_TIER_REQUIRED`) in `code`, add `requestId`, publish the list, and send the normal 401/403/429 API body before the
stream starts.

**App until then:** stream through the shared client (401 logs out), map 403 to "requires Pro", 429 to the daily-limit
banner, anything else to a generic retry.

## BE-09 `isVideo` on calendar entries

**Needed by:** flows `04/02`, `04/05`, `02/02`; tickets CAL-1, HOME-01. Spec `15` §4 row 4 (half done).

**Now:** `AppointmentDto` has `isVideoConsultation` (done); the calendar `EntryDto` has `location`, `locationId`,
`locationLabel` but no kind flag, so a day list still has to guess from the word "video".

**Change:** add `isVideo: Boolean` to `EntryDto` (and `consultationType` if a third kind ever exists).

**App until then:** one mapper function `EntryDto.isVideoFallback()` = `locationId == null && location contains
"video"` (case-insensitive, three languages), marked `ponytail:`; the appointment detail uses the real flag.

## BE-10 Cancel reason; mark no-show

**Needed by:** flows `04/08`, `05/03`; tickets CAL-6, APPT-01. `Appointment.Status` already contains `NO_SHOW`; nothing
sets it from the doctor side.

**Now:** `DELETE /api/appointments/{id}` takes no body; the patient gets a generic cancellation push. No endpoint sets
`NO_SHOW`.

**Change:**
- `POST /api/appointments/{id}/cancel { reasonCode: DOCTOR_UNAVAILABLE | PATIENT_REQUEST | RESCHEDULED | OTHER, note? }`
  (keep `DELETE` as an alias with no reason), stored and included in the patient's notification.
- `POST /api/appointments/{id}/no-show` → `CONFIRMED | IN_PROGRESS → NO_SHOW`, allowed from slot end until 24 h after;
  409 otherwise; emits `APPOINTMENT_NO_SHOW` to the patient.

**App until then:** the cancel dialog has no reason chips; the no-show action is hidden; both are built behind a
`DemoBackend` handler.

## BE-11 `from/to` on finance payments and records

**Needed by:** flows `09/09`, `09/10`; ticket FIN-03. Spec `15` §4 row 2 (the ledger has `from/to` already; clinic
patients have `q` + `pageable` already).

**Now:** `GET /clinics/{id}/finance/payments?page&size` and `GET .../records?status&page&size`. The Flutter app loads
page 0 (50 rows) and filters by month in memory, so older rows vanish (bug 7).

**Change:** add `from` and `to` (clinic-time-zone dates, inclusive) to both, same semantics as the ledger.

**App until then:** the month filter walks pages until the first row older than the month, capped at 20 pages
(`ponytail:` comment names the cap); the "All time" view is paged normally.

## BE-12 Transactional treatment-plan compose

**Needed by:** flow `09/15`; ticket TP-04. Spec `15` §4 row 6.

**Now:** creating a plan is 6–9 calls (`POST /treatment-plans`, `/lines`, `/link-appointments` or `/book-slots`,
`POST /finance/installment-plans`, `POST /treatment-plans/{id}/payments`, `PATCH /status`), none atomic; a failure
mid-way leaves a half plan, and an installment-sum mismatch is skipped silently while the plan goes `ACTIVE` (bug 8).

**Change:** `POST /api/treatment-plans/compose` taking the whole wizard payload (basics, care team, lines with optional
teeth, slots to book, installment schedule, initial payment, target status), validating that installments sum to the
outstanding amount (400 `INSTALLMENTS_MISMATCH`), and creating everything in one transaction. Returns the summary.

**App until then:** a resumable `SaveStep` machine that retries the failed step and never activates a plan whose
installments do not add up.

## BE-13 Idempotency keys on payments and visit charges

**Needed by:** flows `05/09`, `09/18`, `09/08`, `09/11`; tickets DENTAL-03, APPT-07, FIN-04, FIN-05.

**Now:** `POST /treatment-plans/{id}/payments`, `POST /clinics/{id}/finance/payments` and
`POST /clinics/{id}/appointments/{apptId}/visit-charges` create a row per call, so a retry after a timeout or an app
kill records the payment or the charges twice. `/installment-items/{id}/mark-paid` already refuses a second call with
400 "Already paid" (`clinics/api/ClinicFinanceController.kt:916`), which is safe but makes a legitimate retry look like
a failure.

**Change:** honour an `Idempotency-Key` header (UUID, 24 h window, per account) on those four endpoints: the same key
returns the first response. Standard Spring filter + a small table.

**App until then:** the app generates the key per action and sends it anyway (harmless today); the end-visit
orchestrator re-reads the plan before retrying and skips a payment that already appears with the same amount and
minute, marked `ponytail:`.

## BE-14 Chat cursor and push-driven refresh

**Needed by:** flows `07/01`, `07/02`, `07/10`; tickets CHAT-02, CHAT-03. Spec `15` §4 row 3.

**Now:** `GET /messages/conversations/{id}` returns every message; the list and the open thread are refetched every 5 s.

**Change:** `?after=<messageId>` (or `since=<instant>`) and `?limit` on the conversation, `?since` on the list, and a
`CHAT_MESSAGE` data push for every incoming message (already a notification type) so the app refreshes on push
instead of polling.

**App until then:** 5 s polling only while the thread is in the foreground, 10 s for the unread badge, both paused in
background.

## BE-15 Reverse-geocoding proxy; no Google key in clients

**Needed by:** flows `08/03`, `08/04`, `04/13`; tickets PROF-07, LOC-2. Spec `15` §4 row 7; bug 11.

**Now:** `/api/public/config` ships `googleMapsApiKey` to every unauthenticated caller; the Flutter app calls Google
Geocoding directly and logs the key.

**Change:** `GET /api/geocode/reverse?lat&lng&lang` and `GET /api/geocode/search?q&lang` (authenticated, rate-limited,
cached) returning the structured address fields the profile uses (`locationCountry, locationRegion, locationCity,
locationDistrict, locationPostalCode, locationStreetAddress`). Remove `googleMapsApiKey` from the public config once
the Flutter web build no longer needs it.

**App until then:** address fields are typed; coordinates come from the native map pin (when LOC-2 lands) or stay
empty; no Google call from the app.

## BE-16 Doctor-facing legal documents and own contract

**Needed by:** flows `08/13`, `08/12`; tickets SET-01, LEGAL-01, LEGAL-02.

**Now:** nothing doctor-facing. The early-partner contract is issued by `POST /admin/doctors/{id}/early-partner-contract/issue`
and never readable by the doctor.

**Change:** `GET /api/public/legal?lang` → `{terms: url, privacy: url, licences?: url, version, updatedAt}`;
`GET /api/doctors/me/contract` → 404 or `{issuedAt, status, pdfUrl}` (signed URL, short TTL).

**App until then:** About links to the public `shifa.uz` pages; the contract row is hidden.

## BE-17 Notification preferences

**Needed by:** flow `08/10` (door to Notifications), `07/09`; ticket SET-01, PUSH-02. Product decision first: which
categories can a doctor mute (chat, bookings, payments, tasks, AI results, partnerships)?

**Change (when decided):** `GET/PUT /api/doctors/me/notification-preferences { push: {CHAT: true, …}, email: {…} }`
honoured by the sender.

**App until then:** the Settings row opens the OS notification settings.

## BE-18 PREMIUM checkout; Stripe Connect onboarding state

**Needed by:** flow `08/09`; ticket SUB-02.

**Now:** `POST /doctor/subscription/checkout {planCode, gateway: STRIPE}` is used for PRO; whether a PREMIUM
`planCode` exists is unverified (`GET /doctor/subscription/plans` now lists plans, so the app can read it). `BillingDto`
has `stripeConnectAccountId` only; "onboarding incomplete" cannot be shown.

**Change:** confirm the plan codes in `/doctor/subscription/plans` include PREMIUM with prices and the `start`/
`checkout` split per gateway; add `stripeConnectStatus: NOT_CONNECTED | ONBOARDING | ACTIVE | RESTRICTED` to
`BillingDto` from Stripe's `charges_enabled`/`details_submitted`.

**App until then:** plan cards from `/plans`; a plan without a checkout path shows "Contact support"; Connect shows
"Connected" when the id is present and "Resume onboarding" otherwise.

## BE-19 `from/to` on analytics overview, types, engagement

**Needed by:** flows `02/05`, `02/06`, `08/14`; tickets ANALYTICS-02, REPORT-01. Only `appointments-trend?days` and
`sms-usage?from&to` take a period.

**Change:** `from`/`to` on `overview`, `consultation-types`, `engagement`, defaulting to today / last 30 days as now.

**App until then:** KPI cards are labelled "Today"; the period picker drives the trend and SMS usage only.

## BE-20 `followUpOf` on booking

**Needed by:** flow `05/14`; follow-up booking after a visit.

**Change:** optional `followUpOf: appointmentId` on `BookReq`, returned on `AppointmentDto` and shown to the patient.

**App until then:** nothing is stored; the reason field is prefilled with "Follow-up".

## BE-21 Document delete/rename/recategorise; HEIC conversion

**Needed by:** flows `06/06` §13, `06/08` §8; ticket DOCS-04. Product to confirm doctors may delete their own uploads.

**Change:** `PATCH /patients/{id}/documents/{docId} {title?, category?}` and `DELETE` (own uploads, soft delete);
convert HEIC/HEIF to JPEG on upload so every client can preview.

**App until then:** append-only list; HEIC from other clients shows "can't preview, open externally".

## BE-22 `createdAt` on forms

**Needed by:** flow `06/09` (two forms on the same date). `PatientFormDto` has `date` and `formNumber` but no creation
instant.

**Change:** add `createdAt` (instant) and `updatedAt`.

**App until then:** rows show `formNumber` when present.

## BE-23 Installment `lastNotifiedAt`; ledger `doctorId` filter

**Needed by:** flows `09/11`, `09/12`; tickets FIN-04, FIN-05.

**Change:** `lastNotifiedAt` on installment items (set by `/notify`), `doctorId` query on `/finance/appointment-ledger`.

**App until then:** no reminder cooldown; earnings rows do not drill into the ledger.

## BE-24 Clinic self-service and line-level plan edits

**Needed by:** flows `09/19`, `09/20`, `09/17`. Product decision first (self-serve clinic owners vs admin-managed).

**Change (when decided):** `POST /clinics/{id}/invitations/{invId}/resend`; `PATCH /clinics/{id}` (owner),
`POST /clinics/{id}/leave`, `DELETE /clinics/{id}` (owner, soft), `POST /clinics` (self-serve); `PATCH
/treatment-plans/{id}/lines/{lineId}` and `DELETE` for quantity/price edits.

**App until then:** Clinic settings is read-only with the sentence "changes go through your clinic administrator";
plan edits are limited to header fields (`PATCH /treatment-plans/{id}`) and `lines/append`.

## BE-25 Task thresholds; template CRUD

**Needed by:** flows `07/15` §13, `07/14`. Product decision first.

**Change (when decided):** `minValue`/`maxValue` on numeric tasks with a `TASK_THRESHOLD_EXCEEDED` notification;
`POST/PUT/DELETE /tasks/templates`.

**App until then:** read-only templates; no threshold alerts.

## BE-26 Pre-verify the forgot-password code

**Needed by:** flow `01/04` §12 (reset mode). The code is only checked together with the new password.

**Change:** `POST /auth/verify-forgot-password-otp {email, code}` → 200/400 without consuming the code.

**App until then:** the reset screen's error says the code was wrong and offers "Back to code".

## BE-27 Rolling validity; bulk shift on timezone change

**Needed by:** flows `04/10` §13, `08/06` §3.

**Change (if product wants it):** `validUntil: null` meaning "rolling 60 days"; `POST /schedule/rules/shift-timezone
{from, to}` to keep local times after a practice-timezone change.

**App until then:** explicit validity end; the timezone warning states that availability does not move.

## BE-28 Coded `/video/token` errors; explicit join window

**Needed by:** flows `05/10`, `05/01`; ticket VIDEO-01.

**Now:** the token endpoint rejects joins outside −5 min … end +15 min and unpaid visits with English messages; the
client reproduces the window by hand (and the Flutter client used a different one).

**Change:** `code: VIDEO_TOO_EARLY | VIDEO_TOO_LATE | VIDEO_PAYMENT_REQUIRED` with `joinWindow: {from, to}` in the
error body, and `joinWindow` on `AppointmentDto` for video visits.

**App until then:** the window is computed in the app with the backend's constants; errors map by message.

## BE-29 `register` must not re-key an existing doctor account

**Needed by:** flow `01/07` §8; ticket AUTH-06. Found 2026-11-04 reading `AuthController.register`.

**Now:** when the e-mail (or phone) already belongs to a user, `register` adds the DOCTOR role if missing and then
**always** sets `passwordHash` to the new password, also when the user is already a doctor. Anyone holding an unused
doctor invitation key without `emailSentTo` can therefore take over an existing doctor's account by registering
with that doctor's address. The invitation key is consumed, so the doctor only notices when the old password fails.

Also, `check-existing-doctor` matches the e-mail case-sensitively (`users.findByEmail`) while `register` matches it
case-insensitively (`findByEmailIgnoreCase`), and contact updates keep the casing typed. A doctor stored as
`Doctor@example.uz` is therefore "not found" for `doctor@example.uz` but merged (and re-keyed) by `register`.

`register-clinic-staff` does the same for an existing user (it adds CLINIC_STAFF and sets the new password); when the
invitation has no `emailSentTo` (C-4), any address can be typed. And a locked address proves nothing: the clinic
invitation endpoint (`ClinicInvitationController`) returns the key to the inviter, who can register with it and set
the password of whoever owns the invited address.

**Change:** if the existing user already has the DOCTOR role, reject with 409 `code: DOCTOR_EXISTS` before touching the
password; and make `check-existing-doctor` use `findByEmailIgnoreCase` like `register`. In general, adding a role to an
**existing** account must prove ownership (the account's password, or an e-mail code sent to it: BE-03), never just a
key; the registration endpoints should only create new accounts. For an existing patient, keep today's merge (it is the flow's "existing patient" branch) but require the
patient's current password or the registration e-mail code (BE-03), since a doctor key must not reset a patient's
password either.

**App until then:** step 1 calls `POST /auth/check-existing-doctor {email}` and stops with "A doctor account with this
email already exists. Sign in instead." under the e-mail field. This only protects honest users; the endpoint is
still open to direct calls.

---

## Confirm with the backend (no change expected, answer recorded in `docs/implemented/README.md`)

| ID | Question | Flow / ticket |
|---|---|---|
| C-1 | Does clinic membership authorise `PUT /appointments/{id}/change`, `DELETE /appointments/{id}` and `DELETE /schedule/blocks/{id}?doctorId` for receptionists and colleagues? Which roles? | `04` README §4.3, `04/08`, `04/09`; CAL-10 |
| C-2 | Server rules for clinic catalogue edits, invitations, plan status changes, revenue-share edits per membership role (`OWNER, CLINIC_ADMIN, DOCTOR, RECEPTIONIST, STAFF, NURSE`)? | `09` README §4; CLINIC-05/07/08, TP-02 |
| C-3 | What sets `IN_PROGRESS` (video token issued? first notes save?) | `05` README §2; APPT-01 |
| C-4 | Is `emailSentTo` always present on `verify-key` for receptionist keys? | `01/10`; AUTH-08 |
| C-5 | May a member doctor see other doctors' earnings and shares? | `09/12`; FIN-06 |
| C-6 | How are `GET /tasks/templates` derived (from past tasks? curated)? | `07/14`; TASK-05 |
| C-7 | Full list of push `data` fields and of notification `type` values production sends (doc `07` §2.3 lists 18) | `07/08`, `07/10`; NOTIF-01, PUSH-03 |
| C-8 | After a care-partner accepts, does the patient appear in the partner's directory automatically? | `06/14`; CARE-03 |
| C-9 | A consecutive-slot chain across a DST switch: does `/schedule/book` accept ±60 min? | `04` README §4.2; CAL-7 |
| C-10 | `CLINIC_STAFF` JWT role ↔ membership role mapping (`STAFF` vs `RECEPTIONIST`), and does staff ever get `/doctors/me`? | `01/18`, `09` README §4; SHELL-02 |

## Already satisfied since the spec was written (2026-10-24)

Checked on 2026-11-04 against `openapi.json` and the backend source; do not request again.

- Error bodies carry `code` and `requestId`; every rejected or missing token on a protected route is 401 (`doctor-app.md` §2, §4).
- Paged siblings: `GET /notifications/page`, `/patients/{id}/documents/page`, `/patients/{id}/forms/page`.
- `NotificationDto` has `patientName`, `appointmentStartAt`, `appointmentId`, `patientId`, `documentId`, `documentTitle`, `taskId`, `treatmentPlanId`, `documentAccessRequestId/Status`, `requestingDoctorName`, `patientFormId` (spec `15` §4 row 5, inbox side).
- `AppointmentDto.isVideoConsultation`, `paymentStatus`, `signatureRequested`, `alreadySigned`, `documentIds`, `canCancel`, `canReschedule` (row 4, appointment side).
- `PATCH /doctors/me/profile` applies `firstName`, `lastName`, `dob` (row 8; the Flutter bug was client-side).
- `PatientDocumentDto.uploadedByPatient`, `creatorType`, `appointmentId`, `canView`.
- Plan ticks and chair-side payments outside End: `POST /clinics/{id}/appointments/{apptId}/visit-charges`, `POST /treatment-plans/{id}/payments`; `PATCH /treatment-plans/{id}` for header edits; `GET /finance/installment-plans/by-treatment-plan/{id}`.
- Subscription renewal date and plan list: `GET /doctor/subscription/me`, `/plans`.
- `DELETE /patients/{id}/forms/{formId}`, `GET /schedule/blocks?day`, `PUT /schedule/date-specific/{id}`, `GET /calendar?locationId`.
- Development backend `https://api-dev.shifa.uz` with seed accounts (patient app item 3).
- Rate limits answer 429 `RATE_LIMITED`.

## BE-30 Remove the profile photo

**Needed by:** flow `08/02` §3 (photo sheet: "Remove photo"); ticket PROF-03.

**Now:** `POST /doctors/me/photo` replaces the photo, but nothing removes it: `PATCH /doctors/me/profile` would run an
empty `avatarUrl` through the URL signer.

**Change:** `DELETE /doctors/me/photo` → 204, clearing `avatarUrl` (and the stored file).

**App until then:** the photo sheet offers Take photo and Choose from gallery only.


## BE-31 A new photo needs a new URL

**Needed by:** flow `08/02` §3 (the new photo shows at once everywhere); ticket PROF-03.

**Now:** `POST /doctors/me/photo` stores every upload under the same key (`doctors/<name>-<id>.<ext>`), so the
`photoUrl` it returns, and every later `GET /doctors/me`, is the URL of the previous picture. Image caches keep the old
picture (the app's for the rest of the run, and patients' apps too).

**Change:** put a version in the key or the URL (a hash of the content, or `?v=<updatedAt>` added by `PublicUrls`).

**App until then:** the upload answer gets `?v=<time>` on the device, so the doctor sees the new picture right after
uploading; a later profile refresh brings back the plain URL, and the cache may show the old picture until the app
restarts.

## BE-32 Tell a wrong current password apart from a weak new one

**Needed by:** flow `08/11` §8 (wrong current password shown under that field); ticket PROF-04.

**Now:** `POST /auth/reset-password` answers 400 with the generic code `BAD_REQUEST` both when the current password is
wrong and when the new one fails `PasswordPolicy`; only the English `message` differs, and the app never shows or
parses server text.

**Change:** a distinct `code`, e.g. `CURRENT_PASSWORD_INCORRECT` and `PASSWORD_POLICY`.

**App until then:** the form checks the same policy before sending, so a 400 is read as a wrong current password
(field cleared, inline error). If the two policies ever drift, that message would be wrong.

## BE-33 Search the booking patient list

**Needed by:** flow `04/06` §8 (500 patients: server search with a 300 ms debounce); ticket CAL-7.

**Now:** `GET /patients/for-assignment` takes only `page`, `size`, `sort` (`sort=fullName,asc` works and is already
the default order; `name,asc` answers 500); there is no name or id filter, so the app reads every page before the
picker can search.

**Change:** a `q` parameter matching name or id (the directory's search may already have the query).

**App until then:** reads the pages (500 each, at most 20) once per booking form and filters locally.

## BE-34 Tell why a location can't be deleted

**Needed by:** flow `04/13` §8 (deleting the primary, the last, or a location with rules or appointments); ticket LOC-1.

**Now:** `DELETE /doctors/me/locations/{id}` answers 409 for three different reasons (the only location, schedule rules
at it, upcoming appointments at it); only the English `message` differs, and the app never shows server text.

**Change:** a `code` per reason, e.g. `LOCATION_LAST`, `LOCATION_HAS_RULES`, `LOCATION_HAS_APPOINTMENTS` (with the
appointment count).

**App until then:** one message names all three: "This location can't be deleted: it's your only one, or it still
has working hours or upcoming appointments."
