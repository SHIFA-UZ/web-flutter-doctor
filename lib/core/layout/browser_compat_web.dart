// ignore_for_file: avoid_web_libraries_in_flutter

import 'dart:html' as html;
import 'dart:js' as js;

/// Web-only browser capability detection for Safari/macOS compatibility workarounds.
class BrowserCompat {
  BrowserCompat._();

  static bool? _isSafari;
  static bool? _isMacOSWeb;
  static bool? _supportsBackdropFilter;

  /// Keeps `--vh` in sync with the visual viewport (Safari toolbar shrink/grow).
  static void bootstrap() {
    _syncViewportUnit();
    html.window.onResize.listen((_) => _syncViewportUnit());
    if (html.window.visualViewport != null) {
      html.window.visualViewport!.onResize.listen((_) => _syncViewportUnit());
    }
  }

  static void _syncViewportUnit() {
    final height = html.window.visualViewport?.height ?? html.window.innerHeight;
    if (height == null || height <= 0) return;
    html.document.documentElement?.style.setProperty(
      '--vh',
      '${height * 0.01}px',
    );
  }

  static bool _cssSupports(String query) {
    try {
      final css = js.context['CSS'];
      if (css == null) return false;
      return css.callMethod('supports', [query]) == true;
    } catch (_) {
      return false;
    }
  }

  /// Safari (desktop or mobile) detected with feature checks first, UA as fallback.
  static bool get isSafari {
    if (_isSafari != null) return _isSafari!;
    final hasWebkitTouchCallout =
        _cssSupports('(-webkit-touch-callout: none)') ||
            _cssSupports('(-webkit-overflow-scrolling: touch)');
    final hasStandardBackdrop = _cssSupports('(backdrop-filter: blur(1px))');
    final hasWebkitBackdrop =
        _cssSupports('(-webkit-backdrop-filter: blur(1px))');
    final webkitOnlyBackdrop = hasWebkitBackdrop && !hasStandardBackdrop;

    if (webkitOnlyBackdrop || hasWebkitTouchCallout) {
      final ua = html.window.navigator.userAgent.toLowerCase();
      _isSafari = ua.contains('safari') &&
          !ua.contains('chrome') &&
          !ua.contains('chromium') &&
          !ua.contains('android');
      return _isSafari!;
    }

    _isSafari = false;
    return false;
  }

  static bool get isMacOSWeb {
    if (_isMacOSWeb != null) return _isMacOSWeb!;
    final platform = html.window.navigator.platform ?? '';
    _isMacOSWeb = platform.toLowerCase().contains('mac');
    return _isMacOSWeb!;
  }

  static bool get supportsBackdropFilter {
    if (_supportsBackdropFilter != null) return _supportsBackdropFilter!;
    _supportsBackdropFilter =
        _cssSupports('(backdrop-filter: blur(1px))') ||
            _cssSupports('(-webkit-backdrop-filter: blur(1px))');
    return _supportsBackdropFilter!;
  }

  /// Safari web scroll views behave more predictably with clamping physics.
  static bool get prefersClampingScrollPhysics => isSafari;
}
