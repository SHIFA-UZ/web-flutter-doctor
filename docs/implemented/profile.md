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
