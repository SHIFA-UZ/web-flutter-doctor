// lib/features/appointments/presentation/in_person_appointment_screen.dart
import 'dart:async';
import 'dart:convert';
import 'dart:io';
import 'dart:typed_data';
import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/theme/app_colors.dart';
import 'package:shifa_doc_app_v1/core/layout/platform_layout.dart';
import 'package:shifa_doc_app_v1/core/layout/responsive.dart';
import 'package:shifa_doc_app_v1/core/widgets/app_page_back_button.dart';
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
import 'package:shifa_doc_app_v1/state/clinic/clinic_providers.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/appointment_form_0252_panel.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/appointment_treatment_plan_panel.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/appointment_plan_finance_card.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_visit_documentation_panel.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_actions.dart';
import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
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
  int? _linkedPlanId;
  String? _linkedPlanTitle;
  List<int> _fulfilledLineIds = const [];
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
  bool _form0252DocumentationFullScreen = false;
  final GlobalKey<DentalVisitDocumentationPanelState> _dentalDocPanelKey =
      GlobalKey<DentalVisitDocumentationPanelState>();
  final GlobalKey<AppointmentTreatmentPlanPanelState> _treatmentPlanPanelKey =
      GlobalKey<AppointmentTreatmentPlanPanelState>();
  final GlobalKey<AppointmentPlanFinanceCardState> _planFinanceKey =
      GlobalKey<AppointmentPlanFinanceCardState>();

  int? _activePlanId;
  TreatmentPlanDetailDto? _activePlanDetail;
  List<FulfillmentCandidateDto> _fulfillmentCandidates = const [];
  bool _loadingPlanContext = false;

  /// When true, helper sections inside Notes are visible.
  bool _notesSectionsExpanded = false;

  /// Which helper source to show: 'ai' for Shifa AI, '0252' for last 025-2 form, or null for none.
  String? _expandedNoteSource;

  /// When true, inline AI scribe recorder is shown instead of the Start AI Notes button.
  bool _aiInlineVoiceCapture = false;
  bool _awaitingScribe = false;
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

  Future<void> _persistAppointmentDrafts() async {
    if (_documentationType == 'dental') {
      await _dentalDocPanelKey.currentState?.flushSave();
    }
  }

  Future<void> _leaveAppointmentScreen() async {
    await _persistAppointmentDrafts();
    if (mounted) appPop(context);
  }

  Future<void> _setDentalDocumentationFullScreen(bool value) async {
    if (_documentationType != 'dental') return;
    if (value) {
      await _persistAppointmentDrafts();
    }
    if (!mounted) return;
    setState(() => _dentalDocumentationFullScreen = value);
  }

  Future<void> _setForm0252DocumentationFullScreen(bool value) async {
    if (_documentationType != '025-2') return;
    if (!mounted) return;
    setState(() => _form0252DocumentationFullScreen = value);
  }

  void _applyMobileDocumentationLayout(String mode) {
    if (!PlatformLayout.useSinglePane(context)) return;
    if (mode == '025-2') {
      _docPanelCollapsed = true;
      _form0252DocumentationFullScreen = true;
      _dentalDocumentationFullScreen = false;
    } else if (mode == 'dental') {
      _form0252DocumentationFullScreen = false;
    } else {
      _form0252DocumentationFullScreen = false;
      _dentalDocumentationFullScreen = false;
    }
  }

  void _clearGeneralNoteFields() {
    _notesController.clear();
    _soapSubjective.clear();
    _soapObjective.clear();
    _soapAssessment.clear();
    _soapPlan.clear();
  }

  void _appendToActiveDocumentationNotes(String toAdd) {
    if (toAdd.trim().isEmpty) return;
    if (_documentationType == 'dental') {
      _dentalDocPanelKey.currentState?.appendClinicalNotes(toAdd);
      setState(() => _hasUnsavedChanges = true);
      return;
    }
    if (_notesController.text.trim().isNotEmpty) {
      _notesController.text += '\n\n';
    }
    _notesController.text += toAdd;
    _markUnsaved();
  }

  bool get _showDocumentationNoteHelpers =>
      _documentationType == 'general' || _documentationType == 'dental';

  /// [ref.listen] does not fire when [profileAllProvider] is already loaded; sync from [ref.watch] instead.
  void _applyProfessionDocumentationDefaultIfReady(Map<String, dynamic> profile) {
    if (_documentationProfessionDefaultApplied) return;
    final prof = profile['profession'] as String?;
    if (prof == null || prof.trim().isEmpty) return;
    _documentationProfessionDefaultApplied = true;
    if (_userSelectedDocumentationType) return;
    final mode = documentationTemplateForProfession(prof) == DocumentationTemplate.dental
        ? 'dental'
        : 'general';
    if (_documentationType == mode) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      setState(() {
        _documentationType = mode;
        if (mode != 'dental') {
          _dentalDocumentationFullScreen = false;
        }
        if (mode != '025-2') {
          _form0252DocumentationFullScreen = false;
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
        _linkedPlanId = (map['linkedPlanId'] as num?)?.toInt();
        _linkedPlanTitle = map['linkedPlanTitle'] as String?;
        final fulfilledRaw = map['fulfilledLineIds'];
        _fulfilledLineIds = fulfilledRaw is List
            ? fulfilledRaw
                .whereType<num>()
                .map((e) => e.toInt())
                .toList()
            : const [];
        if (_linkedPlanId != null && _activePlanId != _linkedPlanId) {
          _activePlanId = _linkedPlanId;
          unawaited(_loadActivePlanContext(_linkedPlanId));
        }
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

  Future<void> _onPlanSelected(int? planId) async {
    setState(() => _activePlanId = planId);
    await _loadActivePlanContext(planId);
  }

  Future<void> _loadActivePlanContext(int? planId) async {
    if (planId == null) {
      if (mounted) {
        setState(() {
          _activePlanDetail = null;
          _fulfillmentCandidates = const [];
          _loadingPlanContext = false;
        });
      }
      return;
    }
    setState(() => _loadingPlanContext = true);
    try {
      final appointmentIdInt = int.tryParse(widget.appointment.id) ?? 0;
      final detail = await fetchTreatmentPlanDetail(ref, planId);
      final candidates = await fetchFulfillmentCandidates(
        ref,
        planId: planId,
        appointmentId: appointmentIdInt,
      );
      if (!mounted) return;
      setState(() {
        _activePlanDetail = detail;
        _fulfillmentCandidates = candidates;
        _loadingPlanContext = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _activePlanDetail = null;
          _fulfillmentCandidates = const [];
          _loadingPlanContext = false;
        });
      }
    }
  }

  Widget _buildDentalDocumentationColumn(
    Color brand,
    int? clinicId, {
    bool expand = true,
  }) {
    final detail = _activePlanDetail;
    final panel = DentalVisitDocumentationPanel(
      key: _dentalDocPanelKey,
      appointmentId: widget.appointment.id,
      brand: brand,
      onUnsavedChanged: (v) => setState(() => _hasUnsavedChanges = v),
      activePlanId: _activePlanId,
      planTitle: _linkedPlanTitle ?? detail?.summary.title,
      dentalPlanDocumentation: detail?.dentalPlanDocumentation,
      planLines: detail?.lines ?? const [],
      fulfillmentCandidates: _fulfillmentCandidates,
      fulfilledLineIds: _fulfilledLineIds,
      linesTotalCount: detail?.summary.linesTotalCount ?? 0,
      linesCompletedCount: detail?.summary.linesCompletedCount ?? 0,
      loadingPlanContext: _loadingPlanContext,
      planSummary: detail?.summary,
      onFulfillmentChanged: () => setState(() {}),
      onRetryLoadPlan: () => _loadActivePlanContext(_activePlanId),
      primaryScroll: expand,
    );
    final finance = (_activePlanId != null && clinicId != null)
        ? AppointmentPlanFinanceCard(
            key: _planFinanceKey,
            clinicId: clinicId,
            planId: _activePlanId!,
            appointmentId: widget.appointment.id,
            brand: brand,
            embedded: true,
            onTotalsRefreshed: () {
              if (mounted) setState(() {});
            },
          )
        : null;

    if (!expand) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        mainAxisSize: MainAxisSize.min,
        children: [
          panel,
          if (finance != null) ...[
            const SizedBox(height: 8),
            finance,
          ],
        ],
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Expanded(child: panel),
        if (finance != null) ...[
          const SizedBox(height: 8),
          finance,
        ],
      ],
    );
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
        _awaitingScribe = true;
        ref.invalidate(
          consultationNotesForAppointmentProvider(widget.appointment.id),
        );
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(l10n.aiNotesUploaded),
            backgroundColor: Colors.green,
          ),
        );
      } else if (response.statusCode == 429) {
        var backendMsg = '';
        try {
          final j = jsonDecode(response.body);
          if (j is Map<String, dynamic> && j['message'] is String) {
            backendMsg = j['message'] as String;
          }
        } catch (_) {}
        final friendly = AiStreamException(
          'RATE_LIMIT',
          backendMsg.isEmpty ? 'Too many requests' : backendMsg,
        ).userFacingMessage;
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(friendly),
            backgroundColor: Colors.red,
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

  Future<ScribeStatusDto> _waitForScribeUi() async {
    if (!mounted) {
      return const ScribeStatusDto(status: 'none');
    }
    final l10n = AppLocalizations.of(context)!;
    showDialog<void>(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        content: Row(
          children: [
            const SizedBox(
              width: 28,
              height: 28,
              child: CircularProgressIndicator(strokeWidth: 3),
            ),
            const SizedBox(width: 16),
            Expanded(child: Text(l10n.preparingAiDocumentation)),
          ],
        ),
      ),
    );
    try {
      return await waitForScribeReady(
        api: ref.read(apiClientProvider),
        appointmentId: widget.appointment.id,
      );
    } finally {
      if (mounted) {
        Navigator.of(context, rootNavigator: true).pop();
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

      final l10nForPdf = AppLocalizations.of(context)!;
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

      var dentalNotesForPdf = '';
      AppointmentPdfDentalBilling? dentalBilling;
      if (_documentationType == 'dental') {
        final dentalState = _dentalDocPanelKey.currentState;
        await dentalState?.flushSave();
        dentalNotesForPdf = dentalState?.dentalClinicalNotesPdfText ?? '';
        dentalBilling = dentalState?.buildDentalPdfBilling(l10nForPdf);
      }

      final hasBeforeImages = _beforeTreatmentImages.isNotEmpty;
      final hasAfterImages = _afterTreatmentImages.isNotEmpty;
      final dentalState = _dentalDocPanelKey.currentState;
      final dentalHasWork = _documentationType == 'dental' &&
          (dentalState?.hasBillableContent ?? false);
      final hasExtras = hasBeforeImages || hasAfterImages || dentalHasWork;

      if (structuredAndFree.trim().isEmpty &&
          dentalNotesForPdf.isEmpty &&
          !hasExtras &&
          !_awaitingScribe) {
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

      final cidForComplete = ref.read(selectedClinicIdProvider);

      try {
        final planApplied =
            await _dentalDocPanelKey.currentState?.applyPendingIfNeeded(
                  silent: true,
                ) ??
                true;
        if (!planApplied && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(context)!
                    .translate('appointmentPlanApplyFailed'),
              ),
              backgroundColor: Colors.red,
            ),
          );
          return;
        }

        final paymentOk =
            await _planFinanceKey.currentState?.recordSessionPaymentIfNeeded() ??
                true;
        if (!paymentOk && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                AppLocalizations.of(context)!
                    .translate('appointmentPlanPaymentFailed'),
              ),
              backgroundColor: Colors.red,
            ),
          );
          return;
        }

        var completeResult = const CompleteAppointmentResult();
        try {
          completeResult = await completeAppointmentVisit(
            api: api,
            appointmentId: widget.appointment.id,
            clinicId: cidForComplete,
            doctorNotes: structuredAndFree,
            awaitingScribe: _awaitingScribe,
          );
          debugPrint(
            'Appointment ${widget.appointment.id} marked as completed',
          );
        } catch (e) {
          debugPrint('Error marking appointment as completed: $e');
          if (mounted) {
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(AppLocalizations.of(context)!.somethingWentWrong),
                backgroundColor: Colors.red,
              ),
            );
          }
          return;
        }

        var scribeStatus = ScribeStatusDto(
          status: completeResult.hasScribeNote ? 'ready' : 'none',
          hasDocumentation: completeResult.hasDocumentation,
          hasScribeNote: completeResult.hasScribeNote,
        );
        if ((completeResult.scribePending || _awaitingScribe) &&
            !completeResult.hasScribeNote) {
          scribeStatus = await _waitForScribeUi();
        }

        String? pdfSignatureBase64;
        String? pdfSignedAtStr;
        List<int> fulfilledForPdf = _fulfilledLineIds;
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
              final fulfilledRaw = map['fulfilledLineIds'];
              fulfilledForPdf = fulfilledRaw is List
                  ? fulfilledRaw
                      .whereType<num>()
                      .map((e) => e.toInt())
                      .toList()
                  : fulfilledForPdf;
            }
          }
        } catch (_) {}

        TreatmentPlanDetailDto? planDetailForPdf = _activePlanDetail;
        if (_activePlanId != null) {
          try {
            planDetailForPdf =
                await fetchTreatmentPlanDetail(ref, _activePlanId!);
          } catch (_) {}
        }

        final sessionPaymentMinor =
            _planFinanceKey.currentState?.recordedPaymentMinor;
        final sessionPaymentMethod =
            _planFinanceKey.currentState?.recordedPaymentMethod;

        var dentalNotesForPdfFinal = dentalNotesForPdf;
        AppointmentPdfDentalBilling? dentalBillingFinal = dentalBilling;
        AppointmentPdfTreatmentPlanSection? planPdfSection;
        if (_documentationType == 'dental' && _activePlanId != null) {
          dentalBillingFinal = null;
          final lines = planDetailForPdf?.lines ?? const [];
          final summary = planDetailForPdf?.summary;
          if (summary != null) {
            final fulfilledLines = <AppointmentPdfDentalLine>[];
            for (final lineId in fulfilledForPdf) {
              LineDetailDto? line;
              for (final l in lines) {
                if (l.id == lineId) {
                  line = l;
                  break;
                }
              }
              if (line == null) continue;
              var tooth = '';
              final meta = line.specialtyMetadata;
              if (meta != null && meta.isNotEmpty) {
                try {
                  final m = jsonDecode(meta) as Map<String, dynamic>?;
                  tooth = m?['fdi']?.toString() ?? '';
                } catch (_) {}
              }
              fulfilledLines.add(
                AppointmentPdfDentalLine(
                  tooth: tooth,
                  serviceTitle: line.title,
                  amountMinor: line.lineTotalMinor,
                  currency: line.currency,
                ),
              );
            }
            planPdfSection = AppointmentPdfTreatmentPlanSection(
              planId: '${_activePlanId}',
              planTitle: _linkedPlanTitle ?? summary.title,
              planTotalMinor: summary.totalMinor,
              planPaidMinor: summary.paidMinor,
              planOwedMinor: summary.owedMinor,
              currency: summary.currency,
              fulfilledThisVisit: fulfilledLines,
              sessionPaymentMinor: sessionPaymentMinor,
              sessionPaymentMethod: sessionPaymentMethod,
            );
          }
        }

        List<ConsultationNoteDto> consultationNotes = [];
        List<DraftNoteDto> draftNotes = [];
        try {
          ref.invalidate(
            consultationNotesForAppointmentProvider(widget.appointment.id),
          );
          ref.invalidate(
            draftNotesForAppointmentProvider(widget.appointment.id),
          );
          consultationNotes = await ref.read(
            consultationNotesForAppointmentProvider(widget.appointment.id)
                .future,
          );
          draftNotes = await ref.read(
            draftNotesForAppointmentProvider(widget.appointment.id).future,
          );
        } catch (_) {}

        final combinedNotesFinal = [
          composeVisitDocumentationText(
            scribeHeading: l10nForPdf.fromShifaAi,
            doctorHeading: l10nForPdf.doctorNotesSection,
            notes: consultationNotes,
            drafts: draftNotes,
            extraDoctorNotes: consultationNotes.any((n) => !n.isFromAi)
                ? ''
                : structuredAndFree,
          ),
          if (dentalNotesForPdfFinal.isNotEmpty) dentalNotesForPdfFinal,
        ].where((t) => t.trim().isNotEmpty).join('\n\n').trim();
        final hasNotesFinal = combinedNotesFinal.isNotEmpty;

        if (!hasNotesFinal && !hasExtras) {
          if (mounted) {
            final savedOnBackend = scribeStatus.hasDocumentation ||
                completeResult.hasDocumentation ||
                scribeStatus.hasScribeNote;
            ScaffoldMessenger.of(context).showSnackBar(
              SnackBar(
                content: Text(
                  savedOnBackend
                      ? l10nForPdf.appointmentEndedDocumentationSaved
                      : (completeResult.scribePending || _awaitingScribe
                          ? l10nForPdf.aiDocumentationWillAppearShortly
                          : l10nForPdf.noItemsToSave),
                ),
                backgroundColor: savedOnBackend ? Colors.green : null,
              ),
            );
            Navigator.pop(context);
          }
          return;
        }

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

        final pdfData = AppointmentPdfData(
          appointmentId: widget.appointment.id,
          clinicName: widget.appointment.isVideo
              ? null
              : widget.appointment.location.trim().isEmpty
                  ? null
                  : widget.appointment.location.trim(),
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
          notes: hasNotesFinal ? combinedNotesFinal : null,
          dentalBilling: dentalBillingFinal,
          treatmentPlan: planPdfSection,
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
          category: 'APPOINTMENT_NOTE',
          appointmentId: widget.appointment.id,
        );

        debugPrint('Combined PDF saved successfully');

        try {
          await invalidateAppointmentRelatedProviders(
            ref,
            clinicWorkspaceId: cidForComplete,
          );
        } catch (e) {
          debugPrint('Post-complete provider refresh failed (ignored): $e');
        }
        try {
          ref.invalidate(
            patientDocumentsProvider(PatientDocumentsKey(patientId: patientId)),
          );
        } catch (e) {
          debugPrint('Post-complete documents invalidate failed (ignored): $e');
        }

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
                ref.invalidate(patientDocumentsProvider(PatientDocumentsKey(patientId: resolvedId)));
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
          ref.invalidate(patientDocumentsProvider(PatientDocumentsKey(patientId: patientId)));
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
        ? ref.watch(patientDocumentsProvider(PatientDocumentsKey(patientId: patientId)))
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
                onBack: _leaveAppointmentScreen,
              ),
              Expanded(
                child: Padding(
                  padding: Responsive.screenPadding(context).copyWith(bottom: 0),
                  child: LayoutBuilder(
                    builder: (context, constraints) {
                      final stackVertically = PlatformLayout.useSinglePane(context);
                      final expandPanels = !stackVertically;
                      final gap = stackVertically ? 12.0 : 24.0;
                      final docCollapsed = _docPanelCollapsed &&
                          (!stackVertically || _documentationType == '025-2');
                      Widget flexChild({required Widget child}) =>
                          expandPanels ? Expanded(child: child) : child;

                      final footer = ConsultationStickyFooter(
                        pinned: expandPanels,
                        hasPatientSignature: _hasPatientSignature,
                        signatureRequested: _signatureRequested,
                        showRequestSignatureButton:
                            !_hasPatientSignature && !_signatureRequested,
                        isRequestingSignature: _isRequestingSignature,
                        onRequestSignature: _requestSignature,
                        isEndingAppointment: _isSaving,
                        onEndAppointment: _endAppointment,
                      );

                      final notesCard = DocumentationSectionCard(
                      title: _documentationType == 'general'
                          ? AppLocalizations.of(context)!.notes
                          : _documentationType == 'dental'
                              ? AppLocalizations.of(context)!.docModeDental
                              : AppLocalizations.of(context)!.docMode0252,
                      expandBody: expandPanels,
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
                              onPressed: () => _setDentalDocumentationFullScreen(true),
                            ),
                          if (_documentationType == '025-2' &&
                              !_form0252DocumentationFullScreen &&
                              stackVertically)
                            IconButton(
                              icon: const Icon(Icons.fullscreen),
                              tooltip: AppLocalizations.of(context)!.expand,
                              onPressed: () =>
                                  _setForm0252DocumentationFullScreen(true),
                            ),
                          PopupMenuButton<String>(
                            icon: const Icon(Icons.more_vert),
                            tooltip: AppLocalizations.of(context)!.notes,
                            onSelected: (value) async {
                              final l10n = AppLocalizations.of(context)!;

                              // Source actions (AI / 025-2 form) – general + dental modes.
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
                              if (value != '025-2') {
                                _form0252DocumentationFullScreen = false;
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
                                    _applyMobileDocumentationLayout(value);
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
                                    _applyMobileDocumentationLayout(value);
                                  });
                                }
                              } else {
                                setState(() {
                                  _documentationType = value;
                                  _applyMobileDocumentationLayout(value);
                                });
                              }
                            },
                            itemBuilder: (context) {
                              final l10n = AppLocalizations.of(context)!;
                              final items = <PopupMenuEntry<String>>[];
                              if (_showDocumentationNoteHelpers) {
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
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (!widget.appointment.isCompleted)
                            Builder(
                              builder: (context) {
                                final clinicId =
                                    ref.watch(selectedClinicIdProvider);
                                final pidInt = patientId != null
                                    ? int.tryParse(patientId)
                                    : null;
                                if (clinicId == null || pidInt == null) {
                                  return const SizedBox.shrink();
                                }
                                return Column(
                                  crossAxisAlignment:
                                      CrossAxisAlignment.stretch,
                                  children: [
                                    AppointmentTreatmentPlanPanel(
                                      key: _treatmentPlanPanelKey,
                                      clinicId: clinicId,
                                      patientId: pidInt,
                                      appointmentId: widget.appointment.id,
                                      brand: brand,
                                      linkedPlanId: _linkedPlanId,
                                      linkedPlanTitle: _linkedPlanTitle,
                                      fulfilledLineIds: _fulfilledLineIds,
                                      onPlanSelected: _onPlanSelected,
                                      onPlanLinked: _fetchSignatureStatus,
                                      embedded: true,
                                    ),
                                    const SizedBox(height: 12),
                                    Divider(
                                      color: Colors.grey.shade200,
                                      height: 1,
                                    ),
                                    const SizedBox(height: 12),
                                  ],
                                );
                              },
                            ),
                          flexChild(
                            child: _showDocumentationNoteHelpers
                          ? Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              mainAxisSize: expandPanels
                                  ? MainAxisSize.max
                                  : MainAxisSize.min,
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
                                                    if (toAdd.trim().isEmpty) {
                                                      return;
                                                    }
                                                    _appendToActiveDocumentationNotes(
                                                      toAdd,
                                                    );
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
                                                              .isEmpty) {
                                                            return;
                                                          }
                                                          _appendToActiveDocumentationNotes(
                                                            toAdd,
                                                          );
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
                                if (_documentationType == 'general') ...[
                                ConsultationSoapSection(
                                  l10n: AppLocalizations.of(context)!,
                                  subjective: _soapSubjective,
                                  objective: _soapObjective,
                                  assessment: _soapAssessment,
                                  plan: _soapPlan,
                                  onTranscriptAppended: _markUnsaved,
                                ),
                                const SizedBox(height: 8),
                                flexChild(
                                  child: Container(
                                    constraints: expandPanels
                                        ? null
                                        : const BoxConstraints(minHeight: 160),
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
                                      style: expandPanels
                                          ? DoctorSpeechInputStyle
                                              .borderlessExpanding
                                          : DoctorSpeechInputStyle.standard,
                                      expands: expandPanels,
                                      minLines: expandPanels ? null : 6,
                                      maxLines: expandPanels ? null : 12,
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
                                ] else if (!_dentalDocumentationFullScreen)
                                  flexChild(
                                    child: Builder(
                                      builder: (context) {
                                        final clinicId =
                                            ref.watch(selectedClinicIdProvider);
                                        return _buildDentalDocumentationColumn(
                                          brand,
                                          clinicId,
                                          expand: expandPanels,
                                        );
                                      },
                                    ),
                                  )
                                else if (expandPanels)
                                  const Expanded(child: SizedBox.shrink())
                                else
                                  const SizedBox.shrink(),
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
                              : (_form0252DocumentationFullScreen
                                  ? const SizedBox.shrink()
                                  : expandPanels
                                      ? LayoutBuilder(
                                          builder: (context, constraints) {
                                            return SizedBox(
                                              height: constraints.maxHeight,
                                              child: _build0252Panel(
                                                brand,
                                                patientId,
                                                patientIdAsync,
                                              ),
                                            );
                                          },
                                        )
                                      : _build0252Panel(
                                          brand,
                                          patientId,
                                          patientIdAsync,
                                        )),
                          ),
                        ],
                      ),
                      );

                      if (stackVertically) {
                        return SingleChildScrollView(
                          physics: const AlwaysScrollableScrollPhysics(),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              notesCard,
                              const SizedBox(height: 12),
                              footer,
                              const SizedBox(height: 16),
                            ],
                          ),
                        );
                      }

                      return Flex(
                        direction: Axis.horizontal,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          docCollapsed
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
                                      onTap: () => setState(
                                        () => _docPanelCollapsed = false,
                                      ),
                                      borderRadius: BorderRadius.circular(16),
                                      child: Column(
                                        mainAxisAlignment:
                                            MainAxisAlignment.center,
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
                                              AppLocalizations.of(context)!
                                                  .documents,
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
                                    title: AppLocalizations.of(context)!
                                        .documents,
                                    titleTrailing: IconButton(
                                      icon: const Icon(Icons.chevron_left),
                                      onPressed: () => setState(
                                        () => _docPanelCollapsed = true,
                                      ),
                                      tooltip: AppLocalizations.of(context)!
                                          .collapse,
                                    ),
                                    child: Column(
                                      crossAxisAlignment:
                                          CrossAxisAlignment.stretch,
                                      children: [
                                        Expanded(
                                          child: documentsAsync == null
                                              ? Center(
                                                  child: Text(
                                                    AppLocalizations.of(
                                                      context,
                                                    )!.patientIdNotAvailable,
                                                  ),
                                                )
                                              : documentsAsync.when(
                                                  data: (docs) {
                                                    final pid = patientId!;
                                                    return GroupedPatientDocumentsList(
                                                      documents: docs,
                                                      brand: brand,
                                                      patientId: pid,
                                                      onRequestAccess:
                                                          (doc) async {
                                                        final client = ref.read(
                                                          apiClientProvider,
                                                        );
                                                        await requestDocumentAccessWithClient(
                                                          client: client,
                                                          patientId: pid,
                                                          documentId: doc.id,
                                                        );
                                                        ref.invalidate(
                                                          patientDocumentsProvider(
                                                            PatientDocumentsKey(
                                                              patientId: pid,
                                                            ),
                                                          ),
                                                        );
                                                      },
                                                    );
                                                  },
                                                  loading: () => const Center(
                                                    child:
                                                        CircularProgressIndicator(),
                                                  ),
                                                  error: (err, _) => Center(
                                                    child: Text(
                                                      '${AppLocalizations.of(context)!.error}: $err',
                                                    ),
                                                  ),
                                                ),
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
                          SizedBox(width: gap),
                          Expanded(child: notesCard),
                        ],
                      );
                    },
                  ),
                ),
              ),
              if (!PlatformLayout.useSinglePane(context))
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
          if (_documentationType == '025-2' &&
              _form0252DocumentationFullScreen)
            Positioned.fill(
              child: _buildForm0252DocumentationFullScreenOverlay(
                brand,
                patientId,
                patientIdAsync,
              ),
            ),
        ],
      ),
    );
  }

  Widget _buildForm0252DocumentationFullScreenOverlay(
    Color brand,
    String? patientId,
    AsyncValue<String?>? patientIdAsync,
  ) {
    final l10n = AppLocalizations.of(context)!;
    return Material(
      color: Colors.black54,
      child: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
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
                        l10n.docMode0252,
                        style: const TextStyle(
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                      const Spacer(),
                      IconButton(
                        icon: const Icon(Icons.fullscreen_exit),
                        tooltip: l10n.collapse,
                        onPressed: () =>
                            _setForm0252DocumentationFullScreen(false),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    child: _build0252Panel(brand, patientId, patientIdAsync),
                  ),
                ),
              ],
            ),
          ),
        ),
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
                        onPressed: () => _setDentalDocumentationFullScreen(false),
                      ),
                    ],
                  ),
                ),
                Expanded(
                  child: Padding(
                    padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                    child: Builder(
                      builder: (context) {
                        final clinicId = ref.watch(selectedClinicIdProvider);
                        return _buildDentalDocumentationColumn(brand, clinicId);
                      },
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
