import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/providers/auth_providers.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/phone_login/otp_screen.dart';

/// Country code entry for dropdown.
class CountryCode {
  final String code;
  final String label;
  CountryCode(this.code, this.label);
}

final _countryCodes = [
  CountryCode('+998', 'UZ'),
  CountryCode('+1', 'US'),
  CountryCode('+44', 'UK'),
  CountryCode('+7', 'RU'),
  CountryCode('+49', 'DE'),
  CountryCode('+33', 'FR'),
  CountryCode('+90', 'TR'),
  CountryCode('+91', 'IN'),
  CountryCode('+86', 'CN'),
  CountryCode('+82', 'KR'),
  CountryCode('+81', 'JP'),
  CountryCode('+971', 'AE'),
  CountryCode('+966', 'SA'),
  CountryCode('+20', 'EG'),
  CountryCode('+234', 'NG'),
  CountryCode('+27', 'ZA'),
  CountryCode('+61', 'AU'),
  CountryCode('+55', 'BR'),
  CountryCode('+52', 'MX'),
];

class PhoneInputScreen extends ConsumerStatefulWidget {
  const PhoneInputScreen({
    super.key,
    this.forForgotPassword = false,
  });
  final bool forForgotPassword;

  @override
  ConsumerState<PhoneInputScreen> createState() => _PhoneInputScreenState();
}

class _PhoneInputScreenState extends ConsumerState<PhoneInputScreen> {
  final _formKey = GlobalKey<FormState>();
  final _phoneCtrl = TextEditingController();
  String _selectedCode = '+998';
  bool _isLoading = false;

  @override
  void dispose() {
    _phoneCtrl.dispose();
    super.dispose();
  }

  String get _fullPhone => '$_selectedCode${_phoneCtrl.text.trim().replaceAll(RegExp(r'\D'), '')}';

  Future<void> _onContinue() async {
    if (!_formKey.currentState!.validate()) return;
    final fullPhone = _fullPhone;
    if (fullPhone.length < 10) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.enterPhoneNumber)),
      );
      return;
    }
    final rateLimit = ref.read(otpRateLimitProvider);
    if (rateLimit != null && !rateLimit.canRequestOtp(fullPhone)) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.tooManyRequests)),
      );
      return;
    }
    setState(() => _isLoading = true);
    await rateLimit?.recordRequest(fullPhone);
    final repo = ref.read(phoneAuthRepositoryProvider);
    ConfirmationResult? webResult;
    repo.verifyPhoneNumber(
      fullPhoneNumber: fullPhone,
      onCodeSent: (verificationId, _) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        Navigator.push(
          context,
          MaterialPageRoute(
            builder: (_) => OtpScreen(
              verificationId: verificationId,
              fullPhoneNumber: fullPhone,
              forForgotPassword: widget.forForgotPassword,
              webConfirmationResult: webResult,
            ),
          ),
        );
      },
      onWebCodeSent: kIsWeb ? (result) => webResult = result : null,
      onVerificationFailed: (e) {
        if (!mounted) return;
        setState(() => _isLoading = false);
        String msg = e.message ?? '';
        if (e.code == 'auth/unauthorized-domain') {
          msg = 'This domain is not authorized. Add it in Firebase Console → Authentication → Settings → Authorized domains.';
        } else if (e.code == 'auth/too-many-requests' ||
            (msg.isNotEmpty && msg.toLowerCase().contains('blocked'))) {
          msg = 'Too many attempts. Please try again later or use a different network.';
        } else if (msg.isEmpty) {
          msg = AppLocalizations.of(context)!.invalidOtp;
        }
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
      },
      onVerificationCompleted: (_) {
        if (!mounted) return;
        setState(() => _isLoading = false);
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      backgroundColor: Colors.white,
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        elevation: 0,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back, color: Colors.black),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 520),
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Text(
                      widget.forForgotPassword ? l10n.resetPassword : l10n.signInWithPhone,
                      style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      l10n.enterPhoneNumber,
                      style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
                    ),
                    const SizedBox(height: 24),
                    Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        SizedBox(
                          width: 100,
                          child: DropdownButtonFormField<String>(
                            value: _selectedCode,
                            decoration: const InputDecoration(
                              border: OutlineInputBorder(),
                              contentPadding: EdgeInsets.symmetric(horizontal: 12, vertical: 14),
                            ),
                            items: _countryCodes
                                .map((c) => DropdownMenuItem(value: c.code, child: Text(c.code)))
                                .toList(),
                            onChanged: (v) => setState(() => _selectedCode = v ?? '+998'),
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: TextFormField(
                            controller: _phoneCtrl,
                            keyboardType: TextInputType.phone,
                            decoration: InputDecoration(
                              hintText: l10n.enterPhoneNumber,
                              border: const OutlineInputBorder(),
                            ),
                            validator: (v) {
                              final digits = (v ?? '').replaceAll(RegExp(r'\D'), '');
                              if (digits.length < 7) return l10n.enterPhoneNumber;
                              return null;
                            },
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 24),
                    ShifaPrimaryButton(
                      label: l10n.continue_,
                      onPressed: _isLoading ? null : _onContinue,
                      isLoading: _isLoading,
                      width: ButtonWidth.fill,
                    ),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
