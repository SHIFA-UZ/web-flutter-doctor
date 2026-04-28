// lib/state/profile/profile_actions.dart
import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import '../../core/api/api_providers.dart';
import 'package:http/http.dart' as http;

Future<void> patchProfile(WidgetRef ref, Map<String, dynamic> body) async {
  final api = ref.read(apiClientProvider);
  final res = await api.patch('/api/doctors/me/profile', body);
  if (res.statusCode != 200) throw Exception('Save failed: ${res.body}');
  // Optionally refresh
  await ref.refresh(profileAllProvider.future);
}

Future<void> patchContact(WidgetRef ref, Map<String, dynamic> body) async {
  final api = ref.read(apiClientProvider);
  final res = await api.patch('/api/doctors/me/contact', body);
  if (res.statusCode != 200) {
    final msg = _messageFromResponse(res);
    throw Exception(msg);
  }
  await ref.refresh(profileAllProvider.future);
}

String _messageFromResponse(dynamic res) {
  try {
    final decoded = jsonDecode(res.body) as Map<String, dynamic>?;
    final message = decoded?['message'];
    if (message != null) return message.toString();
  } catch (_) {}
  return 'Save failed: ${res.statusCode} ${res.body}';
}

Future<void> patchBilling(WidgetRef ref, Map<String, dynamic> body) async {
  final api = ref.read(apiClientProvider);
  final res = await api.patch('/api/doctors/me/billing', body);
  if (res.statusCode != 200) throw Exception('Save failed: ${res.body}');
  await ref.refresh(profileAllProvider.future);
}

Future<Map<String, dynamic>> createStripeConnectOnboarding(WidgetRef ref) async {
  final api = ref.read(apiClientProvider);
  final res = await api.post('/api/doctor/payments/stripe/connect/onboard', {});
  if (res.statusCode != 200) {
    throw Exception(_messageFromResponse(res));
  }
  return jsonDecode(res.body) as Map<String, dynamic>;
}

Future<Map<String, dynamic>> createSubscriptionCheckout(
  WidgetRef ref, {
  required String planCode,
}) async {
  final api = ref.read(apiClientProvider);
  final res = await api.post('/api/doctor/subscription/checkout', {
    'planCode': planCode,
    'gateway': 'STRIPE',
  });
  if (res.statusCode != 200) {
    throw Exception(_messageFromResponse(res));
  }
  return jsonDecode(res.body) as Map<String, dynamic>;
}

Future<void> patchSettings(WidgetRef ref, Map<String, dynamic> body) async {
  final api = ref.read(apiClientProvider);
  final res = await api.patch('/api/doctors/me/settings', body);
  if (res.statusCode != 200) throw Exception('Save failed: ${res.body}');
  await ref.refresh(profileAllProvider.future);
}

// Existing patch... (unchanged)

// ✅ NEW: upload doctor photo using multipart to /api/doctors/me/photo
Future<String?> uploadDoctorPhoto(
  WidgetRef ref,
  List<int> bytes,
  String filename,
) async {
  final api = ref.read(apiClientProvider);

  final file = http.MultipartFile.fromBytes('file', bytes, filename: filename);

  // Call our new multipart method
  final resp = await api.postMultipart(
    '/api/doctors/me/photo', // <-- matches your backend controller
    files: [file],
  );

  final body = await resp.stream.bytesToString();
  if (resp.statusCode >= 200 && resp.statusCode < 300) {
    final json = jsonDecode(body) as Map<String, dynamic>;
    final url = json['photoUrl'] as String?;
    // Refresh profile data so UI updates immediately
    await ref.refresh(profileAllProvider.future);
    // Force image widgets to refetch (avoid cached old photo)
    ref.read(photoCacheBusterProvider.notifier).state =
        DateTime.now().millisecondsSinceEpoch;
    return url;
  } else if (resp.statusCode == 401) {
    throw Exception(
      'Unauthorized (401): please ensure JWT is set on ApiClient.setToken(...)',
    );
  } else {
    throw Exception('Upload failed (${resp.statusCode}): $body');
  }
}

/// Change password (requires current password, calls /api/auth/reset-password)
Future<void> changePassword(
  WidgetRef ref, {
  required String currentPassword,
  required String newPassword,
}) async {
  final api = ref.read(apiClientProvider);
  final res = await api.post('/api/auth/reset-password', {
    'currentPassword': currentPassword,
    'newPassword': newPassword,
  });
  if (res.statusCode != 200) {
    String msg = 'Failed to change password';
    try {
      final body = jsonDecode(res.body) as Map<String, dynamic>?;
      msg = (body?['message'] ?? body?['error'] ?? msg).toString();
    } catch (_) {}
    throw Exception(msg);
  }
}

// ✅ NEW: upload certificate using multipart to /api/doctors/me/certificate
Future<String?> uploadCertificate(
  WidgetRef ref,
  List<int> bytes,
  String filename,
) async {
  final api = ref.read(apiClientProvider);

  final file = http.MultipartFile.fromBytes('file', bytes, filename: filename);

  final resp = await api.postMultipart(
    '/api/doctors/me/certificate',
    files: [file],
  );

  final body = await resp.stream.bytesToString();
  if (resp.statusCode >= 200 && resp.statusCode < 300) {
    final json = jsonDecode(body) as Map<String, dynamic>;
    final url = json['photoUrl'] as String?; // Backend returns 'photoUrl' field
    await ref.refresh(profileAllProvider.future);
    return url;
  } else if (resp.statusCode == 401) {
    throw Exception(
      'Unauthorized (401): please ensure JWT is set on ApiClient.setToken(...)',
    );
  } else {
    throw Exception('Upload failed (${resp.statusCode}): $body');
  }
}