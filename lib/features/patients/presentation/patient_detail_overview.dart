import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/api/ai_message.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/widgets/ai_response_text.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/presentation/patient_detail_helpers.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

class PatientSummaryStat extends StatelessWidget {
  const PatientSummaryStat({
    super.key,
    required this.label,
    required this.value,
  });

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 14),
      decoration: BoxDecoration(
        color: AppDesignSystem.backgroundSecondary,
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
        border: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.7)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppDesignSystem.caption(context)),
          const SizedBox(height: 6),
          Text(
            value,
            style: AppDesignSystem.h2(context).copyWith(fontSize: 15),
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}

class PatientHeroHeader extends StatelessWidget {
  const PatientHeroHeader({
    super.key,
    required this.patient,
    required this.brand,
    required this.isFavorite,
    required this.onToggleFavorite,
    required this.onMoreActions,
    required this.onAiSummary,
    required this.showAiSummary,
  });

  final Patient patient;
  final Color brand;
  final bool isFavorite;
  final VoidCallback onToggleFavorite;
  final VoidCallback onMoreActions;
  final VoidCallback onAiSummary;
  final bool showAiSummary;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final status = patientStatus(patient, l10n);
    final hasRisk = patient.atRisk;
    final isNarrow = PlatformLayout.useCompactToolbar(context);

    final nameBlock = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          patient.name,
          style: AppDesignSystem.display(context).copyWith(fontSize: 22),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 6,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            _StatusPill(
              label: status.kind == PatientStatusKind.active
                  ? (l10n.translate('activePatient') ?? 'Active Patient')
                  : status.label,
              color: status.textColor,
              background: status.backgroundColor,
            ),
            if (hasRisk)
              _StatusPill(
                label: l10n.translate('aiRiskDetected') ?? 'AI Risk Detected',
                color: AppDesignSystem.warning,
                background: const Color(0xFFFFF7ED),
                icon: Icons.auto_awesome,
              ),
            Text(
              patientLastVisitLabel(patient, l10n),
              style: AppDesignSystem.body2(context),
            ),
          ],
        ),
      ],
    );

    final actions = Wrap(
      spacing: 4,
      runSpacing: 4,
      crossAxisAlignment: WrapCrossAlignment.center,
      children: [
        IconButton(
          onPressed: onToggleFavorite,
          icon: Icon(
            isFavorite ? Icons.star : Icons.star_border,
            color: isFavorite ? brand : Colors.grey.shade500,
          ),
          tooltip: l10n.translate('favorites') ?? 'Favorite',
        ),
        if (showAiSummary)
          ShifaSecondaryButton(
            onPressed: onAiSummary,
            icon: Icons.summarize_outlined,
            label: l10n.translate('aiSummary') ?? 'AI Summary',
          ),
        IconButton(
          onPressed: onMoreActions,
          icon: Icon(Icons.more_vert, color: brand),
        ),
      ],
    );

    if (isNarrow) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _PatientAvatar(
                name: patient.name,
                photoUrl: patient.photoUrl,
                size: 32,
              ),
              const SizedBox(width: 12),
              Expanded(child: nameBlock),
            ],
          ),
          const SizedBox(height: 8),
          actions,
        ],
      );
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        _PatientAvatar(name: patient.name, photoUrl: patient.photoUrl, size: 32),
        const SizedBox(width: 16),
        Expanded(child: nameBlock),
        IconButton(
          onPressed: onToggleFavorite,
          icon: Icon(
            isFavorite ? Icons.star : Icons.star_border,
            color: isFavorite ? brand : Colors.grey.shade500,
          ),
          tooltip: l10n.translate('favorites') ?? 'Favorite',
        ),
        if (showAiSummary)
          ShifaSecondaryButton(
            onPressed: onAiSummary,
            icon: Icons.summarize_outlined,
            label: l10n.translate('aiSummary') ?? 'AI Summary',
          ),
        IconButton(
          onPressed: onMoreActions,
          icon: Icon(Icons.more_vert, color: brand),
        ),
      ],
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({
    required this.label,
    required this.color,
    required this.background,
    this.icon,
  });

  final String label;
  final Color color;
  final Color background;
  final IconData? icon;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: background,
        borderRadius: BorderRadius.circular(20),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (icon != null) ...[
            Icon(icon, size: 12, color: color),
            const SizedBox(width: 4),
          ],
          Text(
            label,
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.w600,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

class PatientAiCopilotCard extends ConsumerStatefulWidget {
  const PatientAiCopilotCard({
    super.key,
    required this.patientId,
    required this.patientName,
    required this.brand,
  });

  final String patientId;
  final String patientName;
  final Color brand;

  @override
  ConsumerState<PatientAiCopilotCard> createState() =>
      _PatientAiCopilotCardState();
}

class _PatientAiCopilotCardState extends ConsumerState<PatientAiCopilotCard> {
  final _controller = TextEditingController();
  final _scroll = ScrollController();
  StreamSubscription<AiStreamEvent>? _sub;

  bool _loading = false;
  String _streamingText = '';
  String? _error;
  List<AiMessage> _conversation = [];

  static const int _maxConversationMessages = 12;

  @override
  void dispose() {
    _sub?.cancel();
    _controller.dispose();
    _scroll.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant PatientAiCopilotCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.patientId != widget.patientId) {
      _sub?.cancel();
      _controller.clear();
      setState(() {
        _loading = false;
        _streamingText = '';
        _error = null;
        _conversation = [];
      });
    }
  }

  List<AiMessage> _trimConversation(List<AiMessage> messages) {
    if (messages.length <= _maxConversationMessages) return messages;
    return messages.sublist(messages.length - _maxConversationMessages);
  }

  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_scroll.hasClients) return;
      _scroll.jumpTo(_scroll.position.maxScrollExtent);
    });
  }

  Future<void> _ask() async {
    final question = _controller.text.trim();
    if (question.isEmpty || _loading) return;

    final aiApi = ref.read(aiApiProvider);
    final patientId = int.tryParse(widget.patientId);
    final next = _trimConversation([
      ..._conversation,
      AiMessage(role: 'user', content: question),
    ]);

    setState(() {
      _conversation = next;
      _streamingText = '';
      _error = null;
      _loading = true;
    });
    _controller.clear();
    _scrollToBottom();

    _sub?.cancel();
    final streamLang =
        ref.read(languageProvider).locale.backendLanguageCode.toUpperCase();
    _sub = aiApi
        .streamAi(
          messages: next,
          question: question,
          language: streamLang,
          patientId: patientId,
        )
        .listen(
          (event) {
            if (event is AiTokenEvent) {
              setState(() => _streamingText += event.token);
              _scrollToBottom();
            }
          },
          onDone: () {
            if (!mounted) return;
            setState(() {
              final answer = _streamingText.trimRight();
              if (answer.isNotEmpty) {
                _conversation = _trimConversation([
                  ..._conversation,
                  AiMessage(role: 'assistant', content: answer),
                ]);
              }
              _streamingText = '';
              _loading = false;
            });
            _scrollToBottom();
          },
          onError: (e) {
            if (!mounted) return;
            setState(() {
              _loading = false;
              _streamingText = '';
              _error = e is AiStreamException ? e.userFacingMessage : e.toString();
            });
          },
        );
  }

  @override
  Widget build(BuildContext context) {
    final canUse = ref.watch(doctorFeatureProvider(DoctorFeature.askShifaAi));
    if (!canUse) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context)!;
    final hasResponse =
        _loading || _error != null || _conversation.isNotEmpty || _streamingText.isNotEmpty;

    return Container(
      decoration: AppDesignSystem.aiCardDecoration(),
      padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(8),
                decoration: BoxDecoration(
                  gradient: LinearGradient(
                    colors: [widget.brand, AppDesignSystem.primaryAi],
                  ),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Icon(Icons.auto_awesome, color: Colors.white, size: 18),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.translate('aiPatientCopilot') ?? 'AI Patient Copilot',
                      style: AppDesignSystem.h2(context),
                    ),
                    Text(
                      l10n.translate('aiPatientCopilotSubtitle') ??
                          'Ask about this patient\'s history, risks, and follow-ups.',
                      style: AppDesignSystem.body2(context),
                    ),
                  ],
                ),
              ),
            ],
          ),
          if (hasResponse) ...[
            const SizedBox(height: 12),
            Container(
              constraints: const BoxConstraints(minHeight: 72, maxHeight: 220),
              width: double.infinity,
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppDesignSystem.border),
              ),
              child: SingleChildScrollView(
                controller: _scroll,
                child: _error != null
                    ? Text(
                        _error!,
                        style: TextStyle(
                          color: Theme.of(context).colorScheme.error,
                          fontSize: 13,
                        ),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          for (final message in _conversation)
                            _PatientAiChatBubble(
                              role: message.role,
                              child: AiResponseText(
                                text: message.content,
                                style: const TextStyle(fontSize: 13),
                                maxWidth: 520,
                              ),
                            ),
                          if (_streamingText.isNotEmpty)
                            _PatientAiChatBubble(
                              role: 'assistant',
                              child: AiResponseText(
                                text: _streamingText,
                                style: const TextStyle(fontSize: 13),
                                maxWidth: 520,
                              ),
                            ),
                          if (_loading && _streamingText.isEmpty)
                            Padding(
                              padding: const EdgeInsets.symmetric(vertical: 8),
                              child: Row(
                                children: [
                                  SizedBox(
                                    width: 16,
                                    height: 16,
                                    child: CircularProgressIndicator(
                                      strokeWidth: 2,
                                      color: widget.brand,
                                    ),
                                  ),
                                  const SizedBox(width: 10),
                                  Expanded(
                                    child: Text(
                                      l10n.translate('aiAnalyzingPatientDocs') ??
                                          'Analyzing patient documents…',
                                      style: AppDesignSystem.body2(context),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                        ],
                      ),
              ),
            ),
          ],
          const SizedBox(height: 14),
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  enabled: !_loading,
                  decoration: InputDecoration(
                    hintText: l10n.translate('askAboutPatient') ??
                        'Ask about this patient…',
                    filled: true,
                    fillColor: Colors.white,
                    isDense: true,
                    contentPadding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 12,
                    ),
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(
                        color: AppColors.primaryTeal.withValues(alpha: 0.2),
                      ),
                    ),
                    enabledBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(14),
                      borderSide: BorderSide(
                        color: AppColors.primaryTeal.withValues(alpha: 0.2),
                      ),
                    ),
                  ),
                  onSubmitted: (_) => _ask(),
                ),
              ),
              const SizedBox(width: 10),
              ShifaPrimaryButton(
                onPressed: _loading ? null : _ask,
                icon: Icons.send_rounded,
                label: l10n.translate('ask') ?? 'Ask',
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class _PatientAiChatBubble extends StatelessWidget {
  const _PatientAiChatBubble({required this.role, required this.child});

  final String role;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final isUser = role == 'user';
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 6),
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: isUser
              ? AppColors.secondaryLight.withValues(alpha: 0.5)
              : Colors.white,
          borderRadius: BorderRadius.circular(10),
          border: Border.all(color: AppDesignSystem.border),
        ),
        child: child,
      ),
    );
  }
}

class ClinicalSummaryCard extends StatelessWidget {
  const ClinicalSummaryCard({super.key, required this.patient});

  final Patient patient;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final chronic = patient.general.chronicDisease;
    final chronicLabel = chronic == null || chronic.isEmpty || chronic == 'None'
        ? (l10n.translate('none') ?? 'None')
        : l10n.translateChronicDisease(chronic);

    return _SectionCard(
      title: l10n.translate('clinicalSummary') ?? 'Clinical Summary',
      child: LayoutBuilder(
        builder: (context, constraints) {
          final isNarrow = constraints.maxWidth < 420;
          final tiles = [
            _MiniStat(
              label: l10n.chronicDisease,
              value: chronicLabel,
            ),
            _MiniStat(
              label: l10n.translate('allergies') ?? 'Allergies',
              value: patientAllergiesLabel(patient, l10n),
            ),
            _MiniStat(
              label: l10n.translate('bloodGroup') ?? 'Blood Group',
              value: patientBloodGroupLabel(patient, l10n),
            ),
            _MiniStat(
              label: l10n.language,
              value: patient.general.language != null
                  ? patient.general.language![0].toUpperCase() +
                      patient.general.language!.substring(1)
                  : (l10n.translate('notSpecified') ?? '—'),
            ),
          ];

          if (isNarrow) {
            return Column(
              children: [
                for (var i = 0; i < tiles.length; i += 2) ...[
                  Row(
                    children: [
                      Expanded(child: tiles[i]),
                      if (i + 1 < tiles.length) ...[
                        const SizedBox(width: 10),
                        Expanded(child: tiles[i + 1]),
                      ],
                    ],
                  ),
                  if (i + 2 < tiles.length) const SizedBox(height: 10),
                ],
              ],
            );
          }

          return Row(
            children: [
              for (var i = 0; i < tiles.length; i++) ...[
                Expanded(child: tiles[i]),
                if (i < tiles.length - 1) const SizedBox(width: 10),
              ],
            ],
          );
        },
      ),
    );
  }
}

class RecentActivityCard extends StatelessWidget {
  const RecentActivityCard({
    super.key,
    required this.activities,
    required this.brand,
    required this.onViewFullHistory,
  });

  final List<PatientActivityItem> activities;
  final Color brand;
  final VoidCallback onViewFullHistory;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    String two(int n) => n.toString().padLeft(2, '0');

    return _SectionCard(
      title: l10n.translate('recentActivity') ?? 'Recent Activity',
      child: Column(
        children: [
          if (activities.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(
                l10n.translate('noRecentActivity') ?? 'No recent activity',
                style: AppDesignSystem.body2(context),
              ),
            )
          else
            ...activities.map((item) {
              return Padding(
                padding: const EdgeInsets.only(bottom: 14),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Container(
                      width: 34,
                      height: 34,
                      decoration: BoxDecoration(
                        color: item.iconColor.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(10),
                      ),
                      child: Icon(item.icon, size: 18, color: item.iconColor),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(item.title, style: AppDesignSystem.h2(context)),
                          Text(item.subtitle, style: AppDesignSystem.body2(context)),
                        ],
                      ),
                    ),
                    Text(
                      '${two(item.date.day)}.${two(item.date.month)}.${item.date.year}',
                      style: AppDesignSystem.caption(context),
                    ),
                  ],
                ),
              );
            }),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: onViewFullHistory,
              icon: Icon(Icons.arrow_forward, size: 16, color: brand),
              label: Text(
                l10n.translate('viewFullHistory') ?? 'View full history',
                style: TextStyle(color: brand, fontWeight: FontWeight.w600),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class QuickActionsCard extends StatelessWidget {
  const QuickActionsCard({
    super.key,
    required this.brand,
    required this.onNewAppointment,
    required this.onSendMessage,
    required this.onCreateDocument,
    required this.onMoreActions,
  });

  final Color brand;
  final VoidCallback onNewAppointment;
  final VoidCallback onSendMessage;
  final VoidCallback onCreateDocument;
  final VoidCallback onMoreActions;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return _SectionCard(
      title: l10n.translate('quickActions') ?? 'Quick Actions',
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: _QuickActionButton(
                  icon: Icons.calendar_today_outlined,
                  label: l10n.translate('newAppointment') ?? 'New Appointment',
                  brand: brand,
                  onTap: onNewAppointment,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _QuickActionButton(
                  icon: Icons.message_outlined,
                  label: l10n.translate('sendMessage') ?? 'Send Message',
                  brand: brand,
                  onTap: onSendMessage,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(
                child: _QuickActionButton(
                  icon: Icons.note_add_outlined,
                  label: l10n.translate('createDocument') ?? 'Create Document',
                  brand: brand,
                  onTap: onCreateDocument,
                ),
              ),
              const SizedBox(width: 10),
              Expanded(
                child: _QuickActionButton(
                  icon: Icons.more_horiz,
                  label: l10n.translate('moreActions') ?? 'More Actions',
                  brand: brand,
                  onTap: onMoreActions,
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

class AiFollowUpSuggestionsCard extends StatelessWidget {
  const AiFollowUpSuggestionsCard({
    super.key,
    required this.suggestions,
    required this.brand,
  });

  final List<String> suggestions;
  final Color brand;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      decoration: AppDesignSystem.aiCardDecoration(),
      padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.lightbulb_outline, color: brand, size: 18),
              const SizedBox(width: 8),
              Text(
                l10n.translate('aiFollowUpSuggestions') ?? 'AI Follow-up Suggestions',
                style: AppDesignSystem.h2(context),
              ),
            ],
          ),
          const SizedBox(height: 12),
          ...suggestions.map(
            (s) => Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(Icons.check_circle_outline, size: 16, color: brand),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(s, style: AppDesignSystem.body2(context)),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
      decoration: AppDesignSystem.cardDecoration(
        borderOverride: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.7)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(title, style: AppDesignSystem.h2(context)),
          const SizedBox(height: 14),
          child,
        ],
      ),
    );
  }
}

class _MiniStat extends StatelessWidget {
  const _MiniStat({required this.label, required this.value});

  final String label;
  final String value;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: AppDesignSystem.backgroundSecondary,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.6)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(label, style: AppDesignSystem.caption(context)),
          const SizedBox(height: 4),
          Text(
            value,
            style: AppDesignSystem.body1(context).copyWith(
              fontWeight: FontWeight.w600,
              fontSize: 13,
            ),
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}

class _QuickActionButton extends StatelessWidget {
  const _QuickActionButton({
    required this.icon,
    required this.label,
    required this.brand,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final Color brand;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 14),
          decoration: BoxDecoration(
            color: AppDesignSystem.backgroundSecondary,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppDesignSystem.border.withValues(alpha: 0.7)),
          ),
          child: Column(
            children: [
              Icon(icon, color: brand, size: 20),
              const SizedBox(height: 8),
              Text(
                label,
                textAlign: TextAlign.center,
                style: const TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PatientAvatar extends StatelessWidget {
  const _PatientAvatar({
    required this.name,
    this.photoUrl,
    this.size = 24,
  });

  final String name;
  final String? photoUrl;
  final double size;

  String get initials {
    final parts = name.trim().split(RegExp(r'\s+'));
    if (parts.isEmpty) return '?';
    if (parts.length == 1) {
      return parts.first.isNotEmpty
          ? parts.first.substring(0, 1).toUpperCase()
          : '?';
    }
    final first = parts.first.isNotEmpty ? parts.first.substring(0, 1) : '?';
    final last = parts.last.isNotEmpty ? parts.last.substring(0, 1) : '?';
    return (first + last).toUpperCase();
  }

  @override
  Widget build(BuildContext context) {
    final hasUrl = photoUrl != null && photoUrl!.isNotEmpty;
    return CircleAvatar(
      radius: size,
      backgroundColor: AppColors.secondaryLight,
      backgroundImage: hasUrl ? NetworkImage(photoUrl!) : null,
      child: hasUrl
          ? null
          : Text(
              initials,
              style: TextStyle(
                fontWeight: FontWeight.w700,
                color: AppColors.primaryTeal,
                fontSize: size * 0.72,
              ),
            ),
    );
  }
}
