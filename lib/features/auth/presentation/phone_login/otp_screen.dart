import 'dart:async';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/localization/locale_detection.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/providers/auth_providers.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/phone_login/reset_password_screen.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

class OtpScreen extends ConsumerStatefulWidget {
  const OtpScreen({
    super.key,
    required this.verificationId,
    required this.fullPhoneNumber,
    this.forForgotPassword = false,
    this.webConfirmationResult,
  });
  final String verificationId;
  final String fullPhoneNumber;
  final bool forForgotPassword;
  /// Set on web when using signInWithPhoneNumber (invisible reCAPTCHA) flow.
  final ConfirmationResult? webConfirmationResult;

  @override
  ConsumerState<OtpScreen> createState() => _OtpScreenState();
}

class _OtpScreenState extends ConsumerState<OtpScreen> {
  String _verificationId = '';
  final _otpCtrl = TextEditingController();
  bool _isLoading = false;
  int _resendCooldown = 0;
  Timer? _cooldownTimer;

  @override
  void initState() {
    super.initState();
    _verificationId = widget.verificationId;
    _startResendCooldown();
  }

  void _startResendCooldown() {
    _resendCooldown = 60;
    _cooldownTimer?.cancel();
    _cooldownTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {
        if (_resendCooldown > 0) _resendCooldown--;
      });
      if (_resendCooldown <= 0) _cooldownTimer?.cancel();
    });
  }

  @override
  void dispose() {
    _otpCtrl.dispose();
    _cooldownTimer?.cancel();
    super.dispose();
  }

  Future<void> _onSubmit() async {
    final code = _otpCtrl.text.trim().replaceAll(RegExp(r'\D'), '');
    if (code.length != 6) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.invalidOtp)),
      );
      return;
    }
    setState(() => _isLoading = true);
    try {
      final repo = ref.read(phoneAuthRepositoryProvider);
      final cred = await repo.signInWithPhoneCredential(
        verificationId: _verificationId,
        smsCode: code,
        webConfirmationResult: widget.webConfirmationResult,
      );
      if (cred.user == null) throw Exception(AppLocalizations.of(context)!.invalidOtp);
      final idToken = await repo.getIdToken(true);
      if (!mounted) return;
      if (widget.forForgotPassword) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => ResetPasswordScreen(idToken: idToken),
          ),
        );
        return;
      }
      await ref.read(authProvider.notifier).loginWithFirebaseToken(
            idToken,
            phoneCountryCode: countryCodeFromPhone(widget.fullPhoneNumber),
          );
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      if (!mounted) return;
      Navigator.pushReplacementNamed(context, AppRoutes.shell);
    } on FirebaseAuthException catch (e) {
      if (!mounted) return;
      String msg = e.message ?? '';
      if (e.code == 'auth/invalid-verification-code') msg = AppLocalizations.of(context)!.invalidOtp;
      if (msg.isEmpty) msg = AppLocalizations.of(context)!.invalidOtp;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    } catch (e) {
      if (!mounted) return;
      final msg = e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e';
      if (msg.contains('Access restricted') || msg.contains('blocked')) {
        await ref.read(phoneAuthRepositoryProvider).signOut();
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
      } else {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
      }
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _onResend() async {
    if (widget.webConfirmationResult != null) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.otpResendHint)),
      );
      return;
    }
    if (_resendCooldown > 0) return;
    final rateLimit = ref.read(otpRateLimitProvider);
    if (rateLimit != null && !rateLimit.canRequestOtp(widget.fullPhoneNumber)) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.tooManyRequests)),
      );
      return;
    }
    await rateLimit?.recordRequest(widget.fullPhoneNumber);
    final repo = ref.read(phoneAuthRepositoryProvider);
    repo.verifyPhoneNumber(
      fullPhoneNumber: widget.fullPhoneNumber,
      onCodeSent: (verificationId, _) {
        if (!mounted) return;
        setState(() => _verificationId = verificationId);
        _startResendCooldown();
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text(AppLocalizations.of(context)!.otpResent)),
        );
      },
      onVerificationFailed: (e) {
        if (!mounted) return;
        final msg = e.message ?? AppLocalizations.of(context)!.invalidOtp;
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
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
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Text(
                    l10n.enterOtp,
                    style: const TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    l10n.otpSent,
                    style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
                  ),
                  const SizedBox(height: 24),
                  TextFormField(
                    controller: _otpCtrl,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    decoration: InputDecoration(
                      hintText: '000000',
                      counterText: '',
                      border: const OutlineInputBorder(),
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                  const SizedBox(height: 16),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      TextButton(
                        onPressed: (_resendCooldown > 0 || _isLoading) ? null : _onResend,
                        child: Text(
                          _resendCooldown > 0 ? '${l10n.resendCode} ($_resendCooldown s)' : l10n.resendCode,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  ShifaPrimaryButton(
                    label: l10n.verify,
                    onPressed: _isLoading ? null : _onSubmit,
                    isLoading: _isLoading,
                    width: ButtonWidth.fill,
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
