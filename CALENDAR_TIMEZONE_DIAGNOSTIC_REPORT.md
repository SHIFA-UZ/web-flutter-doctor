# Calendar + Timezone Deep-Diagnostic Report

**Date:** 2026-02-10  
**Bugs under investigation:**
1. Calendar is empty on first load, but shows data when selecting the same day again.
2. Appointment time shifts by +5 hours after selecting it (e.g. 04:00 → 09:00).

---

## PART 1 — Calendar Initialization Flow

### Step 1 — selectedDate / selectedDay lifecycle

| Item | Location | Evidence |
|------|----------|----------|
| **Where declared** | `calendar_screen.dart` line 25 | `DateTime? _selectedDay;` (also `_focusedDay`, `_selectedEntry`) |
| **Initialization** | `calendar_screen.dart` lines 57–64 (`initState`) | `final today = DateTime.now();` then `_selectedDay = DateTime(today.year, today.month, today.day);` and `_focusedDay = _selectedDay!;` |
| **Includes time?** | No | `DateTime(year, month, day)` is **midnight in local time** (no hours/minutes set; they are 0). |
| **Normalized to midnight?** | Yes | Same date at 00:00:00 local. |

**Fetch on init:**  
- **Yes.** `initState()` calls `_loadDay(_selectedDay!)` (line 63). So the **first load for “today” is triggered in initState**, not only on day tap.

**Fetch on day tap:**  
- `onChanged` from `_CalendarPanel` (lines 285–291): when user picks a date, `_selectedDay` is set to `DateTime(d.year, d.month, d.day)` and `_loadDay(_selectedDay!)` is called.  
- Same pattern when opening the date picker from the header (lines 137–150): after picking, `_loadDay(_selectedDay!)` is called.

**Conclusion (empty on first load):**  
- Code path does **not** “only fetch on tap”. First load runs in `initState`.  
- Possible causes for “empty then full on second tap”:  
  1. **First request fails** (e.g. 401, network, or `profileAllProvider` not ready so something downstream fails).  
  2. **State update after first load not triggering a rebuild** (less likely: `state = next` in controller creates new map).  
  3. **Map key mismatch** (see below).

### Step 2 — Appointment query / filtering (app side)

- **No equality-based filter on “date” in the app.**  
- App asks backend for **one day** via `GET /api/calendar?day=YYYY-MM-DD`.  
- Backend does range-based filtering (see backend section).  
- App stores result by **day key**: `_dayKey(day) => DateTime(day.year, day.month, day.day)` (controller line 231).  
- Lookup: `entries[_dayKey(_selectedDay)]` in `_entriesFor(_selectedDay)` (screen line 40).  
- **Key type:** `DateTime` at local midnight. Two `DateTime` values with same (year, month, day) in Dart are `==` equal, so key lookup is consistent **if** both sides use the same calendar date.  
- **Potential issue:** If `loadDay` is ever called with a `DateTime` that has non-zero time (e.g. UTC midnight), `_dayKey` would still normalize to the same calendar day in local time. So equality-based bug on the app side is **unlikely**; the more likely issue is **first load failing or profile not ready**.

---

## PART 2 — Timezone Flow Analysis

### Step 3 — All date/time parsing and conversion (relevant to calendar)

| File | What happens | Conversion / risk |
|------|----------------|--------------------|
| **calendar_controller.dart** | `loadDay`: calls API, then `CalendarEntry.fromApi(..., doctorTimeZone: doctorTimeZone)`. | Uses **doctor profile timezone** for display. If `profileAllProvider.value` is null (profile not loaded), `doctorTimeZone` is null → model uses `'UTC'`. |
| **calendar_models.dart** | `CalendarEntry.fromApi`: `startAt`/`endAt` from JSON. | `_utcToTimeOfDayInZone(startAt, zone)`: `DateTime.parse(isoUtc)` then, if not UTC, normalized to UTC instant, then `_instantToTimeOfDay(utcInstant, doctorTimeZone)` using `tz.TZDateTime.from(utcInstant, loc)` → `TimeOfDay(hour, minute)`. **Single conversion** from UTC to doctor zone. |
| **calendar_models.dart** | `DateTime.parse(isoUtc)` | If string has **no "Z"**, Dart treats it as **local (device)** time. Then code forces UTC with `DateTime.utc(...)`, so we **misinterpret** non-Z string as UTC → **wrong time** and possible **+offset shift** (e.g. +5 if device is UTC+5). |
| **calendar_screen.dart** (SlotDetails) | `DateTime.parse(e.endAtUtc!)` for “is past” check | Normalizes to UTC instant; used only for past/future, not for display. |
| **calendar_controller.dart** | `_localToUtcIso(doctorTimeZone, ...)` for booking/change | Converts **local** (in doctor TZ) to UTC ISO. Correct for sending to backend. |

**Double conversion?**  
- In the **model**, we convert **once**: UTC string → doctor TZ → `TimeOfDay`.  
- **Risk:** If backend sends a time **without "Z"** (e.g. `2026-02-28T04:00:00`), `DateTime.parse` gives **local device** time. We then treat those components as UTC and convert to doctor zone again → **double conversion** and e.g. +5 hour shift (if device is UTC+5).

### Step 4 — One appointment trace (end-to-end)

1. **Backend** (`CalendarController.kt`):  
   - Returns `EntryDto` with `startAt`/`endAt` from `Instant.toString()`.  
   - In Java/Kotlin, `Instant.toString()` is **ISO-8601 with "Z"** (e.g. `2026-02-28T04:00:00Z`). So **backend contract is UTC with Z**.

2. **App – API response:**  
   - `calendar_controller.dart` `loadDay`: raw body decoded to list of maps.  
   - **DEBUG log:** `RAW API TIME: startAt=... endAt=...` (added).

3. **App – parsing:**  
   - `CalendarEntry.fromApi(j, doctorTimeZone: doctorTimeZone)`.  
   - `_utcToTimeOfDayInZone(startAt, zone)`:  
     - `DateTime.parse(isoUtc)` → if string is `...Z`, `parsed.isUtc == true`.  
     - `_instantToTimeOfDay(utc, doctorTimeZone)` → `tz.TZDateTime.from(utc, loc)` in doctor’s IANA zone → `TimeOfDay(hour, minute)`.  
   - **DEBUG log:** `PARSED TIME: ... | isUtc=...` (added).

4. **State:**  
   - Stored in `calendarProvider` state as `Map<DateTime, List<CalendarEntry>>` keyed by `_dayKey(day)`.  
   - Each entry keeps `start`/`end` as `TimeOfDay` (already in doctor zone) and `startAtUtc`/`endAtUtc` as original strings.

5. **UI:**  
   - `_DayEntriesList` receives `entries` and shows `_fmtRange(e.start, e.end)`.  
   - **DEBUG log:** `UI RENDER TIME: ...` (added).

**Interpretation:**  
- If backend **always** sends `...Z`, then `parsed.isUtc` should be true and we do **one** conversion to doctor zone.  
- If you see **04:00 → 09:00** with doctor in Asia/Tashkent (UTC+5), then either:  
  - Backend sent **04:00Z** (4am UTC) and we correctly show 09:00 in Tashkent (correct behavior), or  
  - Backend sent **04:00** (intended as 4am **local**) without Z, we parsed as 4am **device**/UTC, then converted to Tashkent again → **double conversion** and wrong display.

### Step 5 — Doctor timezone usage

| Search | Where used |
|--------|------------|
| **Doctor / practice timezone** | `calendar_controller.dart` line 266: `ref.read(profileAllProvider).value?.profile['timeZone']`. Used in `loadDay` and `changeAppointmentSlot` / `_localToUtcIso`. |
| **Device timezone** | Not used for **calendar display**. Only `DateTime.now()` for “today” and “current date” in UI. |
| **timezone package** | `calendar_models.dart`: `tz.getLocation(doctorTimeZone)`, `tz.TZDateTime.from(utcInstant, loc)`. Used to convert UTC → doctor local time. |

- **Display:** Uses **doctor profile timezone** only (from `/api/doctors/me` → `profile['timeZone']`).  
- **Risk:** If profile is not loaded when `loadDay` runs, `doctorTimeZone` is **null** → we pass null to `fromApi` → zone becomes `'UTC'`, so all times are shown in UTC instead of doctor’s practice zone. That can look like a “shift” if the doctor is e.g. UTC+5 (UTC 04:00 shown as 04:00 instead of 09:00).

---

## PART 3 — Temporary debug logs added

1. **Immediately after API response**  
   - **File:** `lib/state/calendar/calendar_controller.dart` (in `loadDay`, after decoding list).  
   - **Log:** `print('RAW API TIME: startAt=... endAt=... type=...');` for each entry.

2. **Immediately after parsing**  
   - **File:** same, inside the `.map()` that builds `CalendarEntry.fromApi`.  
   - **Log:** `print('PARSED TIME: $parsed | isUtc=${parsed.isUtc}');` for each start time.

3. **Right before UI rendering**  
   - **File:** `lib/features/calendar/presentation/calendar_screen.dart` in `_DayEntriesList` `itemBuilder`.  
   - **Log:** `print('UI RENDER TIME: HH:MM - HH:MM (entry.start=...)');` for each entry.

**How to use:**  
- Run the app, open Calendar, select a day with an appointment.  
- Check console:  
  - If **parsed.isUtc** is **false** for a string you expect to be UTC → backend may be sending without "Z" or parsing is wrong.  
  - If **RAW** shows e.g. `04:00:00Z` but **UI** shows 09:00 → conversion is correct (UTC → Tashkent).  
  - If **RAW** shows `04:00:00` (no Z) and **UI** shows 09:00 → double conversion (parse as local then convert again).

---

## PART 4 — Backend contract

- **Backend:** `CalendarController.kt` returns `EntryDto(startAt = ap.startAt.toString(), ...)`.  
- `Instant.toString()` in Java/Kotlin is **ISO-8601 with "Z"** (e.g. `2026-02-28T04:00:00Z`).  
- So the **intended** format is **UTC with Z**.  
- If any other endpoint or serializer omits "Z" or sends local time, that would violate the rule “Backend → UTC” and can cause the +5 shift.

---

## FINAL REPORT — Root causes and refactoring plan

### 1. Root cause: Empty calendar on first load

- **Evidence:** `initState` **does** call `_loadDay(_selectedDay!)`, so the first load is not “only on tap”.  
- **Most likely causes:**  
  - **First request fails** (401, network, or backend error). User then taps the same day → second request succeeds.  
  - **Profile not ready:** `profileAllProvider.value` is null when `loadDay` runs → `doctorTimeZone` is null. We still get data and show it in UTC; if there’s an error path that depends on profile, that could cause empty.  
  - **Map key:** Unlikely, as `_dayKey` normalizes to date-only; both init and tap use the same `DateTime(year, month, day)`.  

**Exact location to fix:**  
- **File:** `lib/features/calendar/presentation/calendar_screen.dart` (initState) and/or `lib/state/calendar/calendar_controller.dart` (loadDay).  
- **Recommendation:**  
  - Ensure calendar load runs **after** profile is available (e.g. await or listen to `profileAllProvider` before first `loadDay`, or retry when profile loads).  
  - Optionally retry `loadDay` once on failure so “tap again and it works” is not needed.

### 2. Root cause: +5 hour shift

- **Evidence:** Single conversion in app: UTC → doctor zone in `calendar_models.dart` only. Backend sends UTC with Z.  
- **Possible causes:**  
  - **Backend sometimes sends without "Z"** (or local time): then `DateTime.parse` treats as local; we then normalize to UTC and convert again → **double conversion** and e.g. +5 hours.  
  - **Doctor timezone null:** Times shown in UTC; if user expects “my local 04:00”, they see 04:00 UTC which is 09:00 in Tashkent – so the “shift” could be **no conversion** when we should convert.  
  - **Wrong time at booking:** If at book time we sent “04:00” as if it were UTC instead of “04:00 in doctor TZ” (e.g. 23:00 UTC for Tashkent), backend stores 04:00 UTC and we correctly show 09:00 in Tashkent – so the bug would be in **booking**, not in calendar display.

**Exact location:**  
- **Parsing:** `lib/features/calendar/domain/calendar_models.dart` – `_utcToTimeOfDayInZone`, `DateTime.parse(isoUtc)`.  
- **Display:** `CalendarEntry.start`/`end` (TimeOfDay) in `_DayEntriesList`.  
- **Recommendation:**  
  - Ensure API **always** returns UTC with "Z".  
  - If a string has no "Z", treat as **doctor timezone** (or reject), not as device local.  
  - Ensure `doctorTimeZone` is never null when parsing (defer load or use fallback).

### 3. Summary table

| Issue | Type | File(s) / location |
|-------|------|--------------------|
| Empty first load | Likely: first request fails or profile not ready | `calendar_screen.dart` initState; `calendar_controller.dart` loadDay; `profileAllProvider` timing |
| +5 hour shift | Double conversion **or** backend format (no Z) **or** null doctor TZ | `calendar_models.dart` parsing; backend serialization; `profileAllProvider.value` in loadDay |

### 4. Refactoring plan (high level)

- **Storage:** Keep backend and app state in **UTC** (ISO strings or UTC instants). Already the case for API and `startAtUtc`/`endAtUtc`.  
- **Parsing:**  
  - Always treat API timestamps as UTC (require "Z" or explicitly parse as UTC).  
  - If a value has no "Z", do **not** use `DateTime.parse` as-is (it becomes local); parse as UTC or in doctor zone explicitly.  
- **Rendering:**  
  - Single place: UTC → doctor timezone → `TimeOfDay` (already in `_utcToTimeOfDayInZone`).  
  - Ensure `doctorTimeZone` is set (wait for profile or use safe default) before first calendar load.  
- **Fetch strategy:**  
  - Trigger first load only when profile is available (or retry when it becomes available).  
  - Optionally retry once on failure to avoid “empty first load, works on second tap”.

---

## Rule check: Backend → UTC, DB → UTC, Model → UTC, UI → convert once

- **Backend:** Sends UTC with Z (Instant.toString()).  
- **Model:** Stores UTC strings and converts once to doctor zone for `TimeOfDay` display.  
- **Violations to look for:**  
  - API sending without "Z".  
  - Using device local time in parsing.  
  - `doctorTimeZone` null when converting (effectively showing UTC as if it were local).

---

When you have console output from the three debug logs, compare RAW vs PARSED vs UI to confirm where the +5 hours is introduced and whether the first load fails or returns empty.
