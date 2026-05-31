import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Selected dashboard / reports date range (inclusive calendar days).
class DashboardDateRange {
  const DashboardDateRange({required this.start, required this.end});

  final DateTime start;
  final DateTime end;

  int get dayCount {
    final s = DateTime(start.year, start.month, start.day);
    final e = DateTime(end.year, end.month, end.day);
    return e.difference(s).inDays + 1;
  }

  DashboardDateRange copyWith({DateTime? start, DateTime? end}) =>
      DashboardDateRange(start: start ?? this.start, end: end ?? this.end);
}

class DashboardDateRangeController extends StateNotifier<DashboardDateRange> {
  DashboardDateRangeController() : super(_defaultRange());

  static DashboardDateRange _defaultRange() {
    final now = DateTime.now();
    final end = DateTime(now.year, now.month, now.day);
    final start = end.subtract(const Duration(days: 6));
    return DashboardDateRange(start: start, end: end);
  }

  void setRange(DateTime start, DateTime end) {
    final s = DateTime(start.year, start.month, start.day);
    final e = DateTime(end.year, end.month, end.day);
    if (e.isBefore(s)) return;
    state = DashboardDateRange(start: s, end: e);
  }
}

final homeDashboardDateRangeProvider =
    StateNotifierProvider<DashboardDateRangeController, DashboardDateRange>(
  (ref) => DashboardDateRangeController(),
);

String formatDashboardDateRange(DashboardDateRange range, String locale) {
  final fmt = locale.startsWith('uz') || locale.startsWith('ru')
      ? 'd MMM'
      : 'd MMM';
  final startStr = _formatDate(range.start, fmt);
  final endStr = _formatDate(range.end, '$fmt, yyyy');
  return '$startStr – $endStr';
}

String _formatDate(DateTime d, String pattern) {
  const months = [
    'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
    'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec',
  ];
  if (pattern.contains('yyyy')) {
    return '${d.day} ${months[d.month - 1]}, ${d.year}';
  }
  return '${d.day} ${months[d.month - 1]}';
}
