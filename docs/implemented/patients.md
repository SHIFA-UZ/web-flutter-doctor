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
