import 'dart:async';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/providers/auth_providers.dart';

/// OTP dialog used by doctor registration flow.
///
/// - 60s code expiration enforced in UI
/// - 60s resend cooldown (plus local rate limit guard)
class RegistrationOtpDialog extends ConsumerStatefulWidget {
  const RegistrationOtpDialog({
    super.key,
    required this.fullPhoneNumber,
    required this.verificationId,
    this.resendToken,
    this.webConfirmationResult,
  });

  final String fullPhoneNumber;
  final String verificationId;
  final int? resendToken;

  /// Set on web when using signInWithPhoneNumber (invisible reCAPTCHA) flow.
  final ConfirmationResult? webConfirmationResult;

  @override
  ConsumerState<RegistrationOtpDialog> createState() =>
      _RegistrationOtpDialogState();
}

class _RegistrationOtpDialogState extends ConsumerState<RegistrationOtpDialog> {
  final _otpCtrl = TextEditingController();
  bool _isLoading = false;

  String _verificationId = '';
  int? _resendToken;

  int _resendCooldown = 0;
  int _expiresIn = 0;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _verificationId = widget.verificationId;
    _resendToken = widget.resendToken;
    _startTimers();
  }

  @override
  void dispose() {
    _otpCtrl.dispose();
    _timer?.cancel();
    super.dispose();
  }

  void _startTimers() {
    _resendCooldown = 60;
    _expiresIn = 60;
    _timer?.cancel();
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {
        if (_resendCooldown > 0) _resendCooldown--;
        if (_expiresIn > 0) _expiresIn--;
      });
      if (_resendCooldown <= 0 && _expiresIn <= 0) _timer?.cancel();
    });
  }

  String _mmss(int seconds) {
    final m = (seconds ~/ 60).toString();
    final s = (seconds % 60).toString().padLeft(2, '0');
    return '$m:$s';
  }

  void _snack(String msg) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));
  }

  Future<void> _onSubmit() async {
    final l10n = AppLocalizations.of(context)!;
    final code = _otpCtrl.text.trim().replaceAll(RegExp(r'\D'), '');
    if (code.length != 6) {
      _snack(l10n.invalidOtp);
      return;
    }
    if (_expiresIn <= 0) {
      _snack(l10n.invalidOtp);
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
      if (cred.user == null) throw Exception(l10n.invalidOtp);
      await repo.signOut(); // do not keep Firebase session after OTP
      if (!mounted) return;
      Navigator.of(context).pop(true);
    } on FirebaseAuthException catch (e) {
      if (!mounted) return;
      String msg = e.message ?? '';
      if (e.code == 'auth/invalid-verification-code') msg = l10n.invalidOtp;
      if (e.code == 'auth/code-expired') msg = l10n.invalidOtp;
      if (msg.isEmpty) msg = l10n.invalidOtp;
      _snack(msg);
    } catch (e) {
      if (!mounted) return;
      _snack(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  Future<void> _onResend() async {
    final l10n = AppLocalizations.of(context)!;

    if (_isLoading) return;
    if (_resendCooldown > 0) return;

    if (widget.webConfirmationResult != null) {
      _snack('To get a new code, close this dialog and try again.');
      return;
    }

    final rateLimit = ref.read(otpRateLimitProvider);
    if (rateLimit != null && !rateLimit.canRequestOtp(widget.fullPhoneNumber)) {
      _snack(l10n.tooManyRequests);
      return;
    }
    await rateLimit?.recordRequest(widget.fullPhoneNumber);

    setState(() => _isLoading = true);
    try {
      final repo = ref.read(phoneAuthRepositoryProvider);
      final completer = Completer<void>();

      repo.verifyPhoneNumber(
        fullPhoneNumber: widget.fullPhoneNumber,
        forceResendingToken: _resendToken,
        onCodeSent: (verificationId, resendToken) {
          if (!mounted) return;
          setState(() {
            _verificationId = verificationId;
            _resendToken = resendToken;
          });
          _startTimers();
          _snack(l10n.otpResent);
          if (!completer.isCompleted) completer.complete();
        },
        onVerificationFailed: (e) {
          if (!completer.isCompleted) completer.completeError(e);
        },
      );

      // Ensure we waited at least until codeSent/failed callback.
      await completer.future;
    } on FirebaseAuthException catch (e) {
      if (!mounted) return;
      _snack(e.message ?? l10n.invalidOtp);
    } catch (e) {
      if (!mounted) return;
      _snack(e.toString().replaceFirst('Exception: ', ''));
    } finally {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return AlertDialog(
      title: Text(l10n.enterOtp),
      content: SizedBox(
        width: 420,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              l10n.otpSent,
              style: TextStyle(color: Colors.grey.shade600, fontSize: 13),
            ),
            const SizedBox(height: 16),
            TextField(
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
            const SizedBox(height: 8),
            Align(
              alignment: Alignment.center,
              child: TextButton(
                onPressed: (_resendCooldown > 0 || _isLoading) ? null : _onResend,
                child: Text(
                  _resendCooldown > 0
                      ? l10n.resendCodeIn(_mmss(_resendCooldown))
                      : l10n.resendCode,
                ),
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: _isLoading ? null : () => Navigator.of(context).pop(false),
          child: Text(l10n.cancel),
        ),
        ShifaPrimaryButton(
          label: l10n.verify,
          onPressed: _isLoading ? null : _onSubmit,
          isLoading: _isLoading,
        ),
      ],
    );
  }
}

