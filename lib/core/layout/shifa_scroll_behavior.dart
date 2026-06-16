import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';

import 'browser_compat.dart';

/// App-wide scroll behavior tuned for Safari/macOS web (mouse + trackpad drag).
class ShifaScrollBehavior extends MaterialScrollBehavior {
  const ShifaScrollBehavior();

  @override
  Set<PointerDeviceKind> get dragDevices => const {
        PointerDeviceKind.touch,
        PointerDeviceKind.mouse,
        PointerDeviceKind.stylus,
        PointerDeviceKind.trackpad,
        PointerDeviceKind.unknown,
      };

  @override
  ScrollPhysics getScrollPhysics(BuildContext context) {
    if (BrowserCompat.prefersClampingScrollPhysics) {
      return const ClampingScrollPhysics();
    }
    return super.getScrollPhysics(context);
  }
}
