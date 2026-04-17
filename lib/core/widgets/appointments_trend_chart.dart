import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';
import 'analytics_container.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';

/// Line chart: appointments per day (last 7 days) from API.
class AppointmentsTrendChart extends ConsumerWidget {
  const AppointmentsTrendChart({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(doctorAnalyticsTrendProvider);
    final brand = Theme.of(context).colorScheme.primary;
    final l10n = AppLocalizations.of(context)!;

    return async.when(
      loading: () => AnalyticsContainer(
        title: l10n.appointmentsLast7Days,
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
        title: l10n.appointmentsLast7Days,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 24),
            child: Text(
                l10n.error,
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),
        ),
      ),
      data: (points) {
        if (points.isEmpty) {
          return AnalyticsContainer(
            title: l10n.appointmentsLast7Days,
            child: Padding(
              padding: const EdgeInsets.symmetric(vertical: 24),
              child: Text(
                l10n.analyticsNoData,
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600),
              ),
            ),
          );
        }
        final counts = points.map((p) => p.count).toList();
        final maxValue = counts.isEmpty ? 10.0 : counts.reduce((a, b) => a > b ? a : b).toDouble();
        final yAxisMax = maxValue == 0 ? 10.0 : (maxValue * 1.2).ceilToDouble();
        final yAxisInterval = yAxisMax <= 5 ? 1.0 : (yAxisMax / 5).ceilToDouble();

        // Short date labels (e.g. 01/22)
        String dateLabel(int i) {
          if (i < 0 || i >= points.length) return '';
          final s = points[i].date;
          if (s.length >= 10) return '${s.substring(5, 7)}/${s.substring(8, 10)}';
          return '';
        }

        return AnalyticsContainer(
          title: l10n.appointmentsLast7Days,
          child: SizedBox(
            height: 160,
            child: LineChart(
              LineChartData(
                gridData: FlGridData(
                  show: true,
                  drawVerticalLine: false,
                  horizontalInterval: yAxisInterval,
                  getDrawingHorizontalLine: (value) => FlLine(
                    color: Colors.grey.shade200,
                    strokeWidth: 1,
                  ),
                ),
                borderData: FlBorderData(
                  show: true,
                  border: Border(
                    bottom: BorderSide(color: Colors.grey.shade300, width: 1),
                    left: BorderSide(color: Colors.grey.shade300, width: 1),
                  ),
                ),
                titlesData: FlTitlesData(
                  leftTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 40,
                      interval: yAxisInterval,
                      getTitlesWidget: (value, meta) => Padding(
                        padding: const EdgeInsets.only(right: 4),
                        child: Text(
                          value.toInt().toString(),
                          style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
                          textAlign: TextAlign.right,
                        ),
                      ),
                    ),
                  ),
                  rightTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  topTitles: AxisTitles(sideTitles: SideTitles(showTitles: false)),
                  bottomTitles: AxisTitles(
                    sideTitles: SideTitles(
                      showTitles: true,
                      reservedSize: 24,
                      getTitlesWidget: (value, _) {
                        final i = value.toInt();
                        return value.toInt() >= 0 && value.toInt() < points.length
                            ? Text(
                                dateLabel(i),
                                style: TextStyle(fontSize: 10, color: Colors.grey.shade600),
                              )
                            : const SizedBox.shrink();
                      },
                    ),
                  ),
                ),
                lineBarsData: [
                  LineChartBarData(
                    isCurved: true,
                    color: brand,
                    barWidth: 3,
                    dotData: FlDotData(
                      show: true,
                      getDotPainter: (spot, percent, barData, index) => FlDotCirclePainter(
                        radius: 4,
                        color: brand,
                        strokeWidth: 2,
                        strokeColor: Colors.white,
                      ),
                    ),
                    spots: List.generate(
                      points.length,
                      (i) => FlSpot(i.toDouble(), points[i].count.toDouble()),
                    ),
                  ),
                ],
                minY: 0,
                maxY: yAxisMax,
              ),
            ),
          ),
        );
      },
    );
  }
}
