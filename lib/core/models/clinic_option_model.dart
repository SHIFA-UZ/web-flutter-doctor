class ClinicOption {
  final int id;
  final String name;

  const ClinicOption({
    required this.id,
    required this.name,
  });

  factory ClinicOption.fromJson(Map<String, dynamic> json) {
    return ClinicOption(
      id: (json['id'] as num).toInt(),
      name: json['name'] as String? ?? '',
    );
  }

  bool matches(String query) {
    if (query.isEmpty) return true;
    return name.toLowerCase().contains(query.toLowerCase());
  }
}
