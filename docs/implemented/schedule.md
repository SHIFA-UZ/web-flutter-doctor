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
