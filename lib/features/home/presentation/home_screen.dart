import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_activity_feed.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_ai_copilot.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_analytics_section.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_attention_center.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_floating_quick_actions.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_greeting_header.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_reminders_panel.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_search_overlay.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_timeline_section.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';

/// Next-generation doctor command center — action-first, AI-first dashboard.
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

  void _openSearch() => HomeSearchOverlay.show(context);

  void _openNotifications() =>
      ref.read(shellProvider.notifier).setTab(DoctorShellTab.notifications);

  @override
  Widget build(BuildContext context) {
    final useSinglePane = PlatformLayout.useSinglePane(context);
    final isTablet = Responsive.isTablet(context);

    return HomeSearchShortcut(
      child: Scaffold(
        backgroundColor: AppColors.scaffoldBackground,
        body: Stack(
          children: [
            CustomScrollView(
              slivers: [
                SliverToBoxAdapter(
                  child: Padding(
                    padding: Responsive.screenPadding(context),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        HomeGreetingHeader(
                          onSearchTap: _openSearch,
                          onNotificationsTap: _openNotifications,
                        ),
                        if (useSinglePane) ...[
                          const SizedBox(height: 12),
                          Row(
                            mainAxisAlignment: MainAxisAlignment.end,
                            children: [
                              IconButton.outlined(
                                onPressed: _openSearch,
                                icon: const Icon(Icons.search, size: 20),
                              ),
                              IconButton.outlined(
                                onPressed: _openNotifications,
                                icon: const Icon(Icons.notifications_outlined,
                                    size: 20),
                              ),
                            ],
                          ),
                        ],
                        SizedBox(height: AppDesignSystem.sectionGap),

                        // Row 2 — Timeline (70%) + AI Copilot (30%)
                        if (useSinglePane)
                          Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              HomeTimelineSection(
                                selectedAppointmentId: _selectedAppointmentId,
                                onAppointmentSelected: _onAppointmentSelected,
                              ),
                              const SizedBox(height: AppDesignSystem.sectionGap),
                              HomeAiCopilot(
                                selectedPatientId: _selectedPatientId,
                                selectedAppointmentId: _selectedAppointmentId,
                                onPatientChanged: (v) =>
                                    setState(() => _selectedPatientId = v),
                              ),
                            ],
                          )
                        else
                          IntrinsicHeight(
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Expanded(
                                  flex: isTablet ? 6 : 7,
                                  child: HomeTimelineSection(
                                    selectedAppointmentId:
                                        _selectedAppointmentId,
                                    onAppointmentSelected:
                                        _onAppointmentSelected,
                                  ),
                                ),
                                const SizedBox(width: AppDesignSystem.sectionGap),
                                Expanded(
                                  flex: isTablet ? 4 : 3,
                                  child: Column(
                                    children: [
                                      HomeAiCopilot(
                                        selectedPatientId: _selectedPatientId,
                                        selectedAppointmentId:
                                            _selectedAppointmentId,
                                        onPatientChanged: (v) => setState(
                                            () => _selectedPatientId = v),
                                      ),
                                      const SizedBox(
                                          height: AppDesignSystem.sectionGap),
                                      const HomeRemindersPanel(),
                                    ],
                                  ),
                                ),
                              ],
                            ),
                          ),

                        if (useSinglePane) ...[
                          const SizedBox(height: AppDesignSystem.sectionGap),
                          const HomeRemindersPanel(),
                        ],

                        SizedBox(height: AppDesignSystem.sectionGap),

                        // Row 3 — Attention + Activity feed
                        if (useSinglePane)
                          const Column(
                            children: [
                              HomeAttentionCenter(),
                              SizedBox(height: AppDesignSystem.sectionGap),
                              HomeActivityFeed(),
                            ],
                          )
                        else
                          const Row(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Expanded(child: HomeAttentionCenter()),
                              SizedBox(width: AppDesignSystem.sectionGap),
                              Expanded(child: HomeActivityFeed()),
                            ],
                          ),

                        SizedBox(height: AppDesignSystem.sectionGap),
                        const HomeAnalyticsSection(),
                        SizedBox(
                          height: Responsive.bottomNavClearance(context) + 96,
                        ),
                      ],
                    ),
                  ),
                ),
              ],
            ),
            Positioned(
              right: useSinglePane ? 16 : 32,
              bottom: Responsive.bottomNavClearance(context) + 16,
              child: const HomeFloatingQuickActions(),
            ),
          ],
        ),
      ),
    );
  }
}
