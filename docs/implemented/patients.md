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
