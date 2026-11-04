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
