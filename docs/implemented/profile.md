# Profile, settings, services, subscription (M2)

Built so far on `doc-app2.0`. Spec `docs/kmp-spec/08-profile-settings-reports-legal.md`; flows `docs/ux-flows/08-*`.

## PROF-01 · Profile models and repository

Built 2026-11-04. Spec `08` §1.1, §10, §11. No screen.

- **Models** (`data/model/ProfileModels.kt`): `DoctorMe` = `DoctorProfile` (every `ProfileDto` field, nullable),
  `DoctorContact`, `DoctorBilling`, `DoctorSettings` (`defaultStartTab` defaults to `home`), `DoctorSubscription?`
  (null on old backends: tier unknown, D-03 never fail open); `MeProfile` (`GET /me/profile`, both roles). Flutter
  had untyped maps read with casts (08 §11). Checked against dev on 2026-11-04 (seed doctor): same keys and types;
  unknown keys are ignored, missing ones default.
- **Repository** (`data/profile/ProfileRepository.kt`, app-wide `profileRepository`): `refresh()` (`GET
  /doctors/me`) keeps the result in `me: StateFlow`, shared by the shell (start tab, plan) and the profile screens;
  `updateContact` / `updateBilling` / `updateSettings` (whole block: the backend replaces it, an omitted nullable key is
  cleared) and `update(ProfileUpdateRequest)` (partial: the backend ignores nulls, so clearing a text field means
  sending "") put the block the backend answers with into `me` on success and leave it alone on failure;
  `meProfile()`. `clear()` (on every session end, from `App.kt`) also bumps a generation, so an answer that arrives
  after a sign-out never lands in the next session's profile. Reads and saves are also ordered: a read that started before a newer
  read or save answered is dropped (SHELL-05 review). `services` and `certificates` (JSON arrays in a string)
  are decoded at the boundary (`serviceTags`, `certificateUrls`); a malformed one reads as empty and is logged. Photo/certificate uploads (PROF-03) and the password change (PROF-04) come
  with their screens.
- **Demo data:** like the backend, the profile PATCH merges and contact/billing/settings replace their block; each
  returns the block; `/me/profile` is built from the demo doctor.
- **Tests:** `ProfileRepositoryTest` (5: typed read, whole settings and failure, profile PATCH answer, late answer
  after sign-out, embedded lists), `DemoBackendTest` (+1).

## PROF-02 · Profile root

Built 2026-11-04. Flow `08/01-profile-root.md`; board `DaProfile.dc.html` (root, `rt`).

- **Screen** (`ui/profile/ProfileScreen.kt`), the Profile tab's root in the shell:
  - Bold "Profile" title; **identity card**: photo (brand fallback, `ShifaAvatar` 72), "Dr. med. First Last"
    (`utils/doctorDisplayName`, ported with its Dart test), professions in the app language (Uzbek labels for uz,
    AUTH-07's `Profession.label`), the timezone chip and the plan chip (charcoal; only once known, D-03). Tapping
    the card opens Personal information.
  - **Completeness card** (flow §4's eight checks, 12.5 % each: first name, last name, profession, practice timezone,
    phone, photo, a location (structured address, legacy address or `GET /doctors/me/locations` non-empty), a
    service (`GET /doctors/me/services` non-empty); counts not loaded yet don't count as missing): "Profile 62 %
    complete", a bar, and up to three fix-it chips (Add your name, Add profession, Add practice timezone, Add phone,
    Add photo, Add clinic address, Add services) that open the matching section. Hidden at 100 %.
  - **Sections** with live previews ("Not set" when empty): Personal information (name · dd.MM.yyyy · professions),
    Contact & clinic address (phone · e-mail · street, city, else the legacy address), Practice timezone ("Asia/Tashkent
    (UTC+5)", `TimeZones.utcLabel`, warning colour when unset), Extended profile, Services & pricing, Schedule and
    Reports (both pushed on the Profile stack so Back returns here; placeholders until SCHED-1 / REP-1), Subscription &
    billing (plan), Settings, About; then Sign Out (the
    AUTH-09 question) and the version line ("Shifa Doctor · Version 1.0.1", → About).
  - **States:** skeleton while the first read runs; error with its mapped message, the request id, Retry, and Sign Out
    still there (flow §6); a profile that arrives later from anywhere (the shell's resume refresh) clears the error.
  - No name: "Your name" in the card, "Not set" in the preview (flow §4; `doctorDisplayName` now returns "" instead
    of a lone "Dr. med."). Uzbek profession labels come from the bundled catalogue (AUTH-07).
  - **Staff:** `/me/profile` only (never `/doctors/me`): name, e-mail · phone, timezone; Personal information shows
    "Staff profiles are managed by the clinic owner."; Settings, About, Sign Out.
- **Data:** the shared `profileRepository.me` (PROF-01); read only when the shell hasn't yet.
- **Navigation:** each section pushes `ShellRoute.Profile(section)` (bar hidden); until their tickets the sections
  are placeholders, except Settings, which holds the app lock (SET-03), Language and, in debug builds, the debug menu.
- **Not built here:** the photo action sheet (PROF-03), pull-to-refresh and the offline cache (SHELL/OFFLINE), the
  "phone timezone differs" note (TZ-1).
- **Tests:** `ProfileViewModelTest` (3, incl. request id and recovery), `TimeZonesTest` (+1), `DoctorNameTest` (2), `ProfileScreenshotTest` (4:
  root, incomplete, staff, error). Previews use no photo URL (Kamel can't load one under Robolectric).


## PROF-03 · Personal information

Built 2026-11-04. Flow `08/02-edit-profile.md`; board `DaProfile.dc.html` (`profEdit`).

- **Screen** (`ui/profile/PersonalInfoScreen.kt`), pushed from Profile → Personal information (and the identity card):
  - Own header: Back and a Save check (screen-reader label "Save"; `ShifaSubHeader` gained `checkDescription`),
    shown only when something changed.
  - **Photo:** 96 avatar with "Change photo" under it, one button → sheet with Choose from gallery / Take photo. The
    picked or taken photo uploads at once (`POST /doctors/me/photo`, multipart `file`, JPEG ≤ 1600 px, quality 85,
    EXIF rotation applied); a spinner sits on the avatar meanwhile; "Photo updated" or the mapped error with its
    request id. Native pickers in `utils/ImagePicker` (Android: `GetContent` + `TakePicture` with the CAMERA
    permission and a FileProvider; iOS: PHPicker / UIImagePickerController). No "Remove photo": the backend has no
    way to clear it (BE-30). Every upload lands on the same backend key, so the app adds `?v=<time>` to the answer's
    URL (BE-31).
  - **Personal information:** first and last name (required; letters of any script, spaces, hyphens, apostrophes
    `' ʼ ’`, ≤ 60; trimmed and inner spaces collapsed before sending), date of birth (AUTH-07's picker, dd.MM.yyyy),
    gender (dropdown: Not set / Male / Female / Other, the values registration sends; "Not set" sends "" to clear it).
  - **Professional:** professions as chips → the shared `ProfessionPickerSheet` (moved out of registration into
    `ui/profile/ProfessionPicker.kt`; registration uses it too): search in English, Uzbek Latin and Cyrillic, multi
    select, Clear all, Done; the bundled catalogue with "Couldn't load. Retry" when the backend list fails.
  - **Save:** one `PATCH /doctors/me/profile` with only the changed keys; success shows "Profile information saved"
    on the Profile root (`ShellNotice`, now a `Notice(text, success)`) and closes; failure keeps the form with the
    mapped error and request id.
  - **Back with changes:** "Discard changes?" (Discard / Keep editing); Back on the question closes the question.
  - The ViewModel lives in the shell's store, so a tab switch keeps unsaved edits; each open re-reads the saved
    profile unless an edit is in progress.
- **Review fixes (Codex):** the bundled profession list shows at once; legacy professions are "Current: …" rows in
  the shared picker; all eight EXIF orientations; iOS gallery photos are decoded straight to ≤ 1600 px (ImageIO);
  the uploaded photo's version survives later saves and refreshes; a refused camera shows "Camera access is needed to
  take a photo" with Open settings (`utils/openAppSettings`); Save waits while a photo uploads.
- **Open (the user's call):** the flow's square crop before upload. Android has no platform cropper, so it needs a
  library (uCrop) or a custom crop screen. Flutter didn't crop; avatars display center-cropped.
- **Not built (no backend field):** the board's Short headline, Years of experience and Languages spoken (the doctor
  PATCH has none), and its Account / e-mail block (e-mail is in Contact, PROF-04). Gender is a dropdown, not the
  board's segmented control, matching registration.
- **Fix found on the device:** biometric 1.1.0 pulled `androidx.fragment` 1.2.5, whose `FragmentActivity` refuses the
  request codes of `rememberLauncherForActivityResult`, so any picker crashed the app. `androidx.fragment` 1.8.9 is
  now a direct dependency.
- **Verified on the emulator against dev (2026-11-04):** load, gender save and restore (`PATCH` reached dev),
  discard question, gallery upload, camera upload with the permission prompt, camera refused → banner → Settings. The dev seed doctor
  (`seed.doctor1`) now has a photo (no removal endpoint, BE-30).
- **Tests:** `PersonalInfoViewModelTest` (10: initial form with Uzbek labels, changed-keys-only save and reopen, name
  rules, discard and tab switch, failed save with request id, photo upload success / failure / cancel, photo version
  kept after a save, camera refused, bundled professions while the backend is slow, Save blocked during an upload),
  `DemoBackendTest` (+1, photo upload), `ProfileScreenshotTest` (+2: `PersonalInfo_edited`, `PersonalInfo_errors`).

## PROF-04 · Contact, invoicing and password

Built 2026-11-04. Flows `08/03-contact-and-clinic-address.md`, `08/09-subscription-and-billing.md` (current plan and
invoicing card), `08/11-security.md` (password card); board `DaProfile.dc.html` (`profContact`, `subscription`,
`setSecurity`).

- **Shared edit-form pieces** (`ui/profile/ProfileForms.kt`): `ProfileForm` (header with Back and the Save check,
  scrolling column with keyboard padding, the "Discard changes?" question), `FormExitEffects` (system Back, close after
  Save or Discard), `backStep`, `ProfileSheet`. Personal information (PROF-03) now uses them too.
- **Contact & clinic address** (`ContactScreen.kt`, Profile → Contact):
  - Contact: phone (required; normalised to E.164 by `PhoneE164`, shown grouped "+998 90 123 45 67"), e-mail
    (optional, trimmed and lower-cased, checked), the "Patients see your phone number…" line.
  - Clinic address: Country, Region, District, City, Postal code, Street (≤ 250), all editable (the flow's target;
    Flutter had them read-only), the street helper under it, and the coordinates line (6 decimals, or "Coordinates not
    set"; read-only until the map picker).
  - Practice timezone row → the timezone screen (placeholder until PROF-06).
  - Save: `PATCH /doctors/me/contact` (whole block) when the contact changed, then `PATCH /doctors/me/profile` with only
    the changed address keys ("" clears). A failed address after a saved contact shows "Contact saved, but the address
    could not be saved" with the request id, and only the address stays dirty. Success: "Contact details saved" or
    "Location saved" on the Profile root.
  - Not here: address search, "Pick on map", reverse geocoding, the static preview (PROF-07).
- **Subscription & billing** (`BillingScreen.kt`, Profile → Subscription & billing):
  - Current plan card ("PREMIUM · Your current plan"), shown once the plan is known.
  - Invoicing details card: Billing name, Billing email (checked), IBAN / Account number (spaces removed, upper-cased;
    an IBAN of 15–34 or 10–34 digits, no checksum), Tax ID / VAT ID, and Save in the card. `PATCH /doctors/me/billing`
    sends the whole block: blanks are left out (the backend sets every field, so a missing key is cleared), the Stripe
    account and the Click / Payme ids go back as read. Saves and stays with "Payment & invoicing saved".
  - Not here: plan cards, feature matrix, checkout and Stripe Connect (SUB-02).
- **Security** (`SecurityScreen.kt`, Settings → Security, for doctors and staff): the app lock section (moved here
  from the Settings placeholder) and the password card: Current, New (with the registration checklist), Confirm, each
  with show/hide; Change password. Passwords go exactly as typed (`AuthRepository.changePassword`, `POST
  /auth/reset-password`). The policy is checked first, so a 400 means a wrong current password: "Current password is
  incorrect" under it, the field cleared. Success clears the form with "Password updated successfully". Back with
  typed passwords asks before discarding; a clean Back forgets everything. Change password is enabled only when the
  request can succeed; a 400 is read as a wrong current password because the policy is checked first (BE-32 asks for
  a distinct code).
- **Shell:** a success notice ("… saved") is cleared when the doctor opens another screen or tab; warnings stay until
  closed. `ShifaSubHeader`'s check is labelled "Save" on these forms.
- **Verified on the emulator against dev (2026-11-04):** contact screen loads; postal code saved and cleared again;
  billing name saved and cleared again; wrong current password → inline error; discard question; the Settings →
  Security route. The contact PATCH itself was not sent to dev (the e-mail may be the sign-in identity); it is covered
  by tests.
- **Tests:** `ProfileFormsViewModelTest` (12: contact load, validation, contact + address save order and payloads,
  address only with a cleared field, partial failure and retry, billing payload and plan, IBAN rules, password
  validation and send-as-typed, wrong current password, discard, Change password enablement and reset on Back,
  billing keeps a newer Stripe account), `ShellViewModelTest` (+1, notices),
  `DemoBackendTest` (+1, password change), `ProfileScreenshotTest` (+3: `Contact_phoneError`, `Billing_ibanError`,
  `Security_password`; `PersonalInfo_errors` re-recorded: the Save check hides during an upload).

## PROF-05 · Profession catalogue and dental detection

Built 2026-11-04. Spec `08 §4`.

- **Catalogue:** done earlier and recorded there. `GET /public/professions` (138 entries with both labels) with the
  bundled copy as the immediate fallback (AUTH-07, PROF-03); `Profession.label()` per locale (uz Latin, ўзбекча
  transliterated, en / ru English: the catalogue has no Russian labels); search over English, Uzbek Latin and Cyrillic
  (`List<Profession>.matching`); the shared `ProfessionPickerSheet` with legacy "Current: …" rows (PROF-03). The
  stored value stays the English labels joined with ", ".
- **Dental detection** (`data/catalog/DocumentationTemplate.kt`): `isDentalProfession` / `documentationTemplateFor`
  ported from Flutter's `isDentalDocumentationProfession` (its English, Uzbek and Russian keys). It also catches
  Implantologist, which Flutter missed, plus the Uzbek labels of the catalogue's oral and maxillofacial surgeons. Any
  dental entry in a multi-profession value picks the dental template. The consult screens and the treatment-plan
  wizard will use it (CONSULT / PLAN tickets).
- **Tests:** `ProfessionCatalogTest` (5): Flutter's pinned catalogue checks (138 entries, Urologist → Urolog, ten
  specialties), every dental entry in English and Uzbek, Russian / Uzbek free text, a multi-profession value, and no
  other catalogue entry flagged.

## PROF-06 · Practice timezone picker

Built 2026-11-04. Flows `08/05-practice-timezone-picker.md`, `08/06-practice-timezone-change-warning.md`; board
`DaProfile.dc.html` (`setTimezone`).

- **Shared list** (`ui/profile/TimeZonePicker.kt`, `TimeZonePickerSheet`): each row the city ("Buenos Aires"), then
  "Region/City · UTC+5" (offset now); the device's zone first ("Detected on this device"), then the current one
  ("Current", ticked), then the board's short list; typing searches every platform zone by id, city with spaces
  ("buenos aires") or offset ("+5", "utc+5", "+5:30"); "No timezone found. Try the city name in English." when nothing
  matches. Registration's picker now uses it too.
- **Practice timezone sheet** (`PracticeTimezoneSheet` + `TimezoneViewModel`): opened in place over the Profile root
  (row and completeness chip) and over Contact & clinic address (its row follows the saved zone without touching the
  form's edits). A first zone saves at once; the same zone closes; another zone asks "Change practice timezone?" (from
  → to with offsets; what 09:00 becomes, or "Times will look the same today…" for an equal offset; the phone's zone
  when it differs from the new one), then `PATCH /doctors/me/profile {"timeZone"}`. Success: "Practice timezone saved"
  on the screen beneath. The warning stays up while saving; a failure shows in it with the request id, and Confirm
  retries. The example uses the offsets now and says "the day before" / "the next day"; an unreadable zone gets no
  example. Closing during a save and reopening keeps the save; a rotation keeps the search and the warning.
- **Not built:** region headers and the flow's curated 47 (the board's short list of 19 plus full search instead), the
  "Unknown timezone" row, the appointment count in the warning (no calendar data yet), and staff's read-only
  "Set by your clinic" caption (staff never reach the sheet).
- **Verified on the emulator against dev (2026-11-04):** sheet over the Profile root, the warning for
  Tashkent → Berlin ("09:00 becomes 05:00"), Cancel (nothing written).
- **Tests:** `TimezoneViewModelTest` (5: first zone saves, same zone closes, change asks first and confirm saves,
  failure keeps the old zone, 09:00 shift with day changes and DST, save surviving close/reopen and rotation),
  `TimeZonesTest` (+2: city / offset search incl. Etc/GMT+5, current zone order),
  `Register_timeZonePicker` screenshot re-recorded with the new rows.

## PROF-07 · Location: map pin and coordinates

Built 2026-11-04. Flows `08/03` §3.3 and §7, `08/04-map-pin-picker.md`; board `DaProfile.dc.html` (`profMap`).

- **What it does:** Contact & clinic address gained "Pick on map". The map picker (inside the same screen, so the
  form keeps its edits) opens on the saved pin, or on Tashkent's centre (41.2995, 69.2401) when there is none. It
  shows the native map (`utils/MapPin`: osmdroid with OSM tiles and the "© OpenStreetMap contributors" credit on
  Android, MapKit on iOS), one pin a tap moves, and a card with the coordinates (6 decimals), "Tap the map to move the
  pin" and Confirm (also the header check). Confirm puts the coordinates into the form; Save sends `latitude` and
  `longitude` together in the profile PATCH, even when no address field is filled (flow §12: the pin is never lost).
  Back closes the map, not the form; after the pin moved, Back or × asks "Discard changes?" first. iOS moves the pin
  with a tap gesture on MapKit (compiled, not yet run on a simulator).
- **Not built (backend first, BE-15):** address search, reverse geocoding (fields from the pin), the static map
  preview, the short address in the map card. The address fields are typed; no Google call from the app. Also not
  built: "my location" (needs the location permission flow) and dragging the pin (tap to move).
- **Verified on the emulator against dev (2026-11-04):** map opens with OSM tiles, a tap moves the pin and updates
  the coordinates, Back closes the map without changes.
- **Tests:** `ProfileFormsViewModelTest` (+2: pin → form → PATCH with both coordinates, Back closing only the map and
  asking first after a move;
  Tashkent default); `Contact_phoneError` screenshot re-recorded with the coordinates line and the button.

## PROF-08 · Extended profile

Built 2026-11-04. Flow `08/07-extended-profile.md`; board `DaProfile.dc.html` (`profExtended`).

- **Screen** (`ExtendedProfileScreen.kt`, Profile → Extended profile), on the shared form pieces:
  - Biography (multiline, ≤ 2 000 with a counter from 1 800, "Shown to patients on your profile"), Telegram and
    Instagram: saved together with Save (one `PATCH /doctors/me/profile`, changed keys only, "" clears). Flutter's
    per-keystroke PATCHes are gone. Back with changes asks first.
  - Service tags: input + Add (or the keyboard's Done), chips with ×. Each add or remove saves at once as the JSON
    string the backend keeps (`ProfileRepository.saveServiceTags`); trimmed, inner spaces collapsed, 1–40 characters,
    no duplicate in any case, at most 30. A failed write puts the list back with the error and request id.
  - Certificates: the file names (tap opens the file in the in-app browser), delete with "Delete certificate?" (PATCH
    of the remaining URLs, reverted on failure), "No certificates yet", and Upload certificate → tray with Take photo,
    Choose from gallery (both JPEG ≤ 1600 px via `utils/ImagePicker`) or Choose file (PDF, `utils/DocumentPicker`) →
    `POST /doctors/me/certificate` (multipart `file`; the backend adds the URL to the list). A refused camera shows the
    shared "Camera access is needed" banner.
- **Ordering:** every tag and certificate write (add, remove, upload, delete) waits its turn on one queue and sends
  the latest list, so quick edits never overtake each other and a delete never drops an upload in flight; a failure
  shows the server's lists again. Telegram / Instagram are checked ("@name" or a link); files over 25 MB (the backend's
  limit) are refused before sending.
- **Shared:** `ProfileRepository` uploads go through one multipart helper (photo and certificate);
  `CameraDeniedBanner` and `DiscardChangesSheet` live in `ProfileForms.kt`.
- **Not built:** upload progress bar and cancel, thumbnails, upload dates (not in the data), the corrupt-JSON warning
  (a bad stored value reads as an empty list and is logged), "@handle" display of pasted URLs.
- **Verified on the emulator against dev (2026-11-04):** a tag added (saved as `["Qatag"]`) and removed again (`[]`).
- **Tests:** `ExtendedProfileViewModelTest` (7: tag rules and JSON payload, revert on failure, certificate upload and
  delete payload, Save with changed keys only, rapid edits in order, delete waiting for an upload, handle and size
  checks), `DemoBackendTest` (+1, certificate upload), `ProfileScreenshotTest`
  (+1: `ExtendedProfile_tags`).
