import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_contract_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_contract_pdf_service.dart';

/// Run manually to write a sample PDF:
///   flutter test test/manual/export_early_partner_contract_test.dart
void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  test('export blank SHIFA-0461 contract to docs/contracts/output', () async {
    final bytes = await generateEarlyPartnerContractPdf(
      data: EarlyPartnerContractPdfData.blankTemplate(),
    );

    final dir = Directory('docs/contracts/output');
    if (!await dir.exists()) await dir.create(recursive: true);

    final file = File('${dir.path}/SHIFA-0461-erta-hamkor-shartnoma.pdf');
    await file.writeAsBytes(bytes);

    expect(await file.exists(), isTrue);
    expect(bytes.length, greaterThan(2000));
  });
}
