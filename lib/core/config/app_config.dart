import 'package:flutter/foundation.dart';

/// Application configuration that reads from environment variables at build time.
class AppConfig {
  /// Production backend URL (no trailing /api).
  static const String productionApiBaseUrl =
      'https://shifa-doc-backend-mvp-production.up.railway.app';

  /// Base URL for the API backend.
  ///
  /// Set via: `flutter build apk --dart-define=API_BASE_URL=https://api.example.com`
  /// In release mobile builds, falls back to [productionApiBaseUrl] when unset.
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: '',
  );

  /// Resolved API base URL for HTTP clients.
  static String get resolvedApiBaseUrl {
    if (apiBaseUrl.isNotEmpty) return apiBaseUrl;
    if (kReleaseMode && !kIsWeb) return productionApiBaseUrl;
    return 'http://localhost:8080';
  }

  /// Current environment name (development, staging, production).
  static const String environment = String.fromEnvironment(
    'ENVIRONMENT',
    defaultValue: 'development',
  );

  static bool get isProduction => environment == 'production';

  static bool get isDevelopment => environment == 'development';

  static bool get isStaging => environment == 'staging';

  static bool get enableDebugLogging => !isProduction;

  static const String googleMapsApiKey = String.fromEnvironment(
    'GOOGLE_MAPS_API_KEY',
    defaultValue: '',
  );

  static String get description {
    return 'Environment: $environment | API: $resolvedApiBaseUrl';
  }
}
