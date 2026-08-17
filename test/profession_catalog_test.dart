import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/models/profession_model.dart';

void main() {
  test('onboarding catalog includes Urologist with full surname-style labels', () {
    final urologist = ProfessionData.findByEnglish('Urologist');
    expect(urologist, isNotNull);
    expect(urologist!.uzbek, 'Urolog');
  });

  test('onboarding catalog includes missing top physician specialties', () {
    final names = ProfessionData.allProfessions.map((p) => p.english).toSet();
    expect(
      names,
      containsAll([
        'Urologist',
        'Andrologist',
        'Hospitalist',
        'Colorectal Surgeon (Proctologist)',
        'Radiation Oncologist',
        'Interventional Cardiologist',
        'Interventional Radiologist',
        'Pain Medicine Specialist',
        'Hepatologist',
        'Pediatric Urologist',
      ]),
    );
  });
}
