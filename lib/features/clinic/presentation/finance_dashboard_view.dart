import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/features/clinic/application/finance_export_service.dart';
import 'package:shifa_doc_app_v1/features/clinic/pdf/finance_report_pdf_service.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_month.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

import 'finance_shared.dart';

enum FinanceDashboardKpi { revenue, outstanding, overdue, collectionRate }

class FinanceDashboardView extends ConsumerStatefulWidget {
  final int clinicId;
  const FinanceDashboardView({super.key, required this.clinicId});

  @override
  ConsumerState<FinanceDashboardView> createState() => FinanceDashboardViewState();
}

class FinanceDashboardViewState extends ConsumerState<FinanceDashboardView> {
  FinanceDashboardKpi _selectedKpi = FinanceDashboardKpi.revenue;

  Future<void> _exportDashboardCsv(
    AppLocalizations l10n,
    FinanceDashboardStats stats,
    String monthLabel,
  ) async {
    final sb = StringBuffer()
      ..writeln('Metric,Value')
      ..writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceTotalRevenue'))},'
        '${financeCsvAmount(stats.totalRevenueMinor, stats.currency)}',
      )
      ..writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceOutstanding'))},'
        '${financeCsvAmount(stats.outstandingMinor, stats.currency)}',
      )
      ..writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceOverdueCount'))},'
        '${stats.overdueCount}',
      )
      ..writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceCollectionRate'))},'
        '${(stats.collectionRate * 100).toStringAsFixed(1)}%',
      );
    if (stats.hasRevenueShareTotals) {
      sb.writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceDoctorShareCollected'))},'
        '${financeCsvAmount(stats.totalDoctorShareCollectedMinor!, stats.currency)}',
      );
      sb.writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceClinicShareCollected'))},'
        '${financeCsvAmount(stats.totalClinicShareCollectedMinor!, stats.currency)}',
      );
      sb.writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceDoctorShareGross'))},'
        '${financeCsvAmount(stats.totalDoctorShareGrossMinor!, stats.currency)}',
      );
      sb.writeln(
        '${financeCsvCell(l10n.translate('clinicFinanceClinicShareGross'))},'
        '${financeCsvAmount(stats.totalClinicShareGrossMinor!, stats.currency)}',
      );
    }
    if (monthLabel.isNotEmpty) {
      sb.writeln('Month,$monthLabel');
    }
    try {
      await downloadFinanceCsv(
        'finance_dashboard_${DateTime.now().millisecondsSinceEpoch}.csv',
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

  Future<void> _exportDashboardPdf(
    AppLocalizations l10n,
    FinanceDashboardStats stats,
    String monthLabel,
  ) async {
    try {
      final bytes = await generateFinanceTablePdf(
        title: l10n.translate('clinicFinanceDashboard'),
        subtitle: monthLabel,
        headers: [
          l10n.translate('clinicFinanceDashboard'),
          'Value',
        ],
        rows: [
          [
            l10n.translate('clinicFinanceTotalRevenue'),
            formatFinanceMoney(stats.totalRevenueMinor, stats.currency),
          ],
          [
            l10n.translate('clinicFinanceOutstanding'),
            formatFinanceMoney(stats.outstandingMinor, stats.currency),
          ],
          [
            l10n.translate('clinicFinanceOverdueCount'),
            '${stats.overdueCount}',
          ],
          [
            l10n.translate('clinicFinanceCollectionRate'),
            '${(stats.collectionRate * 100).toStringAsFixed(1)}%',
          ],
          if (stats.hasRevenueShareTotals) ...[
            [
              l10n.translate('clinicFinanceDoctorShareCollected'),
              formatFinanceMoney(
                stats.totalDoctorShareCollectedMinor!,
                stats.currency,
              ),
            ],
            [
              l10n.translate('clinicFinanceClinicShareCollected'),
              formatFinanceMoney(
                stats.totalClinicShareCollectedMinor!,
                stats.currency,
              ),
            ],
            [
              l10n.translate('clinicFinanceDoctorShareGross'),
              formatFinanceMoney(
                stats.totalDoctorShareGrossMinor!,
                stats.currency,
              ),
            ],
            [
              l10n.translate('clinicFinanceClinicShareGross'),
              formatFinanceMoney(
                stats.totalClinicShareGrossMinor!,
                stats.currency,
              ),
            ],
          ],
        ],
      );
      await downloadFinancePdf(
        bytes,
        'finance_dashboard_${DateTime.now().millisecondsSinceEpoch}.pdf',
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
    final l10n = AppLocalizations.of(context)!;
    final month = ref.watch(clinicFinanceMonthFilterProvider(widget.clinicId));
    final revenueHint = month == null
        ? l10n.translate('clinicFinanceTotalRevenueHint')
        : l10n.translate('clinicFinanceTotalRevenueHintMonth');
    final dashboardAsync =
        ref.watch(clinicFinanceDashboardProvider(widget.clinicId));
    final monthLabel = month == null
        ? l10n.translate('clinicFinanceMonthAllTime')
        : formatFinanceMonthLabel(context, month.year, month.month);

    return dashboardAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) => Center(child: Text('${l10n.error}: $e')),
      data: (stats) => RefreshIndicator(
        onRefresh: () async {
          refreshClinicFinancialData(ref, widget.clinicId);
        },
        child: SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    l10n.translate('clinicFinanceDashboard'),
                    style: const TextStyle(
                      fontSize: 20,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
                FinanceExportButton(
                  exportLabel: l10n.translate('clinicFinanceExport'),
                  csvLabel: l10n.translate('clinicFinanceExportCsv'),
                  pdfLabel: l10n.translate('clinicFinanceExportPdf'),
                  onExportCsv: () =>
                      _exportDashboardCsv(l10n, stats, monthLabel),
                  onExportPdf: () =>
                      _exportDashboardPdf(l10n, stats, monthLabel),
                ),
              ],
            ),
            const SizedBox(height: 12),
            FinanceMonthFilterBar(clinicId: widget.clinicId),
            const SizedBox(height: 16),
            Wrap(
              spacing: 16,
              runSpacing: 16,
              children: [
                FinanceKpiCard(
                  title: l10n.translate('clinicFinanceTotalRevenue'),
                  subtitle: revenueHint,
                  value: formatFinanceMoney(stats.totalRevenueMinor, stats.currency),
                  color: Colors.green,
                  selected: _selectedKpi == FinanceDashboardKpi.revenue,
                  onTap: () =>
                      setState(() => _selectedKpi = FinanceDashboardKpi.revenue),
                ),
                FinanceKpiCard(
                  title: l10n.translate('clinicFinanceOutstanding'),
                  value: formatFinanceMoney(stats.outstandingMinor, stats.currency),
                  color: Colors.orange,
                  selected: _selectedKpi == FinanceDashboardKpi.outstanding,
                  onTap: () =>
                      setState(() => _selectedKpi = FinanceDashboardKpi.outstanding),
                ),
                FinanceKpiCard(
                  title: l10n.translate('clinicFinanceOverdueCount'),
                  value: stats.overdueCount.toString(),
                  color: Colors.red,
                  selected: _selectedKpi == FinanceDashboardKpi.overdue,
                  onTap: () =>
                      setState(() => _selectedKpi = FinanceDashboardKpi.overdue),
                ),
                FinanceKpiCard(
                  title: l10n.translate('clinicFinanceCollectionRate'),
                  value: '${(stats.collectionRate * 100).toStringAsFixed(1)}%',
                  color: AppColors.primaryTeal,
                  selected: _selectedKpi == FinanceDashboardKpi.collectionRate,
                  onTap: () => setState(
                      () => _selectedKpi = FinanceDashboardKpi.collectionRate),
                ),
                if (month != null && stats.hasRevenueShareTotals) ...[
                  FinanceKpiCard(
                    title: l10n.translate('clinicFinanceDoctorShareCollected'),
                    subtitle: l10n.translate('clinicFinanceDoctorShareGross') +
                        ': ${formatFinanceMoney(stats.totalDoctorShareGrossMinor!, stats.currency)}',
                    value: formatFinanceMoney(
                      stats.totalDoctorShareCollectedMinor!,
                      stats.currency,
                    ),
                    color: Colors.blue,
                  ),
                  FinanceKpiCard(
                    title: l10n.translate('clinicFinanceClinicShareCollected'),
                    subtitle: l10n.translate('clinicFinanceClinicShareGross') +
                        ': ${formatFinanceMoney(stats.totalClinicShareGrossMinor!, stats.currency)}',
                    value: formatFinanceMoney(
                      stats.totalClinicShareCollectedMinor!,
                      stats.currency,
                    ),
                    color: Colors.deepPurple,
                  ),
                ],
              ],
            ),
            if (stats.doctorEarningsTop.isNotEmpty) ...[
              const SizedBox(height: 24),
              FinanceDashboardDoctorEarningsTop(
                clinicId: widget.clinicId,
                earnings: stats.doctorEarningsTop,
                currency: stats.currency,
              ),
            ],
            const SizedBox(height: 24),
            FinanceDashboardKpiDetail(
              clinicId: widget.clinicId,
              kpi: _selectedKpi,
              currency: stats.currency,
            ),
          ],
        ),
      ),
      ),
    );
  }
}

class FinanceDashboardKpiDetail extends ConsumerWidget {
  final int clinicId;
  final FinanceDashboardKpi kpi;
  final String currency;

  const FinanceDashboardKpiDetail({
    required this.clinicId,
    required this.kpi,
    required this.currency,
  });

  String _detailTitle(AppLocalizations l10n) {
    switch (kpi) {
      case FinanceDashboardKpi.revenue:
        return l10n.translate('clinicFinanceDashboardRevenueDetail');
      case FinanceDashboardKpi.outstanding:
        return l10n.translate('clinicFinanceDashboardOutstandingDetail');
      case FinanceDashboardKpi.overdue:
        return l10n.translate('clinicFinanceDashboardOverdueDetail');
      case FinanceDashboardKpi.collectionRate:
        return l10n.translate('clinicFinanceDashboardCollectionDetail');
    }
  }

  String? _detailHint(AppLocalizations l10n, bool monthScoped) {
    switch (kpi) {
      case FinanceDashboardKpi.revenue:
        return monthScoped
            ? l10n.translate('clinicFinanceDashboardRevenueHintMonth')
            : l10n.translate('clinicFinanceDashboardRevenueHint');
      case FinanceDashboardKpi.outstanding:
        return monthScoped
            ? l10n.translate('clinicFinanceDashboardOutstandingHintMonth')
            : l10n.translate('clinicFinanceDashboardOutstandingHint');
      case FinanceDashboardKpi.overdue:
        return monthScoped
            ? l10n.translate('clinicFinanceDashboardOverdueHintMonth')
            : l10n.translate('clinicFinanceDashboardOverdueHint');
      case FinanceDashboardKpi.collectionRate:
        return monthScoped
            ? l10n.translate('clinicFinanceDashboardCollectionHintMonth')
            : l10n.translate('clinicFinanceDashboardCollectionHint');
    }
  }

  Widget _emptyState(String message) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 24),
      child: Center(
        child: Text(
          message,
          style: TextStyle(color: Colors.grey.shade600),
          textAlign: TextAlign.center,
        ),
      ),
    );
  }

  Widget _detailCard({required Widget child}) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: Colors.grey.shade200),
      ),
      child: child,
    );
  }

  String _planLabel(TreatmentPlanSummaryDto plan, AppLocalizations l10n) {
    final title = plan.title?.trim();
    if (title != null && title.isNotEmpty) {
      return '$title (#${plan.id})';
    }
    return '${l10n.translate('treatmentPlanTitle')} #${plan.id}';
  }

  String _patientLabel(String? name, int? id, AppLocalizations l10n) {
    final n = name?.trim();
    if (n != null && n.isNotEmpty) return n;
    if (id != null && id != 0) {
      return '${l10n.translate('patient')} #$id';
    }
    return '—';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final monthScoped =
        ref.watch(clinicFinanceMonthFilterProvider(clinicId)) != null;
    final hint = _detailHint(l10n, monthScoped);

    Widget body;
    switch (kpi) {
      case FinanceDashboardKpi.revenue:
        body = _buildRevenueDetail(ref, l10n);
      case FinanceDashboardKpi.outstanding:
        body = _buildOutstandingDetail(ref, l10n);
      case FinanceDashboardKpi.overdue:
        body = _buildOverdueDetail(ref, l10n);
      case FinanceDashboardKpi.collectionRate:
        body = _buildCollectionDetail(ref, l10n);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          _detailTitle(l10n),
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
        if (hint != null) ...[
          const SizedBox(height: 4),
          Text(
            hint,
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Colors.grey.shade600,
                ),
          ),
        ],
        const SizedBox(height: 8),
        body,
      ],
    );
  }

  Widget _buildRevenueDetail(WidgetRef ref, AppLocalizations l10n) {
    final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
    if (month != null) {
      return _buildMonthVisitRevenueDetail(ref, l10n);
    }
    final paymentsAsync = ref.watch(clinicPaymentHistoryProvider(clinicId));
    return paymentsAsync.when(
      loading: () => const Center(child: Padding(
        padding: EdgeInsets.all(24),
        child: CircularProgressIndicator(),
      )),
      error: (e, _) => _emptyState('${l10n.error}: $e'),
      data: (payments) {
        if (payments.isEmpty) {
          return _emptyState(l10n.translate('clinicFinanceNoPayments'));
        }
        final sorted = [...payments]
          ..sort((a, b) => b.recordedAt.compareTo(a.recordedAt));
        return _detailCard(
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: sorted.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final p = sorted[i];
              final planTitle = p.treatmentPlanTitle?.trim();
              final planLabel = planTitle != null && planTitle.isNotEmpty
                  ? '$planTitle (#${p.treatmentPlanId})'
                  : '#${p.treatmentPlanId}';
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.payments_outlined,
                  color: Colors.green.shade700,
                  size: 22,
                ),
                title: Text(_patientLabel(p.patientName, p.patientId, l10n)),
                subtitle: Text(
                  '$planLabel · ${formatFinanceDate(p.recordedAt)} · '
                  '${l10n.clinicPaymentMethodLabel(p.method)}',
                ),
                trailing: Text(
                  formatFinanceMoney(p.amountMinor, p.currency),
                  style: TextStyle(
                    color: Colors.green.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildMonthVisitRevenueDetail(WidgetRef ref, AppLocalizations l10n) {
    final visitsAsync = ref.watch(clinicFinanceMonthVisitsProvider(clinicId));
    return visitsAsync.when(
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      ),
      error: (e, _) => _emptyState('${l10n.error}: $e'),
      data: (visits) {
        final scoped = visits
            .where((v) => v.visitCollectedMinor > 0)
            .toList()
          ..sort((a, b) => b.startAt.compareTo(a.startAt));
        if (scoped.isEmpty) {
          return _emptyState(l10n.translate('clinicFinanceNoPayments'));
        }
        return _detailCard(
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: scoped.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final v = scoped[i];
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.event_outlined,
                  color: Colors.green.shade700,
                  size: 22,
                ),
                title: Text(_patientLabel(v.patientName, v.patientId, l10n)),
                subtitle: Text(
                  '${v.doctorName} · ${formatFinanceDate(v.startAt)} · '
                  '#${v.treatmentPlanId}',
                ),
                trailing: Text(
                  formatFinanceMoney(v.visitCollectedMinor, v.currency),
                  style: TextStyle(
                    color: Colors.green.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildOutstandingDetail(WidgetRef ref, AppLocalizations l10n) {
    final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
    if (month != null) {
      return _buildMonthVisitOutstandingDetail(ref, l10n);
    }
    final plansAsync = ref.watch(
      treatmentPlansForClinicProvider(
        ClinicPlansFilter(clinicId: clinicId),
      ),
    );
    final recordsAsync = ref.watch(clinicFinancialRecordsProvider(clinicId));

    if (plansAsync.isLoading || recordsAsync.isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      );
    }
    if (plansAsync.hasError) {
      return _emptyState('${l10n.error}: ${plansAsync.error}');
    }
    if (recordsAsync.hasError) {
      return _emptyState('${l10n.error}: ${recordsAsync.error}');
    }

    final plans = (plansAsync.value ?? [])
        .where((p) => p.owedMinor > 0 && p.status != 'CANCELLED')
        .toList()
      ..sort((a, b) => b.owedMinor.compareTo(a.owedMinor));
    final records = (recordsAsync.value ?? [])
        .where((r) =>
            r.remainingMinor > 0 &&
            r.status != 'VOIDED' &&
            r.status != 'PAID')
        .toList()
      ..sort((a, b) => b.remainingMinor.compareTo(a.remainingMinor));

    if (plans.isEmpty && records.isEmpty) {
      return _emptyState(l10n.translate('clinicFinanceDashboardNoOutstanding'));
    }

    return _detailCard(
      child: ListView(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        children: [
          if (plans.isNotEmpty) ...[
            ...plans.map((plan) {
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.assignment_outlined,
                  color: Colors.orange.shade700,
                  size: 22,
                ),
                title: Text(_patientLabel(plan.patientName, plan.patientId, l10n)),
                subtitle: Text(_planLabel(plan, l10n)),
                trailing: Text(
                  formatFinanceMoney(plan.owedMinor, plan.currency),
                  style: TextStyle(
                    color: Colors.orange.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            }),
          ],
          if (records.isNotEmpty) ...[
            if (plans.isNotEmpty) const Divider(height: 1),
            ...records.map((record) {
              final recordLabel = record.recordNumber?.trim();
              final title = recordLabel != null && recordLabel.isNotEmpty
                  ? recordLabel
                  : '${l10n.clinicRecordTypeLabel(record.recordType)} #${record.id}';
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.receipt_long_outlined,
                  color: Colors.orange.shade700,
                  size: 22,
                ),
                title: Text(
                  _patientLabel(null, record.patientId, l10n),
                ),
                subtitle: Text(title),
                trailing: Text(
                  formatFinanceMoney(record.remainingMinor, record.currency),
                  style: TextStyle(
                    color: Colors.orange.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            }),
          ],
        ],
      ),
    );
  }

  Widget _buildMonthVisitOutstandingDetail(WidgetRef ref, AppLocalizations l10n) {
    final visitsAsync = ref.watch(clinicFinanceMonthVisitsProvider(clinicId));
    return visitsAsync.when(
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      ),
      error: (e, _) => _emptyState('${l10n.error}: $e'),
      data: (visits) {
        final scoped = visits
            .map((v) => (
                  visit: v,
                  outstanding: v.visitTotalMinor - v.visitCollectedMinor,
                ))
            .where((e) => e.outstanding > 0)
            .toList()
          ..sort((a, b) => b.outstanding.compareTo(a.outstanding));
        if (scoped.isEmpty) {
          return _emptyState(l10n.translate('clinicFinanceDashboardNoOutstanding'));
        }
        return _detailCard(
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: scoped.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final v = scoped[i].visit;
              final outstanding = scoped[i].outstanding;
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.event_outlined,
                  color: Colors.orange.shade700,
                  size: 22,
                ),
                title: Text(_patientLabel(v.patientName, v.patientId, l10n)),
                subtitle: Text(
                  '${v.doctorName} · ${formatFinanceDate(v.startAt)} · '
                  '#${v.treatmentPlanId}',
                ),
                trailing: Text(
                  formatFinanceMoney(outstanding, v.currency),
                  style: TextStyle(
                    color: Colors.orange.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildOverdueDetail(WidgetRef ref, AppLocalizations l10n) {
    final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
    if (month != null) {
      return _emptyState(l10n.translate('clinicFinanceDashboardNoOverdueMonth'));
    }
    final installmentsAsync = ref.watch(
      clinicInstallmentItemsProvider((clinicId, 'overdue')),
    );
    final recordsAsync = ref.watch(clinicFinancialRecordsProvider(clinicId));

    if (installmentsAsync.isLoading || recordsAsync.isLoading) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      );
    }
    if (installmentsAsync.hasError) {
      return _emptyState('${l10n.error}: ${installmentsAsync.error}');
    }
    if (recordsAsync.hasError) {
      return _emptyState('${l10n.error}: ${recordsAsync.error}');
    }

    final installments = installmentsAsync.value ?? [];
    final overdueRecords = (recordsAsync.value ?? [])
        .where((r) => r.status == 'OVERDUE' && r.remainingMinor > 0)
        .toList()
      ..sort((a, b) => b.remainingMinor.compareTo(a.remainingMinor));

    if (installments.isEmpty && overdueRecords.isEmpty) {
      return _emptyState(l10n.translate('clinicFinanceDashboardNoOverdue'));
    }

    return _detailCard(
      child: ListView(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        children: [
          if (installments.isNotEmpty) ...[
            ...installments.map((item) {
              final planTitle = item.treatmentPlanTitle?.trim();
              final planLabel = planTitle != null && planTitle.isNotEmpty
                  ? planTitle
                  : '#${item.treatmentPlanId}';
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.schedule_outlined,
                  color: Colors.red.shade700,
                  size: 22,
                ),
                title: Text(item.patientName),
                subtitle: Text(
                  '$planLabel · ${l10n.translate('clinicFinanceInstallColSeq')} '
                  '${item.sequenceNumber} · '
                  '${l10n.translate('clinicFinanceInstallDue')} '
                  '${formatFinanceDate(item.dueDate)}',
                ),
                trailing: Text(
                  formatFinanceMoney(item.amountMinor, item.currency),
                  style: TextStyle(
                    color: Colors.red.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            }),
          ],
          if (overdueRecords.isNotEmpty) ...[
            if (installments.isNotEmpty) const Divider(height: 1),
            ...overdueRecords.map((record) {
              final recordLabel = record.recordNumber?.trim();
              final title = recordLabel != null && recordLabel.isNotEmpty
                  ? recordLabel
                  : '${l10n.clinicRecordTypeLabel(record.recordType)} #${record.id}';
              final due = record.dueDate;
              final dueText = due != null && due.isNotEmpty
                  ? ' · ${l10n.translate('clinicFinanceInstallDue')} ${formatFinanceDate(due)}'
                  : '';
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.receipt_long_outlined,
                  color: Colors.red.shade700,
                  size: 22,
                ),
                title: Text(_patientLabel(null, record.patientId, l10n)),
                subtitle: Text('$title$dueText'),
                trailing: Text(
                  formatFinanceMoney(record.remainingMinor, record.currency),
                  style: TextStyle(
                    color: Colors.red.shade700,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              );
            }),
          ],
        ],
      ),
    );
  }

  Widget _buildCollectionDetail(WidgetRef ref, AppLocalizations l10n) {
    final month = ref.watch(clinicFinanceMonthFilterProvider(clinicId));
    if (month != null) {
      return _buildMonthVisitCollectionDetail(ref, l10n);
    }
    final plansAsync = ref.watch(
      treatmentPlansForClinicProvider(
        ClinicPlansFilter(clinicId: clinicId),
      ),
    );

    return plansAsync.when(
      loading: () => const Center(child: Padding(
        padding: EdgeInsets.all(24),
        child: CircularProgressIndicator(),
      )),
      error: (e, _) => _emptyState('${l10n.error}: $e'),
      data: (allPlans) {
        final plans = allPlans
            .where((p) => p.totalMinor > 0 && p.status != 'CANCELLED')
            .toList()
          ..sort((a, b) {
            final rateA = a.paidMinor / a.totalMinor;
            final rateB = b.paidMinor / b.totalMinor;
            final c = rateA.compareTo(rateB);
            if (c != 0) return c;
            return b.totalMinor.compareTo(a.totalMinor);
          });

        if (plans.isEmpty) {
          return _emptyState(l10n.translate('clinicFinanceDashboardNoCollection'));
        }

        return _detailCard(
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: plans.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final plan = plans[i];
              final rate = plan.totalMinor > 0
                  ? (plan.paidMinor / plan.totalMinor * 100)
                  : 0.0;
              final rateColor = rate >= 100
                  ? Colors.green.shade700
                  : rate >= 50
                      ? Colors.orange.shade700
                      : Colors.red.shade700;
              return ListTile(
                dense: true,
                leading: Icon(Icons.pie_chart_outline, color: rateColor, size: 22),
                title: Text(_patientLabel(plan.patientName, plan.patientId, l10n)),
                subtitle: Text(_planLabel(plan, l10n)),
                trailing: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      '${rate.toStringAsFixed(1)}%',
                      style: TextStyle(
                        color: rateColor,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text(
                      '${formatFinanceMoney(plan.paidMinor, plan.currency)} / '
                      '${formatFinanceMoney(plan.totalMinor, plan.currency)}',
                      style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        );
      },
    );
  }

  Widget _buildMonthVisitCollectionDetail(WidgetRef ref, AppLocalizations l10n) {
    final visitsAsync = ref.watch(clinicFinanceMonthVisitsProvider(clinicId));
    return visitsAsync.when(
      loading: () => const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: CircularProgressIndicator(),
        ),
      ),
      error: (e, _) => _emptyState('${l10n.error}: $e'),
      data: (visits) {
        final scoped = visits
            .where((v) => v.visitTotalMinor > 0)
            .toList()
          ..sort((a, b) {
            final rateA = a.visitCollectedMinor / a.visitTotalMinor;
            final rateB = b.visitCollectedMinor / b.visitTotalMinor;
            final c = rateA.compareTo(rateB);
            if (c != 0) return c;
            return b.visitTotalMinor.compareTo(a.visitTotalMinor);
          });
        if (scoped.isEmpty) {
          return _emptyState(l10n.translate('clinicFinanceDashboardNoCollection'));
        }
        return _detailCard(
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: scoped.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final v = scoped[i];
              final rate = v.visitTotalMinor > 0
                  ? (v.visitCollectedMinor / v.visitTotalMinor * 100)
                  : 0.0;
              final rateColor = rate >= 100
                  ? Colors.green.shade700
                  : rate >= 50
                      ? Colors.orange.shade700
                      : Colors.red.shade700;
              return ListTile(
                dense: true,
                leading: Icon(Icons.pie_chart_outline, color: rateColor, size: 22),
                title: Text(_patientLabel(v.patientName, v.patientId, l10n)),
                subtitle: Text(
                  '${v.doctorName} · ${formatFinanceDate(v.startAt)} · '
                  '#${v.treatmentPlanId}',
                ),
                trailing: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      '${rate.toStringAsFixed(1)}%',
                      style: TextStyle(
                        color: rateColor,
                        fontWeight: FontWeight.w700,
                      ),
                    ),
                    Text(
                      '${formatFinanceMoney(v.visitCollectedMinor, v.currency)} / '
                      '${formatFinanceMoney(v.visitTotalMinor, v.currency)}',
                      style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        );
      },
    );
  }
}

class FinanceDashboardDoctorEarningsTop extends ConsumerWidget {
  final int clinicId;
  final List<DoctorEarningRow> earnings;
  final String currency;

  const FinanceDashboardDoctorEarningsTop({
    super.key,
    required this.clinicId,
    required this.earnings,
    required this.currency,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final members =
        ref.watch(clinicMembersProvider(clinicId)).valueOrNull ??
            <ClinicMember>[];

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          l10n.translate('clinicFinanceDashboardDoctorEarningsTop'),
          style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
        ),
        const SizedBox(height: 8),
        Card(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(color: Colors.grey.shade200),
          ),
          child: ListView.separated(
            shrinkWrap: true,
            physics: const NeverScrollableScrollPhysics(),
            itemCount: earnings.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final row = earnings[i];
              final name = doctorNameFromClinicMembers(
                row.doctorProfileId,
                members,
              );
              return ListTile(
                dense: true,
                leading: Icon(
                  Icons.person_outline,
                  color: AppColors.primaryTeal,
                  size: 22,
                ),
                title: Text(name),
                subtitle: Text(
                  row.hasRevenueShare
                      ? '${l10n.translate('clinicEarningsColVisits')}: ${row.visitCount} · '
                          '${formatOptionalPercent(row.revenueSharePercent)} · '
                          '${l10n.translate('clinicFinanceDoctorShareCollected')}: '
                          '${formatOptionalFinanceMoney(row.doctorShareCollectedMinor, currency)}'
                      : '${l10n.translate('clinicEarningsColVisits')}: ${row.visitCount}',
                ),
                trailing: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      formatFinanceMoney(row.grossMinor, currency),
                      style: const TextStyle(fontWeight: FontWeight.w600),
                    ),
                    Text(
                      '${formatFinanceMoney(row.collectedMinor, currency)} / '
                      '${formatFinanceMoney(row.outstandingMinor, currency)}',
                      style: TextStyle(
                        fontSize: 11,
                        color: Colors.grey.shade600,
                      ),
                    ),
                  ],
                ),
              );
            },
          ),
        ),
      ],
    );
  }
}
