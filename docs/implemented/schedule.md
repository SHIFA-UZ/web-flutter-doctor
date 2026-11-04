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
