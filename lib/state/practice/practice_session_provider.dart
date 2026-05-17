import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

class PracticeColleague {
  final int doctorId;
  final String displayName;

  PracticeColleague({required this.doctorId, required this.displayName});
}

class PracticeSession {
  final String principalRole;
  final List<int> clinicIds;
  final List<PracticeColleague> colleagues;

  PracticeSession({
    required this.principalRole,
    required this.clinicIds,
    required this.colleagues,
  });

  factory PracticeSession.fromJson(Map<String, dynamic> json) {
    final clinicRaw = json['clinicIds'];
    final clinics = <int>[];
    if (clinicRaw is List) {
      for (final c in clinicRaw) {
        if (c is num) clinics.add(c.toInt());
      }
    }
    final colRaw = json['colleagues'];
    final colleagues = <PracticeColleague>[];
    if (colRaw is List) {
      for (final item in colRaw) {
        if (item is Map<String, dynamic>) {
          final id = item['doctorId'];
          final name = item['displayName']?.toString() ?? '';
          if (id is num) {
            colleagues.add(
              PracticeColleague(doctorId: id.toInt(), displayName: name),
            );
          }
        }
      }
    }
    return PracticeSession(
      principalRole: json['principalRole']?.toString() ?? '',
      clinicIds: clinics,
      colleagues: colleagues,
    );
  }
}

final practiceSessionProvider = FutureProvider<PracticeSession?>((ref) async {
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) return null;

  final client = ref.read(apiClientProvider);
  final resp = await client.get('/api/practice/me');
  if (resp.statusCode != 200) return null;

  final body = utf8.decode(resp.bodyBytes);
  if (body.trim().isEmpty) return null;
  final decoded = json.decode(body);
  if (decoded is! Map<String, dynamic>) return null;
  return PracticeSession.fromJson(decoded);
});
