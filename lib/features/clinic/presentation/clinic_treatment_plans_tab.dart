import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_wizard_sheet.dart';
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

  @override
  void dispose() {
    _debounce?.cancel();
    _filterCtrl.dispose();
    super.dispose();
  }

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: 300), () {
      if (!mounted) return;
      setState(() => _query = value.trim());
    });
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
      case 'COMPLETED':
        return Colors.green.shade700;
      case 'CANCELLED':
        return Colors.red.shade700;
      case 'DRAFT':
      default:
        return scheme.outline;
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

    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // --- Toolbar -----------------------------------------------------
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _filterCtrl,
                  decoration: InputDecoration(
                    labelText: l10n.translate('clinicTreatmentPlansFilter'),
                    hintText: l10n
                        .translate('clinicTreatmentPlansFilterHint'),
                    prefixIcon: const Icon(Icons.search),
                    suffixIcon: _filterCtrl.text.isEmpty
                        ? null
                        : IconButton(
                            icon: const Icon(Icons.close),
                            onPressed: () {
                              _filterCtrl.clear();
                              _onSearchChanged('');
                            },
                          ),
                    isDense: true,
                  ),
                  onChanged: _onSearchChanged,
                ),
              ),
              const SizedBox(width: 12),
              FilledButton.icon(
                onPressed: () => TreatmentPlanWizardSheet.show(
                  context,
                  ref,
                  clinicId: widget.clinicId,
                ),
                icon: const Icon(Icons.add),
                label: Text(l10n.translate('clinicTreatmentPlansNew')),
              ),
            ],
          ),
          const SizedBox(height: 8),

          // --- Status filter chips ----------------------------------------
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                _statusChip(null, l10n.translate('clinicTreatmentPlansAll')),
                _statusChip('ACTIVE', 'ACTIVE'),
                _statusChip('DRAFT', 'DRAFT'),
                _statusChip('COMPLETED', 'COMPLETED'),
                _statusChip('CANCELLED', 'CANCELLED'),
              ],
            ),
          ),
          const SizedBox(height: 8),

          // --- Body --------------------------------------------------------
          Expanded(
            child: plansAsync.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => Center(
                child: Text('${l10n.translate('error')}: $e'),
              ),
              data: (plans) {
                if (plans.isEmpty) {
                  return Center(
                    child: Text(l10n.translate('clinicTreatmentPlansEmpty')),
                  );
                }
                return RefreshIndicator(
                  onRefresh: () async {
                    ref.invalidate(treatmentPlansForClinicProvider(filter));
                  },
                  child: ListView.separated(
                    itemCount: plans.length,
                    separatorBuilder: (_, __) => const SizedBox(height: 8),
                    itemBuilder: (ctx, i) => _PlanRow(
                      plan: plans[i],
                      moneyOf: _money,
                      shortDateOf: _shortDate,
                      statusColorOf: (s) =>
                          _statusColor(s, Theme.of(ctx).colorScheme),
                      paymentColorOf: _paymentColor,
                    ),
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  Widget _statusChip(String? value, String label) {
    final selected = _statusFilter == value;
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: ChoiceChip(
        label: Text(label),
        selected: selected,
        onSelected: (_) => setState(() => _statusFilter = value),
      ),
    );
  }
}

class _PlanRow extends StatelessWidget {
  final TreatmentPlanSummaryDto plan;
  final String Function(int minor, String currency) moneyOf;
  final String Function(String? iso) shortDateOf;
  final Color Function(String status) statusColorOf;
  final Color Function(String paymentStatus) paymentColorOf;

  const _PlanRow({
    required this.plan,
    required this.moneyOf,
    required this.shortDateOf,
    required this.statusColorOf,
    required this.paymentColorOf,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final title = plan.title?.trim().isNotEmpty == true
        ? plan.title!.trim()
        : l10n.translate('clinicTreatmentPlansUntitled');
    final patient =
        plan.patientName?.trim().isNotEmpty == true ? plan.patientName! : '—';
    final doctor = plan.attendingDoctorName?.trim().isNotEmpty == true
        ? plan.attendingDoctorName!
        : '—';
    final theme = Theme.of(context);

    return InkWell(
      borderRadius: BorderRadius.circular(10),
      onTap: () {
        // For now opens a simple read-only details dialog; the wizard handles
        // creation only. Editing can be re-introduced later by switching to
        // a "edit existing plan" flow.
        showDialog<void>(
          context: context,
          builder: (ctx) => AlertDialog(
            title: Text('#${plan.id} · $title'),
            content: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  _kv(l10n.translate('clinicTreatmentPlansPatient'), patient),
                  _kv(l10n.translate('clinicTreatmentPlansDoctor'), doctor),
                  _kv(l10n.translate('treatmentPlanDiagnosis'),
                      plan.diagnosis ?? '—'),
                  _kv(l10n.translate('treatmentPlanNotes'),
                      plan.notes ?? '—'),
                  _kv(l10n.translate('clinicTreatmentPlansTotal'),
                      moneyOf(plan.totalMinor, plan.currency)),
                  _kv(l10n.translate('clinicTreatmentPlansPaid'),
                      moneyOf(plan.paidMinor, plan.currency)),
                  _kv(l10n.translate('clinicTreatmentPlansOutstanding'),
                      moneyOf(plan.owedMinor, plan.currency)),
                  _kv(l10n.translate('clinicTreatmentPlansStatus'), plan.status),
                  _kv(l10n.translate('clinicTreatmentPlansPaymentStatus'),
                      plan.planPaymentStatus),
                  _kv(l10n.translate('clinicTreatmentPlansUpdated'),
                      shortDateOf(plan.updatedAt)),
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
      },
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          border: Border.all(color: Colors.grey.shade200),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Header: id + title + status pill
            Row(
              children: [
                Text(
                  '#${plan.id}',
                  style: TextStyle(
                    color: Colors.grey.shade600,
                    fontFeatures: const [FontFeature.tabularFigures()],
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    title,
                    style: const TextStyle(
                        fontWeight: FontWeight.w600, fontSize: 15),
                    overflow: TextOverflow.ellipsis,
                  ),
                ),
                _pill(plan.status, statusColorOf(plan.status)),
              ],
            ),
            const SizedBox(height: 4),

            // Patient · Doctor
            Wrap(
              spacing: 16,
              runSpacing: 2,
              children: [
                _iconText(Icons.person_outline, patient),
                _iconText(Icons.medical_services_outlined, doctor),
                _iconText(
                  Icons.update,
                  '${l10n.translate('clinicTreatmentPlansUpdated')}: ${shortDateOf(plan.updatedAt)}',
                ),
              ],
            ),
            const SizedBox(height: 6),

            // Money row
            Row(
              children: [
                Expanded(
                  child: _money3(
                    l10n.translate('clinicTreatmentPlansTotal'),
                    moneyOf(plan.totalMinor, plan.currency),
                  ),
                ),
                Expanded(
                  child: _money3(
                    l10n.translate('clinicTreatmentPlansPaid'),
                    moneyOf(plan.paidMinor, plan.currency),
                    color: Colors.green.shade700,
                  ),
                ),
                Expanded(
                  child: _money3(
                    l10n.translate('clinicTreatmentPlansOutstanding'),
                    moneyOf(plan.owedMinor, plan.currency),
                    color: plan.owedMinor > 0
                        ? Colors.red.shade700
                        : Colors.grey.shade600,
                  ),
                ),
                _pill(plan.planPaymentStatus, paymentColorOf(plan.planPaymentStatus)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  static Widget _iconText(IconData ic, String text) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(ic, size: 14, color: Colors.grey.shade600),
        const SizedBox(width: 4),
        Text(text, style: const TextStyle(fontSize: 12.5)),
      ],
    );
  }

  static Widget _money3(String label, String value, {Color? color}) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(label,
            style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
        Text(
          value,
          style: TextStyle(
            fontWeight: FontWeight.w600,
            color: color,
            fontFeatures: const [FontFeature.tabularFigures()],
          ),
        ),
      ],
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
