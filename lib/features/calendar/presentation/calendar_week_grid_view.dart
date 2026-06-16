import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_grid_display.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';

/// Google Calendar–style week grid: hour labels on the left, day columns on top.
class CalendarWeekGridView extends StatelessWidget {
  const CalendarWeekGridView({
    super.key,
    required this.days,
    required this.entriesForDay,
    required this.onTapEntry,
    required this.selectedEntry,
    required this.brand,
    this.loading = false,
    this.shrinkWrap = false,
    this.maxGridHeight = 420,
  });

  final List<DateTime> days;
  final List<CalendarEntry> Function(DateTime day) entriesForDay;
  final void Function(CalendarEntry entry, DateTime day) onTapEntry;
  final CalendarEntry? selectedEntry;
  final Color brand;
  final bool loading;

  /// When true, the grid uses a fixed max height instead of expanding (stacked staff rows).
  final bool shrinkWrap;

  final double maxGridHeight;

  static const double hourHeight = 56;
  static const double timeGutterWidth = 52;
  static const int defaultStartHour = 7;
  static const int defaultEndHour = 20;

  String _two(int n) => n.toString().padLeft(2, '0');

  int _entryStartMinutes(CalendarEntry e) =>
      e.start.hour * 60 + e.start.minute;

  int _entryEndMinutes(CalendarEntry e) => e.end.hour * 60 + e.end.minute;

  ({int startHour, int endHour}) _gridBounds() {
    var startHour = defaultStartHour;
    var endHour = defaultEndHour;

    for (final day in days) {
      for (final e in entriesForDay(day)) {
        final startMin = _entryStartMinutes(e);
        final endMin = _entryEndMinutes(e);
        if (startMin < startHour * 60) {
          startHour = (startMin ~/ 60).clamp(0, 23);
        }
        final endH = ((endMin + 59) ~/ 60).clamp(1, 24);
        if (endH > endHour) endHour = endH;
      }
    }

    if (endHour <= startHour) endHour = startHour + 1;
    return (startHour: startHour, endHour: endHour);
  }

  String _weekdayShort(BuildContext context, DateTime day) {
    final l10n = AppLocalizations.of(context)!;
    return switch (day.weekday) {
      DateTime.monday => l10n.monday,
      DateTime.tuesday => l10n.tuesday,
      DateTime.wednesday => l10n.wednesday,
      DateTime.thursday => l10n.thursday,
      DateTime.friday => l10n.friday,
      DateTime.saturday => l10n.saturday,
      DateTime.sunday => l10n.sunday,
      _ => '',
    };
  }

  bool _isSameDay(DateTime a, DateTime b) =>
      a.year == b.year && a.month == b.month && a.day == b.day;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    if (loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (days.isEmpty) {
      return Center(
        child: Text(
          l10n.translate('selectDatesToSeeSchedule') ??
              'Select dates to see your schedule',
          style: TextStyle(color: Colors.grey.shade600),
        ),
      );
    }

    final bounds = _gridBounds();
    final hourCount = bounds.endHour - bounds.startHour;
    final gridHeight = hourCount * hourHeight;
    final today = DateTime.now();
    final nowMinutes = today.hour * 60 + today.minute;

    final gridDecoration = BoxDecoration(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      boxShadow: [
        BoxShadow(
          color: Colors.black.withOpacity(0.04),
          blurRadius: 8,
          offset: const Offset(0, 4),
        ),
      ],
    );

    final gridContent = SizedBox(
      height: gridHeight,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _TimeGutter(
            startHour: bounds.startHour,
            endHour: bounds.endHour,
            hourHeight: hourHeight,
            width: timeGutterWidth,
            formatHour: (h) => '${_two(h)}:00',
          ),
          Expanded(
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                for (var i = 0; i < days.length; i++) ...[
                  if (i > 0)
                    VerticalDivider(
                      width: 1,
                      thickness: 1,
                      color: Colors.grey.shade200,
                    ),
                  Expanded(
                    child: _DayColumn(
                      day: days[i],
                      entries: entriesForDay(days[i]),
                      startHour: bounds.startHour,
                      endHour: bounds.endHour,
                      hourHeight: hourHeight,
                      brand: brand,
                      selectedEntry: selectedEntry,
                      onTapEntry: (e) => onTapEntry(e, days[i]),
                      showNowLine: _isSameDay(days[i], today) &&
                          nowMinutes >= bounds.startHour * 60 &&
                          nowMinutes <= bounds.endHour * 60,
                      nowTop: (nowMinutes - bounds.startHour * 60) /
                          60 *
                          hourHeight,
                    ),
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );

    final gridPanel = DecoratedBox(
      decoration: gridDecoration,
      child: ClipRRect(
        borderRadius: BorderRadius.circular(12),
        child: SingleChildScrollView(
          child: gridContent,
        ),
      ),
    );

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _DayHeaderRow(
          days: days,
          timeGutterWidth: timeGutterWidth,
          brand: brand,
          weekdayLabel: (d) => _weekdayShort(context, d),
          isToday: (d) => _isSameDay(d, today),
        ),
        const SizedBox(height: 4),
        if (shrinkWrap)
          ConstrainedBox(
            constraints: BoxConstraints(maxHeight: maxGridHeight),
            child: gridPanel,
          )
        else
          Expanded(child: gridPanel),
      ],
    );
  }
}

class _DayHeaderRow extends StatelessWidget {
  const _DayHeaderRow({
    required this.days,
    required this.timeGutterWidth,
    required this.brand,
    required this.weekdayLabel,
    required this.isToday,
  });

  final List<DateTime> days;
  final double timeGutterWidth;
  final Color brand;
  final String Function(DateTime) weekdayLabel;
  final bool Function(DateTime) isToday;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        SizedBox(width: timeGutterWidth),
        Expanded(
          child: Row(
            children: [
              for (var i = 0; i < days.length; i++)
                Expanded(
                  child: _DayHeaderCell(
                    day: days[i],
                    weekday: weekdayLabel(days[i]),
                    brand: brand,
                    isToday: isToday(days[i]),
                  ),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _DayHeaderCell extends StatelessWidget {
  const _DayHeaderCell({
    required this.day,
    required this.weekday,
    required this.brand,
    required this.isToday,
  });

  final DateTime day;
  final String weekday;
  final Color brand;
  final bool isToday;

  @override
  Widget build(BuildContext context) {
    final weekdayShort = weekday.length > 3 ? weekday.substring(0, 3) : weekday;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          weekdayShort.toUpperCase(),
          style: TextStyle(
            fontSize: 11,
            fontWeight: FontWeight.w600,
            color: isToday ? brand : Colors.grey.shade600,
            letterSpacing: 0.5,
          ),
        ),
        const SizedBox(height: 4),
        Container(
          width: 32,
          height: 32,
          alignment: Alignment.center,
          decoration: BoxDecoration(
            color: isToday ? brand : Colors.transparent,
            shape: BoxShape.circle,
          ),
          child: Text(
            '${day.day}',
            style: TextStyle(
              fontSize: 15,
              fontWeight: FontWeight.w700,
              color: isToday ? Colors.white : Colors.grey.shade800,
            ),
          ),
        ),
      ],
    );
  }
}

class _TimeGutter extends StatelessWidget {
  const _TimeGutter({
    required this.startHour,
    required this.endHour,
    required this.hourHeight,
    required this.width,
    required this.formatHour,
  });

  final int startHour;
  final int endHour;
  final double hourHeight;
  final double width;
  final String Function(int hour) formatHour;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: width,
      child: Column(
        children: [
          for (var h = startHour; h < endHour; h++)
            SizedBox(
              height: hourHeight,
              child: Align(
                alignment: Alignment.topRight,
                child: Padding(
                  padding: const EdgeInsets.only(right: 8, top: 2),
                  child: Text(
                    formatHour(h),
                    style: TextStyle(
                      fontSize: 11,
                      fontWeight: FontWeight.w500,
                      color: Colors.grey.shade600,
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _DayColumn extends StatelessWidget {
  const _DayColumn({
    required this.day,
    required this.entries,
    required this.startHour,
    required this.endHour,
    required this.hourHeight,
    required this.brand,
    required this.selectedEntry,
    required this.onTapEntry,
    required this.showNowLine,
    required this.nowTop,
  });

  final DateTime day;
  final List<CalendarEntry> entries;
  final int startHour;
  final int endHour;
  final double hourHeight;
  final Color brand;
  final CalendarEntry? selectedEntry;
  final ValueChanged<CalendarEntry> onTapEntry;
  final bool showNowLine;
  final double nowTop;

  @override
  Widget build(BuildContext context) {
    final hourCount = endHour - startHour;
    final gridHeight = hourCount * hourHeight;
    final gridStartMinutes = startHour * 60;

    final cardEntries = entries
        .where(
          (e) =>
              e.type == EntryType.appointment || e.type == EntryType.blocked,
        )
        .toList();
    final freeRanges = mergeFreeSlotRanges(entries);

    return SizedBox(
      height: gridHeight,
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          Column(
            children: [
              for (var h = startHour; h < endHour; h++)
                Container(
                  height: hourHeight,
                  decoration: BoxDecoration(
                    border: Border(
                      top: BorderSide(color: Colors.grey.shade200, width: 1),
                    ),
                  ),
                ),
            ],
          ),
          for (final range in freeRanges)
            Builder(
              builder: (context) {
                final startMin =
                    range.start.hour * 60 + range.start.minute;
                final endMin = range.end.hour * 60 + range.end.minute;
                final top =
                    (startMin - gridStartMinutes) / 60.0 * hourHeight + 1;
                final height =
                    ((endMin - startMin) / 60.0 * hourHeight - 2)
                        .clamp(8.0, 9999.0);
                final isSelected =
                    identical(range.anchorEntry, selectedEntry);

                return Positioned(
                  top: top,
                  left: 2,
                  right: 2,
                  height: height,
                  child: _FreeSlotHitTarget(
                    brand: brand,
                    isSelected: isSelected,
                    onTap: () => onTapEntry(range.anchorEntry),
                  ),
                );
              },
            ),
          for (final e in cardEntries)
            Builder(
              builder: (context) {
                final startMin = e.start.hour * 60 + e.start.minute;
                final endMin = e.end.hour * 60 + e.end.minute;
                final top =
                    (startMin - gridStartMinutes) / 60.0 * hourHeight + 1;
                final height =
                    ((endMin - startMin) / 60.0 * hourHeight - 2)
                        .clamp(22.0, 9999.0);
                final isSelected = identical(e, selectedEntry);

                return Positioned(
                  top: top,
                  left: 2,
                  right: 2,
                  height: height,
                  child: _GridEntryBlock(
                    entry: e,
                    brand: brand,
                    isSelected: isSelected,
                    onTap: () => onTapEntry(e),
                  ),
                );
              },
            ),
          if (showNowLine)
            Positioned(
              top: nowTop,
              left: 0,
              right: 0,
              child: IgnorePointer(
                child: Container(
                  height: 2,
                  color: Colors.red.shade400,
                ),
              ),
            ),
        ],
      ),
    );
  }
}

/// Invisible tap target for merged free-slot ranges (Google Calendar style).
class _FreeSlotHitTarget extends StatelessWidget {
  const _FreeSlotHitTarget({
    required this.brand,
    required this.isSelected,
    required this.onTap,
  });

  final Color brand;
  final bool isSelected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(4),
        child: Container(
          decoration: BoxDecoration(
            color: isSelected ? brand.withOpacity(0.08) : Colors.transparent,
            borderRadius: BorderRadius.circular(4),
            border: isSelected
                ? Border.all(color: brand, width: 2)
                : Border.all(color: Colors.transparent, width: 0),
          ),
        ),
      ),
    );
  }
}

class _GridEntryBlock extends StatelessWidget {
  const _GridEntryBlock({
    required this.entry,
    required this.brand,
    required this.isSelected,
    required this.onTap,
  });

  final CalendarEntry entry;
  final Color brand;
  final bool isSelected;
  final VoidCallback onTap;

  String _two(int n) => n.toString().padLeft(2, '0');

  String _timeRange() =>
      '${_two(entry.start.hour)}:${_two(entry.start.minute)} – '
      '${_two(entry.end.hour)}:${_two(entry.end.minute)}';

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;

    late Color bg;
    late Color border;
    late Color textColor;
    late String title;
    IconData? icon;

    switch (entry.type) {
      case EntryType.appointment:
        bg = brand.withOpacity(0.12);
        border = brand;
        textColor = brand.withOpacity(0.95);
        title = entry.patientName ?? l10n.appointments;
        icon = Icons.person_outline;
      case EntryType.blocked:
        bg = Colors.red.shade50;
        border = Colors.red.shade300;
        textColor = Colors.red.shade800;
        title = entry.blockReason?.trim().isNotEmpty == true
            ? entry.blockReason!.trim()
            : (l10n.translate('blockedTime') ?? 'Blocked');
        icon = Icons.block;
      case EntryType.freeSlot:
        bg = Colors.transparent;
        border = Colors.transparent;
        textColor = Colors.transparent;
        title = '';
        icon = null;
    }

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(6),
        child: Container(
          decoration: BoxDecoration(
            color: bg,
            borderRadius: BorderRadius.circular(6),
            border: Border.all(
              color: isSelected ? brand : border,
              width: isSelected ? 2 : 1,
            ),
          ),
          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  if (icon != null) ...[
                    Icon(icon, size: 12, color: textColor),
                    const SizedBox(width: 4),
                  ],
                  Expanded(
                    child: Text(
                      title,
                      style: TextStyle(
                        fontSize: 11,
                        fontWeight: FontWeight.w600,
                        color: textColor,
                      ),
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ],
              ),
              Text(
                _timeRange(),
                style: TextStyle(
                  fontSize: 10,
                  color: textColor.withOpacity(0.85),
                ),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
      ),
    );
  }
}
