import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_month.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

import 'finance_shared.dart';
class FinanceInstallmentsView extends ConsumerStatefulWidget {
  final int clinicId;
  const FinanceInstallmentsView({super.key, required this.clinicId});

  @override
  ConsumerState<FinanceInstallmentsView> createState() =>
      FinanceInstallmentsViewState();
}

class FinanceInstallmentsViewState extends ConsumerState<FinanceInstallmentsView> {
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
    refreshClinicFinancialData(ref, widget.clinicId);
  }

  /// Client-side filter + sort. The backend already applies the status filter
  /// (`all/pending/overdue/paid`); everything else here (search, date range,
  /// sort) is in-memory so the table feels instant.
  List<InstallmentItemListRow> _applyClientFilters(
    List<InstallmentItemListRow> rows, {
    DateTime? monthFromUtc,
    DateTime? monthToUtc,
  }) {
    Iterable<InstallmentItemListRow> out = rows;
    if (monthFromUtc != null && monthToUtc != null) {
      out = out.where(
        (r) => financeInstantInMonthRange(r.dueDate, monthFromUtc, monthToUtc),
      );
    }
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
    final from = _dueFrom != null ? formatFinanceDate(_dueFrom!.toIso8601String()) : '…';
    final to = _dueTo != null ? formatFinanceDate(_dueTo!.toIso8601String()) : '…';
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
    final month = ref.watch(clinicFinanceMonthFilterProvider(widget.clinicId));
    final clinic = ref.watch(selectedClinicProvider);
    DateTime? monthFromUtc;
    DateTime? monthToUtc;
    if (month != null) {
      final range = monthRangeUtcInTimezone(
        month.year,
        month.month,
        clinic?.timeZone,
      );
      monthFromUtc = range.fromUtc;
      monthToUtc = range.toUtc;
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // ── Toolbar ─────────────────────────────────────────────────────
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              FinanceMonthFilterBar(clinicId: widget.clinicId),
              const SizedBox(height: 8),
              ClinicTableToolbar(
                search: TextField(
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
                actions: [
                  OutlinedButton.icon(
                    onPressed: _pickDateRange,
                    icon: const Icon(Icons.date_range, size: 18),
                    label: Text(_formatDueRangeLabel()),
                  ),
                  if (_dueFrom != null || _dueTo != null)
                    IconButton(
                      tooltip: l10n.translate('clinicFinanceInstallClearDates'),
                      icon: const Icon(Icons.clear, size: 18),
                      onPressed: () => setState(() {
                        _dueFrom = null;
                        _dueTo = null;
                      }),
                    ),
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
            error: (e, _) => Center(child: Text('${l10n.error}: $e')),
            data: (list) {
              final filtered = _applyClientFilters(
                list,
                monthFromUtc: monthFromUtc,
                monthToUtc: monthToUtc,
              );
              final itemCount = filtered.length;
              final scheduled =
                  filtered.fold<int>(0, (a, r) => a + r.amountMinor);
              final paidSum = filtered.fold<int>(
                0,
                (a, r) =>
                    r.status == 'PAID' ? a + r.amountMinor : a,
              );
              final outstanding = scheduled - paidSum;
              final clinicCurrency =
                  ref.watch(clinicFinanceCurrencyProvider(widget.clinicId));
              final cur =
                  filtered.isEmpty ? clinicCurrency : filtered.first.currency;

              Widget totalsStrip() => Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
                    child: Wrap(
                      spacing: 12,
                      runSpacing: 8,
                      children: [
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsItems')}: $itemCount · '
                            '${l10n.translate('clinicFinanceInstallTotalsFilteredNote')}',
                          ),
                        ),
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsScheduled')}: ${formatFinanceMoney(scheduled, cur)}',
                          ),
                        ),
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsPaidSum')}: ${formatFinanceMoney(paidSum, cur)}',
                          ),
                        ),
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsOutstanding')}: ${formatFinanceMoney(outstanding, cur)}',
                          ),
                        ),
                      ],
                    ),
                  );

              if (filtered.isEmpty) {
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    totalsStrip(),
                    Expanded(
                      child: Center(
                        child: Text(
                          l10n.translate('clinicFinanceNoInstallments'),
                        ),
                      ),
                    ),
                  ],
                );
              }

              return Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  totalsStrip(),
                  Expanded(
                    child: RefreshIndicator(
                onRefresh: () async {
                  refreshClinicFinancialData(ref, widget.clinicId);
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
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintSeq',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Text(l10n.translate(
                                      'clinicFinanceInstallColSeq')),
                                ],
                              ),
                            ),
                            numeric: true,
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintPatient',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Flexible(
                                    child: Text(l10n.translate(
                                        'clinicFinanceInstallColPatient')),
                                  ),
                                ],
                              ),
                            ),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintPlan',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Flexible(
                                    child: Text(l10n.translate(
                                        'clinicFinanceInstallColPlan')),
                                  ),
                                ],
                              ),
                            ),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintDue',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Text(l10n.translate(
                                      'clinicFinanceInstallColDue')),
                                ],
                              ),
                            ),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintAmount',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Text(l10n.translate(
                                      'clinicFinanceInstallColAmount')),
                                ],
                              ),
                            ),
                            numeric: true,
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintStatus',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Flexible(
                                    child: Text(l10n.translate(
                                        'clinicFinanceInstallColStatus')),
                                  ),
                                ],
                              ),
                            ),
                            onSort: _onSort,
                          ),
                          DataColumn(
                            label: Tooltip(
                              message: l10n.translate(
                                'clinicFinanceInstallHintActions',
                              ),
                              child: Row(
                                children: [
                                  Icon(Icons.info_outline,
                                      size: 14, color: Colors.grey.shade600),
                                  const SizedBox(width: 4),
                                  Text(l10n.translate(
                                      'clinicFinanceInstallColActions')),
                                ],
                              ),
                            ),
                          ),
                        ],
                        rows: filtered.map((row) {
                          final planTitle = row.treatmentPlanTitle?.trim();
                          final plan = planTitle != null && planTitle.isNotEmpty
                              ? planTitle
                              : '#${row.treatmentPlanId}';
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
                              DataCell(Text(formatFinanceDate(row.dueDate))),
                              DataCell(Text(
                                formatFinanceMoney(row.amountMinor, row.currency),
                                style: const TextStyle(
                                  fontWeight: FontWeight.w600,
                                ),
                              )),
                              DataCell(
                                FinanceStatusPill(
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
                                                    Text(
                                                      ok
                                                          ? l10n.translate(
                                                              'clinicActionSuccess',
                                                            )
                                                          : l10n.translate(
                                                              'clinicActionFailed',
                                                            ),
                                                    ),
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
              ),
            ),
          ],
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
