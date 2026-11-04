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
