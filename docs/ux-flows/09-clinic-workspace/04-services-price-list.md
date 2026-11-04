# Services & price list (clinic catalog) + add/edit service sheet

| | |
|---|---|
| Area | Clinic workspace |
| Who sees it | All members. Add/edit/deactivate buttons are shown to everyone today (server rules UNVERIFIED — target: owner / clinic admin only). No tier gate. |
| Reached from | Clinic workspace root → "Services" sub-tab; the treatment-plan wizard reads the same catalog |
| Screen type | Sub-tab (list with toolbar) + editor sheet (dialog today, bottom sheet target) |
| Current Flutter source | `lib/features/clinic/presentation/clinic_workspace_screen.dart` (`_ServicesTab`, `_openEditor`), `lib/state/clinic/clinic_providers.dart` (`planServicesProvider`, `clinicCatalogProvider`, create/patch) |
| Spec | `docs/kmp-spec/09-clinic-workspace-finance-treatment-plans.md` §2 row 4, ticket CLINIC-07 |

## 1. Purpose
Maintain the list of billable services the clinic offers, with default prices and which doctors can deliver them. It is a **unified** list: clinic-managed catalog items (editable here) plus services each doctor defined in their own profile (read-only here, tagged with the doctor). Treatment plans and visit charges pick from this list. Set up once, edited occasionally when prices change.

## 2. Entry points
- Workspace sub-tab "Services".
- Wizard empty state text "…add services in Clinic → Services or in the doctor's profile" (no link today; target: link).
- The "Add" button opens the editor in create mode; the row overflow opens it in edit mode.

## 3. Layout, top to bottom (phone portrait)
1. **Toolbar**: search field "Search by title or code…" (full width on phones) and a filled "Add clinic service" button wrapping below it (target: move to a FAB / app-bar "+" to free space).
2. **Status chips** single-select: All · Active · Inactive.
3. **Service list** (today a 9-column table: ID, Title, Code, Price, Currency, Status, Source, Doctors, Actions). Target phone card row:
   - Title (bold; grey italic when inactive) + optional code in a small monospace chip.
   - Price line: "150 000 UZS" (money formatting rules §4).
   - Source chip: "Clinic" (teal, building icon) or "Doctor" (indigo, person icon).
   - Doctors line: "All doctors at this clinic" / comma-joined names (2 lines max) / "No doctors assigned".
   - Status pill: "Active" (green) / "Inactive" (grey) — shown only for inactive rows to reduce noise (open question).
   - Trailing: overflow "⋮" for clinic rows (Edit · Deactivate/Reactivate); info icon with tooltip "Defined by the doctor in their profile · read-only here" for doctor rows (target: tap shows the hint as a banner instead of a hover tooltip).
4. Default sort: title A→Z. Table allowed sorting by id, title, code, price, currency, status, source; on phones a sort menu (Title · Price · Status) is enough.
5. **Editor sheet** (opened from Add / Edit): title "Add clinic service" or "Edit clinic service"; fields in order: Service name (required), Code (optional), Price (decimal keyboard) + Currency dropdown (UZS · USD · EUR · RUB; default UZS), switch "Assign to all doctors at this clinic" (default on), and when off a checkbox list "Select doctors" with each member (name + role). Footer: Cancel · Save.

## 4. Data and formats
| Field | Source | Format rule |
|---|---|---|
| Unified list | `GET /api/treatment-plans/plan-services?clinicId=` → rows with key, kind (CLINIC_CATALOG / DOCTOR_SERVICE), catalogItemId?, title, code?, defaultPriceMinor, currency, active, offeredByDoctorIds/Names | One request, in memory |
| Clinic catalog (for editing) | `GET /api/treatment-plans/catalog-items?clinicId=` | Needed to know appliesToAllDoctors + assigned ids for the editor |
| Price | integer minor units | **UZS: whole units, space-grouped, no decimals** ("150 000 UZS"); other currencies 2 decimals ("25.00 USD"). Today the table shows "1500.00" with the currency in a separate column |
| Price input | major units typed by the user | Accept "150000", "150 000", "1500.50", comma or dot decimal; store `round(value × 100)`; show the parsed preview under the field ("= 150 000 UZS") |
| ID | catalogItemId | "#42" for clinic rows; doctor rows have no stable clinic id ("·" today) — hide on phones |
| Status | active | Active / Inactive; inactive rows stay listed but are excluded from the wizard |
| Doctors text | appliesToAllDoctors / names | see §3; names 2 lines then ellipsis |
| Create payload | `POST /api/treatment-plans/catalog-items {clinicId, code?, title, defaultPriceMinor, currency, active:true, sortOrder:0, appliesToAllDoctors, assignedDoctorProfileIds[]}` | — |
| Edit payload | `PATCH /api/treatment-plans/catalog-items/{id}` partial | Clearing the code sends `""` |
| Toggle | same PATCH with `{active}` | — |

## 5. Primary flow (happy path)
1. Tap "Services" → skeleton rows (title line, price line, chip) × 6.
2. List arrives → rows render. Owner taps "+" → editor sheet.
3. Types name "Consultation", price "150000", keeps UZS and "all doctors" on → Save → sheet closes → banner "Service saved" → new row appears (list reloads).
4. Later: overflow → "Deactivate" → row turns grey/italic with "Inactive" pill → banner "Service deactivated".

## 6. Screen states
- **First load**: toolbar + 6 skeleton rows.
- **Empty**: "No services yet. Use the button below to add a clinic service and assign it to one or more doctors (or all)." + the Add button directly under the text (today the text says "button below" but the button is above).
- **Empty after filter**: "Nothing matches your search or filters." + Clear filters.
- **Error on load**: banner "Couldn't load services." + Retry.
- **Offline**: cached list; Add/Edit/Toggle disabled.
- **Refreshing**: pull-to-refresh; after save the list reloads in place without flicker (keep old rows until new arrive).
- **Partial data**: unified list ok but clinic catalog call fails → rows render but clinic rows lose their edit menu; show inline "Editing unavailable · Retry".
- **Permission needed**: n/a.
- **Locked by tier**: n/a.
- **Role difference**: today identical for everyone. Target: Add/Edit/Toggle visible for owner / clinic admin only (UNVERIFIED server rule — if the server lets any member edit, keep it for all and say so); staff see the list read-only; a doctor's own profile services are edited in Profile → Services & pricing, never here.

## 7. Interactions and secondary flows
- **Search** → live filter on title, code, catalog id, doctor names.
- **Status chip** → filter.
- **Tap "+"/Add** → editor (create). **Overflow → Edit** → editor (edit) prefilled.
- **Overflow → Deactivate / Reactivate** → immediate PATCH; row updates; banner. Target: no confirmation for deactivate (reversible), but a note in the banner "Deactivated services stay on existing plans."
- **Switch "Assign to all doctors"** off → doctor checklist appears; at least one must be ticked.
- **Save** → validation (§9) → request → close → list reload.
- **Cancel / swipe down** with changes → ask "Discard changes?" (today: silent discard).
- **Tap doctor-row info icon** → banner/hint "Defined by the doctor in their profile · read-only here".

## 8. Edge cases
- 0 services, 1 service, 300 services (dental catalogs are big) → list virtualised; search mandatory; consider grouping by first letter or by a backend group (groupName exists in the data but is unused — open question).
- Price 0 → allowed (free service) and shown as "0 UZS"; warn softly "Price is 0" under the field.
- Huge price (1 000 000 000 UZS) → grouping keeps it readable; field must not overflow (allow ~14 digits).
- Negative price → rejected inline.
- Decimal UZS ("1500.50") → accepted and stored as 150050 minor; displayed "1 500 UZS" (tiyin hidden) — flag in §13.
- Currency change on an existing service used in open plans → plans keep their own line currency; no retro effect; fine.
- Mixed currencies in one clinic → each row shows its own currency; totals elsewhere skip mismatched currencies (see finance files).
- Same title twice → allowed; code helps distinguish.
- Code cleared on edit → sent as empty string (server treats as null — UNVERIFIED).
- "All doctors" off with zero members ticked → blocked with "Select doctors".
- Member list fails to load inside the editor → show "No doctors listed for this clinic." and keep "all doctors" forced on.
- Doctor-owned service and a clinic service with the same name → both listed; source chip differentiates.
- Deactivate a service that is in a draft plan → plan keeps it; wizard no longer offers it.
- Two owners edit the same service → last write wins; list reload shows the result.
- 401 mid-save → logout.
- Rotation: editor keeps input; list may switch to the table layout on tablets.
- 200 % font: card rows grow; editor scrolls; Save pinned.
- Language switch: chips and labels re-render; user-entered titles never translated.
- Screen reader: row = "Consultation, 150 000 UZS, Clinic, all doctors, active, menu".

## 9. Validation and messages
| Field/action | Rule | When checked | Message (EN) | Where shown |
|---|---|---|---|---|
| Service name | non-empty after trim | on Save | "Service name is required" (**NEW**; today the toast just says "Service name") | inline |
| Price | parses to a number ≥ 0; max 12 integer digits | on Save (live preview while typing) | "Enter a valid price" (**NEW**) | inline |
| Doctors | if "all doctors" off → ≥ 1 selected | on Save | "Select doctors" (`clinicServicePickDoctors`) | inline under the checklist |
| Save | HTTP 200 | on response | "Service saved" (**NEW**) / server message or "Couldn't save service." (**NEW**) | banner |
| Toggle | HTTP 200 | on response | "Service deactivated" / "Service reactivated" (**NEW**) | banner |
| Load | HTTP 200 | on load | "Couldn't load services." (**NEW**) | banner + Retry |

## 10. Navigation out
- Editor closes back to the list (list refreshed on success; unchanged on cancel).
- No other exits; the wizard is not reachable from here.

## 11. Copy
- `clinicWorkspaceServices` "Services", `clinicServicesSearchHint` "Search by title or code…", `clinicServiceAddTitle` "Add clinic service", `clinicServiceEditTitle` "Edit clinic service"
- Chips: `clinicTreatmentPlansAll` "All", `clinicServiceActive` "Active", `clinicServiceInactiveBadge` "Inactive"
- Columns/labels: `clinicServicesColId` "ID", `clinicServicesColTitle` "Title", `clinicServicesColCode` "Code", `clinicServicesColPrice` "Price", `clinicServicesColCurrency` "Currency", `clinicServicesColStatus` "Status", `clinicServicesColSource` "Source", `clinicServicesColDoctors` "Doctors", `clinicServicesColActions` "Actions"
- Source: `clinicServicesSourceClinic` "Clinic", `clinicServicesSourceDoctor` "Doctor", `clinicServicesDoctorOnlyHint` "Defined by the doctor in their profile · read-only here"
- Doctors text: `clinicServicesAssignmentAll` "All doctors at this clinic", `clinicServicesAssignmentNone` "No doctors assigned"
- Menu: `edit` "Edit", `clinicServiceDeactivate` "Deactivate", `clinicServiceActivate` "Reactivate"
- Editor: `clinicServiceTitleLabel` "Service name", `clinicServiceCodeLabel` "Code (optional)", `clinicServicePriceLabel` "Price", `clinicServiceCurrencyLabel` "Currency", `clinicServiceAllDoctorsToggle` "Assign to all doctors at this clinic", `clinicServicePickDoctors` "Select doctors", `clinicServiceSave` "Save", `cancel` "Cancel", `clinicWorkspaceNoDoctors` "No doctors listed for this clinic."
- Empty: `clinicServicesEmpty` "No services yet. Use the button below to add a clinic service and assign it to one or more doctors (or all).", `clinicTableNoFilteredResults` "Nothing matches your search or filters."
- **NEW**: see §9 plus "Discard changes?", "Price is 0", "= {{amount}}" (parsed price preview), "Editing unavailable · Retry"

## 12. Known problems in the current app (do not carry over)
- 9-column spreadsheet with horizontal scroll on phones.
- Price parsed as a floating-point number × 100 (rounding errors on some decimals); price shown as "1500.00" with a separate currency column, unlike finance money formatting.
- Required-field error toast shows the field label instead of a sentence; no success feedback; raw exception text on failures.
- Empty-state text refers to a button "below" that is above.
- Doctor-only rows show a hover tooltip, useless on touch.
- Every member sees Add/Edit regardless of role (needs server confirmation).
- The write calls check only for HTTP 200 (201 would be treated as failure).

## 13. Open questions for design
- Group services by a category/group (backend sends groupName but the app ignores it) or keep a flat searchable list?
- Show tiyin for UZS anywhere? Recommendation: never (whole sums only).
- Is "Inactive" a chip filter or a separate "Archived" section at the bottom?
- Inline price edit from the row (long-press → price field) for quick repricing?
