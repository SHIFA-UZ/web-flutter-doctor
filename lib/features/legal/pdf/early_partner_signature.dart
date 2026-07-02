// lib/features/legal/pdf/early_partner_signature.dart

/// Builds the display text for an auto-generated partner (doctor) signature.
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
