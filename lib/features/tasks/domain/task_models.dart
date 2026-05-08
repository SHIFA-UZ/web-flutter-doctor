// lib/features/tasks/domain/task_models.dart
import 'package:flutter/material.dart';

enum TaskCategory {
  vital,
  exercise,
  medication,
  other,
}

enum TaskStatus {
  draft,
  active,
  completed,
  expired,
  cancelled,
}

enum TaskInputType {
  numeric,
  text,
  boolean,
}

enum CheckInStatus {
  pending,
  completed,
  missed,
}

/// Helper for parsing a list of HH:mm strings into [TimeOfDay] entries.
List<TimeOfDay>? _parseCustomTimes(dynamic raw) {
  if (raw is! List) return null;
  final result = <TimeOfDay>[];
  for (final entry in raw) {
    final s = entry?.toString().trim();
    if (s == null || s.isEmpty) continue;
    final parts = s.split(':');
    if (parts.length < 2) continue;
    final h = int.tryParse(parts[0]);
    final m = int.tryParse(parts[1]);
    if (h == null || m == null) continue;
    result.add(TimeOfDay(hour: h, minute: m));
  }
  if (result.isEmpty) return null;
  result.sort((a, b) {
    final am = a.hour * 60 + a.minute;
    final bm = b.hour * 60 + b.minute;
    return am.compareTo(bm);
  });
  return result;
}

/// Reusable task config from backend (no patient, no dates). Used to pre-fill create form.
class TaskTemplate {
  final String taskName;
  final String? description;
  final TaskCategory category;
  final int timesPerDay;
  final TimeOfDay? startTime;
  final int? intervalHours;
  /// Optional explicit list of slot times. When non-null and non-empty,
  /// the task uses these times instead of (startTime, intervalHours).
  final List<TimeOfDay>? customTimes;
  final TimeOfDay? morningTime;
  final TimeOfDay? afternoonTime;
  final TimeOfDay? eveningTime;
  final TaskInputType inputType;
  final String? inputLabel;
  final bool notesRequired;
  final String? notesLabel;

  TaskTemplate({
    required this.taskName,
    this.description,
    required this.category,
    required this.timesPerDay,
    this.startTime,
    this.intervalHours,
    this.customTimes,
    this.morningTime,
    this.afternoonTime,
    this.eveningTime,
    required this.inputType,
    this.inputLabel,
    required this.notesRequired,
    this.notesLabel,
  });

  factory TaskTemplate.fromApi(Map<String, dynamic> json) {
    TimeOfDay? parseTime(String? timeStr) {
      if (timeStr == null) return null;
      final parts = timeStr.split(':');
      if (parts.length >= 2) {
        return TimeOfDay(
          hour: int.parse(parts[0]),
          minute: int.parse(parts[1]),
        );
      }
      return null;
    }
    return TaskTemplate(
      taskName: json['taskName'] as String,
      description: json['description'] as String?,
      category: TaskCategory.values.firstWhere(
        (e) => e.name.toUpperCase() == (json['category'] as String).toUpperCase(),
        orElse: () => TaskCategory.other,
      ),
      timesPerDay: json['timesPerDay'] as int,
      startTime: parseTime(json['startTime'] as String?),
      intervalHours: json['intervalHours'] as int?,
      customTimes: _parseCustomTimes(json['customTimes']),
      morningTime: parseTime(json['morningTime'] as String?),
      afternoonTime: parseTime(json['afternoonTime'] as String?),
      eveningTime: parseTime(json['eveningTime'] as String?),
      inputType: TaskInputType.values.firstWhere(
        (e) => e.name.toUpperCase() == (json['inputType'] as String).toUpperCase(),
        orElse: () => TaskInputType.text,
      ),
      inputLabel: json['inputLabel'] as String?,
      notesRequired: json['notesRequired'] as bool? ?? false,
      notesLabel: json['notesLabel'] as String?,
    );
  }
}

class RemoteCareTask {
  final int id;
  final int patientId;
  final String patientName;
  /// IANA timezone (e.g. Asia/Tashkent) for the assigned patient; used to show check-in times in patient's timezone.
  final String? patientTimeZone;
  final String taskName;
  final String? description;
  final TaskCategory category;
  final TaskStatus status;
  final int timesPerDay;
  final TimeOfDay? startTime;
  final int? intervalHours;
  final List<TimeOfDay>? customTimes;
  final TimeOfDay? morningTime;
  final TimeOfDay? afternoonTime;
  final TimeOfDay? eveningTime;
  final DateTime startDate;
  final DateTime? endDate;
  final int? durationDays;
  final TaskInputType inputType;
  final String? inputLabel;
  final bool notesRequired;
  final String? notesLabel;
  final DateTime createdAt;
  final TaskProgress? progress;

  RemoteCareTask({
    required this.id,
    required this.patientId,
    required this.patientName,
    this.patientTimeZone,
    required this.taskName,
    this.description,
    required this.category,
    required this.status,
    required this.timesPerDay,
    this.startTime,
    this.intervalHours,
    this.customTimes,
    this.morningTime,
    this.afternoonTime,
    this.eveningTime,
    required this.startDate,
    this.endDate,
    this.durationDays,
    required this.inputType,
    this.inputLabel,
    required this.notesRequired,
    this.notesLabel,
    required this.createdAt,
    this.progress,
  });

  factory RemoteCareTask.fromApi(Map<String, dynamic> json) {
    TimeOfDay? parseTime(String? timeStr) {
      if (timeStr == null) return null;
      final parts = timeStr.split(':');
      if (parts.length >= 2) {
        return TimeOfDay(
          hour: int.parse(parts[0]),
          minute: int.parse(parts[1]),
        );
      }
      return null;
    }

    return RemoteCareTask(
      id: json['id'] as int,
      patientId: json['patientId'] as int,
      patientName: json['patientName'] as String,
      patientTimeZone: json['patientTimeZone'] as String?,
      taskName: json['taskName'] as String,
      description: json['description'] as String?,
      category: TaskCategory.values.firstWhere(
        (e) => e.name.toUpperCase() == (json['category'] as String).toUpperCase(),
        orElse: () => TaskCategory.other,
      ),
      status: TaskStatus.values.firstWhere(
        (e) => e.name.toUpperCase() == (json['status'] as String).toUpperCase(),
        orElse: () => TaskStatus.active,
      ),
      timesPerDay: json['timesPerDay'] as int,
      startTime: parseTime(json['startTime'] as String?),
      intervalHours: json['intervalHours'] as int?,
      customTimes: _parseCustomTimes(json['customTimes']),
      morningTime: parseTime(json['morningTime'] as String?),
      afternoonTime: parseTime(json['afternoonTime'] as String?),
      eveningTime: parseTime(json['eveningTime'] as String?),
      startDate: DateTime.parse(json['startDate'] as String),
      endDate: json['endDate'] != null
          ? DateTime.parse(json['endDate'] as String)
          : null,
      durationDays: json['durationDays'] as int?,
      inputType: TaskInputType.values.firstWhere(
        (e) => e.name.toUpperCase() == (json['inputType'] as String).toUpperCase(),
        orElse: () => TaskInputType.text,
      ),
      inputLabel: json['inputLabel'] as String?,
      notesRequired: json['notesRequired'] as bool? ?? false,
      notesLabel: json['notesLabel'] as String?,
      createdAt: DateTime.parse(json['createdAt'] as String),
      progress: json['progress'] != null
          ? TaskProgress.fromApi(json['progress'] as Map<String, dynamic>)
          : null,
    );
  }
}

class TaskProgress {
  final int totalCheckIns;
  final int completedCheckIns;
  final int pendingCheckIns;
  final int missedCheckIns;

  TaskProgress({
    required this.totalCheckIns,
    required this.completedCheckIns,
    required this.pendingCheckIns,
    required this.missedCheckIns,
  });

  factory TaskProgress.fromApi(Map<String, dynamic> json) {
    return TaskProgress(
      totalCheckIns: json['totalCheckIns'] as int,
      completedCheckIns: json['completedCheckIns'] as int,
      pendingCheckIns: json['pendingCheckIns'] as int,
      missedCheckIns: json['missedCheckIns'] as int,
    );
  }

  double get completionPercentage {
    if (totalCheckIns == 0) return 0.0;
    return completedCheckIns / totalCheckIns;
  }
}

class TaskCheckIn {
  final int id;
  final DateTime scheduledDate;
  final TimeOfDay? scheduledTime;
  final CheckInStatus status;
  final double? numericValue;
  final String? textValue;
  final bool? booleanValue;
  final String? notes;
  final DateTime? completedAt;

  TaskCheckIn({
    required this.id,
    required this.scheduledDate,
    this.scheduledTime,
    required this.status,
    this.numericValue,
    this.textValue,
    this.booleanValue,
    this.notes,
    this.completedAt,
  });

  factory TaskCheckIn.fromApi(Map<String, dynamic> json) {
    TimeOfDay? parseTime(String? timeStr) {
      if (timeStr == null) return null;
      final parts = timeStr.split(':');
      if (parts.length >= 2) {
        return TimeOfDay(
          hour: int.parse(parts[0]),
          minute: int.parse(parts[1]),
        );
      }
      return null;
    }

    return TaskCheckIn(
      id: json['id'] as int,
      scheduledDate: DateTime.parse(json['scheduledDate'] as String),
      scheduledTime: parseTime(json['scheduledTime'] as String?),
      status: CheckInStatus.values.firstWhere(
        (e) => e.name.toUpperCase() == (json['status'] as String).toUpperCase(),
        orElse: () => CheckInStatus.pending,
      ),
      numericValue: json['numericValue'] != null
          ? (json['numericValue'] as num).toDouble()
          : null,
      textValue: json['textValue'] as String?,
      booleanValue: json['booleanValue'] as bool?,
      notes: json['notes'] as String?,
      completedAt: json['completedAt'] != null
          ? DateTime.parse(json['completedAt'] as String)
          : null,
    );
  }
}
