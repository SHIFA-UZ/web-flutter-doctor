> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 05 · Patients, Medical Forms, Documents, Care Partnerships — Flutter → KMP/CMP analysis

Scope: `lib/features/patients/**`, `lib/state/patients/**`, `lib/features/care_partnerships/**`, `lib/core/utils/patient_warning_utils.dart`, `lib/core/widgets/person_avatar.dart`. All paths relative to repo root. Every file in scope was read in full. Items marked **UNVERIFIED** could not be confirmed from the Flutter code alone (usually backend contract details).

File inventory (lines): `patient_detail_panel.dart` 3462 (a `part of patients_screen.dart`), `patient_form_screen.dart` 2761, `patients_screen.dart` 1401, `patient_detail_overview.dart` 995, `patients_directory_panel.dart` 868, `patient_actions.dart` 836, `care_partnership_screens.dart` 417, `patient_form_models.dart` 349, `patient_detail_helpers.dart` 337, `patient_models.dart` 322, `care_partnership_repository.dart` 242, `document_viewer_screen.dart` 213, `create_patient_sheet.dart` 211, `find_therapy_partner_screen.dart` 207, `patients_provider.dart` 84, `document_category.dart` 78, `pdf_viewer_web.dart` 58, `patient_documents_provider.dart` 38, `patient_forms_provider.dart` 35, `pdf_viewer_stub.dart` 16.

---

## 1. Patients screen

### 1.1 Entry points & route args
- Shell tab (kept alive): `lib/features/shell/presentation/main_shell.dart:379` → `PatientsScreen()` with no args.
- Route `AppRoutes.patientsWithSelection = '/app/patients/selection'` (`lib/app/router.dart:49`), registered twice (root router `router.dart:165-195`, shell nested router `router.dart:280-309`). Args are either a `String` patientId or a `Map`:
  - `patientId` (any → `toString()`), `documentId`, `documentTitle` (String?), `openDocumentViewer` (bool, `== true`), `clinicId` (int or parseable string), `clinicScheduleReturn` (nested map parsed by `ClinicScheduleReturnInfo.fromRouteArgs`, `lib/features/clinic/presentation/clinic_schedule_return_info.dart:15-33`: `doctorProfileId`, `doctorDisplayName`, `clinicScheduleTimeZone`, optional `clinicStreetAddress`).
  - Producers: chat header tap passes bare `conv.participantId` string (`lib/features/chat/presentation/chat_screen.dart:950-954`); FCM document-access notifications push a map with `openDocumentViewer: type == 'DOCUMENT_ACCESS_APPROVED'` (`lib/app/app.dart:333-356`); clinic workspace roster passes `clinicId` + `clinicScheduleReturn` (UNVERIFIED exact producer, inferred from `patients_screen.dart:614-618`).
- `PatientsScreen` ctor mirrors those: `initialSelectedId`, `initialDocumentIdToSelect`, `initialDocumentTitle`, `initialOpenDocumentViewer`, `clinicWorkspaceId`, `clinicScheduleReturn` (`patients_screen.dart:591-619`).

### 1.2 Layout: master-detail vs stacked
- Single-pane decision: `PlatformLayout.useSinglePane(context)` → `Responsive.useSinglePane` with `splitPaneBreakpoint = 720` (`lib/core/layout/responsive.dart:22,88-92`).
- Inside `LayoutBuilder`, `isWide = constraints.maxWidth >= splitPaneBreakpoint - 48 (= 672)` (`patients_screen.dart:825-826`).
- Three render modes (`patients_screen.dart:828-890`):
  1. single-pane AND a patient selected → `_buildMobilePatientDetail` (back TextButton "Patients" clears `_selectedId`, otherwise pops; detail panel rendered with `mobileScrollEmbedded: true` inside one `SingleChildScrollView`) (`patients_screen.dart:932-983`).
  2. not wide → directory panel only, full height.
  3. wide → `Row(flex 7 directory, gap AppDesignSystem.sectionGap, flex 13 detail)`. Detail shows a spinner card when `_selectedId != null && _selected == null` (overlay patient still loading).
- Optional route back button row above (`showRouteBack = (appCanPop || initialSelectedId != null || clinicScheduleReturn != null) && !(singlePane && selected)`, `patients_screen.dart:802-821`). `_handlePatientsRouteBack` pops and, if `clinicScheduleReturn` is set, re-pushes `ClinicDoctorScheduleRoute` on the root navigator as a fullscreen dialog (`patients_screen.dart:900-920`).

### 1.3 Data loading & selection
- `initState`: `ref.read(patientsProvider.notifier).loadPatients()` (full list, all pages); if `initialSelectedId` not in current list, fetch an "overlay" patient via `GET /api/patients/{id}?clinicId` and prepend it to the sidebar list (`patients_screen.dart:629-665, 709-714`).
- `ref.listen(shellProvider)` reloads the list every time the Patients tab becomes active (`patients_screen.dart:775-779`).
- `ref.listen(patientsProvider)`: on desktop only, keeps `initialSelectedId` selected (re-fetching overlay if missing) or auto-selects the first patient when nothing is selected (`patients_screen.dart:781-800`). Mobile never auto-selects.
- `_handlePatientSelection(id)`: sets selection, `ref.refresh(patientDocumentsProvider(key))`, then shows the blocking chronic-disease warning dialog (`showChronicDiseaseWarning`, `lib/core/utils/patient_warning_utils.dart:5-83`) if `chronicDisease` is non-empty and ≠ `'None'` (`patients_screen.dart:716-739`).
- Favorites: `Set<String> _favoriteIds` in widget state only — **not persisted**, lost on tab rebuild (`patients_screen.dart:627, 922-930`).
- `_reloadPatients(showFeedback)`: reload + refresh docs for selected + snackbar "listRefreshed" (`patients_screen.dart:672-702`).

### 1.4 Directory panel (`patients_directory_panel.dart`)
- Props: `patients`, `selectedId`, `favoriteIds`, `onSelect`, `onCreatePatient`, `onRefresh` (`:11-28`).
- All filtering/sorting/paging is **client-side** over the fully loaded list:
  - Search (`:59-73`): case-insensitive `contains` over `name`, raw `id`, first phone (`patientPhoneDisplay`) and display id `P-00001` (`patientDisplayId`, `patient_detail_helpers.dart:161-165`). Any keystroke resets page to 0.
  - Tabs `PatientListTab { all, recent, favorites, followUps }` (`patient_detail_helpers.dart:7`); `recent` = last 20 of the list reversed (list is name-sorted, so "recent" is really "last by name" — see §10), `followUps` = `clinicalStatus == 'FOLLOW_UP'` (`patient_detail_helpers.dart:197-216`).
  - Status filter popup `PatientStatusKind { active, atRisk, followUp }` mapped from `clinicalStatus` `'AT_RISK' | 'FOLLOW_UP' | else active` (`patient_detail_helpers.dart:54-63`; UI `:508-562`).
  - Sort popup `PatientSortOption { nameAsc, nameDesc, recent }`; `recent` sorts by newest document date (`patientLastActivityDate`, docs only) (`patient_detail_helpers.dart:167-172, 218-241`).
  - Pagination: `_pageSize = 10`, `_totalPages = ceil(visible/10)` (min 1), footer label `patientsPaginationLabel(start,end,total)` + prev/next + window of ≤5 page numbers centred on current + "…" when >5 (`:38-39, 83-95, 721-823`).
- Two layouts: compact toolbar (`PlatformLayout.useCompactToolbar`) renders a `CustomScrollView` with header slivers + list + pagination + bottom-nav clearance (`:116-262`); wide renders header + `Expanded ListView.separated` + pagination footer (`:264-389`).
- Row (`_PatientListRow :623-719`): avatar initials (local `_PatientAvatar`, duplicates `PersonAvatar`), name, subtitle `"<age>, <gender> · <phone>"`, status pill, chevron. Header shows count badge `patientsCountBadge(total)` (`app_localizations.dart:7050`), filter button, "New Patient" primary button, tabs, search, refresh icon, sort button.
- Clinic-workspace scoping: the directory itself is **not** clinic-scoped; only the overlay patient fetch, documents key, prophylaxis editor, uploads and request-access pass `clinicId` (`patients_screen.dart:641, 667-670`; `patient_documents_provider.dart:10-25`).

### 1.5 "for-assignment" list
- `fetchPatientsForAssignmentWithClient` (`patient_actions.dart:538-591`): loops `GET /api/patients/for-assignment?page=N&size=500&sort=fullName,asc`, accepts either a bare JSON list (then stops) or a Spring page `{content, totalPages}`; dedupes by id, sorts by lowercase name. Exposed via `patientsForAssignmentProvider` (`patients_provider.dart:55-78`). Consumers are outside this area: `lib/features/calendar/presentation/calendar_screen.dart`, `lib/state/auth/auth_controller.dart` (reset on logout, UNVERIFIED purpose).

### 1.6 Create patient — two divergent implementations
| | `_openCreatePatientModal` (`patients_screen.dart:1011-1198`) | `showCreatePatientSheet` (`create_patient_sheet.dart:29-211`) |
|---|---|---|
| Used from | Patients directory "New Patient" | Home quick action (`home_quick_action_navigation.dart:27`), Calendar assign flow (`calendar_screen.dart:5723`) |
| Fields | Full name*, phones (`MultiplePhoneFields`, optional), email, address, language dropdown (default `'uzbek'`, options `:985-996`), gender (`'', Male, Female, Other`), blood group (`'', A+…O-`), allergies, birth date (picker 1900..today, initial 1990) | Full name*, phones, email, address, language, birth date — **no gender/blood group/allergies** |
| Validation | only `name.trim().isNotEmpty` (`:1175`); nothing else validated | same (`:142`) |
| On success | reload list, auto-select created, snackbar `patientCreated` | optional reload (`reloadPatientsList`), snackbar, returns `Patient` |
| On error | strips `"Exception: "`, shows raw message if it contains `'already exists'`, else `createFailed: msg` | same |
- Language values are stored as lowercase English words (`'uzbek'`), displayed by capitalising the first letter (not localized).
- Request body built by `createPatientWithClient` (`patient_actions.dart:314-375`): `name`, `phones[]` (+ `phone` when exactly one), `email`, `address`, `birthDate` `YYYY-MM-DD` or null, `language`, `photoUrl` (always null), `chronicDisease`, optional `gender/bloodGroup/allergies` when non-empty. 409 → `message` or "Patient with this phone number already exists."; 400 → `message` or "Invalid request. Please check the phone number."

### 1.7 Create account for patient / Send test SMS
- `_createAccount` (`patients_screen.dart:1259-1291`): `POST /api/patients/{id}/create-account` → `{username, oneTimePassword}` → non-dismissible dialog with two copyable credential rows and "password shown once" warning (`:1293-1401`); reloads patient list so `hasAccount` flips. Trigger: `_PatientPortalCard` on Overview (`patient_detail_panel.dart:3104-3153`).
- `_SmsReminderCard` (`patient_detail_panel.dart:3181-3427`), shown only when `profileAllProvider.profile['smsRemindersAllowed'] == true` (`:260-262, 297`): toggle `smsReminderEnabled` (PATCH), dropdown `smsReminderHoursBefore` ∈ {24, 1} (PATCH), "Send test SMS now" → `POST /api/patients/{id}/send-test-sms` (`patient_actions.dart:467-489`; 403 "SMS reminders are not enabled for your account.", 503 "SMS service is not configured on the server."). All disabled when patient has no phone.

---

## 2. Patient detail panel (`patient_detail_panel.dart`, `patient_detail_overview.dart`)

`PatientDetailPanel` props: `patient?`, `brand`, `clinicWorkspaceId?`, `onUploadOptions(Patient)`, `onCreateForm(Patient)`, `formatDate`, `selectedDocumentId?`, `documentTitleForViewer?`, `openDocumentViewer`, `isFavorite`, `onToggleFavorite?`, `onPatientDataRefresh?` (clinic split view), `mobileScrollEmbedded` (`:4-36`). Also embedded by `lib/features/clinic/presentation/clinic_workspace_screen.dart:970-984`.

`build` (`:1556-1745`): when `patient == null` → "Select a patient" card. Otherwise watches `patientDocumentsProvider(key)` and `patientFormsProvider(id)`; derives `activities` from docs (`buildPatientActivities`, max 6) and `genderFromForm` (first form with non-empty gender). Desktop: header + `TabBar` + `TabBarView` (6 tabs). Mobile: header + sticky tab bar (`_StickyTabBarDelegate`) + a single tab body rebuilt via `ListenableBuilder` on the controller (not a `TabBarView`), either inside its own `CustomScrollView` or as a plain `Column` when `mobileScrollEmbedded` (`:1619-1710`). `didUpdateWidget` refreshes documents on patient change (`:79-89`).

### 2.1 Header section (`_buildPatientHeaderSection :1000-1038`)
- `PatientHeroHeader` (`patient_detail_overview.dart:68-206`): avatar, name, status pill ("Active Patient" or At Risk / Follow-up), optional "AI Risk Detected" pill (`patient.atRisk`), "Last visit: d Mon yyyy" (derived from **newest document date**, English month abbreviations hardcoded, `patient_detail_helpers.dart:174-195`), favorite star, "AI Summary" (`ShifaSecondaryButton`, only when `doctorFeatureProvider(DoctorFeature.patientBriefing)`) → `patientBriefingProvider.notifier.generate(id, name)` (outside scope), more (⋮).
- Summary stats row (`:192-249`): Age, Gender (form fallback), Phone (first), Birth date `dd.MM.yyyy`, Patient ID `P-<id padded 5>`, Language. 2-column wrap under 720px.
- `PatientAiCopilotCard` (`patient_detail_overview.dart:250-539`): streaming Q&A via `aiApiProvider.streamAi(messages, question, language, patientId)`, conversation capped at 12 messages, hidden unless `DoctorFeature.askShifaAi`. (AI API itself is out of scope.)

### 2.2 More-actions sheet (`_showHeroMoreActions :98-190`)
Make appointment · Upload document (`onUploadOptions`) · Create Form (`onCreateForm`) · Create treatment plan (clinic only → `TreatmentPlanWizardSheet.show(clinicId, initialPatientId)`) · Generate briefing (feature-gated) · Find therapy partner (`AppRoutes.findTherapyPartner`, args `{patientId:int, patientName}`) · Care partnerships (`AppRoutes.carePartnerships`). The last two are deferred with `addPostFrameCallback` after the sheet closes.

### 2.3 Tab 0 · Overview (`_buildOverviewTab :251-349`)
Two columns ≥760px (3:2) else stacked.
- Left: `ClinicalSummaryCard` (chronic disease via `l10n.translateChronicDisease`, allergies, blood group, language) · `PatientVisitTimelineCard(patientId:int)` (from home feature, out of scope) · `RecentActivityCard` (activities, "View full history" → tab 5).
- Right: `QuickActionsCard` (New Appointment → make-appointment dialog; Send Message → `openChatWithPatient(ref, id)` which sets `chatPendingPatientIdProvider` and switches shell tab to Chat (`lib/features/chat/application/open_chat_with_patient.dart`); Create Document → upload options; More Actions) · `_SmsReminderCard` (if allowed) · `_PatientPortalCard` · `AiFollowUpSuggestionsCard` — purely local heuristics (`buildAiFollowUpSuggestions`, `patient_detail_helpers.dart:304-337`: atRisk, followUpRequired, no documents, no account, else routine), max 3. Not AI.
- Activity derivation (`patient_detail_helpers.dart:259-302`): per document, icon/title by title substring (`'blood'`, `'mri'`, startsWith `'form'`) or category `BLOOD_TEST`/`MRI`; sorted desc; `take(6)`.

### 2.4 Tab 1 · Medical Info (`_buildMedicalInfoTab :351-388`)
- `_GeneralInfo` (`:2511-3023`), per-field immediate saves via `PATCH /api/patients/{id}`:
  - Read-only rows: patientId (raw id), DOB, email, `formattedLocation` (street, city, district, region; falls back to legacy `address`; `patient_models.dart:205-220`), language.
  - Phones: N text fields + "Add phone number" + explicit Save → `phones: [...]` (`_savePhones :2704-2736`).
  - Chronic disease dropdown (22 fixed English keys `:2527-2550`, labels via `translateChronicDisease`; `'None'` sent as `''` to clear) → PATCH + snackbar `chronicDiseaseUpdated`.
  - Gender dropdown, Blood group dropdown → PATCH immediately on change; Allergies text → PATCH on keyboard submit only (`:3015`).
  - `_isUpdating` guard disables all inputs during a save; `onUpdate` callback → `onPatientDataRefresh ?? loadPatients()` + `invalidate(patientByIdProvider(id))`.
- `_ClinicProphylaxisEditor` (clinic only, `:2273-2437`): `GET /api/prophylaxis/settings?patientId&clinicId` (null body = no row), dropdown interval 1..60 months (default 12), enabled switch (default true), "Last sent" text, Save → `PUT /api/prophylaxis/settings`.

### 2.5 Tab 2 · Appointments (`_buildAppointmentsTab :390-517`, mobile `:1173-1227`)
- `patientDoctorAppointmentsProvider(id)` → `GET /api/patients/{id}/appointments` (history with the logged-in doctor only). Card: date, status pill (`patientAppointmentStatusLabel`: REQUESTED/CONFIRMED/CANCELLED/COMPLETED/IN_PROGRESS), time range, location or "Video call"/"In clinic", reason. Times use **`DateTime.toLocal()`** (`:436-437, 1112-1113`) — violates the practice-timezone rule.
- "New Appointment" → `_showMakeAppointmentDialog` (`:619-979`): date picker bounded by today in doctor TZ (`getTodayInTimezone`), loads free slots via `calendarProvider.notifier.loadDay(day, doctorTimeZone)` and filters `EntryType.freeSlot`, optional end-time dropdown from `consecutiveEndTimesForFreeSlot`, Clinic/Video choice chips, Confirm → `calendarProvider.notifier.bookFreeSlotRemote(day, slot, patientId, doctorTimeZone, location: 'Video Consultation' | 'Clinic Address', reason: 'Check Up', isVideo, endExclusive)` then `invalidateAppointmentRelatedProviders`, `invalidate(patientDoctorAppointmentsProvider)`, `refreshCalendarDay`. Hardcoded English `location`/`reason` strings are sent to the backend (`:927-930`). Calendar internals are out of scope.

### 2.6 Tab 3 · Documents (`_buildDocumentsTab :1747-1970`, mobile `:1346-1554`)
- Header "Document History", refresh icon (`ref.refresh(patientDocumentsProvider)` + 1s snackbar), ⋮ menu: Upload document, Create Form, Create Task (`AppRoutes.createTask`, args `{patientId:int}`).
- Title search (`_documentSearchQuery`, substring, case-insensitive).
- Deep link: when `openDocumentViewer && selectedDocumentId` present in loaded docs, pushes `DocumentViewerScreen` once (`_documentViewerOpenedFromDeepLink`) (`:1907-1929`).
- Row (`_buildDocumentList :1972-2270`):
  - Linked form lookup: `forms.firstWhere(f.documentId == doc.id)`; fallback parses title `Form (\w+)` + same date, last resort same template (`:1983-2019`).
  - Display title: if form 025-2 (linked form or title matches `Form 025-2…`) → `"${l10n.form0252MedicalDocument} (${date})"` else raw title.
  - Meta: date, category chip (`findDoctorCategory(d.category)`; teal when medical result), group icon with tooltip when `isSharedWithTeam`, and for locked docs a byline "Uploaded by <creator>" (`'Unknown'` → "Another user"; **Uzbek sentence hardcoded** for locale `uz` at `:2149-2151`).
  - Actions: Edit (pencil, only linked form && !locked) → `AppRoutes.patientForm` with `{patient, templateId, existingForm}` then refresh forms+docs; ⋮ "Request access" (locked only) → `POST …/request-access?clinicId` → snackbar `requestAccessSent` + refresh; Open (pdf icon) enabled only when `canView && url non-empty` → `DocumentViewerScreen` via `ShellScope.push(MaterialPageRoute)`; on return refresh docs (`_openDocument :2440-2466`).
  - Selected doc (`selectedDocumentId`) gets a 2px brand border.
- **No delete, rename, re-categorize, archive or share actions exist** for documents (grep: no `delete(` in `patient_actions.dart` / patients feature).

### 2.7 Tab 4 · "Prescriptions" (`_buildPrescriptionsTab :519-567`)
Misnomer: lists **forms** (`patientFormsProvider`) as `templateId` + date, tap → `AppRoutes.patientForm` edit. Empty state: pill icon, "No prescriptions yet", "Create Form" button. Error state silently shows empty list.

### 2.8 Tab 5 · History (`_buildHistoryTab :569-617`)
Same `activities` list as Recent Activity (already capped at 6), so "View full history" shows nothing extra.

### 2.9 Edit / delete / archive
- Patient edits happen only field-by-field in Medical Info and SMS card (PATCH). No full "edit patient" form, no name/email/address/DOB/photo edit in UI (API supports them: `updatePatientWithClient` `patient_actions.dart:378-449`).
- **No patient delete/archive** anywhere in this area (admin has a "deleted patients" screen elsewhere, out of scope).

### 2.10 Document viewer (`document_viewer_screen.dart`)
- Args: `patientId`, `documentId`, `title`, `clinicWorkspaceId?`.
- Loads via authenticated `GET /api/patients/{p}/documents/{d}/download?clinicId` (`fetchDocumentDownloadWithClient`, `patient_actions.dart:173-190`); captures filename from `Content-Disposition` (plain, bare, RFC 5987) and `content-type`.
- Type detection order: magic bytes (`detectMimeFromBytes` JPEG/PNG/GIF/WebP/PDF, `patient_actions.dart:222-267`) → content-type → extension (`:103-119`).
- Image → `InteractiveViewer(0.5–4x)` + `Image.memory`. PDF: web → `PdfViewerWeb` iframe with `data:application/pdf;base64,…` via `HtmlElementView` (`pdf_viewer_web.dart`); mobile → `pdfx` `PdfControllerPinch(PdfDocument.openData(bytes))` + `PdfViewPinch` (`:84-89, 206-212`). Non-image, non-PDF bytes (e.g. docx) still go to the PDF path (would fail to render) — UNVERIFIED backend allows such uploads.
- Error: null/empty body → `_error = ''` sentinel → generic `couldNotLoadDocument` + Retry (`:67-73, 133-169`). No share/save/print/download-to-disk button.

---

## 3. Medical forms

### 3.1 Templates
`formTemplates = [FormTemplate(id: '025-2', name: '025-2')]` (`patient_form_models.dart:335-349`). Template picker sheet lists these (`showPatientFormTemplateSheet`, `patients_screen.dart:543-589`) and pushes `AppRoutes.patientForm` with `{patient: Patient, templateId, existingForm: null}` (route parse `router.dart:118-125, 252-259` — the `Patient` object is passed by reference through route args).

### 3.2 Schema (`PatientForm`, `patient_form_models.dart:3-78`)
`id?`, `patientId` (String), `templateId`, `date` (DateTime, date-only), `fullName`, `gender?`, `address?`, `age?` (int), `job?`, `diagnosis?`, `diagnosisCode?`, `diagnosisDisplay?`, `diagnosisSystem?` (`"ICD10"` or `"ICD-10"`, inconsistent — see §10), `complaints?`, `otherIllnesses?`, `moreDetails?`, `visualCheckup?`, `occlusion?`, `oralCavityCondition?`, `xrayLabData?`, `treatment?`, `treatmentResult?`, `recommendations?`, `doctorName?`, `doctorClinic?`, `formNumber?` (int), `dentalChart: Map<String,String>`, `followups: List<PatientFormFollowup>`, `documentId?`, `signatureRequested` (bool), `patientSignedAt?`, `patientSignatureImageBase64?`, `clinicalDiseaseId?`, `clinicalChipSelections: List<ClinicalChipSelection>` (from `clinical_engine` feature, out of scope).

`PatientFormFollowup`: `date` (date-only), `clinicalFindings`, `doctorName?` (`:289-333`).

Dental chart key grammar (`patient_form_models.dart:30-31`, codec `lib/features/appointments/dental/dental_chart_codec.dart`): FDI tooth keys `"11".."48"` (+ primary `51..85`), legacy `UR1..LL8` migrated on load, editable jaw rows `TOP_{row}_{cell}` / `BOTTOM_{row}_{cell}` (16 cells permanent / 10 primary), history rows `TOP_HIST_{i}_{cell}` with `_date`/`_doctor` suffix keys, meta `__dentition__` = `permanent|primary`. Tooth codes: `'', O, R, C, P, Pt, П, A, I, II, III, K, И` (`patient_form_screen.dart:168-182`).

### 3.3 Serialization (`toJson`, `:155-198`)
Sends `id` and `patientId` as **ints** (`int.parse`), `date` as `YYYY-MM-DD`, includes only non-null scalar fields, `dentalChart` only if non-empty, `followups` only if non-empty, `documentId` as int, `clinicalDiseaseId`, `clinicalChipSelections`. **Not sent**: `signatureRequested`, `patientSignedAt`, `patientSignatureImageBase64` (server-owned). `fromJson` accepts ISO or `YYYY-MM-DD` dates, tolerant `formNumber` parsing (`:200-278`).

### 3.4 Form screen (`PatientFormScreen`, `patient_form_screen.dart:64-1657`)
Props: `patient`, `templateId`, `existingForm?`, `isEmbedded` (no AppBar; `onSaved` instead of pop), `onUnsavedChange`, `registerSaveHandler`, `registerIcdApplyHandler` (the appointment screens embed this form; out of scope).

Sections in order (`build :1195-1623`):
1. Read-only: Patient ID, Date (`existingForm.date ?? now`), Full name, Gender (only if `_gender != null` — **never set**, `:161`), Age (computed from DOB).
2. Address (prefilled from `patient.general.address`), Job, Diagnosis (free text, maxLines 3).
3. ICD-10 picker: `GET /api/icd10/search?q&limit=20&lang=<ui locale>` with 250 ms debounce (`:267-316, 1359-1364`); result list ≤240px; selection sets code/display/system `"ICD10"` and shows an `InputChip` (deletable).
4. `ClinicalEnginePanel` (025-2 only) — fills many text fields and sets diagnosis/ICD from a disease catalogue (`_onClinicalEngineApplied :361-392`; sets system `'ICD-10'`).
5. Complaints, Other illnesses, More details, Visual checkup.
6. 025-2 only: Dental chart (`_DentalChartGrid :1659-2570`: dentition segmented toggle, read-only history rows with date/doctor caption, editable dropdown rows above upper jaw / below lower jaw with add/remove row, tooth tiles painted by `DentalToothPainter`, FDI numbers, anatomy labels), legend text, Occlusion, Oral cavity, X-ray/lab, Treatment, Treatment result, Recommendations, Doctor (read-only, "will be set automatically on save"), Return visits (`_FollowupsTable :2572-2761`: add row (today, empty), edit via dialog with date picker 2000..2100 + speech text field, remove), Signature section (received → green text + signed-at + "save again to refresh PDF"; else optional "pending" + "Request patient signature" secondary button).
7. "Save form" primary button.
- All free-text inputs are `DoctorSpeechTextField` (speech-to-text append; out of scope).
- Chronic-disease warning dialog shown once after first frame (`:140-155, 192-194`).
- New 025-2: dental chart prefilled from the latest 025-2 of this patient (any doctor) as read-only history rows (`_prefillDentalChartFromLatest0252 :413-440`).

### 3.5 Validation / autosave
- `_formKey.currentState!.validate()` is called (`:472`) but **no field has a validator**, so validation always passes. No required fields.
- **No autosave.** Unsaved tracking only via `onUnsavedChange(true)` listeners on every controller (`:339-354`) for the embedding parent; standalone screen has no "discard changes?" guard.

### 3.6 Save flow (`_saveForm :468-633`) — 3–4 sequential requests
1. `POST /api/patients/{id}/forms` (new) or `PUT /api/patients/{id}/forms/{formId}` (existing or `_cachedFormId` after first save).
2. Client generates the PDF (`pdf` package; `DejaVuSans.ttf` from assets for Cyrillic; 025-2 → **landscape A4** with hardcoded Uzbek labels `_Form0252PdfUz :30-54`, dental chart grid, follow-ups table, signature image; other templates → simple portrait key/value list) (`_generatePdf :684-745`). Uses in-memory `_dentalChart` rather than the server response (`:540-543`).
3. If form already has `documentId` → `PUT /api/patients/{id}/documents/{docId}` multipart (file only); else `POST /api/patients/{id}/documents` multipart with `title = "Form 025-2 (YYYY-MM-DD)"`, `fileName = form_025-2_YYYY-MM-DD.pdf`, `category = 'FORM_025_2'`, then `POST …/forms/{formId}/link-document?documentId=` (`:552-584`).
4. `invalidate(patientDocumentsProvider(key without clinicId))`, `invalidate(patientFormsProvider)`; pop (unless embedded) + snackbar `formSavedSuccessfully`.
- Partial-failure risk: if step 3/4 fails the form exists without a PDF; the next save of the same screen would PUT the form and POST a new document (since `_cachedDocumentId` is null) — no retry/cleanup.

### 3.7 Signature request (`_requestPatientSignature :635-682`)
025-2 only; no-op if already signed. Silently saves first when no form id, then `PUT /api/patients/{id}/forms/{formId}/request-signature` → updates `signatureRequested/patientSignedAt/signature image`, invalidates forms, snackbar `patientSignatureRequestSent`. Signature arrives from the patient app; doctor must "save again" to regenerate the PDF with the signature.

### 3.8 Print
AppBar print icon (existing forms only) builds a `PatientForm` from current state, generates the PDF and shows snackbar `pdfGeneratedPrintingNotImplemented` (`:1207-1259`). Effectively dead.

---

## 4. Documents

### 4.1 Categories (`document_category.dart`)
`DocumentCategory { code, icon, isMedicalResult }`; label via l10n key `documentCategory_<code>` with humanised fallback (`:25-33`).

Medical results (shared with every doctor of the patient): `BLOOD_TEST` Blood test · `URINE_TEST` Urine test · `STOOL_TEST` Stool test · `LAB_RESULT` Lab result · `MRI` MRI · `CT_SCAN` CT scan · `XRAY` X-ray · `ULTRASOUND` Ultrasound · `MAMMOGRAPHY` Mammography · `ECG` ECG · `EEG` EEG · `ENDOSCOPY` Endoscopy · `BIOPSY` Biopsy · `PATHOLOGY` Pathology · `IMAGING_OTHER` Other imaging · `PRESCRIPTION` Prescription · `VACCINATION_RECORD` Vaccination record · `DISCHARGE_SUMMARY` Discharge summary · `REFERRAL` Referral · `HOSPITAL_REPORT` Hospital report · `ALLERGY_REPORT` Allergy report · `OTHER_MEDICAL` Other medical result (`:36-59`; labels `assets/localization/en.json:748-769`).

Private (creator-only + access-grant): `APPOINTMENT_NOTE` Appointment note · `REMOTE_TASK_DOCUMENT` Remote task document · `INTERNAL_NOTE` Internal note · `OTHER_PRIVATE` Other private document (`:61-66`; `en.json:770-774`).

Not in either list but used on the wire: `FORM_025_2` ("025-2 form", `en.json:772`) — sent by the form screen; `findDoctorCategory('FORM_025_2')` returns null so form documents render **without** a category chip.

### 4.2 Upload (`patients_screen.dart:72-541`, `patient_actions.dart:19-87`)
- Entry: bottom sheet `showPatientDocumentUploadOptions` with three options: Upload PDF, Take Photo, Scan (multi-page) (`:469-541`).
- Upload PDF: `FilePicker.platform.pickFiles(type: custom, allowedExtensions: ['pdf'], withData: true)` → title+category sheet (`askPatientDocumentTitleAndCategory :72-224`: title text defaults to filename sans `.pdf`, category dropdown with two disabled group headers, **category required** to enable "Add") → snackbar "Uploading..." → multipart → `ref.refresh(patientDocumentsProvider(key))` + `onAfterUpload` + snackbar `Document "x" uploaded`.
- Take Photo / Scan: `ImagePicker().pickImage(source: camera, imageQuality: 85)` in a loop; after each page an "Add another page?" dialog (count shown) unless single page; pages are embedded as images in a `pw.Document`, one page each, `BoxFit.contain` (`:354-466`); uploaded as `scanned_document.pdf` with default title "Scanned document (N pages)". **`flutter_image_compress` is not used here** (only `imageQuality: 85`).
- Multipart request (`uploadPatientDocumentWithClient`): `POST /api/patients/{id}/documents[?clinicId=]`, part `file` (filename, content-type by extension: pdf/png/jpeg/gif/webp else `application/octet-stream`), fields `title`, optional `category`, optional `appointmentId`. Success 2xx parses the created document JSON. **No client-side size limit** and no progress reporting.
- Update file: `updatePatientDocumentWithClient` builds its own `http.MultipartRequest('PUT', …/documents/{docId}?clinicId)` with `client.buildHeaders()` (which includes `Content-Type: application/json`; the http package overwrites it on finalize — works but fragile) and always declares `application/pdf` (`:90-133`).

### 4.3 List
`GET /api/patients/{id}/documents?clinicId` → `[{id, title, date, url?, canView?, creatorLabel?, category?, isSharedWithTeam?}]` (`fetchPatientDocumentsWithClient :279-311`). Invalid/missing `date` falls back to `DateTime.now()` (`:270-277`). `Patient.fromApi` also parses an inline `documents` array with the same shape plus legacy `filePath` (`patient_models.dart:105-124`).

### 4.4 Download / view
Only in-app viewing via the authenticated download endpoint (§2.10). `url` from the list is used only as a "viewable" flag (`patient_detail_panel.dart:2028, 2241`), never opened directly. No save-to-device, share, or print.

### 4.5 Delete
None (no endpoint call, no UI).

### 4.6 Request-access flow
1. Locked doc (`canView == false`, `url == null`) shows lock icon, byline, disabled open button and a ⋮ menu with "Request access".
2. `POST /api/patients/{p}/documents/{d}/request-access?clinicId` with empty JSON body (`patient_actions.dart:136-149`); 401 → "Unauthorized: please login again."; other → "Failed to request access: <status> <body>".
3. Owner (patient app or other doctor — UNVERIFIED who approves) responds; FCM `DOCUMENT_ACCESS_APPROVED` / `REJECTED` / `REQUEST` deep-links to `patientsWithSelection` with `documentId`; APPROVED auto-opens the viewer (`lib/app/app.dart:333-356`).
4. Document list is refreshed whenever the viewer is closed or the patient is reselected so the new `canView/url` state appears.

---

## 5. Care partnerships (`lib/features/care_partnerships/**`)

### 5.1 Concept
A doctor (initiator) invites another doctor (partner) to co-manage a specific patient, optionally naming a requested specialty and a message. Partner accepts/declines; initiator can cancel while pending; either side can complete when active; both can post free-text progress updates while active. Status strings handled in UI: `PENDING`, `ACTIVE` (`care_partnership_screens.dart:267-387`). Other terminal statuses (DECLINED/CANCELLED/COMPLETED) are UNVERIFIED names — the UI only prints `p.status` raw.

### 5.2 Models (`care_partnership_repository.dart`)
- `CarePartnerHit { id:int, firstName, lastName, profession?, clinic?, locationCountry?, locationCity?, avatarUrl? }`, `displayName` (`:5-38`).
- `CarePartnership { id:int, status:String, patientId:int, patientName?, initiatingDoctorId:int, initiatingDoctorName, partnerDoctorId:int, partnerDoctorName, specialtyRequested?, message?, originatingAppointmentId?:int, createdAt:String, updatedAt:String, viewerRole?: 'INITIATOR'|'PARTNER' }` + `iAmInitiator/iAmPartner` (`:40-94`).
- `CarePartnershipProgress { id:int, authorDoctorId:int, authorDoctorName, body, createdAt:String }` (`:96-119`).
- Timestamps are kept as raw strings; the detail screen shows `createdAt.substring(0,16)` (`:376-378`) — no TZ conversion.

### 5.3 Repository / endpoints (`:121-242`)
`searchPartners(country='Uzbekistan', profession?, q?)` → `GET /api/care-partnerships/partners/search?country&profession&q` · `listMine()` → `GET /api/care-partnerships` · `getById(id)` → `GET /api/care-partnerships/{id}` · `invite(patientId, partnerDoctorId, specialtyRequested?, message?, originatingAppointmentId?)` → `POST /api/care-partnerships` · `accept/decline/cancel/complete(id)` → `POST /api/care-partnerships/{id}/{accept|decline|cancel|complete}` with `{}` · `listProgress(id)` → `GET /api/care-partnerships/{id}/progress` · `addProgress(id, body)` → `POST /api/care-partnerships/{id}/progress {body}`. Every call treats **only `200`** as success and decodes `res.body` (not `utf8.decode(bodyBytes)`).

### 5.4 Screens & state
- Provider: `carePartnershipRepositoryProvider = Provider((ref) => CarePartnershipRepository(ref.watch(apiClientProvider)))` (`find_therapy_partner_screen.dart:12-14`). Everything else is `setState` local state — no Riverpod cache, no invalidation; list is refetched on each screen open and after each action.
- `FindTherapyPartnerScreen(patientId:int, patientName)` (`find_therapy_partner_screen.dart`): auto-searches on open; search text + specialty filter + Search button; results `ListTile` (name, "profession · clinic · city/country"), single selection; once selected shows message field + "Send invite" → `invite` (specialty = filter text or hit.profession) → snackbar `partnerInviteSent` → `pushReplacementNamed(carePartnershipDetail, created.id)`. Country hardcoded `'Uzbekistan'`.
- `CarePartnershipsListScreen` (`care_partnership_screens.dart:13-127`): `listMine`, `RefreshIndicator`, rows "patientName · status · role label (invitee/sender) · A ↔ B", tap → detail. Role fallback compares `profileAllProvider.profile['id'|'doctorId'|'doctorProfileId']` when `viewerRole` is null (`:55-73`).
- `CarePartnershipDetailScreen(partnershipId)` (`:129-417`): header (patient, status, doctors, message, waiting/received hint), action buttons by status/role (Accept, Decline, Cancel invite, Complete), progress feed (`Card/ListTile`), composer visible only when `ACTIVE`. Errors shown as raw `e.toString()` snackbars.
- Routes: `findTherapyPartner = '/app/care-partnerships/find'` (args map `patientId`, `patientName`), `carePartnerships = '/app/care-partnerships'`, `carePartnershipDetail = '/app/care-partnerships/detail'` (arg int id) (`router.dart:57-59, 143-163, 317-341`).
- No notification handling for incoming invites in this area (UNVERIFIED whether FCM has a partnership type).

---

## 6. State (Riverpod)

| Provider | File:line | Type | Holds | Refresh / invalidation triggers |
|---|---|---|---|---|
| `patientsProvider` | `patients_provider.dart:34-38` | `StateNotifierProvider<PatientsController, List<Patient>>` | full directory, name-sorted (all pages of `GET /api/patients`) ; auto-loads in ctor | `loadPatients()` on screen init, tab switch to Patients, manual refresh, after create patient, after create account, after any `_GeneralInfo`/SMS update (when no `onPatientDataRefresh`) |
| `patientByIdProvider(String)` | `:41-52` | `FutureProvider.family<Patient?, String>` | single patient; **swallows all errors → null** | `invalidate` after `_GeneralInfo` and SMS updates; also used by home/calendar/appointments |
| `patientsForAssignmentProvider` | `:75-78` | `StateNotifierProvider<…, AsyncValue<List<PatientAssignmentItem>>>` | light list for calendar assign; initial `data([])`, loads only on explicit `loadPatientsForAssignment()` | calendar screen (out of scope) |
| `patientDoctorAppointmentsProvider(String)` | `:81-85` | `FutureProvider.family<List<PatientDoctorAppointment>, String>` | appointment history with this doctor | `invalidate` after booking from detail panel |
| `patientDocumentsProvider(PatientDocumentsKey{patientId, clinicId?})` | `patient_documents_provider.dart:27-38` | `FutureProvider.family` | document list for patient (+clinic scope) | `refresh` on selection, patient change, after upload, after request-access, after viewer close, manual refresh, after form edit; `invalidate` after form save (key **without** clinicId) |
| `patientFormsProvider(String)` | `patient_forms_provider.dart:6-13` | `FutureProvider.family<List<PatientForm>, String>` | all forms of patient | `invalidate` after form save / signature request / dental prefill; `refresh` after form edit from documents tab |
| `last0252FormForPatientProvider(String)` | `:17-35` | `Provider.family<AsyncValue<PatientForm?>, String>` | newest 025-2 (date desc, id desc) derived from forms | derived (consumers: appointment screens) |
| `carePartnershipRepositoryProvider` | `find_therapy_partner_screen.dart:12-14` | `Provider<CarePartnershipRepository>` | repo bound to `apiClientProvider` | n/a |
| Local widget state only | — | — | selection, overlay patient, favorites, search/filter/sort/page, tab index, document search, dialog state, prophylaxis values, partnership lists | — |

Cross-feature providers consumed here (out of scope): `apiClientProvider`, `profileAllProvider` (`timeZone`, `smsRemindersAllowed`, doctor id), `doctorFeatureProvider(DoctorFeature.*)`, `patientBriefingProvider`, `calendarProvider`, `shellProvider`, `languageProvider`, `aiApiProvider`, `chatPendingPatientIdProvider`.

---

## 7. Endpoints

All via `ApiClient` (JWT bearer, base URL from `API_BASE_URL`) unless noted. "Errors" = what the Flutter code distinguishes.

| Method & path | Query | Request | Response parsed | Errors handled | Source |
|---|---|---|---|---|---|
| GET `/api/patients` | `page`, `size=100`, `sort=fullName,asc` | — | JSON **list** of patient objects (see Patient model); loop until `< size` | 401 msg; other → throw | `patient_actions.dart:594-639` |
| GET `/api/patients/for-assignment` | `page`, `size=500`, `sort=fullName,asc` | — | list **or** page `{content[], totalPages}` of `{id, name, photoUrl?}` | 401, 403, other (first 200 chars) | `:538-591` |
| GET `/api/patients/{id}` | `clinicId?` | — | patient object | 401; other | `:492-510` |
| POST `/api/patients` | — | `name, phones[], phone?, email, address, birthDate(YYYY-MM-DD)|null, language, photoUrl(null), chronicDisease, gender?, bloodGroup?, allergies?` | patient object | 401; 409 (`message`); 400 (`message`); other | `:314-375` |
| PATCH `/api/patients/{id}` | — | any of `name, phones[] (+phone), phone, email, address, birthDate, language, photoUrl, chronicDisease ('' clears), smsReminderEnabled, smsReminderHoursBefore, gender, bloodGroup, allergies` | patient object | 401; 409 (`message`); other | `:378-449` |
| GET `/api/patients/{id}/appointments` | — | — | list `{id, startAt, endAt, status, location?, isVideo?, reason?}` | non-200 → `[]` (silent) | `:452-464` |
| POST `/api/patients/{id}/send-test-sms` | — | `{}` | ignored | 401, 403, 503, other (`message`) | `:467-489` |
| POST `/api/patients/{id}/create-account` | — | `{}` | `{username, oneTimePassword}` (read as dynamic) | 401; other | `:743-756` |
| GET `/api/patients/{id}/documents` | `clinicId?` | — | list `{id, title, date, url?, canView?, creatorLabel?, category?, isSharedWithTeam?}` | 401; other | `:279-311` |
| POST `/api/patients/{id}/documents` (multipart) | `clinicId?` | part `file`; fields `title`, `category?`, `appointmentId?` | document object | non-2xx → throw with body | `:19-87` |
| PUT `/api/patients/{id}/documents/{docId}` (multipart, raw `http`) | `clinicId?` | part `file` (pdf) | document object | non-2xx → throw | `:90-133` |
| POST `/api/patients/{id}/documents/{docId}/request-access` | `clinicId?` | `{}` | — | 401; other | `:136-149` |
| GET `/api/patients/{id}/documents/{docId}/download` | `clinicId?` | — | bytes + `Content-Disposition` filename + `Content-Type` | non-200 → null | `:173-190` |
| GET `/api/patients/{id}/forms` | — | — | list of form objects | 401; other | `:642-659` |
| POST `/api/patients/{id}/forms` | — | `PatientForm.toJson()` | form object | 401; other | `:662-677` |
| PUT `/api/patients/{id}/forms/{formId}` | — | `PatientForm.toJson()` | form object | 401; other | `:680-696` |
| PUT `/api/patients/{id}/forms/{formId}/request-signature` | — | `{}` | form object | 401; other | `:699-719` |
| POST `/api/patients/{id}/forms/{formId}/link-document` (raw `http.post`) | `documentId` | none | form object | 401; other | `:722-740` |
| GET `/api/prophylaxis/settings` | `patientId`, `clinicId` | — | `{patientId, clinicId, intervalMonths?, enabled?, lastSentAt?}` or empty/`null` | 401; other | `:789-809` |
| PUT `/api/prophylaxis/settings` | — | `{patientId:int, clinicId:int, intervalMonths, enabled}` | same object | 401; other | `:811-836` |
| GET `/api/icd10/search` | `q`, `limit=20`, `lang` | — | list `{code, title, subtitle?}` | non-200 → empty (silent) | `patient_form_screen.dart:281-308` |
| GET `/api/care-partnerships/partners/search` | `country`, `profession?`, `q?` | — | list of `CarePartnerHit` | ≠200 throw | `care_partnership_repository.dart:126-146` |
| GET `/api/care-partnerships` | — | — | list of `CarePartnership` | ≠200 | `:148-157` |
| GET `/api/care-partnerships/{id}` | — | — | `CarePartnership` | ≠200 | `:159-167` |
| POST `/api/care-partnerships` | — | `{patientId, partnerDoctorId, specialtyRequested?, message?, originatingAppointmentId?}` | `CarePartnership` | ≠200 (body included) | `:169-190` |
| POST `/api/care-partnerships/{id}/accept\|decline\|cancel\|complete` | — | `{}` | `CarePartnership` | ≠200 | `:192-222` |
| GET `/api/care-partnerships/{id}/progress` | — | — | list of `CarePartnershipProgress` | ≠200 | `:224-231` |
| POST `/api/care-partnerships/{id}/progress` | — | `{body}` | `CarePartnershipProgress` | ≠200 | `:233-241` |

Out-of-scope calls triggered from this UI: calendar `loadDay` / `bookFreeSlotRemote` (appointment booking), AI streaming, patient briefing, treatment plan wizard, create task.

---

## 8. Models

### `Patient` (`patient_models.dart:22-160`)
`id: String` (from any JSON type via `toString`), `name: String` (`?? ''`), `general: PatientGeneral`, `documents: List<PatientDocument>` (from inline `documents`), `photoUrl: String?`, `hasAccount: bool` (`== true`), `username: String?`, `clinicalStatus: String` (upper-cased, default `'ACTIVE'`; known values `ACTIVE`, `AT_RISK`, `FOLLOW_UP`), `atRisk: bool`, `followUpRequired: bool`. `copyWith` present.

### `PatientGeneral` (`:162-227`)
`birthDate: DateTime?` (`DateTime.parse`), `phone: String?`, `phones: List<String>` (from `phones[]` else `[phone]`), `email?`, `address?` (legacy), `language?`, `chronicDisease?`, `gender?`, `bloodGroup?`, `allergies?`, `locationCountry/Region/District/City/PostalCode/StreetAddress?`, `smsReminderEnabled: bool`, `smsReminderHoursBefore: int` (only 1 or 24; anything else → 24, `:96-99`). Getters `formattedLocation`, `allPhones`.

### `PatientDocument` (`:229-289`)
`id: String`, `title: String`, `date: DateTime`, `filePath: String?` (legacy), `url: String?` (null when locked), `canView: bool = true`, `creatorLabel: String = 'Unknown'` ("Doctor"/"Patient"/"Unknown"), `category: String?`, `isSharedWithTeam: bool = false`. `copyWith` present.

### `PatientDoctorAppointment` (`:291-322`)
`id`, `startAt: DateTime` (ISO, UTC expected), `endAt`, `status: String`, `location?`, `isVideo`, `reason?`.

### `PatientAssignmentItem` (`:4-20`) — `id`, `name`, `photoUrl?`.

### `PatientForm`, `PatientFormFollowup`, `FormTemplate` — see §3.2.

### `DocumentCategory` — see §4.1. `ProphylaxisSetting` (`patient_actions.dart:759-786`): `patientId:int, clinicId:int, intervalMonths:int (default 12), enabled:bool (default true), lastSentAt:String?`.

### Care partnership models — see §5.2.

### `DocumentDownloadResult` (`patient_actions.dart:159-168`): `bytes`, `filename?`, `contentType?`.

### Helper enums (`patient_detail_helpers.dart:7-11`): `PatientListTab`, `PatientSortOption`, `PatientStatusKind`.

---

## 9. Platform / third-party → KMP equivalents

| Flutter usage | Where | KMP / CMP approach |
|---|---|---|
| `file_picker ^8.0.0` (`pickFiles` pdf only, `withData: true`) | `patients_screen.dart:233-237` | `expect fun pickPdf(): PickedFile?` — Android: `ActivityResultContracts.OpenDocument(["application/pdf"])` + `ContentResolver` bytes; iOS: `UIDocumentPickerViewController` (UTType.pdf). Or a library such as `mohamedrejeb/calf` FilePicker / `vinceglb/FileKit`. |
| `image_picker ^1.0.7` camera, `imageQuality: 85`, multi-shot loop | `patients_screen.dart:436-454` | `expect fun captureImage(): ByteArray?` — Android `TakePicture` contract via `FileProvider`; iOS `UIImagePickerController`/`PHPicker`. Keep the "add another page?" loop in common code. Add `CAMERA` permission handling (Flutter relied on plugin). |
| `pdf ^3.10.8` (client-side PDF generation: scanned pages → PDF; 025-2 form PDF with Cyrillic font, tables, dental grid) | `patients_screen.dart:374-386`; `patient_form_screen.dart:684-1193` | No common-code PDF writer in KMP. Options: (a) **move PDF rendering to the backend** (recommended; form data is already on server); (b) expect/actual: Android `android.graphics.pdf.PdfDocument` + Canvas, iOS `UIGraphicsPDFRenderer` — layout code written twice; (c) JVM-only `openpdf/pdfbox` does not run on iOS. Scanned pages: both platforms can embed images into PDF natively with little code. |
| `pdfx ^2.9.2` (`PdfControllerPinch`, `PdfViewPinch`) | `document_viewer_screen.dart:43,85-89,206-212` | expect/actual composable: Android `PdfRenderer` → pages as bitmaps in a `LazyColumn` with zoom (or `AndroidView` of a WebView/PDF lib); iOS `PDFKit.PDFView` via `UIKitView`. |
| `dart:html` iframe data-URL viewer | `pdf_viewer_web.dart` | Not needed (no web target). |
| `flutter_image_compress ^2.1.0` | **not used** in this area (only imported indirectly elsewhere) | Compress camera captures before PDF embedding: Android `Bitmap.compress(JPEG, 85)`, iOS `UIImage.jpegData(compressionQuality: 0.85)`. |
| `url_launcher` / `path_provider` / `dart:io` | imported in `patients_screen.dart:2,10-12` but **unused** | Drop. |
| `http.MultipartRequest` | `patient_actions.dart:47-68, 98-115` | Ktor `submitFormWithBinaryData` with `formData { append("file", bytes, Headers{ContentType, ContentDisposition filename}) ; append("title", …) }`. Derive MIME from extension or magic bytes (port `detectMimeFromBytes`). |
| `NetworkImage` avatars (`PersonAvatar`, three private `_PatientAvatar`/`_Avatar` clones) | `person_avatar.dart`; `patients_directory_panel.dart:825-868`; `patient_detail_overview.dart:952-995`; `patient_detail_panel.dart:2469-2509` | One `PersonAvatar` composable using Coil 3 (`AsyncImage`) with initials fallback; initials rule = first letter of first and last whitespace-split token, uppercased. |
| `Clipboard.setData` | `patients_screen.dart:1384` | `LocalClipboardManager` / `ClipEntry` in CMP. |
| `showDatePicker` with `localeForMaterialIntl` | create patient, follow-ups, make appointment | Material3 `DatePickerDialog` in CMP; locale via `kotlinx-datetime` formatting. Birth date range 1900..today, initial 1990. |
| `CustomPaint` tooth painter (`DentalToothPainter`, out of scope) + dental grid dropdowns | `patient_form_screen.dart:1876-1963` | `Canvas` in Compose; `ExposedDropdownMenuBox` cells. |
| Speech-to-text fields (`DoctorSpeechTextField`) | all form inputs | out of scope; keep a plain `OutlinedTextField` with an optional mic slot. |
| FCM deep link into patients + document viewer | `app.dart:333-356` | navigation args must include `patientId`, `documentId`, `documentTitle`, `openDocumentViewer`; covered by whichever nav ticket owns notifications. |

---

## 10. Quirks, bugs, dead code, tech debt — do not copy

1. **Two create-patient sheets** with different field sets (§1.6). KMP: one dialog, superset of fields.
2. **Three private avatar clones** plus `PersonAvatar` (§9). Collapse to one.
3. **Favorites not persisted** and the "Recent" tab is "last 20 by name order", not recency (`patient_detail_helpers.dart:205-208`); sort "recent" uses document dates only. Either persist favorites and define recency server-side or drop these tabs.
4. **Full directory downloaded client-side** (pages of 100 until exhausted, `patient_actions.dart:594-639`) then paged 10/visual page in memory; `for-assignment` also pulls everything at 500/page. Reloaded on every tab switch (`patients_screen.dart:775-779`). KMP should use server paging/search or at least cache.
5. **Timezone violations**: appointment history uses `toLocal()` (`patient_detail_panel.dart:436-437, 1112-1113`); `patientAge` and form age use `DateTime.now()` (acceptable per rules but inconsistent); care-partnership timestamps shown as raw substrings.
6. **Hardcoded strings sent to backend**: booking `location: 'Video Consultation' | 'Clinic Address'`, `reason: 'Check Up'` (`patient_detail_panel.dart:927-930`); form document title `Form 025-2 (date)` and the title-regex fallback that depends on it (`:1993-2016`); search `country: 'Uzbekistan'` (`find_therapy_partner_screen.dart:63`).
7. **Hardcoded UI strings / fallbacks**: Uzbek byline `'$creator tomonidan yuklangan'` (`patient_detail_panel.dart:2149-2151`); English month abbreviations (`patient_detail_helpers.dart:179-192`); `'(tap to edit)'`, `'(auto)'` in follow-ups table (`patient_form_screen.dart:2729, 2734`); dental anatomy labels `'incisors'`, `'wisdom'` (`:1699-1710`); `'Patient #id'` (`care_partnership_screens.dart:111, 254`); dozens of `translate('x') ?? 'English'` fallbacks contrary to CLAUDE.md. Raw status strings (`PENDING`) shown to users.
8. **`FORM_025_2` category missing from `kAllDoctorCategories`** → no chip for form documents although the l10n label exists (`document_category.dart:36-70`; `en.json:772`).
9. **Form validation is a no-op** (`validate()` with zero validators, `patient_form_screen.dart:472`); `_gender` is never assigned so Gender never shows nor saves from the form (`:161, 1283`); form save is a 3–4 request non-atomic sequence with client-side PDF generation; the PDF method is named `_build0252PortraitContent` but the page is landscape (`:700-706`); `_dentalChartForPdf` is an identity function (`:973-978`); print button is a stub (`:1207-1259`); `diagnosisSystem` is written as both `"ICD10"` and `"ICD-10"` (`:256, 322, 387`).
10. **Form save invalidates `patientDocumentsProvider(PatientDocumentsKey(patientId))` without `clinicId`** (`patient_form_screen.dart:587`) — stale list in clinic workspace.
11. **`updatePatientDocumentWithClient` / `linkDocumentToFormWithClient` bypass `ApiClient`** with raw `http` and `buildHeaders()` (adds `Content-Type: application/json` to a multipart request; scope checks and auto-logout skipped) (`patient_actions.dart:107-114, 728-730`).
12. **Error handling**: `patientByIdProvider` swallows every error into `null`; `fetchPatientDoctorAppointments` returns `[]` on any non-200; `_GeneralInfo` shows raw `e.toString()`; care repo treats 201 as failure and decodes `res.body` without UTF-8 (`care_partnership_repository.dart:142,153,164…`); `sanitizeErrorMessage` used only in the documents tab.
13. **Chronic-disease blocking dialog** fires on every selection and again on form open (`patients_screen.dart:729-738`; `patient_form_screen.dart:140-155`) — consider once per session per patient.
14. **Dead/unused**: imports `dart:io`, `path_provider`, `url_launcher`, `kIsWeb` in `patients_screen.dart:2-12`; `_Avatar` and `_CollapsibleCard` classes never instantiated (`patient_detail_panel.dart:2469-2509, 3048-3102`); `PatientDocument.filePath` legacy field always null from list endpoint; `_syncTabController` handles a length change that can never happen (`_tabCount` const, `:68-77`); `_showUploadOptions` wrapper (`patients_screen.dart:758-765`); duplicated desktop/mobile tab builders (`_buildAppointmentsTab` vs `_buildAppointmentsTabBody`, documents, prescriptions, history) differing only in scroll wrapping.
15. **"Prescriptions" tab is the forms list**, "History" tab equals "Recent activity" (both capped to 6), "AI Follow-up Suggestions" are static heuristics, "Last visit" is the last document date. Name things honestly in KMP (Forms, Activity) or source from real endpoints.
16. `Patient` object passed through navigation args by reference (`AppRoutes.patientForm`) — in KMP pass `patientId` and read from the repository.
17. `askPatientDocumentTitleAndCategory` never disposes `titleCtrl` (`patients_screen.dart:76`); create-patient modal controllers also leak when the sheet is dismissed (`:1012-1016`).
18. Patient-detail `_handlePatientSelection` iterates `_sidebarPatients` which calls `ref.watch` outside build (`patients_screen.dart:709-714, 722`) — misuse of `watch` in a callback.
19. Tab index / scroll position of the detail panel is reset whenever the widget is recreated; mobile uses a sticky header with manual tab-body switching rather than a pager.

---

## 11. Proposed tickets

Sizes: S ≈ ½–1 day, M ≈ 2–3 days, L ≈ 4–6 days. Ordered so each is buildable after its dependencies. Assumes shared tickets for networking (`ApiClient` Ktor equivalent), auth, theme/design system, navigation shell, l10n infrastructure and timezone utilities exist (referenced as CORE-*).

### PAT-01 · Patient domain models & JSON codecs [S]
Route · — · Spec refs §8 · API: —
Build: `Patient`, `PatientGeneral`, `PatientDocument`, `PatientDoctorAppointment`, `PatientAssignmentItem`, `ProphylaxisSetting`, `PatientStatus` enum (`ACTIVE/AT_RISK/FOLLOW_UP` + UNKNOWN), kotlinx.serialization with lenient decoding (ids as any → String, `phones[]` fallback to `phone`, `smsReminderHoursBefore ∈ {1,24}`), `formattedLocation`, `allPhones`, `displayId` (`P-%05d`), age calc with injected clock.
Done when: unit tests round-trip the sample payload shapes from §7 (list, single, create response, document with/without `url`); unknown status does not crash.
Depends: CORE networking/serialization.
Fix: single source for display id/age/status mapping instead of three helper files.

### PAT-02 · Patients repository & API [M]
Route · — · Spec refs §6, §7 · API: GET/POST/PATCH `/api/patients`, GET `/api/patients/{id}`, GET `/api/patients/for-assignment`, GET `/api/patients/{id}/appointments`, POST `/api/patients/{id}/create-account`, POST `/api/patients/{id}/send-test-sms`, GET/PUT `/api/prophylaxis/settings`.
Build: `PatientsRepository` (Ktor) with typed errors (`Conflict(message)`, `BadRequest(message)`, `Forbidden`, `ServiceUnavailable`, `Unauthorized`), page-walking loader for `/api/patients` and `/for-assignment` (list-or-page tolerant), `clinicId` query support, in-memory cache + `StateFlow<List<Patient>>`, `patientById(id, clinicId)` flow, explicit `refresh()`.
Done when: contract tests against recorded JSON; 409/400 messages surface; 401 handled by core client; `for-assignment` dedupes and sorts.
Depends: PAT-01.
Fix: never swallow errors into `null` (`patientByIdProvider`); don't refetch the whole directory on every tab switch — refresh on pull/explicit action with a short TTL.

### PAT-03 · Patients directory screen (list, search, filter, sort, paging) [M]
Route `patients` (shell tab) · Spec refs §1.2–1.5 · API: via PAT-02.
Build: adaptive layout — list pane ≥720dp with detail pane (7:13), single pane below; header with count badge, New Patient, filter (status), tabs (All / Favorites / Follow-ups; drop or redefine "Recent"), search (name, id, display id, phone), sort (name asc/desc), client-side paging 10/page with the 5-window pager, pull-to-refresh, empty/error states, row with avatar/initials, age·gender·phone subtitle, status pill. Favorites persisted in local settings (DataStore/NSUserDefaults) keyed by doctor id.
Done when: parity with `patients_directory_panel.dart` behaviour on phone and tablet; favorites survive restart; selection highlights; tab switch does not refetch unless stale.
Depends: PAT-02, CORE design system.
Fix: one avatar composable; no `watch`-in-callback equivalents; search debounced.

### PAT-04 · Create patient sheet [S]
Route `patients/create` (bottom sheet / dialog) · Spec refs §1.6 · API: POST `/api/patients`.
Build: one sheet with the superset of fields: full name (required), phones (multi), email, address, language (10 options, display localized), gender, blood group, allergies, birth date (1900..today). Returns created `Patient`; callers (directory, home quick action, calendar assign) reuse it. Snackbar success; 409/400 messages shown via `sanitizeErrorMessage` equivalent.
Done when: both entry points produce identical payloads; empty name blocked; created patient auto-selected in directory.
Depends: PAT-02.
Fix: single implementation; dispose nothing (Compose state); E.164 phone validation via core phone util.

### PAT-05 · Patient detail: header, overview, quick actions [M]
Route `patients/{patientId}?clinicId=` · Spec refs §2.1–2.3, §1.7 · API: GET `/api/patients/{id}`, PATCH (sms fields), POST create-account, POST send-test-sms.
Build: hero header (avatar, name, status pill, at-risk pill, favorite, more menu), summary stat row (6 tiles, 2-col wrap <720dp), Clinical Summary card, Quick Actions (New appointment → CAL ticket dialog, Send message → chat deep link, Upload document → DOCS-03, More), SMS reminder card (gated by profile `smsRemindersAllowed`; toggle + 24h/1h + test SMS; disabled without phone), Patient app access card (create account → credentials dialog with copy, shown once), chronic-disease warning dialog (once per patient per session). Slots for `PatientVisitTimelineCard`, AI copilot and AI follow-ups owned by other tickets (render placeholders if absent).
Done when: tab bar with 6 sections scaffolded (bodies from PAT-06/07, DOCS-04, FORM-05); state survives rotation; mobile = single scroll with sticky tabs.
Depends: PAT-03, PAT-02, CORE profile provider.
Fix: "Last visit" from appointments, not documents; drop fake "AI Follow-up Suggestions" or label as "Reminders".

### PAT-06 · Patient detail: Medical Info tab (editable general info, prophylaxis) [M]
Route `patients/{id}#medical` · Spec refs §2.4 · API: PATCH `/api/patients/{id}`, GET/PUT `/api/prophylaxis/settings`.
Build: read-only rows (id, DOB, email, formatted location, language); phones editor with explicit save; chronic disease dropdown (22 keys, localized, `'None'` → clear); gender; blood group; allergies (save on done); busy state disables inputs; prophylaxis card only when `clinicId` present (interval 1–60, enabled, last sent, save). All writes go through PAT-02 and refresh cached patient.
Done when: each field persists independently; error restores previous value; works in clinic scope.
Depends: PAT-05.
Fix: optimistic update with rollback; one `PatientEditor` ViewModel instead of per-widget mutable state.

### PAT-07 · Patient detail: Appointments & Activity tabs [S]
Route `patients/{id}#appointments`, `#history` · Spec refs §2.5, §2.8 · API: GET `/api/patients/{id}/appointments`.
Build: appointment history list (date, status pill, time range in practice TZ, video/in-clinic/location, reason), "New appointment" button invoking the shared booking dialog (CAL-* ticket; passes `patientId`), empty/error states; Activity tab = documents + forms + appointments merged by date (not capped at 6).
Done when: times rendered with core TZ util (no device-local); invalidates after booking.
Depends: PAT-05, CAL booking dialog ticket.
Fix: do not send hardcoded `location`/`reason`; let the booking dialog own them.

### DOCS-01 · Document models, categories & repository [S]
Route · — · Spec refs §4.1, §4.3, §4.4, §7 · API: GET list, GET download, POST request-access.
Build: `DocumentCategory` enum (26 medical/private codes + `FORM_025_2`, `isMedicalResult`, icon, l10n key `documentCategory_<code>`, `UNKNOWN(raw)`), `PatientDocument`, `DocumentsRepository.list(patientId, clinicId)`, `download(patientId, docId, clinicId): DownloadResult(bytes, filename?, contentType?)` with Content-Disposition parsing (plain + RFC 5987) and magic-byte MIME sniffing, `requestAccess`.
Done when: tests for Content-Disposition variants and MIME sniffing (JPEG/PNG/GIF/WebP/PDF); unknown category never crashes.
Depends: CORE networking.
Fix: include `FORM_025_2` in the known set so form docs get a chip.

### DOCS-02 · Platform pickers & image utilities (expect/actual) [M]
Route · — · Spec refs §9 · API: —
Build: `expect` APIs: `pickPdf(): PickedFile?` (bytes, name), `captureImage(): ByteArray?` (camera, permission prompt), `compressJpeg(bytes, quality=85, maxDim)`, `imagesToPdf(pages: List<ByteArray>): ByteArray` (Android `PdfDocument`, iOS `UIGraphicsPDFRenderer`), `shareOrSaveFile(bytes, name, mime)` (optional). Android actuals via Activity Result APIs + FileProvider; iOS via `UIDocumentPickerViewController`, `UIImagePickerController`.
Done when: manual test on both platforms; camera permission denied path handled; 10-page scan < 5 MB.
Depends: CORE app scaffolding.
Fix: compress before embedding (Flutter did not).

### DOCS-03 · Upload flow (PDF / photo / multi-page scan) [M]
Route `patients/{id}/documents/upload` (sheet) · Spec refs §4.2 · API: POST `/api/patients/{id}/documents` multipart (`file`, `title`, `category`, `appointmentId?`, `?clinicId`).
Build: options sheet (Upload PDF, Take photo, Scan multi-page with "add another page?" loop), title+category sheet (title defaults to filename, category required, grouped dropdown medical/private), Ktor multipart upload with progress and MIME from extension/sniff, success/failure snackbars, refresh documents list; `onAfterUpload` hook for clinic workspace.
Done when: PDF, single photo, 3-page scan upload end-to-end on both platforms; cancellation at every step leaves no partial state.
Depends: DOCS-01, DOCS-02.
Fix: show upload progress instead of a transient "Uploading…" snackbar; size guard (configurable, e.g. 25 MB).

### DOCS-04 · Documents tab + request-access [M]
Route `patients/{id}#documents` · Spec refs §2.6, §4.6 · API: via DOCS-01, POST request-access.
Build: header with refresh and ⋮ (Upload, Create form, Create task), title search, list rows (localized 025-2 title from linked form `documentId`, date, category chip, shared-with-team icon, lock state + "Uploaded by" byline, Edit for linked forms, Request access menu for locked, Open button), highlight `selectedDocumentId`, auto-open viewer once when `openDocumentViewer` arg set, refresh on return from viewer. Scope by `clinicId`.
Done when: locked/unlocked/form/plain rows render per spec; request-access shows confirmation; deep link from notification opens correct document.
Depends: PAT-05, DOCS-01, DOCS-05, FORM-02 (for linked-form lookup).
Fix: match forms only by `documentId` (drop title-regex fallback once backend guarantees link); localized byline for all locales.

### DOCS-05 · In-app document viewer (image + PDF) [M]
Route `patients/{id}/documents/{docId}?title&clinicId` · Spec refs §2.10 · API: GET download.
Build: loads bytes via DOCS-01; image → zoomable (`Modifier.transformable`, 0.5–4x); PDF → expect/actual composable (Android `PdfRenderer` page list with pinch zoom; iOS `PDFKit.PDFView` in `UIKitView`); loading, error + retry; AppBar back + title; optional Share/Save action via DOCS-02.
Done when: renders multi-page PDFs and JPEG/PNG from the API on both platforms; unsupported types show a clear message.
Depends: DOCS-01, DOCS-02.
Fix: unsupported MIME → explicit "cannot preview" instead of feeding bytes to the PDF renderer.

### FORM-01 · Form models, dental-chart codec & serialization [M]
Route · — · Spec refs §3.2, §3.3 · API: —
Build: `PatientForm`, `PatientFormFollowup`, `FormTemplate` (only `025-2`), `ClinicalChipSelection` (coordinate with clinical-engine ticket), `DentalChart` value type wrapping the `Map<String,String>` grammar (FDI keys, `TOP_r_c`/`BOTTOM_r_c`, `*_HIST_*` + `_date/_doctor`, `__dentition__`), legacy `UR/UL/LR/LL` migration, `prefillFromLatest0252`, editable/hist row parsing, dentition-aware FDI lists. `toJson` semantics: ints for ids, `YYYY-MM-DD` dates, omit nulls/empties, never send signature fields.
Done when: codec unit tests ported from Flutter behaviour (round trip, migration, prefill with history rows, primary vs permanent).
Depends: PAT-01.
Fix: single `diagnosisSystem` constant (`"ICD10"`); typed `DentalCode` enum (`O,R,C,P,Pt,П,A,I,II,III,K,И`).

### FORM-02 · Forms repository [S]
Route · — · Spec refs §7 · API: GET/POST `/api/patients/{id}/forms`, PUT `/forms/{formId}`, PUT `/request-signature`, POST `/link-document?documentId`, PUT `/documents/{docId}` (multipart), GET `/api/icd10/search`.
Build: `FormsRepository` with `list(patientId)` flow + cache, `create/update`, `requestSignature`, `linkDocument`, `replaceDocumentFile`, `searchIcd10(q, lang, limit=20)`, derived `latest0252(patientId)` (date desc, id desc).
Done when: contract tests; all calls use the core client (no raw HTTP); 2xx treated as success.
Depends: FORM-01, DOCS-01.
Fix: invalidate documents cache for the correct `clinicId` after save.

### FORM-03 · 025-2 PDF generation strategy [L]
Route · — · Spec refs §3.6 step 2, §9 · API: possibly new backend endpoint (UNVERIFIED availability).
Build: decision + implementation. Preferred: backend renders the 025-2 PDF from the saved form (`POST …/forms/{id}/render-pdf` or server-side on save) so the client only uploads/links. Fallback: expect/actual PDF renderer (Android `PdfDocument` + Compose/Canvas drawing, iOS `UIGraphicsPDFRenderer`) reproducing landscape A4, Uzbek labels, metadata box, sections, dental grid with history captions, follow-ups table, signature image + date, DejaVu Sans for Cyrillic.
Done when: output visually matches the Flutter PDF for a sample form with history rows, primary dentition and signature; Cyrillic renders.
Depends: FORM-01; backend alignment.
Fix: stop regenerating/uploading the PDF from the client on every save if the server can own it.

### FORM-04 · Form 025-2 editor screen [L]
Route `patients/{id}/forms/new?templateId=025-2`, `patients/{id}/forms/{formId}` · Spec refs §3.4–3.7 · API: via FORM-02, DOCS-03 (upload), FORM-03.
Build: read-only header (patient id, date, name, age, gender from patient), text sections (address prefilled, job, diagnosis, complaints, other illnesses, more details, visual checkup, occlusion, oral cavity, x-ray/lab, treatment, treatment result, recommendations, doctor read-only), ICD-10 search with 250 ms debounce + chip, clinical-engine panel slot, dental chart editor (dentition toggle, history rows read-only with date/doctor, editable rows add/remove, tooth tiles with codes, FDI numbers, legend), return-visits table (add/edit dialog with date picker/remove), signature section (request / pending / received), Save; save pipeline = create/update form → PDF (FORM-03) → upload or replace document (category `FORM_025_2`) → link → refresh forms+docs → pop/snackbar; signature request saves first if needed; unsaved-changes guard on back; embedded mode contract for appointment screens (`onSaved`, `registerSaveHandler`, `applyIcd`).
Done when: new and existing forms round-trip all fields; dental prefill from latest 025-2 works; chronic warning shown once; works embedded and standalone; failures mid-pipeline are reported with retry and do not duplicate documents.
Depends: FORM-02, FORM-03, DOCS-03, PAT-05, clinical-engine & speech tickets (slots).
Fix: real validation (at least diagnosis or complaints non-empty — confirm with product); pass gender from patient; idempotent document step (remember created `documentId` before link).

### FORM-05 · Forms tab + template picker [S]
Route `patients/{id}#forms`, `patients/{id}/forms/pick-template` (sheet) · Spec refs §2.7, §3.1 · API: via FORM-02.
Build: list of forms (template, date, signed badge) → FORM-04 edit; empty state with "Create form"; template sheet (single 025-2 entry, extensible). Rename tab "Forms" (was "Prescriptions").
Done when: list refreshes after save; navigation passes ids only.
Depends: FORM-02, FORM-04, PAT-05.
Fix: no `Patient` object in nav args.

### CARE-01 · Care partnership models & repository [S]
Route · — · Spec refs §5.2, §5.3 · API: all `/api/care-partnerships/**`.
Build: `CarePartnerHit`, `CarePartnership` (`PartnershipStatus` enum `PENDING, ACTIVE, DECLINED, CANCELLED, COMPLETED, UNKNOWN(raw)` — confirm names with backend), `CarePartnershipProgress` with `Instant` timestamps, `ViewerRole`; repository with search/list/get/invite/accept/decline/cancel/complete/progress list/add; 2xx = success; UTF-8 decoding; typed errors.
Done when: contract tests; role resolution uses `viewerRole` with doctor-id fallback.
Depends: CORE networking, CORE profile (doctor id).
Fix: enum statuses + localized labels instead of raw strings.

### CARE-02 · Find therapy partner & invite [S]
Route `care-partnerships/find?patientId&patientName` · Spec refs §5.4 · API: partners/search, POST invite.
Build: search field + specialty filter + search button (auto-search on open), results list with selection, message field + "Send invite" → navigate replace to CARE-03 detail; country from doctor profile location with `Uzbekistan` default.
Done when: invite created and detail opened; errors localized.
Depends: CARE-01, PAT-05 (entry from more-menu).
Fix: debounce search; show avatar via shared avatar composable.

### CARE-03 · Partnership list & detail with progress [M]
Route `care-partnerships`, `care-partnerships/{id}` · Spec refs §5.1, §5.4 · API: list, get, accept/decline/cancel/complete, progress.
Build: list with pull-to-refresh (patient, status chip, role label, doctors), detail header with role-aware actions (Accept/Decline for partner when PENDING; Cancel for initiator when PENDING; Complete when ACTIVE), hint texts, progress feed (author, body, time in practice TZ), composer when ACTIVE; ViewModel with `StateFlow`, refetch after each action; entry points from patient more-menu and (if present) notifications.
Done when: all four transitions work and UI updates; empty/error states; timestamps localized.
Depends: CARE-01, CARE-02.
Fix: confirmation dialog before Decline/Cancel/Complete; no raw `e.toString()` snackbars.
