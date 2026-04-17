// lib/core/api/api_providers.dart
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'api_client.dart';
import '../util/admin_host.dart';
import '../../state/auth/auth_controller.dart';
import '../config/app_config.dart';

ApiClient _buildClient(ProviderRef<ApiClient> ref, ApiClientScope scope) {
  // Use environment-aware API URL from AppConfig
  // In development: http://localhost:8080
  // In production: Set via --dart-define=API_BASE_URL=https://api.yourdomain.com
  final client = ApiClient(AppConfig.apiBaseUrl, scope: scope);

  // Always log configuration for debugging (even in production, but only once)
  print('========================================');
  print('API Client Configuration:');
  print('  Base URL: ${AppConfig.apiBaseUrl}');
  print('  Environment: ${AppConfig.environment}');
  print('  Full API URL example: ${AppConfig.apiBaseUrl}/api/auth/login');
  print('  Scope: $scope');
  print('========================================');

  // Logout on 401 only. On admin host, clear admin token only so doctor session is unaffected.
  client.setUnauthorizedCallback(() {
    print('API Client: Received 401 Unauthorized - logging out user');
    ref.read(authProvider.notifier).logout(adminOnly: isAdminHost);
  });

  // Get current token and set it immediately (use read, not watch, to avoid rebuilds)
  final currentToken = ref.read(authTokenProvider);
  if (currentToken != null && currentToken.isNotEmpty) {
    client.setToken(currentToken);
    print('API Client Provider: Token set on initialization: ${currentToken.substring(0, 20)}...');
  } else {
    print('API Client Provider: No token available on initialization');
  }

  // Sync token from auth state when it changes
  ref.listen(authTokenProvider, (previous, next) {
    if (next != null && next.isNotEmpty) {
      client.setToken(next);
      print('API Client Provider: Token updated via listen: ${next.substring(0, 20)}...');
    } else {
      client.clearToken();
      print('API Client Provider: Token cleared via listen');
    }
  });

  return client;
}

/// Doctor-scoped API client (cannot call `/api/admin/**`).
final doctorApiClientProvider = Provider<ApiClient>((ref) {
  return _buildClient(ref, ApiClientScope.doctor);
});

/// Admin-scoped API client (can only call `/api/admin/**` and `/api/auth/**`).
final adminApiClientProvider = Provider<ApiClient>((ref) {
  return _buildClient(ref, ApiClientScope.admin);
});

/// Backward-compatible client that picks scope by host.
/// Prefer using `adminApiClientProvider` inside admin features and `doctorApiClientProvider` elsewhere.
final apiClientProvider = Provider<ApiClient>((ref) {
  return isAdminHost ? ref.watch(adminApiClientProvider) : ref.watch(doctorApiClientProvider);
});
