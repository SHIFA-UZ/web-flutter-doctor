import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/clinic/application/finance_export_service.dart';
import 'package:shifa_doc_app_v1/features/clinic/pdf/finance_report_pdf_service.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_revenue_share_ui.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_month.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';

import 'finance_shared.dart';
class FinanceDoctorEarningsPane extends ConsumerStatefulWidget {
  final int clinicId;
  final bool isActive;

  const FinanceDoctorEarningsPane({
    super.key,
    required this.clinicId,
    required this.isActive,
  });

  @override
  ConsumerState<FinanceDoctorEarningsPane> createState() =>
      FinanceDoctorEarningsPaneState();
}

class FinanceDoctorEarningsPaneState extends ConsumerState<FinanceDoctorEarningsPane> {
  final TextEditingController _searchCtrl = TextEditingController();
  String _search = '';
  int _sortIdx = 2; // Default: highest gross first.
  bool _sortAsc = false;
  bool _refreshing = false;
  bool _initialLoading = true;
  Object? _loadError;
  List<DoctorEarningRow> _rows = const [];

  Future<void> _loadEarnings() async {
    if (_refreshing) return;
    setState(() {
      _refreshing = true;
      _loadError = null;
    });
    try {
      final month =
          ref.read(clinicFinanceMonthFilterProvider(widget.clinicId));
      final clinic = ref.read(selectedClinicProvider);
      String? fromIso;
      String? toIso;
      if (month != null) {
        final range = monthRangeUtcInTimezone(
          month.year,
          month.month,
          clinic?.timeZone,
        );
        fromIso = range.fromUtc.toIso8601String();
        toIso = range.toUtc.toIso8601String();
      }
      final rows = await fetchDoctorEarnings(
        ref,
        clinicId: widget.clinicId,
        fromIso: fromIso,
        toIso: toIso,
      );
      if (!mounted) return;
      setState(() {
        _rows = rows;
        _initialLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _loadError = e;
        _initialLoading = false;
      });
    } finally {
      if (mounted) setState(() => _refreshing = false);
    }
  }

  @override
  void initState() {
    super.initState();
    if (widget.isActive) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _loadEarnings();
      });
    }
  }

  @override
  void didUpdateWidget(covariant FinanceDoctorEarningsPane oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.isActive && !oldWidget.isActive) {
      _loadEarnings();
    }
  }

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

  ClinicMember? _memberFor(int profileId, List<ClinicMember> members) {
    for (final m in members) {
      if (m.doctorProfileId == profileId) return m;
    }
    return null;
  }

  Future<void> _editMemberShare(ClinicMember member) async {
    await showDoctorRevenueShareDialog(
      context: context,
      ref: ref,
      clinicId: widget.clinicId,
      member: member,
    );
    if (mounted) _loadEarnings();
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

  Future<void> _exportCsv(
    AppLocalizations l10n,
    List<DoctorEarningRow> rows,
    List<ClinicMember> members,
    String currency,
  ) async {
    if (rows.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('clinicFinanceExportNoRows'))),
      );
      return;
    }
    final header = [
      l10n.translate('clinicEarningsColDoctor'),
      l10n.translate('clinicEarningsColVisits'),
      l10n.translate('clinicEarningsColGross'),
      l10n.translate('clinicEarningsColCollected'),
      l10n.translate('clinicEarningsColOutstanding'),
      l10n.translate('clinicEarningsColSharePercent'),
      l10n.translate('clinicEarningsColDoctorShareGross'),
      l10n.translate('clinicEarningsColClinicShareGross'),
      l10n.translate('clinicEarningsColDoctorShareCollected'),
      l10n.translate('clinicEarningsColClinicShareCollected'),
    ].join(',');
    final sb = StringBuffer(header);
    for (final row in rows) {
      sb.writeln();
      sb.write([
        financeCsvCell(_doctorName(row.doctorProfileId, members)),
        row.visitCount,
        financeCsvAmount(row.grossMinor, currency),
        financeCsvAmount(row.collectedMinor, currency),
        financeCsvAmount(row.outstandingMinor, currency),
        row.revenueSharePercent ?? '',
        row.doctorShareGrossMinor != null
            ? financeCsvAmount(row.doctorShareGrossMinor!, currency)
            : '',
        row.clinicShareGrossMinor != null
            ? financeCsvAmount(row.clinicShareGrossMinor!, currency)
            : '',
        row.doctorShareCollectedMinor != null
            ? financeCsvAmount(row.doctorShareCollectedMinor!, currency)
            : '',
        row.clinicShareCollectedMinor != null
            ? financeCsvAmount(row.clinicShareCollectedMinor!, currency)
            : '',
      ].join(','));
    }
    try {
      await downloadFinanceCsv(
        'doctor_earnings_${DateTime.now().millisecondsSinceEpoch}.csv',
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
    List<DoctorEarningRow> rows,
    List<ClinicMember> members,
    String currency,
    String subtitle,
  ) async {
    if (rows.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(l10n.translate('clinicFinanceExportNoRows'))),
      );
      return;
    }
    try {
      final bytes = await generateFinanceTablePdf(
        title: l10n.translate('clinicFinanceDoctorEarnings'),
        subtitle: subtitle,
        headers: [
          l10n.translate('clinicEarningsColDoctor'),
          l10n.translate('clinicEarningsColVisits'),
          l10n.translate('clinicEarningsColGross'),
          l10n.translate('clinicEarningsColCollected'),
          l10n.translate('clinicEarningsColOutstanding'),
          l10n.translate('clinicEarningsColSharePercent'),
          l10n.translate('clinicEarningsColDoctorShareGross'),
          l10n.translate('clinicEarningsColClinicShareGross'),
          l10n.translate('clinicEarningsColDoctorShareCollected'),
          l10n.translate('clinicEarningsColClinicShareCollected'),
        ],
        rows: rows
            .map(
              (row) => [
                _doctorName(row.doctorProfileId, members),
                '${row.visitCount}',
                formatFinanceMoney(row.grossMinor, currency),
                formatFinanceMoney(row.collectedMinor, currency),
                formatFinanceMoney(row.outstandingMinor, currency),
                formatOptionalPercent(row.revenueSharePercent),
                formatOptionalFinanceMoney(row.doctorShareGrossMinor, currency),
                formatOptionalFinanceMoney(row.clinicShareGrossMinor, currency),
                formatOptionalFinanceMoney(
                  row.doctorShareCollectedMinor,
                  currency,
                ),
                formatOptionalFinanceMoney(
                  row.clinicShareCollectedMinor,
                  currency,
                ),
              ],
            )
            .toList(),
      );
      await downloadFinancePdf(
        bytes,
        'doctor_earnings_${DateTime.now().millisecondsSinceEpoch}.pdf',
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

  @override
  Widget build(BuildContext context) {
    ref.listen<int>(
      clinicFinanceRefreshTickProvider(widget.clinicId),
      (prev, next) {
        if (prev != null && widget.isActive) _loadEarnings();
      },
    );
    ref.listen<({int year, int month})?>(
      clinicFinanceMonthFilterProvider(widget.clinicId),
      (prev, next) {
        if (prev != next && widget.isActive) _loadEarnings();
      },
    );
    final l10n = AppLocalizations.of(context)!;
    final clinic = ref.watch(selectedClinicProvider);
    final canManage = canManageClinicFinanceFor(clinic);
    final members =
        ref.watch(clinicMembersProvider(widget.clinicId)).valueOrNull ??
            <ClinicMember>[];
    final currency = ref.watch(clinicFinanceCurrencyProvider(widget.clinicId));

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
              onPressed: _loadEarnings,
              icon: const Icon(Icons.refresh),
              label: Text(l10n.refresh),
            ),
          ],
        ),
      );
    }

    final filtered = _apply(_rows, members);
    final month = ref.watch(clinicFinanceMonthFilterProvider(widget.clinicId));
    final configuredRows =
        filtered.where((row) => row.hasRevenueShare).toList();
    final splitTotals = configuredRows.isEmpty
        ? null
        : (
            doctorGross: configuredRows.fold<int>(
              0,
              (sum, row) => sum + (row.doctorShareGrossMinor ?? 0),
            ),
            clinicGross: configuredRows.fold<int>(
              0,
              (sum, row) => sum + (row.clinicShareGrossMinor ?? 0),
            ),
            doctorCollected: configuredRows.fold<int>(
              0,
              (sum, row) => sum + (row.doctorShareCollectedMinor ?? 0),
            ),
            clinicCollected: configuredRows.fold<int>(
              0,
              (sum, row) => sum + (row.clinicShareCollectedMinor ?? 0),
            ),
          );
    final earningsHint = month == null
        ? l10n.translate('clinicFinanceDoctorEarningsHint')
        : l10n.translate('clinicFinanceDoctorEarningsHintMonth');
    final toolbar = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        FinanceMonthFilterBar(clinicId: widget.clinicId),
        if (canManage && clinic != null) ...[
          const SizedBox(height: 8),
          Material(
            color: AppColors.primaryTeal.withValues(alpha: 0.08),
            borderRadius: BorderRadius.circular(8),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(
                    Icons.info_outline,
                    size: 18,
                    color: AppColors.primaryTeal.withValues(alpha: 0.85),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      l10n.translate('clinicFinanceEarningsRevenueShareBanner'),
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade800,
                      ),
                    ),
                  ),
                  const SizedBox(width: 8),
                  OutlinedButton.icon(
                    onPressed: () async {
                      await showDefaultClinicRevenueShareDialog(
                        context: context,
                        ref: ref,
                        clinic: clinic,
                      );
                      if (mounted) _loadEarnings();
                    },
                    icon: const Icon(Icons.tune, size: 16),
                    label: Text(
                      l10n.translate('clinicFinanceConfigureDefaultShare'),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
        const SizedBox(height: 8),
        Row(
          children: [
            Expanded(
              child: Text(
                earningsHint,
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
              ),
            ),
            IconButton(
              tooltip: l10n.refresh,
              onPressed: _refreshing ? null : _loadEarnings,
              icon: _refreshing
                  ? SizedBox(
                      width: 20,
                      height: 20,
                      child: CircularProgressIndicator(
                        strokeWidth: 2,
                        color: AppColors.primaryTeal,
                      ),
                    )
                  : const Icon(Icons.refresh),
            ),
            FinanceExportButton(
              exportLabel: l10n.translate('clinicFinanceExport'),
              csvLabel: l10n.translate('clinicFinanceExportCsv'),
              pdfLabel: l10n.translate('clinicFinanceExportPdf'),
              onExportCsv: () => _exportCsv(l10n, filtered, members, currency),
              onExportPdf: () =>
                  _exportPdf(l10n, filtered, members, currency, earningsHint),
            ),
          ],
        ),
        const SizedBox(height: 8),
        ClinicTableSearchField(
          controller: _searchCtrl,
          hint: l10n.translate('clinicEarningsSearchHint'),
          onChanged: (v) => setState(() => _search = v),
        ),
      ],
    );

    final Widget body = _rows.isEmpty || filtered.isEmpty
        ? ClinicTableEmpty(l10n.translate('clinicFinanceNoLedgerRows'))
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
                label: Text(l10n.translate('clinicEarningsColCollected')),
                numeric: true,
                onSort: _onSort,
              ),
              DataColumn(
                label: Text(l10n.translate('clinicEarningsColOutstanding')),
                numeric: true,
                onSort: _onSort,
              ),
              DataColumn(
                label: Text(l10n.translate('clinicEarningsColSharePercent')),
                numeric: true,
              ),
              if (canManage)
                DataColumn(
                  label: Text(l10n.translate('clinicDoctorsColActions')),
                ),
              DataColumn(
                label: Text(l10n.translate('clinicEarningsColDoctorShareGross')),
                numeric: true,
              ),
              DataColumn(
                label: Text(l10n.translate('clinicEarningsColClinicShareGross')),
                numeric: true,
              ),
              DataColumn(
                label: Text(
                  l10n.translate('clinicEarningsColDoctorShareCollected'),
                ),
                numeric: true,
              ),
              DataColumn(
                label: Text(
                  l10n.translate('clinicEarningsColClinicShareCollected'),
                ),
                numeric: true,
              ),
            ],
            rows: filtered.map((d) {
              final name = _doctorName(d.doctorProfileId, members);
              final member = _memberFor(d.doctorProfileId, members);
              final canEditShare = canManage &&
                  member != null &&
                  canEditMemberRevenueShare(member);
              return DataRow(
                cells: [
                  DataCell(Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      CircleAvatar(
                        radius: 14,
                        backgroundColor:
                            AppColors.primaryTeal.withValues(alpha: 0.15),
                        child: Text(
                          name.isNotEmpty ? name[0].toUpperCase() : '?',
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
                        style: const TextStyle(fontWeight: FontWeight.w500),
                      ),
                      const SizedBox(width: 6),
                      Text(
                        '#${d.doctorProfileId}',
                        style: TextStyle(
                          color: Colors.grey.shade500,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  )),
                  DataCell(Text('${d.visitCount}')),
                  DataCell(Text(
                    formatFinanceMoney(d.grossMinor, currency),
                    style: const TextStyle(fontWeight: FontWeight.w600),
                  )),
                  DataCell(Text(
                    formatFinanceMoney(d.collectedMinor, currency),
                    style: TextStyle(color: Colors.green.shade700),
                  )),
                  DataCell(Text(
                    formatFinanceMoney(d.outstandingMinor, currency),
                    style: TextStyle(
                      color: d.outstandingMinor > 0
                          ? Colors.red.shade700
                          : Colors.grey.shade600,
                      fontWeight: FontWeight.w600,
                    ),
                  )),
                  DataCell(
                    canEditShare
                        ? InkWell(
                            onTap: () => _editMemberShare(member),
                            child: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Text(formatOptionalPercent(d.revenueSharePercent)),
                                const SizedBox(width: 4),
                                Icon(
                                  Icons.edit_outlined,
                                  size: 14,
                                  color: AppColors.primaryTeal.withValues(alpha: 0.85),
                                ),
                              ],
                            ),
                          )
                        : Text(formatOptionalPercent(d.revenueSharePercent)),
                  ),
                  DataCell(Text(
                    formatOptionalFinanceMoney(d.doctorShareGrossMinor, currency),
                  )),
                  DataCell(Text(
                    formatOptionalFinanceMoney(d.clinicShareGrossMinor, currency),
                  )),
                  DataCell(Text(
                    formatOptionalFinanceMoney(
                      d.doctorShareCollectedMinor,
                      currency,
                    ),
                    style: TextStyle(color: Colors.green.shade700),
                  )),
                  DataCell(Text(
                    formatOptionalFinanceMoney(
                      d.clinicShareCollectedMinor,
                      currency,
                    ),
                  )),
                  if (canManage)
                    DataCell(
                      canEditShare
                          ? IconButton(
                              tooltip: l10n.translate(
                                'clinicFinanceEarningsEditShare',
                              ),
                              icon: const Icon(Icons.percent, size: 20),
                              onPressed: () => _editMemberShare(member),
                            )
                          : const SizedBox.shrink(),
                    ),
                ],
              );
            }).toList(),
          );

    final Widget footer = splitTotals == null
        ? const SizedBox.shrink()
        : Padding(
            padding: const EdgeInsets.only(top: 12),
            child: Card(
              elevation: 0,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(12),
                side: BorderSide(color: Colors.grey.shade200),
              ),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      l10n.translate('clinicEarningsSplitTotals'),
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      '${l10n.translate('clinicFinanceDoctorShareCollected')}: '
                      '${formatFinanceMoney(splitTotals.doctorCollected, currency)} · '
                      '${l10n.translate('clinicFinanceClinicShareCollected')}: '
                      '${formatFinanceMoney(splitTotals.clinicCollected, currency)}',
                    ),
                    Text(
                      '${l10n.translate('clinicFinanceDoctorShareGross')}: '
                      '${formatFinanceMoney(splitTotals.doctorGross, currency)} · '
                      '${l10n.translate('clinicFinanceClinicShareGross')}: '
                      '${formatFinanceMoney(splitTotals.clinicGross, currency)}',
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              ),
            ),
          );

    return RefreshIndicator(
      onRefresh: _loadEarnings,
      child: ClinicTableShell(
        toolbar: toolbar,
        body: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Expanded(child: body),
            footer,
          ],
        ),
      ),
    );
  }
}
