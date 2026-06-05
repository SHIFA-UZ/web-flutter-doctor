import 'package:flutter/material.dart';

/// Shared breakpoints and helpers for adapting the doctor web app to phone browsers.
class Responsive {
  Responsive._();

  /// Phone-sized viewports (Safari, Chrome mobile).
  static const double mobileBreakpoint = 768;

  /// Tablet / narrow desktop — side-by-side panes start feeling cramped.
  static const double tabletBreakpoint = 980;

  static double widthOf(BuildContext context) =>
      MediaQuery.sizeOf(context).width;

  static bool isMobile(BuildContext context) =>
      widthOf(context) < mobileBreakpoint;

  static bool isTabletOrMobile(BuildContext context) =>
      widthOf(context) < tabletBreakpoint;

  static EdgeInsets screenPadding(BuildContext context) =>
      isMobile(context)
          ? const EdgeInsets.fromLTRB(12, 8, 12, 8)
          : const EdgeInsets.all(24);

  static double sectionGap(BuildContext context) => isMobile(context) ? 12 : 16;

  static TextStyle pageTitleStyle(BuildContext context) => TextStyle(
        fontSize: isMobile(context) ? 22 : 28,
        fontWeight: FontWeight.bold,
      );

  static TextStyle pageSubtitleStyle(BuildContext context) => TextStyle(
        fontSize: isMobile(context) ? 13 : 14,
        color: Colors.grey.shade700,
      );

  /// Max width for dialogs / bottom sheets on narrow screens.
  static double dialogMaxWidth(BuildContext context) =>
      isMobile(context) ? widthOf(context) - 32 : 480;

  /// Bottom inset so floating panels (e.g. patient briefing) sit above mobile nav.
  static double bottomNavClearance(BuildContext context) {
    if (!isMobile(context)) return 12;
    return kBottomNavigationBarHeight +
        MediaQuery.paddingOf(context).bottom +
        12;
  }

  /// Bottom padding for modal sheets so the last row clears app nav + OS gesture bar.
  /// Mobile browsers (especially Samsung Internet) often report zero [MediaQuery.padding].
  static double mobileBottomSheetPadding(BuildContext context) {
    if (!isMobile(context)) return 16;
    final systemBottom = MediaQuery.paddingOf(context).bottom;
    final gestureFallback = systemBottom < 8 ? 20.0 : 0.0;
    return systemBottom +
        kBottomNavigationBarHeight +
        gestureFallback +
        24;
  }
}

/// Renders [row] on wide screens and [column] on phone-sized viewports.
class ResponsiveRowColumn extends StatelessWidget {
  const ResponsiveRowColumn({
    super.key,
    required this.row,
    required this.column,
    this.gap = 24,
  });

  final List<Widget> row;
  final List<Widget> column;
  final double gap;

  @override
  Widget build(BuildContext context) {
    if (Responsive.isMobile(context)) {
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
  });

  final List<Widget> children;
  final double gap;
  final double mobileGap;

  @override
  Widget build(BuildContext context) {
    if (Responsive.isMobile(context)) {
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
