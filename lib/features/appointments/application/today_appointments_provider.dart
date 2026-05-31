// lib/features/appointments/application/today_appointments_provider.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';

/// Today's appointments, reusing calendar data when possible to avoid duplicate
/// GET /api/calendar calls and 429 rate-limit errors (Home and Calendar share one fetch).
final todayAppointmentsProvider = FutureProvider<List<Appointment>>((ref) async {
  final profile = await ref.watch(profileAllProvider.future);
  final doctorTimeZone = profile.profile['timeZone'] as String?;
  if (doctorTimeZone == null || doctorTimeZone.isEmpty) return <Appointment>[];

  final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
  final todayKey = DateTime(todayInDoctorZone.year, todayInDoctorZone.month, todayInDoctorZone.day);

  final calendarState = ref.read(calendarProvider);
  List<CalendarEntry>? entries = calendarState[todayKey];

  if (entries == null) {
    await ref.read(calendarProvider.notifier).loadDay(day: todayKey, doctorTimeZone: doctorTimeZone);
    entries = ref.read(calendarProvider)[todayKey];
  }

  if (entries == null) return <Appointment>[];

  final list = entries
      .where((e) => e.type == EntryType.appointment && e.patientName != null && e.patientName!.isNotEmpty && e.appointmentId != null)
      .map((e) => Appointment(
            id: e.appointmentId.toString(),
            patientName: e.patientName!,
            patientId: e.patientId?.toString(),
            location: e.location,
            start: e.start,
            end: e.end,
            status: AppointmentStatus.fromString(e.status) ?? AppointmentStatus.confirmed,
            photoUrl: e.photoUrl,
            reason: e.reason.isNotEmpty ? e.reason : null,
          ))
      .toList()
    ..sort((a, b) {
      final am = a.start.hour * 60 + a.start.minute;
      final bm = b.start.hour * 60 + b.start.minute;
      return am.compareTo(bm);
    });

  return list;
});
