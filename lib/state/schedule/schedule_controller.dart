// lib/state/schedule/schedule_controller.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_models.dart';

class ScheduleState {
  final Map<String, List<TimeSlot>> slots; // per-day ranges
  final DateTime? startDate; // validity: from when (optional)
  final DateTime endDate; // validity: until when
  ScheduleState({required this.slots, this.startDate, required this.endDate});

  ScheduleState copyWith({
    Map<String, List<TimeSlot>>? slots,
    DateTime? startDate,
    DateTime? endDate,
  }) => ScheduleState(
    slots: slots ?? this.slots,
    startDate: startDate ?? this.startDate,
    endDate: endDate ?? this.endDate,
  );
}

class ScheduleController extends StateNotifier<ScheduleState> {
  ScheduleController()
    : super(
        ScheduleState(
          slots: {
            'Monday': [],
            'Tuesday': [],
            'Wednesday': [],
            'Thursday': [],
            'Friday': [],
            'Saturday': [],
            'Sunday': [], // add Sunday so UI matches backend 1..7
          },
          startDate: null,
          endDate: DateTime.now().add(const Duration(days: 60)),
        ),
      );

  static const List<String> _weekdayOrder = <String>[
    'Monday',
    'Tuesday',
    'Wednesday',
    'Thursday',
    'Friday',
    'Saturday',
    'Sunday',
  ];

  void setDayEnabled(String day, bool enabled) {
    final next = Map<String, List<TimeSlot>>.from(state.slots);
    if (enabled && !next.containsKey(day)) {
      next[day] = [];
    } else if (!enabled) {
      next.remove(day);
    }
    state = state.copyWith(slots: next);
  }

  void addSlot(String day, TimeSlot slot) {
    final list = List<TimeSlot>.from(state.slots[day] ?? []);
    list.add(slot);
    final next = Map<String, List<TimeSlot>>.from(state.slots);
    next[day] = list;
    state = state.copyWith(slots: next);
  }

  void updateSlot(String day, int index, TimeSlot slot) {
    final list = List<TimeSlot>.from(state.slots[day] ?? []);
    if (index >= 0 && index < list.length) {
      list[index] = slot;
      final next = Map<String, List<TimeSlot>>.from(state.slots);
      next[day] = list;
      state = state.copyWith(slots: next);
    }
  }

  void removeSlot(String day, int index) {
    final list = List<TimeSlot>.from(state.slots[day] ?? []);
    if (index >= 0 && index < list.length) {
      list.removeAt(index);
      final next = Map<String, List<TimeSlot>>.from(state.slots);
      next[day] = list;
      state = state.copyWith(slots: next);
    }
  }

  /// Replace [targetDay] schedule with a copy of [sourceDay] schedule.
  /// Returns true if copy succeeded (source had at least one slot).
  bool copyFromDay(String sourceDay, String targetDay) {
    final source = state.slots[sourceDay];
    if (source == null || source.isEmpty) return false;
    final next = Map<String, List<TimeSlot>>.from(state.slots);
    next[targetDay] = List<TimeSlot>.from(source);
    state = state.copyWith(slots: next);
    return true;
  }

  /// Copy schedule from the previous weekday in [_weekdayOrder] into [day].
  /// Returns true if there was a previous day with at least one slot.
  bool copyFromPreviousDay(String day) {
    final idx = _weekdayOrder.indexOf(day);
    if (idx <= 0) return false;
    final previousDay = _weekdayOrder[idx - 1];
    return copyFromDay(previousDay, day);
  }

  void setStartDate(DateTime? startDate) {
    state = state.copyWith(startDate: startDate);
  }

  void setEndDate(DateTime endDate) {
    state = state.copyWith(endDate: endDate);
  }

  /// Map UI -> backend DTOs
  List<RuleDto> toRuleDtos() {
    const reverseWeekdays = <String, int>{
      'Monday': 1,
      'Tuesday': 2,
      'Wednesday': 3,
      'Thursday': 4,
      'Friday': 5,
      'Saturday': 6,
      'Sunday': 7,
    };
    final result = <RuleDto>[];
    state.slots.forEach((day, ranges) {
      final wd = reverseWeekdays[day]!;
      for (final r in ranges) {
        String hhmm(TimeOfDay t) =>
            '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
        result.add(
          RuleDto(
            weekday: wd,
            startTime: hhmm(r.start),
            endTime: hhmm(r.end),
            slotMinutes: r.slotDuration.inMinutes,
          ),
        );
      }
    });
    return result;
  }

  /// Replace editor from backend rules
  void replaceWithBackendRules(List<RuleDto> rules) {
    const weekdays = <int, String>{
      1: 'Monday',
      2: 'Tuesday',
      3: 'Wednesday',
      4: 'Thursday',
      5: 'Friday',
      6: 'Saturday',
      7: 'Sunday',
    };
    final map = <String, List<TimeSlot>>{};
    for (final r in rules) {
      final dayName = weekdays[r.weekday]!;
      final s = r.startTime.split(':');
      final e = r.endTime.split(':');
      final start = TimeOfDay(hour: int.parse(s[0]), minute: int.parse(s[1]));
      final end = TimeOfDay(hour: int.parse(e[0]), minute: int.parse(e[1]));
      map
          .putIfAbsent(dayName, () => [])
          .add(
            TimeSlot(
              start: start,
              end: end,
              slotDuration: Duration(minutes: r.slotMinutes),
            ),
          );
    }
    state = state.copyWith(slots: map);
  }
}

final scheduleProvider =
    StateNotifierProvider<ScheduleController, ScheduleState>(
      (ref) => ScheduleController(),
    );
