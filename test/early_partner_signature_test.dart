import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/legal/pdf/early_partner_signature.dart';

void main() {
  group('buildPartnerSignatureText', () {
    test('uses first and last name when both provided', () {
      expect(
        buildPartnerSignatureText(
          firstName: 'Alisher',
          lastName: 'Karimov',
          fullName: 'Ignored Full',
        ),
        'Alisher Karimov',
      );
    });

    test('falls back to full name', () {
      expect(
        buildPartnerSignatureText(fullName: 'Maftuna Nurmaxammadova'),
        'Maftuna Nurmaxammadova',
      );
    });

    test('returns empty when no names', () {
      expect(buildPartnerSignatureText(), '');
    });
  });
}
