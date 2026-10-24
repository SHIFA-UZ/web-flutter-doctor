import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart'
    as file_download;
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_voice_session.dart';
import 'package:shifa_doc_app_v1/features/intelligence/application/intelligence_chat_store.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_session_tile.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/workspace/workspace_mode_provider.dart';

/// Clinical Intelligence workspace: conversation-first, history on demand.
class IntelligenceScreen extends ConsumerStatefulWidget {
  const IntelligenceScreen({super.key});

  @override
  ConsumerState<IntelligenceScreen> createState() => _IntelligenceScreenState();
}

class _IntelligenceScreenState extends ConsumerState<IntelligenceScreen> {
  final _scaffoldKey = GlobalKey<ScaffoldState>();
  bool? _welcomeVisible;

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

  Future<void> _renameActive() async {
    final active = ref.read(intelligenceChatProvider).active;
    if (active == null || active.messages.isEmpty) return;
    final l10n = AppLocalizations.of(context)!;
    final current = intelligenceSessionLabel(l10n, active);
    final next = await showIntelligenceRenameDialog(
      context,
      initialTitle: current,
    );
    if (next == null || !mounted) return;
    final trimmed = next.trim();
    if (trimmed.isEmpty || trimmed == current) return;
    await ref
        .read(intelligenceChatProvider.notifier)
        .renameChat(active.id, trimmed);
  }

  Future<void> _deleteActive() async {
    final active = ref.read(intelligenceChatProvider).active;
    if (active == null || active.messages.isEmpty) return;
    final ok = await confirmIntelligenceDelete(context);
    if (!ok || !mounted) return;
    await ref.read(intelligenceChatProvider.notifier).deleteChat(active.id);
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
                    sheetRef
                        .read(intelligenceChatProvider.notifier)
                        .createChat();
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
                  onRename: (id, title) => sheetRef
                      .read(intelligenceChatProvider.notifier)
                      .renameChat(id, title),
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
    ref.watch(languageProvider);
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
      onRename: (id, title) =>
          ref.read(intelligenceChatProvider.notifier).renameChat(id, title),
    );

    final persistedThread = active != null && active.messages.isNotEmpty;
    final conversationOpen = _welcomeVisible == false || persistedThread;

    final body = !chatState.loaded || active == null
        ? const Center(child: CircularProgressIndicator())
        : HomeVoiceSession(
            key: ValueKey(active.id),
            embedded: true,
            hideChrome: true,
            conversationId: active.id,
            initialMessages: active.messages,
            recentSessions: chatState.sessions,
            onOpenSession: (id) =>
                ref.read(intelligenceChatProvider.notifier).selectChat(id),
            onRenameSession: (id, title) => ref
                .read(intelligenceChatProvider.notifier)
                .renameChat(id, title),
            onDeleteSession: (id) =>
                ref.read(intelligenceChatProvider.notifier).deleteChat(id),
            onWelcomeVisible: (visible) {
              if (_welcomeVisible == visible) return;
              setState(() => _welcomeVisible = visible);
            },
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
          tooltip: conversationOpen
              ? l10n.translate('intelBackToWelcome')
              : l10n.translate('intelBackToClinic'),
          icon: const Icon(Icons.arrow_back),
          onPressed: () {
            if (!conversationOpen) {
              ref.read(workspaceModeProvider.notifier).showClinic();
              unawaited(invalidateAppointmentRelatedProviders(ref));
              return;
            }
            ref.read(intelligenceChatProvider.notifier).goToWelcome();
          },
        ),
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.translate('shifaIntelligence'),
              style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16),
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
            tooltip: l10n.translate('intelHistory'),
            icon: const Icon(Icons.history),
            onPressed: _openHistory,
          ),
          IconButton(
            tooltip: l10n.translate('intelNewConversation'),
            icon: const Icon(Icons.add_comment_outlined),
            onPressed: () =>
                ref.read(intelligenceChatProvider.notifier).createChat(),
          ),
          if (conversationOpen)
            PopupMenuButton<String>(
              tooltip: l10n.translate('intelMoreActions'),
              icon: const Icon(Icons.more_vert),
              onSelected: (value) {
                switch (value) {
                  case 'download':
                    unawaited(_downloadConversation());
                  case 'rename':
                    unawaited(_renameActive());
                  case 'delete':
                    unawaited(_deleteActive());
                }
              },
              itemBuilder: (context) => [
                PopupMenuItem(
                  value: 'download',
                  child: Text(l10n.translate('intelDownloadChat')),
                ),
                PopupMenuItem(
                  value: 'rename',
                  child: Text(l10n.translate('intelRename')),
                ),
                PopupMenuItem(
                  value: 'delete',
                  child: Text(l10n.translate('delete')),
                ),
              ],
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
      buf.writeln(const JsonEncoder.withIndent('  ').convert(m.cardPayload));
    }
    buf.writeln();
  }

  buf.writeln('--- JSON (machine-readable) ---');
  buf.writeln(const JsonEncoder.withIndent('  ').convert(session.toJson()));
  return buf.toString();
}

class _HistoryPane extends StatefulWidget {
  const _HistoryPane({
    required this.sessions,
    required this.activeId,
    required this.onNew,
    required this.onSelect,
    required this.onDelete,
    required this.onRename,
  });

  final List<IntelligenceChatSession> sessions;
  final String? activeId;
  final VoidCallback onNew;
  final ValueChanged<String> onSelect;
  final ValueChanged<String> onDelete;
  final void Function(String id, String title) onRename;

  @override
  State<_HistoryPane> createState() => _HistoryPaneState();
}

class _HistoryPaneState extends State<_HistoryPane> {
  final _search = TextEditingController();

  @override
  void dispose() {
    _search.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final query = _search.text;
    final visible = widget.sessions
        .where((s) => s.matchesQuery(query))
        .toList();

    return ColoredBox(
      color: Colors.white,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 12, 8),
            child: Text(
              l10n.translate('intelHistory'),
              style: AppDesignSystem.body1(
                context,
              ).copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: OutlinedButton.icon(
              onPressed: widget.onNew,
              icon: const Icon(Icons.add, size: 18),
              label: Text(l10n.translate('intelNewConversation')),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 12, 8),
            child: TextField(
              controller: _search,
              textInputAction: TextInputAction.search,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                isDense: true,
                hintText: l10n.translate('intelSearchConversations'),
                prefixIcon: const Icon(Icons.search, size: 20),
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ),
          ),
          Expanded(
            child: visible.isEmpty
                ? Padding(
                    padding: const EdgeInsets.all(20),
                    child: Text(
                      query.trim().isEmpty
                          ? l10n.translate('intelNoConversations')
                          : l10n.translate('intelNoSearchResults'),
                      style: AppDesignSystem.body2(
                        context,
                      ).copyWith(color: AppDesignSystem.textSecondary),
                    ),
                  )
                : ListView.builder(
                    padding: const EdgeInsets.fromLTRB(8, 0, 8, 12),
                    itemCount: visible.length,
                    itemBuilder: (context, index) {
                      final session = visible[index];
                      return Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: DecoratedBox(
                          decoration: BoxDecoration(
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: AppDesignSystem.border),
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(10),
                            child: IntelligenceSessionTile(
                              session: session,
                              selected: session.id == widget.activeId,
                              onOpen: () => widget.onSelect(session.id),
                              onRename: (title) =>
                                  widget.onRename(session.id, title),
                              onDelete: () => widget.onDelete(session.id),
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
