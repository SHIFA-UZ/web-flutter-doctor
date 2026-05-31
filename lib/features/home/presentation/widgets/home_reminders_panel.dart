import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';

/// Checklist-style reminders panel (screenshot: Eslatmalar va vazifalar).
class HomeRemindersPanel extends ConsumerStatefulWidget {
  const HomeRemindersPanel({super.key});

  @override
  ConsumerState<HomeRemindersPanel> createState() => _HomeRemindersPanelState();
}

class _HomeRemindersPanelState extends ConsumerState<HomeRemindersPanel> {
  @override
  void initState() {
    super.initState();
    Future.microtask(() => ref.read(tasksProvider.notifier).loadTasks());
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final tasks = ref.watch(tasksProvider);
    final active = tasks
        .where((t) => t.status == TaskStatus.active)
        .take(5)
        .toList();

    return DashboardCard(
      title: l10n.translate('remindersAndTasks'),
      subtitle: active.isEmpty ? null : '${active.length}',
      child: active.isEmpty
          ? Padding(
              padding: const EdgeInsets.symmetric(vertical: 16),
              child: Text(
                l10n.translate('allCaughtUp'),
                style: AppDesignSystem.body2(context),
              ),
            )
          : Column(
              children: [
                for (var i = 0; i < active.length; i++) ...[
                  if (i > 0) const SizedBox(height: 8),
                  _ReminderRow(
                    title: active[i].taskName,
                    subtitle: active[i].patientName,
                    onTap: () {
                      ShellScope.pushNamed(
                        context,
                        AppRoutes.taskDetails,
                        arguments: active[i].id,
                      );
                    },
                  ),
                ],
              ],
            ),
    );
  }
}

class _ReminderRow extends StatelessWidget {
  const _ReminderRow({
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final String title;
  final String subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(10),
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 4),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(Icons.check_box_outline_blank,
                  size: 20, color: AppColors.primaryTeal),
              const SizedBox(width: 10),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w500,
                        fontSize: 14,
                      ),
                    ),
                    Text(subtitle, style: AppDesignSystem.caption(context)),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
