import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_export_service.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_dashboard_toolbar.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/features/tasks/domain/task_models.dart';
import 'package:shifa_doc_app_v1/state/tasks/tasks_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/core/utils/doctor_display_name.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';

class HomeGreetingHeader extends ConsumerStatefulWidget {
  const HomeGreetingHeader({
    super.key,
    this.onSearchTap,
    this.onNotificationsTap,
  });

  final VoidCallback? onSearchTap;
  final VoidCallback? onNotificationsTap;

  @override
  ConsumerState<HomeGreetingHeader> createState() => _HomeGreetingHeaderState();
}

class _HomeGreetingHeaderState extends ConsumerState<HomeGreetingHeader> {
  bool _exporting = false;

  String _greeting(AppLocalizations l10n) {
    final hour = DateTime.now().hour;
    if (hour < 12) return l10n.translate('goodMorning');
    if (hour < 17) return l10n.translate('goodAfternoon');
    return l10n.translate('goodEvening');
  }

  String _doctorName(WidgetRef ref) {
    final profile = ref.watch(profileAllProvider).valueOrNull?.profile;
    final first = profile?['firstName'] as String? ?? '';
    final last = profile?['lastName'] as String? ?? '';
    return formatDoctorMedName(firstName: first, lastName: last);
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

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;
    final appointmentsAsync = ref.watch(todayAppointmentsProvider);
    final unreadNotif =
        ref.watch(doctorNotificationsUnreadCountProvider).valueOrNull ?? 0;
    final pendingTasks = ref.watch(tasksProvider).where((t) =>
        t.status == TaskStatus.active || t.status == TaskStatus.draft);

    final appointmentCount = appointmentsAsync.valueOrNull?.length ?? 0;
    final pendingReports = unreadNotif;
    final followUpCount = pendingTasks.length;
    final nextMinutes = _minutesUntilNext(ref, appointmentsAsync.valueOrNull);

    final compactToolbar = PlatformLayout.useCompactToolbar(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(l10n.home, style: AppDesignSystem.display(context)),
                  const SizedBox(height: 4),
                  Text(
                    l10n.translate('dashboardSubtitle'),
                    style: AppDesignSystem.body2(context),
                  ),
                ],
              ),
            ),
            if (!compactToolbar)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  HomeDashboardToolbar(
                    exporting: _exporting,
                    onExport: _export,
                  ),
                  const SizedBox(width: 8),
                  _HeaderIconButtons(
                    unreadNotif: unreadNotif,
                    onSearchTap: widget.onSearchTap,
                    onNotificationsTap: widget.onNotificationsTap,
                  ),
                ],
              ),
          ],
        ),
        if (compactToolbar) ...[
          const SizedBox(height: 12),
          HomeDashboardToolbar(exporting: _exporting, onExport: _export),
        ],
        const SizedBox(height: 16),
        Text(
          '${_greeting(l10n)}, ${_doctorName(ref)} 👋',
          style: AppDesignSystem.h1(context).copyWith(
            fontSize: compactToolbar ? 20 : 22,
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 16,
          runSpacing: 6,
          children: [
            _SummaryChip(
              icon: Icons.calendar_today_outlined,
              label:
                  '$appointmentCount ${l10n.translate('appointmentsTodayShort')}',
            ),
            _SummaryChip(
              icon: Icons.description_outlined,
              label: '$pendingReports ${l10n.translate('pendingReports')}',
            ),
            _SummaryChip(
              icon: Icons.task_alt_outlined,
              label: '$followUpCount ${l10n.translate('followUpTasks')}',
            ),
          ],
        ),
        if (nextMinutes != null) ...[
          const SizedBox(height: 10),
          Text(
            l10n.translate('nextAppointmentInMinutes').replaceAll(
                  '{minutes}',
                  nextMinutes.toString(),
                ),
            style: AppDesignSystem.body2(context).copyWith(
              color: brand,
              fontWeight: FontWeight.w500,
            ),
          ),
        ],
      ],
    );
  }

  int? _minutesUntilNext(WidgetRef ref, List<Appointment>? appointments) {
    if (appointments == null || appointments.isEmpty) return null;
    final doctorTimeZone =
        ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;
    final now = getNowInTimezone(doctorTimeZone);
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

class _SummaryChip extends StatelessWidget {
  const _SummaryChip({required this.icon, required this.label});

  final IconData icon;
  final String label;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 14, color: AppDesignSystem.textSecondary),
        const SizedBox(width: 6),
        Text(label, style: AppDesignSystem.body2(context)),
      ],
    );
  }
}

class _HeaderIconButtons extends StatelessWidget {
  const _HeaderIconButtons({
    required this.unreadNotif,
    this.onSearchTap,
    this.onNotificationsTap,
  });

  final int unreadNotif;
  final VoidCallback? onSearchTap;
  final VoidCallback? onNotificationsTap;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        IconButton.outlined(
          onPressed: onSearchTap,
          tooltip: AppLocalizations.of(context)?.translate('search') ?? 'Search',
          icon: const Icon(Icons.search, size: 20),
          style: IconButton.styleFrom(
            minimumSize: const Size(40, 40),
            side: const BorderSide(color: AppDesignSystem.border),
          ),
        ),
        Stack(
          clipBehavior: Clip.none,
          children: [
            IconButton.outlined(
              onPressed: onNotificationsTap,
              icon: const Icon(Icons.notifications_outlined, size: 20),
              style: IconButton.styleFrom(
                minimumSize: const Size(40, 40),
                side: const BorderSide(color: AppDesignSystem.border),
              ),
            ),
            if (unreadNotif > 0)
              Positioned(
                right: 8,
                top: 8,
                child: Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: AppColors.destructiveRed,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 1.5),
                  ),
                ),
              ),
          ],
        ),
      ],
    );
  }
}
