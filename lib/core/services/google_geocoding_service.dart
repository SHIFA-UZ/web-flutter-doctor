import 'dart:convert';
import 'dart:async';
import 'package:http/http.dart' as http;
import 'package:flutter/foundation.dart';
import '../config/app_config.dart';

/// Service for Google Maps Geocoding API
/// 
/// To use this service, you need a Google Maps API key with Geocoding API enabled.
/// Get your API key from: https://console.cloud.google.com/google/maps-apis
/// 
/// The API key can be set:
/// 1. At build time: --dart-define=GOOGLE_MAPS_API_KEY=your_key_here
/// 2. Via backend: Set GOOGLE_MAPS_API_KEY in Railway, frontend will fetch it from /api/public/config
class GoogleGeocodingService {
  static String? _cachedApiKey;
  static bool _isFetching = false;
  
  /// Get API key - tries build-time first, then fetches from backend if needed
  static Future<String> _getApiKey() async {
    // First, try build-time key
    final buildTimeKey = AppConfig.googleMapsApiKey;
    if (buildTimeKey.isNotEmpty) {
      return buildTimeKey;
    }
    
    // If cached, return it
    if (_cachedApiKey != null && _cachedApiKey!.isNotEmpty) {
      return _cachedApiKey!;
    }
    
    // Fetch from backend if available
    if (!_isFetching && AppConfig.resolvedApiBaseUrl.isNotEmpty) {
      try {
        _isFetching = true;
        final configUrl = Uri.parse('${AppConfig.resolvedApiBaseUrl}/api/public/config');
        final response = await http.get(configUrl).timeout(
          const Duration(seconds: 5),
        );
        
        if (response.statusCode == 200) {
          final data = json.decode(response.body);
          final key = data['googleMapsApiKey'] as String?;
          if (key != null && key.isNotEmpty) {
            _cachedApiKey = key;
            debugPrint('Google Maps API key fetched from backend');
            return key;
          }
        }
      } catch (e) {
        debugPrint('Failed to fetch Google Maps API key from backend: $e');
      } finally {
        _isFetching = false;
      }
    }
    
    return '';
  }
  
  static const String _baseUrl = 'https://maps.googleapis.com/maps/api/geocode/json';

  /// Reverse geocoding: Convert coordinates to address
  static Future<Map<String, dynamic>?> reverseGeocode(
    double latitude,
    double longitude,
  ) async {
    try {
      final apiKey = await _getApiKey();
      if (apiKey.isEmpty) {
        debugPrint('WARNING: Google Maps API key not set. Geocoding will fail.');
        return null;
      }
      
      final url = Uri.parse(
        '$_baseUrl?latlng=$latitude,$longitude&key=$apiKey&language=en',
      );

      final response = await http.get(url);
      
      debugPrint('Google Geocoding API request: $url');
      debugPrint('Google Geocoding API response status: ${response.statusCode}');
      debugPrint('Google Geocoding API response body: ${response.body.substring(0, response.body.length > 500 ? 500 : response.body.length)}...');

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        
        if (data['status'] == 'OK' && data['results'] != null && data['results'].isNotEmpty) {
          debugPrint('Google Geocoding API success: Found ${data['results'].length} results');
          return _parseAddress(data['results'][0]);
        } else {
          debugPrint('Google Geocoding API error: ${data['status']} - ${data['error_message'] ?? 'Unknown error'}');
          return null;
        }
      } else {
        debugPrint('HTTP error: ${response.statusCode}');
        debugPrint('Response body: ${response.body}');
        return null;
      }
    } catch (e) {
      debugPrint('Reverse geocoding error: $e');
      return null;
    }
  }

  /// Forward geocoding: Convert address to coordinates
  static Future<Map<String, dynamic>?> geocode(String address) async {
    try {
      final apiKey = await _getApiKey();
      if (apiKey.isEmpty) {
        debugPrint('WARNING: Google Maps API key not set. Geocoding will fail.');
        return null;
      }
      
      final encodedAddress = Uri.encodeComponent(address);
      final url = Uri.parse(
        '$_baseUrl?address=$encodedAddress&key=$apiKey&language=en',
      );

      final response = await http.get(url);

      if (response.statusCode == 200) {
        final data = json.decode(response.body);
        
        if (data['status'] == 'OK' && data['results'] != null && data['results'].isNotEmpty) {
          final result = data['results'][0];
          final location = result['geometry']['location'];
          
          return {
            'latitude': location['lat'] as double,
            'longitude': location['lng'] as double,
            'formattedAddress': result['formatted_address'] as String?,
            'addressComponents': result['address_components'] as List?,
          };
        } else {
          debugPrint('Google Geocoding API error: ${data['status']} - ${data['error_message'] ?? 'Unknown error'}');
          return null;
        }
      } else {
        debugPrint('HTTP error: ${response.statusCode}');
        return null;
      }
    } catch (e) {
      debugPrint('Geocoding error: $e');
      return null;
    }
  }

  /// Parse Google Geocoding API response into a structured address
  /// Returns structured location data for doctor profiles
  static Map<String, dynamic> _parseAddress(Map<String, dynamic> result) {
    final addressComponents = result['address_components'] as List<dynamic>? ?? [];
    final formattedAddress = result['formatted_address'] as String? ?? '';
    
    String? streetNumber;
    String? route; // Street name
    String? sublocality; // District/Tuman
    String? locality; // City
    String? administrativeAreaLevel1; // Region/Viloyat
    String? administrativeAreaLevel2; // County
    String? country;
    String? postalCode;

    // Parse all address components
    for (var component in addressComponents) {
      final types = component['types'] as List<dynamic>? ?? [];
      final longName = component['long_name'] as String?;
      
      if (types.contains('street_number')) {
        streetNumber = longName;
      } else if (types.contains('route')) {
        route = longName;
      } else if (types.contains('sublocality') || types.contains('sublocality_level_1')) {
        sublocality = longName;
      } else if (types.contains('locality')) {
        locality = longName;
      } else if (types.contains('administrative_area_level_1')) {
        administrativeAreaLevel1 = longName;
      } else if (types.contains('administrative_area_level_2')) {
        administrativeAreaLevel2 = longName;
      } else if (types.contains('country')) {
        country = longName;
      } else if (types.contains('postal_code')) {
        postalCode = longName;
      }
    }

    // Build street address (editable field)
    final streetAddressParts = <String>[];
    if (streetNumber != null && streetNumber.isNotEmpty) {
      streetAddressParts.add(streetNumber);
    }
    if (route != null && route.isNotEmpty) {
      streetAddressParts.add(route);
    }
    final streetAddress = streetAddressParts.join(' ');

    return {
      // Structured location fields (readonly, auto-filled)
      'country': country ?? '',
      'region': administrativeAreaLevel1 ?? '', // Viloyat
      'district': sublocality ?? '', // Tuman
      'city': locality ?? '',
      'postalCode': postalCode ?? '',
      
      // Street address (editable)
      'streetAddress': streetAddress.isNotEmpty ? streetAddress : route ?? '',
      
      // Full formatted address for fallback
      'formattedAddress': formattedAddress,
      
      // Internal coordinates (hidden from UI)
      'streetNumber': streetNumber,
      'route': route,
      'sublocality': sublocality,
      'locality': locality,
      'administrativeAreaLevel1': administrativeAreaLevel1,
      'administrativeAreaLevel2': administrativeAreaLevel2,
    };
  }
}
