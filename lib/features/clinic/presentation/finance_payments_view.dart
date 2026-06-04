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
class FinancePaymentsView extends ConsumerStatefulWidget {
  final int clinicId;
  const FinancePaymentsView({super.key, required this.clinicId});

  @override
  ConsumerState<FinancePaymentsView> createState() => FinancePaymentsViewState();
}

class FinancePaymentsViewState extends ConsumerState<FinancePaymentsView> {
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

  List<PaymentHistoryItem> _apply(
    List<PaymentHistoryItem> rows, {
    DateTime? monthFromUtc,
    DateTime? monthToUtc,
  }) {
    Iterable<PaymentHistoryItem> out = rows;
    if (monthFromUtc != null && monthToUtc != null) {
      out = out.where(
        (p) =>
            financeInstantInMonthRange(p.recordedAt, monthFromUtc, monthToUtc),
      );
    }
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
      error: (e, _) => Center(child: Text('${l10n.error}: $e')),
      data: (payments) {
        final month =
            ref.watch(clinicFinanceMonthFilterProvider(widget.clinicId));
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
        final filtered = _apply(
          payments,
          monthFromUtc: monthFromUtc,
          monthToUtc: monthToUtc,
        );
        final totalsMinor =
            filtered.fold<int>(0, (a, PaymentHistoryItem p) => a + p.amountMinor);
        final clinicCurrency =
            ref.watch(clinicFinanceCurrencyProvider(widget.clinicId));
        final currency = filtered.isEmpty
            ? (payments.isEmpty ? clinicCurrency : payments.first.currency)
            : filtered.first.currency;

        final toolbar = Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            FinanceMonthFilterBar(clinicId: widget.clinicId),
            const SizedBox(height: 8),
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
                          '${filtered.length} · ${formatFinanceMoney(totalsMinor, currency)}',
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
              options: [
                (
                  value: 'ALL',
                  label: l10n.translate('clinicTreatmentPlansAll'),
                ),
                (
                  value: 'CASH',
                  label: l10n.clinicPaymentMethodLabel('CASH'),
                ),
                (
                  value: 'CARD_EXTERNAL',
                  label: l10n.clinicPaymentMethodLabel('CARD_EXTERNAL'),
                ),
                (
                  value: 'TRANSFER',
                  label: l10n.clinicPaymentMethodLabel('TRANSFER'),
                ),
                (
                  value: 'OTHER',
                  label: l10n.clinicPaymentMethodLabel('OTHER'),
                ),
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
                      DataCell(Text(formatFinanceDate(p.recordedAt))),
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
                          Text(l10n.clinicPaymentMethodLabel(p.method)),
                        ],
                      )),
                      DataCell(Text(
                        formatFinanceMoney(p.amountMinor, p.currency),
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
