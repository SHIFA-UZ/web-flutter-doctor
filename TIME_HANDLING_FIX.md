# Time Handling Consistency Fix

## Problem Summary

The app had inconsistent time handling that caused appointment times to "jump" between UTC and local time. The main issues were:

1. **Using `TimeOfDay` without date context** - `TimeOfDay` only stores hour and minute, no date or timezone information
2. **Mismatched model definitions** - The home screen was trying to use fields that didn't exist in the Appointment model
3. **No centralized time handling** - Different parts of the app converted times differently
4. **Backend data mismatch** - Backend sends times as "minutes from midnight" but frontend was using `TimeOfDay`

## Solution

### 1. Updated Appointment Model (`lib/features/appointments/domain/appointment_models.dart`)

**Changed from:**
- Using `TimeOfDay start` and `TimeOfDay end`
- No date information
- No timezone awareness

**Changed to:**
- Using `DateTime startTime` and `DateTime endTime` (in local timezone)
- Added `day`, `startMinutes`, `endMinutes` properties
- Added factory `fromDayAndMinutes()` for backend data conversion
- Added helper methods for formatting and conversion

**Key improvements:**
```dart
// Full DateTime with date and time in local timezone
final DateTime startTime;
final DateTime endTime;

// Factory for backend data (minutes from midnight)
factory Appointment.fromDayAndMinutes({
  required DateTime day,
  required int startMinutes,
  required int endMinutes,
  ...
})

// Helper properties
int get startMinutes => startTime.hour * 60 + startTime.minute;
DateTime get day => DateTime(startTime.year, startTime.month, startTime.day);
String get timeRange => '${formatTime(startTime)} - ${formatTime(endTime)}';
```

### 2. Updated CalendarEntry Model (`lib/features/calendar/domain/calendar_models.dart`)

**Same changes as Appointment:**
- Changed from `TimeOfDay` to `DateTime`
- Added `fromDayAndMinutes()` factory
- Added helper methods for time conversion
- Consistent with Appointment model

### 3. Updated Calendar Repository (`lib/features/calendar/data/calendar_repository_http.dart`)

**Improved backend data handling:**
```dart
Future<List<CalendarEntry>> entriesFor(DateTime day) async {
  // Normalize day to midnight local time
  final dayNormalized = DateTime(day.year, day.month, day.day);

  return data.map((m) {
    // Convert backend minutes to local DateTime
    return CalendarEntry.fromDayAndMinutes(
      day: dayNormalized,
      startMinutes: m['startMinutes'],
      endMinutes: m['endMinutes'],
      ...
    );
  }).toList();
}
```

**Key points:**
- Always normalize dates to midnight
- Convert backend minutes to full DateTime
- Keep everything in local timezone

### 4. Updated Home Screen (`lib/features/home/presentation/home_screen.dart`)

**Fixed:**
- Removed code trying to use non-existent fields
- Proper conversion from CalendarEntry to Appointment
- Uses new DateTime-based models

```dart
final appt = Appointment(
  id: 'temp-${e.startTime.millisecondsSinceEpoch}',
  patientName: e.patientName ?? 'Patient',
  location: e.location.isEmpty ? 'Video Consultation' : e.location,
  startTime: e.startTime,  // DateTime, not TimeOfDay!
  endTime: e.endTime,      // DateTime, not TimeOfDay!
);
```

### 5. Updated Calendar Screen (`lib/features/calendar/presentation/calendar_screen.dart`)

**Comprehensive rewrite:**
- Removed duplicate CalendarEntry definition (was defined in both screen and domain)
- Updated all time handling to use DateTime
- Fixed time pickers to convert properly
- Updated all formatting functions

**Before:**
```dart
CalendarEntry.appointment(
  start: const TimeOfDay(hour: 10, minute: 0),
  end: const TimeOfDay(hour: 10, minute: 30),
  ...
)
```

**After:**
```dart
CalendarEntry.appointment(
  startTime: dayNorm.add(const Duration(hours: 10)),
  endTime: dayNorm.add(const Duration(hours: 10, minutes: 30)),
  ...
)
```

### 6. Created Time Utility (`lib/core/utils/time_utils.dart`)

**New centralized utility for consistent time handling:**

```dart
class TimeUtils {
  // Format DateTime as HH:mm
  static String formatTime(DateTime time);

  // Format time range
  static String formatTimeRange(DateTime start, DateTime end);

  // Convert minutes from midnight to DateTime
  static DateTime minutesToDateTime(DateTime day, int minutes);

  // Convert DateTime to minutes from midnight
  static int dateTimeToMinutes(DateTime time);

  // Normalize date to midnight (removes time)
  static DateTime normalizeDate(DateTime date);

  // Check if two dates are the same day
  static bool isSameDay(DateTime a, DateTime b);

  // Format duration
  static String formatDuration(int minutes);
}
```

**Benefits:**
- Single source of truth for time formatting
- Consistent conversion logic
- Easy to update formatting across entire app

## How It Works Now

### Data Flow

1. **Backend → Frontend:**
   ```
   Backend sends: { day: "2025-03-04", startMinutes: 600, endMinutes: 630 }
   ↓
   Repository converts: Appointment.fromDayAndMinutes(...)
   ↓
   Frontend uses: DateTime(2025, 3, 4, 10, 0) in LOCAL time
   ```

2. **Frontend → Backend:**
   ```
   Frontend has: DateTime(2025, 3, 4, 10, 0) in LOCAL time
   ↓
   Use: appointment.startMinutes getter
   ↓
   Backend receives: { startMinutes: 600 }
   ```

### Key Principles

1. **Always store DateTime in local timezone** - No UTC conversions for display
2. **Normalize dates to midnight** - `DateTime(year, month, day)` for date keys
3. **Use factory methods** - `fromDayAndMinutes()` for backend data
4. **Use helper properties** - `startMinutes` getter for backend communication
5. **Centralize formatting** - Use `TimeUtils` or model methods

## Testing

To verify the fix works:

1. **Check home screen:**
   ```bash
   flutter run
   ```
   - Times should be consistent
   - No jumping between refreshes
   - Times match your local timezone

2. **Check calendar screen:**
   - Select a date
   - Times should display consistently
   - Editing times should work correctly

3. **Check appointments:**
   - Start an appointment from home screen
   - Verify patient name and time display correctly

## Migration Guide

If you have existing backend data or need to add new features:

### Creating an Appointment

**OLD (Don't use):**
```dart
Appointment(
  start: TimeOfDay(hour: 10, minute: 0),
  end: TimeOfDay(hour: 10, minute: 30),
  ...
)
```

**NEW (Use this):**
```dart
// From backend data
Appointment.fromDayAndMinutes(
  day: DateTime(2025, 3, 4),
  startMinutes: 600,  // 10:00 AM
  endMinutes: 630,    // 10:30 AM
  ...
)

// Or directly
Appointment(
  startTime: DateTime(2025, 3, 4, 10, 0),
  endTime: DateTime(2025, 3, 4, 10, 30),
  ...
)
```

### Formatting Times

**Use model methods:**
```dart
appointment.timeRange  // "10:00 - 10:30"
Appointment.formatTime(appointment.startTime)  // "10:00"
```

**Or use TimeUtils:**
```dart
TimeUtils.formatTime(dateTime)
TimeUtils.formatTimeRange(start, end)
```

### Converting for Backend

```dart
// Send to backend
final data = {
  'day': TimeUtils.normalizeDate(appointment.startTime),
  'startMinutes': appointment.startMinutes,
  'endMinutes': appointment.endMinutes,
};
```

## Benefits

✅ **Consistent time display** - All times shown in local timezone
✅ **No more jumping** - DateTime includes full date/time context
✅ **Type safety** - Compile-time checks for proper time handling
✅ **Easy to maintain** - Centralized time utilities
✅ **Backend compatible** - Smooth conversion to/from minutes
✅ **Clear data flow** - Factory methods document conversion

## Files Changed

1. `lib/features/appointments/domain/appointment_models.dart` - Updated model
2. `lib/features/calendar/domain/calendar_models.dart` - Updated model
3. `lib/features/calendar/data/calendar_repository_http.dart` - Fixed conversion
4. `lib/features/home/presentation/home_screen.dart` - Fixed usage
5. `lib/features/calendar/presentation/calendar_screen.dart` - Complete rewrite
6. `lib/core/utils/time_utils.dart` - New utility file
7. `lib/core/services/api_client.dart` - Fixed import
8. `test/widget_test.dart` - Updated test

## No Errors

Run `flutter analyze` to verify:
```bash
flutter analyze
# 0 errors found
```

Run the app:
```bash
flutter run
# Times should now be consistent!
```
