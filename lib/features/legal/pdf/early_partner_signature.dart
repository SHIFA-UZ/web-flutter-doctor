// lib/features/legal/pdf/early_partner_signature.dart

/// Legal/display name for the partner block (typed above the signature line).
String buildPartnerSignatureText({
  String? firstName,
  String? lastName,
  String? fullName,
}) {
  final first = firstName?.trim() ?? '';
  final last = lastName?.trim() ?? '';
  if (first.isNotEmpty && last.isNotEmpty) {
    return '$first $last';
  }
  final full = fullName?.trim() ?? '';
  if (full.isNotEmpty) return full;
  return '';
}

/// Cursive ink line — abbreviated like a real signature (e.g. "Shohruhmirzo S.").
String buildPartnerSignatureInkText({
  String? firstName,
  String? lastName,
  String? fullName,
}) {
  final first = firstName?.trim() ?? '';
  final last = lastName?.trim() ?? '';
  if (first.isNotEmpty && last.isNotEmpty) {
    final initial = last[0].toUpperCase();
    return '$first $initial.';
  }

  final full = fullName?.trim() ?? '';
  if (full.isEmpty) return '';

  final parts = full.split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (parts.length >= 2) {
    return '${parts.first} ${parts.last[0].toUpperCase()}.';
  }
  return full;
}
