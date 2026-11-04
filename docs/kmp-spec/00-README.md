# Shifa Doctor App — Rebuild Spec for Kotlin Multiplatform + Compose Multiplatform

This folder describes the Flutter app `shifa_doc_app_v1` (v1.0.0+3, commit `4d05e61`, 2026-10-24: 116 k lines of
Dart, 194 backend paths, 1 920 UI strings) so that it can be rebuilt in KMP/CMP the way the patient app was
(`SHIFA-UZ/apps-flutter-patient`, branch `shifa2.0`). It was produced by reading the code; nothing was confirmed
against the backend or the new Figma file yet. Anything the code could not confirm is marked **UNVERIFIED**.

## How to use this folder

> **Revision log**
> - 2026-10-24 (baseline): written from commit `a5ace33`.
> - 2026-10-24 (rev 1): re-checked against merge `4d05e61` ("shifa intelligence", PR #16). Only the AI/Intelligence
>   area changed; see doc 02 "Delta since baseline" and the refreshed `strings/` (export bug fixed: English is
>   complete, the "280 missing" claim is withdrawn). Design-facing per-screen flows now
>   live in [`docs/ux-flows/`](../ux-flows/00-README.md).
> - 2026-11-04 (rev 2): [`17-master-plan.md`](17-master-plan.md) unites this spec, the flows and the live backend
>   contract. **Design no longer comes from Figma**: the Claude Design canvas is vendored in
>   [`docs/design/`](../design/README.md). Every "Figma" instruction in docs 00–16 means `docs/design/`.

1. The rewrite happens on branch `doc-app2.0` of this repo. `CLAUDE.kmp.md` becomes the branch's `CLAUDE.md`
   (ticket CORE-01); it fixes stack, architecture and workflow, identical to the patient app.
2. Read this file, then `01` (core rules every feature relies on), `04` (design-system structure) and `16` (the
   build order). Each feature is one doc; implement one ticket group at a time with the feature doc, `15` and `04`.
3. For **API contract questions** `15` is the source of truth; for **UI and behaviour** the flow file, then the
   feature doc; for **looks** `docs/design/` only (the Flutter look is never ported). If they disagree, trust `15`
   for payloads and flag it.
4. Every feature doc ends with **Quirks / tech debt** and **Proposed tickets**. Decide per quirk: replicate (backend
   compatibility) or fix (bug). Never port the bugs in §"Verified bugs" below.
5. Design: the first Figma deliverable is the token set + component library (§4 of `04`). Tokens land in
   `ui/theme/`, components in `ui/widgets/`, then screens are implemented one by one against their Figma frames.

## Index

| # | File | Covers |
|---|---|---|
| 00 | this file | Scope, decisions, global facts, verified bugs, effort |
| 01 | [01-architecture-core-auth-shell.md](01-architecture-core-auth-shell.md) | Bootstrap, `ApiClient` 401/403 rules, every storage key, JWT roles, subscription tiers, main shell (9 tabs / staff 4 tabs), app lock, inactivity, localisation mechanism, auth flows (password, email OTP, invite-key registration, receptionist, forgot password, logout) |
| 02 | [02-home-ai-intelligence.md](02-home-ai-intelligence.md) | Home dashboard, analytics, Shifa AI copilot / voice session / orb, Intelligence chat, AI APIs (SSE, transcription, drafts), clinical engine, patient briefing |
| 03 | [03-calendar-schedule.md](03-calendar-schedule.md) | Calendar (month + day list + N-day grid), booking (consecutive slots), appointment actions, blocks, staff calendars, schedule setup, locations, practice-timezone rules |
| 04 | [04-design-system-and-ui-kit.md](04-design-system-and-ui-kit.md) | Token structure for Figma → `ui/theme/`, component inventory the app needs, porting rules (banners not snackbars, skeletons not spinners), legacy tokens for comparison |
| 05 | [05-appointments-video-dental.md](05-appointments-video-dental.md) | In-person consultation screen, video call (Daily.co), AI scribe, consultation notes, end-appointment pipeline, dental FDI module, 025-2 embedding, treatment-plan fulfilment, appointment PDF |
| 06 | [06-patients-forms-documents-care.md](06-patients-forms-documents-care.md) | Patients directory, patient detail (6 tabs), create patient, documents (26 categories, upload/scan/viewer/request-access), medical form 025-2, care partnerships |
| 07 | [07-chat-notifications-tasks.md](07-chat-notifications-tasks.md) | Chat (text/image/voice/document), notifications inbox + 18-type catalogue + FCM + deep-link routing, remote-care tasks |
| 08 | [08-profile-settings-reports-legal.md](08-profile-settings-reports-legal.md) | Profile (8 panels), services & pricing, location picker/geocoding, profession catalogue (138), subscription/Stripe, settings, language, app-lock UI, reports, legal contract PDF (admin-only) |
| 09 | [09-clinic-workspace-finance-treatment-plans.md](09-clinic-workspace-finance-treatment-plans.md) | Clinic workspace (9 tabs), roles, finance (dashboard, ledger, installments, earnings, records, payments), treatment plans (list, wizard, detail, PDF), book-on-behalf schedule |
| 10 | [10-admin-parked.md](10-admin-parked.md) | Admin panel analysis (38 endpoints, 15 models, 18 tickets). **Parked**: stays a web product |
| 15 | [15-api-reference.md](15-api-reference.md) | Every endpoint by feature, global HTTP conventions, dead endpoints, backend requests to raise |
| 16 | [16-implementation-plan.md](16-implementation-plan.md) | Milestones M0–M13, 175 tickets in build order with sizes, dependencies and decisions |
| 17 | [17-master-plan.md](17-master-plan.md) | **Start here for implementation.** Unites `ux-flows/`, this spec and the live backend contract (2026-11-04): what changed since 2026-10-24, decisions, tickets ↔ flow files ↔ endpoints ↔ backend gaps per milestone, the 34 unlisted endpoints; gaps in [`../backend-improvements.md`](../backend-improvements.md) |
| — | [CLAUDE.kmp.md](CLAUDE.kmp.md) | The `CLAUDE.md` for the `doc-app2.0` branch (filled-in copy of the patient app's rules) |
| — | [strings/](strings/README.md) | `en.json`, `uz.json`, `ru.json` (1 920 keys, lossless), conversion rules, missing-key report |

## Scope and decisions

Decided by the analysis, to be confirmed by the user (recorded in `docs/implemented/README.md` once confirmed):

| Topic | Flutter today | Plan |
|---|---|---|
| Platforms | Web primary (Firebase Hosting), Android, iOS, Windows | **Android + iOS** in KMP, same stack as the patient app. The Flutter web build keeps serving `shifa-doctor-*` hosting from `main` until a web target is decided (see Open decisions). Phone-first layouts; tablets get wider gutters, not the desktop split panes. |
| Admin panel | same build, hostname `admin*` | **Out of the mobile app.** Never ran off-web (`isAdminHost` is false on mobile). Keep as web; optionally extract `:admin:data` later. Tickets ADMIN-01…18 are parked in doc 10. |
| Clinic staff (`CLINIC_STAFF`) | 4-tab shell on web, blocked on native | Allow on mobile with the 4-tab shell (it is a mobile rewrite); the "web only" block is dropped. Receptionists book on behalf via CLINIC-09. |
| Firebase phone OTP login | code present, unreachable | Not ported (AUTH-10 optional). Login = password, email OTP, invite-key registration. |
| Client-side PDFs (appointment summary, 025-2, treatment plan, finance, legal contract) | `pdf` package, DejaVuSans, en/uz only | Ask the backend to render; client fallback behind `expect/actual` only for the appointment summary and 025-2 (PDF-01, FORM-03, TP-05). Legal contract stays admin-side. |
| Maps | flutter_map + OSM tiles + Google geocoding key on the client | Native maps behind `expect/actual` (LOC-2 / PROF-07) after a first release that saves address + coordinates without a map; geocoding via backend proxy. |
| Video | Daily Prebuilt iframe (web), `daily_flutter` (mobile) | Daily Android SDK + Swift bridge, as in the patient app. |
| Real time | REST polling | Same intervals; pause in background. |
| Locales | en, uz, uz-Cyrl (runtime transliteration), ru | Same; English is complete, fill the 92 `ru` / 10 `uz` gaps and the 41 keys missing everywhere. |

### Open decisions (block nothing in M0–M2, decide before M3)

1. **Web target.** Compose for Web/Wasm for the doctor desktop experience vs keeping Flutter web vs a later
   separate web app. Affects only whether desktop layouts are designed now.
2. **Tier gating default.** Flutter fails open to PREMIUM when the tier is missing; the KMP app should wait for the
   profile (hide gated entries until resolved). Confirm with product.
3. **Dead features**: waiting room, typing indicator, document-access approve/reject in the inbox, "AI follow-up
   suggestions" heuristics, Prescriptions/History tabs that only show forms/recent activity. Default: not ported.
4. **Version/CI offsets**: last Google Play `versionCode` and TestFlight build number of the Flutter app (both were
   `1 + run_number`); read them from the Actions history before CORE-01.

## Global facts every feature depends on

- **Backend** `https://shifa-doc-backend-mvp-production.up.railway.app` (`/api/...`), JSON, 30 s timeouts, Bearer JWT,
  no refresh token. Public paths: `/api/auth/**`, `/api/public/**`.
- **401/403** (doc 01 §3): 401 ⇒ logout if a token was sent. 403 ⇒ logout only when the JWT is expired or the body
  carries one of three "session" error strings or it is a `Forbidden` on `/api/doctors/me*` for a non-staff role.
  Clinic staff legitimately receive 403 on doctor-only endpoints. Pre-flight: expired JWT (30 s grace) ⇒ logout.
- **Roles** from the JWT `role` claim: `DOCTOR` (default), `CLINIC_STAFF` (4-tab shell, `/api/me/profile` instead of
  `/api/doctors/me`), `ADMIN` (web only). Clinic membership roles (`OWNER`, `CLINIC_ADMIN`, `RECEPTIONIST`,
  `DOCTOR`, `NURSE`) gate finance actions client-side; the server is authoritative.
- **Subscription tiers** `BASIC < PRO < PREMIUM` from `GET /api/doctors/me` `subscription.tier`. Gates actually
  checked: Tasks tab (PREMIUM), Ask Shifa AI / speech-to-text / AI notes / patient briefing (PRO), advanced
  analytics (PREMIUM).
- **Time** (doc 03 §4): instants are UTC ISO; display in the doctor's **practice time zone** (`profile.timeZone`),
  clinic TZ in the clinic workspace, patient TZ for task check-ins. Device time is used only for the "device differs
  from schedule zone" hint. "Today", "past", video join window (−5 min … end + 1 h) are computed in practice TZ.
- **Storage keys** (doc 01 §4): secure `shifa_doctor_auth_token`, app-lock keys (`app_lock_pin` → store a salted
  hash in KMP, `biometric_enabled`, `app_lock_enabled`); plain `doctor_language`, `doctor_language_explicit`,
  `shifa_doctor_last_activity_ms`, `active_location_id:<doctorId>`, `calendar_visible_staff_v1:<doctor>:<clinic>`,
  `clinic_workspace_selected_id:<meId>`.
- **Push**: FCM token → `PUT /api/doctors/me/fcm-token` after login and on refresh, `""` on logout; Android channel
  `shifa_doctor_channel`; one router for 18 notification types (doc 07 §2.3). Firebase project
  `shifa-doctor-staging` (also production).
- **Polling**: chat list + open conversation 5 s, chat unread 10 s, task details 15 s, scribe 3 s (≤ 120 s),
  signature 10 s, briefing 3 s; notifications unread refresh on resume/push only.
- **Strings**: 1 920 keys, camelCase, placeholders `{x}`, `{{x}}`, `%s` → `%1$s`; six hand-written Russian plural
  sites → `<plurals>`; Uzbek Cyrillic by transliteration of Uzbek Latin.
- **Money**: integer minor units + currency; one `Money.format(minor, currency, locale)` for the whole app.

## Verified bugs — do NOT replicate

Each was confirmed in the source (file:line in the owning doc).

1. **App-lock PIN auto-verifies at 4 digits**, so 5/6-digit PINs can never unlock; PIN stored in plaintext
   (doc 01 §7, §13.1).
2. **Profile full name and birth date are never saved**; the Save button still says "saved" (doc 08 §13.1).
3. **Registration extras are re-PATCHed on every login** because `copyWith(null)` cannot clear them; the plaintext
   password stays in memory for the process lifetime (doc 01 §13.2).
4. **Registration email OTP is collected but never verified** by the client or sent with `register` (doc 01 §13.3).
5. **End-appointment completes the visit before the summary PDF exists**; a later failure strands a completed visit
   without its document (doc 05 §10.6). Four SOAP controllers leak on dispose (§10.2).
6. **41 referenced strings exist in no locale** and render as raw keys (incl. every app-lock label); `ru` lacks 92
   keys and `uz` 10, and neither falls back to English. (The baseline's "280 missing English strings" was an export
   artefact: the repo's exporter drops keys after a double-quoted value. English is complete bar one key.) `uz`/`ru` never fall back to English (strings/README).
7. **Finance lists filter page 0 (50 rows) in memory** for month filters: older payments/records/patients vanish
   (doc 09 §10.1).
8. **Treatment-plan wizard: 6–9 non-atomic calls**; installment-sum mismatch is silently skipped yet the plan is set
   ACTIVE; plan detail is cached forever (`keepAlive`, never invalidated) (doc 09 §10.7, §10.10).
9. **Device time leaks** into the calendar now-line/`isToday`, chat timestamps, notification grouping, task
   check-in "missed", plan dates, wizard slot times, PDF dates (docs 03 §9.2, 07 §8.4, 09 §10.6).
10. **Video vs in-person decided by `location.contains("video")`**; payment status dropped from the appointment
    model; hard-coded English `'Video Consultation'` / `'Clinic Address'` / `'Check Up'` sent to the backend
    (docs 03 §9.6, 05 §10.7, 06 §10.6).
11. **Map pin is lost when reverse geocoding fails**; street-address edits PATCH per keystroke; Google key fetched
    unauthenticated and logged (doc 08 §13.2, §13.3, §13.9).
12. **Subscription tier fails open to PREMIUM** when missing/loading/for clinic staff (doc 01 §13.12).
13. **`print()` logging in release** of JWT prefixes, request bodies, full chat message objects and push payloads
    (HIPAA) (docs 01 §13.10, 07 §8.5).
14. **Dental subtotal silently skips lines in a second currency**; `as int` JSON casts crash on doubles across
    finance models (docs 05 §10.10, 09 §10.4).
15. **Patients directory downloads every page client-side** on every tab switch; favourites are not persisted;
    "Recent" is "last 20 by name" (doc 06 §10.3, §10.4).
16. **Chat voice/document URLs are not normalised** (images are); mark-read only on select; typing indicator is
    dead UI (doc 07 §8.1, §8.6, §8.7).
17. **Services & pricing writes never check the response status** (doc 08 §13.6).
18. Dead code committed: `video_call_screen.dart.backup`, 820 commented lines at the top of `profile_screen.dart`,
    214 at the top of `calendar_controller.dart`, unreachable waiting room, phone-OTP screens, timezone debug
    screen, 11 unused shell widgets, three JWT decoders, two create-patient sheets, three avatar clones.

## Findings surfaced by the UX-flows pass (2026-10-24, `docs/ux-flows/`)

Per-screen writers re-read the code screen by screen. New facts the plan must absorb (details in each flow file's §12/§13):

- **No force-update / maintenance gate exists**; `GET /api/public/config` only returns one flag. SPLASH gates need new backend fields (flows 01/01).
- **No notification preferences, privacy/export, delete-account, About or doctor-facing legal screens exist** at all; delete-account is a store-policy requirement (flows 08/12–13). Add tickets to M2/M12.
- **Client-side AI booking intents were removed in `4d05e61`**; propose cards with `workflowId` are read-only and the backend books on "yes" (doc 02 §0, INTEL-06).
- **No tier gate anywhere in the clinic workspace**; access = non-empty `GET /api/me/clinics`. Services, invitations and plan status changes have no client role gate (flows 09).
- **Push deep links reuse doctor tab indexes for clinic staff** (appointment → Notifications tab, patient → Profile tab); fix in SHELL-02/NOTIF tickets (flows 01/20, 07/10).
- **Booking is slot-first only** (no patient-first flow, no walk-in action); past slots on today are bookable; "Change slot" adopts the target slot's length (flows 04/06–07).
- **Three different video start windows** (Home 15 min, calendar 5 min → 1 h after end, backend token 5 min → 15 min after end) (flows 05/01, 05/10).
- **Plan ticks and chair-side payments are posted only inside End Appointment**; leaving the screen loses them; retry after a kill can double-pay (flows 05/08–09, 09/18).
- **Patients list and Tasks list have no loading state and swallow errors**; create-patient sheets close before the request is sent, losing input on 409 (flows 06/01–02, 07/11).
- **Chat thread opens at the oldest message; Send is never disabled** (double send, empty `text: null` POST) (flows 07/02–03).
- **Change-password validates 6 chars while the backend policy is 8–128 + classes**; registration email OTP is never verified (confirmed) (flows 08/11, 01/09).

## Where the code differs from the repo's CLAUDE.md / README

- "Firebase phone authentication (OTP) for doctor login": the screens exist but nothing navigates to them; login is
  password or email OTP. Firebase itself only initialises on web (`firebase_options.dart` throws on mobile), so push
  never worked on the Flutter mobile builds.
- "14 feature modules": there are 20 (`clinic`, `clinical_engine`, `intelligence`, `care_partnerships`, `legal`,
  `reports`, `settings`, `dental` are undocumented).
- "6 tabs": the doctor shell has 9 tab slots (Chat, Home, Calendar, Patients, Clinic, Tasks, Reports,
  Notifications, Profile) with different subsets on desktop sidebar vs phone bottom bar + More sheet.
- `--dart-define=ENVIRONMENT` only switches the localhost fallback; staging and production share one Firebase
  project and one backend URL.

## Effort summary (from doc 16)

| Milestone | Tickets | Size |
|---|---|---|
| M0 Foundation | 9 | ≈ 16 d |
| M1 Launch & auth | 11 | ≈ 14 d |
| M2 Shell, profile, settings | 20 | ≈ 36 d |
| M3 Calendar & schedule | 16 | ≈ 42 d |
| M4 Patients, documents, forms | 19 | ≈ 44 d |
| M5 Appointments, scribe, dental, video | 21 | ≈ 50 d |
| M6 Home & analytics | 8 | ≈ 16 d |
| M7 Chat | 7 | ≈ 18 d |
| M8 Notifications & push | 7 | ≈ 14 d |
| M9 Remote-care tasks | 5 | ≈ 11 d |
| M10 Shifa AI & Intelligence | 19 | ≈ 40 d |
| M11 Clinic workspace, finance, treatment plans | 21 | ≈ 48 d |
| M12 Care partnerships, reports, legal | 6 | ≈ 9 d |
| M13 Release | 5 | ≈ 8 d |

≈ 175 tickets, ≈ 368 developer-days (≈ 74 weeks) for one developer with AI assistance, about 9 months. Two
developers (clinical stream vs communication/clinic stream after M2) bring the calendar time to about 5 months.
Sizes are ±30 %; the patient-app rewrite is the calibration point. The admin panel (18 tickets) is parked.
