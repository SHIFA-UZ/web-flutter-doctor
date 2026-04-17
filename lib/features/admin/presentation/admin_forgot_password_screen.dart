import 'dart:async';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/state/auth/auth_controller.dart';

enum _Step { email, otp, newPassword }

class AdminForgotPasswordScreen extends ConsumerStatefulWidget {
  const AdminForgotPasswordScreen({super.key});

  @override
  ConsumerState<AdminForgotPasswordScreen> createState() => _AdminForgotPasswordScreenState();
}

class _AdminForgotPasswordScreenState extends ConsumerState<AdminForgotPasswordScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailCtrl = TextEditingController();
  final _otpCtrl = TextEditingController();
  final _passwordCtrl = TextEditingController();
  final _confirmCtrl = TextEditingController();
  bool _obscurePassword = true;
  bool _obscureConfirm = true;
  bool _isLoading = false;
  _Step _step = _Step.email;
  int _resendCooldown = 0;
  Timer? _resendTimer;

  @override
  void dispose() {
    _emailCtrl.dispose();
    _otpCtrl.dispose();
    _passwordCtrl.dispose();
    _confirmCtrl.dispose();
    _resendTimer?.cancel();
    super.dispose();
  }

  void _startResendCooldown() {
    _resendCooldown = 60;
    _resendTimer?.cancel();
    _resendTimer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {
        if (_resendCooldown > 0) _resendCooldown--;
      });
      if (_resendCooldown <= 0) _resendTimer?.cancel();
    });
  }

  void _snack(String msg, {Color? color}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(msg), backgroundColor: color ?? AppColors.destructiveRed),
    );
  }

  Future<void> _onSendCode() async {
    if (!_formKey.currentState!.validate()) return;
    final email = _emailCtrl.text.trim().toLowerCase();
    setState(() => _isLoading = true);
    try {
      await ref.read(authProvider.notifier).sendForgotPasswordOtp(email);
      if (!mounted) return;
      _startResendCooldown();
      setState(() => _step = _Step.otp);
    } catch (e) {
      _snack(e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _onResend() async {
    if (_resendCooldown > 0 || _isLoading) return;
    setState(() => _isLoading = true);
    try {
      await ref.read(authProvider.notifier).sendForgotPasswordOtp(
            _emailCtrl.text.trim().toLowerCase(),
          );
      if (!mounted) return;
      _startResendCooldown();
      _snack(AppLocalizations.of(context)!.otpResent, color: Colors.green);
    } catch (e) {
      _snack(e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  void _onVerifyCode() {
    final code = _otpCtrl.text.replaceAll(RegExp(r'\D'), '');
    if (code.length != 6) {
      _snack(AppLocalizations.of(context)!.invalidOtp);
      return;
    }
    // Normalize the OTP field so submit uses the same verified value.
    _otpCtrl.text = code;
    setState(() => _step = _Step.newPassword);
  }

  Future<void> _onResetPassword() async {
    if (!_formKey.currentState!.validate()) return;
    final l10n = AppLocalizations.of(context)!;
    final code = _otpCtrl.text.replaceAll(RegExp(r'\D'), '');
    if (code.length != 6) {
      _snack(l10n.invalidOtp);
      return;
    }
    final newPassword = _passwordCtrl.text;
    if (newPassword != _confirmCtrl.text) {
      _snack(l10n.passwordMismatch);
      return;
    }
    setState(() => _isLoading = true);
    try {
      await ref.read(authProvider.notifier).resetPasswordWithEmailOtp(
            _emailCtrl.text.trim().toLowerCase(),
            code,
            newPassword,
          );
      if (!mounted) return;
      _snack(l10n.passwordUpdatedSuccessfully, color: Colors.green);
      Navigator.pop(context);
    } catch (e) {
      _snack(e is Exception ? e.toString().replaceFirst('Exception: ', '') : '$e');
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  static String? _validatePassword(String? value, AppLocalizations l10n) {
    final v = value ?? '';
    if (v.length < 8) return l10n.passwordTooWeak;
    if (!v.contains(RegExp(r'[A-Z]'))) return l10n.passwordTooWeak;
    if (!v.contains(RegExp(r'[0-9]'))) return l10n.passwordTooWeak;
    return null;
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
          icon: const Icon(Icons.arrow_back, color: Colors.black87),
          onPressed: () {
            if (_step == _Step.otp) {
              setState(() => _step = _Step.email);
            } else if (_step == _Step.newPassword) {
              setState(() => _step = _Step.otp);
            } else {
              Navigator.pop(context);
            }
          },
        ),
        title: Text(l10n.resetPassword, style: const TextStyle(color: Colors.black87)),
        centerTitle: true,
      ),
      body: SafeArea(
        child: Center(
          child: ConstrainedBox(
            constraints: const BoxConstraints(maxWidth: 420),
            child: Padding(
              padding: const EdgeInsets.all(32),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (_step == _Step.email) ..._buildEmailStep(l10n),
                    if (_step == _Step.otp) ..._buildOtpStep(l10n),
                    if (_step == _Step.newPassword) ..._buildPasswordStep(l10n),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _buildEmailStep(AppLocalizations l10n) => [
        Text(
          l10n.enterEmailForOtp,
          style: TextStyle(fontSize: 14, color: Colors.grey.shade700),
        ),
        const SizedBox(height: 24),
        TextFormField(
          controller: _emailCtrl,
          keyboardType: TextInputType.emailAddress,
          autofillHints: const [AutofillHints.email],
          textInputAction: TextInputAction.done,
          onFieldSubmitted: (_) => _onSendCode(),
          decoration: InputDecoration(
            labelText: l10n.email,
            prefixIcon: const Icon(Icons.email_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
          validator: (v) {
            final val = (v ?? '').trim();
            if (val.isEmpty) return l10n.enterEmail;
            if (!val.contains('@') || !val.contains('.')) return l10n.enterEmail;
            return null;
          },
        ),
        const SizedBox(height: 32),
        ShifaPrimaryButton(
          label: l10n.continue_,
          onPressed: _isLoading ? null : _onSendCode,
          isLoading: _isLoading,
          width: ButtonWidth.fill,
        ),
      ];

  List<Widget> _buildOtpStep(AppLocalizations l10n) => [
        Text(
          l10n.otpSentToEmail(_emailCtrl.text.trim()),
          style: TextStyle(fontSize: 14, color: Colors.grey.shade700),
        ),
        const SizedBox(height: 24),
        TextFormField(
          controller: _otpCtrl,
          keyboardType: TextInputType.number,
          inputFormatters: [
            FilteringTextInputFormatter.digitsOnly,
            LengthLimitingTextInputFormatter(6),
          ],
          textInputAction: TextInputAction.done,
          onFieldSubmitted: (_) => _onVerifyCode(),
          decoration: InputDecoration(
            labelText: l10n.enterOtp,
            prefixIcon: const Icon(Icons.pin_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 24),
        ShifaPrimaryButton(
          label: l10n.verify,
          onPressed: _isLoading ? null : _onVerifyCode,
          width: ButtonWidth.fill,
        ),
        const SizedBox(height: 12),
        TextButton(
          onPressed: (_isLoading || _resendCooldown > 0) ? null : _onResend,
          child: Text(
            _resendCooldown > 0 ? '${l10n.resendCode} ($_resendCooldown s)' : l10n.resendCode,
          ),
        ),
      ];

  List<Widget> _buildPasswordStep(AppLocalizations l10n) => [
        Text(
          l10n.passwordTooWeak,
          style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
        ),
        const SizedBox(height: 24),
        TextFormField(
          controller: _passwordCtrl,
          obscureText: _obscurePassword,
          decoration: InputDecoration(
            labelText: l10n.newPassword,
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              icon: Icon(_obscurePassword ? Icons.visibility_off : Icons.visibility),
              onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
            ),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
          validator: (v) => _validatePassword(v, l10n),
        ),
        const SizedBox(height: 16),
        TextFormField(
          controller: _confirmCtrl,
          obscureText: _obscureConfirm,
          decoration: InputDecoration(
            labelText: l10n.confirmPassword,
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              icon: Icon(_obscureConfirm ? Icons.visibility_off : Icons.visibility),
              onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
            ),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
          validator: (v) {
            if ((v ?? '').isEmpty) return l10n.confirmPassword;
            if (v != _passwordCtrl.text) return l10n.passwordMismatch;
            return null;
          },
        ),
        const SizedBox(height: 32),
        ShifaPrimaryButton(
          label: l10n.submit,
          onPressed: _isLoading ? null : _onResetPassword,
          isLoading: _isLoading,
          width: ButtonWidth.fill,
        ),
      ];
}
