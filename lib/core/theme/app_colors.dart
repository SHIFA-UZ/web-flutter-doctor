import 'package:flutter/material.dart';

/// Shifa Design System — Color Tokens
///
/// All color values used by both Doctor and Patient apps.
/// Never hard-code hex values at the widget level; reference these constants.
abstract final class AppColors {
  // ── Primary ───────────────────────────────────────────
  static const Color primaryTeal = Color(0xFF00BBB0);
  static const Color primaryLight = Color(0xFF59C2BC);
  static const Color secondaryLight = Color(0xFFCCF1EF);

  // ── Destructive ───────────────────────────────────────
  static const Color destructiveRed = Color(0xFFDC2F2F);
  static const Color destructiveLight = Color(0xFFEB5454);
  static const Color destructiveSecondaryLight = Color(0xFFF5C1C1);

  // ── Neutral ───────────────────────────────────────────
  static const Color disabledGrey = Color(0xFFC6C6C6);
  static const Color white = Color(0xFFFFFFFF);

  /// White fill for cards sitting on [cardboard].
  static const Color card = white;

  /// Page board behind cards (Home, lists, settings). Matches the Figma
  /// cardboard / board surface used by the doctor mobile screens.
  static const Color cardboard = Color(0xFFF5F5F5);

  static const Color scaffoldBackground = cardboard;
}
