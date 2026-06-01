import 'dart:js_util' as js_util;

String? readBrowserTimeZoneId() {
  try {
    final intl = js_util.getProperty(js_util.globalThis, 'Intl');
    if (intl == null) return null;
    final dtf = js_util.callConstructor(
      js_util.getProperty(intl, 'DateTimeFormat'),
      const [],
    );
    final options = js_util.callMethod(dtf, 'resolvedOptions', const []);
    return js_util.getProperty(options, 'timeZone') as String?;
  } catch (_) {
    return null;
  }
}
