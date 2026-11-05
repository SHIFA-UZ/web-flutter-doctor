# What's implemented

Read this before starting a task. Update it at the end of every job, in the same commit: the status table, the file
for the area you touched, and one line in the log.

Ticket IDs and order: `docs/kmp-spec/17-master-plan.md` §4 (then `16-implementation-plan.md`). What the app needs from
the backend team: [../backend-improvements.md](../backend-improvements.md). Codex reviews of each ticket:
[codex/](codex/).

| Area | File |
|---|---|
| Project, CI, HTTP client, storage, session, demo backend, utils, native spikes | [foundation.md](foundation.md) |
| Design tokens and the UI kit | [design-system.md](design-system.md) |
| Strings and localization | [strings.md](strings.md) |
| Appointments and visits (M5) | [visits.md](visits.md) |

## Decisions

Recorded so they are not re-litigated. IDs are from `17-master-plan.md` §3.

| ID | Decision | Whose call |
|---|---|---|
| D-00 | **One branch, `doc-app2.0`, for the whole rewrite; no per-ticket branches** (overrides §3's default of short PR branches per ticket group). Created from `origin/main` at `078af42` after the docs PR (#21) was merged. | The user's call, 2026-11-04 |
| D-01 | Design source is `docs/design/` (the vendored Claude Design canvas); the primary fill stays `teal-50` with white labels. | The user's call, 2026-11-04 (§3 default) |
| D-02 | Clinic staff use the mobile app with the 4-tab shell (`01/18`). | The user's call, 2026-11-04 (§3 default) |
| D-03 | Gated entries stay hidden until the profile has loaded; gate on `subscription.features`, never fail open. | The user's call, 2026-11-04 (§3 default) |
| D-04 | No registration e-mail code dialog until BE-03 ships; `send-email-otp` wiring kept ready. | The user's call, 2026-11-04 (§3 default) |
| D-05 | PDFs: backend rendering requested (BE-01); client `expect/actual` fallback only for the appointment summary and 025-2. | The user's call, 2026-11-04 (§3 default) |
| D-06 | Maps: address fields + coordinates first, native map behind `expect/actual` later, no Google key in the app (BE-15). | The user's call, 2026-11-04 (§3 default) |
| D-07 | Not ported: waiting room, typing indicator, doc-access approve/reject in the inbox, phone OTP. | The user's call, 2026-11-04 (§3 default) |
| D-08 | Flutter web keeps deploying from `main` for now; a KMP web target is built **after M2** (the user asked for KMP web too). | The user's call, 2026-11-04 (confirmed mid-run) |
| D-09 | App-lock PIN 4–6 digits, user's choice, explicit submit, salted hash. | The user's call, 2026-11-04 (§3 default) |
| D-10 | Version `1.0.1`; build numbers start at 10 on both stores: `VERSION_CODE_OFFSET` 1 (next Play run 9 → 10), `BUILD_NUMBER_OFFSET` 4 (next TestFlight run 6 → 10), same workflow files (last Flutter uploads: Play run 8 → versionCode 9, TestFlight run 5 → build 6; public Actions API, 2026-11-04). The shared `iosApp.xcscheme` is committed so fastlane can build. | The user's call, 2026-11-04 (§3 default) |
| D-11 | Doctor delete-account UI built; the action goes to "contact support" until BE-02 ships. | The user's call, 2026-11-04 (§3 default) |
| D-12 | Video gated on `VIDEO_CONSULTATION` in `features`, hidden while unresolved. | The user's call, 2026-11-04 (§3 default) |
| D-13 | Push language and per-device tokens: inherit the patient app's asks (BE-05, BE-06); single token until then. | The user's call, 2026-11-04 (§3 default) |
| D-14 | Booking is slot-first only; "Book for this patient" opens the calendar with the patient pre-filled. | The user's call, 2026-11-04 (§3 default) |
| D-15 | Push (FCM token, PUSH-01…03) moves to the very end, together with a clean-up of the Firebase project; the debug build's token refusal is part of that. | The user's call, 2026-11-04 |
| D-16 | Daily video may be tested against the production backend (`POST /video/token`), as the patient app does. | The user's call, 2026-11-04 |
| D-17 | The uz/ru texts written for the port stay as they are for now (no native-speaker pass before release prep). | The user's call, 2026-11-04 |
| — | **Open, to confirm:** patient language stored as codes (uz, ru, en…; Flutter stored words), PAT-04. | My call, 2026-11-04 |
| — | **Open, to confirm:** the clinical engine card is open to every plan (the board marks it PRO; the backend has no feature code and Flutter showed it to everyone), CLIN-02. | My call, 2026-11-04 |
| — | The clinical engine fills the form only on **Apply** (the board's button); Flutter re-filled the fields after every chip toggle, over the doctor's typing. CLIN-02. | My call, 2026-11-04 (board + safety) |

## Status

| Ticket | Status | Notes |
|---|---|---|
| CORE-01 Project skeleton, CI | Done | KMP project (`composeApp`, `shifa.doctor`, `com.shifa.doc` / `uz.shifa.doc`), the patient app's Gradle catalog and ktlint, `iosApp` host, `CLAUDE.md` from `CLAUDE.kmp.md`, `ci.yml` + the two deploy workflows ported. CI has never run. See foundation.md. |
| CORE-02 Design tokens | Done | Every `--*` property of `shifa-ds.css` in `ui/theme/`; doctor text colours use the 70-steps. Token gallery = previews + screenshots. See design-system.md. |
| CORE-03 UI kit | Done | One composable per `.sh-*` block plus the AuCodebase doctor components, phone layouts; every …States board state has a preview and a screenshot (51). Icons generated from the design library. See design-system.md. |
| CORE-04 Strings | Done | 2218 keys per locale generated from `docs/kmp-spec/strings/` (all gaps + flow NEW keys), 16 plurals, `tr()` with runtime Uzbek Cyrillic, `LanguageStorage` (en/uz/uz-Cyrl/ru). Backend language sync moves to CORE-05. See foundation.md. |
| CORE-05 HTTP client | Done | Ktor client + `apiCall` with the 17 §1 session rule (401 with token or `ACCOUNT_DISABLED` ends it, 403 never), `code`/`requestId` errors, no server text in logs, SSE reader, dev-host switch, `TokenStorage` + `Jwt` (from CORE-06), language sync call. See foundation.md. |
| CORE-06 Storage, session | Done | `SessionRepository` (restore without network, sign-in, sign-out, ends on `SessionEvents`; logout clears only the token and last activity), `AppLockStorage` with a salted iterated PIN hash (bug 1), language sync wired to the session. See foundation.md. |
| CORE-07 Native spikes | Done (Daily skipped, FCM blocked) | AAC record + play, PDF view and map pin work on the emulator and the simulator (osmdroid / MapKit, no Google key). FCM token: debug build rejected ("FCM Registration failed!"), credentials. Daily: skipped, needs an appointment room token + SDKs. Outcomes in `CLAUDE.md` and foundation.md. |
| CORE-08 Demo backend, debug menu | Done (fake pushes deferred) | `DemoBackend` (MockEngine) answers every endpoint called so far from a scrubbed dev fixture; `auth/` and unknown GETs go to the real backend, other writes are refused. Debug menu: demo data, development backend, native spikes. Fake pushes come with PUSH-01/NOTIF. See foundation.md. |
| CORE-09 Utils | Done | `PracticeClock` (practice-zone today/now, DST-safe conversions, mismatch flag, zone-less timestamps as UTC), `Money` (UZS whole sums, grouped), `PhoneE164`, `PasswordPolicy` (the backend's patterns), document picker / share / in-app browser / file saver. `sanitizeErrorMessage` not ported (17 §1). See foundation.md. |
| AUTH-01 Splash | Done | Session restore + BE-04 boot check in parallel (0.8 s for a returning doctor), force-update and maintenance frames, top-level navigation. See auth.md. |
| AUTH-02 Sign In | Done | Shared `AuthFrame` (bar, banner, language pill, centred scrolling content); validation, password sent as typed, banners by error type, forced-logout reason banner. Signed in against dev on the emulator. See auth.md. |
| AUTH-03 E-mail code sign-in | Done | E-mail and code screens (one mode for sign-in and reset), 60 s resend per address, sixth digit submits, wrong code clears the field. See auth.md. |
| AUTH-04 Forgot password | Done | Reset code by e-mail (app=doctor), new password with the shared live checklist (backend policy), wrong/expired code banner and Back to the code. See auth.md. |
| AUTH-05 Invitation key | Done | Key trimmed, one neutral invalid-key banner, purpose picks the doctor or receptionist registration, kept in memory only. See auth.md. |
| AUTH-06 Create account (doctor) | Done (no e-mail code: D-04) | Step 1 form, duplicate-doctor check, existing-patient confirmation, step 2 with the practice timezone only (AUTH-07 adds the rest), `register` → shell. Invited address locked. Found BE-29 (register re-keys an existing doctor). See auth.md. |
| AUTH-07 Registration step 2 | Done | Date of birth (Material date picker), gender, address, clinic search, professions (multi), timezone; PATCHed once after `register`, failure leaves a shell notice. Pickers built here are the ones PROF-05/06 reuse. See auth.md. |
| AUTH-08 Receptionist registration | Done | One-step staff form on the registration screen: clinic card, invited address read-only (editable only when the invitation has none, C-4), names, timezone, passwords; `register-clinic-staff` → session → shell (SHELL-02 gives staff their shell). See auth.md. |
| AUTH-09 Sign out | Done (push token: D-15) | One sign-out question (`SignOutSheet` + `SignOutViewModel`) for every place that offers it, PIN line when app lock is on, one navigation through the session, "You've signed out" on Sign In. Offered in the shell stand-in until SHELL-01. See auth.md. |
| LOCK-01 App lock | Done | Lock window over every screen and sheet after a restored/new session and after ≥ 5 s in the background (monotonic clock), PIN 4–6 digits with "Unlock" (D-09), attempts left, fifth wrong PIN → sign-out with its banner and the PIN forgotten, biometrics (BiometricPrompt / LAContext) with auto-prompt, "Sign out instead", Back blocked, suppress hook. Settings section with set/repeat/off/change sheets (shell stand-in until SET-03). See auth.md. |
| LOCK-02 Inactivity timeout | Done | 30 days without a touch or an expired token end the session on a cold start (decided in the splash, before the shell), on coming back, and on a tap with an expired token; activity written at most every 5 min; Sign In explains it. **M1 complete.** See auth.md. |
| PROF-01 Profile models + repository | Done | Typed `/doctors/me` (profile, contact, billing, settings, subscription) and `/me/profile`; shared `profileRepository` with the four PATCHes updating it in place; cleared when the session ends. Checked against dev. See profile.md. |
| SHELL-01 Doctor shell | Done (tabs are placeholders) | Floating bar Home/Calendar/Patients/Chat/More, per-tab stacks kept when switching, re-tap → root, bar hidden on pushed screens, Back pop → Home → exit, More sheet (Intelligence, Clinic if a member, Tasks with its plan, Reports, Notifications, Profile, Language, Sign Out), start tab from the profile once with clamps. See shell.md. |
| SHELL-02 Clinic-staff shell | Done | Chat / Clinic / Notifications / Profile (their photo), starts on Clinic, no saved start tab, never calls `/doctors/me`, Back → Clinic → exit; Language and Sign Out in their Profile. See shell.md. |
| SHELL-03 Badges | Done | Chat unread polled every 10 s only while the app is in front, notifications unread on each return, active tasks once (PREMIUM); bar badges (staff: chat + notifications) and More counts, "99+" cap, screen-reader "Chat, 3 unread". See shell.md. |
| SHELL-04 Plan gating | Done | `FeatureGate` (backend `SubscriptionFeature` codes and tiers), gating on `subscription.features`, unknown allows nothing (D-03); the shell uses it; a plan change later moves a doctor off Tasks with a note. Also SUB-01's source. See shell.md. |
| SHELL-05 Intelligence mode, resume refresh | Done (AI surface: area 03) | Shifa Intelligence replaces the tab content with the bar kept (tab tap or Back leaves it, the tab underneath untouched, gated on `ASK_SHIFA_AI`); every resume re-reads the profile, clinics and notifications. See shell.md. |
| PROF-02 Profile root | Done (sections are placeholders) | Identity card ("Dr. med. First Last", professions in the app language, timezone and plan chips), completeness card with fix-it chips, ten section rows with live previews, Sign Out, version; staff read-only variant; skeleton, error with Retry. See profile.md. |
| PROF-03 Personal information | Done | Names, date of birth, gender, professions (shared picker), changed-keys-only save, discard question; photo from gallery or camera uploads at once. Remove photo waits for BE-30. See profile.md. |
| PROF-04 Contact, invoicing, password | Done | Contact & clinic address (phone, e-mail, editable address fields, timezone row), Subscription & billing (current plan, invoicing card), Security (app lock + change password). Address search and map are PROF-07; plans and checkout SUB-02. See profile.md. |
| PROF-05 Profession catalogue | Done | Catalogue, fallback and picker came with AUTH-07 / PROF-03; dental detection (with Implantologist) ported with Flutter's tests. See profile.md. |
| PROF-06 Timezone picker | Done | Shared timezone list (city, zone, offset; offset search), practice-timezone sheet over Profile and Contact with the change warning. See profile.md. |
| PROF-07 Location (map pin) | Done (partly) | "Pick on map" on Contact: native map, tap to place the pin, coordinates saved with the address. Address search and reverse geocoding wait for BE-15. See profile.md. |
| PROF-08 Extended profile | Done | Biography and socials with Save; service tags and certificates saved at once (photo, gallery or PDF upload; delete). See profile.md. |
| SVC-01 Services & pricing | Done | Grouped list with prices per currency and location, editor with checked writes and validation, confirmed delete, clinic-managed read-only. See profile.md. |
| SVC-02 Service groups | Done | Groups editor: rename, reorder (up / down), add, delete; saved in order, then the list reloads. See profile.md. |
| SUB-02 Checkout and Stripe Connect | Done | Plan cards with prices and Upgrade, Stripe checkout and Connect onboarding in the in-app browser, plan re-read on return. See profile.md. |
| SET-01 Settings | Done | Country, language row, starting screen, 2FA and encrypted-documents flags with queued whole-block PATCH; staff variant. See profile.md. |
| SET-02 Language | Done | Language screen in Settings (own names, applies at once); sync, mini toggle and regional default were already in place. See profile.md. |
| SET-03 App-lock settings | Done | Section and strings were in place (LOCK-01, PROF-04); the Settings Security row now previews the lock state. See profile.md. |
| TZ-1 Practice clock | Done | PracticeClock (CORE-09) plus shared date/time formatters and DST tests. See calendar.md. |
| TZ-2 Timezone list | Done | Detection and the shared list came with registration and PROF-06. See calendar.md. |
| CAL-1 Calendar data | Done | Typed calendar entries, per-day cache with 60 s throttle, month batch, colleague preview; demo days. See calendar.md. |
| CAL-2 Today, next, changes | Done | Today's appointments, the next appointment (14-day horizon), one refresh and one event per appointment change. See calendar.md. |
| CAL-3 Month picker | Done | Monday-first month card with the board's occupancy shading, today ring, appointment dots. See calendar.md. |
| CAL-4 Calendar screen | Done | Doctor's Calendar tab: month picker, day agenda (compact and details rows), filters, timezone-mismatch banner, resume reads. See calendar.md. |
| CAL-6 Appointment panel | Done | Appointment details from the calendar: status, reschedule to a free slot, adjust duration, payment reminder, main button by time window, cancel. See calendar.md. |
| CAL-7 Booking | Done | Book a free slot for a patient: picker (search by name or id), clinic or video, start and an end along the chain of free slots, reason; past-day guard; taken range kept with its message. See calendar.md. |
| CAL-8 Blocks | Done | Block a day, some hours or several days from the calendar header (reason chips, cancel overlapping with a live count); blocked row panel with Remove block. See calendar.md. |
| LOC-1 Locations | Done | Profile → Locations: list, add, edit (label, clinic, street, city, primary), delete with a confirmation; one shared list (services use it too). See schedule.md. |
| CAL-9 Deep-link intents | Waits for its first caller | Go-to-appointment and quick-book are only started by push (D-15, at the end) and Home (M6); built with them instead of as unused code (2026-11-04). |
| CAL-10 Staff calendars | Waits for CLINIC-05 | Needs the clinic workspace members (M11). |
| CAL-5 N-day grid | Last in M3 | Tablet only (master plan: build last in M3 or in a tablet pass). |
| SCHED-1 Schedule data | Done | Weekly template per location, validity periods, date-specific hours; range and period validators. See schedule.md. |
| SCHED-2 Working hours | Done | Profile → Schedule: week overview with a sheet per day (ranges, copy, Mon–Fri), the bookable period, extra hours on dates; Save blocked until the rules are read. See schedule.md. |
| LOC-2 Location map pin | Done (pin only) | Pick on map in the location editor (the contact picker, shared); coordinates saved; geocoding waits for BE-15. See schedule.md. |
| PAT-01 Patient models | Done | Patient, status, display id, age on the practice date, phones and location; lenient reads. See patients.md. |
| PAT-02 Patients repository | Done | The directory as one flow (pages, 60 s TTL), one patient, create, update, appointments, account, test SMS, prophylaxis, booking list; typed errors. See patients.md. |
| PAT-03 Patients directory | Done (phone) | Patients tab: count, search (name, id, display id, phone digits; debounced), All / Favorites / Follow-ups, status filter, name order, 10 per page, pull to refresh; favourites kept per doctor. See patients.md. |
| PAT-04 Create patient | Done | One create sheet (directory and booking picker): name, phones in E.164, email, address, language, gender, blood group, allergies, birth date; stays open while saving. See patients.md. |
| PAT-05 Patient detail | Done (phone, Overview) | Header, stats, record tabs (Overview built), clinical summary, patient-app login shown once, SMS reminders saved at once with rollback, chronic warning once per session, shared favourites. See patients.md. |
| PAT-06 Medical info | Done (no prophylaxis yet) | Per-field saves with rollback (dropdowns at once, phones and allergies with Save), localised chronic diseases, discard guard, Edit details (the create sheet, PATCH of changes). Prophylaxis with CLINIC-06. See patients.md. |
| PAT-07 Appointments & Activity | Done (visits only) | Visits on the practice clock, Appointments tab, Activity by month and Recent activity (visits; documents and forms join later), New appointment → free-slot sheet → CAL-7 booking with the patient chosen. See patients.md. |
| DOCS-01 Document models | Done | 27 categories (labels), lenient list, download named by Content-Disposition (RFC 5987) and typed by bytes, request access; a locked download is 404 on dev. See patients.md. |
| DOCS-02 Pickers & photos to PDF | Done | Existing pickers reused; a shared photos-to-PDF writer that embeds the JPEGs unchanged (48 KB for two photos instead of megabytes). See patients.md. |
| DOCS-05 Document viewer | Done | Download → zoomable image, PDF in the zoomable PDF view, or "can't preview" with Save to device; 403 request access, 404 gone, Retry. See patients.md. |
| DOCS-04 Documents tab | Done | Search, cards (lock, chip, shared mark, byline, localised 025-2 title), open in the viewer, request access; read on each showing. See patients.md. |
| DOCS-03 Upload | Done | PDF, photo (as JPEG), gallery, multi-page scan → title & required type → multipart upload with progress, Cancel, Retry, 25 MB cap. See patients.md. |
| FORM-01 Form models + dental codec | Done | `PatientForm` / `FormRequest` (no server fields, one ICD spelling), the 025-2 dental-chart key grammar ported with tests (FDI wins over legacy keys). See patients.md. |
| FORM-02 Forms repository | Done | Cached list per patient, save (POST/PUT), request signature, link document, replace a document's file, ICD-10 search, latest 025-2. See patients.md. |
| FORM-03 025-2 PDF | Done (iOS not run) | Shared layout (official Uzbek labels, sections, dental chart, return visits, signature; text flows across pages) drawn by PdfDocument / UIGraphicsPDFRenderer with system fonts. See patients.md. |
| CLIN-01 Clinical engine API | Done | Lenient models, groups / diseases / chips / occlusion / shared texts, synthesize, record-usage (new: Flutter never sent it). See patients.md. |
| CLIN-02 Clinical engine panel | Done (hosted by FORM-04) | Disease picker and frequent diagnoses, chips per field with [X] values, search, occlusion and ready texts; Apply hands the texts over once (Flutter overwrote on every toggle). See patients.md. |
| FORM-04 025-2 editor | Done (no speech yet) | New (prefilled, chart carried over) or existing form, ICD-10, clinical engine, dental chart editor, return visits, signature; save = form → PDF → document → link → usage, resumable at the failed step. See patients.md. |
| FORM-05 Forms tab + template picker | Done | Forms with signature / PDF badges, error with Retry (no Create until loaded), template sheet, Edit form on a form's PDF; checked on the emulator with the editor. See patients.md. |
| APPT-01 Appointment model + visit route | Done (no screen yet) | `AppointmentDetail` as the doctor's GET really is (patient, signature, plan; no video flag), `VisitKind`, `ShellRoute.Visit(id, kind)` from Start / Continue. See visits.md. |
| APPT-02 Appointment detail + signature | Done (no screen yet) | Detail, request signature, a 10 s poll only while the signature is awaited, ending when signed; demo visits sign 15 s after the request. See visits.md. |
| AI-11 Consultation notes & scribe API | Done (no screen yet) | Notes, drafts (confirm / discard), complete, scribe status with a cancellable 3 s / 120 s wait, the documentation helpers (Flutter's tests ported); failed reads are failures, not empty lists. See visits.md. |
| APPT-03 Visit notes + autosave | Done (no screen yet) | SOAP + free notes as one labelled body, saved 2 s after the last edit and on leave, earlier notes filled once, drafts saved / dropped; notes can't be emptied (backend). See visits.md. |
| APPT-04 Visit workspace | Done (phone; no End, video pane, dental yet) | Header with the visit clock, patient chips, notes card (modes, menu, Shifa AI and last-025-2 helpers, SOAP + free notes autosaved, focus mode), grouped documents with upload, signature footer; leaving saves; staff screen. See visits.md. |
| APPT-06 Visit briefing sheet | Done | Row on the appointment details, sheet with pending / ready / failed / empty, meta line, attached documents, Copy, Retry; reads every 3 s (10 s after a minute) only while open. See visits.md. |
| AI-03 Recorder level | Done (level not yet seen on a phone) | `VoiceRecorder.level()` (Flutter's bar height from Android's peak / iOS metering), recording id and duration; the debug spike shows it. See visits.md. |
| AI-05 Transcription | Done (no field yet) | `/ai/transcribe` multipart with the language hint and recording id, 90 s, typed 403 / 429 / 502, "(No speech detected)", reports and the public-config flag. See visits.md. |
| AI-06 Speech text fields | Done | Mic in the visit's SOAP / free notes / focus mode and the 025-2 note fields (PRO), recording pill with live wave and time, transcript appended with a space, messages in the app's language, Report. See visits.md. |
| AI-01 AI HTTP client | Done (no screen yet) | Both streams on the shared client (401 ends the session), the backend's exact SSE framing (verbatim `data:`, multi-line, EOF flush, typed `error` codes), tool cards, drafts, `conversationId`, 75 s per turn; speak (560-char cut, 25 s, audio type), patient briefing, visit facts / timeline / commit; demo AI answers locally. Fixed `streamEvents` sending a bare GET. See intelligence.md. |
| AI-02 AI text renderer | Done | `AiResponseText` (ui/widgets): `#`–`###` and whole-line `**…**` headings with the brand bar, `-` / `*` / `•` / `1.` bullets, `**bold**`, one gap per blank run, a long single paragraph split into sentences, selectable; Flutter's tests ported. The visit briefing uses it. See intelligence.md. |
| SCRIBE-01 AI scribe recording | Done (in-person; video with VIDEO-02) | Start AI Notes (PRO), recording pill, upload with the visit and language, awaiting-scribe flag for End, 429 / 403 messages, failed upload kept for another try; From Shifa AI reads again. See visits.md. |
| PDF-01 Visit summary document | Done (made in the app; BE-01 pending) | A4 summary: header, patient and visit, clinical notes across pages, signatures, footer with page numbers; en/uz/ru labels; shared `PdfFlow` with the 025-2. Wired by APPT-07. See visits.md. |
| APPT-07 End appointment (+ SCRIBE-02) | Done (no plan / payment steps yet) | Checklist sheet, then notes → complete → AI notes wait (3 s / 120 s, Continue without waiting) → summary PDF filed under the patient; failure before completion keeps the visit open, after it only the summary is retried. See visits.md. |
| SCRIBE-02 Scribe wait | Done with APPT-07 | The End pipeline's AI step: cancellable, elapsed time shown. |
| APPT-08 AI ICD into the 025-2 | Done | Apply keeps the AI code, the 025-2 mode shows it, Open Form 025-2 fills a new form's ICD-10 with it (unsaved). The editor isn't embedded (the board opens it). See visits.md. |
| DENTAL-01 Tooth map and dental record | Done | FDI tooth map (free and plan modes, phone and wide tiles), the version 2 dental record codec with legacy keys, totals per currency. Used by DENTAL-02. See visits.md. |
| DENTAL-02 Dental visit record | Done | Free mode: tooth map, services sheet, general services, dentition, discount, clinical notes, totals per currency; 1200 ms autosave and flush (nothing edited before the record is read); End saves it, its checklist row and the summary's dental section. Plan mode is DENTAL-03. See visits.md. |
| APPT-05 Before/after photos | Done | Before / After Treatment from the notes menu (camera or gallery, compressed), removable chips, kept until End (leaving asks), Photos row in the End checklist, one summary PDF page each. See visits.md. |
| TP-01 Treatment plans data (visit part) | Done | Plans per patient (no cache yet), detail (nested or flat summary, line teeth), fulfilment candidates and fulfil; typed failures; selected clinic = first membership until CLINIC-01; demo plan. Dev has no plans (shapes from OpenAPI). See visits.md. |
| DENTAL-03 + TP-06 Treatment plan in the visit | Done | Plan panel (linked/only plan chosen, progress), plan-mode chart and fulfil sheet, Plan finances with this visit's payment; End applies ticks then records the payment before completing (once each); checklist rows; one plan section in the summary; leaving asks. Demo only (no dev plans). See visits.md. |
| DENTAL-04 Plan editor + read-only plan chart | Done (hosts in M11) | The visit's dental record UI and draft reused with plan services; plan lines with the discount spread per currency (Flutter's fixture ported); read-only plan chart. Wizard (TP-04) and plan detail (TP-03) host them. See visits.md. |
| VIDEO-01 Daily token + call client | Done (no live join) | `POST /video/token`; refusals explained by the visit's window and payment (backend sends a bare 403, BE-28); the patient app's Daily client copied (Android SDK, iOS Swift package + bridge; the iOS app builds). A real join needs a dev video visit in its window. See visits.md. |
| VIDEO-02 Video call screen | Done (no live call) | Pre-join page (preview, camera/mic switches, explained refusals, mic denied), the call on top of the workspace (waiting, patient + own corner, controls, leave with confirm, Rejoin), screen kept awake; the call lives in the visit's ViewModel; End leaves it first. See visits.md. |
| VIDEO-03 Waiting room | Not built (D-07) | Decided in D-07: Flutter's page was unreachable and the backend has no presence endpoint. |
| PDF-02 PDF content tests | Done | Text-in-order tests for the general, dental, plan, photo and Russian summaries, plus a golden of the full summary's layout (`RECORD_GOLDEN=1` re-records and then fails on purpose; run again without it). See visits.md. |
| Follow-up booking (05/14) | Done (basic) | Book follow-up on End's done step opens the slot picker, then booking with the patient pre-filled (D-14). Quick picks, flag-only and the previous-visit link not built. See visits.md. |
| HOME-01 Home data layer | Done | Today's visits and the hero (in slot / next / 14-day look-ahead; cancelled and no-show never the hero) from the shared calendar day cache, look-ahead four days at a time; 60 s refresh gate. See home.md. |
| HOME-02 Home screen skeleton | Done | Header (photo, name, Refresh, Search, bell with dot), practice-time note, Today/Notifications/Follow-up glance, pull-to-refresh keeping content; the hero follows (HOME-03). See home.md. |
| HOME-03 Next patient, quick actions, timeline | Done | Hero card (Start 15 min before, Continue, Open chart, age, "In N min" ticker), empty/done/no-timezone cards, New appointment + Video Call chips (first free slot booked as video), today's timeline with folded earlier rows opening the Calendar panels. Prescription chip and medical-record flags not built. See home.md. |
| HOME-04 Attention centre + activity feed | Done | After today's schedule: "Attention required" (up to 4 unread with Mark as read, then the active tasks with "3 of 6 check-ins", 6 in all; "All caught up!"; "All notifications") and "Patient activity" (the 8 newest, time, type dot, 2-line body, "See all"); one read of the inbox's first page and of the active tasks (PREMIUM) with each read of Home; a tile reads and opens its target over Home (the inbox's opener, now shared), a feed row opens without reading. See home.md. |
| HOME-05 Patient search | Done | Home's Search button opens a full-screen search (keyboard up): first 8 by name, then every match by name, id, display id or phone digits (the Patients tab's matcher, now `Patient.matches`); Search key opens a single match; a row opens the patient over the search. See home.md. |
| ANALYTICS-01 Analytics data | Done | `AnalyticsRepository` (overview, trend with days 1–90, consultation types, engagement, SMS usage from/to), `AnalyticsRange` (inclusive, default last 7 practice days), demo handlers from the demo calendar. Verified on dev. See home.md. |
| ANALYTICS-02 Clinic performance card | Done | Last on Home: range button (localised "30 Oct – 5 Nov, 2026", picker with 7/30/90-day presets, a year back to 30 days ahead) and Export (CSV through the share sheet), today's four KPI tiles, PREMIUM engagement or a Premium teaser, per-block "Could not load" + Retry. Charts: ANALYTICS-03. See home.md. |
| ANALYTICS-03 Trend and visit types | Done | PREMIUM: Canvas trend line for the range (title names the days, ~7 date labels, tap a dot for "2 Nov · 3 visits") and the visit-type ring (shares adding up to 100, legend with counts), "No data yet", per-block Retry, spoken summaries. See home.md. |
| CHAT-01 Chat data | Done | `ChatRepository` (conversations, thread, start, read, send, search, upload), models tolerant of case and unknown types, every attachment URL absolute (`absoluteFileUrl`), in-memory demo chat. See chat.md. |
| CHAT-02 Conversation list | Done | Chat tab root (doctors and staff): cards with photo, name, preview (bold while unread), today/yesterday/date in practice time, 99+ pill; filter sheet (newest/oldest, unread only); people search after 300 ms; a result opens its conversation or starts one; read every 5 s only in front, on coming back and on pull. The thread is CHAT-03. See chat.md. |
| CHAT-03 Conversation thread | Done | Pushed thread: header (a patient's name opens their record), messages oldest→newest opened at the newest with practice-day dividers, board bubbles with role labels, practice-time times, status ticks, tappable links; optimistic send in order with "Not sent. Tap to retry"; read on open and on each new incoming; "New message" pill when scrolled up; 5 s polling in front only. Attachments show as labelled bubbles until CHAT-04…06. See chat.md. |
| CHAT-04 Image attachments | Done | Paperclip → attach sheet (Select Image, Take Photo); the photo (already a ≤ 1600 px JPEG from the picker) shows at once, uploads, then sends as an image (≤ 20 MB, else a banner); "Uploading file..." strip; image tiles with skeleton / failed / not-available states and the time pill; full-screen viewer with pinch zoom. See chat.md. |
| CHAT-05 Document attachments | Done | "Select Document" (pdf, doc, docx, jpg, png only; jpg/png go as images), document bubble with type icon, name, size ("242.2 KB") and time, opened outside the app; ≤ 20 MB; the shared document picker now takes a kind. See chat.md. |
| CHAT-06 Voice messages | Done | "Record Voice Message" replaces the composer with the dark recorder pill (live level, 0:07 counter, Cancel, Send); < 1 s "Recording too short"; the `.m4a` uploads and sends as `voice` with its seconds, retry only re-sends once uploaded; voice bubbles play one at a time with position and total, "Couldn't play the voice message" when a stream fails. See chat.md. |
| CHAT-07 Unread badge + open chat with a patient | Done | Patient Overview "Quick Actions" (New appointment, Send message, Create document; More actions waits for the ⋮ sheet): Send message opens the Chat tab on the patient's thread over the list, the header filled at once while `start` (find-or-create on the backend) resolves; failure says so and stays on the list. A conversation read drops the Chat badge at once. No Home "Message" (not on the board; Flutter's caller is dead code). See chat.md. |
| NOTIF-01 Notification data + type catalogue | Done | `NotificationsRepository` (the paged list newest first, read one, read all; the count stays the shell's), `AppNotification` tolerant of case and bad rows, every backend type (35 on dev) mapped to a kind with its chip, tap target, board look and a body template from the structured fields (never the English text); in-memory demo inbox. See notifications.md. |
| NOTIF-02 Inbox screen | Done | The Notifications tab (doctors: More / the bell; staff: their tab): "Notifications (n)" from the shell's count (99+), Mark all as read, Refresh, Settings, chips (no Tasks for staff), practice-day sections, board cards (tint + blue dot while unread, icon, label, relative time, 3-line body) with the board's actions; pages as you scroll; skeletons, empty states, banner + Retry keeping the old list. A tap reads the row (back on failure) and opens its target over the inbox: an appointment's Calendar panel (found on its practice day), the patient, the document viewer, Chat; gone → "This appointment no longer exists". See notifications.md. |
| NOTIF-03 Unread badge refresh | Done (push trigger with PUSH-02) | One count in the shell (Home bell, More tile, staff tab, inbox title), never polled, read again on: opening or refreshing the inbox, a read or mark-all (`NotificationReads`), app resume, Home refresh. A foreground push comes with PUSH-02 (D-15). See notifications.md. |
| TASK-01 Task data + gate | Done | `TasksRepository` (list with patient/status filters, one, check-ins, templates, create, cancel; typed errors, a 403 is `Forbidden`), models tolerant of case, unknown values and bad rows (legacy morning/afternoon/evening times still read: dev uses them), the day's times, a pending check-in shown missed once its time passed in the **patient's** zone; the PREMIUM gate is the existing `DoctorFeature.RemoteCareTasks`; in-memory demo tasks. See tasks.md. |
| TASK-02 Tasks list | Done | The Tasks tab (PREMIUM doctors): title and subtitle (once, not twice), Refresh, Use Templates, the docked Create Task button; Active / Completed / Overdue cards over every task that also filter; search plus localised status and category dropdowns; board cards (category tile, name in 2 lines, patient, status pill, progress, "3× per day", "4 Nov – 17 Nov 2026", "14 days"); skeletons, "No tasks found" + Create, "No tasks match your filters" + Clear filters, banner + Retry keeping the list; read on every return. Details, create and templates are TASK-03…05. See tasks.md. |
| TASK-03 Task details | Done | Pushed details: the info card (name, status, the patient linking to their record, category, input type and label, description, locale dates, duration, progress with Completed / Pending / Missed, "hasn't submitted anything yet"), check-ins newest first with values as written ("120", not "120.0") and missed by the **patient's** clock, a check-in's sheet (submitted in patient time), Cancel Task with its confirm while active (one request, back to the list with "Task cancelled successfully"); 404 "Task not found" vs "Couldn't load task" + Retry; read on open, return and pull (no 15 s poll). The inbox's task rows and "View Result" open it. See tasks.md. |
| TASK-04 Create task | Done | "Create Remote Care Task": the patient from the shared picker (now also booking's), name, description, category and input type, input label; even spacing (times, start, interval, the day's slots live, orange with how many won't fit) or custom times (sorted, no repeats, 1–15); start date, end date (cleared when the start passes it) or a duration ≥ 1; notes; a sticky Create; inline errors; Back with changes asks; the payload as the spec says, sending the slot count that fits; template prefill with the interval snapped up and said. See tasks.md. |
| TASK-05 Templates | Done | "Use Templates": the doctor's earlier tasks' settings (no editing exists): category tile, name, "Vital · 3× per day · Numeric", the day's times ("+3 more" past four); skeletons, "No templates yet" + Create Task, "Couldn't load templates" + Retry; a tap opens the form pre-filled (one per tap), a create closes the form and the list onto Tasks, Back returns to the list. Also: `ShifaSwitchRow`, one labelled switch row (the row toggles, the label names it) for the form, Settings and Services. See tasks.md. |

## Log

- 2026-11-04 · CORE-01 · KMP skeleton next to the Flutter code on `doc-app2.0`; both platforms compile; CI ported.
- 2026-11-04 · CORE-02 · Tokens from `docs/design/` in `ui/theme/`, Manrope fonts, token gallery screenshots.
- 2026-11-04 · CORE-03 · UI kit in `ui/widgets/` from `shifa-ds.css` and the boards; 52 screenshots; icon generator.
- 2026-11-04 · CORE-04 · Strings generated from the JSON (gaps filled, plurals, CMP escaping corrected), Uzbek Cyrillic via `tr()`, language storage.
- 2026-11-04 · CORE-05 · HTTP client with the new session rule and code-based errors; verified against dev (doctor tokens last 30 days there).
- 2026-11-04 · CORE-06 · Session repository and the hashed app-lock PIN.
- 2026-11-04 · CORE-07 · Native spikes on emulator and simulator: audio, PDF view, map pin work; FCM blocked by credentials; Daily skipped.
- 2026-11-04 · CORE-08 · Demo backend from real dev fixtures, debug menu.
- 2026-11-04 · CORE-09 · Practice clock, money, phone and password rules, platform helpers. M0 complete.
- 2026-11-04 · AUTH-01 · Splash with session restore, the BE-04 boot gate and the app's top-level navigation.
- 2026-11-04 · AUTH-02 · Sign In on the shared auth frame; real sign-in against dev verified.
- 2026-11-04 · AUTH-03 · Sign in with an e-mail code; system Back on the auth screens (missed in AUTH-02).
- 2026-11-04 · AUTH-04 · Forgot password with the shared password checklist.
- 2026-11-04 · AUTH-05 · Invitation key, routing to the doctor or receptionist registration.
- 2026-11-04 · AUTH-06 · Doctor registration step 1, existing patient, timezone step; picker sheet and pick field in the kit.
- 2026-11-04 · AUTH-07 · Registration step 2: birth date, gender, address, clinic and profession pickers, one profile PATCH.
- 2026-11-04 · AUTH-08 · Receptionist registration on the same screen (`register-clinic-staff`).
- 2026-11-04 · AUTH-09 · Sign-out question and neutral "You've signed out" on Sign In.
- 2026-11-04 · LOCK-01 · App lock: lock window, biometrics, PIN settings section; 4–6 digit PIN with explicit Unlock (D-09).
- 2026-11-04 · LOCK-02 · Inactivity timeout (30 days) and expiry checks on start, resume and touch. M1 done.
- 2026-11-04 · PROF-01 · Typed profile models and the shared profile repository (no screen).
- 2026-11-04 · SHELL-01 · Doctor shell: bar, per-tab stacks, Back, More sheet, start tab; placeholders per tab.
- 2026-11-04 · SHELL-02 · Clinic-staff shell (D-02: staff on mobile).
- 2026-11-04 · SHELL-03 · Unread badges and active-task count.
- 2026-11-04 · SHELL-04 · Feature gating on the plan's features (D-03).
- 2026-11-04 · SHELL-05 · Intelligence mode in the shell and the resume refresh.
- 2026-11-04 · PROF-02 · Profile root (doctor and staff), section routes as placeholders.
- 2026-11-04 · PROF-03 · Personal information panel, photo upload, shared profession picker; fragment 1.8.9 fixes picker crash.
- 2026-11-04 · PROF-04 · Contact & clinic address, invoicing details, Security with password change; shared profile form pieces.
- 2026-11-04 · PROF-05 · Dental documentation detection and the pinned profession catalogue tests.
- 2026-11-04 · PROF-06 · Practice timezone sheet with change warning; shared timezone list for registration and profile.
- 2026-11-04 · PROF-07 · Map pin picker on Contact; coordinates saved; geocoding waits for BE-15.
- 2026-11-04 · PROF-08 · Extended profile: biography, socials, service tags, certificates.
- 2026-11-04 · SVC-01 · Services & pricing list and editor.
- 2026-11-04 · SVC-02 · Service groups editor.
- 2026-11-04 · SUB-02 · Plans, Stripe checkout and Connect onboarding with the return check.
- 2026-11-04 · SET-01 · Settings screen with queued settings PATCH.
- 2026-11-04 · SET-02 · Language screen.
- 2026-11-04 · SET-03 · App-lock preview on the Settings Security row; M2 complete.
- 2026-11-04 · TZ-1, TZ-2 · Shared date/time formatters and DST tests; M3 started.
- 2026-11-04 · CAL-1 · Calendar models and the cached day repository.
- 2026-11-04 · CAL-2 · Today / next-appointment selectors and the appointment change bus.
- 2026-11-04 · CAL-3 · Month picker with occupancy shading.
- 2026-11-04 · CAL-4 · Calendar tab with the day agenda.
- 2026-11-04 · CAL-6 · Appointment panel: reschedule, duration, payment reminder, cancel.
- 2026-11-04 · CAL-7 · Booking a free slot (multi-slot range) with the patient picker; BE-33.
- 2026-11-04 · CAL-8 · Block time and the blocked-row panel; dropdowns scroll to their value.
- 2026-11-04 · LOC-1 · Locations list and editor, one shared location list; BE-34.
- 2026-11-04 · SCHED-1 · Schedule models, repository and validators.
- 2026-11-04 · SCHED-2 · Working hours screen.
- 2026-11-04 · LOC-2 · Map pin for locations (shared map picker).
- 2026-11-04 · PAT-01 · Patient models; M4 started.
- 2026-11-04 · PAT-02 · Patients repository; BE-33 corrected (name sort works).
- 2026-11-04 · PAT-03 · Patients directory.
- 2026-11-04 · PAT-04 · Create patient sheet; patient language stored as codes (decision).
- 2026-11-04 · PAT-05 · Patient detail (Overview); favourites shared with the directory.
- 2026-11-04 · PAT-06 · Medical info and Edit details; prophylaxis card moved to CLINIC-06.
- 2026-11-04 · PAT-07 · Appointments and Activity tabs; booking from the record through CAL-7's screen.
- 2026-11-04 · DOCS-01 · Document models and repository.
- 2026-11-04 · DOCS-02 · Photos to PDF in shared code (platform PDF writers were megabytes a page).
- 2026-11-04 · DOCS-05 · Document viewer (before DOCS-04, which opens it).
- 2026-11-04 · DOCS-04 · Documents tab.
- 2026-11-04 · DOCS-03 · Upload (PDF, photo, scan) from the Documents tab.
- 2026-11-04 · FORM-01 · Form models and the dental-chart codec.
- 2026-11-04 · FORM-02 · Forms repository.
- 2026-11-04 · FORM-03 · 025-2 PDF in the app (shared layout, platform text drawing).
- 2026-11-04 · CLIN-01 · Clinical engine API.
- 2026-11-04 · CLIN-02 · Clinical engine panel (explicit Apply, the board's).
- 2026-11-04 · FORM-04 · 025-2 editor with the resumable save pipeline.
- 2026-11-04 · FORM-05 · Forms tab, template sheet, Edit form on a form's PDF.
- 2026-11-04 · APPT-01 · Appointment detail model, visit kind, the visit route.
- 2026-11-04 · APPT-02 · Appointment detail, signature request and its poll.
- 2026-11-04 · AI-11 · Consultation notes, AI drafts, complete and the scribe status.
- 2026-11-04 · APPT-03 · Visit notes with a 2 s autosave and flush on leave.
- 2026-11-04 · APPT-04 · Visit workspace (General and 025-2 modes, helpers, documents, signature).
- 2026-11-04 · APPT-06 · Visit briefing sheet from the appointment details.
- 2026-11-04 · AI-03 · The recorder's live level for the recording pill.
- 2026-11-04 · AI-05 · Speech to text and transcription reports.
- 2026-11-04 · AI-06 · Dictation in the visit notes and the 025-2 editor.
- 2026-11-04 · SCRIBE-01 · AI scribe recording and upload from the visit.
- 2026-11-04 · PDF-01 · The visit summary PDF, made in the app.
- 2026-11-04 · APPT-07, SCRIBE-02 · End appointment with the summary document and the scribe wait.
- 2026-11-04 · APPT-08 · An AI-suggested ICD code carried into the new 025-2.
- 2026-11-04 · DENTAL-01 · The tooth map and the dental record codec.
- 2026-11-04 · DENTAL-02 · The dental visit record in free mode, autosaved, in the End checklist and the summary.
- 2026-11-05 · APPT-05 · Before/after treatment photos in the visit and its summary.
- 2026-11-05 · TP-01 · Treatment plan data the visit needs, with a demo plan.
- 2026-11-05 · DENTAL-03, TP-06 · The treatment plan in the visit: ticks, payment, End and summary.
- 2026-11-05 · DENTAL-04 · The dental plan editor and read-only plan chart.
- 2026-11-05 · VIDEO-01 · The Daily room token and the call client on both platforms.
- 2026-11-05 · VIDEO-02, VIDEO-03 · The video visit: pre-join page and the call in the workspace; no waiting room.
- 2026-11-05 · PDF-02 · Summary PDF content tests and a layout golden.
- 2026-11-05 · 05/14 · Book follow-up from the ended visit.
- 2026-11-05 · HOME-01 · Home's schedule and hero from the calendar cache.
- 2026-11-05 · HOME-02 · The Home tab: header, glance and refresh.
- 2026-11-05 · HOME-03 · Home's next patient, quick actions and today's timeline.
- 2026-11-05 · HOME-05 · Patient search from Home's header.
- 2026-11-05 · ANALYTICS-01 · Analytics models, range and repository.
- 2026-11-05 · ANALYTICS-02 · Home's Clinic performance card: range, export, KPIs, engagement.
- 2026-11-05 · ANALYTICS-03 · The trend line and visit-type ring on Clinic performance.
- 2026-11-05 · CHAT-01 · Chat models, repository, upload and file URLs.
- 2026-11-05 · CHAT-02 · The Chat tab's conversation list and people search.
- 2026-11-05 · CHAT-03 · The conversation thread with optimistic sending.
- 2026-11-05 · CHAT-04 · Photos in chat: attach, upload, tiles and the viewer.
- 2026-11-05 · CHAT-05 · Documents in chat: pick, send and open.
- 2026-11-05 · CHAT-06 · Voice messages in chat: record, send and play.
- 2026-11-05 · CHAT-07 · Send message from a patient, and the Chat badge drops on read.
- 2026-11-05 · NOTIF-01 · Notification data, the type catalogue and the demo inbox.
- 2026-11-05 · NOTIF-02 · The notifications inbox.
- 2026-11-05 · NOTIF-03 · The bell's count read again on every trigger but push.
- 2026-11-05 · TASK-01 · Remote-care task data, rules and demo tasks.
- 2026-11-05 · TASK-02 · The remote-care tasks list.
- 2026-11-05 · TASK-03 · Task details, check-ins and cancel.
- 2026-11-05 · TASK-04 · The create task form.
- 2026-11-05 · TASK-05 · Task templates; M9 done.
- 2026-11-05 · HOME-04 · Home's attention centre and activity feed; M6 done.
- 2026-11-05 · AI-01 · The Shifa AI client: streams, speech, briefing and visit facts.
- 2026-11-05 · AI-02 · The AI text renderer; the visit briefing shows headings, bullets and bold.
