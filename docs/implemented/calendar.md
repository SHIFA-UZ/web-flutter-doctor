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
