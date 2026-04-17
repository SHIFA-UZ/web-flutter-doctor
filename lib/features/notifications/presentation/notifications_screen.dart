import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/services/push_notification_service.dart';
import 'package:shifa_doc_app_v1/features/notifications/domain/notification_model.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notification_ui_helpers.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';

/// Notifications screen for the doctor app.
/// Product-grade UI: grouped by date, semantic colors, cards, actions, filters.
class NotificationsScreen extends ConsumerStatefulWidget {
  const NotificationsScreen({super.key});

  @override
  ConsumerState<NotificationsScreen> createState() => _NotificationsScreenState();
}

class _NotificationsScreenState extends ConsumerState<NotificationsScreen> {
  NotificationFilter _filter = NotificationFilter.all;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final notificationsAsync = ref.watch(doctorNotificationsProvider);
    final unreadCountAsync = ref.watch(doctorNotificationsUnreadCountProvider);
    final controller = ref.read(doctorNotificationsControllerProvider);
    final unreadCount = unreadCountAsync.valueOrNull ?? 0;

    return Scaffold(
      body: SafeArea(
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            // Header: "Notifications (12)" and [Mark all read] [Settings]
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    unreadCount > 0
                        ? '${l10n.notifications} ($unreadCount)'
                        : l10n.notifications,
                    style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                  ),
                  Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      TextButton.icon(
                        onPressed: () => controller.markAllAsRead(),
                        icon: const Icon(Icons.done_all, size: 18),
                        label: Text(l10n.markAllAsRead, style: const TextStyle(fontSize: 13)),
                      ),
                      IconButton(
                        onPressed: () => ref.read(shellProvider.notifier).setTab(6),
                        icon: const Icon(Icons.settings_outlined, size: 22),
                        tooltip: l10n.notificationSettings,
                      ),
                    ],
                  ),
                ],
              ),
            ),
            // Filters: All | Appointments | Tasks | Messages
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              child: Row(
                children: [
                  _FilterChip(
                    label: l10n.notificationFilterAll,
                    selected: _filter == NotificationFilter.all,
                    onTap: () => setState(() => _filter = NotificationFilter.all),
                  ),
                  const SizedBox(width: 8),
                  _FilterChip(
                    label: l10n.notificationFilterAppointments,
                    selected: _filter == NotificationFilter.appointments,
                    onTap: () => setState(() => _filter = NotificationFilter.appointments),
                  ),
                  const SizedBox(width: 8),
                  _FilterChip(
                    label: l10n.notificationFilterTasks,
                    selected: _filter == NotificationFilter.tasks,
                    onTap: () => setState(() => _filter = NotificationFilter.tasks),
                  ),
                  const SizedBox(width: 8),
                  _FilterChip(
                    label: l10n.notificationFilterMessages,
                    selected: _filter == NotificationFilter.messages,
                    onTap: () => setState(() => _filter = NotificationFilter.messages),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),
            Expanded(
              child: notificationsAsync.when(
                data: (items) {
                  final filtered = items
                      .where((n) => notificationMatchesFilter(n.type, _filter))
                      .toList();
                  if (filtered.isEmpty) {
                    return _EmptyState(
                      hasFilter: _filter != NotificationFilter.all,
                      l10n: l10n,
                    );
                  }
                  final grouped = _groupByDate(filtered, l10n);
                  return ListView.builder(
                    padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
                    itemCount: grouped.length,
                    itemBuilder: (context, index) {
                      final section = grouped[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 24),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Padding(
                              padding: const EdgeInsets.only(left: 4, bottom: 12),
                              child: Text(
                                section.label,
                                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                      color: Colors.grey.shade600,
                                      fontWeight: FontWeight.w600,
                                    ),
                              ),
                            ),
                            ...section.items.map((n) => Padding(
                                  padding: const EdgeInsets.only(bottom: 12),
                                  child: _NotificationCard(
                                    notification: n,
                                    controller: controller,
                                    l10n: l10n,
                                    onTap: () {
                                      controller.markAsRead(n.id);
                                      _deliverPayload(n);
                                    },
                                    onOpenCalendar: () {
                                      ref.read(shellProvider.notifier).setTab(2);
                                    },
                                  ),
                                )),
                          ],
                        ),
                      );
                    },
                  );
                },
                loading: () => const Center(child: CircularProgressIndicator()),
                error: (err, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline, size: 32, color: Colors.redAccent),
                        const SizedBox(height: 12),
                        Text(
                          l10n.error,
                          style: Theme.of(context).textTheme.bodyMedium,
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 8),
                        Text(
                          err.toString(),
                          style: Theme.of(context)
                              .textTheme
                              .bodySmall
                              ?.copyWith(color: Colors.grey.shade600),
                          textAlign: TextAlign.center,
                        ),
                        const SizedBox(height: 16),
                        ShifaSecondaryButton(
                          label: l10n.retry,
                          onPressed: () => controller.refresh(),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  void _deliverPayload(DoctorNotificationModel n) {
    final payload = <String, dynamic>{
      'notificationId': n.id,
      'type': n.type,
      if (n.appointmentId != null) 'appointmentId': n.appointmentId,
      if (n.patientId != null) 'patientId': n.patientId,
      if (n.documentId != null) 'documentId': n.documentId,
      if (n.documentTitle != null) 'documentTitle': n.documentTitle,
      if (n.documentAccessRequestId != null) 'documentAccessRequestId': n.documentAccessRequestId,
      if (n.taskId != null) 'taskId': n.taskId,
    };
    PushNotificationService().deliverPayloadFromApp(payload);
  }

  List<({String label, List<DoctorNotificationModel> items})> _groupByDate(
      List<DoctorNotificationModel> items, AppLocalizations l10n) {
    final map = <String, List<DoctorNotificationModel>>{};
    for (final n in items) {
      final label = dateSectionLabel(n.createdAt, l10n);
      map.putIfAbsent(label, () => []).add(n);
    }
    final order = <String>[l10n.today, l10n.notificationYesterday];
    final rest = map.keys.where((k) => !order.contains(k)).toList()..sort();
    final orderedLabels = [...order.where((l) => map.containsKey(l)), ...rest];
    return orderedLabels
        .map((label) => (label: label, items: map[label]!))
        .toList();
  }
}

class _FilterChip extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _FilterChip({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected
          ? Theme.of(context).colorScheme.primaryContainer
          : Colors.grey.shade100,
      borderRadius: BorderRadius.circular(20),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(20),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
          child: Text(
            label,
            style: TextStyle(
              fontSize: 13,
              fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
              color: selected
                  ? Theme.of(context).colorScheme.onPrimaryContainer
                  : Colors.grey.shade700,
            ),
          ),
        ),
      ),
    );
  }
}

class _NotificationCard extends StatelessWidget {
  final DoctorNotificationModel notification;
  final DoctorNotificationsController controller;
  final AppLocalizations l10n;
  final VoidCallback onTap;
  final VoidCallback? onOpenCalendar;

  const _NotificationCard({
    required this.notification,
    required this.controller,
    required this.l10n,
    required this.onTap,
    this.onOpenCalendar,
  });

  @override
  Widget build(BuildContext context) {
    final isUnread = !notification.isRead;
    final style = styleForNotificationType(notification.type);
    final titleLabel = humanLabelForNotificationType(notification.type, l10n);
    final timeStr = formatNotificationTime(notification.createdAt, l10n);

    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(8),
        child: Container(
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: isUnread
                ? style.color.withOpacity(0.06)
                : Theme.of(context).cardColor,
            borderRadius: BorderRadius.circular(8),
            border: Border.all(
              color: Colors.grey.shade200,
              width: 1,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.04),
                blurRadius: 8,
                offset: const Offset(0, 2),
              ),
            ],
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  if (isUnread)
                    Padding(
                      padding: const EdgeInsets.only(right: 10, top: 6),
                      child: Container(
                        width: 8,
                        height: 8,
                        decoration: BoxDecoration(
                          color: const Color(0xFF1976D2),
                          shape: BoxShape.circle,
                        ),
                      ),
                    ),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          children: [
                            Icon(
                              style.icon,
                              size: 20,
                              color: style.color,
                            ),
                            const SizedBox(width: 8),
                            Expanded(
                              child: Text(
                                titleLabel,
                                style: Theme.of(context).textTheme.titleSmall?.copyWith(
                                      fontWeight: FontWeight.w600,
                                      color: style.color,
                                    ),
                              ),
                            ),
                            Text(
                              timeStr,
                              style: Theme.of(context).textTheme.bodySmall?.copyWith(
                                    color: Colors.grey.shade600,
                                  ),
                            ),
                          ],
                        ),
                        const SizedBox(height: 6),
                        Text(
                          localizedNotificationMessage(notification, l10n),
                          style: Theme.of(context).textTheme.bodyMedium,
                          maxLines: 3,
                          overflow: TextOverflow.ellipsis,
                        ),
                        _buildActions(context),
                      ],
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildActions(BuildContext context) {
    final hasAppointment = notification.appointmentId != null;
    final isCancelled = notification.type.contains('CANCELLED');
    final isTaskCompleted = notification.type == 'TASK_COMPLETED';

    if (!hasAppointment && !isTaskCompleted) return const SizedBox.shrink();

    final buttons = <Widget>[];
    if (isTaskCompleted && notification.taskId != null) {
      buttons.add(
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: ShifaActionButton(
            label: l10n.notificationViewResult,
            icon: Icons.visibility_outlined,
            actionStyle: ActionButtonStyle.secondary,
            onPressed: () {
              controller.markAsRead(notification.id);
              _deliverPayload();
            },
          ),
        ),
      );
    }
    if (hasAppointment && !isCancelled) {
      buttons.add(
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: Row(
            children: [
              ShifaActionButton(
                label: l10n.notificationViewAppointment,
                icon: Icons.event_outlined,
                actionStyle: ActionButtonStyle.secondary,
                onPressed: () {
                  controller.markAsRead(notification.id);
                  _deliverPayload();
                },
              ),
              const SizedBox(width: 8),
              ShifaActionButton(
                label: l10n.notificationOpenCalendar,
                icon: Icons.calendar_month_outlined,
                actionStyle: ActionButtonStyle.secondary,
                onPressed: () {
                  controller.markAsRead(notification.id);
                  onOpenCalendar?.call();
                },
              ),
            ],
          ),
        ),
      );
    }
    if (hasAppointment && isCancelled) {
      buttons.add(
        Padding(
          padding: const EdgeInsets.only(top: 12),
          child: ShifaActionButton(
            label: l10n.notificationReschedule,
            icon: Icons.schedule_outlined,
            actionStyle: ActionButtonStyle.secondary,
            onPressed: () {
              controller.markAsRead(notification.id);
              _deliverPayload();
            },
          ),
        ),
      );
    }

    if (buttons.isEmpty) return const SizedBox.shrink();
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: buttons,
    );
  }

  void _deliverPayload() {
    final n = notification;
    final payload = <String, dynamic>{
      'notificationId': n.id,
      'type': n.type,
      if (n.appointmentId != null) 'appointmentId': n.appointmentId,
      if (n.patientId != null) 'patientId': n.patientId,
      if (n.documentId != null) 'documentId': n.documentId,
      if (n.documentTitle != null) 'documentTitle': n.documentTitle,
      if (n.documentAccessRequestId != null) 'documentAccessRequestId': n.documentAccessRequestId,
      if (n.taskId != null) 'taskId': n.taskId,
    };
    PushNotificationService().deliverPayloadFromApp(payload);
  }
}

class _EmptyState extends StatelessWidget {
  final bool hasFilter;
  final AppLocalizations l10n;

  const _EmptyState({this.hasFilter = false, required this.l10n});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(32),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(
              Icons.notifications_none_rounded,
              size: 80,
              color: Colors.grey.shade400,
            ),
            const SizedBox(height: 24),
            Text(
              hasFilter ? l10n.notificationEmptyFilter : l10n.noNotifications,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              hasFilter
                  ? l10n.notificationEmptyFilterHint
                  : l10n.notificationEmptyBody,
              style: Theme.of(context)
                  .textTheme
                  .bodyMedium
                  ?.copyWith(color: Colors.grey.shade600),
              textAlign: TextAlign.center,
            ),
          ],
        ),
      ),
    );
  }
}
