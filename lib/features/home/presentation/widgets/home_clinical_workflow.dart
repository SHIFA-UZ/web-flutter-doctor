import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_export_service.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_quick_action_navigation.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/appointment_timeline_tile.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_search_overlay.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/next_patient_card.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/overview_metric_card.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/quick_action_chip.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_date_range_provider.dart';

/// Mobile-first clinical workflow stack for the doctor Home screen.
class HomeClinicalWorkflow extends ConsumerStatefulWidget {
  const HomeClinicalWorkflow({
    super.key,
    required this.selectedAppointmentId,
    required this.onAppointmentSelected,
  });

  final String? selectedAppointmentId;
  final ValueChanged<Appointment> onAppointmentSelected;

  @override
  ConsumerState<HomeClinicalWorkflow> createState() =>
      _HomeClinicalWorkflowState();
}

class _HomeClinicalWorkflowState extends ConsumerState<HomeClinicalWorkflow> {
  bool _exporting = false;
  final _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _export() async {
    if (_exporting) return;
    setState(() => _exporting = true);
    final l10n = AppLocalizations.of(context)!;
    try {
      await ref.read(homeDashboardExportServiceProvider).exportCsv();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(l10n.translate('exportStarted'))),
        );
      }
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.translate('exportFailed')),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) setState(() => _exporting = false);
    }
  }

  Future<void> _pickRange() async {
    final l10n = AppLocalizations.of(context)!;
    final current = ref.read(homeDashboardDateRangeProvider);
    final picked = await showDateRangePicker(
      context: context,
      firstDate: DateTime.now().subtract(const Duration(days: 365)),
      lastDate: DateTime.now().add(const Duration(days: 30)),
      initialDateRange: DateTimeRange(start: current.start, end: current.end),
      helpText: l10n.translate('selectDateRange'),
    );
    if (picked != null) {
      ref
          .read(homeDashboardDateRangeProvider.notifier)
          .setRange(picked.start, picked.end);
    }
  }

  Future<void> _startAppointment(Appointment appt) async {
    if (appt.patientId != null) {
      try {
        final patientAsync = ref.read(patientByIdProvider(appt.patientId!));
        final patient = await patientAsync.when(
          data: (p) => Future.value(p),
          loading: () => Future.value(null),
          error: (_, __) => Future.value(null),
        );
        if (patient != null &&
            patient.general.chronicDisease != null &&
            patient.general.chronicDisease!.isNotEmpty &&
            patient.general.chronicDisease != 'None' &&
            mounted) {
          await showChronicDiseaseWarning(
            context,
            patient.name,
            patient.general.chronicDisease!,
          );
        }
      } catch (_) {}
    }
    if (!mounted) return;
    ShellScope.pushNamed(
      context,
      appt.isVideo ? AppRoutes.videoCall : AppRoutes.inPerson,
      arguments: appt,
    );
  }

  void _openPatientCard(Appointment appt) {
    final patientId = appt.patientId?.toString();
    widget.onAppointmentSelected(appt);
    ref.read(shellProvider.notifier).setTab(DoctorShellTab.patients);
    if (patientId == null || patientId.trim().isEmpty) return;
    // Same path as calendar "open patient profile": push PatientsScreen with
    // initialSelectedId so mobile opens the detail view directly.
    ShellScope.pushIntoShell(patientId);
  }

  void _submitSearch(String query) {
    final q = query.trim();
    if (q.isEmpty) {
      HomeSearchOverlay.show(context);
      return;
    }
    ref.read(shellProvider.notifier).setTab(DoctorShellTab.patients);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final appointmentsAsync = ref.watch(todayAppointmentsProvider);
    final unread =
        ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;
    final followUps = ref.watch(tasksProvider).where(
          (t) =>
              t.status == TaskStatus.active || t.status == TaskStatus.draft,
        );
    final range = ref.watch(homeDashboardDateRangeProvider);
    final locale = ref.watch(languageProvider).locale.toString();
    final rangeLabel = formatDashboardDateRange(range, locale);

    final appointments = appointmentsAsync.valueOrNull ?? const <Appointment>[];
    final valid = appointments
        .where((a) => a.id.isNotEmpty && a.patientName.isNotEmpty)
        .toList();
    final doctorTimeZone =
        ref.watch(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
    final now = getNowInTimezone(doctorTimeZone);
    final classified = _classify(valid, now, doctorTimeZone);
    final hero = classified.current ?? classified.nextUp;
    final listItems = valid.where((a) => !a.isCompleted).toList();
    final nextTimeLabel = hero?.start.format(context) ?? '—';
    final nextMinutes = _minutesUntilNext(valid, now, doctorTimeZone);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        // Date chip + export icon
        Row(
          children: [
            Expanded(
              child: Material(
                color: Colors.transparent,
                child: InkWell(
                  onTap: _pickRange,
                  borderRadius: BorderRadius.circular(24),
                  child: Ink(
                    height: 40,
                    padding: const EdgeInsets.symmetric(horizontal: 12),
                    decoration: BoxDecoration(
                      color: AppDesignSystem.background,
                      borderRadius: BorderRadius.circular(24),
                      border: Border.all(color: AppDesignSystem.border),
                    ),
                    child: Row(
                      children: [
                        Icon(Icons.calendar_month_outlined,
                            size: 18, color: brand),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            rangeLabel,
                            style: AppDesignSystem.body2(context).copyWith(
                              color: AppDesignSystem.textPrimary,
                              fontWeight: FontWeight.w500,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            const SizedBox(width: 8),
            SizedBox(
              width: 44,
              height: 44,
              child: IconButton(
                onPressed: _exporting ? null : _export,
                tooltip: l10n.translate('export'),
                style: IconButton.styleFrom(
                  backgroundColor: brand.withValues(alpha: 0.1),
                  foregroundColor: brand,
                  minimumSize: const Size(44, 44),
                ),
                icon: _exporting
                    ? SizedBox(
                        width: 18,
                        height: 18,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: brand,
                        ),
                      )
                    : const Icon(Icons.download_outlined, size: 20),
              ),
            ),
          ],
        ),
        const SizedBox(height: AppDesignSystem.itemGap),

        // 2x2 metrics
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 10,
          crossAxisSpacing: 10,
          childAspectRatio: 1.45,
          children: [
            OverviewMetricCard(
              icon: Icons.event_available_outlined,
              label: l10n.translate('metricToday'),
              value: '${valid.length}',
              secondary: l10n.translate('appointmentsTodayShort'),
              onTap: () =>
                  ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar),
            ),
            OverviewMetricCard(
              icon: Icons.description_outlined,
              label: l10n.translate('metricReports'),
              value: '$unread',
              secondary: l10n.translate('pendingReports'),
              onTap: () => ref
                  .read(shellProvider.notifier)
                  .setTab(DoctorShellTab.notifications),
            ),
            OverviewMetricCard(
              icon: Icons.task_alt_outlined,
              label: l10n.translate('metricFollowUp'),
              value: '${followUps.length}',
              secondary: l10n.translate('followUpTasks'),
              onTap: () =>
                  ref.read(shellProvider.notifier).setTab(DoctorShellTab.tasks),
            ),
            OverviewMetricCard(
              icon: Icons.schedule,
              label: l10n.translate('metricNext'),
              value: nextTimeLabel,
              secondary: nextMinutes == null
                  ? l10n.translate('noNextPatient')
                  : l10n
                      .translate('nextAppointmentInMinutes')
                      .replaceAll('{minutes}', '$nextMinutes'),
            ),
          ],
        ),
        const SizedBox(height: AppDesignSystem.sectionGap),

        // Next patient hero
        if (appointmentsAsync.isLoading)
          const DashboardSkeleton(height: 180, lines: 4)
        else if (hero != null)
          _NextPatientSlot(
            appointment: hero,
            isNow: classified.current?.id == hero.id,
            doctorTimeZone: doctorTimeZone,
            now: now,
            onStart: () => _startAppointment(hero),
            onOpenChart: () => _openPatientCard(hero),
          )
        else
          _EmptyNextPatient(l10n: l10n),

        const SizedBox(height: AppDesignSystem.sectionGap),

        // Quick actions (replaces FAB)
        Text(
          l10n.translate('quickActions'),
          style: AppDesignSystem.h2(context),
        ),
        const SizedBox(height: 10),
        SizedBox(
          height: 44,
          child: ListView(
            scrollDirection: Axis.horizontal,
            children: [
              QuickActionChip(
                icon: Icons.add_circle_outline,
                label: l10n.translate('newAppointment'),
                onTap: () => openCalendarForNewAppointment(ref),
              ),
              const SizedBox(width: 8),
              QuickActionChip(
                icon: Icons.videocam_outlined,
                label: l10n.videoCall,
                onTap: () => openCalendarForVideoConsultation(ref),
              ),
              const SizedBox(width: 8),
              QuickActionChip(
                icon: Icons.medication_outlined,
                label: l10n.translate('issuePrescription'),
                onTap: () => openCreateRemoteCareTaskForm(context, ref),
              ),
              const SizedBox(width: 8),
              QuickActionChip(
                icon: Icons.person_search_outlined,
                label: l10n.translate('searchPatients'),
                onTap: () => HomeSearchOverlay.show(context),
              ),
            ],
          ),
        ),
        const SizedBox(height: AppDesignSystem.sectionGap),

        // Persistent search
        TextField(
          controller: _searchController,
          textInputAction: TextInputAction.search,
          onSubmitted: _submitSearch,
          onTap: () {
            // Keep field editable; also allow quick open of overlay via icon.
          },
          decoration: InputDecoration(
            hintText: l10n.translate('searchPatientsHint'),
            prefixIcon: const Icon(Icons.search, size: 20),
            suffixIcon: IconButton(
              tooltip: l10n.translate('scanMultiPage'),
              onPressed: () =>
                  ref.read(shellProvider.notifier).setTab(DoctorShellTab.patients),
              icon: const Icon(Icons.document_scanner_outlined, size: 20),
            ),
            filled: true,
            fillColor: AppDesignSystem.background,
            contentPadding:
                const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: AppDesignSystem.border),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: const BorderSide(color: AppDesignSystem.border),
            ),
            focusedBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(14),
              borderSide: BorderSide(color: brand, width: 1.5),
            ),
          ),
        ),
        const SizedBox(height: AppDesignSystem.sectionGap),

        // Schedule list
        Text(
          l10n.translate('todaysSchedule'),
          style: AppDesignSystem.h2(context),
        ),
        const SizedBox(height: 4),
        Text(
          l10n.translate('todayTimelineSubtitle'),
          style: AppDesignSystem.body2(context),
        ),
        const SizedBox(height: 10),
        if (appointmentsAsync.hasError)
          Text('${l10n.error}: ${appointmentsAsync.error}')
        else if (listItems.isEmpty)
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 24),
            child: Text(
              l10n.translate('noAppointmentsToday'),
              style: AppDesignSystem.body2(context),
              textAlign: TextAlign.center,
            ),
          )
        else
          Column(
            children: [
              for (var i = 0; i < listItems.length; i++)
                AppointmentTimelineTile(
                  appointment: listItems[i],
                  selected:
                      widget.selectedAppointmentId == listItems[i].id,
                  isLast: i == listItems.length - 1,
                  onTap: () => widget.onAppointmentSelected(listItems[i]),
                ),
            ],
          ),
      ],
    );
  }

  ({Appointment? current, Appointment? nextUp}) _classify(
    List<Appointment> valid,
    DateTime now,
    String? doctorTimeZone,
  ) {
    Appointment? current;
    Appointment? nextUp;
    for (final appt in valid) {
      if (appt.isCompleted) continue;
      final start = timeOfDayToDateTimeInZone(appt.start, now, doctorTimeZone);
      final end = timeOfDayToDateTimeInZone(appt.end, now, doctorTimeZone);
      if (!now.isBefore(start) && now.isBefore(end)) {
        current = appt;
        break;
      }
    }
    if (current == null) {
      for (final appt in valid) {
        if (appt.isCompleted) continue;
        final start =
            timeOfDayToDateTimeInZone(appt.start, now, doctorTimeZone);
        if (!start.isBefore(now)) {
          nextUp = appt;
          break;
        }
      }
    }
    return (current: current, nextUp: nextUp);
  }

  int? _minutesUntilNext(
    List<Appointment> appointments,
    DateTime now,
    String? doctorTimeZone,
  ) {
    int? best;
    for (final appt in appointments) {
      if (appt.isCompleted) continue;
      final start = timeOfDayToDateTimeInZone(appt.start, now, doctorTimeZone);
      final diff = start.difference(now).inMinutes;
      if (diff >= 0 && (best == null || diff < best)) best = diff;
    }
    return best;
  }
}

class _NextPatientSlot extends ConsumerWidget {
  const _NextPatientSlot({
    required this.appointment,
    required this.isNow,
    required this.doctorTimeZone,
    required this.now,
    required this.onStart,
    required this.onOpenChart,
  });

  final Appointment appointment;
  final bool isNow;
  final String? doctorTimeZone;
  final DateTime now;
  final VoidCallback onStart;
  final VoidCallback onOpenChart;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final patient = appointment.patientId != null
        ? ref.watch(patientByIdProvider(appointment.patientId!)).valueOrNull
        : null;
    final start = timeOfDayToDateTimeInZone(
      appointment.start,
      now,
      doctorTimeZone,
    );
    final startEnabled =
        !start.isAfter(now.add(const Duration(minutes: 15))) || isNow;

    return NextPatientCard(
      appointment: appointment,
      patient: patient,
      isNow: isNow,
      startEnabled: startEnabled,
      onStart: onStart,
      onOpenChart: onOpenChart,
    );
  }
}

class _EmptyNextPatient extends StatelessWidget {
  const _EmptyNextPatient({required this.l10n});

  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: AppDesignSystem.cardDecoration(),
      child: Column(
        children: [
          Icon(Icons.event_available_outlined,
              size: 36, color: AppDesignSystem.textTertiary),
          const SizedBox(height: 8),
          Text(
            l10n.translate('noNextPatient'),
            style: AppDesignSystem.body1(context),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }
}
