// lib/features/patients/domain/patient_models.dart

/// Minimal patient data for calendar assign-patient: search by id or name, show avatar.
class PatientAssignmentItem {
  final String id;
  final String name;
  final String? photoUrl;
  const PatientAssignmentItem({
    required this.id,
    required this.name,
    this.photoUrl,
  });
  factory PatientAssignmentItem.fromJson(Map<String, dynamic> json) {
    return PatientAssignmentItem(
      id: (json['id'] ?? '').toString(),
      name: (json['name'] ?? '') as String,
      photoUrl: json['photoUrl'] as String?,
    );
  }
}

class Patient {
  final String id;
  final String name;
  final PatientGeneral general;
  final List<PatientDocument> documents;

  /// ✅ Use this in UI (public image URL)
  final String? photoUrl;

  final bool hasAccount;
  final String? username;
  final String clinicalStatus;
  final bool atRisk;
  final bool followUpRequired;

  Patient({
    required this.id,
    required this.name,
    required this.general,
    required this.documents,
    this.photoUrl,
    this.hasAccount = false,
    this.username,
    this.clinicalStatus = 'ACTIVE',
    this.atRisk = false,
    this.followUpRequired = false,
  });

  Patient copyWith({
    String? id,
    String? name,
    PatientGeneral? general,
    List<PatientDocument>? documents,
    String? photoUrl,
    bool? hasAccount,
    String? username,
    String? clinicalStatus,
    bool? atRisk,
    bool? followUpRequired,
  }) {
    return Patient(
      id: id ?? this.id,
      name: name ?? this.name,
      general: general ?? this.general,
      documents: documents ?? List<PatientDocument>.from(this.documents),
      photoUrl: photoUrl ?? this.photoUrl,
      hasAccount: hasAccount ?? this.hasAccount,
      username: username ?? this.username,
      clinicalStatus: clinicalStatus ?? this.clinicalStatus,
      atRisk: atRisk ?? this.atRisk,
      followUpRequired: followUpRequired ?? this.followUpRequired,
    );
  }

  /// Safe string from JSON (handles null, non-string).
  static String? _stringOrNull(dynamic value) {
    if (value == null) return null;
    if (value is String) return value;
    return value.toString();
  }

  static List<String> _parsePhones(Map<String, dynamic> json) {
    final raw = json['phones'];
    if (raw is List) {
      return raw
          .map((e) => e?.toString().trim() ?? '')
          .where((p) => p.isNotEmpty)
          .toList();
    }
    final single = _stringOrNull(json['phone']);
    if (single != null && single.isNotEmpty) return [single];
    return const [];
  }

  /// ✅ Parse `photoUrl` and `documents` coming from backend.
  /// The backend may return documents with either:
  ///  - {id, title, date, filePath}  (older contract)
  ///  - {id, title, date, url}       (new contract used by /api/patients/{id}/documents)
  factory Patient.fromApi(Map<String, dynamic> json) {
    final docsJson = (json['documents'] as List? ?? []);

    final parsedDocs = docsJson.map((d) {
      final dateStr = d['date'] as String;
      final hasUrl = d['url'] != null && (d['url'] as String).isNotEmpty;
      final hasPath =
          d['filePath'] != null && (d['filePath'] as String).isNotEmpty;
      return PatientDocument(
        id: d['id'].toString(),
        title: (d['title'] ?? '') as String,
        date: DateTime.parse(dateStr),
        filePath: hasPath ? d['filePath'] as String : null,
        url: hasUrl ? d['url'] as String : null,
        canView: d['canView'] as bool? ?? true,
        creatorLabel: (d['creatorLabel'] as String?) ?? 'Unknown',
        category: d['category'] as String?,
        isSharedWithTeam: d['isSharedWithTeam'] as bool? ?? false,
      );
    }).toList();

    return Patient(
      id: json['id'].toString(),
      name: json['name'] ?? '',
      general: PatientGeneral(
        birthDate: json['birthDate'] != null
            ? DateTime.parse(json['birthDate'])
            : null,
        phone: json['phone'],
        phones: _parsePhones(json),
        email: json['email'],
        address: json['address'],
        language: json['language'],
        chronicDisease: json['chronicDisease'],
        locationCountry: json['locationCountry'],
        locationRegion: json['locationRegion'],
        locationDistrict: json['locationDistrict'],
        locationCity: json['locationCity'],
        locationPostalCode: json['locationPostalCode'],
        locationStreetAddress: json['locationStreetAddress'],
        smsReminderEnabled: json['smsReminderEnabled'] == true,
        gender: _stringOrNull(json['gender']),
        bloodGroup: _stringOrNull(json['bloodGroup']),
        allergies: _stringOrNull(json['allergies']),
      ),
      documents: parsedDocs,
      photoUrl: json['photoUrl'] as String?,
      hasAccount: json['hasAccount'] == true,
      username: _stringOrNull(json['username']),
      clinicalStatus: (_stringOrNull(json['clinicalStatus']) ?? 'ACTIVE').toUpperCase(),
      atRisk: json['atRisk'] == true,
      followUpRequired: json['followUpRequired'] == true,
    );
  }
}

class PatientGeneral {
  final DateTime? birthDate;
  final String? phone;
  final List<String> phones;
  final String? email;
  final String? address; // Legacy field - populated from structured location if available
  final String? language;
  final String? chronicDisease;
  final String? gender;
  final String? bloodGroup;
  final String? allergies;
  // Structured location fields
  final String? locationCountry;
  final String? locationRegion;
  final String? locationDistrict;
  final String? locationCity;
  final String? locationPostalCode;
  final String? locationStreetAddress;
  final bool smsReminderEnabled;

  const PatientGeneral({
    this.birthDate,
    this.phone,
    this.phones = const [],
    this.email,
    this.address,
    this.language,
    this.chronicDisease,
    this.gender,
    this.bloodGroup,
    this.allergies,
    this.locationCountry,
    this.locationRegion,
    this.locationDistrict,
    this.locationCity,
    this.locationPostalCode,
    this.locationStreetAddress,
    this.smsReminderEnabled = false,
  });
  
  /// Format structured location for display: Street (if any), City, District, Region
  String? get formattedLocation {
    final parts = <String>[];
    if (locationStreetAddress?.isNotEmpty == true) {
      parts.add(locationStreetAddress!);
    }
    if (locationCity?.isNotEmpty == true) {
      parts.add(locationCity!);
    }
    if (locationDistrict?.isNotEmpty == true) {
      parts.add(locationDistrict!);
    }
    if (locationRegion?.isNotEmpty == true) {
      parts.add(locationRegion!);
    }
    return parts.isEmpty ? address : parts.join(', ');
  }

  List<String> get allPhones {
    if (phones.isNotEmpty) return phones;
    if (phone != null && phone!.isNotEmpty) return [phone!];
    return const [];
  }
}

class PatientDocument {
  final String id;
  final String title;
  final DateTime date;

  /// Old contract: relative file path (e.g., "patientdocuments/1/file.pdf")
  final String? filePath;

  /// Absolute URL when canView is true; null when locked.
  final String? url;

  /// Whether the current doctor can open this document (creator or granted).
  final bool canView;

  /// Who created/uploaded: "Doctor", "Patient", or "Unknown".
  final String creatorLabel;

  /// Optional category/tag chosen at upload (e.g. "MRI", "BLOOD_TEST",
  /// "FORM_025_2"). Null when uploaded before categories existed.
  final String? category;

  /// True when the document is visible to every doctor of the patient
  /// (patient uploads, or doctor uploads tagged as a medical result).
  final bool isSharedWithTeam;

  PatientDocument({
    required this.id,
    required this.title,
    required this.date,
    this.filePath,
    this.url,
    this.canView = true,
    this.creatorLabel = 'Unknown',
    this.category,
    this.isSharedWithTeam = false,
  });

  PatientDocument copyWith({
    String? id,
    String? title,
    DateTime? date,
    String? filePath,
    String? url,
    bool? canView,
    String? creatorLabel,
    String? category,
    bool? isSharedWithTeam,
  }) {
    return PatientDocument(
      id: id ?? this.id,
      title: title ?? this.title,
      date: date ?? this.date,
      filePath: filePath ?? this.filePath,
      url: url ?? this.url,
      canView: canView ?? this.canView,
      creatorLabel: creatorLabel ?? this.creatorLabel,
      category: category ?? this.category,
      isSharedWithTeam: isSharedWithTeam ?? this.isSharedWithTeam,
    );
  }
}

/// Appointment row for patient detail — history with the logged-in doctor.
class PatientDoctorAppointment {
  const PatientDoctorAppointment({
    required this.id,
    required this.startAt,
    required this.endAt,
    required this.status,
    this.location,
    this.isVideo = false,
    this.reason,
  });

  final String id;
  final DateTime startAt;
  final DateTime endAt;
  final String status;
  final String? location;
  final bool isVideo;
  final String? reason;

  factory PatientDoctorAppointment.fromJson(Map<String, dynamic> json) {
    return PatientDoctorAppointment(
      id: (json['id'] ?? '').toString(),
      startAt: DateTime.parse(json['startAt'] as String),
      endAt: DateTime.parse(json['endAt'] as String),
      status: (json['status'] ?? '').toString(),
      location: json['location'] as String?,
      isVideo: json['isVideo'] == true,
      reason: json['reason'] as String?,
    );
  }
}
