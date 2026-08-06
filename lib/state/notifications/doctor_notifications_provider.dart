import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/features/notifications/domain/notification_model.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notification_actions.dart';

/// Intentionally no REST polling — FCM + home tab focus / pull-to-refresh
/// keep feeds current without continuous background timers.
final notificationAutoRefreshProvider = Provider.autoDispose<void>((ref) {});


final doctorNotificationsProvider =
    FutureProvider.autoDispose<List<DoctorNotificationModel>>((ref) async {
  if (ref.read(authTokenProvider) == null || ref.read(authTokenProvider)!.isEmpty) {
    throw StateError('Not authenticated');
  }
  final client = ref.watch(apiClientProvider);
  return fetchDoctorNotificationsWithClient(client: client);
});

final doctorNotificationsUnreadCountProvider =
    FutureProvider.autoDispose<int>((ref) async {
  if (ref.read(authTokenProvider) == null || ref.read(authTokenProvider)!.isEmpty) {
    return 0;
  }
  final client = ref.watch(apiClientProvider);
  return fetchDoctorNotificationsUnreadCountWithClient(client: client);
});

final doctorNotificationsControllerProvider =
    Provider<DoctorNotificationsController>((ref) {
  return DoctorNotificationsController(ref);
});

class DoctorNotificationsController {
  final Ref ref;

  DoctorNotificationsController(this.ref);

  Future<void> refresh() async {
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
  }

  Future<void> markAsRead(int notificationId) async {
    final client = ref.read(apiClientProvider);
    await markDoctorNotificationAsReadWithClient(
      client: client,
      notificationId: notificationId,
    );
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
  }

  Future<void> markAllAsRead() async {
    final client = ref.read(apiClientProvider);
    await markAllDoctorNotificationsAsReadWithClient(client: client);
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
  }

  Future<void> approveDocumentAccessRequest(int requestId, int notificationId) async {
    final client = ref.read(apiClientProvider);
    await approveDocumentAccessRequestWithClient(client: client, requestId: requestId);
    await markAsRead(notificationId);
  }

  Future<void> rejectDocumentAccessRequest(int requestId, int notificationId) async {
    final client = ref.read(apiClientProvider);
    await rejectDocumentAccessRequestWithClient(client: client, requestId: requestId);
    await markAsRead(notificationId);
  }
}
