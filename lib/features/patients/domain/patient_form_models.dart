class PatientForm {
  final String? id; // null for new forms, set when saved
  final String patientId;
  final String templateId; // e.g., '025-2'
  final DateTime date;
  final String fullName;
  final String? gender;
  final String? address;
  final int? age;
  final String? job;
  final String? diagnosis;
  final String? diagnosisCode;
  final String? diagnosisDisplay;
  final String? diagnosisSystem;
  final String? complaints;
  final String? otherIllnesses;
  final String? moreDetails;
  final String? visualCheckup;
  final String? occlusion;
  final String? oralCavityCondition;
  final String? xrayLabData;
  final String? treatment;
  final String? treatmentResult;
  final String? recommendations;
  final String? doctorName;
  final String? doctorClinic;
  final int? formNumber;
  /// Dental chart: per-tooth codes use ISO 3950 keys ("11"…"48") plus optional legacy UR/UL/LR/LL keys;
  /// jaw rows use TOP_{row}_{cell}, BOTTOM_{row}_{cell}; past visits use TOP_HIST_*, BOTTOM_HIST_* with _date/_doctor.
  final Map<String, String> dentalChart;
  final List<PatientFormFollowup> followups;
  final String? documentId; // Link to PDF document if exists
  final bool signatureRequested;
  final String? patientSignedAt;
  final String? patientSignatureImageBase64;

  PatientForm({
    this.id,
    required this.patientId,
    required this.templateId,
    required this.date,
    required this.fullName,
    this.gender,
    this.address,
    this.age,
    this.job,
    this.diagnosis,
    this.diagnosisCode,
    this.diagnosisDisplay,
    this.diagnosisSystem,
    this.complaints,
    this.otherIllnesses,
    this.moreDetails,
    this.visualCheckup,
    this.occlusion,
    this.oralCavityCondition,
    this.xrayLabData,
    this.treatment,
    this.treatmentResult,
    this.recommendations,
    this.doctorName,
    this.doctorClinic,
    this.formNumber,
    Map<String, String>? dentalChart,
    List<PatientFormFollowup>? followups,
    this.documentId,
    this.signatureRequested = false,
    this.patientSignedAt,
    this.patientSignatureImageBase64,
  })  : dentalChart = dentalChart ?? const {},
        followups = followups ?? const [];

  PatientForm copyWith({
    String? id,
    String? patientId,
    String? templateId,
    DateTime? date,
    String? fullName,
    String? gender,
    String? address,
    int? age,
    String? job,
    String? diagnosis,
    String? diagnosisCode,
    String? diagnosisDisplay,
    String? diagnosisSystem,
    String? complaints,
    String? otherIllnesses,
    String? moreDetails,
    String? visualCheckup,
    String? occlusion,
    String? oralCavityCondition,
    String? xrayLabData,
    String? treatment,
    String? treatmentResult,
    String? recommendations,
    String? doctorName,
    String? doctorClinic,
    int? formNumber,
    Map<String, String>? dentalChart,
    List<PatientFormFollowup>? followups,
    String? documentId,
    bool? signatureRequested,
    String? patientSignedAt,
    String? patientSignatureImageBase64,
  }) {
    return PatientForm(
      id: id ?? this.id,
      patientId: patientId ?? this.patientId,
      templateId: templateId ?? this.templateId,
      date: date ?? this.date,
      fullName: fullName ?? this.fullName,
      gender: gender ?? this.gender,
      address: address ?? this.address,
      age: age ?? this.age,
      job: job ?? this.job,
      diagnosis: diagnosis ?? this.diagnosis,
      diagnosisCode: diagnosisCode ?? this.diagnosisCode,
      diagnosisDisplay: diagnosisDisplay ?? this.diagnosisDisplay,
      diagnosisSystem: diagnosisSystem ?? this.diagnosisSystem,
      complaints: complaints ?? this.complaints,
      otherIllnesses: otherIllnesses ?? this.otherIllnesses,
      moreDetails: moreDetails ?? this.moreDetails,
      visualCheckup: visualCheckup ?? this.visualCheckup,
      occlusion: occlusion ?? this.occlusion,
      oralCavityCondition: oralCavityCondition ?? this.oralCavityCondition,
      xrayLabData: xrayLabData ?? this.xrayLabData,
      treatment: treatment ?? this.treatment,
      treatmentResult: treatmentResult ?? this.treatmentResult,
      recommendations: recommendations ?? this.recommendations,
      doctorName: doctorName ?? this.doctorName,
      doctorClinic: doctorClinic ?? this.doctorClinic,
      formNumber: formNumber ?? this.formNumber,
      dentalChart: dentalChart ?? this.dentalChart,
      followups: followups ?? this.followups,
      documentId: documentId ?? this.documentId,
      signatureRequested: signatureRequested ?? this.signatureRequested,
      patientSignedAt: patientSignedAt ?? this.patientSignedAt,
      patientSignatureImageBase64:
          patientSignatureImageBase64 ?? this.patientSignatureImageBase64,
    );
  }

  Map<String, dynamic> toJson() {
    // Format date as YYYY-MM-DD for backend (LocalDate format)
    String formatDate(DateTime date) {
      return '${date.year.toString().padLeft(4, '0')}-'
          '${date.month.toString().padLeft(2, '0')}-'
          '${date.day.toString().padLeft(2, '0')}';
    }

    return {
      if (id != null) 'id': int.parse(id!),
      'patientId': int.parse(patientId),
      'templateId': templateId,
      'date': formatDate(date),
      'fullName': fullName,
      if (gender != null) 'gender': gender,
      if (address != null) 'address': address,
      if (age != null) 'age': age,
      if (job != null) 'job': job,
      if (diagnosis != null) 'diagnosis': diagnosis,
      if (diagnosisCode != null) 'diagnosisCode': diagnosisCode,
      if (diagnosisDisplay != null) 'diagnosisDisplay': diagnosisDisplay,
      if (diagnosisSystem != null) 'diagnosisSystem': diagnosisSystem,
      if (complaints != null) 'complaints': complaints,
      if (otherIllnesses != null) 'otherIllnesses': otherIllnesses,
      if (moreDetails != null) 'moreDetails': moreDetails,
      if (visualCheckup != null) 'visualCheckup': visualCheckup,
      if (occlusion != null) 'occlusion': occlusion,
      if (oralCavityCondition != null) 'oralCavityCondition': oralCavityCondition,
      if (xrayLabData != null) 'xrayLabData': xrayLabData,
      if (treatment != null) 'treatment': treatment,
      if (treatmentResult != null) 'treatmentResult': treatmentResult,
      if (recommendations != null) 'recommendations': recommendations,
      if (doctorName != null) 'doctorName': doctorName,
      if (doctorClinic != null) 'doctorClinic': doctorClinic,
      if (formNumber != null) 'formNumber': formNumber,
      if (dentalChart.isNotEmpty) 'dentalChart': dentalChart,
      if (followups.isNotEmpty)
        'followups': followups.map((f) => f.toJson()).toList(),
      if (documentId != null) 'documentId': int.parse(documentId!),
    };
  }

  factory PatientForm.fromJson(Map<String, dynamic> json) {
    // Handle date format - backend returns LocalDate as "YYYY-MM-DD"
    DateTime parseDate(String dateStr) {
      try {
        // Try parsing as ISO8601 first
        return DateTime.parse(dateStr);
      } catch (e) {
        // If that fails, try parsing as LocalDate format (YYYY-MM-DD)
        final parts = dateStr.split('-');
        if (parts.length == 3) {
          return DateTime(
            int.parse(parts[0]),
            int.parse(parts[1]),
            int.parse(parts[2]),
          );
        }
        throw FormatException('Invalid date format: $dateStr');
      }
    }

    final dentalChartRaw = json['dentalChart'];
    final parsedDentalChart = <String, String>{};
    if (dentalChartRaw is Map) {
      dentalChartRaw.forEach((k, v) {
        if (k != null && v != null) {
          parsedDentalChart[k.toString()] = v.toString();
        }
      });
    }

    final followupsRaw = json['followups'];
    final parsedFollowups = <PatientFormFollowup>[];
    if (followupsRaw is List) {
      for (final item in followupsRaw) {
        if (item is Map) {
          parsedFollowups.add(PatientFormFollowup.fromJson(Map<String, dynamic>.from(item)));
        }
      }
    }

    return PatientForm(
      id: json['id']?.toString(),
      patientId: json['patientId'].toString(),
      templateId: json['templateId'] as String,
      date: parseDate(json['date'] as String),
      fullName: json['fullName'] as String,
      gender: json['gender'] as String?,
      address: json['address'] as String?,
      age: json['age'] as int?,
      job: json['job'] as String?,
      diagnosis: json['diagnosis'] as String?,
      diagnosisCode: json['diagnosisCode'] as String?,
      diagnosisDisplay: json['diagnosisDisplay'] as String?,
      diagnosisSystem: json['diagnosisSystem'] as String?,
      complaints: json['complaints'] as String?,
      otherIllnesses: json['otherIllnesses'] as String?,
      moreDetails: json['moreDetails'] as String?,
      visualCheckup: json['visualCheckup'] as String?,
      occlusion: json['occlusion'] as String?,
      oralCavityCondition: json['oralCavityCondition'] as String?,
      xrayLabData: json['xrayLabData'] as String?,
      treatment: json['treatment'] as String?,
      treatmentResult: json['treatmentResult'] as String?,
      recommendations: json['recommendations'] as String?,
      doctorName: json['doctorName'] as String?,
      doctorClinic: json['doctorClinic'] as String?,
      formNumber: (json['formNumber'] is int)
          ? json['formNumber'] as int
          : int.tryParse((json['formNumber'] ?? '').toString()),
      dentalChart: parsedDentalChart,
      followups: parsedFollowups,
      documentId: json['documentId']?.toString(),
      signatureRequested: json['signatureRequested'] == true,
      patientSignedAt: json['patientSignedAt']?.toString(),
      patientSignatureImageBase64: json['patientSignatureImageBase64']?.toString(),
    );
  }
}

class PatientFormFollowup {
  final DateTime date;
  final String clinicalFindings;
  final String? doctorName;

  const PatientFormFollowup({
    required this.date,
    required this.clinicalFindings,
    this.doctorName,
  });

  Map<String, dynamic> toJson() {
    String formatDate(DateTime date) {
      return '${date.year.toString().padLeft(4, '0')}-'
          '${date.month.toString().padLeft(2, '0')}-'
          '${date.day.toString().padLeft(2, '0')}';
    }

    return {
      'date': formatDate(date),
      'clinicalFindings': clinicalFindings,
      if (doctorName != null) 'doctorName': doctorName,
    };
  }

  factory PatientFormFollowup.fromJson(Map<String, dynamic> json) {
    DateTime parseDate(String dateStr) {
      final parts = dateStr.split('-');
      if (parts.length == 3) {
        return DateTime(
          int.parse(parts[0]),
          int.parse(parts[1]),
          int.parse(parts[2]),
        );
      }
      return DateTime.tryParse(dateStr) ?? DateTime.now();
    }

    return PatientFormFollowup(
      date: parseDate((json['date'] ?? '').toString()),
      clinicalFindings: (json['clinicalFindings'] ?? '').toString(),
      doctorName: json['doctorName']?.toString(),
    );
  }
}

class FormTemplate {
  final String id;
  final String name;

  const FormTemplate({
    required this.id,
    required this.name,
  });
}

// Available form templates
const List<FormTemplate> formTemplates = [
  FormTemplate(id: '025-2', name: '025-2'),
  // Add more templates as needed
];
