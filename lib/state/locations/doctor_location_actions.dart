import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/locations/doctor_location_models.dart';

/// GET /api/doctors/me/locations
Future<List<DoctorLocationDto>> fetchDoctorLocations(WidgetRef ref) async {
  final api = ref.read(apiClientProvider);
  final res = await api.get('/api/doctors/me/locations');
  if (res.statusCode != 200) {
    throw Exception(_extractMessage(res) ??
        'Failed to load locations: ${res.statusCode} ${res.body}');
  }
  final list =
      (jsonDecode(utf8.decode(res.bodyBytes)) as List).cast<Map<String, dynamic>>();
  return list.map(DoctorLocationDto.fromJson).toList();
}

/// POST /api/doctors/me/locations
Future<DoctorLocationDto> createDoctorLocation(
  WidgetRef ref,
  DoctorLocationDto body,
) async {
  final api = ref.read(apiClientProvider);
  final res = await api.post('/api/doctors/me/locations', body.toJson());
  if (res.statusCode != 200 && res.statusCode != 201) {
    throw Exception(_extractMessage(res) ??
        'Failed to create location: ${res.statusCode} ${res.body}');
  }
  return DoctorLocationDto.fromJson(
    jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>,
  );
}

/// PATCH /api/doctors/me/locations/{id}
Future<DoctorLocationDto> updateDoctorLocation(
  WidgetRef ref,
  int id,
  DoctorLocationDto body,
) async {
  final api = ref.read(apiClientProvider);
  final res = await api.patch('/api/doctors/me/locations/$id', body.toJson());
  if (res.statusCode != 200) {
    throw Exception(_extractMessage(res) ??
        'Failed to update location: ${res.statusCode} ${res.body}');
  }
  return DoctorLocationDto.fromJson(
    jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>,
  );
}

/// DELETE /api/doctors/me/locations/{id}
Future<void> deleteDoctorLocation(WidgetRef ref, int id) async {
  final api = ref.read(apiClientProvider);
  final res = await api.delete('/api/doctors/me/locations/$id');
  if (res.statusCode != 200 && res.statusCode != 204) {
    throw Exception(_extractMessage(res) ??
        'Failed to delete location: ${res.statusCode} ${res.body}');
  }
}

String? _extractMessage(http.Response res) {
  try {
    final decoded = jsonDecode(utf8.decode(res.bodyBytes));
    if (decoded is Map) {
      final msg = decoded['message'] ?? decoded['error'];
      if (msg is String && msg.isNotEmpty) return msg;
    }
  } catch (_) {}
  return null;
}
