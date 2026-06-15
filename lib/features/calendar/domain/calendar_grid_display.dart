import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

/// One contiguous free-slot band for grid hit-testing (Google Calendar style).
class FreeSlotDisplayRange {
  const FreeSlotDisplayRange({
    required this.start,
    required this.end,
    required this.anchorEntry,
  });

  final TimeOfDay start;
  final TimeOfDay end;

  /// First slot in the chain — used for booking and selection identity.
  final CalendarEntry anchorEntry;
}

int _todMinutes(TimeOfDay t) => t.hour * 60 + t.minute;

bool _sameVenue(int? a, int? b) => a == b;

bool _slotsAreAdjacent(CalendarEntry prev, CalendarEntry next) {
  return _todMinutes(prev.end) == _todMinutes(next.start);
}

/// Merges consecutive [EntryType.freeSlot] rows (same [CalendarEntry.locationId])
/// into display ranges for invisible grid tap targets.
List<FreeSlotDisplayRange> mergeFreeSlotRanges(List<CalendarEntry> entries) {
  final free = entries.where((e) => e.type == EntryType.freeSlot).toList()
    ..sort((a, b) {
      final cmp = _todMinutes(a.start).compareTo(_todMinutes(b.start));
      if (cmp != 0) return cmp;
      return (a.locationId ?? -1).compareTo(b.locationId ?? -1);
    });

  if (free.isEmpty) return const [];

  final ranges = <FreeSlotDisplayRange>[];
  var anchor = free.first;
  var rangeEnd = free.first.end;

  for (var i = 1; i < free.length; i++) {
    final slot = free[i];
    if (_sameVenue(anchor.locationId, slot.locationId) &&
        _slotsAreAdjacent(free[i - 1], slot)) {
      rangeEnd = slot.end;
    } else {
      ranges.add(
        FreeSlotDisplayRange(
          start: anchor.start,
          end: rangeEnd,
          anchorEntry: anchor,
        ),
      );
      anchor = slot;
      rangeEnd = slot.end;
    }
  }

  ranges.add(
    FreeSlotDisplayRange(
      start: anchor.start,
      end: rangeEnd,
      anchorEntry: anchor,
    ),
  );

  return ranges;
}
