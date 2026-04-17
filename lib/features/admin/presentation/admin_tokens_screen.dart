// lib/features/admin/presentation/admin_tokens_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_provider_params.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/error_formatter.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

class AdminTokensScreen extends ConsumerStatefulWidget {
  const AdminTokensScreen({super.key});

  @override
  ConsumerState<AdminTokensScreen> createState() => _AdminTokensScreenState();
}

class _AdminTokensScreenState extends ConsumerState<AdminTokensScreen> {
  int _currentPage = 0;
  bool? _filterConsumed;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final tokensAsync = ref.watch(
      adminTokensProvider(
        TokensProviderParams(
          consumed: _filterConsumed,
          page: _currentPage,
          size: 20,
        ),
      ),
    );

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: Text(l10n.translate('tokenManagement')),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
        actions: [
          IconButton(
            icon: const Icon(Icons.add),
            onPressed: () => _showGenerateTokenDialog(context, ref),
          ),
        ],
      ),
      body: Column(
        children: [
          // Filters
          Container(
            padding: const EdgeInsets.all(16),
            color: Colors.white,
            child: Row(
              children: [
                DropdownButton<bool?>(
                  value: _filterConsumed,
                  hint: Text(l10n.translate('filterByStatus')),
                  items: [
                    DropdownMenuItem(value: null, child: Text(l10n.all)),
                    DropdownMenuItem(value: false, child: Text(l10n.active)),
                    DropdownMenuItem(value: true, child: Text(l10n.translate('consumed'))),
                  ],
                  onChanged: (v) => setState(() {
                    _filterConsumed = v;
                    _currentPage = 0;
                  }),
                ),
                const Spacer(),
                ShifaPrimaryButton(
                  onPressed: () => ref.invalidate(adminTokensProvider(
                    TokensProviderParams(
                      consumed: _filterConsumed,
                      page: _currentPage,
                      size: 20,
                    ),
                  )),
                  icon: Icons.refresh,
                  label: l10n.refresh,
                ),
              ],
            ),
          ),
          // Tokens List
          Expanded(
            child: tokensAsync.when(
              loading: () => Center(
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    const CircularProgressIndicator(),
                    const SizedBox(height: 16),
                    Text(l10n.translate('loadingTokens')),
                  ],
                ),
              ),
              error: (e, stackTrace) {
                debugPrint('AdminTokensScreen: Error loading tokens: $e');
                debugPrint('AdminTokensScreen: Stack trace: $stackTrace');
                return Center(
                  child: Padding(
                    padding: const EdgeInsets.all(24.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        const Icon(Icons.error_outline, size: 48, color: AppColors.destructiveRed),
                        const SizedBox(height: 16),
                        Text(
                          l10n.translate('errorLoadingTokens'),
                          style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 8),
                        Expanded(
                          child: SingleChildScrollView(
                            child: Text(
                              '$e\n\nStack trace:\n$stackTrace',
                              textAlign: TextAlign.left,
                              style: TextStyle(
                                color: Colors.grey.shade600,
                                fontSize: 12,
                                fontFamily: 'monospace',
                              ),
                            ),
                          ),
                        ),
                        const SizedBox(height: 16),
                        ShifaPrimaryButton(
                          onPressed: () => ref.invalidate(adminTokensProvider(
                            TokensProviderParams(
                              consumed: _filterConsumed,
                              page: _currentPage,
                              size: 20,
                            ),
                          )),
                          label: l10n.retry,
                        ),
                      ],
                    ),
                  ),
                );
              },
              data: (data) {
                final tokens = data['content'] as List<AdminToken>;
                final totalPages = data['totalPages'] as int;
                return Column(
                  children: [
                    Expanded(
                      child: tokens.isEmpty
                          ? Center(child: Text(l10n.translate('noTokensFound')))
                          : ListView.builder(
                              padding: const EdgeInsets.all(16),
                              itemCount: tokens.length,
                              itemBuilder: (context, i) {
                                final token = tokens[i];
                                return _TokenCard(token: token, ref: ref);
                              },
                            ),
                    ),
                    // Pagination
                    if (totalPages > 1)
                      Container(
                        padding: const EdgeInsets.all(16),
                        child: Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            IconButton(
                              icon: const Icon(Icons.chevron_left),
                              onPressed: _currentPage > 0
                                  ? () => setState(() => _currentPage--)
                                  : null,
                            ),
                            Text('${l10n.translate('page')} ${_currentPage + 1} ${l10n.translate('of')} $totalPages'),
                            IconButton(
                              icon: const Icon(Icons.chevron_right),
                              onPressed: _currentPage < totalPages - 1
                                  ? () => setState(() => _currentPage++)
                                  : null,
                            ),
                          ],
                        ),
                      ),
                  ],
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  void _showGenerateTokenDialog(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final expiresCtrl = TextEditingController();
    final notesCtrl = TextEditingController();
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.translate('generateToken')),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: expiresCtrl,
              decoration: InputDecoration(
                labelText: l10n.translate('expiresInDaysOptional'),
                hintText: '30',
              ),
              keyboardType: TextInputType.number,
            ),
            const SizedBox(height: 16),
            TextField(
              controller: notesCtrl,
              decoration: InputDecoration(
                labelText: l10n.translate('notesOptional'),
              ),
              maxLines: 3,
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: Text(l10n.cancel),
          ),
          ShifaPrimaryButton(
            label: l10n.translate('generate'),
            onPressed: () async {
              try {
                final actions = ref.read(adminActionsProvider);
                final expires = expiresCtrl.text.isEmpty
                    ? null
                    : int.tryParse(expiresCtrl.text);
                await actions.generateToken(
                  expiresInDays: expires,
                  notes: notesCtrl.text.isEmpty ? null : notesCtrl.text,
                );
                if (context.mounted) {
                  Navigator.pop(context);
                  ref.invalidate(adminTokensProvider(
                    TokensProviderParams(
                      consumed: _filterConsumed,
                      page: _currentPage,
                      size: 20,
                    ),
                  ));
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text(l10n.translate('tokenGeneratedSuccessfully'))),
                  );
                }
              } catch (e) {
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(content: Text('${l10n.error}: $e')),
                  );
                }
              }
            },
          ),
        ],
      ),
    );
  }
}

class _TokenCard extends ConsumerWidget {
  final AdminToken token;
  final WidgetRef ref;

  const _TokenCard({required this.token, required this.ref});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            token.keyCode,
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                              fontFamily: 'monospace',
                            ),
                          ),
                          const SizedBox(width: 8),
                          Chip(
                            label: Text(
                              token.consumed
                                  ? l10n.translate('consumed')
                                  : token.isExpired
                                      ? l10n.expired
                                      : l10n.active,
                            ),
                            backgroundColor: token.consumed
                                ? Colors.grey
                                : token.isExpired
                                    ? Colors.orange
                                    : Colors.green,
                            labelStyle: const TextStyle(color: Colors.white, fontSize: 12),
                          ),
                        ],
                      ),
                      if (token.expiresAt != null)
                        Text(
                          '${l10n.translate('expires')}: ${DateTime.parse(token.expiresAt!).toLocal()}',
                          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                        ),
                      if (token.notes != null)
                        Text(
                          '${l10n.notes}: ${token.notes}',
                          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                        ),
                    ],
                  ),
                ),
                PopupMenuButton(
                  itemBuilder: (context) => [
                    if (!token.consumed)
                      PopupMenuItem(
                        child: Row(
                          children: [
                            const Icon(Icons.copy, size: 18),
                            const SizedBox(width: 8),
                            Text(l10n.translate('copyKey')),
                          ],
                        ),
                        onTap: () {
                          Clipboard.setData(ClipboardData(text: token.keyCode));
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(content: Text(l10n.translate('keyCopiedToClipboard'))),
                          );
                        },
                      ),
                    if (!token.consumed)
                      PopupMenuItem(
                        child: Row(
                          children: [
                            const Icon(Icons.block, size: 18, color: Colors.red),
                            const SizedBox(width: 8),
                            Text(l10n.translate('revoke'), style: const TextStyle(color: Colors.red)),
                          ],
                        ),
                        onTap: () async {
                          try {
                            final actions = ref.read(adminActionsProvider);
                            await actions.revokeToken(token.id);
                            ref.invalidate(adminTokensProvider(
                              TokensProviderParams(
                                consumed: null,
                                page: 0,
                                size: 20,
                              ),
                            ));
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text(l10n.tokenRevoked)),
                              );
                            }
                          } catch (e) {
                            if (context.mounted) {
                              ScaffoldMessenger.of(context).showSnackBar(
                                SnackBar(content: Text('${l10n.error}: $e')),
                              );
                            }
                          }
                        },
                      ),
                    PopupMenuItem(
                      child: Row(
                        children: [
                          const Icon(Icons.refresh, size: 18),
                          const SizedBox(width: 8),
                          Text(l10n.translate('regenerate')),
                        ],
                      ),
                      onTap: () async {
                        try {
                          final actions = ref.read(adminActionsProvider);
                          await actions.regenerateToken(token.id);
                          ref.invalidate(adminTokensProvider(
                            TokensProviderParams(
                              consumed: null,
                              page: 0,
                              size: 20,
                            ),
                          ));
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(content: Text(AppLocalizations.of(context)!.tokenRegenerated)),
                            );
                          }
                        } catch (e) {
                          if (context.mounted) {
                            ScaffoldMessenger.of(context).showSnackBar(
                              SnackBar(
                                content: Text(sanitizeErrorMessage(e, l10n)),
                                backgroundColor: AppColors.destructiveRed,
                              ),
                            );
                          }
                        }
                      },
                    ),
                  ],
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
