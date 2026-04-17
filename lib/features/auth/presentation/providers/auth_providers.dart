import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:shifa_doc_app_v1/features/auth/data/otp_rate_limit_service.dart';
import 'package:shifa_doc_app_v1/features/auth/data/phone_auth_repository.dart';

final sharedPreferencesProvider = FutureProvider<SharedPreferences>((ref) async {
  return SharedPreferences.getInstance();
});

final otpRateLimitProvider = Provider<OtpRateLimitService?>((ref) {
  final prefsAsync = ref.watch(sharedPreferencesProvider);
  return prefsAsync.when(
    data: (prefs) => OtpRateLimitService(prefs),
    loading: () => null,
    error: (_, __) => null,
  );
});

final phoneAuthRepositoryProvider = Provider<PhoneAuthRepository>((ref) {
  return PhoneAuthRepository();
});
