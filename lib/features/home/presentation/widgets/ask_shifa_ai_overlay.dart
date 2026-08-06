import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_message.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/ai_response_text.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_speech_text_field.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/state/ask_shifa_ai_panel_provider.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_context_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

/// Floating AI chat widget anchored bottom-right — opened from sidebar or shell.
class AskShifaAiOverlay extends ConsumerStatefulWidget {
  const AskShifaAiOverlay({super.key});

  static void open(WidgetRef ref) {
    ref.read(askShifaAiPanelProvider.notifier).open();
  }

  @override
  ConsumerState<AskShifaAiOverlay> createState() => _AskShifaAiOverlayState();
}

class _AskShifaAiOverlayState extends ConsumerState<AskShifaAiOverlay> {
  final _aiController = TextEditingController();
  final _aiScroll = ScrollController();
  StreamSubscription<AiStreamEvent>? _aiSub;

  bool _aiLoading = false;
  String _streamingAiText = '';
  String? _aiError;
  List<AiMessage> _conversation = [];
  String? _selectedPatientId;
  bool _loadedConversation = false;

  static const String _conversationStoragePrefix =
      'ask_shifa_ai_overlay_conversation_v1';
  static const String _aiLangUzbekCyrillic = 'UZ_CYRL';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadConversation());
  }

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

  String _conversationStorageKey() {
    final profile = ref.read(profileAllProvider).valueOrNull?.profile;
    final doctorId =
        (profile?['id'] ?? profile?['doctorId'] ?? 'unknown').toString();
    return '$_conversationStoragePrefix:$doctorId:${_selectedPatientId ?? 'none'}';
  }

  Future<void> _loadConversation() async {
    if (_loadedConversation) return;
    _loadedConversation = true;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_conversationStorageKey());
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

  Future<void> _persistConversation() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _conversationStorageKey(),
      jsonEncode(_conversation.map((m) => m.toJson()).toList()),
    );
  }

  Future<void> _askAi() async {
    final question = _aiController.text.trim();
    if (question.isEmpty || _aiLoading) return;

    // Lock UI immediately so repeated sends cannot queue duplicates
    // while patient briefing (or the AI stream) is still loading.
    final next = [..._conversation, AiMessage(role: 'user', content: question)];
    setState(() {
      _conversation = next;
      _streamingAiText = '';
      _aiError = null;
      _aiLoading = true;
    });
    _aiController.clear();

    if (_selectedPatientId != null) {
      await ref
          .read(patientBriefingContextProvider.notifier)
          .ensureLoaded(_selectedPatientId!);
    }

    final aiApi = ref.read(aiApiProvider);
    _aiSub?.cancel();
    _aiSub = aiApi
        .streamAi(
          messages: next,
          question: question,
          language: _aiLanguageBackendCode(
            _aiUiLanguageFromLocale(ref.read(languageProvider).locale),
          ),
          patientId: _selectedPatientId == null
              ? null
              : int.tryParse(_selectedPatientId!),
        )
        .listen(
          (event) {
            if (event is AiTokenEvent) {
              setState(() => _streamingAiText += event.token);
              if (_aiScroll.hasClients) {
                _aiScroll.jumpTo(_aiScroll.position.maxScrollExtent + 40);
              }
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

  void _onPatientChanged(String? patientId) {
    setState(() {
      _selectedPatientId = patientId;
      _conversation = [];
      _streamingAiText = '';
      _aiError = null;
      _loadedConversation = false;
    });
    _loadConversation();
  }

  @override
  Widget build(BuildContext context) {
    final panelState = ref.watch(askShifaAiPanelProvider);
    if (panelState == AskShifaAiPanelState.closed) {
      return const SizedBox.shrink();
    }

    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final useSinglePane = PlatformLayout.useSinglePane(context);
    final screenSize = MediaQuery.sizeOf(context);

    if (panelState == AskShifaAiPanelState.minimized) {
      return _MinimizedBubble(
        brand: brand,
        tooltip: l10n.translate('sidebarAiTitle'),
        onTap: () => ref.read(askShifaAiPanelProvider.notifier).restore(),
      );
    }

    final panelWidth = useSinglePane
        ? screenSize.width - 24
        : Responsive.overlayWidth(context, 400).clamp(320.0, 420.0);
    final panelHeight = (screenSize.height * (useSinglePane ? 0.62 : 0.58))
        .clamp(useSinglePane ? 360.0 : 420.0, useSinglePane ? 520.0 : 580.0);

    return Shortcuts(
      shortcuts: {
        LogicalKeySet(LogicalKeyboardKey.escape): const _DismissIntent(),
      },
      child: Actions(
        actions: {
          _DismissIntent: CallbackAction<_DismissIntent>(
            onInvoke: (_) {
              ref.read(askShifaAiPanelProvider.notifier).close();
              return null;
            },
          ),
        },
        child: Focus(
          autofocus: true,
          child: AnimatedScale(
            scale: 1,
            duration: const Duration(milliseconds: 250),
            curve: Curves.easeOutCubic,
            child: Material(
              elevation: 12,
              borderRadius: BorderRadius.circular(16),
              clipBehavior: Clip.antiAlias,
              child: AnimatedContainer(
                duration: const Duration(milliseconds: 250),
                curve: Curves.easeOutCubic,
                width: panelWidth,
                height: panelHeight,
                decoration: AppDesignSystem.aiCardDecoration().copyWith(
                  borderRadius: BorderRadius.circular(16),
                ),
                child: Column(
                  children: [
                    _ChatHeader(
                      brand: brand,
                      title: l10n.translate('sidebarAiTitle'),
                      subtitle: l10n.translate('aiCommandCenterSubtitle'),
                      onMinimize: () =>
                          ref.read(askShifaAiPanelProvider.notifier).minimize(),
                      onClose: () =>
                          ref.read(askShifaAiPanelProvider.notifier).close(),
                    ),
                    Padding(
                      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                      child: DropdownButtonFormField<String>(
                        value: _selectedPatientId,
                        isExpanded: true,
                        decoration: InputDecoration(
                          hintText: l10n.allPatients,
                          isDense: true,
                          contentPadding: const EdgeInsets.symmetric(
                            horizontal: 12,
                            vertical: 10,
                          ),
                          border: OutlineInputBorder(
                            borderRadius: BorderRadius.circular(10),
                            borderSide:
                                BorderSide(color: AppDesignSystem.border),
                          ),
                        ),
                        items: [
                          DropdownMenuItem(
                            value: null,
                            child: Text(l10n.allPatients),
                          ),
                          ...ref.watch(patientsProvider).map(
                                (p) => DropdownMenuItem(
                                  value: p.id.toString(),
                                  child: Text(
                                    p.name,
                                    overflow: TextOverflow.ellipsis,
                                  ),
                                ),
                              ),
                        ],
                        onChanged: _onPatientChanged,
                      ),
                    ),
                    Expanded(
                      child: Container(
                        width: double.infinity,
                        margin: const EdgeInsets.symmetric(horizontal: 16),
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(
                          color: Colors.white,
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(color: AppDesignSystem.border),
                        ),
                        child: SingleChildScrollView(
                          controller: _aiScroll,
                          child: _buildConversation(context, l10n),
                        ),
                      ),
                    ),
                    Padding(
                      padding: const EdgeInsets.all(16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          DoctorSpeechTextField(
                            controller: _aiController,
                            maxLines: 3,
                            minLines: 2,
                            textCapitalization: TextCapitalization.sentences,
                            decoration: InputDecoration(
                              hintText: l10n.translate('askShifaAi'),
                              border: OutlineInputBorder(
                                borderRadius: BorderRadius.circular(12),
                              ),
                              filled: true,
                              fillColor: Colors.white,
                            ),
                          ),
                          const SizedBox(height: 10),
                          ShifaPrimaryButton(
                            label: l10n.translate('askShifaAi'),
                            icon: Icons.send_rounded,
                            onPressed: _aiLoading ? null : _askAi,
                            width: ButtonWidth.fill,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildConversation(BuildContext context, AppLocalizations l10n) {
    if (_aiError != null) {
      return Text(
        _aiError!,
        style: TextStyle(color: Theme.of(context).colorScheme.error),
      );
    }

    final visible = _conversation.where((m) => m.role != 'system').toList();
    if (visible.isEmpty && _streamingAiText.isEmpty) {
      return Text(
        l10n.translate('aiWillRespondHere'),
        style: AppDesignSystem.body2(context),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final msg in visible)
          _ChatBubble(
            isUser: msg.role == 'user',
            child: AiResponseText(
              text: msg.content,
              style: const TextStyle(fontSize: 13),
              maxWidth: 360,
            ),
          ),
        if (_streamingAiText.isNotEmpty)
          _ChatBubble(
            isUser: false,
            child: AiResponseText(
              text: _streamingAiText,
              style: const TextStyle(fontSize: 13),
              maxWidth: 360,
            ),
          ),
      ],
    );
  }
}

class _ChatHeader extends StatelessWidget {
  const _ChatHeader({
    required this.brand,
    required this.title,
    required this.subtitle,
    required this.onMinimize,
    required this.onClose,
  });

  final Color brand;
  final String title;
  final String subtitle;
  final VoidCallback onMinimize;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(16, 12, 4, 12),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [brand, AppDesignSystem.primaryAi],
        ),
        borderRadius: const BorderRadius.vertical(top: Radius.circular(16)),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: Colors.white.withValues(alpha: 0.2),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(
              Icons.auto_awesome,
              color: Colors.white,
              size: 18,
            ),
          ),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  title,
                  style: AppDesignSystem.h2(context).copyWith(
                    color: Colors.white,
                    fontSize: 15,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
                Text(
                  subtitle,
                  style: AppDesignSystem.body2(context).copyWith(
                    color: Colors.white.withValues(alpha: 0.9),
                    fontSize: 12,
                  ),
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
          IconButton(
            onPressed: onMinimize,
            icon: const Icon(Icons.remove, color: Colors.white),
            visualDensity: VisualDensity.compact,
          ),
          IconButton(
            onPressed: onClose,
            icon: const Icon(Icons.close, color: Colors.white),
            tooltip: MaterialLocalizations.of(context).closeButtonTooltip,
            visualDensity: VisualDensity.compact,
          ),
        ],
      ),
    );
  }
}

class _MinimizedBubble extends StatelessWidget {
  const _MinimizedBubble({
    required this.brand,
    required this.tooltip,
    required this.onTap,
  });

  final Color brand;
  final String tooltip;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: tooltip,
      child: Material(
        elevation: 8,
        shape: const CircleBorder(),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: Ink(
            width: 56,
            height: 56,
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: [brand, AppDesignSystem.primaryAi],
              ),
              shape: BoxShape.circle,
            ),
            child: const Icon(
              Icons.auto_awesome,
              color: Colors.white,
              size: 26,
            ),
          ),
        ),
      ),
    );
  }
}

class _ChatBubble extends StatelessWidget {
  const _ChatBubble({required this.isUser, required this.child});

  final bool isUser;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
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

class _DismissIntent extends Intent {
  const _DismissIntent();
}
