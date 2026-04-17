// lib/state/schedule/schedule_models.dart
import 'package:flutter/material.dart';

class TimeSlot {
  final TimeOfDay start;
  final TimeOfDay end;
  final Duration slotDuration;
  TimeSlot({
    required this.start,
    required this.end,
    required this.slotDuration,
  });
}

/// Backend DTO
class RuleDto {
  final int weekday; // 1..7 (Mon..Sun)
  final String startTime; // "HH:mm"
  final String endTime; // "HH:mm"
  final int slotMinutes;

  RuleDto({
    required this.weekday,
    required this.startTime,
    required this.endTime,
    required this.slotMinutes,
  });

  factory RuleDto.fromJson(Map<String, dynamic> j) => RuleDto(
    weekday: j['weekday'] as int,
    startTime: j['startTime'] as String,
    endTime: j['endTime'] as String,
    slotMinutes: j['slotMinutes'] as int,
  );

  Map<String, dynamic> toJson() => {
    'weekday': weekday,
    'startTime': startTime,
    'endTime': endTime,
    'slotMinutes': slotMinutes,
  };
}

/// Date-specific schedule rule DTO (expand existing schedule for date range).
class DateSpecificRuleDto {
  final int? id;
  final String startDate; // YYYY-MM-DD
  final String endDate;
  final String startTime; // HH:mm
  final String endTime;
  final int slotMinutes;

  DateSpecificRuleDto({
    this.id,
    required this.startDate,
    required this.endDate,
    required this.startTime,
    required this.endTime,
    required this.slotMinutes,
  });

  factory DateSpecificRuleDto.fromJson(Map<String, dynamic> j) => DateSpecificRuleDto(
    id: j['id'] as int?,
    startDate: j['startDate'] as String,
    endDate: j['endDate'] as String,
    startTime: j['startTime'] as String,
    endTime: j['endTime'] as String,
    slotMinutes: j['slotMinutes'] as int,
  );

  Map<String, dynamic> toJson() => {
    if (id != null) 'id': id,
    'startDate': startDate,
    'endDate': endDate,
    'startTime': startTime,
    'endTime': endTime,
    'slotMinutes': slotMinutes,
  };
}

/// One calendar validity period from GET /api/schedule/validity-periods.
class ValidityPeriodDto {
  final String validFrom; // YYYY-MM-DD
  final String validUntil; // YYYY-MM-DD

  ValidityPeriodDto({required this.validFrom, required this.validUntil});

  factory ValidityPeriodDto.fromJson(Map<String, dynamic> j) => ValidityPeriodDto(
    validFrom: j['validFrom'] as String,
    validUntil: j['validUntil'] as String,
  );
}
