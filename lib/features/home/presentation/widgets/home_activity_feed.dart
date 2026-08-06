import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/notifications/domain/notification_model.dart';
import 'package:shifa_doc_app_v1/features/notifications/presentation/notification_ui_helpers.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

class HomeActivityFeed extends ConsumerWidget {
  const HomeActivityFeed({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final doctorTimeZone = ref
        .watch(profileAllProvider)
        .valueOrNull
        ?.profile['timeZone'] as String?;
    final notificationsAsync = ref.watch(doctorNotificationsProvider);

    return DashboardCard(
      title: l10n.translate('patientActivity'),
      subtitle: l10n.translate('patientActivitySubtitle'),
      child: notificationsAsync.when(
        loading: () => const DashboardSkeleton(height: 180, lines: 4),
        error: (_, __) => Text(l10n.error),
        data: (notifications) {
          final recent = [...notifications]
            ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
          final feed = recent.take(8).toList();
          if (feed.isEmpty) {
            return Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text(
                l10n.translate('noRecentActivity') ?? 'No recent activity',
                style: AppDesignSystem.body2(context),
              ),
            );
          }
          return Column(
            children: [
              for (var i = 0; i < feed.length; i++) ...[
                _ActivityRow(
                  notification: feed[i],
                  l10n: l10n,
                  doctorTimeZone: doctorTimeZone,
                ),
                if (i < feed.length - 1)
                  Divider(
                    height: 20,
                    color: AppDesignSystem.border.withValues(alpha: 0.6),
                  ),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _ActivityRow extends StatelessWidget {
  const _ActivityRow({
    required this.notification,
    required this.l10n,
    this.doctorTimeZone,
  });

  final DoctorNotificationModel notification;
  final AppLocalizations l10n;
  final String? doctorTimeZone;

  @override
  Widget build(BuildContext context) {
    final time = formatNotificationTime(notification.createdAt, l10n);
    final dotColor = _colorForType(notification.type);
    final title = localizedNotificationTitle(notification, l10n);
    final message = localizedNotificationMessage(
      notification,
      l10n,
      timeZone: doctorTimeZone,
    );

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Column(
          children: [
            Text(
              time,
              style: AppDesignSystem.caption(context).copyWith(
                fontFeatures: [const FontFeature.tabularFigures()],
              ),
            ),
          ],
        ),
        const SizedBox(width: 12),
        Container(
          width: 8,
          height: 8,
          margin: const EdgeInsets.only(top: 4),
          decoration: BoxDecoration(
            color: dotColor,
            shape: BoxShape.circle,
          ),
        ),
        const SizedBox(width: 12),
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
              if (message.isNotEmpty)
                Text(
                  message,
                  style: AppDesignSystem.body2(context),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
            ],
          ),
        ),
      ],
    );
  }

  Color _colorForType(String type) {
    if (type.contains('DOCUMENT')) return AppDesignSystem.info;
    if (type.contains('APPOINTMENT')) return AppColors.primaryTeal;
    if (type.contains('TASK')) return AppDesignSystem.warning;
    return AppDesignSystem.textTertiary;
  }
}
