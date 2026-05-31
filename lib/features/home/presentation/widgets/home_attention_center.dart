import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/notifications/domain/notification_model.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notification_ui_helpers.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';

class HomeAttentionCenter extends ConsumerStatefulWidget {
  const HomeAttentionCenter({super.key});

  @override
  ConsumerState<HomeAttentionCenter> createState() =>
      _HomeAttentionCenterState();
}

class _HomeAttentionCenterState extends ConsumerState<HomeAttentionCenter> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() {
      ref.read(tasksProvider.notifier).loadTasks();
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final doctorTimeZone = ref
        .watch(profileAllProvider)
        .valueOrNull
        ?.profile['timeZone'] as String?;
    final notificationsAsync = ref.watch(doctorNotificationsProvider);
    final tasks = ref.watch(tasksProvider);

    return DashboardCard(
      title: l10n.translate('attentionRequired') ?? 'Attention required',
      subtitle: l10n.translate('attentionRequiredSubtitle') ??
          'Items that need your action',
      child: notificationsAsync.when(
        loading: () => const DashboardSkeleton(height: 160, lines: 3),
        error: (_, __) => Text(l10n.error),
        data: (notifications) {
          final items = _buildItems(
            context,
            l10n,
            notifications,
            tasks,
            doctorTimeZone: doctorTimeZone,
          );
          if (items.isEmpty) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Center(
                child: Column(
                  children: [
                    Icon(Icons.check_circle_outline,
                        size: 40, color: AppDesignSystem.success),
                    const SizedBox(height: 8),
                    Text(
                      l10n.translate('allCaughtUp') ?? 'All caught up!',
                      style: AppDesignSystem.body1(context),
                    ),
                  ],
                ),
              ),
            );
          }
          return Column(
            children: [
              for (var i = 0; i < items.length; i++) ...[
                if (i > 0) const SizedBox(height: 8),
                items[i],
              ],
            ],
          );
        },
      ),
    );
  }

  List<Widget> _buildItems(
    BuildContext context,
    AppLocalizations l10n,
    List<DoctorNotificationModel> notifications,
    List<RemoteCareTask> tasks, {
    String? doctorTimeZone,
  }) {
    final items = <Widget>[];
    final unread = notifications.where((n) => !n.isRead).take(4);
    for (final n in unread) {
      items.add(_AttentionTile(
        icon: _iconForType(n.type),
        title: localizedNotificationTitle(n, l10n),
        subtitle: localizedNotificationMessage(
          n,
          l10n,
          timeZone: doctorTimeZone,
        ),
        time: formatNotificationTime(n.createdAt, l10n),
        onTap: () => _onNotificationTap(n),
      ));
    }

    final activeTasks =
        tasks.where((t) => t.status == TaskStatus.active).take(3);
    for (final t in activeTasks) {
      items.add(_AttentionTile(
        icon: Icons.task_alt_outlined,
        title: t.taskName,
        subtitle: l10n.translate('followUpTask') ?? 'Follow-up task',
        time: '',
        onTap: () => _onTaskTap(context, t),
      ));
    }

    return items.take(6).toList();
  }

  void _onNotificationTap(DoctorNotificationModel notification) {
    ref.read(doctorNotificationsControllerProvider).markAsRead(notification.id);
    navigateToNotificationTarget(notification);
  }

  void _onTaskTap(BuildContext context, RemoteCareTask task) {
    ShellScope.pushNamed(
      context,
      AppRoutes.taskDetails,
      arguments: task.id,
    );
  }

  IconData _iconForType(String type) {
    switch (type) {
      case 'DOCUMENT_ACCESS_REQUEST':
      case 'DOCUMENT_UPLOADED':
        return Icons.description_outlined;
      case 'APPOINTMENT_MISSED':
      case 'APPOINTMENT_CANCELLED':
        return Icons.event_busy_outlined;
      default:
        return Icons.notifications_outlined;
    }
  }
}

class _AttentionTile extends StatelessWidget {
  const _AttentionTile({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.time,
    this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final String time;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        hoverColor: AppColors.primaryTeal.withValues(alpha: 0.04),
        child: Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(
            color: AppDesignSystem.backgroundSecondary,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppDesignSystem.border),
          ),
          child: Row(
            children: [
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: AppColors.primaryTeal.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(icon, size: 18, color: AppColors.primaryTeal),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                        fontSize: 14,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      subtitle,
                      style: AppDesignSystem.body2(context),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ],
                ),
              ),
              if (time.isNotEmpty)
                Text(time, style: AppDesignSystem.caption(context)),
              const SizedBox(width: 4),
              Icon(Icons.chevron_right,
                  size: 18, color: AppDesignSystem.textTertiary),
            ],
          ),
        ),
      ),
    );
  }
}
