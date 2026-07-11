class ClinicalLocalizedName {
  final String ru;
  final String uz;
  final String en;

  const ClinicalLocalizedName({
    required this.ru,
    required this.uz,
    required this.en,
  });

  factory ClinicalLocalizedName.fromJson(Map<String, dynamic> json) {
    return ClinicalLocalizedName(
      ru: (json['ru'] ?? '').toString(),
      uz: (json['uz'] ?? '').toString(),
      en: (json['en'] ?? '').toString(),
    );
  }

  String forLocale(String locale) {
    switch (locale.toLowerCase().substring(0, locale.length >= 2 ? 2 : locale.length)) {
      case 'uz':
        return uz.isNotEmpty ? uz : ru;
      case 'en':
        return en.isNotEmpty ? en : ru;
      default:
        return ru;
    }
  }
}

class ClinicalGroup {
  final String groupId;
  final int sortOrder;
  final ClinicalLocalizedName names;

  const ClinicalGroup({
    required this.groupId,
    required this.sortOrder,
    required this.names,
  });

  factory ClinicalGroup.fromJson(Map<String, dynamic> json) {
    return ClinicalGroup(
      groupId: json['groupId'] as String,
      sortOrder: (json['sortOrder'] as num?)?.toInt() ?? 0,
      names: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['names'] as Map),
      ),
    );
  }
}

class ClinicalDiseaseSummary {
  final String diseaseId;
  final int number;
  final String groupId;
  final String slug;
  final List<String> icdCodes;
  final ClinicalLocalizedName names;

  const ClinicalDiseaseSummary({
    required this.diseaseId,
    required this.number,
    required this.groupId,
    required this.slug,
    required this.icdCodes,
    required this.names,
  });

  factory ClinicalDiseaseSummary.fromJson(Map<String, dynamic> json) {
    return ClinicalDiseaseSummary(
      diseaseId: json['diseaseId'] as String,
      number: (json['number'] as num?)?.toInt() ?? 0,
      groupId: json['groupId'] as String,
      slug: json['slug'] as String,
      icdCodes: (json['icdCodes'] as List<dynamic>? ?? [])
          .map((e) => e.toString())
          .toList(),
      names: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['names'] as Map),
      ),
    );
  }
}

class ClinicalChip {
  final String chipId;
  final String field;
  final ClinicalLocalizedName labels;
  final List<String> variables;
  final int priority;

  const ClinicalChip({
    required this.chipId,
    required this.field,
    required this.labels,
    required this.variables,
    required this.priority,
  });

  factory ClinicalChip.fromJson(Map<String, dynamic> json) {
    return ClinicalChip(
      chipId: json['chipId'] as String,
      field: json['field'] as String,
      labels: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['labels'] as Map),
      ),
      variables: (json['variables'] as List<dynamic>? ?? [])
          .map((e) => e.toString())
          .toList(),
      priority: (json['priority'] as num?)?.toInt() ?? 50,
    );
  }
}

class ClinicalDiseaseDetail extends ClinicalDiseaseSummary {
  final List<ClinicalChip> chips;

  const ClinicalDiseaseDetail({
    required super.diseaseId,
    required super.number,
    required super.groupId,
    required super.slug,
    required super.icdCodes,
    required super.names,
    required this.chips,
  });

  factory ClinicalDiseaseDetail.fromJson(Map<String, dynamic> json) {
    return ClinicalDiseaseDetail(
      diseaseId: json['diseaseId'] as String,
      number: (json['number'] as num?)?.toInt() ?? 0,
      groupId: json['groupId'] as String,
      slug: json['slug'] as String,
      icdCodes: (json['icdCodes'] as List<dynamic>? ?? [])
          .map((e) => e.toString())
          .toList(),
      names: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['names'] as Map),
      ),
      chips: (json['chips'] as List<dynamic>? ?? [])
          .map((e) => ClinicalChip.fromJson(e as Map<String, dynamic>))
          .toList(),
    );
  }
}

class ClinicalOcclusionChip {
  final String chipId;
  final String? angleClass;
  final String? icdHint;
  final ClinicalLocalizedName labels;
  final List<String> variables;
  final int priority;

  const ClinicalOcclusionChip({
    required this.chipId,
    this.angleClass,
    this.icdHint,
    required this.labels,
    required this.variables,
    required this.priority,
  });

  factory ClinicalOcclusionChip.fromJson(Map<String, dynamic> json) {
    return ClinicalOcclusionChip(
      chipId: json['chipId'] as String,
      angleClass: json['angleClass'] as String?,
      icdHint: json['icdHint'] as String?,
      labels: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['labels'] as Map),
      ),
      variables: (json['variables'] as List<dynamic>? ?? [])
          .map((e) => e.toString())
          .toList(),
      priority: (json['priority'] as num?)?.toInt() ?? 50,
    );
  }
}

class ClinicalTopDiagnosis {
  final String diseaseId;
  final ClinicalLocalizedName names;
  final List<String> icdCodes;
  final int useCount;
  final String? lastUsedAt;

  const ClinicalTopDiagnosis({
    required this.diseaseId,
    required this.names,
    required this.icdCodes,
    required this.useCount,
    this.lastUsedAt,
  });

  factory ClinicalTopDiagnosis.fromJson(Map<String, dynamic> json) {
    return ClinicalTopDiagnosis(
      diseaseId: json['diseaseId'] as String,
      names: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['names'] as Map),
      ),
      icdCodes: (json['icdCodes'] as List<dynamic>? ?? [])
          .map((e) => e.toString())
          .toList(),
      useCount: (json['useCount'] as num?)?.toInt() ?? 0,
      lastUsedAt: json['lastUsedAt'] as String?,
    );
  }
}

class ClinicalChipSelection {
  final String chipId;
  final Map<String, String> variables;

  const ClinicalChipSelection({
    required this.chipId,
    this.variables = const {},
  });

  Map<String, dynamic> toJson() => {
        'chipId': chipId,
        'variables': variables,
      };

  factory ClinicalChipSelection.fromJson(Map<String, dynamic> json) {
    final varsRaw = json['variables'];
    final vars = <String, String>{};
    if (varsRaw is Map) {
      varsRaw.forEach((k, v) {
        if (k != null && v != null) vars[k.toString()] = v.toString();
      });
    }
    return ClinicalChipSelection(
      chipId: json['chipId'] as String,
      variables: vars,
    );
  }
}

class ClinicalSharedTemplate {
  final String templateId;
  final String type;
  final String field;
  final ClinicalLocalizedName labels;
  final int priority;

  const ClinicalSharedTemplate({
    required this.templateId,
    required this.type,
    required this.field,
    required this.labels,
    required this.priority,
  });

  String get chipId => 'shared.$templateId';

  factory ClinicalSharedTemplate.fromJson(Map<String, dynamic> json) {
    return ClinicalSharedTemplate(
      templateId: json['templateId'] as String,
      type: json['type'] as String,
      field: json['field'] as String,
      labels: ClinicalLocalizedName.fromJson(
        Map<String, dynamic>.from(json['labels'] as Map),
      ),
      priority: (json['priority'] as num?)?.toInt() ?? 50,
    );
  }
}

class ClinicalFieldMapping {
  static const complaints = 'complaints';
  static const morbi = 'morbi';
  static const objective = 'objective';
  static const occlusion = 'occlusion';
  static const oralCavity = 'oral_cavity';
  static const xray = 'xray';
  static const treatment1 = 'treatment_1';
  static const treatment2 = 'treatment_2';
  static const recommendations = 'recommendations';

  static const chipFields = [
    complaints,
    morbi,
    objective,
    oralCavity,
    xray,
    treatment1,
    treatment2,
    recommendations,
  ];
}
