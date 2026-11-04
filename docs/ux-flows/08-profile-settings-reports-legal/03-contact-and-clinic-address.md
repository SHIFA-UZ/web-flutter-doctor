# Contact & clinic address

| | |
|---|---|
| Area | Profile, settings, reports & legal |
| Who sees it | Doctor only. No tier gate. Clinic staff see phone/email read-only on the Profile root. |
| Reached from | Profile root → "Contact & clinic address" row; completeness chip "Add clinic address"; Calendar / Schedule "Add location" shortcuts open the same address component inside area 04's location editor |
| Screen type | Full screen (pushed) + address search sheet + map pin picker (`04-map-pin-picker.md`) |
| Current Flutter source | `profile_screen.dart` (panel `contact`, ~1526–1564; location card inside panel `profile` ~1508–1520), `lib/features/profile/presentation/location_picker_widget.dart` (`LocationPickerSection`), `lib/core/services/google_geocoding_service.dart` |
| Spec | `docs/kmp-spec/08-profile-settings-reports-legal.md` §1.6, §3, tickets PROF-04 (contact), PROF-07 (location) |

## 1. Purpose
How patients and the clinic reach the doctor (phone, email) and where the practice physically is (structured address + coordinates). Set once; the address matters for patient directions, the patient app map and the "device timezone differs" logic. First release is **address-first**: the doctor types or searches an address and optionally fine-tunes a pin; a native map is added later.

## 2. Entry points
- Profile root → row → pushed.
- Completeness chip "Add clinic address" → pushed and scrolled to the address section.
- Area 04 location editor (multiple practice locations) embeds the same address component; this file describes the component once.

## 3. Layout, top to bottom (phone portrait)
1. **App bar** — back, title "Contact & clinic address", right action **Save** (enabled when dirty and valid).
2. **Section "Contact"**
   - Phone (tel keyboard, country prefix chip defaulting to +998, formatted as typed)
   - Email (email keyboard, optional)
   - Helper under the section: "Patients see your phone number on confirmed appointments" **NEW** (UNVERIFIED product rule — confirm what patients actually see).
3. **Section "Clinic address"**
   - **Address search field** (NEW primary entry): placeholder "Search address or place", magnifier icon. Typing ≥ 3 characters queries the backend geocoding proxy (debounced 400 ms) and shows up to 6 suggestions in a dropdown under the field. Selecting a suggestion fills the structured fields and coordinates.
   - **"Pick on map"** secondary button → `04-map-pin-picker.md` (fine-tune or choose without an address).
   - **Structured fields** (auto-filled, editable in the target design; read-only today): Country, Region (Viloyat), District (Tuman), City, Postal code.
   - **Street address** (editable, 2 lines, helper "You can edit this field to add building details, floor, room number, etc.").
   - **Coordinates line** (caption): "41.299500, 69.240100 · Set from map" or "Coordinates not set"; tap → map picker.
   - **Static map preview** (NEW, optional for v1): 16:9 image of the pin when coordinates exist (static map image via backend, no SDK).
4. **Section "Practice timezone"** — one row showing the current zone with chevron → `05-practice-timezone-picker.md`. It is here because address and timezone are set together; the row is also on the Profile root.

Scrolls under the pinned app bar; Save in the app bar.

## 4. Data and formats
| Field | Source → saved | Format rule |
|---|---|---|
| Phone | contact.phone ← `PATCH /api/doctors/me/contact {phone, email}` | normalised to E.164 before sending (`toE164Phone`: strips non-digits, keeps `+`, prepends +998 when no prefix, drops a leading 0); displayed grouped `+998 90 123 45 67` |
| Email | contact.email ← same PATCH (`null` when blank) | trimmed, lower-cased for display; RFC-light check `x@y.z` |
| Country / Region / District / City / Postal code / Street | profile.locationCountry/Region/District/City/PostalCode/StreetAddress ← `PATCH /api/doctors/me/profile` (8 keys with latitude/longitude) | from reverse geocoding (country ← `country`, region ← `administrative_area_level_1`, district ← `sublocality`, city ← `locality`, postal ← `postal_code`, street ← `street_number route`); language = app language (today forced English) |
| Latitude / longitude | profile.latitude/longitude | 6 decimals; saved **even when geocoding fails** |
| Legacy `address` | profile.address | read-only fallback preview on the root; never edited here |
| Timezone | profile.timeZone | see `05` |

The contact PATCH and the profile PATCH are two requests; Save sends only the ones that are dirty, sequentially; a failure in the second leaves the first saved and reports which part failed.

## 5. Primary flow (happy path)
1. Open → fields pre-filled; address section shows current structured address and a static preview if coordinates exist.
2. Doctor types "Amir Temur 12 Tashkent" in the search → suggestions appear after 400 ms → taps one → Country/Region/District/City/Postal/Street fill; coordinates line updates; preview image loads.
3. Doctor edits Street to add "3rd floor, room 12".
4. Doctor taps Save → ring → `PATCH /api/doctors/me/profile` (8 keys) → 200 → banner "Location saved" → pop.

```mermaid
flowchart TD
  A[Open] --> B{How to set address?}
  B -- type/search --> C[Suggestions from geocoding proxy]
  C --> D[Select → fields + coordinates filled]
  B -- Pick on map --> E[Map pin picker]
  E --> F[Confirm pin → reverse geocode]
  F -- success --> D
  F -- failed/no key --> G[Keep coordinates, fields empty, banner "Could not get address details"]
  D --> H[Edit street / phone / email]
  G --> H
  H --> I[Save → PATCH contact and/or profile]
  I -- 200 --> J[Banner, pop]
  I -- error --> K[Banner with message, stay]
```

## 6. Screen states
- **First load:** values from memory; skeleton for two contact fields + address block only on cold open.
- **Empty:** no phone (rare — phone is the login identity), no address: search field focused automatically with the helper "Search your clinic address to fill the fields below" **NEW**; structured fields hidden until something fills them (today) → target: shown empty but collapsed under a "Address details" expander.
- **Error on load:** inherits the Profile root error.
- **Offline:** contact fields editable; address search disabled with helper "Address search needs a connection"; map picker opens (tiles may not load) and still returns coordinates; Save disabled with the offline helper. 
- **Refreshing:** n/a.
- **Partial data:** coordinates present but structured fields empty (old accounts) → on open, run one reverse geocode in the background; if it succeeds, fields fill and Save becomes enabled with a hint "Address details were filled automatically — check and save" **NEW**.
- **Permission needed:** location permission only inside the map picker (`04`). Nothing here.
- **Locked by tier:** n/a.
- **Role difference:** clinic staff: not reachable (clinic address is managed in the Clinic workspace, area 09).

## 7. Interactions and secondary flows
- **Type phone** → auto-format; prefix chip tap → country list (+998 default, others allowed). Paste "998901234567" → becomes "+998 90 123 45 67".
- **Type email** → validation on blur.
- **Type in address search** → debounced suggestions; arrow/return selects first; clear (×) empties only the search box, not the saved fields.
- **Select suggestion** → fills fields, marks form dirty, scrolls so the street field is visible.
- **Pick on map** → push map picker with the current pin (or Tashkent centre 41.2995, 69.2401 when none) → returns coordinates → reverse geocode (spinner on the coordinates line, max 10 s) → fills fields; if it fails, coordinates stay and a warning banner shows.
- **Edit structured fields** → allowed (target); a small "Reset from map" link re-runs reverse geocoding.
- **Edit street** → dirty; no request until Save (today: one PATCH per keystroke).
- **Tap timezone row** → push timezone picker; returning updates the row (timezone saves independently, see `05`).
- **Save** → §5. **Back with dirty** → unsaved-changes dialog.
- **Pull-to-refresh** → n/a (form).

## 8. Edge cases
- Phone with spaces, dashes, brackets, leading 0 ("(90) 123-45-67") → normalised to +998901234567; shown grouped.
- Phone from another country ("+49 30 …") → accepted; prefix chip shows DE; grouping falls back to blocks of 3.
- Phone too short (< 9 national digits) → inline error, Save disabled.
- Email with uppercase or trailing space → trimmed and lower-cased; "name@domain" without TLD → inline error; empty → saved as null (allowed).
- Email already used by another account → server message (UNVERIFIED) shown in the banner; field keeps the value.
- Address search returns nothing ("xyzxyz") → dropdown row "Address not found — try a nearby landmark or pick on the map" (`addressNotFound` + NEW hint).
- Search in Uzbek Cyrillic or Russian → sent as typed; proxy language = app language; results may be Latin — acceptable.
- Reverse geocoding returns no district or postal code (common in Uzbekistan) → those fields stay empty, no error.
- Geocoding proxy down / key missing → coordinates saved, fields empty, banner "Could not get address details. Please try selecting a different location." (`couldNotGetAddressDetails`), Save still enabled.
- Pin placed in the sea or another country → fields fill with whatever is returned; a soft hint "Address is outside Uzbekistan" is **not** shown (doctors abroad exist).
- Very long street text (300 chars) → field grows to 4 lines then scrolls; counter at 200/250 **NEW** limit (UNVERIFIED server limit).
- Coordinates line while geocoding > 3 s → spinner + "Looking up address…"; user may still edit other fields.
- Save while geocoding is still running → Save waits for it (max 10 s) then sends; if still running, sends coordinates without new structured fields.
- Contact PATCH succeeds, profile PATCH fails → banner "Contact saved, but the address could not be saved" **NEW**; address section stays dirty.
- 401 on either PATCH → global logout.
- Double tap Save → ignored.
- Rotation on this screen → form state kept; suggestions dropdown closes.
- Language switch (via Settings in another tab is impossible while pushed) → n/a; structured labels re-render on return.
- Font scaling 200 % → suggestion rows wrap to two lines; prefix chip and phone field stack vertically.
- Screen reader: suggestion dropdown announced as a list with count; coordinates line readable; static map image has alt "Map preview of clinic location".
- Same address component inside area 04's location editor: it has its own Save; no timezone row there.

## 9. Validation and messages
| Field/action | Rule | When | Message (EN) | Where |
|---|---|---|---|---|
| Phone | required; after normalisation `+` and 9–15 digits | blur + submit | "Phone number is required" (`phoneNumberRequired`) / "Please enter a valid phone number" (`invalidPhone`) | inline |
| Email | optional; `local@domain.tld` | blur + submit | "Please enter a valid email address" (`invalidEmail`) | inline |
| Address search | ≥ 3 chars to query | typing | "Address not found" (`addressNotFound`) | dropdown row |
| Street address | ≤ 250 chars (UNVERIFIED) | typing | counter | inline |
| Reverse geocode | — | after pin / suggestion | `couldNotGetAddressDetails` | top banner (warning) |
| Save contact | 200 | after request | "Contact details saved" (`contactDetailsSaved`) / server `message` | top banner |
| Save address | 200 | after request | "Location saved" (`locationSaved`) / "Save failed" → use `somethingWentWrong` | top banner |
| Leave dirty | — | back | `unsavedChangesMessage` | dialog |

## 10. Navigation out
- Save → pop to Profile root; root refreshes contact and address previews; Calendar and patient app pick up the new address on their next load.
- Map picker (push) → returns coordinates into this form; nothing is saved until Save (except in the "coordinates-only" fallback, which is also only applied on Save).
- Timezone row (push) → timezone saves on its own screen; this form's dirty state is untouched.
- Back → dialog if dirty.

## 11. Copy
- Title: "Contact & clinic address" **NEW** (today `contactDetails` "Contact Details").
- Contact: `phone` ("Phone") / `phoneNumber` ("Phone Number"), `email` ("Email") / `emailOptional` ("Email (optional)").
- Address: `location` ("Location") as section title → prefer `clinicAddress` ("Clinic Address"); "Search address or place" **NEW**; `selectLocationOnMap` ("Select Location on Map") → shorter "Pick on map" **NEW**; `country`, `region`, `district`, `city`, `postalCode`, `streetAddress` ("Street Address"), `enterStreetAddress` (hint), `streetAddressHelper`; `latitude` / `longitude`; `addressFound` ("Address found"), `addressNotFound` ("Address not found"), `couldNotGetAddressDetails`, `locationSaved` ("Location saved"); "Coordinates not set" **NEW**; "Reset from map" **NEW**; "Looking up address…" **NEW**.
- Timezone row: `practiceTimezone` ("Practice timezone").
- Validation: `phoneNumberRequired`, `invalidPhone`, `invalidEmail`, `enterValidEmail`.
- Save: `save`, `contactDetailsSaved`.

## 12. Known problems in the current app (do not carry over)
- The location card sits inside the Personal-information panel; contact is a separate panel with its own Save — two screens for one mental model.
- **Street-address edits send one PATCH per keystroke**, each followed by a full profile refetch (verified bug #11).
- **The picked pin is lost when reverse geocoding fails** or the Maps key is missing — only the success branch emits coordinates.
- Reverse geocoding is forced to English regardless of app language; the Google key is fetched unauthenticated from `/api/public/config` and logged with the request URL. Target: backend proxy.
- Structured fields are read-only and simply hidden when empty, so the doctor cannot correct a wrong district.
- No phone or email validation at all; the backend error is shown raw.
- There is no address search; the only way to set an address is the map pin.
- Snackbars (orange/red) instead of top banners.

## 13. Open questions for design
- Static map preview in v1 or text-only until native maps ship?
- Should structured fields be always visible (empty) or collapsed behind "Address details"?
- Phone prefix chip with flag list vs. plain text field with "+998" prefilled.
- Should the timezone row live here, on the root only, or both?
