// KPI cards for home screen: appointments today, completed, cancelled, new patients.

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/home/application/home_analytics_providers.dart';

class AnalyticsKpiCards extends ConsumerWidget {
  const AnalyticsKpiCards({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(doctorAnalyticsOverviewProvider);
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    return async.when(
      loading: () => const _KpiSkeleton(),
      error: (e, _) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 8),
        child: Text(
          l10n.error,
          style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
        ),
      ),
      data: (overview) => LayoutBuilder(
        builder: (context, constraints) {
          final cards = [
            _KpiCard(
              title: l10n.appointmentsToday,
              value: overview.appointmentsToday.toString(),
              icon: Icons.calendar_today,
              color: brand,
            ),
            _KpiCard(
              title: l10n.completedToday,
              value: overview.completedToday.toString(),
              icon: Icons.check_circle_outline,
              color: Colors.green.shade700,
            ),
            _KpiCard(
              title: l10n.cancelledToday,
              value: overview.cancelledToday.toString(),
              icon: Icons.cancel_outlined,
              color: Colors.orange.shade700,
            ),
            _KpiCard(
              title: l10n.newPatientsToday,
              value: overview.newPatientsToday.toString(),
              icon: Icons.person_add_outlined,
              color: Colors.indigo.shade700,
            ),
          ];
          if (constraints.maxWidth < 600) {
            return Column(
              children: [
                for (var i = 0; i < cards.length; i++) ...[
                  if (i > 0) const SizedBox(height: 12),
                  cards[i],
                ],
              ],
            );
          }
          return Row(
            children: [
              for (var i = 0; i < cards.length; i++) ...[
                if (i > 0) const SizedBox(width: 12),
                Expanded(child: cards[i]),
              ],
            ],
          );
        },
      ),
    );
  }
}

class _KpiCard extends StatelessWidget {
  final String title;
  final String value;
  final IconData icon;
  final Color color;

  const _KpiCard({
    required this.title,
    required this.value,
    required this.icon,
    required this.color,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: AppDesignSystem.cardDecoration(
        borderOverride: Border.all(color: AppDesignSystem.border),
      ),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: BoxDecoration(
              color: color.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Icon(icon, size: 20, color: color),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  value,
                  style: const TextStyle(
                    fontSize: 22,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.5,
                  ),
                ),
                Text(
                  title,
                  style: AppDesignSystem.caption(context),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _KpiSkeleton extends StatelessWidget {
  const _KpiSkeleton();

  @override
  Widget build(BuildContext context) {
    return Row(
      children: List.generate(
        4,
        (i) => Expanded(
          child: Padding(
            padding: EdgeInsets.only(left: i > 0 ? 12 : 0),
            child: Container(
              height: 72,
              decoration: BoxDecoration(
                color: Colors.grey.shade200,
                borderRadius:
                    BorderRadius.circular(AppDesignSystem.cardRadiusSm),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
