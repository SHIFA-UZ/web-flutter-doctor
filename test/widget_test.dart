// Basic Flutter widget test for Shifa Doctor App

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

import 'package:shifa_doc_app_v1/app/app.dart';

void main() {
  testWidgets('App launches smoke test', (WidgetTester tester) async {
    // Build our app and trigger a frame.
    await tester.pumpWidget(const ShifaDoctorApp());

    // Verify that the app renders without errors
    expect(find.byType(MaterialApp), findsOneWidget);
  });
}
