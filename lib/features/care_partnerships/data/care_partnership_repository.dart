import 'dart:convert';

import 'package:shifa_doc_app_v1/core/api/api_client.dart';

class CarePartnerHit {
  const CarePartnerHit({
    required this.id,
    required this.firstName,
    required this.lastName,
    this.profession,
    this.clinic,
    this.locationCountry,
    this.locationCity,
    this.avatarUrl,
  });

  final int id;
  final String firstName;
  final String lastName;
  final String? profession;
  final String? clinic;
  final String? locationCountry;
  final String? locationCity;
  final String? avatarUrl;

  String get displayName => '$firstName $lastName'.trim();

  factory CarePartnerHit.fromJson(Map<String, dynamic> json) => CarePartnerHit(
        id: (json['id'] as num).toInt(),
        firstName: json['firstName'] as String? ?? '',
        lastName: json['lastName'] as String? ?? '',
        profession: json['profession'] as String?,
        clinic: json['clinic'] as String?,
        locationCountry: json['locationCountry'] as String?,
        locationCity: json['locationCity'] as String?,
        avatarUrl: json['avatarUrl'] as String?,
      );
}

class CarePartnership {
  const CarePartnership({
    required this.id,
    required this.status,
    required this.patientId,
    this.patientName,
    required this.initiatingDoctorId,
    required this.initiatingDoctorName,
    required this.partnerDoctorId,
    required this.partnerDoctorName,
    this.specialtyRequested,
    this.message,
    this.originatingAppointmentId,
    required this.createdAt,
    required this.updatedAt,
    this.viewerRole,
  });

  final int id;
  final String status;
  final int patientId;
  final String? patientName;
  final int initiatingDoctorId;
  final String initiatingDoctorName;
  final int partnerDoctorId;
  final String partnerDoctorName;
  final String? specialtyRequested;
  final String? message;
  final int? originatingAppointmentId;
  final String createdAt;
  final String updatedAt;
  /// INITIATOR | PARTNER from the backend for the requesting doctor.
  final String? viewerRole;

  bool get iAmInitiator => viewerRole == 'INITIATOR';
  bool get iAmPartner => viewerRole == 'PARTNER';

  factory CarePartnership.fromJson(Map<String, dynamic> json) => CarePartnership(
        id: (json['id'] as num).toInt(),
        status: json['status'] as String? ?? '',
        patientId: (json['patientId'] as num).toInt(),
        patientName: json['patientName'] as String?,
        initiatingDoctorId: (json['initiatingDoctorId'] as num).toInt(),
        initiatingDoctorName: json['initiatingDoctorName'] as String? ?? '',
        partnerDoctorId: (json['partnerDoctorId'] as num).toInt(),
        partnerDoctorName: json['partnerDoctorName'] as String? ?? '',
        specialtyRequested: json['specialtyRequested'] as String?,
        message: json['message'] as String?,
        originatingAppointmentId:
            (json['originatingAppointmentId'] as num?)?.toInt(),
        createdAt: json['createdAt'] as String? ?? '',
        updatedAt: json['updatedAt'] as String? ?? '',
        viewerRole: json['viewerRole'] as String?,
      );
}

class CarePartnershipProgress {
  const CarePartnershipProgress({
    required this.id,
    required this.authorDoctorId,
    required this.authorDoctorName,
    required this.body,
    required this.createdAt,
  });

  final int id;
  final int authorDoctorId;
  final String authorDoctorName;
  final String body;
  final String createdAt;

  factory CarePartnershipProgress.fromJson(Map<String, dynamic> json) =>
      CarePartnershipProgress(
        id: (json['id'] as num).toInt(),
        authorDoctorId: (json['authorDoctorId'] as num).toInt(),
        authorDoctorName: json['authorDoctorName'] as String? ?? '',
        body: json['body'] as String? ?? '',
        createdAt: json['createdAt'] as String? ?? '',
      );
}

class CarePartnershipRepository {
  CarePartnershipRepository(this._api);

  final ApiClient _api;

  Future<List<CarePartnerHit>> searchPartners({
    String country = 'Uzbekistan',
    String? profession,
    String? q,
  }) async {
    final res = await _api.get(
      '/api/care-partnerships/partners/search',
      params: {
        'country': country,
        if (profession != null && profession.isNotEmpty) 'profession': profession,
        if (q != null && q.isNotEmpty) 'q': q,
      },
    );
    if (res.statusCode != 200) {
      throw Exception('Partner search failed (${res.statusCode})');
    }
    final list = jsonDecode(res.body) as List<dynamic>;
    return list
        .map((e) => CarePartnerHit.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<List<CarePartnership>> listMine() async {
    final res = await _api.get('/api/care-partnerships');
    if (res.statusCode != 200) {
      throw Exception('Failed to load partnerships (${res.statusCode})');
    }
    final list = jsonDecode(res.body) as List<dynamic>;
    return list
        .map((e) => CarePartnership.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<CarePartnership> getById(int id) async {
    final res = await _api.get('/api/care-partnerships/$id');
    if (res.statusCode != 200) {
      throw Exception('Failed to load partnership (${res.statusCode})');
    }
    return CarePartnership.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }

  Future<CarePartnership> invite({
    required int patientId,
    required int partnerDoctorId,
    String? specialtyRequested,
    String? message,
    int? originatingAppointmentId,
  }) async {
    final res = await _api.post('/api/care-partnerships', {
      'patientId': patientId,
      'partnerDoctorId': partnerDoctorId,
      if (specialtyRequested != null) 'specialtyRequested': specialtyRequested,
      if (message != null) 'message': message,
      if (originatingAppointmentId != null)
        'originatingAppointmentId': originatingAppointmentId,
    });
    if (res.statusCode != 200) {
      throw Exception('Invite failed (${res.statusCode}): ${res.body}');
    }
    return CarePartnership.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }

  Future<CarePartnership> accept(int id) async {
    final res = await _api.post('/api/care-partnerships/$id/accept', {});
    if (res.statusCode != 200) throw Exception('Accept failed');
    return CarePartnership.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }

  Future<CarePartnership> decline(int id) async {
    final res = await _api.post('/api/care-partnerships/$id/decline', {});
    if (res.statusCode != 200) throw Exception('Decline failed');
    return CarePartnership.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }

  Future<CarePartnership> cancel(int id) async {
    final res = await _api.post('/api/care-partnerships/$id/cancel', {});
    if (res.statusCode != 200) throw Exception('Cancel failed');
    return CarePartnership.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }

  Future<CarePartnership> complete(int id) async {
    final res = await _api.post('/api/care-partnerships/$id/complete', {});
    if (res.statusCode != 200) throw Exception('Complete failed');
    return CarePartnership.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }

  Future<List<CarePartnershipProgress>> listProgress(int id) async {
    final res = await _api.get('/api/care-partnerships/$id/progress');
    if (res.statusCode != 200) throw Exception('Failed to load progress');
    final list = jsonDecode(res.body) as List<dynamic>;
    return list
        .map((e) => CarePartnershipProgress.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<CarePartnershipProgress> addProgress(int id, String body) async {
    final res = await _api.post('/api/care-partnerships/$id/progress', {
      'body': body,
    });
    if (res.statusCode != 200) throw Exception('Failed to post progress');
    return CarePartnershipProgress.fromJson(
      jsonDecode(res.body) as Map<String, dynamic>,
    );
  }
}
