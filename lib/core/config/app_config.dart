/// Application configuration that reads from environment variables
/// 
/// This allows the app to work in different environments (dev, staging, production)
/// without code changes. Configuration is set at build time using --dart-define flags.
class AppConfig {
  /// Base URL for the API backend
  /// 
  /// Set via: flutter build web --dart-define=API_BASE_URL=https://api.yourdomain.com
  /// Defaults to localhost for development
  static const String apiBaseUrl = String.fromEnvironment(
    'API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  /// Current environment name (development, staging, production)
  /// 
  /// Set via: flutter build web --dart-define=ENVIRONMENT=production
  static const String environment = String.fromEnvironment(
    'ENVIRONMENT',
    defaultValue: 'development',
  );

  /// Whether the app is running in production mode
  static bool get isProduction => environment == 'production';

  /// Whether the app is running in development mode
  static bool get isDevelopment => environment == 'development';

  /// Whether the app is running in staging mode
  static bool get isStaging => environment == 'staging';

  /// Enable debug logging (disabled in production)
  static bool get enableDebugLogging => !isProduction;

  /// Google Maps API key for geocoding services
  /// 
  /// Set via: flutter build web --dart-define=GOOGLE_MAPS_API_KEY=your_key_here
  /// Defaults to empty string (will cause geocoding to fail if not set)
  static const String googleMapsApiKey = String.fromEnvironment(
    'GOOGLE_MAPS_API_KEY',
    defaultValue: '',
  );

  /// Get a human-readable description of the current configuration
  static String get description {
    return 'Environment: $environment | API: $apiBaseUrl';
  }
}
