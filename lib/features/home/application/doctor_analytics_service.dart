// Fetches and caches doctor analytics from backend. No blocking UI calls.

import 'dart:convert';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/home/domain/analytics_models.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

final doctorAnalyticsServiceProvider = Provider<DoctorAnalyticsService>((ref) {
  final api = ref.watch(apiClientProvider);
  return DoctorAnalyticsService(api);
});

class DoctorAnalyticsService {
  final dynamic _api; // ApiClient

  DoctorAnalyticsService(this._api);

  Future<DoctorAnalyticsOverview> getOverview() async {
    final res = await _api.get('/api/doctor/analytics/overview');
    if (res.statusCode != 200) {
      throw Exception('Analytics overview: ${res.statusCode} ${res.body}');
    }
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return DoctorAnalyticsOverview.fromJson(map);
  }

  Future<List<AppointmentTrendPoint>> getAppointmentsTrend({int days = 7}) async {
    final res = await _api.get(
      '/api/doctor/analytics/appointments-trend',
      params: {'days': days.toString()},
    );
    if (res.statusCode != 200) {
      throw Exception('Appointments trend: ${res.statusCode} ${res.body}');
    }
    final list = json.decode(utf8.decode(res.bodyBytes)) as List<dynamic>;
    return list
        .map((e) => AppointmentTrendPoint.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<ConsultationTypes> getConsultationTypes() async {
    final res = await _api.get('/api/doctor/analytics/consultation-types');
    if (res.statusCode != 200) {
      throw Exception('Consultation types: ${res.statusCode} ${res.body}');
    }
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return ConsultationTypes.fromJson(map);
  }

  Future<DoctorEngagement> getEngagement() async {
    final res = await _api.get('/api/doctor/analytics/engagement');
    if (res.statusCode != 200) {
      throw Exception('Engagement: ${res.statusCode} ${res.body}');
    }
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return DoctorEngagement.fromJson(map);
  }

  Future<DoctorSmsUsage> getSmsUsage({
    required String fromIso,
    required String toIso,
  }) async {
    final res = await _api.get(
      '/api/doctor/analytics/sms-usage',
      params: {'from': fromIso, 'to': toIso},
    );
    if (res.statusCode != 200) {
      throw Exception('SMS usage: ${res.statusCode} ${res.body}');
    }
    final map = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    return DoctorSmsUsage.fromJson(map);
  }
}
