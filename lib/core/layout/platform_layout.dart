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

  /// Use bottom navigation shell on native mobile or narrow web viewports.
  static bool useMobileShell(BuildContext context) {
    return isNativeMobile || Responsive.isMobile(context);
  }
}
