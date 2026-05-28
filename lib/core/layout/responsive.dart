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
          ? const EdgeInsets.symmetric(horizontal: 12, vertical: 12)
          : const EdgeInsets.all(24);

  /// Bottom inset so floating panels (e.g. patient briefing) sit above mobile nav.
  static double bottomNavClearance(BuildContext context) {
    if (!isMobile(context)) return 12;
    return kBottomNavigationBarHeight +
        MediaQuery.paddingOf(context).bottom +
        12;
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
