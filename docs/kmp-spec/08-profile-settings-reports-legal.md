> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 07 · Profile, Services & Pricing, Location, Subscription, Settings, Reports, Legal PDFs

Source: `/Users/sheroziy.saidkhodjaev/Projects/Private/web-flutter-doctor` (read-only analysis, 2026-10-24).
All paths below are relative to the repo root. `P` = `lib/features/profile/presentation/profile_screen.dart`.

> Heads-up on `P`: lines 1–820 are a `/* … */` commented-out legacy copy of the whole screen (`P:1`, `P:820`). Live code starts at `P:821`. All citations below point at the live copy.

---

## 1. Profile screen (`ProfileScreen`)

### 1.1 Entry, data source, role branch
- Hosted as shell tab index 8 (`DoctorShellTab.profile`, `lib/features/shell/domain/doctor_shell_tab.dart:11`), kept alive in an `IndexedStack` (`lib/features/shell/presentation/main_shell.dart:384`); clinic-staff shell uses index 3 (`main_shell.dart:373`). Desktop sidebar labels this tab **"navSettings"** with a gear icon (`main_shell.dart:602`), mobile "More" sheet labels it profile (`main_shell.dart:873-881`).
- `build()` watches `languageProvider` purely to force rebuilds on locale change (`P:1082`), then branches: JWT role `clinicStaff` → `_ClinicStaffProfileShell` (`P:1083-1085`); else `profileAllProvider` (`P:1086`).
- `profileAllProvider` = `FutureProvider<ProfileAll>` → `GET /api/doctors/me` → `{profile, contact, billing, settings, subscription?}` (`lib/state/profile/profile_providers.dart:12-33`). Throws `StateError` when no token or when role is clinic staff (`profile_providers.dart:14-19`).
- **ProfileAll composition** (`lib/state/profile/profile_models.dart:2-17`): four untyped `Map<String,dynamic>` blocks + optional `subscription` map (`{tier, features[]}`; only `tier` is read anywhere, `lib/state/subscription/doctor_subscription_provider.dart:19`). No typed model exists for the doctor profile — every field is read with `as String?` casts in widgets.
- Loading → full-screen spinner; error → `somethingWentWrong` text, error is `debugPrint`ed (`P:1088-1094`).

### 1.2 Responsive layout
- `PlatformLayout.useSinglePane(context)` ⇒ `Responsive.splitPaneBreakpoint = 720` px (`lib/core/layout/responsive.dart:22`, `lib/core/layout/platform_layout.dart:37`).
- Single pane: section list, tapping a card sets `_mobileDetailOpen = true` and shows the panel with a "← Profile" `TextButton.icon` back affordance (`P:1123-1158`, `P:1195-1202`).
- Two pane: `Row` with list `flex: 3` and panel `flex: 2`, 24 px gap, `AnimatedSwitcher` 250 ms keyed by selected panel (`P:1159-1188`).
- Background `AppColors.cardboard`, padding `Responsive.screenPadding` (`P:1119-1121`).

### 1.3 Section list (left column) — order and preview lines (`P:1220-1323`)
| # | Card title (l10n key) | Preview lines | Action |
|---|---|---|---|
| 1 | `profileInformation` | full name (`firstName lastName`), DOB as `dd.MM.yyyy` (`_fmtDobLabel`, `P:898-903`), `profile.address` | panel `profile` |
| 2 | `contactDetails` | `contact.phone`, `contact.email` | panel `contact` |
| 3 | `paymentAndInvoicing` | `billing.billingName`, `billingEmail`, `iban`, `taxId` | panel `payment` |
| 4 | `settings` | `settingsSubtitle` | panel `settings` |
| 5 | `servicesPricing` | `servicesPricingSubtitle` | panel `servicesPricing` |
| 6 | `extendedProfile` | `extendedProfileSubtitle` | panel `extended` |
| 7 | `schedule` | `updateOrChangeSchedule` | **pushes `ScheduleScreen`** via `MaterialPageRoute` (`P:1306-1311`), never "selected" |
| 8 | `password` | `changeOrResetPassword` | panel `password` |

Each card is `_SectionCard` (white, radius 16, 2 px brand border when selected, chevron) (`P:2568-2640`). Missing values render as `—`.

### 1.4 Panel wrapper and the "Save" button (`P:1326-1383`)
Every panel is wrapped in a white card with a scrollable body and a full-width `ShifaPrimaryButton` **Save**. Save runs the panel's optional `onSaveAsync` then `_saveCurrentSectionBackend` (`P:920-996`), which for **profile, payment, settings, servicesPricing, extended** only shows a success snackbar — it does not persist anything. Only `contact` and `payment` pass an `onSaveAsync`; `password` does its own POST inside `_saveCurrentSectionBackend`. Errors are surfaced as `e.toString().replaceFirst('Exception: ', '')` red snackbar, 5 s (`P:1366-1376`).

### 1.5 Panel: Profile information (`P:1397-1525`)
Controllers are created inside `build` on every rebuild (`P:1399-1410`) — see quirks.

| Element | Behaviour | Validation | Persistence |
|---|---|---|---|
| Header: `CircleAvatar` r=28 + name + **Upload photo** button (`_headerAvatarAndName`, `P:2526-2566`) | `NetworkImage(photoUrl?t=<cacheBuster>)` (`P:1112-1116`); `image_picker` gallery, `maxWidth: 1600`, `imageQuality: 85` (`P:1039-1045`) | none (no size/type check client-side) | `POST multipart /api/doctors/me/photo`, part name `file` → `{photoUrl}`; then `ref.refresh(profileAllProvider.future)` and bump `photoCacheBusterProvider` (`lib/state/profile/profile_actions.dart:97-129`) |
| Section header "personalInformation" | `_sectionHeader` divider style (`P:2484-2516`) | | |
| Full name `TextFormField` | `onChanged` is a **no-op** with a commented-out `patchProfile` (`P:1438-1441`) | none | **NOT SAVED** |
| Birth date (read-only + calendar icon) | `showDatePicker` 1900..today, initial = dob or 1990-01-01, locale via `localeForMaterialIntl` (`P:1452-1465`); picked date only updates the text; `patchProfile({'dob': iso})` is commented out (`P:1470`) | n/a | **NOT SAVED** |
| Profession `SearchableProfessionDropdown` | single select | n/a | immediate `PATCH /api/doctors/me/profile {"profession": "<English label>"}` (`P:1482-1487`) |
| Section header "workplaceInformation" | | | |
| Practice timezone `SearchableTimezoneDropdown` | hint/label hard-coded English `'Practice timezone (e.g. Europe/Berlin)'` (`P:1496-1497`) | n/a | immediate `PATCH /api/doctors/me/profile {"timeZone": "<IANA>"}` (`P:1498-1503`) |
| `LocationPickerSection` (see §3) | | | immediate `PATCH /api/doctors/me/profile` with 8 location keys (`P:1508-1520`) |
| Save button | snackbar `profileInformationSaved` only (`P:928-932`) | | |

Profile map keys read here: `firstName, lastName, dob (yyyy-MM-dd), address, photoUrl, profession, timeZone, latitude, longitude, locationCountry, locationRegion, locationDistrict, locationCity, locationPostalCode, locationStreetAddress, biography, services (JSON string), certificates (JSON string), telegram, instagram, subscriptionTier (legacy)`.

### 1.6 Panel: Contact details (`P:1526-1564`)
- Fields: phone (`TextInputType.phone`), email (`emailAddress`). **No client validation.**
- Save → `PATCH /api/doctors/me/contact {"phone": trimmed, "email": trimmed|null}` (`P:1540-1543`); non-200 → message from body `message` else `Save failed: <status> <body>` (`profile_actions.dart:17-34`). Refreshes `profileAllProvider`.

### 1.7 Panel: Payment & invoicing (`P:1565-1782`)
- Fields: billingName, billingEmail, iban, taxId (no validation; no IBAN checksum).
- Save → `PATCH /api/doctors/me/billing` with body `{billingName|null, billingEmail|null, iban|null, taxId|null, stripeConnectAccountId|null (echoed, read-only), clickMerchantId (echoed raw), paymeMerchantId (echoed raw)}` (`P:1586-1594`). Click/Payme merchant IDs are **never editable in UI**, only round-tripped.
- **Stripe Connect card** (`P:1629-1697`, all strings hard-coded English): status `Not connected` / `Connected account: <acct id>`; button `Connect Stripe` / `Resume Stripe onboarding` → `POST /api/doctor/payments/stripe/connect/onboard {}` → `{onboardingUrl}` (`profile_actions.dart:43-50`) → `launchUrl(mode: externalApplication)`. No return/deep-link handling, no refresh after returning — the connected status only updates on next `profileAllProvider` refresh.
- **Doctor monthly subscription card** (`P:1699-1777`, hard-coded English): dropdown `BASIC`/`PRO` (local state, default `BASIC`, `P:879`) and button `Start subscription checkout` → `POST /api/doctor/subscription/checkout {"planCode": "<BASIC|PRO>", "gateway": "STRIPE"}` → `{checkoutUrl}` (`profile_actions.dart:52-65`) → `launchUrl` external. `PREMIUM` is not offered. Shared `_paymentActionLoading` flag disables both buttons (`P:878`).

### 1.8 Panel: Settings (`P:1783-1955`)
Every control calls `patchSettings` immediately with a **full** body built by `buildDoctorSettingsPatch` (`profile_actions.dart:75-92`): `{country: v ?? settings.country ?? 'Uzbekistan', language: currentLanguageTag, twoFA: bool, encryptedDocs: bool (default true), defaultStartTab: key}` → `PATCH /api/doctors/me/settings`.

| Control | Values | Notes |
|---|---|---|
| Country `DropdownButtonFormField` | `Germany`, `Uzbekistan`, `USA`, `Other` (`P:1815-1849`) | stored as these literal English strings |
| Language dropdown | `en`, `uz`, `uz-Cyrl` (`kUzbekCyrillicLocaleTag`), `ru` (`P:1851-1880`) | calls `languageProvider.setLanguage` (SharedPreferences `doctor_language` + explicit flag, `lib/core/providers/language_provider.dart:101-107`). **Not** PATCHed to backend by itself; `language` only reaches the backend when another setting is patched. Does **not** call `PushNotificationService().refreshLocalizationCache()` (the standalone screen does, §6). |
| Starting screen dropdown | `DoctorStartTab.doctorKeys` = chat, home, calendar, patients, clinic, tasks, reports, notifications, profile; `tasks` hidden unless `remoteCareTasks` feature (`P:1792-1803`); persisted as `defaultStartTab` | labels via `_startTabLabel` (`P:905-918`) |
| Two-factor Authentication `SwitchListTile` | `settings.twoFA == true` | only stored; nothing in-app enforces it (UNVERIFIED backend behaviour) |
| Encrypted Documents switch | `settings.encryptedDocs != false` | stored only |
| `AppLockSettingsSection` | rendered only on native mobile (§6) | |
| Save button | snackbar `settingsSaved` only | |

Not present in profile: notification/SMS toggles, logout, delete account, early-partner contract, clinic membership editing. Logout lives in the shell "More" sheet (`main_shell.dart:916`, `_MobileMoreLogoutButton`). Clinic membership is shown in auth onboarding and clinic feature, not here.

### 1.9 Panel: Services & pricing (`P:2195-2220`)
Text `servicesPricingPanelDesc` + button `openServicesPricing` → `Navigator.push(MaterialPageRoute(ServicesPricingScreen))`. Save → snackbar `openServicesPricingToManageEntries`.

### 1.10 Panel: Extended profile (`P:1956-2194`)
Persistent controllers (`P:872-875`), initialised when the panel is first selected (`P:1965-1973`).

| Element | Persistence |
|---|---|
| Biography (`maxLines: 5`) | debounced 500 ms → `PATCH /api/doctors/me/profile {"biography": v}` (`P:2026-2031`) |
| Services (free-text tags): text field + **Add** (Enter or button) | `profile.services` is a **JSON-encoded string** of `List<String>` (`P:1959`, `P:1977-1979`); add/remove → `PATCH {"services": jsonEncode(list)}` then `ref.refresh(profileAllProvider)` + `setState` (`P:2046-2056`, `P:2088-2096`) |
| Certificates: **Upload certificate** button (image_picker gallery, no compression) + list (`cert.split('/').last` title, full URL subtitle, delete) | `POST multipart /api/doctors/me/certificate` part `file` → response key `photoUrl` (`profile_actions.dart:153-180`); list from `profile.certificates` JSON string; delete → `PATCH {"certificates": json}` (`P:2107-2157`) |
| Telegram (`prefixIcon send`) / Instagram (`camera_alt`) | debounced 500 ms `PATCH {"telegram": v}` / `{"instagram": v}` (`P:2160-2189`) |
| Save button | snackbar `extendedProfileSaved` only |

### 1.11 Panel: Password (`P:2221-2267`)
- `Form` + validators: current required (`currentPasswordIsRequired`), new `length >= 6` (hard-coded `'Minimum 6 characters'`, `P:2249-2251`), confirm required; mismatch checked in `_saveCurrentSectionBackend` (`P:959-968`).
- `POST /api/auth/reset-password {"currentPassword", "newPassword"}`; non-200 → body `message` ?? `error` ?? `'Failed to change password'` (`profile_actions.dart:132-150`). Clears fields on success.

### 1.12 Clinic-staff variant `_ClinicStaffProfileShell` (`P:2272-2482`)
- Data: `meProfileProvider` → `GET /api/me/profile` → `MeProfile{id, role, firstName, lastName, email?, phone?, photoUrl?, timeZone?}` (`profile_providers.dart:36-48`, `profile_models.dart:20-55`).
- Read-only cards: avatar (cache-busted) + name; email (labelled with `enterEmail` key — wrong label), phone, practice timezone; then the same password form with Enter-to-submit. No photo upload, no settings.
- Error state renders raw `'$e'` (`P:2340`).

---

## 2. Services & pricing (`lib/features/profile/presentation/services_pricing_screen.dart`)

- Plain `ConsumerStatefulWidget` with `setState`-managed lists; no provider (`:53-57`).
- Load sequence (`_load`, `:65-89`): `GET /api/doctors/me/locations` (via `fetchDoctorLocations`, errors swallowed → `[]`), `GET /api/doctors/me/service-groups` → `List<{id, name, sortOrder}>`, `GET /api/doctors/me/services` → `List<Map>`; non-200 on services silently keeps previous list.
- Service row fields parsed: `id, title, description, isActive, isFreeConsultation, groupId, groupName, clinicManaged, prices[] {amountMinor, currency, locationId?}` (`:112-140`, `:404-411`, `:460-472`, `:504-535`).
- Currencies: `UZS, USD, EUR, RUB`; unknown existing code is kept in the dropdown so edits don't drop it; default `UZS` (`:15-32`). Amount shown as `amountMinor / 100` with 2 decimals; saved as `round(parse(amount) * 100)` (`:134`, `:365`).
- **Edit/Create dialog** (`_editService`, `:102-420`): title, description (2–4 lines), group dropdown (`None` + groups), **Free consultation** switch (hides price rows and sends `prices: []`), N price rows each = amount (decimal keyboard) + currency + scope (`All locations` or a `DoctorLocationDto` `label — city`), "Add price row", remove row when >1.
  - Validation: rows with `amountMinor <= 0` are skipped; duplicate `(currency, locationId|global)` → snackbar `'Duplicate currency and location combination'` (hard-coded); zero valid rows → `'Add at least one valid price'` (hard-coded). **Title emptiness is not validated.**
  - Body: `{title, description, isActive (new→true, else preserve), isFreeConsultation, groupId (nullable), prices[]}` → `POST /api/doctors/me/services` or `PATCH /api/doctors/me/services/{id}` (`:413-418`). **Response status is not checked.**
- Delete → `DELETE /api/doctors/me/services/{id}` with no confirmation and no status check (`:422-435`).
- `clinicManaged == true` rows: no edit/delete trailing icons; tapping edit/delete paths shows `serviceManagedByClinic` snackbar (`:104-111`, `:424-431`, `:518-532`).
- **Groups editor dialog** (`_ServiceGroupsEditorDialog`, `:542-740`, opened from AppBar folder icon): list of `name` + `sortOrder`; edit → `PATCH /api/doctors/me/service-groups/{id} {name, sortOrder:int}`; delete → `DELETE /api/doctors/me/service-groups/{id}` (no confirm); add → `POST /api/doctors/me/service-groups {name, sortOrder}`. No status checks. Ordering is the integer `sortOrder`; the client renders in server order (whether the server sorts is UNVERIFIED).
- List rendering: `Card > ListTile` with subtitle `managedLine + groupName + description + price summary` (one price per line) (`:504-535`).

---

## 3. Location picker (`lib/features/profile/presentation/location_picker_widget.dart`)

### 3.1 `LocationPickerSection` (embedded card)
- Props: `latitude, longitude, locationCountry, locationRegion, locationDistrict, locationCity, locationPostalCode, locationStreetAddress, onLocationSelected(Map)` (`:35-57`). Used by the profile panel (`P:1508`) and the schedule feature's `DoctorLocationsScreen` editor dialog (`lib/features/schedule/presentation/doctor_locations_screen.dart:384-408`).
- UI: header "location", primary button `selectLocationOnMap` (spinner while reverse-geocoding), read-only filled fields for country / region (Viloyat) / district (Tuman) / city / postal code — each rendered only when non-empty (`:259-306`), then an editable 2-line **Street address** field with helper text (`:309-334`).
- On init: if lat/lng present but no structured country/region → reverse-geocode (`:90-96`); same in `didUpdateWidget` (`:123-131`).
- After map pick: 50 ms delay then `_reverseGeocode()` (`:214-221`). On success fills all fields and emits the full 8-key map (`:169-178`). On null result nothing is emitted — **the picked coordinates are dropped** (see §13). Error snackbar (orange) `couldNotGetAddressDetails`.
- Street-address `onChanged` emits the 8-key map on **every keystroke** (`:319-333`) → in the profile panel this is an un-debounced `PATCH` per character.
- Emitted payload keys: `latitude, longitude, locationCountry, locationRegion, locationDistrict, locationCity, locationPostalCode, locationStreetAddress`.

### 3.2 `_MapPickerScreen` (full screen, `:360-596`)
- `flutter_map ^7.0.2` + `latlong2`, OSM raster tiles `https://tile.openstreetmap.org/{z}/{x}/{y}.png`, `userAgentPackageName: 'com.shifa.doctor'` (`:518-521`). Tap moves the single red `Icons.location_on` marker (`:390-394`, `:522-535`). Initial centre = existing pin or **Tashkent 41.2995, 69.2401**, zoom 15 (`:206-209`, `:380`).
- FAB "my location" (`:539-560`) via `geolocator ^12`: service-enabled check → permission check/request → `deniedForever` message → `getCurrentPosition(LocationAccuracy.high)` → move map (`:396-490`). Three localized error snackbars.
- Bottom card: lat/lng to 6 decimals + **Confirm** (`:562-591`); AppBar check icon also pops with the `LatLng` (`:502-506`).

### 3.3 `GoogleGeocodingService` (`lib/core/services/google_geocoding_service.dart`)
- API key resolution (`:20-58`): `AppConfig.googleMapsApiKey` (`--dart-define GOOGLE_MAPS_API_KEY`, `lib/core/config/app_config.dart:40-43`) → else `GET {resolvedApiBaseUrl}/api/public/config` → `{googleMapsApiKey}` (5 s timeout, cached in a static). The key therefore ships to the client / is fetchable unauthenticated.
- `reverseGeocode(lat, lng)` → `GET https://maps.googleapis.com/maps/api/geocode/json?latlng=…&key=…&language=en` (`:74-78`, raw `http`, **always English**); uses `results[0]`; `debugPrint`s the full URL including the key and 500 chars of body (`:80-82`).
- `_parseAddress` mapping (`:150-219`): `country`←`country`; `region`←`administrative_area_level_1`; `district`←`sublocality|sublocality_level_1`; `city`←`locality`; `postalCode`←`postal_code`; `streetAddress`←`street_number + ' ' + route` (fallback `route`). Also returns `formattedAddress` and raw parts (unused by callers).
- `geocode(address)` (forward) exists (`:106-146`) but has **no callers**. The `geocoding ^3.0.0` pub package is declared (`pubspec.yaml:55`) but **never imported**.

### 3.4 What is saved
- Profile: `PATCH /api/doctors/me/profile` with the 8 keys above (free-text `address` is a separate legacy key shown in the list preview and never edited).
- Practice locations (`DoctorLocationDto`, `lib/state/locations/doctor_location_models.dart:4-66`): `id?, label, clinic?, address? (= street address), latitude?, longitude?, 6 structured fields, isPrimary` via `GET/POST/PATCH/DELETE /api/doctors/me/locations[/{id}]` (`lib/state/locations/doctor_location_actions.dart`). Editor validation: label required only (`doctor_locations_screen.dart:315-322`).

---

## 4. Profession catalog & display name

### 4.1 Catalog (`lib/core/models/profession_model.dart`)
- `ProfessionModel{english, uzbek}`; **138** entries (`:35-195`), grouped by comments: General & Primary Care (8), Medical non-surgical (34), Mental health (3), Diagnostic & lab (7), Surgical (22), Women's & reproductive (8), ENT & senses (5), Dental (8: Dentist/Stomatolog, Orthodontist/Ortodont, Oral Surgeon, Periodontist/Parodontolog, Prosthodontist/Ortopedik stomatolog, Pediatric Dentist/Bolalar stomatologi, Endodontist, Implantologist), Children & development (19), Emergency & ICU (8), Other (18).
- **The stored id is the English label string** (e.g. `"Dentist"`, `"Otolaryngologist (ENT)"`), persisted in `profile.profession` — there is no numeric id or slug. Full list is reproducible by copying the Dart constant; KMP port should keep the English string as the wire key for backward compatibility.
- `getDisplayText(locale)` (`:17-24`): `en`/`ru` → English; `uz` → Uzbek Latin; `uz-Cyrl` → `transliterateUzbekLatinToCyrillicUi(uzbek)`. **No Russian labels exist.**
- `matches(query)` substring on both languages (`:27-31`); `findByEnglish`, `search` helpers (`:198-213`).
- Backend catalog: `GET /api/public/professions?lang=<en|uz|ru>&search=` → `[{english, uzbek}]`, raw `http`, 5 s timeout, falls back to local list (`lib/core/services/profession_service.dart:30-103`). Base URL is taken from `apiClientProvider.baseUrl` (`:15-26`).
- Tests: `test/profession_catalog_test.dart` pins `Urologist → Urolog` and presence of 10 specific specialties (`:5-28`).

### 4.2 `SearchableProfessionDropdown` (`lib/features/profile/presentation/searchable_profession_dropdown.dart`)
- `InputDecorator` + dialog list; single (`onChanged(String)`) or multi (`allowMultiple`, chips, `onChangedMultiple(List<String>)`, used only by auth `account_information_screen.dart:227`).
- Loads via `ProfessionService.getProfessions(language: backendLanguageCode, useBackend)` in `initState`; on failure uses the local list (`:51-74`).
- Dialog search filters the already-loaded list locally (`:284-298`); the backend-search branch in `_updateFilteredList` (`:104-120`) is only ever invoked with `''` from `_showSearchDialog` (`:132-133`) → **backend search is effectively dead**.

### 4.3 Dental detection (`lib/features/appointments/dental/dental_documentation_professions.dart`)
`isDentalDocumentationProfession(String?)` (`:25-59`): lower-cased profession contains `dental`, `odont`, `stomat`, `tish`, or equals/contains one of 22 keys (`dentist, orthodontist, endodontist, pediatric/child/children dentist, periodontist, prosthodontist, oral surgeon, oral therapist/therapy, maxillofacial surgeon, maxillo-facial, dental hygienist, dental therapist, stomatologist, стоматолог, ортодонт, пародонтолог, хирург-стоматолог, детский стоматолог`). Returns `DocumentationTemplate.dental` vs `general` (`:15-20`). Note `Implantologist` from the catalog is **not** detected (no `odont`/`dental` substring). Consumers: video/in-person consult screens, treatment plan wizard.

### 4.4 Display name (`lib/core/utils/doctor_display_name.dart`)
`formatDoctorMedName(firstName, lastName, includePrefix=true)` → `"Dr. med. First Last"`, trimmed; empty names → just `"Dr. med."`; `includePrefix:false` → `"First Last"` (`:2-13`). Test: `test/doctor_display_name_test.dart`. Used by shell header, home greeting, patients, calendar, clinic screens.

---

## 5. Subscription

### 5.1 Model (`lib/core/subscription/doctor_subscription.dart`)
- `DoctorTier { basic, pro, premium }` with `atLeast` (`:9-15`).
- `DoctorFeature` and min tier (`:19-53`):
  - BASIC: `calendar, documents, chat, patientManagement, basicAnalytics`
  - PRO: `videoConsultation, aiNotes, speechToText, patientBriefing, askShifaAi`
  - PREMIUM: `remoteCareTasks, advancedAnalytics, differentialDiagnosis`
- `parseDoctorTier(raw)`: `BASIC|PRO|PREMIUM` case-insensitive; **unknown/missing → PREMIUM** (fail-open, `:64-75`). `doctorTierLabel` exists but has no callers.

### 5.2 Providers (`lib/state/subscription/doctor_subscription_provider.dart`)
- `doctorTierProvider = Provider<DoctorTier>`: from `profileAll.subscription['tier']`, legacy fallback `profile['subscriptionTier']`, else PREMIUM; while loading/error → PREMIUM "to avoid flicker" (`:15-28`).
- `doctorFeatureProvider = Provider.family<bool, DoctorFeature>` (`:32-35`).
- Gating surfaces (all UI-side; backend is authoritative per file header): tasks tab visibility & start-tab option (`main_shell.dart:217,349,2082`, `P:1792-1800`, `tasks_screen.dart:37`, `home_quick_action_navigation.dart:86`), Ask Shifa AI (`main_shell.dart:284`, home widgets, `patient_detail_overview.dart:379`), speech-to-text (`doctor_speech_text_field.dart:97,379`, `home_voice_session.dart`), patient briefing (`patient_briefing_panel.dart:32`, `patient_detail_panel.dart:101,1578`), AI notes (`in_person_appointment_screen.dart:2745`), advanced analytics (`home_analytics_section.dart:56`, `reports_screen.dart:60`). `videoConsultation`, `differentialDiagnosis`, `documents`, `chat`, `calendar`, `patientManagement`, `basicAnalytics` are **declared but never checked**.

### 5.3 Billing / checkout model
- Tier is **admin-managed** (file header `doctor_subscription.dart:3-6`); the doctor-side checkout is a thin Stripe hosted-checkout launcher: `POST /api/doctor/subscription/checkout {planCode: BASIC|PRO, gateway: 'STRIPE'}` → `{checkoutUrl}` → external browser (`P:1724-1773`). No webhook/return handling in app; no success/cancel route; no "current plan" display anywhere in profile (the subscription card doesn't show `subscription.tier`).
- Payout side: Stripe Connect Express-style onboarding link `POST /api/doctor/payments/stripe/connect/onboard {}` → `{onboardingUrl}`; `billing.stripeConnectAccountId` displayed when set (`P:1579`, `P:1640-1644`). `clickMerchantId` / `paymeMerchantId` exist in the billing map but have no UI.

---

## 6. Settings screens

### 6.1 `LanguageSettingsScreen` (`lib/features/settings/presentation/language_settings_screen.dart`)
- Opened from the mobile "More" sheet → Language tile via `ShellScope.push(MaterialPageRoute)` (`main_shell.dart:885-913`).
- Options `en`, `uz`, `uz-Cyrl`, `ru` as radio-style rows (`:16-27`, `:45-98`); on tap: `languageProvider.setLanguage(localeFromPersistenceTag)`, `PushNotificationService().refreshLocalizationCache()` (fire-and-forget), snackbar `languageChanged` (`:59-69`). Nothing is sent to the backend.
- `LanguageMiniToggle` (`lib/core/widgets/language_mini_toggle.dart`): pill `EN/UZ/ЎЗ/RU` with a `showMenu` anchored through the root `navigatorKey` overlay (`:74-120`); same 4 values; not used in the profile screen itself (profile uses the dropdown in §1.8).

### 6.2 `AppLockSettingsSection` (`lib/features/settings/presentation/app_lock_settings_section.dart`) — UI only
- Rendered only when `PlatformLayout.isNativeMobile` (`:117`); embedded at the bottom of the profile Settings panel (`P:1951`).
- Card with `SwitchListTile` "App lock" (`appLock` / `appLockSubtitle`) and, when enabled and biometrics available, a `FutureBuilder`-driven "Biometric unlock" switch (`:123-153`).
- Enable: PIN dialog with confirm (`showScrollableFormDialog`, numeric, obscured, `maxLength = AppLockService.maxPinLength (6)`, validity via `AppLockService.isValidPin` → 4–6 digits), then `service.setPin` + `appLockStateProvider.notifier.setLockEnabled(true)` (`:31-37`, `:56-113`). Disable: prompt PIN, `verifyPin`, on failure snackbar `incorrectPin`, else `clearPin`, `setBiometricEnabled(false)`, `setLockEnabled(false)` (`:39-54`).
- **All 7 l10n keys (`appLock, appLockSubtitle, biometricUnlock, setPin, pin, confirmPin, incorrectPin`) are missing from `assets/localization/*.json` and from the embedded map** → the `?? 'English'` fallbacks never fire (`translate()` returns the key string, `lib/core/localization/app_localizations.dart:6278-6300`), so the UI shows raw keys like `appLock` in all languages. UNVERIFIED on device, inferred from code.
- Internals (`AppLockService`, `lockManagerProvider`, inactivity) are covered by another analyst.

### 6.3 Other settings
There is no standalone settings screen; the desktop sidebar's "Settings" item is the profile tab (`main_shell.dart:602`). Notification preferences are not in this area.

---

## 7. Reports screen (`lib/features/reports/presentation/reports_screen.dart`)

- Shell tab index 6 (`DoctorShellTab.reports`), desktop sidebar "navReports" with analytics icon (`main_shell.dart:599`), mobile via More sheet (`main_shell.dart:851-858`).
- Layout: title `navReports` + `reportsScreenSubtitle`, `HomeDashboardToolbar` (date-range button → `showDateRangePicker`, export button) (`:79-82`), one `DashboardCard` "clinicPerformance" containing `AnalyticsKpiCards`, `AnalyticsSmsUsageWidget`, and — only when `advancedAnalytics` (PREMIUM) — `AppointmentsTrendChart`, `VisitTypeDonutChart`, `AnalyticsEngagementWidget` (`:84-102`). Bottom nav clearance padding.
- Date range state: `homeDashboardDateRangeProvider` (`StateNotifierProvider<DashboardDateRangeController, DashboardDateRange>`, default last 7 days, `lib/features/home/application/home_dashboard_date_range_provider.dart:21-42`) — shared with the Home dashboard.
- Export (`:31-54`): `HomeDashboardExportService.exportCsv()` (`lib/features/home/application/home_dashboard_export_service.dart:18-62`) awaits overview, trend, consultation types, engagement providers, builds a CSV (`Metric,Value` rows + `Date,Appointments` trend) and on mobile calls `Share.shareXFiles` (share_plus), on web triggers an anchor download. Snackbars `exportStarted` / `exportFailed`.
- Endpoints consumed (via `DoctorAnalyticsService`, `lib/features/home/application/doctor_analytics_service.dart`): `GET /api/doctor/analytics/overview`, `GET /api/doctor/analytics/appointments-trend?days=<range.dayCount>`, `GET /api/doctor/analytics/consultation-types`, `GET /api/doctor/analytics/engagement`, `GET /api/doctor/analytics/sms-usage?from=&to=` (ISO strings). Response models: `DoctorAnalyticsOverview{appointmentsToday, completedToday, cancelledToday, newPatientsToday}`, `AppointmentTrendPoint{date yyyy-MM-dd, count}`, `ConsultationTypes{video, inPerson}`, `DoctorEngagement{activePatients, documentsReceived}`, `DoctorSmsUsage{sentCount, totalCostMinor, currency, pricePerSmsMinor, smsRemindersAllowed}` (`lib/features/home/domain/analytics_models.dart`). Note the date range only affects `days` for trend and `from/to` for SMS; overview/engagement/types ignore it (the CSV still labels everything with the range).
- Widget internals belong to the Home analyst; Reports is a re-composition of Home widgets.

---

## 8. Legal PDFs — early-partner contract

### 8.1 Where it lives / who triggers it
- Trigger is **admin-only**: Admin → Doctor activity screen → row "Contract" column / detail panel button "Partnership contract PDF" (`lib/features/admin/presentation/admin_doctor_activity_screen.dart:212-233`, `:561`, `:600-614`; `admin_doctor_activity_detail_panel.dart:230-236`).
- Flow (`lib/features/admin/services/admin_early_partner_contract_pdf.dart:11-25`): `POST /api/admin/doctors/{doctorId}/early-partner-contract/issue {}` (`lib/state/admin/admin_actions.dart:624-633`) → JSON → `earlyPartnerContractPdfDataFromIssue` → `generateEarlyPartnerContractPdf` → bytes opened in a new tab (web only; `admin_pdf_downloader_stub.dart:11` throws `UnsupportedError` on non-web). Filename `<contractNumber>_<doctor-name-slug>.pdf` (`:27-35`). Row model carries `earlyPartnerContractNumber` (`lib/features/admin/domain/admin_models.dart:600`).
- **The doctor app never shows, downloads, or signs this contract.** There is no doctor-side endpoint or screen. For the KMP doctor app this is out of scope unless product wants a "my contract" view (see LEGAL tickets).

### 8.2 Issue payload → data (`lib/features/legal/pdf/early_partner_contract_from_issue.dart:6-37`)
`contractNumber (default 'SHIFA-0000'), effectiveDate (first 10 chars parsed, default 2026-06-01), termMonths (default 6), partnerFullName, partnerFirstName, partnerLastName, partnerClinic, roleDoctor (default true), rolePatient, roleBoth, partnerPhone, partnerEmail, newAllocation (bool, used for the snackbar)`. `shifaSignedDate` and `partnerSignedDate` are set to `DateTime.now()` client-side; `autoSignPartner = true`.

### 8.3 `EarlyPartnerContractPdfData` (`early_partner_contract_pdf_data.dart`)
Defaults baked into the client: `contractNumber 'SHIFA-0461'`, `termMonths 6`, `supplierLegalName "Qobilov Bekzodbek Olimjon o'g'li YaTT"`, `supplierAddress "Istiqbol 30, Andijon, O'zbekiston"`, `shifaSignatoryName`, `shifaSignatoryTitle 'CEO, Founder'`, `blankTemplate()` with effective 2026-06-01 / signed 2026-05-25 (`:29-64`).

### 8.4 Rendering (`early_partner_contract_pdf_service.dart`)
- `pdf ^3.10.8`; A4, 40 pt margins, `DejaVuSans.ttf` base font for Cyrillic/Latin (`:28-29`), brand teal `#00BBB0` (`:15`), watermark logo `assets/branding/shifa_logo.png` at 7 % opacity (`:47-73`), `MultiPage` max 12 pages, footer `SHIFA — <contractNumber>`, `Sahifa n / N`, confidentiality line (`:673-723`).
- Content (Uzbek Latin, hard-coded): header (logo, "SHIFA", "Raqamli tibbiy platforma", doc type/version), title "SHIFA ERTA FOYDALANUVCHI HAMKORLIK SHARTNOMASI", meta strip (number, effective date `dd.MM.yyyy`, term), sections **1 Maqsad, 2 SHIFA xizmatlari (2.1–2.3), 3 Hamkor majburiyatlari (3.1–3.2), 4 Ma'lumotlar va maxfiylik, 5 Intellektual mulk, 6 Muddat va bekor qilish, 7 Yakunlangandan keyingi afzalliklar, 8 Javobgarlikni cheklash, 9 Umumiy qoidalar**, TOMONLAR (supplier block + partner F.I.Sh./Klinika/Roli checkboxes `[X]`/Telefon/Email), IMZOLAR (two bordered columns: SHIFA signatory name/title/date; partner name/signature image/date in Uzbek month names), acknowledgement box (`:89-219`, `:366-583`). Section 6 text hard-codes "(olti)" regardless of `termMonths` (`:181`, `:360`).
- Signature image (`partner_signature_image.dart`): ink text from `buildPartnerSignatureInkText` = `"First L."` (`early_partner_signature.dart:20-40`), rendered with `TextPainter` using **Sacramento-Regular.ttf**, fallback **DancingScript-Regular.ttf** (`:71-84`), 52 px, skew −0.12 and rotate −0.04 rad, plus a cubic flourish underline, exported as PNG via `ui.PictureRecorder` (`:13-69`). `buildPartnerSignatureText` (typed legal name) exists but is unused by the PDF (the name line uses `partnerFullName`).
- Tests: `test/early_partner_contract_pdf_service_test.dart` (bytes > 2000, starts with `%PDF`), `test/early_partner_signature_test.dart` (name/ink text rules).

---

## 9. State — providers in this area

| Provider | Type | Holds | Invalidation / refresh |
|---|---|---|---|
| `profileAllProvider` (`profile_providers.dart:12`) | `FutureProvider<ProfileAll>` | `GET /api/doctors/me` blocks | `ref.refresh(.future)` after every patch/upload (`profile_actions.dart`); `ref.refresh(profileAllProvider)` in extended panel; `ref.invalidate` in auth controller (login/logout/OTP flows, `lib/state/auth/auth_controller.dart:99,183,214,252,304,331,405,439,473`); re-evaluates when `authTokenProvider` or `doctorAppJwtRoleProvider` change. Read by ~30 other files (timezone, profession, name, locations). |
| `meProfileProvider` (`profile_providers.dart:36`) | `FutureProvider<MeProfile>` | `GET /api/me/profile` | token-dependent; used by clinic staff shell, auth, shell header |
| `photoCacheBusterProvider` (`profile_providers.dart:10`) | `StateProvider<int>` | epoch ms | set after photo upload; read by profile & shell avatar |
| `profileProvider` (`profile_controller.dart:129`) | `StateNotifierProvider<ProfileController, ProfileState>` | **hard-coded fake data** ("Ulugbek Karimov", IBAN `DE12…`) | **no callers — dead** |
| `doctorTierProvider` (`doctor_subscription_provider.dart:15`) | `Provider<DoctorTier>` | derived from `profileAllProvider` | automatic |
| `doctorFeatureProvider` (`doctor_subscription_provider.dart:32`) | `Provider.family<bool, DoctorFeature>` | derived | automatic |
| `languageProvider` (`language_provider.dart:110`) | `StateNotifierProvider<LanguageNotifier, LanguageState>` | `Locale`; SharedPreferences `doctor_language`, `languageExplicitPrefKey` | `setLanguage` |
| `homeDashboardDateRangeProvider` | `StateNotifierProvider` | start/end days | toolbar |
| `homeDashboardExportServiceProvider` | `Provider` | CSV exporter | — |
| `appLockServiceProvider`, `appLockStateProvider` (`lib/core/services/app_lock_provider.dart:7,18`) | `Provider` / `StateNotifierProvider<…, bool>` | secure-storage backed | other analyst |
| Services & pricing, locations, professions | none — local `setState` + static service classes | | |

---

## 10. Endpoints table

| Method & path | Query / body | Response parsed | Errors handled | Source |
|---|---|---|---|---|
| `GET /api/doctors/me` | — | `{profile{…}, contact{phone,email}, billing{billingName,billingEmail,iban,taxId,stripeConnectAccountId,clickMerchantId,paymeMerchantId}, settings{country,language,twoFA,encryptedDocs,defaultStartTab}, subscription{tier,features[]}?}` | non-200 → `Exception('Profile load failed')` | `profile_providers.dart:21-32` |
| `GET /api/me/profile` | — | `{id, role, firstName, lastName, email?, phone?, photoUrl?, timeZone?}` | non-200 → Exception | `profile_providers.dart:42-47` |
| `PATCH /api/doctors/me/profile` | partial JSON: any of `profession, timeZone, biography, telegram, instagram, services (JSON string), certificates (JSON string), latitude, longitude, locationCountry, locationRegion, locationDistrict, locationCity, locationPostalCode, locationStreetAddress` (dead code also used `firstName,lastName,dob,address`) | ignored | `!= 200` → `Save failed: <body>` | `profile_actions.dart:9-15` |
| `PATCH /api/doctors/me/contact` | `{phone, email|null}` | ignored | `!= 200` → body `message` | `profile_actions.dart:17-25` |
| `PATCH /api/doctors/me/billing` | `{billingName, billingEmail, iban, taxId, stripeConnectAccountId, clickMerchantId, paymeMerchantId}` (nullables) | ignored | `!= 200` → `Save failed` | `profile_actions.dart:36-41` |
| `PATCH /api/doctors/me/settings` | `{country, language, twoFA, encryptedDocs, defaultStartTab}` (always full) | ignored | `!= 200` → `Save failed` | `profile_actions.dart:67-92` |
| `POST /api/doctors/me/photo` (multipart, part `file`) | — | `{photoUrl}` | 401 special-cased; other → `Upload failed (<code>)` | `profile_actions.dart:97-129` |
| `POST /api/doctors/me/certificate` (multipart, part `file`) | — | `{photoUrl}` (sic) | same | `profile_actions.dart:153-180` |
| `POST /api/auth/reset-password` | `{currentPassword, newPassword}` | ignored | `!= 200` → `message ?? error` | `profile_actions.dart:132-150` |
| `POST /api/doctor/payments/stripe/connect/onboard` | `{}` | `{onboardingUrl}` | `!= 200` → body `message` | `profile_actions.dart:43-50` |
| `POST /api/doctor/subscription/checkout` | `{planCode: BASIC|PRO, gateway: 'STRIPE'}` | `{checkoutUrl}` | `!= 200` → body `message` | `profile_actions.dart:52-65` |
| `GET /api/doctors/me/services` | — | `[{id,title,description,isActive,isFreeConsultation,groupId,groupName,clinicManaged,prices[{amountMinor,currency,locationId?}]}]` | non-200 silently ignored | `services_pricing_screen.dart:83-87` |
| `POST /api/doctors/me/services` | `{title, description, isActive, isFreeConsultation, groupId?, prices[]}` | ignored | **none** | `:415` |
| `PATCH /api/doctors/me/services/{id}` | same | ignored | **none** | `:417` |
| `DELETE /api/doctors/me/services/{id}` | — | — | **none** | `:433` |
| `GET /api/doctors/me/service-groups` | — | `[{id,name,sortOrder}]` | non-200 → `[]` | `:75-81`, `:567-574` |
| `POST /api/doctors/me/service-groups` | `{name, sortOrder}` | ignored | none | `:726-732` |
| `PATCH /api/doctors/me/service-groups/{id}` | `{name, sortOrder}` | ignored | none | `:653-659` |
| `DELETE /api/doctors/me/service-groups/{id}` | — | — | none | `:666` |
| `GET /api/doctors/me/locations` | — | `[DoctorLocationDto]` | non-200 → message/Exception | `doctor_location_actions.dart:10-20` |
| `POST /api/doctors/me/locations` | `DoctorLocationDto.toJson()` | `DoctorLocationDto` | 200/201 else Exception | `:23-36` |
| `PATCH /api/doctors/me/locations/{id}` | same | `DoctorLocationDto` | 200 else Exception | `:39-53` |
| `DELETE /api/doctors/me/locations/{id}` | — | — | 200/204 else Exception | `:56-63` |
| `GET /api/public/professions` (raw http, no auth) | `lang`, `search` | `[{english, uzbek}]` | timeout 5 s / non-200 → null → local fallback | `profession_service.dart:30-76` |
| `GET /api/public/clinics` (raw http) | `search` | `[{id, name}]` | → `[]` | `clinic_service.dart:23-72` |
| `GET /api/public/config` (raw http) | — | `{googleMapsApiKey}` | swallowed | `google_geocoding_service.dart:36-49` |
| `GET https://maps.googleapis.com/maps/api/geocode/json` | `latlng`, `key`, `language=en` (or `address`) | `results[0].address_components`, `formatted_address`, `geometry.location` | status ≠ OK → null | `:74-146` |
| `GET /api/doctor/analytics/overview` / `appointments-trend?days=` / `consultation-types` / `engagement` / `sms-usage?from=&to=` | see §7 | see §7 | non-200 → Exception | `doctor_analytics_service.dart` |
| `POST /api/admin/doctors/{id}/early-partner-contract/issue` (admin scope) | `{}` | §8.2 fields + `newAllocation` | non-200 → Exception | `admin_actions.dart:624-633` |

---

## 11. Models

| Model | Fields (type, nullability) | Notes |
|---|---|---|
| `ProfileAll` (`profile_models.dart:2`) | `profile, contact, billing, settings: Map<String,dynamic>` (required); `subscription: Map` (default `{}`) | untyped; KMP should introduce `DoctorProfile`, `DoctorContact`, `DoctorBilling`, `DoctorSettings`, `DoctorSubscription` data classes |
| Implied `profile` map | `firstName: String?`, `lastName: String?`, `dob: String? (yyyy-MM-dd)`, `address: String?`, `photoUrl: String?`, `profession: String? (English catalog label)`, `timeZone: String? (IANA)`, `latitude/longitude: num?`, `locationCountry/Region/District/City/PostalCode/StreetAddress: String?`, `biography: String?`, `services: String? (JSON array)`, `certificates: String? (JSON array of URLs)`, `telegram: String?`, `instagram: String?`, `subscriptionTier: String? (legacy)` | from reads in `P` |
| Implied `contact` | `phone: String?`, `email: String?` | |
| Implied `billing` | `billingName, billingEmail, iban, taxId, stripeConnectAccountId, clickMerchantId, paymeMerchantId: String?` | |
| Implied `settings` | `country: String? (Germany|Uzbekistan|USA|Other)`, `language: String? (en|uz|uz-Cyrl|ru)`, `twoFA: bool?`, `encryptedDocs: bool?`, `defaultStartTab: String? (DoctorStartTab keys)` | |
| Implied `subscription` | `tier: String (BASIC|PRO|PREMIUM)`, `features: List<String>` (unused) | |
| `MeProfile` (`profile_models.dart:20`) | `id:int, role:String (default 'DOCTOR'), firstName, lastName: String, email, phone, photoUrl, timeZone: String?`; `isClinicStaff` getter | |
| `ProfileState` (`profile_controller.dart:4`) | 13 fake fields | dead |
| `DoctorTier` enum, `DoctorFeature` enum | §5 | |
| `DoctorLocationDto` (`doctor_location_models.dart:4`) | `id:int?, label:String, clinic, address: String?, latitude, longitude: double?, locationCountry/Region/District/City/PostalCode/StreetAddress: String?, isPrimary: bool` + `fromJson/toJson/copyWith` | |
| `LocationData` (`location_picker_widget.dart:13`) | 8 required fields | **declared, never instantiated** (dead) |
| `ProfessionModel` (`profession_model.dart:7`) | `english, uzbek: String` | |
| `ClinicOption` (`clinic_option_model.dart:1`) | `id:int, name:String` | |
| `DashboardDateRange` | `start, end: DateTime` (+`dayCount`) | |
| Analytics models | §7 | |
| `EarlyPartnerContractPdfData` (`early_partner_contract_pdf_data.dart:4`) | `contractNumber: String, effectiveDate: DateTime, termMonths: int, partnerFullName/FirstName/LastName/Clinic/Phone/Email: String?, roleDoctor/rolePatient/roleBoth: bool, supplierLegalName, supplierAddress, shifaSignatoryName, shifaSignatoryTitle: String, shifaSignedDate, partnerSignedDate: DateTime?, autoSignPartner: bool` | |
| Service / ServiceGroup / ServicePrice | untyped maps — see §2 for implied fields; `amountMinor: int`, `currency: String`, `locationId: int?` | |
| `_PriceRow` (`services_pricing_screen.dart:34`) | editor-only | |

---

## 12. Platform / third-party → KMP equivalents

| Flutter | Used for | KMP / CMP recommendation |
|---|---|---|
| `flutter_map ^7` + `latlong2` + OSM raster tiles | pin picker | Prefer **Google Maps Compose** (Android) + **MapKit via Swift/`expect`/`actual`** (iOS) for a tap-to-pin map, or ship a cheaper v1: **address search + static map preview** (Google Static Maps or OSM static image via Ktor) and only a lat/lng confirm. Given the picker only needs one pin and a "my location" button, start with the simple route and add native maps when asked. If OSM tiles are kept, honour the OSM tile usage policy (proper UA, caching). |
| `geolocator ^12` | one-shot current position + permission flow | Android `FusedLocationProviderClient`, iOS `CLLocationManager` behind an `expect class LocationProvider { suspend fun current(): LatLng? }`; or `moko-geo` / `compass`. Keep the three error states (service off / denied / denied forever). |
| `GoogleGeocodingService` (raw http) | reverse geocode | Ktor `HttpClient` call in `commonMain`; **move the key server-side** (proxy `GET /api/geocode/reverse?lat&lng`) instead of shipping `/api/public/config`'s key. Pass `language=<app locale>`. |
| `geocoding` package | — | unused, drop |
| `image_picker` | gallery photo + certificate | CMP: `peekaboo` or `FileKit` / `mohamedrejeb/calf` file picker; compress with platform `Bitmap`/`UIImage` before upload (the Flutter code only downsizes the photo, not certificates). |
| `url_launcher` | Stripe onboarding / checkout | `UriHandler` from `LocalUriHandler` in CMP; on Android prefer Custom Tabs, iOS `SFSafariViewController` so the user returns to the app; add `shifa://stripe/return` deep link + refetch profile. |
| `pdf` + `TextPainter` signature PNG | admin contract | Doctor app does not need it. If needed: generate on the backend (recommended) or iText/PdfBox on Android + PDFKit on iOS. |
| `share_plus` + web anchor download | CSV export | Android `ShareCompat`/`FileProvider`, iOS `UIActivityViewController` via `expect fun shareFile`. |
| `shared_preferences` (language) | locale | `multiplatform-settings` / DataStore. |
| `flutter_secure_storage` + `local_auth` (app lock) | | other analyst (Keystore/Keychain + BiometricPrompt/LocalAuthentication). |
| `timezone` package `commonIanaTimeZones` (47 ids incl. `Asia/Tashkent`, `UTC`) | dropdown | `kotlinx-datetime` `TimeZone.availableZoneIds` filtered to the same curated list, or the full list with search. |
| Manual l10n JSON + embedded Dart map + Uzbek Cyrillic transliteration | strings | `moko-resources` / Compose resources with `uz`, `uz-Cyrl`, `ru`, `en`; port `transliterateUzbekLatinToCyrillicUi` only if the catalog keeps Latin-only Uzbek. |

---

## 13. Quirks, bugs, dead code, tech debt — do not copy

1. **Name and birth date are not saved.** `onChanged` no-op and commented `patchProfile` (`P:1438-1441`, `P:1470`). The Save button shows "saved" anyway (`P:928-932`). Backend `PATCH /api/doctors/me/profile` accepted `firstName/lastName/dob/address` in the dead copy (`P:381`, `P:410`, `P:421`).
2. **Picked map pin is lost when geocoding fails or the key is missing** — `onLocationSelected` only fires inside the success branch (`location_picker_widget.dart:155-178`); `reverseGeocode` returns `null` on missing key (`google_geocoding_service.dart:69-72`).
3. **Street-address keystrokes → one `PATCH` each** in the profile panel (`location_picker_widget.dart:319-333` + `P:1517-1520`), each followed by a full `GET /api/doctors/me`.
4. **Shared debounce timer** across biography/telegram/instagram (`P:880`, `P:2027-2030`, `P:2168-2171`, `P:2184-2187`): typing in a second field within 500 ms cancels the first field's pending save.
5. Controllers created inside `build` for profile/contact/payment panels (`P:1399-1410`, `P:1528-1533`, `P:1567-1578`) — never disposed, text resets on any rebuild (e.g. after `patchProfile` refreshes the provider, the typed name is wiped).
6. **No response-status checks** on all services/groups CRUD (`services_pricing_screen.dart:415-417, 433, 653-659, 666, 726-732`) — failures look like success.
7. Services & certificates stored as **JSON strings inside string fields** (`P:1959-1960`) — model this as arrays in KMP and ask the backend to return arrays.
8. Certificate upload response reuses the key `photoUrl` (`profile_actions.dart:170`), picks only images (no PDF), no compression.
9. Google Maps key is fetched unauthenticated from `/api/public/config` and **logged with the full request URL** (`google_geocoding_service.dart:80`). Geocoding is forced to `language=en` (`:75`).
10. `parseDoctorTier` and `doctorTierProvider` **fail open to PREMIUM** (`doctor_subscription.dart:73`, `doctor_subscription_provider.dart:24-26`) — fine for a UI hint, but a KMP port must not treat it as entitlement.
11. Subscription card: `PREMIUM` not selectable, current tier never shown, no post-checkout refresh or deep link; both payment buttons share one loading flag (`P:878`).
12. Hard-coded English strings: timezone labels (`P:1496-1497`), whole Stripe/subscription cards (`P:1636-1771`), `'Minimum 6 characters'` (`P:2250`, `P:2447`), duplicate-price snackbars (`services_pricing_screen.dart:375, 393`), `'No timezone found'` (`searchable_timezone_dropdown.dart:160`). Violates the project's l10n rule.
13. **Missing l10n keys for app lock** (`appLock, appLockSubtitle, biometricUnlock, setPin, pin, confirmPin, incorrectPin`) in all JSON files and the embedded map; `translate()` returns the key, so `?? 'fallback'` never applies (`app_localizations.dart:6278-6300`). UNVERIFIED visually.
14. Clinic staff email tile uses the `enterEmail` label key (`P:2396`) and renders raw exceptions (`P:2340`).
15. Dead code: the 820-line commented block (`P:1-820`), commented `_pickAndUploadPhoto` duplicate (`P:999-1037`), `ProfileController/profileProvider` with fake PII-looking data (`profile_controller.dart`), `LocationData` class, `GoogleGeocodingService.geocode`, `buildPartnerSignatureText`, `doctorTierLabel`, `geocoding` dependency, duplicated `TimeoutException` classes in `profession_service.dart:106` and `clinic_service.dart:75`.
16. `ProfessionService`/`ClinicService` bypass `ApiClient` with raw `http` and a `localhost:8080` fallback (`profession_service.dart:25`, `clinic_service.dart:20`); backend search in the profession dialog is unreachable (§4.2).
17. Language dropdown in Settings and `LanguageSettingsScreen` diverge: only the latter refreshes the push-notification localization cache (`language_settings_screen.dart:64`); neither PATCHes `settings.language` directly.
18. Settings PATCH always sends the full object built from possibly-stale `settings` map captured at build time (`P:1836-1846`), so two quick toggles can overwrite each other.
19. Reports CSV labels all metrics with the selected range while overview/engagement/types endpoints ignore the range (`home_dashboard_export_service.dart:27-37`).
20. `_MapPickerScreen` disposes `MapController` manually and uses `Future.delayed(50 ms)` to "wait for state" (`location_picker_widget.dart:219`).
21. Contract PDF: term text hard-codes "(olti)" (`early_partner_contract_pdf_service.dart:181, 360`); signed dates are client `DateTime.now()` (`early_partner_contract_from_issue.dart:33-34`); supplier legal identity and default contract number live in client code.
22. `Implantologist` is in the dental catalog group but not detected by `isDentalDocumentationProfession` (`dental_documentation_professions.dart:25-59`).

---

## 14. Proposed tickets

Sizes: S ≈ ½–1 day, M ≈ 2–3 days, L ≈ 4–6 days. Ordered so each is buildable after its dependencies. Routes are proposals for the KMP navigation graph.

### PROF-01 · Typed profile models + repository [M]
Route · — · Spec refs §1.1, §9, §10, §11 · API: `GET /api/doctors/me`, `GET /api/me/profile`
Build: `DoctorProfile`, `DoctorContact`, `DoctorBilling`, `DoctorSettings`, `DoctorSubscription`, `MeProfile` data classes (kotlinx-serialization, all fields nullable where the map reads are nullable); `ProfileRepository` with a cached `StateFlow<Result<ProfileAll>>`, `refresh()`, and `patchProfile/patchContact/patchBilling/patchSettings` (§10 bodies). `services`/`certificates` decoded from JSON strings into `List<String>` at the boundary.
Done when: unit tests round-trip the sample `/api/doctors/me` payload; every other feature reads name/timezone/profession from this repository.
Depends: core API client (other analyst's ticket).
Fix: do not keep `Map<String,dynamic>`; do not fail open on parse errors silently — log and surface.

### PROF-02 · Profile screen shell: section list + adaptive two-pane [M]
Route `profile` (shell tab) · §1.2, §1.3, §1.4 · API: PROF-01
Build: `LazyColumn` of 8 section cards with preview lines; `WindowSizeClass` compact → push detail; medium/expanded → list/detail row (3:2). Loading/error states. Clinic-staff variant (§1.12) as a separate composable reading `MeProfile`.
Done when: navigation between sections works on phone and tablet; clinic staff never hits `/api/doctors/me`.
Depends: PROF-01.
Fix: one Save button only where there is something to save (drop the snackbar-only Save).

### PROF-03 · Personal information panel incl. name/DOB persistence [M]
Route `profile/personal` · §1.5 · API: `PATCH /api/doctors/me/profile`, `POST /api/doctors/me/photo`
Build: avatar + photo picker (downscale ≤1600 px, JPEG q85) + multipart upload + cache-bust; first/last name (two fields, required, trimmed), DOB picker (1900..today, ISO `yyyy-MM-dd`), profession picker (PROF-05), timezone picker (PROF-06). Explicit Save that PATCHes only dirty fields; immediate patch for profession/timezone is acceptable but go through the same debounced "dirty → save" path.
Done when: name and DOB actually persist (confirm with backend that `firstName/lastName/dob` are accepted — UNVERIFIED); photo upload shows the new image without restart.
Depends: PROF-02.
Fix: bug #1, #5.

### PROF-04 · Contact, Payment & invoicing, Password panels [M]
Routes `profile/contact`, `profile/billing`, `profile/password` · §1.6, §1.7 (fields only), §1.11 · API: `PATCH …/contact`, `PATCH …/billing`, `POST /api/auth/reset-password`
Build: phone (E.164 helper from core), email format validation, IBAN basic format check, tax id free text; billing PATCH must round-trip `stripeConnectAccountId`, `clickMerchantId`, `paymeMerchantId` unchanged. Password form: current required, new ≥ 6 (localized), confirm match.
Done when: validation errors are localized; server `message` is shown; fields keep their text across refreshes.
Depends: PROF-02.

### PROF-05 · Profession catalog + searchable picker [S]
Route dialog/sheet · §4 · API: `GET /api/public/professions?lang&search`
Build: embed the 138-entry `(english, uzbek)` catalog as a Kotlin list; display rule en/ru→English, uz→Uzbek, uz-Cyrl→transliterated; search over both; backend fetch through the shared API client with local fallback; `isDentalDocumentationProfession` port (+ unit tests from `profession_catalog_test.dart`, `doctor_display_name_test.dart`, add `Implantologist`).
Done when: stored value is the English label; multi-select variant available for onboarding.
Depends: PROF-01.
Fix: #16 (use the API client; remove dead backend-search path).

### PROF-06 · Timezone picker [S]
Route dialog/sheet · §1.5, §12 · API: `PATCH /api/doctors/me/profile {timeZone}`
Build: curated 47-id list (keep `Asia/Tashkent` prominent) with search; ensure current unknown value is listed; localized labels.
Done when: selection patches and `TimezoneService` consumers re-read it.
Depends: PROF-01.

### PROF-07 · Location picker (address + pin) [L]
Route `profile/location` and reused by schedule locations · §3, §12 · API: `PATCH /api/doctors/me/profile` (8 keys), geocoding proxy
Build: shared `LocationPickerSection` composable: "Select on map" → full-screen native map (Google Maps Compose / MapKit via expect-actual) with tap-to-pin, my-location (permission flow with the 3 error states), confirm; reverse geocode via a **backend proxy** (coordinate with backend; interim: Ktor call with key from `/api/public/config`) with locale-aware `language`; read-only structured fields + editable street address; debounced (≥600 ms) save; always persist lat/lng even when geocoding fails.
Done when: picking a pin without network for geocoding still saves coordinates; no per-keystroke PATCH; address shows in the app language.
Depends: PROF-03.
Fix: #2, #3, #9.

### PROF-08 · Extended profile panel [M]
Route `profile/extended` · §1.10 · API: `PATCH /api/doctors/me/profile`, `POST /api/doctors/me/certificate`
Build: biography (debounced per-field), service tags add/remove, certificates upload (image or PDF via file picker, compressed images) + list with filename + delete, telegram/instagram; one debouncer per field.
Done when: typing in two fields back-to-back saves both; certificate list refreshes after upload.
Depends: PROF-01, PROF-02.
Fix: #4, #7, #8.

### SVC-01 · Services & pricing list + CRUD [M]
Route `profile/services` · §2 · API: `GET/POST/PATCH/DELETE /api/doctors/me/services[/{id}]`, `GET /api/doctors/me/locations`
Build: typed `DoctorService`, `ServicePrice`; list with group/price summary; editor sheet with title (required), description, group, free-consultation switch, N price rows (amount decimal → minor units, currency `UZS/USD/EUR/RUB` + unknown passthrough, scope = all/location), duplicate `(currency, location)` check; clinic-managed rows read-only; delete confirmation.
Done when: every write checks status and shows server `message`; localized validation.
Depends: PROF-01 (locations fetch can reuse schedule's locations repo).
Fix: #6.

### SVC-02 · Service groups editor [S]
Route `profile/services/groups` · §2 · API: `GET/POST/PATCH/DELETE /api/doctors/me/service-groups[/{id}]`
Build: list sorted by `sortOrder`, add/edit (name required, sortOrder int), delete with confirm; refresh services list after changes.
Done when: ordering is deterministic client-side (sort by `sortOrder`, then name).
Depends: SVC-01.

### SUB-01 · Tier/feature gating [S]
Route — · §5.1, §5.2 · API: `subscription.tier` from PROF-01
Build: `DoctorTier`, `DoctorFeature`, `minTier` map, `FeatureGate` flow derived from the profile repository; default while loading = PREMIUM (keep UX parity) but expose `isResolved` so callers can hide instead of flicker.
Done when: tasks tab, AI features, advanced analytics gate from this single source; unit tests for `tierAllows` and parse fallback.
Depends: PROF-01.
Fix: #10 — document that the backend is authoritative.

### SUB-02 · Billing: Stripe Connect onboarding + subscription checkout [M]
Route `profile/billing` (cards inside PROF-04) · §1.7, §5.3 · API: `POST /api/doctor/payments/stripe/connect/onboard`, `POST /api/doctor/subscription/checkout`
Build: Connect card (status from `stripeConnectAccountId`, open `onboardingUrl` in Custom Tab / SFSafariVC); subscription card showing **current tier** + plan selector BASIC/PRO (ask product about PREMIUM) → open `checkoutUrl`; app-link `shifa://billing/return` (or `ON_RESUME`) → `ProfileRepository.refresh()`. Separate loading states per button; all strings localized.
Done when: returning from Stripe refreshes connected status/tier without relaunch.
Depends: PROF-04, SUB-01.
Fix: #11, #12.

### SET-01 · Settings panel (country, language, start tab, 2FA, encrypted docs) [M]
Route `profile/settings` · §1.8 · API: `PATCH /api/doctors/me/settings`
Build: country dropdown (keep wire values `Germany|Uzbekistan|USA|Other`), language (en/uz/uz-Cyrl/ru → local locale store **and** included in the next settings PATCH; also refresh push localization), starting-screen dropdown filtered by `remoteCareTasks`, two switches; serialize writes through the repository so the full-body PATCH is built from the latest state (queue, not stale snapshot).
Done when: rapid toggles persist both values; language change updates UI immediately.
Depends: PROF-01, SUB-01.
Fix: #17, #18.

### SET-02 · Language settings screen + mini toggle [S]
Route `settings/language` · §6.1 · API: none
Build: radio list of 4 locales; pill toggle composable for auth screens; persistence via `multiplatform-settings` with the "explicit choice" flag semantics from `language_provider.dart:101-107`.
Done when: choice survives restart and regional auto-detection does not override an explicit choice.
Depends: core l10n setup.

### SET-03 · App lock settings section (UI) [S]
Route inside `profile/settings` · §6.2 · API: none
Build: card with App lock switch, PIN set/confirm dialog (4–6 digits), biometric switch when available; wire to the app-lock service from the security analyst's ticket; add the 7 missing string keys in all 4 locales.
Done when: enabling/disabling round-trips through the service; no raw keys visible.
Depends: SET-01, app-lock service ticket.
Fix: #13.

### REPORT-01 · Reports screen [M]
Route `reports` (shell tab) · §7 · API: `GET /api/doctor/analytics/{overview, appointments-trend?days, consultation-types, engagement, sms-usage?from&to}`
Build: title/subtitle, date-range toolbar (shared `DashboardDateRange` state with Home), KPI cards + SMS usage; trend chart, visit-type donut and engagement only when `advancedAnalytics`; CSV export via platform share sheet.
Done when: reuses the Home analytics composables/repos (no duplicate fetch code); CSV content matches §7 columns; range-insensitive endpoints are not labelled with the range (or backend adds params).
Depends: Home analytics tickets (other analyst), SUB-01.
Fix: #19.

### LEGAL-01 · Decide doctor-side contract visibility (spike) [S]
Route — · §8 · API: none today (admin-only `POST /api/admin/doctors/{id}/early-partner-contract/issue`)
Build: confirm with product/backend whether doctors should see/download their early-partner contract in the mobile app. If yes, request a backend endpoint (`GET /api/doctors/me/early-partner-contract.pdf`) that renders the PDF server-side using the admin's existing data, and a simple "My contract" row in Settings that opens it in the platform PDF viewer.
Done when: decision recorded; if "yes", LEGAL-02 is scheduled.
Depends: none.
Fix: #21 — do not port client-side PDF generation, signature fonts, or supplier legal identity into the mobile app.

### LEGAL-02 · "My partnership contract" viewer (conditional) [S]
Route `profile/legal/contract` · §8 · API: backend endpoint from LEGAL-01
Build: fetch PDF bytes with auth, show via platform viewer/share; display contract number and effective date.
Done when: opens on both platforms; 404 shows "no contract issued".
Depends: LEGAL-01.
