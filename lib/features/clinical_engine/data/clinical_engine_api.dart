import 'dart:convert';

import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/clinical_engine/domain/clinical_engine_models.dart';

class ClinicalEngineApi {
  ClinicalEngineApi(this._api);

  final ApiClient _api;

  Future<List<ClinicalGroup>> fetchGroups() async {
    final res = await _api.get('/api/clinical-engine/groups');
    if (res.statusCode != 200) return [];
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>? ?? [];
    return list
        .map((e) => ClinicalGroup.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<ClinicalDiseaseSummary>> fetchDiseases(String groupId) async {
    final res = await _api.get('/api/clinical-engine/groups/$groupId/diseases');
    if (res.statusCode != 200) return [];
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>? ?? [];
    return list
        .map((e) => ClinicalDiseaseSummary.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<ClinicalDiseaseDetail?> fetchDisease(String diseaseId) async {
    final res = await _api.get('/api/clinical-engine/diseases/$diseaseId');
    if (res.statusCode != 200) return null;
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return ClinicalDiseaseDetail.fromJson(map);
  }

  Future<List<ClinicalTopDiagnosis>> fetchTopDiagnoses({int limit = 5}) async {
    final res = await _api.get('/api/clinical-engine/doctors/me/top-diagnoses?limit=$limit');
    if (res.statusCode != 200) return [];
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>? ?? [];
    return list
        .map((e) => ClinicalTopDiagnosis.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<ClinicalChip>> searchChips({
    required String query,
    required String locale,
    int limit = 15,
  }) async {
    final encoded = Uri.encodeQueryComponent(query);
    final res = await _api.get(
      '/api/clinical-engine/chips/search?q=$encoded&locale=$locale&limit=$limit',
    );
    if (res.statusCode != 200) return [];
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>? ?? [];
    return list.map((e) => ClinicalChip.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<ClinicalOcclusionChip>> fetchOcclusionChips() async {
    final res = await _api.get('/api/clinical-engine/occlusion/chips');
    if (res.statusCode != 200) return [];
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>? ?? [];
    return list
        .map((e) => ClinicalOcclusionChip.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<ClinicalSharedTemplate>> fetchSharedTemplates(String type) async {
    final res = await _api.get('/api/clinical-engine/shared/$type');
    if (res.statusCode != 200) return [];
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>? ?? [];
    return list
        .map((e) => ClinicalSharedTemplate.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Map<String, String>> synthesize({
    required String locale,
    required List<ClinicalChipSelection> selections,
    String? diseaseId,
  }) async {
    final body = {
      'locale': locale,
      if (diseaseId != null) 'diseaseId': diseaseId,
      'selections': selections.map((e) => e.toJson()).toList(),
    };
    final res = await _api.post(
      '/api/clinical-engine/synthesize',
      body,
    );
    if (res.statusCode != 200) return {};
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    final fields = map['fields'];
    if (fields is! Map) return {};
    return fields.map((k, v) => MapEntry(k.toString(), v.toString()));
  }
}
