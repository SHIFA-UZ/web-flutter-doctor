/* import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/schedule/domain/schedule_models.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_controller.dart';

class CalendarController
    extends StateNotifier<Map<DateTime, List<CalendarEntry>>> {
  CalendarController(this.ref) : super(<DateTime, List<CalendarEntry>>{}) {
    _generateFromSchedule();
    ref.listen<ScheduleState>(scheduleProvider, (previous, next) {
      _generateFromSchedule();
    });
  }

  final Ref ref;

  // Appointments are stored separately so schedule changes don't wipe them
  final Map<DateTime, List<CalendarEntry>> _appointmentsByDay = {};

  // ----------------------------- Public API -----------------------------

  void addAppointment(DateTime day, CalendarEntry appointment) {
    final key = _dayKey(day);
    final list = List<CalendarEntry>.from(_appointmentsByDay[key] ?? []);
    list.add(appointment);
    _appointmentsByDay[key] = list;
    _recomputeDay(key);
  }

  void bookFreeSlot({
    required DateTime day,
    required CalendarEntry slot,
    required String patientName,
    String location = 'Clinic Address',
    String reason = 'Check Up',
    bool isVideo = false,
  }) {
    assert(slot.type == EntryType.freeSlot, 'Can only book a free slot');
    final appt = CalendarEntry.appointment(
      start: slot.start,
      end: slot.end,
      patientName: patientName,
      location: location,
      reason: reason,
      isVideo: isVideo,
    );
    addAppointment(day, appt);
  }

  void clearAppointmentsForDay(DateTime day) {
    final key = _dayKey(day);
    _appointmentsByDay.remove(key);
    _recomputeDay(key);
  }

  // ----------------------------- Generation -----------------------------

  void _generateFromSchedule() {
    final schedule = ref.read(scheduleProvider);
    final startDate = _today();
    final endDate = schedule.endDate;

    if (!_isSameOrAfter(endDate, startDate)) {
      state = {};
      return;
    }

    final Map<DateTime, List<CalendarEntry>> next = {};

    for (
      DateTime d = startDate;
      _isSameOrBefore(d, endDate);
      d = d.add(const Duration(days: 1))
    ) {
      final key = _dayKey(d);
      final weekdayName = _weekdayName(d.weekday);
      final daySlots = schedule.slots[weekdayName] ?? const <TimeSlot>[];

      final freeSlots = <CalendarEntry>[];
      for (final period in daySlots) {
        freeSlots.addAll(_splitPeriodIntoFreeSlots(period));
      }

      final appointments = List<CalendarEntry>.from(
        _appointmentsByDay[key] ?? const <CalendarEntry>[],
      );

      final filteredFree = _removeOverlaps(freeSlots, appointments);

      final combined = [...appointments, ...filteredFree]..sort(_byStartTime);
      next[key] = combined;
    }

    state = next;
  }

  void _recomputeDay(DateTime key) {
    final schedule = ref.read(scheduleProvider);
    final DateTime d = key;
    final weekdayName = _weekdayName(d.weekday);
    final daySlots = schedule.slots[weekdayName] ?? const <TimeSlot>[];

    final freeSlots = <CalendarEntry>[];
    for (final period in daySlots) {
      freeSlots.addAll(_splitPeriodIntoFreeSlots(period));
    }

    final appointments = List<CalendarEntry>.from(
      _appointmentsByDay[key] ?? const <CalendarEntry>[],
    );
    final filteredFree = _removeOverlaps(freeSlots, appointments);

    final combined = [...appointments, ...filteredFree]..sort(_byStartTime);

    final next = Map<DateTime, List<CalendarEntry>>.from(state);
    next[key] = combined;
    state = next;
  }

  // ----------------------------- Helpers -----------------------------

  DateTime _dayKey(DateTime d) => DateTime(d.year, d.month, d.day);

  DateTime _today() {
    final now = DateTime.now();
    return DateTime(now.year, now.month, now.day);
  }

  bool _isSameOrBefore(DateTime a, DateTime b) {
    final ak = _dayKey(a);
    final bk = _dayKey(b);
    return ak.isBefore(bk) || ak.isAtSameMomentAs(bk);
  }

  bool _isSameOrAfter(DateTime a, DateTime b) {
    final ak = _dayKey(a);
    final bk = _dayKey(b);
    return ak.isAfter(bk) || ak.isAtSameMomentAs(bk);
  }

  String _weekdayName(int weekday) {
    const names = [
      'Monday',
      'Tuesday',
      'Wednesday',
      'Thursday',
      'Friday',
      'Saturday',
      'Sunday',
    ];
    return names[weekday - 1];
  }

  List<CalendarEntry> _splitPeriodIntoFreeSlots(TimeSlot slot) {
    final startMin = _toMinutes(slot.start);
    final endMin = _toMinutes(slot.end);
    final step = slot.slotDuration.inMinutes;

    final list = <CalendarEntry>[];
    for (int m = startMin; m + step <= endMin; m += step) {
      final s = _fromMinutes(m);
      final e = _fromMinutes(m + step);
      list.add(CalendarEntry.freeSlot(start: s, end: e));
    }
    return list;
  }

  List<CalendarEntry> _removeOverlaps(
    List<CalendarEntry> freeSlots,
    List<CalendarEntry> appointments,
  ) {
    bool overlaps(TimeOfDay s1, TimeOfDay e1, TimeOfDay s2, TimeOfDay e2) {
      final a0 = _toMinutes(s1), a1 = _toMinutes(e1);
      final b0 = _toMinutes(s2), b1 = _toMinutes(e2);
      return (a0 < b1) && (b0 < a1);
    }

    final result = <CalendarEntry>[];
    for (final fs in freeSlots) {
      final hasOverlap = appointments.any(
        (ap) => overlaps(fs.start, fs.end, ap.start, ap.end),
      );
      if (!hasOverlap) result.add(fs);
    }
    return result;
  }

  int _byStartTime(CalendarEntry a, CalendarEntry b) {
    final am = _toMinutes(a.start);
    final bm = _toMinutes(b.start);
    return am.compareTo(bm);
  }

  int _toMinutes(TimeOfDay t) => t.hour * 60 + t.minute;

  TimeOfDay _fromMinutes(int minutes) {
    int h = minutes ~/ 60;
    int m = minutes % 60;
    if (h < 0) h = 0;
    if (h > 23) h = 23;
    if (m < 0) m = 0;
    if (m > 59) m = 59;
    return TimeOfDay(hour: h, minute: m);
  }
}

final calendarProvider =
    StateNotifierProvider<
      CalendarController,
      Map<DateTime, List<CalendarEntry>>
    >((ref) => CalendarController(ref));
*/

import 'dart:convert';
import 'package:flutter/foundation.dart' show debugPrint;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/consecutive_slot_range.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:timezone/timezone.dart' as tz;

/// Response body could not be decoded into calendar rows ([loadDay] clears that day).
class CalendarDecodeFailure implements Exception {
  CalendarDecodeFailure(this.message);
  final String message;
  @override
  String toString() => message;
}

/// Throttle: avoid refetching the same day within this duration (reduces 429 rate-limit errors).
const _calendarThrottleDuration = Duration(seconds: 60);

/// Holds a map keyed by day (00:00 local), with list of entries for that day.
class CalendarController
    extends StateNotifier<Map<DateTime, List<CalendarEntry>>> {
  CalendarController(this.ref, {int? initialResourceDoctorId})
    : super(<DateTime, List<CalendarEntry>>{}) {
    if (initialResourceDoctorId != null) {
      _resourceDoctorId = initialResourceDoctorId;
    }
    // When auth token changes (logout or another doctor logs in), clear all cached days.
    ref.listen<String?>(authTokenProvider, (previous, next) {
      if (previous == next) return;
      _lastLoadTime.clear();
      _resourceDoctorId = null;
      state = <DateTime, List<CalendarEntry>>{};
    });
  }

  final Ref ref;

  final Map<DateTime, DateTime> _lastLoadTime = {};

  /// When set, calendar GET / book POST target this doctor profile (same clinic / receptionist view).
  int? _resourceDoctorId;

  int? get resourceDoctorId => _resourceDoctorId;

  void setResourceDoctorId(int? doctorProfileId) {
    if (_resourceDoctorId == doctorProfileId) return;
    _resourceDoctorId = doctorProfileId;
    _lastLoadTime.clear();
    state = <DateTime, List<CalendarEntry>>{};
  }

  DateTime _dayKey(DateTime d) => DateTime(d.year, d.month, d.day);

  String _ymd(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  int _byStartTime(CalendarEntry a, CalendarEntry b) {
    final am = a.start.hour * 60 + a.start.minute;
    final bm = b.start.hour * 60 + b.start.minute;
    return am.compareTo(bm);
  }

  /// Build ISO 8601 UTC string from local date/time in [doctorTimeZone] (IANA).
  static String localDateTimeToUtcIso(
    String doctorTimeZone,
    int year,
    int month,
    int day,
    int hour,
    int minute,
  ) {
    tz.Location loc;
    try {
      loc = tz.getLocation(doctorTimeZone);
    } catch (_) {
      loc = tz.UTC;
    }
    final local = tz.TZDateTime(loc, year, month, day, hour, minute);
    final utc = local.toUtc();
    return utc.toIso8601String();
  }

  static String _localToUtcIso(
    String doctorTimeZone,
    int year,
    int month,
    int day,
    int hour,
    int minute,
  ) =>
      localDateTimeToUtcIso(
        doctorTimeZone,
        year,
        month,
        day,
        hour,
        minute,
      );

  /// Fetch entries for a given day from backend and store in state.
  /// [doctorTimeZone] is an explicit dependency; caller (e.g. CalendarScreen) must pass it.
  /// Throttled: same day is not refetched within [_calendarThrottleDuration] to avoid 429 rate limits.
  /// BACKEND CONTRACT: All datetimes must be ISO-8601 UTC with "Z". We defensively normalize naive timestamps.
  Future<void> loadDay({
    required DateTime day,
    required String doctorTimeZone,
    bool forceRefresh = false,
  }) async {
    if (ref.read(authTokenProvider) == null ||
        ref.read(authTokenProvider)!.isEmpty) {
      return;
    }
    final key = _dayKey(day);
    final now = DateTime.now();
    final last = _lastLoadTime[key];
    if (!forceRefresh &&
        last != null &&
        now.difference(last) < _calendarThrottleDuration) {
      if (state.containsKey(key)) return;
    }
    _lastLoadTime[key] = now;

    try {
      final entries = await fetchCalendarDayFromApi(
        day: day,
        doctorTimeZone: doctorTimeZone,
        doctorProfileIdQuery: _resourceDoctorId,
      );
      final next = Map<DateTime, List<CalendarEntry>>.from(state);
      next[key] = entries;
      state = next;
    } on CalendarDecodeFailure catch (e, st) {
      debugPrint('Calendar decode error for day $_ymd(day): $e');
      debugPrint('$st');
      final next = Map<DateTime, List<CalendarEntry>>.from(state);
      next[key] = [];
      state = next;
      throw Exception('Calendar response invalid: $e');
    }
  }

  /// Prefetch all uncached days in [month] for month-grid occupancy coloring.
  Future<void> loadMonth({
    required DateTime month,
    required String doctorTimeZone,
  }) async {
    if (ref.read(authTokenProvider) == null ||
        ref.read(authTokenProvider)!.isEmpty) {
      return;
    }

    final first = DateTime(month.year, month.month, 1);
    final last = DateTime(month.year, month.month + 1, 0);
    final daysToLoad = <DateTime>[];

    for (var d = first; !d.isAfter(last); d = d.add(const Duration(days: 1))) {
      final key = _dayKey(d);
      if (!state.containsKey(key)) {
        daysToLoad.add(d);
      }
    }

    // Load in small batches so we do not saturate the browser connection pool
    // and starve other screens (e.g. patient documents) on the same origin.
    const batchSize = 4;
    for (var i = 0; i < daysToLoad.length; i += batchSize) {
      final batch = daysToLoad.skip(i).take(batchSize).toList();
      await Future.wait(
        batch.map(
          (d) => loadDay(day: d, doctorTimeZone: doctorTimeZone).catchError((_) {}),
        ),
      );
    }
  }

  /// Read-only GET for one day ([doctorProfileId] as `doctorId` query).
  ///
  /// Does not write [state], [_resourceDoctorId], or throttle maps — safe for dialogs.
  Future<List<CalendarEntry>> previewDayForDoctorProfile({
    required DateTime day,
    required String doctorTimeZone,
    required int doctorProfileId,
  }) async {
    if (ref.read(authTokenProvider) == null ||
        ref.read(authTokenProvider)!.isEmpty) {
      throw Exception('Not logged in.');
    }
    return fetchCalendarDayFromApi(
      day: day,
      doctorTimeZone: doctorTimeZone,
      doctorProfileIdQuery: doctorProfileId,
    );
  }

  /// Low-level GET + parse — no notifier cache side effects except calling [ref.read].
  Future<List<CalendarEntry>> fetchCalendarDayFromApi({
    required DateTime day,
    required String doctorTimeZone,
    int? doctorProfileIdQuery,
  }) async {
    // BACKEND CONTRACT: All datetimes must be ISO-8601 UTC with "Z".
    final client = ref.read(apiClientProvider);
    final params = <String, String>{'day': _ymd(day)};
    if (doctorProfileIdQuery != null) {
      params['doctorId'] = doctorProfileIdQuery.toString();
    }
    final resp = await client.get('/api/calendar', params: params);
    if (resp.statusCode == 200) {
      try {
        final body = utf8.decode(resp.bodyBytes);
        if (body.trim().isEmpty) {
          return [];
        }
        final decoded = json.decode(body);
        final List list = _extractCalendarList(decoded);
        final entries = <CalendarEntry>[];
        for (final item in list) {
          try {
            final map = item is Map<String, dynamic>
                ? item
                : Map<String, dynamic>.from(item as Map);
            final sa = map['startAt'] as String?;
            final ea = map['endAt'] as String?;
            if (sa == null || sa.isEmpty || ea == null || ea.isEmpty)
              continue;
            entries.add(
              CalendarEntry.fromApi(map, doctorTimeZone: doctorTimeZone),
            );
          } catch (e) {
            debugPrint('Calendar skip bad entry: $e');
          }
        }
        entries.sort(_byStartTime);
        return entries;
      } catch (e, st) {
        debugPrint('Calendar parse error for day $_ymd(day): $e');
        debugPrint('$st');
        throw CalendarDecodeFailure('$e');
      }
    }
    if (resp.statusCode == 401) {
      throw Exception('Unauthorized: please login again.');
    }
    throw Exception(
      'GET /api/calendar failed: ${resp.statusCode} ${resp.body}',
    );
  }

  /// Backend returns a JSON array; some setups wrap in { "content": [...] } or { "data": [...] }.
  static List _extractCalendarList(dynamic decoded) {
    if (decoded is List) return decoded;
    if (decoded is Map) {
      for (final key in ['content', 'data', 'entries']) {
        final value = decoded[key];
        if (value is List) return value;
      }
    }
    return [];
  }

  int? _actingDoctorId(int? override) => override ?? _resourceDoctorId;

  /// Book a FREE_SLOT on the backend, then refresh the day.
  /// [doctorTimeZone] is used for post-book refresh; caller must pass it.
  ///
  /// [endExclusive] optional wall end (doctor-local [day]); defaults to slot's encoded end row.
  /// On 409/400 the day is refreshed (race / stale selections) before throwing.
  Future<void> bookFreeSlotRemote({
    required DateTime day,
    required CalendarEntry slot,
    required int patientId,
    required String doctorTimeZone,
    String location = 'Clinic Address',
    String? reason,
    bool isVideo = false,
    TimeOfDay? endExclusive,
    int? actingAsDoctorProfileId,
  }) async {
    assert(slot.type == EntryType.freeSlot, 'Can only book a free slot');
    final startAtUtc = slot.startAtUtc;
    if (startAtUtc == null || startAtUtc.isEmpty) {
      throw Exception(
        'Slot has no startAt (UTC); cannot book. Refresh the calendar.',
      );
    }

    // Only block past *days*: allow all slots on today, including morning slots.
    // CRITICAL: Use doctor's timezone to determine "today" vs "past"
    // When device is in different timezone, "today" differs by timezone offset
    final todayInDoctorZone = getTodayInTimezone(doctorTimeZone);
    final slotDay = DateTime(day.year, day.month, day.day);
    if (slotDay.isBefore(todayInDoctorZone)) {
      throw Exception(
        'Cannot assign a patient to a past date. Please select today or a future day.',
      );
    }

    final client = ref.read(apiClientProvider);
    final endWall = endExclusive ?? slot.end;
    final slotMinutes = bookingSlotMinutesForRange(
      freeSlotStart: slot,
      endExclusiveWall: endWall,
      calendarDay: day,
      doctorTimeZone: doctorTimeZone,
    );
    if (slotMinutes <= 0) {
      throw Exception(
        'Selected end must be after the slot start. Please refresh the calendar.',
      );
    }

    final body = <String, dynamic>{
      'startAt': startAtUtc,
      'slotMinutes': slotMinutes,
      'patientId': patientId,
      'location': isVideo ? 'Video Consultation' : location,
      'reason': reason,
      'isVideo': isVideo,
    };
    // Backend validates consecutive slots with [PatientDaySlotsService] filtered by
    // location; without this, BookingController assumes primary location and rejects
    // ranges that belong to another venue (calendar still shows merged free slots).
    if (!isVideo && slot.locationId != null) {
      body['locationId'] = slot.locationId;
    }
    if (_actingDoctorId(actingAsDoctorProfileId) != null) {
      body['resourceDoctorId'] = _actingDoctorId(actingAsDoctorProfileId);
    }

    final res = await client.post('/api/schedule/book', body);

    if (res.statusCode == 200 || res.statusCode == 201) {
      await loadDay(
        day: day,
        doctorTimeZone: doctorTimeZone,
        forceRefresh: true,
      );
      return;
    }

    if (res.statusCode == 409 || res.statusCode == 400) {
      await loadDay(
        day: day,
        doctorTimeZone: doctorTimeZone,
        forceRefresh: true,
      );
    }

    throw Exception('Booking failed: ${res.statusCode} ${res.body}');
  }

  int _durationMinutes(TimeOfDay s, TimeOfDay e) =>
      (e.hour * 60 + e.minute) - (s.hour * 60 + s.minute);

  /// Cancel an appointment on the backend, then refresh the day.
  /// [doctorTimeZone] is used for post-cancel refresh; caller must pass it.
  Future<void> cancelAppointment({
    required int appointmentId,
    required DateTime day,
    required String doctorTimeZone,
  }) async {
    final client = ref.read(apiClientProvider);
    final res = await client.delete('/api/appointments/$appointmentId');

    if (res.statusCode == 200 || res.statusCode == 204) {
      await loadDay(
        day: day,
        doctorTimeZone: doctorTimeZone,
        forceRefresh: true,
      );
      return;
    }

    throw Exception('Cancel failed: ${res.statusCode} ${res.body}');
  }

  /// Change an appointment slot on the backend, then refresh the day.
  /// [doctorTimeZone] is required for _localToUtcIso and post-change refresh; caller must pass it.
  Future<void> changeAppointmentSlot({
    required int appointmentId,
    required DateTime day,
    required DateTime newDay,
    required TimeOfDay newStartTime,
    required int slotMinutes,
    required String doctorTimeZone,
  }) async {
    final startAtUtc = _localToUtcIso(
      doctorTimeZone,
      newDay.year,
      newDay.month,
      newDay.day,
      newStartTime.hour,
      newStartTime.minute,
    );

    final client = ref.read(apiClientProvider);
    final body = {'startAt': startAtUtc, 'slotMinutes': slotMinutes};

    final res = await client.put(
      '/api/appointments/$appointmentId/change',
      body,
    );

    if (res.statusCode == 200 || res.statusCode == 204) {
      await loadDay(
        day: day,
        doctorTimeZone: doctorTimeZone,
        forceRefresh: true,
      );
      if (day != newDay) {
        await loadDay(
          day: newDay,
          doctorTimeZone: doctorTimeZone,
          forceRefresh: true,
        );
      }
      return;
    }

    if (res.statusCode == 409 || res.statusCode == 400) {
      await loadDay(day: day, doctorTimeZone: doctorTimeZone, forceRefresh: true);
      await loadDay(
        day: newDay,
        doctorTimeZone: doctorTimeZone,
        forceRefresh: true,
      );
    }

    throw Exception('Change slot failed: ${res.statusCode} ${res.body}');
  }

  /// Ask backend to notify the patient to complete payment (video + pending only).
  Future<void> notifyPaymentReminder({required int appointmentId}) async {
    final client = ref.read(apiClientProvider);
    final res = await client.post(
      '/api/appointments/$appointmentId/notify-payment-reminder',
      <String, dynamic>{},
    );
    if (res.statusCode == 200 || res.statusCode == 204) {
      return;
    }
    throw Exception('Notify payment reminder failed: ${res.statusCode} ${res.body}');
  }

  /// Block free slots for a period (emergency / unavailability), then refresh affected days.
  /// Returns how many appointments were auto-cancelled by the backend.
  Future<int> createScheduleBlock({
    required DateTime day,
    required String startAtUtc,
    required String endAtUtc,
    required String doctorTimeZone,
    String? reason,
    bool cancelOverlappingAppointments = false,
    DateTime? refreshThroughDay,
    int? actingAsDoctorProfileId,
  }) async {
    final client = ref.read(apiClientProvider);
    final body = <String, dynamic>{
      'startAt': startAtUtc,
      'endAt': endAtUtc,
      'cancelOverlappingAppointments': cancelOverlappingAppointments,
      if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
      if (_actingDoctorId(actingAsDoctorProfileId) != null)
        'resourceDoctorId': _actingDoctorId(actingAsDoctorProfileId),
    };
    final res = await client.post('/api/schedule/blocks', body);
    if (res.statusCode != 200 && res.statusCode != 201) {
      throw Exception('Block failed: ${res.statusCode} ${res.body}');
    }

    var cancelledCount = 0;
    try {
      final decoded = json.decode(utf8.decode(res.bodyBytes));
      if (decoded is Map && decoded['cancelledAppointmentCount'] is num) {
        cancelledCount = (decoded['cancelledAppointmentCount'] as num).toInt();
      }
    } catch (_) {}

    final lastDay = refreshThroughDay ?? day;
    final firstKey = DateTime(day.year, day.month, day.day);
    final lastKey = DateTime(lastDay.year, lastDay.month, lastDay.day);
    for (var d = firstKey;
        !d.isAfter(lastKey);
        d = d.add(const Duration(days: 1))) {
      await loadDay(
        day: d,
        doctorTimeZone: doctorTimeZone,
        forceRefresh: true,
      );
    }
    return cancelledCount;
  }

  /// Preview how many appointments would be cancelled for a block range.
  Future<int> countOverlappingAppointmentsForBlock({
    required String startAtUtc,
    required String endAtUtc,
    int? actingAsDoctorProfileId,
  }) async {
    final client = ref.read(apiClientProvider);
    final params = <String, String>{
      'startAt': startAtUtc,
      'endAt': endAtUtc,
    };
    if (_actingDoctorId(actingAsDoctorProfileId) != null) {
      params['doctorId'] =
          _actingDoctorId(actingAsDoctorProfileId).toString();
    }
    final res = await client.get('/api/schedule/blocks/overlapping-count', params: params);
    if (res.statusCode != 200) {
      return 0;
    }
    try {
      final decoded = json.decode(utf8.decode(res.bodyBytes));
      if (decoded is Map && decoded['count'] is num) {
        return (decoded['count'] as num).toInt();
      }
    } catch (_) {}
    return 0;
  }

  /// Remove a schedule block, then refresh the day.
  Future<void> deleteScheduleBlock({
    required int blockId,
    required DateTime day,
    required String doctorTimeZone,
    int? actingAsDoctorProfileId,
  }) async {
    final client = ref.read(apiClientProvider);
    final actingId = _actingDoctorId(actingAsDoctorProfileId);
    final path = actingId != null
        ? '/api/schedule/blocks/$blockId?doctorId=$actingId'
        : '/api/schedule/blocks/$blockId';
    final res = await client.delete(path);
    if (res.statusCode != 200 && res.statusCode != 204) {
      throw Exception('Unblock failed: ${res.statusCode} ${res.body}');
    }
    await loadDay(
      day: day,
      doctorTimeZone: doctorTimeZone,
      forceRefresh: true,
    );
  }
}

/// Provider used by the screen.
final calendarProvider =
    StateNotifierProvider<
      CalendarController,
      Map<DateTime, List<CalendarEntry>>
    >((ref) => CalendarController(ref));

/// When set to an appointment id, CalendarScreen will load that day and select the slot.
/// Set by notification tap after resolving the appointment day (fetch before navigate).
final calendarGoToAppointmentIdProvider = StateProvider<int?>((ref) => null);

/// Resolved day for go-to-appointment. Set with calendarGoToAppointmentIdProvider before
/// navigating so CalendarScreen opens on this day from frame one (no "today" flash).
final calendarGoToAppointmentDayProvider = StateProvider<DateTime?>(
  (ref) => null,
);

/// When set, [CalendarScreen] opens the first available free slot for booking.
/// Used by home quick actions (e.g. video consultation).
class CalendarQuickBookIntent {
  const CalendarQuickBookIntent({this.preferVideoConsultation = false});

  final bool preferVideoConsultation;
}

final calendarQuickBookIntentProvider =
    StateProvider<CalendarQuickBookIntent?>((ref) => null);
