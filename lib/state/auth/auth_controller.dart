import 'dart:async';
import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../core/api/api_providers.dart';
import '../../core/util/admin_host.dart';
import '../../core/util/jwt_utils.dart';
import '../../core/utils/secure_token_storage.dart';
import '../profile/profile_providers.dart';
import '../clinic/clinic_providers.dart';
import '../appointments/appointment_invalidation.dart';
import '../patients/patients_provider.dart';
import '../../core/providers/language_provider.dart';

// Holds the current JWT for the session, reactively.
final authTokenProvider = StateProvider<String?>((_) => null);

class AuthState {
  final bool isAuthenticated;
  final String? userId;
  final String? email;
  const AuthState({required this.isAuthenticated, this.userId, this.email});
  static const unauthenticated = AuthState(isAuthenticated: false);
}

class AuthController extends StateNotifier<AuthState> {
  final Ref ref;
  final SecureTokenStorage _tokenStorage = SecureTokenStorage();
  bool _storageMigrated = false;

  AuthController(this.ref) : super(AuthState.unauthenticated);

  Future<void> _ensureStorageMigrated() async {
    if (_storageMigrated) return;
    await _tokenStorage.migrateFromSharedPreferencesIfNeeded();
    _storageMigrated = true;
  }

  /// [app] When 'admin', requests an ADMIN-scoped JWT so admin endpoints accept the token.
  /// Admin tokens are stored separately from doctor/patient tokens to avoid 403 when opening admin panel with a doctor session.
  Future<void> login(String username, String password, {String? app}) async {
    final api = ref.read(apiClientProvider);
    final path = app != null && app.isNotEmpty
        ? '/api/auth/login?app=${Uri.encodeComponent(app)}'
        : '/api/auth/login';
    try {
      final res = await api.post(path, {
        'username': username,
        'password': password,
      });
      
      // Check if response is JSON (not HTML error page)
      final contentType = res.headers['content-type'] ?? '';
      final isJson = contentType.contains('application/json') || 
                     (contentType.isEmpty && (res.body.trim().startsWith('{') || res.body.trim().startsWith('[')));
      
      if (!isJson) {
        print('========================================');
        print('ERROR: API returned non-JSON response');
        print('  Status: ${res.statusCode}');
        print('  Content-Type: $contentType');
        print('  Response Length: ${res.body.length}');
        print('  Response Preview (first 1000 chars):');
        print('  ${res.body.substring(0, res.body.length > 1000 ? 1000 : res.body.length)}');
        print('========================================');
        
        // Check if it's an HTML error page
        if (res.body.contains('<!DOCTYPE') || res.body.contains('<html')) {
          throw Exception('Backend returned an HTML page instead of JSON. This usually means:\n'
              '1. The API endpoint does not exist (check URL: ${api.baseUrl}/api/auth/login)\n'
              '2. The backend is serving a default page\n'
              '3. There is a routing issue\n\n'
              'Status: ${res.statusCode}\n'
              'Please verify your backend URL is correct and the endpoint exists.');
        }
        
        throw Exception('Server returned non-JSON response. Status: ${res.statusCode}. Content-Type: $contentType');
      }
      
      if (res.statusCode == 200) {
        try {
          final responseBody = jsonDecode(res.body);
          final token = responseBody['token'] as String?;
          
          if (token == null || token.isEmpty) {
            throw Exception('Login response missing token');
          }
          
          api.setToken(token);
          // Also store in token provider for sync
          ref.read(authTokenProvider.notifier).state = token;
          state = AuthState(
            isAuthenticated: true,
            userId: 'doctor',
            email: username,
          );
          await _saveTokenToStorage(token, forAdmin: app == 'admin');

          // 🔁 Force a clean, token-aware refetch for profile after login:
          ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);

          if (app != 'admin') {
            await _applyRegionalLanguageDefault();
          }
          
          // Note: Inactivity timer is automatically started when authProvider becomes authenticated
          // via inactivityTimerProvider which watches authProvider
        } catch (e) {
          print('ERROR: Failed to parse login response: $e');
          print('Response body: ${res.body}');
          throw Exception('Invalid response from server. Please check your backend configuration.');
        }
      } else {
        // Try to parse error message from JSON response
        String errorMessage = 'Login failed';
        try {
          final errorBody = jsonDecode(res.body);
          errorMessage = errorBody['message'] ?? errorBody['error'] ?? res.body;
        } catch (_) {
          // If not JSON, use the raw body (might be HTML)
          errorMessage = res.body.length > 200 
              ? '${res.body.substring(0, 200)}...' 
              : res.body;
        }
        throw Exception('Login failed (${res.statusCode}): $errorMessage');
      }
    } catch (e) {
      // Re-throw with more context if it's a network error
      if (e.toString().contains('Failed host lookup') || 
          e.toString().contains('Connection refused') ||
          e.toString().contains('Network is unreachable')) {
        throw Exception('Cannot connect to backend. Please check your backend URL: ${api.baseUrl}');
      }
      rethrow;
    }
  }

  /// Admin panel step 1: validate password and send email OTP. Returns masked email hint from backend.
  Future<String?> requestAdminLoginOtp(String username, String password) async {
    final api = ref.read(adminApiClientProvider);
    try {
      final res = await api.post('/api/auth/admin/request-login-otp', {
        'username': username,
        'password': password,
      });
      final body = _tryDecodeJson(res.body);
      if (res.statusCode == 200) {
        return body?['emailHint'] as String?;
      }
      throw Exception(
        body?['message']?.toString() ?? body?['error']?.toString() ?? 'Request failed (${res.statusCode})',
      );
    } catch (e) {
      final msg = e.toString();
      if (msg.contains('Connection reset') ||
          msg.contains('Connection closed') ||
          msg.contains('Failed host lookup') ||
          msg.contains('timed out')) {
        throw Exception(
          'Cannot reach the Shifa API (${api.baseUrl}). The server may be restarting — try again in a minute.',
        );
      }
      rethrow;
    }
  }

  /// Admin panel step 2: verify email code and complete sign-in (stores admin token).
  Future<void> verifyAdminLoginOtp(String username, String password, String code) async {
    final api = ref.read(adminApiClientProvider);
    final res = await api.post('/api/auth/admin/verify-login-otp', {
      'username': username,
      'password': password,
      'code': code.trim(),
    });
    final body = _tryDecodeJson(res.body);
    if (res.statusCode == 200) {
      final token = body?['token'] as String?;
      if (token == null || token.isEmpty) throw Exception('Login response missing token');
      api.setToken(token);
      ref.read(authTokenProvider.notifier).state = token;
      state = AuthState(isAuthenticated: true, userId: 'admin', email: username);
      await _saveTokenToStorage(token, forAdmin: true);
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      return;
    }
    throw Exception(
      body?['message']?.toString() ?? body?['error']?.toString() ?? 'Verification failed (${res.statusCode})',
    );
  }

  /// Login with Firebase ID token (after phone OTP). Backend verifies token and checks DOCTOR role.
  /// On success sets JWT and state. On 403 (not doctor / blocked) throws with localized message key.
  Future<void> loginWithFirebaseToken(
    String idToken, {
    String? phoneCountryCode,
  }) async {
    final api = ref.read(apiClientProvider);
    final res = await api.postWithBearer('/api/auth/verify', <String, dynamic>{}, idToken);
    final contentType = res.headers['content-type'] ?? '';
    final isJson = contentType.contains('application/json') ||
        (res.body.trim().startsWith('{') || res.body.trim().startsWith('['));
    if (!isJson) {
      throw Exception('Server returned non-JSON. Status: ${res.statusCode}');
    }
    final responseBody = jsonDecode(res.body) as Map<String, dynamic>?;
    if (res.statusCode == 200) {
      final token = responseBody?['token'] as String?;
      if (token == null || token.isEmpty) throw Exception('Verify response missing token');
      api.setToken(token);
      ref.read(authTokenProvider.notifier).state = token;
      state = AuthState(isAuthenticated: true, userId: 'doctor', email: null);
      await _saveTokenToStorage(token);
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      await _applyRegionalLanguageDefault(phoneCountryCode: phoneCountryCode);
      return;
    }
    if (res.statusCode == 403) {
      final message = responseBody?['message'] ?? responseBody?['error'] ?? 'Access denied';
      throw Exception(message);
    }
    final message = responseBody?['message'] ?? responseBody?['error'] ?? res.body;
    throw Exception('Verify failed (${res.statusCode}): $message');
  }

  /// Send email OTP for doctor login.
  Future<void> sendLoginOtp(String email) async {
    final api = ref.read(apiClientProvider);
    final res = await api.post('/api/auth/send-login-otp', {'email': email});
    if (res.statusCode != 200) {
      final body = _tryDecodeJson(res.body);
      throw Exception(body?['message'] ?? 'Failed to send verification code');
    }
  }

  /// Login with email OTP code. Backend verifies and returns JWT.
  Future<void> loginWithEmailOtp(String email, String code) async {
    final api = ref.read(apiClientProvider);
    final res = await api.post('/api/auth/verify-email-otp', {
      'email': email,
      'code': code,
    });
    final body = _tryDecodeJson(res.body);
    if (res.statusCode == 200) {
      final token = body?['token'] as String?;
      if (token == null || token.isEmpty) throw Exception('Verify response missing token');
      api.setToken(token);
      ref.read(authTokenProvider.notifier).state = token;
      state = AuthState(isAuthenticated: true, userId: 'doctor', email: email);
      await _saveTokenToStorage(token);
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      await _applyRegionalLanguageDefault();
      return;
    }
    throw Exception(body?['message'] ?? 'Verification failed (${res.statusCode})');
  }

  /// Send forgot-password OTP (identifier can be email or phone).
  Future<void> sendForgotPasswordOtp(String identifier) async {
    final api = ref.read(apiClientProvider);
    final appType = isAdminHost ? 'admin' : 'doctor';
    final res = await api.post('/api/auth/send-forgot-password-otp', {
      'identifier': identifier,
      'app': appType,
    });
    if (res.statusCode != 200) {
      final body = _tryDecodeJson(res.body);
      throw Exception(body?['message'] ?? 'Failed to send verification code');
    }
  }

  /// Reset password using email OTP.
  Future<void> resetPasswordWithEmailOtp(String email, String code, String newPassword) async {
    final api = ref.read(apiClientProvider);
    final appType = isAdminHost ? 'admin' : 'doctor';
    final res = await api.post('/api/auth/forgot-password-reset', {
      'email': email,
      'emailOtp': code,
      'app': appType,
      'newPassword': newPassword,
    });
    final body = _tryDecodeJson(res.body);
    if (res.statusCode == 200) {
      final token = body?['token'] as String?;
      if (token == null || token.isEmpty) throw Exception('Reset response missing token');
      if (isAdminHost && _extractJwtRole(token) != 'ADMIN') {
        await _clearAdminTokenFromStorage();
        api.clearToken();
        ref.read(authTokenProvider.notifier).state = null;
        state = AuthState.unauthenticated;
        throw Exception('Reset succeeded but account is not an admin account for admin panel access.');
      }
      api.setToken(token);
      ref.read(authTokenProvider.notifier).state = token;
      final isAdminSession = isAdminHost;
      state = AuthState(
        isAuthenticated: true,
        userId: isAdminSession ? 'admin' : 'doctor',
        email: email,
      );
      await _saveTokenToStorage(token, forAdmin: isAdminSession);
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      return;
    }
    throw Exception(body?['message'] ?? 'Reset password failed (${res.statusCode})');
  }

  /// Forgot-password flow (legacy): send Firebase ID token + new password.
  Future<void> resetPasswordWithFirebaseToken(String idToken, String newPassword) async {
    final api = ref.read(apiClientProvider);
    final appType = isAdminHost ? 'admin' : 'doctor';
    final res = await api.post('/api/auth/forgot-password-reset', {
      'idToken': idToken,
      'app': appType,
      'newPassword': newPassword,
    });
    final isJson = (res.headers['content-type'] ?? '').contains('application/json') ||
        (res.body.trim().startsWith('{'));
    if (!isJson) throw Exception('Server returned non-JSON. Status: ${res.statusCode}');
    final responseBody = jsonDecode(res.body) as Map<String, dynamic>?;
    if (res.statusCode == 200) {
      final token = responseBody?['token'] as String?;
      if (token == null || token.isEmpty) throw Exception('Reset response missing token');
      api.setToken(token);
      ref.read(authTokenProvider.notifier).state = token;
      state = AuthState(isAuthenticated: true, userId: 'doctor', email: null);
      await _saveTokenToStorage(token);
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      return;
    }
    final message = responseBody?['message'] ?? responseBody?['error'] ?? res.body;
    throw Exception('Reset password failed (${res.statusCode}): $message');
  }

  Map<String, dynamic>? _tryDecodeJson(String body) {
    try {
      return jsonDecode(body) as Map<String, dynamic>?;
    } catch (_) {
      return null;
    }
  }

  Future<void> register({
    required String firstName,
    required String lastName,
    required String phone,
    String? email,
    required String password,
    required String key,
  }) async {
    final api = ref.read(apiClientProvider);
    final res = await api.post('/api/auth/register', {
      'firstName': firstName,
      'lastName': lastName,
      'phone': phone,
      'email': email,
      'password': password,
      'key': key,
    });
    if (res.statusCode == 200) {
      final token = jsonDecode(res.body)['token'] as String;
      api.setToken(token);
      // Also store in token provider for sync
      ref.read(authTokenProvider.notifier).state = token;
      state = AuthState(
        isAuthenticated: true,
        userId: 'doctor',
        email: email ?? phone,
      );
      await _saveTokenToStorage(token);

      // Note: Inactivity timer is automatically started when authProvider becomes authenticated
      // via inactivityTimerProvider which watches authProvider
    } else {
      throw Exception('Register failed: ${res.body}');
    }
  }

  void logout({bool adminOnly = false}) {
    // Note: Inactivity timer is automatically stopped when authProvider becomes unauthenticated
    // via inactivityTimerProvider which watches authProvider and returns null when not authenticated

    // Best-effort: clear doctor FCM token on backend so push stops after logout.
    // Never do this for admin-only logout (admin app must not call doctor endpoints).
    if (!adminOnly && !isAdminHost) {
      Future.microtask(() async {
        try {
          await ref.read(doctorApiClientProvider).put(
            '/api/doctors/me/fcm-token',
            <String, dynamic>{'fcmToken': ''},
          );
        } catch (_) {
          // Ignore failures; token will eventually expire on device side.
        }
      });
    }
    ref.read(apiClientProvider).clearToken();
    ref.read(authTokenProvider.notifier).state = null;
    _clearTokenFromStorage(adminOnly: adminOnly);
    // Clear doctor-scoped cached data so next login never sees stale data.
    ref.invalidate(profileAllProvider);
    ref.invalidate(meProfileProvider);
    ref.read(clinicWorkspaceKnownProvider.notifier).state = null;
    ref.invalidate(myClinicsProvider);
    unawaited(invalidateAppointmentRelatedProviders(ref));
    ref.invalidate(patientsProvider);
    ref.invalidate(patientByIdProvider);
    ref.invalidate(patientsForAssignmentProvider);
    state = AuthState.unauthenticated;
  }

  /// Save JWT to persistent storage (e.g. localStorage on web) so session survives refresh.
  /// [forAdmin] When true, saves to admin-specific key to keep admin and doctor tokens separate.
  Future<void> _saveTokenToStorage(String token, {bool forAdmin = false}) async {
    try {
      await _ensureStorageMigrated();
      await _tokenStorage.saveToken(token, forAdmin: forAdmin);
    } catch (e) {
      // Ignore storage errors; session will still work until refresh
    }
  }

  /// Clear JWT from persistent storage on logout.
  /// [adminOnly] When true (from admin panel logout), only clear admin token to avoid affecting doctor session.
  void _clearTokenFromStorage({bool adminOnly = false}) {
    _tokenStorage.clearToken(adminOnly: adminOnly);
  }

  /// Set session from a JWT token (e.g. after registration). Same effect as login/restoreSession.
  Future<void> setSessionFromToken(String token) async {
    ref.read(apiClientProvider).setToken(token);
    ref.read(authTokenProvider.notifier).state = token;
    state = AuthState(isAuthenticated: true, userId: 'doctor', email: null);
    await _saveTokenToStorage(token);
    ref.invalidate(profileAllProvider);
    ref.invalidate(meProfileProvider);
  }

  /// Restore session from persistent storage. Call on app start (e.g. after splash).
  /// Returns true if a valid token was restored and user is logged in.
  /// [forAdmin] When true (admin panel), only restores from admin token key so doctor token is not used.
  Future<bool> restoreSession({bool forAdmin = false}) async {
    try {
      await _ensureStorageMigrated();
      final token = await _tokenStorage.readToken(forAdmin: forAdmin);
      if (token == null || token.isEmpty) return false;
      if (isJwtExpired(token)) {
        await _tokenStorage.clearToken(adminOnly: forAdmin);
        if (!forAdmin) {
          await _tokenStorage.clearToken(adminOnly: false);
        }
        return false;
      }
      if (forAdmin && _extractJwtRole(token) != 'ADMIN') {
        await _clearAdminTokenFromStorage();
        ref.read(apiClientProvider).clearToken();
        ref.read(authTokenProvider.notifier).state = null;
        state = AuthState.unauthenticated;
        return false;
      }

      ref.read(apiClientProvider).setToken(token);
      ref.read(authTokenProvider.notifier).state = token;
      state = AuthState(
        isAuthenticated: true,
        userId: forAdmin ? 'admin' : 'doctor',
        email: null,
      );
      ref.invalidate(profileAllProvider);
      ref.invalidate(meProfileProvider);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<void> _clearAdminTokenFromStorage() async {
    try {
      await _tokenStorage.clearToken(adminOnly: true);
    } catch (_) {
      // Best effort.
    }
  }

  String? _extractJwtRole(String token) {
    try {
      final parts = token.split('.');
      if (parts.length < 2) return null;
      final payload = utf8.decode(base64Url.decode(base64Url.normalize(parts[1])));
      final json = jsonDecode(payload) as Map<String, dynamic>;
      final role = json['role']?.toString();
      return role?.toUpperCase();
    } catch (_) {
      return null;
    }
  }

  Future<void> _applyRegionalLanguageDefault({String? phoneCountryCode}) async {
    await ref.read(languageProvider.notifier).applyRegionalDefaultIfUnset(
          phoneCountryCode: phoneCountryCode,
        );
  }
}

final authProvider = StateNotifierProvider<AuthController, AuthState>(
  (ref) => AuthController(ref),
);
