// lib/features/admin/presentation/admin_audit_logs_screen.dart
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_providers.dart';
import 'package:shifa_doc_app_v1/state/admin/admin_provider_params.dart';
import 'package:shifa_doc_app_v1/features/admin/domain/admin_models.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

class AdminAuditLogsScreen extends ConsumerStatefulWidget {
  const AdminAuditLogsScreen({super.key});

  @override
  ConsumerState<AdminAuditLogsScreen> createState() => _AdminAuditLogsScreenState();
}

class _AdminAuditLogsScreenState extends ConsumerState<AdminAuditLogsScreen> {
  int _currentPage = 0;
  String _search = '';
  String _sortBy = 'createdAt';
  bool _sortAsc = false;
  final _searchCtrl = TextEditingController();

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  // Format camelCase or UPPER_SNAKE_CASE to Title Case
  String _formatText(String text) {
    if (text.isEmpty) return text;

    // Handle UPPER_SNAKE_CASE (e.g., "USER_CREATED" -> "User Created")
    if (text.contains('_')) {
      return text
          .split('_')
          .map((word) => word.isEmpty
              ? ''
              : word[0].toUpperCase() + word.substring(1).toLowerCase())
          .join(' ');
    }

    // Handle camelCase (e.g., "userCreated" -> "User Created")
    final words = <String>[];
    final buffer = StringBuffer();

    for (int i = 0; i < text.length; i++) {
      final char = text[i];
      if (i > 0 && char == char.toUpperCase() && char != char.toLowerCase()) {
        words.add(buffer.toString());
        buffer.clear();
      }
      buffer.write(char);
    }
    if (buffer.isNotEmpty) words.add(buffer.toString());

    return words
        .map((word) => word.isEmpty
            ? ''
            : word[0].toUpperCase() + word.substring(1).toLowerCase())
        .join(' ');
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final logsAsync = ref.watch(
      auditLogsProvider(
        AuditLogsProviderParams(
          page: _currentPage,
          size: 50,
        ),
      ),
    );

    return Scaffold(
      backgroundColor: Colors.grey.shade50,
      appBar: AppBar(
        title: Text(l10n.translate('auditLogs')),
        backgroundColor: Colors.white,
        elevation: 0,
        foregroundColor: Colors.black87,
      ),
      body: Column(
        children: [
          Container(
            color: Colors.white,
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _searchCtrl,
                    decoration: const InputDecoration(
                      hintText: 'Search action/entity/admin...',
                      prefixIcon: Icon(Icons.search),
                      border: OutlineInputBorder(),
                      isDense: true,
                    ),
                    onChanged: (v) => setState(() => _search = v.trim().toLowerCase()),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  onPressed: () {
                    _searchCtrl.clear();
                    setState(() => _search = '');
                  },
                  icon: const Icon(Icons.clear),
                ),
              ],
            ),
          ),
          Expanded(
            child: logsAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.error}: $e')),
        data: (data) {
          var logs = List<AuditLogEntry>.from(data['content'] as List<AuditLogEntry>);
          if (_search.isNotEmpty) {
            logs = logs.where((log) {
              final hay = '${log.actionType} ${log.entityType} ${log.adminUserId} ${log.entityId ?? ''}'.toLowerCase();
              return hay.contains(_search);
            }).toList();
          }
          logs.sort((a, b) {
            int cmp;
            switch (_sortBy) {
              case 'actionType':
                cmp = a.actionType.compareTo(b.actionType);
                break;
              case 'entityType':
                cmp = a.entityType.compareTo(b.entityType);
                break;
              case 'adminUserId':
                cmp = a.adminUserId.compareTo(b.adminUserId);
                break;
              case 'createdAt':
              default:
                cmp = a.createdAt.compareTo(b.createdAt);
                break;
            }
            return _sortAsc ? cmp : -cmp;
          });
          final totalPages = data['totalPages'] as int;
          return Column(
            children: [
              Expanded(
                child: logs.isEmpty
                    ? Center(child: Text(l10n.translate('noLogsFound')))
                    : SingleChildScrollView(
                        padding: const EdgeInsets.all(16),
                        scrollDirection: Axis.horizontal,
                        child: DataTable(
                          sortAscending: _sortAsc,
                          sortColumnIndex: _sortBy == 'actionType'
                              ? 0
                              : _sortBy == 'entityType'
                                  ? 1
                                  : _sortBy == 'adminUserId'
                                      ? 2
                                      : 3,
                          columns: [
                            DataColumn(
                              label: const Text('Action'),
                              onSort: (_, __) => setState(() {
                                _sortBy = 'actionType';
                                _sortAsc = !_sortAsc;
                              }),
                            ),
                            DataColumn(
                              label: const Text('Entity'),
                              onSort: (_, __) => setState(() {
                                _sortBy = 'entityType';
                                _sortAsc = !_sortAsc;
                              }),
                            ),
                            DataColumn(
                              label: const Text('Admin ID'),
                              numeric: true,
                              onSort: (_, __) => setState(() {
                                _sortBy = 'adminUserId';
                                _sortAsc = !_sortAsc;
                              }),
                            ),
                            DataColumn(
                              label: const Text('Created'),
                              onSort: (_, __) => setState(() {
                                _sortBy = 'createdAt';
                                _sortAsc = !_sortAsc;
                              }),
                            ),
                            const DataColumn(label: Text('Details')),
                          ],
                          rows: logs
                              .map(
                                (log) => DataRow(
                                  cells: [
                                    DataCell(Text(_formatText(log.actionType))),
                                    DataCell(Text('${_formatText(log.entityType)} #${log.entityId ?? "N/A"}')),
                                    DataCell(Text('${log.adminUserId}')),
                                    DataCell(Text(DateTime.parse(log.createdAt).toLocal().toString())),
                                    DataCell(
                                      IconButton(
                                        icon: const Icon(Icons.visibility_outlined),
                                        onPressed: () => _showLogDetails(context, log),
                                      ),
                                    ),
                                  ],
                                ),
                              )
                              .toList(),
                        ),
                      ),
              ),
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

  void _showLogDetails(BuildContext context, AuditLogEntry log) {
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Audit Details'),
        content: SizedBox(
          width: 520,
          child: SingleChildScrollView(
            child: SelectableText(
              'Action: ${log.actionType}\n'
              'Entity: ${log.entityType}\n'
              'Entity ID: ${log.entityId}\n'
              'Admin User ID: ${log.adminUserId}\n'
              'IP: ${log.ipAddress ?? "-"}\n'
              'User Agent: ${log.userAgent ?? "-"}\n'
              'Created: ${DateTime.parse(log.createdAt).toLocal()}\n'
              'Details: ${log.details ?? {}}',
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}
