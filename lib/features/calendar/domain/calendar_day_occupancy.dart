import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

/// Occupancy stats for one calendar day, derived from cached [CalendarEntry] rows.
class CalendarDayOccupancy {
  const CalendarDayOccupancy({
    required this.freeSlots,
    required this.appointments,
    required this.blocked,
  });

  final int freeSlots;
  final int appointments;
  final int blocked;

  int get occupiedSlots => appointments + blocked;

  int get totalSlots => freeSlots + occupiedSlots;

  /// Null when the day has no schedule (no slots at all).
  double? get freeRatio =>
      totalSlots == 0 ? null : freeSlots / totalSlots;

  static CalendarDayOccupancy fromEntries(List<CalendarEntry> entries) {
    var free = 0;
    var booked = 0;
    var blocked = 0;
    for (final e in entries) {
      switch (e.type) {
        case EntryType.freeSlot:
          free++;
        case EntryType.appointment:
          booked++;
        case EntryType.blocked:
          blocked++;
      }
    }
    return CalendarDayOccupancy(
      freeSlots: free,
      appointments: booked,
      blocked: blocked,
    );
  }
}

Color? occupancyBackgroundColor(double? freeRatio) {
  if (freeRatio == null) return null;

  // Completely free — no fill (plain day number).
  if (freeRatio >= 1.0) return null;

  // Fully booked — explicit dark fill.
  if (freeRatio <= 0.0) return Colors.grey.shade900;

  // Partially booked — subtle mid-tone gradient as availability drops.
  if (freeRatio >= 0.5) {
    final t = (freeRatio - 0.5) / 0.5;
    return Color.lerp(Colors.grey.shade300, Colors.grey.shade100, t);
  }
  final t = freeRatio / 0.5;
  return Color.lerp(Colors.grey.shade700, Colors.grey.shade300, t);
}

Color occupancyTextColor(double? freeRatio) {
  if (freeRatio == null) return Colors.grey.shade800;
  if (freeRatio >= 1.0) return Colors.grey.shade800;
  if (freeRatio <= 0.0) return Colors.white;
  return freeRatio < 0.35 ? Colors.white : Colors.grey.shade800;
}
