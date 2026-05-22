import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

import 'clinic_finance_record_dialog.dart';

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
                    '${_formatMoney(d.collectedMinor, stats.currency)} / '
                    '${_formatMoney(d.outstandingMinor, stats.currency)}',
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

class _AppointmentLedgerView extends ConsumerStatefulWidget {
  final int clinicId;
  const _AppointmentLedgerView({required this.clinicId});

  @override
  ConsumerState<_AppointmentLedgerView> createState() =>
      _AppointmentLedgerViewState();
}

class _AppointmentLedgerViewState
    extends ConsumerState<_AppointmentLedgerView> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  String _statusFilter = 'ALL'; // matches planSimplePaymentStatus
  int _sortIdx = 0; // Default: most recent visits first.
  bool _sortAsc = false;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  List<AppointmentLedgerRowDto> _apply(List<AppointmentLedgerRowDto> rows) {
    Iterable<AppointmentLedgerRowDto> out = rows;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((r) =>
          r.patientName.toLowerCase().contains(s) ||
          r.doctorName.toLowerCase().contains(s) ||
          r.appointmentId.toString().contains(s) ||
          r.treatmentPlanId.toString().contains(s));
    }
    if (_statusFilter != 'ALL') {
      out = out.where((r) => r.planSimplePaymentStatus == _statusFilter);
    }
    final list = out.toList();
    int cmp(AppointmentLedgerRowDto a, AppointmentLedgerRowDto b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.startAt.compareTo(b.startAt);
          break;
        case 1:
          c = a.patientName
              .toLowerCase()
              .compareTo(b.patientName.toLowerCase());
          break;
        case 2:
          c = a.doctorName
              .toLowerCase()
              .compareTo(b.doctorName.toLowerCase());
          break;
        case 3:
          c = a.treatmentPlanId.compareTo(b.treatmentPlanId);
          break;
        case 4:
          c = a.services.length.compareTo(b.services.length);
          break;
        case 5:
          c = a.visitTotalMinor.compareTo(b.visitTotalMinor);
          break;
        case 6:
          c = a.planSimplePaymentStatus
              .compareTo(b.planSimplePaymentStatus);
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  Color _statusColor(String s) {
    switch (s.toUpperCase()) {
      case 'PAID':
        return Colors.green.shade700;
      case 'PARTIAL':
        return Colors.orange.shade700;
      case 'UNPAID':
        return Colors.red.shade700;
      default:
        return Colors.grey.shade600;
    }
  }

  void _showServices(AppointmentLedgerRowDto row) {
    final l10n = AppLocalizations.of(context)!;
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('${row.patientName} · ${_formatDate(row.startAt)}'),
        content: SizedBox(
          width: 480,
          child: SingleChildScrollView(
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Text(
                  '${l10n.translate('clinicFinanceVisitServices')} · '
                  '${row.doctorName}',
                  style: Theme.of(ctx).textTheme.titleSmall,
                ),
                const SizedBox(height: 8),
                ...row.services.map(
                  (s) => ListTile(
                    dense: true,
                    contentPadding: EdgeInsets.zero,
                    title: Text(s.title),
                    trailing: Text(
                      _formatMoney(s.lineTotalMinor, row.currency),
                    ),
                  ),
                ),
                const Divider(),
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(l10n.translate('clinicTreatmentPlansTotal'),
                          style: const TextStyle(
                              fontWeight: FontWeight.w600)),
                      Text(
                        _formatMoney(row.visitTotalMinor, row.currency),
                        style:
                            const TextStyle(fontWeight: FontWeight.w700),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: Text(l10n.close),
          ),
        ],
      ),
    );
  }

  void _invalidateFinanceAfterPayment() {
    ref.invalidate(clinicAppointmentLedgerProvider(widget.clinicId));
    ref.invalidate(clinicPaymentHistoryProvider(widget.clinicId));
    ref.invalidate(clinicDoctorEarningsProvider(widget.clinicId));
    ref.invalidate(clinicFinanceDashboardProvider(widget.clinicId));
    ref.invalidate(clinicFinancialRecordsProvider(widget.clinicId));
  }

  Future<void> _quickPayRow(AppointmentLedgerRowDto row, String method) async {
    final l10n = AppLocalizations.of(context)!;
    if (row.visitTotalMinor <= 0) return;
    try {
      await recordClinicPayment(
        ref,
        clinicId: widget.clinicId,
        treatmentPlanId: row.treatmentPlanId,
        amountMinor: row.visitTotalMinor,
        currency: row.currency,
        method: method,
        linkedAppointmentId: row.appointmentId,
      );
      if (!mounted) return;
      _invalidateFinanceAfterPayment();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('clinicFinancePaymentRecorded'))),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          backgroundColor: Colors.red,
          content:
              Text('${l10n.translate('clinicFinancePaymentFailed')}: $e'),
        ),
      );
    }
  }

  Future<void> _showCustomPayDialog(AppointmentLedgerRowDto row) async {
    final l10n = AppLocalizations.of(context)!;
    final major = row.visitTotalMinor / 100.0;
    final ctrl = TextEditingController(
      text: major == major.roundToDouble()
          ? '${major.round()}'
          : major.toStringAsFixed(2),
    );
    final memoCtrl = TextEditingController();
    String method = 'CASH';

    await showDialog<void>(
      context: context,
      builder: (ctx) {
        return StatefulBuilder(
          builder: (ctx, setSt) {
            return AlertDialog(
              title: Text(l10n.translate('clinicFinancePaymentDialogTitle')),
              content: SizedBox(
                width: 360,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      TextField(
                        controller: ctrl,
                        decoration: InputDecoration(
                          labelText:
                              l10n.translate('clinicFinancePaymentAmountLabel'),
                        ),
                        keyboardType: const TextInputType.numberWithOptions(
                          decimal: true,
                        ),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        l10n.translate('clinicPaymentsColMethod'),
                        style: Theme.of(ctx).textTheme.labelLarge,
                      ),
                      const SizedBox(height: 8),
                      Wrap(
                        spacing: 8,
                        runSpacing: 6,
                        children: [
                          (
                            'CASH',
                            l10n.translate('clinicFinancePayByCash'),
                          ),
                          (
                            'CARD_EXTERNAL',
                            l10n.translate('clinicFinancePayByCard'),
                          ),
                          (
                            'TRANSFER',
                            l10n.translate('clinicFinancePayByTransfer'),
                          ),
                          (
                            'OTHER',
                            l10n.translate('clinicFinancePayByOther'),
                          ),
                        ]
                            .map(
                              (e) => ChoiceChip(
                                label: Text(e.$2),
                                selected: method == e.$1,
                                onSelected: (_) =>
                                    setSt(() => method = e.$1),
                              ),
                            )
                            .toList(),
                      ),
                      TextField(
                        controller: memoCtrl,
                        decoration: InputDecoration(
                          labelText: l10n
                              .translate('clinicFinancePaymentMemoLabel'),
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.pop(ctx),
                  child: Text(l10n.cancel),
                ),
                FilledButton(
                  onPressed: () async {
                    final parsed = double.tryParse(
                      ctrl.text.replaceAll(' ', '').replaceAll(',', '.').trim(),
                    );
                    if (parsed == null || parsed <= 0) {
                      ScaffoldMessenger.of(ctx).showSnackBar(
                        SnackBar(
                          content: Text(
                            l10n.translate('clinicFinanceInvalidAmount'),
                          ),
                        ),
                      );
                      return;
                    }
                    final minor = (parsed * 100).round();
                    Navigator.pop(ctx);
                    try {
                      await recordClinicPayment(
                        ref,
                        clinicId: widget.clinicId,
                        treatmentPlanId: row.treatmentPlanId,
                        amountMinor: minor,
                        currency: row.currency,
                        method: method,
                        memo: memoCtrl.text.trim().isEmpty
                            ? null
                            : memoCtrl.text.trim(),
                        linkedAppointmentId: row.appointmentId,
                      );
                      if (!mounted) return;
                      _invalidateFinanceAfterPayment();
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text(
                            l10n.translate('clinicFinancePaymentRecorded'),
                          ),
                        ),
                      );
                    } catch (e) {
                      if (!mounted) return;
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          backgroundColor: Colors.red,
                          content: Text(
                            '${l10n.translate('clinicFinancePaymentFailed')}: $e',
                          ),
                        ),
                      );
                    }
                  },
                  child: Text(l10n.translate('clinicFinancePaymentConfirm')),
                ),
              ],
            );
          },
        );
      },
    );
  }

  Widget _appointmentPaymentStatusCell(
    BuildContext context,
    AppointmentLedgerRowDto row,
    Color color,
    bool canAct,
  ) {
    final l10n = AppLocalizations.of(context)!;
    final label = row.planSimplePaymentStatus;
    final fullyPaidPlan = row.planSimplePaymentStatus == 'PAID';
    final payable = row.visitTotalMinor > 0 &&
        row.planSimplePaymentStatus != 'NONE' &&
        !fullyPaidPlan;
    final enabled = canAct && payable;

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

    if (!enabled) return pill;

    return PopupMenuButton<String>(
      tooltip: l10n.translate('clinicLedgerPayMenu'),
      onSelected: (key) {
        if (key == 'CUSTOM') {
          _showCustomPayDialog(row);
        } else {
          _quickPayRow(row, key);
        }
      },
      itemBuilder: (ctx) => [
        PopupMenuItem(
          value: 'CASH',
          child: Text(l10n.translate('clinicFinancePayByCash')),
        ),
        PopupMenuItem(
          value: 'CARD_EXTERNAL',
          child: Text(l10n.translate('clinicFinancePayByCard')),
        ),
        PopupMenuItem(
          value: 'TRANSFER',
          child: Text(l10n.translate('clinicFinancePayByTransfer')),
        ),
        PopupMenuItem(
          value: 'OTHER',
          child: Text(l10n.translate('clinicFinancePayByOther')),
        ),
        const PopupMenuDivider(),
        PopupMenuItem(
          value: 'CUSTOM',
          child: Text(l10n.translate('clinicFinancePayCustomAmount')),
        ),
      ],
      child: pill,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final canAct = ref.watch(canManageFinanceProvider);
    final async =
        ref.watch(clinicAppointmentLedgerProvider(widget.clinicId));
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (page) {
        final content = page['content'] as List<dynamic>? ?? [];
        final rows = content
            .map((r) => AppointmentLedgerRowDto.fromJson(
                Map<String, dynamic>.from(r as Map)))
            .toList();
        final filtered = _apply(rows);

        final toolbar = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ClinicTableSearchField(
              controller: _searchCtrl,
              hint: l10n.translate('clinicLedgerSearchHint'),
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
                (value: 'PAID', label: 'PAID'),
                (value: 'PARTIAL', label: 'PARTIAL'),
                (value: 'UNPAID', label: 'UNPAID'),
              ],
            ),
          ],
        );

        final Widget body = rows.isEmpty || filtered.isEmpty
            ? ClinicTableEmpty(l10n.translate('clinicFinanceNoLedgerRows'))
            : clinicDataTable(
                context: context,
                sortColumnIndex: _sortIdx,
                sortAscending: _sortAsc,
                columns: [
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColDate')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColPatient')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColDoctor')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Tooltip(
                      message: l10n.translate('clinicLedgerColPlanTooltip'),
                      child: Row(
                        children: [
                          Icon(Icons.info_outline,
                              size: 14, color: Colors.grey.shade600),
                          const SizedBox(width: 6),
                          Flexible(
                            child: Text(
                              l10n.translate('clinicLedgerColPlanId'),
                            ),
                          ),
                        ],
                      ),
                    ),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColServices')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColTotal')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColStatus')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicLedgerColActions')),
                  ),
                ],
                rows: filtered.map((row) {
                  final color =
                      _statusColor(row.planSimplePaymentStatus);
                  return DataRow(
                    cells: [
                      DataCell(Text(_formatDate(row.startAt))),
                      DataCell(Text(
                        row.patientName,
                        style:
                            const TextStyle(fontWeight: FontWeight.w500),
                      )),
                      DataCell(Text(row.doctorName)),
                      DataCell(
                        Tooltip(
                          message: '#${row.treatmentPlanId}',
                          child: Text('#${row.treatmentPlanId}'),
                        ),
                      ),
                      DataCell(Text('${row.services.length}')),
                      DataCell(Text(
                        _formatMoney(row.visitTotalMinor, row.currency),
                        style:
                            const TextStyle(fontWeight: FontWeight.w600),
                      )),
                      DataCell(
                        _appointmentPaymentStatusCell(
                          context,
                          row,
                          color,
                          canAct,
                        ),
                      ),
                      DataCell(
                        IconButton(
                          tooltip: l10n
                              .translate('clinicLedgerViewServices'),
                          icon: const Icon(
                              Icons.medical_information_outlined,
                              size: 20),
                          onPressed: () => _showServices(row),
                        ),
                      ),
                    ],
                  );
                }).toList(),
              );

        return ClinicTableShell(toolbar: toolbar, body: body);
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
              final itemCount = filtered.length;
              final scheduled =
                  filtered.fold<int>(0, (a, r) => a + r.amountMinor);
              final paidSum = filtered.fold<int>(
                0,
                (a, r) =>
                    r.status == 'PAID' ? a + r.amountMinor : a,
              );
              final outstanding = scheduled - paidSum;
              final cur = filtered.isEmpty ? 'UZS' : filtered.first.currency;

              Widget totalsStrip() => Padding(
                    padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
                    child: Wrap(
                      spacing: 12,
                      runSpacing: 8,
                      children: [
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsItems')}: $itemCount',
                          ),
                        ),
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsScheduled')}: ${_formatMoney(scheduled, cur)}',
                          ),
                        ),
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsPaidSum')}: ${_formatMoney(paidSum, cur)}',
                          ),
                        ),
                        Chip(
                          label: Text(
                            '${l10n.translate('clinicFinanceInstallTotalsOutstanding')}: ${_formatMoney(outstanding, cur)}',
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

class _DoctorEarningsPane extends ConsumerStatefulWidget {
  final int clinicId;
  const _DoctorEarningsPane({required this.clinicId});

  @override
  ConsumerState<_DoctorEarningsPane> createState() =>
      _DoctorEarningsPaneState();
}

class _DoctorEarningsPaneState extends ConsumerState<_DoctorEarningsPane> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  int _sortIdx = 2; // Default: highest gross first.
  bool _sortAsc = false;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  String _doctorName(int profileId, List<ClinicMember> members) {
    for (final m in members) {
      if (m.doctorProfileId == profileId) return m.displayName;
    }
    return '#$profileId';
  }

  List<DoctorEarningRow> _apply(
    List<DoctorEarningRow> rows,
    List<ClinicMember> members,
  ) {
    Iterable<DoctorEarningRow> out = rows;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((d) {
        final name = _doctorName(d.doctorProfileId, members).toLowerCase();
        return name.contains(s) || d.doctorProfileId.toString().contains(s);
      });
    }
    final list = out.toList();
    int cmp(DoctorEarningRow a, DoctorEarningRow b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = _doctorName(a.doctorProfileId, members)
              .toLowerCase()
              .compareTo(
                  _doctorName(b.doctorProfileId, members).toLowerCase());
          break;
        case 1:
          c = a.visitCount.compareTo(b.visitCount);
          break;
        case 2:
          c = a.grossMinor.compareTo(b.grossMinor);
          break;
        case 3:
          c = a.collectedMinor.compareTo(b.collectedMinor);
          break;
        case 4:
          c = a.outstandingMinor.compareTo(b.outstandingMinor);
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
    final async = ref.watch(clinicDoctorEarningsProvider(widget.clinicId));
    final members =
        ref.watch(clinicMembersProvider(widget.clinicId)).valueOrNull ??
            <ClinicMember>[];
    return async.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (rows) {
        final filtered = _apply(rows, members);
        final toolbar = ClinicTableSearchField(
          controller: _searchCtrl,
          hint: l10n.translate('clinicEarningsSearchHint'),
          onChanged: (v) => setState(() => _search = v),
        );

        final Widget body = rows.isEmpty || filtered.isEmpty
            ? ClinicTableEmpty(
                l10n.translate('clinicFinanceNoLedgerRows'))
            : clinicDataTable(
                context: context,
                sortColumnIndex: _sortIdx,
                sortAscending: _sortAsc,
                columns: [
                  DataColumn(
                    label: Text(l10n.translate('clinicEarningsColDoctor')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicEarningsColVisits')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicEarningsColGross')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label:
                        Text(l10n.translate('clinicEarningsColCollected')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(
                        l10n.translate('clinicEarningsColOutstanding')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                ],
                rows: filtered.map((d) {
                  final name = _doctorName(d.doctorProfileId, members);
                  return DataRow(
                    cells: [
                      DataCell(Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          CircleAvatar(
                            radius: 14,
                            backgroundColor: AppColors.primaryTeal
                                .withValues(alpha: 0.15),
                            child: Text(
                              name.isNotEmpty
                                  ? name[0].toUpperCase()
                                  : '?',
                              style: const TextStyle(
                                color: AppColors.primaryTeal,
                                fontWeight: FontWeight.bold,
                                fontSize: 12,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                          Text(
                            name,
                            style: const TextStyle(
                                fontWeight: FontWeight.w500),
                          ),
                          const SizedBox(width: 6),
                          Text(
                            '#${d.doctorProfileId}',
                            style: TextStyle(
                                color: Colors.grey.shade500, fontSize: 12),
                          ),
                        ],
                      )),
                      DataCell(Text('${d.visitCount}')),
                      DataCell(Text(
                        _formatMoney(d.grossMinor, 'UZS'),
                        style:
                            const TextStyle(fontWeight: FontWeight.w600),
                      )),
                      DataCell(Text(
                        _formatMoney(d.collectedMinor, 'UZS'),
                        style: TextStyle(color: Colors.green.shade700),
                      )),
                      DataCell(Text(
                        _formatMoney(d.outstandingMinor, 'UZS'),
                        style: TextStyle(
                          color: d.outstandingMinor > 0
                              ? Colors.red.shade700
                              : Colors.grey.shade600,
                          fontWeight: FontWeight.w600,
                        ),
                      )),
                    ],
                  );
                }).toList(),
              );

        return ClinicTableShell(toolbar: toolbar, body: body);
      },
    );
  }
}

class _RecordsView extends ConsumerStatefulWidget {
  final int clinicId;
  const _RecordsView({required this.clinicId});

  @override
  ConsumerState<_RecordsView> createState() => _RecordsViewState();
}

class _RecordsViewState extends ConsumerState<_RecordsView> {
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
      error: (e, _) => Center(child: Text('Error: $e')),
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
                    options: const [
                      (value: 'ALL', label: 'All'),
                      (value: 'ISSUED', label: 'Issued'),
                      (value: 'PAID', label: 'Paid'),
                      (value: 'PARTIALLY_PAID', label: 'Partial'),
                      (value: 'OVERDUE', label: 'Overdue'),
                      (value: 'VOID', label: 'Void'),
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
                      DataCell(Text(_formatDate(r.createdAt))),
                      DataCell(Text(r.recordType)),
                      DataCell(Text(r.recordNumber ?? '#${r.id}')),
                      DataCell(Text(
                        _formatMoney(r.totalMinor, r.currency),
                        style: const TextStyle(
                            fontWeight: FontWeight.w600),
                      )),
                      DataCell(Text(
                        _formatMoney(r.paidMinor, r.currency),
                        style: TextStyle(color: Colors.green.shade700),
                      )),
                      DataCell(Text(
                        _formatMoney(r.remainingMinor, r.currency),
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
                              : r.status,
                          style: TextStyle(
                            color: color,
                            fontSize: 11,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      )),
                      DataCell(Text(
                          r.dueDate != null ? _formatDate(r.dueDate!) : '—')),
                    ],
                  );
                }).toList(),
              );
        }

        return ClinicTableShell(toolbar: toolbar, body: body);
      },
    );
  }
}

class _PaymentsView extends ConsumerStatefulWidget {
  final int clinicId;
  const _PaymentsView({required this.clinicId});

  @override
  ConsumerState<_PaymentsView> createState() => _PaymentsViewState();
}

class _PaymentsViewState extends ConsumerState<_PaymentsView> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  String _methodFilter = 'ALL';
  int _sortIdx = 1; // Default: most recent payments first.
  bool _sortAsc = false;

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  Color _methodColor(String method) {
    switch (method) {
      case 'CASH':
        return Colors.green;
      case 'CARD_EXTERNAL':
        return Colors.blue;
      case 'TRANSFER':
        return Colors.purple;
      default:
        return Colors.grey;
    }
  }

  IconData _methodIcon(String method) {
    switch (method) {
      case 'CASH':
        return Icons.payments_outlined;
      case 'CARD_EXTERNAL':
        return Icons.credit_card;
      case 'TRANSFER':
        return Icons.account_balance;
      default:
        return Icons.receipt_long;
    }
  }

  List<PaymentHistoryItem> _apply(List<PaymentHistoryItem> rows) {
    Iterable<PaymentHistoryItem> out = rows;
    if (_search.trim().isNotEmpty) {
      final s = _search.trim().toLowerCase();
      out = out.where((p) {
        final pid = (p.patientId != null ? '#${p.patientId}' : '');
        final did = (p.doctorProfileId != null ? '#${p.doctorProfileId}' : '');
        final planTitle =
            '${(p.treatmentPlanTitle ?? '').toLowerCase()} #${p.treatmentPlanId}';
        final patientName = (p.patientName ?? '').toLowerCase();
        final doctorName = (p.doctorName ?? '').toLowerCase();
        return p.method.toLowerCase().contains(s) ||
            (p.memo ?? '').toLowerCase().contains(s) ||
            p.id.toString().contains(s) ||
            p.treatmentPlanId.toString().contains(s) ||
            patientName.contains(s) ||
            doctorName.contains(s) ||
            planTitle.contains(s) ||
            pid.contains(s) ||
            did.contains(s);
      });
    }
    if (_methodFilter != 'ALL') {
      out = out.where((p) => p.method == _methodFilter);
    }
    final list = out.toList();
    String planSort(PaymentHistoryItem p) {
      final t = (p.treatmentPlanTitle ?? '').trim().toLowerCase();
      final key = t.isNotEmpty ? t : '${p.treatmentPlanId}';
      return '$key:${p.treatmentPlanId}';
    }

    int cmp(PaymentHistoryItem a, PaymentHistoryItem b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.id.compareTo(b.id);
          break;
        case 1:
          c = a.recordedAt.compareTo(b.recordedAt);
          break;
        case 2:
          c = ((a.patientName ?? '').toLowerCase())
              .compareTo((b.patientName ?? '').toLowerCase());
          break;
        case 3:
          c = ((a.doctorName ?? '').toLowerCase())
              .compareTo((b.doctorName ?? '').toLowerCase());
          break;
        case 4:
          c = planSort(a).compareTo(planSort(b));
          break;
        case 5:
          c = a.method.compareTo(b.method);
          break;
        case 6:
          c = a.amountMinor.compareTo(b.amountMinor);
          break;
        case 7:
          c = (a.memo ?? '').compareTo(b.memo ?? '');
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  String _paymentPlanCellLabel(PaymentHistoryItem p) {
    final t = p.treatmentPlanTitle?.trim();
    if (t != null && t.isNotEmpty) {
      return '${p.treatmentPlanTitle!.trim()} (#${p.treatmentPlanId})';
    }
    return '#${p.treatmentPlanId}';
  }

  String _paymentPatientLabel(PaymentHistoryItem p, AppLocalizations l10n) {
    final n = p.patientName?.trim();
    if (n != null && n.isNotEmpty) return n;
    final id = p.patientId;
    if (id != null && id != 0) {
      return '${l10n.translate('patient')} #$id';
    }
    return '—';
  }

  String _paymentDoctorLabel(PaymentHistoryItem p, AppLocalizations l10n) {
    final n = p.doctorName?.trim();
    if (n != null && n.isNotEmpty) return n;
    final id = p.doctorProfileId;
    if (id != null && id != 0) {
      return '${l10n.translate('doctor')} #$id';
    }
    return '—';
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final paymentsAsync =
        ref.watch(clinicPaymentHistoryProvider(widget.clinicId));

    return paymentsAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('Error: $e')),
      data: (payments) {
        final filtered = _apply(payments);
        final totalsMinor =
            filtered.fold<int>(0, (a, PaymentHistoryItem p) => a + p.amountMinor);
        final currency = filtered.isEmpty
            ? (payments.isEmpty ? 'UZS' : payments.first.currency)
            : filtered.first.currency;

        final toolbar = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: ClinicTableSearchField(
                    controller: _searchCtrl,
                    hint: l10n.translate('clinicPaymentsSearchHint'),
                    onChanged: (v) => setState(() => _search = v),
                  ),
                ),
                if (filtered.isNotEmpty) ...[
                  const SizedBox(width: 12),
                  Padding(
                    padding: const EdgeInsets.only(top: 2),
                    child: Tooltip(
                      message: l10n.translate('clinicPaymentsTotals'),
                      child: Chip(
                        avatar:
                            Icon(Icons.summarize_outlined, size: 18, color: Colors.grey.shade700),
                        padding: const EdgeInsets.symmetric(horizontal: 8),
                        labelPadding: EdgeInsets.zero,
                        label: Text(
                          '${filtered.length} · ${_formatMoney(totalsMinor, currency)}',
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                    ),
                  ),
                ],
              ],
            ),
            const SizedBox(height: 8),
            ClinicFilterChips<String>(
              selected: _methodFilter,
              onSelected: (v) => setState(() => _methodFilter = v),
              options: const [
                (value: 'ALL', label: 'All'),
                (value: 'CASH', label: 'Cash'),
                (value: 'CARD_EXTERNAL', label: 'Card'),
                (value: 'TRANSFER', label: 'Transfer'),
                (value: 'OTHER', label: 'Other'),
              ],
            ),
          ],
        );

        final Widget body;
        if (payments.isEmpty) {
          body =
              ClinicTableEmpty(l10n.translate('clinicFinanceNoPayments'));
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
                    label: Text(l10n.translate('clinicPaymentsColId')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColDate')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColPatient')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColDoctor')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColPlan')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColMethod')),
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColAmount')),
                    numeric: true,
                    onSort: _onSort,
                  ),
                  DataColumn(
                    label: Text(l10n.translate('clinicPaymentsColMemo')),
                    onSort: _onSort,
                  ),
                ],
                rows: filtered.map((p) {
                  final color = _methodColor(p.method);
                  final planText = _paymentPlanCellLabel(p);
                  final patientText = _paymentPatientLabel(p, l10n);
                  final doctorText = _paymentDoctorLabel(p, l10n);
                  return DataRow(
                    cells: [
                      DataCell(Text('#${p.id}')),
                      DataCell(Text(_formatDate(p.recordedAt))),
                      DataCell(Text(
                        patientText,
                        style: const TextStyle(fontWeight: FontWeight.w500),
                      )),
                      DataCell(Text(doctorText)),
                      DataCell(Text(planText)),
                      DataCell(Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(_methodIcon(p.method),
                              size: 16, color: color),
                          const SizedBox(width: 6),
                          Text(p.method),
                        ],
                      )),
                      DataCell(Text(
                        _formatMoney(p.amountMinor, p.currency),
                        style: TextStyle(
                          color: Colors.green.shade700,
                          fontWeight: FontWeight.w600,
                        ),
                      )),
                      DataCell(
                        ConstrainedBox(
                          constraints:
                              const BoxConstraints(maxWidth: 280),
                          child: Text(
                            p.memo ?? '—',
                            overflow: TextOverflow.ellipsis,
                            maxLines: 2,
                          ),
                        ),
                      ),
                    ],
                  );
                }).toList(),
              );
        }

        return ClinicTableShell(toolbar: toolbar, body: body);
      },
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
