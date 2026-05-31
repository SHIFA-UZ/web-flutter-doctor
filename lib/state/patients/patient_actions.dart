// lib/state/patients/patient_actions.dart
import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';

String _optionalClinicQuery(int? clinicId) =>
    clinicId == null ? '' : '?clinicId=$clinicId';

/// Upload a document and get the created document JSON from the backend.
///
/// Pass [category] to tag the upload with a [PatientDocumentCategory] code.
/// Medical-result categories (MRI, BLOOD_TEST, ULTRASOUND, ...) make the doc
/// visible to every doctor of the patient. Doctor-private categories
/// (APPOINTMENT_NOTE, INTERNAL_NOTE, ...) keep it restricted to the uploader.
Future<PatientDocument?> uploadPatientDocumentWithClient({
  required ApiClient client,
  required String patientId,
  required Uint8List fileBytes,
  required String fileName,
  required String title,
  String? category,
  int? clinicId,
}) async {
  final lowerName = fileName.toLowerCase();
  // Use the actual MIME type when we can detect it from the filename so the
  // backend stores the file with the right extension (jpg/png/pdf/...).
  final MediaType mediaType;
  if (lowerName.endsWith('.pdf')) {
    mediaType = MediaType('application', 'pdf');
  } else if (lowerName.endsWith('.png')) {
    mediaType = MediaType('image', 'png');
  } else if (lowerName.endsWith('.jpg') || lowerName.endsWith('.jpeg')) {
    mediaType = MediaType('image', 'jpeg');
  } else if (lowerName.endsWith('.gif')) {
    mediaType = MediaType('image', 'gif');
  } else if (lowerName.endsWith('.webp')) {
    mediaType = MediaType('image', 'webp');
  } else {
    mediaType = MediaType('application', 'octet-stream');
  }

  final files = <http.MultipartFile>[
    http.MultipartFile.fromBytes(
      'file',
      fileBytes,
      filename: fileName,
      contentType: mediaType,
    ),
  ];

  final fields = <String, String>{'title': title};
  if (category != null && category.isNotEmpty) {
    fields['category'] = category;
  }

  final streamed = await client.postMultipart(
    '/api/patients/$patientId/documents${_optionalClinicQuery(clinicId)}',
    files: files,
    fields: fields,
  );

  final res = await http.Response.fromStream(streamed);
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(res.body) as Map<String, dynamic>;
    final urlVal = j['url'];
    return PatientDocument(
      id: j['id'].toString(),
      title: (j['title'] ?? '') as String,
      date: DateTime.parse(j['date'] as String),
      url: urlVal != null && (urlVal as String).isNotEmpty ? urlVal as String : null,
      filePath: null,
      canView: j['canView'] as bool? ?? true,
      creatorLabel: (j['creatorLabel'] as String?) ?? 'Unknown',
      category: j['category'] as String?,
      isSharedWithTeam: j['isSharedWithTeam'] as bool? ?? false,
    );
  }
  throw Exception('Upload failed: ${res.statusCode} ${res.body}');
}

/// Update an existing document's file content.
Future<PatientDocument?> updatePatientDocumentWithClient({
  required ApiClient client,
  required String patientId,
  required String documentId,
  required Uint8List fileBytes,
  required String fileName,
  int? clinicId,
}) async {
  final files = <http.MultipartFile>[
    http.MultipartFile.fromBytes(
      'file',
      fileBytes,
      filename: fileName,
      contentType: MediaType('application', 'pdf'),
    ),
  ];

  final uri = Uri.parse('${client.baseUrl}/api/patients/$patientId/documents/$documentId${_optionalClinicQuery(clinicId)}');
  final headers = client.buildHeaders();
  
  final req = http.MultipartRequest('PUT', uri);
  req.headers.addAll(headers);
  req.files.addAll(files);

  final streamed = await req.send();
  final res = await http.Response.fromStream(streamed);
  
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(res.body) as Map<String, dynamic>;
    final urlVal = j['url'];
    return PatientDocument(
      id: j['id'].toString(),
      title: (j['title'] ?? '') as String,
      date: DateTime.parse(j['date'] as String),
      url: urlVal != null && (urlVal as String).isNotEmpty ? urlVal as String : null,
      filePath: null,
      canView: j['canView'] as bool? ?? true,
      creatorLabel: (j['creatorLabel'] as String?) ?? 'Unknown',
      category: j['category'] as String?,
      isSharedWithTeam: j['isSharedWithTeam'] as bool? ?? false,
    );
  }
  throw Exception('Update failed: ${res.statusCode} ${res.body}');
}

/// Request access to a locked document. Creates a pending request and notifies the owner.
Future<void> requestDocumentAccessWithClient({
  required ApiClient client,
  required String patientId,
  required String documentId,
  int? clinicId,
}) async {
  final res = await client.post(
    '/api/patients/$patientId/documents/$documentId/request-access${_optionalClinicQuery(clinicId)}',
    <String, dynamic>{},
  );
  if (res.statusCode >= 200 && res.statusCode < 300) return;
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to request access: ${res.statusCode} ${res.body}');
}

/// Result of a document download.
///
/// Includes filename (from Content-Disposition when available) and content
/// type (from the response Content-Type) so callers can pick the right
/// renderer regardless of which signal is present. Note that on Flutter web
/// browsers may strip Content-Disposition unless the server explicitly
/// exposes it via CORS, so the most reliable signal is usually the bytes
/// themselves (see `detectMimeFromBytes`).
class DocumentDownloadResult {
  const DocumentDownloadResult({
    required this.bytes,
    this.filename,
    this.contentType,
  });
  final Uint8List bytes;
  final String? filename;
  final String? contentType;
}

/// Download document via authenticated GET so it works after access is granted.
/// Returns the bytes plus any filename/content-type the server provided, or
/// null on 4xx/5xx.
Future<DocumentDownloadResult?> fetchDocumentDownloadWithClient({
  required ApiClient client,
  required String patientId,
  required String documentId,
  int? clinicId,
}) async {
  final res = await client.get(
    '/api/patients/$patientId/documents/$documentId/download',
    params: clinicId == null ? null : {'clinicId': '$clinicId'},
  );
  if (res.statusCode != 200) return null;
  final filename = _filenameFromContentDisposition(res.headers['content-disposition']);
  return DocumentDownloadResult(
    bytes: res.bodyBytes,
    filename: filename,
    contentType: res.headers['content-type'],
  );
}

String? _filenameFromContentDisposition(String? value) {
  if (value == null || value.isEmpty) return null;
  // Try plain filename="x" first (Spring's default for ASCII filenames).
  final match = RegExp(r'filename\s*=\s*"([^"]+)"').firstMatch(value);
  if (match != null) return match.group(1)?.trim();
  final match2 = RegExp(r'filename\s*=\s*([^;\s]+)').firstMatch(value);
  if (match2 != null) return match2.group(1)?.trim();

  // RFC 5987 form: filename*=UTF-8''percent-encoded.jpg (Spring uses this
  // when the filename has non-ASCII characters). Decode percent escapes so
  // the extension is recoverable.
  final ext5987 = RegExp(
    r"""filename\*\s*=\s*[A-Za-z0-9-]+''([^;\s"]+)""",
  ).firstMatch(value);
  if (ext5987 != null) {
    final raw = ext5987.group(1);
    if (raw != null && raw.isNotEmpty) {
      try {
        return Uri.decodeComponent(raw);
      } catch (_) {
        return raw;
      }
    }
  }
  return null;
}

/// Inspect the first few bytes of a downloaded file to determine its MIME
/// type. This is more reliable than trusting Content-Disposition or
/// Content-Type because some proxies/CORS configurations strip those.
String? detectMimeFromBytes(Uint8List bytes) {
  if (bytes.length < 4) return null;
  // JPEG: FF D8 FF
  if (bytes[0] == 0xFF && bytes[1] == 0xD8 && bytes[2] == 0xFF) {
    return 'image/jpeg';
  }
  // PNG: 89 50 4E 47 0D 0A 1A 0A
  if (bytes.length >= 8 &&
      bytes[0] == 0x89 &&
      bytes[1] == 0x50 &&
      bytes[2] == 0x4E &&
      bytes[3] == 0x47 &&
      bytes[4] == 0x0D &&
      bytes[5] == 0x0A &&
      bytes[6] == 0x1A &&
      bytes[7] == 0x0A) {
    return 'image/png';
  }
  // GIF: "GIF8"
  if (bytes[0] == 0x47 &&
      bytes[1] == 0x49 &&
      bytes[2] == 0x46 &&
      bytes[3] == 0x38) {
    return 'image/gif';
  }
  // WebP: "RIFF" .. "WEBP"
  if (bytes.length >= 12 &&
      bytes[0] == 0x52 &&
      bytes[1] == 0x49 &&
      bytes[2] == 0x46 &&
      bytes[3] == 0x46 &&
      bytes[8] == 0x57 &&
      bytes[9] == 0x45 &&
      bytes[10] == 0x42 &&
      bytes[11] == 0x50) {
    return 'image/webp';
  }
  // PDF: "%PDF"
  if (bytes[0] == 0x25 &&
      bytes[1] == 0x50 &&
      bytes[2] == 0x44 &&
      bytes[3] == 0x46) {
    return 'application/pdf';
  }
  return null;
}

/// Fetch list of documents for a patient (from patient_documents table).
Future<List<PatientDocument>> fetchPatientDocumentsWithClient({
  required ApiClient client,
  required String patientId,
  int? clinicId,
}) async {
  final res = await client.get(
    '/api/patients/$patientId/documents',
    params: clinicId == null ? null : {'clinicId': '$clinicId'},
  );

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List data = jsonDecode(utf8.decode(res.bodyBytes)) as List;
    return data.map((e) {
      final j = e as Map<String, dynamic>;
      final urlVal = j['url'];
      return PatientDocument(
        id: j['id'].toString(),
        title: (j['title'] ?? '') as String,
        date: DateTime.parse(j['date'] as String),
        url: urlVal != null && (urlVal as String).isNotEmpty ? urlVal as String : null,
        filePath: null,
        canView: j['canView'] as bool? ?? true,
        creatorLabel: (j['creatorLabel'] as String?) ?? 'Unknown',
        category: j['category'] as String?,
        isSharedWithTeam: j['isSharedWithTeam'] as bool? ?? false,
      );
    }).toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch documents: ${res.statusCode} ${res.body}');
  }
}

/// Create a new patient via POST /api/patients. Phone is optional (uniqueness applies when set).
Future<Patient> createPatientWithClient({
  required ApiClient client,
  required String name,
  String? phone,
  List<String>? phones,
  String? email,
  String? address,
  DateTime? birthDate,
  String? language,
  String? photoUrl,
  String? chronicDisease,
}) async {
  final resolvedPhones = phones != null
      ? phones.map((p) => p.trim()).where((p) => p.isNotEmpty).toList()
      : (phone != null && phone.trim().isNotEmpty ? [phone.trim()] : <String>[]);

  final res = await client.post('/api/patients', {
    'name': name,
    if (resolvedPhones.isNotEmpty) 'phones': resolvedPhones,
    if (resolvedPhones.length == 1) 'phone': resolvedPhones.first,
    'email': email,
    'address': address,
    'birthDate': birthDate == null
        ? null
        : '${birthDate.year.toString().padLeft(4, '0')}-'
              '${birthDate.month.toString().padLeft(2, '0')}-'
              '${birthDate.day.toString().padLeft(2, '0')}',

    'language': language,
    'photoUrl': photoUrl,
    'chronicDisease': chronicDisease,
  });

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return Patient.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else if (res.statusCode == 409) {
    String msg = 'Patient with this phone number already exists.';
    try {
      final j = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>?;
      if (j != null && j['message'] != null) msg = j['message'] as String;
    } catch (_) {}
    throw Exception(msg);
  } else if (res.statusCode == 400) {
    String msg = 'Invalid request. Please check the phone number.';
    try {
      final j = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>?;
      if (j != null && j['message'] != null) msg = j['message'] as String;
    } catch (_) {}
    throw Exception(msg);
  } else {
    throw Exception('Failed to create patient: ${res.statusCode} ${res.body}');
  }
}

/// Update a patient via PATCH /api/patients/{id}
Future<Patient> updatePatientWithClient({
  required ApiClient client,
  required String patientId,
  String? name,
  String? phone,
  String? email,
  String? address,
  DateTime? birthDate,
  String? language,
  String? photoUrl,
  String? chronicDisease,
  bool? smsReminderEnabled,
}) async {
  final body = <String, dynamic>{};
  if (name != null) body['name'] = name;
  if (phone != null) body['phone'] = phone;
  if (email != null) body['email'] = email;
  if (address != null) body['address'] = address;
  if (birthDate != null) {
    body['birthDate'] = '${birthDate.year.toString().padLeft(4, '0')}-'
        '${birthDate.month.toString().padLeft(2, '0')}-'
        '${birthDate.day.toString().padLeft(2, '0')}';
  }
  if (language != null) body['language'] = language;
  if (photoUrl != null) body['photoUrl'] = photoUrl;
  // Always include chronicDisease if provided (even if empty string to clear it)
  // Backend will convert empty string to null via takeIf { it.isNotEmpty() }
  // We use a special marker to distinguish between "not updating" (null) and "clearing" (empty string)
  // Since String? can't distinguish, we'll always include it if it's not null
  if (chronicDisease != null) {
    body['chronicDisease'] = chronicDisease; // Empty string will clear it in backend
  }
  if (smsReminderEnabled != null) {
    body['smsReminderEnabled'] = smsReminderEnabled;
  }

  final res = await client.patch('/api/patients/$patientId', body);

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return Patient.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else if (res.statusCode == 409) {
    String msg = 'Patient with this phone number already exists.';
    try {
      final j = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>?;
      if (j != null && j['message'] != null) msg = j['message'] as String;
    } catch (_) {}
    throw Exception(msg);
  } else {
    throw Exception('Failed to update patient: ${res.statusCode} ${res.body}');
  }
}

/// POST /api/patients/{id}/send-test-sms — immediate DevSMS test (billed).
Future<void> sendPatientTestSmsWithClient({
  required ApiClient client,
  required String patientId,
}) async {
  final res = await client.post('/api/patients/$patientId/send-test-sms', {});

  if (res.statusCode >= 200 && res.statusCode < 300) {
    return;
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else if (res.statusCode == 403) {
    throw Exception('SMS reminders are not enabled for your account.');
  } else if (res.statusCode == 503) {
    throw Exception('SMS service is not configured on the server.');
  } else {
    String msg = 'Failed to send test SMS: ${res.statusCode}';
    try {
      final j = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>?;
      if (j?['message'] != null) msg = j!['message'] as String;
    } catch (_) {}
    throw Exception(msg);
  }
}

/// Fetch a single patient by ID
Future<Patient> fetchPatientWithClient({
  required ApiClient client,
  required String patientId,
  int? clinicId,
}) async {
  final res = await client.get(
    '/api/patients/$patientId',
    params: clinicId == null ? null : {'clinicId': '$clinicId'},
  );

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return Patient.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch patient: ${res.statusCode} ${res.body}');
  }
}

List<PatientAssignmentItem> _patientAssignmentItemsFromDecoded(dynamic decoded) {
  if (decoded is List) {
    return decoded
        .whereType<Map>()
        .map((e) => PatientAssignmentItem.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }
  if (decoded is Map) {
    final raw = decoded['content'];
    if (raw is! List) return [];
    return raw
        .whereType<Map>()
        .map((e) => PatientAssignmentItem.fromJson(Map<String, dynamic>.from(e)))
        .toList();
  }
  return [];
}

int? _totalPagesFromDecoded(dynamic decoded) {
  if (decoded is Map) {
    return (decoded['totalPages'] as num?)?.toInt();
  }
  return null;
}

/// Fetch patients for calendar assignment (id + name). GET /api/patients/for-assignment — full profile directory (paginated).
Future<List<PatientAssignmentItem>> fetchPatientsForAssignmentWithClient({
  required ApiClient client,
}) async {
  const pageSize = 500;
  final byId = <String, PatientAssignmentItem>{};

  var page = 0;
  while (true) {
    final res = await client.get('/api/patients/for-assignment', params: {
      'page': '$page',
      'size': '$pageSize',
      'sort': 'fullName,asc',
    });

    if (res.statusCode >= 200 && res.statusCode < 300) {
      final body = utf8.decode(res.bodyBytes);
      if (body.trim().isEmpty) {
        break;
      }
      final decoded = jsonDecode(body);
      if (decoded is! List && decoded is! Map) {
        throw Exception('Patients response expected list or page JSON, got: ${decoded.runtimeType}');
      }

      final batch = _patientAssignmentItemsFromDecoded(decoded);
      final totalPages = _totalPagesFromDecoded(decoded);

      for (final p in batch) {
        byId.putIfAbsent(p.id, () => p);
      }

      if (decoded is List) {
        break;
      }

      page++;
      final doneByMeta = totalPages != null && page >= totalPages;
      if (batch.isEmpty || doneByMeta || batch.length < pageSize) {
        break;
      }
    } else if (res.statusCode == 401) {
      throw Exception('Unauthorized (401): please sign in again.');
    } else if (res.statusCode == 403) {
      throw Exception('Forbidden (403): access denied.');
    } else {
      final preview = res.body.length > 200 ? '${res.body.substring(0, 200)}...' : res.body;
      throw Exception('Failed to load patients: ${res.statusCode} $preview');
    }
  }

  final list = byId.values.toList()
    ..sort((a, b) => a.name.toLowerCase().compareTo(b.name.toLowerCase()));
  return list;
}

/// Fetch list of forms for a patient.
Future<List<PatientForm>> fetchPatientFormsWithClient({
  required ApiClient client,
  required String patientId,
}) async {
  final res = await client.get('/api/patients/$patientId/forms');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final List data = jsonDecode(utf8.decode(res.bodyBytes)) as List;
    return data.map((e) {
      final j = e as Map<String, dynamic>;
      return PatientForm.fromJson(j);
    }).toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch forms: ${res.statusCode} ${res.body}');
  }
}

/// Create a new form.
Future<PatientForm> createPatientFormWithClient({
  required ApiClient client,
  required String patientId,
  required PatientForm form,
}) async {
  final res = await client.post('/api/patients/$patientId/forms', form.toJson());

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return PatientForm.fromJson(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to create form: ${res.statusCode} ${res.body}');
  }
}

/// Update an existing form.
Future<PatientForm> updatePatientFormWithClient({
  required ApiClient client,
  required String patientId,
  required String formId,
  required PatientForm form,
}) async {
  final res = await client.put('/api/patients/$patientId/forms/$formId', form.toJson());

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return PatientForm.fromJson(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to update form: ${res.statusCode} ${res.body}');
  }
}

/// Request patient signature on form **025-2** (notifies patient app).
Future<PatientForm> requestPatientFormSignatureWithClient({
  required ApiClient client,
  required String patientId,
  required String formId,
}) async {
  final res = await client.put(
    '/api/patients/$patientId/forms/$formId/request-signature',
    <String, dynamic>{},
  );

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return PatientForm.fromJson(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception(
      'Failed to request patient signature: ${res.statusCode} ${res.body}',
    );
  }
}

/// Link a document to a form.
Future<PatientForm> linkDocumentToFormWithClient({
  required ApiClient client,
  required String patientId,
  required String formId,
  required String documentId,
}) async {
  final uri = Uri.parse('${client.baseUrl}/api/patients/$patientId/forms/$formId/link-document?documentId=$documentId');
  final headers = client.buildHeaders();
  final res = await http.post(uri, headers: headers);

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return PatientForm.fromJson(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to link document: ${res.statusCode} ${res.body}');
  }
}

/// Create a patient account (generate username/password) via POST /api/patients/{id}/create-account
Future<Map<String, dynamic>> createPatientAccountWithClient({
  required ApiClient client,
  required String patientId,
}) async {
  final res = await client.post('/api/patients/$patientId/create-account', {});

  if (res.statusCode >= 200 && res.statusCode < 300) {
    return jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to create patient account: ${res.statusCode} ${res.body}');
  }
}

/// Hygiene recall / prophylaxis reminders (per patient and clinic).
class ProphylaxisSetting {
  const ProphylaxisSetting({
    required this.patientId,
    required this.clinicId,
    required this.intervalMonths,
    required this.enabled,
    this.lastSentAt,
  });

  final int patientId;
  final int clinicId;
  final int intervalMonths;
  final bool enabled;
  final String? lastSentAt;

  static ProphylaxisSetting? fromJsonMap(Map<String, dynamic> j) {
    final pid = j['patientId'];
    final cid = j['clinicId'];
    if (pid == null || cid == null) return null;
    return ProphylaxisSetting(
      patientId: (pid as num).toInt(),
      clinicId: (cid as num).toInt(),
      intervalMonths: (j['intervalMonths'] as num?)?.toInt() ?? 12,
      enabled: j['enabled'] as bool? ?? true,
      lastSentAt: j['lastSentAt'] as String?,
    );
  }
}

/// GET returns null when no row exists yet.
Future<ProphylaxisSetting?> fetchProphylaxisSettingsWithClient({
  required ApiClient client,
  required String patientId,
  required int clinicId,
}) async {
  final res = await client.get(
    '/api/prophylaxis/settings',
    params: {'patientId': patientId, 'clinicId': '$clinicId'},
  );
  if (res.statusCode == 200) {
    final raw = utf8.decode(res.bodyBytes).trim();
    if (raw.isEmpty || raw == 'null') return null;
    final decoded = jsonDecode(raw);
    if (decoded is! Map<String, dynamic>) return null;
    return ProphylaxisSetting.fromJsonMap(decoded);
  }
  if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  }
  throw Exception('Failed to load prophylaxis: ${res.statusCode} ${res.body}');
}

Future<ProphylaxisSetting> upsertProphylaxisSettingsWithClient({
  required ApiClient client,
  required String patientId,
  required int clinicId,
  required int intervalMonths,
  required bool enabled,
}) async {
  final res = await client.put('/api/prophylaxis/settings', {
    'patientId': int.parse(patientId),
    'clinicId': clinicId,
    'intervalMonths': intervalMonths,
    'enabled': enabled,
  });
  if (res.statusCode >= 200 && res.statusCode < 300) {
    final j = jsonDecode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>;
    final parsed = ProphylaxisSetting.fromJsonMap(j);
    if (parsed == null) {
      throw Exception('Invalid prophylaxis response');
    }
    return parsed;
  }
  if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  }
  throw Exception('Failed to save prophylaxis: ${res.statusCode} ${res.body}');
}
