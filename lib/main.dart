/* import 'package:flutter/material.dart';
import 'app/app.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const ShifaDoctorApp());
} */

// lib/main.dart
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/date_symbol_data_local.dart';
import 'package:timezone/data/latest.dart' as tz;
import 'app/app.dart';
import 'core/services/push_notification_service.dart';
import 'firebase_options.dart';

/// Background message handler (must be top-level function).
@pragma('vm:entry-point')
Future<void> firebaseMessagingBackgroundHandler(RemoteMessage message) async {
  try {
    await Firebase.initializeApp(options: DefaultFirebaseOptions.currentPlatform);
  } catch (_) {
    // ignore if Firebase is not configured
  }
  if (kDebugMode) {
    debugPrint('Doctor background FCM message: ${message.messageId}');
  }
}

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  tz.initializeTimeZones();
  // Initialize date formatting for calendar day/month names (en, uz, ru)
  await Future.wait([
    initializeDateFormatting('en'),
    initializeDateFormatting('uz'),
    initializeDateFormatting('ru'),
  ]);
  try {
    await Firebase.initializeApp(
      options: DefaultFirebaseOptions.currentPlatform,
    );
    FirebaseMessaging.onBackgroundMessage(firebaseMessagingBackgroundHandler);
    final pushService = PushNotificationService();
    await pushService.initialize();
  } on UnsupportedError {
    // Firebase not configured (run "flutterfire configure") - phone OTP login and push will be unavailable
  } catch (e, st) {
    if (kDebugMode) {
      debugPrint('Firebase/FCM init skipped for doctor app: $e');
      debugPrint(st.toString());
    }
  }
  runApp(
    const ProviderScope(
      child: ShifaDoctorApp(),
    ),
  );
}
