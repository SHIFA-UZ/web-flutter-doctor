import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/core/widgets/appointments_trend_chart.dart';
import 'package:shifa_doc_app_v1/core/widgets/visit_type_donut_chart.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_dashboard_export_service.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/analytics_engagement_widget.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/analytics_kpi_cards.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/dashboard_card.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/home_dashboard_toolbar.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';

class HomeAnalyticsSection extends ConsumerStatefulWidget {
  const HomeAnalyticsSection({super.key});

  @override
  ConsumerState<HomeAnalyticsSection> createState() =>
      _HomeAnalyticsSectionState();
}

class _HomeAnalyticsSectionState extends ConsumerState<HomeAnalyticsSection> {
  bool _exporting = false;

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
    final showAdvanced =
        ref.watch(doctorFeatureProvider(DoctorFeature.advancedAnalytics));

    return DashboardCard(
      title: l10n.translate('clinicPerformance'),
      subtitle: l10n.translate('clinicPerformanceSubtitle'),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Align(
            alignment: Alignment.centerLeft,
            child: HomeDashboardToolbar(
              exporting: _exporting,
              onExport: _export,
            ),
          ),
          const SizedBox(height: 16),
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
