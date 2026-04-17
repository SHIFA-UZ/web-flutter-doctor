import 'dart:convert';
import 'dart:html' as html;
import 'dart:js_util' as js_util;

import 'package:firebase_messaging/firebase_messaging.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter_local_notifications/flutter_local_notifications.dart';

/// Centralized notification service (FCM + local) for the doctor app.
/// Mirrors the patient app implementation so payloads and behavior stay consistent.
///
/// Required payload from backend (standardized):
///   - [type] (required): e.g. DOCUMENT_ACCESS_REQUEST, TASK_COMPLETED
///   - [id]/[notificationId] (required): id to mark as read (PUT /api/notifications/:id/read)
///   - [appointmentId]/[taskId]/[documentAccessRequestId] (optional): navigation hints
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

  /// IDs we've already marked read or are about to handle – avoid duplicate popups in foreground.
  final Set<String> _readNotificationIds = {};

  /// Web only: payload by notification tag so we can navigate when user clicks the browser notification.
  final Map<String, Map<String, dynamic>> _webNotificationPayloads = {};

  /// Initialize: wire all three FCM entry points. Foreground shows local; background and
  /// terminated both use [handleNotificationNavigation].
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

    // 1. Foreground: show local notification only if not already in read cache
    FirebaseMessaging.onMessage.listen(_handleForegroundMessage);

    // 2. Background: user tapped notification → single handler
    FirebaseMessaging.onMessageOpenedApp.listen(handleNotificationNavigation);

    // 3. Terminated: store; delivered when callback is set via processPendingInitialMessage()
    final initialMessage = await _firebaseMessaging.getInitialMessage();
    if (initialMessage != null && initialMessage.data.isNotEmpty) {
      _pendingInitialMessage = initialMessage;
    }
  }

  /// Single entry for navigation flow (FCM background + terminated). App layer must
  /// mark notification as read first (in callback), then navigate by type.
  void handleNotificationNavigation(RemoteMessage message) {
    debugPrint('═══ NOTIFICATION TAPPED ═══');
    debugPrint('Source: Firebase FCM (user tapped notification)');
    debugPrint('State: Background or Terminated');
    debugPrint('Data payload: ${message.data}');

    if (message.data.isEmpty) {
      debugPrint('⊘ No data payload, cannot navigate');
      return;
    }

    _deliverPayload(Map<String, dynamic>.from(message.data));
  }

  /// Same as [handleNotificationNavigation] but from a data map (e.g. local notification tap).
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

  /// Call after setting the tap callback (e.g. when shell is ready). Delivers
  /// any notification that opened the app from terminated state or local tap.
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
  }

  /// Foreground: show local notification only if not already in read cache (no duplicate popup).
  Future<void> _handleForegroundMessage(RemoteMessage message) async {
    debugPrint('═══ NOTIFICATION SOURCE: FIREBASE FCM ═══');
    debugPrint('Received FCM push notification (FOREGROUND)');
    debugPrint('Title: ${message.notification?.title}');
    debugPrint('Body: ${message.notification?.body}');
    debugPrint('Data payload: ${message.data}');

    final data = Map<String, dynamic>.from(message.data);
    final notificationId = _notificationIdFromPayload(data);

    debugPrint('Notification ID: $notificationId');

    if (notificationId != null && _readNotificationIds.contains(notificationId)) {
      debugPrint('⊘ Notification already read, skipping local notification');
      return;
    }

    debugPrint('✓ Showing local notification to user');
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
    // On web, use the browser Notification API so the doctor sees a real
    // browser/system notification (like Telegram Web), not just in-app UI.
    if (kIsWeb) {
      try {
        if (html.Notification.supported) {
          if (html.Notification.permission != 'granted') {
            final perm = await html.Notification.requestPermission();
            if (perm != 'granted') {
              if (kDebugMode) {
                debugPrint('Web notification permission not granted: $perm');
              }
              return;
            }
          }
          final nid = data != null ? _notificationIdFromPayload(data) : null;
          final tag = 'shifa_${nid ?? DateTime.now().millisecondsSinceEpoch}';
          if (data != null && data.isNotEmpty) {
            _webNotificationPayloads[tag] = Map<String, dynamic>.from(data);
          }
          final notification = html.Notification(
            title,
            body: body,
            icon: '/icons/Icon-192.png',
            tag: tag,
          );
          notification.onClick.listen((_) {
            try {
              js_util.callMethod(html.window, 'focus', []);
            } catch (_) {}
            final payload = _webNotificationPayloads.remove(tag);
            if (payload != null) {
              _deliverPayload(payload);
            }
          });
        } else {
          if (kDebugMode) {
            debugPrint('Browser Notification API not supported');
          }
        }
      } catch (e) {
        if (kDebugMode) {
          debugPrint('Error showing web notification: $e');
        }
      }
      return;
    }

    const androidDetails = AndroidNotificationDetails(
      'shifa_doctor_channel',
      'Shifa Doctor Notifications',
      channelDescription: 'Notifications for documents, tasks, and appointments',
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
        if (nid is int) {
          id = nid;
        } else {
          id = int.tryParse(nid.toString()) ?? 0;
        }
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

  /// Call from the app (e.g. notification list card tap) to run the same
  /// navigation as FCM/notification tap. Delivers payload to the registered
  /// tap callback so user is taken to calendar/document/patient as appropriate.
  void deliverPayloadFromApp(Map<String, dynamic> data) {
    _deliverPayload(data);
  }

  void setOnFcmTokenReady(void Function(String token) callback) {
    _onFcmTokenReady = callback;
    if (_fcmToken != null) callback(_fcmToken!);
  }

  String? getFcmToken() => _fcmToken;
}

