import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

int todMinutes(TimeOfDay t) => t.hour * 60 + t.minute;

/// Individual free slots for grid hit-testing (Google Calendar style: one block per slot).
List<CalendarEntry> sortedFreeSlots(List<CalendarEntry> entries) {
  final free = entries.where((e) => e.type == EntryType.freeSlot).toList()
    ..sort((a, b) {
      final cmp = todMinutes(a.start).compareTo(todMinutes(b.start));
      if (cmp != 0) return cmp;
      return (a.locationId ?? -1).compareTo(b.locationId ?? -1);
    });
  return free;
}

/// Maps a vertical tap position in the day column to the free slot at that time.
CalendarEntry? freeSlotAtVerticalPosition({
  required List<CalendarEntry> freeSlots,
  required double localY,
  required int gridStartMinutes,
  required double hourHeight,
}) {
  if (freeSlots.isEmpty || localY < 0) return null;

  final tappedMinutes =
      gridStartMinutes + (localY / hourHeight * 60).floor();

  for (final slot in freeSlots) {
    final start = todMinutes(slot.start);
    final end = todMinutes(slot.end);
    if (tappedMinutes >= start && tappedMinutes < end) {
      return slot;
    }
  }
  return null;
}
