// Analytics providers: fetch from API and expose to home screen. Loading/error handled by widgets.

import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/home/application/doctor_analytics_service.dart';
import 'package:shifa_doc_app_v1/features/home/domain/analytics_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

final doctorAnalyticsOverviewProvider =
    FutureProvider.autoDispose<DoctorAnalyticsOverview>((ref) async {
  if (ref.read(authTokenProvider) == null || ref.read(authTokenProvider)!.isEmpty) {
    throw StateError('Not authenticated');
  }
  final service = ref.watch(doctorAnalyticsServiceProvider);
  
  // Watch appointments to auto-refresh KPIs when appointments change
  ref.watch(todayAppointmentsProvider);
  
  // Auto-refresh every 30 seconds for real-time updates
  final timer = Timer.periodic(const Duration(seconds: 30), (_) {
    try {
      ref.invalidateSelf();
    } catch (_) {
      // Provider already disposed, ignore
    }
  });
  
  ref.onDispose(() {
    timer.cancel();
  });
  
  return service.getOverview();
});

final doctorAnalyticsTrendProvider =
    FutureProvider<List<AppointmentTrendPoint>>((ref) async {
  final service = ref.watch(doctorAnalyticsServiceProvider);
  return service.getAppointmentsTrend(days: 7);
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
