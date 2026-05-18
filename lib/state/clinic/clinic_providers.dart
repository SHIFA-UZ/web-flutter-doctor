// lib/state/clinic/clinic_providers.dart
import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

/// Reload clinic list when auth token is set.
final myClinicsProvider = FutureProvider<List<MyClinicSummary>>((ref) async {
  final token = ref.watch(authTokenProvider);
  if (token == null || token.isEmpty) return [];

  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get('/api/me/clinics');
  if (res.statusCode != 200) {
    throw Exception('Failed to load clinics (${res.statusCode})');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => MyClinicSummary.fromJson(Map<String, dynamic>.from(e)))
      .toList();
});

final hasClinicWorkspaceProvider = Provider<bool>((ref) {
  final async = ref.watch(myClinicsProvider);
  return async.maybeWhen(data: (l) => l.isNotEmpty, orElse: () => false);
});

class SelectedClinicIdNotifier extends StateNotifier<int?> {
  SelectedClinicIdNotifier(this._ref) : super(null) {
    _ref.listen<AsyncValue<List<MyClinicSummary>>>(myClinicsProvider, (_, next) {
      next.whenData(_applyClinicList);
    });
    Future.microtask(() {
      final v = _ref.read(myClinicsProvider);
      v.whenData(_applyClinicList);
    });
  }

  final Ref _ref;

  Future<String> _prefsKey() async {
    final profile = _ref.read(profileAllProvider).valueOrNull?.profile;
    final id = profile?['id'] ?? profile?['doctorId'] ?? profile?['doctorProfileId'];
    return 'clinic_workspace_selected_id:${id ?? 'unknown'}';
  }

  Future<void> _applyClinicList(List<MyClinicSummary> list) async {
    if (list.isEmpty) {
      state = null;
      return;
    }
    final prefs = await SharedPreferences.getInstance();
    final key = await _prefsKey();
    final saved = prefs.getInt(key);
    if (saved != null && list.any((c) => c.clinicId == saved)) {
      state = saved;
      return;
    }
    if (state != null && list.any((c) => c.clinicId == state)) {
      await prefs.setInt(key, state!);
      return;
    }
    state = list.first.clinicId;
    await prefs.setInt(key, state!);
  }

  Future<void> select(int clinicId) async {
    state = clinicId;
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(await _prefsKey(), clinicId);
  }
}

final selectedClinicIdProvider =
    StateNotifierProvider<SelectedClinicIdNotifier, int?>((ref) {
  return SelectedClinicIdNotifier(ref);
});

final selectedClinicProvider = Provider<MyClinicSummary?>((ref) {
  final id = ref.watch(selectedClinicIdProvider);
  final listAsync = ref.watch(myClinicsProvider);
  return listAsync.whenOrNull(
    data: (list) {
      if (list.isEmpty) return null;
      final cid = id;
      if (cid == null) return list.first;
      for (final c in list) {
        if (c.clinicId == cid) return c;
      }
      return list.first;
    },
  );
});

final clinicOverviewStatsProvider =
    FutureProvider.family<ClinicOverviewStats, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get('/api/clinics/$clinicId/overview-stats');
  if (res.statusCode != 200) {
    throw Exception('overview-stats ${res.statusCode}');
  }
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map<String, dynamic>) throw Exception('Invalid JSON');
  return ClinicOverviewStats.fromJson(m);
});

final clinicMembersProvider = FutureProvider.family<List<ClinicMember>, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get('/api/clinics/$clinicId/members');
  if (res.statusCode != 200) {
    throw Exception('members ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => ClinicMember.fromJson(Map<String, dynamic>.from(e)))
      .toList();
});

final clinicPatientsFirstPageProvider =
    FutureProvider.family<ClinicPatientsPage, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get(
    '/api/clinics/$clinicId/patients',
    params: {'page': '0', 'size': '50'},
  );
  if (res.statusCode != 200) {
    throw Exception('patients ${res.statusCode}');
  }
  final body = json.decode(utf8.decode(res.bodyBytes));
  if (body is! Map<String, dynamic>) throw Exception('Invalid page JSON');
  return ClinicPatientsPage.fromJson(body);
});

final clinicCatalogProvider =
    FutureProvider.family<List<ClinicCatalogItem>, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get(
    '/api/treatment-plans/catalog-items',
    params: {'clinicId': clinicId.toString()},
  );
  if (res.statusCode != 200) {
    throw Exception('catalog ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => ClinicCatalogItem.fromJson(Map<String, dynamic>.from(e)))
      .toList();
});

Future<ClinicCatalogItem> createClinicCatalogItem(
  WidgetRef ref, {
  required int clinicId,
  String? code,
  required String title,
  required int defaultPriceMinor,
  required String currency,
  required bool active,
  int sortOrder = 0,
  required bool appliesToAllDoctors,
  required List<int> assignedDoctorProfileIds,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post('/api/treatment-plans/catalog-items', {
    'clinicId': clinicId,
    if (code != null && code.isNotEmpty) 'code': code,
    'title': title,
    'defaultPriceMinor': defaultPriceMinor,
    'currency': currency,
    'active': active,
    'sortOrder': sortOrder,
    'appliesToAllDoctors': appliesToAllDoctors,
    'assignedDoctorProfileIds': assignedDoctorProfileIds,
  });
  if (res.statusCode != 200) {
    throw Exception('catalog create ${res.statusCode}: ${res.body}');
  }
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) throw Exception('Invalid catalog JSON');
  ref.invalidate(clinicCatalogProvider(clinicId));
  return ClinicCatalogItem.fromJson(Map<String, dynamic>.from(m));
}

Future<ClinicCatalogItem> patchClinicCatalogItem(
  WidgetRef ref, {
  required int clinicId,
  required int catalogItemId,
  String? code,
  String? title,
  int? defaultPriceMinor,
  String? currency,
  bool? active,
  int? sortOrder,
  bool? appliesToAllDoctors,
  List<int>? assignedDoctorProfileIds,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{};
  if (code != null) body['code'] = code;
  if (title != null) body['title'] = title;
  if (defaultPriceMinor != null) body['defaultPriceMinor'] = defaultPriceMinor;
  if (currency != null) body['currency'] = currency;
  if (active != null) body['active'] = active;
  if (sortOrder != null) body['sortOrder'] = sortOrder;
  if (appliesToAllDoctors != null) body['appliesToAllDoctors'] = appliesToAllDoctors;
  if (assignedDoctorProfileIds != null) {
    body['assignedDoctorProfileIds'] = assignedDoctorProfileIds;
  }
  final res = await api.patch(
    '/api/treatment-plans/catalog-items/$catalogItemId',
    body,
  );
  if (res.statusCode != 200) {
    throw Exception('catalog patch ${res.statusCode}: ${res.body}');
  }
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) throw Exception('Invalid catalog JSON');
  ref.invalidate(clinicCatalogProvider(clinicId));
  return ClinicCatalogItem.fromJson(Map<String, dynamic>.from(m));
}

class ClinicPatientsPage {
  final List<ClinicPatientRow> content;
  final int totalElements;
  final int totalPages;
  final int number;
  final int size;

  ClinicPatientsPage({
    required this.content,
    required this.totalElements,
    required this.totalPages,
    required this.number,
    required this.size,
  });

  factory ClinicPatientsPage.fromJson(Map<String, dynamic> json) {
    final raw = json['content'];
    final list = <ClinicPatientRow>[];
    if (raw is List) {
      for (final e in raw) {
        if (e is Map) {
          list.add(ClinicPatientRow.fromJson(Map<String, dynamic>.from(e)));
        }
      }
    }
    return ClinicPatientsPage(
      content: list,
      totalElements: (json['totalElements'] as num?)?.toInt() ?? list.length,
      totalPages: (json['totalPages'] as num?)?.toInt() ?? 1,
      number: (json['number'] as num?)?.toInt() ?? 0,
      size: (json['size'] as num?)?.toInt() ?? list.length,
    );
  }
}
