import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';

/// Compact equal-height metric tile for the home overview grid.
class OverviewMetricCard extends StatelessWidget {
  const OverviewMetricCard({
    super.key,
    required this.icon,
    required this.label,
    required this.value,
    this.secondary,
    this.onTap,
  });

  final IconData icon;
  final String label;
  final String value;
  final String? secondary;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    final card = Container(
      padding: const EdgeInsets.all(14),
      decoration: AppDesignSystem.cardDecoration(
        color: AppDesignSystem.background,
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        mainAxisSize: MainAxisSize.min,
        children: [
          Row(
            children: [
              Container(
                width: 28,
                height: 28,
                decoration: BoxDecoration(
                  color: brand.withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Icon(icon, size: 16, color: brand),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: Text(
                  label,
                  style: AppDesignSystem.caption(context),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Text(
            value,
            style: AppDesignSystem.h1(context).copyWith(
              fontSize: 22,
              letterSpacing: -0.4,
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          if (secondary != null && secondary!.isNotEmpty) ...[
            const SizedBox(height: 2),
            Text(
              secondary!,
              style: AppDesignSystem.caption(context),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
            ),
          ],
        ],
      ),
    );

    if (onTap == null) return card;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(AppDesignSystem.cardRadius),
        child: card,
      ),
    );
  }
}
