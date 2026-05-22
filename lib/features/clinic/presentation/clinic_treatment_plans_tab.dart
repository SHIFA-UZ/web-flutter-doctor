import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_wizard_sheet.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

/// Clinic-wide treatment plans ledger.
///
/// Shows every plan in the clinic (no patient pre-selection required),
/// with key columns the doctor needs at a glance: plan id, title, patient,
/// attending doctor, totals, payment status. Supports a free-text filter
/// (matched server-side against title + patient name) and a status filter.
class ClinicTreatmentPlansTab extends ConsumerStatefulWidget {
  final int clinicId;

  const ClinicTreatmentPlansTab({super.key, required this.clinicId});

  @override
  ConsumerState<ClinicTreatmentPlansTab> createState() =>
      _ClinicTreatmentPlansTabState();
}

class _ClinicTreatmentPlansTabState
    extends ConsumerState<ClinicTreatmentPlansTab> {
  final _filterCtrl = TextEditingController();
  Timer? _debounce;
  String _query = '';
  String? _statusFilter;

  /// Sort state for the DataTable. Default: most recently updated first
  /// (column index 9 = "updated", descending).
  int _sortIdx = 9;
  bool _sortAsc = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _filterCtrl.dispose();
    super.dispose();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (!mounted) return;
      setState(() => _query = value.trim());
    });
  }

  List<TreatmentPlanSummaryDto> _sort(List<TreatmentPlanSummaryDto> plans) {
    final list = [...plans];
    int cmp(TreatmentPlanSummaryDto a, TreatmentPlanSummaryDto b) {
      int c;
      switch (_sortIdx) {
        case 0:
          c = a.id.compareTo(b.id);
          break;
        case 1:
          c = (a.title ?? '').toLowerCase().compareTo(
              (b.title ?? '').toLowerCase());
          break;
        case 2:
          c = (a.patientName ?? '').toLowerCase().compareTo(
              (b.patientName ?? '').toLowerCase());
          break;
        case 3:
          final ad = a.attendingDoctors.isNotEmpty
              ? a.attendingDoctors.map((d) => d.name).join(',')
              : (a.attendingDoctorName ?? '');
          final bd = b.attendingDoctors.isNotEmpty
              ? b.attendingDoctors.map((d) => d.name).join(',')
              : (b.attendingDoctorName ?? '');
          c = ad.toLowerCase().compareTo(bd.toLowerCase());
          break;
        case 4:
          c = a.totalMinor.compareTo(b.totalMinor);
          break;
        case 5:
          c = a.paidMinor.compareTo(b.paidMinor);
          break;
        case 6:
          c = a.owedMinor.compareTo(b.owedMinor);
          break;
        case 7:
          c = a.status.compareTo(b.status);
          break;
        case 8:
          c = a.planPaymentStatus.compareTo(b.planPaymentStatus);
          break;
        case 9:
          c = (a.updatedAt ?? '').compareTo(b.updatedAt ?? '');
          break;
        default:
          c = 0;
      }
      return _sortAsc ? c : -c;
    }
    list.sort(cmp);
    return list;
  }

  String _money(int minor, String currency) =>
      '${(minor / 100).toStringAsFixed(2)} $currency';

  String _shortDate(String? iso) {
    if (iso == null || iso.isEmpty) return '—';
    try {
      final dt = DateTime.parse(iso).toLocal();
      final mm = dt.month.toString().padLeft(2, '0');
      final dd = dt.day.toString().padLeft(2, '0');
      return '${dt.year}-$mm-$dd';
    } catch (_) {
      return iso;
    }
  }

  Color _statusColor(String status, ColorScheme scheme) {
    switch (status.toUpperCase()) {
      case 'ACTIVE':
        return Colors.blue.shade600;
      case 'IN_PROGRESS':
        return Colors.indigo.shade600;
      case 'ON_HOLD':
        return Colors.amber.shade800;
      case 'COMPLETED':
        return Colors.green.shade700;
      case 'CANCELLED':
        return Colors.red.shade700;
      case 'DRAFT':
      default:
        return scheme.outline;
    }
  }

  /// Localized human label for a [TreatmentPlan.Status] enum string. Falls
  /// back to the raw enum name if no translation is registered.
  String _statusLabel(BuildContext context, String status) {
    final l10n = AppLocalizations.of(context)!;
    switch (status.toUpperCase()) {
      case 'DRAFT':
        return l10n.translate('clinicPlanStatusDraft');
      case 'ACTIVE':
        return l10n.translate('clinicPlanStatusActive');
      case 'ON_HOLD':
        return l10n.translate('clinicPlanStatusOnHold');
      case 'IN_PROGRESS':
        return l10n.translate('clinicPlanStatusInProgress');
      case 'COMPLETED':
        return l10n.translate('clinicPlanStatusCompleted');
      case 'CANCELLED':
        return l10n.translate('clinicPlanStatusCancelled');
      default:
        return status;
    }
  }

  /// Full enum used by the popup menu. ACTIVE / IN_PROGRESS / ON_HOLD /
  /// COMPLETED are the "operational" states a doctor toggles between;
  /// CANCELLED is exposed unconditionally because product rule states that
  /// any doctor may cancel a plan from any other status. DRAFT is included
  /// for symmetry so doctors can revert an accidental promotion.
  static const List<String> _selectableStatuses = [
    'ACTIVE',
    'IN_PROGRESS',
    'ON_HOLD',
    'COMPLETED',
    'DRAFT',
    'CANCELLED',
  ];

  Future<void> _changeStatus(
    BuildContext context,
    TreatmentPlanSummaryDto plan,
    String newStatus,
  ) async {
    if (newStatus == plan.status) return;
    final l10n = AppLocalizations.of(context)!;

    // Cancellation is irreversible from the patient's POV (records
    // installments / payments stay), so require an explicit confirmation
    // before firing the PATCH.
    if (newStatus == 'CANCELLED') {
      final ok = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          title: Text(l10n.translate('clinicPlanCancelConfirmTitle')),
          content: Text(
            l10n
                .translate('clinicPlanCancelConfirmBody')
                .replaceAll(
                    '{{title}}',
                    plan.title?.trim().isNotEmpty == true
                        ? plan.title!.trim()
                        : '#${plan.id}'),
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.pop(ctx, false),
              child: Text(l10n.translate('cancel')),
            ),
            FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: Colors.red.shade700,
              ),
              onPressed: () => Navigator.pop(ctx, true),
              child: Text(l10n.translate('clinicPlanCancelConfirm')),
            ),
          ],
        ),
      );
      if (ok != true) return;
    }

    try {
      final res = await patchTreatmentPlanStatus(
        ref,
        planId: plan.id,
        status: newStatus,
      );
      if (!context.mounted) return;
      if (res == null) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              l10n.translate('clinicPlanStatusUpdateFailed'),
            ),
          ),
        );
        return;
      }
      // Force the table + finance widgets to re-pull so the new status
      // (and any downstream payment-status / outstanding changes) become
      // visible immediately.
      ref.invalidate(treatmentPlansForClinicProvider);
      ref.invalidate(clinicFinanceDashboardProvider(widget.clinicId));
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('clinicPlanStatusUpdated')}: '
            '${_statusLabel(context, newStatus)}',
          ),
        ),
      );
    } catch (e) {
      if (!context.mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            '${l10n.translate('clinicPlanStatusUpdateFailed')}: $e',
          ),
        ),
      );
    }
  }

  Color _paymentColor(String s) {
    switch (s.toUpperCase()) {
      case 'PAID':
        return Colors.green.shade700;
      case 'PARTIAL':
        return Colors.orange.shade700;
      case 'UNPAID':
        return Colors.red.shade700;
      case 'NONE':
      default:
        return Colors.grey.shade600;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final filter = ClinicPlansFilter(
      clinicId: widget.clinicId,
      status: _statusFilter,
      query: _query.isEmpty ? null : _query,
    );
    final plansAsync = ref.watch(treatmentPlansForClinicProvider(filter));

    final toolbar = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            Expanded(
              child: ClinicTableSearchField(
                controller: _filterCtrl,
                hint: l10n.translate('clinicTreatmentPlansFilterHint'),
                onChanged: _onSearchChanged,
              ),
            ),
            const SizedBox(width: 8),
            FilledButton.icon(
              onPressed: () => TreatmentPlanWizardSheet.show(
                context,
                ref,
                clinicId: widget.clinicId,
              ),
              icon: const Icon(Icons.add, size: 18),
              label: Text(l10n.translate('clinicTreatmentPlansNew')),
            ),
          ],
        ),
        const SizedBox(height: 8),
        ClinicFilterChips<String?>(
          selected: _statusFilter,
          onSelected: (v) => setState(() => _statusFilter = v),
          options: [
            (
              value: null,
              label: l10n.translate('clinicTreatmentPlansAll'),
            ),
            (value: 'ACTIVE', label: 'ACTIVE'),
            (value: 'DRAFT', label: 'DRAFT'),
            (value: 'COMPLETED', label: 'COMPLETED'),
            (value: 'CANCELLED', label: 'CANCELLED'),
          ],
        ),
      ],
    );

    Widget body = plansAsync.when(
      loading: () => const Center(child: CircularProgressIndicator()),
      error: (e, _) =>
          Center(child: Text('${l10n.translate('error')}: $e')),
      data: (plans) {
        if (plans.isEmpty) {
          return ClinicTableEmpty(
            l10n.translate('clinicTreatmentPlansEmpty'),
          );
        }
        final sorted = _sort(plans);
        final scheme = Theme.of(context).colorScheme;
        return clinicDataTable(
          context: context,
          sortColumnIndex: _sortIdx,
          sortAscending: _sortAsc,
          columns: [
            DataColumn(
              label: Text(l10n.translate('clinicPlansColId')),
              numeric: true,
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicPlansColTitle')),
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicTreatmentPlansPatient')),
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicTreatmentPlansDoctor')),
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicTreatmentPlansTotal')),
              numeric: true,
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicTreatmentPlansPaid')),
              numeric: true,
              onSort: _onSort,
            ),
            DataColumn(
              label:
                  Text(l10n.translate('clinicTreatmentPlansOutstanding')),
              numeric: true,
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicTreatmentPlansStatus')),
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(
                  l10n.translate('clinicTreatmentPlansPaymentStatus')),
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicTreatmentPlansUpdated')),
              onSort: _onSort,
            ),
            DataColumn(
              label: Text(l10n.translate('clinicPlansColActions')),
            ),
          ],
          rows: sorted.map((p) {
            final title = p.title?.trim().isNotEmpty == true
                ? p.title!.trim()
                : l10n.translate('clinicTreatmentPlansUntitled');
            final patient = p.patientName?.trim().isNotEmpty == true
                ? p.patientName!
                : '—';
            final doctor = p.attendingDoctors.isNotEmpty
                ? p.attendingDoctors.map((d) => d.name).join(', ')
                : (p.attendingDoctorName?.trim().isNotEmpty == true
                    ? p.attendingDoctorName!
                    : '—');
            return DataRow(
              cells: [
                DataCell(Text('#${p.id}')),
                DataCell(
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 220),
                    child: Text(
                      title,
                      style: const TextStyle(
                        fontWeight: FontWeight.w600,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
                DataCell(Text(patient)),
                DataCell(
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 200),
                    child: Text(
                      doctor,
                      overflow: TextOverflow.ellipsis,
                      maxLines: 2,
                    ),
                  ),
                ),
                DataCell(Text(_money(p.totalMinor, p.currency))),
                DataCell(Text(
                  _money(p.paidMinor, p.currency),
                  style: TextStyle(color: Colors.green.shade700),
                )),
                DataCell(Text(
                  _money(p.owedMinor, p.currency),
                  style: TextStyle(
                    color: p.owedMinor > 0
                        ? Colors.red.shade700
                        : Colors.grey.shade600,
                    fontWeight: FontWeight.w600,
                  ),
                )),
                DataCell(_StatusPill(
                  status: p.status,
                  color: _statusColor(p.status, scheme),
                  label: _statusLabel(context, p.status),
                  entries: [
                    for (final s in _selectableStatuses)
                      if (s != p.status)
                        PopupMenuItem<String>(
                          value: s,
                          child: Row(
                            children: [
                              Icon(Icons.circle,
                                  size: 10, color: _statusColor(s, scheme)),
                              const SizedBox(width: 8),
                              Text(_statusLabel(context, s)),
                            ],
                          ),
                        ),
                  ],
                  onSelected: (s) => _changeStatus(context, p, s),
                )),
                DataCell(_pill(
                  p.planPaymentStatus,
                  _paymentColor(p.planPaymentStatus),
                )),
                DataCell(Text(_shortDate(p.updatedAt))),
                DataCell(
                  IconButton(
                    tooltip: l10n.translate('clinicPlansViewTooltip'),
                    icon: const Icon(Icons.open_in_new, size: 20),
                    onPressed: () => _openPlanDialog(context, p),
                  ),
                ),
              ],
            );
          }).toList(),
        );
      },
    );

    return ClinicTableShell(toolbar: toolbar, body: body);
  }

  void _openPlanDialog(BuildContext context, TreatmentPlanSummaryDto p) {
    final l10n = AppLocalizations.of(context)!;
    final title = p.title?.trim().isNotEmpty == true
        ? p.title!.trim()
        : l10n.translate('clinicTreatmentPlansUntitled');
    final patient = p.patientName?.trim().isNotEmpty == true
        ? p.patientName!
        : '—';
    final doctor = p.attendingDoctors.isNotEmpty
        ? p.attendingDoctors.map((d) => d.name).join(', ')
        : (p.attendingDoctorName?.trim().isNotEmpty == true
            ? p.attendingDoctorName!
            : '—');
    showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('#${p.id} · $title'),
        content: SingleChildScrollView(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              _kv(l10n.translate('clinicTreatmentPlansPatient'), patient),
              _kv(l10n.translate('clinicTreatmentPlansDoctor'), doctor),
              _kv(l10n.translate('treatmentPlanDiagnosis'),
                  p.diagnosis ?? '—'),
              _kv(l10n.translate('treatmentPlanNotes'), p.notes ?? '—'),
              _kv(l10n.translate('clinicTreatmentPlansTotal'),
                  _money(p.totalMinor, p.currency)),
              _kv(l10n.translate('clinicTreatmentPlansPaid'),
                  _money(p.paidMinor, p.currency)),
              _kv(l10n.translate('clinicTreatmentPlansOutstanding'),
                  _money(p.owedMinor, p.currency)),
              _kv(l10n.translate('clinicTreatmentPlansStatus'), p.status),
              _kv(l10n.translate('clinicTreatmentPlansPaymentStatus'),
                  p.planPaymentStatus),
              _kv(l10n.translate('clinicTreatmentPlansUpdated'),
                  _shortDate(p.updatedAt)),
            ],
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

  static Widget _pill(String label, Color color) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
        border: Border.all(color: color.withValues(alpha: 0.6)),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: color,
          fontSize: 11,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }

  static Widget _kv(String k, String v) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 2),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 140,
            child: Text(k,
                style: const TextStyle(
                    color: Colors.black54, fontWeight: FontWeight.w500)),
          ),
          Expanded(child: Text(v)),
        ],
      ),
    );
  }
}

/// Interactive treatment-plan status pill. Renders the same rounded "tag"
/// look as the read-only payment pill but is wrapped in a [PopupMenuButton]
/// so any clinic doctor can transition to any other status (in particular,
/// CANCELLED is always reachable per the product rule).
class _StatusPill extends StatelessWidget {
  final String status;
  final String label;
  final Color color;
  final List<PopupMenuEntry<String>> entries;
  final ValueChanged<String> onSelected;

  const _StatusPill({
    required this.status,
    required this.label,
    required this.color,
    required this.entries,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<String>(
      tooltip: '',
      onSelected: onSelected,
      itemBuilder: (_) => entries,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
        decoration: BoxDecoration(
          color: color.withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: color.withValues(alpha: 0.6)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              label,
              style: TextStyle(
                color: color,
                fontSize: 11,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(width: 4),
            Icon(Icons.arrow_drop_down, size: 14, color: color),
          ],
        ),
      ),
    );
  }
}

