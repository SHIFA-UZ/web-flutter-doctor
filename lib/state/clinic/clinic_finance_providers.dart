import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

/// Query params for doctor earnings (clinic + UTC month range as ISO instants).
class DoctorEarningsQuery {
  final int clinicId;
  final String fromIso;
  final String toIso;

  const DoctorEarningsQuery({
    required this.clinicId,
    required this.fromIso,
    required this.toIso,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is DoctorEarningsQuery &&
          other.clinicId == clinicId &&
          other.fromIso == fromIso &&
          other.toIso == toIso;

  @override
  int get hashCode => Object.hash(clinicId, fromIso, toIso);
}

final clinicFinanceDashboardProvider =
    FutureProvider.family<FinanceDashboardStats, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get('/api/clinics/$clinicId/finance/dashboard');
  if (res.statusCode != 200) {
    throw Exception('Failed to load finance dashboard (${res.statusCode})');
  }
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return FinanceDashboardStats.fromJson(data);
});

final clinicFinancialRecordsProvider =
    FutureProvider.family<List<FinancialRecordRow>, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res =
      await api.get('/api/clinics/$clinicId/finance/records?page=0&size=50');
  if (res.statusCode != 200) {
    throw Exception('Failed to load financial records (${res.statusCode})');
  }
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  final content = data['content'] as List<dynamic>? ?? [];
  return content
      .whereType<Map>()
      .map((e) => FinancialRecordRow.fromJson(Map<String, dynamic>.from(e)))
      .toList();
});

final clinicPaymentHistoryProvider =
    FutureProvider.family<List<PaymentHistoryItem>, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res =
      await api.get('/api/clinics/$clinicId/finance/payments?page=0&size=50');
  if (res.statusCode != 200) {
    throw Exception('Failed to load payment history (${res.statusCode})');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => PaymentHistoryItem.fromJson(Map<String, dynamic>.from(e)))
      .toList();
});

final clinicOverdueProvider =
    FutureProvider.family<Map<String, dynamic>, int>((ref, clinicId) async {
  final api = ref.watch(doctorApiClientProvider);
  final res = await api.get('/api/clinics/$clinicId/finance/overdue');
  if (res.statusCode != 200) {
    throw Exception('Failed to load overdue data (${res.statusCode})');
  }
  return json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
});

/// (clinicId, filter) where filter is `all` | `pending` | `overdue` | `paid`.
final clinicInstallmentItemsProvider = FutureProvider.autoDispose
    .family<List<InstallmentItemListRow>, (int, String)>((ref, key) async {
  return fetchClinicInstallmentItems(
    ref,
    clinicId: key.$1,
    filter: key.$2,
  );
});

final clinicAppointmentLedgerProvider =
    FutureProvider.family<Map<String, dynamic>, int>((ref, clinicId) {
  return fetchAppointmentLedgerPage(ref, clinicId: clinicId);
});

final clinicDoctorEarningsProvider =
    FutureProvider.family<List<DoctorEarningRow>, DoctorEarningsQuery>(
        (ref, query) {
  return fetchDoctorEarnings(
    ref,
    clinicId: query.clinicId,
    fromIso: query.fromIso,
    toIso: query.toIso,
  );
});

/// Whether user may record payments, installments, etc. (patient-scoped on server).
final canManageFinanceProvider = Provider<bool>((ref) {
  final clinic = ref.watch(selectedClinicProvider);
  if (clinic == null) return false;
  const roles = ['OWNER', 'CLINIC_ADMIN', 'RECEPTIONIST', 'DOCTOR'];
  return roles.contains(clinic.membershipRole);
});

/// Whether current user can view finance tab based on membership role.
final canViewFinanceProvider = Provider<bool>((ref) {
  final clinic = ref.watch(selectedClinicProvider);
  if (clinic == null) return false;
  const financeRoles = ['OWNER', 'CLINIC_ADMIN', 'RECEPTIONIST', 'DOCTOR', 'NURSE'];
  return financeRoles.contains(clinic.membershipRole);
});

/// Refresh all cached endpoints used by Clinic → Finance (any sub-tab).
///
/// Accepts either [Ref] (providers) or [WidgetRef] (widgets) — both expose
/// `invalidate(ProviderOrFamily)`. The parameter is typed `dynamic` so callers
/// from either context don't trip Dart's runtime type check (WidgetRef does
/// not extend Ref, so a narrower signature crashes appointment completion when
/// invoked from the in-person/video screens).
void invalidateClinicFinanceTabDataForClinic(dynamic ref, int clinicId) {
  ref.invalidate(clinicFinanceDashboardProvider(clinicId));
  ref.invalidate(clinicFinancialRecordsProvider(clinicId));
  ref.invalidate(clinicPaymentHistoryProvider(clinicId));
  ref.invalidate(clinicOverdueProvider(clinicId));
  ref.invalidate(clinicAppointmentLedgerProvider(clinicId));
  ref.invalidate(clinicDoctorEarningsProvider);
  for (final f in ['all', 'pending', 'overdue', 'paid']) {
    ref.invalidate(clinicInstallmentItemsProvider((clinicId, f)));
  }
}

/// Refreshes every provider backing Clinic → Finance and related treatment-plan totals.
void refreshClinicFinancialData(dynamic ref, int clinicId) {
  invalidateClinicFinanceTabDataForClinic(ref, clinicId);
  ref.invalidate(treatmentPlansForClinicProvider);
  ref.invalidate(treatmentPlansForPatientProvider);
}
