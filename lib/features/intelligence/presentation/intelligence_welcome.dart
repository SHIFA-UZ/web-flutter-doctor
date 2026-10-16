import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/utils/doctor_display_name.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_search_overlay.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

class IntelligenceWelcome extends ConsumerWidget {
  const IntelligenceWelcome({
    super.key,
    required this.recentSessions,
    required this.onSendPrompt,
    required this.onOpenSession,
  });

  final List<IntelligenceChatSession> recentSessions;
  final ValueChanged<String> onSendPrompt;
  final ValueChanged<String> onOpenSession;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final first = (profile?['firstName'] as String?) ?? '';
    final last = (profile?['lastName'] as String?) ?? '';
    final name = formatDoctorMedName(firstName: first, lastName: last);
    final hour = DateTime.now().hour;
    final greetingKey = hour < 12
        ? 'goodMorning'
        : hour < 17
            ? 'goodAfternoon'
            : 'goodEvening';

    final cards = <_WelcomeCardData>[
      _WelcomeCardData(
        icon: Icons.calendar_today_outlined,
        title: l10n.translate('intelWelcomeSchedule'),
        subtitle: l10n.translate('intelWelcomeScheduleDesc'),
        onTap: () => onSendPrompt(
          l10n.translate('intelPromptSchedule'),
        ),
      ),
      _WelcomeCardData(
        icon: Icons.person_search_outlined,
        title: l10n.translate('intelWelcomeFindPatient'),
        subtitle: l10n.translate('intelWelcomeFindPatientDesc'),
        onTap: () => HomeSearchOverlay.show(context),
      ),
      _WelcomeCardData(
        icon: Icons.event_available_outlined,
        title: l10n.translate('intelWelcomeAppointments'),
        subtitle: l10n.translate('intelWelcomeAppointmentsDesc'),
        onTap: () => onSendPrompt(
          l10n.translate('intelPromptManageAppointments'),
        ),
      ),
      _WelcomeCardData(
        icon: Icons.notes_outlined,
        title: l10n.translate('intelWelcomeNotes'),
        subtitle: l10n.translate('intelWelcomeNotesDesc'),
        onTap: () => onSendPrompt(
          l10n.translate('intelPromptClinicalNotes'),
        ),
      ),
    ];

    final recent = recentSessions
        .where((s) => s.messages.isNotEmpty)
        .take(5)
        .toList();

    return ListView(
      padding: const EdgeInsets.fromLTRB(28, 28, 28, 16),
      children: [
        Text(
          '${l10n.translate(greetingKey)}, $name.',
          style: AppDesignSystem.h2(context),
        ),
        const SizedBox(height: 6),
        Text(
          l10n.translate('intelligenceEmptyHint'),
          style: AppDesignSystem.body2(context).copyWith(
            color: AppDesignSystem.textSecondary,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          l10n.translate('intelHowCanIHelp'),
          style: AppDesignSystem.h2(context).copyWith(
            fontSize: 22,
            fontWeight: FontWeight.w700,
          ),
        ),
        const SizedBox(height: 20),
        LayoutBuilder(
          builder: (context, constraints) {
            final twoCol = constraints.maxWidth >= 560;
            if (!twoCol) {
              return Column(
                children: [
                  for (final c in cards) ...[
                    _WelcomeActionCard(data: c, brand: brand),
                    const SizedBox(height: 10),
                  ],
                ],
              );
            }
            return Column(
              children: [
                for (var i = 0; i < cards.length; i += 2)
                  Padding(
                    padding: EdgeInsets.only(bottom: i + 2 < cards.length ? 10 : 0),
                    child: Row(
                      children: [
                        Expanded(
                          child: _WelcomeActionCard(data: cards[i], brand: brand),
                        ),
                        const SizedBox(width: 10),
                        Expanded(
                          child: i + 1 < cards.length
                              ? _WelcomeActionCard(
                                  data: cards[i + 1],
                                  brand: brand,
                                )
                              : const SizedBox.shrink(),
                        ),
                      ],
                    ),
                  ),
              ],
            );
          },
        ),
        if (recent.isNotEmpty) ...[
          const SizedBox(height: 28),
          Text(
            l10n.translate('intelRecentConversations'),
            style: AppDesignSystem.body1(context).copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          const SizedBox(height: 10),
          ...recent.map((s) {
            return ListTile(
              contentPadding: EdgeInsets.zero,
              leading: Icon(Icons.chat_bubble_outline, color: brand, size: 20),
              title: Text(
                s.displayTitle,
                style: AppDesignSystem.body2(context),
              ),
              subtitle: Text(
                _relative(s.updatedAt),
                style: AppDesignSystem.caption(context),
              ),
              onTap: () => onOpenSession(s.id),
            );
          }),
        ],
      ],
    );
  }

  String _relative(DateTime at) {
    final d = DateTime.now().difference(at);
    if (d.inMinutes < 1) return 'Just now';
    if (d.inHours < 1) return '${d.inMinutes}m ago';
    if (d.inDays < 1) return '${d.inHours}h ago';
    if (d.inDays < 7) return '${d.inDays}d ago';
    return '${at.day}.${at.month}.${at.year}';
  }
}

class _WelcomeCardData {
  const _WelcomeCardData({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback onTap;
}

class _WelcomeActionCard extends StatelessWidget {
  const _WelcomeActionCard({required this.data, required this.brand});

  final _WelcomeCardData data;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.white,
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        onTap: data.onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          width: double.infinity,
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppDesignSystem.border),
          ),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Icon(data.icon, color: brand, size: 22),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      data.title,
                      style: AppDesignSystem.body1(context).copyWith(
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      data.subtitle,
                      style: AppDesignSystem.caption(context).copyWith(
                        color: AppDesignSystem.textSecondary,
                      ),
                    ),
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
