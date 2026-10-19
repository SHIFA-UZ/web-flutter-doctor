import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_welcome.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_models.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

void main() {
  testWidgets('welcome prompts start a chat and recent rows show a preview', (
    tester,
  ) async {
    String? sent;
    final recent = IntelligenceChatSession(
      id: 'chat-1',
      title: IntelligenceChatSession.untitled,
      createdAt: DateTime.utc(2026, 10, 18, 11),
      updatedAt: DateTime.now().subtract(const Duration(minutes: 2)),
      messages: [
        IntelligenceChatMessage(
          role: 'user',
          content: 'Help me find a patient.',
          at: DateTime.utc(2026, 10, 18, 11),
        ),
        IntelligenceChatMessage(
          role: 'assistant',
          content: 'Which patient should I look up?',
          at: DateTime.utc(2026, 10, 18, 11, 1),
          toolName: 'search_patients',
          cardPayload: {
            'cardType': 'patient_search',
            'matches': [
              {'patientId': 4, 'patientName': 'Ada Lovelace'},
            ],
          },
        ),
      ],
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          profileAllProvider.overrideWith(
            (ref) async => ProfileAll(
              {'firstName': 'Ada', 'lastName': 'Lovelace'},
              const {},
              const {},
              const {},
              const {},
            ),
          ),
        ],
        child: MaterialApp(
          locale: const Locale('en'),
          localizationsDelegates: const [
            _ImmediateL10nDelegate(),
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en'), Locale('ru'), Locale('uz')],
          home: Scaffold(
            body: IntelligenceWelcome(
              recentSessions: [recent],
              onSendPrompt: (text) => sent = text,
              onOpenSession: (_) {},
              onRenameSession: (_, __) {},
              onDeleteSession: (_) {},
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(tester.takeException(), isNull);
    expect(find.byType(IntelligenceWelcome), findsOneWidget);
    expect(find.byType(Scaffold), findsOneWidget);

    final texts = tester
        .widgetList<Text>(find.byType(Text))
        .map((w) => w.data ?? w.textSpan?.toPlainText() ?? '')
        .join(' | ');
    expect(texts, contains('Ada Lovelace'));
    expect(find.text("Today's schedule"), findsOneWidget);
    expect(find.text('Find a patient'), findsWidgets);
    expect(find.text('Ask about your day'), findsOneWidget);
    expect(find.text('Search records'), findsOneWidget);
    expect(find.text('Which patient should I look up?'), findsOneWidget);
    expect(find.text('Recent conversations'), findsOneWidget);

    await tester.tap(find.text('Search records'));
    await tester.pump();
    expect(sent, 'Help me find a patient.');

    await tester.tap(find.text("Today's schedule"));
    await tester.pump();
    expect(sent, 'Summarize my schedule for today.');
  });
}

class _ImmediateL10nDelegate extends LocalizationsDelegate<AppLocalizations> {
  const _ImmediateL10nDelegate();

  @override
  bool isSupported(Locale locale) => true;

  @override
  Future<AppLocalizations> load(Locale locale) async =>
      AppLocalizations(const Locale('en'));

  @override
  bool shouldReload(_ImmediateL10nDelegate old) => false;
}
