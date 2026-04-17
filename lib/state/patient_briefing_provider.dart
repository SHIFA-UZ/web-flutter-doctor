import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_context_provider.dart';

enum BriefingStatus { idle, loading, success, error }

class PatientBriefingState {
  final String? patientId;
  final String? patientName;
  final BriefingStatus status;
  final String? briefingText;
  final int documentCount;
  final int appointmentCount;
  final String? errorMessage;

  const PatientBriefingState({
    this.patientId,
    this.patientName,
    this.status = BriefingStatus.idle,
    this.briefingText,
    this.documentCount = 0,
    this.appointmentCount = 0,
    this.errorMessage,
  });

  bool get isVisible =>
      status != BriefingStatus.idle || (briefingText != null && briefingText!.isNotEmpty);
  bool get isLoading => status == BriefingStatus.loading;
  bool get isSuccess => status == BriefingStatus.success;
  bool get isError => status == BriefingStatus.error;
}

class PatientBriefingNotifier extends StateNotifier<PatientBriefingState> {
  PatientBriefingNotifier(this._ref, this._aiApi, this._languageCode) : super(const PatientBriefingState());

  final Ref _ref;
  final AiApi _aiApi;
  final String _languageCode;

  /// Map app locale to backend language param (en, uz, ru).
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

  /// Start generating briefing for the given patient. Panel will show loading then result.
  Future<void> generate(String patientId, String patientName) async {
    state = PatientBriefingState(
      patientId: patientId,
      patientName: patientName,
      status: BriefingStatus.loading,
    );
    try {
      final result = await _aiApi.fetchPatientBriefing(
        patientId,
        language: _briefingLanguage,
      );
      state = PatientBriefingState(
        patientId: patientId,
        patientName: patientName,
        status: BriefingStatus.success,
        briefingText: result.briefing,
        documentCount: result.documentCount,
        appointmentCount: result.appointmentCount,
      );
      _ref.read(patientBriefingContextProvider.notifier).putFromFetch(
            patientId,
            briefingText: result.briefing,
            documentCount: result.documentCount,
            appointmentCount: result.appointmentCount,
          );
    } on AiStreamException catch (e) {
      state = PatientBriefingState(
        patientId: patientId,
        patientName: patientName,
        status: BriefingStatus.error,
        errorMessage: e.message,
      );
    } catch (e) {
      state = PatientBriefingState(
        patientId: patientId,
        patientName: patientName,
        status: BriefingStatus.error,
        errorMessage: e.toString(),
      );
    }
  }

  void close() {
    state = const PatientBriefingState();
  }

  void collapse() {
    // Keep state but UI can track expanded in widget
  }
}

final patientBriefingProvider =
    StateNotifierProvider<PatientBriefingNotifier, PatientBriefingState>((ref) {
  final aiApi = ref.watch(aiApiProvider);
  final locale = ref.watch(languageProvider).locale;
  return PatientBriefingNotifier(ref, aiApi, locale.languageCode);
});
