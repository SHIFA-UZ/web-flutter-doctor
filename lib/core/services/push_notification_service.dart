import 'dart:convert';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'push_notification_web_stub.dart'
    if (dart.library.html) 'push_notification_web.dart' as web_push;
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/localization/localization_asset_loader.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notification_ui_helpers.dart';

/// Centralized notification service (FCM + local) for the doctor app.
class PushNotificationService {
  static final PushNotificationService _instance = PushNotificationService._internal();
  factory PushNotificationService() => _instance;
  PushNotificationService._internal();

  final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications = FlutterLocalNotificationsPlugin();

  String? _fcmToken;
  void Function(Map<String, dynamic>)? _onNotificationTap;
  void Function(Map<String, dynamic>)? _onForegroundDataRefresh;
  void Function(String token)? _onFcmTokenReady;
  RemoteMessage? _pendingInitialMessage;
  Map<String, dynamic>? _pendingLocalNotificationTap;

  final Set<String> _readNotificationIds = {};
  final Map<String, Map<String, dynamic>> _webNotificationPayloads = {};
  AppLocalizations? _cachedL10n;

  Future<void> initialize() async {
    await _warmLocalizationCache();
    await _initializeLocalNotifications();

    if (!kIsWeb) {
      await _localNotifications
          .resolvePlatformSpecificImplementation<
              AndroidFlutterLocalNotificationsPlugin>()
          ?.requestNotificationsPermission();
    }

    final settings = await _firebaseMessaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
    );

    if (kDebugMode) {
      debugPrint(
        'Doctor notification permission: ${settings.authorizationStatus}',
      );
    }

    // Still register the token when the user has not granted alerts yet, so
    // later permission grants and data-only messages can be delivered.
    _fcmToken = await _firebaseMessaging.getToken();
    if (kDebugMode) debugPrint('Doctor FCM Token: $_fcmToken');
    if (_fcmToken != null) _onFcmTokenReady?.call(_fcmToken!);
    _firebaseMessaging.onTokenRefresh.listen((token) {
      _fcmToken = token;
      if (kDebugMode) debugPrint('Doctor FCM Token refreshed');
      _onFcmTokenReady?.call(token);
    });

    FirebaseMessaging.onMessage.listen(_handleForegroundMessage);
    FirebaseMessaging.onMessageOpenedApp.listen(handleNotificationNavigation);

    final initialMessage = await _firebaseMessaging.getInitialMessage();
    if (initialMessage != null && initialMessage.data.isNotEmpty) {
      _pendingInitialMessage = initialMessage;
    }
  }

  void handleNotificationNavigation(RemoteMessage message) {
    if (message.data.isEmpty) return;
    _deliverPayload(Map<String, dynamic>.from(message.data));
  }

  void handleNotificationNavigationFromData(Map<String, dynamic> data) {
    if (data.isEmpty) return;
    _deliverPayload(data);
  }

  void _deliverPayload(Map<String, dynamic> data) {
    final notificationId = _notificationIdFromPayload(data);
    if (notificationId != null && notificationId.isNotEmpty) {
      _readNotificationIds.add(notificationId);
    }
    if (_onNotificationTap != null) {
      _onNotificationTap!(data);
    } else {
      _pendingLocalNotificationTap = data;
    }
  }

  static String? _notificationIdFromPayload(Map<String, dynamic> data) {
    final v = data['notificationId'] ?? data['id'];
    if (v == null) return null;
    return v.toString();
  }

  void processPendingInitialMessage() {
    if (_onNotificationTap == null) return;
    final fcmPending = _pendingInitialMessage;
    if (fcmPending != null && fcmPending.data.isNotEmpty) {
      _pendingInitialMessage = null;
      handleNotificationNavigation(fcmPending);
      return;
    }
    final localPending = _pendingLocalNotificationTap;
    if (localPending != null && localPending.isNotEmpty) {
      _pendingLocalNotificationTap = null;
      handleNotificationNavigationFromData(localPending);
    }
  }

  Future<void> _initializeLocalNotifications() async {
    const androidSettings = AndroidInitializationSettings('@mipmap/ic_launcher');
    const iosSettings = DarwinInitializationSettings(
      requestAlertPermission: true,
      requestBadgePermission: true,
      requestSoundPermission: true,
    );
    const initSettings = InitializationSettings(android: androidSettings, iOS: iosSettings);

    await _localNotifications.initialize(
      initSettings,
      onDidReceiveNotificationResponse: (details) {
        if (details.payload == null || details.payload!.isEmpty) return;
        try {
          final data =
              Map<String, dynamic>.from(jsonDecode(details.payload!) as Map<String, dynamic>);
          handleNotificationNavigationFromData(data);
        } catch (e) {
          if (kDebugMode) debugPrint('Doctor push: error parsing notification payload: $e');
        }
      },
    );

    if (!kIsWeb) {
      const channel = AndroidNotificationChannel(
        'shifa_doctor_channel',
        'Shifa Doctor Notifications',
        description: 'Notifications for documents, tasks, chat, and appointments',
        importance: Importance.high,
      );
      await _localNotifications
          .resolvePlatformSpecificImplementation<AndroidFlutterLocalNotificationsPlugin>()
          ?.createNotificationChannel(channel);
    }
  }

  Future<void> _handleForegroundMessage(RemoteMessage message) async {
    final data = Map<String, dynamic>.from(message.data);
    final notificationId = _notificationIdFromPayload(data);

    if (notificationId != null && _readNotificationIds.contains(notificationId)) {
      return;
    }

    _onForegroundDataRefresh?.call(data);

    final l10n = await _localizations();
    final localized = localizedDoctorPushText(
      data: data,
      l10n: l10n,
      fallbackTitle: message.notification?.title,
      fallbackBody: message.notification?.body,
    );
    await _showLocalNotification(
      title: localized.title,
      body: localized.body,
      data: data,
    );
  }

  Future<void> _warmLocalizationCache() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final tag = prefs.getString('doctor_language') ?? 'en';
      final locale = localeFromPersistenceTag(tag);
      await LocalizationAssetLoader.load(locale.languageCode);
      _cachedL10n = AppLocalizations(locale);
    } catch (_) {
      _cachedL10n = AppLocalizations(const Locale('en'));
    }
  }

  Future<AppLocalizations> _localizations() async {
    if (_cachedL10n != null) return _cachedL10n!;
    await _warmLocalizationCache();
    return _cachedL10n ?? AppLocalizations(const Locale('en'));
  }

  /// Reload tray strings after the doctor changes app language.
  Future<void> refreshLocalizationCache() async {
    _cachedL10n = null;
    await _warmLocalizationCache();
  }

  Future<void> _showLocalNotification({
    required String title,
    required String body,
    Map<String, dynamic>? data,
  }) async {
    if (kIsWeb) {
      final nid = data != null ? _notificationIdFromPayload(data) : null;
      final tag = 'shifa_${nid ?? DateTime.now().millisecondsSinceEpoch}';
      if (data != null && data.isNotEmpty) {
        _webNotificationPayloads[tag] = Map<String, dynamic>.from(data);
      }
      web_push.showWebBrowserNotification(
        title: title,
        body: body,
        tag: tag,
        onTap: () {
          final payload = _webNotificationPayloads.remove(tag);
          if (payload != null) _deliverPayload(payload);
        },
      );
      return;
    }

    const androidDetails = AndroidNotificationDetails(
      'shifa_doctor_channel',
      'Shifa Doctor Notifications',
      channelDescription: 'Notifications for documents, tasks, chat, and appointments',
      importance: Importance.high,
      priority: Priority.high,
      showWhen: true,
    );
    const iosDetails = DarwinNotificationDetails(
      presentAlert: true,
      presentBadge: true,
      presentSound: true,
    );
    const notificationDetails = NotificationDetails(android: androidDetails, iOS: iosDetails);

    final payload = (data != null && data.isNotEmpty) ? jsonEncode(data) : null;
    var id = 0;
    if (data != null) {
      final nid = data['notificationId'] ?? data['id'];
      if (nid != null) {
        id = nid is int ? nid : (int.tryParse(nid.toString()) ?? 0);
      }
    }
    if (id == 0) id = DateTime.now().millisecondsSinceEpoch.remainder(100000);
    await _localNotifications.show(
      id.abs().clamp(1, 0x7FFFFFFF),
      title,
      body,
      notificationDetails,
      payload: payload,
    );
  }

  void setOnNotificationTap(void Function(Map<String, dynamic>) callback) {
    _onNotificationTap = callback;
  }

  void setOnForegroundDataRefresh(void Function(Map<String, dynamic>) callback) {
    _onForegroundDataRefresh = callback;
  }

  void deliverPayloadFromApp(Map<String, dynamic> data) {
    _deliverPayload(data);
  }

  void setOnFcmTokenReady(void Function(String token) callback) {
    _onFcmTokenReady = callback;
    if (_fcmToken != null) callback(_fcmToken!);
  }

  String? getFcmToken() => _fcmToken;
}
