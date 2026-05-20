import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

class ClinicFinanceTab extends ConsumerStatefulWidget {
  final int clinicId;

  const ClinicFinanceTab({super.key, required this.clinicId});

  @override
  ConsumerState<ClinicFinanceTab> createState() => _ClinicFinanceTabState();
}

class _ClinicFinanceTabState extends ConsumerState<ClinicFinanceTab> {
  int _selectedSubTab = 0;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Column(
      children: [
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 8),
          child: Row(
            children: List.generate(6, (i) {
              final tabs = [
                l10n.translate('clinicFinanceDashboard'),
                l10n.translate('clinicFinanceByAppointment'),
                l10n.translate('clinicFinanceInstallments'),
                l10n.translate('clinicFinanceDoctorEarnings'),
                l10n.translate('clinicFinanceRecords'),
                l10n.translate('clinicFinancePayments'),
              ];
              final selected = _selectedSubTab == i;
              return Padding(
                padding: const EdgeInsets.only(right: 6),
                child: ChoiceChip(
                  label: Text(tabs[i], overflow: TextOverflow.ellipsis),
                  selected: selected,
                  onSelected: (_) => setState(() => _selectedSubTab = i),
                  selectedColor: AppColors.primaryTeal.withValues(alpha: 0.15),
                  labelStyle: TextStyle(
                    color: selected ? AppColors.primaryTeal : Colors.grey.shade700,
                    fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                  ),
                ),
              );
            }),
          ),
        ),
        const Divider(height: 1),
        Expanded(
          child: IndexedStack(
            index: _selectedSubTab,
            children: [
              _DashboardView(clinicId: widget.clinicId),
              _AppointmentLedgerView(clinicId: widget.clinicId),
              _InstallmentsFinanceView(clinicId: widget.clinicId),
              _DoctorEarningsPane(clinicId: widget.clinicId),
              _RecordsView(clinicId: widget.clinicId),
              _PaymentsView(clinicId: widget.clinicId),
            ],
          ),
        ),
      ],
    );
  }
}

class _DashboardView extends ConsumerWidget {
  final int clinicId;
  const _DashboardView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final dashboardAsync = ref.watch(clinicFinanceDashboardProvider(clinicId));

    return dashboardAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (stats) => SingleChildScrollView(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              l10n.translate('clinicFinanceDashboard'),
              style: const TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 16),
            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: [
                _KpiCard(
                  title: l10n.translate('clinicFinanceTotalRevenue'),
                  value: _formatMoney(stats.totalRevenueMinor, stats.currency),
                  color: Colors.green,
                ),
                _KpiCard(
                  title: l10n.translate('clinicFinanceOutstanding'),
                  value: _formatMoney(stats.outstandingMinor, stats.currency),
                  color: Colors.orange,
                ),
                _KpiCard(
                  title: l10n.translate('clinicFinanceOverdueCount'),
                  value: stats.overdueCount.toString(),
                  color: Colors.red,
                ),
                _KpiCard(
                  title: l10n.translate('clinicFinanceCollectionRate'),
                  value: '${(stats.collectionRate * 100).toStringAsFixed(1)}%',
                  color: AppColors.primaryTeal,
                ),
              ],
            ),
            if (stats.doctorEarningsTop.isNotEmpty) ...[
              const SizedBox(height: 24),
              Text(
                l10n.translate('clinicFinanceDoctorEarnings'),
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600),
              ),
              const SizedBox(height: 8),
              ...stats.doctorEarningsTop.map((d) {
                return ListTile(
                  dense: true,
                  title: Text('#${d.doctorProfileId}'),
                  subtitle: Text(l10n.translate('clinicFinanceDoctorEarningsHint')),
                  trailing: Text(
                    '${_formatMoney(d.grossMinor, stats.currency)} / '
                    '${_formatMoney(d.collectedMinor, stats.currency)}',
                  ),
                );
              }),
            ],
          ],
        ),
      ),
    );
  }
}

class _AppointmentLedgerView extends ConsumerWidget {
  final int clinicId;
  const _AppointmentLedgerView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicAppointmentLedgerProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (page) {
        final content = page['content'] as List<dynamic>? ?? [];
        if (content.isEmpty) {
          return Center(child: Text(l10n.translate('clinicFinanceNoLedgerRows')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: content.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (ctx, i) {
            final row = AppointmentLedgerRowDto.fromJson(
              Map<String, dynamic>.from(content[i] as Map),
            );
            return ExpansionTile(
              title: Text('${row.patientName} · ${_formatDate(row.startAt)}'),
              subtitle: Text(
                '${row.planSimplePaymentStatus} · ${_formatMoney(row.visitTotalMinor, row.currency)}',
              ),
              children: [
                ListTile(
                  dense: true,
                  title: Text('${l10n.translate('clinicFinanceVisitServices')} · '
                      '${row.doctorName}'),
                ),
                ...row.services.map(
                  (s) => ListTile(
                    dense: true,
                    title: Text(s.title),
                    trailing: Text(_formatMoney(s.lineTotalMinor, row.currency)),
                  ),
                ),
              ],
            );
          },
        );
      },
    );
  }
}

class _InstallmentsFinanceView extends ConsumerStatefulWidget {
  final int clinicId;
  const _InstallmentsFinanceView({required this.clinicId});

  @override
  ConsumerState<_InstallmentsFinanceView> createState() =>
      _InstallmentsFinanceViewState();
}

class _InstallmentsFinanceViewState extends ConsumerState<_InstallmentsFinanceView> {
  String _filter = 'all';

  /// Free-text search applied client-side over patient name + plan title.
  String _search = '';
  final TextEditingController _searchCtrl = TextEditingController();

  /// Inclusive date range filter applied client-side over [InstallmentItemListRow.dueDate].
  DateTime? _dueFrom;
  DateTime? _dueTo;

  /// Sort state for the DataTable (default: ascending by due date).
  int _sortColumnIndex = 3;
  bool _sortAscending = true;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _invalidateInstallments() {
    ref.invalidate(clinicInstallmentItemsProvider((widget.clinicId, _filter)));
    ref.invalidate(clinicOverdueProvider(widget.clinicId));
    ref.invalidate(clinicFinanceDashboardProvider(widget.clinicId));
  }

  /// Client-side filter + sort. The backend already applies the status filter
  /// (`all/pending/overdue/paid`); everything else here (search, date range,
  /// sort) is in-memory so the table feels instant.
  List<InstallmentItemListRow> _applyClientFilters(
    List<InstallmentItemListRow> rows,
  ) {
    Iterable<InstallmentItemListRow> out = rows;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((r) {
        final patient = r.patientName.toLowerCase();
        final plan = (r.treatmentPlanTitle ?? '').toLowerCase();
        final planFallback = 'plan #${r.treatmentPlanId}'.toLowerCase();
        return patient.contains(s) ||
            plan.contains(s) ||
            planFallback.contains(s);
      });
    }
    if (_dueFrom != null || _dueTo != null) {
      out = out.where((r) {
        DateTime? d;
        try {
          d = DateTime.parse(r.dueDate);
        } catch (_) {
          return false;
        }
        if (_dueFrom != null && d.isBefore(_dueFrom!)) return false;
        if (_dueTo != null && d.isAfter(_dueTo!)) return false;
        return true;
      });
    }
    final list = out.toList();
    int cmp(InstallmentItemListRow a, InstallmentItemListRow b) {
      int c;
      switch (_sortColumnIndex) {
        case 0:
          c = a.sequenceNumber.compareTo(b.sequenceNumber);
          break;
        case 1:
          c = a.patientName.toLowerCase().compareTo(b.patientName.toLowerCase());
          break;
        case 2:
          c = (a.treatmentPlanTitle ?? '')
              .toLowerCase()
              .compareTo((b.treatmentPlanTitle ?? '').toLowerCase());
          break;
        case 3:
          c = a.dueDate.compareTo(b.dueDate);
          break;
        case 4:
          c = a.amountMinor.compareTo(b.amountMinor);
          break;
        case 5:
          c = a.status.compareTo(b.status);
          break;
        default:
          c = 0;
      }
      return _sortAscending ? c : -c;
    }

    list.sort(cmp);
    return list;
  }

  void _onSort(int idx, bool asc) {
    setState(() {
      _sortColumnIndex = idx;
      _sortAscending = asc;
    });
  }

  Future<void> _pickDateRange() async {
    final now = DateTime.now();
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime(now.year - 3),
      lastDate: DateTime(now.year + 5),
      initialDateRange: _dueFrom != null && _dueTo != null
          ? DateTimeRange(start: _dueFrom!, end: _dueTo!)
          : null,
    );
    if (picked != null) {
      setState(() {
        _dueFrom = picked.start;
        _dueTo = picked.end;
      });
    }
  }

  String _formatDueRangeLabel() {
    if (_dueFrom == null && _dueTo == null) {
      return AppLocalizations.of(context)!
          .translate('clinicFinanceInstallDateRangeAny');
    }
    final from = _dueFrom != null ? _formatDate(_dueFrom!.toIso8601String()) : '…';
    final to = _dueTo != null ? _formatDate(_dueTo!.toIso8601String()) : '…';
    return '$from → $to';
  }

  Color _statusColor(String status) {
    switch (status) {
      case 'PAID':
        return Colors.green.shade700;
      case 'OVERDUE':
        return Colors.red.shade700;
      case 'PENDING':
        return Colors.orange.shade800;
      case 'WAIVED':
        return Colors.blue.shade700;
      case 'CANCELLED':
        return Colors.grey.shade600;
      default:
        return Colors.grey.shade700;
    }
  }

  /// All statuses except [InstallmentItem.Status.OVERDUE], which is computed
  /// automatically by the backend sweeper and is not a user choice.
  static const List<String> _selectableStatuses = [
    'PENDING',
    'PAID',
    'WAIVED',
    'CANCELLED',
  ];

  String _statusLabel(BuildContext context, String value) {
    final l10n = AppLocalizations.of(context)!;
    switch (value) {
      case 'PENDING':
        return l10n.translate('clinicFinanceInstallStatusPending');
      case 'PAID':
        return l10n.translate('clinicFinanceInstallStatusPaid');
      case 'OVERDUE':
        return l10n.translate('clinicFinanceInstallStatusOverdue');
      case 'WAIVED':
        return l10n.translate('clinicFinanceInstallStatusWaived');
      case 'CANCELLED':
        return l10n.translate('clinicFinanceInstallStatusCancelled');
      default:
        return value;
    }
  }

  Future<void> _changeStatus(InstallmentItemListRow row, String newStatus) async {
    if (newStatus == row.status) return;
    final l10n = AppLocalizations.of(context)!;
    try {
      if (newStatus == 'PAID') {
        await markInstallmentItemPaid(
          ref,
          clinicId: widget.clinicId,
          itemId: row.id,
          method: 'CASH',
        );
      } else {
        await patchInstallmentItem(
          ref,
          clinicId: widget.clinicId,
          itemId: row.id,
          status: newStatus,
        );
      }
      if (!mounted) return;
      _invalidateInstallments();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('clinicFinanceInstallStatusUpdated')}: ${_statusLabel(context, newStatus)}',
          ),
        ),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('clinicFinanceInstallStatusUpdateFailed')}: $e',
          ),
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final canAct = ref.watch(canManageFinanceProvider);
    final itemsAsync =
        ref.watch(clinicInstallmentItemsProvider((widget.clinicId, _filter)));

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // ── Toolbar ─────────────────────────────────────────────────────
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  // Search box
                  Expanded(
                    child: TextField(
                      controller: _searchCtrl,
                      onChanged: (v) => setState(() => _search = v),
                      decoration: InputDecoration(
                        prefixIcon: const Icon(Icons.search, size: 20),
                        hintText: l10n.translate(
                          'clinicFinanceInstallSearchHint',
                        ),
                        isDense: true,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 10,
                        ),
                        suffixIcon: _search.isEmpty
                            ? null
                            : IconButton(
                                icon: const Icon(Icons.close, size: 18),
                                onPressed: () {
                                  _searchCtrl.clear();
                                  setState(() => _search = '');
                                },
                              ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  // Date range filter
                  OutlinedButton.icon(
                    onPressed: _pickDateRange,
                    icon: const Icon(Icons.date_range, size: 18),
                    label: Text(_formatDueRangeLabel()),
                  ),
                  if (_dueFrom != null || _dueTo != null) ...[
                    IconButton(
                      tooltip: l10n.translate('clinicFinanceInstallClearDates'),
                      icon: const Icon(Icons.clear, size: 18),
                      onPressed: () => setState(() {
                        _dueFrom = null;
                        _dueTo = null;
                      }),
                    ),
                  ],
                ],
              ),
              const SizedBox(height: 8),
              // Status filter chips
              SingleChildScrollView(
                scrollDirection: Axis.horizontal,
                child: Row(
                  children: [
                    _filterChip('all',
                        l10n.translate('clinicFinanceInstallFilterAll')),
                    _filterChip('pending',
                        l10n.translate('clinicFinanceInstallFilterPending')),
                    _filterChip('overdue',
                        l10n.translate('clinicFinanceInstallFilterOverdue')),
                    _filterChip('paid',
                        l10n.translate('clinicFinanceInstallFilterPaid')),
                  ],
                ),
              ),
            ],
          ),
        ),
        const Divider(height: 1),
        // ── Table ───────────────────────────────────────────────────────
        Expanded(
          child: itemsAsync.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (e, _) => Center(child: Text('Error: $e')),
            data: (list) {
              final filtered = _applyClientFilters(list);
              if (filtered.isEmpty) {
                return Center(
                  child: Text(
                    l10n.translate('clinicFinanceNoInstallments'),
                  ),
                );
              }
              return RefreshIndicator(
                onRefresh: () async {
                  ref.invalidate(
                    clinicInstallmentItemsProvider((widget.clinicId, _filter)),
                  );
                },
                child: SingleChildScrollView(
                  primary: true,
                  child: SingleChildScrollView(
                    scrollDirection: Axis.horizontal,
                    padding: const EdgeInsets.symmetric(horizontal: 8),
                    child: ConstrainedBox(
                      constraints: BoxConstraints(
                        minWidth: MediaQuery.of(context).size.width - 16,
                      ),
                      child: DataTable(
                        sortColumnIndex: _sortColumnIndex,
                        sortAscending: _sortAscending,
                        headingRowHeight: 44,
                        dataRowMinHeight: 52,
                        dataRowMaxHeight: 64,
                        columnSpacing: 24,
                        showCheckboxColumn: false,
                        columns: [
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColSeq')),
                            numeric: true,
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColPatient')),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColPlan')),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColDue')),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColAmount')),
                            numeric: true,
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColStatus')),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Text(l10n.translate(
                                'clinicFinanceInstallColActions')),
                          ),
                        ],
                        rows: filtered.map((row) {
                          final planTitle = row.treatmentPlanTitle?.trim();
                          final plan = planTitle != null && planTitle.isNotEmpty
                              ? planTitle
                              : 'Plan #${row.treatmentPlanId}';
                          final canEditStatus =
                              canAct && row.status != 'PAID';
                          return DataRow(
                            cells: [
                              DataCell(Text('#${row.sequenceNumber}')),
                              DataCell(Text(
                                row.patientName,
                                style: const TextStyle(
                                  fontWeight: FontWeight.w500,
                                ),
                              )),
                              DataCell(Text(plan)),
                              DataCell(Text(_formatDate(row.dueDate))),
                              DataCell(Text(
                                _formatMoney(row.amountMinor, row.currency),
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              )),
                              DataCell(
                                _StatusPill(
                                  status: row.status,
                                  color: _statusColor(row.status),
                                  label: _statusLabel(context, row.status),
                                  enabled: canEditStatus,
                                  entries: [
                                    for (final s in _selectableStatuses)
                                      if (s != row.status)
                                        PopupMenuItem<String>(
                                          value: s,
                                          child: Row(
                                            children: [
                                              Icon(Icons.circle,
                                                  size: 10,
                                                  color: _statusColor(s)),
                                              const SizedBox(width: 8),
                                              Text(_statusLabel(context, s)),
                                            ],
                                          ),
                                        ),
                                  ],
                                  onSelected: (s) => _changeStatus(row, s),
                                ),
                              ),
                              DataCell(
                                canAct && row.status != 'PAID'
                                    ? IconButton(
                                        tooltip: l10n.translate(
                                            'clinicFinanceNotifyInstallment'),
                                        icon: const Icon(
                                          Icons.notifications_active_outlined,
                                          size: 20,
                                        ),
                                        onPressed: () async {
                                          final ok = await notifyInstallmentItem(
                                            ref,
                                            clinicId: widget.clinicId,
                                            itemId: row.id,
                                          );
                                          if (context.mounted) {
                                            ScaffoldMessenger.of(context)
                                                .showSnackBar(
                                              SnackBar(
                                                content:
                                                    Text(ok ? 'OK' : 'Failed'),
                                              ),
                                            );
                                          }
                                        },
                                      )
                                    : const SizedBox.shrink(),
                              ),
                            ],
                          );
                        }).toList(),
                      ),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _filterChip(String value, String label) {
    final selected = _filter == value;
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: ChoiceChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => setState(() => _filter = value),
        selectedColor: AppColors.primaryTeal.withValues(alpha: 0.15),
        labelStyle: TextStyle(
          color: selected ? AppColors.primaryTeal : Colors.grey.shade700,
          fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
        ),
      ),
    );
  }
}

class _DoctorEarningsPane extends ConsumerWidget {
  final int clinicId;
  const _DoctorEarningsPane({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final async = ref.watch(clinicDoctorEarningsProvider(clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (rows) {
        if (rows.isEmpty) {
          return Center(child: Text(l10n.translate('clinicFinanceNoLedgerRows')));
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: rows.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (ctx, i) {
            final d = rows[i];
            return ListTile(
              title: Text('Doctor #${d.doctorProfileId}'),
              subtitle: Text(
                '${l10n.translate('clinicFinanceDoctorEarningsHint')} · visits ${d.visitCount}',
              ),
              trailing: Text(
                '${_formatMoney(d.grossMinor, 'UZS')} / ${_formatMoney(d.collectedMinor, 'UZS')}',
              ),
            );
          },
        );
      },
    );
  }
}

class _RecordsView extends ConsumerWidget {
  final int clinicId;
  const _RecordsView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final recordsAsync = ref.watch(clinicFinancialRecordsProvider(clinicId));

    return recordsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (records) {
        if (records.isEmpty) {
          return Center(
            child: Text(l10n.translate('clinicFinanceNoRecords')),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: records.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final r = records[index];
            return _FinancialRecordTile(record: r);
          },
        );
      },
    );
  }
}

class _PaymentsView extends ConsumerWidget {
  final int clinicId;
  const _PaymentsView({required this.clinicId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final paymentsAsync = ref.watch(clinicPaymentHistoryProvider(clinicId));

    return paymentsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (payments) {
        if (payments.isEmpty) {
          return Center(
            child: Text(l10n.translate('clinicFinanceNoPayments')),
          );
        }
        return ListView.separated(
          padding: const EdgeInsets.all(16),
          itemCount: payments.length,
          separatorBuilder: (_, __) => const Divider(height: 1),
          itemBuilder: (context, index) {
            final p = payments[index];
            return ListTile(
              leading: _methodIcon(p.method),
              title: Text(_formatMoney(p.amountMinor, p.currency)),
              subtitle: Text('${p.method} • ${_formatDate(p.recordedAt)}'),
              trailing: p.memo != null
                  ? Text(
                      p.memo!,
                      style: TextStyle(
                          color: Colors.grey.shade600, fontSize: 12),
                    )
                  : null,
            );
          },
        );
      },
    );
  }

  Widget _methodIcon(String method) {
    switch (method) {
      case 'CASH':
        return const Icon(Icons.payments_outlined, color: Colors.green);
      case 'CARD_EXTERNAL':
        return const Icon(Icons.credit_card, color: Colors.blue);
      case 'TRANSFER':
        return const Icon(Icons.account_balance, color: Colors.purple);
      default:
        return const Icon(Icons.receipt_long, color: Colors.grey);
    }
  }
}

class _FinancialRecordTile extends StatelessWidget {
  final FinancialRecordRow record;
  const _FinancialRecordTile({required this.record});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: _statusBadge(record.status),
      title: Text(
        '${record.recordType} ${record.recordNumber ?? '#${record.id}'}',
        style: const TextStyle(fontWeight: FontWeight.w500),
      ),
      subtitle: Text(
        '${_formatMoney(record.totalMinor, record.currency)} • '
        '${record.uiPaymentStatus.isNotEmpty ? record.uiPaymentStatus : record.status} • '
        'Paid: ${_formatMoney(record.paidMinor, record.currency)}',
      ),
      trailing: record.remainingMinor > 0
          ? Text(
              _formatMoney(record.remainingMinor, record.currency),
              style: const TextStyle(
                  color: Colors.red, fontWeight: FontWeight.w600),
            )
          : const Icon(Icons.check_circle, color: Colors.green, size: 20),
    );
  }

  Widget _statusBadge(String status) {
    Color color;
    switch (status) {
      case 'PAID':
        color = Colors.green;
        break;
      case 'OVERDUE':
        color = Colors.red;
        break;
      case 'PARTIALLY_PAID':
        color = Colors.orange;
        break;
      case 'ISSUED':
        color = Colors.blue;
        break;
      default:
        color = Colors.grey;
    }
    return CircleAvatar(
      radius: 6,
      backgroundColor: color,
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String title;
  final String value;
  final Color color;

  const _KpiCard({
    required this.title,
    required this.value,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: 200,
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color.withValues(alpha: 0.2)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            title,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade700,
              fontWeight: FontWeight.w500,
            ),
          ),
          const SizedBox(height: 8),
          Text(
            value,
            style: TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}

String _formatMoney(int amountMinor, String currency) {
  if (currency == 'UZS') {
    final whole = amountMinor ~/ 100;
    final formatted = whole.toString().replaceAllMapped(
      RegExp(r'(\d)(?=(\d{3})+$)'),
      (m) => '${m[1]} ',
    );
    return '$formatted $currency';
  }
  final amount = amountMinor / 100;
  return '${amount.toStringAsFixed(2)} $currency';
}

String _formatDate(String isoDate) {
  try {
    final dt = DateTime.parse(isoDate);
    return '${dt.day.toString().padLeft(2, '0')}.${dt.month.toString().padLeft(2, '0')}.${dt.year}';
  } catch (_) {
    return isoDate;
  }
}

/// Compact, colour-coded status chip that doubles as a status picker. When
/// [enabled] is true the user can tap it to open a popup menu of the other
/// available statuses; otherwise it renders as a read-only pill (used for
/// PAID rows, which the backend forbids editing).
class _StatusPill extends StatelessWidget {
  final String status;
  final String label;
  final Color color;
  final bool enabled;
  final List<PopupMenuEntry<String>> entries;
  final ValueChanged<String> onSelected;

  const _StatusPill({
    required this.status,
    required this.label,
    required this.color,
    required this.enabled,
    required this.entries,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    final pill = Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.4)),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(
            label,
            style: TextStyle(
              color: color,
              fontWeight: FontWeight.w600,
              fontSize: 12,
            ),
          ),
          if (enabled) ...[
            const SizedBox(width: 4),
            Icon(Icons.arrow_drop_down, size: 18, color: color),
          ],
        ],
      ),
    );

    if (!enabled || entries.isEmpty) return pill;

    return PopupMenuButton<String>(
      tooltip: 'Change status',
      onSelected: onSelected,
      itemBuilder: (_) => entries,
      child: pill,
    );
  }
}
