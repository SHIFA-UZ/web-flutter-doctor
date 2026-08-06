/// Maps doctor profession strings to appointment documentation templates.
///
/// Current templates:
/// - [DocumentationTemplate.dental] — teeth diagram / dental visit panel
/// - [DocumentationTemplate.general] — free-text notes (+ optional 025-2)
///
/// Add future specialty templates here (same switch pattern used by
/// in-person and video consult screens) without rewriting those screens.
enum DocumentationTemplate {
  general,
  dental,
}

/// Resolve which documentation template a profession should open by default.
DocumentationTemplate documentationTemplateForProfession(String? profession) {
  if (isDentalDocumentationProfession(profession)) {
    return DocumentationTemplate.dental;
  }
  return DocumentationTemplate.general;
}

/// Professions that use the dental visit documentation template (tooth chart + services + totals).
///
/// Matches EN catalog names and common UZ/RU variants used in doctor profiles.
bool isDentalDocumentationProfession(String? profession) {
  if (profession == null || profession.trim().isEmpty) return false;
  final p = profession.trim().toLowerCase();
  if (p.contains('dental')) return true;
  if (p.contains('odont')) return true; // odontology / stomatology roots
  if (p.contains('stomat')) return true; // RU: стоматолог / stomatolog
  if (p.contains('tish')) return true; // UZ: tish shifokori
  const keys = <String>{
    'dentist',
    'orthodontist',
    'endodontist',
    'pediatric dentist',
    'child dentist',
    'children dentist',
    'periodontist',
    'prosthodontist',
    'oral surgeon',
    'oral therapist',
    'oral therapy',
    'maxillofacial surgeon',
    'maxillo-facial',
    'dental hygienist',
    'dental therapist',
    'stomatologist',
    'стоматолог',
    'ортодонт',
    'пародонтолог',
    'хирург-стоматолог',
    'детский стоматолог',
  };
  for (final k in keys) {
    if (p == k || p.contains(k)) return true;
  }
  return false;
}
