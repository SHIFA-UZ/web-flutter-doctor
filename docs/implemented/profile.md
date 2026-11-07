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

## SVC-01 · Services & pricing

Built 2026-11-04. Flow `08/08-services-and-pricing.md`; spec `08 §2`; board `DaProfile.dc.html` (`setServices`).

- **Data** (`data/services/ServicesRepository.kt`, `data/model/ServiceModels.kt`): `GET /doctors/me/services`,
  `/service-groups` (sorted by sort order, then name), `/locations`; `POST` / `PATCH /doctors/me/services[/{id}]`
  with `{title, description, isActive, isFreeConsultation, groupId, prices[]}` and `DELETE`, every status checked
  (Flutter ignored them). `paymentType` is never sent: the backend keeps it on PATCH and defaults new services to
  PAY_NOW. Group writes are in the repository for SVC-02.
- **List** (`ServicesScreen.kt`, Profile → Services & pricing): services grouped by group ("No group" first), each with
  title, two-line description, price lines through `Money` ("150 000 UZS · All locations (default)", "20.00 USD ·
  Clinic — City", "Location #id" for a removed one) or "Free consultation (video)", and "Clinic-managed" / "Inactive"
  tags (inactive rows dimmed). Skeleton, error with the request id and Retry, empty state, sticky "Add Service".
- **Editor** (in the same screen, on the shared form scaffold): Title (required, ≤ 80), Description (≤ 500), Group,
  Active, Free consultation (video) (hides the prices; "Existing prices will be removed" when it had some), price rows
  of amount + currency (UZS, USD, EUR, RUB, plus any unknown saved code) + scope (all locations or one location), Add
  price, remove a row. Amounts go through `Money.parse` (UZS whole units, others ≤ 2 decimals); blank rows are skipped;
  invalid ones are marked; at least one valid price; no duplicate currency + scope. Save → "Service saved"; Delete asks
  "Delete service?". Back with changes asks first. A clinic-managed row doesn't open: "This service is managed under
  Clinic → Services." An untouched price row sends its saved minor units back unchanged (a stored fractional UZS amount
  isn't rounded); a delete that finds the service already gone counts as done; the list reloads each time the screen
  opens.
- **Not built:** the group filter chips and summary strip, swipe to delete, reordering (SVC-02 has the groups
  editor).
- **Verified on the emulator against dev (2026-11-04):** list with locations; a new "QA test" at 50 000 UZS saved as
  `amountMinor` 5 000 000 for all locations (`paymentType` PAY_NOW by default), then deleted through the app; the
  validation message when no price was entered.
- **Tests:** `ServicesViewModelTest` (8: load with groups in order, failed load, clinic-managed, validation, create
  payload, edit / free / delete, unchanged fractional UZS price, 404 delete), `DemoBackendTest` (+1: services in memory), `ProfileScreenshotTest` (+2:
  `Services_list`, `Services_editor`).

## SVC-02 · Service groups

Built 2026-11-04. Flow `08/08` §3 (groups editor sheet) and §7.

- **Entry:** a "Service groups" row above the services list (it shows the group names); the flow puts a folder icon in
  the app bar, but `ShifaSubHeader` has no slot for a custom action, so the row stands in.
- **Editor** (in the services screen, on the shared form scaffold): one row per group: name (required, ≤ 60), move up
  and move down (the order is the sort order: 1, 2, 3…), delete; Add group; the helper says services in a deleted
  group become ungrouped (the backend's `ON DELETE SET NULL`). Save runs the deletes, then the creates and the changed
  rows, one request at a time, stopping at the first failure with its message and request id; then services, groups
  and locations reload so the list shows the new group names. A group already gone (404 on delete) counts as deleted. Deleting asks "Delete group? Its services stay and become ungrouped." first; controls are off while
  saving; a created group keeps its id in the draft, so a retry after a partial failure never creates it twice; the
  editor closes only after the reload.
  Back with changes asks first.
- **Not built:** drag to reorder (up / down instead).
- **Tests:** `ServicesViewModelTest` (+2: delete, PATCH with the new order, POST of a new group, the required name;
  retry after a partial failure),
  `ProfileScreenshotTest` (+1: `Services_groups`; `Services_list` re-recorded with the groups row).

## SUB-02 · Plans, checkout and Stripe Connect

Built 2026-11-04. Flow `08/09-subscription-and-billing.md` §3–§5; spec `08 §1.7, §5.3`.

- **Data** (`data/subscription/SubscriptionRepository.kt`): `GET /doctor/subscription/plans` (dev: BASIC €99 and PRO
  €199 a month; PREMIUM has no plan, admins set it), `POST /doctor/subscription/checkout {planCode, gateway:
  "STRIPE"}` → `checkoutUrl`, `POST /doctor/payments/stripe/connect/onboard {}` → `onboardingUrl`.
- **Subscription & billing** gained two cards above the invoicing details:
  - **Plans** (once the plan is known, D-03): each plan's name and "99.00 EUR / month" (`Money`), "Current plan" on
    the doctor's, "Upgrade to Pro" on higher ones, nothing on lower ones (no downgrade in the app); "Your plan is
    managed by Shifa…" when there is nothing higher to buy (PREMIUM, or PRO with no PREMIUM plan).
  - **Payouts:** "Not connected" or "Connected · acct_••••1234" (last four only), the helper line, and Connect Stripe /
    Resume Stripe onboarding.
  - Both pages open in the in-app browser (Custom Tab / SFSafariViewController, `utils/InAppBrowser`). Back in the
    app (`ON_RESUME`) the profile is read again: after a checkout "You're now on PRO" or "No change yet. Payments can
    take a minute."; after onboarding the account line updates. A failed request: "Couldn't open the payment page"
    with the request id. `CodeBanner` gained `args` for the plan name. After a checkout the plan is read up to three
    more times, 3 s apart (the webhook may lag); an unread profile keeps the check for the next return; reopening
    the screen keeps a request in progress. "Renews on dd.MM.yyyy" from `GET /doctor/subscription/me` when there is a
    paid period; a blank page URL is an error; failed plans show Retry.
- **Not built:** the feature matrix, the "Needed for: …" chip from locked features, the `shifa://billing/return` app
  link (the resume check covers closing the tab), "Open Stripe dashboard" (no endpoint).
- **Not tried on dev:** the checkout and onboarding buttons (they create Stripe sessions); covered by tests and the
  demo backend, which answers with placeholder pages.
- **Tests:** `BillingSubscriptionTest` (5: plans and the checkout request, resume after checkout changed / unchanged,
  Connect and its failure, renewal date and plans retry, empty URL), `ProfileScreenshotTest` (`Billing_ibanError` re-recorded with plans and payouts).

## SET-01 · Settings

Built 2026-11-04. Flow `08/10-settings-root.md`; spec `08 §1.8`; board `DaProfile.dc.html` (`settings`).

- **Screen** (`ui/profile/SettingsScreen.kt`, Profile → Settings; replaces the placeholder):
  - General: Country (sheet: Uzbekistan, Germany, USA, Other; stored as those English words, shown localised),
    Language (→ the language screen, SET-02; the row shows the language in its own name), Starting screen (sheet of
    the shell's tabs; Tasks only when the plan has remote-care tasks) with the helper line.
  - Security: the Security screen (PROF-04: password and app lock), Two-factor Authentication and Encrypted Documents
    switches (stored flags only; no helper text promising behaviour the backend may not have, as the flow asks).
  - Notifications → the OS settings for the app (no in-app preferences), About (LEGAL-01), and in
    debug builds the debug menu.
- **Writes:** every change shows at once and `PATCH /doctors/me/settings` sends the whole block (`country, language,
  twoFA, encryptedDocs, defaultStartTab`), built from the latest state when its turn comes in one queue, so two quick
  toggles never overwrite each other; `language` is the app language's backend code (Cyrillic Uzbek is `uz`). A
  failure puts back only the fields that request carried (a later change stays and goes out next), with the error
  and request id. A spinner shows while a write is in flight; a start tab the plan no longer allows is saved as Home;
  the Tasks choice follows the plan when the profile refreshes.
- **Clinic staff:** no doctor settings (the route is doctor-only): Language, Security, Notifications, About.
- **Not built:** Privacy & data (LEGAL), the app-lock preview on the Security row ("App lock on · Face ID"), the
  notification-permission state ("Allowed" / "Blocked").
- **Tests:** `SettingsViewModelTest` (6: whole-block payload with the language code, quick toggles in order, revert
  on failure, Tasks gating and staff, partial revert, start-tab repair), `ProfileScreenshotTest` (+1: `Settings_doctor`).

## SET-02 · Language

Built 2026-11-04. Flow `01/15-language-settings.md` §3a; board `DaProfile.dc.html` (`langSettings`).

- **Screen** (`ui/profile/LanguageScreen.kt`, Settings → Language; replaces the placeholder): "Select Language" and the
  four languages, each in its own name (English, O'zbekcha, Ўзбекча, Русский) with the name in the current language
  under it; a tap applies at once (no Save). The list scrolls and reads as a radio group (`ShifaOptionList(role =
  Role.RadioButton)`, also used by the Settings country and starting-screen sheets).
- **Already in place and reused:** the mini toggle on the sign-in screens (`ui/auth/LanguageSwitch`, AUTH-02), the
  regional default (Uzbekistan → Uzbek, `LanguageStorage.applyRegionalDefault`), and the backend sync in `App`
  (`PUT /doctors/me/language` on sign-in and every change, Cyrillic Uzbek as `uz`, deduplicated by
  `LanguageRepository`); the settings PATCH (SET-01) carries the language too.
- **Verified on the emulator against dev (2026-11-04):** Russian applied at once and dev showed `settings.language =
  ru`; back to English (`en`).
- **Tests:** the sync and storage were covered by CORE-04 / AUTH tests; `ProfileScreenshotTest` (+1:
  `Language_settings`).

## SET-03 · App-lock settings

Built 2026-11-04. Spec `08 §6.2`; flow `08/11` §3 and `08/10` §3.3.

- **What was left:** the app-lock section itself (switch, PIN set / confirm, turn off with the PIN, biometric unlock)
  came with LOCK-01 and moved onto the Security screen with PROF-04; the seven strings Flutter never translated
  (`appLock, appLockSubtitle, biometricUnlock, setPin, pin, confirmPin, incorrectPin`) are in en / uz / ru.
- **Added:** the Settings → Security row's preview: "App lock on" (· "Biometric unlock" when that is on) or "App lock
  off", read again each time Settings shows (the lock is changed on the Security screen and Back lands here). The
  generic "Password and app lock" subtitle from SET-01 is gone.
- **Tests:** `SettingsViewModelTest` (+1: preview off, then on with biometrics after reopening); `Settings_doctor`
  screenshot re-recorded.

## REPORT-01 · Reports

Built 2026-11-06. Spec 08 §7, flow 08/14, ticket REPORT-01. Board `DaProfile` reports (`ui/analytics/ReportsScreen.kt`).
- **Where:** More → Reports (the tab's root: title and subtitle) and Profile → Reports (pushed, Back to Profile). Not
  for clinic staff (the tab falls back to the placeholder they never reach; their More sheet has no Reports).
- **Content:** "Clinic analytics, trends, and performance reports", then Home's Clinic performance card
  (ANALYTICS-02/03: range, Export, today's KPIs, PREMIUM trend / visit types / engagement or the teaser, nothing while
  the plan is unknown) with one more block, **SMS reminders** for the range: SMS sent (grouped), total SMS cost, "{price}
  {currency} per SMS" (hidden at 0); "SMS reminders are not enabled for your account. Contact support." when the flag
  is off or the call answers 403 (flow §6 partial data). `GET /doctor/analytics/sms-usage?from&to` (plain dates, the
    range's), read on every visit, on refresh and on a new range (the block goes back to its skeleton); an answer for
  an older range is dropped. Pull to refresh. Each visit also passes the practice day as it is now (a new day moves an
  untouched range). As a tab root the page clears the floating tab bar.
- **Range:** Reports has its own (a separate ViewModel); Flutter shared it with Home, so changing it here moved Home's
  (flow §13 flags it). Export is Home's CSV (the dashboard metrics and the trend; no SMS rows, as Flutter);
  its visit-type rows now say "(30d)", the backend's window, not the period.
- **Codex review:** SMS skeleton on a new range, the practice day on each visit, tab bar clearance, the CSV's 30-day
  labels (answers in `codex/REPORT-01.md`).
- **Checked:** emulator on dev (reads only): More → Reports: the KPIs (0, 0, 2, 1), SMS "not enabled" (dev answers
  `smsRemindersAllowed: false`, checked with a real GET), the trend for 31 Oct – 6 Nov (PREMIUM seed doctor).
- **Tests:** `AnalyticsViewModelTest` (Reports reads SMS for the range and again for a new one, a 403 as not enabled,
  Home never reads it); screenshots `Reports`, `Reports_noSms`.

## LEGAL-01 · Contract visibility spike and About (LEGAL-02 not built)

Built 2026-11-06. Spec 08 §8, flow 08/13, tickets LEGAL-01, LEGAL-02; BE-16. Board `DaProfile` legalAbout
(`ui/profile/AboutScreen.kt`).
- **Spike result (LEGAL-01):** the early-partner contract is issued only by `POST
  /admin/doctors/{id}/early-partner-contract/issue` (dev OpenAPI and the backend source: no other contract or legal
  route). A doctor can't read their contract, and there is no legal-documents endpoint either. Decision as the master
  plan set it: About ships with the public page; the contract card stays hidden; LEGAL-02 waits for BE-16 (`GET
  /doctors/me/contract`).
- **About:** Settings → About and the Profile version line (doctors and clinic staff). The app ("S" mark, "Shifa
  Doctor", "Version 1.0.1"); Legal → Privacy policy (`https://shifa.uz/#privacy`, the page the patient app links, in
  the in-app browser; "Couldn't open the page" when it can't); Copy diagnostics (also seven taps on the version, the
  board's gesture): "Shifa Doctor 1.0.1 (12) · android release · production · uz-Cyrl · Asia/Tashkent" (version and
  build, platform and build type, backend or demo, the app's language, practice zone; no names, ids or patient data) →
  "Diagnostics copied".
- **Left out, not invented:** terms of service, open-source licences, support contacts, Report a problem and Rate the
  app (no published URLs or contacts; BE-16 asks for the legal URLs, support contacts need product). The version line shows
  the version name (the build number is in the diagnostics).
- **Codex review:** build number and environment in the diagnostics, the app's language tag, the tap counter in the
  screen (answers in `codex/LEGAL-01.md`).
- **Checked:** emulator: Profile → About, Copy diagnostics ("Diagnostics copied"), Privacy policy opened shifa.uz in a
  Custom Tab, Back to About.
- **Tests:** `AboutTest` (the diagnostics line, the URL); screenshot `About`.

## LEGAL-02 · Legal pages and "My partnership contract" (BE-16)

Built 2026-11-07. Board `DaProfile` legalAbout. `AboutViewModel` + `data/legal/LegalRepository`.

- **Legal:** Terms of service, Privacy policy and Open-source licences as `GET /public/legal?lang=` lists them (the
  backend language of the app), each "Version {version}", opened in the in-app browser. A backend without the route (or
  a failed read): the public privacy page only, as before.
- **Contract:** doctors only (`GET /doctors/me/contract`; 404 `NO_CONTRACT` or an older backend: no section). The row
  shows the number and "from 01.06.2026"; it opens the backend's PDF (`/doctors/me/contract/pdf`) full screen (skeleton,
  then the pages; a failure with its request id and Retry). Clinic staff never ask.
- **Demo:** the legal links, contract SHIFA-0461 and a one-page stand-in PDF.
- **Tests:** `AboutViewModelTest`, `DemoBackendTest`; screenshot `About` re-recorded (Terms, Privacy, the contract).
