// lib/state/schedule/schedule_actions.dart
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_controller.dart';
import 'package:shifa_doc_app_v1/state/schedule/schedule_models.dart';

/// PUT rules + PATCH valid-until.
///
/// When [locationId] is non-null, the save only replaces rules for that location and the
/// request URL carries `?locationId=...`. When null, the legacy full-replacement behavior
/// is preserved so single-location doctors don't have to change anything.
///
/// Throws on non-200 so caller can show SnackBar.
Future<void> saveScheduleToBackend(WidgetRef ref, {int? locationId}) async {
  final api = ref.read(apiClientProvider);
  final state = ref.read(scheduleProvider);
  final dtos = ref.read(scheduleProvider.notifier).toRuleDtos(locationId: locationId);

  // Debug: verify exact dates sent (no gap-fill; 08 Apr – 30 Apr must stay as-is)
  final startStr = state.startDate != null
      ? '${state.startDate!.year}-${state.startDate!.month.toString().padLeft(2, '0')}-${state.startDate!.day.toString().padLeft(2, '0')}'
      : null;
  final endStr = '${state.endDate.year}-${state.endDate.month.toString().padLeft(2, '0')}-${state.endDate.day.toString().padLeft(2, '0')}';
  debugPrint('Submitting calendar: startDate=$startStr, endDate=$endStr');

  // PUT /api/schedule/rules[?locationId=...]
  final putPath = locationId != null
      ? '/api/schedule/rules?locationId=$locationId'
      : '/api/schedule/rules';
  final put = await api.put(
    putPath,
    dtos.map((e) => e.toJson()).toList(),
  );
  if (put.statusCode != 200) {
    throw Exception(_errorMessageFromResponse(put));
  }

  // PATCH /api/schedule/valid-range — adds a new period (must not overlap any existing)
  final end = state.endDate;
  final endDateStr =
      '${end.year}-${end.month.toString().padLeft(2, '0')}-${end.day.toString().padLeft(2, '0')}';
  final body = <String, dynamic>{'validUntil': endDateStr};
  if (state.startDate != null) {
    final start = state.startDate!;
    body['validFrom'] =
        '${start.year}-${start.month.toString().padLeft(2, '0')}-${start.day.toString().padLeft(2, '0')}';
  }
  final patch = await api.patch('/api/schedule/valid-range', body);
  if (patch.statusCode != 200) {
    // Try to extract error message from backend response
    String errorMsg = 'Saving validity failed: ${patch.statusCode} ${patch.body}';
    try {
      final decoded = jsonDecode(utf8.decode(patch.bodyBytes));
      if (decoded is Map && decoded.containsKey('message')) {
        errorMsg = decoded['message'] as String;
      } else if (decoded is Map && decoded.containsKey('error')) {
        errorMsg = decoded['error'] as String;
      }
    } catch (_) {
      // If parsing fails, use default message
    }
    throw Exception(errorMsg);
  }
}

/// GET /api/schedule/validity-periods — list all calendar validity periods (multiple allowed).
Future<List<ValidityPeriodDto>> fetchValidityPeriods(WidgetRef ref) async {
  final api = ref.read(apiClientProvider);
  final res = await api.get('/api/schedule/validity-periods');
  if (res.statusCode != 200) {
    throw Exception('Failed to load validity periods: ${res.statusCode} ${res.body}');
  }
  final list = (jsonDecode(utf8.decode(res.bodyBytes)) as List).cast<Map<String, dynamic>>();
  return list.map(ValidityPeriodDto.fromJson).toList();
}

/// GET /api/schedule/date-specific[?locationId=...]
Future<List<DateSpecificRuleDto>> fetchDateSpecificRules(
  WidgetRef ref, {
  int? locationId,
}) async {
  final api = ref.read(apiClientProvider);
  final path = locationId != null
      ? '/api/schedule/date-specific?locationId=$locationId'
      : '/api/schedule/date-specific';
  final res = await api.get(path);
  if (res.statusCode != 200) {
    throw Exception('Failed to load date-specific rules: ${res.statusCode} ${res.body}');
  }
  final list = (jsonDecode(utf8.decode(res.bodyBytes)) as List).cast<Map<String, dynamic>>();
  return list.map(DateSpecificRuleDto.fromJson).toList();
}

/// POST /api/schedule/date-specific
/// Throws with message from backend on validation error (e.g. cannot override).
Future<DateSpecificRuleDto> createDateSpecificRule(
  WidgetRef ref, {
  required String startDate,
  required String endDate,
  required String startTime,
  required String endTime,
  required int slotMinutes,
  int? locationId,
}) async {
  final api = ref.read(apiClientProvider);
  final body = <String, dynamic>{
    'startDate': startDate,
    'endDate': endDate,
    'startTime': startTime,
    'endTime': endTime,
    'slotMinutes': slotMinutes,
    if (locationId != null) 'locationId': locationId,
  };
  final res = await api.post('/api/schedule/date-specific', body);
  if (res.statusCode != 200 && res.statusCode != 201) {
    final msg = _errorMessageFromResponse(res);
    throw Exception(msg);
  }
  final map = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return DateSpecificRuleDto.fromJson(map);
}

/// DELETE /api/schedule/date-specific/{id}
Future<void> deleteDateSpecificRule(WidgetRef ref, int id) async {
  final api = ref.read(apiClientProvider);
  final res = await api.delete('/api/schedule/date-specific/$id');
  if (res.statusCode != 200 && res.statusCode != 204) {
    final msg = _errorMessageFromResponse(res);
    throw Exception(msg);
  }
}

String _errorMessageFromResponse(http.Response res) {
  try {
    final m = jsonDecode(res.body) as Map<String, dynamic>?;
    final err = m?['message'] ?? m?['error'];
    if (err != null) return err.toString();
  } catch (_) {}
  return '${res.statusCode} ${res.body}';
}

/// POST /api/schedule/blocks — block free slots for emergencies / unavailability.
Future<void> createScheduleBlock(
  WidgetRef ref, {
  required String startAtUtc,
  required String endAtUtc,
  String? reason,
  int? resourceDoctorId,
  bool cancelOverlappingAppointments = false,
}) async {
  final api = ref.read(apiClientProvider);
  final body = <String, dynamic>{
    'startAt': startAtUtc,
    'endAt': endAtUtc,
    'cancelOverlappingAppointments': cancelOverlappingAppointments,
    if (reason != null && reason.trim().isNotEmpty) 'reason': reason.trim(),
    if (resourceDoctorId != null) 'resourceDoctorId': resourceDoctorId,
  };
  final res = await api.post('/api/schedule/blocks', body);
  if (res.statusCode != 200 && res.statusCode != 201) {
    throw Exception(_errorMessageFromResponse(res));
  }
}

/// DELETE /api/schedule/blocks/{id}
Future<void> deleteScheduleBlock(
  WidgetRef ref, {
  required int blockId,
  int? doctorId,
}) async {
  final api = ref.read(apiClientProvider);
  final path = doctorId != null
      ? '/api/schedule/blocks/$blockId?doctorId=$doctorId'
      : '/api/schedule/blocks/$blockId';
  final res = await api.delete(path);
  if (res.statusCode != 200 && res.statusCode != 204) {
    throw Exception(_errorMessageFromResponse(res));
  }
}
