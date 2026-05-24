// Multi-slot chaining for Doctor calendar. Mirrors Kotlin [SlotAvailabilityService]:
// UTC instants anchor slot boundaries; [CalendarEntry.locationId] must match throughout.
import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

DateTime utcInstantFromBackendIso(String iso) {
  final p = DateTime.parse(iso.trim());
  final u = p.isUtc ? p : p.toUtc();
  return DateTime.utc(
    u.year,
    u.month,
    u.day,
    u.hour,
    u.minute,
    u.second,
    u.millisecond,
    u.microsecond,
  );
}

Map<int, CalendarEntry> _mapFreeStartsMicros({
  required Iterable<CalendarEntry> venueSlots,
}) {
  final m = <int, CalendarEntry>{};
  for (final e in venueSlots) {
    if (e.type != EntryType.freeSlot) continue;
    final s = e.startAtUtc ?? '';
    if (s.trim().isEmpty) continue;
    m[utcInstantFromBackendIso(s).microsecondsSinceEpoch] = e;
  }
  return m;
}

Iterable<CalendarEntry> _sameVenueFreeSlots({
  required Iterable<CalendarEntry> slots,
  required int? venueId,
}) =>
    slots.where((e) => e.type == EntryType.freeSlot && e.locationId == venueId);

/// Walk half-open `[start, endExclusive)` UTC range using consecutive venue slots.
///
/// Tests compare against Kotlin [SlotAvailabilityService] expectations.
bool coversUtcRangeWithFreeSlotChain({
  required Iterable<CalendarEntry> dayFreeSlots,
  required DateTime rangeStartInclusiveUtc,
  required DateTime rangeEndExclusiveUtc,
  required int? anchorVenueId,
}) {
  final same = _sameVenueFreeSlots(slots: dayFreeSlots, venueId: anchorVenueId).toList();
  final by = _mapFreeStartsMicros(venueSlots: same);

  DateTime cur =
      DateTime.utc(
        rangeStartInclusiveUtc.year,
        rangeStartInclusiveUtc.month,
        rangeStartInclusiveUtc.day,
        rangeStartInclusiveUtc.hour,
        rangeStartInclusiveUtc.minute,
        rangeStartInclusiveUtc.second,
        rangeStartInclusiveUtc.millisecond,
        rangeStartInclusiveUtc.microsecond,
      );

  final end =
      DateTime.utc(
        rangeEndExclusiveUtc.year,
        rangeEndExclusiveUtc.month,
        rangeEndExclusiveUtc.day,
        rangeEndExclusiveUtc.hour,
        rangeEndExclusiveUtc.minute,
        rangeEndExclusiveUtc.second,
        rangeEndExclusiveUtc.millisecond,
        rangeEndExclusiveUtc.microsecond,
      );

  if (!end.isAfter(cur)) return false;

  while (cur.isBefore(end)) {
    final seg = by[cur.microsecondsSinceEpoch];
    final endIso = seg?.endAtUtc;
    if (seg == null || endIso == null || endIso.trim().isEmpty) {
      return false;
    }
    final nxtRaw = utcInstantFromBackendIso(endIso);
    final nxt =
        DateTime.utc(
          nxtRaw.year,
          nxtRaw.month,
          nxtRaw.day,
          nxtRaw.hour,
          nxtRaw.minute,
          nxtRaw.second,
          nxtRaw.millisecond,
          nxtRaw.microsecond,
        );

    if (!nxt.isAfter(cur)) return false;
    cur = nxt;
  }

  return cur.microsecondsSinceEpoch == end.microsecondsSinceEpoch;
}

/// Valid exclusive end-clock times chaining forward from [startSlot] within [dayEntries].
List<TimeOfDay> consecutiveEndTimesForFreeSlot({
  required Iterable<CalendarEntry> dayEntries,
  required CalendarEntry startSlot,
  required String doctorTimeZone,
}) {
  if (startSlot.type != EntryType.freeSlot) return const [];

  final anchorVenueId = startSlot.locationId;

  final by =
      _mapFreeStartsMicros(
        venueSlots:
            _sameVenueFreeSlots(slots: dayEntries, venueId: anchorVenueId),
      );

  final startUtc = utcInstantFromBackendIso(startSlot.startAtUtc ?? '');

  var cur =
      DateTime.utc(
        startUtc.year,
        startUtc.month,
        startUtc.day,
        startUtc.hour,
        startUtc.minute,
        startUtc.second,
        startUtc.millisecond,
        startUtc.microsecond,
      );

  final first = by[cur.microsecondsSinceEpoch];
  if (first == null) return const [];

  final outs = <TimeOfDay>[
    CalendarEntry.utcIsoToTimeOfDayInZone(first.endAtUtc!, doctorTimeZone),
  ];

  cur =
      utcInstantFromBackendIso(first.endAtUtc!); // advancing cursor at chain ends

  for (var i = 0; i < 400; i++) {
    final next = by[cur.microsecondsSinceEpoch];
    if (next == null) break;

    outs.add(CalendarEntry.utcIsoToTimeOfDayInZone(next.endAtUtc!, doctorTimeZone));

    final after = utcInstantFromBackendIso(next.endAtUtc!);

    cur =
        DateTime.utc(
          after.year,
          after.month,
          after.day,
          after.hour,
          after.minute,
          after.second,
          after.millisecond,
          after.microsecond,
        );
  }

  return outs;
}

/// POST /api/schedule/book payload `slotMinutes` from selected free-slot UTC start → wall end.
int bookingSlotMinutesForRange({
  required CalendarEntry freeSlotStart,
  required TimeOfDay endExclusiveWall,
  required DateTime calendarDay,
  required String doctorTimeZone,
}) {
  final startUtc = utcInstantFromBackendIso(freeSlotStart.startAtUtc ?? '');

  final endWall =
      timeOfDayToDateTimeInZone(endExclusiveWall, calendarDay, doctorTimeZone);

  final endUtc = DateTime.utc(
    endWall.year,
    endWall.month,
    endWall.day,
    endWall.hour,
    endWall.minute,
    endWall.second,
    endWall.millisecond,
    endWall.microsecond,
  ).toUtc();

  return endUtc.difference(startUtc).inMinutes.clamp(0, 48 * 60);
}

int _gcdInt(int a, int b) {
  var x = a.abs();
  var y = b.abs();
  while (y != 0) {
    final t = x % y;
    x = y;
    y = t;
  }
  return x == 0 ? 1 : x;
}

int _slotDurationUtcMinutes(CalendarEntry e) {
  final s = e.startAtUtc;
  final t = e.endAtUtc;
  if (s == null || s.trim().isEmpty || t == null || t.trim().isEmpty) {
    return ((e.end.hour * 60 + e.end.minute) -
            (e.start.hour * 60 + e.start.minute))
        .abs()
        .clamp(1, 48 * 60);
  }
  return utcInstantFromBackendIso(t)
      .difference(utcInstantFromBackendIso(s))
      .inMinutes
      .abs();
}

/// GCD of free-slot durations for one venue (`locationId`).
int scheduleGrainMinutesGuess({
  required Iterable<CalendarEntry> entries,
  required int? venueId,
  int defaultGrain = 30,
}) {
  final durations = entries
      .where((x) => x.type == EntryType.freeSlot && x.locationId == venueId)
      .map(_slotDurationUtcMinutes)
      .where((d) => d > 0)
      .toSet()
      .toList();

  if (durations.isEmpty) return defaultGrain.clamp(5, 240);
  return durations.fold(durations.first, _gcdInt).clamp(5, 240);
}

/// Free-slot chains continuing exactly at UTC boundary [cursorUtcStartIso].
List<TimeOfDay> extendEndWallsChainFromUtcBoundary({
  required Iterable<CalendarEntry> dayEntries,
  required String cursorUtcStartIso,
  required int? anchorVenueId,
  required String doctorTimeZone,
}) {
  final by =
      _mapFreeStartsMicros(
        venueSlots:
            _sameVenueFreeSlots(slots: dayEntries, venueId: anchorVenueId),
      );

  DateTime cursor = utcInstantFromBackendIso(cursorUtcStartIso);

  cursor =
      DateTime.utc(
        cursor.year,
        cursor.month,
        cursor.day,
        cursor.hour,
        cursor.minute,
        cursor.second,
        cursor.millisecond,
        cursor.microsecond,
      );

  final outs = <TimeOfDay>[];

  for (var i = 0; i < 400; i++) {
    final next = by[cursor.microsecondsSinceEpoch];
    if (next == null || (next.endAtUtc ?? '').trim().isEmpty) break;

    outs.add(CalendarEntry.utcIsoToTimeOfDayInZone(next.endAtUtc!, doctorTimeZone));

    final raw = utcInstantFromBackendIso(next.endAtUtc!);

    cursor =
        DateTime.utc(
          raw.year,
          raw.month,
          raw.day,
          raw.hour,
          raw.minute,
          raw.second,
          raw.millisecond,
          raw.microsecond,
        );

    if ((next.endAtUtc ?? '') == next.startAtUtc) break;
  }

  return outs;

}

/// Wall-clock exclusives spaced by grain along UTC interval `[start,end)`.
List<TimeOfDay> shorteningEndWallsGrainUtc({
  required String appointmentStartUtc,
  required String appointmentEndUtcExclusive,
  required int grainMinutes,
  required String doctorTimeZone,
}) {
  var w = utcInstantFromBackendIso(appointmentStartUtc);

  final endEx =
      utcInstantFromBackendIso(appointmentEndUtcExclusive);

  final outs = <TimeOfDay>[];

  final g = grainMinutes.clamp(5, 240);

  for (var iter = 0; iter < 400; iter++) {
    final nextUtcBase =
        DateTime.utc(
          w.year,
          w.month,
          w.day,
          w.hour,
          w.minute,
          w.second,
          w.millisecond,
          w.microsecond,
        ).add(Duration(minutes: g));

    final nextUtc =
        DateTime.utc(
          nextUtcBase.year,
          nextUtcBase.month,
          nextUtcBase.day,
          nextUtcBase.hour,
          nextUtcBase.minute,
          nextUtcBase.second,
          nextUtcBase.millisecond,
          nextUtcBase.microsecond,
        );

    if (!nextUtc.isBefore(endEx)) break;

    outs.add(
      CalendarEntry.utcIsoToTimeOfDayInZone(
        nextUtc.toUtc().toIso8601String(),
        doctorTimeZone,
      ),
    );

    w = nextUtc;

  }

  return outs;

}

/// `slotMinutes` for PUT /appointments/:id/change when start UTC is fixed but end moves.
int appointmentSlotMinutesUtcStartWallEnd({
  required String appointmentStartUtcIso,
  required TimeOfDay endExclusiveWall,
  required DateTime calendarDay,
  required String doctorTimeZone,
}) {
  final startUtc = utcInstantFromBackendIso(appointmentStartUtcIso);

  final endWall =
      timeOfDayToDateTimeInZone(endExclusiveWall, calendarDay, doctorTimeZone);

  final endUtc = DateTime.utc(
    endWall.year,
    endWall.month,
    endWall.day,
    endWall.hour,
    endWall.minute,
    endWall.second,
    endWall.millisecond,
    endWall.microsecond,
  ).toUtc();

  return endUtc.difference(startUtc.toUtc()).inMinutes.clamp(0, 48 * 60);
}

