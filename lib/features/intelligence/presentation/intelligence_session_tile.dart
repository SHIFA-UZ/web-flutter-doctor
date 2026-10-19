import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/submit_on_enter.dart';
import 'package:shifa_doc_app_v1/features/intelligence/domain/intelligence_chat_models.dart';

String intelligenceRelativeTime(AppLocalizations l10n, DateTime at) {
  final d = DateTime.now().difference(at);
  if (d.inMinutes < 1) return l10n.timeJustNow;
  if (d.inMinutes < 60) return l10n.timeMinAgo(d.inMinutes);
  if (d.inHours < 24) {
    if (d.inHours <= 1) return l10n.translate('hourAgo');
    return l10n.translate('hoursAgo').replaceAll('%s', '${d.inHours}');
  }
  if (d.inDays < 2) return l10n.translate('yesterday');
  if (d.inDays < 7) {
    return l10n.translate('intelDaysAgo').replaceAll('{n}', '${d.inDays}');
  }
  final local = at.toLocal();
  return '${local.day}.${local.month}.${local.year}';
}

String intelligenceSessionLabel(
  AppLocalizations l10n,
  IntelligenceChatSession session,
) {
  if (!session.titleCustom &&
      session.displayTitle == IntelligenceChatSession.untitled) {
    return l10n.translate('newChat');
  }
  return session.displayTitle;
}

Future<String?> showIntelligenceRenameDialog(
  BuildContext context, {
  required String initialTitle,
}) {
  return showDialog<String>(
    context: context,
    builder: (ctx) => _RenameConversationDialog(initialTitle: initialTitle),
  );
}

Future<bool> confirmIntelligenceDelete(BuildContext context) async {
  final l10n = AppLocalizations.of(context)!;
  final ok = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(l10n.translate('intelDeleteConversation')),
      content: Text(l10n.translate('intelDeleteConversationConfirm')),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(ctx, false),
          child: Text(l10n.translate('cancel')),
        ),
        TextButton(
          onPressed: () => Navigator.pop(ctx, true),
          child: Text(l10n.translate('delete')),
        ),
      ],
    ),
  );
  return ok == true;
}

class IntelligenceSessionTile extends StatelessWidget {
  const IntelligenceSessionTile({
    super.key,
    required this.session,
    required this.selected,
    required this.onOpen,
    required this.onRename,
    required this.onDelete,
    this.enableMenu = true,
  });

  final IntelligenceChatSession session;
  final bool selected;
  final VoidCallback onOpen;
  final ValueChanged<String> onRename;
  final VoidCallback onDelete;
  final bool enableMenu;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final title = intelligenceSessionLabel(l10n, session);
    final preview = session.preview.trim();
    final showPreview =
        preview.isNotEmpty && preview.toLowerCase() != title.toLowerCase();
    final time = intelligenceRelativeTime(l10n, session.updatedAt);
    final patient = session.patientLinked;

    return Material(
      color: selected ? brand.withValues(alpha: 0.1) : Colors.transparent,
      child: InkWell(
        onTap: onOpen,
        hoverColor: brand.withValues(alpha: 0.06),
        child: Semantics(
          button: true,
          selected: selected,
          explicitChildNodes: true,
          label: patient ? '$title. ${l10n.translate('intelPatient')}' : title,
          child: Padding(
            padding: const EdgeInsets.fromLTRB(12, 10, 4, 10),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Icon(
                    Icons.chat_bubble_outline,
                    color: brand.withValues(alpha: 0.8),
                    size: 18,
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          if (patient) ...[
                            Tooltip(
                              message: l10n.translate(
                                'intelPatientConversation',
                              ),
                              child: Icon(
                                Icons.person_outline,
                                size: 16,
                                color: brand,
                              ),
                            ),
                            const SizedBox(width: 4),
                          ],
                          Expanded(
                            child: Text(
                              title,
                              maxLines: 1,
                              overflow: TextOverflow.ellipsis,
                              style: AppDesignSystem.body2(context).copyWith(
                                fontWeight: selected
                                    ? FontWeight.w600
                                    : FontWeight.w500,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            time,
                            style: AppDesignSystem.caption(
                              context,
                            ).copyWith(color: AppDesignSystem.textTertiary),
                          ),
                        ],
                      ),
                      if (showPreview) ...[
                        const SizedBox(height: 2),
                        Text(
                          preview,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: AppDesignSystem.caption(
                            context,
                          ).copyWith(color: AppDesignSystem.textSecondary),
                        ),
                      ],
                    ],
                  ),
                ),
                if (enableMenu)
                  PopupMenuButton<String>(
                    tooltip: l10n.translate('intelMoreActions'),
                    icon: Icon(
                      Icons.more_horiz,
                      size: 18,
                      color: AppDesignSystem.textTertiary,
                    ),
                    constraints: const BoxConstraints(
                      minWidth: 40,
                      minHeight: 40,
                    ),
                    onSelected: (value) async {
                      if (value == 'rename') {
                        final next = await showIntelligenceRenameDialog(
                          context,
                          initialTitle: title,
                        );
                        if (next == null) return;
                        final trimmed = next.trim();
                        if (trimmed.isEmpty || trimmed == title) return;
                        onRename(trimmed);
                      } else if (value == 'delete') {
                        if (!context.mounted) return;
                        final ok = await confirmIntelligenceDelete(context);
                        if (ok) onDelete();
                      }
                    },
                    itemBuilder: (context) => [
                      PopupMenuItem(
                        value: 'rename',
                        child: Text(l10n.translate('intelRename')),
                      ),
                      PopupMenuItem(
                        value: 'delete',
                        child: Text(l10n.translate('delete')),
                      ),
                    ],
                  )
                else
                  const SizedBox(width: 8),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _RenameConversationDialog extends StatefulWidget {
  const _RenameConversationDialog({required this.initialTitle});

  final String initialTitle;

  @override
  State<_RenameConversationDialog> createState() =>
      _RenameConversationDialogState();
}

class _RenameConversationDialogState extends State<_RenameConversationDialog> {
  late final TextEditingController _controller = TextEditingController(
    text: widget.initialTitle,
  );

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _save() => Navigator.pop(context, _controller.text);

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l10n.translate('intelRenameConversation')),
      content: TextField(
        controller: _controller,
        autofocus: true,
        textInputAction: TextInputAction.done,
        onSubmitted: submitOnEnter(_save),
        decoration: InputDecoration(
          hintText: l10n.translate('intelRenameHint'),
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.translate('cancel')),
        ),
        TextButton(onPressed: _save, child: Text(l10n.translate('save'))),
      ],
    );
  }
}
