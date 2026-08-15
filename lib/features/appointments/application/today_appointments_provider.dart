// lib/features/appointments/application/today_appointments_provider.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/state/calendar/calendar_controller.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';

const _upcomingLookaheadDays = 14;

Appointment appointmentFromCalendarEntry(CalendarEntry e, DateTime day) {
  return Appointment(
    id: e.appointmentId.toString(),
    patientName: e.patientName!,
    patientId: e.patientId?.toString(),
    location: e.location,
    start: e.start,
    end: e.end,
    status: AppointmentStatus.fromString(e.status) ?? AppointmentStatus.confirmed,
    photoUrl: e.photoUrl,
    reason: e.reason.isNotEmpty ? e.reason : null,
    briefingStatus: e.briefingStatus,
    attachmentCount: e.attachmentCount,
    day: DateTime(day.year, day.month, day.day),
  );
}

List<Appointment> appointmentsFromCalendarEntries(
  List<CalendarEntry> entries,
  DateTime day,
) {
  final list = entries
      .where(
        (e) =>
            e.type == EntryType.appointment &&
            e.patientName != null &&
            e.patientName!.isNotEmpty &&
            e.appointmentId != null,
      )
      .map((e) => appointmentFromCalendarEntry(e, day))
      .toList()
    ..sort((a, b) {
      final am = a.start.hour * 60 + a.start.minute;
      final bm = b.start.hour * 60 + b.start.minute;
      return am.compareTo(bm);
    });
  return list;
}

Future<List<Appointment>> _loadAppointmentsForDay(
  Ref ref, {
  required DateTime day,
  required String doctorTimeZone,
}) async {
  final key = DateTime(day.year, day.month, day.day);
  var entries = ref.read(calendarProvider)[key];
  if (entries == null) {
    try {
      await ref.read(calendarProvider.notifier).loadDay(
            day: key,
            doctorTimeZone: doctorTimeZone,
          );
    } catch (_) {
      return const [];
    }
    entries = ref.read(calendarProvider)[key];
  }
  if (entries == null) return const [];
  return appointmentsFromCalendarEntries(entries, key);
}

/// Current visit if one is in progress, otherwise the next visit that has not
/// started yet. Past / completed / cancelled rows are skipped.
Appointment? pickNextUpcomingAppointment(
  List<Appointment> appointments,
  DateTime now,
  String? doctorTimeZone,
) {
  Appointment? inProgress;
  Appointment? nextUp;
  var nextUpStart = DateTime.fromMillisecondsSinceEpoch(0);

  for (final appt in appointments) {
    if (appt.isCompleted) continue;
    if (appt.status == AppointmentStatus.cancelled) continue;
    final day = appt.day ?? DateTime(now.year, now.month, now.day);
    final start = timeOfDayToDateTimeInZone(appt.start, day, doctorTimeZone);
    final end = timeOfDayToDateTimeInZone(appt.end, day, doctorTimeZone);
    if (!now.isBefore(start) && now.isBefore(end)) {
      inProgress = appt;
      break;
    }
    if (!start.isBefore(now) && (nextUp == null || start.isBefore(nextUpStart))) {
      nextUp = appt;
      nextUpStart = start;
    }
  }
  return inProgress ?? nextUp;
}

/// Today's appointments, reusing calendar data when possible to avoid duplicate
/// GET /api/calendar calls and 429 rate-limit errors (Home and Calendar share one fetch).
final todayAppointmentsProvider = FutureProvider<List<Appointment>>((ref) async {
  final profile = await ref.watch(profileAllProvider.future);
  final doctorTimeZone = profile.profile['timeZone'] as String?;
  if (doctorTimeZone == null || doctorTimeZone.isEmpty) return <Appointment>[];

  final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
  final todayKey =
      DateTime(todayInDoctorZone.year, todayInDoctorZone.month, todayInDoctorZone.day);

  return _loadAppointmentsForDay(
    ref,
    day: todayKey,
    doctorTimeZone: doctorTimeZone,
  );
});

/// Next visit to show on Home: remaining today, otherwise the soonest booking
/// in the next [_upcomingLookaheadDays] days.
final nextUpcomingAppointmentProvider = FutureProvider<Appointment?>((ref) async {
  final profile = await ref.watch(profileAllProvider.future);
  final doctorTimeZone = profile.profile['timeZone'] as String?;
  if (doctorTimeZone == null || doctorTimeZone.isEmpty) return null;

  final todayList = await ref.watch(todayAppointmentsProvider.future);
  final now = getNowInTimezone(doctorTimeZone);
  final fromToday = pickNextUpcomingAppointment(todayList, now, doctorTimeZone);
  if (fromToday != null) return fromToday;

  final todayKey = DateTime(now.year, now.month, now.day);
  for (var i = 1; i <= _upcomingLookaheadDays; i++) {
    final day = todayKey.add(Duration(days: i));
    final list = await _loadAppointmentsForDay(
      ref,
      day: day,
      doctorTimeZone: doctorTimeZone,
    );
    final found = pickNextUpcomingAppointment(list, now, doctorTimeZone);
    if (found != null) return found;
  }
  return null;
});
