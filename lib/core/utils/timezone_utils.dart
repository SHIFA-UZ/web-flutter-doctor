// lib/core/utils/timezone_utils.dart
// Centralized timezone utilities for consistent time handling across the app.
// All appointment-related times should use doctor's practice timezone.

import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart' show debugPrint;
import 'package:timezone/timezone.dart' as tz;

/// Gets the current DateTime in the specified IANA timezone.
///
/// Example:
/// ```dart
/// final now = getNowInTimezone('Asia/Tashkent');
/// // Returns current time in Tashkent, not device local time
/// ```
///
/// Returns UTC time if timezone is null/invalid.
DateTime getNowInTimezone(String? timezoneId) {
  debugPrint('getNowInTimezone called with: $timezoneId');

  if (timezoneId == null || timezoneId.isEmpty) {
    debugPrint('  → Timezone null/empty, returning UTC');
    return DateTime.now().toUtc();
  }

  try {
    final location = tz.getLocation(timezoneId);
    final result = tz.TZDateTime.now(location);
    debugPrint('  → Success: ${result.hour}:${result.minute} in $timezoneId');
    return result;
  } catch (e) {
    // Invalid timezone - fall back to UTC
    debugPrint('  → Invalid timezone "$timezoneId", falling back to UTC. Error: $e');
    return DateTime.now().toUtc();
  }
}

/// Converts a TimeOfDay to a full DateTime on a specific date in the given timezone.
///
/// This is useful for combining appointment times (TimeOfDay) with dates
/// for comparisons and calculations.
///
/// Example:
/// ```dart
/// final appointmentTime = TimeOfDay(hour: 14, minute: 30);
/// final today = DateTime.now();
/// final appointmentDateTime = timeOfDayToDateTimeInZone(
///   appointmentTime,
///   today,
///   'Asia/Tashkent'
/// );
/// ```
DateTime timeOfDayToDateTimeInZone(
  TimeOfDay time,
  DateTime date,
  String? timezoneId,
) {
  final zoneId = (timezoneId != null && timezoneId.isNotEmpty) ? timezoneId : 'UTC';

  try {
    final location = tz.getLocation(zoneId);
    return tz.TZDateTime(
      location,
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    );
  } catch (_) {
    // Invalid timezone - use UTC
    return tz.TZDateTime(
      tz.UTC,
      date.year,
      date.month,
      date.day,
      time.hour,
      time.minute,
    );
  }
}

/// Calculates minutes from now until the target TimeOfDay today, in the given timezone.
///
/// Returns negative if target time has already passed today.
///
/// Example:
/// ```dart
/// final appointment = TimeOfDay(hour: 15, minute: 0);
/// final minutesUntil = calculateMinutesUntil(appointment, 'Asia/Tashkent');
/// // If current time in Tashkent is 14:30, returns 30
/// // If current time in Tashkent is 15:30, returns -30
/// ```
int calculateMinutesUntil(TimeOfDay target, String? timezoneId) {
  final now = getNowInTimezone(timezoneId);
  final targetDateTime = timeOfDayToDateTimeInZone(target, now, timezoneId);

  final difference = targetDateTime.difference(now);
  return difference.inMinutes;
}

/// Gets "today" as a date (year, month, day) in the specified timezone.
///
/// This is critical for determining which appointments belong to "today"
/// when the doctor's timezone differs from the device timezone.
///
/// Example:
/// ```dart
/// // Device in UTC-5 at 23:00 (11 PM)
/// // Doctor in UTC+5 at 09:00 next day (9 AM)
/// final todayInDoctorZone = getTodayInTimezone('Asia/Tashkent');
/// // Returns next calendar day, not device's "today"
/// ```
DateTime getTodayInTimezone(String? timezoneId) {
  final now = getNowInTimezone(timezoneId);
  return DateTime(now.year, now.month, now.day);
}

/// Converts a UTC DateTime to the specified timezone.
///
/// Example:
/// ```dart
/// final utcTime = DateTime.parse('2024-03-15T10:00:00Z');
/// final doctorTime = utcToTimezone(utcTime, 'Asia/Tashkent');
/// // Returns time in Tashkent timezone (UTC+5)
/// ```
DateTime utcToTimezone(DateTime utc, String? timezoneId) {
  final zoneId = (timezoneId != null && timezoneId.isNotEmpty) ? timezoneId : 'UTC';

  try {
    final location = tz.getLocation(zoneId);
    final utcInstant = utc.isUtc ? utc : utc.toUtc();
    return tz.TZDateTime.from(utcInstant, location);
  } catch (_) {
    // Invalid timezone - return as-is
    return utc;
  }
}

/// Formats a DateTime as HH:mm for display.
String formatTimeForDisplay(DateTime dateTime) {
  final hour = dateTime.hour.toString().padLeft(2, '0');
  final minute = dateTime.minute.toString().padLeft(2, '0');
  return '$hour:$minute';
}
