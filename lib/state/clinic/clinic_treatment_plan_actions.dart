import 'dart:convert';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

Future<List<TreatmentPlanSummaryDto>> fetchTreatmentPlansForPatient(
  dynamic ref, {
  required int clinicId,
  required int patientId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get(
    '/api/treatment-plans?clinicId=$clinicId&patientId=$patientId',
  );
  if (res.statusCode != 200) {
    throw Exception('Plans ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}

/// Clinic-wide treatment plan list with optional status / free-text filters.
/// Mirrors `GET /api/treatment-plans?clinicId=...&status=...&q=...` and is
/// used by the doctor "Treatment plans" tab so the doctor can see every plan
/// (with patient + doctor names + totals) without picking a patient first.
Future<List<TreatmentPlanSummaryDto>> fetchTreatmentPlansForClinic(
  dynamic ref, {
  required int clinicId,
  String? status,
  String? query,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final parts = <String>['clinicId=$clinicId'];
  if (status != null && status.isNotEmpty) {
    parts.add('status=${Uri.encodeQueryComponent(status)}');
  }
  if (query != null && query.trim().isNotEmpty) {
    parts.add('q=${Uri.encodeQueryComponent(query.trim())}');
  }
  final res = await api.get('/api/treatment-plans?${parts.join('&')}');
  if (res.statusCode != 200) {
    throw Exception('Plans ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}

Future<TreatmentPlanDetailDto?> fetchTreatmentPlanDetail(dynamic ref, int planId) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get('/api/treatment-plans/$planId/detail');
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanDetailDto.fromJson(Map<String, dynamic>.from(m));
}

Future<TreatmentPlanSummaryDto?> createTreatmentPlan(
  dynamic ref, {
  required int clinicId,
  required int patientId,
  String? title,
  String? diagnosis,
  String? notes,
  int? paymentReminderDays,
  int? attendingDoctorId,
  List<String>? symptoms,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{
    'clinicId': clinicId,
    'patientId': patientId,
  };
  if (title != null && title.isNotEmpty) body['title'] = title;
  if (diagnosis != null && diagnosis.isNotEmpty) body['diagnosis'] = diagnosis;
  if (notes != null && notes.isNotEmpty) body['notes'] = notes;
  if (paymentReminderDays != null) body['paymentReminderDays'] = paymentReminderDays;
  if (attendingDoctorId != null) body['attendingDoctorId'] = attendingDoctorId;
  if (symptoms != null && symptoms.isNotEmpty) body['symptoms'] = symptoms;

  final res = await api.post('/api/treatment-plans', body);
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(m));
}

Future<TreatmentPlanSummaryDto?> patchTreatmentPlan(
  dynamic ref, {
  required int planId,
  String? title,
  String? diagnosis,
  String? notes,
  int? paymentReminderDays,
  int? attendingDoctorId,
  List<String>? symptoms,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{};
  if (title != null) body['title'] = title;
  if (diagnosis != null) body['diagnosis'] = diagnosis;
  if (notes != null) body['notes'] = notes;
  if (paymentReminderDays != null) body['paymentReminderDays'] = paymentReminderDays;
  if (attendingDoctorId != null) body['attendingDoctorId'] = attendingDoctorId;
  if (symptoms != null) body['symptoms'] = symptoms;

  final res = await api.patch('/api/treatment-plans/$planId', body);
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(m));
}

Future<TreatmentPlanSummaryDto?> replaceTreatmentPlanLines(
  dynamic ref, {
  required int planId,
  required List<Map<String, dynamic>> lines,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post(
    '/api/treatment-plans/$planId/lines',
    lines,
  );
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(m));
}

Future<TreatmentPlanDetailDto?> linkTreatmentPlanAppointments(
  dynamic ref, {
  required int planId,
  required List<Map<String, dynamic>> pairs,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post(
    '/api/treatment-plans/$planId/link-appointments',
    pairs,
  );
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanDetailDto.fromJson(Map<String, dynamic>.from(m));
}

Future<TreatmentPlanSummaryDto?> patchTreatmentPlanStatus(
  dynamic ref, {
  required int planId,
  required String status,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.patch(
    '/api/treatment-plans/$planId/status',
    {'status': status},
  );
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanSummaryDto.fromJson(Map<String, dynamic>.from(m));
}

Future<List<ClinicPatientAppointmentDto>> fetchPatientAppointments(
  dynamic ref, {
  required int clinicId,
  required int patientId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get(
    '/api/clinics/$clinicId/patients/$patientId/appointments',
  );
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => ClinicPatientAppointmentDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}
