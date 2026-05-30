// lib/features/admin/services/admin_early_partner_contract_pdf.dart

import 'dart:convert';
import 'dart:typed_data';

import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_contract_from_issue.dart';
import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_contract_pdf_service.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_actions.dart';

/// Issues/refreshes contract on server, builds latest PDF bytes for download.
Future<({Uint8List bytes, String contractNumber, bool newAllocation})>
    adminGenerateEarlyPartnerContractPdf(
  AdminActions actions,
  int doctorId,
) async {
  final response = await actions.issueEarlyPartnerContract(doctorId);
  final json = jsonDecode(response.body) as Map<String, dynamic>;
  final data = earlyPartnerContractPdfDataFromIssue(json);
  final bytes = await generateEarlyPartnerContractPdf(data: data);
  return (
    bytes: bytes,
    contractNumber: data.contractNumber,
    newAllocation: json['newAllocation'] as bool? ?? false,
  );
}

String earlyPartnerContractPdfFilename(String contractNumber, String doctorName) {
  final slug = doctorName
      .toLowerCase()
      .replaceAll(RegExp(r'[^a-z0-9]+'), '_')
      .replaceAll(RegExp(r'_+'), '_')
      .replaceAll(RegExp(r'^_|_$'), '');
  final safeSlug = slug.isEmpty ? 'hamkor' : slug;
  return '${contractNumber}_$safeSlug.pdf';
}
