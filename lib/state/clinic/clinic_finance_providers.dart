import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_month.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

/// Query params for doctor earnings (clinic + optional UTC date range as ISO instants).
class DoctorEarningsQuery {
  final int clinicId;
  final String? fromIso;
  final String? toIso;

  const DoctorEarningsQuery({
    required this.clinicId,
    this.fromIso,
    this.toIso,
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

/// Query params for appointment ledger (clinic + optional UTC date range as ISO instants).
class AppointmentLedgerQuery {
  final int clinicId;
  final String? fromIso;
  final String? toIso;
  final int page;
  final int size;

  const AppointmentLedgerQuery({
    required this.clinicId,
    this.fromIso,
    this.toIso,
    this.page = 0,
    this.size = 20,
  });

  @override
  bool operator ==(Object other) =>
      identical(this, other) ||
      other is AppointmentLedgerQuery &&
          other.clinicId == clinicId &&
          other.fromIso == fromIso &&
          other.toIso == toIso &&
          other.page == page &&
          other.size == size;

  @override
  int get hashCode => Object.hash(clinicId, fromIso, toIso, page, size);
}

/// Builds an appointment-ledger query using the shared finance month filter.
AppointmentLedgerQuery appointmentLedgerQueryForClinic(
  dynamic ref,
  int clinicId, {
  int page = 0,
  int size = 20,
}) {
  final range = financeMonthRangeIso(ref, clinicId);
  return AppointmentLedgerQuery(
    clinicId: clinicId,
    fromIso: range.fromIso,
    toIso: range.toIso,
    page: page,
    size: size,
  );
}

final clinicFinanceRefreshTickProvider =
    StateProvider.family<int, int>((ref, clinicId) => 0);

final clinicFinanceDashboardProvider =
    FutureProvider.family<FinanceDashboardStats, int>((ref, clinicId) async {
  final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
  final clinic = ref.watch(selectedClinicProvider);
  String? fromIso;
  String? toIso;
  if (month != null) {
    final range = monthRangeUtcInTimezone(
      month.year,
      month.month,
      clinic?.timeZone,
    );
    fromIso = range.fromUtc.toIso8601String();
    toIso = range.toUtc.toIso8601String();
  }
  return fetchFinanceDashboard(
    ref,
    clinicId: clinicId,
    fromIso: fromIso,
    toIso: toIso,
  );
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
    FutureProvider.family<Map<String, dynamic>, AppointmentLedgerQuery>(
        (ref, query) {
  return fetchAppointmentLedgerPage(
    ref,
    clinicId: query.clinicId,
    fromIso: query.fromIso,
    toIso: query.toIso,
    page: query.page,
    size: query.size,
  );
});

/// Visit-level ledger rows for the selected month (dashboard drill-downs).
final clinicFinanceMonthVisitsProvider =
    FutureProvider.family<List<AppointmentLedgerRowDto>, int>((ref, clinicId) async {
  final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
  if (month == null) return [];
  final range = financeMonthRangeIso(ref, clinicId);
  final page = await fetchAppointmentLedgerPage(
    ref,
    clinicId: clinicId,
    fromIso: range.fromIso,
    toIso: range.toIso,
    page: 0,
    size: 500,
  );
  final content = page['content'] as List<dynamic>? ?? [];
  return content
      .whereType<Map>()
      .map((r) => AppointmentLedgerRowDto.fromJson(Map<String, dynamic>.from(r)))
      .toList();
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

/// Clinic currency for finance display — from clinic settings or dashboard API.
final clinicFinanceCurrencyProvider = Provider.family<String, int>((ref, clinicId) {
  final clinic = ref.watch(selectedClinicProvider);
  if (clinic != null &&
      clinic.clinicId == clinicId &&
      clinic.currency.isNotEmpty) {
    return clinic.currency;
  }
  return ref.watch(clinicFinanceDashboardProvider(clinicId)).valueOrNull?.currency ??
      'UZS';
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
  ref.invalidate(clinicAppointmentLedgerProvider);
  ref.invalidate(clinicFinanceMonthVisitsProvider(clinicId));
  ref.invalidate(clinicDoctorEarningsProvider);
  for (final f in ['all', 'pending', 'overdue', 'paid']) {
    ref.invalidate(clinicInstallmentItemsProvider((clinicId, f)));
  }
}

/// Refreshes every provider backing Clinic → Finance and related treatment-plan totals.
void refreshClinicFinancialData(dynamic ref, int clinicId) {
  invalidateClinicFinanceTabDataForClinic(ref, clinicId);
  ref.read(clinicFinanceRefreshTickProvider(clinicId).notifier).state++;
  ref.invalidate(treatmentPlansForClinicProvider);
  ref.invalidate(treatmentPlansForPatientProvider);
}
