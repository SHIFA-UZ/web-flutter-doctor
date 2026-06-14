// Analytics providers: fetch from API and expose to home screen. Loading/error handled by widgets.

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/home/application/doctor_analytics_service.dart';
import 'package:shifa_doc_app_v1/features/home/domain/analytics_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_date_range_provider.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

final doctorAnalyticsOverviewProvider =
    FutureProvider.autoDispose<DoctorAnalyticsOverview>((ref) async {
  if (ref.read(authTokenProvider) == null || ref.read(authTokenProvider)!.isEmpty) {
    throw StateError('Not authenticated');
  }
  final service = ref.watch(doctorAnalyticsServiceProvider);
  
  // Refetch KPIs when today's calendar loads or changes (no background polling).
  ref.watch(todayAppointmentsProvider);

  return service.getOverview();
});

final doctorAnalyticsTrendProvider =
    FutureProvider<List<AppointmentTrendPoint>>((ref) async {
  final service = ref.watch(doctorAnalyticsServiceProvider);
  final days = ref.watch(homeDashboardDateRangeProvider).dayCount;
  return service.getAppointmentsTrend(days: days.clamp(1, 90));
});

final doctorConsultationTypesProvider =
    FutureProvider<ConsultationTypes>((ref) async {
  final service = ref.watch(doctorAnalyticsServiceProvider);
  return service.getConsultationTypes();
});

final doctorEngagementProvider = FutureProvider<DoctorEngagement>((ref) async {
  final service = ref.watch(doctorAnalyticsServiceProvider);
  return service.getEngagement();
});

String _ymd(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

final doctorSmsUsageProvider = FutureProvider.autoDispose<DoctorSmsUsage>((ref) async {
  final service = ref.watch(doctorAnalyticsServiceProvider);
  final range = ref.watch(homeDashboardDateRangeProvider);
  return service.getSmsUsage(
    fromIso: _ymd(range.start),
    toIso: _ymd(range.end),
  );
});

// Legacy stub providers (used by charts if API not yet wired) — kept for backward compatibility.
// TODO: Remove once all widgets use API providers above.
final todayAnalyticsProvider = Provider((ref) {
  return {'appointments': 12, 'inPerson': 8, 'video': 4, 'nextInMinutes': 18};
});

final clinicalLoadProvider = Provider((ref) {
  return {'avgPerDay': 9.2, 'documents': 43, 'repeatPercent': 62};
});

final aiImpactProvider = Provider((ref) {
  return {'queriesToday': 14, 'timeSavedMin': 35, 'escalations': 1};
});

final appointmentsTrendProvider = Provider<List<int>>((ref) {
  return [6, 8, 7, 9, 11, 10, 12];
});

final visitTypeSplitProvider = Provider<Map<String, int>>((ref) {
  return {'In-person': 8, 'Video': 4};
});
