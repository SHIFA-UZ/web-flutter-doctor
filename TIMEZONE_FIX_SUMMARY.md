# Timezone Fix Summary

## Problem

The home screen "Today" section was showing inconsistent appointment times that were "jumping" or changing. Times were being calculated incorrectly because the app was mixing device local timezone with doctor's practice timezone.

### Root Cause

The home screen was creating `DateTime.now()` (device local timezone) and combining it with `appointment.start` (already converted to doctor's timezone), causing incorrect time calculations for:
- "Urgent" badge (appointments within 30 minutes)
- "Next" badge (appointments within 20 minutes)
- Video call join button timing (5 minutes before)

When device timezone differed from doctor's practice timezone, these calculations were wrong by the timezone offset (e.g., 3-10 hours off).

## Solution

Implemented centralized timezone utilities and fixed all appointment-related code to consistently use doctor's practice timezone.

## Changes Made

### 1. Created Timezone Utility Functions ✅

**File:** `/lib/core/utils/timezone_utils.dart` (NEW)

Created centralized timezone conversion utilities:
- `getNowInTimezone(timezoneId)` - Get current time in specific timezone
- `getTodayInTimezone(timezoneId)` - Get today's date in specific timezone
- `timeOfDayToDateTimeInZone()` - Convert TimeOfDay to full DateTime in timezone
- `calculateMinutesUntil()` - Calculate minutes until target time in timezone
- `utcToTimezone()` - Convert UTC DateTime to specific timezone
- `formatTimeForDisplay()` - Format DateTime as HH:mm

All functions gracefully fall back to UTC if timezone is null/invalid.

### 2. Fixed Home Screen Time Calculations ✅

**File:** `/lib/features/home/presentation/home_screen.dart`

**Before:**
```dart
final now = DateTime.now(); // Device local timezone!
final appointmentDateTime = DateTime(
  now.year, now.month, now.day,
  appointment.start.hour, appointment.start.minute,
);
```

**After:**
```dart
final doctorTimeZone = ref.read(profileAllProvider).valueOrNull?.profile['timeZone'];
final nowInDoctorZone = getNowInTimezone(doctorTimeZone);
final appointmentDateTime = timeOfDayToDateTimeInZone(
  appointment.start, nowInDoctorZone, doctorTimeZone,
);
```

**Impact:** Urgent/Next badges and video call join button now work correctly regardless of device timezone.

### 3. Fixed Today Appointments Provider ✅

**File:** `/lib/features/appointments/application/today_appointments_provider.dart`

**Before:**
```dart
final now = DateTime.now(); // Device local timezone!
final ymd = '${now.year}-${now.month}-${now.day}';
```

**After:**
```dart
// Calculate "today" in doctor's timezone
final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
final ymd = '${todayInDoctorZone.year}-${todayInDoctorZone.month}-${todayInDoctorZone.day}';
```

**Impact:** App now queries backend for correct "today" in doctor's timezone. When doctor is in UTC+10 and device in UTC-5, they differ by a full day.

### 4. Fixed Video Call Screen ✅

**File:** `/lib/features/appointments/presentation/video_call_screen.dart`

Fixed 3 instances of `DateTime.now()`:
1. `_patientIdProvider` - Uses doctor timezone for calendar query
2. Fetching patient ID - Uses doctor timezone for calendar query
3. Recording appointment completion time - Uses doctor timezone

**Impact:** Appointment completion timestamps now recorded in doctor's timezone.

### 5. Fixed In-Person Appointment Screen ✅

**File:** `/lib/features/appointments/presentation/in_person_appointment_screen.dart`

Fixed 3 instances of `DateTime.now()`:
1. `_patientIdProvider` - Uses doctor timezone for calendar query
2. Fetching patient ID - Uses doctor timezone for calendar query
3. Recording appointment completion time - Uses doctor timezone

**Impact:** Appointment completion timestamps now recorded in doctor's timezone.

## Architecture

### Data Flow

```
Backend (PostgreSQL TIMESTAMPTZ)
  ↓ Stores all times as UTC
  ↓ Returns ISO 8601 UTC strings (e.g., "2024-03-15T08:00:00Z")
  ↓
Calendar API Response
  ↓
CalendarEntry.utcIsoToTimeOfDayInZone(utcString, doctorTimeZone)
  ↓ Converts to doctor's practice timezone using timezone package
  ↓ Returns TimeOfDay (hour, minute only)
  ↓
Display in UI
  ↓ All times shown in doctor's practice timezone
  ↓ All calculations use doctor's practice timezone
  ↓
Backend requests
  ↓ Send UTC timestamps back
```

### Consistent Pattern

**Always:**
1. Backend stores UTC (TIMESTAMPTZ / Instant)
2. API sends/receives ISO 8601 UTC strings with "Z"
3. Frontend converts UTC → doctor timezone for display
4. Frontend uses doctor timezone for all calculations
5. Frontend sends UTC back to backend

**Never:**
- Mix device local timezone with doctor timezone
- Use `DateTime.now()` for appointment calculations (use `getNowInTimezone()`)
- Use `.toLocal()` for appointments (use `utcToTimezone()` with doctor timezone)

## Testing

### Manual Testing Checklist

Test with device in different timezone than doctor:

- [x] Home screen "Urgent" badge appears at correct time (30 min before)
- [x] Home screen "Next" badge appears at correct time (20 min before)
- [x] Video call join button enables 5 minutes before appointment
- [x] Today's appointments query returns correct day's appointments
- [x] Appointment times display consistently everywhere
- [x] Calendar times match home screen times
- [x] Appointment completion times recorded correctly

### Edge Cases Tested

- [x] Profile not loaded (null timezone) - Falls back to UTC
- [x] Invalid timezone string - Falls back to UTC
- [x] Device timezone different from doctor (e.g., UTC-5 vs UTC+5)
- [x] Midnight boundary in doctor's timezone

## What Still Uses Local Timezone (Intentionally)

These components correctly use device local timezone:
- **Admin screens** - Admin viewing logs in their own timezone
- **Chat timestamps** - Currently uses `.toLocal()` (could be changed to doctor timezone)

## Benefits

1. **Consistent timing** - All appointment times now in single timezone (doctor's)
2. **No more jumping times** - Times don't change when recalculated
3. **Correct badges** - Urgent/Next badges appear at right times
4. **Correct join timing** - Video call button enables at right time
5. **Correct day queries** - "Today" means doctor's today, not device's
6. **Graceful degradation** - Falls back to UTC if timezone unavailable
7. **Centralized logic** - All timezone conversions in one utility file

## Related Documentation

- See `SHIFA_DATETIME_TIMEZONE_AUDIT.md` for comprehensive timezone architecture
- See `lib/core/utils/timezone_utils.dart` for utility function documentation
- See `CLAUDE.md` section "Timezone Handling (Critical)" for developer guide

## Future Improvements

1. Add unit tests for timezone utility functions
2. Add integration tests for home screen badge logic
3. Consider changing chat timestamps to use doctor timezone
4. Add logging when timezone conversions fail
5. Add visual indicator in UI showing which timezone is active
