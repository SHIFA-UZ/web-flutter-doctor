import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/clinic/application/finance_export_service.dart';
import 'package:shifa_doc_app_v1/features/clinic/pdf/finance_report_pdf_service.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_month.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

import 'finance_shared.dart';

class FinanceAppointmentLedgerView extends ConsumerStatefulWidget {
  final int clinicId;
  const FinanceAppointmentLedgerView({super.key, required this.clinicId});

  @override
  ConsumerState<FinanceAppointmentLedgerView> createState() =>
      FinanceAppointmentLedgerViewState();
}

class FinanceAppointmentLedgerViewState
    extends ConsumerState<FinanceAppointmentLedgerView> {
  static const _pageSize = 20;

  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  String _statusFilter = 'ALL';
  int _sortIdx = 0;
  bool _sortAsc = false;

  int _page = 0;
  int? _totalElements;
  bool _hasMore = false;
  bool _initialLoading = true;
  bool _loadingMore = false;
  bool _refreshing = false;
  Object? _loadError;
  List<AppointmentLedgerRowDto> _rows = const [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _loadLedger(reset: true);
    });
  }

  @override
  void dispose() {
    _searchCtrl.dispose();
    super.dispose();
  }

  Future<void> _loadLedger({bool reset = false}) async {
    if (_refreshing || _loadingMore) return;
    if (reset) {
      setState(() {
        _refreshing = true;
        _loadError = null;
        _page = 0;
      });
    } else {
      setState(() => _loadingMore = true);
    }

    try {
      final range = financeMonthRangeIso(ref, widget.clinicId);
      final pageToFetch = reset ? 0 : _page + 1;
      final page = await fetchAppointmentLedgerPage(
        ref,
        clinicId: widget.clinicId,
        fromIso: range.fromIso,
        toIso: range.toIso,
        page: pageToFetch,
        size: _pageSize,
      );
      if (!mounted) return;

      final content = page['content'] as List<dynamic>? ?? [];
      final fetched = content
          .whereType<Map>()
          .map(
            (r) => AppointmentLedgerRowDto.fromJson(
              Map<String, dynamic>.from(r),
            ),
          )
          .toList();
      final total = page['totalElements'] is num
          ? (page['totalElements'] as num).toInt()
          : null;

      setState(() {
        if (reset) {
          _rows = fetched;
          _page = 0;
        } else {
          _rows = [..._rows, ...fetched];
          _page = pageToFetch;
        }
        _totalElements = total;
        _hasMore = total != null
            ? _rows.length < total
            : fetched.length >= _pageSize;
        _initialLoading = false;
        _loadError = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loadError = e;
        _initialLoading = false;
      });
    } finally {
      if (mounted) {
        setState(() {
          _refreshing = false;
          _loadingMore = false;
        });
      }
    }
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

  Future<void> _exportCsv(
    AppLocalizations l10n,
    List<AppointmentLedgerRowDto> rows,
  ) async {
    if (rows.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('clinicFinanceExportNoRows'))),
      );
      return;
    }
    final header = [
      l10n.translate('clinicLedgerColDate'),
      l10n.translate('clinicLedgerColPatient'),
      l10n.translate('clinicLedgerColDoctor'),
      l10n.translate('clinicLedgerColPlanId'),
      l10n.translate('clinicLedgerColServices'),
      l10n.translate('clinicLedgerColTotal'),
      l10n.translate('clinicLedgerColStatus'),
    ].join(',');
    final sb = StringBuffer(header);
    for (final row in rows) {
      sb.writeln();
      sb.write([
        financeCsvCell(formatFinanceDate(row.startAt)),
        financeCsvCell(row.patientName),
        financeCsvCell(row.doctorName),
        row.treatmentPlanId,
        row.services.length,
        financeCsvAmount(row.visitTotalMinor, row.currency),
        financeCsvCell(
          l10n.clinicPaymentStatusLabel(row.planSimplePaymentStatus),
        ),
      ].join(','));
    }
    try {
      await downloadFinanceCsv(
        'appointment_ledger_${DateTime.now().millisecondsSinceEpoch}.csv',
        sb.toString(),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.translate('clinicFinanceExportFailed')}: $e'),
        ),
      );
    }
  }

  Future<void> _exportPdf(
    AppLocalizations l10n,
    List<AppointmentLedgerRowDto> rows,
  ) async {
    if (rows.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('clinicFinanceExportNoRows'))),
      );
      return;
    }
    try {
      final bytes = await generateFinanceTablePdf(
        title: l10n.translate('clinicFinanceByAppointment'),
        subtitle: '',
        headers: [
          l10n.translate('clinicLedgerColDate'),
          l10n.translate('clinicLedgerColPatient'),
          l10n.translate('clinicLedgerColDoctor'),
          l10n.translate('clinicLedgerColPlanId'),
          l10n.translate('clinicLedgerColServices'),
          l10n.translate('clinicLedgerColTotal'),
          l10n.translate('clinicLedgerColStatus'),
        ],
        rows: rows
            .map(
              (row) => [
                formatFinanceDate(row.startAt),
                row.patientName,
                row.doctorName,
                '#${row.treatmentPlanId}',
                '${row.services.length}',
                formatFinanceMoney(row.visitTotalMinor, row.currency),
                l10n.clinicPaymentStatusLabel(row.planSimplePaymentStatus),
              ],
            )
            .toList(),
      );
      await downloadFinancePdf(
        bytes,
        'appointment_ledger_${DateTime.now().millisecondsSinceEpoch}.pdf',
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('${l10n.translate('clinicFinanceExportFailed')}: $e'),
        ),
      );
    }
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
        title: Text('${row.patientName} · ${formatFinanceDate(row.startAt)}'),
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
                      formatFinanceMoney(s.lineTotalMinor, row.currency),
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
                        formatFinanceMoney(row.visitTotalMinor, row.currency),
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
    refreshClinicFinancialData(ref, widget.clinicId);
    _loadLedger(reset: true);
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
    final label = l10n.clinicPaymentStatusLabel(row.planSimplePaymentStatus);
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
    ref.listen<int>(
      clinicFinanceRefreshTickProvider(widget.clinicId),
      (prev, next) {
        if (prev != null) _loadLedger(reset: true);
      },
    );
    ref.listen<({int year, int month})?>(
      clinicFinanceMonthFilterProvider(widget.clinicId),
      (prev, next) {
        if (prev != next) _loadLedger(reset: true);
      },
    );

    final l10n = AppLocalizations.of(context)!;
    final canAct = ref.watch(canManageFinanceProvider);
    final filtered = _apply(_rows);

    if (_initialLoading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_loadError != null && _rows.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text('${l10n.error}: $_loadError'),
            const SizedBox(height: 12),
            FilledButton.icon(
              onPressed: () => _loadLedger(reset: true),
              icon: const Icon(Icons.refresh),
              label: Text(l10n.refresh),
            ),
          ],
        ),
      );
    }

    final countLabel = _totalElements != null
        ? l10n
            .translate('clinicFinanceShowingRows')
            .replaceAll('{{shown}}', '${_rows.length}')
            .replaceAll('{{total}}', '$_totalElements')
        : null;

    final toolbar = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        FinanceMonthFilterBar(clinicId: widget.clinicId),
        const SizedBox(height: 8),
        Row(
          children: [
            if (countLabel != null)
              Expanded(
                child: Text(
                  countLabel,
                  style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                ),
              )
            else
              const Spacer(),
            IconButton(
              tooltip: l10n.refresh,
              onPressed: _refreshing ? null : () => _loadLedger(reset: true),
              icon: _refreshing
                  ? const SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : const Icon(Icons.refresh),
            ),
            FinanceExportButton(
              exportLabel: l10n.translate('clinicFinanceExport'),
              csvLabel: l10n.translate('clinicFinanceExportCsv'),
              pdfLabel: l10n.translate('clinicFinanceExportPdf'),
              onExportCsv: () => _exportCsv(l10n, filtered),
              onExportPdf: () => _exportPdf(l10n, filtered),
            ),
          ],
        ),
        const SizedBox(height: 8),
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
            (
              value: 'PAID',
              label: l10n.clinicPaymentStatusLabel('PAID'),
            ),
            (
              value: 'PARTIAL',
              label: l10n.clinicPaymentStatusLabel('PARTIAL'),
            ),
            (
              value: 'UNPAID',
              label: l10n.clinicPaymentStatusLabel('UNPAID'),
            ),
          ],
        ),
      ],
    );

    final table = _rows.isEmpty || filtered.isEmpty
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
              final color = _statusColor(row.planSimplePaymentStatus);
              return DataRow(
                cells: [
                  DataCell(Text(formatFinanceDate(row.startAt))),
                  DataCell(Text(
                    row.patientName,
                    style: const TextStyle(fontWeight: FontWeight.w500),
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
                    formatFinanceMoney(row.visitTotalMinor, row.currency),
                    style: const TextStyle(fontWeight: FontWeight.w600),
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
                      tooltip: l10n.translate('clinicLedgerViewServices'),
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

    final body = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(child: table),
        if (_hasMore)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 12),
            child: Center(
              child: _loadingMore
                  ? const SizedBox(
                      width: 24,
                      height: 24,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : OutlinedButton.icon(
                      onPressed: () => _loadLedger(reset: false),
                      icon: const Icon(Icons.expand_more),
                      label: Text(l10n.translate('clinicFinanceLoadMore')),
                    ),
            ),
          ),
      ],
    );

    return RefreshIndicator(
      onRefresh: () => _loadLedger(reset: true),
      child: ClinicTableShell(toolbar: toolbar, body: body),
    );
  }
}
