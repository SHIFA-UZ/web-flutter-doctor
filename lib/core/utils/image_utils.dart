// lib/core/utils/image_utils.dart
/// Utility functions for handling image URLs
import '../config/app_config.dart';

/// Normalizes a photo URL to an absolute URL.
/// If the URL is already absolute (starts with http:// or https://), returns it as-is.
/// Otherwise, prepends the API base URL to make it absolute.
String? normalizePhotoUrl(String? photoUrl) {
  if (photoUrl == null || photoUrl.trim().isEmpty) {
    return null;
  }
  
  final trimmed = photoUrl.trim();
  
  // If already absolute, return as-is
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) {
    return trimmed;
  }
  
  // Otherwise, prepend the API base URL (same as backend URL)
  final publicBaseUrl = AppConfig.apiBaseUrl;
  final path = trimmed.startsWith('/') ? trimmed.substring(1) : trimmed;
  return '$publicBaseUrl/$path';
}
