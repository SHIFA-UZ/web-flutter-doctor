import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_chart_codec.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_fdi_chart.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

/// Read-only FDI chart for a treatment plan with planned vs completed highlighting.
class DentalPlanReadonlyView extends StatelessWidget {
  const DentalPlanReadonlyView({
    super.key,
    required this.brand,
    required this.dentalPlanDocumentation,
    this.lines = const [],
    this.compact = false,
  });

  final Color brand;
  final String? dentalPlanDocumentation;
  final List<LineDetailDto> lines;
  final bool compact;

  static Map<String, DentalToothPlanState> toothStatesFromLines(
    List<LineDetailDto> lines,
  ) {
    final completed = <String, int>{};
    final open = <String, int>{};
    for (final line in lines) {
      if (line.status == 'CANCELLED') continue;
      final meta = line.specialtyMetadata;
      if (meta == null || meta.isEmpty) continue;
      try {
        final m = jsonDecode(meta) as Map<String, dynamic>?;
        final fdi = m?['fdi']?.toString();
        if (fdi == null || fdi.isEmpty) continue;
        if (line.status == 'COMPLETED') {
          completed[fdi] = (completed[fdi] ?? 0) + 1;
        } else {
          open[fdi] = (open[fdi] ?? 0) + 1;
        }
      } catch (_) {}
    }
    final out = <String, DentalToothPlanState>{};
    final keys = {...completed.keys, ...open.keys};
    for (final fdi in keys) {
      final c = completed[fdi] ?? 0;
      final o = open[fdi] ?? 0;
      if (c > 0 && o > 0) {
        out[fdi] = DentalToothPlanState.partial;
      } else if (c > 0) {
        out[fdi] = DentalToothPlanState.completed;
      } else if (o > 0) {
        out[fdi] = DentalToothPlanState.planned;
      }
    }
    return out;
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (dentalPlanDocumentation == null || dentalPlanDocumentation!.trim().isEmpty) {
      return const SizedBox.shrink();
    }

    Map<String, dynamic>? doc;
    try {
      doc = jsonDecode(dentalPlanDocumentation!) as Map<String, dynamic>?;
    } catch (_) {
      return const SizedBox.shrink();
    }
    if (doc == null) return const SizedBox.shrink();

    final dentRaw = doc['dentition']?.toString().trim().toLowerCase();
    final dentition =
        dentRaw == 'primary' ? DentalDentition.primary : DentalDentition.permanent;

    final toothPlanStates = toothStatesFromLines(lines);

    final teethRaw = doc['teeth'];
    final serviceCounts = <String, int>{};
    if (teethRaw is Map) {
      teethRaw.forEach((k, v) {
        if (k.toString() == DentalChartCodec.generalServicesKey) return;
        if (v is List && v.isNotEmpty) {
          serviceCounts[k.toString()] = v.length;
        }
      });
    }

    final completed = lines.where((l) => l.status == 'COMPLETED').length;
    final total = lines.where((l) => l.status != 'CANCELLED').length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        if (total > 0)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Chip(
              avatar: Icon(
                completed == total ? Icons.check_circle : Icons.pending_outlined,
                size: 18,
                color: brand,
              ),
              label: Text(
                l10n
                    .translate('dentalPlanProgress')
                    .replaceAll('{{done}}', '$completed')
                    .replaceAll('{{total}}', '$total'),
              ),
            ),
          ),
        SizedBox(
          height: compact ? 220 : null,
          child: DentalFdiChart(
            brand: brand,
            dentition: dentition,
            showDentitionToggle: false,
            toothServiceCounts: serviceCounts,
            toothPlanStates: toothPlanStates,
            onToothTap: (_) {},
            onDentitionChanged: null,
          ),
        ),
        if (!compact && toothPlanStates.isNotEmpty) ...[
          const SizedBox(height: 8),
          Wrap(
            spacing: 12,
            runSpacing: 4,
            children: [
              if (toothPlanStates.values.contains(DentalToothPlanState.planned))
                _LegendDot(
                  color: brand.withValues(alpha: 0.35),
                  label: l10n.translate('dentalPlanLegendPlanned'),
                ),
              if (toothPlanStates.values.contains(DentalToothPlanState.partial))
                _LegendDot(
                  color: Colors.orange.shade700,
                  label: l10n.translate('dentalPlanLegendPartial'),
                ),
              if (toothPlanStates.values.contains(DentalToothPlanState.completed))
                _LegendDot(
                  color: brand,
                  label: l10n.translate('dentalPlanLegendCompleted'),
                ),
            ],
          ),
        ],
      ],
    );
  }
}

class _LegendDot extends StatelessWidget {
  const _LegendDot({required this.color, required this.label});
  final Color color;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: 12,
          height: 12,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        const SizedBox(width: 4),
        Text(label, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}
