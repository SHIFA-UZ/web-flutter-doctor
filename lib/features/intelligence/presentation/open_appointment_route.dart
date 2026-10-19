import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';

/// Parsed navigation target for an Intelligence `open_appointment` card.
class OpenAppointmentRoutePlan {
  const OpenAppointmentRoutePlan({
    required this.appointment,
    required this.routeName,
    required this.autoOpenKey,
  });

  final Appointment appointment;
  final String routeName;
  final String autoOpenKey;
}

/// Session guard so a rebuilt or remounted card does not push the same visit again.
class OpenAppointmentAutoOpen {
  static final Set<String> _claimed = <String>{};

  static bool claim(String key) => _claimed.add(key);

  static void resetForTest() => _claimed.clear();
}

/// Returns null when the payload cannot build the appointment the destination screen needs.
OpenAppointmentRoutePlan? planOpenAppointmentRoute(
  Map<String, dynamic> payload,
) {
  final raw = payload['appointment'];
  if (raw is! Map) return null;
  final row = Map<String, dynamic>.from(raw);
  final id = row['appointmentId']?.toString();
  final location = row['location']?.toString().trim();
  final day = DateTime.tryParse(row['day']?.toString() ?? '');
  final start = _time(row['startLocal']?.toString());
  final end = _time(row['endLocal']?.toString());
  final status = AppointmentStatus.fromString(row['status']?.toString());
  final route = (payload['route'] ?? row['route'])?.toString();
  if (id == null ||
      id.isEmpty ||
      location == null ||
      location.isEmpty ||
      day == null ||
      start == null ||
      end == null ||
      status == null ||
      (route != 'inPerson' && route != 'video')) {
    return null;
  }
  if (status == AppointmentStatus.noShow ||
      status == AppointmentStatus.cancelled) {
    return null;
  }
  final routeName = route == 'video' ? AppRoutes.videoCall : AppRoutes.inPerson;
  final startAt = row['startAt']?.toString() ?? '';
  return OpenAppointmentRoutePlan(
    appointment: Appointment(
      id: id,
      patientName: row['patientName']?.toString().trim().isNotEmpty == true
          ? row['patientName'].toString()
          : 'Patient',
      patientId: row['patientId']?.toString(),
      location: location,
      start: start,
      end: end,
      status: status,
      reason: row['reason']?.toString(),
      day: day,
    ),
    routeName: routeName,
    autoOpenKey: '$id|$routeName|${status.name}|$startAt',
  );
}

TimeOfDay? _time(String? raw) {
  final parts = (raw ?? '').split(':');
  if (parts.length < 2) return null;
  final hour = int.tryParse(parts[0]);
  final minute = int.tryParse(parts[1]);
  if (hour == null ||
      minute == null ||
      hour < 0 ||
      hour > 23 ||
      minute < 0 ||
      minute > 59) {
    return null;
  }
  return TimeOfDay(hour: hour, minute: minute);
}
