import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/email_login/email_reset_password_screen.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';

class EmailOtpScreen extends ConsumerStatefulWidget {
  const EmailOtpScreen({
    super.key,
    required this.email,
    this.forForgotPassword = false,
  });
  final String email;
  final bool forForgotPassword;

  @override
  ConsumerState<EmailOtpScreen> createState() => _EmailOtpScreenState();
}

class _EmailOtpScreenState extends ConsumerState<EmailOtpScreen> {
  final _otpCtrl = TextEditingController();
  bool _isLoading = false;
  int _resendCooldown = 0;
  Timer? _cooldownTimer;

  @override
  void initState() {
    super.initState();
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
      final auth = ref.read(authProvider.notifier);
      if (widget.forForgotPassword) {
        if (!mounted) return;
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(
            builder: (_) => EmailResetPasswordScreen(
              email: widget.email,
              otpCode: code,
            ),
          ),
        );
        return;
      }
      await auth.loginWithEmailOtp(widget.email, code);
      ref.invalidate(profileAllProvider);
      if (!mounted) return;
      Navigator.pushReplacementNamed(context, AppRoutes.shell);
    } catch (e) {
      if (!mounted) return;
      final msg = e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _onResend() async {
    if (_resendCooldown > 0) return;
    setState(() => _isLoading = true);
    try {
      final auth = ref.read(authProvider.notifier);
      if (widget.forForgotPassword) {
        await auth.sendForgotPasswordOtp(widget.email);
      } else {
        await auth.sendLoginOtp(widget.email);
      }
      if (!mounted) return;
      _startResendCooldown();
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.otpResent)),
      );
    } catch (e) {
      if (!mounted) return;
      final msg = e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e';
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
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
                    l10n.otpSentToEmail(widget.email),
                    style: TextStyle(color: Colors.grey.shade600, fontSize: 14),
                  ),
                  const SizedBox(height: 24),
                  TextFormField(
                    controller: _otpCtrl,
                    keyboardType: TextInputType.number,
                    maxLength: 6,
                    decoration: const InputDecoration(
                      hintText: '000000',
                      counterText: '',
                      border: OutlineInputBorder(),
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
                          _resendCooldown > 0
                              ? '${l10n.resendCode} ($_resendCooldown s)'
                              : l10n.resendCode,
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
