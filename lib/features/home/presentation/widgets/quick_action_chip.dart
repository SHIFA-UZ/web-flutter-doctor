import 'package:flutter/material.dart';

import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';

/// Compact horizontally scrollable quick-action chip.
class QuickActionChip extends StatelessWidget {
  const QuickActionChip({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
  });

  final IconData icon;
  final String label;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final brand = Theme.of(context).colorScheme.primary;
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(24),
        child: Ink(
          height: 44,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            color: AppDesignSystem.background,
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: AppDesignSystem.border),
            boxShadow: AppDesignSystem.cardShadow,
          ),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(icon, size: 18, color: brand),
              const SizedBox(width: 8),
              Text(
                label,
                style: AppDesignSystem.body2(context).copyWith(
                  color: AppDesignSystem.textPrimary,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
