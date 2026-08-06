import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_refresh.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_activity_feed.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_ai_copilot.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_analytics_section.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_attention_center.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_clinical_workflow.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_header.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_reminders_panel.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_search_overlay.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

/// Doctor command center — clinical workflow first, insights second.
class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  String? _selectedAppointmentId;
  String? _selectedPatientId;

  void _onAppointmentSelected(Appointment appt) {
    setState(() {
      _selectedAppointmentId = appt.id;
      if (appt.patientId != null) {
        _selectedPatientId = appt.patientId.toString();
      }
    });
  }

  void _openNotifications() =>
      ref.read(shellProvider.notifier).setTab(DoctorShellTab.notifications);

  Future<void> _refreshHome() => refreshHomeDashboard(ref);

  void _refreshHomeOnTabFocus() {
    refreshHomeDashboardOnTabFocus(ref);
  }

  @override
  Widget build(BuildContext context) {
    final useSinglePane = PlatformLayout.useSinglePane(context);

    ref.listen<int>(shellProvider, (prev, next) {
      if (next == DoctorShellTab.home && prev != DoctorShellTab.home) {
        _refreshHomeOnTabFocus();
      }
    });

    final workflow = HomeClinicalWorkflow(
      selectedAppointmentId: _selectedAppointmentId,
      onAppointmentSelected: _onAppointmentSelected,
    );

    final secondaryColumn = Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        HomeAiCopilot(
          selectedPatientId: _selectedPatientId,
          selectedAppointmentId: _selectedAppointmentId,
          onPatientChanged: (v) => setState(() => _selectedPatientId = v),
        ),
        const SizedBox(height: AppDesignSystem.sectionGap),
        const HomeRemindersPanel(),
        const SizedBox(height: AppDesignSystem.sectionGap),
        const HomeAttentionCenter(),
        const SizedBox(height: AppDesignSystem.sectionGap),
        const HomeActivityFeed(),
      ],
    );

    return HomeSearchShortcut(
      child: Scaffold(
        backgroundColor: AppColors.scaffoldBackground,
        body: RefreshIndicator(
          onRefresh: _refreshHome,
          child: CustomScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            slivers: [
              SliverToBoxAdapter(
                child: Padding(
                  padding: Responsive.screenPadding(context),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      HomeHeader(onNotificationsTap: _openNotifications),
                      SizedBox(height: AppDesignSystem.itemGap),
                      if (useSinglePane) ...[
                        workflow,
                        SizedBox(height: AppDesignSystem.sectionGap),
                        secondaryColumn,
                      ] else
                        Row(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Expanded(flex: 7, child: workflow),
                            const SizedBox(width: AppDesignSystem.sectionGap),
                            Expanded(flex: 3, child: secondaryColumn),
                          ],
                        ),
                      SizedBox(height: AppDesignSystem.sectionGap),
                      const HomeAnalyticsSection(),
                      SizedBox(
                        height: Responsive.bottomNavClearance(context) + 24,
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
