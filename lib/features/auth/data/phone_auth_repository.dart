import 'package:firebase_auth/firebase_auth.dart';
import 'package:flutter/foundation.dart' show kDebugMode, kIsWeb;

/// Result of requesting OTP: either success with verificationId (mobile) or ConfirmationResult (web), or failure.
sealed class OtpRequestResult {}

class OtpRequestSuccess implements OtpRequestResult {
  final String? verificationId;
  final ConfirmationResult? webConfirmationResult;
  OtpRequestSuccess({this.verificationId, this.webConfirmationResult})
      : assert(verificationId != null || webConfirmationResult != null);
}

class OtpRequestFailure implements OtpRequestResult {
  final String message;
  OtpRequestFailure(this.message);
}

/// Handles Firebase Phone Auth. On web uses signInWithPhoneNumber with invisible reCAPTCHA (no container).
/// See: https://firebase.google.com/docs/auth/web/phone-auth
class PhoneAuthRepository {
  final FirebaseAuth _auth = FirebaseAuth.instance;

  /// Request OTP for [fullPhoneNumber]. On web: invisible reCAPTCHA + signInWithPhoneNumber; on mobile: verifyPhoneNumber.
  /// Pass [forceResendingToken] from a previous onCodeSent callback to force sending a new SMS (resend).
  Future<void> verifyPhoneNumber({
    required String fullPhoneNumber,
    int? forceResendingToken,
    required void Function(String verificationId, int? resendToken) onCodeSent,
    void Function(ConfirmationResult webConfirmationResult)? onWebCodeSent,
    void Function(PhoneAuthCredential credential)? onVerificationCompleted,
    void Function(FirebaseAuthException e)? onVerificationFailed,
    void Function(String verificationId)? onCodeAutoRetrievalTimeout,
  }) async {
    if (kIsWeb) {
      try {
        // Helps local/manual testing on web with Firebase "test phone numbers".
        // Do NOT rely on this for real numbers; production requires a valid reCAPTCHA verifier.
        if (kDebugMode) {
          await _auth.setSettings(appVerificationDisabledForTesting: true);
        }

        // Let firebase_auth create & manage the RecaptchaVerifier internally.
        // This avoids relying on any private delegate fields which can change between versions.
        final result = await _auth.signInWithPhoneNumber(fullPhoneNumber);
        onWebCodeSent?.call(result);
        onCodeSent('', null);
      } catch (e) {
        onVerificationFailed?.call(
          e is FirebaseAuthException ? e : FirebaseAuthException(code: 'unknown', message: e.toString()),
        );
      }
      return;
    }
    await _auth.verifyPhoneNumber(
      phoneNumber: fullPhoneNumber,
      forceResendingToken: forceResendingToken,
      verificationCompleted: (credential) {
        onVerificationCompleted?.call(credential);
      },
      verificationFailed: (e) {
        onVerificationFailed?.call(e);
      },
      codeSent: (verificationId, resendToken) {
        onCodeSent(verificationId, resendToken);
      },
      codeAutoRetrievalTimeout: (verificationId) {
        onCodeAutoRetrievalTimeout?.call(verificationId);
      },
      timeout: const Duration(seconds: 60),
    );
  }

  /// Sign in with the SMS code. On web pass [webConfirmationResult]; on mobile use [verificationId].
  Future<UserCredential> signInWithPhoneCredential({
    required String verificationId,
    required String smsCode,
    ConfirmationResult? webConfirmationResult,
  }) async {
    if (kIsWeb && webConfirmationResult != null) {
      return webConfirmationResult.confirm(smsCode);
    }
    final credential = PhoneAuthProvider.credential(
      verificationId: verificationId,
      smsCode: smsCode,
    );
    return _auth.signInWithCredential(credential);
  }

  /// Get the current Firebase ID token (force refresh). Call after sign-in.
  /// Throws if not signed in.
  Future<String> getIdToken(bool forceRefresh) async {
    final user = _auth.currentUser;
    if (user == null) throw Exception('Not signed in with Firebase');
    final token = await user.getIdToken(forceRefresh);
    if (token == null) throw Exception('Failed to get ID token');
    return token;
  }

  /// Sign out from Firebase (e.g. when backend rejects doctor role).
  Future<void> signOut() async {
    await _auth.signOut();
  }
}
