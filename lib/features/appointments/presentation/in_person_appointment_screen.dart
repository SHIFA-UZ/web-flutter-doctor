// lib/features/appointments/presentation/in_person_appointment_screen.dart
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';
import 'package:file_picker/file_picker.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_service.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_translations.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/appointment_form_0252_panel.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_forms_provider.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/widgets/inline_voice_recorder_bar.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_speech_text_field.dart';
import 'package:http/http.dart' as http;
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/widgets/consultation_documentation_widgets.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/widgets/consultation_document_upload_strip.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/widgets/consultation_soap_section.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_documentation_professions.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_visit_documentation_panel.dart';

// Provider to fetch patientId from appointment
final _patientIdProvider = FutureProvider.family<String?, String>((
  ref,
  appointmentId,
) async {
  final api = ref.read(apiClientProvider);
  final doctorTimeZone =
      ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;

  try {
    // Use doctor's timezone to determine "today" for consistency
    final today = getTodayInTimezone(doctorTimeZone);
    final ymd =
        '${today.year.toString().padLeft(4, '0')}-'
        '${today.month.toString().padLeft(2, '0')}-'
        '${today.day.toString().padLeft(2, '0')}';
    final res = await api.get('/api/calendar', params: {'day': ymd});
    if (res.statusCode == 200) {
      final List data = json.decode(utf8.decode(res.bodyBytes)) as List;
      final appointment = data.firstWhere(
        (e) =>
            (e['appointmentId'] ?? e['id'] ?? '').toString() == appointmentId,
        orElse: () => null,
      );
      if (appointment != null) {
        return appointment['patientId']?.toString();
      }
    }
  } catch (e) {
    debugPrint('Error fetching patientId: $e');
  }
  return null;
});

class InPersonAppointmentScreen extends ConsumerStatefulWidget {
  const InPersonAppointmentScreen({super.key, required this.appointment});
  final Appointment appointment;

  @override
  ConsumerState<InPersonAppointmentScreen> createState() =>
      _InPersonAppointmentScreenState();
}

class _InPersonAppointmentScreenState
    extends ConsumerState<InPersonAppointmentScreen> {
  final _notesController = TextEditingController();
  final _soapSubjective = TextEditingController();
  final _soapObjective = TextEditingController();
  final _soapAssessment = TextEditingController();
  final _soapPlan = TextEditingController();
  final List<XFile> _beforeTreatmentImages = [];
  final List<XFile> _afterTreatmentImages = [];
  bool _isSaving = false;
  bool _warningShown = false;
  final ImagePicker _imagePicker = ImagePicker();

  bool _signatureRequested = false;
  String? _patientSignedAt;
  String? _patientSignatureImageBase64;
  bool _isRequestingSignature = false;
  Timer? _signaturePollTimer;

  /// 'general' = notes + images; '025-2' = structured dental form
  String _documentationType = 'general';
  bool _hasUnsavedChanges = false;
  /// One-shot: apply [profileAllProvider] default mode when profile first loads.
  bool _documentationProfessionDefaultApplied = false;
  /// After the doctor explicitly picks a mode, do not override from profile.
  bool _userSelectedDocumentationType = false;
  bool _dentalDocumentationFullScreen = false;
  final GlobalKey<DentalVisitDocumentationPanelState> _dentalDocPanelKey =
      GlobalKey<DentalVisitDocumentationPanelState>();

  /// When true, helper sections inside Notes are visible.
  bool _notesSectionsExpanded = false;

  /// Which helper source to show: 'ai' for Shifa AI, '0252' for last 025-2 form, or null for none.
  String? _expandedNoteSource;

  /// When true, inline AI scribe recorder is shown instead of the Start AI Notes button.
  bool _aiInlineVoiceCapture = false;
  final _form0252PanelKey = GlobalKey<AppointmentForm0252PanelState>();
  bool _docPanelCollapsed = false;

  /// Index of the AI output currently shown in the carousel (0-based). Clamped when building.
  int _consultationNoteIndex = 0;

  bool get _hasPatientSignature =>
      _patientSignedAt != null &&
      _patientSignedAt!.trim().isNotEmpty &&
      _patientSignedAt != 'null';

  @override
  void initState() {
    super.initState();
    _notesController.addListener(_markUnsaved);
    _soapSubjective.addListener(_markUnsaved);
    _soapObjective.addListener(_markUnsaved);
    _soapAssessment.addListener(_markUnsaved);
    _soapPlan.addListener(_markUnsaved);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkChronicDiseaseWarning();
      _fetchSignatureStatus();
    });
  }

  void _markUnsaved() {
    if (mounted) setState(() => _hasUnsavedChanges = true);
  }

  void _clearGeneralNoteFields() {
    _notesController.clear();
    _soapSubjective.clear();
    _soapObjective.clear();
    _soapAssessment.clear();
    _soapPlan.clear();
  }

  /// [ref.listen] does not fire when [profileAllProvider] is already loaded; sync from [ref.watch] instead.
  void _applyProfessionDocumentationDefaultIfReady(Map<String, dynamic> profile) {
    if (_documentationProfessionDefaultApplied) return;
    final prof = profile['profession'] as String?;
    if (prof == null || prof.trim().isEmpty) return;
    _documentationProfessionDefaultApplied = true;
    if (_userSelectedDocumentationType) return;
    final mode = isDentalDocumentationProfession(prof) ? 'dental' : 'general';
    if (_documentationType == mode) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      setState(() {
        _documentationType = mode;
        if (mode != 'dental') {
          _dentalDocumentationFullScreen = false;
        }
      });
    });
  }

  Future<void> _openConsultationFocusMode() async {
    if (_documentationType != 'general') return;
    final l10n = AppLocalizations.of(context)!;
    await Navigator.of(context).push<void>(
      MaterialPageRoute<void>(
        fullscreenDialog: true,
        builder: (ctx) => Scaffold(
          appBar: AppBar(
            title: Text(l10n.consultationFocusModeTitle),
            leading: IconButton(
              icon: const Icon(Icons.close),
              onPressed: () => Navigator.pop(ctx),
            ),
          ),
          body: SafeArea(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  ConsultationSoapSection(
                    l10n: l10n,
                    subjective: _soapSubjective,
                    objective: _soapObjective,
                    assessment: _soapAssessment,
                    plan: _soapPlan,
                    onTranscriptAppended: _markUnsaved,
                  ),
                  const SizedBox(height: 12),
                  Expanded(
                    child: Container(
                      padding: const EdgeInsets.all(12),
                      decoration: BoxDecoration(
                        color: const Color(0xFFF9FAFB),
                        borderRadius: BorderRadius.circular(14),
                        border: Border.all(
                          color: Colors.black.withValues(alpha: 0.07),
                        ),
                      ),
                      child: DoctorSpeechTextField(
                        controller: _notesController,
                        style: DoctorSpeechInputStyle.borderlessExpanding,
                        expands: true,
                        maxLines: null,
                        textAlignVertical: TextAlignVertical.top,
                        textStyle: const TextStyle(fontSize: 17, height: 1.45),
                        onTranscriptAppended: _markUnsaved,
                        decoration: InputDecoration(
                          hintText: l10n.enterNotes,
                          border: InputBorder.none,
                          contentPadding: const EdgeInsets.fromLTRB(
                            4,
                            8,
                            44,
                            8,
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _fetchSignatureStatus() async {
    try {
      final api = ref.read(apiClientProvider);
      final res = await api.get('/api/appointments/${widget.appointment.id}');
      if (res.statusCode != 200 || !mounted) return;
      final map =
          json.decode(utf8.decode(res.bodyBytes)) as Map<String, dynamic>?;
      if (map == null) return;
      setState(() {
        _signatureRequested = map['signatureRequested'] == true;
        final rawSignedAt = map['patientSignedAt'];
        final signedAtStr = rawSignedAt is String ? rawSignedAt.trim() : null;
        _patientSignedAt =
            (signedAtStr != null &&
                signedAtStr.isNotEmpty &&
                signedAtStr != 'null')
            ? signedAtStr
            : null;
        final rawImg = map['patientSignatureImageBase64'];
        _patientSignatureImageBase64 =
            (rawImg is String && rawImg.trim().isNotEmpty) ? rawImg : null;
        if (_signatureRequested && _patientSignedAt == null) {
          _signaturePollTimer?.cancel();
          _signaturePollTimer = Timer.periodic(const Duration(seconds: 10), (
            _,
          ) async {
            await _fetchSignatureStatus();
          });
        } else {
          _signaturePollTimer?.cancel();
          _signaturePollTimer = null;
        }
      });
    } catch (e) {
      debugPrint('Fetch signature status: $e');
    }
  }

  Future<void> _requestSignature() async {
    if (_isRequestingSignature || _signatureRequested) return;
    setState(() => _isRequestingSignature = true);
    try {
      final api = ref.read(apiClientProvider);
      final res = await api.put(
        '/api/appointments/${widget.appointment.id}/request-signature',
        {},
      );
      if (res.statusCode == 200 && mounted) {
        setState(() {
          _signatureRequested = true;
          _isRequestingSignature = false;
        });
        _signaturePollTimer?.cancel();
        _signaturePollTimer = Timer.periodic(const Duration(seconds: 10), (
          _,
        ) async {
          await _fetchSignatureStatus();
        });
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.signatureRequestSent),
              backgroundColor: Colors.green,
            ),
          );
        }
      } else {
        if (mounted) setState(() => _isRequestingSignature = false);
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                '${AppLocalizations.of(context)!.errorSaving}: ${res.statusCode}',
              ),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } catch (e) {
      if (mounted) setState(() => _isRequestingSignature = false);
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${AppLocalizations.of(context)!.errorSaving}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _checkChronicDiseaseWarning() async {
    if (_warningShown) return;

    final patientId = widget.appointment.patientId;
    if (patientId == null || patientId.isEmpty) return;

    try {
      final patientAsync = ref.read(patientByIdProvider(patientId));
      final patient = await patientAsync.when(
        data: (p) => Future.value(p),
        loading: () => Future.value(null),
        error: (_, __) => Future.value(null),
      );

      if (patient != null &&
          patient.general.chronicDisease != null &&
          patient.general.chronicDisease!.isNotEmpty &&
          patient.general.chronicDisease != 'None' &&
          mounted) {
        _warningShown = true;
        await showChronicDiseaseWarning(
          context,
          patient.name,
          patient.general.chronicDisease!,
        );
      }
    } catch (e) {
      // Silently fail - don't block the screen
      debugPrint('Error checking chronic disease: $e');
    }
  }

  @override
  void dispose() {
    _signaturePollTimer?.cancel();
    _notesController.removeListener(_markUnsaved);
    _notesController.dispose();
    super.dispose();
  }

  Future<void> _pickImage(bool isBefore) async {
    final l10n = AppLocalizations.of(context)!;

    // Check if we're on desktop (Windows, macOS, Linux)
    final isDesktop =
        !kIsWeb && (Platform.isWindows || Platform.isMacOS || Platform.isLinux);

    // Show bottom sheet to choose between camera and gallery
    final ImageSource? source = await showModalBottomSheet<ImageSource>(
      context: context,
      builder: (context) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              leading: const Icon(Icons.camera_alt),
              title: Text(l10n.takePhoto),
              onTap: () => Navigator.pop(context, ImageSource.camera),
            ),
            ListTile(
              leading: const Icon(Icons.photo_library),
              title: Text(l10n.chooseFromGallery),
              onTap: () => Navigator.pop(context, ImageSource.gallery),
            ),
          ],
        ),
      ),
    );

    if (source == null) return;

    try {
      XFile? image;

      // On desktop, camera source doesn't work with image_picker
      // Use file_picker as fallback for camera selection on desktop
      if (isDesktop && source == ImageSource.camera) {
        final result = await FilePicker.platform.pickFiles(
          type: FileType.image,
          allowMultiple: false,
        );

        if (result != null && result.files.single.path != null) {
          // Create XFile from the selected file path
          image = XFile(result.files.single.path!);
        }
      } else {
        // Use image_picker for mobile/web or gallery on desktop
        image = await _imagePicker.pickImage(source: source, imageQuality: 85);
      }

      if (image != null) {
        setState(() {
          _hasUnsavedChanges = true;
          if (isBefore) {
            _beforeTreatmentImages.add(image!);
          } else {
            _afterTreatmentImages.add(image!);
          }
        });
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.errorPickingImage}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  void _startAiNotes() {
    setState(() => _aiInlineVoiceCapture = true);
  }

  Future<void> _uploadScribeRecording(String filePath) async {
    final l10n = AppLocalizations.of(context)!;
    try {
      Uint8List fileBytes;
      String fileName;

      if (kIsWeb) {
        if (filePath.startsWith('blob:')) {
          final response = await http.get(Uri.parse(filePath));
          if (response.statusCode != 200) {
            throw Exception('Failed to fetch blob: ${response.statusCode}');
          }
          fileBytes = response.bodyBytes;
        } else if (filePath.startsWith('data:')) {
          final base64String = filePath.split(',')[1];
          fileBytes = base64Decode(base64String);
        } else {
          throw Exception('Invalid file path: $filePath');
        }
        fileName = 'ai_notes_${DateTime.now().millisecondsSinceEpoch}.wav';
      } else {
        final file = File(filePath);
        if (!await file.exists()) return;
        fileBytes = await file.readAsBytes();
        fileName = 'ai_notes_${DateTime.now().millisecondsSinceEpoch}.m4a';
      }

      if (!mounted) return;
      ScaffoldMessenger.of(
        context,
      ).showSnackBar(SnackBar(content: Text(l10n.uploadingFile)));

      final client = ref.read(apiClientProvider);
      final uiLang = ref
          .read(languageProvider)
          .locale
          .languageCode
          .toLowerCase();
      final normalizedLang =
          (uiLang == 'uz' || uiLang == 'ru' || uiLang == 'en') ? uiLang : 'uz';
      final multipartFile = http.MultipartFile.fromBytes(
        'audio',
        fileBytes,
        filename: fileName,
      );
      final streamedResponse = await client.postMultipart(
        '/api/consultations/upload-recording',
        files: [multipartFile],
        fields: {
          'appointmentId': widget.appointment.id,
          'language': normalizedLang,
        },
      );
      final response = await http.Response.fromStream(streamedResponse);

      if (!mounted) return;
      ScaffoldMessenger.of(context).hideCurrentSnackBar();

      if (response.statusCode == 202) {
        ref.invalidate(
          consultationNotesForAppointmentProvider(widget.appointment.id),
        );
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.aiNotesUploaded),
            backgroundColor: Colors.green,
          ),
        );
      } else {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.errorUploadingFile}: ${response.body}'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('${l10n.errorUploadingFile}: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    }
  }

  Future<void> _endAppointment() async {
    setState(() => _isSaving = true);

    try {
      final api = ref.read(apiClientProvider);

      // Get patientId - try from appointment first, or fetch from appointment details
      String? patientId = widget.appointment.patientId;

      if (patientId == null || patientId.isEmpty) {
        // Try to fetch patientId from calendar endpoint for this day
        final doctorTimeZone =
            ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
                as String?;
        try {
          final today = getTodayInTimezone(doctorTimeZone);
          final ymd =
              '${today.year.toString().padLeft(4, '0')}-'
              '${today.month.toString().padLeft(2, '0')}-'
              '${today.day.toString().padLeft(2, '0')}';
          final res = await api.get('/api/calendar', params: {'day': ymd});
          if (res.statusCode == 200) {
            final List data = json.decode(utf8.decode(res.bodyBytes)) as List;
            final appointment = data.firstWhere(
              (e) =>
                  (e['appointmentId'] ?? e['id'] ?? '').toString() ==
                  widget.appointment.id,
              orElse: () => null,
            );
            if (appointment != null) {
              patientId = appointment['patientId']?.toString();
            }
          }
        } catch (e) {
          debugPrint('Error fetching appointment details: $e');
        }
      }

      if (patientId == null || patientId.isEmpty) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                '${AppLocalizations.of(context)!.patientIdNotAvailable}. ${AppLocalizations.of(context)!.cannotSaveNotes}',
              ),
              backgroundColor: Colors.red,
            ),
          );
        }
        return;
      }

      // Use doctor's timezone for recording appointment completion time
      final doctorTimeZone =
          ref.read(profileAllProvider).valueOrNull?.profile['timeZone']
              as String?;
      final now = getNowInTimezone(doctorTimeZone);
      final dateStr =
          '${now.year.toString().padLeft(4, '0')}-'
          '${now.month.toString().padLeft(2, '0')}-'
          '${now.day.toString().padLeft(2, '0')}';
      final timeStr =
          '${now.hour.toString().padLeft(2, '0')}:'
          '${now.minute.toString().padLeft(2, '0')}';

      // Include consultation notes (e.g. From Shifa AI) in the saved documentation
      List<ConsultationNoteDto> consultationNotes = [];
      try {
        consultationNotes = await ref.read(
          consultationNotesForAppointmentProvider(widget.appointment.id).future,
        );
      } catch (_) {}

      final l10nForPdf = AppLocalizations.of(context)!;
      final consultationNotesBlock = consultationNotes.isEmpty
          ? ''
          : consultationNotes
                .map((n) => '${l10nForPdf.fromShifaAi}:\n${n.displayText}')
                .join('\n\n');
      final structuredAndFree = _documentationType == 'general'
          ? composeConsultationNotesPdf(
              l10n: l10nForPdf,
              soapSubjective: _soapSubjective,
              soapObjective: _soapObjective,
              soapAssessment: _soapAssessment,
              soapPlan: _soapPlan,
              freeNotes: _notesController,
            )
          : '';

      var dentalPdfBlock = '';
      if (_documentationType == 'dental') {
        dentalPdfBlock =
            await _dentalDocPanelKey.currentState?.persistForPdfAndSave() ?? '';
      }

      final combinedNotes = [
        if (consultationNotesBlock.isNotEmpty) consultationNotesBlock,
        if (structuredAndFree.isNotEmpty) structuredAndFree,
        if (dentalPdfBlock.isNotEmpty) dentalPdfBlock,
      ].join('\n\n').trim();

      final hasNotes = combinedNotes.isNotEmpty;
      final hasBeforeImages = _beforeTreatmentImages.isNotEmpty;
      final hasAfterImages = _afterTreatmentImages.isNotEmpty;
      final dentalHasWork = _documentationType == 'dental' &&
          (_dentalDocPanelKey.currentState?.hasBillableContent ?? false);

      if (!hasNotes && !hasBeforeImages && !hasAfterImages && !dentalHasWork) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.noItemsToSave),
            ),
          );
          Navigator.pop(context);
        }
        return;
      }

      // Generate professional appointment summary PDF (localized, branded)
      try {
        // Refetch appointment so PDF gets latest patient signature from server
        String? pdfSignatureBase64;
        String? pdfSignedAtStr;
        try {
          final res = await api.get(
            '/api/appointments/${widget.appointment.id}',
          );
          if (res.statusCode == 200) {
            final map =
                json.decode(utf8.decode(res.bodyBytes))
                    as Map<String, dynamic>?;
            if (map != null) {
              final rawImg = map['patientSignatureImageBase64'];
              pdfSignatureBase64 =
                  (rawImg is String && rawImg.trim().isNotEmpty)
                  ? rawImg
                  : null;
              final rawAt = map['patientSignedAt'];
              pdfSignedAtStr =
                  (rawAt is String &&
                      rawAt.trim().isNotEmpty &&
                      rawAt != 'null')
                  ? rawAt
                  : null;
            }
          }
        } catch (_) {}

        final languageCode = ref.read(languageProvider).locale.languageCode;
        final t = AppointmentPdfTranslations.forLanguage(languageCode);
        String? doctorName;
        String? specialization;
        try {
          final all = await ref.read(profileAllProvider.future);
          final p = all.profile;
          doctorName = '${p['firstName'] ?? ''} ${p['lastName'] ?? ''}'.trim();
          if (doctorName.isEmpty) doctorName = null;
          specialization = p['profession'] as String?;
        } catch (_) {}

        final start = widget.appointment.start;
        final end = widget.appointment.end;
        final durationMin =
            (end.hour * 60 + end.minute) - (start.hour * 60 + start.minute);
        final durationStr = durationMin > 0 ? '$durationMin min' : null;

        Uint8List? signatureBytes;
        DateTime? signedAt;
        if (pdfSignatureBase64 != null &&
            pdfSignatureBase64.trim().isNotEmpty) {
          try {
            signatureBytes = Uint8List.fromList(
              base64Decode(pdfSignatureBase64),
            );
          } catch (_) {}
        }
        if (pdfSignedAtStr != null) {
          signedAt = DateTime.tryParse(pdfSignedAtStr);
          if (signedAt == null) {
            final cleaned = pdfSignedAtStr
                .replaceFirst(RegExp(r'\[[^\]]*\]$'), '')
                .trim();
            signedAt = DateTime.tryParse(cleaned);
          }
        }

        // Phase 4: include structured diagnosis (ICD-10) and free-text diagnosis (if present)
        // from the latest saved 025-2 form for this patient.
        String? dxCode;
        String? dxDisplay;
        String? dxSystem;
        String? dxFreeText;
        try {
          final forms = await ref.read(patientFormsProvider(patientId).future);
          final o252 = forms.where((f) => f.templateId == '025-2').toList()
            ..sort((a, b) {
              final dateCmp = b.date.compareTo(a.date);
              if (dateCmp != 0) return dateCmp;
              final idA = int.tryParse(a.id ?? '0') ?? 0;
              final idB = int.tryParse(b.id ?? '0') ?? 0;
              return idB.compareTo(idA);
            });
          final latest = o252.isNotEmpty ? o252.first : null;
          dxCode = latest?.diagnosisCode;
          dxDisplay = latest?.diagnosisDisplay;
          dxSystem = latest?.diagnosisSystem;
          dxFreeText = latest?.diagnosis;
        } catch (_) {}

        final pdfData = AppointmentPdfData(
          appointmentId: widget.appointment.id,
          patientName: widget.appointment.patientName,
          patientId: widget.appointment.patientId,
          dateOfBirth: null,
          gender: null,
          doctorName: doctorName,
          specialization: specialization,
          licenseNumber: null,
          appointmentType: widget.appointment.isVideo
              ? t.videoConsultation
              : t.faceToFace,
          duration: durationStr,
          dateStr: dateStr,
          timeStr: timeStr,
          appointmentDate: now,
          notes: hasNotes ? combinedNotes : null,
          diagnosis: dxFreeText,
          diagnosisCode: dxCode,
          diagnosisDisplay: dxDisplay,
          diagnosisSystem: dxSystem,
          prescriptions: null,
          recommendations: null,
          followUpDate: null,
          patientSignatureImageBytes: signatureBytes,
          patientSignedAt: signedAt,
          isDentalDocumentation: _documentationType == 'dental',
        );

        final combinedPdf = await generateAppointmentPdf(
          data: pdfData,
          languageCode: languageCode,
          beforeImages: _beforeTreatmentImages.isNotEmpty
              ? _beforeTreatmentImages
              : null,
          afterImages: _afterTreatmentImages.isNotEmpty
              ? _afterTreatmentImages
              : null,
        );

        final l10n = AppLocalizations.of(context)!;
        final title = '${l10n.appointmentDocumentation} - $dateStr $timeStr';
        await uploadPatientDocumentWithClient(
          client: api,
          patientId: patientId,
          fileBytes: combinedPdf,
          fileName: 'appointment_${now.millisecondsSinceEpoch}.pdf',
          title: title,
          // Appointment notes stay doctor-private under the new visibility
          // rules; tag the upload so the document list shows the type.
          category: 'APPOINTMENT_NOTE',
        );

        debugPrint('Combined PDF saved successfully');

        // Mark appointment as completed in backend
        try {
          await api.put(
            '/api/appointments/${widget.appointment.id}/complete',
            {},
          );
          debugPrint(
            'Appointment ${widget.appointment.id} marked as completed',
          );
        } catch (e) {
          debugPrint('Error marking appointment as completed: $e');
          // Continue even if status update fails
        }

        // Refresh appointments and analytics
        await invalidateAppointmentRelatedProviders(ref);

        // Refresh documents
        ref.invalidate(patientDocumentsProvider(patientId));

        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(
                  context,
                )!.appointmentEndedDocumentationSaved,
              ),
              backgroundColor: Colors.green,
            ),
          );
          Navigator.pop(context);
        }
      } catch (e, stackTrace) {
        debugPrint('Error saving combined PDF: $e');
        debugPrint('Stack trace: $stackTrace');
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.somethingWentWrong),
              backgroundColor: Colors.red,
              duration: const Duration(seconds: 5),
            ),
          );
        }
      }
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
      }
    }
  }

  /// Builds the 025-2 form panel. Shows loading while patientId is being fetched
  /// instead of "Patient ID not available" so newly created accounts resolve correctly.
  Widget _build0252Panel(
    Color brand,
    String? patientId,
    AsyncValue<String?>? patientIdAsync,
  ) {
    if (patientIdAsync != null) {
      return patientIdAsync.when(
        data: (id) {
          final resolvedId = id ?? patientId;
          if (resolvedId != null && resolvedId.isNotEmpty) {
            return AppointmentForm0252Panel(
              key: _form0252PanelKey,
              patientId: resolvedId,
              brand: brand,
              onDocumentsChanged: () {
                ref.invalidate(patientDocumentsProvider(resolvedId));
                ref.refresh(patientFormsProvider(resolvedId));
              },
              onHasUnsavedChanges: (v) =>
                  setState(() => _hasUnsavedChanges = v),
            );
          }
          return Center(
            child: Text(AppLocalizations.of(context)!.patientIdNotAvailable),
          );
        },
        loading: () => const Center(
          child: Padding(
            padding: EdgeInsets.all(24),
            child: CircularProgressIndicator(),
          ),
        ),
        error: (_, __) => Center(
          child: Text(AppLocalizations.of(context)!.patientIdNotAvailable),
        ),
      );
    }
    if (patientId != null && patientId.isNotEmpty) {
      return AppointmentForm0252Panel(
        key: _form0252PanelKey,
        patientId: patientId,
        brand: brand,
        onDocumentsChanged: () {
          ref.invalidate(patientDocumentsProvider(patientId));
          ref.refresh(patientFormsProvider(patientId));
        },
        onHasUnsavedChanges: (v) => setState(() => _hasUnsavedChanges = v),
      );
    }
    return Center(
      child: Text(AppLocalizations.of(context)!.patientIdNotAvailable),
    );
  }

  Widget _documentsFinalizeHintRow(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.only(top: 2),
          child: Icon(
            Icons.info_outline_rounded,
            size: 18,
            color: Colors.grey.shade600,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: Text(
            l10n.documentsFinalizeHint,
            style: TextStyle(
              fontSize: 12,
              color: Colors.grey.shade700,
              height: 1.35,
            ),
          ),
        ),
      ],
    );
  }

  @override
  Widget build(BuildContext context) {
    final brand = AppColors.primaryTeal;
    final profileAllAsync = ref.watch(profileAllProvider);
    final profileAll = profileAllAsync.valueOrNull;
    if (profileAll != null) {
      _applyProfessionDocumentationDefaultIfReady(profileAll.profile);
    }
    final profession = profileAll?.profile['profession'] as String?;
    final showDentalDoc = isDentalDocumentationProfession(profession);
    final initialPatientId = widget.appointment.patientId;

    // If patientId is not available, try to fetch it from calendar
    final patientIdAsync =
        (initialPatientId == null || initialPatientId.isEmpty)
        ? ref.watch(_patientIdProvider(widget.appointment.id))
        : null;

    // Get the actual patientId to use
    final patientId = initialPatientId ?? patientIdAsync?.value;

    // Fetch patient documents
    final documentsAsync = patientId != null && patientId.isNotEmpty
        ? ref.watch(patientDocumentsProvider(patientId))
        : null;

    final consultationUploadEnabled =
        patientId != null &&
        patientId.isNotEmpty &&
        (patientIdAsync == null ||
            (!patientIdAsync.isLoading && !patientIdAsync.hasError));

    // Consultation notes for this appointment (e.g. saved Shifa AI drafts) – shown in documentation with "From Shifa AI" badge
    final consultationNotesAsync = ref.watch(
      consultationNotesForAppointmentProvider(widget.appointment.id),
    );
    // Pending AI draft notes for this appointment (e.g. AI Scribe) – show with Confirm/Discard
    final draftNotesAsync = ref.watch(
      draftNotesForAppointmentProvider(widget.appointment.id),
    );

    final patientProfileAsync =
        patientId != null && patientId.isNotEmpty
            ? ref.watch(patientByIdProvider(patientId))
            : null;
    final resolvedPatient = patientProfileAsync?.asData?.value;
    final patientProfileLoading =
        patientProfileAsync != null &&
        patientProfileAsync.isLoading &&
        !patientProfileAsync.hasValue;

    return Scaffold(
      backgroundColor: AppColors.scaffoldBackground,
      body: Stack(
        children: [
          Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              AppointmentConsultationHeader(
                appointment: widget.appointment,
                resolvedPatient: resolvedPatient,
                patientLoading: patientProfileLoading,
                onBack: () => Navigator.pop(context),
              ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(24, 16, 24, 0),
                  child: Row(
                    children: [
                  // Documents (collapsible)
                  _docPanelCollapsed
                      ? Container(
                          width: 52,
                          margin: const EdgeInsets.only(right: 8),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(16),
                            border: Border.all(
                              color: Colors.black.withValues(alpha: 0.06),
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withValues(alpha: 0.05),
                                blurRadius: 14,
                                offset: const Offset(0, 4),
                              ),
                            ],
                          ),
                          child: Material(
                            color: Colors.transparent,
                            child: InkWell(
                              onTap: () =>
                                  setState(() => _docPanelCollapsed = false),
                              borderRadius: BorderRadius.circular(16),
                              child: Column(
                                mainAxisAlignment: MainAxisAlignment.center,
                                children: [
                                  Icon(
                                    Icons.chevron_right,
                                    color: brand,
                                    size: 28,
                                  ),
                                  const SizedBox(height: 4),
                                  RotatedBox(
                                    quarterTurns: 3,
                                    child: Text(
                                      AppLocalizations.of(context)!.documents,
                                      style: TextStyle(
                                        fontSize: 12,
                                        color: Colors.grey.shade700,
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        )
                      : Expanded(
                          child: DocumentationSectionCard(
                            title: AppLocalizations.of(context)!.documents,
                            titleTrailing: IconButton(
                              icon: const Icon(Icons.chevron_left),
                              onPressed: () =>
                                  setState(() => _docPanelCollapsed = true),
                              tooltip: AppLocalizations.of(context)!.collapse,
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Expanded(
                                  child: () {
                                    // Check if we're still loading patientId
                                    if (patientIdAsync != null) {
                                      return patientIdAsync.when(
                                        data: (id) {
                                          if (id == null || id.isEmpty) {
                                            return Center(
                                              child: Text(
                                                AppLocalizations.of(
                                                  context,
                                                )!.patientIdNotAvailable,
                                              ),
                                            );
                                          }
                                          final docsAsync = ref.watch(
                                            patientDocumentsProvider(id),
                                          );
                                          return docsAsync.when(
                                            data: (docs) =>
                                                GroupedPatientDocumentsList(
                                                  documents: docs,
                                                  brand: brand,
                                                  patientId: id,
                                                  onRequestAccess: (doc) async {
                                                    final client = ref.read(
                                                      apiClientProvider,
                                                    );
                                                    await requestDocumentAccessWithClient(
                                                      client: client,
                                                      patientId: id,
                                                      documentId: doc.id,
                                                    );
                                                    ref.refresh(
                                                      patientDocumentsProvider(
                                                        id,
                                                      ),
                                                    );
                                                  },
                                                ),
                                            loading: () => const Center(
                                              child:
                                                  CircularProgressIndicator(),
                                            ),
                                            error: (err, _) => Center(
                                              child: Text(
                                                '${AppLocalizations.of(context)!.error}: $err',
                                              ),
                                            ),
                                          );
                                        },
                                        loading: () => const Center(
                                          child: CircularProgressIndicator(),
                                        ),
                                        error: (err, _) => Center(
                                          child: Text(
                                            '${AppLocalizations.of(context)!.errorLoadingPatientId}: $err',
                                          ),
                                        ),
                                      );
                                    }
                                    // If patientId is already available, use documentsAsync
                                    if (documentsAsync == null) {
                                      return Center(
                                        child: Text(
                                          AppLocalizations.of(
                                            context,
                                          )!.patientIdNotAvailable,
                                        ),
                                      );
                                    }
                                    return documentsAsync.when(
                                      data: (docs) {
                                        final pid = patientId!;
                                        return GroupedPatientDocumentsList(
                                            documents: docs,
                                            brand: brand,
                                            patientId: pid,
                                            onRequestAccess: (doc) async {
                                              final client = ref.read(
                                                apiClientProvider,
                                              );
                                              await requestDocumentAccessWithClient(
                                                client: client,
                                                patientId: pid,
                                                documentId: doc.id,
                                              );
                                              ref.refresh(
                                                patientDocumentsProvider(
                                                  pid,
                                                ),
                                              );
                                            },
                                          );
                                      },
                                      loading: () => const Center(
                                        child: CircularProgressIndicator(),
                                      ),
                                      error: (err, _) => Center(
                                        child: Text(
                                          '${AppLocalizations.of(context)!.error}: $err',
                                        ),
                                      ),
                                    );
                                  }(),
                                ),
                                ConsultationDocumentUploadStrip(
                                  patientId: patientId,
                                  brand: brand,
                                  enabled: consultationUploadEnabled,
                                ),
                                const SizedBox(height: 10),
                                _documentsFinalizeHintRow(context),
                              ],
                            ),
                          ),
                        ),
                  const SizedBox(width: 24),
                  // Documentation (general notes or 025-2 form)
                  Expanded(
                    child: DocumentationSectionCard(
                      title: _documentationType == 'general'
                          ? AppLocalizations.of(context)!.notes
                          : _documentationType == 'dental'
                              ? AppLocalizations.of(context)!.docModeDental
                              : AppLocalizations.of(context)!.docMode0252,
                      titleTrailing: Row(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          if (_documentationType == 'general')
                            IconButton(
                              icon: const Icon(Icons.center_focus_strong),
                              tooltip: AppLocalizations.of(
                                context,
                              )!.consultationFocusModeTooltip,
                              onPressed: _openConsultationFocusMode,
                            ),
                          if (_documentationType == 'dental' &&
                              !_dentalDocumentationFullScreen)
                            IconButton(
                              icon: const Icon(Icons.fullscreen),
                              tooltip: AppLocalizations.of(context)!.expand,
                              onPressed: () => setState(
                                () => _dentalDocumentationFullScreen = true,
                              ),
                            ),
                          PopupMenuButton<String>(
                            icon: const Icon(Icons.more_vert),
                            tooltip: AppLocalizations.of(context)!.notes,
                            onSelected: (value) async {
                              final l10n = AppLocalizations.of(context)!;

                              // Source actions (AI / 025-2 form) – only meaningful in general mode.
                              if (value == 'ai' || value == '0252') {
                                setState(() {
                                  _notesSectionsExpanded = true;
                                  _expandedNoteSource = value == 'ai'
                                      ? 'ai'
                                      : '0252';
                                });
                                if (value == 'ai') {
                                  final id = widget.appointment.id;
                                  ref.invalidate(
                                    draftNotesForAppointmentProvider(id),
                                  );
                                  ref.invalidate(
                                    consultationNotesForAppointmentProvider(id),
                                  );
                                  final drafts = await ref.read(
                                    draftNotesForAppointmentProvider(id).future,
                                  );
                                  final notes = await ref.read(
                                    consultationNotesForAppointmentProvider(
                                      id,
                                    ).future,
                                  );
                                  if (mounted &&
                                      drafts.isEmpty &&
                                      notes.isEmpty) {
                                    ScaffoldMessenger.of(context).showSnackBar(
                                      SnackBar(
                                        content: Text(
                                          l10n.aiNotesNotReadyTryLater,
                                        ),
                                      ),
                                    );
                                  }
                                } else if (value == '0252') {
                                  final pid = patientId;
                                  if (pid != null && pid.isNotEmpty) {
                                    ref.invalidate(patientFormsProvider(pid));
                                  }
                                }
                                return;
                              }

                              if (value == 'pick_before') {
                                await _pickImage(true);
                                return;
                              }
                              if (value == 'pick_after') {
                                await _pickImage(false);
                                return;
                              }

                              // Mode switching (general vs 025-2) with unsaved-changes protection.
                              if (_documentationType == value) return;
                              if (value != 'general' &&
                                  value != 'dental' &&
                                  value != '025-2') {
                                return;
                              }
                              _userSelectedDocumentationType = true;
                              if (value != 'dental') {
                                _dentalDocumentationFullScreen = false;
                              }
                              if (_hasUnsavedChanges) {
                                final result = await showDialog<String>(
                                  context: context,
                                  barrierDismissible: false,
                                  builder: (ctx) => AlertDialog(
                                    title: Text(l10n.unsavedChangesSwitch),
                                    actions: [
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(ctx, 'cancel'),
                                        child: Text(l10n.cancel),
                                      ),
                                      TextButton(
                                        onPressed: () =>
                                            Navigator.pop(ctx, 'discard'),
                                        child: Text(l10n.discardAndSwitch),
                                      ),
                                      ShifaPrimaryButton(
                                        label: l10n.saveAndSwitch,
                                        onPressed: () =>
                                            Navigator.pop(ctx, 'save'),
                                      ),
                                    ],
                                  ),
                                );
                                if (result == null || result == 'cancel')
                                  return;
                                if (result == 'save') {
                                  if (_documentationType == '025-2') {
                                    final ok =
                                        await _form0252PanelKey.currentState
                                            ?.requestSave() ??
                                        false;
                                    if (!ok || !mounted) return;
                                  } else if (_documentationType == 'dental') {
                                    final ok =
                                        await _dentalDocPanelKey.currentState
                                            ?.requestSave() ??
                                        false;
                                    if (!ok || !mounted) return;
                                  }
                                  setState(() {
                                    _hasUnsavedChanges = false;
                                    _documentationType = value;
                                  });
                                  return;
                                }
                                if (result == 'discard') {
                                  _clearGeneralNoteFields();
                                  setState(() {
                                    _beforeTreatmentImages.clear();
                                    _afterTreatmentImages.clear();
                                    _hasUnsavedChanges = false;
                                    _documentationType = value;
                                  });
                                }
                              } else {
                                setState(() => _documentationType = value);
                              }
                            },
                            itemBuilder: (context) {
                              final l10n = AppLocalizations.of(context)!;
                              final items = <PopupMenuEntry<String>>[];
                              if (_documentationType == 'general') {
                                items.addAll([
                                  PopupMenuItem(
                                    value: 'ai',
                                    child: Text(l10n.fromShifaAi),
                                  ),
                                  PopupMenuItem(
                                    value: '0252',
                                    child: Text(l10n.fromLast0252Form),
                                  ),
                                  PopupMenuItem(
                                    value: 'pick_before',
                                    child: Row(
                                      children: [
                                        Icon(
                                          Icons.photo_camera_outlined,
                                          size: 18,
                                          color: brand,
                                        ),
                                        const SizedBox(width: 12),
                                        Expanded(child: Text(l10n.beforeTreatment)),
                                      ],
                                    ),
                                  ),
                                  PopupMenuItem(
                                    value: 'pick_after',
                                    child: Row(
                                      children: [
                                        Icon(
                                          Icons.photo_camera_outlined,
                                          size: 18,
                                          color: Colors.green.shade700,
                                        ),
                                        const SizedBox(width: 12),
                                        Expanded(child: Text(l10n.afterTreatment)),
                                      ],
                                    ),
                                  ),
                                  const PopupMenuDivider(),
                                ]);
                              }
                              items.addAll([
                                PopupMenuItem(
                                  value: 'general',
                                  child: Text(l10n.docModeGeneral),
                                ),
                                if (showDentalDoc)
                                  PopupMenuItem(
                                    value: 'dental',
                                    child: Text(l10n.docModeDental),
                                  ),
                                PopupMenuItem(
                                  value: '025-2',
                                  child: Text(l10n.docMode0252),
                                ),
                              ]);
                              return items;
                            },
                          ),
                        ],
                      ),
                      child: _documentationType == 'general'
                          ? Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                if (_notesSectionsExpanded) ...[
                                  // From Shifa AI – only when AI source is selected
                                  if (_expandedNoteSource == 'ai') ...[
                                    // Pending AI drafts (e.g. AI Scribe) – Confirm to save as consultation note, or Discard
                                    draftNotesAsync.when(
                                      data: (drafts) {
                                        if (drafts.isEmpty)
                                          return const SizedBox.shrink();
                                        final l10n = AppLocalizations.of(
                                          context,
                                        )!;
                                        final appointmentIdInt = int.tryParse(
                                          widget.appointment.id,
                                        );
                                        final patientIdStr = patientId;
                                        final patientIdInt =
                                            patientIdStr != null &&
                                                patientIdStr.isNotEmpty
                                            ? int.tryParse(patientIdStr)
                                            : null;
                                        return Padding(
                                          padding: const EdgeInsets.only(
                                            bottom: 8,
                                          ),
                                          child: ConstrainedBox(
                                            constraints: const BoxConstraints(
                                              maxHeight: 260,
                                            ),
                                            child: ListView.builder(
                                              shrinkWrap: true,
                                              itemCount: drafts.length,
                                              itemBuilder: (context, i) {
                                                final d = drafts[i];
                                                return Container(
                                                  margin: const EdgeInsets.only(
                                                    bottom: 8,
                                                  ),
                                                  padding: const EdgeInsets.all(
                                                    10,
                                                  ),
                                                  decoration: BoxDecoration(
                                                    color:
                                                        Colors.orange.shade50,
                                                    borderRadius:
                                                        BorderRadius.circular(
                                                          10,
                                                        ),
                                                    border: Border.all(
                                                      color: Colors
                                                          .orange
                                                          .shade200,
                                                    ),
                                                  ),
                                                  child: Column(
                                                    crossAxisAlignment:
                                                        CrossAxisAlignment
                                                            .stretch,
                                                    mainAxisSize:
                                                        MainAxisSize.min,
                                                    children: [
                                                      Row(
                                                        children: [
                                                          Icon(
                                                            Icons.edit_note,
                                                            size: 16,
                                                            color: Colors
                                                                .orange
                                                                .shade800,
                                                          ),
                                                          const SizedBox(
                                                            width: 6,
                                                          ),
                                                          Expanded(
                                                            child: Text(
                                                              d.aiLabel,
                                                              style: TextStyle(
                                                                fontWeight:
                                                                    FontWeight
                                                                        .w600,
                                                                fontSize: 12,
                                                                color: Colors
                                                                    .orange
                                                                    .shade900,
                                                              ),
                                                            ),
                                                          ),
                                                          Text(
                                                            l10n.draft,
                                                            style: TextStyle(
                                                              fontSize: 11,
                                                              color: Colors
                                                                  .orange
                                                                  .shade700,
                                                            ),
                                                          ),
                                                          const Spacer(),
                                                          IconButton(
                                                            icon: const Icon(
                                                              Icons.close,
                                                              size: 18,
                                                            ),
                                                            tooltip: l10n.close,
                                                            onPressed: () {
                                                              setState(() {
                                                                _expandedNoteSource =
                                                                    null;
                                                                _notesSectionsExpanded =
                                                                    false;
                                                              });
                                                            },
                                                          ),
                                                        ],
                                                      ),
                                                      const SizedBox(height: 6),
                                                      SingleChildScrollView(
                                                        child: Text(
                                                          d.body,
                                                          style: TextStyle(
                                                            fontSize: 13,
                                                            color: Colors
                                                                .grey
                                                                .shade800,
                                                          ),
                                                        ),
                                                      ),
                                                      if (d
                                                          .icdSuggestions
                                                          .isNotEmpty) ...[
                                                        const SizedBox(
                                                          height: 10,
                                                        ),
                                                        Text(
                                                          'Suggested Diagnoses (AI):',
                                                          style: TextStyle(
                                                            fontSize: 12,
                                                            fontWeight:
                                                                FontWeight.w700,
                                                            color: Colors
                                                                .orange
                                                                .shade900,
                                                          ),
                                                        ),
                                                        const SizedBox(
                                                          height: 6,
                                                        ),
                                                        ...d.icdSuggestions.map((
                                                          s,
                                                        ) {
                                                          return Padding(
                                                            padding:
                                                                const EdgeInsets.only(
                                                                  bottom: 6,
                                                                ),
                                                            child: Row(
                                                              children: [
                                                                Expanded(
                                                                  child: Text(
                                                                    '${s.code} — ${s.title}',
                                                                    style: TextStyle(
                                                                      fontSize:
                                                                          12,
                                                                      color: Colors
                                                                          .grey
                                                                          .shade800,
                                                                    ),
                                                                    maxLines: 2,
                                                                    overflow:
                                                                        TextOverflow
                                                                            .ellipsis,
                                                                  ),
                                                                ),
                                                                if (s
                                                                    .isTop) ...[
                                                                  const SizedBox(
                                                                    width: 8,
                                                                  ),
                                                                  Container(
                                                                    padding: const EdgeInsets.symmetric(
                                                                      horizontal:
                                                                          8,
                                                                      vertical:
                                                                          3,
                                                                    ),
                                                                    decoration: BoxDecoration(
                                                                      color: Colors
                                                                          .green
                                                                          .shade50,
                                                                      borderRadius:
                                                                          BorderRadius.circular(
                                                                            999,
                                                                          ),
                                                                      border: Border.all(
                                                                        color: Colors
                                                                            .green
                                                                            .shade200,
                                                                      ),
                                                                    ),
                                                                    child: Text(
                                                                      'Recommended',
                                                                      style: TextStyle(
                                                                        fontSize:
                                                                            11,
                                                                        fontWeight:
                                                                            FontWeight.w700,
                                                                        color: Colors
                                                                            .green
                                                                            .shade800,
                                                                      ),
                                                                    ),
                                                                  ),
                                                                ],
                                                                const SizedBox(
                                                                  width: 8,
                                                                ),
                                                                TextButton(
                                                                  onPressed: () {
                                                                    // Never auto-apply: doctor must click Apply.
                                                                    if (_documentationType !=
                                                                        '025-2') {
                                                                      _userSelectedDocumentationType =
                                                                          true;
                                                                      setState(() {
                                                                        _documentationType =
                                                                            '025-2';
                                                                        _notesSectionsExpanded =
                                                                            true;
                                                                      });
                                                                    }
                                                                    WidgetsBinding.instance.addPostFrameCallback((
                                                                      _,
                                                                    ) {
                                                                      _form0252PanelKey
                                                                          .currentState
                                                                          ?.applyIcdSuggestion(
                                                                            s.code,
                                                                            s.title,
                                                                          );
                                                                      if (mounted) {
                                                                        ScaffoldMessenger.of(
                                                                          context,
                                                                        ).showSnackBar(
                                                                          SnackBar(
                                                                            content: Text(
                                                                              'Applied: ${s.code}',
                                                                            ),
                                                                          ),
                                                                        );
                                                                      }
                                                                    });
                                                                  },
                                                                  child:
                                                                      const Text(
                                                                        'Apply',
                                                                      ),
                                                                ),
                                                              ],
                                                            ),
                                                          );
                                                        }),
                                                      ],
                                                      const SizedBox(height: 8),
                                                      Row(
                                                        mainAxisAlignment:
                                                            MainAxisAlignment
                                                                .end,
                                                        children: [
                                                          TextButton(
                                                            onPressed: () async {
                                                              try {
                                                                final aiApi =
                                                                    ref.read(
                                                                      aiApiProvider,
                                                                    );
                                                                await aiApi
                                                                    .discardDraft(
                                                                      d.id,
                                                                    );
                                                                if (context
                                                                    .mounted) {
                                                                  ref.invalidate(
                                                                    draftNotesForAppointmentProvider(
                                                                      widget
                                                                          .appointment
                                                                          .id,
                                                                    ),
                                                                  );
                                                                  ref.invalidate(
                                                                    consultationNotesForAppointmentProvider(
                                                                      widget
                                                                          .appointment
                                                                          .id,
                                                                    ),
                                                                  );
                                                                  ScaffoldMessenger.of(
                                                                    context,
                                                                  ).showSnackBar(
                                                                    SnackBar(
                                                                      content: Text(
                                                                        l10n.translate(
                                                                              'draftDiscarded',
                                                                            ) ??
                                                                            'Draft discarded',
                                                                      ),
                                                                    ),
                                                                  );
                                                                }
                                                              } catch (e) {
                                                                if (context
                                                                    .mounted) {
                                                                  ScaffoldMessenger.of(
                                                                    context,
                                                                  ).showSnackBar(
                                                                    SnackBar(
                                                                      content: Text(
                                                                        l10n.translate(
                                                                              'failedToSaveDraft',
                                                                            ) ??
                                                                            'Failed to discard draft',
                                                                      ),
                                                                    ),
                                                                  );
                                                                }
                                                              }
                                                            },
                                                            child: Text(
                                                              l10n.discard,
                                                            ),
                                                          ),
                                                          const SizedBox(
                                                            width: 8,
                                                          ),
                                                          ShifaPrimaryButton(
                                                            label: l10n.translate(
                                                                  'saveAsConsultationNote',
                                                                ) ??
                                                                'Save as note',
                                                            icon: Icons.check,
                                                            onPressed: () async {
                                                              try {
                                                                final aiApi =
                                                                    ref.read(
                                                                      aiApiProvider,
                                                                    );
                                                                await aiApi.confirmDraft(
                                                                  d.id,
                                                                  patientId:
                                                                      patientIdInt,
                                                                  appointmentId:
                                                                      appointmentIdInt,
                                                                );
                                                                if (context
                                                                    .mounted) {
                                                                  ref.invalidate(
                                                                    draftNotesForAppointmentProvider(
                                                                      widget
                                                                          .appointment
                                                                          .id,
                                                                    ),
                                                                  );
                                                                  ref.invalidate(
                                                                    consultationNotesForAppointmentProvider(
                                                                      widget
                                                                          .appointment
                                                                          .id,
                                                                    ),
                                                                  );
                                                                  ScaffoldMessenger.of(
                                                                    context,
                                                                  ).showSnackBar(
                                                                    SnackBar(
                                                                      content: Text(
                                                                        l10n.translate(
                                                                              'draftSavedAsConsultationNote',
                                                                            ) ??
                                                                            'Draft saved as consultation note',
                                                                      ),
                                                                    ),
                                                                  );
                                                                }
                                                              } catch (e) {
                                                                if (context
                                                                    .mounted) {
                                                                  ScaffoldMessenger.of(
                                                                    context,
                                                                  ).showSnackBar(
                                                                    SnackBar(
                                                                      content: Text(
                                                                        l10n.translate(
                                                                              'failedToSaveDraft',
                                                                            ) ??
                                                                            'Failed to save draft',
                                                                      ),
                                                                    ),
                                                                  );
                                                                }
                                                              }
                                                            },
                                                          ),
                                                        ],
                                                      ),
                                                    ],
                                                  ),
                                                );
                                              },
                                            ),
                                          ),
                                        );
                                      },
                                      loading: () => const SizedBox.shrink(),
                                      error: (_, __) => const SizedBox.shrink(),
                                    ),
                                    // Saved AI outputs for this appointment – browse with < >, add to notes with +
                                    consultationNotesAsync.when(
                                      data: (notes) {
                                        if (notes.isEmpty)
                                          return const SizedBox.shrink();
                                        final l10n = AppLocalizations.of(
                                          context,
                                        )!;
                                        final currentIndex =
                                            _consultationNoteIndex.clamp(
                                              0,
                                              notes.length - 1,
                                            );
                                        final n = notes[currentIndex];
                                        final hasMultiple = notes.length > 1;
                                        return Padding(
                                          padding: const EdgeInsets.only(
                                            bottom: 8,
                                          ),
                                          child: ConstrainedBox(
                                            constraints: const BoxConstraints(
                                              maxHeight: 220,
                                            ),
                                            child: Row(
                                              crossAxisAlignment:
                                                  CrossAxisAlignment.stretch,
                                              children: [
                                                if (hasMultiple)
                                                  IconButton(
                                                    icon: const Icon(
                                                      Icons.chevron_left,
                                                    ),
                                                    onPressed: currentIndex > 0
                                                        ? () => setState(
                                                            () => _consultationNoteIndex =
                                                                currentIndex -
                                                                1,
                                                          )
                                                        : null,
                                                    tooltip:
                                                        l10n.translate(
                                                          'previous',
                                                        ) ??
                                                        'Previous',
                                                    style: IconButton.styleFrom(
                                                      foregroundColor: brand,
                                                    ),
                                                  ),
                                                Expanded(
                                                  child: Container(
                                                    padding:
                                                        const EdgeInsets.all(
                                                          10,
                                                        ),
                                                    decoration: BoxDecoration(
                                                      color: brand.withOpacity(
                                                        0.06,
                                                      ),
                                                      borderRadius:
                                                          BorderRadius.circular(
                                                            10,
                                                          ),
                                                      border: Border.all(
                                                        color: brand
                                                            .withOpacity(0.3),
                                                      ),
                                                    ),
                                                    child: SingleChildScrollView(
                                                      child: Column(
                                                        crossAxisAlignment:
                                                            CrossAxisAlignment
                                                                .start,
                                                        mainAxisSize:
                                                            MainAxisSize.min,
                                                        children: [
                                                          Row(
                                                            children: [
                                                              Icon(
                                                                Icons
                                                                    .auto_awesome,
                                                                size: 16,
                                                                color: brand,
                                                              ),
                                                              const SizedBox(
                                                                width: 6,
                                                              ),
                                                              Text(
                                                                l10n.fromShifaAi,
                                                                style: TextStyle(
                                                                  fontWeight:
                                                                      FontWeight
                                                                          .w600,
                                                                  fontSize: 12,
                                                                  color: brand,
                                                                ),
                                                              ),
                                                              if (hasMultiple) ...[
                                                                const Spacer(),
                                                                Text(
                                                                  '${currentIndex + 1} / ${notes.length}',
                                                                  style: TextStyle(
                                                                    fontSize:
                                                                        12,
                                                                    color: Colors
                                                                        .grey
                                                                        .shade600,
                                                                  ),
                                                                ),
                                                              ],
                                                            ],
                                                          ),
                                                          const SizedBox(
                                                            height: 6,
                                                          ),
                                                          Text(
                                                            n.displayText,
                                                            style: TextStyle(
                                                              fontSize: 13,
                                                              color: Colors
                                                                  .grey
                                                                  .shade800,
                                                            ),
                                                          ),
                                                        ],
                                                      ),
                                                    ),
                                                  ),
                                                ),
                                                if (hasMultiple)
                                                  IconButton(
                                                    icon: const Icon(
                                                      Icons.chevron_right,
                                                    ),
                                                    onPressed:
                                                        currentIndex <
                                                            notes.length - 1
                                                        ? () => setState(
                                                            () => _consultationNoteIndex =
                                                                currentIndex +
                                                                1,
                                                          )
                                                        : null,
                                                    tooltip:
                                                        l10n.translate(
                                                          'next',
                                                        ) ??
                                                        'Next',
                                                    style: IconButton.styleFrom(
                                                      foregroundColor: brand,
                                                    ),
                                                  ),
                                                IconButton.filled(
                                                  icon: const Icon(
                                                    Icons.add,
                                                    size: 18,
                                                  ),
                                                  onPressed: () {
                                                    final toAdd = n.displayText;
                                                    if (toAdd.trim().isEmpty)
                                                      return;
                                                    if (_notesController.text
                                                        .trim()
                                                        .isNotEmpty) {
                                                      _notesController.text +=
                                                          '\n\n';
                                                    }
                                                    _notesController.text +=
                                                        toAdd;
                                                    _markUnsaved();
                                                    setState(
                                                      () =>
                                                          _notesSectionsExpanded =
                                                              false,
                                                    );
                                                    if (mounted) {
                                                      ScaffoldMessenger.of(
                                                        context,
                                                      ).showSnackBar(
                                                        SnackBar(
                                                          content: Text(
                                                            l10n.translate(
                                                                  'addedToNotes',
                                                                ) ??
                                                                'Added to notes',
                                                          ),
                                                          duration:
                                                              const Duration(
                                                                seconds: 2,
                                                              ),
                                                        ),
                                                      );
                                                    }
                                                  },
                                                  tooltip:
                                                      l10n.translate(
                                                        'addToNotes',
                                                      ) ??
                                                      'Add to notes',
                                                  style: IconButton.styleFrom(
                                                    backgroundColor: brand,
                                                    foregroundColor:
                                                        Colors.white,
                                                    padding:
                                                        const EdgeInsets.all(8),
                                                    minimumSize: const Size(
                                                      32,
                                                      32,
                                                    ),
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ),
                                        );
                                      },
                                      loading: () => const SizedBox.shrink(),
                                      error: (_, __) => const SizedBox.shrink(),
                                    ),
                                  ], // end Shifa AI helper
                                  // From last 025-2 form: Shikoyati, Tashxis, Davolanish – add to notes with +
                                  if (_expandedNoteSource == '0252')
                                    if (patientId != null &&
                                        patientId!.isNotEmpty)
                                      ref
                                          .watch(
                                            last0252FormForPatientProvider(
                                              patientId!,
                                            ),
                                          )
                                          .when(
                                            data: (form) {
                                              if (form == null)
                                                return const SizedBox.shrink();
                                              final c =
                                                  form.complaints?.trim() ?? '';
                                              final d =
                                                  form.diagnosis?.trim() ?? '';
                                              final t =
                                                  form.treatment?.trim() ?? '';
                                              if (c.isEmpty &&
                                                  d.isEmpty &&
                                                  t.isEmpty)
                                                return const SizedBox.shrink();
                                              final l10n = AppLocalizations.of(
                                                context,
                                              )!;
                                              final parts = <String>[];
                                              if (c.isNotEmpty)
                                                parts.add(
                                                  '${l10n.complaints}: $c',
                                                );
                                              if (d.isNotEmpty)
                                                parts.add(
                                                  '${l10n.diagnosis}: $d',
                                                );
                                              if (t.isNotEmpty)
                                                parts.add(
                                                  '${l10n.treatment}: $t',
                                                );
                                              final toAdd = parts.join('\n\n');
                                              return Padding(
                                                padding: const EdgeInsets.only(
                                                  bottom: 8,
                                                ),
                                                child: ConstrainedBox(
                                                  constraints:
                                                      const BoxConstraints(
                                                        maxHeight: 220,
                                                      ),
                                                  child: Row(
                                                    crossAxisAlignment:
                                                        CrossAxisAlignment
                                                            .stretch,
                                                    children: [
                                                      Expanded(
                                                        child: Container(
                                                          padding:
                                                              const EdgeInsets.all(
                                                                10,
                                                              ),
                                                          decoration: BoxDecoration(
                                                            color: brand
                                                                .withOpacity(
                                                                  0.06,
                                                                ),
                                                            borderRadius:
                                                                BorderRadius.circular(
                                                                  10,
                                                                ),
                                                            border: Border.all(
                                                              color: brand
                                                                  .withOpacity(
                                                                    0.3,
                                                                  ),
                                                            ),
                                                          ),
                                                          child: SingleChildScrollView(
                                                            child: Column(
                                                              crossAxisAlignment:
                                                                  CrossAxisAlignment
                                                                      .start,
                                                              mainAxisSize:
                                                                  MainAxisSize
                                                                      .min,
                                                              children: [
                                                                Row(
                                                                  children: [
                                                                    Icon(
                                                                      Icons
                                                                          .description,
                                                                      size: 16,
                                                                      color:
                                                                          brand,
                                                                    ),
                                                                    const SizedBox(
                                                                      width: 6,
                                                                    ),
                                                                    Text(
                                                                      l10n.fromLast0252Form,
                                                                      style: TextStyle(
                                                                        fontWeight:
                                                                            FontWeight.w600,
                                                                        fontSize:
                                                                            12,
                                                                        color:
                                                                            brand,
                                                                      ),
                                                                    ),
                                                                    const Spacer(),
                                                                    IconButton(
                                                                      icon: const Icon(
                                                                        Icons
                                                                            .close,
                                                                        size:
                                                                            18,
                                                                      ),
                                                                      tooltip: l10n
                                                                          .close,
                                                                      onPressed: () {
                                                                        setState(() {
                                                                          _expandedNoteSource =
                                                                              null;
                                                                          _notesSectionsExpanded =
                                                                              false;
                                                                        });
                                                                      },
                                                                    ),
                                                                  ],
                                                                ),
                                                                const SizedBox(
                                                                  height: 6,
                                                                ),
                                                                if (c
                                                                    .isNotEmpty)
                                                                  Text(
                                                                    '${l10n.complaints}: $c',
                                                                    style: TextStyle(
                                                                      fontSize:
                                                                          13,
                                                                      color: Colors
                                                                          .grey
                                                                          .shade800,
                                                                    ),
                                                                  ),
                                                                if (c
                                                                    .isNotEmpty)
                                                                  const SizedBox(
                                                                    height: 4,
                                                                  ),
                                                                if (d
                                                                    .isNotEmpty)
                                                                  Text(
                                                                    '${l10n.diagnosis}: $d',
                                                                    style: TextStyle(
                                                                      fontSize:
                                                                          13,
                                                                      color: Colors
                                                                          .grey
                                                                          .shade800,
                                                                    ),
                                                                  ),
                                                                if (d
                                                                    .isNotEmpty)
                                                                  const SizedBox(
                                                                    height: 4,
                                                                  ),
                                                                if (t
                                                                    .isNotEmpty)
                                                                  Text(
                                                                    '${l10n.treatment}: $t',
                                                                    style: TextStyle(
                                                                      fontSize:
                                                                          13,
                                                                      color: Colors
                                                                          .grey
                                                                          .shade800,
                                                                    ),
                                                                  ),
                                                              ],
                                                            ),
                                                          ),
                                                        ),
                                                      ),
                                                      IconButton.filled(
                                                        icon: const Icon(
                                                          Icons.add,
                                                          size: 18,
                                                        ),
                                                        onPressed: () {
                                                          if (toAdd
                                                              .trim()
                                                              .isEmpty)
                                                            return;
                                                          if (_notesController
                                                              .text
                                                              .trim()
                                                              .isNotEmpty) {
                                                            _notesController
                                                                    .text +=
                                                                '\n\n';
                                                          }
                                                          _notesController
                                                                  .text +=
                                                              toAdd;
                                                          _markUnsaved();
                                                          setState(
                                                            () =>
                                                                _notesSectionsExpanded =
                                                                    false,
                                                          );
                                                          if (mounted) {
                                                            ScaffoldMessenger.of(
                                                              context,
                                                            ).showSnackBar(
                                                              SnackBar(
                                                                content: Text(
                                                                  l10n.translate(
                                                                        'addedToNotes',
                                                                      ) ??
                                                                      'Added to notes',
                                                                ),
                                                                duration:
                                                                    const Duration(
                                                                      seconds:
                                                                          2,
                                                                    ),
                                                              ),
                                                            );
                                                          }
                                                        },
                                                        tooltip:
                                                            l10n.translate(
                                                              'addToNotes',
                                                            ) ??
                                                            'Add to notes',
                                                        style: IconButton.styleFrom(
                                                          backgroundColor:
                                                              brand,
                                                          foregroundColor:
                                                              Colors.white,
                                                          padding:
                                                              const EdgeInsets.all(
                                                                8,
                                                              ),
                                                          minimumSize:
                                                              const Size(
                                                                32,
                                                                32,
                                                              ),
                                                        ),
                                                      ),
                                                    ],
                                                  ),
                                                ),
                                              );
                                            },
                                            loading: () =>
                                                const SizedBox.shrink(),
                                            error: (_, __) =>
                                                const SizedBox.shrink(),
                                          ),
                                ],
                                ConsultationSoapSection(
                                  l10n: AppLocalizations.of(context)!,
                                  subjective: _soapSubjective,
                                  objective: _soapObjective,
                                  assessment: _soapAssessment,
                                  plan: _soapPlan,
                                  onTranscriptAppended: _markUnsaved,
                                ),
                                const SizedBox(height: 8),
                                Expanded(
                                  child: Container(
                                    padding: const EdgeInsets.all(12),
                                    decoration: BoxDecoration(
                                      color: const Color(0xFFF9FAFB),
                                      borderRadius: BorderRadius.circular(14),
                                      border: Border.all(
                                        color: Colors.black.withValues(
                                          alpha: 0.07,
                                        ),
                                      ),
                                      boxShadow: [
                                        BoxShadow(
                                          color: Colors.black.withValues(
                                            alpha: 0.03,
                                          ),
                                          blurRadius: 12,
                                          offset: const Offset(0, 2),
                                        ),
                                      ],
                                    ),
                                    child: DoctorSpeechTextField(
                                      controller: _notesController,
                                      style:
                                          DoctorSpeechInputStyle.borderlessExpanding,
                                      expands: true,
                                      maxLines: null,
                                      textAlignVertical:
                                          TextAlignVertical.top,
                                      textStyle: const TextStyle(
                                        fontSize: 17,
                                        height: 1.45,
                                      ),
                                      onTranscriptAppended: _markUnsaved,
                                      decoration: InputDecoration(
                                        hintText: AppLocalizations.of(
                                          context,
                                        )!.enterNotes,
                                        border: InputBorder.none,
                                        contentPadding:
                                            const EdgeInsets.fromLTRB(
                                          4,
                                          8,
                                          44,
                                          8,
                                        ),
                                      ),
                                    ),
                                  ),
                                ),
                                const SizedBox(height: 8),
                                // Before/after photos: ⋮ → Before treatment / After treatment
                                // Start AI Notes button — PRO+ feature.
                                if (ref.watch(doctorFeatureProvider(DoctorFeature.aiNotes)))
                                  _aiInlineVoiceCapture
                                      ? Padding(
                                          padding: const EdgeInsets.only(bottom: 8),
                                          child: InlineVoiceRecorderBar(
                                            titleLabel: AppLocalizations.of(
                                              context,
                                            )!.recordingForAiNotes,
                                            confirmButtonLabel: AppLocalizations.of(
                                              context,
                                            )!.processRecording,
                                            onRecordingComplete: (filePath, _) async {
                                              setState(() => _aiInlineVoiceCapture = false);
                                              await _uploadScribeRecording(filePath);
                                            },
                                            onCancel: () {
                                              setState(() => _aiInlineVoiceCapture = false);
                                            },
                                          ),
                                        )
                                      : ShifaSecondaryButton(
                                          label: AppLocalizations.of(
                                            context,
                                          )!.startAiNotes,
                                          onPressed: _startAiNotes,
                                          icon: Icons.mic,
                                          width: ButtonWidth.fill,
                                        ),
                                if (_beforeTreatmentImages.isNotEmpty ||
                                    _afterTreatmentImages.isNotEmpty)
                                  Padding(
                                    padding: const EdgeInsets.only(top: 8),
                                    child: Wrap(
                                      spacing: 8,
                                      runSpacing: 8,
                                      children: [
                                        ..._beforeTreatmentImages
                                            .asMap()
                                            .entries
                                            .map((entry) {
                                              final index = entry.key;
                                              return Chip(
                                                label: Text(
                                                  '${AppLocalizations.of(context)!.beforeTreatment} ${index + 1}',
                                                ),
                                                onDeleted: () {
                                                  setState(() {
                                                    _hasUnsavedChanges = true;
                                                    _beforeTreatmentImages
                                                        .removeAt(index);
                                                  });
                                                },
                                                backgroundColor: brand
                                                    .withOpacity(0.1),
                                                deleteIconColor: brand,
                                              );
                                            }),
                                        ..._afterTreatmentImages
                                            .asMap()
                                            .entries
                                            .map((entry) {
                                              final index = entry.key;
                                              return Chip(
                                                label: Text(
                                                  '${AppLocalizations.of(context)!.afterTreatment} ${index + 1}',
                                                ),
                                                onDeleted: () {
                                                  setState(() {
                                                    _hasUnsavedChanges = true;
                                                    _afterTreatmentImages
                                                        .removeAt(index);
                                                  });
                                                },
                                                backgroundColor: brand
                                                    .withOpacity(0.1),
                                                deleteIconColor: brand,
                                              );
                                            }),
                                      ],
                                    ),
                                  ),
                              ],
                            )
                          : _documentationType == 'dental'
                              ? (_dentalDocumentationFullScreen
                                  ? const SizedBox.shrink()
                                  : DentalVisitDocumentationPanel(
                                      key: _dentalDocPanelKey,
                                      appointmentId: widget.appointment.id,
                                      brand: brand,
                                      onUnsavedChanged: (v) =>
                                          setState(
                                            () => _hasUnsavedChanges = v,
                                          ),
                                    ))
                              : _build0252Panel(brand, patientId, patientIdAsync),
                    ),
                  ),
                ],
              ),
            ),
            ),
          ConsultationStickyFooter(
            hasPatientSignature: _hasPatientSignature,
            signatureRequested: _signatureRequested,
            showRequestSignatureButton:
                !_hasPatientSignature && !_signatureRequested,
            isRequestingSignature: _isRequestingSignature,
            onRequestSignature: _requestSignature,
            isEndingAppointment: _isSaving,
            onEndAppointment: _endAppointment,
          ),
            ],
          ),
          if (_documentationType == 'dental' &&
              _dentalDocumentationFullScreen)
            Positioned.fill(
              child: _buildDentalDocumentationFullScreenOverlay(brand),
            ),
        ],
      ),
    );
  }

  Widget _buildDentalDocumentationFullScreenOverlay(Color brand) {
    final l10n = AppLocalizations.of(context)!;
    return Material(
      color: Colors.black54,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Container(
            decoration: BoxDecoration(
              color: Colors.white,
              borderRadius: BorderRadius.circular(16),
              boxShadow: [
                BoxShadow(
                  color: Colors.black.withValues(alpha: 0.2),
                  blurRadius: 12,
                  spreadRadius: 2,
                ),
              ],
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 16, 8, 8),
                  child: Row(
                    children: [
                      Text(
                        l10n.docModeDental,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const Spacer(),
                      IconButton(
                        icon: const Icon(Icons.fullscreen_exit),
                        tooltip: l10n.collapse,
                        onPressed: () => setState(
                          () => _dentalDocumentationFullScreen = false,
                        ),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    child: DentalVisitDocumentationPanel(
                      key: _dentalDocPanelKey,
                      appointmentId: widget.appointment.id,
                      brand: brand,
                      onUnsavedChanged: (v) =>
                          setState(() => _hasUnsavedChanges = v),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
