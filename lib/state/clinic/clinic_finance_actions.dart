import 'dart:convert';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';

Future<PaymentHistoryItem?> recordClinicPayment(
  Ref ref, {
  required int clinicId,
  required int treatmentPlanId,
  required int amountMinor,
  required String currency,
  required String method,
  String? memo,
  int? financialRecordId,
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

  final res = await api.post(
    '/api/clinics/$clinicId/finance/payments',
    json.encode(body),
  );
  if (res.statusCode != 200) return null;
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return PaymentHistoryItem.fromJson(data);
}

Future<FinancialRecordRow?> createFinancialRecord(
  Ref ref, {
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
    json.encode(body),
  );
  if (res.statusCode != 200) return null;
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return FinancialRecordRow.fromJson(data);
}

Future<InstallmentPlanSummary?> createInstallmentPlan(
  Ref ref, {
  required int clinicId,
  required int treatmentPlanId,
  required int totalAmountMinor,
  String currency = 'UZS',
  required int numInstallments,
  String frequency = 'MONTHLY',
  required String startDate,
  String? notes,
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

  final res = await api.post(
    '/api/clinics/$clinicId/finance/installment-plans',
    json.encode(body),
  );
  if (res.statusCode != 200) return null;
  final data = json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  return InstallmentPlanSummary.fromJson(data);
}
