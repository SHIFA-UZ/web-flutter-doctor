import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';

/// Cached patient briefing text for **Ask Shifa AI context only**.
/// Fetched via API without touching [patientBriefingProvider], so the
/// Patient Briefing panel does not open or show loading.
class PatientBriefingContextEntry {
  final String briefingText;
  final int documentCount;
  final int appointmentCount;

  const PatientBriefingContextEntry({
    required this.briefingText,
    this.documentCount = 0,
    this.appointmentCount = 0,
  });

  bool get hasText => briefingText.trim().isNotEmpty;
}

class PatientBriefingContextNotifier extends StateNotifier<Map<String, PatientBriefingContextEntry>> {
  PatientBriefingContextNotifier(this._aiApi, this._languageCode) : super({});

  final AiApi _aiApi;
  final String _languageCode;

  final Map<String, Future<void>> _inFlight = {};

  String get _briefingLanguage {
    switch (_languageCode) {
      case 'uz':
        return 'uz';
      case 'ru':
        return 'ru';
      default:
        return 'en';
    }
  }

  /// Loads briefing for [patientId] if not already cached. No UI side effects.
  Future<void> ensureLoaded(String patientId) async {
    final existing = state[patientId];
    if (existing != null && existing.hasText) return;

    if (_inFlight.containsKey(patientId)) {
      await _inFlight[patientId];
      return;
    }

    final future = _fetch(patientId);
    _inFlight[patientId] = future;
    try {
      await future;
    } finally {
      _inFlight.remove(patientId);
    }
  }

  Future<void> _fetch(String patientId) async {
    try {
      final result = await _aiApi.fetchPatientBriefing(
        patientId,
        language: _briefingLanguage,
      );
      putFromFetch(
        patientId,
        briefingText: result.briefing,
        documentCount: result.documentCount,
        appointmentCount: result.appointmentCount,
      );
    } on AiStreamException {
      // Leave cache empty; Ask Shifa will fall back to demographics-only context.
    } catch (_) {}
  }

  /// Called when the foreground Patient Briefing panel finishes a successful generate,
  /// or internally after silent fetch — keeps Ask Shifa context in sync without extra API calls.
  void putFromFetch(
    String patientId, {
    required String briefingText,
    int documentCount = 0,
    int appointmentCount = 0,
  }) {
    state = {
      ...state,
      patientId: PatientBriefingContextEntry(
        briefingText: briefingText.trim(),
        documentCount: documentCount,
        appointmentCount: appointmentCount,
      ),
    };
  }

  void clearPatient(String patientId) {
    final next = Map<String, PatientBriefingContextEntry>.from(state);
    next.remove(patientId);
    state = next;
  }

  void clearAll() {
    state = {};
  }
}

final patientBriefingContextProvider =
    StateNotifierProvider<PatientBriefingContextNotifier, Map<String, PatientBriefingContextEntry>>((ref) {
  final aiApi = ref.watch(aiApiProvider);
  final locale = ref.watch(languageProvider).locale;
  return PatientBriefingContextNotifier(aiApi, locale.languageCode);
});
