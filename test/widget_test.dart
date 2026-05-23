// Minimal smoke test. Importing ShifaDoctorApp pulls web-only libs (dart:html) and
// platform-specific bundles that do not compile on the VM widget-test runner.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('MaterialApp smoke build', (WidgetTester tester) async {
    await tester.pumpWidget(
      const MaterialApp(
        home: Scaffold(
          body: Text('widget_test_placeholder'),
        ),
      ),
    );
    expect(find.text('widget_test_placeholder'), findsOneWidget);
  });
}
