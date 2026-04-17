# Quick Fix Reference - Timezone Consistency

## ✅ What Was Fixed

### The Problem
Home screen appointment times were "jumping" and inconsistent because the code was mixing:
- Device local timezone (from `DateTime.now()`)
- Doctor's practice timezone (from appointment data)

This caused incorrect calculations for "Urgent" and "Next" badges, and video call timing.

### The Solution
Created centralized timezone utilities and ensured all appointment-related code uses **doctor's practice timezone consistently**.

## 📁 Files Changed

### 1. NEW: `/lib/core/utils/timezone_utils.dart`
Centralized timezone conversion utilities - use these instead of `DateTime.now()`:
```dart
// DON'T use this for appointments:
final now = DateTime.now(); // ❌ Device local timezone

// DO use this:
final now = getNowInTimezone(doctorTimeZone); // ✅ Doctor timezone
```

### 2. `/lib/features/home/presentation/home_screen.dart`
Fixed appointment time calculations (lines 41-84)

### 3. `/lib/features/appointments/application/today_appointments_provider.dart`
Fixed "today" calculation to use doctor's timezone

### 4. `/lib/features/appointments/presentation/video_call_screen.dart`
Fixed 3 instances to use doctor's timezone

### 5. `/lib/features/appointments/presentation/in_person_appointment_screen.dart`
Fixed 3 instances to use doctor's timezone

## 🎯 How To Use Timezone Utils

### Get Current Time in Doctor's Timezone
```dart
// Old way (WRONG for appointments):
final now = DateTime.now();

// New way (CORRECT):
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

final doctorTz = ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
final now = getNowInTimezone(doctorTz); // Falls back to UTC if null
```

### Get Today's Date in Doctor's Timezone
```dart
// For querying "today's" appointments
final today = getTodayInTimezone(doctorTimeZone);
final ymd = '${today.year}-${today.month}-${today.day}';
```

### Convert TimeOfDay to Full DateTime
```dart
// For calculating time differences
final appointmentDateTime = timeOfDayToDateTimeInZone(
  appointment.start, // TimeOfDay
  DateTime.now(),    // Date component
  doctorTimeZone,    // Target timezone
);
```

### Calculate Minutes Until Appointment
```dart
final minutesUntil = calculateMinutesUntil(
  appointment.start,  // TimeOfDay
  doctorTimeZone,
);
// Returns negative if time has passed
```

## ⚠️ Important Rules

### DO:
✅ Always use doctor's timezone for appointment-related times
✅ Use `getNowInTimezone()` instead of `DateTime.now()` for appointments
✅ Use timezone utility functions for consistency
✅ Handle null timezone gracefully (falls back to UTC)

### DON'T:
❌ Don't use `DateTime.now()` for appointment calculations
❌ Don't mix device local timezone with doctor timezone
❌ Don't use `.toLocal()` for appointment times
❌ Don't assume device and doctor are in same timezone

## 🧪 Testing

To verify the fix works:

1. **Change Device Timezone:**
   - Settings → Date & Time → Set to different timezone than doctor
   - Example: Set device to UTC-5, doctor profile is UTC+5

2. **Check Home Screen:**
   - Appointments should show at correct time (doctor's timezone)
   - "Urgent" badge should appear 30 minutes before (in doctor time)
   - "Next" badge should appear 20 minutes before (in doctor time)

3. **Check Video Call Button:**
   - Should enable 5 minutes before appointment (in doctor time)
   - Not based on device time

4. **Check Calendar:**
   - Times should match home screen exactly
   - No more "jumping" or changing times

## 🔍 Troubleshooting

### Times Still Wrong?
Check if profile provider has doctor timezone:
```dart
final profile = ref.read(profileAllProvider).valueOrNull;
debugPrint('Doctor timezone: ${profile?.profile['timeZone']}');
// Should print something like: "Asia/Tashkent" or "Europe/Berlin"
```

### Falls Back to UTC?
The utilities gracefully fall back to UTC if:
- Doctor timezone is null
- Doctor timezone is empty string
- Doctor timezone is invalid IANA name

This is intentional - app continues working even if timezone not set.

### Still See DateTime.now()?
Search for remaining instances:
```bash
grep -r "DateTime.now()" lib/ | grep -v "timezone_utils"
```

If found in appointment-related code, replace with `getNowInTimezone()`.

## 📚 Related Docs

- `TIMEZONE_FIX_SUMMARY.md` - Detailed fix explanation
- `SHIFA_DATETIME_TIMEZONE_AUDIT.md` - Full timezone architecture
- `CLAUDE.md` - Developer guide (see "Timezone Handling" section)
