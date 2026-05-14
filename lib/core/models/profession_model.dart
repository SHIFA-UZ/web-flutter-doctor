// Profession model with English and Uzbek translations

import 'dart:ui' show Locale;

import 'package:shifa_doc_app_v1/core/localization/uzbek_latin_to_cyrillic.dart';

class ProfessionModel {
  final String english;
  final String uzbek;

  const ProfessionModel({
    required this.english,
    required this.uzbek,
  });

  // Get display text based on language - show only one language
  String getDisplayText(Locale? locale) {
    if (locale == null) return english;
    if (locale.languageCode != 'uz') return english;
    if ((locale.scriptCode ?? '') == 'Cyrl') {
      return transliterateUzbekLatinToCyrillicUi(uzbek);
    }
    return uzbek;
  }

  // Search helper - checks both English and Uzbek
  bool matches(String query) {
    final lowerQuery = query.toLowerCase();
    return english.toLowerCase().contains(lowerQuery) ||
        uzbek.toLowerCase().contains(lowerQuery);
  }
}

class ProfessionData {
  static const List<ProfessionModel> allProfessions = [
    // 🩺 General & Primary Care
    ProfessionModel(english: 'General Practitioner (GP)', uzbek: 'Umumiy amaliyot shifokori'),
    ProfessionModel(english: 'Family Physician', uzbek: 'Oilaviy shifokor'),
    ProfessionModel(english: 'Internist (Internal Medicine)', uzbek: 'Terapevt'),
    ProfessionModel(english: 'Pediatrician', uzbek: 'Pediatr'),
    ProfessionModel(english: 'Geriatrician', uzbek: 'Geriatr (keksalar shifokori)'),

    // 🫀 Medical (Non-Surgical) Specialties
    ProfessionModel(english: 'Cardiologist', uzbek: 'Kardiolog'),
    ProfessionModel(english: 'Endocrinologist', uzbek: 'Endokrinolog'),
    ProfessionModel(english: 'Gastroenterologist', uzbek: 'Gastroenterolog'),
    ProfessionModel(english: 'Pulmonologist', uzbek: 'Pulmonolog'),
    ProfessionModel(english: 'Nephrologist', uzbek: 'Nefrolog'),
    ProfessionModel(english: 'Hematologist', uzbek: 'Gematolog'),
    ProfessionModel(english: 'Rheumatologist', uzbek: 'Revmatolog'),
    ProfessionModel(english: 'Allergist / Immunologist', uzbek: 'Allergolog / Immunolog'),
    ProfessionModel(english: 'Infectious Disease Specialist', uzbek: 'Infeksionist'),
    ProfessionModel(english: 'Oncologist', uzbek: 'Onkolog'),
    ProfessionModel(english: 'Neurologist', uzbek: 'Nevrolog'),
    ProfessionModel(english: 'Psychiatrist', uzbek: 'Psixiatr'),
    ProfessionModel(english: 'Dermatologist', uzbek: 'Dermatolog'),
    ProfessionModel(english: 'Venereologist', uzbek: 'Venerolog'),
    ProfessionModel(english: 'Phthisiatrician (TB specialist)', uzbek: 'Ftiziatr'),
    ProfessionModel(english: 'Toxicologist', uzbek: 'Toksikolog'),
    ProfessionModel(english: 'Sports Medicine Doctor', uzbek: 'Sport shifokori'),
    ProfessionModel(english: 'Occupational Medicine Specialist', uzbek: 'Mehnat tibbiyoti shifokori'),

    // 🧠 Mental Health & Behavioral
    ProfessionModel(english: 'Psychotherapist', uzbek: 'Psixoterapevt'),
    ProfessionModel(english: 'Clinical Psychologist', uzbek: 'Klinik psixolog'),
    ProfessionModel(english: 'Child Psychiatrist', uzbek: 'Bolalar psixiatri'),

    // 🩻 Diagnostic & Laboratory
    ProfessionModel(english: 'Radiologist', uzbek: 'Radiolog'),
    ProfessionModel(english: 'Ultrasound Doctor (Sonographer)', uzbek: 'UZI shifokori'),
    ProfessionModel(english: 'Pathologist', uzbek: 'Patolog'),
    ProfessionModel(english: 'Laboratory Doctor', uzbek: 'Laboratoriya shifokori'),
    ProfessionModel(english: 'Nuclear Medicine Specialist', uzbek: 'Yadro tibbiyoti shifokori'),
    ProfessionModel(english: 'Functional Diagnostics Doctor', uzbek: 'Funksional diagnostika shifokori'),

    // 🔪 Surgical Specialties
    ProfessionModel(english: 'General Surgeon', uzbek: 'Umumiy jarroh'),
    ProfessionModel(english: 'Cardiothoracic Surgeon', uzbek: 'Yurak-ko\'krak jarrohi'),
    ProfessionModel(english: 'Neurosurgeon', uzbek: 'Neyroxirurg'),
    ProfessionModel(english: 'Orthopedic Surgeon', uzbek: 'Ortoped-jarroh'),
    ProfessionModel(english: 'Trauma Surgeon', uzbek: 'Travmatolog'),
    ProfessionModel(english: 'Plastic Surgeon', uzbek: 'Plastik jarroh'),
    ProfessionModel(english: 'Vascular Surgeon', uzbek: 'Qon tomir jarrohi'),
    ProfessionModel(english: 'Pediatric Surgeon', uzbek: 'Bolalar jarrohi'),
    ProfessionModel(english: 'Oncologic Surgeon', uzbek: 'Onkojarroh'),
    ProfessionModel(english: 'Maxillofacial Surgeon', uzbek: 'Yuz-jag\' jarrohi'),
    ProfessionModel(english: 'Transplant Surgeon', uzbek: 'Transplantolog'),

    // 👶 Women's & Reproductive Health
    ProfessionModel(english: 'Gynecologist', uzbek: 'Ginekolog'),
    ProfessionModel(english: 'Obstetrician', uzbek: 'Akusher'),
    ProfessionModel(english: 'Obstetrician-Gynecologist (OB-GYN)', uzbek: 'Akusher-ginekolog'),
    ProfessionModel(english: 'Reproductive Medicine Specialist', uzbek: 'Reproduktolog'),
    ProfessionModel(english: 'Mammologist', uzbek: 'Mammolog'),

    // 👁️👂 ENT & Senses
    ProfessionModel(english: 'Ophthalmologist', uzbek: 'Oftalmolog (ko\'z shifokori)'),
    ProfessionModel(english: 'Otolaryngologist (ENT)', uzbek: 'LOR shifokori'),
    ProfessionModel(english: 'Audiologist', uzbek: 'Audiolog'),
    ProfessionModel(english: 'Phoniatrician', uzbek: 'Foniatr'),

    // 🦷 Dental Specialties
    ProfessionModel(english: 'Dentist', uzbek: 'Stomatolog'),
    ProfessionModel(english: 'Orthodontist', uzbek: 'Ortodont'),
    ProfessionModel(english: 'Oral Surgeon', uzbek: 'Og\'iz bo\'shlig\'i jarrohi'),
    ProfessionModel(english: 'Periodontist', uzbek: 'Parodontolog'),
    ProfessionModel(english: 'Prosthodontist', uzbek: 'Ortopedik stomatolog'),
    ProfessionModel(english: 'Pediatric Dentist', uzbek: 'Bolalar stomatologi'),

    // 🧒 Children & Development
    ProfessionModel(english: 'Neonatologist', uzbek: 'Neonatolog'),
    ProfessionModel(english: 'Pediatric Neurologist', uzbek: 'Bolalar nevrologi'),
    ProfessionModel(english: 'Pediatric Cardiologist', uzbek: 'Bolalar kardiologi'),
    ProfessionModel(english: 'Developmental Pediatrician', uzbek: 'Rivojlanish pediatri'),
    ProfessionModel(english: 'Speech Therapist', uzbek: 'Logoped'),

    // 🚑 Emergency & Intensive Care
    ProfessionModel(english: 'Emergency Physician', uzbek: 'Shoshilinch tibbiyot shifokori'),
    ProfessionModel(english: 'Intensivist (ICU Doctor)', uzbek: 'Reanimatolog'),
    ProfessionModel(english: 'Anesthesiologist', uzbek: 'Anesteziolog'),
    ProfessionModel(english: 'Anesthesiologist-Resuscitator', uzbek: 'Anesteziolog-reanimatolog'),

    // 🧬 Other Important Specialties
    ProfessionModel(english: 'Geneticist', uzbek: 'Genetik'),
    ProfessionModel(english: 'Epidemiologist', uzbek: 'Epidemiolog'),
    ProfessionModel(english: 'Public Health Specialist', uzbek: 'Jamoat salomatligi mutaxassisi'),
    ProfessionModel(english: 'Rehabilitation Doctor', uzbek: 'Reabilitolog'),
    ProfessionModel(english: 'Physiotherapist', uzbek: 'Fizioterapevt'),
    ProfessionModel(english: 'Manual Therapist', uzbek: 'Manual terapevt'),
    ProfessionModel(english: 'Nutritionist / Dietitian', uzbek: 'Dietolog'),
    ProfessionModel(english: 'Palliative Care Specialist', uzbek: 'Palliativ yordam shifokori'),
    ProfessionModel(english: 'Sleep Medicine Specialist', uzbek: 'Uyqu bo\'yicha mutaxassis'),
  ];

  // Find profession by English name (for backward compatibility)
  static ProfessionModel? findByEnglish(String english) {
    try {
      return allProfessions.firstWhere(
        (p) => p.english == english,
      );
    } catch (e) {
      return null;
    }
  }

  // Find profession by any matching text (English or Uzbek)
  static List<ProfessionModel> search(String query) {
    if (query.isEmpty) return allProfessions;
    final lowerQuery = query.toLowerCase();
    return allProfessions.where((p) => p.matches(lowerQuery)).toList();
  }
}
