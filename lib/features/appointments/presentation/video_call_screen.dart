// lib/features/appointments/presentation/video_call_screen.dart
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
import 'dart:async';
// Conditional import: daily_flutter only works on native platforms, not web
import 'package:daily_flutter/daily_flutter.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/core/services/daily_flutter_stub.dart';
// Conditional import: embed Daily.co iframe on web (keeps call in-app instead of new tab)
import 'package:shifa_doc_app_v1/features/appointments/presentation/daily_video_embed_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/appointments/presentation/daily_video_embed_web.dart'
    as daily_embed;
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/services/daily_video_service.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_actions.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_documents_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart';
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_data.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_service.dart';
import 'package:shifa_doc_app_v1/features/appointments/services/appointment_pdf_translations.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/appointment_form_0252_panel.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/state/patients/patient_forms_provider.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/widgets/consultation_documentation_widgets.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/widgets/consultation_document_upload_strip.dart';
import 'package:shifa_doc_app_v1/features/appointments/presentation/widgets/consultation_soap_section.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_speech_text_field.dart';
import 'package:shifa_doc_app_v1/core/api/consultation_notes_api.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_documentation_professions.dart';
import 'package:shifa_doc_app_v1/features/appointments/dental/dental_visit_documentation_panel.dart';

/// Maps English video token errors from the backend / [DailyVideoService] to [AppLocalizations].
String _localizeDoctorVideoError(BuildContext context, String errStr) {
  final l10n = AppLocalizations.of(context)!;
  var trimmed = errStr.trim();
  for (var i = 0; i < 8; i++) {
    final next = trimmed.replaceFirst(RegExp(r'^Exception:\s*'), '').trim();
    if (next == trimmed) break;
    trimmed = next;
  }
  final lower = trimmed.toLowerCase();

  const endedBackend =
      'Video call has ended. The join window closes 15 minutes after the appointment end.';
  const endedClient =
      'Video call is not available. It may have ended, or the join window has closed (usually 15 minutes after the appointment end).';
  const notYet =
      'Video call is not yet available. You can join 5 minutes before the appointment start.';
  const payment =
      'Payment is required before joining this video consultation.';

  if (trimmed == endedBackend ||
      trimmed == endedClient ||
      lower.contains('video call has ended') ||
      (lower.contains('join window has closed') &&
          lower.contains('15 minutes after')) ||
      (lower.contains('join window closes') &&
          lower.contains('15 minutes after'))) {
    return l10n.videoCallJoinWindowClosedMessage;
  }
  if (trimmed == notYet ||
      (lower.contains('not yet available') &&
          lower.contains('5 minutes before'))) {
    return l10n.videoCallNotYetAvailableMessage;
  }
  if (trimmed == payment ||
      (lower.contains('payment is required') &&
          lower.contains('video'))) {
    return l10n.videoCallPaymentRequiredMessage;
  }
  if (trimmed == 'Call error occurred') {
    return l10n.callErrorOccurred;
  }
  return trimmed;
}

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

class VideoCallScreen extends ConsumerStatefulWidget {
  const VideoCallScreen({super.key, required this.appointment});
  final Appointment appointment;

  @override
  ConsumerState<VideoCallScreen> createState() => _VideoCallScreenState();
}

class _VideoCallScreenState extends ConsumerState<VideoCallScreen> {
  final TextEditingController _notesController = TextEditingController();
  final TextEditingController _soapSubjective = TextEditingController();
  final TextEditingController _soapObjective = TextEditingController();
  final TextEditingController _soapAssessment = TextEditingController();
  final TextEditingController _soapPlan = TextEditingController();
  final List<XFile> _beforeTreatmentImages = [];
  final List<XFile> _afterTreatmentImages = [];
  bool _isSaving = false;
  bool _warningShown = false;
  String _documentationType = 'general';
  bool _hasUnsavedChanges = false;
  bool _documentationProfessionDefaultApplied = false;
  bool _userSelectedDocumentationType = false;

  /// When false, "From Shifa AI" and "From last 025-2 form" are hidden; expand via ⋮ → Show.
  bool _notesSectionsExpanded = false;

  /// Which note source to display: 'ai' for AI notes, '0252' for form 025-2, null for none
  String? _expandedNoteSource;
  final GlobalKey<AppointmentForm0252PanelState> _form0252PanelKey =
      GlobalKey<AppointmentForm0252PanelState>();
  final GlobalKey<DentalVisitDocumentationPanelState> _dentalDocPanelKey =
      GlobalKey<DentalVisitDocumentationPanelState>();
  bool _notesPanelFullScreen = false;
  int _consultationNoteIndex = 0;

  // Digital signature (MVP): request + polling
  bool _signatureRequested = false;
  String? _patientSignedAt; // ISO string when patient signed
  String? _patientSignatureImageBase64;
  bool _isRequestingSignature = false;
  Timer? _signaturePollTimer;

  /// True only when we have a real signature (non-empty signed-at timestamp).
  bool get _hasPatientSignature =>
      _patientSignedAt != null &&
      _patientSignedAt!.trim().isNotEmpty &&
      _patientSignedAt != 'null';

  // Daily.co video call state
  // For mobile: CallClient, for web: iframe URL
  dynamic _callClient; // CallClient on mobile, String (roomUrl) on web
  DailyVideoService? _videoService;
  bool _isVideoInitialized = false;
  bool _isVideoLoading = true;
  bool _isMuted = false;
  bool _isVideoOff = false;
  bool _isScreenSharing = false;
  /// Raw English/technical message from the API or client; localized in [build] via [_localizeDoctorVideoError].
  String? _videoErrorRaw;
  StreamSubscription? _eventSubscription; // CallEvent on mobile, null on web
  String? _roomUrl; // For web
  String? _token; // For web

  // Helper to reliably detect if we're on web platform
  // Uses state variables as fallback since kIsWeb might not work in production builds
  bool get _isWebPlatform {
    // Primary check: kIsWeb
    if (kIsWeb) return true;
    // Fallback: if we have roomUrl/token but no callClient, we're on web
    if (_roomUrl != null && _token != null && _callClient == null) return true;
    // Additional fallback: if callClient is null and we're initialized, likely web
    if (_isVideoInitialized && _callClient == null && _roomUrl != null)
      return true;
    return false;
  }

  void _markUnsaved() {
    if (!_hasUnsavedChanges) setState(() => _hasUnsavedChanges = true);
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
    final mode = isDentalDocumentationProfession(prof) ? 'dental' : 'general';
    if (_documentationType == mode) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      setState(() => _documentationType = mode);
    });
  }

  @override
  void initState() {
    super.initState();
    _notesController.addListener(_markUnsaved);
    _soapSubjective.addListener(_markUnsaved);
    _soapObjective.addListener(_markUnsaved);
    _soapAssessment.addListener(_markUnsaved);
    _soapPlan.addListener(_markUnsaved);
    // Check for chronic disease warning after first frame
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _checkChronicDiseaseWarning();
      _initializeVideoCall();
      _fetchSignatureStatus();
    });
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
          _startSignaturePolling();
        } else {
          _stopSignaturePolling();
        }
      });
    } catch (e) {
      debugPrint('Fetch signature status: $e');
    }
  }

  void _startSignaturePolling() {
    _signaturePollTimer?.cancel();
    _signaturePollTimer = Timer.periodic(const Duration(seconds: 10), (
      _,
    ) async {
      await _fetchSignatureStatus();
    });
  }

  void _stopSignaturePolling() {
    _signaturePollTimer?.cancel();
    _signaturePollTimer = null;
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
        _startSignaturePolling();
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

  Future<void> _initializeVideoCall() async {
    try {
      setState(() {
        _isVideoLoading = true;
        _videoErrorRaw = null;
      });

      final api = ref.read(apiClientProvider);
      _videoService = DailyVideoService(api);

      // Get video token from backend
      final appointmentId = int.tryParse(widget.appointment.id) ?? 0;
      if (appointmentId == 0) {
        throw Exception('Invalid appointment ID');
      }

      final tokenData = await _videoService!.getVideoToken(
        appointmentId: appointmentId,
      );

      // Determine platform: web uses roomUrl/token, mobile uses CallClient
      // Force web path if kIsWeb is true OR if we're in a browser environment
      // The conditional import ensures daily_flutter_stub is used on web
      final bool isWebPlatform = kIsWeb;

      // Always use web path on web builds (conditional import handles the stub)
      if (isWebPlatform) {
        // Web: Use Daily.co Prebuilt iframe
        _roomUrl = tokenData.roomUrl;
        _token = tokenData.token;
        // On web, we'll open Daily.co Prebuilt in the iframe or new window
        setState(() {
          _isVideoInitialized = true;
          _isVideoLoading = false;
        });
      } else {
        // Mobile/Desktop: Use CallClient
        _callClient = CallClient();

        // Set up event listeners
        _eventSubscription = (_callClient as CallClient).events.listen((event) {
          _handleCallEvent(event);
        });

        // Join the call
        await (_callClient as CallClient).join(
          roomUrl: tokenData.roomUrl,
          token: tokenData.token,
        );

        setState(() {
          _isVideoInitialized = true;
          _isVideoLoading = false;
        });
      }

      setState(() {
        _isVideoInitialized = true;
        _isVideoLoading = false;
      });
    } catch (e) {
      debugPrint('Failed to initialize video call: $e');
      final errStr = e.toString().replaceFirst(RegExp(r'^Exception:\s*'), '');
      if (!mounted) return;
      final localized = _localizeDoctorVideoError(context, errStr);
      final wasKnownVideoMessage = localized != errStr.trim();
      setState(() {
        _videoErrorRaw = errStr;
        _isVideoLoading = false;
        _isVideoInitialized = false;
      });

      if (mounted) {
        final l10n = AppLocalizations.of(context)!;
        final snackMessage = wasKnownVideoMessage
            ? localized
            : '${l10n.failedToStartVideoCall}: $errStr';
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(snackMessage),
            backgroundColor: Colors.red,
            duration: const Duration(seconds: 6),
          ),
        );
      }
    }
  }

  void _handleCallEvent(dynamic event) {
    if (_isWebPlatform) {
      // Web events handled via iframe messages
      return;
    }

    // Mobile: Handle CallEvent
    if (event is CallStateUpdated) {
      final state = event.state;
      if (state == CallState.left) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.videoCallEnded),
            ),
          );
        }
      } else if (state == CallState.error) {
        if (mounted) {
          setState(() {
            _videoErrorRaw = 'Call error occurred';
          });
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.callErrorOccurred),
              backgroundColor: Colors.red,
            ),
          );
        }
      }
    } else if (event is ParticipantJoined) {
      debugPrint('Participant joined: ${event.participant.id}');
    } else if (event is ParticipantLeft) {
      debugPrint('Participant left: ${event.participant.id}');
    }
  }

  Future<void> _toggleMute() async {
    if (_isWebPlatform) {
      // Web: Controls are handled by Daily.co Prebuilt UI
      setState(() => _isMuted = !_isMuted);
      return;
    }

    if (_callClient != null) {
      try {
        await (_callClient as CallClient).setLocalAudio(!_isMuted);
        setState(() => _isMuted = !_isMuted);
      } catch (e) {
        debugPrint('Failed to toggle mute: $e');
      }
    }
  }

  Future<void> _toggleVideo() async {
    if (_isWebPlatform) {
      // Web: Controls are handled by Daily.co Prebuilt UI
      setState(() => _isVideoOff = !_isVideoOff);
      return;
    }

    if (_callClient != null) {
      try {
        await (_callClient as CallClient).setLocalVideo(!_isVideoOff);
        setState(() => _isVideoOff = !_isVideoOff);
      } catch (e) {
        debugPrint('Failed to toggle video: $e');
      }
    }
  }

  Future<void> _toggleScreenShare() async {
    if (_isWebPlatform) {
      // Web: Controls are handled by Daily.co Prebuilt UI
      setState(() => _isScreenSharing = !_isScreenSharing);
      return;
    }

    if (_callClient != null) {
      try {
        if (_isScreenSharing) {
          await (_callClient as CallClient).stopScreenShare();
        } else {
          await (_callClient as CallClient).startScreenShare();
        }
        setState(() => _isScreenSharing = !_isScreenSharing);
      } catch (e) {
        debugPrint('Failed to toggle screen share: $e');
      }
    }
  }

  Future<void> _endVideoCall() async {
    if (_isWebPlatform) {
      // Web: Navigate away or close iframe
      _roomUrl = null;
      _token = null;
      setState(() {
        _isVideoInitialized = false;
      });
      return;
    }

    if (_callClient != null) {
      try {
        await (_callClient as CallClient).leave();
        await _eventSubscription?.cancel();
        _callClient = null;
        _eventSubscription = null;
        setState(() {
          _isVideoInitialized = false;
        });
      } catch (e) {
        debugPrint('Error ending call: $e');
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

  Widget _buildVideoView() {
    // Use helper getter for reliable web detection
    if (_isWebPlatform) {
      // Web: Use Daily.co Prebuilt iframe
      if (_roomUrl == null || _token == null) {
        return const Center(child: CircularProgressIndicator());
      }

      // Embed Daily.co Prebuilt using iframe
      return _buildWebVideoView();
    } else {
      // Mobile: Use CallClient
      if (_callClient == null) {
        return const Center(child: CircularProgressIndicator());
      }

      // Get participants and render their video tracks
      final participants = (_callClient as CallClient).participants();

      if (participants.isEmpty) {
        return Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const CircularProgressIndicator(),
              const SizedBox(height: 16),
              Text(AppLocalizations.of(context)!.waitingForParticipants),
            ],
          ),
        );
      }

      // Render video tracks for each participant
      return Container(
        color: Colors.black,
        child: participants.length == 1
            ? _buildSingleParticipantView(participants.first)
            : _buildMultipleParticipantsView(participants),
      );
    }
  }

  Widget _buildWebVideoView() {
    // For web, embed Daily.co Prebuilt iframe directly in the screen
    // (no new tab - video call stays in-app per Daily.co Flutter docs)
    if (_roomUrl == null || _token == null) {
      return const Center(child: CircularProgressIndicator());
    }
    return Container(
      color: Colors.black,
      child: daily_embed.DailyVideoEmbedWeb(roomUrl: _roomUrl!, token: _token!),
    );
  }

  Widget _buildSingleParticipantView(Participant participant) {
    return Center(
      child: participant.videoTrack != null
          ? VideoView(
              track: participant.videoTrack!,
              mirror: participant.isLocal,
            )
          : const Center(
              child: Icon(Icons.person, size: 100, color: Colors.white54),
            ),
    );
  }

  Widget _buildMultipleParticipantsView(List<Participant> participants) {
    // Show local participant small, remote participant large
    final localParticipant = participants.firstWhere(
      (p) => p.isLocal,
      orElse: () => participants.first,
    );
    final remoteParticipant = participants.firstWhere(
      (p) => !p.isLocal,
      orElse: () => participants.last,
    );

    return Stack(
      children: [
        // Remote participant (large)
        Positioned.fill(
          child: remoteParticipant.videoTrack != null
              ? VideoView(track: remoteParticipant.videoTrack!)
              : const Center(
                  child: Icon(Icons.person, size: 100, color: Colors.white54),
                ),
        ),
        // Local participant (small, bottom right)
        Positioned(
          bottom: 100,
          right: 16,
          child: Container(
            width: 120,
            height: 160,
            decoration: BoxDecoration(
              color: Colors.grey[800],
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.white, width: 2),
            ),
            child: localParticipant.videoTrack != null
                ? ClipRRect(
                    borderRadius: BorderRadius.circular(10),
                    child: VideoView(
                      track: localParticipant.videoTrack!,
                      mirror: true,
                    ),
                  )
                : const Center(
                    child: Icon(Icons.person, size: 60, color: Colors.white54),
                  ),
          ),
        ),
      ],
    );
  }

  @override
  void dispose() {
    _signaturePollTimer?.cancel();
    _endVideoCall();
    _eventSubscription?.cancel();
    _notesController.removeListener(_markUnsaved);
    _soapSubjective.removeListener(_markUnsaved);
    _soapObjective.removeListener(_markUnsaved);
    _soapAssessment.removeListener(_markUnsaved);
    _soapPlan.removeListener(_markUnsaved);
    _notesController.dispose();
    _soapSubjective.dispose();
    _soapObjective.dispose();
    _soapAssessment.dispose();
    _soapPlan.dispose();
    super.dispose();
  }

  Future<void> _pickImage(bool isBeforeTreatment) async {
    final l10n = AppLocalizations.of(context)!;
    final picker = ImagePicker();

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
        image = await picker.pickImage(source: source, imageQuality: 85);
      }

      if (image != null) {
        setState(() {
          if (isBeforeTreatment) {
            _beforeTreatmentImages.add(image!);
          } else {
            _afterTreatmentImages.add(image!);
          }
          _hasUnsavedChanges = true;
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

  Future<void> _endAppointment() async {
    setState(() => _isSaving = true);

    // First, end the video call if it's active
    await _endVideoCall();

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

      var dentalNotesForPdf = '';
      AppointmentPdfDentalBilling? dentalBilling;
      if (_documentationType == 'dental') {
        final dentalState = _dentalDocPanelKey.currentState;
        await dentalState?.flushSave();
        dentalNotesForPdf = dentalState?.dentalClinicalNotesPdfText ?? '';
        dentalBilling = dentalState?.buildDentalPdfBilling(l10nForPdf);
      }

      final combinedNotes = [
        if (consultationNotesBlock.isNotEmpty) consultationNotesBlock,
        if (structuredAndFree.isNotEmpty) structuredAndFree,
        if (dentalNotesForPdf.isNotEmpty) dentalNotesForPdf,
      ].join('\n\n').trim();

      final hasNotes = combinedNotes.isNotEmpty;
      final hasBeforeImages = _beforeTreatmentImages.isNotEmpty;
      final hasAfterImages = _afterTreatmentImages.isNotEmpty;
      final dentalState = _dentalDocPanelKey.currentState;
      final dentalHasWork = _documentationType == 'dental' &&
          (dentalState?.hasBillableContent ?? false);

      if (!hasNotes && !hasBeforeImages && !hasAfterImages && !dentalHasWork) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(AppLocalizations.of(context)!.noItemsToSave),
            ),
          );
          // Pop back to shell (Home/Calendar), not to waiting room
          Navigator.of(context).popUntil((route) => route.isFirst);
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
            signatureBytes = base64Decode(pdfSignatureBase64);
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
          notes: hasNotes ? combinedNotes : null,
          dentalBilling: dentalBilling,
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

        // Refresh appointments, analytics, and documents. These are best-effort
        // side effects — the appointment has already been completed on the
        // backend and the PDF uploaded, so a stale-cache refresh failure must
        // not surface as a user-facing error.
        try {
          await invalidateAppointmentRelatedProviders(ref);
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
          // Pop back to shell (Home/Calendar), not to waiting room
          Navigator.of(context).popUntil((route) => route.isFirst);
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
    } catch (e, stackTrace) {
      debugPrint('Error in _endAppointment: $e');
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
    } finally {
      if (mounted) {
        setState(() => _isSaving = false);
      }
    }
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
                      final gap = stackVertically ? 12.0 : 24.0;
                      return Flex(
                        direction:
                            stackVertically ? Axis.vertical : Axis.horizontal,
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                      // Left: video canvas + controls
                      Expanded(
                        flex: 3,
                        child: Container(
                          padding: const EdgeInsets.all(16),
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
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              // Video canvas with Daily.co
                              Expanded(
                                child: Container(
                                  decoration: BoxDecoration(
                                    color: Colors.black,
                                    borderRadius: BorderRadius.circular(12),
                                  ),
                                  child: ClipRRect(
                                    borderRadius: BorderRadius.circular(12),
                                    child: _isVideoLoading
                                        ? Center(
                                            child: Column(
                                              mainAxisAlignment:
                                                  MainAxisAlignment.center,
                                              children: [
                                                const CircularProgressIndicator(
                                                  color: Colors.white,
                                                ),
                                                const SizedBox(height: 16),
                                                Text(
                                                  AppLocalizations.of(
                                                    context,
                                                  )!.videoCallConnecting,
                                                  style: TextStyle(
                                                    color: Colors.white,
                                                    fontSize: 16,
                                                  ),
                                                ),
                                              ],
                                            ),
                                          )
                                        : _videoErrorRaw != null
                                        ? Center(
                                            child: Column(
                                              mainAxisAlignment:
                                                  MainAxisAlignment.center,
                                              children: [
                                                Icon(
                                                  Icons.error_outline,
                                                  size: 64,
                                                  color: Colors.red.shade300,
                                                ),
                                                const SizedBox(height: 16),
                                                Text(
                                                  AppLocalizations.of(
                                                    context,
                                                  )!.videoCallErrorTitle,
                                                  style: TextStyle(
                                                    color: Colors.white,
                                                    fontSize: 16,
                                                  ),
                                                ),
                                                const SizedBox(height: 8),
                                                Padding(
                                                  padding:
                                                      const EdgeInsets.symmetric(
                                                        horizontal: 16,
                                                      ),
                                                  child: Text(
                                                    _localizeDoctorVideoError(
                                                      context,
                                                      _videoErrorRaw!,
                                                    ),
                                                    style: TextStyle(
                                                      color: Colors.white70,
                                                      fontSize: 12,
                                                    ),
                                                    textAlign: TextAlign.center,
                                                  ),
                                                ),
                                                const SizedBox(height: 16),
                                                ShifaPrimaryButton(
                                                  label: AppLocalizations.of(
                                                    context,
                                                  )!.retry,
                                                  onPressed:
                                                      _initializeVideoCall,
                                                ),
                                              ],
                                            ),
                                          )
                                        : _isVideoInitialized
                                        ? Stack(
                                            children: [
                                              _buildVideoView(),
                                              // Controls - hide on web as Daily.co Prebuilt has its own controls
                                              if (!_isWebPlatform)
                                                Align(
                                                  alignment:
                                                      Alignment.bottomCenter,
                                                  child: _CallControls(
                                                    onMute: _toggleMute,
                                                    onVideo: _toggleVideo,
                                                    onScreenShare:
                                                        _toggleScreenShare,
                                                    onEndCall: _endVideoCall,
                                                    isMuted: _isMuted,
                                                    isVideoOff: _isVideoOff,
                                                    isScreenSharing:
                                                        _isScreenSharing,
                                                  ),
                                                ),
                                            ],
                                          )
                                        : Center(
                                            child: Column(
                                              mainAxisAlignment:
                                                  MainAxisAlignment.center,
                                              children: [
                                                Icon(
                                                  Icons.videocam_off,
                                                  size: 64,
                                                  color: Colors.grey.shade400,
                                                ),
                                                const SizedBox(height: 16),
                                                Text(
                                                  AppLocalizations.of(
                                                    context,
                                                  )!.videoCallNotAvailableShort,
                                                  style: TextStyle(
                                                    color: Colors.grey.shade600,
                                                    fontSize: 18,
                                                  ),
                                                ),
                                              ],
                                            ),
                                          ),
                                  ),
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),

                      SizedBox(
                        width: stackVertically ? 0 : gap,
                        height: stackVertically ? gap : 0,
                      ),

                      // Right: Documents + Notes
                      Expanded(
                        flex: 2,
                        child: Column(
                          children: [
                            // Documents
                            Expanded(
                              child: DocumentationSectionCard(
                                title: AppLocalizations.of(context)!.documents,
                                child:
                                    patientIdAsync != null &&
                                        patientIdAsync.isLoading
                                    ? const Center(
                                        child: CircularProgressIndicator(),
                                      )
                                    : patientIdAsync != null &&
                                          patientIdAsync.hasError
                                    ? Center(
                                        child: Text(
                                          '${AppLocalizations.of(context)!.errorLoadingPatientId}: ${patientIdAsync.error}',
                                        ),
                                      )
                                    : documentsAsync == null
                                    ? Center(
                                        child: Text(
                                          AppLocalizations.of(
                                            context,
                                          )!.patientIdNotAvailable,
                                        ),
                                      )
                                    : documentsAsync.when(
                                        data: (docs) => Column(
                                          crossAxisAlignment:
                                              CrossAxisAlignment.stretch,
                                          children: [
                                            Expanded(
                                              child:
                                                  GroupedPatientDocumentsList(
                                                    documents: docs,
                                                    brand: brand,
                                                    patientId: patientId,
                                                    onRequestAccess:
                                                        (doc) async {
                                                          final pid = patientId;
                                                          if (pid == null ||
                                                              pid.isEmpty) {
                                                            return;
                                                          }
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
                                                              PatientDocumentsKey(patientId: pid),
                                                            ),
                                                          );
                                                        },
                                                  ),
                                            ),
                                            ConsultationDocumentUploadStrip(
                                              patientId: patientId,
                                              brand: brand,
                                              enabled: consultationUploadEnabled,
                                            ),
                                            const SizedBox(height: 10),
                                            _documentsFinalizeHintRow(
                                              context,
                                            ),
                                          ],
                                        ),
                                        loading: () => const Center(
                                          child: CircularProgressIndicator(),
                                        ),
                                        error: (err, _) => Center(
                                          child: Text(
                                            '${AppLocalizations.of(context)!.error}: $err',
                                          ),
                                        ),
                                      ),
                              ),
                            ),
                            const SizedBox(height: 24),
                            // Documentation (general notes or 025-2 form)
                            Expanded(
                              child: _notesPanelFullScreen
                                  ? const SizedBox.shrink()
                                  : DocumentationSectionCard(
                                      title: _documentationType == 'general'
                                          ? AppLocalizations.of(context)!.notes
                                          : _documentationType == 'dental'
                                              ? AppLocalizations.of(
                                                  context,
                                                )!.docModeDental
                                              : AppLocalizations.of(
                                                  context,
                                                )!.docMode0252,
                                      titleTrailing: Row(
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          IconButton(
                                            icon: const Icon(Icons.fullscreen),
                                            onPressed: () => setState(
                                              () =>
                                                  _notesPanelFullScreen = true,
                                            ),
                                            tooltip: AppLocalizations.of(
                                              context,
                                            )!.expand,
                                          ),
                                          PopupMenuButton<String>(
                                            icon: const Icon(Icons.more_vert),
                                            tooltip: AppLocalizations.of(
                                              context,
                                            )!.notes,
                                            onSelected: (value) async {
                                              final l10n = AppLocalizations.of(
                                                context,
                                              )!;

                                              // Show AI notes helper
                                              if (value == 'show_ai') {
                                                Future.microtask(() {
                                                  if (!mounted) return;
                                                  setState(() {
                                                    _notesSectionsExpanded =
                                                        true;
                                                    _expandedNoteSource = 'ai';
                                                  });
                                                });
                                                final id =
                                                    widget.appointment.id;
                                                ref.invalidate(
                                                  draftNotesForAppointmentProvider(
                                                    id,
                                                  ),
                                                );
                                                ref.invalidate(
                                                  consultationNotesForAppointmentProvider(
                                                    id,
                                                  ),
                                                );
                                                final drafts = await ref.read(
                                                  draftNotesForAppointmentProvider(
                                                    id,
                                                  ).future,
                                                );
                                                final notes = await ref.read(
                                                  consultationNotesForAppointmentProvider(
                                                    id,
                                                  ).future,
                                                );
                                                if (mounted &&
                                                    drafts.isEmpty &&
                                                    notes.isEmpty) {
                                                  ScaffoldMessenger.of(
                                                    context,
                                                  ).showSnackBar(
                                                    SnackBar(
                                                      content: Text(
                                                        l10n.aiNotesNotReadyTryLater,
                                                      ),
                                                    ),
                                                  );
                                                  // Collapse helper section so user can type in general notes
                                                  Future.microtask(() {
                                                    if (!mounted) return;
                                                    setState(() {
                                                      _notesSectionsExpanded =
                                                          false;
                                                      _expandedNoteSource =
                                                          null;
                                                    });
                                                  });
                                                }
                                                return;
                                              }

                                              // Show 025-2 form helper
                                              if (value == 'show_0252') {
                                                Future.microtask(() {
                                                  if (!mounted) return;
                                                  setState(() {
                                                    _notesSectionsExpanded =
                                                        true;
                                                    _expandedNoteSource =
                                                        '0252';
                                                  });
                                                });
                                                final pid = patientId;
                                                if (pid != null &&
                                                    pid.isNotEmpty) {
                                                  ref.invalidate(
                                                    patientFormsProvider(pid),
                                                  );
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

                                              // Switch documentation type (defer entire logic to avoid build errors)
                                              if (value == 'switch_general' ||
                                                  value == 'switch_0252' ||
                                                  value == 'switch_dental') {
                                                final newType =
                                                    value == 'switch_general'
                                                    ? 'general'
                                                    : value == 'switch_dental'
                                                    ? 'dental'
                                                    : '025-2';
                                                Future.microtask(() async {
                                                  if (!mounted) return;
                                                  if (_documentationType ==
                                                      newType)
                                                    return;

                                                  if (_hasUnsavedChanges) {
                                                    final result = await showDialog<String>(
                                                      context: context,
                                                      barrierDismissible: false,
                                                      builder: (ctx) => AlertDialog(
                                                        title: Text(
                                                          l10n.unsavedChangesSwitch,
                                                        ),
                                                        actions: [
                                                          TextButton(
                                                            onPressed: () =>
                                                                Navigator.pop(
                                                                  ctx,
                                                                  'cancel',
                                                                ),
                                                            child: Text(
                                                              l10n.cancel,
                                                            ),
                                                          ),
                                                          TextButton(
                                                            onPressed: () =>
                                                                Navigator.pop(
                                                                  ctx,
                                                                  'discard',
                                                                ),
                                                            child: Text(
                                                              l10n.discardAndSwitch,
                                                            ),
                                                          ),
                                                          ShifaPrimaryButton(
                                                            label: l10n.saveAndSwitch,
                                                            onPressed: () =>
                                                                Navigator.pop(
                                                                  ctx,
                                                                  'save',
                                                                ),
                                                          ),
                                                        ],
                                                      ),
                                                    );

                                                    if (result == null ||
                                                        result == 'cancel')
                                                      return;

                                                    if (result == 'save') {
                                                      if (_documentationType ==
                                                          '025-2') {
                                                        final ok =
                                                            await _form0252PanelKey
                                                                .currentState
                                                                ?.requestSave() ??
                                                            false;
                                                        if (!ok || !mounted)
                                                          return;
                                                      } else if (_documentationType ==
                                                          'dental') {
                                                        final ok =
                                                            await _dentalDocPanelKey
                                                                .currentState
                                                                ?.requestSave() ??
                                                            false;
                                                        if (!ok || !mounted)
                                                          return;
                                                      }
                                                      Future.microtask(() {
                                                        if (!mounted) return;
                                                        setState(() {
                                                          _userSelectedDocumentationType =
                                                              true;
                                                          _hasUnsavedChanges =
                                                              false;
                                                          _documentationType =
                                                              newType;
                                                        });
                                                      });
                                                      return;
                                                    }

                                                    if (result == 'discard') {
                                                      _clearGeneralNoteFields();
                                                      Future.microtask(() {
                                                        if (!mounted) return;
                                                        setState(() {
                                                          _userSelectedDocumentationType =
                                                              true;
                                                          _beforeTreatmentImages
                                                              .clear();
                                                          _afterTreatmentImages
                                                              .clear();
                                                          _hasUnsavedChanges =
                                                              false;
                                                          _documentationType =
                                                              newType;
                                                        });
                                                      });
                                                    }
                                                  } else {
                                                    setState(() {
                                                      _userSelectedDocumentationType =
                                                          true;
                                                      _documentationType =
                                                          newType;
                                                    });
                                                  }
                                                }); // Close Future.microtask for entire switch
                                              }
                                            },
                                            itemBuilder: (context) {
                                              final l10n = AppLocalizations.of(
                                                context,
                                              )!;
                                              return [
                                                // Show helpers (general + dental modes)
                                                if (_showDocumentationNoteHelpers) ...[
                                                  PopupMenuItem(
                                                    value: 'show_ai',
                                                    child: Row(
                                                      children: [
                                                        Icon(
                                                          Icons.auto_awesome,
                                                          size: 18,
                                                          color: Colors.purple,
                                                        ),
                                                        SizedBox(width: 12),
                                                        Text(l10n.fromShifaAi),
                                                      ],
                                                    ),
                                                  ),
                                                  PopupMenuItem(
                                                    value: 'show_0252',
                                                    child: Row(
                                                      children: [
                                                        Icon(
                                                          Icons.description,
                                                          size: 18,
                                                          color: Colors.blue,
                                                        ),
                                                        SizedBox(width: 12),
                                                        Text(
                                                          l10n.fromLast0252Form,
                                                        ),
                                                      ],
                                                    ),
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
                                                        SizedBox(width: 12),
                                                        Text(l10n.beforeTreatment),
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
                                                        SizedBox(width: 12),
                                                        Text(l10n.afterTreatment),
                                                      ],
                                                    ),
                                                  ),
                                                  PopupMenuDivider(),
                                                ],

                                                // Switch documentation type
                                                if (_documentationType !=
                                                    'general')
                                                  PopupMenuItem(
                                                    value: 'switch_general',
                                                    child: Row(
                                                      children: [
                                                        Icon(
                                                          Icons.notes,
                                                          size: 18,
                                                        ),
                                                        SizedBox(width: 12),
                                                        Text(
                                                          l10n.docModeGeneral,
                                                        ),
                                                      ],
                                                    ),
                                                  ),
                                                if (showDentalDoc &&
                                                    _documentationType !=
                                                        'dental')
                                                  PopupMenuItem(
                                                    value: 'switch_dental',
                                                    child: Row(
                                                      children: [
                                                        Icon(
                                                          Icons.medical_services_outlined,
                                                          size: 18,
                                                        ),
                                                        SizedBox(width: 12),
                                                        Text(l10n.docModeDental),
                                                      ],
                                                    ),
                                                  ),
                                                if (_documentationType !=
                                                    '025-2')
                                                  PopupMenuItem(
                                                    value: 'switch_0252',
                                                    child: Row(
                                                      children: [
                                                        Icon(
                                                          Icons.assignment,
                                                          size: 18,
                                                        ),
                                                        SizedBox(width: 12),
                                                        Text(l10n.docMode0252),
                                                      ],
                                                    ),
                                                  ),
                                              ];
                                            },
                                          ),
                                        ],
                                      ), // titleTrailing Row
                                      child: _buildNotesPanelChild(
                                        brand,
                                        patientId,
                                      ),
                                    ),
                            ),
                          ],
                        ),
                      ),
                        ],
                      );
                    },
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
          if (_notesPanelFullScreen)
            Positioned.fill(
              child: _buildNotesFullScreenOverlay(brand, patientId),
            ),
        ],
      ),
    );
  }

  Widget _buildNotesFullScreenOverlay(Color brand, String? patientId) {
    final l10n = AppLocalizations.of(context)!;
    final profession =
        ref.watch(profileAllProvider).valueOrNull?.profile['profession']
            as String?;
    final showDentalDoc = isDentalDocumentationProfession(profession);
    final notesTitle = _documentationType == 'general'
        ? l10n.notes
        : _documentationType == 'dental'
        ? l10n.docModeDental
        : l10n.docMode0252;
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          borderRadius: BorderRadius.circular(16),
          boxShadow: [
            BoxShadow(
              color: Colors.black.withOpacity(0.2),
              blurRadius: 12,
              spreadRadius: 2,
            ),
          ],
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 8, 12),
              child: Row(
                children: [
                  Text(
                    notesTitle,
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const Spacer(),
                  PopupMenuButton<String>(
                    icon: const Icon(Icons.more_vert),
                    tooltip: AppLocalizations.of(context)!.notes,
                    onSelected: (value) async {
                      final l10n = AppLocalizations.of(context)!;

                      // Show AI notes helper
                      if (value == 'show_ai') {
                        Future.microtask(() {
                          if (!mounted) return;
                          setState(() {
                            _notesSectionsExpanded = true;
                            _expandedNoteSource = 'ai';
                          });
                        });
                        final id = widget.appointment.id;
                        ref.invalidate(draftNotesForAppointmentProvider(id));
                        ref.invalidate(
                          consultationNotesForAppointmentProvider(id),
                        );
                        final drafts = await ref.read(
                          draftNotesForAppointmentProvider(id).future,
                        );
                        final notes = await ref.read(
                          consultationNotesForAppointmentProvider(id).future,
                        );
                        if (mounted && drafts.isEmpty && notes.isEmpty) {
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(l10n.aiNotesNotReadyTryLater),
                            ),
                          );
                          Future.microtask(() {
                            if (!mounted) return;
                            setState(() {
                              _notesSectionsExpanded = false;
                              _expandedNoteSource = null;
                            });
                          });
                        }
                        return;
                      }

                      // Show 025-2 form helper
                      if (value == 'show_0252') {
                        Future.microtask(() {
                          if (!mounted) return;
                          setState(() {
                            _notesSectionsExpanded = true;
                            _expandedNoteSource = '0252';
                          });
                        });
                        final pid = patientId;
                        if (pid != null && pid.isNotEmpty) {
                          ref.invalidate(patientFormsProvider(pid));
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

                      // Switch documentation type
                      if (value == 'switch_general' ||
                          value == 'switch_0252' ||
                          value == 'switch_dental') {
                        final newType = value == 'switch_general'
                            ? 'general'
                            : value == 'switch_dental'
                            ? 'dental'
                            : '025-2';
                        Future.microtask(() async {
                          if (!mounted) return;
                          if (_documentationType == newType) return;

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
                                    onPressed: () => Navigator.pop(ctx, 'save'),
                                  ),
                                ],
                              ),
                            );

                            if (result == null || result == 'cancel') return;

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
                                _userSelectedDocumentationType = true;
                                _hasUnsavedChanges = false;
                                _documentationType = newType;
                              });
                              return;
                            }

                            if (result == 'discard') {
                              _clearGeneralNoteFields();
                              setState(() {
                                _userSelectedDocumentationType = true;
                                _beforeTreatmentImages.clear();
                                _afterTreatmentImages.clear();
                                _hasUnsavedChanges = false;
                                _documentationType = newType;
                              });
                            }
                          } else {
                            setState(() {
                              _userSelectedDocumentationType = true;
                              _documentationType = newType;
                            });
                          }
                        });
                      }
                    },
                    itemBuilder: (context) {
                      final l10n = AppLocalizations.of(context)!;
                      return [
                        // Show helpers (general + dental modes)
                        if (_showDocumentationNoteHelpers) ...[
                          PopupMenuItem(
                            value: 'show_ai',
                            child: Row(
                              children: [
                                Icon(
                                  Icons.auto_awesome,
                                  size: 18,
                                  color: Colors.purple,
                                ),
                                SizedBox(width: 12),
                                Text(l10n.fromShifaAi),
                              ],
                            ),
                          ),
                          PopupMenuItem(
                            value: 'show_0252',
                            child: Row(
                              children: [
                                Icon(
                                  Icons.description,
                                  size: 18,
                                  color: Colors.blue,
                                ),
                                SizedBox(width: 12),
                                Text(l10n.fromLast0252Form),
                              ],
                            ),
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
                                SizedBox(width: 12),
                                Text(l10n.beforeTreatment),
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
                                SizedBox(width: 12),
                                Text(l10n.afterTreatment),
                              ],
                            ),
                          ),
                          PopupMenuDivider(),
                        ],

                        // Switch documentation type
                        if (_documentationType != 'general')
                          PopupMenuItem(
                            value: 'switch_general',
                            child: Row(
                              children: [
                                Icon(Icons.notes, size: 18),
                                SizedBox(width: 12),
                                Text(l10n.docModeGeneral),
                              ],
                            ),
                          ),
                        if (showDentalDoc && _documentationType != 'dental')
                          PopupMenuItem(
                            value: 'switch_dental',
                            child: Row(
                              children: [
                                Icon(
                                  Icons.medical_services_outlined,
                                  size: 18,
                                ),
                                SizedBox(width: 12),
                                Text(l10n.docModeDental),
                              ],
                            ),
                          ),
                        if (_documentationType != '025-2')
                          PopupMenuItem(
                            value: 'switch_0252',
                            child: Row(
                              children: [
                                Icon(Icons.assignment, size: 18),
                                SizedBox(width: 12),
                                Text(l10n.docMode0252),
                              ],
                            ),
                          ),
                      ];
                    },
                  ),
                  IconButton(
                    icon: const Icon(Icons.fullscreen_exit),
                    onPressed: () =>
                        setState(() => _notesPanelFullScreen = false),
                    tooltip: l10n.collapse,
                  ),
                ],
              ),
            ),
            const SizedBox(height: 8),
            Expanded(
              child: Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                child: _buildNotesPanelChild(brand, patientId),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildNotesPanelChild(Color brand, String? patientId) {
    if (_showDocumentationNoteHelpers) {
      final consultationNotesAsync = ref.watch(
        consultationNotesForAppointmentProvider(widget.appointment.id),
      );
      final draftNotesAsync = ref.watch(
        draftNotesForAppointmentProvider(widget.appointment.id),
      );
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (_notesSectionsExpanded) ...[
            // Show AI notes ONLY if 'ai' source selected
            if (_expandedNoteSource == 'ai') ...[
              // Pending AI drafts (e.g. AI Scribe) – Confirm to save as consultation note, or Discard
              draftNotesAsync.when(
                data: (drafts) {
                  if (drafts.isEmpty) return const SizedBox.shrink();
                  final l10n = AppLocalizations.of(context)!;
                  final appointmentIdInt = int.tryParse(widget.appointment.id);
                  final patientIdInt = patientId != null && patientId.isNotEmpty
                      ? int.tryParse(patientId)
                      : null;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 260),
                      child: ListView.builder(
                        shrinkWrap: true,
                        itemCount: drafts.length,
                        itemBuilder: (context, i) {
                          final d = drafts[i];
                          return Container(
                            margin: const EdgeInsets.only(bottom: 8),
                            padding: const EdgeInsets.all(10),
                            decoration: BoxDecoration(
                              color: Colors.orange.shade50,
                              borderRadius: BorderRadius.circular(10),
                              border: Border.all(color: Colors.orange.shade200),
                            ),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Row(
                                  children: [
                                    Icon(
                                      Icons.edit_note,
                                      size: 16,
                                      color: Colors.orange.shade800,
                                    ),
                                    const SizedBox(width: 6),
                                    Expanded(
                                      child: Text(
                                        d.aiLabel,
                                        style: TextStyle(
                                          fontWeight: FontWeight.w600,
                                          fontSize: 12,
                                          color: Colors.orange.shade900,
                                        ),
                                      ),
                                    ),
                                    Text(
                                      l10n.draft,
                                      style: TextStyle(
                                        fontSize: 11,
                                        color: Colors.orange.shade700,
                                      ),
                                    ),
                                    const Spacer(),
                                    IconButton(
                                      icon: const Icon(Icons.close, size: 18),
                                      tooltip: l10n.close,
                                      onPressed: () {
                                        setState(() {
                                          _expandedNoteSource = null;
                                          _notesSectionsExpanded = false;
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
                                      color: Colors.grey.shade800,
                                    ),
                                  ),
                                ),
                                if (d.icdSuggestions.isNotEmpty) ...[
                                  const SizedBox(height: 10),
                                  Text(
                                    'Suggested Diagnoses (AI):',
                                    style: TextStyle(
                                      fontSize: 12,
                                      fontWeight: FontWeight.w700,
                                      color: Colors.orange.shade900,
                                    ),
                                  ),
                                  const SizedBox(height: 6),
                                  ...d.icdSuggestions.map((s) {
                                    return Padding(
                                      padding: const EdgeInsets.only(bottom: 6),
                                      child: Row(
                                        children: [
                                          Expanded(
                                            child: Text(
                                              '${s.code} — ${s.title}',
                                              style: TextStyle(
                                                fontSize: 12,
                                                color: Colors.grey.shade800,
                                              ),
                                              maxLines: 2,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                          ),
                                          if (s.isTop) ...[
                                            const SizedBox(width: 8),
                                            Container(
                                              padding:
                                                  const EdgeInsets.symmetric(
                                                    horizontal: 8,
                                                    vertical: 3,
                                                  ),
                                              decoration: BoxDecoration(
                                                color: Colors.green.shade50,
                                                borderRadius:
                                                    BorderRadius.circular(999),
                                                border: Border.all(
                                                  color: Colors.green.shade200,
                                                ),
                                              ),
                                              child: Text(
                                                'Recommended',
                                                style: TextStyle(
                                                  fontSize: 11,
                                                  fontWeight: FontWeight.w700,
                                                  color: Colors.green.shade800,
                                                ),
                                              ),
                                            ),
                                          ],
                                          const SizedBox(width: 8),
                                          TextButton(
                                            onPressed: () {
                                              if (_documentationType !=
                                                  '025-2') {
                                                setState(() {
                                                  _userSelectedDocumentationType =
                                                      true;
                                                  _documentationType =
                                                      '025-2';
                                                  _notesSectionsExpanded =
                                                      true;
                                                });
                                              }
                                              WidgetsBinding.instance
                                                  .addPostFrameCallback((_) {
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
                                            child: const Text('Apply'),
                                          ),
                                        ],
                                      ),
                                    );
                                  }),
                                ],
                                const SizedBox(height: 8),
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.end,
                                  children: [
                                    TextButton(
                                      onPressed: () async {
                                        try {
                                          final aiApi = ref.read(aiApiProvider);
                                          await aiApi.discardDraft(d.id);
                                          if (context.mounted) {
                                            ref.invalidate(
                                              draftNotesForAppointmentProvider(
                                                widget.appointment.id,
                                              ),
                                            );
                                            ref.invalidate(
                                              consultationNotesForAppointmentProvider(
                                                widget.appointment.id,
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
                                          if (context.mounted) {
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
                                      child: Text(l10n.discard),
                                    ),
                                    const SizedBox(width: 8),
                                    ShifaPrimaryButton(
                                      label: l10n.translate(
                                            'saveAsConsultationNote',
                                          ) ??
                                          'Save as note',
                                      icon: Icons.check,
                                      onPressed: () async {
                                        try {
                                          final aiApi = ref.read(aiApiProvider);
                                          await aiApi.confirmDraft(
                                            d.id,
                                            patientId: patientIdInt,
                                            appointmentId: appointmentIdInt,
                                          );
                                          if (context.mounted) {
                                            ref.invalidate(
                                              draftNotesForAppointmentProvider(
                                                widget.appointment.id,
                                              ),
                                            );
                                            ref.invalidate(
                                              consultationNotesForAppointmentProvider(
                                                widget.appointment.id,
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
                                          if (context.mounted) {
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
              // Saved AI outputs – browse with < >, add to notes with +
              consultationNotesAsync.when(
                data: (notes) {
                  if (notes.isEmpty) return const SizedBox.shrink();
                  final l10n = AppLocalizations.of(context)!;
                  final currentIndex = _consultationNoteIndex.clamp(
                    0,
                    notes.length - 1,
                  );
                  final n = notes[currentIndex];
                  final hasMultiple = notes.length > 1;
                  return Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(maxHeight: 220),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          if (hasMultiple)
                            IconButton(
                              icon: const Icon(Icons.chevron_left),
                              onPressed: currentIndex > 0
                                  ? () => setState(
                                      () => _consultationNoteIndex =
                                          currentIndex - 1,
                                    )
                                  : null,
                              tooltip: l10n.translate('previous') ?? 'Previous',
                              style: IconButton.styleFrom(
                                foregroundColor: brand,
                              ),
                            ),
                          Expanded(
                            child: Container(
                              padding: const EdgeInsets.all(10),
                              decoration: BoxDecoration(
                                color: brand.withOpacity(0.06),
                                borderRadius: BorderRadius.circular(10),
                                border: Border.all(
                                  color: brand.withOpacity(0.3),
                                ),
                              ),
                              child: SingleChildScrollView(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  mainAxisSize: MainAxisSize.min,
                                  children: [
                                    Row(
                                      children: [
                                        Icon(
                                          Icons.auto_awesome,
                                          size: 16,
                                          color: brand,
                                        ),
                                        const SizedBox(width: 6),
                                        Text(
                                          l10n.fromShifaAi,
                                          style: TextStyle(
                                            fontWeight: FontWeight.w600,
                                            fontSize: 12,
                                            color: brand,
                                          ),
                                        ),
                                        if (hasMultiple) ...[
                                          const Spacer(),
                                          Text(
                                            '${currentIndex + 1} / ${notes.length}',
                                            style: TextStyle(
                                              fontSize: 12,
                                              color: Colors.grey.shade600,
                                            ),
                                          ),
                                        ],
                                      ],
                                    ),
                                    const SizedBox(height: 6),
                                    Text(
                                      n.displayText,
                                      style: TextStyle(
                                        fontSize: 13,
                                        color: Colors.grey.shade800,
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                            ),
                          ),
                          if (hasMultiple)
                            IconButton(
                              icon: const Icon(Icons.chevron_right),
                              onPressed: currentIndex < notes.length - 1
                                  ? () => setState(
                                      () => _consultationNoteIndex =
                                          currentIndex + 1,
                                    )
                                  : null,
                              tooltip: l10n.translate('next') ?? 'Next',
                              style: IconButton.styleFrom(
                                foregroundColor: brand,
                              ),
                            ),
                          IconButton.filled(
                            icon: const Icon(Icons.add, size: 18),
                            onPressed: () {
                              final toAdd = n.displayText;
                              if (toAdd.trim().isEmpty) return;
                              _appendToActiveDocumentationNotes(toAdd);
                              setState(() => _notesSectionsExpanded = false);
                              if (mounted) {
                                ScaffoldMessenger.of(context).showSnackBar(
                                  SnackBar(
                                    content: Text(
                                      l10n.translate('addedToNotes') ??
                                          'Added to notes',
                                    ),
                                    duration: const Duration(seconds: 2),
                                  ),
                                );
                              }
                            },
                            tooltip:
                                l10n.translate('addToNotes') ?? 'Add to notes',
                            style: IconButton.styleFrom(
                              backgroundColor: brand,
                              foregroundColor: Colors.white,
                              padding: const EdgeInsets.all(8),
                              minimumSize: const Size(32, 32),
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
              // From last 025-2 form: Shikoyati, Tashxis, Davolanish – add to notes with +
            ],
            // Show 025-2 form ONLY if '0252' source selected
            if (_expandedNoteSource == '0252')
              if (patientId != null && patientId.isNotEmpty)
                ref
                    .watch(last0252FormForPatientProvider(patientId!))
                    .when(
                      data: (form) {
                        if (form == null) return const SizedBox.shrink();
                        final c = form.complaints?.trim() ?? '';
                        final d = form.diagnosis?.trim() ?? '';
                        final t = form.treatment?.trim() ?? '';
                        if (c.isEmpty && d.isEmpty && t.isEmpty)
                          return const SizedBox.shrink();
                        final l10n = AppLocalizations.of(context)!;
                        final parts = <String>[];
                        if (c.isNotEmpty) parts.add('${l10n.complaints}: $c');
                        if (d.isNotEmpty) parts.add('${l10n.diagnosis}: $d');
                        if (t.isNotEmpty) parts.add('${l10n.treatment}: $t');
                        final toAdd = parts.join('\n\n');
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 8),
                          child: ConstrainedBox(
                            constraints: const BoxConstraints(maxHeight: 220),
                            child: Row(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                Expanded(
                                  child: Container(
                                    padding: const EdgeInsets.all(10),
                                    decoration: BoxDecoration(
                                      color: brand.withOpacity(0.06),
                                      borderRadius: BorderRadius.circular(10),
                                      border: Border.all(
                                        color: brand.withOpacity(0.3),
                                      ),
                                    ),
                                    child: SingleChildScrollView(
                                      child: Column(
                                        crossAxisAlignment:
                                            CrossAxisAlignment.start,
                                        mainAxisSize: MainAxisSize.min,
                                        children: [
                                          Row(
                                            children: [
                                              Icon(
                                                Icons.description,
                                                size: 16,
                                                color: brand,
                                              ),
                                              const SizedBox(width: 6),
                                              Text(
                                                l10n.fromLast0252Form,
                                                style: TextStyle(
                                                  fontWeight: FontWeight.w600,
                                                  fontSize: 12,
                                                  color: brand,
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
                                                    _expandedNoteSource = null;
                                                    _notesSectionsExpanded =
                                                        false;
                                                  });
                                                },
                                              ),
                                            ],
                                          ),
                                          const SizedBox(height: 6),
                                          if (c.isNotEmpty)
                                            Text(
                                              '${l10n.complaints}: $c',
                                              style: TextStyle(
                                                fontSize: 13,
                                                color: Colors.grey.shade800,
                                              ),
                                            ),
                                          if (c.isNotEmpty)
                                            const SizedBox(height: 4),
                                          if (d.isNotEmpty)
                                            Text(
                                              '${l10n.diagnosis}: $d',
                                              style: TextStyle(
                                                fontSize: 13,
                                                color: Colors.grey.shade800,
                                              ),
                                            ),
                                          if (d.isNotEmpty)
                                            const SizedBox(height: 4),
                                          if (t.isNotEmpty)
                                            Text(
                                              '${l10n.treatment}: $t',
                                              style: TextStyle(
                                                fontSize: 13,
                                                color: Colors.grey.shade800,
                                              ),
                                            ),
                                        ],
                                      ),
                                    ),
                                  ),
                                ),
                                IconButton.filled(
                                  icon: const Icon(Icons.add, size: 18),
                                  onPressed: () {
                                    if (toAdd.trim().isEmpty) return;
                                    _appendToActiveDocumentationNotes(toAdd);
                                    setState(
                                      () => _notesSectionsExpanded = false,
                                    );
                                    if (mounted) {
                                      ScaffoldMessenger.of(
                                        context,
                                      ).showSnackBar(
                                        SnackBar(
                                          content: Text(
                                            l10n.translate('addedToNotes') ??
                                                'Added to notes',
                                          ),
                                          duration: const Duration(seconds: 2),
                                        ),
                                      );
                                    }
                                  },
                                  tooltip:
                                      l10n.translate('addToNotes') ??
                                      'Add to notes',
                                  style: IconButton.styleFrom(
                                    backgroundColor: brand,
                                    foregroundColor: Colors.white,
                                    padding: const EdgeInsets.all(8),
                                    minimumSize: const Size(32, 32),
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
            Expanded(
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: const Color(0xFFF9FAFB),
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(
                    color: Colors.black.withValues(alpha: 0.07),
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.black.withValues(alpha: 0.03),
                      blurRadius: 12,
                      offset: const Offset(0, 2),
                    ),
                  ],
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
                    hintText: AppLocalizations.of(context)!.enterNotes,
                    border: InputBorder.none,
                    contentPadding: const EdgeInsets.fromLTRB(4, 8, 44, 8),
                  ),
                ),
              ),
            ),
          ] else
            Expanded(
              child: DentalVisitDocumentationPanel(
                key: _dentalDocPanelKey,
                appointmentId: widget.appointment.id,
                brand: brand,
                onUnsavedChanged: (v) => setState(() => _hasUnsavedChanges = v),
              ),
            ),
          if (_beforeTreatmentImages.isNotEmpty ||
              _afterTreatmentImages.isNotEmpty) ...[
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ..._beforeTreatmentImages.asMap().entries.map((entry) {
                  final index = entry.key;
                  return Chip(
                    label: Text(
                      '${AppLocalizations.of(context)!.beforeTreatment} ${index + 1}',
                    ),
                    deleteIcon: const Icon(Icons.close, size: 18),
                    onDeleted: () {
                      setState(() {
                        _beforeTreatmentImages.removeAt(index);
                        _hasUnsavedChanges = true;
                      });
                    },
                    backgroundColor: Colors.blue.shade50,
                  );
                }),
                ..._afterTreatmentImages.asMap().entries.map((entry) {
                  final index = entry.key;
                  return Chip(
                    label: Text(
                      '${AppLocalizations.of(context)!.afterTreatment} ${index + 1}',
                    ),
                    deleteIcon: const Icon(Icons.close, size: 18),
                    onDeleted: () {
                      setState(() {
                        _afterTreatmentImages.removeAt(index);
                        _hasUnsavedChanges = true;
                      });
                    },
                    backgroundColor: Colors.green.shade50,
                  );
                }),
              ],
            ),
          ],
        ],
      );
    }
    if (patientId == null || patientId.isEmpty) {
      return Center(
        child: Text(AppLocalizations.of(context)!.patientIdNotAvailable),
      );
    }
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
}

// --- controls and helpers ---
class _CallControls extends StatelessWidget {
  final VoidCallback? onMute;
  final VoidCallback? onVideo;
  final VoidCallback? onScreenShare;
  final VoidCallback? onEndCall;
  final bool isMuted;
  final bool isVideoOff;
  final bool isScreenSharing;

  const _CallControls({
    this.onMute,
    this.onVideo,
    this.onScreenShare,
    this.onEndCall,
    this.isMuted = false,
    this.isVideoOff = false,
    this.isScreenSharing = false,
  });

  @override
  Widget build(BuildContext context) {
    final bg = Colors.black.withOpacity(0.35);
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: BorderRadius.circular(24),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          _RoundBtn(
            icon: isVideoOff ? Icons.videocam_off : Icons.videocam,
            onPressed: onVideo,
            color: isVideoOff ? Colors.red : Colors.white,
          ),
          const SizedBox(width: 8),
          _RoundBtn(
            icon: isMuted ? Icons.mic_off : Icons.mic,
            onPressed: onMute,
            color: isMuted ? Colors.red : Colors.white,
          ),
          const SizedBox(width: 8),
          _RoundBtn(
            icon: Icons.screen_share,
            onPressed: onScreenShare,
            color: isScreenSharing ? Colors.green : Colors.white,
          ),
          const SizedBox(width: 8),
          _RoundBtn(
            icon: Icons.call_end,
            color: const Color(0xFFE75656),
            onPressed: onEndCall,
          ),
        ],
      ),
    );
  }
}

class _RoundBtn extends StatelessWidget {
  const _RoundBtn({required this.icon, this.color, this.onPressed});

  final IconData icon;
  final Color? color;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTap: onPressed,
      child: CircleAvatar(
        radius: 22,
        backgroundColor: Colors.white,
        child: Icon(icon, color: color ?? Colors.black87),
      ),
    );
  }
}
