import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/widgets/appointments_trend_chart.dart';
import 'package:shifa_doc_app_v1/core/widgets/visit_type_donut_chart.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/analytics_engagement_widget.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/analytics_kpi_cards.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

class HomeAnalyticsSection extends ConsumerWidget {
  const HomeAnalyticsSection({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final l10n = AppLocalizations.of(context)!;
    final showAdvanced =
        ref.watch(doctorFeatureProvider(DoctorFeature.advancedAnalytics));

    return DashboardCard(
      title: l10n.translate('clinicPerformance') ?? 'Clinic performance',
      subtitle: l10n.translate('clinicPerformanceSubtitle') ??
          'Analytics overview — patients first, insights second',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          const AnalyticsKpiCards(),
          if (showAdvanced) ...[
            SizedBox(height: Responsive.sectionGap(context)),
            const AppointmentsTrendChart(),
            SizedBox(height: Responsive.sectionGap(context)),
            const VisitTypeDonutChart(),
            SizedBox(height: Responsive.sectionGap(context)),
            const AnalyticsEngagementWidget(),
          ],
        ],
      ),
    );
  }
}
