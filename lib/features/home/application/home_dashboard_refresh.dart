import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';

/// Minimum gap between automatic (tab-focus) home feed refreshes.
/// Prevents CPU/network spikes from rapid Home tab switching.
const Duration _homeAutoRefreshCooldown = Duration(seconds: 60);

DateTime? _lastAutoRefreshAt;
bool _refreshInFlight = false;

/// Light refresh for returning to the Home tab: notifications + tasks only.
/// Skipped if a refresh is already running or the cooldown has not elapsed.
Future<void> refreshHomeDashboardOnTabFocus(dynamic ref) async {
  final now = DateTime.now();
  if (_refreshInFlight) return;
  if (_lastAutoRefreshAt != null &&
      now.difference(_lastAutoRefreshAt!) < _homeAutoRefreshCooldown) {
    return;
  }
  _refreshInFlight = true;
  _lastAutoRefreshAt = now;
  try {
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
    try {
      await ref.read(tasksProvider.notifier).loadTasks();
    } catch (_) {}
  } finally {
    _refreshInFlight = false;
  }
}

/// Full refresh for explicit pull-to-refresh (user-initiated).
Future<void> refreshHomeDashboard(dynamic ref) async {
  if (_refreshInFlight) return;
  _refreshInFlight = true;
  _lastAutoRefreshAt = DateTime.now();
  try {
    ref.invalidate(doctorNotificationsProvider);
    ref.invalidate(doctorNotificationsUnreadCountProvider);
    ref.invalidate(doctorAnalyticsOverviewProvider);
    try {
      await ref.read(tasksProvider.notifier).loadTasks();
    } catch (_) {}
    await invalidateAppointmentRelatedProviders(ref);
  } finally {
    _refreshInFlight = false;
  }
}
