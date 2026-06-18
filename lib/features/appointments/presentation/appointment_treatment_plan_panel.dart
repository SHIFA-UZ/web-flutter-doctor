import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

/// During a visit: pick an active comprehensive plan linked to chart fulfillment.
class AppointmentTreatmentPlanPanel extends ConsumerStatefulWidget {
  const AppointmentTreatmentPlanPanel({
    super.key,
    required this.clinicId,
    required this.patientId,
    required this.appointmentId,
    required this.brand,
    this.linkedPlanId,
    this.linkedPlanTitle,
    this.fulfilledLineIds = const [],
    this.onPlanSelected,
    this.onPlanLinked,
    this.embedded = false,
  });

  final int clinicId;
  final int patientId;
  final String appointmentId;
  final Color brand;
  final int? linkedPlanId;
  final String? linkedPlanTitle;
  final List<int> fulfilledLineIds;
  final ValueChanged<int?>? onPlanSelected;
  final VoidCallback? onPlanLinked;
  final bool embedded;

  @override
  ConsumerState<AppointmentTreatmentPlanPanel> createState() =>
      AppointmentTreatmentPlanPanelState();
}

class AppointmentTreatmentPlanPanelState
    extends ConsumerState<AppointmentTreatmentPlanPanel> {
  List<TreatmentPlanSummaryDto> _plans = [];
  bool _loadingPlans = true;
  int? _selectedPlanId;

  int? get selectedPlanId => _selectedPlanId;

  @override
  void initState() {
    super.initState();
    _selectedPlanId = widget.linkedPlanId;
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadPlans());
  }

  @override
  void didUpdateWidget(covariant AppointmentTreatmentPlanPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.linkedPlanId != widget.linkedPlanId &&
        widget.linkedPlanId != null) {
      _selectedPlanId = widget.linkedPlanId;
      widget.onPlanSelected?.call(_selectedPlanId);
    }
  }

  Future<void> _loadPlans() async {
    setState(() => _loadingPlans = true);
    try {
      final all = await fetchTreatmentPlansForPatient(
        ref,
        clinicId: widget.clinicId,
        patientId: widget.patientId,
        status: 'ACTIVE',
        planKind: 'COMPREHENSIVE',
      );
      if (!mounted) return;
      setState(() {
        _plans = all;
        _loadingPlans = false;
        if (_selectedPlanId != null &&
            !_plans.any((p) => p.id == _selectedPlanId)) {
          _selectedPlanId = null;
        }
        _selectedPlanId ??= widget.linkedPlanId;
        _selectedPlanId ??= _plans.length == 1 ? _plans.first.id : null;
      });
      if (_selectedPlanId != null) {
        widget.onPlanSelected?.call(_selectedPlanId);
      }
    } catch (_) {
      if (mounted) setState(() => _loadingPlans = false);
    }
  }

  TreatmentPlanSummaryDto? get _selectedSummary {
    final id = _selectedPlanId;
    if (id == null) return null;
    for (final p in _plans) {
      if (p.id == id) return p;
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (_loadingPlans) {
      return const LinearProgressIndicator(minHeight: 2);
    }
    if (_plans.isEmpty) {
      return const SizedBox.shrink();
    }

    final summary = _selectedSummary;

    final body = Padding(
      padding: EdgeInsets.all(widget.embedded ? 0 : 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (widget.linkedPlanId != null && widget.linkedPlanTitle != null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8),
              child: Row(
                children: [
                  Icon(Icons.assignment_outlined, color: widget.brand),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      l10n
                          .translate('appointmentLinkedPlanBanner')
                          .replaceAll('{{id}}', '${widget.linkedPlanId}')
                          .replaceAll('{{title}}', widget.linkedPlanTitle!),
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                ],
              ),
            ),
          Text(
            l10n.translate('appointmentTreatmentPlanTitle'),
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 4),
          Text(
            l10n.translate('appointmentTreatmentPlanChartHint'),
            style: Theme.of(context).textTheme.bodySmall?.copyWith(
                  color: Colors.grey.shade700,
                ),
          ),
          const SizedBox(height: 8),
          DropdownButtonFormField<int?>(
            value: _selectedPlanId,
            decoration: InputDecoration(
              labelText: l10n.translate('appointmentTreatmentPlanPick'),
            ),
            items: [
              DropdownMenuItem<int?>(
                value: null,
                child: Text(l10n.translate('appointmentTreatmentPlanNone')),
              ),
              for (final p in _plans)
                DropdownMenuItem<int?>(
                  value: p.id,
                  child: Text(
                    p.title?.trim().isNotEmpty == true
                        ? p.title!
                        : '${l10n.translate('clinicTreatmentPlansUntitled')} #${p.id}',
                  ),
                ),
            ],
            onChanged: (v) {
              setState(() => _selectedPlanId = v);
              widget.onPlanSelected?.call(v);
              widget.onPlanLinked?.call();
            },
          ),
          if (summary != null && summary.linesTotalCount > 0) ...[
            const SizedBox(height: 8),
            Text(
              l10n
                  .translate('dentalPlanProgress')
                  .replaceAll('{{done}}', '${summary.linesCompletedCount}')
                  .replaceAll('{{total}}', '${summary.linesTotalCount}'),
              style: Theme.of(context).textTheme.bodySmall,
            ),
          ],
        ],
      ),
    );

    if (widget.embedded) {
      return body;
    }

    return Card(
      child: body,
    );
  }
}
