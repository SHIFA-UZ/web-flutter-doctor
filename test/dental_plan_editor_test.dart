import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_plan_editor_panel.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';

/// Sync delegate so widget tests do not depend on asset bundle JSON load.
class _SyncAppLocalizationsDelegate
    extends LocalizationsDelegate<AppLocalizations> {
  const _SyncAppLocalizationsDelegate();

  @override
  bool isSupported(Locale locale) =>
      ['en', 'uz', 'ru'].contains(locale.languageCode);

  @override
  Future<AppLocalizations> load(Locale locale) async =>
      AppLocalizations(locale);

  @override
  bool shouldReload(_SyncAppLocalizationsDelegate old) => false;
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('DentalPlanEditorPanel.buildLineRequests', () {
    testWidgets('flattens teeth into lines with FDI metadata and discount', (
      tester,
    ) async {
      const catalog = [
        PlanServiceOption(
          key: 'svc-a',
          kind: PlanServiceOption.kindClinicCatalog,
          catalogItemId: 10,
          doctorServiceId: null,
          title: 'Filling',
          code: null,
          defaultPriceMinor: 100000,
          currency: 'UZS',
          active: true,
          offeredByDoctorIds: [],
          offeredByDoctorNames: [],
        ),
        PlanServiceOption(
          key: 'svc-b',
          kind: PlanServiceOption.kindClinicCatalog,
          catalogItemId: 11,
          doctorServiceId: null,
          title: 'Crown',
          code: null,
          defaultPriceMinor: 200000,
          currency: 'UZS',
          active: true,
          offeredByDoctorIds: [],
          offeredByDoctorNames: [],
        ),
      ];

      const initialJson = '''
{
  "version": 2,
  "dentition": "permanent",
  "teeth": {
    "16": [
      {"serviceKey": "svc-a", "title": "Filling", "amountMinor": 100000, "currency": "UZS"}
    ],
    "26": [
      {"serviceKey": "svc-b", "title": "Crown", "amountMinor": 200000, "currency": "UZS"}
    ]
  },
  "discountPercent": 10,
  "notes": "Phase 1"
}
''';

      await tester.pumpWidget(
        MaterialApp(
          locale: const Locale('en'),
          supportedLocales: const [Locale('en'), Locale('uz'), Locale('ru')],
          localizationsDelegates: const [
            _SyncAppLocalizationsDelegate(),
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          home: Scaffold(
            body: SingleChildScrollView(
              child: DentalPlanEditorPanel(
                brand: Colors.teal,
                catalog: catalog,
                initialDocumentationJson: initialJson,
              ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();

      expect(find.byType(DentalPlanEditorPanel), findsOneWidget);
      final state = tester.state<DentalPlanEditorPanelState>(
        find.byType(DentalPlanEditorPanel),
      );
      final lines = state.buildLineRequests();

      expect(lines, hasLength(2));

      final meta16 = lines.firstWhere((l) => l['title'] == 'Filling');
      expect(meta16['catalogItemId'], 10);
      expect(meta16['discountMinor'], greaterThan(0));
      final specialty16 = jsonDecode(
        meta16['specialtyMetadata'] as String,
      ) as Map<String, dynamic>;
      expect(specialty16['fdi'], '16');
      expect(specialty16['dentition'], 'permanent');

      final meta26 = lines.firstWhere((l) => l['title'] == 'Crown');
      expect(meta26['discountMinor'], greaterThan(0));

      final totalDiscount =
          lines.fold<int>(0, (s, l) => s + (l['discountMinor'] as int));
      expect(totalDiscount, 30000);
    });
  });
}
