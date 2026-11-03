# Edit profile (personal information)

| | |
|---|---|
| Area | Profile, settings, reports & legal |
| Who sees it | Doctor only (clinic staff have no editable profile). No tier gate. |
| Reached from | Profile root → header card or "Personal information" row; completeness chips "Add photo"; account-creation step 2 reuses the same pickers |
| Screen type | Full screen (pushed, tab bar hidden) + photo action sheet + date picker + profession picker sheet |
| Current Flutter source | `lib/features/profile/presentation/profile_screen.dart` (panel `profile`, ~line 1397–1525), `searchable_profession_dropdown.dart`, `lib/state/profile/profile_actions.dart` |
| Spec | `docs/kmp-spec/08-profile-settings-reports-legal.md` §1.5, §4, tickets PROF-03, PROF-05 |

## 1. Purpose
Set who the doctor is: photo, first and last name, date of birth, gender, profession (specialty), short bio line. Done once at onboarding, corrected rarely. Name and profession appear everywhere (patient app, chat, PDFs), so saving must be explicit and reliable.

## 2. Entry points
- Profile root → header card / "Personal information" row → pushed on top of Profile.
- Profile root → completeness chip "Add photo" → same screen, photo sheet opens immediately.
- Registration step 2 (area 01) reuses the profession picker sheet and the date picker, not this screen.

## 3. Layout, top to bottom (phone portrait)
1. **App bar** — back arrow, title "Personal information", right text action **Save** (disabled until the form is dirty and valid; shows a small progress ring while saving).
2. **Photo block** — avatar 96 px centred, small camera badge bottom-right; caption "Change photo". Tapping either opens the photo action sheet (Take photo / Choose from gallery / Remove photo — last only when a photo exists).
3. **Section "Personal information"**
   - First name (text, required, auto-capitalise words)
   - Last name (text, required)
   - Date of birth (read-only field with calendar icon → platform date picker, range 1900-01-01 … today, initial = current value or 1990-01-01)
   - Gender (segmented: Male / Female / Prefer not to say) — **NEW**: the field exists in registration (`gender`) but not on the profile screen today; UNVERIFIED whether `PATCH /api/doctors/me/profile` accepts it.
4. **Section "Professional"**
   - Profession (picker field showing the localised label; chevron) → opens the profession picker sheet (§7).
   - Short bio / headline (optional, 120 chars, single line) — **NEW** proposal; the long biography stays in Extended profile.
5. **Bottom**: nothing sticky; Save is in the app bar. Keyboard "Next" moves through text fields; "Done" on the last closes the keyboard.

Everything scrolls under the pinned app bar. Fields are grouped in cards with 16 px gutters.

## 4. Data and formats
| Field | Source → saved as | Format rule |
|---|---|---|
| Photo | profile.photoUrl ← `POST /api/doctors/me/photo` (multipart `file`) | client downsizes to ≤ 1600 px longest side, JPEG quality 85, converts HEIC/PNG to JPEG; square crop in the picker; cache-busted after upload |
| First / last name | profile.firstName / lastName ← PATCH | trimmed; internal double spaces collapsed; 1–60 chars each; letters, spaces, hyphen, apostrophe (straight and ʼ), diacritics allowed |
| Date of birth | profile.dob ← PATCH `yyyy-MM-dd` | shown `dd.MM.yyyy`; never in the future; age ≥ 18 is a soft warning, not a block |
| Gender | profile.gender (UNVERIFIED key) | MALE / FEMALE / UNSPECIFIED |
| Profession | profile.profession ← PATCH `{"profession": "<English label>"}` | wire value is the English catalogue label (e.g. "Otolaryngologist (ENT)"); displayed per locale (en/ru English, uz Uzbek Latin, uz-Cyrl transliterated) |
| Headline | profile.headline (UNVERIFIED) | 120 chars, one line |

Catalogue: 138 professions in 11 groups (General & primary care 8, Medical non-surgical 34, Mental health 3, Diagnostic & lab 7, Surgical 22, Women's & reproductive 8, ENT & senses 5, Dental 8, Children & development 19, Emergency & ICU 8, Other 18). Russian labels do not exist — Russian UI shows English.

## 5. Primary flow (happy path)
1. Screen opens with current values (skeleton for the photo and six fields only if the profile is not yet in memory; normally instant because Profile root already loaded it).
2. Doctor edits first name → Save becomes enabled.
3. Doctor taps Date of birth → platform date picker → picks → field shows `dd.MM.yyyy`.
4. Doctor taps Profession → picker sheet → searches "orto" → taps "Orthodontist" → sheet closes, field updates.
5. Doctor taps Save → app bar action shows a ring, fields stay editable-looking but are disabled → one `PATCH /api/doctors/me/profile` with only the changed keys → 200 → top banner "Profile information saved" → screen pops to Profile root, which shows the new name.

```mermaid
flowchart TD
  A[Open] --> B[Edit fields]
  B --> C{Dirty and valid?}
  C -- no --> B
  C -- yes --> D[Tap Save]
  D --> E[PATCH changed keys]
  E -- 200 --> F[Banner "Profile information saved" → pop]
  E -- 4xx with message --> G[Banner with server message, stay]
  E -- network --> H[Banner "Network error…", stay, Save re-enabled]
  E -- 401 --> I[Global logout]
```

Photo flow is independent of Save: picking a photo uploads immediately (ring on the avatar), then the avatar swaps and a banner "Photo updated" appears; a failed upload leaves the old photo.

## 6. Screen states
- **First load:** values pre-filled from the cached profile; if absent, skeleton rows for avatar + 6 fields (no spinner).
- **Empty:** new doctor with no values: fields empty with placeholders; profession field says "Select profession"; avatar shows initials or a person glyph.
- **Error on load:** only if the profile could not load at all — inline error with Retry (the same component as Profile root).
- **Offline:** fields editable; Save disabled with helper text under the app bar "You're offline — changes can't be saved yet"; photo sheet disabled (greyed with same reason). Draft is kept in memory while the screen is open; not persisted across app restarts.
- **Refreshing:** none on this screen; the repository refresh after Save happens on pop.
- **Partial data:** profile block present but e.g. `dob` malformed → field shows empty and is treated as "not set".
- **Permission needed:** camera (Take photo) and photo library (Choose from gallery) are requested on first use via the OS prompt. Denied once → banner "Camera access is needed to take a photo" with a "Settings" action; denied permanently → the same banner, action opens the OS app settings. Gallery pick on Android 13+ uses the photo picker (no permission).
- **Locked by tier:** n/a.
- **Role difference:** clinic staff never reach this screen (their name/photo are managed by the clinic admin; UNVERIFIED whether staff can change their own photo — today they cannot).

## 7. Interactions and secondary flows
- **Tap avatar / Change photo** → action sheet → Take photo (camera) or Choose from gallery → square crop UI (platform cropper) → upload with progress ring → success banner. Remove photo → confirm inline in the sheet ("Remove photo?" destructive) → `PATCH {"photoUrl": null}` (UNVERIFIED support) → initials avatar.
- **Tap Date of birth** → platform date picker (wheel on iOS, calendar on Android), min 1900, max today, locale of the app. Cancel keeps the previous value.
- **Tap Profession** → bottom sheet, 90 % height: search field (auto-focus, placeholder "Search profession..."), list grouped by the 11 catalogue groups with sticky group headers, current value ticked, matching on both English and Uzbek names (substring, case-insensitive). Tap row → select, sheet closes. Empty result: "No professions found". While the catalogue downloads (backend `GET /api/public/professions`, 5 s timeout) the local list is shown immediately — no loading state visible.
- **Type in name fields** → Save enables on first change; validation runs on blur and on Save.
- **Tap Save** → see §5; double tap is ignored while the ring shows.
- **Back arrow / back gesture / swipe-back** with dirty form → dialog "You have unsaved changes. Discard them?" with Keep editing (default) / Discard (destructive). Clean form → pops immediately.
- **Keyboard "Done"** on the last field → closes keyboard; does not submit.
- **Long-press on avatar** → n/a.

## 8. Edge cases
- Avatar 20 MB / 48 MP photo → downsized to 1600 px before upload (~300 KB); the ring shows during resize too. If the result is still > 5 MB (UNVERIFIED server limit) → banner "Photo is too large" **NEW**.
- HEIC from iPhone → converted to JPEG client-side; Live Photo → still frame only.
- Portrait with EXIF rotation → orientation applied before upload (the server does not rotate).
- Photo upload succeeds but the profile refetch fails → avatar still swaps from the local file; next load shows the server copy.
- Upload returns 401 → global logout (do not show the raw "Unauthorized (401): please ensure JWT…" text that exists today).
- Name with apostrophe ("O'tkir"), typographic apostrophe ("Oʻtkir"), hyphen ("Anna-Maria"), diacritics ("Çelik"), Cyrillic ("Ғафуров") → all accepted; only digits and symbols like `@#$` are rejected.
- Name of one character ("Li") → accepted (min 1).
- Name > 60 characters → blocked at input with a counter appearing at 50.
- Leading/trailing whitespace → trimmed silently on save; the field does not show the trimmed value until pop.
- DOB in the future → impossible via the picker; if the server returns one, show it and flag "Check date of birth" under the field.
- DOB today's date (age 0) → allowed by the picker, soft warning "Please check the date of birth" **NEW**, Save still enabled.
- DOB 29 February → stored as entered; display never shifts to 1 March.
- Profession not in the catalogue (legacy free text from an old account) → picker shows it as the ticked first row "Current: <value>" so the field never looks empty.
- Profession search with Cyrillic query in uz-Cyrl UI → match against transliterated Uzbek label as well.
- Slow network on Save (> 3 s) → ring continues; after 15 s banner "Still saving…"; after the 30 s client timeout → "Network error…" and Save re-enabled, values kept.
- Double tap Save → second tap ignored.
- Saving while offline → Save is disabled, so impossible; if the connection drops mid-request → treat as network error, keep values.
- Server 409/422 with a `message` → show that message in the banner verbatim (localised by the backend, UNVERIFIED).
- Profile changed elsewhere (admin edited the name) → on pop the root shows server values; while this screen is open no live merge.
- Deep link arrives while the discard dialog is open → dismiss dialog as "Keep editing"? No: discard dialog stays; the deep link is queued until the screen is left (area 01 rule).
- Rotation mid-edit → values and dirty state preserved; date picker/sheet survive rotation.
- Language switch cannot happen while here (no language control on this screen); on return from Settings the profession label re-renders.
- Font scaling 200 % → labels above fields (never floating inside), sheets become scrollable, segmented gender control wraps to a vertical radio list.
- Screen reader: avatar announced as "Profile photo, button, double tap to change"; date field announced with the formatted date and "button"; profession field with the current label.

## 9. Validation and messages
| Field/action | Rule | When | Message (EN) | Where |
|---|---|---|---|---|
| First name | required, 1–60 chars, letters/space/hyphen/apostrophe/diacritics | blur + submit | "This field is required" (`fieldRequired`) / "Use letters only" **NEW** | inline under field |
| Last name | same as first name | blur + submit | same | inline |
| Date of birth | ≤ today; soft warn if age < 18 | on pick | "Please check the date of birth" **NEW** (warning style) | inline |
| Profession | required for a complete profile, not blocking Save | submit | banner chip on Profile root only | — |
| Headline | ≤ 120 chars | typing (counter) | counter "118/120" | inline |
| Photo | JPEG/PNG/HEIC; after resize ≤ 5 MB (UNVERIFIED) | after pick | "Photo is too large" **NEW** / "Upload failed" (`uploadFailed`) | top banner |
| Save | network / server error | after request | server `message` or "Network error. Please check your connection." (`networkError`) | top banner |
| Save | success | after 200 | "Profile information saved" (`profileInformationSaved`) | top banner on the root |
| Leave dirty | — | back | "You have unsaved changes. Discard them?" (`unsavedChangesMessage`), buttons "Discard" (`discard`) / "Keep editing" **NEW** | dialog |

## 10. Navigation out
- Save success → pop to Profile root; root previews refresh (name, DOB, profession, photo).
- Back with clean form → pop, nothing to refresh.
- Back with dirty form → dialog; Discard → pop and drop input; Keep editing → stay.
- 401 → logout, stack replaced by Login; draft lost.

## 11. Copy
- Title: `personalInformation` ("Personal Information").
- Labels: "First name" **NEW**, "Last name" **NEW** (today one `fullName` field), `birthDate` ("Birth Date") → prefer `dateOfBirth` ("Date of Birth"), `gender` / `male` / `female`, "Prefer not to say" **NEW**, `profession` ("Profession"), `selectProfession` ("Select Profession"), `searchProfession` ("Search profession..."), `noProfessionsFound` ("No professions found"), "Headline" **NEW**.
- Photo: "Change photo" **NEW**, `takePhoto` ("Take Photo"), `chooseFromGallery` ("Choose from Gallery"), "Remove photo" **NEW**, `uploading` ("Uploading..."), `photoUpdated` ("Photo updated"), `uploadFailed` ("Upload failed").
- Date picker help: `selectDateHint` ("Select Date of Birth").
- Save: `save` ("Save"); success `profileInformationSaved`.
- Offline helper: "You're offline — changes can't be saved yet" **NEW**.
- Permission: "Camera access is needed to take a photo" **NEW**, "Open Settings" **NEW**.

## 12. Known problems in the current app (do not carry over)
- **Name and date of birth are never saved** — the text field's change handler is empty and the date patch is commented out; Save still says "Profile information saved" (verified bug #2 in the spec README).
- One "Full name" field; the backend stores first and last name separately, so the split must be explicit.
- Controllers are rebuilt on every rebuild, so after the profession or timezone saves (which trigger a profile refetch) the typed-but-unsaved name is wiped.
- Profession and timezone save immediately with no feedback except a silent refetch; all other fields need Save — mixed model. Target: everything on this screen goes through Save; the photo is the only immediate action.
- Upload button is a primary "Upload Photo" button in the header; no camera option, no crop, no remove.
- Practice timezone and the whole location card live on this panel under "Workplace information"; they move to their own screens (`03`, `05`).
- Raw error strings leak ("Unauthorized (401): please ensure JWT is set on ApiClient.setToken(...)").
- No validation of any kind on names or DOB.

## 13. Open questions for design
- Gender: show at all on the doctor profile (patients see it in the patient app, UNVERIFIED)? If yes, segmented control vs. picker.
- Should Save be an app-bar text action (proposed) or a sticky bottom button like the rest of the app's forms?
- Should the profession picker be grouped with sticky headers or a flat alphabetical list with a group tag per row?
- Do we allow multiple professions on the profile (registration allows multi-select, the profile stores one)?
