import 'dart:convert';
import 'dart:html' as html;
import 'dart:js_util' as js_util;

/// Shows a browser notification on web (Telegram-style).
void showWebBrowserNotification({
  required String title,
  required String body,
  required String tag,
  required void Function() onTap,
  void Function(String tag, Map<String, dynamic> payload)? registerPayload,
  void Function(String tag)? unregisterPayload,
}) {
  if (!html.Notification.supported) return;

  html.Notification.requestPermission().then((perm) {
    if (perm != 'granted') return;
    final notification = html.Notification(
      title,
      body: body,
      icon: '/icons/Icon-192.png',
      tag: tag,
    );
    notification.onClick.listen((_) {
      try {
        js_util.callMethod(html.window, 'focus', []);
      } catch (_) {}
      unregisterPayload?.call(tag);
      onTap();
    });
  });
}

String encodeWebPayload(Map<String, dynamic> data) => jsonEncode(data);
