// lib/features/legal/pdf/early_partner_contract_from_issue.dart

import 'early_partner_contract_pdf_data.dart';

/// Maps admin API issue payload to PDF input.
EarlyPartnerContractPdfData earlyPartnerContractPdfDataFromIssue(
  Map<String, dynamic> json,
) {
  DateTime parseDate(String? raw, DateTime fallback) {
    if (raw == null || raw.isEmpty) return fallback;
    final datePart = raw.length >= 10 ? raw.substring(0, 10) : raw;
    return DateTime.tryParse(datePart) ?? fallback;
  }

  final effective = parseDate(
    json['effectiveDate'] as String?,
    DateTime(2026, 6, 1),
  );

  return EarlyPartnerContractPdfData(
    contractNumber: json['contractNumber'] as String? ?? 'SHIFA-0000',
    effectiveDate: effective,
    termMonths: (json['termMonths'] as num?)?.toInt() ?? 6,
    partnerFullName: json['partnerFullName'] as String?,
    partnerClinic: json['partnerClinic'] as String?,
    roleDoctor: json['roleDoctor'] as bool? ?? true,
    rolePatient: json['rolePatient'] as bool? ?? false,
    roleBoth: json['roleBoth'] as bool? ?? false,
    partnerPhone: json['partnerPhone'] as String?,
    partnerEmail: json['partnerEmail'] as String?,
    shifaSignedDate: DateTime.now(),
  );
}
