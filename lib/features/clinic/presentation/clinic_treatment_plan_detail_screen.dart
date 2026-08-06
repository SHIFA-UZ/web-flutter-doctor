import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_plan_readonly_view.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_pdf_export.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_providers.dart';

/// Full treatment plan view with FDI chart, lines, and linked visits.
class ClinicTreatmentPlanDetailScreen extends ConsumerWidget {
  const ClinicTreatmentPlanDetailScreen({
    super.key,
    required this.planId,
  });

  final int planId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final detailAsync = ref.watch(treatmentPlanDetailProvider(planId));
    final visitsAsync = ref.watch(treatmentPlanVisitsProvider(planId));

    return Scaffold(
      appBar: AppBar(
        leading: appBarBackLeading(context),
        automaticallyImplyLeading: false,
        backgroundColor: brand,
        foregroundColor: Colors.white,
        title: detailAsync.maybeWhen(
          data: (detail) {
            if (detail == null) {
              return Text(l10n.translate('clinicTreatmentPlanDetailTitle'));
            }
            final title = detail.summary.title?.trim().isNotEmpty == true
                ? detail.summary.title!.trim()
                : l10n.translate('clinicTreatmentPlansUntitled');
            return Text('#${detail.summary.id} · $title');
          },
          orElse: () => Text(l10n.translate('clinicTreatmentPlanDetailTitle')),
        ),
        actions: [
          IconButton(
            tooltip: l10n.translate('clinicTreatmentPlanExportPdf'),
            icon: const Icon(Icons.picture_as_pdf),
            onPressed: () => exportTreatmentPlanPdf(context, ref, planId: planId),
          ),
        ],
      ),
      body: detailAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('${l10n.translate('error')}: $e')),
        data: (detail) {
          if (detail == null) {
            return Center(
              child: Text(l10n.translate('clinicTreatmentPlanDetailNotFound')),
            );
          }
          final summary = detail.summary;
          final scheme = Theme.of(context).colorScheme;
          return SingleChildScrollView(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _OverviewCard(
                  summary: summary,
                  scheme: scheme,
                  l10n: l10n,
                ),
                if (detail.dentalPlanDocumentation != null &&
                    detail.dentalPlanDocumentation!.trim().isNotEmpty) ...[
                  const SizedBox(height: 16),
                  _SectionCard(
                    title: l10n.translate('clinicTreatmentPlanDetailDentalChart'),
                    child: DentalPlanReadonlyView(
                      brand: brand,
                      dentalPlanDocumentation: detail.dentalPlanDocumentation,
                      lines: detail.lines,
                      compact: false,
                    ),
                  ),
                ],
                if (detail.lines.isNotEmpty) ...[
                  const SizedBox(height: 16),
                  _SectionCard(
                    title: l10n.translate('treatmentPlanWizardSectionServices'),
                    child: _PlanLinesTable(
                      lines: detail.lines,
                      l10n: l10n,
                    ),
                  ),
                ],
                const SizedBox(height: 16),
                _SectionCard(
                  title: l10n.translate('clinicTreatmentPlansVisits'),
                  child: visitsAsync.when(
                    loading: () => Padding(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      child: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(strokeWidth: 2),
                          ),
                          const SizedBox(width: 12),
                          Text(l10n.translate('clinicTreatmentPlansVisitsLoading')),
                        ],
                      ),
                    ),
                    error: (e, _) => Text('${l10n.translate('error')}: $e'),
                    data: (visits) {
                      if (visits.isEmpty) {
                        return Text(
                          l10n.translate('clinicTreatmentPlansNoVisits'),
                          style: TextStyle(color: Colors.grey.shade700),
                        );
                      }
                      return _VisitsList(
                        summary: summary,
                        visits: visits,
                        ref: ref,
                        l10n: l10n,
                        scheme: scheme,
                      );
                    },
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}

class _OverviewCard extends StatelessWidget {
  const _OverviewCard({
    required this.summary,
    required this.scheme,
    required this.l10n,
  });

  final TreatmentPlanSummaryDto summary;
  final ColorScheme scheme;
  final AppLocalizations l10n;

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

  Color _statusColor(String status) {
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
      default:
        return Colors.grey.shade600;
    }
  }

  @override
  Widget build(BuildContext context) {
    final title = summary.title?.trim().isNotEmpty == true
        ? summary.title!.trim()
        : l10n.translate('clinicTreatmentPlansUntitled');
    final patient = summary.patientName?.trim().isNotEmpty == true
        ? summary.patientName!
        : '—';
    final doctor = summary.attendingDoctors.isNotEmpty
        ? summary.attendingDoctors.map((d) => d.name).join(', ')
        : (summary.attendingDoctorName?.trim().isNotEmpty == true
            ? summary.attendingDoctorName!
            : '—');
    final symptoms = summary.symptoms.join(', ');

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
            ),
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                _StatusPill(
                  label: l10n.clinicPlanStatusLabel(summary.status),
                  color: _statusColor(summary.status),
                ),
                _StatusPill(
                  label: l10n.clinicPaymentStatusLabel(summary.planPaymentStatus),
                  color: _paymentColor(summary.planPaymentStatus),
                ),
                if (summary.linesTotalCount > 0)
                  Chip(
                    avatar: Icon(
                      summary.linesCompletedCount == summary.linesTotalCount
                          ? Icons.check_circle
                          : Icons.pending_outlined,
                      size: 18,
                    ),
                    label: Text(
                      l10n
                          .translate('dentalPlanProgress')
                          .replaceAll('{{done}}', '${summary.linesCompletedCount}')
                          .replaceAll('{{total}}', '${summary.linesTotalCount}'),
                    ),
                  ),
              ],
            ),
            const SizedBox(height: 16),
            _kv(l10n.translate('clinicTreatmentPlansPatient'), patient),
            _kv(l10n.translate('clinicTreatmentPlansDoctor'), doctor),
            _kv(l10n.translate('treatmentPlanDiagnosis'), summary.diagnosis ?? '—'),
            if (symptoms.isNotEmpty)
              _kv(l10n.translate('treatmentPlanWizardSymptoms'), symptoms),
            _kv(l10n.translate('treatmentPlanNotes'), summary.notes ?? '—'),
            const Divider(height: 24),
            _kv(
              l10n.translate('clinicTreatmentPlansTotal'),
              _money(summary.totalMinor, summary.currency),
            ),
            _kv(
              l10n.translate('clinicTreatmentPlansPaid'),
              _money(summary.paidMinor, summary.currency),
            ),
            _kv(
              l10n.translate('clinicTreatmentPlansOutstanding'),
              _money(summary.owedMinor, summary.currency),
              valueStyle: TextStyle(
                fontWeight: FontWeight.w700,
                color: summary.owedMinor > 0
                    ? Colors.red.shade700
                    : Colors.grey.shade700,
              ),
            ),
            _kv(
              l10n.translate('clinicTreatmentPlansUpdated'),
              _shortDate(summary.updatedAt),
            ),
          ],
        ),
      ),
    );
  }

  static Widget _kv(String k, String v, {TextStyle? valueStyle}) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 3),
      child: LayoutBuilder(
        builder: (context, constraints) {
          final stack = constraints.maxWidth < 360;
          final label = Text(
            k,
            style: const TextStyle(
              color: Colors.black54,
              fontWeight: FontWeight.w500,
            ),
          );
          final value = Text(v, style: valueStyle);
          if (stack) {
            return Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                label,
                const SizedBox(height: 2),
                value,
              ],
            );
          }
          return Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              SizedBox(width: 140, child: label),
              Expanded(child: value),
            ],
          );
        },
      ),
    );
  }
}

class _SectionCard extends StatelessWidget {
  const _SectionCard({required this.title, required this.child});

  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.w600,
                  ),
            ),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}

class _PlanLinesTable extends StatelessWidget {
  const _PlanLinesTable({required this.lines, required this.l10n});

  final List<LineDetailDto> lines;
  final AppLocalizations l10n;

  String _money(int minor, String currency) =>
      '${(minor / 100).toStringAsFixed(2)} $currency';

  String _lineStatusLabel(String status) {
    switch (status.toUpperCase()) {
      case 'PLANNED':
        return l10n.translate('clinicTreatmentPlanLineStatusPlanned');
      case 'SCHEDULED':
        return l10n.translate('clinicTreatmentPlanLineStatusScheduled');
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

  @override
  Widget build(BuildContext context) {
    final sorted = [...lines]..sort((a, b) => a.sortOrder.compareTo(b.sortOrder));
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: DataTable(
        columnSpacing: 20,
        columns: [
          DataColumn(label: Text(l10n.translate('clinicPlansColTitle'))),
          DataColumn(
            label: Text(l10n.translate('treatmentPlanWizardQty')),
            numeric: true,
          ),
          DataColumn(
            label: Text(l10n.translate('clinicTreatmentPlansTotal')),
            numeric: true,
          ),
          DataColumn(label: Text(l10n.translate('clinicTreatmentPlanLineStatus'))),
          DataColumn(label: Text(l10n.translate('treatmentPlanWizardLineAppt'))),
        ],
        rows: [
          for (final line in sorted)
            DataRow(
              cells: [
                DataCell(
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 260),
                    child: Text(line.title, overflow: TextOverflow.ellipsis),
                  ),
                ),
                DataCell(Text('${line.quantity}')),
                DataCell(Text(_money(line.lineTotalMinor, line.currency))),
                DataCell(Text(_lineStatusLabel(line.status))),
                DataCell(Text(
                  line.linkedAppointment != null
                      ? '#${line.linkedAppointment!.id}'
                      : '—',
                )),
              ],
            ),
        ],
      ),
    );
  }
}

class _VisitsList extends StatelessWidget {
  const _VisitsList({
    required this.summary,
    required this.visits,
    required this.ref,
    required this.l10n,
    required this.scheme,
  });

  final TreatmentPlanSummaryDto summary;
  final List<TreatmentPlanVisitDto> visits;
  final WidgetRef ref;
  final AppLocalizations l10n;
  final ColorScheme scheme;

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

  String _timingLabel(String timing) {
    switch (timing.toUpperCase()) {
      case 'UPCOMING':
        return l10n.translate('clinicTreatmentPlansVisitUpcoming');
      case 'CANCELLED':
        return l10n.translate('clinicTreatmentPlansVisitCancelled');
      case 'PAST':
      default:
        return l10n.translate('clinicTreatmentPlansVisitPast');
    }
  }

  Color _timingColor(String timing) {
    switch (timing.toUpperCase()) {
      case 'UPCOMING':
        return Colors.blue.shade700;
      case 'CANCELLED':
        return Colors.red.shade700;
      default:
        return Colors.grey.shade700;
    }
  }

  Future<void> _openVisit(BuildContext context, TreatmentPlanVisitDto visit) async {
    if (visit.status.toUpperCase() == 'CANCELLED') {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.translate('clinicTreatmentPlansVisitCancelledOpen')),
        ),
      );
      return;
    }
    final tz = ref.read(selectedClinicProvider)?.timeZone;
    final appt = Appointment(
      id: visit.appointmentId.toString(),
      patientName: summary.patientName?.trim().isNotEmpty == true
          ? summary.patientName!.trim()
          : l10n.patient,
      patientId: summary.patientId?.toString(),
      location: visit.location,
      start: CalendarEntry.utcIsoToTimeOfDayInZone(visit.startAt, tz),
      end: CalendarEntry.utcIsoToTimeOfDayInZone(visit.endAt, tz),
      status: AppointmentStatus.fromString(visit.status),
    );
    if (!context.mounted) return;
    await ShellScope.pushNamed(
      context,
      appt.isVideo ? AppRoutes.videoCall : AppRoutes.inPerson,
      arguments: appt,
    );
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      children: [
        for (final visit in visits) ...[
          ListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(
              '${l10n.translate('clinicTreatmentPlansVisitLabel')} #${visit.appointmentId}',
              style: const TextStyle(fontWeight: FontWeight.w600),
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '${_shortDate(visit.startAt)} · ${visit.doctorName}',
                ),
                if (visit.services.isNotEmpty)
                  Text(
                    visit.services.join(', '),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
                  ),
                const SizedBox(height: 4),
                Text(
                  '${l10n.translate('clinicTreatmentPlansTotal')}: '
                  '${_money(visit.visitTotalMinor, visit.currency)}',
                  style: const TextStyle(fontSize: 12),
                ),
              ],
            ),
            trailing: Wrap(
              spacing: 4,
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                _StatusPill(
                  label: _timingLabel(visit.timing),
                  color: _timingColor(visit.timing),
                ),
                if (visit.status.toUpperCase() != 'CANCELLED')
                  IconButton(
                    tooltip: l10n.translate('clinicTreatmentPlansOpenVisit'),
                    icon: const Icon(Icons.open_in_new, size: 20),
                    onPressed: () => _openVisit(context, visit),
                  ),
              ],
            ),
            onTap: visit.status.toUpperCase() != 'CANCELLED'
                ? () => _openVisit(context, visit)
                : null,
          ),
          if (visit != visits.last) const Divider(height: 1),
        ],
      ],
    );
  }
}

class _StatusPill extends StatelessWidget {
  const _StatusPill({required this.label, required this.color});

  final String label;
  final Color color;

  @override
  Widget build(BuildContext context) {
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
}
