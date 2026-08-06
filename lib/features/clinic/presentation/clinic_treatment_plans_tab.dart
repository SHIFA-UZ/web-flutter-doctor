import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/clinic_table_shell.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_pdf_export.dart';
import 'package:shifa_doc_app_v1/features/clinic/presentation/treatment_plan_wizard_sheet.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/calendar/domain/calendar_models.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_finance_providers.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
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
  final Set<int> _expandedPlanIds = {};

  /// Debounced search needle — filtering is client-side so this only throttles
  /// table rebuilds while typing, not network calls.
  static const _searchDebounceMs = 300;

  /// Sort state for the DataTable. Default: most recently updated first
  /// (column index 10 = "updated", descending).
  int _sortIdx = 10;
  bool _sortAsc = false;

  @override
  void dispose() {
    _debounce?.cancel();
    _filterCtrl.dispose();
    super.dispose();
  }

  @override
  void didUpdateWidget(covariant ClinicTreatmentPlansTab oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.clinicId != widget.clinicId) {
      for (final id in _expandedPlanIds) {
        ref.invalidate(treatmentPlanVisitsProvider(id));
      }
      _expandedPlanIds.clear();
    }
  }

  /// Status + search are applied in-memory (see finance installments tab) so
  /// typing in the search box does not re-hit the API on every debounce tick.
  List<TreatmentPlanSummaryDto> _applyClientFilters(
    List<TreatmentPlanSummaryDto> plans,
  ) {
    Iterable<TreatmentPlanSummaryDto> out = plans;
    final status = _statusFilter?.trim();
    if (status != null && status.isNotEmpty) {
      final needle = status.toUpperCase();
      out = out.where((p) => p.status.toUpperCase() == needle);
    }
    if (_query.isNotEmpty) {
      final needle = _query.toLowerCase();
      out = out.where((p) {
        final title = (p.title ?? '').toLowerCase();
        final patient = (p.patientName ?? '').toLowerCase();
        return title.contains(needle) || patient.contains(needle);
      });
    }
    return out.toList();
  }

  void _onSort(int i, bool asc) =>
      setState(() => (_sortIdx = i, _sortAsc = asc));

  void _onSearchChanged(String value) {
    _debounce?.cancel();
    _debounce = Timer(const Duration(milliseconds: _searchDebounceMs), () {
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
          c = a.visitCount.compareTo(b.visitCount);
          break;
        case 3:
          c = (a.patientName ?? '').toLowerCase().compareTo(
              (b.patientName ?? '').toLowerCase());
          break;
        case 4:
          final ad = a.attendingDoctors.isNotEmpty
              ? a.attendingDoctors.map((d) => d.name).join(',')
              : (a.attendingDoctorName ?? '');
          final bd = b.attendingDoctors.isNotEmpty
              ? b.attendingDoctors.map((d) => d.name).join(',')
              : (b.attendingDoctorName ?? '');
          c = ad.toLowerCase().compareTo(bd.toLowerCase());
          break;
        case 5:
          c = a.totalMinor.compareTo(b.totalMinor);
          break;
        case 6:
          c = a.paidMinor.compareTo(b.paidMinor);
          break;
        case 7:
          c = a.owedMinor.compareTo(b.owedMinor);
          break;
        case 8:
          c = a.status.compareTo(b.status);
          break;
        case 9:
          c = a.planPaymentStatus.compareTo(b.planPaymentStatus);
          break;
        case 10:
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
      refreshClinicFinancialData(ref, widget.clinicId);
      ref.invalidate(treatmentPlanVisitsProvider(plan.id));
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

  Future<void> _togglePlanVisits(TreatmentPlanSummaryDto plan) async {
    final planId = plan.id;
    if (_expandedPlanIds.contains(planId)) {
      setState(() => _expandedPlanIds.remove(planId));
      return;
    }
    setState(() => _expandedPlanIds.add(planId));

    final cached = ref.read(treatmentPlanVisitsProvider(planId));
    if (cached.hasValue) return;

    try {
      await ref.read(treatmentPlanVisitsProvider(planId).future);
    } catch (_) {
      // Error state is surfaced via [_visitsLoading]/empty row below.
    }
    if (mounted) setState(() {});
  }

  List<TreatmentPlanVisitDto> _visitsForPlan(int planId) =>
      ref.read(treatmentPlanVisitsProvider(planId)).valueOrNull ?? const [];

  bool _visitsLoading(int planId) =>
      ref.read(treatmentPlanVisitsProvider(planId)).isLoading;

  String? _clinicTimeZone(WidgetRef ref) =>
      ref.read(selectedClinicProvider)?.timeZone;

  String _timingLabel(BuildContext context, String timing) {
    final l10n = AppLocalizations.of(context)!;
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
      case 'PAST':
      default:
        return Colors.grey.shade700;
    }
  }

  Future<void> _openVisitAppointment(
    BuildContext context,
    TreatmentPlanSummaryDto plan,
    TreatmentPlanVisitDto visit,
  ) async {
    final l10n = AppLocalizations.of(context)!;
    if (visit.status.toUpperCase() == 'CANCELLED') {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(l10n.translate('clinicTreatmentPlansVisitCancelledOpen')),
        ),
      );
      return;
    }
    final tz = _clinicTimeZone(ref);
    final appt = Appointment(
      id: visit.appointmentId.toString(),
      patientName: plan.patientName?.trim().isNotEmpty == true
          ? plan.patientName!.trim()
          : l10n.patient,
      patientId: plan.patientId?.toString(),
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

  String _visitCountLabel(BuildContext context, int count) {
    final l10n = AppLocalizations.of(context)!;
    if (count == 0) return l10n.translate('clinicTreatmentPlansNoVisitsShort');
    final template = l10n.translate('clinicTreatmentPlansVisitCount');
    return template.replaceAll('{{count}}', count.toString());
  }

  String _appointmentStatusLabel(BuildContext context, String status) {
    final l10n = AppLocalizations.of(context)!;
    switch (status.toUpperCase()) {
      case 'REQUESTED':
        return l10n.translate('appointmentStatusRequested');
      case 'CONFIRMED':
        return l10n.translate('appointmentStatusConfirmed');
      case 'CANCELLED':
        return l10n.translate('appointmentStatusCancelled');
      case 'COMPLETED':
        return l10n.translate('appointmentStatusCompleted');
      case 'IN_PROGRESS':
        return l10n.translate('appointmentStatusInProgress');
      default:
        return status;
    }
  }

  Color _appointmentStatusColor(String status, ColorScheme scheme) {
    switch (status.toUpperCase()) {
      case 'CONFIRMED':
        return Colors.blue.shade700;
      case 'COMPLETED':
        return Colors.green.shade700;
      case 'CANCELLED':
        return Colors.red.shade700;
      case 'IN_PROGRESS':
        return Colors.indigo.shade700;
      case 'REQUESTED':
        return Colors.amber.shade800;
      default:
        return scheme.outline;
    }
  }

  Future<void> _openPlanDetail(
    BuildContext context,
    TreatmentPlanSummaryDto plan,
  ) {
    return ShellScope.pushNamed(
      context,
      AppRoutes.clinicTreatmentPlanDetail,
      arguments: plan.id,
    );
  }

  List<DataRow> _buildTableRows(
    BuildContext context,
    List<TreatmentPlanSummaryDto> sorted,
    ColorScheme scheme,
    AppLocalizations l10n,
  ) {
    final rows = <DataRow>[];
    for (final p in sorted) {
      rows.add(_buildPlanRow(context, p, scheme, l10n));
      if (!_expandedPlanIds.contains(p.id)) continue;

      if (_visitsLoading(p.id)) {
        rows.add(_buildVisitLoadingRow(l10n));
        continue;
      }

      final visits = _visitsForPlan(p.id);
      if (visits.isEmpty) {
        rows.add(_buildVisitEmptyRow(l10n));
        continue;
      }

      for (final visit in visits) {
        rows.add(_buildVisitRow(context, p, visit, l10n));
      }
    }
    return rows;
  }

  DataRow _buildPlanRow(
    BuildContext context,
    TreatmentPlanSummaryDto p,
    ColorScheme scheme,
    AppLocalizations l10n,
  ) {
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
    final expanded = _expandedPlanIds.contains(p.id);
    return DataRow(
      cells: [
        DataCell(Text('#${p.id}')),
        DataCell(
          ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 220),
            child: InkWell(
              onTap: () => _openPlanDetail(context, p),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    title,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      color: Colors.blue.shade800,
                      decoration: TextDecoration.underline,
                    ),
                    overflow: TextOverflow.ellipsis,
                  ),
                  if (p.linesTotalCount > 0)
                    Text(
                      l10n
                          .translate('dentalPlanProgress')
                          .replaceAll('{{done}}', '${p.linesCompletedCount}')
                          .replaceAll('{{total}}', '${p.linesTotalCount}'),
                      style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                    ),
                ],
              ),
            ),
          ),
        ),
        DataCell(
          TextButton.icon(
            onPressed: () => _togglePlanVisits(p),
            icon: Icon(
              expanded ? Icons.expand_less : Icons.expand_more,
              size: 18,
            ),
            label: Text(_visitCountLabel(context, p.visitCount)),
            style: TextButton.styleFrom(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              visualDensity: VisualDensity.compact,
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
          l10n.clinicPaymentStatusLabel(p.planPaymentStatus),
          _paymentColor(p.planPaymentStatus),
        )),
        DataCell(Text(_shortDate(p.updatedAt))),
        DataCell(
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              IconButton(
                tooltip: l10n.translate('clinicTreatmentPlanExportPdf'),
                icon: const Icon(Icons.picture_as_pdf, size: 20),
                onPressed: () =>
                    exportTreatmentPlanPdf(context, ref, planId: p.id),
              ),
              IconButton(
                tooltip: l10n.translate('clinicPlansViewTooltip'),
                icon: const Icon(Icons.open_in_new, size: 20),
                onPressed: () => _openPlanDetail(context, p),
              ),
            ],
          ),
        ),
      ],
    );
  }

  DataRow _buildVisitLoadingRow(AppLocalizations l10n) {
    return DataRow(
      color: WidgetStateProperty.all(Colors.grey.shade50),
      cells: [
        const DataCell(SizedBox.shrink()),
        DataCell(
          Padding(
            padding: const EdgeInsets.only(left: 12),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                const SizedBox(width: 10),
                Text(
                  l10n.translate('clinicTreatmentPlansVisitsLoading'),
                  style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
                ),
              ],
            ),
          ),
        ),
        for (var i = 2; i < 12; i++) const DataCell(SizedBox.shrink()),
      ],
    );
  }

  DataRow _buildVisitEmptyRow(AppLocalizations l10n) {
    return DataRow(
      color: WidgetStateProperty.all(Colors.grey.shade50),
      cells: [
        const DataCell(SizedBox.shrink()),
        DataCell(
          Padding(
            padding: const EdgeInsets.only(left: 12),
            child: Text(
              l10n.translate('clinicTreatmentPlansNoVisits'),
              style: TextStyle(color: Colors.grey.shade700, fontSize: 12),
            ),
          ),
        ),
        for (var i = 2; i < 12; i++) const DataCell(SizedBox.shrink()),
      ],
    );
  }

  DataRow _buildVisitRow(
    BuildContext context,
    TreatmentPlanSummaryDto plan,
    TreatmentPlanVisitDto visit,
    AppLocalizations l10n,
  ) {
    final services = visit.services.join(', ');
    final canOpen = visit.status.toUpperCase() != 'CANCELLED';
    return DataRow(
      color: WidgetStateProperty.all(Colors.grey.shade50),
      onSelectChanged: canOpen ? (_) => _openVisitAppointment(context, plan, visit) : null,
      cells: [
        const DataCell(SizedBox.shrink()),
        DataCell(
          InkWell(
            onTap: canOpen ? () => _openVisitAppointment(context, plan, visit) : null,
            child: Padding(
              padding: const EdgeInsets.only(left: 12, top: 4, bottom: 4),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Text(
                          '${l10n.translate('clinicTreatmentPlansVisitLabel')} #${visit.appointmentId}',
                          style: TextStyle(
                            fontWeight: FontWeight.w600,
                            fontSize: 12,
                            color: canOpen ? Colors.blue.shade800 : Colors.grey.shade700,
                            decoration:
                                canOpen ? TextDecoration.underline : TextDecoration.none,
                          ),
                        ),
                      ),
                      _pill(
                        _timingLabel(context, visit.timing),
                        _timingColor(visit.timing),
                      ),
                    ],
                  ),
                  if (services.isNotEmpty)
                    Text(
                      services,
                      maxLines: 2,
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(
                        color: Colors.grey.shade700,
                        fontSize: 11,
                      ),
                    ),
                ],
              ),
            ),
          ),
        ),
        DataCell(Text(_shortDate(visit.startAt))),
        const DataCell(SizedBox.shrink()),
        DataCell(Text(visit.doctorName)),
        DataCell(Text(_money(visit.visitTotalMinor, visit.currency))),
        DataCell(Text(
          _money(visit.visitCollectedMinor, visit.currency),
          style: TextStyle(color: Colors.green.shade700),
        )),
        DataCell(Text(
          _money(visit.visitOwedMinor, visit.currency),
          style: TextStyle(
            color: visit.visitOwedMinor > 0
                ? Colors.red.shade700
                : Colors.grey.shade600,
            fontWeight: FontWeight.w600,
          ),
        )),
        DataCell(_pill(
          _appointmentStatusLabel(context, visit.status),
          _appointmentStatusColor(visit.status, Theme.of(context).colorScheme),
        )),
        DataCell(_pill(
          l10n.clinicPaymentStatusLabel(visit.visitPaymentStatus),
          _paymentColor(visit.visitPaymentStatus),
        )),
        DataCell(Text(_shortDate(visit.startAt))),
        DataCell(
          canOpen
              ? IconButton(
                  tooltip: l10n.translate('clinicTreatmentPlansOpenVisit'),
                  icon: const Icon(Icons.open_in_new, size: 18),
                  onPressed: () => _openVisitAppointment(context, plan, visit),
                )
              : const SizedBox.shrink(),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final listFilter = ClinicPlansFilter(
      clinicId: widget.clinicId,
      planKind: 'COMPREHENSIVE',
    );
    final plansAsync = ref.watch(treatmentPlansForClinicProvider(listFilter));

    for (final planId in _expandedPlanIds) {
      ref.listen(treatmentPlanVisitsProvider(planId), (_, next) {
        if (!next.isLoading && mounted) setState(() {});
      });
    }

    final toolbar = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClinicTableToolbar(
          search: ClinicTableSearchField(
            controller: _filterCtrl,
            hint: l10n.translate('clinicTreatmentPlansFilterHint'),
            onChanged: _onSearchChanged,
          ),
          actions: [
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
            (
              value: 'ACTIVE',
              label: l10n.clinicPlanStatusLabel('ACTIVE'),
            ),
            (
              value: 'DRAFT',
              label: l10n.clinicPlanStatusLabel('DRAFT'),
            ),
            (
              value: 'COMPLETED',
              label: l10n.clinicPlanStatusLabel('COMPLETED'),
            ),
            (
              value: 'CANCELLED',
              label: l10n.clinicPlanStatusLabel('CANCELLED'),
            ),
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
        final filtered = _applyClientFilters(plans);
        if (filtered.isEmpty) {
          return ClinicTableEmpty(
            l10n.translate('clinicTreatmentPlansEmpty'),
          );
        }
        final sorted = _sort(filtered);
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
              label: Text(l10n.translate('clinicTreatmentPlansVisits')),
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
          rows: _buildTableRows(context, sorted, scheme, l10n),
        );
      },
    );

    return ClinicTableShell(toolbar: toolbar, body: body);
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

