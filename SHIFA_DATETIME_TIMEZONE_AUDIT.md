# Shifa System — Date, Time & Timezone Audit

**Scope:** Backend (API + DB), Doctor web app, Patient mobile apps (iOS & Android).  
**Purpose:** Document how date, time, and timezones are **currently** implemented. No solutions proposed.

---

## 1. DATABASE LAYER

### 1.1 Appointment date/time storage

| Aspect | Implementation |
|--------|----------------|
| **Data type** | PostgreSQL `TIMESTAMPTZ` (timestamp with time zone). In Kotlin/JPA mapped as `java.time.Instant`. |
| **Stored value** | UTC. PostgreSQL stores TIMESTAMPTZ in UTC internally; JDBC/Instant round-trip as UTC. |
| **Timezone stored separately?** | No. No `time_zone` or offset column for appointments. |
| **Doctor timezone stored?** | No. Not in `doctor_profiles` or anywhere. |
| **Patient timezone stored?** | No. Not in `patient_profiles` or anywhere. |

**Schema (appointments):**

```sql
-- V1__init.sql (excerpt)
CREATE TABLE IF NOT EXISTS appointments (
  id             BIGSERIAL PRIMARY KEY,
  doctor_id      BIGINT NOT NULL REFERENCES doctor_profiles(id),
  patient_id     BIGINT NOT NULL REFERENCES patient_profiles(id),
  start_at       TIMESTAMPTZ NOT NULL,
  end_at         TIMESTAMPTZ NOT NULL,
  ...
  CONSTRAINT appt_time_valid CHECK (end_at > start_at)
);
```

**Domain (Kotlin):**

```kotlin
// Appointment.kt
@Column(name="start_at", nullable = false)
var startAt: Instant,
@Column(name="end_at", nullable = false)
var endAt: Instant,
```

### 1.2 Other timestamp columns

- **Audit / created_at:** `TIMESTAMPTZ NOT NULL DEFAULT now()` (e.g. `users`, `notifications`, `ai_draft_notes`, `consultation_notes`, `user_roles`, `document_access_requests`, `messages`, `audit_logs`). Stored in UTC.
- **Availability slots (V1):** `start_at` / `end_at` as `TIMESTAMPTZ NOT NULL`.
- **Remote care:** `remote_care_tasks.created_at` = `TIMESTAMP WITH TIME ZONE`; `task_check_ins` has `scheduled_date DATE`, `scheduled_time TIME` (no timezone; wall-clock only).

### 1.3 Recurring availability (schedule rules)

**Weekly rules (V2):**

```sql
CREATE TABLE IF NOT EXISTS weekly_schedule_rules (
  id            BIGSERIAL PRIMARY KEY,
  doctor_id     BIGINT NOT NULL REFERENCES doctor_profiles(id),
  weekday       INT NOT NULL,               -- 1..7 (Mon..Sun)
  start_time    TIME NOT NULL,              -- local wall-clock time
  end_time      TIME NOT NULL,              -- local wall-clock time
  slot_minutes  INT NOT NULL,
  ...
);
```

**Date-specific rules (V31):**

```sql
CREATE TABLE IF NOT EXISTS date_specific_schedule_rules (
  ...
  start_date DATE NOT NULL,
  end_date DATE NOT NULL,
  start_time TIME NOT NULL,
  end_time TIME NOT NULL,
  slot_minutes INTEGER NOT NULL ...
);
```

- **Recurrence:** By weekday + `TIME` (weekly) or by date range + `TIME` (date-specific). No timezone column.
- **Interpretation:** Backend treats these `TIME`/`DATE` values as **Asia/Tashkent** when building slots (see §2). So recurrence is **implicitly** timezone-aware only in the sense that one fixed zone (Asia/Tashkent) is used everywhere.

### 1.4 Task check-ins (remote care)

- `task_check_ins.scheduled_date` = `DATE`, `scheduled_time` = `TIME`. No timezone.
- Used with **Asia/Tashkent** in reminder logic (e.g. `ReminderNotificationService`).

### 1.5 Hardcoded offsets

- No literal numeric offsets (e.g. `+2`, `-5`) in migrations.
- Single **hardcoded timezone** in application code: **Asia/Tashkent** (see §2).

---

## 2. BACKEND LOGIC

### 2.1 Single application timezone

**Definition:**

```kotlin
// config/AppTime.kt
object AppTime {
    val ZONE: ZoneId = ZoneId.of("Asia/Tashkent")
}
```

All backend date/time behaviour that is “local” is in **Asia/Tashkent**. There is no doctor or patient timezone; no request header or profile setting.

### 2.2 When an appointment is created

**Doctor books (BookingController — `/api/schedule/book`):**

- Request: `day` (yyyy-MM-dd), `startTime` (HH:mm), `slotMinutes`, …
- Backend interprets `day` + `startTime` as **local date/time in Asia/Tashkent**.
- Conversion to UTC:

```kotlin
val date = LocalDate.parse(b.day)
val time = LocalTime.parse(b.startTime)
val zone = AppTime.ZONE
val startLdt = LocalDateTime.of(date, time)
val endLdt = startLdt.plusMinutes(b.slotMinutes.toLong())
val startAt = startLdt.atZone(zone).toInstant()
val endAt = endLdt.atZone(zone).toInstant()
```

**Patient books (PatientController — POST `/api/patients/me/appointments`):**

- Same pattern: `req.day` + `req.startTime` parsed as **Asia/Tashkent** local, then:

```kotlin
val startAt = startLdt.atZone(zone).toInstant()
val endAt = endLdt.atZone(zone).toInstant()
```

- So: backend **always** assumes the submitted (date, time) is in **Asia/Tashkent**. It does **not** use client timezone or any offset from the client.

### 2.3 When returning appointments

**Doctor app (CalendarController, AppointmentController, BookingController):**

- UTC → “local” for response: `appointment.startAt.atZone(AppTime.ZONE).toLocalDateTime()` then `.toString()`.
- So API returns **naive** strings like `2025-12-30T08:00` (no offset, no zone id). Semantics: “this is in Asia/Tashkent” but not stated in the payload.

**Doctor app (PatientController list for doctor-view of patient’s appointments):**

- Same: `startLdt.toString()`, `endLdt.toString()` (Asia/Tashkent local, naive string).

**Patient app — list (GET /api/patients/me/appointments):**

- Same: `startAt = startLdt.toString()`, `endAt = endLdt.toString()` (naive).

**Patient app — single (PatientAppointmentController GET /api/patients/me/appointments/{id}):**

- Uses offset in response:

```kotlin
startAt = appointment.startAt.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
endAt = appointment.endAt.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
```

- So returns strings like `2026-02-12T13:00+05:00` (Asia/Tashkent offset). Only this endpoint returns an explicit offset.

**Summary:** Backend converts UTC → Asia/Tashkent for all human-facing times. List endpoints return **naive** local datetime strings; single-appointment (patient) returns **ISO with offset**. Frontends are left to interpret naive strings (see §3, §4).

### 2.4 Calendar / slots

- **GET /api/calendar?day=yyyy-MM-dd** (doctor): `day` is interpreted as a **calendar date in Asia/Tashkent**. `dayStart`/`dayEnd` are built as `localDate.atStartOfDay(zone).toInstant()` and `localDate.plusDays(1).atStartOfDay(zone).toInstant()`.
- **Slots:** Built from `weekly_schedule_rules` and `date_specific_schedule_rules` (TIME + DATE). `LocalDateTime.of(localDate, t)` then `atZone(zone).toInstant()`. So all slot times are Asia/Tashkent.
- **GET /api/patients/me/schedule/doctors/{id}/available?day=yyyy-MM-dd** (patient): Same: `day` and slot times are Asia/Tashkent.

### 2.5 Libraries and “now”

- **Java time only:** `java.time` (Instant, LocalDate, LocalTime, LocalDateTime, ZonedDateTime, ZoneId). No `moment.js`, no `date-fns`, no third-party date libs.
- **“Now” usage:**
  - **UTC:** `Instant.now()` (e.g. appointment overlap checks, `patientSignedAt`, notification `readAt`, AI draft cleanup).
  - **Asia/Tashkent:** `ZonedDateTime.now(AppTime.ZONE)` (e.g. ReminderNotificationService task window, DoctorAnalyticsService “today”).
  - **Server default (unspecified zone):** `LocalDateTime.now()` in `RemoteCareTaskController.effectiveStatus()` / `computeProgress()` — **not** explicitly Asia/Tashkent; uses JVM default. This is inconsistent with task reminder logic that uses `AppTime.ZONE`.

---

## 3. DOCTOR APP

### 3.1 How doctor timezone is determined

- **Not** from profile or settings. **Not** from browser timezone.
- Backend uses a **single fixed zone** (Asia/Tashkent). So “doctor timezone” is effectively **hardcoded** as Asia/Tashkent for all server-side logic (slots, “today”, date ranges).

### 3.2 How time slots are displayed

- **Calendar (GET /api/calendar):** Backend returns entries with `startAt`/`endAt` as **naive** strings (e.g. `2025-12-30T08:00`). Doctor app parses and displays:
  - `CalendarEntry.fromApi`: parses with `_parseLocalTime` — extracts hour/minute from the string **without** any timezone conversion. So “08:00” is shown as 08:00.
- So slots are displayed **as-is** (same numbers as in the string). No conversion from UTC in the app; backend already sent Asia/Tashkent local time as naive strings, and the app treats them as plain “wall clock” for that day.

### 3.3 “Today” and calendar day

- **Today:** `DateTime.now()` in the doctor app (device/browser local).
- **Calendar request:** `day` sent as `yyyy-MM-dd` from that **local** “today” (e.g. in `today_appointments_provider`: `now.year`, `now.month`, `now.day` → `ymd`).
- So when a doctor in **Berlin** opens the app, “today” is Berlin’s today (e.g. 25 Feb). They send `day=2025-02-25`. Backend returns slots for **2025-02-25 in Asia/Tashkent**. So the doctor sees Tashkent’s 25 Feb slots, which may not match their own “today” in terms of actual UTC day. If the doctor expects “my local today,” mismatch is possible (e.g. late evening in Berlin could already be next day in Tashkent).

### 3.4 Today appointments / home screen

- **today_appointments_provider:** Requests `/api/calendar` with `day` = local today (device). Backend returns appointments for that **date in Asia/Tashkent**. So “today’s appointments” are “appointments on calendar date = device’s today in Asia/Tashkent,” not necessarily “appointments whose start falls in the doctor’s local today.”
- **home_screen:** Builds “time until appointment” using `DateTime(now.year, now.month, now.day, appointment.start.hour, appointment.start.minute)`. So it uses **today’s** date (device) plus the appointment’s **hour/minute** from the API (which are Asia/Tashkent). That mixes device date with backend time-of-day and can be wrong for appointments on another calendar day or when doctor is not in Asia/Tashkent.

### 3.5 If doctor changes physical location

- Backend and app do **not** change. Still one zone (Asia/Tashkent). Slots, “today,” and list views remain tied to Asia/Tashkent. Doctor in another country would see:
  - Slots for “today” that are Tashkent’s day, not necessarily their local day.
  - No automatic adjustment of schedule or display to doctor’s current location.

---

## 4. PATIENT APP

### 4.1 How patient timezone is detected

- **Not** stored or sent. **Not** manually selected. Device timezone is used only implicitly when the app uses `DateTime.now()` (e.g. “now” for filtering or countdown). Slots and booking are **not** sent in patient’s timezone; they are in doctor’s (backend) zone.

### 4.2 When patient books

- Patient picks a slot from **GET /api/patients/me/schedule/doctors/{id}/available?day=yyyy-MM-dd**. Slots are returned as naive local datetimes in Asia/Tashkent (e.g. `startAt: "2025-12-30T08:00"`).
- From the selected slot the app derives `day` (yyyy-MM-dd) and `startTime` (HH:mm) and sends them in **POST /api/patients/me/appointments**.
- So the **time sent to the backend is the slot’s date and time as returned** — i.e. **Asia/Tashkent** local. Backend interprets it as Asia/Tashkent and converts to UTC. So patient books “doctor’s local time”; no conversion from patient’s device timezone.

### 4.3 When displaying booked appointments

- **List (GET /api/patients/me/appointments):** Backend returns `startAt`/`endAt` as **naive** strings (e.g. `2025-12-30T08:00`). Patient app uses `parseAppointmentDateTime()` → `DateTime.parse()`. In Dart, a string **without** an offset (like `2025-12-30T08:00`) is parsed as **local** (device) time. So the same string that backend meant as “08:00 Asia/Tashkent” is shown as “08:00 in the patient’s device timezone” — **wrong** if patient is not in Asia/Tashkent.
- **Detail (GET /api/patients/me/appointments/{id}):** Backend returns **ISO with offset** (e.g. `2026-02-12T13:00+05:00`). `parseAppointmentDateTime` strips optional `[ZoneId]` and parses. So the value is a correct instant. Formatting with `DateFormat` in Dart uses device local time by default, so the **detail** screen can show the correct local time for the patient. So list vs detail are **inconsistent**: list = wrong for non–Tashkent patients, detail = correct if parsed/displayed as instant.

### 4.4 Summary (patient)

- Timezone is **not** explicitly handled (no profile, no selector).
- Booking: patient effectively selects “doctor’s local time” (Asia/Tashkent); backend stores UTC.
- Display: list uses naive strings → misinterpreted as device local; detail uses offset strings → can be shown correctly in device local.

---

## 5. VIDEO CONSULTATION LOGIC

- Video call start is **not** driven by a shared “session start time” comparison in the audited code. Joining is via appointment id and token; there is no explicit check in the snippets like “current time >= appointment start and <= end” in a single timezone.
- **Patient:** Can open video call from appointment details; no explicit “can join at start time” logic using UTC or a shared zone was found in the patient video/waiting room flow.
- **Backend:** Appointments are stored as UTC (`start_at`/`end_at`). Any server-side “is it time for the call?” would use UTC if implemented elsewhere (e.g. token validity). Not fully audited here.
- So: **both parties align only indirectly** — same appointment (UTC) in DB; clients receive start/end in API either as naive (Asia/Tashkent) or with offset (patient detail). No explicit “both sides use UTC for join window” logic was found in the reviewed client code.

---

## 6. EDGE CASES

| Scenario | Current behaviour |
|----------|-------------------|
| **DST (Daylight Saving Time)** | Asia/Tashkent does not observe DST. So backend is DST-free. If in future a zone with DST is used, `ZonedDateTime` would handle it; current code does not. |
| **Doctor in Europe, patient in US** | Both see and book in Asia/Tashkent times. No conversion to doctor or patient local. Doctor “today” may be wrong (Berlin today vs Tashkent today). Patient list shows wrong local time (naive parsed as device local). |
| **Appointment booked before DST change** | N/A for Asia/Tashkent. If server or a future zone had DST, stored UTC is correct; display would depend on client/server zone handling. |
| **Doctor travels to new timezone** | No change: still one fixed zone (Asia/Tashkent). No “current doctor location” or “doctor timezone” in the system. |
| **Server in different timezone** | Backend uses `Instant` and `AppTime.ZONE` explicitly, not server default. So server TZ does not affect appointment storage or API semantics. `LocalDateTime.now()` in RemoteCareTaskController is the only place using JVM default. |

---

## 7. CURRENT RISKS

1. **Single hardcoded timezone (Asia/Tashkent):** All “local” behaviour is tied to one zone. Doctors or patients in other timezones get wrong “today,” wrong list times, or confusing slot semantics.
2. **Naive datetime in API:** List endpoints return `startAt`/`endAt` without offset. Clients that parse as “local” (e.g. Dart `DateTime.parse` for strings without Z) will show wrong time for non–Tashkent users. Doctor app treats them as “display as-is” (numbers only), which is only correct if doctor is in Tashkent.
3. **Double conversion / wrong “today”:** Doctor app sends device “today” as `day`; backend returns slots for that date **in Asia/Tashkent**. So “today” is ambiguous (device vs Tashkent). Home screen mixes device date with backend time-of-day for “time until appointment,” which can be incorrect.
4. **Patient list vs detail:** List returns naive → wrong when patient not in Tashkent. Detail returns offset → correct. Inconsistent contract and behaviour.
5. **RemoteCareTaskController:** Uses `LocalDateTime.now()` (JVM default) for “now” in task progress/status, while task reminders use `ZonedDateTime.now(AppTime.ZONE)`. Risk of mismatch if server TZ ≠ Asia/Tashkent.
6. **No doctor/patient timezone storage:** Cannot later “fix” display or slots per user without adding timezone and changing APIs and clients.

---

## 8. SUMMARY

### 8.1 Time flow (high level)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         BACKEND (single zone: Asia/Tashkent)             │
│  • Stores: start_at/end_at as TIMESTAMPTZ (UTC)                          │
│  • Schedule rules: DATE + TIME (no TZ column) → interpreted as Tashkent │
│  • All “local” conversion: UTC ↔ Asia/Tashkent only                      │
└─────────────────────────────────────────────────────────────────────────┘
         │                                    │
         │ POST book: (day, startTime)        │ GET calendar/slots/appts
         │ interpreted as Asia/Tashkent       │ returned as naive local
         │ → toInstant() → UTC                │ or (patient detail) ISO+offset
         ▼                                    ▼
┌──────────────────────┐           ┌──────────────────────┐
│   DOCTOR APP          │           │   PATIENT APP        │
│ • “Today” = device    │           │ • Slots = as returned │
│ • day param = device  │           │ • Book: day+time from │
│   date                │           │   slot (Tashkent)     │
│ • Slots shown as-is   │           │ • List: parse naive   │
│   (no conversion)     │           │   → device local ❌   │
│ • Home: device date   │           │ • Detail: parse offset│
│   + API time → bug    │           │   → device local ✓    │
└──────────────────────┘           └──────────────────────┘
```

### 8.2 Classification

- **Storage:** **UTC-first.** Appointments and other timestamps are stored in UTC (TIMESTAMPTZ / Instant). Good.
- **Business logic / API:** **Local-time-first in one zone.** All “local” meaning is Asia/Tashkent. No per-user or per-role timezone. So effectively “single timezone (Tashkent) first.”
- **Clients:** **Mixed and inconsistent.** Doctor app assumes Tashkent semantics (display as-is; “today” from device but backend uses Tashkent). Patient app: list interprets naive as device local (wrong); detail uses offset (correct). So overall: **mixed (dangerous)** — storage is UTC, but API and clients mix naive local (Tashkent) with device local and no clear contract, leading to wrong display and “today” for users outside Asia/Tashkent.

### 8.3 One-line summary

**The system stores UTC and uses a single fixed application timezone (Asia/Tashkent) for all server-side “local” logic and for slot/booking semantics. Doctor and patient timezones are not stored or used. APIs often return naive datetime strings that clients can misinterpret (especially patient list and doctor “today”), so the current implementation is mixed and risky for users outside Asia/Tashkent.**
