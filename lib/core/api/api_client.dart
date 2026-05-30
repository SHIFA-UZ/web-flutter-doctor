import 'dart:convert';
import 'package:http/http.dart' as http;
import '../util/jwt_utils.dart';

/// Callback function type for handling 401 unauthorized responses
typedef UnauthorizedCallback = void Function();

enum ApiClientScope { doctor, admin }

class ApiClient {
  final String baseUrl;
  final ApiClientScope scope;
  String? _jwt;
  UnauthorizedCallback? _onUnauthorized;

  ApiClient(this.baseUrl, {this.scope = ApiClientScope.doctor});

  void setToken(String token) => _jwt = token;
  void clearToken() => _jwt = null;
  String? getAuthToken() => _jwt;
  
  /// Set callback to handle 401 unauthorized responses
  void setUnauthorizedCallback(UnauthorizedCallback? callback) {
    _onUnauthorized = callback;
  }
  
  /// Proactively logout when the stored JWT is past its `exp` claim.
  /// Returns false when the request should not proceed.
  bool _ensureTokenStillValid({String? path}) {
    if (_jwt == null || _jwt!.isEmpty) return true;
    if (!isJwtExpired(_jwt!)) return true;
    print('API Client: JWT expired${path != null ? " before $path" : ""} - triggering logout');
    _onUnauthorized?.call();
    return false;
  }

  /// Check response and trigger unauthorized callback if needed.
  /// Do not logout when we never sent a token (avoids 403 loop when shell loads before token is set).
  void _checkUnauthorized(http.Response response, {String? path}) {
    if (_onUnauthorized == null) return;

    final statusCode = response.statusCode;
    if (statusCode != 401 && statusCode != 403) return;

    // Only trigger logout if we had a token (i.e. we sent Authorization and it was rejected).
    // When token is null we must not logout, or we get: request without token → 403 → logout → more requests without token.
    if (_jwt == null || _jwt!.isEmpty) {
      if (path != null) {
        print('API Client: $statusCode on $path - no token sent, skipping logout');
      }
      return;
    }

    if (statusCode == 401 || _shouldLogoutOn403(response, path: path)) {
      print('API Client: $statusCode on ${path ?? "request"} - triggering unauthorized callback (logout)');
      _onUnauthorized!();
    }
  }

  /// Expired JWTs are rejected before auth is set, so Spring returns generic 403 (not 401).
  /// Only treat 403 as logout when the token is expired or the server signals account/session issues.
  /// Do not logout on 403 for valid tokens (e.g. clinic staff hitting doctor-only endpoints).
  bool _shouldLogoutOn403(http.Response response, {String? path}) {
    if (isJwtExpired(_jwt)) return true;

    final body = response.body;
    if (body.contains('"error":"Account is disabled"')) return true;
    if (body.contains('"error":"Session invalid"')) return true;
    if (body.contains('"error":"Session expired or signed out"')) return true;

    // Expired/invalid JWT is rejected before auth is set; Spring returns generic 403 on
    // doctor-only routes. Clinic staff legitimately get 403 here — do not log them out.
    if (path != null && body.contains('"status":403') && body.contains('"error":"Forbidden"')) {
      final basePath = path.split('?').first;
      if (basePath == '/api/doctors/me' || basePath.startsWith('/api/doctors/me/')) {
        final role = jwtRoleFromToken(_jwt)?.toUpperCase();
        if (role != null && role != 'CLINIC_STAFF') return true;
      }
    }
    return false;
  }

  Map<String, String> _headers({Map<String, String>? extra}) => {
    'Content-Type': 'application/json',
    if (_jwt != null) 'Authorization': 'Bearer $_jwt',
    ...?extra,
  };

  void _assertAllowedPath(String path) {
    final cleanPath = path.startsWith('/') ? path : '/$path';
    final basePath = cleanPath.split('?').first;

    if (scope == ApiClientScope.doctor && basePath.startsWith('/api/auth/admin/')) {
      throw StateError('Doctor app cannot call admin auth endpoints: $basePath');
    }

    // Always allow remaining auth endpoints in both apps.
    if (basePath.startsWith('/api/auth/')) return;

    if (scope == ApiClientScope.admin) {
      if (!basePath.startsWith('/api/admin/')) {
        throw StateError('Admin app attempted to call non-admin endpoint: $basePath');
      }
      return;
    }

    // Doctor app
    if (basePath.startsWith('/api/admin/')) {
      throw StateError('Doctor app attempted to call admin endpoint: $basePath');
    }
  }

  Future<http.Response> get(String path, {Map<String, String>? params}) async {
    _assertAllowedPath(path);
    if (!_ensureTokenStillValid(path: path)) {
      return http.Response('{"error":"Session expired"}', 401, headers: const {'content-type': 'application/json'});
    }
    final uri = Uri.parse('$baseUrl$path').replace(queryParameters: params);
    final headers = _headers();
    // Debug: Check if token is present
    if (headers.containsKey('Authorization')) {
      print('API GET $path: Authorization header present');
    } else {
      print('API GET $path: WARNING - No Authorization header! Token: ${_jwt != null ? "exists" : "null"}');
    }
    final response = await http.get(uri, headers: headers);
    _checkUnauthorized(response, path: path);
    return response;
  }

  /// POST with a custom Bearer token (e.g. Firebase ID token for /api/auth/verify).
  /// Does not use stored JWT and does not trigger 401 callback on 403.
  Future<http.Response> postWithBearer(String path, Object body, String bearerToken) async {
    final cleanPath = path.startsWith('/') ? path : '/$path';
    _assertAllowedPath(cleanPath);
    final uri = Uri.parse('$baseUrl$cleanPath');
    final headers = {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer $bearerToken',
    };
    return http.post(uri, headers: headers, body: jsonEncode(body));
  }

  /// POST that does not trigger logout on 401/403. Use for endpoints that return
  /// 403 for business rules (e.g. video token "call has ended") rather than auth.
  Future<http.Response> postNoUnauthorizedCheck(String path, Object body) async {
    final cleanPath = path.startsWith('/') ? path : '/$path';
    _assertAllowedPath(cleanPath);
    final uri = Uri.parse('$baseUrl$cleanPath');
    return http.post(uri, headers: _headers(), body: jsonEncode(body));
  }

  Future<http.Response> post(String path, Object body) async {
    // Ensure path starts with / if baseUrl doesn't end with /
    final cleanPath = path.startsWith('/') ? path : '/$path';
    _assertAllowedPath(cleanPath);
    if (!_ensureTokenStillValid(path: cleanPath)) {
      return http.Response('{"error":"Session expired"}', 401, headers: const {'content-type': 'application/json'});
    }
    final uri = Uri.parse('$baseUrl$cleanPath');
    
    // Log request for debugging
    print('========================================');
    print('API POST Request:');
    print('  Full URL: $uri');
    print('  Base URL: $baseUrl');
    print('  Path: $cleanPath');
    print('  Method: POST');
    print('========================================');
    
    try {
      final response = await http.post(uri, headers: _headers(), body: jsonEncode(body));
      
      // Log response for debugging
      print('========================================');
      print('API POST Response:');
      print('  Status: ${response.statusCode} ${response.reasonPhrase}');
      print('  Content-Type: ${response.headers['content-type'] ?? 'unknown'}');
      print('  Content-Length: ${response.body.length}');
      
      // Log first part of response body if it's not JSON (likely an error page)
      final contentType = response.headers['content-type'] ?? '';
      if (!contentType.contains('application/json') && response.body.isNotEmpty) {
        final preview = response.body.length > 500 
            ? '${response.body.substring(0, 500)}...' 
            : response.body;
        print('  ⚠️  Non-JSON Response Detected!');
        print('  Response Preview (first 500 chars):');
        print('  $preview');
      } else if (response.body.isNotEmpty && response.body.length < 500) {
        print('  Response Body: ${response.body}');
      }
      print('========================================');
      
      _checkUnauthorized(response, path: cleanPath);
      return response;
    } catch (e) {
      print('========================================');
      print('API POST Error:');
      print('  Error: $e');
      print('  Failed URL: $uri');
      print('========================================');
      rethrow;
    }
  }

  Future<http.Response> put(String path, Object body) async {
    _assertAllowedPath(path);
    if (!_ensureTokenStillValid(path: path)) {
      return http.Response('{"error":"Session expired"}', 401, headers: const {'content-type': 'application/json'});
    }
    final uri = Uri.parse('$baseUrl$path');
    final response = await http.put(uri, headers: _headers(), body: jsonEncode(body));
    _checkUnauthorized(response, path: path);
    return response;
  }

  Future<http.Response> patch(String path, Object body) async {
    _assertAllowedPath(path);
    if (!_ensureTokenStillValid(path: path)) {
      return http.Response('{"error":"Session expired"}', 401, headers: const {'content-type': 'application/json'});
    }
    final uri = Uri.parse('$baseUrl$path');
    final response = await http.patch(uri, headers: _headers(), body: jsonEncode(body));
    _checkUnauthorized(response, path: path);
    return response;
  }

  Future<http.Response> delete(String path) async {
    _assertAllowedPath(path);
    if (!_ensureTokenStillValid(path: path)) {
      return http.Response('{"error":"Session expired"}', 401, headers: const {'content-type': 'application/json'});
    }
    final uri = Uri.parse('$baseUrl$path');
    final response = await http.delete(uri, headers: _headers());
    _checkUnauthorized(response, path: path);
    return response;
  }

  // ✅ NEW: multipart upload with JWT support
  Future<http.StreamedResponse> postMultipart(
    String path, {
    required List<http.MultipartFile> files,
    Map<String, String>? fields,
    Map<String, String>? extraHeaders,
  }) async {
    _assertAllowedPath(path);
    final uri = Uri.parse('$baseUrl$path');
    final req = http.MultipartRequest('POST', uri);
    // Attach JWT if present
    if (_jwt != null && _jwt!.isNotEmpty) {
      req.headers['Authorization'] = 'Bearer $_jwt';
    }
    // CORS note: for Flutter web, cookies aren't used here; JWT is enough.
    if (extraHeaders != null) {
      req.headers.addAll(extraHeaders);
    }
    if (fields != null) {
      req.fields.addAll(fields);
    }
    req.files.addAll(files);
    final streamedResponse = await req.send();
    // Note: StreamedResponse doesn't have statusCode directly accessible
    // We'd need to read the response to check, but for multipart we'll handle 401 in the calling code
    return streamedResponse;
  }

  // ✅ Expose headers for streaming/SSE use-cases
  Map<String, String> buildHeaders({Map<String, String>? extra}) {
    return {
      'Content-Type': 'application/json',
      if (_jwt != null) 'Authorization': 'Bearer $_jwt',
      ...?extra,
    };
  }
}
