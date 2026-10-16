import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html)
        'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart'
    as file_download;
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_voice_session.dart';
import 'package:shifa_doc_app_v1/features/intelligence/application/intelligence_chat_store.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';

/// Clinical Intelligence workspace: conversation-first, history on demand.
class IntelligenceScreen extends ConsumerStatefulWidget {
  const IntelligenceScreen({super.key});

  @override
  ConsumerState<IntelligenceScreen> createState() => _IntelligenceScreenState();
}

class _IntelligenceScreenState extends ConsumerState<IntelligenceScreen> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(intelligenceChatProvider.notifier).ensureLoaded();
    });
  }

  Future<void> _downloadConversation() async {
    final l10n = AppLocalizations.of(context)!;
    final active = ref.read(intelligenceChatProvider).active;
    if (active == null) return;
    if (active.messages.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.translate('intelDownloadEmpty')),
          behavior: SnackBarBehavior.floating,
        ),
      );
      return;
    }

    final stamp = DateTime.now()
        .toIso8601String()
        .replaceAll(':', '-')
        .replaceAll('.', '-');
    final safeId = active.id.replaceAll(RegExp(r'[^a-zA-Z0-9_-]'), '_');
    final filename = 'shifa-intelligence-$safeId-$stamp.txt';
    final body = _formatConversationExport(active);
    try {
      await file_download.downloadBytes(
        Uint8List.fromList(utf8.encode(body)),
        filename: filename,
        mimeType: 'text/plain;charset=utf-8',
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.translate('exportStarted')),
          behavior: SnackBarBehavior.floating,
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.translate('intelDownloadFailed')}: $e'),
          behavior: SnackBarBehavior.floating,
        ),
      );
    }
  }

  void _openHistory() {
    final narrow = PlatformLayout.useSinglePane(context);
    if (narrow) {
      showModalBottomSheet<void>(
        context: context,
        isScrollControlled: true,
        backgroundColor: Colors.white,
        shape: const RoundedRectangleBorder(
          borderRadius: BorderRadius.vertical(top: Radius.circular(16)),
        ),
        builder: (ctx) {
          return Consumer(
            builder: (context, sheetRef, _) {
              final state = sheetRef.watch(intelligenceChatProvider);
              return SizedBox(
                height: MediaQuery.of(ctx).size.height * 0.72,
                child: _HistoryPane(
                  sessions: state.sessions,
                  activeId: state.activeSessionId,
                  onNew: () {
                    sheetRef.read(intelligenceChatProvider.notifier).createChat();
                    Navigator.pop(ctx);
                  },
                  onSelect: (id) {
                    sheetRef
                        .read(intelligenceChatProvider.notifier)
                        .selectChat(id);
                    Navigator.pop(ctx);
                  },
                  onDelete: (id) => sheetRef
                      .read(intelligenceChatProvider.notifier)
                      .deleteChat(id),
                ),
              );
            },
          );
        },
      );
      return;
    }
    _scaffoldKey.currentState?.openDrawer();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final chatState = ref.watch(intelligenceChatProvider);
    final active = chatState.active;
    final narrow = PlatformLayout.useSinglePane(context);

    final historyPane = _HistoryPane(
      sessions: chatState.sessions,
      activeId: chatState.activeSessionId,
      onNew: () => ref.read(intelligenceChatProvider.notifier).createChat(),
      onSelect: (id) {
        ref.read(intelligenceChatProvider.notifier).selectChat(id);
        if (!narrow) _scaffoldKey.currentState?.closeDrawer();
      },
      onDelete: (id) =>
          ref.read(intelligenceChatProvider.notifier).deleteChat(id),
    );

    final body = !chatState.loaded || active == null
        ? const Center(child: CircularProgressIndicator())
        : HomeVoiceSession(
            key: ValueKey(active.id),
            embedded: true,
            hideChrome: true,
            initialMessages: active.messages,
            recentSessions: chatState.sessions,
            onOpenSession: (id) =>
                ref.read(intelligenceChatProvider.notifier).selectChat(id),
            onMessagesChanged: (messages) {
              ref
                  .read(intelligenceChatProvider.notifier)
                  .replaceMessages(active.id, messages);
            },
          );

    return Scaffold(
      key: _scaffoldKey,
      backgroundColor: AppDesignSystem.backgroundSecondary,
      drawer: narrow
          ? null
          : Drawer(
              width: 300,
              backgroundColor: Colors.white,
              child: SafeArea(child: historyPane),
            ),
      appBar: AppBar(
        backgroundColor: Colors.white,
        foregroundColor: AppDesignSystem.textPrimary,
        elevation: 0,
        scrolledUnderElevation: 0.5,
        leading: IconButton(
          tooltip: l10n.translate('intelHowCanIHelp'),
          icon: const Icon(Icons.arrow_back),
          onPressed: () =>
              ref.read(intelligenceChatProvider.notifier).goToWelcome(),
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.translate('shifaIntelligence'),
              style: const TextStyle(
                fontWeight: FontWeight.w700,
                fontSize: 16,
              ),
            ),
            Text(
              l10n.translate('intelligenceEmptyHint'),
              style: TextStyle(
                fontSize: 11,
                color: AppDesignSystem.textSecondary,
                fontWeight: FontWeight.w400,
              ),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: l10n.translate('intelDownloadChat'),
            icon: const Icon(Icons.download_outlined),
            onPressed: active == null || active.messages.isEmpty
                ? null
                : () => unawaited(_downloadConversation()),
          ),
          IconButton(
            tooltip: l10n.translate('chatHistory'),
            icon: const Icon(Icons.history),
            onPressed: _openHistory,
          ),
          IconButton(
            tooltip: l10n.translate('newChat'),
            icon: const Icon(Icons.add_comment_outlined),
            onPressed: () =>
                ref.read(intelligenceChatProvider.notifier).createChat(),
          ),
        ],
      ),
      body: body,
    );
  }
}

String _formatConversationExport(IntelligenceChatSession session) {
  final buf = StringBuffer();
  buf.writeln('Shifa Intelligence conversation export');
  buf.writeln('=====================================');
  buf.writeln('sessionId: ${session.id}');
  buf.writeln('title: ${session.displayTitle}');
  buf.writeln('createdAt: ${session.createdAt.toIso8601String()}');
  buf.writeln('updatedAt: ${session.updatedAt.toIso8601String()}');
  buf.writeln('messageCount: ${session.messages.length}');
  buf.writeln('exportedAt: ${DateTime.now().toIso8601String()}');
  buf.writeln();

  for (var i = 0; i < session.messages.length; i++) {
    final m = session.messages[i];
    final role = m.role.toUpperCase();
    buf.writeln('--- [$i] $role @ ${m.at.toIso8601String()} ---');
    buf.writeln(m.content.trim().isEmpty ? '(empty)' : m.content);
    if (m.toolName != null && m.toolName!.trim().isNotEmpty) {
      buf.writeln();
      buf.writeln('toolName: ${m.toolName}');
    }
    if (m.cardPayload != null && m.cardPayload!.isNotEmpty) {
      buf.writeln();
      buf.writeln('cardPayload:');
      buf.writeln(
        const JsonEncoder.withIndent('  ').convert(m.cardPayload),
      );
    }
    buf.writeln();
  }

  buf.writeln('--- JSON (machine-readable) ---');
  buf.writeln(
    const JsonEncoder.withIndent('  ').convert(session.toJson()),
  );
  return buf.toString();
}

class _HistoryPane extends StatelessWidget {
  const _HistoryPane({
    required this.sessions,
    required this.activeId,
    required this.onNew,
    required this.onSelect,
    required this.onDelete,
  });

  final List<IntelligenceChatSession> sessions;
  final String? activeId;
  final VoidCallback onNew;
  final ValueChanged<String> onSelect;
  final ValueChanged<String> onDelete;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    return ColoredBox(
      color: Colors.white,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 12, 8),
            child: Row(
              children: [
                Expanded(
                  child: Text(
                    l10n.translate('chatHistory'),
                    style: AppDesignSystem.body1(context).copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                ),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: OutlinedButton.icon(
              onPressed: onNew,
              icon: const Icon(Icons.add, size: 18),
              label: Text(l10n.translate('newChat')),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: ListView.builder(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              itemCount: sessions.length,
              itemBuilder: (context, index) {
                final session = sessions[index];
                final selected = session.id == activeId;
                return Padding(
                  padding: const EdgeInsets.only(bottom: 4),
                  child: Material(
                    color: selected
                        ? brand.withValues(alpha: 0.1)
                        : Colors.transparent,
                    borderRadius: BorderRadius.circular(10),
                    child: InkWell(
                      borderRadius: BorderRadius.circular(10),
                      onTap: () => onSelect(session.id),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        child: Row(
                          children: [
                            Icon(
                              Icons.chat_bubble_outline,
                              color: brand.withValues(alpha: 0.75),
                              size: 16,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                                child: Text(
                                  session.displayTitle,
                                  style: AppDesignSystem.body2(context).copyWith(
                                    fontWeight: selected
                                        ? FontWeight.w600
                                        : FontWeight.w400,
                                  ),
                                ),
                            ),
                            IconButton(
                              visualDensity: VisualDensity.compact,
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(
                                minWidth: 28,
                                minHeight: 28,
                              ),
                              tooltip: l10n.translate('delete'),
                              onPressed: () => onDelete(session.id),
                              icon: Icon(
                                Icons.close,
                                size: 16,
                                color: AppDesignSystem.textTertiary,
                              ),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
