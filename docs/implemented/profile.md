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
