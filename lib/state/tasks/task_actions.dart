// lib/state/tasks/task_actions.dart
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';

String _timeOfDayToApi(TimeOfDay t) =>
    '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

Future<RemoteCareTask> createTaskWithClient({
  required ApiClient client,
  required int patientId,
  required String taskName,
  String? description,
  required TaskCategory category,
  required int timesPerDay,
  TimeOfDay? startTime,
  int? intervalHours,
  required DateTime startDate,
  DateTime? endDate,
  int? durationDays,
  required TaskInputType inputType,
  String? inputLabel,
  required bool notesRequired,
  String? notesLabel,
}) async {
  final body = {
    'patientId': patientId,
    'taskName': taskName,
    if (description != null) 'description': description,
    'category': category.name.toUpperCase(),
    'timesPerDay': timesPerDay.clamp(1, 15),
    if (startTime != null) 'startTime': _timeOfDayToApi(startTime),
    if (timesPerDay >= 2 && intervalHours != null) 'intervalHours': intervalHours,
    'startDate': '${startDate.year.toString().padLeft(4, '0')}-'
        '${startDate.month.toString().padLeft(2, '0')}-'
        '${startDate.day.toString().padLeft(2, '0')}',
    if (endDate != null)
      'endDate': '${endDate.year.toString().padLeft(4, '0')}-'
          '${endDate.month.toString().padLeft(2, '0')}-'
          '${endDate.day.toString().padLeft(2, '0')}',
    if (durationDays != null) 'durationDays': durationDays,
    'inputType': inputType.name.toUpperCase(),
    if (inputLabel != null) 'inputLabel': inputLabel,
    'notesRequired': notesRequired,
    if (notesLabel != null) 'notesLabel': notesLabel,
  };

  final res = await client.post('/api/tasks', body);

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return RemoteCareTask.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to create task: ${res.statusCode} ${res.body}');
  }
}

Future<List<TaskTemplate>> fetchTemplatesWithClient({
  required ApiClient client,
}) async {
  final res = await client.get('/api/tasks/templates');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List data = jsonDecode(utf8.decode(res.bodyBytes)) as List;
    return data
        .map((e) => TaskTemplate.fromApi(e as Map<String, dynamic>))
        .toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch templates: ${res.statusCode} ${res.body}');
  }
}

Future<List<RemoteCareTask>> fetchTasksWithClient({
  required ApiClient client,
  int? patientId,
  TaskStatus? status,
}) async {
  final params = <String, String>{};
  if (patientId != null) params['patientId'] = patientId.toString();
  if (status != null) params['status'] = status.name.toUpperCase();

  final res = await client.get('/api/tasks', params: params);

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List data = jsonDecode(utf8.decode(res.bodyBytes)) as List;
    return data
        .map((e) => RemoteCareTask.fromApi(e as Map<String, dynamic>))
        .toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch tasks: ${res.statusCode} ${res.body}');
  }
}

Future<RemoteCareTask> fetchTaskWithClient({
  required ApiClient client,
  required int taskId,
}) async {
  final res = await client.get('/api/tasks/$taskId');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return RemoteCareTask.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch task: ${res.statusCode} ${res.body}');
  }
}

Future<TaskProgress> fetchTaskProgressWithClient({
  required ApiClient client,
  required int taskId,
}) async {
  final res = await client.get('/api/tasks/$taskId/progress');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return TaskProgress.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch progress: ${res.statusCode} ${res.body}');
  }
}

Future<List<TaskCheckIn>> fetchCheckInsWithClient({
  required ApiClient client,
  required int taskId,
}) async {
  final res = await client.get('/api/tasks/$taskId/check-ins');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List data = jsonDecode(utf8.decode(res.bodyBytes)) as List;
    return data
        .map((e) => TaskCheckIn.fromApi(e as Map<String, dynamic>))
        .toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch check-ins: ${res.statusCode} ${res.body}');
  }
}

Future<void> cancelTaskWithClient({
  required ApiClient client,
  required int taskId,
}) async {
  final res = await client.patch('/api/tasks/$taskId/cancel', {});

  if (res.statusCode >= 200 && res.statusCode < 300) {
    return;
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to cancel task: ${res.statusCode} ${res.body}');
  }
}
