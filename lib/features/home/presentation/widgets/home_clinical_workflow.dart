import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_quick_action_navigation.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/appointment_timeline_tile.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
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

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final appointmentsAsync = ref.watch(todayAppointmentsProvider);
    final upcomingAsync = ref.watch(nextUpcomingAppointmentProvider);
    final unread =
        ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;
    final followUps = ref.watch(tasksProvider).where(
          (t) =>
              t.status == TaskStatus.active || t.status == TaskStatus.draft,
        );

    final appointments = appointmentsAsync.valueOrNull ?? const <Appointment>[];
    final valid = appointments
        .where((a) => a.id.isNotEmpty && a.patientName.isNotEmpty)
        .toList();
    final doctorTimeZone =
        ref.watch(profileAllProvider).valueOrNull?.profile['timeZone']
            as String?;
    final now = getNowInTimezone(doctorTimeZone);
    final classified = _classify(valid, now, doctorTimeZone);
    final hero = classified.current ??
        classified.nextUp ??
        upcomingAsync.valueOrNull;
    final listItems = valid.where((a) => !a.isCompleted).toList();
    final followUpCount = followUps.length;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: OverviewMetricCard(
                icon: Icons.event_available_outlined,
                label: l10n.translate('metricToday'),
                value: '${valid.length}',
                secondary: l10n.visitsCountNoun(valid.length),
                onTap: () => ref
                    .read(shellProvider.notifier)
                    .setTab(DoctorShellTab.calendar),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: OverviewMetricCard(
                icon: Icons.notifications_outlined,
                label: l10n.notifications,
                value: '$unread',
                secondary: l10n.translate('unreadCountLabel'),
                onTap: () => ref
                    .read(shellProvider.notifier)
                    .setTab(DoctorShellTab.notifications),
              ),
            ),
            const SizedBox(width: 10),
            Expanded(
              child: OverviewMetricCard(
                icon: Icons.task_alt_outlined,
                label: l10n.translate('metricFollowUp'),
                value: '$followUpCount',
                secondary: followUpCount == 1
                    ? l10n.translate('followUpTask')
                    : l10n.translate('followUpTasks'),
                onTap: () =>
                    ref.read(shellProvider.notifier).setTab(DoctorShellTab.tasks),
              ),
            ),
          ],
        ),
        const SizedBox(height: AppDesignSystem.sectionGap),

        if (appointmentsAsync.isLoading || upcomingAsync.isLoading)
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
          _EmptyNextPatient(
            l10n: l10n,
            onNewAppointment: () => openCalendarForNewAppointment(ref),
          ),

        const SizedBox(height: AppDesignSystem.sectionGap),

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
            ],
          ),
        ),

        if (appointmentsAsync.hasError) ...[
          const SizedBox(height: AppDesignSystem.sectionGap),
          Text('${l10n.error}: ${appointmentsAsync.error}'),
        ] else if (listItems.isNotEmpty) ...[
          const SizedBox(height: AppDesignSystem.sectionGap),
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
          Column(
            children: [
              for (var i = 0; i < listItems.length; i++)
                AppointmentTimelineTile(
                  appointment: listItems[i],
                  selected: widget.selectedAppointmentId == listItems[i].id,
                  isLast: i == listItems.length - 1,
                  onTap: () => widget.onAppointmentSelected(listItems[i]),
                ),
            ],
          ),
        ],
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
      final day = appt.day ?? DateTime(now.year, now.month, now.day);
      final start = timeOfDayToDateTimeInZone(appt.start, day, doctorTimeZone);
      final end = timeOfDayToDateTimeInZone(appt.end, day, doctorTimeZone);
      if (!now.isBefore(start) && now.isBefore(end)) {
        current = appt;
        break;
      }
    }
    if (current == null) {
      for (final appt in valid) {
        if (appt.isCompleted) continue;
        final day = appt.day ?? DateTime(now.year, now.month, now.day);
        final start =
            timeOfDayToDateTimeInZone(appt.start, day, doctorTimeZone);
        if (!start.isBefore(now)) {
          nextUp = appt;
          break;
        }
      }
    }
    return (current: current, nextUp: nextUp);
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
    final startDay = appointment.day ?? DateTime(now.year, now.month, now.day);
    final start = timeOfDayToDateTimeInZone(
      appointment.start,
      startDay,
      doctorTimeZone,
    );
    final startEnabled =
        !start.isAfter(now.add(const Duration(minutes: 15))) || isNow;

    final today = DateTime(now.year, now.month, now.day);
    final apptDay = DateTime(startDay.year, startDay.month, startDay.day);
    final dateLabel = apptDay == today
        ? null
        : MaterialLocalizations.of(context).formatMediumDate(apptDay);

    return NextPatientCard(
      appointment: appointment,
      patient: patient,
      isNow: isNow,
      startEnabled: startEnabled,
      onStart: onStart,
      onOpenChart: onOpenChart,
      dateLabel: dateLabel,
    );
  }
}

class _EmptyNextPatient extends StatelessWidget {
  const _EmptyNextPatient({
    required this.l10n,
    required this.onNewAppointment,
  });

  final AppLocalizations l10n;
  final VoidCallback onNewAppointment;

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
            l10n.translate('noAppointmentsToday'),
            style: AppDesignSystem.body1(context),
            textAlign: TextAlign.center,
          ),
          const SizedBox(height: 16),
          ShifaPrimaryButton(
            label: l10n.translate('newAppointment'),
            icon: Icons.add_circle_outline,
            onPressed: onNewAppointment,
          ),
        ],
      ),
    );
  }
}
