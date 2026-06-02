/// Stub for non-web platforms.
void showWebBrowserNotification({
  required String title,
  required String body,
  required String tag,
  required void Function() onTap,
  void Function(String tag)? registerPayload,
  void Function(String tag)? unregisterPayload,
}) {
  // No-op on mobile/desktop native.
}
