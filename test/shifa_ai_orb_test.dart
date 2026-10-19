import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/shifa_ai_orb.dart';

void main() {
  test('listening energy stays quiet under the noise floor', () {
    expect(
      shifaOrbTargetIntensity(
        phase: VoiceSessionPhase.listening,
        level: 0.1,
        reduceMotion: false,
      ),
      0.08,
    );
    expect(
      shifaOrbTargetIntensity(
        phase: VoiceSessionPhase.listening,
        level: double.nan,
        reduceMotion: false,
      ),
      0.08,
    );
  });

  test('listening energy scales above the noise floor and clamps', () {
    final mid = shifaOrbTargetIntensity(
      phase: VoiceSessionPhase.listening,
      level: 0.58,
      reduceMotion: false,
    );
    expect(mid, greaterThan(0.08));
    expect(mid, lessThan(1));
    expect(
      shifaOrbTargetIntensity(
        phase: VoiceSessionPhase.listening,
        level: 4,
        reduceMotion: false,
      ),
      1,
    );
  });

  test('speaking and thinking ignore microphone level', () {
    expect(
      shifaOrbTargetIntensity(
        phase: VoiceSessionPhase.speaking,
        level: 0.9,
        reduceMotion: false,
      ),
      0.46,
    );
    expect(
      shifaOrbTargetIntensity(
        phase: VoiceSessionPhase.thinking,
        level: 0.9,
        reduceMotion: false,
      ),
      0.16,
    );
  });

  testWidgets('listening panel shows status, subtitle, logo, and stop', (
    tester,
  ) async {
    var stopped = 0;
    await tester.pumpWidget(
      _app(
        ShifaVoiceOrbPanel(
          phase: VoiceSessionPhase.listening,
          status: 'Listening… speak naturally',
          subtitle: 'Pause when finished — Shifa answers, then listens again.',
          onStop: () => stopped++,
          stopLabel: 'Stop',
          stopSemanticsLabel: 'Stop listening',
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Listening… speak naturally'), findsOneWidget);
    expect(
      find.text('Pause when finished — Shifa answers, then listens again.'),
      findsOneWidget,
    );
    expect(find.byKey(ShifaAiOrb.logoKey), findsOneWidget);
    expect(find.byIcon(Icons.mic), findsOneWidget);
    expect(find.byIcon(Icons.graphic_eq), findsNothing);

    await tester.tap(find.byKey(ShifaVoiceOrbPanel.stopKey));
    await tester.pump();
    expect(stopped, 1);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('speaking panel is distinct and has no stop button', (
    tester,
  ) async {
    await tester.pumpWidget(
      _app(
        const ShifaVoiceOrbPanel(
          phase: VoiceSessionPhase.speaking,
          status: 'Shifa is speaking…',
          subtitle: 'Tap the microphone to interrupt',
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Shifa is speaking…'), findsOneWidget);
    expect(find.byIcon(Icons.graphic_eq), findsOneWidget);
    expect(find.byKey(ShifaVoiceOrbPanel.stopKey), findsNothing);
    expect(find.byKey(ShifaAiOrb.logoKey), findsOneWidget);

    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('thinking panel uses a progress indicator', (tester) async {
    await tester.pumpWidget(
      _app(
        const ShifaVoiceOrbPanel(
          phase: VoiceSessionPhase.thinking,
          status: 'Reviewing…',
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Reviewing…'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsOneWidget);
    expect(find.byIcon(Icons.mic), findsNothing);

    await tester.pumpWidget(const SizedBox.shrink());
  });
}

Widget _app(Widget child) {
  return MaterialApp(
    builder: (context, page) {
      return MediaQuery(
        data: MediaQuery.of(context).copyWith(disableAnimations: true),
        child: page ?? const SizedBox.shrink(),
      );
    },
    home: Scaffold(body: child),
  );
}
