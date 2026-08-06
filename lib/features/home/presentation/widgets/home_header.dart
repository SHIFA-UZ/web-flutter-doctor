import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/language_mini_toggle.dart';
import 'package:shifa_doc_app_v1/core/widgets/person_avatar.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

/// Compact home app bar: avatar, title, doctor name, notifications, language.
class HomeHeader extends ConsumerWidget {
  const HomeHeader({
    super.key,
    required this.onNotificationsTap,
  });

  final VoidCallback onNotificationsTap;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final unread =
        ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;

    final first = profile?['firstName'] as String? ?? '';
    final last = profile?['lastName'] as String? ?? '';
    final title = profile?['title'] as String? ?? 'Dr.';
    final photoUrl = profile?['photoUrl'] as String?;
    final displayName = (first.isEmpty && last.isEmpty)
        ? title
        : '$title $first ${last.isNotEmpty ? '${last[0]}.' : ''}'.trim();
    final avatarName =
        '$first $last'.trim().isEmpty ? displayName : '$first $last'.trim();

    return Row(
      children: [
        PersonAvatar(name: avatarName, photoUrl: photoUrl, radius: 22),
        const SizedBox(width: 12),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                l10n.home,
                style: AppDesignSystem.h1(context).copyWith(fontSize: 20),
              ),
              const SizedBox(height: 2),
              Text(
                displayName,
                style: AppDesignSystem.body2(context),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
            ],
          ),
        ),
        const LanguageMiniToggle(),
        const SizedBox(width: 4),
        _NotificationButton(
          unread: unread,
          onTap: onNotificationsTap,
        ),
      ],
    );
  }
}

class _NotificationButton extends StatelessWidget {
  const _NotificationButton({required this.unread, required this.onTap});

  final int unread;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 44,
      height: 44,
      child: IconButton(
        onPressed: onTap,
        tooltip: AppLocalizations.of(context)?.translate('notifications'),
        style: IconButton.styleFrom(
          foregroundColor: AppDesignSystem.textPrimary,
          minimumSize: const Size(44, 44),
          padding: EdgeInsets.zero,
        ),
        icon: Badge(
          isLabelVisible: unread > 0,
          smallSize: 8,
          backgroundColor: AppColors.destructiveRed,
          child: const Icon(Icons.notifications_outlined, size: 22),
        ),
      ),
    );
  }
}
