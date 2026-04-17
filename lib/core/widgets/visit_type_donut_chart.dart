import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';
import 'analytics_container.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Pie/donut: video vs in-person consultations (last 30 days) from API.
class VisitTypeDonutChart extends ConsumerWidget {
  const VisitTypeDonutChart({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(doctorConsultationTypesProvider);
    final brand = Theme.of(context).colorScheme.primary;
    final l10n = AppLocalizations.of(context)!;

    return async.when(
      loading: () => AnalyticsContainer(
        title: l10n.visitTypeDistribution,
        child: SizedBox(
          height: 160,
          child: Center(
            child: SizedBox(
              width: 24,
              height: 24,
              child: CircularProgressIndicator(strokeWidth: 2, color: brand),
            ),
          ),
        ),
      ),
      error: (e, _) => AnalyticsContainer(
        title: l10n.visitTypeDistribution,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 24),
            child: Text(
            l10n.error,
            style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
          ),
        ),
      ),
      data: (data) {
        final total = data.total;
        if (total == 0) {
          return AnalyticsContainer(
            title: l10n.visitTypeDistribution,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text(
                l10n.analyticsNoData,
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),
            ),
          );
        }
        final inPersonPercent = ((data.inPerson / total) * 100).round();
        final videoPercent = ((data.video / total) * 100).round();
        final inPersonValue = data.inPerson.toDouble();
        final videoValue = data.video.toDouble();

        return AnalyticsContainer(
          title: l10n.visitTypeDistribution,
          child: SizedBox(
            height: 160,
            child: Stack(
              alignment: Alignment.center,
              children: [
                PieChart(
                  PieChartData(
                    centerSpaceRadius: 50,
                    sectionsSpace: 2,
                    sections: [
                      PieChartSectionData(
                        value: inPersonValue,
                        color: brand,
                        title: '',
                        radius: 50,
                      ),
                      PieChartSectionData(
                        value: videoValue,
                        color: brand.withOpacity(0.4),
                        title: '',
                        radius: 50,
                      ),
                    ],
                  ),
                ),
                Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Text(
                      '$inPersonPercent%',
                      style: TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                        color: brand,
                      ),
                    ),
                    Text(
                      l10n.inPerson,
                      style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                    ),
                    const SizedBox(height: 4),
                    Text(
                      '$videoPercent% ${l10n.videoCall}',
                      style: TextStyle(fontSize: 10, color: Colors.grey.shade600),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      '${data.inPerson} / ${data.video}',
                      style: TextStyle(fontSize: 9, color: Colors.grey.shade500),
                    ),
                  ],
                ),
              ],
            ),
          ),
        );
      },
    );
  }
}
