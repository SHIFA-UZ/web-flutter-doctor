import 'dart:async';
import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/utils/phone_e164.dart';
import 'package:shifa_doc_app_v1/features/auth/data/phone_auth_repository.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/create_account/registration_otp_dialog.dart';
import 'package:shifa_doc_app_v1/features/auth/presentation/providers/auth_providers.dart';

class _OtpInitResult {
  final String verificationId;
  final int? resendToken;
  final ConfirmationResult? webConfirmationResult;
  const _OtpInitResult({
    required this.verificationId,
    required this.resendToken,
    required this.webConfirmationResult,
  });
}

/// Sends OTP to [rawPhone] and shows a dialog to enter it.
///
/// Returns true only when OTP is successfully verified.
Future<bool> runRegistrationPhoneVerification({
  required BuildContext context,
  required WidgetRef ref,
  required String rawPhone,
}) async {
  final l10n = AppLocalizations.of(context)!;
  final fullPhoneNumber = toE164Phone(rawPhone);

  final rateLimit = ref.read(otpRateLimitProvider);
  if (rateLimit != null && !rateLimit.canRequestOtp(fullPhoneNumber)) {
    throw Exception(l10n.tooManyRequests);
  }
  await rateLimit?.recordRequest(fullPhoneNumber);

  final repo = ref.read(phoneAuthRepositoryProvider);
  final init = await _sendOtp(repo, fullPhoneNumber);

  final verified = await showDialog<bool>(
        context: context,
        barrierDismissible: false,
        builder: (_) => RegistrationOtpDialog(
          fullPhoneNumber: fullPhoneNumber,
          verificationId: init.verificationId,
          resendToken: init.resendToken,
          webConfirmationResult: init.webConfirmationResult,
        ),
      ) ??
      false;

  return verified;
}

Future<_OtpInitResult> _sendOtp(PhoneAuthRepository repo, String fullPhone) async {
  final completer = Completer<_OtpInitResult>();

  repo.verifyPhoneNumber(
    fullPhoneNumber: fullPhone,
    onCodeSent: (verificationId, resendToken) {
      if (completer.isCompleted) return;
      completer.complete(
        _OtpInitResult(
          verificationId: verificationId,
          resendToken: resendToken,
          webConfirmationResult: null,
        ),
      );
    },
    onWebCodeSent: (webConfirmationResult) {
      if (completer.isCompleted) return;
      completer.complete(
        _OtpInitResult(
          verificationId: '',
          resendToken: null,
          webConfirmationResult: webConfirmationResult,
        ),
      );
    },
    onVerificationFailed: (e) {
      if (completer.isCompleted) return;
      completer.completeError(e);
    },
  );

  return completer.future;
}

