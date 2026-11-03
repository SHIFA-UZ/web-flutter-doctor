> **Status:** analysis baseline, written 2026-10-24 from the Flutter code at commit `a5ace33` (no access to backend or Figma). It is the behavioural spec for the KMP rewrite: API shapes, flows, rules, quirks and the tickets at the end. Refine it per ticket while implementing (add Figma node IDs, confirm UNVERIFIED items against `<BASE_URL>/v3/api-docs`), and fix the doc in the same PR when the implementation differs. Paths are Flutter sources on `main`, for traceability only.

# 03 · Calendar, Schedule Setup, Doctor Locations, Timezone

Repo: `/Users/sheroziy.saidkhodjaev/Projects/Private/web-flutter-doctor` (read-only analysis). All paths below are relative to `lib/` unless they start with `test/` or `assets/`. Line numbers are from the files as read on 2026-10-24. Anything I could not confirm from code is marked **UNVERIFIED**.

Files covered (fully read): `features/calendar/**` (calendar_screen.dart 5 869 lines, calendar_week_grid_view.dart, domain/*), `state/calendar/calendar_controller.dart`, `features/schedule/**`, `state/schedule/*`, `state/locations/*`, `core/services/timezone_service.dart`, `core/utils/timezone_utils.dart`, `features/clinic/presentation/clinic_doctor_schedule_page.dart`, `clinic_schedule_return_info.dart`, `features/appointments/application/today_appointments_provider.dart`, `state/appointments/appointment_invalidation.dart`, `features/debug/timezone_debug_screen.dart`, `test/consecutive_slot_range_test.dart`.

---

## 1. Calendar screen (`features/calendar/presentation/calendar_screen.dart`)

### 1.1 Layout / views

| Breakpoint | Layout | Evidence |
|---|---|---|
| Single pane (native mobile shell, or content width < 720 px) | Vertical scroll: optional TZ-hint banner → header row (title + date label + Block / Filter icon buttons) → `CalendarMonthPanel(compact:true)` (table_calendar month grid) → `CalendarDayEntriesList` for the selected day (shrink-wrapped). Selecting an entry replaces the **whole screen** with `CalendarSlotDetailsPanel`. | `calendar_screen.dart:883-895`, `898-966`; breakpoint `core/layout/responsive.dart:22,88-94` |
| Two pane (desktop / wide web) | `Row`: left flex 3 = title row + **multi-day week grid** (`CalendarWeekGridView`); right flex 2 = toolbar (Block, 1–7 day selector, Filter, spinner) + `AnimatedSwitcher` between `CalendarMonthPanel` (no entry selected) and `CalendarSlotDetailsPanel`. | `968-1014`, `2112-2153` |

There is **no month-with-events view and no classic "week view" tab**: the views are (a) a month *picker* with occupancy shading, (b) a day **list** (mobile) or (c) an N-day **time grid** (desktop, N = `_dayViewCount` 1..7 via `SegmentedButton`, `2157-2198`, only offered when `!useSinglePane`, `1089-1099`). Visible grid days = anchor day + N-1 following days (`121-129`); `_viewAnchorDay` stays fixed when tapping a slot inside the grid so the columns do not jump (`79-80`, `1800-1803`).

Date-range label formatting: single day `d MMMM yyyy`, same month `d–d MMMM yyyy`, cross-month, cross-year variants (`131-150`); month names come from `AppLocalizations.monthName` (`app_localizations.dart:6405`).

### 1.2 Date navigation

* Month picker: `TableCalendar(firstDay: 2020, lastDay: 2030, startingDayOfWeek: monday)`; `onDaySelected` → `onChanged` (sets `_selectedDay`, `_viewAnchorDay`, `_focusedDay`, clears selection, `_reloadCalendar`); `onPageChanged` → `_loadMonth` prefetch for occupancy shading (`2689-2705`, `929-950`, `2124-2145`).
* Initial day = today **in doctor practice TZ** (`getTodayInTimezone`) unless opened via push notification go-to (`388-409`).
* Go-to-appointment: `calendarGoToAppointmentIdProvider` + `calendarGoToAppointmentDayProvider` set in `app/app.dart:126-127`; screen switches day, loads it, selects the matching `appointmentId` (`790-855`).
* Quick-book intent (`calendarQuickBookIntentProvider`, set in `features/home/application/home_quick_action_navigation.dart:21`): sequentially loads **up to 30 days** and selects the first free slot (today: only slots starting after now in doctor TZ); pre-selects "Video call" place when `preferVideoConsultation` (`702-765`).
* Reload triggers: profile loaded (`listenManual(profileAllProvider, fireImmediately)` `416-444`), shell tab becomes Calendar (`446-465`), app resumed (≥5 s since last refresh, `474-492`), filter applied, day/day-count/staff toggles changed.

### 1.3 What is rendered

**Week grid (`calendar_week_grid_view.dart`)**
* Constants: `hourHeight 56`, `timeGutterWidth 52`, default hours 07–20, auto-extended to cover entries (`33-63`).
* Header cells: 3-letter weekday (upper-cased, localized) + day number; today = filled brand circle (`240-290`).
* Appointment block: `brand.withOpacity(0.12)` bg, brand border, `person_outline` icon, patient name (max 2 lines) + `HH:mm – HH:mm`; **COMPLETED** → line-through + a 2.5 px red strike line across the block (`556-691`).
* Blocked block: `red.shade50` bg, `red.shade300` border, `block` icon, title = `blockReason` or "Blocked" (`596-603`).
* Free slots are **not drawn**; one translucent `Listener` per column maps pointer-down Y → free slot (`freeSlotAtVerticalPosition`, `calendar_grid_display.dart:19-38`); the selected free slot gets a brand outline highlight (`517-554`).
* Overlap layout: `layoutOverlappingCalendarEntries` (§1.9) gives `columnIndex/columnCount`; width = `(colWidth − 2px gap)`, 2 px horizontal inset (`409-443`).
* "Now" line: red 2 px line, **device** `DateTime.now()` (`103-104`, `444-455`) — see §9.
* Min block height 22 px (`419`).

**Day list (`CalendarDayEntriesList`, `calendar_screen.dart:2242-2589`)**
* Free slot: white card, `+` avatar, label "Free slots", location chip, time range.
* Blocked: `red.shade50` card, block avatar, reason, "Blocked" caption.
* Appointment: avatar (`NetworkImage` photo or person icon), patient name, location chip (video → blue `videocam`, else teal `location_on`; label "Video call" if location contains "video"), time range, duration badge (`1h 30 min`, only when > 5 min), reason line; COMPLETED → strike-through + red line. Card min height grows with duration `68 + dur*0.45`, clamped 68..154 (`2311-2314`).
* Selected = brand 2 px border.

**Month cell occupancy (`_OccupancyDayCell` `2870-2961`, `domain/calendar_day_occupancy.dart`)**
* `freeRatio = free / (free + appointments + blocked)`; null when day not cached or has no entries → plain number.
* ≥ 1.0 → no fill; ≤ 0.0 → `grey.shade900` + white text; 0.5..1 → lerp `grey300→grey100`; < 0.5 → lerp `grey700→grey300`; text white when ratio < 0.35 (`47-70`).
* Selected = filled primary-teal circle; today = teal text / teal ring. Legend text under the panel on desktop: "Dark = fully booked · No fill = open availability" (`2816-2826`, key `calendarOccupancyLegend`).

**Staff colours** (`domain/calendar_staff_colors.dart`): 8-colour palette, index = position of doctorProfileId in sorted roster, fallback `id % 8`. Used only when ≥ 2 schedulable staff (`calendar_screen.dart:257-268`).

**Status chip in details** (`4303-4363`): COMPLETED green/check, CANCELLED red, IN_PROGRESS blue/timelapse, CONFIRMED amber/verified, else grey "UNKNOWN"/raw status. **Payment chip** (video only, `4365-4422`): PAID green, PENDING orange, FAILED red, NOT_REQUIRED blue-grey, else grey raw.

### 1.4 Interactions

* **Tap only.** No long-press, no drag/drop, no resize gestures anywhere in the calendar files (grep for `onLongPress|Draggable|onPanUpdate` returns only the patient bottom sheet's `DraggableScrollableSheet` at `5755`).
* Tap on list row / grid block / grid free area → `_selectCalendarEntry(entry, doctorProfileId)` (`663-673`).
* Header buttons: Block time (disabled until a day is selected), 1–7 day selector (desktop), Filter.

### 1.5 Filters (`1223-1305`)

Dialog with 3 checkboxes: Show Appointments / Show Free Slots / Show blocked time. Applied client-side in `_entriesForDoctor` (`168-173`); after Apply the screen also **re-fetches** the day (unnecessary, `1298-1303`). `_shouldShowUpdateScheduleCard` reads **raw** entries so hiding free slots does not trigger the "update schedule" card (`352-386`).

### 1.6 Booking a free slot (`CalendarSlotDetailsPanel`, `3064-5690`)

Panel state is seeded in `didChangeDependencies` / `didUpdateWidget` (`3399-3462`). For a free slot:

1. Past-day guard: if slot day < today (doctor TZ) → orange "This slot is in the past" card, Save disabled (`3665-3671`, `5000-5034`). Same-day earlier slots **are** bookable.
2. **Assign Patient** tile → loads `patientsForAssignmentProvider.loadPatientsForAssignment()` then `_AssignPatientSheet` (`5693-5869`): draggable sheet, search by name or id, "+" button opens `showCreatePatientSheet` and auto-selects the created patient.
3. **Place** dropdown: exactly two options — localized "Clinic Address" and "Video call" (`4636-4643`, `5128-5191`). Hint text below. Pre-selected from `initialBookingPlace` (quick action).
4. **Start time** dropdown: all free slots of the day with the same `locationId` as the tapped slot (`4650-4661`); changing it resets the end.
5. **End time** dropdown: `consecutiveEndTimesForFreeSlot` chain (§1.10) (`4663-4669`); duration label shown.
6. **Reason** text (default `"Check Up"`, `3410-3413`).
7. **Save** → `_assignOnSave` (`4013-4124`): validate patient + place; fetch patient via `patientByIdProvider` and show `showChronicDiseaseWarning` if `chronicDisease` set and ≠ "None" (non-blocking); `isVideo = place contains "video"`; `location = isVideo ? 'Video Consultation' : 'Clinic Address'` (hard-coded English strings sent to backend, `4062-4066`); call `bookFreeSlotRemote(day, slot, patientId, tz, location, reason, isVideo, endExclusive, actingAsDoctorProfileId)`; then `invalidateAppointmentRelatedProviders` + `refreshCalendarDay` + `onSavedSuccessfully` (parent reloads and closes panel); snackbar "Patient assigned: name". **Any** error shows the same "Selected time range is no longer fully available" message (`4108-4120`).
8. Dirty tracking (`3503-3521`) on reason/place/start/end; Discard asks to confirm when dirty (`3523-3548`).

`bookFreeSlotRemote` (`state/calendar/calendar_controller.dart:488-570`): requires `slot.startAtUtc`; blocks past **days** via `getTodayInTimezone`; `slotMinutes = bookingSlotMinutesForRange(...)` (UTC start → wall-clock end in practice TZ); body includes `locationId` only for non-video and when slot has one; `resourceDoctorId` when acting for a colleague; on 409/400 force-refreshes the day before throwing.

### 1.7 Appointment detail flows

* **Primary CTA** label (`4281-4301`): COMPLETED → "Open Summary" (opens saved appointment-note PDF via patients tab, `4209-4279`); IN_PROGRESS → "Continue Appointment"; in-clinic earlier-today → "Start"; past-due blocked → "Appointment Ended" (disabled); else "Start Appointment". Start pushes `AppRoutes.videoCall` or `AppRoutes.inPerson` with an `Appointment` built from the **live cache entry** (`4126-4156`).
* **Video start window**: enabled from 5 min before start until 1 h after end, in doctor TZ (`3363-3397`); hint text explains too-early / too-late.
* **In-clinic past-due on today** can still be started (`3646-3653`).
* **Date & Time tile → Change slot dialog** (`3673-3869`): date picker (firstDate = today in doctor TZ, lastDate 2030), `FutureBuilder` loads free slots of the chosen day (`_loadAvailableSlots` `3986-4011`; uses `previewDayForDoctorProfile` when acting for a colleague, else `loadDay` + cache), pick one slot, confirm → `changeAppointmentSlot(appointmentId, day, newDay, newStart, slotMinutes = picked slot duration, tz)` → PUT; refresh both days.
* **Adjust duration** (`3871-3917`, `3919-3984`, UI `5368-5430`): choices = current end ∪ shorter ends at venue grain (`scheduleGrainMinutesGuess` = GCD of same-venue free-slot durations, default = appointment length) ∪ longer ends chaining through adjacent same-venue free slots; Apply → `changeAppointmentSlot` with unchanged start and `slotMinutes = appointmentSlotMinutesUtcStartWallEnd(...)` (min 5 min).
* **Place** (read-only `AppointmentPlaceDropdown`, `2964-3059`): shows clinic street (clinic override → profile `address` → `locationStreetAddress` → "Clinic Address") or "Video call"; hint "cannot be changed here".
* **Encourage payment** (`3213-3219`, `4435-4462`): video + `paymentStatus == PENDING` + not past/cancelled/completed → POST notify-payment-reminder.
* **Cancel** (`5562-5652`): confirm dialog → `cancelAppointment` → DELETE; disabled when past, completed or cancelled.
* **AI documentation** expansion tile (`4863-4996`): two tabs (Appointment Notes = consultation notes + AI drafts; Summary PDF = latest `APPOINTMENT_NOTE` patient document closest to the slot time, `4158-4207`). While open / after starting a visit, a 4 s timer re-invalidates notes/drafts/docs up to 30 ticks (`3489-3501`).
* Patient name tap → patients tab with `patientId` (and `clinicScheduleReturn` args when opened from the clinic overlay) (`3221-3252`).

### 1.8 Blocks (time off)

* **Create** (`_showBlockTimeDialog` `1335-1778`): modes `entireDay` (00:00–23:59 of selected day), `timeRange` (defaults 09:00–17:00, end must be > start), `dateRange` (from/to dates, 00:00–23:59, to ≥ from). Reason text (default "Emergency"), checkbox "Cancel overlapping appointments" (default **true**). Live preview of overlapping count via `countOverlappingAppointmentsForBlock` (GET). Confirm → `createScheduleBlock` (POST) → force-refresh every day in range → snackbar with cancelled count; if cancellations happened, `invalidateAppointmentRelatedProviders`.
* **Blocked entry details**: red card + reason + "Remove block" (confirm → `deleteScheduleBlock` DELETE, `3254-3309`); no other actions (`4775-4818`, `5477-5489`).
* Blocks for a colleague carry `resourceDoctorId` / `?doctorId=` (`calendar_controller.dart:683-685, 750-753`).

### 1.9 Overlap layout algorithm (`domain/calendar_overlap_layout.dart`)

1. Sort by start asc, then end desc (`32-37`).
2. Greedy clustering: each entry joins the first cluster it overlaps with; if it overlaps several, later clusters are merged into the first (`39-58`). Overlap = half-open interval test on minutes-of-day (`23-29`).
3. Inside each cluster, first-fit column assignment: put the entry into the first column where it overlaps nothing, else open a new column (`71-85`).
4. Every entry in the cluster gets `columnCount = columns.length`, so all blocks in a cluster share equal width (no Google-style "expand into free space") (`87-98`).

Complexity O(n²) per day; only appointments + blocked are laid out (free slots are a tap layer).

### 1.10 Consecutive slot range logic (`domain/consecutive_slot_range.dart`, test `test/consecutive_slot_range_test.dart`)

* Slots are chained on **UTC instants** (`utcInstantFromBackendIso`) and must share `locationId` ("venue") (`23-40`).
* `coversUtcRangeWithFreeSlotChain(dayFreeSlots, startUtc, endExclusiveUtc, venueId)` walks `cur = slot.endAt` while a free slot starts exactly at `cur`; true iff it lands exactly on `end` (`45-104`). Test: two adjacent hourly slots → true; gap → false.
* `consecutiveEndTimesForFreeSlot(dayEntries, startSlot, tz)` → list of wall-clock `TimeOfDay` ends reachable by chaining forward from the slot (max 400 hops) (`107-168`).
* `bookingSlotMinutesForRange(freeSlotStart, endExclusiveWall, calendarDay, tz)` → minutes between UTC start and the wall-clock end interpreted in practice TZ on `calendarDay`, clamped 0..2880 (`171-186`). Tests: `Asia/Tashkent` 03:00Z start, 11:00 wall end → 180.
* `scheduleGrainMinutesGuess` → GCD of same-venue free-slot durations, clamped 5..240 (`215-229`).
* `extendEndWallsChainFromUtcBoundary` / `shorteningEndWallsGrainUtc` / `appointmentSlotMinutesUtcStartWallEnd` support the "Adjust duration" UI (`232-360`).
* Comment says it mirrors backend Kotlin `SlotAvailabilityService` (`1-2`) — **UNVERIFIED** (backend not in repo).

### 1.11 Timezone mismatch hint

`_shouldShowTimeZoneMismatchHint` compares `getNowInTimezone(practiceTz).timeZoneOffset` with device `DateTime.now().timeZoneOffset` (`767-777`); amber banner with `calendarTimezoneMismatchHint` ("...device timezone does not match the calendar schedule timezone ({scheduleTimeZone})...", `assets/localization/en.json:32`) and a Dismiss button (session-only flag `_timeZoneHintDismissed`, `84`, `1016-1049`).

### 1.12 Staff calendars (multi-doctor)

* Roster = `clinicMembersProvider(selectedClinic.clinicId)` filtered to `membershipRole ∈ {DOCTOR, OWNER}`, sorted by display name (`201-210`).
* Shown only when roster ≥ 2: "Staff calendars" `FilterChip` row (`2003-2061`). Own doctor always in `_visibleStaffDoctorIds`; cannot deselect the last one (`683-700`). Selection persisted in `SharedPreferences` key `calendar_visible_staff_v1:<doctorId>:<clinicId>` (`270-350`).
* Colleague data is fetched read-only via `previewDayForDoctorProfile` (GET `/api/calendar?doctorId=`) into widget-local `_staffEntriesByDoctor` (`569-593`); own data stays in `calendarProvider`.
* Rendering: one grid/list per visible doctor, stacked vertically with a coloured dot + name header ("My schedule" for self); grids are `shrinkWrap` with `maxGridHeight 420` (`1805-1831`, `1916-1936`).
* Timezone for a colleague's grid = `selectedClinic.timeZone`, own = profile TZ (`245-252`). Slot panel receives `clinicDoctorProfileId`, `clinicDoctorDisplayName`, `scheduleTimeZone`, `primaryClinicVenueLabel = clinic.address` (`2063-2110`).
* Separate full-screen variant for receptionists/owners: `ClinicDoctorScheduleRoute` (`features/clinic/presentation/clinic_doctor_schedule_page.dart`): wraps a `ProviderScope` overriding `calendarProvider` with `CalendarController(initialResourceDoctorId)` (`42-57`); month panel + day list only (no week grid, no block button, filter has 2 options), always `forceRefresh` (`119-136`), date pill opens a date picker on desktop (`209-226`), passes `scheduleTimeZone = clinicScheduleTimeZone` to the panel (`264-282`). `ClinicScheduleReturnInfo` (`clinic_schedule_return_info.dart`) serialises `{doctorProfileId, doctorDisplayName, clinicScheduleTimeZone, clinicStreetAddress}` into route args so the patients overlay can navigate back.

### 1.13 Responsive summary

| Concern | Mobile / single pane | Desktop |
|---|---|---|
| Day content | list | N-day time grid |
| Month grid | compact (row 36, cell 30, single-letter weekdays via `DateFormat('EEEEE')`) | full (row 48, cell 36, year headline, legend) |
| Details | full screen | right pane with AnimatedSwitcher |
| Toolbar | icon buttons | labelled buttons + day-count selector |
| Scroll | one `SingleChildScrollView`, bottom clearance for nav bar | panes individually scroll |

---

## 2. Schedule setup screen (`features/schedule/presentation/setup_schedule_screen.dart`, `ScheduleScreen`)

**Route**: `AppRoutes.setupSchedule = '/create/schedule'` (`app/router.dart:42`), registered both in the root router (`:92`) and the shell nested router (`:278`). The only caller in the app is the calendar's "Go To Schedule" card (`calendar_screen.dart:953, 2148`). Registration **no longer** routes here (`features/auth/presentation/create_account/account_information_screen.dart:1`: "goes straight to Login (no schedule)"), so there is no first-time-vs-edit distinction left: same screen, same behaviour. Keep the route name only if deep links depend on it (**UNVERIFIED**).

### 2.1 Load (`91-203`)
1. `fetchDoctorLocations` → resolve active location: current → prefs `active_location_id:<doctorId>` → `isPrimary` → first; save back to prefs (`96-128`).
2. `GET /api/schedule/rules[?locationId=]` → `RuleDto[]` → `scheduleProvider.replaceWithBackendRules` (`131-149`); 401 → "unauthorizedPleaseLoginAgain".
3. `fetchValidityPeriods` (optional, errors swallowed).
4. `GET /api/doctors/me` → `profile.scheduleValidFrom/scheduleValidUntil` → prefill start/end date (`160-187`).
5. `fetchDateSpecificRules(locationId)`.

### 2.2 Editor
* Location selector (dropdown of `label`, `★` for primary) + "Manage" → `DoctorLocationsScreen` (pushed with `MaterialPageRoute`, `577-585`); shown even for one location, amber hint when none (`726-746`).
* Existing validity periods as chips + note "new period must not overlap".
* Validity: optional **start date** (picker today..endDate) and **end date** (picker start..today+365) (`356-396`). Default end = now + 60 days (`state/schedule/schedule_controller.dart:37`).
* "Expand schedule for dates" section (`_ExpandScheduleSection`): add = from date, to date, start time, end time (default start+1h), slot duration → `POST /api/schedule/date-specific` **immediately** (`398-465`); list with delete → `DELETE` immediately (`467-481`).
* 7 `_DayCard`s (Mon..Sun, 1 column < 720 px, else 2): checkbox enables the day; expand shows `_SlotEditorInline` with per-range cards (time period, slot timeframe, Remove) and Add. Add/Edit = two 24-h `showTimePicker`s + duration bottom sheet (10/15/20/30/45/60 min) (`206-246`, `272-350`). Popup menu: copy from previous weekday / copy from another day (`483-568`).
* **Validation**: end > start (`248-252`); no overlap with other ranges on the same day (`254-261`); expansion to ≥ from; client-side validity-period conflict check: new range must be entirely before, entirely after, or fully contained in each existing period (`591-640`).
* Not present: consultation types, per-rule location (location is screen-wide), buffers, breaks, per-location validity.

### 2.3 Save (`587-665` → `state/schedule/schedule_actions.dart:19-69`)
1. `PUT /api/schedule/rules[?locationId=]` with JSON array `[{weekday 1..7, startTime "HH:mm", endTime "HH:mm", slotMinutes, locationId?}]` (`schedule_controller.dart:118-146`).
2. `PATCH /api/schedule/valid-range` `{validUntil: "YYYY-MM-DD", validFrom?: "YYYY-MM-DD"}`; error message extracted from `{message|error}`.
3. Snackbar "scheduleSaved" and `Navigator.pop`. Any failure shows the generic "somethingWentWrong" (backend message is logged only).

---

## 3. Doctor locations screen (`features/schedule/presentation/doctor_locations_screen.dart`)

* List (pull-to-refresh) of `_LocationCard`: label, "Primary" badge, clinic, address, `city, region, country`; edit / delete icons; FAB "Add location".
* Editor dialog (`268-431`): `label` (required), `clinic`, `LocationPickerSection` (`features/profile/presentation/location_picker_widget.dart` — `flutter_map` tiles + `google_geocoding_service` reverse/forward geocoding, lines 4, 9, 169-183, 510-518) returning `latitude, longitude, locationCountry, locationRegion, locationDistrict, locationCity, locationPostalCode, locationStreetAddress`; `isPrimary` switch. `address` is set to the street address (`327-329`).
* CRUD via `state/locations/doctor_location_actions.dart`: GET/POST `/api/doctors/me/locations`, PATCH/DELETE `/api/doctors/me/locations/{id}`; errors use `{message|error}` from body.
* No provider; both this screen and `ScheduleScreen` hold local lists. `main_shell.dart:163-181` also calls `fetchDoctorLocations` to show the "active location" label (prefs key shared with the schedule screen).

---

## 4. Timezone

### 4.1 Sources
| Context | Source | Evidence |
|---|---|---|
| Doctor practice TZ | `profileAllProvider` → `GET /api/doctors/me` → `profile['timeZone']` (IANA string) | `state/profile/profile_providers.dart:12-33`; read in `calendar_screen.dart:638-642` (fallback `'UTC'` when empty) |
| Clinic staff (no doctor profile) | `meProfileProvider.timeZone` (`GET /api/me/profile`) | `appointment_invalidation.dart:46-53` |
| Colleague grid / clinic route | `selectedClinic.timeZone` / `clinicScheduleTimeZone` arg | `calendar_screen.dart:245-252`, `clinic_doctor_schedule_page.dart:91-94` |
| Device TZ | `iana_time_zone` (`getDetectedTimeZone`) — **only** to prefill registration forms; `commonIanaTimeZones` list feeds `searchable_timezone_dropdown.dart` | `core/services/timezone_service.dart`; callers in `features/auth/**`, `features/profile/presentation/searchable_timezone_dropdown.dart` |
| TZ database | `tz.initializeTimeZones()` in `main.dart:40` (`timezone` ^0.9.4) | |

### 4.2 Helpers (`core/utils/timezone_utils.dart`)
`getNowInTimezone(tz)` (TZDateTime.now or UTC fallback, with `debugPrint` spam), `timeOfDayToDateTimeInZone(tod, date, tz)` → `TZDateTime`, `calculateMinutesUntil`, `getTodayInTimezone`, `utcToTimezone`, `monthRangeUtcInTimezone`, `formatTimeForDisplay`. In `calendar_models.dart`: `utcIsoToTimeOfDayInZone` / `_utcToTimeOfDayInZone` / `_instantToTimeOfDay` (`114-140`). In `calendar_controller.dart`: `localDateTimeToUtcIso(tz, y, m, d, h, min)` (`284-301`).

### 4.3 Rules actually applied
1. **Backend → UI**: every `startAt/endAt` is ISO-8601 UTC; converted to `TimeOfDay` in practice TZ at parse time; the raw UTC strings are kept on the entry for mutations (`calendar_models.dart:144-212`).
2. **UI → backend**: wall-clock (practice TZ) → UTC ISO via `TZDateTime(loc, …).toUtc()` for bookings (`bookingSlotMinutesForRange`), change-slot (`changeAppointmentSlot` `607-614`) and blocks (`_localToUtcIso` `calendar_screen.dart:1320-1333`). Date-only values (`day=`, `validFrom`, `startDate`) are sent as plain `YYYY-MM-DD` with no TZ.
3. **"Today"/"past"** decisions use `getTodayInTimezone` / `getNowInTimezone` (booking guard `calendar_controller.dart:510-516`, past free slot `3665-3671`, past appointment `3614-3633`, video window `3363-3385`, date pickers `3699-3709`, `setup_schedule_screen.dart:358-361`).
4. **Day keys** are device-local `DateTime(y,m,d)` midnight (`_dayKey`), used purely as map keys; fine as long as every producer builds them the same way (they do).
5. **Device time still leaks** in: grid "now" line & today header (`calendar_week_grid_view.dart:103-104`), table_calendar `isToday` (`calendar_screen.dart:2729,2735,2740`), `_focusedDay = DateTime.now()` (`77`), block dialog date-picker bounds (`1374-1375`), `scheduleProvider` default end date (`schedule_controller.dart:37`), schedule save fallback start (`setup_schedule_screen.dart:595`), mismatch-hint comparison itself (intended).
6. Naive timestamps: `_utcToTimeOfDayInZone` does `parsed.isUtc ? parsed : parsed.toUtc()` (`calendar_models.dart:125-127`); for a string without `Z`, `DateTime.parse` yields *device-local* time, so `.toUtc()` shifts it by the device offset — the comment "treat as UTC" is wrong. `_isPastAppointment` (`3617-3628`) and `utcInstantFromBackendIso` (`consecutive_slot_range.dart:8-21`) re-wrap fields with `DateTime.utc(...)`, which is the intended behaviour. Backend always sends `Z` today (**UNVERIFIED**), so this only matters for robustness.

### 4.4 Timezone debug screen (`features/debug/timezone_debug_screen.dart`)
Marked "TEMPORARY DEBUG SCREEN"; shows profile TZ (+ full profile map), device/UTC/doctor "now", first 3 of today's appointments, and converts a fixed `2024-03-15T08:00:00Z`. **Not routed anywhere** (grep `TimezoneDebugScreen` outside `features/debug` → nothing) — dead code; do not port.

---

## 5. State (providers in scope)

| Provider | Type | Holds | Notes |
|---|---|---|---|
| `calendarProvider` (`state/calendar/calendar_controller.dart:767-771`) | `StateNotifierProvider<CalendarController, Map<DateTime, List<CalendarEntry>>>` | per-day cache (own doctor, or `resourceDoctorId` when set) | not autoDispose; throttle 60 s per day (`237`, `336-341`); clears on `authTokenProvider` change (`248-253`) and on `setResourceDoctorId` change (`265-270`); `loadMonth` fetches uncached days in batches of 4 (`363-394`); overridden per `ProviderScope` in `ClinicDoctorScheduleRoute` |
| `calendarGoToAppointmentIdProvider` / `calendarGoToAppointmentDayProvider` (`775-781`) | `StateProvider<int?>` / `StateProvider<DateTime?>` | push-tap target | set in `app/app.dart:126-127`; consumed & cleared by CalendarScreen (`802-803`) |
| `calendarQuickBookIntentProvider` (`791-792`) | `StateProvider<CalendarQuickBookIntent?>` | `{preferVideoConsultation}` | set in `home_quick_action_navigation.dart:21`, cleared by CalendarScreen (`861`) |
| `scheduleProvider` (`state/schedule/schedule_controller.dart:180-183`) | `StateNotifierProvider<ScheduleController, ScheduleState>` | `{slots: Map<weekdayName, List<TimeSlot>>, startDate?, endDate}` editor state | not autoDispose → survives between visits; replaced from backend on each open |
| `todayAppointmentsProvider` (`today_appointments_provider.dart:103-117`) | `FutureProvider<List<Appointment>>` | today's (practice TZ) appointments derived from `calendarProvider` cache (loads the day if missing) | depends on `profileAllProvider` |
| `nextUpcomingAppointmentProvider` (`121-143`) | `FutureProvider<Appointment?>` | in-progress or next appointment today, else first within 14 days (sequential `loadDay` per day) | up to 14 GETs on cold cache |
| (none) locations | plain `Future` action functions taking `WidgetRef` | — | screens keep local `List<DoctorLocationDto>` |
| Related read-only deps | `profileAllProvider`, `meProfileProvider`, `selectedClinicProvider`, `selectedClinicIdProvider`, `clinicMembersProvider(clinicId)`, `patientsForAssignmentProvider`, `patientByIdProvider(id)`, `patientDocumentsProvider(key)`, `consultationNotesForAppointmentProvider(id)`, `draftNotesForAppointmentProvider(id)`, `shellProvider`, `doctorAnalyticsOverviewProvider` | | |

**Invalidation graph** (`state/appointments/appointment_invalidation.dart`):

```
mutation (book / cancel / change slot / adjust duration / block w/ cancellations /
          FCM type ∈ appointmentCalendarPushTypes / push go-to / visit completion elsewhere)
  └─ invalidateAppointmentRelatedProviders(ref, {clinicWorkspaceId, appointmentDay})
       ├─ resolve TZ (meProfileProvider → profileAllProvider)
       ├─ refreshCalendarDay(today in TZ)            → calendarProvider.loadDay(force)
       ├─ refreshCalendarDay(appointmentDay) if ≠ today
       ├─ invalidate todayAppointmentsProvider, nextUpcomingAppointmentProvider
       ├─ invalidate doctorAnalyticsOverviewProvider
       └─ clinicId? refreshClinicFinancialData : invalidate treatmentPlansForClinic/ForPatient
refreshCalendarFromPushPayload(data)  → above + refresh the day of data.appointmentStartAt (if ≠ today)
```

In addition every `CalendarController` mutation already force-reloads the affected day(s) itself, and the slot panel then calls `refreshCalendarDay` **and** `onSavedSuccessfully → _reloadDoctorCalendar → loadDay`, so one booking triggers 2–3 GETs of the same day (`calendar_screen.dart:4075-4094`).

**Polling / timers**: slot-panel scribe poll every 4 s × 30 ticks (`3489-3501`); app-resume refresh gated at 5 s (`486-490`); no periodic calendar polling.

---

## 6. Endpoints

| Method & path | Query / body (as sent) | Response parsed | Errors handled | Where |
|---|---|---|---|---|
| GET `/api/calendar` | `day=YYYY-MM-DD`, `doctorId?` | JSON array (or `{content|data|entries:[…]}`) of entries: `type` (`FREE_SLOT`\|`APPOINTMENT`\|`BLOCKED`, anything else → free slot), `startAt`, `endAt` (ISO UTC), `locationId?`, `locationLabel?`, `location?`, `patientName?`, `patientPhotoUrl?`\|`photoUrl?`, `appointmentId?`, `patientId?`, `status?`, `paymentStatus?`, `reason?`, `briefingStatus?`, `attachmentCount?`, `blockId?`, `blockReason?` | 401 → "Unauthorized: please login again"; other → `Exception`; body decode failure → `CalendarDecodeFailure`, day set to `[]`; entries missing startAt/endAt skipped | `calendar_controller.dart:416-479`, `calendar_models.dart:144-212` |
| POST `/api/schedule/book` | `{startAt, slotMinutes, patientId, location, reason, isVideo, locationId?, resourceDoctorId?}` | status only | 200/201 ok; 409/400 → refresh day then throw; else throw | `488-570` |
| DELETE `/api/appointments/{id}` | — | — | 200/204 ok | `577-595` |
| PUT `/api/appointments/{id}/change` | `{startAt, slotMinutes}` | — | 200/204 ok; 409/400 refresh both days | `599-650` |
| POST `/api/appointments/{id}/notify-payment-reminder` | `{}` | — | 200/204 ok | `653-663` |
| POST `/api/schedule/blocks` | `{startAt, endAt, cancelOverlappingAppointments, reason?, resourceDoctorId?}` | `{cancelledAppointmentCount?}` | 200/201 ok | `667-712` |
| GET `/api/schedule/blocks/overlapping-count` | `startAt`, `endAt`, `doctorId?` | `{count}` | non-200 → 0 | `715-740` |
| DELETE `/api/schedule/blocks/{id}` | `doctorId?` | — | 200/204 ok | `743-763` |
| GET `/api/schedule/rules` | `locationId?` | `[RuleDto]` | 401 / other → localized messages | `setup_schedule_screen.dart:131-149` |
| PUT `/api/schedule/rules` | `locationId?`; body `[RuleDto]` | — | non-200 → `{message|error}` | `schedule_actions.dart:31-41` |
| PATCH `/api/schedule/valid-range` | `{validUntil, validFrom?}` | — | non-200 → `{message|error}` | `43-68` |
| GET `/api/schedule/validity-periods` | — | `[{validFrom, validUntil}]` | throw | `72-80` |
| GET `/api/schedule/date-specific` | `locationId?` | `[DateSpecificRuleDto]` | throw | `83-97` |
| POST `/api/schedule/date-specific` | `{startDate, endDate, startTime, endTime, slotMinutes, locationId?}` | `DateSpecificRuleDto` | non-200/201 → `{message|error}` | `101-126` |
| DELETE `/api/schedule/date-specific/{id}` | — | — | 200/204 ok | `129-136` |
| GET `/api/doctors/me` | — | `{profile:{timeZone, scheduleValidFrom?, scheduleValidUntil?, id|doctorId|doctorProfileId, address?, locationStreetAddress?}, contact, billing, settings, subscription?}` | non-200 throw | `profile_providers.dart:12-33`, `setup_schedule_screen.dart:160-187` |
| GET `/api/me/profile` | — | `MeProfile{timeZone,…}` | | `profile_providers.dart:36-49` |
| GET / POST `/api/doctors/me/locations` | body `DoctorLocationDto` | `[DoctorLocationDto]` / `DoctorLocationDto` | `{message|error}` | `doctor_location_actions.dart:10-36` |
| PATCH / DELETE `/api/doctors/me/locations/{id}` | body `DoctorLocationDto` | `DoctorLocationDto` / — | `{message|error}` | `39-63` |

`schedule_actions.dart:148-184` also defines `createScheduleBlock` / `deleteScheduleBlock` duplicates that nobody calls (all callers use the controller methods).

---

## 7. Models

**`CalendarEntry`** (`features/calendar/domain/calendar_models.dart:9-111`): `type: EntryType {appointment, freeSlot, blocked}`; `start, end: TimeOfDay` (practice TZ); `startAtUtc?, endAtUtc?: String`; appointment-only: `patientName?`, `isVideo` (derived: location contains "video"), `location` (`locationLabel` ?? `location` ?? ''), `reason` ('' default), `photoUrl?`, `appointmentId?`, `patientId?`, `status?` (raw string), `paymentStatus?` (raw), `locationId?`, `briefingStatus?`, `attachmentCount` (0); blocked-only: `blockId?`, `blockReason?`. Free slot: `location`, `locationId?`. `_intFromJson` accepts int/double only (string ids → null).

**Appointment status strings seen**: `REQUESTED, CONFIRMED, CANCELLED, COMPLETED, IN_PROGRESS, NO_SHOW` (`features/appointments/domain/appointment_models.dart:4-29`, enum `AppointmentStatus`); calendar UI branches on `COMPLETED`, `CANCELLED`, `IN_PROGRESS`, `CONFIRMED`. **Payment status**: `PAID, PENDING, FAILED, NOT_REQUIRED`. **Consultation type**: not a model — encoded as `isVideo` + `location` string (`'Video Consultation'` / `'Clinic Address'`). **Block types**: none; a block has only `startAt/endAt/reason`. Backend `type` strings: `FREE_SLOT`, `APPOINTMENT`, `BLOCKED` (`FREE_SLOT` inferred as the default branch — **UNVERIFIED** literal).

**`CalendarDayOccupancy`** `{freeSlots, appointments, blocked}` + `freeRatio?`. **`CalendarEntryLayoutSlot`** `{entry, columnIndex, columnCount}`. **`CalendarQuickBookIntent`** `{preferVideoConsultation=false}`.

**Schedule** (`state/schedule/schedule_models.dart`): `TimeSlot{start, end: TimeOfDay, slotDuration: Duration}` (duplicate dead copy in `features/schedule/domain/schedule_models.dart`, no importers); `RuleDto{weekday 1..7, startTime, endTime "HH:mm", slotMinutes, locationId?}`; `DateSpecificRuleDto{id?, startDate, endDate "YYYY-MM-DD", startTime, endTime, slotMinutes, locationId?}`; `ValidityPeriodDto{validFrom, validUntil}`; `ScheduleState{slots: Map<String weekdayName, List<TimeSlot>>, startDate?, endDate}`.

**`DoctorLocationDto`** (`state/locations/doctor_location_models.dart`): `id?`, `label` (required, '' default), `clinic?`, `address?`, `latitude?`, `longitude?`, `locationCountry?`, `locationRegion?`, `locationDistrict?`, `locationCity?`, `locationPostalCode?`, `locationStreetAddress?`, `isPrimary` (false). `toJson` omits nulls.

**`ClinicMember`** (used for staff): `doctorProfileId`, `displayName`, `membershipRole` (`DOCTOR|OWNER|CLINIC_ADMIN|…`); `MyClinicSummary`: `clinicId`, `timeZone` ('UTC' default), `address?` (`state/clinic/clinic_models.dart:9-41, 80-102`).

**`ClinicScheduleReturnInfo`** `{doctorProfileId, doctorDisplayName, clinicScheduleTimeZone, clinicStreetAddress?}`.

---

## 8. Platform / third-party → KMP

| Flutter | Used for | KMP / Compose equivalent | Custom work |
|---|---|---|---|
| `table_calendar ^3.1.3` | month picker with custom day builders, locale headers | **None mature in CMP** → custom `LazyVerticalGrid`/`Row` month grid (6×7, Monday start), swipe/chevron paging, custom day cell composable | Month grid, occupancy cell, dow header (localized), page animation |
| `timezone ^0.9.4` + `tz.initializeTimeZones()` | `TZDateTime` conversions | `kotlinx-datetime`: `Instant`, `TimeZone.of(iana)`, `LocalDateTime.toInstant(tz)`, `Instant.toLocalDateTime(tz)`, `Clock.System.now()`; `LocalTime` replaces `TimeOfDay` | Wrap in one `PracticeClock(tz)` helper; fallback to `TimeZone.UTC` on `IllegalTimeZoneException` |
| `iana_time_zone` | device IANA id (registration prefill) | `TimeZone.currentSystemDefault().id` | none |
| `intl` (`DateFormat('EEEEE', locale)`, `initializeDateFormatting`) | single-letter weekday names | `kotlinx-datetime` gives numbers only → own localized weekday/month tables (app already has `monthName`, `monday..sunday` keys) | small string tables per locale (en/uz/ru/uz-Cyrl) |
| `shared_preferences` | staff selection, active location id | `multiplatform-settings` or DataStore | none |
| `flutter_map` + Google geocoding (`location_picker_widget.dart`) | location editor map | Out of this area's core; expect/actual map (Google Maps SDK Android / MapKit iOS) or a lighter "address + coordinates via geocoding only" first version | see LOC-2 |
| `SegmentedButton`, `FilterChip`, `showTimePicker`, `showDatePicker`, `DropdownButtonFormField`, `AlertDialog`, `DraggableScrollableSheet` | controls | Material3 `SingleChoiceSegmentedButtonRow`, `FilterChip`, `TimePicker`, `DatePicker`, `ExposedDropdownMenu`, `AlertDialog`, `ModalBottomSheet` | none |
| `NetworkImage` avatars | patient photos | Coil 3 (multiplatform) | none |
| Riverpod providers | state | ViewModel + `StateFlow`; one `CalendarRepository` with day cache | see §10 |

Must be custom-built: month grid, N-day time grid (hour gutter, absolute-positioned blocks, free-slot hit test, now-line, overlap columns), occupancy shading, consecutive-slot chaining (pure Kotlin, port tests), block dialog, slot details panel.

---

## 9. Quirks, bugs, dead code, tech debt — do **not** copy

1. **Dead code**: 214-line commented-out legacy `CalendarController` at top of `calendar_controller.dart:1-214`; `features/schedule/domain/schedule_models.dart` (unused duplicate `TimeSlot`); `schedule_actions.dart:148-184` block helpers (unused); `calendar_controller.dart:572-573 _durationMinutes` (unused); in `calendar_screen.dart` `_latinUzbekForDisplay` (46-50), `_timeRangesOverlap` (1307-1318), `_pickDuration` (3550-3568), `_editText` (3570-3598) are defined but never called; duplicate `import 'package:intl/intl.dart'` (42-43) and duplicate `app_colors` import in `setup_schedule_screen.dart:20,23`; `TimezoneDebugScreen` unreachable.
2. **Device-time leaks** (§4.3 item 5): now-line, `isToday`, default focused day, date-picker bounds, schedule default end date.
3. **Naive-timestamp handling contradicts its comment** (`calendar_models.dart:125-127`), inconsistent with `consecutive_slot_range.dart:8-21`.
4. **Redundant fetches**: booking/cancel/change each trigger 2–3 GETs of the same day (controller reload + `refreshCalendarDay` + `onSavedSuccessfully`); filter Apply re-fetches although filters are client-side (`1298-1303`); `nextUpcomingAppointmentProvider` walks up to 14 days with one GET each; quick-book walks up to 30 days sequentially with a loading spinner per day (`717-745`).
5. **Generic error swallowing**: booking shows "range unavailable" for every failure (`4108-4120`); schedule save shows "somethingWentWrong" and discards the backend message (`650-661`); `loadMonth` swallows per-day errors (`390`).
6. **Hard-coded English sent to backend**: `'Video Consultation'`, `'Clinic Address'`, `'Check Up'` (`4062-4069`, `calendar_controller.dart:493,536`); video detection = `location.contains('video')` (`calendar_models.dart:176`). Backend should carry an explicit `isVideo`/`consultationType` instead.
7. **`ScheduleState.copyWith` cannot clear `startDate`** (`schedule_controller.dart:12-20`) → `setStartDate(null)` is a no-op; `scheduleProvider` is global and not autoDispose, so stale editor state can leak between locations/sessions until the next `replaceWithBackendRules`.
8. **Location scoping is half-done**: `RuleDto.locationId` is applied uniformly from the screen-level selector (`toRuleDtos(locationId:)`), while `PUT ?locationId=` is also sent — two ways to say the same thing (**UNVERIFIED** which one backend honours). Validity range is global, not per location.
9. **Profile id guessing** `profile['id'] ?? profile['doctorId'] ?? profile['doctorProfileId']` (`176-182`, `270-274`, `setup_schedule_screen.dart:63-66`) — backend contract unclear (**UNVERIFIED**).
10. **God widget**: 5.9k-line file mixing screen, list, month panel, details panel, booking, block dialog, change-slot dialog, AI notes tabs, patient picker. Panel re-derives everything from `widget.entry` + cache (`_liveEntry`) each build.
11. **`l10n.translate(key) ?? 'fallback'`** everywhere (violates CLAUDE.md rule); some keys resolved with a `t(key, fallback)` helper that treats `value == key` as missing (`4282-4286`).
12. **`debugPrint` noise** in hot paths: every UTC→TimeOfDay conversion logs twice (`calendar_models.dart:116-118`), `getNowInTimezone` logs on every call (`timezone_utils.dart:19-33`) — these run per entry per rebuild.
13. **Staff grids all share one `_loadingDay` spinner and one `_selectedEntry`**, and colleague caches live in widget state (lost on tab switch) rather than in a provider.
14. **Selection identity** uses `identical(e, selected)` (`calendar_week_grid_view.dart:420`, `calendar_screen.dart:2315`), so a refresh (new object instances) silently drops the highlight while the panel stays open.
15. **Block "entire day" ends at 23:59** not 24:00 (`1403-1407`) — last minute stays bookable if a slot ends at 00:00 (edge case).
16. **Change-slot dialog** uses `FutureBuilder` inside a `StatefulBuilder` and re-runs `_loadAvailableSlots` on every dialog rebuild (`3732-3733`).
17. Two registrations of `/create/schedule` (root + shell routers) with identical builders.

---

## 10. Proposed tickets

Sizes: S ≈ ½–1 day, M ≈ 2–3 days, L ≈ 4–6 days. Order = build order. "Spec refs" point to sections above.

### TZ-1 · Practice-timezone core [S]
Route — · Spec refs §4.1–4.3 · API: GET `/api/doctors/me` (`profile.timeZone`), GET `/api/me/profile`
Build: `PracticeClock(tz: TimeZone)` in shared Kotlin: `todayIn()`, `nowIn()`, `instantToLocal(iso)`, `localToUtcIso(date, time)`, `LocalDate`/`LocalTime` helpers; parse ISO with/without `Z` as UTC; fallback `TimeZone.UTC` on unknown id; single source `practiceTimeZoneFlow` (doctor profile → clinic staff `/api/me/profile`); device-TZ mismatch flag (`TimeZone.currentSystemDefault()` offset vs practice offset at now).
Done when: unit tests for Asia/Tashkent & America/New_York cover today/past boundaries, naive vs `Z` parsing, DST day; no `Clock.System.now()` used for display outside this class.
Depends: profile/auth tickets from other areas.
Fix: §9 items 2, 3, 12 (no logging in hot path).

### TZ-2 · Timezone selection & detection utilities [S]
Route — · Spec refs §4.1 · API: —
Build: `commonIanaTimeZones` list (copy from `timezone_service.dart:19-67`), `detectDeviceTimeZone()` = `TimeZone.currentSystemDefault().id`, searchable dropdown composable (used by profile/registration areas).
Done when: dropdown filters by substring, pre-selects detected zone, accepts an unknown current value by prepending it.
Depends: TZ-1.
Fix: drop `iana_time_zone`; do not port `TimezoneDebugScreen`.

### CAL-1 · Calendar domain models + day repository [M]
Route — · Spec refs §5, §6, §7 · API: GET `/api/calendar?day&doctorId`
Build: `CalendarEntry` (sealed: `FreeSlot`, `Appointment`, `Blocked`) with `startUtc: Instant`, `endUtc: Instant`, derived `startLocal/endLocal: LocalTime` via TZ-1; tolerant JSON parsing (array or `content|data|entries`, skip rows without startAt/endAt, numeric ids only); `CalendarRepository` with `StateFlow<Map<LocalDate, List<CalendarEntry>>>`, per-day 60 s throttle, `loadDay(force)`, `loadMonth` (batched 4), `previewDay(doctorId)` (no cache write), `resourceDoctorId` scoping, clear on token change; `AppointmentStatus`, `PaymentStatus` enums with `UNKNOWN`.
Done when: parsing tests from fixtures (3 entry types, wrapped array, bad row), throttle test, repository used by today/next-appointment providers (CAL-2).
Depends: TZ-1, shared ApiClient.
Fix: §9 items 1 (no legacy code), 6 (explicit `isVideo` + `location` kept but video decided once at parse), 14 (stable identity = `appointmentId`/`blockId`/`startUtc+locationId`).

### CAL-2 · Today / next-appointment selectors + invalidation hub [S]
Route — · Spec refs §5 invalidation graph · API: reuse CAL-1
Build: `todayAppointments(tz)` and `nextUpcomingAppointment(tz, lookahead=14)` derived from the repository cache; `AppointmentChangeBus.notify(day?)` that force-refreshes today (+ given day) and emits an event other features (home analytics, clinic finance) subscribe to; FCM payload hook mirroring `appointmentCalendarPushTypes`.
Done when: one booking causes exactly one GET for the affected day; `pickNextUpcomingAppointment` logic ported with tests (in-progress wins, cancelled/completed skipped).
Depends: CAL-1.
Fix: §9 item 4 (single refresh path; lookahead fetched lazily, stop at first hit).

### CAL-3 · Month picker with occupancy shading [M]
Route `/calendar` (tab) · Spec refs §1.2, §1.3 "Month cell occupancy" · API: GET `/api/calendar` via `loadMonth`
Build: custom Compose month grid (Mon-first, 6 rows, chevrons + horizontal swipe, 2020–2030 bounds), localized month title and 1-letter/3-letter weekday headers, `OccupancyDayCell` with the exact colour function from `calendar_day_occupancy.dart:47-70`, selected/today styling, legend text, "Update schedule" card (`no free slots && no appointments && !loading`) with "Go To Schedule" action; compact and regular variants.
Done when: visual parity on phone & tablet; occupancy colours unit-tested for ratios 1, 0.75, 0.5, 0.25, 0; page change triggers `loadMonth`.
Depends: CAL-1, TZ-1.
Fix: `isToday` uses practice TZ (§9 item 2).

### CAL-4 · Day entries list + calendar screen shell (phone layout) [M]
Route `/calendar` · Spec refs §1.1 (single pane), §1.3 "Day list", §1.5, §1.11 · API: GET `/api/calendar`
Build: `CalendarViewModel` (selected day, anchor, dayViewCount, filters, selected entry, loading, tz-hint dismissed); phone layout = TZ-mismatch banner (dismissable) → header (title, date label, Block & Filter buttons) → CAL-3 month panel → `DayEntriesList` rows (free / blocked / appointment cards incl. location chip, duration badge, completed strike-through, photo avatar via Coil); filter dialog (3 toggles, client-side only); lifecycle reloads (tab shown, app resumed ≥ 5 s); go-to-appointment and quick-book intents (CAL-9 wires them).
Done when: selecting a day loads and lists entries; filters hide types without refetch; banner appears only when offsets differ.
Depends: CAL-1, CAL-3, TZ-1.
Fix: §9 items 4 (no refetch on filter), 11 (all strings in l10n), 14.

### CAL-5 · N-day time grid (tablet / wide layout) [L]
Route `/calendar` (wide) · Spec refs §1.1 (two pane), §1.3 "Week grid", §1.9 · API: reuse
Build: `WeekGrid` composable: hour gutter (56 dp/h, auto bounds 07–20 expanded to content), day header cells, per-column `Layout` placing appointment/blocked blocks from a pure-Kotlin port of `layoutOverlappingCalendarEntries`, free-slot tap layer (`pointerInput` Y → slot), selection highlight, now-line (practice TZ), completed strike style, 1–7 day segmented selector, two-pane screen (grid left, toolbar + month/details right with crossfade), `shrinkWrap`/max-height mode for stacked staff grids.
Done when: overlap layout unit tests (3 cases: disjoint, pair, triple chain merge), tap on free area selects the right slot at 30- and 15-min grains, grid scrolls vertically and keeps header fixed.
Depends: CAL-4.
Fix: §9 items 2, 14.

### CAL-6 · Slot details panel: appointment view & actions [L]
Route `/calendar` (pane or full screen) · Spec refs §1.7 · API: DELETE `/api/appointments/{id}`, PUT `/api/appointments/{id}/change`, POST `/api/appointments/{id}/notify-payment-reminder`
Build: panel for `Appointment` entries: header (title, status chip, payment chip for video, date/time, duration), patient card → patient profile, read-only place card, primary CTA state machine (Open Summary / Continue / Start / Appointment Ended; video window −5 min..end+1 h; in-clinic same-day exception), Cancel with confirm, Change-slot dialog (date picker from today, free-slot list via repository `loadDay`/`previewDay`, PUT with slot duration), Adjust-duration dropdown (port `scheduleGrainMinutesGuess`, `shorteningEndWallsGrainUtc`, `extendEndWallsChainFromUtcBoundary`, `appointmentSlotMinutesUtcStartWallEnd`), Encourage-payment button, "past appointments cannot be changed" note, Discard with dirty check. AI-documentation tabs are **out of scope here** (owned by the appointments/notes area) — expose a slot for them.
Done when: each CTA state covered by a ViewModel test using a fake clock in Asia/Tashkent; cancel/change/adjust each trigger one refresh via CAL-2.
Depends: CAL-4, CAL-2, TZ-1, patients-profile route from other area.
Fix: §9 items 4, 5 (surface backend `message`), 16 (load slots once per date).

### CAL-7 · Booking a free slot (multi-slot range) [L]
Route `/calendar` · Spec refs §1.6, §1.10 · API: POST `/api/schedule/book`
Build: port `consecutive_slot_range.kt` (chain on `Instant` + `locationId`, `bookingSlotMinutesForRange`) with the 5 existing tests + DST case; free-slot panel: past-day guard, patient picker bottom sheet (search by name/id, "new patient" hook), place selector (Clinic / Video), start-slot dropdown (same venue), end-time dropdown from chain, reason (default from l10n, not hard-coded), chronic-disease warning hook, Save → repository `book(...)` (body exactly as §6, `locationId` only for in-person, `resourceDoctorId` for colleagues), 409/400 → refresh + specific message, success snackbar, dirty/discard.
Done when: booking 2 consecutive 30-min slots sends `slotMinutes=60`; booking across a venue boundary is impossible in UI; past-day slot cannot be saved; tests green.
Depends: CAL-6 (shared panel scaffold), patients list API from other area.
Fix: §9 items 5, 6 (send `isVideo` and let backend derive label; keep `location` for compatibility).

### CAL-8 · Blocks: create dialog, blocked details, unblock [M]
Route `/calendar` · Spec refs §1.8 · API: POST `/api/schedule/blocks`, GET `/api/schedule/blocks/overlapping-count`, DELETE `/api/schedule/blocks/{id}`
Build: Block-time dialog (entire day / time range / date range, reason default "Emergency" from l10n, cancel-overlapping checkbox default on, debounced overlapping-count preview, validation messages), success snackbar with cancelled count, refresh of all days in range; blocked-entry panel with reason card and Remove block (confirm).
Done when: entire-day block spans 00:00–24:00 of the practice-TZ day (sent as UTC), count preview updates on every field change, cancellations trigger CAL-2 bus.
Depends: CAL-6 scaffold, TZ-1.
Fix: §9 item 15.

### CAL-9 · Deep-link intents: push go-to-appointment & quick-book [S]
Route `/calendar` · Spec refs §1.2 · API: reuse
Build: `CalendarIntent` channel (`GoToAppointment(id, day)`, `QuickBook(preferVideo)`) consumed once by the ViewModel: switch day, load, select matching appointment; quick-book scans days lazily (stop at first free slot ≥ now, max 30 days) with one spinner, selects it and pre-sets place=Video.
Done when: opening a push while on another tab lands on the right day with the appointment selected; quick-book with no slots shows "no slots" once.
Depends: CAL-4, CAL-7, notifications area for payload parsing.
Fix: §9 item 4 (no per-day spinner flicker).

### CAL-10 · Staff calendars (multi-doctor) + clinic doctor schedule screen [L]
Route `/calendar`, `/clinic/doctors/{id}/schedule` · Spec refs §1.12 · API: GET `/api/calendar?doctorId`, plus `resourceDoctorId`/`doctorId` on book/block endpoints
Build: staff chip bar (roles DOCTOR/OWNER, palette colours by roster index, own always on, min 1, persisted per doctor+clinic via multiplatform-settings), per-doctor repositories/caches (`previewDay`) held in the ViewModel, stacked grids/lists with coloured headers, colleague TZ = clinic TZ, panel context (`clinicDoctorProfileId`, display name, venue label) so CAL-7/CAL-8 act on behalf of the colleague; standalone `ClinicDoctorScheduleScreen` for receptionists (month + day list + details, isolated repository instance with `resourceDoctorId`, always force refresh, return-info args).
Done when: toggling a colleague fetches only their visible days; booking for a colleague sends `resourceDoctorId`; receptionist screen works without a doctor profile (uses clinic TZ).
Depends: CAL-5, CAL-7, CAL-8, clinic workspace tickets (members/selected clinic).
Fix: §9 item 13 (caches in ViewModel, per-doctor loading state).

### SCHED-1 · Schedule models, repository & validation [S]
Route — · Spec refs §2.1–2.3, §7 · API: GET/PUT `/api/schedule/rules[?locationId]`, PATCH `/api/schedule/valid-range`, GET `/api/schedule/validity-periods`, GET/POST/DELETE `/api/schedule/date-specific`
Build: `RuleDto`, `DateSpecificRuleDto`, `ValidityPeriodDto`, `WeeklySchedule` (Map<DayOfWeek, List<TimeRange(start, end, slotMinutes)>>, validFrom?, validUntil); pure validators: end > start, no intra-day overlap, validity-period conflict rule (before / after / fully contained); `ScheduleRepository.load(locationId)`, `save(locationId)` (PUT then PATCH), date-specific CRUD, error-message extraction (`message|error`).
Done when: validator tests for the three allowed placements + overlap rejection; `toRuleDtos` round-trips `replaceWithBackendRules`.
Depends: ApiClient.
Fix: §9 items 7 (nullable `validFrom` can be cleared), 8 (one location-scoping mechanism — decide with backend, UNVERIFIED).

### SCHED-2 · Schedule setup screen [L]
Route `/create/schedule` (keep name; entry from calendar "Go To Schedule" and profile) · Spec refs §2.2 · API: SCHED-1 + GET `/api/doctors/me` (scheduleValidFrom/Until) + LOC-1
Build: location selector (+ Manage), existing-period chips, start/end date pickers (bounds as §2.2, practice-TZ today), expansion section (add via 2 dates + 2 times + duration → POST immediately; delete), 7 day cards (enable, expand, ranges with 24 h time pickers and duration sheet 10/15/20/30/45/60, remove, copy from previous / another day), 1- or 2-column layout at 720 dp, sticky "Complete" save with backend error surfaced.
Done when: load → edit → save round-trip against staging; overlap errors shown with the backend text; active location persisted per doctor.
Depends: SCHED-1, LOC-1, TZ-1.
Fix: §9 items 5, 7, 17 (single route registration).

### LOC-1 · Doctor locations repository + list/edit (no map) [M]
Route `/profile/locations` (currently pushed from schedule screen) · Spec refs §3 · API: GET/POST `/api/doctors/me/locations`, PATCH/DELETE `/api/doctors/me/locations/{id}`
Build: `DoctorLocationDto` (+ null-omitting serialization), `LocationsRepository` with `StateFlow<List<DoctorLocationDto>>` (shared by schedule screen and shell "active location" label), list screen with cards (label, Primary badge, clinic, address, city/region/country), FAB add, edit/delete with confirm, editor dialog with label (required), clinic, street address text, primary switch.
Done when: CRUD round-trips; schedule screen and shell label observe the same flow; delete asks for confirmation.
Depends: ApiClient.
Fix: no duplicate local lists (§3).

### LOC-2 · Map picker & geocoding for locations [L]
Route `/profile/locations` (editor) · Spec refs §3 · API: Google Geocoding (external, key via build config)
Build: expect/actual map composable (Google Maps Compose on Android, MapKit via UIKitView on iOS), tap/drag pin → reverse geocode into country/region/district/city/postal/street; address search → forward geocode; fields editable after geocoding.
Done when: picking a point fills the structured address and lat/lng; editor saves them via LOC-1.
Depends: LOC-1, profile-area geocoding service ticket.
Fix: isolate the third-party map behind an interface so the first release can ship LOC-1 without it.
