# 16 — Implementation Plan (milestones and tickets, in build order)

The order follows what a doctor needs first and what every later screen depends on:
**Foundation → Auth → Shell + Profile → Calendar → Patients → Appointments (the clinical core) → Home → Chat →
Notifications/Push → Tasks → Shifa AI → Clinic workspace → Care partnerships / Reports → Release.**
Each milestone ends in something the user can test on a phone.

Full ticket texts (Build / Done when / Depends / Fix, with `file:line` evidence) live at the end of each feature
doc; this file is the index and the sequencing. Where this file and a feature doc disagree on IDs, this file wins
(see "ID mapping" at the bottom).

## How to read a ticket row

`ID · Title [size] · depends · spec` — size **S** ≈ ½–1 day, **M** ≈ 2–3 days, **L** ≈ 4–6 days for one developer
with AI assistance. "spec" points at the doc and section with the detail; "Figma" is the node ID column to fill in
when the design file is shared.

### Definition of done (every ticket)

- Builds and runs on **Android and iOS** (`assembleDebug`, `linkDebugFrameworkIosSimulatorArm64`).
- Every string in `composeResources` for `en`, `uz`, `ru` (plus the `uz-Cyrl` transliteration path); no fallbacks.
- Loading (skeleton), error (inline banner + retry) and empty states wherever the spec lists them.
- `XxxViewModelTest` + `XxxScreenshotTest` for the screen; repository tests with Ktor `MockEngine` fixtures from the
  feature doc's endpoint table; a `DemoBackend` handler for every new endpoint.
- Times shown in practice / clinic / patient time zone as the spec says; never device time for display.
- The "Fix" line respected; `docs/implemented/` updated in the same commit; spec doc corrected if reality differed.

### Prompt template for handing a ticket to Claude

```
Implement ticket CAL-7 from docs/kmp-spec/16-implementation-plan.md.
Read docs/kmp-spec/03-calendar-schedule.md §1.6, §1.10 and the CAL-7 ticket text at its end,
15-api-reference.md for the endpoints, 04-design-system-and-ui-kit.md and the Figma frames <node ids>.
Plan first, then data layer → ViewModel → screen → tests. Respect the "Fix" line. Where the spec says
UNVERIFIED, check <BASE_URL>/v3/api-docs or the Flutter file it cites (git show main:lib/...) before deciding.
```

---

## Milestone overview

| # | Milestone | Tickets | Size | Result you can demo |
|---|---|---|---|---|
| M0 | Foundation | CORE-01…09 | 9 · ≈ 16 d | Empty app: theme + components from Figma, strings, HTTP client, storage, demo backend, CI green |
| M1 | Launch & auth | AUTH-01…09, LOCK-01…02 | 11 · ≈ 14 d | Register with invite key, log in (password / email OTP), reset password, restore session, app lock |
| M2 | Shell, profile, settings | SHELL-01…05, PROF-01…08, SVC-01…02, SUB-01…02, SET-01…03 | 20 · ≈ 36 d | Tabs with badges, full profile, services & pricing, settings, language |
| M3 | Calendar & schedule | TZ-1…2, CAL-1…10, SCHED-1…2, LOC-1…2 | 16 · ≈ 42 d | See, book, change, cancel, block; set up weekly schedule and locations |
| M4 | Patients, documents, forms | PAT-01…07, DOCS-01…05, FORM-01…05, CLIN-01…02 | 19 · ≈ 44 d | Directory, patient detail, uploads/scan/viewer, 025-2 form with clinical engine |
| M5 | Appointments, scribe, dental, video | APPT-01…08, SCRIBE-01…02, DENTAL-01…04, VIDEO-01…03, PDF-01…02, AI-11, TP-06 | 21 · ≈ 50 d | Run an in-person or video visit end-to-end incl. AI scribe, dental chart, plan fulfilment, summary document |
| M6 | Home & analytics | HOME-01…05, ANALYTICS-01…03 | 8 · ≈ 16 d | Dashboard with next patient, timeline, attention centre, KPIs and charts |
| M7 | Chat | CHAT-01…07 | 7 · ≈ 18 d | Text, image, document and voice messages with unread badge |
| M8 | Notifications & push | NOTIF-01…04, PUSH-01…03 | 7 · ≈ 14 d | Inbox, FCM on both OSes, every notification type deep-links |
| M9 | Remote-care tasks | TASK-01…05 | 5 · ≈ 11 d | Create from template, track check-ins, cancel |
| M10 | Shifa AI & Intelligence | AI-01…10, INTEL-01…10 | 20 · ≈ 42 d | Streaming assistant with cards, voice session with TTS, patient briefing, visit facts |
| M11 | Clinic workspace, finance, treatment plans | CLINIC-01…09, FIN-01…07, TP-01…05 | 21 · ≈ 48 d | Multi-doctor clinic back-office on the phone incl. finance and treatment-plan wizard |
| M12 | Care partnerships, reports, legal | CARE-01…03, REPORT-01, LEGAL-01…02 | 6 · ≈ 9 d | Therapy-partner invites, reports tab, contract decision |
| M13 | Release | REL-01…05 | 5 · ≈ 8 d | Store builds replace the Flutter app; Flutter code removed |

**Total ≈ 175 tickets, ≈ 368 developer-days (≈ 74 weeks) for one developer**, roughly 9 months. Two developers
split as *clinical stream* (M3 → M4 → M5 → M6 → M10) and *communication/clinic stream* (M7 → M8 → M9 → M11 →
M12) after M2 bring it to about 5 months. The sizes come from the per-area analyses and were calibrated against the
patient-app rewrite; treat them as ±30 %.

Do **CORE-07 (native risk spikes)** during M1 in parallel: Daily, FCM/APNs, AAC recording + playback, PDF
rendering decision and native maps are the places most likely to force architecture changes.

Parked: **ADMIN-01…18** (doc 10) — the admin panel stays a web product; the tickets are ready if a CMP
desktop/web target is ever chosen.

---

## M0 — Foundation (≈ 16 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| CORE-01 | Project skeleton on `doc-app2.0`: copy the patient app's Gradle/catalog/ktlint/CI shapes, package `shifa.doctor`, ids `com.shifa.doc` / `uz.shifa.doc`, `CLAUDE.kmp.md` → `CLAUDE.md`, `docs/implemented/`, `docs/backend-improvements.md`, port `flutter_ci.yml` → `ci.yml` | M | — | CLAUDE.kmp.md; 01 §1 |
| CORE-02 | Design tokens from Figma → `ui/theme/` (`ShifaColors`, `Typography`, `Dimens`, `Theme.kt`), fonts, icon export pipeline, debug token gallery | M | CORE-01, **Figma file** | 04 §1–4, §6 |
| CORE-03 | Shared UI kit (`ui/widgets/`): buttons, input field (+ mic slot), OTP field, phone field, searchable dropdown, chips, badge, avatar, banner, skeletons, empty state, card, tray, pop-up, list item, tabs/segmented, nav bar, top bar, language toggle, date/time pickers — each with previews + screenshot tests | L | CORE-02 | 04 §5 |
| CORE-04 | Strings: convert `strings/*.json` → `values{,-uz,-ru}/strings.xml`, add the 41 keys missing everywhere + 92 `ru` / 10 `uz` gaps, `<plurals>` for the six Russian plural sites, `UzbekCyrillic` transliterator (+ golden tests), `ProvideAppLocale`, `StringsTest` | M | CORE-01 | strings/README; 01 §8 |
| CORE-05 | Ktor `httpClient` + `apiCall` + `ErrorMessages`: Bearer interceptor skipping public paths, 30 s timeout on all verbs, pre-flight JWT expiry, exact 401/403 rules, HTML-body detection, lenient error bodies, multipart + SSE helpers, `httpLogger` (redacted, debug only) | M | CORE-01 | 01 §3, §9; 15 §1 |
| CORE-06 | Storage & session: `secureSettings` (Keychain / EncryptedSharedPreferences) with the exact keys of 01 §4, plain settings, single `Jwt` decoder (`exp`, `role`), `SessionRepository` (`StateFlow<Session?>`, restore, logout clears only the listed keys) | S | CORE-05 | 01 §4, §5.1, §2.9 |
| CORE-07 | Native risk spikes (throwaway, both OSes): Daily room join, FCM/APNs token + push, AAC m4a record + play, PDF render (platform API) vs backend decision, native map pin. Write the choices into `CLAUDE.md` | L | CORE-01 | 05 §9; 07 §7; 08 §12 |
| CORE-08 | `DemoBackend` (`MockEngine`) + debug menu (demo data, fake pushes), `DemoBackendTest`; fixtures = endpoint tables of docs 01–09 | M | CORE-05 | CLAUDE.kmp.md "Debug builds" |
| CORE-09 | Cross-cutting utils: `PracticeClock`/`Dates.kt` (practice-TZ formatting, `getCurrentInstant`), `Money.format`, `PhoneE164`, `PasswordPolicy` (8–128, upper/lower/digit/special), `sanitizeErrorMessage` port, `expect` helpers for pickers/share/open-URL/clipboard | M | CORE-01 | 03 §4; 09 §8; 01 §11 |

Done when: both apps launch to a token gallery + component gallery that matches Figma; CI runs ktlint, unit tests
and Roborazzi on a PR.

---

## M1 — Launch & auth (≈ 14 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| AUTH-01 | Splash + session restore (skip the fixed 1.8 s when a valid token exists) | S | CORE-06 | 01 §1.4, §2.9 |
| AUTH-02 | Login (identifier + password ≥ 6, links to email OTP / forgot / create) | S | CORE-03, CORE-04, CORE-05 | 01 §2.1 |
| AUTH-03 | Email OTP login (6 digits, 60 s resend) | S | AUTH-02 | 01 §2.2 |
| AUTH-04 | Forgot password via email OTP → new password (full policy) → logged in | S | AUTH-03 | 01 §2.4 |
| AUTH-05 | Verify invitation key → doctor or receptionist branch | S | AUTH-02 | 01 §2.5 |
| AUTH-06 | Create account step 1 + existing-patient confirm + email OTP (send the code or drop the dialog: backend call) | M | AUTH-05 | 01 §2.6 |
| AUTH-07 | Create account step 2 (DOB, gender, address, clinic search, professions, timezone) + one-time profile extras PATCH | M | AUTH-06, PROF-05, PROF-06 (pickers) | 01 §2.6 |
| AUTH-08 | Receptionist registration (locked invite email); staff allowed on mobile (user's call) | S | AUTH-05 | 01 §2.7–2.8 |
| AUTH-09 | Logout + session invalidation (one dialog, one navigation, FCM token cleared, repositories reset) | S | CORE-06, CORE-08 | 01 §2.10 |
| LOCK-01 | App lock: salted-hash PIN 4–6 digits with explicit submit, biometrics (BiometricPrompt / LAContext), cold-start + resume ≥ 5 s, 5 attempts ⇒ logout, temporary-disable hook | M | CORE-06, AUTH-09 | 01 §7.1 |
| LOCK-02 | Inactivity timeout (30 d native, persisted ≤ every 5 min) + JWT-expiry enforcement on resume | S | CORE-06, AUTH-09 | 01 §7.2 |
| AUTH-10 | *(optional, not planned)* Firebase phone OTP login/reset — unreachable in Flutter; only on product request | L | AUTH-02 | 01 §2.3 |

Done when: a new doctor registers with a key, logs out, logs in again with password and with email OTP, resets a
password, and the app lock asks for a 6-digit PIN after backgrounding.

---

## M2 — Shell, profile, settings (≈ 36 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| SHELL-01 | Shell scaffold: bottom nav Home / Calendar / Patients / Chat / More, More sheet, tab back stacks + `BackHandler`, start tab from `settings.defaultStartTab`, role clamps, placeholders | L | CORE-03, CORE-04, AUTH-01 | 01 §6 |
| SHELL-02 | Clinic-staff 4-tab shell (Chat / Clinic / Notifications / Profile) | S | SHELL-01, AUTH-08 | 01 §6.1 |
| SHELL-03 | Badges & polling (chat unread 10 s foreground-only, notifications unread on resume/push, active tasks count) | S | SHELL-01 | 01 §6.3 |
| SHELL-04 | Subscription tier gating (`DoctorTier`, `DoctorFeature`, `FeatureGate`; wait for profile instead of failing open — user's call) | S | PROF-01 | 01 §5.3; 08 §5 |
| SHELL-05 | Shell surface toggle (dashboard ↔ Intelligence), resume refresh hooks | S | SHELL-01 | 01 §5.4, §6.6; 09 §1.2 |
| PROF-01 | Typed profile models + `ProfileRepository` (`/api/doctors/me`, `/api/me/profile`, all PATCHes) | M | CORE-05 | 08 §1.1, §10, §11 |
| PROF-02 | Profile screen: section list, phone push / tablet two-pane, clinic-staff variant | M | PROF-01, SHELL-01 | 08 §1.2–1.4 |
| PROF-03 | Personal information panel incl. **name/DOB persistence**, photo upload, profession + timezone pickers | M | PROF-02, PROF-05, PROF-06 | 08 §1.5 |
| PROF-04 | Contact, payment & invoicing, password panels | M | PROF-02 | 08 §1.6, §1.7, §1.11 |
| PROF-05 | Profession catalogue (138) + searchable picker + dental detection (+ `Implantologist`) | S | PROF-01 | 08 §4 |
| PROF-06 | Timezone picker (curated IANA list + search) | S | PROF-01 | 08 §1.5; 03 TZ-2 |
| PROF-07 | Location picker: address fields + coordinates first, native map behind `expect/actual`, geocoding via backend proxy, always persist lat/lng | L | PROF-03, CORE-07 | 08 §3 |
| PROF-08 | Extended profile (biography, service tags, certificates, telegram/instagram) with per-field debounce | M | PROF-02 | 08 §1.10 |
| SVC-01 | Services & pricing list + editor (status-checked writes, duplicate price check, clinic-managed read-only) | M | PROF-01, LOC-1 | 08 §2 |
| SVC-02 | Service groups editor | S | SVC-01 | 08 §2 |
| SUB-01 | Tier/feature gating source (shared with SHELL-04) | S | PROF-01 | 08 §5 |
| SUB-02 | Stripe Connect onboarding + subscription checkout in Custom Tab / SFSafariVC with return refresh | M | PROF-04, SUB-01 | 08 §1.7, §5.3 |
| SET-01 | Settings panel (country, language, start tab, 2FA, encrypted docs) with queued full-body PATCH | M | PROF-01, SUB-01 | 08 §1.8 |
| SET-02 | Language settings screen + mini toggle + region default + `PUT /doctors/me/language` sync | S | CORE-04 | 01 §8; 08 §6.1 |
| SET-03 | App-lock settings section (with the 7 missing strings) | S | LOCK-01, SET-01 | 08 §6.2 |

Done when: a doctor edits every profile section and sees it persist after restart; the staff user lands in the
4-tab shell; language switches at runtime.

---

## M3 — Calendar & schedule (≈ 42 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| TZ-1 | `PracticeClock`: practice-TZ today/now/parse/format, device-mismatch flag, DST tests | S | CORE-09, PROF-01 | 03 §4 |
| TZ-2 | Timezone detection + curated list (shared with PROF-06) | S | TZ-1 | 03 §4.1 |
| CAL-1 | Calendar models (sealed `CalendarEntry`) + day repository with 60 s throttle, month batch, `previewDay(doctorId)` | M | TZ-1, CORE-05 | 03 §5–7 |
| CAL-2 | Today / next-appointment selectors + `AppointmentChangeBus` (one refresh per mutation) | S | CAL-1 | 03 §5 |
| CAL-3 | Month picker with occupancy shading (custom Compose grid) | M | CAL-1 | 03 §1.2–1.3 |
| CAL-4 | Day entries list + calendar screen (phone layout), filters, TZ-mismatch banner, lifecycle reloads | M | CAL-3 | 03 §1.1, §1.3, §1.5, §1.11 |
| CAL-5 | N-day time grid (tablet / landscape): hour gutter, overlap columns, free-slot tap layer, now-line | L | CAL-4 | 03 §1.3, §1.9 |
| CAL-6 | Slot details panel: appointment view, CTA state machine (video window), cancel, change slot, adjust duration, payment reminder | L | CAL-4, CAL-2 | 03 §1.7 |
| CAL-7 | Booking a free slot: consecutive-slot chain (ported tests), patient picker, place, start/end, reason | L | CAL-6, PAT-02 | 03 §1.6, §1.10 |
| CAL-8 | Blocks: create dialog (day / range / dates, overlap preview), blocked details, unblock | M | CAL-6 | 03 §1.8 |
| CAL-9 | Deep-link intents: go-to-appointment, quick-book | S | CAL-4, CAL-7 | 03 §1.2 |
| CAL-10 | Staff calendars (multi-doctor chips, per-doctor caches) + clinic doctor schedule screen (book on behalf) | L | CAL-5, CAL-7, CAL-8, CLINIC-05 | 03 §1.12; 09 §5 |
| SCHED-1 | Schedule models, repository, validators (overlap, validity placement) | S | CORE-05 | 03 §2 |
| SCHED-2 | Schedule setup screen (location selector, validity dates, expansions, 7 day cards, copy-day) | L | SCHED-1, LOC-1, TZ-1 | 03 §2.2–2.3 |
| LOC-1 | Doctor locations repository + list/edit (no map) | M | CORE-05 | 03 §3 |
| LOC-2 | Map picker & geocoding for locations (shares PROF-07's components) | L | LOC-1, PROF-07 | 03 §3 |

Done when: the doctor sets up a weekly schedule for a location, sees free slots, books a 60-minute visit over two
30-minute slots, moves it, blocks an afternoon, and everything shows in Asia/Tashkent while the phone is in another
zone.

---

## M4 — Patients, documents, forms (≈ 44 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| PAT-01 | Patient models & lenient codecs (`displayId`, age with injected clock, status enum) | S | CORE-05 | 06 §8 |
| PAT-02 | Patients repository (typed errors, page walking, `clinicId`, cache + refresh TTL) | M | PAT-01 | 06 §6–7 |
| PAT-03 | Patients directory (search, status filter, tabs, sort, 10/page pager, persisted favourites) | M | PAT-02, CORE-03 | 06 §1.2–1.5 |
| PAT-04 | Create patient sheet (one implementation, superset of fields) | S | PAT-02 | 06 §1.6 |
| PAT-05 | Patient detail: header, overview, quick actions, SMS reminder card, patient-app access card, tab scaffold | M | PAT-03, PROF-01 | 06 §2.1–2.3, §1.7 |
| PAT-06 | Medical Info tab (per-field edits with rollback, prophylaxis in clinic scope) | M | PAT-05 | 06 §2.4 |
| PAT-07 | Appointments & Activity tabs (practice-TZ times, booking dialog from CAL-7) | S | PAT-05, CAL-7 | 06 §2.5, §2.8 |
| DOCS-01 | Document models, 27 categories (incl. `FORM_025_2`), repository (list, download with Content-Disposition + MIME sniff, request-access) | S | CORE-05 | 06 §4.1, §4.3–4.6 |
| DOCS-02 | Platform pickers & image utils (`pickPdf`, `captureImage`, `compressJpeg`, `imagesToPdf`, `shareOrSaveFile`) | M | CORE-09 | 06 §9 |
| DOCS-03 | Upload flow: PDF / photo / multi-page scan, title + category sheet, progress, size guard | M | DOCS-01, DOCS-02 | 06 §4.2 |
| DOCS-04 | Documents tab: rows (linked-form titles, lock + byline, shared icon), search, request access, deep-link auto-open | M | PAT-05, DOCS-01, DOCS-05, FORM-02 | 06 §2.6, §4.6 |
| DOCS-05 | In-app viewer (zoomable image; PDF via `PdfRenderer` / `PDFKit`) | M | DOCS-01, DOCS-02 | 06 §2.10 |
| FORM-01 | Form models + dental-chart codec (FDI, hist rows, legacy migration, prefill) with ported tests | M | PAT-01 | 06 §3.2–3.3 |
| FORM-02 | Forms repository (CRUD, request-signature, link-document, replace file, ICD-10 search) | S | FORM-01, DOCS-01 | 06 §7 |
| FORM-03 | 025-2 PDF strategy: backend render (preferred) or `expect/actual` renderer | L | FORM-01, CORE-07 | 06 §3.6 |
| FORM-04 | 025-2 editor screen (all sections, ICD chip, dental grid, follow-ups, signature, save pipeline, embedded mode) | L | FORM-02, FORM-03, DOCS-03, PAT-05, CLIN-02 | 06 §3.4–3.7 |
| FORM-05 | Forms tab + template picker (renamed from "Prescriptions") | S | FORM-04 | 06 §2.7, §3.1 |
| CLIN-01 | Clinical engine API + models | S | CORE-05 | 02 §5.1–5.2 |
| CLIN-02 | Clinical engine panel (groups → diseases, chips, synthesize) embedded in the 025-2 form | L | CLIN-01 | 02 §5.3 |

Done when: create a patient, upload a scanned 3-page document, open it, fill and sign-request a 025-2 form, and see
the form's PDF in the documents list on both OSes.

---

## M5 — Appointments, scribe, dental, video (≈ 50 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| APPT-01 | Appointment domain model (`kind` enum, payment status kept) + typed route `appointment/{id}` | S | CAL-1 | 05 §1 |
| APPT-02 | Appointment detail + signature repository (10 s poll only while pending) | S | APPT-01 | 05 §2.2, §7 |
| AI-11 | Consultation notes & scribe API (notes, drafts, complete, scribe-status flow; ported tests) | S | CORE-05 | 02 §7.1 |
| APPT-03 | Consultation notes repository + autosave + `composeVisitDocumentationText` | M | APPT-01, AI-11 | 05 §2.2, §8 |
| APPT-04 | Consultation shell screen shared by in-person & video (header, mode menu, SOAP + notes, AI drafts, docs list + upload strip, sticky footer, unsaved guard) | L | APPT-01…03, DOCS-04 | 05 §2.1, §2.4 |
| APPT-05 | Before/after photos + document upload via pickers | M | APPT-04, DOCS-02 | 05 §2.2 |
| APPT-06 | Pre-visit briefing sheet (3 s poll, retry) | S | APPT-01 | 05 §7 |
| SCRIBE-01 | AI scribe recording + upload-recording (AAC m4a, PRO gate, 202/429) — also on video visits | M | APPT-04, AI-03 | 05 §2.2 |
| SCRIBE-02 | Scribe status polling + cancellable completion gate | S | SCRIBE-01, APPT-07 | 05 §2.3 |
| APPT-07 | End-appointment orchestrator (fulfil → payment → complete → document) with per-step errors and "completed, document pending" state | M | APPT-02, APPT-03, DENTAL-03, PDF-01 | 05 §2.3 |
| DENTAL-01 | FDI codec + chart composable (Canvas teeth, states, dentition toggle, localised captions) | M | CORE-03 | 05 §4.1–4.2 |
| DENTAL-02 | Dental visit documentation panel (free mode: services, per-tooth lines, discount, autosave, mixed currency handled) | M | DENTAL-01, APPT-04 | 05 §4.3 |
| DENTAL-03 | Treatment-plan link, fulfilment and session-payment finance card | M | DENTAL-01, TP-01 | 05 §4.3, §5 |
| DENTAL-04 | Dental plan editor + read-only plan chart (for the wizard and detail) | M | DENTAL-01 | 05 §4.4 |
| TP-06 | Fulfilment hooks (`PlanFulfillmentSection`) shared by appointment screens | S | TP-01, DENTAL-03 | 09 TP-06 |
| APPT-08 | 025-2 panel embedding (`save()`, `applyIcd()`) | S | APPT-04, FORM-04 | 05 §5 |
| VIDEO-01 | Daily token repository (no-401, localised join-window errors) + `expect class DailyCallClient` (Android SDK, Swift bridge) | M | APPT-01, CORE-07 | 05 §3.1 |
| VIDEO-02 | Video call screen (remote + PiP, controls, permissions, keep-screen-on, visit-facts slot, same documentation side) | L | APPT-04, VIDEO-01 | 05 §3.2 |
| VIDEO-03 | Waiting room: decision (default: not ported) | S | VIDEO-02 | 05 §3.3 |
| PDF-01 | Appointment summary document strategy (backend preferred; `expect/actual` fallback with `ru`) | M | APPT-07, CORE-07 | 05 §6, §9 |
| PDF-02 | PDF content/golden tests (fallback path only) | S | PDF-01 | 05 §6 |

Done when: start a visit from the calendar, dictate SOAP notes, record an AI scribe, attach a photo, document
teeth, request a signature, end the visit and find the summary document under the patient; join a video room on
both OSes.

---

## M6 — Home & analytics (≈ 16 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| HOME-01 | Home data layer: today / next-upcoming via one range query, classification in practice TZ, refresh cooldown | M | CAL-2, PROF-01 | 02 §1.4, §1.10 |
| HOME-02 | Home skeleton: header with badge, metric row, single-column layout, pull-to-refresh, skeletons | M | HOME-01, SHELL-01 | 02 §1.2–1.4 |
| HOME-03 | Next-patient hero, quick-action chips, today's timeline (Start ≤ 15 min before) | M | HOME-02, APPT-04, CAL-9 | 02 §1.4 |
| HOME-04 | Attention centre + activity feed | S | HOME-02, NOTIF-01, TASK-01 | 02 §1.5–1.6 |
| HOME-05 | Patient search overlay | S | HOME-02, PAT-03 | 02 §1.9 |
| ANALYTICS-01 | Analytics API, DTOs, date range (default 7 d, clamp 1–90) | S | CORE-05 | 02 §2.1–2.3 |
| ANALYTICS-02 | KPI cards, engagement, date-range toolbar, CSV export via share sheet | M | ANALYTICS-01, HOME-02 | 02 §1.7–1.8, §2.4 |
| ANALYTICS-03 | Trend line + visit-type donut (Compose Canvas, PREMIUM gate) | M | ANALYTICS-02 | 02 §2.4 |

Done when: Home shows the real next patient with a working Start button and the analytics card matches the Reports
numbers.

---

## M7 — Chat (≈ 18 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| CHAT-01 | Chat models, repository, multipart upload, URL normaliser for all attachment types | M | CORE-05 | 07 §1.1, §1.4–1.5, §5–6 |
| CHAT-02 | Conversation list (sort/unread filter, user search, start-or-select, foreground polling) | M | CHAT-01, SHELL-01 | 07 §1.1 |
| CHAT-03 | Conversation screen: text bubbles, optimistic send, mark-read on incoming, header → patient | M | CHAT-02 | 07 §1.2–1.3 |
| CHAT-04 | Image attachments (pick, compress > 500 KB, upload, bubble, zoom viewer) | M | CHAT-03, DOCS-02 | 07 §1.3–1.4 |
| CHAT-05 | Document attachments (pdf/doc/docx/jpg/png rule, open via system viewer) | S | CHAT-04 | 07 §1.3–1.4 |
| CHAT-06 | Voice messages: recorder bar + player bubble, single active player | L | CHAT-03, AI-03, AI-04 | 07 §1.3–1.4 |
| CHAT-07 | Unread badge flow + open-chat-with-patient entry points | S | CHAT-02, SHELL-03 | 07 §1.2, §1.6 |

---

## M8 — Notifications & push (≈ 14 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| NOTIF-01 | Notification model, repository, 18-type catalogue (label / icon / colour / filter / body / target) as pure testable mapping | S | CORE-05 | 07 §2.2–2.3 |
| NOTIF-02 | Inbox screen (filters, Today/Yesterday grouping in practice TZ, inline actions, pull-to-refresh) | M | NOTIF-01, SHELL-01 | 07 §2.1 |
| NOTIF-03 | Unread badge & cross-feature invalidation (no polling) | S | NOTIF-01, PUSH-02 | 07 §2.4 |
| NOTIF-04 | *(optional)* document-access approve/reject in the inbox — only if backend confirms the endpoints are live | S | NOTIF-02 | 07 §2.1 |
| PUSH-01 | FCM/APNs bridge + token registration (channel `shifa_doctor_channel`, Swift delegate, token upload/refresh/clear) | M | CORE-07, AUTH-09 | 07 §2.5 |
| PUSH-02 | Foreground handling: localised local notification from `data`, dedupe, data refresh | M | PUSH-01, NOTIF-01 | 07 §2.5 |
| PUSH-03 | Deep-link router (tap / background / terminated) for every type; pending buffer until the shell is ready | M | PUSH-01, CHAT-03, TASK-03, CAL-9, PAT-05 | 07 §2.3, §2.5 |

Done when: every row of the type catalogue, sent from the debug menu as a fake push, lands on the right screen from
all three app states on both OSes.

---

## M9 — Remote-care tasks (≈ 11 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| TASK-01 | Task models/enums, repository (typed errors), PREMIUM gate helper | S | CORE-05, SUB-01 | 07 §3.5, §5 |
| TASK-02 | Tasks list (summary cards, localised filters, cards, empty state) | M | TASK-01, SHELL-01 | 07 §3.1 |
| TASK-03 | Task details + check-ins (effective status in **patient TZ**) + cancel | M | TASK-02 | 07 §3.2 |
| TASK-04 | Create task form (even-spacing / custom times, dates, payload rules, template prefill) | L | TASK-02, PAT-02 | 07 §3.3 |
| TASK-05 | Select template screen | S | TASK-04 | 07 §3.4 |

---

## M10 — Shifa AI & Intelligence (≈ 42 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| AI-01 | AI HTTP client: SSE line parser (exact protocol), errors, speak, briefing, visit facts, draft confirm/discard; `conversationId` in stream body | M | CORE-05 | 02 §3.2, §8, §0 |
| AI-02 | `AiResponseText` markdown-subset renderer (ported tests) | S | CORE-03 | 02 §3.2 |
| AI-03 | Voice recorder `expect/actual` with amplitude (AAC 16 kHz mono 32 kbps) — shared with CHAT-06 / SCRIBE-01 | M | CORE-07 | 02 §3.5, §10 |
| AI-04 | Audio player `expect/actual` for TTS and voice bubbles | S | CORE-07 | 02 §3.4 |
| AI-05 | Transcription + feedback flag (`/api/ai/transcribe` + `recordingId`, 403 → PRO, 429) | S | AI-01, AI-03 | 02 §7.2, §0 |
| AI-06 | Speech-enabled text field + inline recorder bar (used by SOAP, forms, chat) | S | AI-03, AI-05, SUB-01 | 02 §7.3 |
| AI-07 | Shifa orb + voice orb panel (Canvas, ported intensity tests) | S | CORE-03 | 02 §3.10 |
| AI-08 | Voice session controller (VAD, dictation, TTS, barge-in, hands-free; no wake word) | L | AI-01, AI-03…05, AI-07 | 02 §3.3–3.5 |
| AI-09 | Patient briefing (visible on mobile as a sheet) | S | AI-01, AI-02 | 02 §6 |
| AI-10 | Visit facts panel + commit, patient visit timeline card | S | AI-01 | 02 §3.2 |
| INTEL-01 | Chat models, titles/wake helpers, `detectReplyLanguage`, session store (encrypted option, capped) | M | PROF-01 | 02 §4.2–4.3, §3.9, §0 |
| INTEL-02 | Intelligence screen shell: app bar, history sheet, rename/delete/export | M | INTEL-01, SHELL-05 | 02 §4.1 |
| INTEL-03 | Welcome screen (ported tests) | S | INTEL-02 | 02 §4.6–4.7 |
| INTEL-04 | Chat thread + composer + streaming (history tags, 75 s timeout, persistence) | L | AI-01, AI-02, INTEL-02 | 02 §3.3, §3.7, §4.4 |
| INTEL-05 | Read-only action cards (schedule, appointments, free slots, patient search, most visited, patterns, brief) | M | INTEL-04 | 02 §4.4 |
| INTEL-06 | Booking cards: server-workflow (read-only, backend books on "yes") + legacy local buttons | M | INTEL-04, INTEL-10, CAL-2 | 02 §0, §4.4 |
| INTEL-07 | Clinical cards: draft visit facts, documents + inline viewer, dental chart | L | INTEL-05, AI-10, DENTAL-01, DOCS-05 | 02 §4.4–4.5 |
| INTEL-08 | `open_appointment` card + auto-open (ported route tests; wire the missing card) | S | INTEL-05, APPT-04 | 02 §4.8 |
| INTEL-09 | Voice in Intelligence: mic, dictation, orb panel, hands-free loop, sign-off | M | AI-08, INTEL-04, INTEL-06 | 02 §3.4–3.5 |
| INTEL-10 | Card text localiser (status/date/range/cancellation hints; ported golden test) — stop-gap until backend sends codes | S | INTEL-05, CORE-04 | 02 §0 |

Note the ordering trick: AI-03 and AI-04 are built early (they unblock CHAT-06 and SCRIBE-01 in M5/M7); the rest of
M10 can run after M6.

---

## M11 — Clinic workspace, finance, treatment plans (≈ 48 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| CLINIC-01 | Membership model, repository, persisted selected clinic, sticky `hasClinicWorkspace`, single permission helper | M | CORE-05, CORE-06 | 09 §1, §6 |
| CLINIC-02 | Shell integration: Clinic tab, staff shell, identity bar + clinic switcher, 9-tab pager host | M | CLINIC-01, SHELL-01 | 09 §1.5–1.6 |
| CLINIC-03 | Shared clinic list/table kit (phone card rows, tablet columns, pills, `Money.format`, clinic-TZ dates) | M | CORE-03 | 09 §2, §9 |
| CLINIC-04 | Overview + Settings tabs | S | CLINIC-02 | 09 §1.7, §2 |
| CLINIC-05 | Doctors tab + one revenue-share dialog | M | CLINIC-03 | 09 §2, §3.8 |
| CLINIC-06 | Clinic patients tab (server paging + `q`, push to patient detail with clinic context) | M | CLINIC-03, PAT-05 | 09 §2 |
| CLINIC-07 | Services tab (unified catalogue) + catalogue editor | M | CLINIC-05 | 09 §2 |
| CLINIC-08 | Invitations tab | S | CLINIC-01 | 09 §2 |
| CLINIC-09 | Clinic doctor schedule screen (book on behalf, typed back-stack) — shares CAL-10 | L | CAL-10, CLINIC-05 | 09 §5 |
| FIN-01 | Finance DTOs (num-safe), repository, month filter in clinic TZ, refresh bus | M | CLINIC-01 | 09 §3.1, §3.12, §6–8 |
| FIN-02 | Finance dashboard (KPIs + 8 drill-downs) | M | FIN-01, CLINIC-03 | 09 §3.2 |
| FIN-03 | Payments & records views + new record dialog (server paging / date range) | M | FIN-01, CLINIC-06 | 09 §3.3–3.4 |
| FIN-04 | Appointment ledger with quick pay | M | FIN-01, CLINIC-03 | 09 §3.5 |
| FIN-05 | Installments view (method + memo on mark-paid) | M | FIN-01, CLINIC-03 | 09 §3.7 |
| FIN-06 | Doctor earnings view + CSV | S | FIN-01, CLINIC-05 | 09 §3.6 |
| FIN-07 | Export infrastructure (CSV share; PDF decision) | S | CORE-09 | 09 §3.10 |
| TP-01 | Treatment-plan DTOs, enums, repository (value-keyed caches, invalidation) — pulled forward for M5 | M | CLINIC-01 | 09 §4.5, §6–8 |
| TP-02 | Treatment plans list tab (server `status`/`q`, expandable visits, status menu) | M | TP-01, CLINIC-03 | 09 §4.1 |
| TP-03 | Treatment plan detail screen (re-fetch on refresh bus) | S | TP-01, DENTAL-04 | 09 §4.3 |
| TP-04 | Treatment plan wizard (patient → basics → care team & slots → services (list / dental chart) → payment) as a resumable `SaveStep` machine | L | TP-01, TP-03, CLINIC-05, CLINIC-07, FIN-01, DENTAL-04 | 09 §4.2 |
| TP-05 | Treatment plan PDF (server-side preferred; keep `TreatmentPlanPdfData` + `ru`) | M | TP-01, FIN-07 | 09 §4.4 |

TP-01 is needed by M5 (DENTAL-03, TP-06): build it right after CLINIC-01 at the start of M5 even though the rest of
M11 comes later.

---

## M12 — Care partnerships, reports, legal (≈ 9 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| CARE-01 | Care partnership models (status enum) + repository | S | CORE-05, PROF-01 | 06 §5.2–5.3 |
| CARE-02 | Find therapy partner & invite | S | CARE-01, PAT-05 | 06 §5.4 |
| CARE-03 | Partnership list & detail with progress feed, confirmations | M | CARE-01, CARE-02 | 06 §5.4 |
| REPORT-01 | Reports tab reusing the Home analytics composables + CSV share | M | ANALYTICS-03, SUB-01 | 08 §7 |
| LEGAL-01 | Decide doctor-side contract visibility (spike; needs a backend endpoint) | S | — | 08 §8 |
| LEGAL-02 | *(conditional)* "My partnership contract" viewer | S | LEGAL-01 | 08 §8 |

---

## M13 — Release (≈ 8 d)

| ID | Title | Size | Depends | Spec |
|---|---|---|---|---|
| REL-01 | Port `google_play_deploy.yml` (offset above the Flutter `versionCode`, `track` input incl. alpha/production) and `ios_testflight.yml` (framework step, fastlane `beta`, ASC key trimming); `MARKETING_VERSION` 1.1.0 | M | CORE-01 | CLAUDE.kmp.md CI/CD |
| REL-02 | Firebase per flavour (`google-services.json`, `GoogleService-Info.plist` from the `DOCTOR_*` secrets), App Distribution for debug builds | S | PUSH-01 | 07 §2.5 |
| REL-03 | Full regression: all `*ScreenshotTest`s recorded, `StringsTest`, `DemoBackendTest`, device matrix (Android 8+/iOS 15+), accessibility pass (content descriptions, 48 dp targets) | M | all | 04 §7 |
| REL-04 | Remove the Flutter code and assets (`lib/`, `android/`, `ios/`, `web/`, `windows/`, `pubspec.*`, `test/`, fonts), update `README.md`, keep docs/contracts/scripts that still apply; web hosting decision executed | S | REL-03 | CLAUDE.kmp.md "Rewrite in place" |
| REL-05 | Staged rollout: internal → alpha → production on Play; TestFlight external group → App Store; `docs/implemented/README.md` final status; merge `doc-app2.0` → `main` by PR (the user's call) | S | REL-04 | — |

---

## Dependency graph (coarse)

```
CORE-01 ─ CORE-02 ─ CORE-03 ─┐
   │        (Figma)          ├─ AUTH-* ─ SHELL-01 ─┬─ PROF-* / SET-* / SUB-*
   ├─ CORE-04 (strings) ─────┤                     ├─ CAL-* ─ SCHED-* ─ LOC-*
   ├─ CORE-05 (Ktor) ─ CORE-06 (session) ─ CORE-08 ┤      └─ APPT-* ─ VIDEO-* ─ SCRIBE-* ─ PDF-*
   ├─ CORE-07 (spikes: Daily, push, audio, PDF, map)│             ▲
   └─ CORE-09 (utils)                               ├─ PAT-* ─ DOCS-* ─ FORM-* ─ CLIN-*
                                                    ├─ HOME-* ─ ANALYTICS-* ─ REPORT-01
                                                    ├─ CHAT-* ◄── AI-03/04 (audio)
                                                    ├─ NOTIF-* ─ PUSH-* (needs CAL-9, PAT-05, TASK-03, CHAT-03)
                                                    ├─ TASK-*
                                                    ├─ AI-* ─ INTEL-*
                                                    └─ CLINIC-* ─ FIN-* ─ TP-* (TP-01 early, feeds DENTAL-03)
```

## Decisions to take before the milestone that needs them

| Decision | Needed by | Default in this plan |
|---|---|---|
| Figma token/component pages shared | CORE-02 | blocks M0 completion |
| Clinic staff allowed on mobile | AUTH-08, SHELL-02 | yes |
| Tier gating when the profile is loading | SHELL-04 | hide gated entries until resolved |
| Email OTP on registration: verify or drop | AUTH-06 | ask backend; send `emailOtp` if accepted |
| Backend-rendered PDFs (appointment summary, 025-2, treatment plan) | PDF-01, FORM-03, TP-05 | request now; client fallback only for summary + 025-2 |
| Native maps vs address-only first release | PROF-07, LOC-2 | address + coordinates first, map behind `expect/actual` |
| Implicit AI booking ("yes" books a slot) | INTEL-06 | **Resolved by `4d05e61`:** the backend owns confirmation (`workflowId`); no client intent, no flag |
| Waiting room, typing indicator, doc-access approve/reject, phone OTP | VIDEO-03, CHAT-03, NOTIF-04, AUTH-10 | not ported |
| Web target for the doctor desktop experience | after M2 | Flutter web keeps shipping from `main` |
| Last Play `versionCode` / TestFlight build number | REL-01 | read from Actions history |

## ID mapping (feature docs → this plan)

The per-area analyses numbered their foundation tickets independently. This plan's IDs are canonical:

| Feature doc ticket | Plan ticket |
|---|---|
| 01 CORE-01 (skeleton, DI, config) | CORE-01 + CORE-09 |
| 01 CORE-02 (storage) + CORE-03 (JWT/session) | CORE-06 |
| 01 CORE-04 (Ktor ApiClient) | CORE-05 |
| 01 CORE-05 (shared UI kit) | CORE-03 |
| 01 CORE-06 (push bootstrap) | PUSH-01 |
| 01 LANG-01 | CORE-04 |
| 01 LANG-02 | SET-02 |
| 03 TZ-2 ≙ 08 PROF-06 | both kept; PROF-06 is the picker, TZ-2 the data |
| 08 SUB-01 ≙ 01 SHELL-04 | both kept; SUB-01 is the source, SHELL-04 the shell wiring |
| 09 CLINIC-09 ≙ 03 CAL-10 (clinic doctor schedule screen) | CAL-10 builds it, CLINIC-09 integrates the back-stack |
| 02 AI-11 | scheduled in M5 (needed by APPT-03) |
| 09 TP-06 | scheduled in M5 (needed by DENTAL-03) |
| 10 ADMIN-01…18 | parked |
