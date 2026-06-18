import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

void main() {
  group('plan fulfillment candidates', () {
    test('groups open lines by FDI for tooth sheet', () {
      const candidates = [
        FulfillmentCandidateDto(
          lineId: 1,
          title: 'Crown prep',
          fdi: '21',
          unitPriceMinor: 15000000,
          quantity: 1,
          discountMinor: 0,
          currency: 'UZS',
          lineTotalMinor: 15000000,
          status: 'OPEN',
          toothMatch: true,
        ),
        FulfillmentCandidateDto(
          lineId: 2,
          title: 'Root canal',
          fdi: '21',
          unitPriceMinor: 20000000,
          quantity: 1,
          discountMinor: 0,
          currency: 'UZS',
          lineTotalMinor: 20000000,
          status: 'OPEN',
          toothMatch: true,
        ),
        FulfillmentCandidateDto(
          lineId: 3,
          title: 'Panoramic X-ray',
          unitPriceMinor: 5000000,
          quantity: 1,
          discountMinor: 0,
          currency: 'UZS',
          lineTotalMinor: 5000000,
          status: 'OPEN',
          toothMatch: false,
        ),
      ];

      final for21 =
          candidates.where((c) => c.fdi == '21').toList(growable: false);
      final general = candidates
          .where((c) => c.fdi == null || c.fdi!.isEmpty)
          .toList(growable: false);

      expect(for21, hasLength(2));
      expect(general, hasLength(1));
      expect(for21.map((c) => c.title), containsAll(['Crown prep', 'Root canal']));
    });
  });
}
