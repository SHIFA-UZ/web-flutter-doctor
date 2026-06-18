import 'dart:convert';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

Future<List<TreatmentPlanSummaryDto>> fetchTreatmentPlansForPatient(
  dynamic ref, {
  required int clinicId,
  required int patientId,
  String? status,
  String? planKind,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final parts = <String>[
    'clinicId=$clinicId',
    'patientId=$patientId',
  ];
  if (status != null && status.isNotEmpty) {
    parts.add('status=${Uri.encodeQueryComponent(status)}');
  }
  if (planKind != null && planKind.isNotEmpty) {
    parts.add('planKind=${Uri.encodeQueryComponent(planKind)}');
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

/// Clinic-wide treatment plan list with optional status / free-text filters.
/// Mirrors `GET /api/treatment-plans?clinicId=...&status=...&q=...` and is
/// used by the doctor "Treatment plans" tab so the doctor can see every plan
/// (with patient + doctor names + totals) without picking a patient first.
Future<List<TreatmentPlanSummaryDto>> fetchTreatmentPlansForClinic(
  dynamic ref, {
  required int clinicId,
  String? status,
  String? query,
  String? planKind,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final parts = <String>['clinicId=$clinicId'];
  if (status != null && status.isNotEmpty) {
    parts.add('status=${Uri.encodeQueryComponent(status)}');
  }
  if (planKind != null && planKind.isNotEmpty) {
    parts.add('planKind=${Uri.encodeQueryComponent(planKind)}');
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

Future<List<TreatmentPlanVisitDto>> fetchTreatmentPlanVisits(
  dynamic ref,
  int planId,
) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get('/api/treatment-plans/$planId/visits');
  if (res.statusCode != 200) {
    throw Exception('Plan visits ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => TreatmentPlanVisitDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
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
  List<int>? attendingDoctorIds,
  List<String>? symptoms,
  String? dentalPlanDocumentation,
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
  if (attendingDoctorIds != null && attendingDoctorIds.isNotEmpty) {
    body['attendingDoctorIds'] = attendingDoctorIds;
  }
  if (symptoms != null && symptoms.isNotEmpty) body['symptoms'] = symptoms;
  if (dentalPlanDocumentation != null && dentalPlanDocumentation.isNotEmpty) {
    body['dentalPlanDocumentation'] = dentalPlanDocumentation;
  }

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
  String? dentalPlanDocumentation,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{};
  if (title != null) body['title'] = title;
  if (diagnosis != null) body['diagnosis'] = diagnosis;
  if (notes != null) body['notes'] = notes;
  if (paymentReminderDays != null) body['paymentReminderDays'] = paymentReminderDays;
  if (attendingDoctorId != null) body['attendingDoctorId'] = attendingDoctorId;
  if (symptoms != null) body['symptoms'] = symptoms;
  if (dentalPlanDocumentation != null) {
    body['dentalPlanDocumentation'] = dentalPlanDocumentation;
  }

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

/// Locations for a clinic doctor (multi-site slot picker).
Future<List<PlanDoctorLocationDto>> fetchTreatmentPlanDoctorLocations(
  dynamic ref, {
  required int clinicId,
  required int doctorId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get(
    '/api/treatment-plans/doctor-locations?clinicId=$clinicId&doctorId=$doctorId',
  );
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => PlanDoctorLocationDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}

/// Free slots for a doctor on a given calendar day. Used by the wizard's
/// "Schedule visits" section to surface availability when picking new times.
Future<List<FreeSlotDto>> fetchTreatmentPlanFreeSlots(
  dynamic ref, {
  required int clinicId,
  required int doctorId,
  required String dayIso, // YYYY-MM-DD
  int? locationId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final parts = <String>[
    'clinicId=$clinicId',
    'doctorId=$doctorId',
    'day=$dayIso',
  ];
  if (locationId != null) parts.add('locationId=$locationId');
  final res = await api.get('/api/treatment-plans/free-slots?${parts.join('&')}');
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => FreeSlotDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}

/// Books a batch of free slots against a treatment plan. The backend
/// validates each one for doctor + patient conflict and creates a
/// confirmed appointment per slot, optionally linking it to a plan line.
Future<TreatmentPlanDetailDto?> bookTreatmentPlanSlots(
  dynamic ref, {
  required int planId,
  required List<Map<String, dynamic>> slots,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post('/api/treatment-plans/$planId/book-slots', slots);
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanDetailDto.fromJson(Map<String, dynamic>.from(m));
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

Future<List<FulfillmentCandidateDto>> fetchFulfillmentCandidates(
  dynamic ref, {
  required int planId,
  int? appointmentId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final q = appointmentId != null ? '?appointmentId=$appointmentId' : '';
  final res = await api.get('/api/treatment-plans/$planId/fulfillment-candidates$q');
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => FulfillmentCandidateDto.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}

Future<TreatmentPlanDetailDto?> fulfillTreatmentPlanLines(
  dynamic ref, {
  required int planId,
  required int appointmentId,
  required List<int> lineIds,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post(
    '/api/treatment-plans/$planId/appointments/$appointmentId/fulfill',
    {'lineIds': lineIds},
  );
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanDetailDto.fromJson(Map<String, dynamic>.from(m));
}

Future<TreatmentPlanDetailDto?> appendTreatmentPlanLines(
  dynamic ref, {
  required int planId,
  int? appointmentId,
  required List<Map<String, dynamic>> lines,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post(
    '/api/treatment-plans/$planId/lines/append',
    {
      if (appointmentId != null) 'appointmentId': appointmentId,
      'lines': lines,
    },
  );
  if (res.statusCode != 200) return null;
  final m = json.decode(utf8.decode(res.bodyBytes));
  if (m is! Map) return null;
  return TreatmentPlanDetailDto.fromJson(Map<String, dynamic>.from(m));
}
