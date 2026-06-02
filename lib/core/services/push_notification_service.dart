import 'dart:convert';

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

import 'push_notification_web_stub.dart'
    if (dart.library.html) 'push_notification_web.dart' as web_push;

/// Centralized notification service (FCM + local) for the doctor app.
class PushNotificationService {
  static final PushNotificationService _instance = PushNotificationService._internal();
  factory PushNotificationService() => _instance;
  PushNotificationService._internal();

  final FirebaseMessaging _firebaseMessaging = FirebaseMessaging.instance;
  final FlutterLocalNotificationsPlugin _localNotifications = FlutterLocalNotificationsPlugin();

  String? _fcmToken;
  void Function(Map<String, dynamic>)? _onNotificationTap;
  void Function(String token)? _onFcmTokenReady;
  RemoteMessage? _pendingInitialMessage;
  Map<String, dynamic>? _pendingLocalNotificationTap;

  final Set<String> _readNotificationIds = {};
  final Map<String, Map<String, dynamic>> _webNotificationPayloads = {};

  Future<void> initialize() async {
    final settings = await _firebaseMessaging.requestPermission(
      alert: true,
      badge: true,
      sound: true,
      provisional: false,
    );

    if (settings.authorizationStatus != AuthorizationStatus.authorized) return;

    await _initializeLocalNotifications();

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

    await _showLocalNotification(
      title: message.notification?.title ?? 'New Notification',
      body: message.notification?.body ?? '',
      data: data,
    );
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

  void deliverPayloadFromApp(Map<String, dynamic> data) {
    _deliverPayload(data);
  }

  void setOnFcmTokenReady(void Function(String token) callback) {
    _onFcmTokenReady = callback;
    if (_fcmToken != null) callback(_fcmToken!);
  }

  String? getFcmToken() => _fcmToken;
}
