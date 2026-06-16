import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/layout/shifa_scroll_behavior.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_appointment_card.dart';
import 'package:shifa_doc_app_v1/features/chat/application/open_chat_with_patient.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_current_appointment_hero.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

typedef AppointmentSelectedCallback = void Function(Appointment appt);

class HomeTimelineSection extends ConsumerWidget {
  const HomeTimelineSection({
    super.key,
    required this.selectedAppointmentId,
    required this.onAppointmentSelected,
  });

  final String? selectedAppointmentId;
  final AppointmentSelectedCallback onAppointmentSelected;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final appointmentsAsync = ref.watch(todayAppointmentsProvider);

    return DashboardCard(
      title: l10n.today,
      subtitle: l10n.translate('todayTimelineSubtitle'),
      child: appointmentsAsync.when(
        loading: () => const DashboardSkeleton(height: 320, lines: 5),
        error: (e, _) => Text('${l10n.error}: $e'),
        data: (appointments) {
          final valid = appointments
              .where((a) => a.id.isNotEmpty && a.patientName.isNotEmpty)
              .toList();
          if (valid.isEmpty) return _EmptyTimeline(l10n: l10n);

          final doctorTimeZone =
              ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
                  as String?;
          final now = getNowInTimezone(doctorTimeZone);

          final classified = _classifyAppointments(valid, now, doctorTimeZone);
          final hero = classified.current ?? classified.nextUp;
          final upcoming = valid
              .where((a) => hero == null || a.id != hero.id)
              .where((a) => !a.isCompleted)
              .toList();
          final completed = valid.where((a) => a.isCompleted).toList();

          return Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (hero != null) ...[
                _HeroSlot(
                  ref: ref,
                  appointment: hero,
                  isNow: classified.current?.id == hero.id,
                  doctorTimeZone: doctorTimeZone,
                  now: now,
                  onStart: () => _startAppointment(context, ref, hero),
                  onOpenChart: hero.patientId != null
                      ? () => ref.read(shellProvider.notifier).setTab(3)
                      : null,
                  onOpenDocuments: hero.patientId != null
                      ? () => ref.read(shellProvider.notifier).setTab(3)
                      : null,
                  onMessagePatient: hero.patientId != null
                      ? () => openChatWithPatient(ref, hero.patientId!)
                      : () => ref.read(shellProvider.notifier).setTab(DoctorShellTab.chat),
                ),
                const SizedBox(height: 20),
              ],
              if (upcoming.isNotEmpty) ...[
                Text(
                  l10n.translate('upcomingAppointments'),
                  style: AppDesignSystem.h2(context),
                ),
                const SizedBox(height: 12),
                _UpcomingAppointmentsStrip(
                  itemCount: upcoming.length,
                  itemBuilder: (context, i) {
                    final appt = upcoming[i];
                    return _UpcomingSlot(
                      ref: ref,
                      appointment: appt,
                      selected: selectedAppointmentId == appt.id,
                      doctorTimeZone: doctorTimeZone,
                      now: now,
                      onTap: () => onAppointmentSelected(appt),
                      onStart: () => _startAppointment(context, ref, appt),
                    );
                  },
                ),
              ],
              if (completed.isNotEmpty) ...[
                const SizedBox(height: 20),
                ...completed.map(
                  (appt) => Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: _CompletedRow(
                      ref: ref,
                      appointment: appt,
                      doctorTimeZone: doctorTimeZone,
                      now: now,
                      onTap: () => onAppointmentSelected(appt),
                    ),
                  ),
                ),
              ],
              const SizedBox(height: 8),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: () => ref.read(shellProvider.notifier).setTab(2),
                  icon: const Icon(Icons.add, size: 18),
                  label: Text(l10n.translate('newAppointmentBtn')),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  Future<void> _startAppointment(
    BuildContext context,
    WidgetRef ref,
    Appointment appt,
  ) async {
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
            context.mounted) {
          await showChronicDiseaseWarning(
            context,
            patient.name,
            patient.general.chronicDisease!,
          );
        }
      } catch (_) {}
    }
    if (context.mounted) {
      ShellScope.pushNamed(
        context,
        appt.isVideo ? AppRoutes.videoCall : AppRoutes.inPerson,
        arguments: appt,
      );
    }
  }

  ({Appointment? current, Appointment? nextUp}) _classifyAppointments(
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
        final start = timeOfDayToDateTimeInZone(appt.start, now, doctorTimeZone);
        if (!start.isBefore(now)) {
          nextUp = appt;
          break;
        }
      }
    }
    return (current: current, nextUp: nextUp);
  }
}

class _HeroSlot extends ConsumerWidget {
  const _HeroSlot({
    required this.ref,
    required this.appointment,
    required this.isNow,
    required this.doctorTimeZone,
    required this.now,
    required this.onStart,
    this.onOpenChart,
    this.onOpenDocuments,
    this.onMessagePatient,
  });

  final WidgetRef ref;
  final Appointment appointment;
  final bool isNow;
  final String? doctorTimeZone;
  final DateTime now;
  final VoidCallback onStart;
  final VoidCallback? onOpenChart;
  final VoidCallback? onOpenDocuments;
  final VoidCallback? onMessagePatient;

  @override
  Widget build(BuildContext context, WidgetRef _) {
    final patient = appointment.patientId != null
        ? ref.watch(patientByIdProvider(appointment.patientId!)).valueOrNull
        : null;
    final timing = _appointmentTiming(context, appointment, now, doctorTimeZone);

    return HomeCurrentAppointmentHero(
      appointment: appointment,
      patient: patient,
      isNow: isNow,
      startButtonEnabled: timing.startEnabled,
      videoTooltip: timing.videoTooltip,
      durationMinutes: timing.durationMinutes,
      onStart: onStart,
      onOpenChart: onOpenChart,
      onOpenDocuments: onOpenDocuments,
      onMessagePatient: onMessagePatient,
    );
  }
}

class _UpcomingSlot extends ConsumerWidget {
  const _UpcomingSlot({
    required this.ref,
    required this.appointment,
    required this.selected,
    required this.doctorTimeZone,
    required this.now,
    required this.onTap,
    required this.onStart,
  });

  final WidgetRef ref;
  final Appointment appointment;
  final bool selected;
  final String? doctorTimeZone;
  final DateTime now;
  final VoidCallback onTap;
  final VoidCallback onStart;

  @override
  Widget build(BuildContext context, WidgetRef _) {
    final patient = appointment.patientId != null
        ? ref.watch(patientByIdProvider(appointment.patientId!)).valueOrNull
        : null;
    final timing = _appointmentTiming(context, appointment, now, doctorTimeZone);

    return HomeUpcomingTimelineCard(
      appointment: appointment,
      patient: patient,
      selected: selected,
      startButtonEnabled: timing.startEnabled,
      videoTooltip: timing.videoTooltip,
      onTap: onTap,
      onStart: onStart,
    );
  }
}

class _CompletedRow extends ConsumerWidget {
  const _CompletedRow({
    required this.ref,
    required this.appointment,
    required this.doctorTimeZone,
    required this.now,
    required this.onTap,
  });

  final WidgetRef ref;
  final Appointment appointment;
  final String? doctorTimeZone;
  final DateTime now;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context, WidgetRef _) {
    final patient = appointment.patientId != null
        ? ref.watch(patientByIdProvider(appointment.patientId!)).valueOrNull
        : null;
    final timing = _appointmentTiming(context, appointment, now, doctorTimeZone);

    return HomeAppointmentCard(
      appointment: appointment,
      selected: false,
      isCurrent: false,
      isNext: false,
      startButtonEnabled: timing.startEnabled,
      videoTooltip: timing.videoTooltip,
      patient: patient,
      onTap: onTap,
      onStart: () {},
      compact: true,
    );
  }
}

({bool startEnabled, String videoTooltip, int durationMinutes}) _appointmentTiming(
  BuildContext context,
  Appointment appointment,
  DateTime now,
  String? doctorTimeZone,
) {
  final l10n = AppLocalizations.of(context)!;
  final appointmentDateTime =
      timeOfDayToDateTimeInZone(appointment.start, now, doctorTimeZone);
  final appointmentEndDateTime =
      timeOfDayToDateTimeInZone(appointment.end, now, doctorTimeZone);
  final durationMinutes = appointmentEndDateTime
      .difference(appointmentDateTime)
      .inMinutes
      .clamp(1, 480);
  final videoColdJoinCutoff =
      appointmentEndDateTime.add(const Duration(hours: 1));
  final isVideoPastColdJoinGrace = appointment.isVideo &&
      !appointment.isInProgress &&
      !now.isBefore(videoColdJoinCutoff);
  final joinAllowedFrom =
      appointmentDateTime.subtract(const Duration(minutes: 5));
  final canStartVideo = !appointment.isVideo ||
      ((now.isAfter(joinAllowedFrom) ||
              now.isAtSameMomentAs(joinAllowedFrom)) &&
          !isVideoPastColdJoinGrace);
  final videoTooltip = appointment.isVideo && !canStartVideo
      ? (isVideoPastColdJoinGrace
          ? l10n.videoCallTooLateAfterOneHour
          : l10n.translate('videoCallAvailableFiveMinBefore'))
      : '';
  return (
    startEnabled: !appointment.isCompleted && canStartVideo,
    videoTooltip: videoTooltip,
    durationMinutes: durationMinutes,
  );
}

class _EmptyTimeline extends StatelessWidget {
  const _EmptyTimeline({required this.l10n});

  final AppLocalizations l10n;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 32),
      child: Column(
        children: [
          Icon(Icons.event_available_outlined,
              size: 48, color: Colors.grey.shade400),
          const SizedBox(height: 12),
          Text(
            l10n.noAppointmentsToday,
            style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 16),
          ),
          const SizedBox(height: 4),
          Text(
            l10n.scheduleIsClear,
            style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
          ),
        ],
      ),
    );
  }
}

/// Horizontal upcoming strip with mouse drag, scrollbar, and chevron navigation.
class _UpcomingAppointmentsStrip extends StatefulWidget {
  const _UpcomingAppointmentsStrip({
    required this.itemCount,
    required this.itemBuilder,
  });

  final int itemCount;
  final Widget Function(BuildContext context, int index) itemBuilder;

  static const _cardWidth = 280.0;
  static const _separatorWidth = 12.0;
  static const _scrollStep = _cardWidth + _separatorWidth;
  static const _height = 168.0;

  @override
  State<_UpcomingAppointmentsStrip> createState() =>
      _UpcomingAppointmentsStripState();
}

class _UpcomingAppointmentsStripState extends State<_UpcomingAppointmentsStrip> {
  late final ScrollController _controller;
  bool _canScrollLeft = false;
  bool _canScrollRight = false;

  @override
  void initState() {
    super.initState();
    _controller = ScrollController()..addListener(_syncScrollButtons);
    WidgetsBinding.instance.addPostFrameCallback((_) => _syncScrollButtons());
  }

  @override
  void didUpdateWidget(covariant _UpcomingAppointmentsStrip oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.itemCount != widget.itemCount) {
      WidgetsBinding.instance.addPostFrameCallback((_) => _syncScrollButtons());
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  void _syncScrollButtons() {
    if (!mounted || !_controller.hasClients) return;
    final offset = _controller.offset;
    final maxExtent = _controller.position.maxScrollExtent;
    final canLeft = offset > 4;
    final canRight = offset < maxExtent - 4;
    if (canLeft != _canScrollLeft || canRight != _canScrollRight) {
      setState(() {
        _canScrollLeft = canLeft;
        _canScrollRight = canRight;
      });
    }
  }

  void _scrollBy(double delta) {
    if (!_controller.hasClients) return;
    final target = (_controller.offset + delta)
        .clamp(0.0, _controller.position.maxScrollExtent);
    _controller.animateTo(
      target,
      duration: const Duration(milliseconds: 280),
      curve: Curves.easeOut,
    );
  }

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;

    return SizedBox(
      height: _UpcomingAppointmentsStrip._height,
      child: ScrollConfiguration(
        behavior: const ShifaScrollBehavior(),
        child: Stack(
          clipBehavior: Clip.none,
          children: [
            Scrollbar(
              controller: _controller,
              thumbVisibility: true,
              notificationPredicate: (notification) =>
                  notification.metrics.axis == Axis.horizontal,
              child: ListView.separated(
                controller: _controller,
                scrollDirection: Axis.horizontal,
                primary: false,
                itemCount: widget.itemCount,
                separatorBuilder: (_, __) =>
                    const SizedBox(width: _UpcomingAppointmentsStrip._separatorWidth),
                itemBuilder: widget.itemBuilder,
              ),
            ),
            if (_canScrollLeft)
              _UpcomingScrollArrow(
                alignment: Alignment.centerLeft,
                icon: Icons.chevron_left,
                brand: brand,
                onPressed: () =>
                    _scrollBy(-_UpcomingAppointmentsStrip._scrollStep),
              ),
            if (_canScrollRight)
              _UpcomingScrollArrow(
                alignment: Alignment.centerRight,
                icon: Icons.chevron_right,
                brand: brand,
                onPressed: () =>
                    _scrollBy(_UpcomingAppointmentsStrip._scrollStep),
              ),
          ],
        ),
      ),
    );
  }
}

class _UpcomingScrollArrow extends StatelessWidget {
  const _UpcomingScrollArrow({
    required this.alignment,
    required this.icon,
    required this.brand,
    required this.onPressed,
  });

  final Alignment alignment;
  final IconData icon;
  final Color brand;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: alignment,
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 2),
        child: Material(
          elevation: 2,
          color: Colors.white,
          shape: const CircleBorder(),
          clipBehavior: Clip.antiAlias,
          child: InkWell(
            onTap: onPressed,
            customBorder: const CircleBorder(),
            child: SizedBox(
              width: 32,
              height: 32,
              child: Icon(icon, size: 22, color: brand),
            ),
          ),
        ),
      ),
    );
  }
}
