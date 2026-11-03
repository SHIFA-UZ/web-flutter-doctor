> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 04 · Appointments, Video (Daily.co), Consultation Documentation, Dental, PDF

Scope read fully: `lib/features/appointments/**` (incl. `dental/`, `services/`, `presentation/widgets/`), `lib/core/services/daily_video_service.dart`, `lib/core/services/daily_flutter_stub.dart`, `lib/core/api/consultation_notes_api.dart`, `test/{appointment_pdf_service_test,dental_plan_editor_test,dental_plan_fulfill_panel_test}.dart`. Supporting files were grepped only (router, clinic actions/models, patient actions, ai_api, recorder session). Paths are relative to repo root. Anything I could not confirm from code is marked **UNVERIFIED**.

---

## 1. Appointment model and route args

`lib/features/appointments/domain/appointment_models.dart`

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | stringified backend `appointmentId` (`today_appointments_provider.dart:13`) |
| `patientName` | `String` | required |
| `patientId` | `String?` | may be null → screens fall back to `/api/calendar?day=` lookup (`in_person_appointment_screen.dart:53-84`) |
| `location` | `String` | `'Video Consultation'` or clinic name; `isVideo => location.toLowerCase().contains('video')` (`:73`) — **string sniffing, not a type field** |
| `start`, `end` | `TimeOfDay` | wall-clock in doctor tz; no date except `day` |
| `status` | `AppointmentStatus?` | enum `requested, confirmed, cancelled, completed, inProgress, noShow`; `fromString` maps `REQUESTED/CONFIRMED/CANCELLED/COMPLETED/IN_PROGRESS/NO_SHOW` (`:12-30`) |
| `reason` | `String?` | visit reason from booking |
| `photoUrl` | `String?` | |
| `briefingStatus` | `String?` | `PENDING|READY|FAILED|SKIPPED|GENERATING|null`; `hasVisitBriefing` (`:76-81`) |
| `attachmentCount` | `int` | default 0 |
| `day` | `DateTime?` | date-only, doctor tz |

- **No consultation-type enum** (video vs in-person derived from `location`). **No payment fields** on `Appointment`; `CalendarEntry.paymentStatus` exists (`calendar_models.dart:41`) but is **dropped** in `appointmentFromCalendarEntry` (`today_appointments_provider.dart:11-26`). The payment "PENDING" badge lives only in `calendar_screen.dart:3195,4366` (another analyst's area). The video token endpoint returns the `payment is required` error string, handled in `video_call_screen.dart:73-94`.
- Built from `CalendarEntry` only (`appointmentsFromCalendarEntries`, `:28-48`, filter `type==appointment && patientName non-empty && appointmentId!=null`, sorted by start minutes). Also built ad hoc in `lib/features/intelligence/presentation/open_appointment_route.dart:59-70` from `startLocal/endLocal/status/startAt` rows.
- Route args: `AppRoutes.inPerson='/appointment/in-person'`, `waitingRoom='/appointment/waiting-room'`, `videoCall='/appointment/video-call'` (`router.dart:44-46`), each with `settings.arguments as Appointment` in **both** the root router (`:98-116`) and the shell nested router (`:236-251`). Callers: `home_timeline_section.dart:189`, `home_clinical_workflow.dart:69`, `calendar_screen.dart:4150`, `clinic_treatment_plan_detail_screen.dart:512`, `clinic_treatment_plans_tab.dart:409`, `open_appointment_route.dart:56` — all `appt.isVideo ? videoCall : inPerson` via `ShellScope.pushNamed`.
- Per-appointment extra fields fetched by screens from `GET /api/appointments/{id}`: `signatureRequested`, `patientSignedAt`, `patientSignatureImageBase64`, `linkedPlanId`, `linkedPlanTitle`, `fulfilledLineIds` (`in_person_appointment_screen.dart:397-446`).
- Providers: `todayAppointmentsProvider`, `nextUpcomingAppointmentProvider` (14-day lookahead, `today_appointments_provider.dart:9,121-143`), `consultationNotesForAppointmentProvider(id)`, `draftNotesForAppointmentProvider(id)` (`consultation_notes_provider.dart`).

---

## 2. In-person appointment screen

`lib/features/appointments/presentation/in_person_appointment_screen.dart` (3176 lines, `ConsumerStatefulWidget`).

### 2.1 Layout by region

- **Header** `AppointmentConsultationHeader` (`consultation_documentation_widgets.dart:539-701`): back arrow (→ `_leaveAppointmentScreen`, persists drafts first), avatar (`NetworkImage`, raw not cached — violates project rule), patient name, status pill (`appointmentStatusColor/Label`, `:138-170`), meta row "age – ID" (age from `Patient.general.birthDate`, `:127-136`), schedule line `consultationScheduleLine(start,end)` using `MaterialLocalizations.formatTimeOfDay`, location line.
- **Body** inside `Responsive.screenPadding`; `stackVertically = PlatformLayout.useSinglePane(context)` (`:1491`) → phone = single scroll column (`:2871-2884`), else horizontal `Flex`:
  - **Left: Documents card** (`DocumentationSectionCard` title `documents`, collapsible to 52 px rail `:2890-2940`): `GroupedPatientDocumentsList` grouped by clinical bucket order `laboratory, imaging, clinical, forms, private, other, uncategorized` (`:10-18,20-62`), tiles open `doc.url` via `url_launcher` or "Request access" (`requestDocumentAccessWithClient`), then `ConsultationDocumentUploadStrip` + hint row `documentsFinalizeHint`.
  - **Right: Notes card** titled `notes` / `docModeDental` / `docMode0252` by `_documentationType` (`'general' | 'dental' | '025-2'`, `:118`). Title trailing: focus-mode button (general), fullscreen (dental; 025-2 on phone), and the `⋮` `PopupMenuButton` (`:1545-1744`) with items: `fromShifaAi`, `fromLast0252Form`, `pick_before`, `pick_after` (only in general/dental), divider, mode switches `general`, `dental` (only if `isDentalDocumentationProfession`), `025-2`.
  - Inside notes card, top: `AppointmentTreatmentPlanPanel` (hidden when `appointment.isCompleted` or no `selectedClinicIdProvider`/numeric patientId, `:1750-1787`).
  - Helper region (when `_notesSectionsExpanded`): AI drafts list (orange cards, max-height 260, per-draft `Discard`/`Save as note`, ICD suggestion rows with `Apply` → switches to 025-2 and calls `applyIcdSuggestion`, `:1800-2206`); saved AI notes carousel `< n/N >` with `+` append (`:2208-2419`); "From last 025-2 form" card (complaints/diagnosis/treatment, `+` append, `:2422-2674`).
  - General mode: `ConsultationSoapSection` (ExpansionTile with 4 `DoctorSpeechTextField`s, `consultation_soap_section.dart`), free-notes `DoctorSpeechTextField` (expanding; font 17), then **Start AI Notes** button (PRO gate `doctorFeatureProvider(DoctorFeature.aiNotes)`, `:2745`) that swaps to `InlineVoiceRecorderBar` (`:2746-2772`).
  - Dental mode: `_buildDentalDocumentationColumn` = `DentalVisitDocumentationPanel` + `AppointmentPlanFinanceCard` when `_activePlanId && clinicId` (`:490-553`).
  - 025-2 mode: `_build0252Panel` → `AppointmentForm0252Panel` (`:1339-1391`).
  - Before/after image chips (`Wrap` of `Chip` with delete, `:2791-2843`).
- **Footer** `ConsultationStickyFooter` (`consultation_documentation_widgets.dart:725-852`): "Patient signed" / "Waiting for patient signature" line, `Request signature` secondary button, `End appointment` destructive primary. Pinned on wide layouts (`:3023-3033`), in-flow on phones.
- **Full-screen overlays** (Stack `Positioned.fill`, black54 scrim): dental (`:3116-3175`) and 025-2 (`:3055-3114`). General mode has a separate **focus mode** pushed as `fullscreenDialog` route (`:328-395`).

### 2.2 Actions

| Action | Code | Behaviour |
|---|---|---|
| Chronic disease warning | `:609-639` | post-frame; `patientByIdProvider` snapshot; `showChronicDiseaseWarning` once |
| Fetch signature/plan status | `:397-446` | `GET /api/appointments/{id}`; if `signatureRequested && !signed` → `Timer.periodic(10s)` re-fetch; also seeds `_linkedPlanId` → `_loadActivePlanContext` |
| Request signature | `:555-607` | `PUT /api/appointments/{id}/request-signature {}`; starts 10 s poll; snackbars |
| Plan selection | `:448-488` | `fetchTreatmentPlanDetail` + `fetchFulfillmentCandidates(planId, appointmentId)` |
| Pick before/after image | `:649-720` | bottom sheet camera/gallery; desktop camera → `file_picker`; `image_picker` `imageQuality: 85`; kept in memory as `XFile` lists; marks unsaved |
| Start AI notes | `:722-724` | shows inline recorder |
| Upload scribe recording | `:726-831` | web: `blob:`/`data:` URL → bytes, name `ai_notes_<ms>.wav`; native: file bytes `.m4a`. `POST multipart /api/consultations/upload-recording` field `audio`, fields `appointmentId`, `language` (uz/ru/en, default uz). 202 → `_awaitingScribe=true`; 429 → `AiStreamException('RATE_LIMIT').userFacingMessage`; else error body shown |
| Wait for scribe | `:833-865` | modal `preparingAiDocumentation` + `waitForScribeReady` (poll `GET /api/appointments/{id}/scribe-status` every 3 s up to 120 s, `consultation_notes_api.dart:252-271`) |
| Draft confirm/discard | `:2062-2193` | `POST /api/ai/draft/{id}/confirm {patientId?,appointmentId?}` / `POST /api/ai/draft/{id}/discard` (`ai_api.dart:263-281`), then invalidate both note providers |
| Append AI/025-2 text | `:286-298` | general → appends to free notes; dental → `appendClinicalNotes` |
| Persist general notes | `:185-214` | `PUT /api/appointments/{id}/consultation-notes {body}` with `composeConsultationNotesPdf` text (SOAP labels + free notes); only if `_hasUnsavedChanges` and non-empty |
| Hydrate notes | `:216-242` | once: manual (non-AI) notes from `GET …/consultation-notes` fill empty free notes; also pushed into dental panel when ready |
| Mode switch | `:1604-1681` | unsaved-changes dialog `cancel / discardAndSwitch / saveAndSwitch`; save calls `requestSave()` on 025-2 or dental panel; discard clears SOAP/free/images |
| End appointment | `:867-1335` | see 2.3 |
| Leave | `:244-247` | `flushSave()` dental + persist general, then `appPop` |

### 2.3 End-appointment sequence (`_endAppointment`, `:867-1335`)

1. Resolve `patientId` (fallback `GET /api/calendar?day=today`); abort with snackbar if none.
2. `now` in doctor tz → `dateStr yyyy-MM-dd`, `timeStr HH:mm`.
3. Compose `structuredAndFree` (general) or dental `flushSave()` + `dentalClinicalNotesPdfText` + `buildDentalPdfBilling`.
4. `applyPendingIfNeeded(silent)` → `POST /api/treatment-plans/{planId}/appointments/{apptId}/fulfill {lineIds}`; failure aborts.
5. `recordSessionPaymentIfNeeded()` → `POST /api/clinics/{clinicId}/finance/payments`; failure aborts.
6. `PUT /api/appointments/{id}/complete {clinicId?, doctorNotes?, awaitingScribe?}` → `CompleteAppointmentResult{scribePending,hasDocumentation,hasScribeNote}`; failure aborts (status stays). Then `invalidateAppointmentRelatedProviders`.
7. If `scribePending || _awaitingScribe` and no scribe note → modal poll (step above).
8. Re-`GET /api/appointments/{id}` for signature image/date and `fulfilledLineIds`; re-fetch plan detail.
9. Build `AppointmentPdfTreatmentPlanSection` (dental+plan) from fulfilled line ids with `fdi` parsed from `specialtyMetadata` JSON; in that case `dentalBilling` is nulled (`:1092-1136`).
10. Re-fetch notes + drafts; `composeVisitDocumentationText` (AI block first, then doctor block; local SOAP text only if no manual note exists server-side).
11. If nothing to put in PDF and no images/dental work → snackbar (`appointmentEndedDocumentationSaved` / `aiDocumentationWillAppearShortly` / `noItemsToSave`) and pop. **No PDF.**
12. Else `generateAppointmentPdf` (client-side, see §6) and `POST multipart /api/patients/{pid}/documents` (`title`, `category=APPOINTMENT_NOTE`, `appointmentId`), invalidate, snackbar, pop.

### 2.4 State machine / timers / guards

- Documentation mode: `general ⇄ dental ⇄ 025-2`; default from profession once profile loads (`_applyProfessionDocumentationDefaultIfReady`, `:304-326`), never overriding an explicit user choice.
- Timers: signature poll 10 s (`:433,570`), dental autosave debounce 1200 ms (`dental_visit_documentation_panel.dart:146`), scribe poll 3 s/120 s. **General notes have no autosave timer** — persisted only on leave/mode switch/end.
- Unsaved guard: `_hasUnsavedChanges` set by controller listeners, image add/remove, dental/025-2 callbacks. Guard fires only on mode switch; back button silently persists; there is **no `PopScope`/`WillPopScope`** for system back/swipe (drafts may be lost on Android back — **UNVERIFIED** whether `appPop` route wraps it).
- Responsive: `PlatformLayout.useSinglePane` (mobile shell or content width < `splitPaneBreakpoint`, `responsive.dart:88-93`); compact < 720 px for card paddings (`consultation_documentation_widgets.dart:192,752`). `_applyMobileDocumentationLayout` forces 025-2 fullscreen on phones (`:264-276`).

---

## 3. Video call screen

`lib/features/appointments/presentation/video_call_screen.dart` (3481 lines). Documentation half is a near copy of §2 (same helpers, same `_endAppointment`, `:1081-1562`), differences noted below.

### 3.1 Join flow

- `initState` post-frame: `_checkChronicDiseaseWarning`, `_initializeVideoCall`, `_fetchSignatureStatus` (`:351-355`).
- `_initializeVideoCall` (`:550-651`): `DailyVideoService(api).getVideoToken(appointmentId:int)` → `POST /api/video/token {appointmentId, roomName?}` via `postNoUnauthorizedCheck` (so 403 does not log out, `daily_video_service.dart:12-37`). Response `{token, roomUrl, roomName}` (`:53-71`).
  - **Web (`kIsWeb`)**: store `_roomUrl/_token`, render `DailyVideoEmbedWeb` = `HtmlElementView` iframe `src=roomUrl?t=token`, `allow="camera; microphone; display-capture; autoplay; fullscreen"` (`daily_video_embed_web.dart:37-54`). Daily **Prebuilt** UI provides all controls; app's own controls hidden (`:1773`).
  - **Native**: `CallClient.create()`, subscribe `events`, `join(url, token, clientSettings: camera+mic enabled & publishing)` (`:587-613`). Events handled via `whenOrNull` (`callStateUpdated.left` → snackbar `videoCallEnded`; `inputsUpdated` → `_isMuted/_isVideoOff`; participant joined/updated/left → `setState`, dispose `VideoViewController`) (`:653-696`).
- Error path: `errStr` stripped of `Exception:`; `_localizeDoctorVideoError` (`:57-99`) maps backend English strings → l10n `videoCallJoinWindowClosedMessage` (403 "ended / 15 min after end"), `videoCallNotYetAvailableMessage` ("5 minutes before"), `videoCallPaymentRequiredMessage`, `callErrorOccurred`; shown in the canvas with **Retry** (`:1718-1767`) and a 6 s snackbar.

### 3.2 In-call UI

- Two-pane `Flex` (vertical on single-pane): left flex 3 = white card with black rounded video canvas (loading spinner `videoCallConnecting` / error / `_buildVideoView` / `videoCallNotAvailableShort`); right flex 2 = Documents card (desktop/tablet only, `:1833`) + Notes card.
- Native video: single participant → own camera; otherwise remote full + local PiP 120×160 bottom-right (`:886-949`). Only `remoteParticipants.first` is rendered (no grid).
- `_CallControls` (native only, `:3402-3461`): camera toggle, mic toggle, screen share (**UI toggle only — not supported in daily_flutter 0.37, `:744-751`**), end call (`_endVideoCall` → `leave()`, dispose controllers; does **not** complete the appointment).
- No call timer, no chat, no participant list. **Notes side panel** = same documentation panel, plus `VisitFactsPanel(appointmentId, patientId)` at top (`:2691-2697`, AI visit facts: `GET /api/ai/visit-facts/appointment/{id}`, `POST /api/ai/visit-facts/commit`, `ai_api.dart:339,375`). The in-person screen does **not** include `VisitFactsPanel`.
- Notes fullscreen overlay (`_notesPanelFullScreen`, `:2361-2674`) duplicates the whole `⋮` menu a third time.
- Menu values differ from in-person (`show_ai/show_0252/switch_*` vs `ai/0252/general`), wrapped in `Future.microtask` hacks (`:1990-2197`).
- **No Start-AI-Notes recorder in video screen** (no `_uploadScribeRecording`); scribe only arrives server-side (`completeResult.scribePending`), `awaitingScribe` not sent (`:1216-1221`).
- End appointment: first `_endVideoCall()` (`:1085`), then same pipeline as §2.3, but finishes with `popUntil(isFirst)` (`:1402,1530`) instead of single pop.
- `dispose` (`:951-971`): cancels poll, `_endVideoCall()` (fire-and-forget async), disposes all controllers including SOAP ones.

### 3.3 Waiting room

`waiting_room_screen.dart` (95 lines): static page — avatar, "`<name>` is waiting.", "Open the room when you're ready…", Back / Open room (→ `AppRoutes.videoCall`). **No polling, no presence check, no data fetch.** Nothing in `lib/` navigates to `AppRoutes.waitingRoom` (only the route table) → **dead screen**. Uses `translate(...) ?? 'fallback'` pattern (rule violation).

---

## 4. Dental module

`lib/features/appointments/dental/`

### 4.1 Codec (`dental_chart_codec.dart`)

- FDI/ISO 3950 two-digit keys. Permanent quadrants 1–4 (UR, UL, LL, LR) × teeth 1–8; primary 5–8 × 1–5 (`:7-18`). Display order per row: UR 18→11, UL 21→28, LR 48→41, LL 31→38.
- `DentalDentition {permanent, primary}`; meta key `__dentition__` inside 025-2 chart maps; `__general__` key for services not tied to a tooth (`:21-24`).
- Legacy keys `UR1…` normalised to FDI (`normalizeToothKey`, `migrateLegacyToothKeys`, `:126-177`).
- 025-2 form chart rows: `TOP_{row}_{cell}` / `BOTTOM_…` (16 cells permanent, 10 primary) and history rows `TOP_HIST_{i}_{cell}` + `_date`/`_doctor` (`:137-146,179-274`); `prefillFromLatest0252` moves editable rows into history when a new 025-2 opens (`:278-315`). **No per-surface (mesial/distal/…) data and no condition codes** — a "tooth" holds a list of service lines only.
- **Payload sent to backend** (both visit doc and plan editor, `dental_visit_documentation_panel.dart:803-815`, `dental_plan_editor_panel.dart:226-238`):
  ```json
  {"version":2,"dentition":"permanent|primary",
   "teeth":{"16":[{"serviceId":12,"title":"…","amountMinor":100000,"currency":"UZS"}],
            "__general__":[…]},
   "discountPercent":10.0,"notes":"…"}
  ```
  Plan-editor lines use `serviceKey`/`catalogItemId` instead of `serviceId`.

### 4.2 FDI chart widget (`dental_fdi_chart.dart`)

Stateless; horizontal-scroll rows with Roman quadrant labels I/II over upper, IV/III under lower, anatomy captions (hard-coded English `incisors/Canine/pre-molars/molars/wisdom`, `:158-169`, **not localized**), tooth tiles 45×60 with `DentalToothPainter` shapes (incisor/canine/premolar/molar + root lines), count badge, `toothPlanStates` colouring (`planned` light brand, `completed` brand, `partial` orange, `:58-70`), optional `SegmentedButton` dentition toggle. Tap → `onToothTap(fdi)`.

### 4.3 Visit documentation panel (`dental_visit_documentation_panel.dart`, 1309 lines)

Two modes decided by `activePlanId` (`_isPlanMode`, `:136`):

- **Free mode** (no plan): on init loads `GET /api/doctors/me/services` (filters `isActive!=false`, first positive price among `prices[]`, groups by `groupId/groupName/groupSortOrder`, `:248-307`) and `GET /api/appointments/{id}/dental-documentation` (`:344-401`); if notes empty, fills from manual consultation notes (`:403-418`). Body: intro, FDI chart (tap → services bottom sheet with group list ‖ services list, selected lines with delete, `:925-1134`), "General services" button, discount % field, clinical notes `DoctorSpeechTextField`, totals card (subtotal · line count, discount, total; `_computeSubtotal` **ignores lines whose currency differs from the first**, `:779-796`). Autosave: debounce 1200 ms → `PUT /api/appointments/{id}/dental-documentation` (payload §4.1, `:829-872`); `flushSave`, `requestSave` (snackbar), `persistForPdf`; `dispose` fires a final save if dirty (`:238-246`).
- **Plan mode**: no loading of saved doc; chart shows plan's `dentalPlanDocumentation.teeth` counts + states from `LineDetailDto.specialtyMetadata.fdi`; tap → fulfil sheet with checkboxes over `FulfillmentCandidateDto` for that tooth (or general), already-fulfilled disabled (`:562-639`); banners all-done / load-failed+retry / no open lines (`:641-684`); clinical notes field. `applyPendingIfNeeded` posts fulfil (`:437-467`). `buildPlanPdfSection` (`:469-516`) exists but screens rebuild the same thing inline (duplicate).
- PDF helpers: `dentalClinicalNotesPdfText`, `buildDentalPdfBilling(l10n)` (`:880-923`, header `dentalPdfHeader`).

### 4.4 Plan editor (`dental_plan_editor_panel.dart`) and read-only view

- Editor (planning wizard, another analyst's screen embeds it): clinic `PlanServiceOption` catalog, search when >8, grouped two-column picker ≥640 px else sliver list, discount %, notes, total. `buildLineRequests()` flattens to treatment-plan lines `{catalogItemId?, title, quantity:1, unitPriceMinor, discountMinor, currency, sortOrder, status:'PLANNED', specialtyMetadata:'{"fdi":"16","dentition":"permanent","source":"PLAN_WIZARD"}'}` distributing the % discount per line with remainder on the last (`:255-316`); tested in `test/dental_plan_editor_test.dart`.
- `DentalPlanReadonlyView` (`dental_plan_readonly_view.dart`): progress chip, FDI chart with `toothStatesFromLines` (COMPLETED/other/CANCELLED → completed/planned/partial), legend.

### 4.5 Which professions enable it

`dental_documentation_professions.dart:25-59`: substring match `dental|odont|stomat|tish` or exact/contains any of a list (`dentist, orthodontist, endodontist, pediatric dentist, periodontist, prosthodontist, oral surgeon, maxillofacial…, стоматолог, ортодонт, пародонтолог, …`). Dental mode becomes default for those (`documentationTemplateForProfession`), and the `dental` menu item is shown only for them.

---

## 5. Form 025-2 panel, treatment plan panel, plan finance card

- **`AppointmentForm0252Panel`** (`appointment_form_0252_panel.dart`): thin wrapper that loads `patientByIdProvider(patientId)` and embeds `PatientFormScreen(templateId:'025-2', isEmbedded:true, existingForm:null)`, wiring `registerSaveHandler` (for Save&Switch) and `registerIcdApplyHandler` (AI ICD suggestion → `_icdSearchCtrl`). The form itself (ICD-10 search `GET /api/icd10/search`, form create `POST /api/patients/{id}/forms`, PDF generation, document link) is in `patient_form_screen.dart` — another analyst's area. Saving a 025-2 does **not** complete the appointment.
- **`AppointmentTreatmentPlanPanel`** (`appointment_treatment_plan_panel.dart`): `GET /api/treatment-plans?clinicId&patientId&status=ACTIVE&planKind=COMPREHENSIVE` (`fetchTreatmentPlansForPatient`); dropdown "none / plans"; auto-selects the only plan or `linkedPlanId`; shows linked banner `appointmentLinkedPlanBanner {{id}} {{title}}` and progress `dentalPlanProgress {{done}}/{{total}}`; `onPlanSelected` → parent loads detail/candidates; `onPlanLinked` → parent re-fetches appointment. **Selection is local only — nothing is PUT to link the plan to the appointment client-side** (linking appears to happen server-side on fulfil — **UNVERIFIED**).
- **`AppointmentPlanFinanceCard`** (`appointment_plan_finance_card.dart`): loads `GET /api/treatment-plans/{id}/detail` summary → Total / Paid / Outstanding (`minor/100` with 2 decimals + currency); amount field + method dropdown `CASH | CARD_EXTERNAL | TRANSFER`; `recordSessionPaymentIfNeeded()` (called only from End appointment) → `POST /api/clinics/{clinicId}/finance/payments {treatmentPlanId, amountMinor, currency, method, linkedAppointmentId}`; records once; shows `appointmentPlanFinanceRecorded {{amount}}`.

---

## 6. PDF service

`lib/features/appointments/services/appointment_pdf_service.dart` (+ `_data.dart`, `_translations.dart`)

- Only **one document**: the appointment summary generated at End appointment (both screens). 025-2 and legal PDFs are elsewhere.
- Library `pdf ^3.10.8`; A4, 40 pt margins; font `assets/fonts/DejaVuSans.ttf` loaded via `rootBundle` (Cyrillic/Latin). **Signature fonts `DancingScript`/`Sacramento` are NOT used here** (only in `lib/features/legal/pdf/partner_signature_image.dart`). Logo `assets/branding/shifa_logo.png` optional.
- Content (`_buildReportContentAsList`, `:117-253`): header (logo + "SHIFA", clinic, weekday/full date/time, appointment id, teal rule) → title band "Appointment Summary" → metadata card (patient name/ID/DOB/gender | doctor/specialization/license/type/duration) → **Clinical Notes** (chunked 35 lines) → **Services provided** dental billing table (tooth/service/amount, subtotal, discount %, total) → **Treatment plan** (plan #/title, total/paid/outstanding, fulfilled this visit, payment this visit (method)) → Diagnosis / Prescriptions / Recommendations / Follow-up (all **always null** from screens — dead sections) → dental sterilization attestation (Uzbek, hard-coded `:114-115`, shows signature image) → Signatures (doctor line; patient signature image + "Signed electronically on …" + "via SHIFA Patient App", or blank line) → footer (generated-by, `Page n / total`, confidential). Image pages appended per before/after `XFile`.
- Translations: static Dart map `AppointmentPdfTranslations._byLocale` with **only `en` and `uz`** (`:103-203`); `ru` falls back to `en`. Day/month names likewise.
- Delivery: **not downloaded/shared/printed anywhere** — bytes are uploaded as a patient document (`uploadPatientDocumentWithClient`) and viewable later from the Documents list via `url_launcher`. `share_plus`/`pdfx` are used only by other features (`document_viewer_screen.dart`, finance/dashboard export). No `printing` package.
- Test `test/appointment_pdf_service_test.dart` only asserts byte length > N for three fixtures.

---

## 7. Endpoints table

| METHOD path | Request | Response parsed | Notes / errors |
|---|---|---|---|
| `GET /api/calendar?day=YYYY-MM-DD` | — | list; find row by `appointmentId|id`, read `patientId` | fallback for missing patientId (`in_person:68`, `video:117`) |
| `GET /api/appointments/{id}` | — | `signatureRequested`, `patientSignedAt`, `patientSignatureImageBase64`, `linkedPlanId`, `linkedPlanTitle`, `fulfilledLineIds[]` | polled every **10 s** while signature pending |
| `PUT /api/appointments/{id}/request-signature` | `{}` | 200 | snackbar on non-200 |
| `GET /api/appointments/{id}/consultation-notes` | — | `[ConsultationNoteDto]` | non-200 → `[]` |
| `PUT /api/appointments/{id}/consultation-notes` | `{body}` | 2xx | throws otherwise |
| `GET /api/appointments/{id}/draft-notes` | — | `[DraftNoteDto]` | non-200 → `[]` |
| `POST /api/consultations/upload-recording` (multipart) | file `audio` (`.wav` web / `.m4a` native), `appointmentId`, `language` | 202 accepted | 429 → rate-limit message; other → body shown |
| `GET /api/appointments/{id}/scribe-status` | — | `{status, hasDocumentation, hasScribeNote}` | polled **3 s**, timeout **120 s** |
| `PUT /api/appointments/{id}/complete` | `{clinicId?, doctorNotes?, awaitingScribe?}` | `{scribePending, hasDocumentation, hasScribeNote}` | non-2xx throws → "something went wrong", stays on screen |
| `POST /api/ai/draft/{draftId}/confirm` | `{patientId?, appointmentId?}` | 200 | raw `http.post` w/ bearer |
| `POST /api/ai/draft/{draftId}/discard` | — | 200 | |
| `GET /api/ai/visit-facts/appointment/{id}` / `POST /api/ai/visit-facts/commit` | — | (video screen only, `VisitFactsPanel`) | |
| `POST /api/video/token` | `{appointmentId:int, roomName?}` | `{token, roomUrl, roomName}` | `postNoUnauthorizedCheck`; 403 → "call ended / join window closed" message; backend strings "not yet available… 5 minutes before", "Payment is required…" |
| `GET /api/appointments/{id}/briefing` / `POST …/briefing/retry` | — | `{appointmentId,status,briefingText,documentCount,generatedAt,error}` | sheet re-polls every **3 s** while `PENDING|GENERATING` |
| `GET /api/doctors/me/services` | — | `[ {id,title,isActive,isFreeConsultation,groupId,groupName,groupSortOrder,prices:[{amountMinor,currency}]} ]` | dental free mode catalog |
| `GET /api/appointments/{id}/dental-documentation` | — | payload §4.1 | 200 only |
| `PUT /api/appointments/{id}/dental-documentation` | payload §4.1 | 200 | debounced 1200 ms autosave; error message from `message|error` |
| `GET /api/treatment-plans?clinicId&patientId&status=ACTIVE&planKind=COMPREHENSIVE` | — | `[TreatmentPlanSummaryDto]` | |
| `GET /api/treatment-plans/{planId}/detail` | — | `TreatmentPlanDetailDto{summary,lines,installmentPlans,dentalPlanDocumentation}` | |
| `GET /api/treatment-plans/{planId}/fulfillment-candidates?appointmentId=` | — | `[FulfillmentCandidateDto]` | |
| `POST /api/treatment-plans/{planId}/appointments/{apptId}/fulfill` | `{lineIds:[int]}` | detail | null → abort end |
| `POST /api/clinics/{clinicId}/finance/payments` | `{treatmentPlanId, amountMinor, currency, method, linkedAppointmentId}` | `PaymentHistoryItem` | |
| `POST /api/patients/{pid}/documents` (multipart) | file, `title`, `category` (`APPOINTMENT_NOTE` / `OTHER_MEDICAL` for strip), `appointmentId?` | `PatientDocument?` | |
| `POST /api/patients/{pid}/documents/{docId}/request-access` | — | — | from locked tile |
| `GET /api/patients/{pid}/documents`, `GET /api/patients/{pid}` , `GET /api/patients/{pid}/forms` | — | via providers | documents list, header patient, last 025-2 helper |

---

## 8. Models

- `Appointment`, `AppointmentStatus` — §1.
- `ConsultationNoteDto {id:int, body?, subjective?, assessment?, plan?, source:'MANUAL'|'AI_DRAFT', createdAt}`; `isFromAi`, `displayText` strips `TRANSCRIPT:` tail (`consultation_notes_api.dart:6-51,274-282`).
- `DraftNoteDto {id:String, aiLabel, body, createdAt, icdSuggestions:[IcdSuggestionDto{code,title,confidence?,isTop}]}` (`:94-152`).
- `CompleteAppointmentResult {scribePending, hasDocumentation, hasScribeNote}`; `ScribeStatusDto {status:String('none' default), hasDocumentation, hasScribeNote}`.
- `VideoTokenResponse {token, roomUrl, roomName}` (all non-null, crashes on missing key).
- `AppointmentBriefingResult {appointmentId:int, status:String('NONE'), briefingText?, documentCount, generatedAt?, error?}`.
- `ConsultationDroppedFile {bytes:Uint8List, name}`.
- PDF: `AppointmentPdfData` (30 fields, see `appointment_pdf_data.dart:66-143`), `AppointmentPdfDentalBilling {header, lines, subtotalMinor, discountPercent?, totalMinor, currency}`, `AppointmentPdfDentalLine {tooth, serviceTitle, amountMinor, currency}`, `AppointmentPdfTreatmentPlanSection {planId, planTitle?, planTotal/Paid/OwedMinor, currency, fulfilledThisVisit, sessionPaymentMinor?, sessionPaymentMethod?}`.
- Dental: `DentalDentition`, `DentalToothPlanState {none, planned, completed, partial}`, `DentalHistRowSnapshot {sortIndex, cells, dateIso?, doctor?}`, private `_ServiceOption {id,title,amountMinor,currency,isFreeConsultation,groupId?,groupName?,groupSortOrder}`; tooth line = untyped `Map<String,dynamic>`.
- Clinic (consumed): `TreatmentPlanSummaryDto {id, clinicId, patientId?, title?, status, planKind, totalMinor, paidMinor, owedMinor, currency, planPaymentStatus, linesCompletedCount, linesTotalCount, …}`, `LineDetailDto {id, catalogItemId?, title, quantity, unitPriceMinor, discountMinor, currency, sortOrder, status, lineTotalMinor, specialtyMetadata?(JSON string), …}`, `FulfillmentCandidateDto {lineId, title, fdi?, dentition?, unitPriceMinor, quantity, discountMinor, currency, lineTotalMinor, status, toothMatch}`, `TreatmentPlanDetailDto {summary, lines, installmentPlans, dentalPlanDocumentation?}`.

---

## 9. Platform / third-party → KMP recommendation

| Flutter | Where | KMP equivalent |
|---|---|---|
| `daily_flutter ^0.37` (native) + iframe Prebuilt (web) | `video_call_screen.dart`, `daily_video_embed_web.dart` | **Daily Android SDK (`co.daily:client`) + Daily iOS SDK (Swift)** behind a common `expect class CallClient` with a small event flow (`joined/left/participantUpdated/inputsUpdated/error`). Render via `AndroidView(VideoView)` / `UIKitView(VideoView)`. Keep UI controls in Compose (mute/cam/leave). Simplest alternative that matches today's web behaviour: Daily **Prebuilt in a WebView** on both platforms (needs camera/mic permission bridging; iOS WKWebView supports getUserMedia since iOS 14.3). Recommend native SDKs for quality, WebView only as fallback/spike. |
| `image_picker` (camera/gallery) + `file_picker` | before/after photos, document strip | expect/actual: Android `ActivityResultContracts.PickVisualMedia`/`TakePicture`/`OpenMultipleDocuments`; iOS `PHPickerViewController`/`UIImagePickerController`/`UIDocumentPickerViewController`. Or one lib (e.g. `calf`/`peekaboo`/`FileKit`) if already adopted elsewhere. |
| Web drag-and-drop (`consultation_document_upload_web.dart`) | strip | drop; not applicable on mobile. |
| `record` (wav 16 kHz web / aac-lc 32 kbps 16 kHz m4a native) | AI scribe + speech fields | Android `MediaRecorder` (AAC/MPEG_4, 16 kHz mono); iOS `AVAudioRecorder` (AAC, 16 kHz mono). Upload as `.m4a` — backend already accepts m4a. |
| `pdf` (client-side layout) | `generateAppointmentPdf` | **Recommend backend-side PDF**: screens already push all inputs to the server (notes, dental doc, plan, signature, complete). Add `POST /api/appointments/{id}/summary-pdf` (or have `/complete` produce the APPOINTMENT_NOTE document) and upload only before/after images as attachments. Removes DejaVu font bundling, Cyrillic handling, duplicated money formatting and the 3-way client/backend state reconciliation. Fallback if backend cannot: Android `PdfDocument`/iOS `UIGraphicsPDFRenderer` via expect/actual (text layout only; tables become manual). |
| `share_plus` / `printing` | not used in this area | none needed. |
| `url_launcher` | open document URL | `UriHandler` (Compose MP). |
| `http` blob fetch of web recordings | n/a on mobile | drop. |

---

## 10. Quirks, bugs, dead code, tech debt (do not copy)

1. **~2.5 k lines duplicated** between `in_person_appointment_screen.dart` and `video_call_screen.dart` (identical `_fetchSignatureStatus`, `_requestSignature`, `_endAppointment`, draft/AI helper UI, menu); video screen duplicates its own menu a third time in the fullscreen overlay (`video_call_screen.dart:1978-2319` vs `2401-2653`).
2. **Leak**: in-person `dispose()` disposes only `_notesController`; four SOAP controllers and their listeners are never disposed (`in_person_appointment_screen.dart:641-647`).
3. **Dead files**: `video_call_screen.dart.backup` (2063 lines) and `video_call_screen_unified_menu.dart.patch` committed in `lib/`; `WaitingRoomScreen` is routable but unreachable; `DentalVisitDocumentationPanelState.buildPlanPdfSection` and `persistForPdf` unused by screens; PDF sections diagnosis/prescriptions/recommendations/followUp never populated; `AppointmentPdfData.dateOfBirth/gender/licenseNumber` always null.
4. **Video `_isWebPlatform` heuristics** (`:214-223`) and triple `setState(_isVideoInitialized=true)` (`:581-624`) — leftover debugging. Screen-share button is a no-op on native (`:744-751`). Web "mute/camera" toggles only flip local booleans (`:698-742`).
5. **End-call vs end-appointment** semantics: red hang-up only leaves the Daily room; appointment stays `IN_PROGRESS` until "End appointment". No confirmation dialog for either.
6. **Completion ordering risk**: `/complete` is called **before** the PDF is generated/uploaded; any later failure leaves a completed visit without its summary document and the user on the screen with a generic error (`:998-1029,1317-1329`).
7. `isVideo` by substring on `location` (`appointment_models.dart:73`); payment status dropped from the model.
8. `_patientIdProvider` fetches **today's** calendar to find patientId — fails for appointments on other days (`:53-84`), and the same code is pasted again inside `_endAppointment` (`:876-903`).
9. Hard-coded English in UI: `'Suggested Diagnoses (AI):'`, `'Recommended'`, `'Apply'`, `'Applied: …'` (`:1928-2049`), FDI anatomy captions, `'$durationMin min'` in PDF; `translate(k) ?? 'fallback'` pattern throughout; PDF has no `ru` table.
10. Dental subtotal silently skips lines in a second currency (`dental_visit_documentation_panel.dart:778-796`); discount parsed from free text; lines are untyped maps.
11. Signature polling (10 s) never stops if the backend never flips `patientSignedAt`; two separate timers may run if `_fetchSignatureStatus` and `_requestSignature` both start them (cancelled first, so OK, but fragile).
12. `ref.refresh(...)` results ignored (`:1355,1383`, lint); `withOpacity` deprecated mixed with `withValues`.
13. `AppointmentConsultationHeader` uses `NetworkImage` (project rule says `cached_network_image`).
14. Dental panel `dispose()` fires an async save after unmount using a cached `ApiClient` — works but is an unsupervised request; prefer explicit flush on navigation.
15. `_waitForScribeUi` shows an undismissable dialog for up to 120 s with no cancel.
16. AI scribe recorder only exists on the in-person screen; video visits rely on server-side scribe (asymmetric feature set, probably unintended).

---

## 11. Proposed tickets

### APPT-01 · Appointment domain model + navigation args [S]
Route · Spec §1 · API: none
Build: `Appointment` data class (id:Long, patientId:Long?, patientName, kind enum `IN_PERSON|VIDEO` derived once from location, status enum, start/end `LocalTime` + `day LocalDate`, reason, photoUrl, briefingStatus, attachmentCount, paymentStatus), mapper from calendar entry, typed nav route `Appointment(id)` carrying id + kind (re-fetch rest).
Done when: both screens open from id without `/api/calendar` patientId lookup; unit test for mapper/status parsing.
Depends: calendar analyst's `CalendarEntry` model.
Fix: drop `isVideo` substring sniff; keep paymentStatus.

### APPT-02 · Appointment detail + signature repository [S]
Route · Spec §2.2, §7 · API: `GET /api/appointments/{id}`, `PUT …/request-signature`
Build: `AppointmentRepository.detail(id)` → `AppointmentDetail{signatureRequested, patientSignedAt, signatureImageBase64, linkedPlanId, linkedPlanTitle, fulfilledLineIds}`; `requestSignature(id)`; a `Flow` that polls every 10 s only while `requested && !signed`, cancelled with the screen.
Done when: footer state (none/waiting/signed) reflects server; poll stops on sign or leave.
Depends: APPT-01.
Fix: single timer owner; no duplicate start.

### APPT-03 · Consultation notes repository (manual + AI drafts) [M]
Route · Spec §2.2, §8 · API: `GET/PUT …/consultation-notes`, `GET …/draft-notes`, `POST /api/ai/draft/{id}/confirm|discard`
Build: DTOs `ConsultationNote`, `DraftNote`, `IcdSuggestion`; `displayText` with `TRANSCRIPT:` stripping; `composeVisitDocumentationText` port with unit tests; `saveDoctorNotes` debounced autosave (e.g. 2 s after last edit + on leave) replacing today's save-only-on-leave.
Done when: notes hydrate once from manual notes; drafts confirm/discard refresh; autosave verified by test of debounce.
Depends: APPT-01.
Fix: autosave general notes; typed DTOs.

### APPT-04 · Consultation shell screen (shared by in-person & video) [L]
Route `appointment/{id}` · Spec §2.1, §2.4 · API: via APPT-02/03, patients documents
Build: one Compose screen with header (cached avatar, status pill, age/ID, schedule line), adaptive layout (phone single column; tablet two panes), Notes card with mode menu (`General | Dental | 025-2`, gated by profession), helper region (AI drafts/notes carousel/last 025-2), SOAP expandable + free notes, before/after photo chips, Documents list grouped by bucket + upload strip, sticky footer (request signature / end). Mode state machine + unsaved-changes dialog + system-back guard that flushes drafts. Video variant simply adds a video pane on top (VIDEO-02).
Done when: all §2.2 actions work for general mode; mode switch guard; back flushes; snapshot tests for phone/tablet.
Depends: APPT-01..03, patients-documents repo (other analyst).
Fix: one implementation, no duplication; all strings localized incl. "Suggested Diagnoses/Recommended/Apply".

### APPT-05 · Before/after photos + document upload (pickers) [M]
Route · Spec §2.2, §9 · API: `POST /api/patients/{pid}/documents` multipart
Build: expect/actual image picker (camera/gallery) and multi-file picker; compress images before upload; upload strip with progress + per-file failure toast; invalidate documents list.
Done when: works on Android & iOS; files >N MB compressed; cancel path safe.
Depends: APPT-04.
Fix: n/a (drag-drop is web-only, skip).

### APPT-06 · Pre-visit briefing sheet [S]
Route · Spec §7 · API: `GET …/briefing`, `POST …/briefing/retry`
Build: bottom sheet with states NONE/SKIPPED/PENDING|GENERATING (poll 3 s)/FAILED(retry)/READY (markdown-ish text).
Done when: polling cancels on dismiss; retry disabled while loading.
Depends: APPT-01.
Fix: use a coroutine loop, not nested `delayed` callbacks.

### SCRIBE-01 · Audio recording + upload-recording [M]
Route · Spec §2.2, §9 · API: `POST /api/consultations/upload-recording` (multipart `audio`, `appointmentId`, `language`)
Build: expect/actual recorder (AAC 16 kHz mono m4a), inline recorder bar (timer, waveform optional, cancel/confirm), PRO-tier gate, upload with 202/429/other handling, `awaitingScribe` flag in screen state.
Done when: recording survives rotation/background; 429 shows friendly message; file deleted after upload.
Depends: APPT-04, subscription provider (other analyst).
Fix: make it available on video visits too.

### SCRIBE-02 · Scribe status polling + completion gate [S]
Route · Spec §2.3 step 7 · API: `GET …/scribe-status`
Build: `waitForScribeReady(interval 3 s, timeout 120 s)` as cancellable coroutine; blocking dialog with **Cancel** that proceeds without AI note.
Done when: unit test with fake clock; cancel path completes visit.
Depends: SCRIBE-01, APPT-07.
Fix: cancelable dialog.

### APPT-07 · End-appointment orchestrator [M]
Route · Spec §2.3 · API: fulfil, payments, `PUT …/complete`, documents upload
Build: single use-case `EndAppointment(state)` executing steps 1–12 with explicit result types; re-orderable so document generation happens before/without blocking completion (see PDF-01); error mapping per step; emits progress for UI.
Done when: each step unit-tested with fakes; failure after `/complete` is surfaced as "completed, document pending" with retry instead of generic error.
Depends: APPT-02, APPT-03, DENTAL-03, PDF-01.
Fix: don't leave completed visit without a summary; no `popUntil(isFirst)` surprise.

### VIDEO-01 · Daily token + call client abstraction [M]
Route · Spec §3.1 · API: `POST /api/video/token`
Build: `VideoTokenRepository` (no auto-logout on 403; error mapper for ended/not-yet/payment/other → localized); `expect class DailyCallClient { join(url, token); leave(); setMic(Boolean); setCamera(Boolean); events: Flow<CallEvent>; participants: StateFlow<...> }` with Android (`co.daily:client`) and iOS (Daily Swift SDK via bridge) actuals.
Done when: join/leave/mute/camera round-trip on both OSes; 403 shows join-window message with Retry.
Depends: APPT-01.
Fix: no `_isWebPlatform` heuristics; screen share omitted (not supported today either).

### VIDEO-02 · Video call screen [L]
Route `appointment/{id}` (kind=VIDEO) · Spec §3.2 · API: VIDEO-01
Build: video pane (remote full + local PiP, placeholder avatar, connecting/error/retry states), Compose call controls (cam, mic, leave with confirm), permissions flow (camera/mic), keep-screen-on, `VisitFactsPanel` slot at top of notes; reuse APPT-04 for the documentation side; "End appointment" leaves the call first.
Done when: remote joins/leaves update UI; leaving call does not complete visit; end-visit path identical to in-person.
Depends: APPT-04, VIDEO-01.
Fix: distinguish "leave call" vs "end visit" clearly in UI copy.

### VIDEO-03 · Waiting room (decide: delete or implement) [S]
Route `appointment/{id}/waiting` · Spec §3.3 · API: none today
Build: recommend **not porting** (unreachable in Flutter). If product wants it: static page + "Open room"; add patient-presence polling only when backend offers it (**UNVERIFIED** endpoint).
Done when: decision recorded; if built, reachable from home card for video visits.
Depends: VIDEO-02.
Fix: dead route.

### DENTAL-01 · FDI codec + chart composable [M]
Route · Spec §4.1, §4.2 · API: none
Build: `DentalChartCodec` (FDI tables, dentition, legacy key normalisation, payload v2 serialisation with typed `ToothServiceLine`), `FdiChart` composable (Canvas tooth shapes, count badge, plan-state colours, dentition segmented toggle, localized anatomy captions).
Done when: codec unit tests (legacy→FDI, payload round-trip); chart renders 32/20 teeth; tap returns FDI.
Depends: none.
Fix: localize captions; typed lines.

### DENTAL-02 · Dental visit documentation panel (free mode) [M]
Route · Spec §4.3 · API: `GET /api/doctors/me/services`, `GET/PUT …/dental-documentation`
Build: services catalog (grouped picker sheet), per-tooth/general lines, discount %, clinical notes, totals; autosave 1200 ms debounce + flush on leave; subtotal must handle mixed currencies explicitly (group or block).
Done when: payload equals §4.1 byte-for-byte for same input; autosave/flush tested.
Depends: DENTAL-01, APPT-04.
Fix: mixed-currency silent skip.

### DENTAL-03 · Treatment-plan link, fulfilment and finance card [M]
Route · Spec §4.3 plan mode, §5 · API: `GET /api/treatment-plans?…`, `…/detail`, `…/fulfillment-candidates`, `POST …/fulfill`, `POST /api/clinics/{id}/finance/payments`
Build: plan picker (auto-select single/linked), plan-mode chart (states from `specialtyMetadata.fdi`, candidates per tooth, checkbox sheet, banners), finance card (totals, session payment amount+method, record once), `applyPendingIfNeeded` & `recordSessionPaymentIfNeeded` as use-cases used by APPT-07.
Done when: fulfil then payment then complete order preserved; fulfil failure blocks completion with message.
Depends: DENTAL-01, clinic plan DTOs (other analyst).
Fix: keep one `buildPlanPdfSection` (not three copies).

### DENTAL-04 · Plan editor + read-only plan chart [M]
Route (planning wizard, other analyst's screen) · Spec §4.4 · API: none (emits lines)
Build: `DentalPlanEditor` (catalog search/groups, discount split with remainder-on-last, `buildLineRequests()`), `DentalPlanReadonlyView` (progress chip, legend).
Done when: port of `dental_plan_editor_test.dart` passes (discount total 30000 for fixture).
Depends: DENTAL-01.
Fix: none.

### APPT-08 · 025-2 panel embedding [S]
Route · Spec §5 · API: via patient-forms feature
Build: host the shared 025-2 form composable in embedded mode exposing `save()` and `applyIcd(code,title)`; wire unsaved flag and Save&Switch.
Done when: ICD "Apply" from an AI draft switches mode and fills the form search field.
Depends: APPT-04, patients analyst's form screen.
Fix: none.

### PDF-01 · Appointment summary document strategy [M]
Route · Spec §6, §9 · API: new `POST /api/appointments/{id}/summary-document` (**backend ticket, UNVERIFIED availability**) or keep client-side
Build: Preferred: backend renders the summary (notes, dental billing, plan, signature, sterilization line, en/uz/ru) and stores it as `APPOINTMENT_NOTE`; client uploads before/after photos as separate documents and triggers generation. Fallback: expect/actual PDF renderer (Android `PdfDocument`, iOS `UIGraphicsPDFRenderer`) reproducing §6 sections with a bundled Cyrillic font.
Done when: document appears in patient documents after completion in all three languages; images attached.
Depends: APPT-07.
Fix: add `ru` translations; drop never-populated sections.

### PDF-02 · PDF content tests / golden [S]
Route · Spec §6 · API: none
Build: if client-side fallback chosen: golden tests for general, dental billing, plan section fixtures (port of `appointment_pdf_service_test.dart` with real assertions on text content, not byte length).
Done when: 3 fixtures pass on CI.
Depends: PDF-01.
Fix: meaningful assertions.
