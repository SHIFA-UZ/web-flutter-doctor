import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

/// Plan totals and optional session payment during a linked-plan visit.
class AppointmentPlanFinanceCard extends ConsumerStatefulWidget {
  const AppointmentPlanFinanceCard({
    super.key,
    required this.clinicId,
    required this.planId,
    required this.appointmentId,
    required this.brand,
    this.onTotalsRefreshed,
    this.embedded = false,
  });

  final int clinicId;
  final int planId;
  final String appointmentId;
  final Color brand;
  final VoidCallback? onTotalsRefreshed;
  final bool embedded;

  @override
  ConsumerState<AppointmentPlanFinanceCard> createState() =>
      AppointmentPlanFinanceCardState();
}

class AppointmentPlanFinanceCardState
    extends ConsumerState<AppointmentPlanFinanceCard> {
  TreatmentPlanSummaryDto? _summary;
  bool _loading = true;
  final TextEditingController _amountCtrl = TextEditingController();
  String _method = 'CASH';
  int? _recordedPaymentMinor;
  String? _recordedPaymentMethod;

  int? get recordedPaymentMinor => _recordedPaymentMinor;
  String? get recordedPaymentMethod => _recordedPaymentMethod;

  int get _appointmentIdInt => int.tryParse(widget.appointmentId) ?? 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  @override
  void didUpdateWidget(covariant AppointmentPlanFinanceCard oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.planId != widget.planId) {
      _load();
    }
  }

  @override
  void dispose() {
    _amountCtrl.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() => _loading = true);
    try {
      final detail = await fetchTreatmentPlanDetail(ref, widget.planId);
      if (!mounted) return;
      setState(() {
        _summary = detail?.summary;
        _loading = false;
      });
      widget.onTotalsRefreshed?.call();
    } catch (_) {
      if (mounted) setState(() => _loading = false);
    }
  }

  int? _parseAmountMinor() {
    final raw = _amountCtrl.text.trim().replaceAll(',', '.');
    if (raw.isEmpty) return null;
    final major = double.tryParse(raw);
    if (major == null || major <= 0) return null;
    return (major * 100).round();
  }

  /// Records payment entered by the doctor when ending the visit.
  Future<bool> recordSessionPaymentIfNeeded() async {
    final amountMinor = _parseAmountMinor();
    if (amountMinor == null) return true;
    if (_recordedPaymentMinor != null) return true;

    final summary = _summary;
    if (summary == null) return false;

    try {
      await recordClinicPayment(
        ref,
        clinicId: widget.clinicId,
        treatmentPlanId: widget.planId,
        amountMinor: amountMinor,
        currency: summary.currency,
        method: _method,
        linkedAppointmentId: _appointmentIdInt,
      );
      _recordedPaymentMinor = amountMinor;
      _recordedPaymentMethod = _method;
      await _load();
      return true;
    } catch (_) {
      return false;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loading) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: LinearProgressIndicator(minHeight: 2),
      );
    }
    final summary = _summary;
    if (summary == null) {
      final message = Padding(
        padding: EdgeInsets.all(widget.embedded ? 0 : 12),
        child: Text(l10n.translate('appointmentPlanFinanceLoadFailed')),
      );
      return widget.embedded ? message : Card(child: message);
    }

    final ccy = summary.currency;
    String fmt(int minor) => '${(minor / 100).toStringAsFixed(2)} $ccy';

    final content = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          l10n.translate('appointmentPlanFinanceTitle'),
          style: Theme.of(context).textTheme.titleSmall,
        ),
        const SizedBox(height: 8),
        Text('${l10n.translate('appointmentPlanFinanceTotal')}: ${fmt(summary.totalMinor)}'),
        Text('${l10n.translate('appointmentPlanFinancePaid')}: ${fmt(summary.paidMinor)}'),
        Text(
          '${l10n.translate('appointmentPlanFinanceOutstanding')}: ${fmt(summary.owedMinor)}',
          style: TextStyle(
            fontWeight: FontWeight.w700,
            color: widget.brand,
          ),
        ),
        const SizedBox(height: 12),
        Text(
          l10n.translate('appointmentPlanFinanceSessionPayment'),
          style: Theme.of(context).textTheme.labelLarge,
        ),
        const SizedBox(height: 8),
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              flex: 2,
              child: TextField(
                controller: _amountCtrl,
                keyboardType:
                    const TextInputType.numberWithOptions(decimal: true),
                decoration: InputDecoration(
                  labelText: l10n.translate('appointmentPlanFinanceAmount'),
                  suffixText: ccy,
                ),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: DropdownButtonFormField<String>(
                value: _method,
                decoration: InputDecoration(
                  labelText: l10n.translate('appointmentPlanFinanceMethod'),
                ),
                items: [
                  DropdownMenuItem(
                    value: 'CASH',
                    child: Text(l10n.clinicPaymentMethodLabel('CASH')),
                  ),
                  DropdownMenuItem(
                    value: 'CARD_EXTERNAL',
                    child: Text(l10n.clinicPaymentMethodLabel('CARD_EXTERNAL')),
                  ),
                  DropdownMenuItem(
                    value: 'TRANSFER',
                    child: Text(l10n.clinicPaymentMethodLabel('TRANSFER')),
                  ),
                ],
                onChanged: (v) {
                  if (v != null) setState(() => _method = v);
                },
              ),
            ),
          ],
        ),
        if (_recordedPaymentMinor != null) ...[
          const SizedBox(height: 8),
          Text(
            l10n
                .translate('appointmentPlanFinanceRecorded')
                .replaceAll(
                  '{{amount}}',
                  fmt(_recordedPaymentMinor!),
                ),
            style: TextStyle(color: Colors.green.shade700, fontSize: 12),
          ),
        ],
      ],
    );

    if (widget.embedded) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Divider(color: Colors.grey.shade200, height: 1),
          const SizedBox(height: 12),
          content,
        ],
      );
    }

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: content,
      ),
    );
  }
}
