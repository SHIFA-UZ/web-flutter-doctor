class Patient {
  final String id;
  final String name;
  final PatientGeneral general;
  final List<PatientDocument> documents;
  final String? avatar;

  Patient({
    required this.id,
    required this.name,
    required this.general,
    required this.documents,
    this.avatar,
  });

  Patient copyWith({
    String? id,
    String? name,
    PatientGeneral? general,
    List<PatientDocument>? documents,
    String? avatar,
  }) {
    return Patient(
      id: id ?? this.id,
      name: name ?? this.name,
      general: general ?? this.general,
      documents: documents ?? List<PatientDocument>.from(this.documents),
      avatar: avatar ?? this.avatar,
    );
  }
}

class PatientGeneral {
  final DateTime? birthDate;
  final String? phone;
  final String? email;
  final String? address;
  final String? language;

  const PatientGeneral({
    this.birthDate,
    this.phone,
    this.email,
    this.address,
    this.language,
  });
}

class PatientDocument {
  final String id;
  final String title;
  final DateTime date;
  final String? filePath;

  PatientDocument({
    required this.id,
    required this.title,
    required this.date,
    this.filePath,
  });
}
