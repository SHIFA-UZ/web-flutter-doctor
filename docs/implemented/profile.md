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

