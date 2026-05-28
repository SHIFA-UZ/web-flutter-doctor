import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

/// Occupancy stats for one calendar day, derived from cached [CalendarEntry] rows.
class CalendarDayOccupancy {
  const CalendarDayOccupancy({
    required this.freeSlots,
    required this.appointments,
  });

  final int freeSlots;
  final int appointments;

  int get totalSlots => freeSlots + appointments;

  /// Null when the day has no schedule (no slots at all).
  double? get freeRatio =>
      totalSlots == 0 ? null : freeSlots / totalSlots;

  static CalendarDayOccupancy fromEntries(List<CalendarEntry> entries) {
    var free = 0;
    var booked = 0;
    for (final e in entries) {
      if (e.type == EntryType.freeSlot) {
        free++;
      } else {
        booked++;
      }
    }
    return CalendarDayOccupancy(freeSlots: free, appointments: booked);
  }
}

Color? occupancyBackgroundColor(double? freeRatio) {
  if (freeRatio == null) return null;

  if (freeRatio >= 1.0) return AppColors.secondaryLight;
  if (freeRatio <= 0.0) return AppColors.primaryTeal;

  // Light (all free) → mid → dark (fully booked) as freeRatio drops.
  if (freeRatio >= 0.5) {
    final t = (freeRatio - 0.5) / 0.5;
    return Color.lerp(AppColors.primaryLight, AppColors.secondaryLight, t)!;
  }
  final t = freeRatio / 0.5;
  return Color.lerp(AppColors.primaryTeal, AppColors.primaryLight, t)!;
}

Color occupancyTextColor(double? freeRatio) {
  if (freeRatio == null) return Colors.grey.shade800;
  // Darker backgrounds need white text for contrast.
  return freeRatio < 0.35 ? Colors.white : Colors.grey.shade800;
}
