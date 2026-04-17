// lib/core/services/timezone_service.dart
// Practice timezone for onboarding (Shifa Global Time Architecture v2).

import 'package:iana_time_zone/iana_time_zone.dart';

/// Returns the device/browser IANA timezone (e.g. Europe/Berlin).
/// On unsupported platforms (e.g. web) or on error, returns null → caller should default to UTC or last known.
Future<String?> getDetectedTimeZone() async {
  try {
    final tz = await IanaTimeZone.getIanaTimeZone;
    return (tz != null && tz.trim().isNotEmpty) ? tz.trim() : null;
  } catch (_) {
    return null;
  }
}

/// Common IANA timezones for practice selection (subset of IANA database).
/// Sorted by region then city. Used for searchable dropdown.
const List<String> commonIanaTimeZones = [
  'Africa/Cairo',
  'Africa/Johannesburg',
  'Africa/Lagos',
  'Africa/Nairobi',
  'America/Argentina/Buenos_Aires',
  'America/Bogota',
  'America/Caracas',
  'America/Chicago',
  'America/Denver',
  'America/Los_Angeles',
  'America/Mexico_City',
  'America/New_York',
  'America/Phoenix',
  'America/Sao_Paulo',
  'America/Toronto',
  'Asia/Almaty',
  'Asia/Bangkok',
  'Asia/Dubai',
  'Asia/Ho_Chi_Minh',
  'Asia/Hong_Kong',
  'Asia/Jakarta',
  'Asia/Karachi',
  'Asia/Kolkata',
  'Asia/Seoul',
  'Asia/Shanghai',
  'Asia/Singapore',
  'Asia/Tashkent',
  'Asia/Tehran',
  'Asia/Tokyo',
  'Australia/Melbourne',
  'Australia/Perth',
  'Australia/Sydney',
  'Europe/Amsterdam',
  'Europe/Athens',
  'Europe/Berlin',
  'Europe/Istanbul',
  'Europe/London',
  'Europe/Madrid',
  'Europe/Moscow',
  'Europe/Paris',
  'Europe/Rome',
  'Europe/Vienna',
  'Europe/Warsaw',
  'Europe/Zurich',
  'Pacific/Auckland',
  'Pacific/Fiji',
  'UTC',
];
