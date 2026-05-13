/// Professions that use the dental visit documentation template (tooth chart + services + totals).
bool isDentalDocumentationProfession(String? profession) {
  if (profession == null || profession.trim().isEmpty) return false;
  final p = profession.trim().toLowerCase();
  const keys = <String>{
    'dentist',
    'orthodontist',
    'pediatric dentist',
    'periodontist',
    'prosthodontist',
    'oral surgeon',
    'maxillofacial surgeon',
  };
  for (final k in keys) {
    if (p == k || p.contains(k)) return true;
  }
  return false;
}
