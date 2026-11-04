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
