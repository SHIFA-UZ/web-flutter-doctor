# Calendar & schedule (M3)

What is built for the calendar, availability and locations area. Flows `docs/ux-flows/04-calendar-schedule/`, spec
`docs/kmp-spec/03-calendar-schedule.md`, board `docs/design/boards/DaCalendar.dc.html`.

## TZ-1 · Practice clock

Built 2026-11-04 (the clock itself came with CORE-09). Spec `03 §4`; flow `04` README §4.2.

- **`utils/PracticeClock`:** now, today, backend instant → practice wall clock, wall clock → UTC instant (DST-correct
  per day), start of a day, the device-mismatch flag (offsets now, flow 04/03), and backend timestamps without a zone
  read as UTC (Flutter read them as device time, 03 §4.3 item 6). An empty or unknown zone falls back to UTC and says
  so (`zoneMissing`).
- **`utils/DateFormat` (new):** `displayDate` ("05.12.2026", moved here from registration so every area shares it),
  `displayTime` ("09:05"), `displayMinutes` (minutes after midnight, wrapping, for slot labels). The timezone change
  warning uses it.
- **Tests:** `PracticeClockTest` (8: practice today, 09:00 across a DST switch, the spring-forward gap, the autumn
  repeated hour and the 25-hour day, the mismatch flag with DST, UTC fallback, zone-less timestamps, formats).

## TZ-2 · Timezone detection and list

Nothing new: the device-zone detection (`TimeZones.device`) and the shared list (`TimeZonePickerSheet`, city, zone,
offset, offset search) were built for registration and PROF-06 and are recorded in `profile.md`.

## CAL-1 · Calendar models and day cache

Built 2026-11-04. Spec `03 §5–7`; endpoint `GET /calendar?day=YYYY-MM-DD[&doctorId=]` (checked against dev).

- **Models** (`data/model/CalendarModels.kt`): `CalendarEntryDto` (every field optional, as dev sends them) and the
  sealed `CalendarEntry`: `FreeSlot`, `Appointment` (id, patient, photo, status, payment status, reason, booked
  location, signature fields, `briefingStatus`, `attachmentCount`; `isVideo` and `cancelled`), `Blocked` (id,
  reason). Times stay UTC instants; screens show them through `PracticeClock`. Any type other than APPOINTMENT or
  BLOCKED is a free slot (03 §6); a row without readable times, with end ≤ start, or an appointment without an id is
  skipped and logged, never the whole day. `isVideo` reads the booked location text until the backend sends a flag
  (BE-09, marked `ponytail:`). `DayOccupancy` counts free, booked (not cancelled) and blocked rows for the month
  shading; the free share counts blocked rows too (a fully blocked day is 0).
- **`CalendarRepository`** (`data/calendar/`, shared `calendarRepository`): days cached per date, doctor and location
  (`DayKey`, all three in the query), observed through `state` (`CalendarCache.days`); read again only after 60 s
  unless forced; reads of a day already in flight share its request; `month(dates)` reads the missing or stale days
  four at a time; `previewDay` reads a colleague's day without caching; `invalidate` for mutations elsewhere; `clear`
  on sign-out (wired in `App`). The cache is one value updated atomically: an answer is kept only if it is the newest
  request for its day and from the same session. The answer may be an array or wrapped in `content` / `data` /
  `entries`.
- **Demo backend:** any day gets 30-minute slots 09:00–17:00 (Tashkent), lunch blocked 13:00–14:00, and on even dates
  a video and a clinic appointment.
- **Tests:** `CalendarRepositoryTest` (6: row parsing and sorting, wrapped answer and preview, throttle / force /
  invalidate, month batch and sign-out, older answer vs forced / late answer after sign-out, shared in-flight reads
  and location key), `DemoBackendTest` (+1).

## CAL-2 · Today, next appointment and the change bus

Built 2026-11-04. Spec `03 §5` (providers and the invalidation graph).

- **Selectors** (`data/calendar/CalendarSelectors.kt`, on the shared repository):
  - `todayAppointments(clock)`: the practice day's appointments in time order, cancelled ones left out (reads the day
    if it isn't cached).
  - `nextAppointment(clock, horizonDays = 14)`: one in progress now, else the next one today, else the first in the
    next 14 days, read day by day (from cache when fresh) and stopping at the first hit; completed, cancelled and
    no-show ones don't count. Returns a failure when a day before the hit can't be read.
- **`AppointmentChanges` and `changed(clock, days…)`:** after a booking, cancel, move, a block that cancels some, or a
  push about one, today and the touched days are read again once each (forced, deduplicated: Flutter read the same
  day two or three times per booking), then one event with those days goes to every other listener (home,
  analytics) instead of each chaining its own reloads. Other cached copies of those dates (another location or
  colleague) are marked stale; the event is never dropped (suspending emit). `PracticeClock.nowInstant()` was added for the comparisons.
- **Tests:** `CalendarSelectorsTest` (6: today without cancelled, in progress / next today / next days with the cache
  reused, nothing within the horizon, one read per touched day plus the event, a failed day, location copies stale).

## CAL-3 · Month picker

Built 2026-11-04. Flow `04/01` §3.4, §4; board `DaCalendar.dc.html` (month grid, `dr-month` / `dr-day` in
`shifa-ds.css`).

- **`ui/calendar/MonthPicker.kt`:** the month card: previous / next month with the localised month title, one letter
  per weekday (each weekday's own name, Monday first in every language), and the days in a Monday-first grid padded to
  full weeks (`monthCells`).
- **Day cells (the board, which differs from the flow's grey ramp):** the booked share of the bookable time
  (appointments over appointments plus free slots) shades the day: none → no fill, under two thirds → teal 15 %, more
  → teal 25 %; a day blocked from midnight (practice zone, `DayOccupancy.fullDayBlock`) → the error tint; a day not
  read yet is plain (`dayShade`). Selected = filled teal
  with white bold number; today = teal ring and strong-teal bold number; past days tertiary; a charcoal dot under a
  future day that has appointments. Each cell reads its date, its appointment count and "Day blocked" to screen readers and says when it is selected.
  Months page with the chevrons or a horizontal swipe, within January 2020 – December 2030.
- **Tests:** `MonthPickerTest` (2: shading rules, Monday-first padding for Nov 2026 and Feb 2027),
  `CalendarScreenshotTest` (`Calendar_monthPicker`).

## CAL-4 · Calendar screen (phone)

Built 2026-11-04. Flows `04/01-calendar-tab.md`, `04/02-day-agenda-item.md`, `04/03-timezone-mismatch-notice.md`,
`04/15-refresh-live-updates.md`; board `DaCalendar.dc.html` (day view, compact rows and "Show details").

- **Screen** (`ui/calendar/CalendarScreen.kt`, the doctor's Calendar tab; clinic staff keep their stand-in until
  CAL-10): one scrolling page, clear of the floating tab bar: the timezone-mismatch banner, the header ("Calendar",
  the selected date in the app language, Filter), the month picker (CAL-3, shaded from the cache), and the selected
  day's agenda.
- **Agenda rows** (`AgendaRow`): free slot (brand ring with +, "Free slot", location), appointment (photo or the brand
  fallback per the project rule, the board draws initials; patient name or "Patient"; "Video Call" or the location;
  cancelled ones dimmed and struck through), block (error tint and border, reason or "Blocked", "All day" for a whole
  day). Compact by default; "Show details" adds the location chip (info tint for video), the status chip (Confirmed,
  Complete, Cancelled, In progress, Requested, No-show, else the raw code), the payment chip for video, the duration
  badge (over 5 minutes) and the reason; the compact line reads place · status · payment. The right column is capped so
  a long reason never hides the name. Rows are tappable; their panels come
  with CAL-6 / CAL-7 / CAL-8.
- **States:** a 4-row skeleton while the day is read the first time; "Couldn't load this day" with the request id and
  Retry (forced); "No items for this day"; "Hidden by filters · Reset" when the filters hide everything.
- **Filter** sheet: Appointments, Free slots, Blocked on a draft that Apply commits (dismissing drops it); the button
  turns primary while a filter is on. A Refresh button forces the day.
- **Timezone mismatch** (04/03): shown while the phone's offset differs from the practice zone's now and the doctor
  hasn't dismissed it this session; the text names the practice zone and a second line shows both zones with offsets.
  Not shown when the practice zone is missing.
- **Data and refresh:** the screen observes the shared calendar cache and the profile's zone (when it arrives or
  changes, "today" follows unless another day was picked, and the cache is read again); it reads the selected day on open, on selecting a day, and on every return to the app (`ON_RESUME`,
  within the 60 s throttle), and prefetches the shown month for the shading. Changes made elsewhere arrive through the
  shared cache.
- **Not built:** the "Update schedule" / "Day off" card (SCHED), Block time in the header (CAL-8), pull-to-refresh (the
  resume read covers it), a "Today" button, push and quick-book intents (CAL-9).
- **Verified on the emulator against dev (2026-11-04):** today's 16 free slots at Shifa Dev Clinic, the month, and the
  mismatch banner (emulator on Berlin time).
- **Tests:** `CalendarViewModelTest` (6: practice today with the day and month read, filters and "hidden", failure with
  request id and forced retry, month paging keeps the day, a late profile moving today and re-reading, the filter
  draft), `CalendarScreenshotTest` (+2: `Calendar_day`,
  `Calendar_details`).

## CAL-6 · Appointment panel

Built 2026-11-04. Flows `04/05-appointment-preview-sheet.md`, `04/07` (reschedule and adjust duration), `04/08`
(cancel); board `DaCalendar.dc.html` (appointment detail). On the phone it is a pushed screen (the tab bar steps aside
for its buttons), not a sheet.

- **Data** (`data/appointments/`): `AppointmentsRepository` with `cancel` (`DELETE /appointments/{id}`), `change`
  (`PUT /appointments/{id}/change {startAt, slotMinutes}`, used for both a move and a new length) and `remindPayment`
  (`POST /appointments/{id}/notify-payment-reminder`); all three checked against dev's OpenAPI. Every status is checked
  (Flutter didn't). `AppointmentRules.kt`: `ctaFor` (the 04/05 §4 table: Completed → Open Summary, In progress →
  Continue, cancelled → none, video from 5 minutes before until 1 hour after the end, in-person until the end of the
  practice day), `changeable`, `durationOptions` (shorter by the grain: the GCD of the free-slot lengths at its location,
  5–240, never under 5 minutes; longer by chaining the free slots right after it at the same location), and `fits` (a
  slot can take the move only when free slots chained without a gap cover the visit's whole length).
- **Panel** (`ui/calendar/AppointmentPanel.kt`): patient and time with status and (video) payment chips; Date and Time →
  Reschedule sheet (the month picker, shaded from the cache, past days closed; the day's future free slots where the
  visit fits as chips, the location named when it differs, the current slot greyed as "current"; "Move to 10:30", the
  length kept; a failed read shows the error with Retry);
  Adjust duration (the end time and length per option, Apply); Place (read-only, "Video Call" or the location); "Remind
  patient to pay" for an unpaid video visit; the main button with its hint; Cancel. A past, cancelled, completed or
  no-show visit offers no changes. The time rules re-evaluate every 30 seconds while the panel is open; an unapplied
  duration pick survives other days arriving in the cache, and Back asks before dropping it (the shared Discard
  question). A successful move reads both days, closes the panel and the shell shows "Appointment moved"; a length
  change stays in the panel with its banner. If the appointment disappears from its re-read day the panel closes; if it
  turns cancelled the cancel question closes. Each open is a new route, so the panel starts fresh; the cache collector
  and the clock tick run only while the panel is shown (`follow()`).
- **Shared fix:** `ProfileSheet` opens at full height, so a tall tray (the reschedule month) shows its button instead
  of stopping half way.
- **Cancel** (04/08): the question names the patient, date and time and says the patient will be notified; the button
  spins; a failure stays in the question with the request id for a retry; success closes the panel and the shell shows
  "Appointment cancelled successfully". No reason field until BE-10.
- **Refresh:** after every change the touched days and today are read once (`CalendarRepository.changed`), also after a
  failed change or cancel (a conflict means the calendar moved; a lost answer may hide a change that went through). The panel follows the live entry in the cache and takes the new
  times on success, so after a move it watches the new day.
- **Not built:** the date strip above the month (the month alone covers it), grouping slots by location (the
  location is on the chip instead), a same-day move that overlaps the visit's own time (its slot isn't free), the main
  button's targets (start / continue / summary are the visit flows, M5), the patient link,
  the colleague's calendar (CAL-10).
- **Demo:** the three calls answer OK without changing the generated days, so after a move the panel shows the old
  time again once the day is re-read (the demo calendar is stateless).
- **Verified on dev (2026-11-04, with CAL-7):** a booked hour moved from 10:00 to 14:00 (the backend kept 60
  minutes) and was cancelled (status CANCELLED); the sheet offered only slots where the hour fits.
- **Verified on the emulator (demo data, 2026-11-04):** opened Jasur Karimov's 6 November visit, rescheduled to 11:00
  ("Appointment moved"), the month sheet at full height with the greyed current slot, a move
  closing with its notice, the Discard question on Back after picking 1 h, cancelled (the calendar showed with the success notice). Dev has no appointments for the seed
  doctor, so the real calls wait for a booking (CAL-7).
- **Tests:** `AppointmentRulesTest` (6: + the GCD grain and the 5-minute floor, `fits`), `AppointmentPanelViewModelTest`
  (6: duration options and the PUT body, a move to another day refreshing both and following it, cancel failure keeping
  the question with the request id and re-reading the day, then success with the notice; a failed slot read with Retry
  and past days closed; a duration pick surviving cache updates; Back asking before dropping a length and closing when the visit is gone), `DemoBackendTest` (+1), `CalendarScreenshotTest` (+2: `Calendar_appointmentPanel`,
  `Calendar_appointmentPanelCompleted`).

## CAL-7 · Booking a free slot

Built 2026-11-04. Flow `04/06-book-appointment.md`; spec 03 §1.6, §1.10; board `DaCalendar.dc.html` (slot detail). A
pushed screen ("Slot Details") like the appointment panel.

- **Chain** (`data/calendar/SlotChain.kt`, `chainEnds`): the ends reachable from a start through free slots that begin
  exactly where the previous ended, at one location, at most 400 hops, on instants. The CAL-6 duration options and
  `fits` use it too. `slotMinutes` is the instant difference, so a chain across a DST switch books its real length
  (Flutter's wall-clock round trip is gone).
- **Data:** `PatientsRepository.forAssignment()` (`data/patients/`, the start of PAT-02): `GET /patients/for-assignment`
  page by page (500, at most 20 pages), rows without an id dropped, sorted by name here because the server's name sort
  answers 500 and there is no search (BE-33). `AppointmentsRepository.book` → `POST /schedule/book {startAt,
  slotMinutes, patientId, isVideo, reason, location, locationId?}`; `locationId` only in person; `location` is kept
  for older backends only (this one labels video and known locations itself).
- **Screen** (`ui/calendar/BookingScreen.kt`): summary (date · start – end, duration, the location chip, 04/06 §8
  target); a day before today shows only "This slot is in the past…" and no Book; a slot today that has begun is
  bookable (walk-ins) with a note saying so; Assign Patient → picker; Place as two chips (Clinic Address / Video Call)
  with the helper line; Time: start among the day's free slots at the same location, end along the chain with its
  length (a single choice is plain text); Reason prefilled with the localised "Check-up" (empty sends it); sticky
  **Book** (the flow's target label).
- **Picker** (sheet): search by name or id over the list read when the form opens (instant picker); a nameless patient
  shows as "Patient #id"; error with Retry; "No patients found".
- **Save:** no patient → "Please select a patient" under the tile, nothing sent. Success → the day (and today) read
  once, the form closes and the shell shows "Patient assigned: {name}". 409 / 400 → the day read again, "Selected time
  range is no longer fully available — calendar refreshed." with the request id, the form stays; other failures →
  the mapped message or "Couldn't book. Try again.". Back asks before dropping a picked patient, place, time or reason.
- **Follows the day:** the form watches the cached day (a day not read yet is unknown, a day read empty frees nothing);
  a start taken meanwhile moves to one still free (the tapped slot first) with the "no longer available" banner, an
  end the chain no longer reaches goes back to the start's own, and Book needs the whole range free. While the request
  runs the fields are locked; Book reads the clock again before deciding "past"; the patient list is read once even if
  the picker opens during the prefetch.
- **Shared:** `Notice` takes arguments (`args`) for the shell banner.
- **Not built:** "New patient" in the picker (the create-patient sheet is PAT), the chronic-disease warning (needs the
  patient detail, PAT-04), booking for a colleague (`resourceDoctorId`, CAL-10), quick-book (CAL-9).
- **Also in this commit:** lines over 120 characters left by earlier tickets were wrapped (ktlint failed on them), and
  the calendar ViewModel tests cancel their ViewModels before resetting Main (the "Dispatchers.Main is used
  concurrently" flake).
- **Verified:** demo data (picker, search "mal", 11:00 → 12:00, "Patient assigned: Malika Tursunova") and dev (Azamat
  Rakhimov 10:00–11:00 booked as 60 minutes, then moved and cancelled with CAL-6, dev left as it was).
- **Tests:** `SlotChainTest` (6: the five Flutter cases, DST, venue boundary), `BookingViewModelTest` (9: two slots as
  60 minutes with the request body and the notice; video without `locationId` and the default reason; no patient and a
  past day send nothing; a taken range keeps the form; picker order and search; a new start resets the end and Back
  asks; a conflict narrows the range; one patient read during the prefetch; a taken start moves back with the banner), `DemoBackendTest` (+1), `CalendarScreenshotTest` (+2: `Calendar_booking`, `Calendar_bookingPast`).
