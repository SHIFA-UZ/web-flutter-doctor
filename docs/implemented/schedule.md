# Schedule and locations (area 04, SCHED / LOC)

## LOC-1 · Locations

Built 2026-11-04. Flow `04/13-locations-management.md`; spec 03 §3, §6. Reached from Profile → **Locations** ("Where
you see patients", the flow's target entry; SCHED-2 will link here too). No board screen: built from the kit (section
cards, chips, the profile form).

- **Data** (`data/locations/LocationsRepository.kt`): one `StateFlow` of the doctor's locations for every screen
  (Flutter kept three copies). `GET /doctors/me/locations`, `POST`, `PATCH /{id}`, `DELETE /{id}`; after a save or
  delete the list is read again (a new primary demotes the old one; deleting the primary promotes another). Cleared on
  sign-out. Reads and writes are numbered so an older read never overwrites a newer write, and a write is applied to the
  list at once with the backend's rules; the screen then reads the list again, and a failed read shows with Retry. `DoctorLocation` moved to `data/model/LocationModels.kt` with every address field; the services screen's
  price scopes collect the shared list (its own fetch is gone).
- **List** (`ui/schedule/LocationsScreen.kt`): a card per location: label with the Primary badge, clinic, street,
  "City, Region, Country"; tap to edit; skeleton, error with Retry, "No locations yet…"; pull to refresh; a failed
  refresh over a shown list says so above it with Retry; Add location at the bottom.
- **Editor** (the flow's target: a pushed form, not Flutter's dialog): Label (required, inline "Label is required",
  hint "e.g. Main Clinic"), Clinic, Street Address, City, Set as primary. The primary can't be switched off ("Patients
  see the primary location first. Make another one primary to change it."); the first location ever is primary. Blank
  fields go as "" so the backend clears them (a missing key keeps the old value); `address` mirrors the street. The
  form stays open with a spinner while saving; errors show in it. Back with changes asks first. Delete location →
  confirmation naming it → "Location deleted"; a 409 (only one, has rules, has appointments; BE-34) says so in one
  message.
- **Fixed on the way:** `ProfileScreen` rebuilt its actions positionally, so the new Locations action shifted Reports
  and Sign Out; they are named now.
- **Not built:** map pin and coordinates (LOC-2), region / country / district / postal fields (they come from
  geocoding, BE-15), "Working hours at this location" (SCHED-2), the shell's active-location label (no shell header
  shows it yet).
- **Verified on dev (2026-11-04):** the list, adding "QA Evening…" and deleting it (dev back to its one location).
- **Tests:** `LocationsRepositoryTest` (2: a save counts when the read after it fails; a slow read can't bring a deleted
  location back), `LocationsViewModelTest` (4: + a failed refresh keeps the list and says so; label required and blanks sent as "", a new location posts and the first is
  primary, a refused delete with one message and Back asking), `DemoBackendTest` (+1: in-memory locations, primary
  moves, the last one refused), `ServicesViewModelTest` (uses the shared list), `ScheduleScreenshotTest` (2:
  `Schedule_locations`, `Schedule_locationEditor`), `ProfileScreenshotTest` (`Profile_root`, `Profile_incomplete`
  re-recorded with the Locations row).

## SCHED-1 · Schedule data

Built 2026-11-04. Spec 03 §2.1–2.3, §6; flows `04/10`, `04/11`. No screen (SCHED-2 builds it).

- **Models** (`data/model/ScheduleModels.kt`): `RuleDto {weekday 1–7, startTime, endTime, slotMinutes, locationId}`,
  `DateSpecificRuleDto`, `ValidityPeriodDto`; all checked against dev (`GET /schedule/rules?locationId=2` answers Monday
  to Friday 09:00–17:00 in 30-minute slots, one period 2026-09-30 – 2027-10-30).
- **Weekly template** (`data/schedule/ScheduleRules.kt`): `WeeklySchedule` (weekday → ranges in start order; a day with
  none is off), `of(rules)` (a row with a bad time or weekday is skipped, times with seconds accepted) and
  `toRules(locationId)`; they round-trip. Times must parse whole; seconds are kept as read. `SLOT_SIZES`
  10/15/20/30/45/60.
- **Validators:** `rangeProblem` (the backend's checks: end after start, no range crosses midnight; slots of at least
  5 minutes; at most 200 slots; no overlap with the day's others, touching is fine) and `periodFits` (the backend's rule: inside an existing period changes nothing, otherwise no
  overlap with any).
- **Repository** (`ScheduleRepository`): rules per location, always for one (`?locationId=` and the rows'
  `locationId`: one scope, §9 item 8; unscoped rules saved back would all move to the primary); rows are decoded one
  by one (`data/api/Rows.kt`, shared with the calendar), so one bad row is skipped, not the list; `saveRules` (`PUT`, the whole template), `periods`, `addPeriod` (`PATCH /schedule/valid-range`), date-
  specific list / add / delete.
- **Backend behaviour learned (read in its controller):** `valid-range` *adds* a validity period (one already inside a
  period is a no-op; an overlap is a 400). It never changes or shortens one, and a missing `validFrom` makes a one-day
  period ending on `validUntil`, so the app always sends `validFrom`; no start date means from today in the practice
  zone (§9 item 7).
- **Not here:** the screen and its error texts (SCHED-2); `PUT /schedule/date-specific/{id}` (exists on dev now) waits
  for the editor that uses it.
- **Also:** the ViewModel tests cancel and join their ViewModels before resetting Main (`stopAll`), ending the "Main
  used concurrently" flake.
- **Tests:** `ScheduleRulesTest` (4: the round trip with sorting and skipped rows; range rules; the three allowed period
  placements and two refusals; the repository's location scope and request bodies), `DemoBackendTest` (+1).

## SCHED-2 · Working hours

Built 2026-11-04. Flows `04/10-availability-setup.md`, `04/11-date-specific-hours.md`, `04/12` (slot size and time
pickers); spec 03 §2.2–2.3. The flow's target layout (week overview + a sheet per day), not Flutter's seven
expandable cards. Reached from Profile → Schedule (`ShellRoute.Schedule`, the placeholder is gone); the calendar's
"Go to schedule" card is not built yet.

- **Screen** (`ui/schedule/ScheduleScreen.kt`, "Working hours"): location chips when there are two or more (one: its
  name), Manage → Locations; none → "Add at least one practice location…" with Add location. The location is the one
  picked this session, else the one last used on this phone (per doctor, `ActiveLocationStorage`), else the primary,
  else the first.
- **Week:** seven rows, Monday first: "09:00 – 13:00, 14:00 – 18:00 · 30 min" or "Off". A row opens the **day sheet**:
  its ranges (remove each; "No slot fits in this range." when a range is shorter than its slot), Add hours (Start /
  End in quarter hours, 24-hour labels, ending by 23:45 since the backend reads a time of day; slot size chips
  10/15/20/30/45 min/1 h), checked like the backend with inline messages (end after start, overlap, slots ≥ 5 min,
  ≤ 200 slots), Copy from (the days with hours), Apply to Mon–Fri.
- **Bookable period:** "Bookable until {date}" (the latest period's end) or "No bookable period yet" (then Save adds
  60 days from today with the hours, so a first setup is bookable); Add a period starts the next one the day after the
  last (or today) for 60 days, never past a year from today; From / To from the calendar's month inline (today
  to a year ahead); a period across an existing one says "A new period must be before, after or inside an existing
  one." and Save stays off.
- **Extra hours on dates** (04/11): the list (date or span, hours, slot) with delete, and Add in a sheet (From / To,
  hours, slot); written at once, not with Save.
- **Save** (sticky): off until the rules, periods and extra hours are all read (a failed read shows the error with
  Retry and never offers to save an empty template, Flutter's bug) and while nothing changed; nothing can be edited
  while it runs. Reads are numbered, so switching A → B → A can't end with the first A's answer. `PUT` the location's rules if they changed, then `PATCH
  valid-range` if a period was added; a failed period after saved hours says "Hours saved, but the new period couldn't
  be added." The calendar is marked stale as soon as the hours are saved (`CalendarRepository.invalidateAll()`:
  answers already on their way are dropped too); success closes with "Schedule saved!". Back or switching the
  location with unsaved edits asks first.
- **Not built:** the shell's active-location label, the calendar's "Update schedule" card and its first-run hint, editing a date-specific entry in place (`PUT
  /schedule/date-specific/{id}` exists; delete and add again for now).
- **Verified on dev (2026-11-04):** the week (Monday to Friday 09:00–17:00), "Bookable until 30 October 2027", the
  Monday sheet; left without saving (dev rules unchanged).
- **Tests:** `ScheduleViewModelTest` (11: + the A → B → A race, no edits while saving, the first setup's 60 days, the
  one-year limit and the remembered location, extra hours that can't be read; primary first and Save off until the rules are read, a failed read saves
  nothing; day ranges checked, copy and Mon–Fri; save with the period after the last one and the notice; a failed
  period keeps the hours saved; switching location asks and a crossing period is refused; extra hours written at once
  and deleted), `ScheduleScreenshotTest` (+1: `Schedule_week`).
