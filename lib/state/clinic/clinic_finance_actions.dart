import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';

Future<PaymentHistoryItem> recordClinicPayment(
  dynamic ref, {
  required int clinicId,
  required int treatmentPlanId,
  required int amountMinor,
  required String currency,
  required String method,
  String? memo,
  int? financialRecordId,
  int? linkedAppointmentId,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{
    'treatmentPlanId': treatmentPlanId,
    'amountMinor': amountMinor,
    'currency': currency,
    'method': method,
  };
  if (memo != null && memo.isNotEmpty) body['memo'] = memo;
  if (financialRecordId != null) body['financialRecordId'] = financialRecordId;
  if (linkedAppointmentId != null) {
    body['linkedAppointmentId'] = linkedAppointmentId;
  }

  final res = await api.post(
    '/api/clinics/$clinicId/finance/payments',
    body,
  );
  if (res.statusCode != 200) throw Exception(_extractServerMessage(res));
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return PaymentHistoryItem.fromJson(data);
}

Future<FinancialRecordRow> createFinancialRecord(
  dynamic ref, {
  required int clinicId,
  required int patientId,
  int? treatmentPlanId,
  required String recordType,
  String? recordNumber,
  int subtotalMinor = 0,
  int discountMinor = 0,
  int taxMinor = 0,
  String currency = 'UZS',
  String? dueDate,
  String? notes,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{
    'patientId': patientId,
    'recordType': recordType,
    'subtotalMinor': subtotalMinor,
    'discountMinor': discountMinor,
    'taxMinor': taxMinor,
    'currency': currency,
  };
  if (treatmentPlanId != null) body['treatmentPlanId'] = treatmentPlanId;
  if (recordNumber != null) body['recordNumber'] = recordNumber;
  if (dueDate != null) body['dueDate'] = dueDate;
  if (notes != null) body['notes'] = notes;

  final res = await api.post(
    '/api/clinics/$clinicId/finance/records',
    body,
  );
  if (res.statusCode != 200) {
    throw Exception(_extractServerMessage(res));
  }
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return FinancialRecordRow.fromJson(data);
}

Future<InstallmentPlanSummary?> createInstallmentPlan(
  dynamic ref, {
  required int clinicId,
  required int treatmentPlanId,
  required int totalAmountMinor,
  String currency = 'UZS',
  required int numInstallments,
  String frequency = 'MONTHLY',
  required String startDate,
  String? notes,
  List<Map<String, dynamic>>? scheduleItems,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{
    'treatmentPlanId': treatmentPlanId,
    'totalAmountMinor': totalAmountMinor,
    'currency': currency,
    'numInstallments': numInstallments,
    'frequency': frequency,
    'startDate': startDate,
  };
  if (notes != null) body['notes'] = notes;
  if (scheduleItems != null && scheduleItems.isNotEmpty) {
    body['scheduleItems'] = scheduleItems;
  }

  final res = await api.post(
    '/api/clinics/$clinicId/finance/installment-plans',
    body,
  );
  if (res.statusCode != 200) {
    // Surface the server-side message so the wizard can show a useful
    // snackbar (e.g. "Installment amounts sum to X but plan total is Y").
    String message = 'HTTP ${res.statusCode}';
    try {
      final body = json.decode(utf8.decode(res.bodyBytes));
      if (body is Map && body['message'] != null) {
        message = body['message'].toString();
      }
    } catch (_) {
      // Fall through with the generic HTTP code.
    }
    throw Exception(message);
  }
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return InstallmentPlanSummary.fromJson(data);
}

Future<bool> markInstallmentItemPaid(
  dynamic ref, {
  required int clinicId,
  required int itemId,
  required String method,
  String? memo,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{'method': method};
  if (memo != null && memo.isNotEmpty) body['memo'] = memo;
  final res = await api.post(
    '/api/clinics/$clinicId/finance/installment-items/$itemId/mark-paid',
    body,
  );
  if (res.statusCode != 200) {
    throw Exception(_extractServerMessage(res));
  }
  return true;
}

Future<bool> patchInstallmentItem(
  dynamic ref, {
  required int clinicId,
  required int itemId,
  String? status,
  String? dueDate,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final body = <String, dynamic>{};
  if (status != null) body['status'] = status;
  if (dueDate != null) body['dueDate'] = dueDate;
  final res = await api.patch(
    '/api/clinics/$clinicId/finance/installment-items/$itemId',
    body,
  );
  if (res.statusCode != 200) {
    throw Exception(_extractServerMessage(res));
  }
  return true;
}

String _extractServerMessage(http.Response res) {
  try {
    final body = json.decode(utf8.decode(res.bodyBytes));
    if (body is Map && body['message'] != null) {
      return body['message'].toString();
    }
  } catch (_) {}
  return 'HTTP ${res.statusCode}';
}

Future<bool> notifyInstallmentItem(dynamic ref, {required int clinicId, required int itemId}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.post(
    '/api/clinics/$clinicId/finance/installment-items/$itemId/notify',
    <String, dynamic>{},
  );
  return res.statusCode == 200;
}

Future<Map<String, dynamic>> fetchAppointmentLedgerPage(
  dynamic ref, {
  required int clinicId,
  int page = 0,
  int size = 20,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get(
    '/api/clinics/$clinicId/finance/appointment-ledger?page=$page&size=$size',
  );
  if (res.statusCode != 200) {
    throw Exception('Ledger ${res.statusCode}');
  }
  return json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
}

Future<List<DoctorEarningRow>> fetchDoctorEarnings(
  dynamic ref, {
  required int clinicId,
  String? fromIso,
  String? toIso,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final q = <String>[];
  if (fromIso != null) q.add('from=$fromIso');
  if (toIso != null) q.add('to=$toIso');
  final qs = q.isEmpty ? '' : '?${q.join('&')}';
  final res = await api.get('/api/clinics/$clinicId/finance/doctor-earnings$qs');
  if (res.statusCode != 200) return [];
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => DoctorEarningRow.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}

Future<Map<String, dynamic>> fetchFinanceAudit(
  dynamic ref, {
  required int clinicId,
  int? recordId,
  int page = 0,
  int size = 50,
}) async {
  final api = ref.read(doctorApiClientProvider);
  final q = <String>['page=$page', 'size=$size'];
  if (recordId != null) q.add('recordId=$recordId');
  final res = await api.get('/api/clinics/$clinicId/finance/audit?${q.join('&')}');
  if (res.statusCode != 200) {
    throw Exception('Audit ${res.statusCode}');
  }
  return json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
}

/// Clinic-wide installment rows for the Finance → Installments tab.
/// [filter]: `all`, `pending`, `overdue`, or `paid`.
Future<List<InstallmentItemListRow>> fetchClinicInstallmentItems(
  dynamic ref, {
  required int clinicId,
  String filter = 'all',
}) async {
  final api = ref.read(doctorApiClientProvider);
  final res = await api.get(
    '/api/clinics/$clinicId/finance/installment-items?filter=${Uri.encodeQueryComponent(filter)}',
  );
  if (res.statusCode != 200) {
    throw Exception('Installments ${res.statusCode}');
  }
  final list = json.decode(utf8.decode(res.bodyBytes));
  if (list is! List) return [];
  return list
      .whereType<Map>()
      .map((e) => InstallmentItemListRow.fromJson(Map<String, dynamic>.from(e)))
      .toList();
}
