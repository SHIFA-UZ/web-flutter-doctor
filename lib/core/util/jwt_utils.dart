import 'dart:convert';

/// Reads JWT `exp` (seconds since epoch). Returns null when missing or unparsable.
DateTime? jwtExpirationFromToken(String? token) {
  if (token == null || token.isEmpty) return null;
  try {
    final parts = token.split('.');
    if (parts.length < 2) return null;
    final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
    final json = jsonDecode(payload) as Map<String, dynamic>;
    final exp = json['exp'];
    if (exp is int) {
      return DateTime.fromMillisecondsSinceEpoch(exp * 1000, isUtc: true).toLocal();
    }
    if (exp is num) {
      return DateTime.fromMillisecondsSinceEpoch(exp.toInt() * 1000, isUtc: true).toLocal();
    }
    return null;
  } catch (_) {
    return null;
  }
}

/// Reads JWT `role` claim. Returns null when missing or unparsable.
String? jwtRoleFromToken(String? token) {
  if (token == null || token.isEmpty) return null;
  try {
    final parts = token.split('.');
    if (parts.length < 2) return null;
    final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
    final json = jsonDecode(payload) as Map<String, dynamic>;
    return json['role']?.toString();
  } catch (_) {
    return null;
  }
}

/// True when the JWT `exp` claim is in the past (with optional clock skew).
bool isJwtExpired(String? token, {Duration grace = const Duration(seconds: 30)}) {
  final exp = jwtExpirationFromToken(token);
  if (exp == null) return false;
  return DateTime.now().isAfter(exp.add(grace));
}
