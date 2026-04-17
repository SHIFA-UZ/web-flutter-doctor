import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/notifications/domain/notification_model.dart';

/// Fetch notifications for the current doctor (GET /api/notifications).
Future<List<DoctorNotificationModel>> fetchDoctorNotificationsWithClient({
  required ApiClient client,
}) async {
  debugPrint('═══ NOTIFICATION SOURCE: REST API ═══');
  debugPrint('Fetching notifications via: GET /api/notifications');
  debugPrint('Method: Backend REST API polling (every 20 seconds)');
  debugPrint('NOT using Firebase FCM for this fetch');

  final res = await client.get('/api/notifications');
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final data = jsonDecode(res.body) as List;
    final notifications = data
        .map((e) =>
            DoctorNotificationModel.fromJson(e as Map<String, dynamic>))
        .toList();

    debugPrint('✓ Fetched ${notifications.length} notifications from REST API');
    for (var notif in notifications.take(3)) {
      debugPrint('  • ID:${notif.id} Type:${notif.type} Read:${notif.isRead}');
    }

    return notifications;
  }
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to fetch notifications: ${res.statusCode} ${res.body}');
}

/// Unread count (GET /api/notifications/unread/count).
Future<int> fetchDoctorNotificationsUnreadCountWithClient({
  required ApiClient client,
}) async {
  final res = await client.get('/api/notifications/unread/count');
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final data = jsonDecode(res.body) as Map<String, dynamic>;
    return (data['count'] as num?)?.toInt() ?? 0;
  }
  return 0;
}

/// Mark notification as read (PUT /api/notifications/{id}/read).
Future<void> markDoctorNotificationAsReadWithClient({
  required ApiClient client,
  required int notificationId,
}) async {
  final res = await client.put(
    '/api/notifications/$notificationId/read',
    <String, dynamic>{},
  );
  if (res.statusCode >= 200 && res.statusCode < 300) return;
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to mark as read: ${res.statusCode} ${res.body}');
}

/// Mark all notifications as read (PUT /api/notifications/read-all).
Future<void> markAllDoctorNotificationsAsReadWithClient({
  required ApiClient client,
}) async {
  final res = await client.put(
    '/api/notifications/read-all',
    <String, dynamic>{},
  );
  if (res.statusCode >= 200 && res.statusCode < 300) return;
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to mark all as read: ${res.statusCode} ${res.body}');
}

/// Approve document access request (POST /api/document-access-requests/{id}/approve).
Future<void> approveDocumentAccessRequestWithClient({
  required ApiClient client,
  required int requestId,
}) async {
  final res = await client.post(
    '/api/document-access-requests/$requestId/approve',
    <String, dynamic>{},
  );
  if (res.statusCode >= 200 && res.statusCode < 300) return;
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to approve: ${res.statusCode} ${res.body}');
}

/// Reject document access request (POST /api/document-access-requests/{id}/reject).
Future<void> rejectDocumentAccessRequestWithClient({
  required ApiClient client,
  required int requestId,
}) async {
  final res = await client.post(
    '/api/document-access-requests/$requestId/reject',
    <String, dynamic>{},
  );
  if (res.statusCode >= 200 && res.statusCode < 300) return;
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to reject: ${res.statusCode} ${res.body}');
}
