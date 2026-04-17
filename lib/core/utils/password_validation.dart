/// Password validation matching backend PasswordPolicy (min 8, max 128, upper, lower, digit, special).
/// Use for real-time hints and form validation so users see requirements as they type.
class PasswordValidation {
  static const int minLength = 8;
  static const int maxLength = 128;

  static final RegExp _hasUppercase = RegExp(r'[A-Z]');
  static final RegExp _hasLowercase = RegExp(r'[a-z]');
  static final RegExp _hasDigit = RegExp(r'[0-9]');
  static final RegExp _hasSpecial = RegExp(r'[^A-Za-z0-9\s]');

  /// Requirement key for l10n (translate(key)).
  static const String keyMinLength = 'passwordRequirementMinLength';
  static const String keyMaxLength = 'passwordRequirementMaxLength';
  static const String keyUppercase = 'passwordRequirementUppercase';
  static const String keyLowercase = 'passwordRequirementLowercase';
  static const String keyDigit = 'passwordRequirementDigit';
  static const String keySpecial = 'passwordRequirementSpecialChar';

  /// All requirement keys in display order.
  static const List<String> requirementKeys = [
    keyMinLength,
    keyUppercase,
    keyLowercase,
    keyDigit,
    keySpecial,
    keyMaxLength,
  ];

  /// Returns null if valid, or the first error message key for l10n if invalid.
  static String? validate(String? password) {
    if (password == null || password.isEmpty) return 'passwordRequired';
    if (password.length < minLength) return 'passwordTooShort';
    if (password.length > maxLength) return 'passwordTooLong';
    if (!_hasUppercase.hasMatch(password)) return 'passwordRequirementUppercase';
    if (!_hasLowercase.hasMatch(password)) return 'passwordRequirementLowercase';
    if (!_hasDigit.hasMatch(password)) return 'passwordRequirementDigit';
    if (!_hasSpecial.hasMatch(password)) return 'passwordRequirementSpecialChar';
    return null;
  }

  /// Per-requirement result for showing checkmarks as user types.
  static List<PasswordRequirementResult> getRequirementResults(String password) {
    return [
      PasswordRequirementResult(keyMinLength, password.length >= minLength),
      PasswordRequirementResult(keyUppercase, _hasUppercase.hasMatch(password)),
      PasswordRequirementResult(keyLowercase, _hasLowercase.hasMatch(password)),
      PasswordRequirementResult(keyDigit, _hasDigit.hasMatch(password)),
      PasswordRequirementResult(keySpecial, _hasSpecial.hasMatch(password)),
      PasswordRequirementResult(keyMaxLength, password.length <= maxLength),
    ];
  }
}

class PasswordRequirementResult {
  final String l10nKey;
  final bool satisfied;

  const PasswordRequirementResult(this.l10nKey, this.satisfied);
}
