# Patients, documents and forms (area 06, M4)

## PAT-01 · Patient models

Built 2026-11-04. Spec 06 §8. No screen.

- **Models** (`data/model/PatientModels.kt`): `Patient` as `/patients` answers it (checked against a dev row:
  id, name, phone and `phones[]`, birth date, language, photo, chronic disease, account, the location parts, time zone,
  SMS reminder, gender, blood group, allergies, `clinicalStatus`, `atRisk`, `followUpRequired`); `PatientAppointment`
  (`/patients/{id}/appointments`), `ProphylaxisSetting`. The inline `documents` are left to DOCS-01.
- **One place for display rules** (06 §8 Fix): `displayId` "P-00018", `age(today)` in whole years on an injected date
  (the practice date), `status` (`PatientStatus` Active / AtRisk / FollowUp / Unknown: a new value never crashes),
  `allPhones` (`phones[]`, trimmed, once each; the single `phone` only when the list has nothing usable), `smsHours`
  (1 or 24, anything else 24, even a value that isn't a number), `formattedLocation` (street, city, district, region
  as flow 06/05 orders them; else the legacy `address`). A birth date after today gives no age.
- **Not here:** the inline `documents` (DOCS-01 reads them from their own endpoint).
- **Tests:** `PatientModelsTest` (3: + a stale single phone, the location order, a future birth, non-numeric SMS hours; a dev row with display id, age either side of the birthday, status, phones and
  location; lenient fields: a string id, a single phone, odd SMS hours, an unknown status, a bad date, a missing name).

## PAT-02 · Patients repository

Built 2026-11-04. Spec 06 §6–7. No screen.

- **Directory** (`data/patients/PatientsRepository.kt`): one `patients` flow for every screen. `GET /patients?page=&
  size=100&sort=fullName,asc`, a plain list (dev) or a Spring page, until the last page (at most 200, logged), rows
  without an id dropped, each patient once. Reads are numbered: a read older than a create, an update or a sign-out
  is dropped, so it can't remove a new patient or bring back a signed-out doctor's list. `refreshIfStale()` reads again only after 60 s (Flutter refetched every page on every tab
  switch); `refresh()` for pull-to-refresh. Cleared on sign-out.
- **One patient:** `patient(id, clinicId?)` returns an error with its status and request id (Flutter's provider turned
  every error into null), and the directory takes the fresh copy; `create` / `update` (`PatientRequest`: null left out,
  `""` clears) do the same.
- **The rest:** `appointments(id)` (rows read one by one), `createAccount(id)` (`{username, oneTimePassword}`),
  `sendTestSms(id)` (403 / 503 come back as such for the caller to name), `prophylaxis(patientId, clinicId)` (an empty
  answer is "no setting", not an error) and `saveProphylaxis`, and the booking list `forAssignment()` (a list or a page,
  sorted, once each). Endpoints checked against dev's OpenAPI and real reads (`/patients`, `/appointments`).
- **Backend note corrected:** `sort=fullName,asc` works on both lists; only `name,asc` fails. BE-33 now asks only for
  search.
- **Demo:** four patients in memory (one at risk), create and edit kept, appointments empty, a demo account, the test
  SMS answered, no prophylaxis setting.
- **Tests:** `PatientsRepositoryTest` (5: + a Spring page and a held read across a create; two pages with a row without an id and a repeat; the TTL; a 404 with its
  request id and a created patient joining the directory with the exact body; prophylaxis empty and set, the booking
  list as a page), `DemoBackendTest` (+1).

## PAT-03 · Patients directory

Built 2026-11-04. Flow `06/01-patients-list.md`; spec 06 §1.2–1.5. The doctor's Patients tab (clinic staff keep their
own shell). Phone layout only: the tablet list + detail panes are not built (phone/tablet app; the detail is a pushed
screen, PAT-05).

- **Screen** (`ui/patients/PatientsScreen.kt`): "Patients" with the count badge ("5 patients", plural rules), the search
  field (with its clear button), the status filter (sheet: All statuses / Active / At risk / Follow-up; the icon turns
  primary while one is set), New patient (shown once PAT-04 wires the sheet: no dead button), All patients / Favorites /
  Follow-ups, the name order chip (A–Z / Z–A), the rows, and "Showing 1 to 10 of 47 patients" with ‹ › and up to five
  page numbers. Pull to refresh; clear of the tab bar.
- **Rows:** the avatar (photo or the brand fallback, the project rule; the flow's initials are not used), the name or
  "Patient", "36, Female · +998 90 …" with the parts left out when unknown (no "—" line), the status pill (Active teal,
  At risk amber, Follow-up info blue; an unknown status counts as Active), chevron. Tap pushes the patient route (a
  placeholder until PAT-05).
- **Search:** 300 ms after typing stops, over name, raw id, display id ("P-00007") and phone digits (3 or more, so
  "90 123" finds "+998 (90) 123-45-67"; Flutter compared the stored string). Any change of search, segment, filter or
  order starts again on page 1 (Flutter kept the page on sort); opening a sheet or closing an error keeps the page, a
  shrinking list keeps it in range, and a new page scrolls to the top. Phone digits are compared only for number-like
  queries, so "Alice 123" doesn't match phones.
- **Data:** the shared directory (PAT-02), read on open only when older than 60 s; pull and Retry read again. Ages on the
  practice date. A first read that fails shows the error with Retry; a failed refresh keeps the list with the error
  above it.
- **Dropped (PAT-03):** "Recent" (Flutter's was the last 20 by name, not recency) and "Recent activity" sort (document
  dates only).
- **Favourites:** kept on the phone per doctor (`FavoritesStorage`), loaded when the signed-in doctor arrives (also the
  practice date for ages); the star itself is on the patient detail (PAT-05). The segments and the pager wrap on narrow
  screens; Previous / Next show only where they lead.
- **Not built:** the tablet two-pane layout, the chronic-disease warning on open (PAT-05), deep-link selection and the
  clinic-roster back row (later tickets).
- **Verified on the emulator against dev (2026-11-04):** the five dev patients with age, gender, phone and status;
  "malika" narrowed to Malika Tursunova.
- **Tests:** `PatientsViewModelTest` (6: + page kept in range and through a sheet, words not matching phones; ten per page and page resets, both orders; debounced search over name, display
  id and phone digits; follow-ups, the status filter with unknown as Active, clear filters; favourites across screens and
  no second read while fresh; a failed first read), `PatientsScreenshotTest` (2: `Patients_list`, `Patients_empty`).

## PAT-04 · Create patient

Built 2026-11-04. Flow `06/02-add-patient.md`; spec 06 §1.6. One sheet for every caller (Flutter had two with
different fields): the directory's New patient (the new patient then opens) and the booking picker's New patient
(the new patient comes back picked); Home and the clinic will use the same.

- **Sheet** (`ui/patients/CreatePatient.kt`): Full Name * (required; "Full name is required" after the first try),
  phone rows (one at least; remove on each when there are several; Add phone number), Email, Address, Language (ten),
  Gender (Not specified / Male / Female / Other), Blood group (Not specified / A+ … O−), Allergies, Birth date (the
  registration date picker: 1900 to today, "dd.MM.yyyy"). Create Patient stays off while the name is empty.
- **Request** (`POST /patients`): name trimmed; phones normalised to E.164 with the Uzbek default (`PhoneE164`), once
  each, `phone` too when exactly one; blank fields left out; `chronicDisease` "" (set later in Medical info).
- **Language decision (2026-11-04, mine, to confirm):** stored as a code ("uz", "ru", "en", "de", "kaa", "kk", "ky",
  "tg", "tk", "ar"), as the patient app and dev's seed store it; Flutter's doctor app sent English words ("uzbek").
  `PatientLanguage.of` reads both.
- **Saving:** the sheet stays open and locked with the button's spinner (Flutter closed it first and lost the input on
  an error). 409 → "A patient with this phone number already exists." under the phones (the server's text is never
  shown); 400 → "Couldn't create the patient. Check the details."; other failures → the mapped message with the request
  id. Success → "Patient created" on both paths (set after the caller opens the new patient, since opening a screen
  clears success notices).
- **Closing** (×, scrim, drag, Back) with anything typed asks "Discard changes?"; never while saving. A drag or scrim
  tap can't hide it then: the shared `ProfileSheet` gained `confirmHide`, asked before the sheet hides (other sheets
  keep hiding as before). A picked birth date can be cleared.
- **Not built:** "Open existing patient" on a duplicate (the backend doesn't return the id), the E.164 preview line,
  contacts import.
- **Verified on the emulator (demo data, 2026-11-04):** "Demo New" with "901112233" was created, opened, and listed
  with +998901112233. Not created on dev: there is no way to delete a test patient there.
- **Tests:** `CreatePatientViewModelTest` (4: the exact request with E.164 phones once each, a language code and blank
  fields left out; no name / a bad phone / a bad email send nothing; a 409 keeps the sheet with the phone marked and a
  400 says check the details; closing asks only with input, and language codes and words both read),
  `PatientsScreenshotTest` (+1: `Patients_create`).

## PAT-05 · Patient detail

Built 2026-11-04. Flow `06/03-patient-detail.md` (and the chronic warning of `06/04`); spec 06 §2.1–2.3, §1.7. Board
`DaPatients`, screen `patDetail` (header, stats, record tabs, Overview's Clinical summary, Patient App Access and SMS
appointment reminders). Phone layout: a pushed screen from the directory (`ShellRoute.Patient(id)`, ids only).

- **Screen** (`ui/patients/PatientDetail.kt`): back (no title: the header names the patient), avatar, name (or
  "Patient", two lines at most), the status pill ("Active patient" / At risk / Follow-up), "AI risk detected" when
  `atRisk`, the chronic disease as a red pill, "Last visit: 21 October 2026" or "No recent visit", the star. Six tiles
  two per row (Age, Gender, Phone, Birth date dd.MM.yyyy, Patient ID, Language in the app's language; "—" when
  unknown). The six record tabs (Overview, Medical info, Appointments, Documents, Forms, Activity: the flow's renames);
  only Overview is built, the others say "This part of the record is on its way." until their tickets. Pull to
  refresh; the tabs stay pinned under the app bar while the record scrolls.
- **Data:** the directory's copy shows at once, then a fresh read (a 404 shows "Patient not found" with Back, any other
  failure "Couldn't load this patient." with Retry and the request id; with a patient shown, a failed refresh is a
  banner, but a 404 still leaves: the patient was deleted elsewhere). **Last visit** is the last COMPLETED appointment up to now, on the practice date (Flutter used the newest
  document's date); a failed appointments read leaves "No recent visit".
- **Favourites:** one `FavoritesRepository` (flow per doctor, kept on the phone) shared with the directory, so a star
  on the detail shows in Favorites at once (checked on the emulator).
- **Overview:** Clinical summary (chronic disease and allergies in red when set; "None", "No known allergies", "Not
  specified"); Patient App Access ("Account active" + username, or "No account yet" + Create Patient Account → the
  login in a sheet only Done closes, each value copyable, "shown only once"; the account flag comes from a fresh read;
  409 says the patient already has one); SMS appointment reminders only when the doctor's profile allows SMS: the
  switch and the timing (24 h / 1 h) save at once with only that field, roll back on failure, one save at a time;
  without a phone the warning shows and nothing is sent; Send test SMS names 403 (not on your plan) and 503 (service
  down).
- **Chronic-disease warning:** once per patient per session (Flutter showed it on every selection), cleared on sign-out.
- **Not built here:** Quick actions, the ⋮ sheet and "AI summary" (they open booking from the detail, chat, upload,
  forms, briefing and partners, which come with PAT-07, CHAT, DOCS, FORM, AI and CARE tickets; no dead buttons), the AI
  copilot card and the clinical timeline (AI tickets), Recent activity (the activity ticket), the tablet two-pane
  (same call as CAL-5), deep links to a tab (PUSH-03).
- **Tests:** `PatientDetailViewModelTest` (7: last visit on the practice date and one warning per session; the shared
  star; 404 (also over a directory copy) vs failure with request id; SMS save with the exact body and rollback; no phone sends nothing; test-SMS
  403/503; the account shown once and 409), `PatientsViewModelTest` on the shared favourites; screenshots
  `PatientDetail_overview`, `PatientDetail_sparse`, `PatientDetail_notFound`. Checked against dev on the emulator
  (read only; the star set and cleared).

## PAT-06 · Medical info

Built 2026-11-04. Flow `06/05-edit-patient-medical-info.md`; spec 06 §2.4. Board `DaPatients`, `patDetail` Medical info
(General Information with "Edit details", phones, chronic disease with its note, gender and blood group side by side
on the board, stacked here on a phone, allergies with Save).

- **Tab** (`ui/patients/PatientMedical.kt`): read-only rows (Patient ID as P-00042, birth date, email, the address as
  "Street, City, District, Region" or the legacy address, language; empty rows left out), then the fields kept here.
  **Dropdowns save at once** (chronic disease, gender, blood group; "None" / "Not specified" send "" to clear, checked
  on dev); **phones and allergies save with their button** (allergies also on the keyboard's Done; Flutter lost
  allergy text left without Done). Only the changed field is sent, so another field edited elsewhere isn't
  overwritten. One save at a time: every input is locked under a thin bar; a failure rolls the value back with
  "Failed to update" and the request id.
- **Phones:** the whole list in E.164, once each (the backend sets `phone` from `phones`, checked on dev); a bad row is
  marked, a phone another patient has (409) keeps the typing and says so; saving with no phone left asks first ("SMS
  reminders stop"), here and in Edit details. A save answered 404 shows "Patient not found" (deleted elsewhere).
- **Chronic disease:** the 22 conditions in the app's language (`ChronicDisease`, the English name stays on the wire);
  a stored value outside the list shows as written until changed (Flutter couldn't show it); "A warning will be shown
  when opening this patient." under a set condition. The header, summary and warning dialog use the same labels now.
- **Leaving with typed phones or allergies** (another tab, Back, the system back) asks "Discard changes?". A pull to
  refresh keeps the typing.
- **Edit details** (the board's button; flow §12 "no way to edit name, email…"): the create sheet (PAT-04), filled,
  titled "Edit details", Save enabled once something changed, sending only the changed fields as a PATCH (a cleared
  text as ""); "Patient details saved". Typing left in the tab survives unless the sheet changed that field; a 404
  there shows "Patient not found" too. A birth date can be changed but not cleared there (a PATCH keeps a null).
  Address edits the legacy one-line address; the structured location parts aren't edited in the app (as before).
- **Not built:** the prophylaxis card (clinic scope): the detail is only opened from the doctor's own directory today;
  it comes with CLINIC-06, which opens it with a clinic id (the repository calls exist since PAT-02).
- **Tests:** `PatientDetailViewModelTest` (+4: dropdown saves with clearing and rollback; a 404 on save; phones: bad row, 409, saved
  list, last-phone question; the discard guard on tab and back, allergies save and rollback, Edit details over drafts),
  `CreatePatientViewModelTest` (+3: edit filled, only changes sent as PATCH; removing every phone asks; a 404 hands back); screenshots `PatientDetail_medical`, `Patients_edit`. Checked on the emulator
  against dev (read only in the app; the clearing and phone rules checked with direct requests, restored).

## PAT-07 · Appointments and Activity

Built 2026-11-04. Flows `06/03` §3.6 (Appointments), `06/11-patient-activity-timeline.md`, `06/04` (Make appointment);
spec 06 §2.5, §2.8. Board `DaPatients`, `patDetail` Appointments ("Appointment history" + New appointment, cards) and
Activity (month groups), Overview's Recent activity.

- **Visits** (`PatientDetailViewModel`): `GET /patients/{id}/appointments` once per open (and on pull, Retry), on the
  practice clock (Flutter used the phone's time, 06 §2.5), newest first. "Last visit" comes from the same list. A
  failed read shows "Couldn't load the appointments." with the request id and Retry in the tabs and Recent activity,
  and "No recent visit" in the header (Flutter printed the raw error).
- **Appointments tab** (`ui/patients/PatientVisits.kt`): "4 November 2026 · 09:00–09:30", "reason · place" (Video
  Call, the location's name, or In Clinic), the calendar's status pill (every status, NO_SHOW too; an unknown one as
  sent). Empty: "No appointments with you yet." Rows aren't tappable yet: the appointment panel needs a calendar
  entry, and the visit details screen comes with folder 05 (APPT tickets).
- **Activity tab and Recent activity** (latest six, "View full history"): visits as events (Visit completed /
  cancelled / No-show / in progress / requested / Upcoming visit, the board's tints), Today / Yesterday / the date,
  grouped by month in the tab; upcoming visits (ahead and still booked: confirmed, requested, awaiting payment, in
  progress) aren't history: apart under "Upcoming" (soonest first) in
  the tab, left out of Recent activity. Any status other than the known ones is a neutral "Visit" naming its status
  (Awaiting payment, Expired and Payment failed now have labels in the shared status pill, the calendar's too), never "upcoming". Both
  tabs are lazy rows (100+ visits, flow 06/03 §8). Documents, forms, tasks and partnerships join with DOCS-04, FORM-05, TASK and CARE
  (never by sniffing titles, flow §8); the filter chips come then too.
- **New appointment** (`ui/calendar/SlotPicker.kt`): a sheet with the month (shaded like the calendar, past days
  closed) and the picked day's future free slots (re-checked every 30 s and at Next) (two places at one time stay two chips, named), then Next opens
  CAL-7's booking screen with this patient already chosen (still changeable; leaving untouched doesn't ask to
  discard). One booking screen for both paths
  instead of Flutter's second dialog; the visit list refreshes on return. Checked on the emulator against dev up to
  the booking screen (nothing booked).
- **Not built:** quick actions and the ⋮ sheet (still waiting for chat, upload and forms), tapping a visit.
- **Tests:** `PatientDetailViewModelTest` (+1, and visits on the practice clock newest first in the first test: a
  failed read with Retry), `SlotPickViewModelTest` (4: today's past slots hidden and past days closed; a slot booked
  elsewhere leaves with its selection; a failed day; a slot starting while open), `BookingViewModelTest` (+1: a chosen patient, not a change); screenshots
  `PatientDetail_appointments`, `PatientDetail_activity`, `Patients_slotPick`, and Overview with Recent activity.

## DOCS-01 · Document models and repository

Built 2026-11-04. Spec 06 §4.1, §4.3–4.6, §2.10 (download). No screen (DOCS-04 lists, DOCS-05 views, DOCS-03 uploads).

- **Models** (`data/model/DocumentModels.kt`): `PatientDocument` as dev answers `GET /patients/{id}/documents`
  (id, title, date, url, canView, creatorLabel, creatorType, category, isSharedWithTeam, uploadedByPatient,
  appointmentId; checked against dev's OpenAPI and a real read). A missing or bad date is null (Flutter turned it into
  "now"); `locked` = not `canView`. `DocumentCategory`: the 27 codes, 22 medical results (shared with every doctor)
  and 4 private, plus `FORM_025_2`, which the upload sheet won't offer but which now gets a label (Flutter showed form
  documents without a chip); `lab` for the activity feed (by category only, never by title). Labels in the app's
  language: `ui/documents/DocumentLabels.kt` (the `documentCategory_*` keys).
- **Download** (`DocumentFile`): always through `/download` (never the list's `url`); the name from
  `Content-Disposition` (RFC 5987 `filename*=UTF-8''…` first, so Cyrillic names survive, then quoted or bare
  `filename`, quotes and a language tag respected); the type from the first bytes (JPEG, PNG, GIF, WebP, PDF), then the content type, then the extension;
  anything else stays a binary the viewer won't try to draw as a PDF (Flutter did).
- **Repository** (`data/documents/DocumentsRepository.kt`): `list` (one bad row never drops the rest), `download`,
  `requestAccess` (`POST …/request-access` with `{}`), each in a clinic's scope when given. **Dev fact:** downloading
  a locked document answers **404 with an empty body**, not 403; it comes back as `NotFound`, which DOCS-04/05 show as
  "locked, request access".
- **Demo:** a shared blood test that downloads as a real one-page PDF (valid cross-reference table, for the viewer) and
  the patient's locked ECG (404); request access succeeds.
- **Tests:** `DocumentModelsTest` (3: categories, file names in every form, type order), `DocumentsRepositoryTest` (3:
  lenient list with the clinic, a Cyrillic RFC 5987 name and a JPEG behind an octet-stream type, the locked 404;
  request access), `DemoBackendTest` (+1).

## DOCS-02 · Pickers and photos to PDF

Built 2026-11-04. Spec 06 §4.2, §9. No screen (DOCS-03 uses it); a "Photos to PDF" check on the debug Spikes screen.

- **Already there (CORE-07, PROF):** the PDF picker (`rememberDocumentPickerLauncher`, PDF only), the photo library and
  camera launchers (`rememberImagePickerLauncher`, `rememberCameraLauncher`: upright JPEG, quality 85, at most
  1600 px, so the "compressJpeg" step of the plan is done by the pickers), and `FileSaver`. Nothing duplicated.
- **New: `utils/imagesToPdf`** (multi-page scan): each JPEG on its own A4 page, fitted inside a 24 pt margin and
  centred. **One shared writer instead of the planned `PdfDocument` / `UIGraphicsPDFRenderer`:** it puts each JPEG in as
  it is (`/DCTDecode`), so a page weighs what its photo does. Measured on the emulator: Android's `PdfDocument` stored
  two small images as 2.7 MB; two camera photos through the shared writer are 48 KB and render in the PDF view. Only an
  8-bit baseline, extended or progressive JPEG with 1 or 3 channels, complete and ending with its end marker, is
  embedded; anything else (a cut-off photo, lossless or 12-bit JPEG, CMYK, not a JPEG) is skipped; none left is null. Flutter embedded camera photos at quality 85 the same way.
- **Tests:** `ImagesToPdfTest` (5: the JPEG frame header after other segments and what can't be embedded; pages with
  the image bytes unchanged and others skipped; the cross-reference offsets point at every object; tall and wide
  photos fit the page). Checked on the emulator: two camera photos → a 2-page PDF shown by `PdfView`.

## DOCS-05 · Document viewer

Built 2026-11-04. Flow `06/08-document-viewer.md`; spec 06 §2.10. Not in the boards as a screen (the board's Documents
tab opens one); built in the existing style. Route `ShellRoute.Document(patientId, documentId, title, clinicId)`,
opened from the Documents tab (DOCS-04).

- **Screen** (`ui/documents/DocumentViewer.kt`): back and the title; a page-shaped skeleton while downloading; then by
  type (DOCS-01's sniffing): **images** pinch-zoom 1–4×, pan once zoomed, double tap 2×, fitted on open; **PDFs** in
  the existing `PdfView` (Android `PdfRenderer`, iOS PDFKit) inside the same zoom; **anything else** (DOCX, unknown
  bytes) "This file type can't be previewed." with Save to device (Flutter fed it to the PDF view and failed
  silently); a PDF the renderer can't open says so, with Save too.
- **States:** 403 "You don't have access to this document." with Request access (then back with "Request sent");
  404 "This document is no longer available." with Back (dev answers 404 for a locked document too, see DOCS-01);
  any other failure, or an empty answer, "Could not load document" with Retry and the request id.
- **Save to device** (`FileSaver`, Downloads on Android): the server's file name, else the title with the type's
  extension; "Saved to your device" or "Couldn't save the file.". The file stays in memory otherwise; nothing about
  it is logged.
- **Zoom** (`ui/widgets/Zoomable.kt`): two fingers zoom, one finger is left to the content at 1× (so PDF pages still
  scroll) and pans once zoomed.
- **Not built:** Share, Print, a page indicator, "Document info", the password-protected PDF message (the renderer's
  failure is the generic one), downsampling huge images (uploads from the app are at most 1600 px).
- **Leaving** releases the file and cancels a running download or request, so the shell (which keeps the
  ViewModel) holds no document and reopening starts afresh.
- **Tests:** `DocumentViewerViewModelTest` (5: a PDF typed by bytes; 403 / 404 / failure with request id / empty;
  request access, then reopening; a failed save; late answers after leaving), screenshots `DocumentViewer_loading`, `_noAccess`, `_cantPreview`.

## DOCS-04 · Documents tab

Built 2026-11-04. Flow `06/06-patient-documents.md`; spec 06 §2.6, §4.6. Board `DaPatients`, `patDetail` Documents
("Document History", search, the cards). The record's Documents tab.

- **Tab** (`ui/documents/PatientDocuments.kt`, rows in the record's lazy list): "Document History", the search (title,
  instant, any case; shown once there are documents), then the cards newest first as the server sends them. Read when
  the tab shows, each time it shows again (back from the viewer: access may have changed) and on pull. A failed first
  read is the tab's error with Retry and the request id; a failed refresh is a banner over the list. Empty: "No
  documents available" with the hint; no match: "No documents match “…”".
- **Card:** a lock for a locked one, the title (two lines, then …), the date dd.MM.yyyy ("—" when missing; Flutter
  showed today), the category chip (teal for results shared with every doctor, grey for private ones, the 025-2 form
  chip too, an unknown new code humanised), the shared-with-team mark (screen reader: "Visible to all doctors of this
  patient"), "Uploaded by …" on a locked one (a name as given; "Doctor" / "Patient" / "Another user" in the app's
  language). Open only with a file to fetch (`canView` and a `url`); at 150% text the Request access button goes under
  the details. A 025-2 form's PDF reads "Form 025-2
  (21.10.2026)" in the app's language, by category, not by guessing from the title. Open (the whole card too) opens
  the viewer with that title; a locked card offers Request access instead ("Request sent", "Request already sent" on
  a 409; the lock stays until the owner approves).
- **Not built yet:** Upload (DOCS-03, next), the header ⋮ (Create Form with FORM-03, Create Task with TASK), Edit on a
  linked form (FORM-05 links forms to their PDFs), the deep-link outline (PUSH-03), the "Requested" chip (no backend
  flag).
- **Checked against dev on the emulator:** patient 14's blood test (shared) opens in the viewer (dev's real PDF); the
  locked ECG shows its byline and Request access (not pressed on dev: a request can't be withdrawn).
- **Tests:** `PatientDocumentsViewModelTest` (4: list and search; first-read error vs refresh banner; a late failing
  read; request access sent / already sent / error), `DocumentModelsTest` (+1: openable), screenshot
  `PatientDetail_documents`.

## DOCS-03 · Upload

Built 2026-11-04. Flow `06/07-upload-document.md`; spec 06 §4.2, §9. Not a board screen (the board's Documents tab
has the Upload button); built in the existing style. Started from the Documents tab's "Upload".

- **Sources** (`ui/documents/DocumentUpload.kt`): Upload PDF (the PDF picker; the file is typed by its bytes and only a PDF
  or a JPEG/PNG goes, else "Only PDF and image files can be uploaded."), Take Photo and Choose from Gallery (one
  JPEG, uploaded as the image itself: Flutter wrapped it in a PDF), Scan (multi-page): camera, "Add another page?"
  with "2 pages scanned", Finish (or the camera cancelled with pages taken) → one PDF through DOCS-02's writer, named
  `scan_20261104_0930.pdf` on the practice clock. Camera refused for good → "Camera access is off…" with Open
  settings.
- **Title & type sheet:** the title prefilled (the file name without its extension, or "Scanned document (2 pages)",
  plural rules; an emptied title falls back to it; at most 120 characters); the type is required (chips in two
  headed groups, medical results shared with every doctor, then private types; Add stays disabled with "Select a
  document type" under the choices until one is picked);
  "Large file: the upload may take a while." above 10 MB; Cancel with scanned pages asks "Discard the scanned
  pages?" (a drag or a scrim tap too).
- **Upload:** multipart `file` (the right content type and name) with `title` and `category` as form fields and the
  clinic as a query parameter (dev's OpenAPI lists them as request parameters; Flutter sent fields). A card over the
  list shows the title, the share sent once the size is known, and Cancel ("Upload cancelled"). Success: "Document
  “…” uploaded" and the list read again (the new document on top). Failure: the banner with the request id and Retry
  sending the same bytes (kept in memory only; no offline queue, flow §6); a 404 says "Patient not found". Over
  25 MB is refused before sending ("The file is too large (max 25 MB)"); an empty file says "No file data to upload".
- **Demo:** uploads are kept in memory per patient, listed first, and open as what was uploaded. **Checked on the emulator in demo mode** (an upload on dev
  can't be deleted): a two-page camera scan, the type required, uploaded, on top of the list as an X-ray.
- **Not built:** the in-visit entry with `appointmentId` (folder 05; the repository takes it), Quick actions / ⋮
  entries (later tickets), HEIC conversion (the gallery picker already returns JPEG), several uploads at once (one at
  a time: Upload is disabled while one runs).
- **Tests:** `DocumentUploadViewModelTest` (4: PDF naming, empty and too large; the type required, then the multipart
  body with title fallback, category and clinic; the scan loop to a 2-page PDF and the discard question; failure with
  Retry, Cancel of a running upload), `DemoBackendTest` (+ upload), screenshots `Upload_details`, `Upload_status`.

## FORM-01 · Form models and dental-chart codec

Built 2026-11-04. Spec 06 §3.2–3.3. No screen.

- **Models** (`data/model/FormModels.kt`): `PatientForm` as dev's OpenAPI `PatientFormDto` lists it (every field of the
  025-2, the dental chart as a flat string map, follow-ups, the linked document, the signature fields, clinical-engine
  disease and chip selections, `createdByDoctorId`); read leniently (an old string `formNumber` reads as a number,
  unknown keys ignored). Saving sends `FormRequest`: the same without the server's fields (signature requested /
  signed at / signature image, creator), null fields left out. **One ICD system spelling:** Flutter sent "ICD10" from
  the picker and "ICD-10" from the clinical engine (06 §10); a save now always sends "ICD10" (the ticket's constant).
  Dates go as `YYYY-MM-DD`; an empty chart and empty lists are left out. Templates: only
  `025-2` (06 §3.1).
- **Dental chart** (`data/forms/DentalChartCodec.kt`, ported from Flutter's `dental_chart_codec.dart`): FDI keys
  ("11"…"48", primary "51"…"85"), legacy "UR1" keys normalised and migrated, the dentition meta key and row widths (16
  / 10), editable rows `TOP_{row}_{cell}`, history rows `TOP_HIST_{i}_{cell}` with their date and doctor, the next
  history index, the prefill of a new 025-2 from the patient's latest (teeth and history carried over, the latest's
  editable rows become history dated and signed with this visit, empty rows dropped), dd.MM.yyyy history dates; `DentalCode`, Flutter's twelve chart codes (O, R, C, P, Pt, П, A, I, II, III,
  K, И).
  **Fixed on the way:** when a chart held both "11" and "UR1", Flutter's migration kept whichever came first in the
  map; the FDI value now always wins. Flutter had no codec tests (the plan's "ported tests" don't exist); written
  here.
- **Tests:** `DentalChartCodecTest` (5: FDI keys for both dentitions and normalisation; dentition and widths;
  migration with the FDI value winning in either order; editable and history rows, sparse and ordered, out-of-range
  cells dropped, next index; prefill), `FormModelsTest` (3: lenient read, the request without server fields, one ICD
  spelling; days and empties left out; the dental codes).
