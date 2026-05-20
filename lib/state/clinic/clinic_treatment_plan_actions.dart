import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

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

  final res = await api.post('/api/treatment-plans', json.encode(body));
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

  final res = await api.patch('/api/treatment-plans/$planId', json.encode(body));
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
    json.encode(lines),
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
    json.encode(pairs),
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
    json.encode({'status': status}),
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
