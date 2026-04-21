// calendar_models.dart
// Shifa Global Time Architecture v2: API returns ISO 8601 UTC; we display in doctor practice timezone.
import 'package:flutter/material.dart';
import 'package:timezone/timezone.dart' as tz;
import 'package:flutter/foundation.dart' show debugPrint;

enum EntryType { appointment, freeSlot }

class CalendarEntry {
  final EntryType type;
  final TimeOfDay start;
  final TimeOfDay end;

  /// ISO 8601 UTC strings from API (e.g. "2026-02-12T08:00:00Z"). Used for booking and change-slot.
  final String? startAtUtc;
  final String? endAtUtc;

  // Appointment-only
  final String? patientName;
  final bool isVideo;
  final String location;
  final String reason;

  /// Patient photo URL
  final String? photoUrl;

  /// Appointment ID (for canceling/changing)
  final int? appointmentId;

  /// Patient ID (for notifications)
  final int? patientId;

  /// Backend status string (e.g. CONFIRMED, COMPLETED, CANCELLED) for Today list.
  final String? status;

  CalendarEntry.appointment({
    required this.start,
    required this.end,
    this.startAtUtc,
    this.endAtUtc,
    required this.patientName,
    required this.location,
    required this.reason,
    this.isVideo = false,
    this.photoUrl,
    this.appointmentId,
    this.patientId,
    this.status,
  }) : type = EntryType.appointment;

  CalendarEntry.freeSlot({
    required this.start,
    required this.end,
    this.startAtUtc,
    this.endAtUtc,
    this.location = '',
  })  : type = EntryType.freeSlot,
        patientName = null,
        isVideo = false,
        reason = '',
        photoUrl = null,
        appointmentId = null,
        patientId = null,
        status = null;

  /// Converts ISO 8601 UTC string to TimeOfDay in [doctorTimeZone]. Use for consistent display (Today list and Calendar).
  static TimeOfDay utcIsoToTimeOfDayInZone(String isoUtc, String? doctorTimeZone) {
    final zone = doctorTimeZone?.isNotEmpty == true ? doctorTimeZone! : 'UTC';
    debugPrint('utcIsoToTimeOfDayInZone: input=$isoUtc, targetZone=$zone');
    final result = _utcToTimeOfDayInZone(isoUtc, zone);
    debugPrint('utcIsoToTimeOfDayInZone: output=${result.hour}:${result.minute}');
    return result;
  }

  /// Parse ISO 8601 UTC (e.g. "2026-02-12T08:00:00Z") to TimeOfDay in [doctorTimeZone] (IANA).
  /// Backend must send ISO-8601 UTC with "Z". We defensively normalize naive timestamps to UTC.
  static TimeOfDay _utcToTimeOfDayInZone(String isoUtc, String doctorTimeZone) {
    final parsed = DateTime.parse(isoUtc);
    // Treat ALL backend timestamps as UTC instants. If backend forgets "Z", normalize safely.
    final utc = parsed.isUtc ? parsed : parsed.toUtc();
    return _instantToTimeOfDay(utc, doctorTimeZone);
  }

  static TimeOfDay _instantToTimeOfDay(DateTime utcInstant, String doctorTimeZone) {
    tz.Location loc;
    try {
      loc = tz.getLocation(doctorTimeZone);
    } catch (_) {
      loc = tz.UTC;
    }
    final local = tz.TZDateTime.from(utcInstant, loc);
    return TimeOfDay(hour: local.hour, minute: local.minute);
  }

  /// Build from backend JSON (GET /api/calendar). [startAt]/[endAt] are ISO 8601 UTC.
  /// [doctorTimeZone] is IANA id (e.g. Europe/Berlin) for display; fallback UTC if invalid.
  factory CalendarEntry.fromApi(
    Map<String, dynamic> j, {
    String? doctorTimeZone,
  }) {
    final startAt = (j['startAt'] as String?) ?? '';
    final endAt = (j['endAt'] as String?) ?? '';
    final zone = doctorTimeZone?.isNotEmpty == true ? doctorTimeZone! : 'UTC';

    final start = _utcToTimeOfDayInZone(startAt, zone);
    final end = _utcToTimeOfDayInZone(endAt, zone);
    final locationLabel = (j['locationLabel'] as String?)?.trim();
    final locationText = (j['location'] as String?)?.trim();
    final effectiveLocation = (locationLabel?.isNotEmpty == true)
        ? locationLabel!
        : (locationText ?? '');

    final typeStr = (j['type'] as String?)?.toUpperCase();

    if (typeStr == 'APPOINTMENT') {
      final reason = (j['reason'] as String?) ?? '';
      final pname = j['patientName'] as String?;
      final isVideo = effectiveLocation.toLowerCase().contains('video');
      final photoUrl =
          (j['patientPhotoUrl'] as String?) ?? (j['photoUrl'] as String?);
      final appointmentId = _intFromJson(j['appointmentId']);
      final patientId = _intFromJson(j['patientId']);
      final status = j['status'] as String?;

      return CalendarEntry.appointment(
        start: start,
        end: end,
        startAtUtc: startAt,
        endAtUtc: endAt,
        patientName: pname,
        location: effectiveLocation,
        reason: reason,
        isVideo: isVideo,
        photoUrl: photoUrl,
        appointmentId: appointmentId,
        patientId: patientId,
        status: status,
      );
    }

    return CalendarEntry.freeSlot(
      start: start,
      end: end,
      startAtUtc: startAt,
      endAtUtc: endAt,
      location: effectiveLocation,
    );
  }

  static int? _intFromJson(dynamic v) {
    if (v == null) return null;
    if (v is int) return v;
    if (v is double) return v.toInt();
    return null;
  }
}
