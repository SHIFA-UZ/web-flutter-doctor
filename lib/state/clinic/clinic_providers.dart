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
    final me = _ref.read(meProfileProvider).valueOrNull;
    if (me != null) {
      return 'clinic_workspace_selected_id:${me.id}';
    }
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

/// Family key for [planServicesProvider]. The doctor id list is normalized to
/// a sorted, deduplicated immutable list so that two callers with the same
/// (clinic, doctorIds set) hit the same cache slot regardless of selection
/// order.
class PlanServicesKey {
  final int clinicId;
  final List<int> doctorIds;

  PlanServicesKey({required this.clinicId, List<int> doctorIds = const []})
      : doctorIds = List<int>.unmodifiable({...doctorIds}.toList()..sort());

  @override
  bool operator ==(Object other) {
    if (identical(this, other)) return true;
    if (other is! PlanServicesKey) return false;
    if (other.clinicId != clinicId) return false;
    if (other.doctorIds.length != doctorIds.length) return false;
    for (var i = 0; i < doctorIds.length; i++) {
      if (doctorIds[i] != other.doctorIds[i]) return false;
    }
    return true;
  }

  @override
  int get hashCode => Object.hash(clinicId, Object.hashAll(doctorIds));
}

/// Unified service catalog for the clinic Services tab and the treatment-plan
/// wizard. Returns clinic catalog items + doctor-defined profile services
/// (each tagged with the doctors that offer it).
///
/// Pass empty [PlanServicesKey.doctorIds] to get the full clinic view
/// (Services tab). Pass the wizard's selected attending doctor ids to filter.
final planServicesProvider = FutureProvider.autoDispose
    .family<List<PlanServiceOption>, PlanServicesKey>((ref, key) async {
  final api = ref.watch(doctorApiClientProvider);
  final params = <String, String>{'clinicId': key.clinicId.toString()};
  if (key.doctorIds.isNotEmpty) {
    params['doctorIds'] = key.doctorIds.join(',');
  }
  final res = await api.get('/api/treatment-plans/plan-services', params: params);
  if (res.statusCode != 200) {
    throw Exception('plan-services ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return const <PlanServiceOption>[];
  return list
      .whereType<Map>()
      .map((e) => PlanServiceOption.fromJson(Map<String, dynamic>.from(e)))
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

class ClinicInvitationRow {
  ClinicInvitationRow({
    required this.id,
    this.emailSentTo,
    required this.consumed,
    this.expiresAt,
    required this.pending,
  });

  final int id;
  final String? emailSentTo;
  final bool consumed;
  final String? expiresAt;
  final bool pending;

  factory ClinicInvitationRow.fromJson(Map<String, dynamic> m) {
    return ClinicInvitationRow(
      id: (m['id'] as num).toInt(),
      emailSentTo: m['emailSentTo']?.toString(),
      consumed: m['consumed'] == true,
      expiresAt: m['expiresAt']?.toString(),
      pending: m['pending'] == true,
    );
  }
}

final clinicInvitationsProvider =
    FutureProvider.family<List<ClinicInvitationRow>, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get('/api/clinics/$clinicId/invitations');
  if (res.statusCode != 200) {
    throw Exception('invitations ${res.statusCode}');
  }
  final decoded = json.decode(utf8.decode(res.bodyBytes));
  if (decoded is! List) return [];
  return decoded
      .whereType<Map>()
      .map((e) => ClinicInvitationRow.fromJson(Map<String, dynamic>.from(e)))
      .toList();
});

Future<void> createClinicReceptionistInvitation(
  WidgetRef ref, {
  required int clinicId,
  required String email,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final trimmed = email.trim().toLowerCase();
  final res = await api.post('/api/clinics/$clinicId/invitations', {
    'email': trimmed,
    'role': 'RECEPTIONIST',
  });
  if (res.statusCode != 200 && res.statusCode != 201) {
    throw Exception(res.body.isNotEmpty ? res.body : 'Invite failed (${res.statusCode})');
  }
  ref.invalidate(clinicInvitationsProvider(clinicId));
}

Future<void> revokeClinicInvitation(
  WidgetRef ref, {
  required int clinicId,
  required int invitationId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.delete('/api/clinics/$clinicId/invitations/$invitationId');
  if (res.statusCode != 200 && res.statusCode != 204) {
    throw Exception(
      res.body.isNotEmpty ? res.body : 'Revoke failed (${res.statusCode})',
    );
  }
  ref.invalidate(clinicInvitationsProvider(clinicId));
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

bool canManageClinicFinanceSettings(String membershipRole) =>
    membershipRole == 'OWNER' || membershipRole == 'CLINIC_ADMIN';

bool canManageClinicFinanceFor(MyClinicSummary? clinic) {
  if (clinic == null) return false;
  return clinic.canManageFinanceSettings;
}

bool canEditMemberRevenueShare(ClinicMember member) =>
    member.membershipRole == 'DOCTOR' || member.membershipRole == 'OWNER';

Future<void> updateClinicFinanceSettings(
  WidgetRef ref,
  int clinicId,
  int? defaultDoctorRevenueSharePercent,
) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.patch(
    '/api/clinics/$clinicId/finance-settings',
    {'defaultDoctorRevenueSharePercent': defaultDoctorRevenueSharePercent},
  );
  if (res.statusCode != 200) {
    throw Exception('finance-settings ${res.statusCode}: ${res.body}');
  }
  ref.invalidate(myClinicsProvider);
}

Future<void> updateMemberRevenueShare(
  WidgetRef ref,
  int clinicId,
  int doctorProfileId,
  int? doctorRevenueSharePercent,
) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.patch(
    '/api/clinics/$clinicId/members/$doctorProfileId/revenue-share',
    {'doctorRevenueSharePercent': doctorRevenueSharePercent},
  );
  if (res.statusCode != 200) {
    throw Exception('revenue-share ${res.statusCode}: ${res.body}');
  }
  ref.invalidate(clinicMembersProvider(clinicId));
}
