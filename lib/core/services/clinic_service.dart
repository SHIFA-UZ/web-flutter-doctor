import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/models/clinic_option_model.dart';

class ClinicService {
  static const String _apiPath = '/api/public/clinics';

  static String _getBaseUrl(WidgetRef? ref) {
    try {
      if (ref != null) {
        return ref.read(apiClientProvider).baseUrl;
      }
    } catch (e) {
      debugPrint('Could not get base URL from API client: $e');
    }
    return 'http://localhost:8080';
  }

  static Future<List<ClinicOption>?> fetchFromBackend({
    String? search,
    WidgetRef? ref,
  }) async {
    try {
      final queryParams = <String, String>{};
      if (search != null && search.isNotEmpty) {
        queryParams['search'] = search;
      }

      final baseUrl = _getBaseUrl(ref);
      final uri = Uri.parse('$baseUrl$_apiPath').replace(queryParameters: queryParams);

      final response = await http.get(
        uri,
        headers: {'Content-Type': 'application/json'},
      ).timeout(
        const Duration(seconds: 5),
        onTimeout: () {
          throw TimeoutException('Clinic request timed out');
        },
      );

      if (response.statusCode != 200) {
        debugPrint('Failed to fetch clinics: ${response.statusCode}');
        return null;
      }

      final parsed = jsonDecode(response.body);
      if (parsed is! List) return null;

      return parsed
          .whereType<Map>()
          .map((e) => ClinicOption.fromJson(Map<String, dynamic>.from(e)))
          .where((c) => c.name.isNotEmpty)
          .toList();
    } catch (e, stackTrace) {
      debugPrint('Error fetching clinics from backend: $e');
      debugPrint('Stack trace: $stackTrace');
      return null;
    }
  }

  static Future<List<ClinicOption>> getClinics({
    String? search,
    WidgetRef? ref,
  }) async {
    final clinics = await fetchFromBackend(search: search, ref: ref);
    return clinics ?? const [];
  }
}

class TimeoutException implements Exception {
  final String message;
  TimeoutException(this.message);

  @override
  String toString() => message;
}
