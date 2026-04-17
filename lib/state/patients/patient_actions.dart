// lib/state/patients/patient_actions.dart
import 'dart:convert';
import 'dart:typed_data';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:shifa_doc_app_v1/core/api/api_client.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_form_models.dart';

/// Upload a PDF and get the created document JSON from backend.
/// Returns PatientDocument with absolute `url` (8090) to open in the viewer).
Future<PatientDocument?> uploadPatientDocumentWithClient({
  required ApiClient client,
  required String patientId,
  required Uint8List fileBytes,
  required String fileName,
  required String title,
}) async {
  final files = <http.MultipartFile>[
    http.MultipartFile.fromBytes(
      'file',
      fileBytes,
      filename: fileName,
      contentType: MediaType('application', 'pdf'),
    ),
  ];

  final streamed = await client.postMultipart(
    '/api/patients/$patientId/documents',
    files: files,
    fields: {'title': title},
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
}) async {
  final files = <http.MultipartFile>[
    http.MultipartFile.fromBytes(
      'file',
      fileBytes,
      filename: fileName,
      contentType: MediaType('application', 'pdf'),
    ),
  ];

  final uri = Uri.parse('${client.baseUrl}/api/patients/$patientId/documents/$documentId');
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
    );
  }
  throw Exception('Update failed: ${res.statusCode} ${res.body}');
}

/// Request access to a locked document. Creates a pending request and notifies the owner.
Future<void> requestDocumentAccessWithClient({
  required ApiClient client,
  required String patientId,
  required String documentId,
}) async {
  final res = await client.post(
    '/api/patients/$patientId/documents/$documentId/request-access',
    <String, dynamic>{},
  );
  if (res.statusCode >= 200 && res.statusCode < 300) return;
  if (res.statusCode == 401) throw Exception('Unauthorized: please login again.');
  throw Exception('Failed to request access: ${res.statusCode} ${res.body}');
}

/// Result of a document download: bytes and optional filename (for correct extension).
class DocumentDownloadResult {
  const DocumentDownloadResult({required this.bytes, this.filename});
  final Uint8List bytes;
  final String? filename;
}

/// Download document via authenticated GET so it works after access is granted.
/// Returns bytes and filename (from Content-Disposition) or null if not found/forbidden.
/// Use filename so images (e.g. .jpg) are saved with the right extension, not always .pdf.
Future<DocumentDownloadResult?> fetchDocumentDownloadWithClient({
  required ApiClient client,
  required String patientId,
  required String documentId,
}) async {
  final res = await client.get(
    '/api/patients/$patientId/documents/$documentId/download',
  );
  if (res.statusCode != 200) return null;
  final filename = _filenameFromContentDisposition(res.headers['content-disposition']);
  return DocumentDownloadResult(bytes: res.bodyBytes, filename: filename);
}

String? _filenameFromContentDisposition(String? value) {
  if (value == null || value.isEmpty) return null;
  // Match filename="x" or filename=x (Content-Disposition header)
  final match = RegExp(r'filename\s*=\s*"([^"]+)"').firstMatch(value);
  if (match != null) return match.group(1)?.trim();
  final match2 = RegExp(r'filename\s*=\s*([^;\s]+)').firstMatch(value);
  return match2?.group(1)?.trim();
}

/// Fetch list of documents for a patient (from patient_documents table).
Future<List<PatientDocument>> fetchPatientDocumentsWithClient({
  required ApiClient client,
  required String patientId,
}) async {
  final res = await client.get('/api/patients/$patientId/documents');

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
      );
    }).toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch documents: ${res.statusCode} ${res.body}');
  }
}

/// Create a new patient via POST /api/patients. Phone is required (one patient per phone).
Future<Patient> createPatientWithClient({
  required ApiClient client,
  required String name,
  required String phone,
  String? email,
  String? address,
  DateTime? birthDate,
  String? language,
  String? photoUrl,
  String? chronicDisease,
}) async {
  final res = await client.post('/api/patients', {
    'name': name,
    'phone': phone,
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

/// Fetch a single patient by ID
Future<Patient> fetchPatientWithClient({
  required ApiClient client,
  required String patientId,
}) async {
  final res = await client.get('/api/patients/$patientId');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final Map<String, dynamic> j = jsonDecode(utf8.decode(res.bodyBytes));
    return Patient.fromApi(j);
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized: please login again.');
  } else {
    throw Exception('Failed to fetch patient: ${res.statusCode} ${res.body}');
  }
}

/// Fetch patients for calendar assignment (id + name only). GET /api/patients/for-assignment.
Future<List<PatientAssignmentItem>> fetchPatientsForAssignmentWithClient({
  required ApiClient client,
}) async {
  final res = await client.get('/api/patients/for-assignment');

  if (res.statusCode >= 200 && res.statusCode < 300) {
    final body = utf8.decode(res.bodyBytes);
    if (body.trim().isEmpty) return [];
    final decoded = jsonDecode(body);
    if (decoded is! List) {
      throw Exception('Patients list expected, got: ${decoded.runtimeType}');
    }
    final List data = decoded;
    return data
        .map((e) => PatientAssignmentItem.fromJson(e as Map<String, dynamic>))
        .toList();
  } else if (res.statusCode == 401) {
    throw Exception('Unauthorized (401): please sign in again.');
  } else if (res.statusCode == 403) {
    throw Exception('Forbidden (403): access denied.');
  } else {
    final preview = res.body.length > 200 ? '${res.body.substring(0, 200)}...' : res.body;
    throw Exception('Failed to load patients: ${res.statusCode} $preview');
  }
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
