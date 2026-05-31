import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/api/ai_message.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/localization/uzbek_latin_to_cyrillic.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/ai_response_text.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_context_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';

/// AI-first command center with proactive insights and expandable Ask Shifa chat.
class HomeAiCopilot extends ConsumerStatefulWidget {
  const HomeAiCopilot({
    super.key,
    this.selectedPatientId,
    this.selectedAppointmentId,
    this.onPatientChanged,
  });

  final String? selectedPatientId;
  final String? selectedAppointmentId;
  final ValueChanged<String?>? onPatientChanged;

  @override
  ConsumerState<HomeAiCopilot> createState() => _HomeAiCopilotState();
}

class _HomeAiCopilotState extends ConsumerState<HomeAiCopilot> {
  final _aiController = TextEditingController();
  final _aiScroll = ScrollController();
  StreamSubscription<AiStreamEvent>? _aiSub;

  bool _aiLoading = false;
  bool _chatExpanded = false;
  String _streamingAiText = '';
  String? _aiError;
  List<AiMessage> _conversation = [];
  String? _draftId;
  String? _draftLabel;
  String? _lastConversationStorageKey;

  static const int _maxConversationMessages = 15;
  static const String _conversationStoragePrefix = 'ask_shifa_ai_conversation_v1';
  static const String _aiLangUzbekCyrillic = 'UZ_CYRL';

  @override
  void dispose() {
    _aiSub?.cancel();
    _aiController.dispose();
    _aiScroll.dispose();
    super.dispose();
  }

  String _aiUiLanguageFromLocale(Locale locale) {
    switch (locale.languageCode) {
      case 'uz':
        return locale.isUzbekCyrillic ? _aiLangUzbekCyrillic : 'UZ';
      case 'ru':
        return 'RU';
      default:
        return 'EN';
    }
  }

  String _aiLanguageBackendCode(String selected) {
    final u = selected.toUpperCase();
    if (u == _aiLangUzbekCyrillic) return 'UZ';
    return u;
  }

  @override
  Widget build(BuildContext context) {
    final canUse = ref.watch(doctorFeatureProvider(DoctorFeature.askShifaAi));
    if (!canUse) return const SizedBox.shrink();

    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final insights = _buildInsights(l10n);

    return AnimatedContainer(
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOut,
      decoration: AppDesignSystem.aiCardDecoration(),
      child: Padding(
        padding: const EdgeInsets.all(AppDesignSystem.cardPadding),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      colors: [brand, AppDesignSystem.primaryAi],
                    ),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(Icons.auto_awesome, color: Colors.white, size: 20),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        l10n.translate('aiCommandCenter'),
                        style: AppDesignSystem.h2(context),
                      ),
                      Text(
                        l10n.translate('aiCommandCenterSubtitle'),
                        style: AppDesignSystem.body2(context),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  onPressed: () => setState(() => _chatExpanded = !_chatExpanded),
                  icon: Icon(
                    _chatExpanded ? Icons.expand_less : Icons.expand_more,
                    color: brand,
                  ),
                  tooltip: _chatExpanded
                      ? l10n.collapse
                      : (l10n.translate('askShifaAi') ?? 'Ask Shifa AI'),
                ),
              ],
            ),
            const SizedBox(height: 16),
            ...insights.map(
              (insight) => Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: _InsightRow(
                  insight: insight,
                  brand: brand,
                  onTap: insight.onTap,
                ),
              ),
            ),
            if (_chatExpanded) ...[
              const SizedBox(height: 12),
              _buildChatArea(context, l10n, brand),
            ] else ...[
              const SizedBox(height: 8),
              ShifaSecondaryButton(
                label: l10n.translate('askShifaAi') ?? 'Ask Shifa AI',
                onPressed: () {
                  setState(() => _chatExpanded = true);
                  _loadConversationForCurrentContext();
                },
                width: ButtonWidth.fill,
              ),
            ],
          ],
        ),
      ),
    );
  }

  List<_AiInsight> _buildInsights(AppLocalizations l10n) {
    final insights = <_AiInsight>[];
    final appointments = ref.watch(todayAppointmentsProvider).valueOrNull ?? [];
    final incomplete = appointments.where((a) => !a.isCompleted).length;
    if (incomplete > 0) {
      insights.add(_AiInsight(
        icon: Icons.schedule,
        text: l10n.translate('aiInsightAppointments')?.replaceAll(
                '{count}', incomplete.toString()) ??
            'You have $incomplete appointments remaining today.',
        action: l10n.translate('review') ?? 'Review',
        onTap: () =>
            ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar),
      ));
    }

    final unread =
        ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;
    if (unread > 0) {
      insights.add(_AiInsight(
        icon: Icons.mark_email_unread_outlined,
        text: l10n.translate('aiInsightNotifications')?.replaceAll(
                '{count}', unread.toString()) ??
            '$unread items require your attention.',
        action: l10n.translate('review') ?? 'Review',
        onTap: () => ref
            .read(shellProvider.notifier)
            .setTab(DoctorShellTab.notifications),
      ));
    }

    final activeTasks = ref
        .watch(tasksProvider)
        .where((t) => t.status == TaskStatus.active)
        .length;
    if (activeTasks > 0) {
      insights.add(_AiInsight(
        icon: Icons.task_alt,
        text: l10n.translate('aiInsightTasks')?.replaceAll(
                '{count}', activeTasks.toString()) ??
            '$activeTasks follow-up tasks are pending.',
        action: l10n.translate('review') ?? 'Review',
        onTap: () =>
            ref.read(shellProvider.notifier).setTab(DoctorShellTab.tasks),
      ));
    }

    if (insights.isEmpty) {
      insights.add(_AiInsight(
        icon: Icons.check_circle_outline,
        text: l10n.translate('aiInsightAllClear') ??
            'Your schedule looks clear. Ask me anything about your patients.',
        action: l10n.translate('askShifaAi') ?? 'Ask Shifa AI',
        onTap: () {
          setState(() => _chatExpanded = true);
          _loadConversationForCurrentContext();
        },
      ));
    }

    return insights.take(4).toList();
  }

  Widget _buildChatArea(BuildContext context, AppLocalizations l10n, Color brand) {
    final patients = ref.watch(patientsProvider);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SizedBox(
          height: 36,
          child: DropdownButtonFormField<String>(
            value: widget.selectedPatientId,
            isExpanded: true,
            decoration: InputDecoration(
              hintText: l10n.allPatients,
              isDense: true,
              contentPadding:
                  const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              border: OutlineInputBorder(
                borderRadius: BorderRadius.circular(10),
                borderSide: BorderSide(color: AppDesignSystem.border),
              ),
            ),
            items: [
              DropdownMenuItem(value: null, child: Text(l10n.allPatients)),
              ...patients.map(
                (p) => DropdownMenuItem(
                  value: p.id.toString(),
                  child: Text(p.name, overflow: TextOverflow.ellipsis),
                ),
              ),
            ],
            onChanged: widget.onPatientChanged,
          ),
        ),
        const SizedBox(height: 8),
        Container(
          constraints: const BoxConstraints(minHeight: 100, maxHeight: 200),
          padding: const EdgeInsets.all(10),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppDesignSystem.border),
          ),
          child: SingleChildScrollView(
            controller: _aiScroll,
            child: _aiError != null
                ? Text(_aiError!, style: TextStyle(color: Theme.of(context).colorScheme.error))
                : (_conversation.where((m) => m.role != 'system').isEmpty &&
                        _streamingAiText.isEmpty)
                    ? Text(
                        l10n.translate('aiWillRespondHere') ?? 'AI will respond here…',
                        style: AppDesignSystem.body2(context),
                      )
                    : Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          ..._conversation
                              .where((m) => m.role != 'system')
                              .map((m) => _AiChatBubble(
                                    role: m.role,
                                    child: AiResponseText(
                                      text: m.content,
                                      style: const TextStyle(fontSize: 13),
                                      maxWidth: 400,
                                    ),
                                  )),
                          if (_streamingAiText.isNotEmpty)
                            _AiChatBubble(
                              role: 'assistant',
                              child: AiResponseText(
                                text: _streamingAiText,
                                style: const TextStyle(fontSize: 13),
                                maxWidth: 400,
                              ),
                            ),
                        ],
                      ),
          ),
        ),
        const SizedBox(height: 8),
        TextField(
          controller: _aiController,
          decoration: InputDecoration(
            hintText: l10n.translate('askShifaAi') ?? 'Ask Shifa AI',
            suffixIcon: IconButton(
              icon: _aiLoading
                  ? SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2, color: brand),
                    )
                  : Icon(Icons.send, color: brand),
              onPressed: _aiLoading ? null : _askAi,
            ),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(20)),
            filled: true,
            fillColor: Colors.white,
          ),
          onSubmitted: (_) => _askAi(),
        ),
      ],
    );
  }

  Future<void> _askAi() async {
    final question = _aiController.text.trim();
    if (question.isEmpty || _aiLoading) return;
    if (widget.selectedPatientId != null) {
      await ref
          .read(patientBriefingContextProvider.notifier)
          .ensureLoaded(widget.selectedPatientId!);
    }

    final aiApi = ref.read(aiApiProvider);
    final next = [..._conversation, AiMessage(role: 'user', content: question)];
    setState(() {
      _conversation = next;
      _streamingAiText = '';
      _aiError = null;
      _aiLoading = true;
    });
    _aiController.clear();
    _aiSub?.cancel();
    _aiSub = aiApi
        .streamAi(
          messages: next,
          question: question,
          language: _aiLanguageBackendCode(
              _aiUiLanguageFromLocale(ref.read(languageProvider).locale)),
          patientId: widget.selectedPatientId == null
              ? null
              : int.tryParse(widget.selectedPatientId!),
          consultationId: widget.selectedAppointmentId != null
              ? int.tryParse(widget.selectedAppointmentId!)
              : null,
        )
        .listen(
          (event) {
            if (event is AiTokenEvent) {
              setState(() => _streamingAiText += event.token);
            }
          },
          onDone: () {
            setState(() {
              final answer = _streamingAiText.trimRight();
              if (answer.isNotEmpty) {
                _conversation = [
                  ..._conversation,
                  AiMessage(role: 'assistant', content: answer),
                ];
              }
              _streamingAiText = '';
              _aiLoading = false;
            });
            _persistConversation();
          },
          onError: (e) => setState(() {
            _aiLoading = false;
            _aiError = e.toString();
          }),
        );
  }

  String _conversationStorageKey() {
    final profile = ref.read(profileAllProvider).valueOrNull?.profile;
    final doctorId =
        (profile?['id'] ?? profile?['doctorId'] ?? 'unknown').toString();
    return '$_conversationStoragePrefix:$doctorId:${widget.selectedPatientId ?? 'none'}:${widget.selectedAppointmentId ?? 'none'}';
  }

  Future<void> _persistConversation() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _conversationStorageKey(),
      jsonEncode(_conversation.map((m) => m.toJson()).toList()),
    );
  }

  Future<void> _loadConversationForCurrentContext() async {
    final key = _conversationStorageKey();
    if (_lastConversationStorageKey == key) return;
    _lastConversationStorageKey = key;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(key);
    if (raw == null || !mounted) return;
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      setState(() {
        _conversation = decoded
            .whereType<Map>()
            .map((m) => AiMessage.fromJson(Map<String, dynamic>.from(m)))
            .toList();
      });
    } catch (_) {}
  }
}

class _AiInsight {
  const _AiInsight({
    required this.icon,
    required this.text,
    required this.action,
    required this.onTap,
  });

  final IconData icon;
  final String text;
  final String action;
  final VoidCallback onTap;
}

class _InsightRow extends StatelessWidget {
  const _InsightRow({
    required this.insight,
    required this.brand,
    required this.onTap,
  });

  final _AiInsight insight;
  final Color brand;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(12),
        hoverColor: brand.withValues(alpha: 0.06),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.7),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: AppDesignSystem.border),
          ),
          child: Row(
            children: [
              Icon(insight.icon, size: 18, color: brand),
              const SizedBox(width: 10),
              Expanded(
                child: Text(insight.text, style: AppDesignSystem.body2(context)),
              ),
              Text(
                insight.action,
                style: TextStyle(
                  fontSize: 12,
                  fontWeight: FontWeight.w600,
                  color: brand,
                ),
              ),
              const SizedBox(width: 2),
              Icon(Icons.chevron_right, size: 16, color: brand),
            ],
          ),
        ),
      ),
    );
  }
}

class _AiChatBubble extends StatelessWidget {
  const _AiChatBubble({required this.role, required this.child});

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
