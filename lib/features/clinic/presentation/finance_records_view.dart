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
import 'clinic_finance_record_dialog.dart';

class FinanceRecordsView extends ConsumerStatefulWidget {
  final int clinicId;
  const FinanceRecordsView({super.key, required this.clinicId});

  @override
  ConsumerState<FinanceRecordsView> createState() => FinanceRecordsViewState();
}

class FinanceRecordsViewState extends ConsumerState<FinanceRecordsView> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  String _statusFilter = 'ALL'; // ALL | ISSUED | PAID | PARTIALLY_PAID | OVERDUE | VOID
  int _sortIdx = 0;
  bool _sortAsc = false;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  Color _statusColor(String status) {
    switch (status) {
      case 'PAID':
        return Colors.green.shade700;
      case 'OVERDUE':
        return Colors.red.shade700;
      case 'PARTIALLY_PAID':
        return Colors.orange.shade700;
      case 'ISSUED':
        return Colors.blue.shade700;
      default:
        return Colors.grey.shade700;
    }
  }

  List<FinancialRecordRow> _apply(List<FinancialRecordRow> rows) {
    Iterable<FinancialRecordRow> out = rows;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((r) =>
          r.id.toString().contains(s) ||
          (r.recordNumber ?? '').toLowerCase().contains(s) ||
          r.recordType.toLowerCase().contains(s) ||
          (r.notes ?? '').toLowerCase().contains(s));
    }
    if (_statusFilter != 'ALL') {
      out = out.where((r) => r.status == _statusFilter);
    }
    final list = out.toList();
    int cmp(FinancialRecordRow a, FinancialRecordRow b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.createdAt.compareTo(b.createdAt);
          break;
        case 1:
          c = a.recordType.compareTo(b.recordType);
          break;
        case 2:
          c = (a.recordNumber ?? '').compareTo(b.recordNumber ?? '');
          break;
        case 3:
          c = a.totalMinor.compareTo(b.totalMinor);
          break;
        case 4:
          c = a.paidMinor.compareTo(b.paidMinor);
          break;
        case 5:
          c = a.remainingMinor.compareTo(b.remainingMinor);
          break;
        case 6:
          c = a.status.compareTo(b.status);
          break;
        case 7:
          c = (a.dueDate ?? '').compareTo(b.dueDate ?? '');
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final recordsAsync =
        ref.watch(clinicFinancialRecordsProvider(widget.clinicId));

    return recordsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('${l10n.error}: $e')),
      data: (records) {
        final filtered = _apply(records);
        final canManage = ref.watch(canManageFinanceProvider);
        final toolbar = Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ClinicTableSearchField(
                    controller: _searchCtrl,
                    hint: l10n.translate('clinicRecordsSearchHint'),
                    onChanged: (v) => setState(() => _search = v),
                  ),
                  const SizedBox(height: 8),
                  ClinicFilterChips<String>(
                    selected: _statusFilter,
                    onSelected: (v) => setState(() => _statusFilter = v),
                    options: [
                      (
                        value: 'ALL',
                        label: l10n.translate('clinicTreatmentPlansAll'),
                      ),
                      (
                        value: 'ISSUED',
                        label: l10n.clinicRecordStatusLabel('ISSUED'),
                      ),
                      (
                        value: 'PAID',
                        label: l10n.clinicRecordStatusLabel('PAID'),
                      ),
                      (
                        value: 'PARTIALLY_PAID',
                        label: l10n.clinicRecordStatusLabel('PARTIALLY_PAID'),
                      ),
                      (
                        value: 'OVERDUE',
                        label: l10n.clinicRecordStatusLabel('OVERDUE'),
                      ),
                      (
                        value: 'VOID',
                        label: l10n.clinicRecordStatusLabel('VOID'),
                      ),
                    ],
                  ),
                ],
              ),
            ),
            if (canManage) ...[
              const SizedBox(width: 12),
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: FilledButton.icon(
                  icon: const Icon(Icons.add, size: 20),
                  onPressed: () {
                    showClinicFinanceRecordDialog(
                      context: context,
                      ref: ref,
                      clinicId: widget.clinicId,
                    );
                  },
                  label: Text(l10n.translate('clinicRecordsNewRecord')),
                ),
              ),
            ],
          ],
        );

        Widget body;
        if (records.isEmpty) {
          body = Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 560),
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Icon(
                      Icons.receipt_long_outlined,
                      size: 52,
                      color: Colors.grey.shade500,
                    ),
                    const SizedBox(height: 16),
                    Text(
                      l10n.translate('clinicRecordsEmptyTitle'),
                      textAlign: TextAlign.center,
                      style:
                          Theme.of(context).textTheme.titleLarge?.copyWith(
                                fontWeight: FontWeight.w700,
                              ),
                    ),
                    const SizedBox(height: 12),
                    Text(
                      l10n.translate('clinicRecordsEmptyBody'),
                      textAlign: TextAlign.center,
                      style:
                          Theme.of(context).textTheme.bodyMedium?.copyWith(
                                color: Colors.grey.shade700,
                                height: 1.35,
                              ),
                    ),
                  ],
                ),
              ),
            ),
          );
        } else if (filtered.isEmpty) {
          body = Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Text(
                l10n.translate('clinicTableNoFilteredResults'),
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyLarge,
              ),
            ),
          );
        } else {
          body = clinicDataTable(
                context: context,
                sortColumnIndex: _sortIdx,
                sortAscending: _sortAsc,
                columns: [
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColCreated')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColType')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColNumber')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColTotal')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColPaid')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label:
                        Text(l10n.translate('clinicRecordsColRemaining')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColStatus')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicRecordsColDue')),
                    onSort: _onSort,
                  ),
                ],
                rows: filtered.map((r) {
                  final color = _statusColor(r.status);
                  return DataRow(
                    cells: [
                      DataCell(Text(formatFinanceDate(r.createdAt))),
                      DataCell(Text(l10n.clinicRecordTypeLabel(r.recordType))),
                      DataCell(Text(r.recordNumber ?? '#${r.id}')),
                      DataCell(Text(
                        formatFinanceMoney(r.totalMinor, r.currency),
                        style: const TextStyle(
                            fontWeight: FontWeight.w600),
                      )),
                      DataCell(Text(
                        formatFinanceMoney(r.paidMinor, r.currency),
                        style: TextStyle(color: Colors.green.shade700),
                      )),
                      DataCell(Text(
                        formatFinanceMoney(r.remainingMinor, r.currency),
                        style: TextStyle(
                          color: r.remainingMinor > 0
                              ? Colors.red.shade700
                              : Colors.grey.shade600,
                          fontWeight: FontWeight.w600,
                        ),
                      )),
                      DataCell(Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 8, vertical: 3),
                        decoration: BoxDecoration(
                          color: color.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(999),
                          border: Border.all(
                              color: color.withValues(alpha: 0.6)),
                        ),
                        child: Text(
                          r.uiPaymentStatus.isNotEmpty
                              ? r.uiPaymentStatus
                              : l10n.clinicRecordStatusLabel(r.status),
                          style: TextStyle(
                            color: color,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      )),
                      DataCell(Text(
                          r.dueDate != null ? formatFinanceDate(r.dueDate!) : '—')),
                    ],
                  );
                }).toList(),
              );
        }

        return RefreshIndicator(
          onRefresh: () async {
            refreshClinicFinancialData(ref, widget.clinicId);
          },
          child: ClinicTableShell(toolbar: toolbar, body: body),
        );
      },
    );
  }
}
