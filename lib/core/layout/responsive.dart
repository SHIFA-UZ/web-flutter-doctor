import 'dart:io' show Platform;

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';

/// Shared breakpoints and helpers for adapting the doctor web app to phone,
/// tablet (iPad Safari), and desktop browsers.
class Responsive {
  Responsive._();

  /// Phone-sized viewports (Safari, Chrome mobile).
  static const double mobileBreakpoint = 768;

  /// Tablet / narrow desktop — side-by-side panes start feeling cramped.
  static const double tabletBreakpoint = 980;

  /// Viewports below this width use a compact icon sidebar instead of the full
  /// labeled sidebar (typical iPad portrait in Safari).
  static const double compactSidebarBreakpoint = 1100;

  /// Minimum content width (after sidebar) for master-detail split layouts.
  static const double splitPaneBreakpoint = 720;

  static const double compactSidebarWidth = 72;
  static const double desktopSidebarWidth = 260;

  static double widthOf(BuildContext context) =>
      MediaQuery.sizeOf(context).width;

  static double heightOf(BuildContext context) =>
      MediaQuery.sizeOf(context).height;

  static double shortestSideOf(BuildContext context) =>
      MediaQuery.sizeOf(context).shortestSide;

  /// True for phone browsers and narrow web viewports.
  static bool isMobile(BuildContext context) =>
      widthOf(context) < mobileBreakpoint;

  /// True for iPad-sized browsers and narrow laptops (768–1100 CSS px).
  static bool isTablet(BuildContext context) {
    final w = widthOf(context);
    return w >= mobileBreakpoint && w < compactSidebarBreakpoint;
  }

  static bool isTabletOrMobile(BuildContext context) =>
      widthOf(context) < tabletBreakpoint;

  /// Web phones only — excludes iPad/tablet browsers that may report <768 px
  /// when browser chrome is visible.
  static bool isPhoneWeb(BuildContext context) {
    if (!kIsWeb) return isMobile(context);
    final size = MediaQuery.sizeOf(context);
    if (size.shortestSide >= 600) return false;
    return size.width < mobileBreakpoint;
  }

  /// Mirrors [PlatformLayout.useMobileShell] without a circular import.
  static bool useMobileShell(BuildContext context) {
    if (!kIsWeb && (Platform.isAndroid || Platform.isIOS)) return true;
    return isPhoneWeb(context);
  }

  static bool _shell(BuildContext context, bool? useMobileShell) =>
      useMobileShell ?? Responsive.useMobileShell(context);

  /// Sidebar width for the current viewport (0 when bottom-nav shell is active).
  static double sidebarWidthForViewport(double viewportWidth) {
    if (viewportWidth < mobileBreakpoint && !kIsWeb) return 0;
    if (kIsWeb && viewportWidth < mobileBreakpoint) {
      // Narrow web without tablet dimensions → no sidebar (bottom nav).
      return 0;
    }
    if (viewportWidth < compactSidebarBreakpoint) return compactSidebarWidth;
    return desktopSidebarWidth;
  }

  /// Effective content width after subtracting the shell sidebar.
  static double contentWidthOf(
    BuildContext context, {
    required bool useMobileShell,
  }) {
    if (useMobileShell) return widthOf(context);
    return widthOf(context) - sidebarWidthForViewport(widthOf(context));
  }

  /// Use stacked / single-pane layouts (master-detail, calendar mobile, etc.).
  static bool useSinglePane(BuildContext context, {bool? useMobileShell}) {
    final shell = _shell(context, useMobileShell);
    if (shell) return true;
    return contentWidthOf(context, useMobileShell: false) <
        splitPaneBreakpoint;
  }

  /// Use compact toolbars (icon buttons, vertical stacks).
  static bool useCompactToolbar(BuildContext context, {bool? useMobileShell}) {
    final shell = _shell(context, useMobileShell);
    if (shell) return true;
    return contentWidthOf(context, useMobileShell: false) <
        tabletBreakpoint;
  }

  static EdgeInsets screenPadding(BuildContext context, {bool? useMobileShell}) {
    final shell = _shell(context, useMobileShell);
    if (shell) {
      return const EdgeInsets.fromLTRB(12, 8, 12, 8);
    }
    if (isTablet(context)) {
      return const EdgeInsets.fromLTRB(16, 12, 16, 12);
    }
    return const EdgeInsets.all(24);
  }

  static double sectionGap(BuildContext context, {bool? useMobileShell}) =>
      _shell(context, useMobileShell) || isTablet(context) ? 12 : 16;

  static TextStyle pageTitleStyle(BuildContext context, {bool? useMobileShell}) =>
      TextStyle(
        fontSize: _shell(context, useMobileShell) || isTablet(context) ? 22 : 28,
        fontWeight: FontWeight.bold,
      );

  static TextStyle pageSubtitleStyle(BuildContext context, {bool? useMobileShell}) =>
      TextStyle(
        fontSize: _shell(context, useMobileShell) || isTablet(context) ? 13 : 14,
        color: Colors.grey.shade700,
      );

  /// Max width for dialogs / bottom sheets on narrow screens.
  static double dialogMaxWidth(BuildContext context) {
    final w = widthOf(context);
    if (w < mobileBreakpoint) return w - 32;
    if (isTablet(context)) return w - 48;
    return 480;
  }

  /// Constrain overlay/dialog width to the available viewport.
  static double overlayWidth(BuildContext context, double preferredWidth) {
    final maxW = widthOf(context) - 48;
    return preferredWidth.clamp(280.0, maxW);
  }

  /// Bottom inset so floating panels (e.g. patient briefing) sit above mobile nav.
  static double bottomNavClearance(BuildContext context, {bool? useMobileShell}) {
    if (!_shell(context, useMobileShell)) return 12;
    return kBottomNavigationBarHeight +
        MediaQuery.paddingOf(context).bottom +
        12;
  }

  static double mobileBottomSheetPadding(BuildContext context, {bool? useMobileShell}) {
    if (!_shell(context, useMobileShell)) return 16;
    final systemBottom = MediaQuery.paddingOf(context).bottom;
    final gestureFallback = systemBottom < 8 ? 20.0 : 0.0;
    return systemBottom +
        kBottomNavigationBarHeight +
        gestureFallback +
        24;
  }
}

/// Convenience accessors that read shell mode from [PlatformLayoutScope] when
/// available, or fall back to legacy viewport-only checks.
extension ResponsiveContext on BuildContext {
  bool get isMobileLayout => Responsive.isMobile(this);
  bool get isTabletLayout => Responsive.isTablet(this);
}

/// Renders [row] on wide screens and [column] on phone-sized viewports.
class ResponsiveRowColumn extends StatelessWidget {
  const ResponsiveRowColumn({
    super.key,
    required this.row,
    required this.column,
    this.gap = 24,
    this.useMobileShell,
  });

  final List<Widget> row;
  final List<Widget> column;
  final double gap;
  final bool? useMobileShell;

  @override
  Widget build(BuildContext context) {
    final stackVertically = Responsive.useSinglePane(
      context,
      useMobileShell: useMobileShell,
    );
    if (stackVertically) {
      final spaced = <Widget>[];
      for (var i = 0; i < column.length; i++) {
        if (i > 0) spaced.add(SizedBox(height: gap));
        spaced.add(column[i]);
      }
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: spaced,
      );
    }

    final spaced = <Widget>[];
    for (var i = 0; i < row.length; i++) {
      if (i > 0) spaced.add(SizedBox(width: gap));
      spaced.add(row[i]);
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: spaced,
    );
  }
}

/// Wraps [children] in a [Row] on desktop and a vertical [Column] on mobile.
class ResponsiveToolbar extends StatelessWidget {
  const ResponsiveToolbar({
    super.key,
    required this.children,
    this.gap = 12,
    this.mobileGap = 8,
    this.useMobileShell,
  });

  final List<Widget> children;
  final double gap;
  final double mobileGap;
  final bool? useMobileShell;

  @override
  Widget build(BuildContext context) {
    final stackVertically = Responsive.useCompactToolbar(
      context,
      useMobileShell: useMobileShell,
    );
    if (stackVertically) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          for (var i = 0; i < children.length; i++) ...[
            if (i > 0) SizedBox(height: mobileGap),
            children[i],
          ],
        ],
      );
    }
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < children.length; i++) ...[
          if (i > 0) SizedBox(width: gap),
          if (i == 0) Expanded(child: children[i]) else children[i],
        ],
      ],
    );
  }
}
