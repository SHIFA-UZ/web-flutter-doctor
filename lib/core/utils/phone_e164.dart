/// Very small helper to normalize phone numbers to E.164-ish format.
///
/// Notes:
/// - Firebase phone auth expects E.164 (e.g. +998901234567).
/// - Registration flow currently stores phone as user-entered string.
/// - We default to Uzbekistan (+998) when number is not prefixed with '+'.
String toE164Phone(String input, {String defaultCountryCode = '+998'}) {
  final raw = input.trim();
  if (raw.isEmpty) return raw;

  // Keep leading '+' if present, but strip everything else to digits.
  final hasPlus = raw.startsWith('+');
  final digits = raw.replaceAll(RegExp(r'\D'), '');

  if (digits.isEmpty) return raw;

  if (hasPlus) return '+$digits';

  final ccDigits = defaultCountryCode.replaceAll(RegExp(r'\D'), '');

  // If user already typed country code digits (e.g. 998...), keep it.
  if (digits.startsWith(ccDigits)) return '+$digits';

  // If user typed a local number with leading 0, strip it (e.g. 0xx...).
  final normalizedLocal = digits.startsWith('0') ? digits.substring(1) : digits;

  return '+$ccDigits$normalizedLocal';
}

