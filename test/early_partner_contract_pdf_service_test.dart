import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_contract_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_contract_pdf_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('generateEarlyPartnerContractPdf', () {
    test('blank template produces multi-page branded PDF', () async {
      final bytes = await generateEarlyPartnerContractPdf(
        data: EarlyPartnerContractPdfData.blankTemplate(),
      );
      expect(bytes.length, greaterThan(2000));
      expect(String.fromCharCodes(bytes.take(4)), '%PDF');
    });

    test('filled partner fields produce valid PDF', () async {
      final bytes = await generateEarlyPartnerContractPdf(
        data: EarlyPartnerContractPdfData(
          contractNumber: 'SHIFA-0461',
          effectiveDate: DateTime(2026, 6, 1),
          partnerFullName: 'Karimov Alisher',
          partnerClinic: 'Andijon Dental',
          roleDoctor: true,
          partnerPhone: '+998 90 123 45 67',
          partnerEmail: 'partner@example.com',
          shifaSignedDate: DateTime(2026, 5, 25),
        ),
      );
      expect(bytes.length, greaterThan(2000));
    });
  });
}
