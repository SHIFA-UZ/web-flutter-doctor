// lib/core/util/admin_host.dart
// Shared helper so API layer can know admin context without importing app.dart (avoids circular deps).
import 'package:flutter/foundation.dart' show kIsWeb;

/// True when the app is running on the admin hosting URL (e.g. shifa-admin-*.web.app).
bool get isAdminHost {
  if (!kIsWeb) return false;
  return Uri.base.host.toLowerCase().contains('admin');
}
