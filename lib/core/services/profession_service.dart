import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter/foundation.dart';
import 'package:shifa_doc_app_v1/core/models/profession_model.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

/// Service to fetch professions from backend API
/// Falls back to local data if backend is unavailable
class ProfessionService {
  static const String _apiPath = '/api/public/professions';
  
  /// Get base URL from API client provider (if available)
  /// Falls back to localhost if provider is not available
  static String _getBaseUrl(WidgetRef? ref) {
    try {
      if (ref != null) {
        final apiClient = ref.read(apiClientProvider);
        return apiClient.baseUrl;
      }
    } catch (e) {
      debugPrint('Could not get base URL from API client: $e');
    }
    // Fallback to localhost
    return 'http://localhost:8080';
  }
  
  /// Fetch professions from backend
  /// Returns null if backend is unavailable (will use fallback)
  static Future<List<ProfessionModel>?> fetchFromBackend({
    String? language,
    String? search,
    WidgetRef? ref,
  }) async {
    try {
      final queryParams = <String, String>{};
      if (language != null) queryParams['lang'] = language;
      if (search != null && search.isNotEmpty) queryParams['search'] = search;
      
      final baseUrl = _getBaseUrl(ref);
      final uri = Uri.parse('$baseUrl$_apiPath').replace(queryParameters: queryParams);
      
      debugPrint('Fetching professions from: $uri');
      
      final response = await http.get(
        uri,
        headers: {'Content-Type': 'application/json'},
      ).timeout(
        const Duration(seconds: 5),
        onTimeout: () {
          debugPrint('Profession API request timeout');
          throw TimeoutException('Request timeout');
        },
      );
      
      if (response.statusCode == 200) {
        final List<dynamic> data = json.decode(response.body);
        final professions = data.map((item) {
          return ProfessionModel(
            english: item['english'] as String,
            uzbek: item['uzbek'] as String,
          );
        }).toList();
        
        debugPrint('Successfully fetched ${professions.length} professions from backend');
        return professions;
      } else {
        debugPrint('Profession API returned status ${response.statusCode}');
        return null;
      }
    } catch (e, stackTrace) {
      debugPrint('Error fetching professions from backend: $e');
      debugPrint('Stack trace: $stackTrace');
      return null; // Return null to use fallback
    }
  }
  
  /// Get professions - tries backend first, falls back to local data
  static Future<List<ProfessionModel>> getProfessions({
    String? language,
    String? search,
    bool useBackend = true,
    WidgetRef? ref,
  }) async {
    if (useBackend) {
      final backendProfessions = await fetchFromBackend(
        language: language,
        search: search,
        ref: ref,
      );
      
      if (backendProfessions != null && backendProfessions.isNotEmpty) {
        return backendProfessions;
      }
    }
    
    // Fallback to local data
    debugPrint('Using local profession data as fallback');
    if (search != null && search.isNotEmpty) {
      return ProfessionData.search(search);
    }
    return ProfessionData.allProfessions;
  }
}

class TimeoutException implements Exception {
  final String message;
  TimeoutException(this.message);
  
  @override
  String toString() => message;
}
