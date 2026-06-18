import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

/// Horizontal placement for a timed entry in a day column (Google Calendar style).
class CalendarEntryLayoutSlot {
  const CalendarEntryLayoutSlot({
    required this.entry,
    required this.columnIndex,
    required this.columnCount,
  });

  final CalendarEntry entry;
  final int columnIndex;
  final int columnCount;

  double get widthFraction => 1 / columnCount;
  double get leftFraction => columnIndex / columnCount;
}

int _startMinutes(CalendarEntry e) => e.start.hour * 60 + e.start.minute;

int _endMinutes(CalendarEntry e) => e.end.hour * 60 + e.end.minute;

bool calendarEntriesOverlap(CalendarEntry a, CalendarEntry b) {
  final aStart = _startMinutes(a);
  final aEnd = _endMinutes(a);
  final bStart = _startMinutes(b);
  final bEnd = _endMinutes(b);
  return aStart < bEnd && bStart < aEnd;
}

List<List<CalendarEntry>> _clusterOverlappingEntries(List<CalendarEntry> entries) {
  final sorted = [...entries]
    ..sort((a, b) {
      final byStart = _startMinutes(a).compareTo(_startMinutes(b));
      if (byStart != 0) return byStart;
      return _endMinutes(b).compareTo(_endMinutes(a));
    });

  final clusters = <List<CalendarEntry>>[];

  for (final entry in sorted) {
    var targetIndex = -1;
    for (var i = 0; i < clusters.length; i++) {
      if (clusters[i].any((e) => calendarEntriesOverlap(e, entry))) {
        targetIndex = targetIndex == -1 ? i : targetIndex;
        if (targetIndex != i) {
          clusters[targetIndex].addAll(clusters[i]);
          clusters.removeAt(i);
          i--;
        } else {
          clusters[i].add(entry);
        }
      }
    }
    if (targetIndex == -1) {
      clusters.add([entry]);
    }
  }

  return clusters;
}

/// Assigns side-by-side columns for entries that overlap in time.
List<CalendarEntryLayoutSlot> layoutOverlappingCalendarEntries(
  List<CalendarEntry> entries,
) {
  if (entries.isEmpty) return const [];

  final slots = <CalendarEntryLayoutSlot>[];
  for (final cluster in _clusterOverlappingEntries(entries)) {
    final columns = <List<CalendarEntry>>[];

    for (final entry in cluster) {
      var placed = false;
      for (final column in columns) {
        if (!column.any((e) => calendarEntriesOverlap(e, entry))) {
          column.add(entry);
          placed = true;
          break;
        }
      }
      if (!placed) {
        columns.add([entry]);
      }
    }

    final columnCount = columns.length;
    for (var col = 0; col < columns.length; col++) {
      for (final entry in columns[col]) {
        slots.add(
          CalendarEntryLayoutSlot(
            entry: entry,
            columnIndex: col,
            columnCount: columnCount,
          ),
        );
      }
    }
  }

  return slots;
}
