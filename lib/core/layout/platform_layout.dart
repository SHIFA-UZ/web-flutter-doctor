import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

import 'responsive.dart';

/// Layout helpers that distinguish native mobile apps from responsive web.
class PlatformLayout {
  PlatformLayout._();

  /// True when running as a native Android or iOS app (not web).
  static bool get isNativeMobile {
    if (kIsWeb) return false;
    return Platform.isAndroid || Platform.isIOS;
  }

  /// Use bottom navigation shell on native mobile or phone-sized web viewports.
  /// iPad Safari (shortest side ≥ 600) keeps the sidebar even when width dips
  /// below 768 px due to browser chrome.
  static bool useMobileShell(BuildContext context) =>
      Responsive.useMobileShell(context);

  /// Sidebar width for the current shell mode (0 when bottom nav is shown).
  static double sidebarWidth(BuildContext context) {
    if (useMobileShell(context)) return 0;
    return Responsive.sidebarWidthForViewport(Responsive.widthOf(context));
  }

  /// Content area width inside [MainShell] after the sidebar.
  static double contentWidth(BuildContext context) =>
      Responsive.contentWidthOf(
        context,
        useMobileShell: useMobileShell(context),
      );

  static bool useSinglePane(BuildContext context) =>
      Responsive.useSinglePane(context);

  static bool useCompactToolbar(BuildContext context) =>
      Responsive.useCompactToolbar(context);

  static bool useCompactSidebar(BuildContext context) {
    if (useMobileShell(context)) return false;
    return Responsive.widthOf(context) < Responsive.compactSidebarBreakpoint;
  }

  static EdgeInsets screenPadding(BuildContext context) =>
      Responsive.screenPadding(context);

  static double sectionGap(BuildContext context) =>
      Responsive.sectionGap(context);

  static TextStyle pageTitleStyle(BuildContext context) =>
      Responsive.pageTitleStyle(context);

  static TextStyle pageSubtitleStyle(BuildContext context) =>
      Responsive.pageSubtitleStyle(context);

  static double bottomNavClearance(BuildContext context) =>
      Responsive.bottomNavClearance(context);

  static double mobileBottomSheetPadding(BuildContext context) =>
      Responsive.mobileBottomSheetPadding(context);
}
