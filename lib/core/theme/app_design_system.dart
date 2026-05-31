import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';

/// Extended design tokens for the doctor web app dashboard redesign.
abstract final class AppDesignSystem {
  // ── Extended palette ─────────────────────────────────────
  static const Color primaryDark = Color(0xFF129B8A);
  static const Color primaryAi = Color(0xFF26BAA4);
  static const Color textPrimary = Color(0xFF1A1A1A);
  static const Color textSecondary = Color(0xFF6B7280);
  static const Color textTertiary = Color(0xFF9CA3AF);
  static const Color border = Color(0xFFE5E7EB);
  static const Color background = Color(0xFFFFFFFF);
  static const Color backgroundSecondary = Color(0xFFF9FAFB);
  static const Color backgroundTertiary = Color(0xFFF3F4F6);
  static const Color success = Color(0xFF059669);
  static const Color warning = Color(0xFFD97706);
  static const Color info = Color(0xFF0284C7);

  // ── Typography ───────────────────────────────────────────
  static const double displaySize = 28;
  static const double h1Size = 22;
  static const double h2Size = 16;
  static const double body1Size = 15;
  static const double body2Size = 13;
  static const double captionSize = 11;

  static TextStyle display(BuildContext context) => TextStyle(
        fontSize: displaySize,
        fontWeight: FontWeight.w700,
        color: textPrimary,
        letterSpacing: -0.5,
        height: 1.2,
      );

  static TextStyle h1(BuildContext context) => TextStyle(
        fontSize: h1Size,
        fontWeight: FontWeight.w600,
        color: textPrimary,
        height: 1.25,
      );

  static TextStyle h2(BuildContext context) => TextStyle(
        fontSize: h2Size,
        fontWeight: FontWeight.w600,
        color: textPrimary,
        height: 1.3,
      );

  static TextStyle body1(BuildContext context) => TextStyle(
        fontSize: body1Size,
        fontWeight: FontWeight.w400,
        color: textPrimary,
        height: 1.45,
      );

  static TextStyle body2(BuildContext context) => TextStyle(
        fontSize: body2Size,
        fontWeight: FontWeight.w400,
        color: textSecondary,
        height: 1.4,
      );

  static TextStyle caption(BuildContext context) => TextStyle(
        fontSize: captionSize,
        fontWeight: FontWeight.w500,
        color: textTertiary,
        height: 1.35,
      );

  // ── Spacing & radii ──────────────────────────────────────
  static const double cardRadius = 20;
  static const double cardRadiusSm = 14;
  static const double cardPadding = 20;
  static const double sectionGap = 20;
  static const double itemGap = 12;

  static List<BoxShadow> get cardShadow => [
        BoxShadow(
          color: Colors.black.withValues(alpha: 0.04),
          blurRadius: 12,
          offset: const Offset(0, 2),
        ),
        BoxShadow(
          color: Colors.black.withValues(alpha: 0.02),
          blurRadius: 4,
          offset: const Offset(0, 1),
        ),
      ];

  static List<BoxShadow> get elevatedShadow => [
        BoxShadow(
          color: AppColors.primaryTeal.withValues(alpha: 0.08),
          blurRadius: 24,
          offset: const Offset(0, 8),
        ),
      ];

  static BoxDecoration cardDecoration({Color? color, Border? borderOverride}) =>
      BoxDecoration(
        color: color ?? background,
        borderRadius: BorderRadius.circular(cardRadius),
        border: borderOverride ?? Border.all(color: border, width: 1),
        boxShadow: cardShadow,
      );

  static BoxDecoration aiCardDecoration() => BoxDecoration(
        gradient: LinearGradient(
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
          colors: [
            AppColors.primaryTeal.withValues(alpha: 0.06),
            background,
            primaryAi.withValues(alpha: 0.04),
          ],
        ),
        borderRadius: BorderRadius.circular(cardRadius),
        border: Border.all(color: AppColors.primaryTeal.withValues(alpha: 0.18)),
        boxShadow: elevatedShadow,
      );
}
