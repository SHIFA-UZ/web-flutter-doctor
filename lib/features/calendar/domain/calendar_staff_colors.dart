import 'package:flutter/material.dart';

/// Distinct accent colors for each doctor on multi-staff calendar views.
abstract final class CalendarStaffColors {
  static const List<Color> palette = [
    Color(0xFF00897B),
    Color(0xFF3949AB),
    Color(0xFF1E88E5),
    Color(0xFF8E24AA),
    Color(0xFFF4511E),
    Color(0xFF6D4C41),
    Color(0xFF546E7A),
    Color(0xFF7CB342),
  ];

  /// Stable color for [doctorProfileId] within a clinic roster ordering.
  static Color forDoctorInRoster(
    int doctorProfileId,
    List<int> rosterDoctorIds, {
    Color fallback = const Color(0xFF00897B),
  }) {
    final idx = rosterDoctorIds.indexOf(doctorProfileId);
    if (idx >= 0) return palette[idx % palette.length];
    return palette[doctorProfileId.abs() % palette.length];
  }
}
