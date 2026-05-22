import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:shifa_doc_app_v1/app/router.dart';
import 'package:shifa_doc_app_v1/features/shell/presentation/shell_scope.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/widgets/person_avatar.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/today_appointments_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:shifa_doc_app_v1/state/patients/patients_provider.dart' show patientByIdProvider, patientsProvider;
import 'package:shifa_doc_app_v1/core/utils/patient_warning_utils.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/localization/uzbek_latin_to_cyrillic.dart';
import 'package:shifa_doc_app_v1/core/api/ai_message.dart';
import 'package:shifa_doc_app_v1/core/widgets/ai_response_text.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/core/providers/language_provider.dart';

import 'package:shifa_doc_app_v1/state/patient_briefing_provider.dart';
import 'package:shifa_doc_app_v1/state/patient_briefing_context_provider.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/analytics_kpi_cards.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/analytics_engagement_widget.dart';
import 'package:shifa_doc_app_v1/state/profile/profile_providers.dart';
import 'package:shifa_doc_app_v1/core/utils/timezone_utils.dart';
import 'package:shifa_doc_app_v1/core/subscription/doctor_subscription.dart';
import 'package:shifa_doc_app_v1/state/subscription/doctor_subscription_provider.dart';
import '../../../core/widgets/appointments_trend_chart.dart';
import '../../../core/widgets/visit_type_donut_chart.dart';

// ============================================================================
// APPOINTMENT ITEM (must be BEFORE HomeScreen)
// ============================================================================

class _AppointmentItem extends ConsumerWidget {
  final Appointment appointment;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onStart;

  const _AppointmentItem({
    required this.appointment,
    required this.selected,
    required this.onTap,
    required this.onStart,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final brand = Theme.of(context).colorScheme.primary;
    final l10n = AppLocalizations.of(context)!;
    final isCompleted = appointment.isCompleted;

    // Fetch patient data to check for chronic disease
    final patientAsync = appointment.patientId != null
        ? ref.watch(patientByIdProvider(appointment.patientId!))
        : null;

    final hasChronicDisease = patientAsync?.when(
      data: (patient) => patient?.general.chronicDisease != null &&
          patient!.general.chronicDisease!.isNotEmpty &&
          patient.general.chronicDisease != 'None',
      loading: () => false,
      error: (_, __) => false,
    ) ?? false;

    // CRITICAL: Use doctor's practice timezone for all time calculations
    // to match how appointment times are displayed. appointment.start is already
    // converted to doctor's timezone in todayAppointmentsProvider.
    final doctorTimeZone = ref.read(profileAllProvider).valueOrNull?.profile['timeZone'] as String?;

    // DEBUG: Log timezone and appointment time
    debugPrint('=== HOME SCREEN APPOINTMENT ===');
    debugPrint('Doctor timezone: $doctorTimeZone');
    debugPrint('Appointment start: ${appointment.start.hour}:${appointment.start.minute}');
    debugPrint('Patient: ${appointment.patientName}');

    // Get current time in doctor's timezone (not device local time)
    final nowInDoctorZone = getNowInTimezone(doctorTimeZone);

    // Convert appointment TimeOfDay to full DateTime in doctor's timezone for today
    final appointmentDateTime = timeOfDayToDateTimeInZone(
      appointment.start,
      nowInDoctorZone,
      doctorTimeZone,
    );

    // Calculate time until appointment in doctor's timezone
    final timeUntilAppointment = appointmentDateTime.difference(nowInDoctorZone);
    final isUrgent = !isCompleted && timeUntilAppointment.inMinutes >= 0 &&
                     timeUntilAppointment.inMinutes <= 30;
    final isNext = !isCompleted && timeUntilAppointment.inMinutes >= 0 &&
                   timeUntilAppointment.inMinutes <= 20;

    final appointmentEndDateTime = timeOfDayToDateTimeInZone(
      appointment.end,
      nowInDoctorZone,
      doctorTimeZone,
    );
    final videoColdJoinCutoff =
        appointmentEndDateTime.add(const Duration(hours: 1));
    final isVideoPastColdJoinGrace = appointment.isVideo &&
        !appointment.isInProgress &&
        !nowInDoctorZone.isBefore(videoColdJoinCutoff);

    // Video call: Start button only clickable from 5 minutes before appointment onwards,
    // and not more than 1 hour after scheduled end unless already in progress (doctor reopened).
    final joinAllowedFrom = appointmentDateTime.subtract(const Duration(minutes: 5));
    final canStartVideo = !appointment.isVideo ||
        ((nowInDoctorZone.isAfter(joinAllowedFrom) ||
                nowInDoctorZone.isAtSameMomentAs(joinAllowedFrom)) &&
            !isVideoPastColdJoinGrace);
    final startButtonEnabled = !isCompleted && canStartVideo;

    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Opacity(
        opacity: isCompleted ? 0.6 : 1.0,
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 8),
          decoration: BoxDecoration(
            color: selected ? brand.withOpacity(0.06) : Colors.white,
            borderRadius: BorderRadius.circular(12),
            border: Border.all(
              color: selected 
                  ? brand 
                  : isCompleted
                      ? Colors.grey.shade400
                      : isUrgent 
                          ? brand.withOpacity(0.5) 
                          : Colors.grey.shade300,
              width: isUrgent ? 2 : 1,
            ),
          ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Row 1: Avatar + Name + Badges
            Row(
              children: [
                PersonAvatar(
                  name: appointment.patientName,
                  photoUrl: appointment.photoUrl,
                  radius: 18,
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: Row(
                    children: [
                      Flexible(
                        child: Text(
                          appointment.patientName,
                          style: const TextStyle(
                            fontSize: 15,
                            fontWeight: FontWeight.bold,
                          ),
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (hasChronicDisease) ...[
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.all(3),
                          decoration: const BoxDecoration(
                            color: Colors.red,
                            shape: BoxShape.circle,
                          ),
                          child: const Icon(
                            Icons.flag,
                            color: Colors.white,
                            size: 12,
                          ),
                        ),
                      ],
                      if (isCompleted) ...[
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: Colors.grey.shade400,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(Icons.check_circle, size: 12, color: Colors.white),
                              const SizedBox(width: 4),
                              Text(
                                l10n.complete,
                                style: const TextStyle(
                                  fontSize: 10,
                                  fontWeight: FontWeight.w600,
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ] else if (isNext) ...[
                        const SizedBox(width: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: brand,
                            borderRadius: BorderRadius.circular(8),
                          ),
                          child: Text(
                            l10n.translate('next') ?? 'Next',
                            style: const TextStyle(
                              fontSize: 10,
                              fontWeight: FontWeight.w600,
                              color: Colors.white,
                            ),
                          ),
                        ),
                      ],
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            // Row 2: Time + Type + Start Button
            Row(
              children: [
                // Time chip (prominent)
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: brand.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.access_time,
                        size: 14,
                        color: brand,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        appointment.start.format(context),
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.w600,
                          color: brand,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                // Visit type (compact)
                Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      appointment.isVideo ? Icons.videocam : Icons.location_on,
                      size: 14,
                      color: Colors.grey.shade600,
                    ),
                    const SizedBox(width: 4),
                    Text(
                      appointment.isVideo 
                          ? l10n.videoCall 
                          : l10n.inClinic,
                      style: TextStyle(
                        fontSize: 12,
                        color: Colors.grey.shade700,
                      ),
                    ),
                  ],
                ),
                const Spacer(),
                // Start button (disabled if completed)
                isCompleted
                    ? Container(
                        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 6),
                        decoration: BoxDecoration(
                          color: Colors.grey.shade300,
                          borderRadius: BorderRadius.circular(6),
                        ),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Icon(Icons.check_circle, size: 14, color: Colors.grey.shade600),
                            const SizedBox(width: 4),
                            Text(
                              l10n.complete,
                              style: TextStyle(
                                fontSize: 13,
                                fontWeight: FontWeight.w600,
                                color: Colors.grey.shade600,
                              ),
                            ),
                          ],
                        ),
                      )
                    : Tooltip(
                        message: appointment.isVideo && !canStartVideo
                            ? (isVideoPastColdJoinGrace
                                ? l10n.videoCallTooLateAfterOneHour
                                : (l10n.translate('videoCallAvailableFiveMinBefore') ??
                                    'You can start 5 minutes before the appointment.'))
                            : '',
                        child: ShifaActionButton(
                          label: l10n.start,
                          onPressed: startButtonEnabled ? onStart : null,
                          actionStyle: ActionButtonStyle.primary,
                        ),
                      ),
                if (appointment.patientId != null) ...[
                  const SizedBox(width: 6),
                  Tooltip(
                    message: l10n.generateBriefing,
                    child: IconButton(
                      onPressed: () {
                        ref.read(patientBriefingProvider.notifier).generate(
                          appointment.patientId!,
                          appointment.patientName,
                        );
                      },
                      icon: Icon(Icons.summarize, size: 20, color: brand),
                      style: IconButton.styleFrom(
                        padding: const EdgeInsets.all(6),
                        minimumSize: const Size(32, 32),
                      ),
                    ),
                  ),
                ],
              ],
            ),
          ],
        ),
        ),
      ),
    );
  }
}

// ============================================================================
// HOME SCREEN
// ============================================================================

class HomeScreen extends ConsumerStatefulWidget {
  const HomeScreen({super.key});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  String? _selectedAppointmentId;

  // ---------------- AI STATE ----------------
  final _aiController = TextEditingController();
  final _aiScroll = ScrollController();
  StreamSubscription<AiStreamEvent>? _aiSub;

  bool _aiLoading = false;
  String _streamingAiText = '';
  String? _aiError;
  bool _aiPanelExpanded = false;
  List<AiMessage> _conversation = [];

  String? _draftId;
  String? _draftLabel;

  String? _selectedPatientId;
  static const int _maxConversationMessages = 15;
  static const String _conversationStoragePrefix = 'ask_shifa_ai_conversation_v1';
  String? _lastConversationStorageKey;

  static const String _aiLangUzbekCyrillic = 'UZ_CYRL';

  /// Maps sidebar / [languageProvider] locale to the UI language codes used by prompts (EN/UZ/UZ_CYRL/RU).
  String _aiUiLanguageFromLocale(Locale locale) {
    switch (locale.languageCode) {
      case 'uz':
        return locale.isUzbekCyrillic ? _aiLangUzbekCyrillic : 'UZ';
      case 'ru':
        return 'RU';
      default:
        return 'EN';
    }
  }

  /// Backend [AiApi.streamAi] expects EN/UZ/RU/DE; Cyrillic Uzbek maps to UZ.
  String _aiLanguageBackendCode(String selected) {
    final u = selected.toUpperCase();
    if (u == _aiLangUzbekCyrillic) return 'UZ';
    return u;
  }

  String _assistantSystemPromptForLanguage() {
    final uiLang = _aiUiLanguageFromLocale(ref.read(languageProvider).locale);
    final lang = uiLang.toUpperCase();
    final backendLang = _aiLanguageBackendCode(uiLang);
    String assessment = 'Assessment';
    String causes = 'Possible Causes';
    String redFlags = 'Red Flags';
    String recommendations = 'Recommendations';
    String uncertaintyRule =
        'In structured mode, include a brief uncertainty / limitation note inside $assessment.';
    String redFlagsRule =
        'In structured mode only: include at least one concrete $redFlags item when clinically meaningful; if escalation risk is truly negligible, state that plainly in one line instead of inventing artificial emergencies.';

    if (lang == 'UZ' || lang == _aiLangUzbekCyrillic) {
      assessment = 'Baholash';
      causes = 'Mumkin bo\'lgan sabablar';
      redFlags = 'Xavfli belgilar';
      recommendations = 'Tavsiyalar';
      uncertaintyRule =
          'Tuzilgan javobda $assessment ichida qisqa noaniqlik / cheklov yozing.';
      redFlagsRule =
          'Faqat tuzilgan rejimda: klinik jihatdan asos bo\'lsa, kamida bitta aniq $redFlags bandi bo\'lsin; agar shoshilinch xavf haqiqatan yo\'q bo\'lsa, sun\'iy favqulodda holat ixtiro qilmang — bitta qator bilan past ekanini yozing.';
      if (lang == _aiLangUzbekCyrillic) {
        final uncertaintyLatin =
            'Tuzilgan javobda $assessment ichida qisqa noaniqlik / cheklov yozing.';
        final redFlagsLatin =
            'Faqat tuzilgan rejimda: klinik jihatdan asos bo\'lsa, kamida bitta aniq $redFlags bandi bo\'lsin; agar shoshilinch xavf haqiqatan yo\'q bo\'lsa, sun\'iy favqulodda holat ixtiro qilmang — bitta qator bilan past ekanini yozing.';
        assessment = transliterateUzbekLatinToCyrillicUi(assessment);
        causes = transliterateUzbekLatinToCyrillicUi(causes);
        redFlags = transliterateUzbekLatinToCyrillicUi(redFlags);
        recommendations = transliterateUzbekLatinToCyrillicUi(recommendations);
        uncertaintyRule = transliterateUzbekLatinToCyrillicUi(uncertaintyLatin);
        redFlagsRule = transliterateUzbekLatinToCyrillicUi(redFlagsLatin);
      }
    } else if (lang == 'RU') {
      assessment = 'Оценка';
      causes = 'Возможные причины';
      redFlags = 'Тревожные признаки';
      recommendations = 'Рекомендации';
      uncertaintyRule =
          'В структурированном ответе добавьте краткое указание на неопределённость / ограничения в разделе "$assessment".';
      redFlagsRule =
          'Только в структурированном режиме: если это клинически уместно, укажите минимум один конкретный пункт в "$redFlags"; если острой опасности нет, не выдумывайте её — одной строкой укажите, что неотложная эскалация маловероятна.';
    } else if (lang == 'DE') {
      assessment = 'Beurteilung';
      causes = 'Mögliche Ursachen';
      redFlags = 'Warnzeichen';
      recommendations = 'Empfehlungen';
      uncertaintyRule =
          'Im strukturierten Modus: kurzen Hinweis auf Unsicherheit / Grenzen in "$assessment".';
      redFlagsRule =
          'Nur im strukturierten Modus: bei klinischer Relevanz mindestens einen konkreten Punkt unter "$redFlags"; wenn das Notfallrisiko wirklich vernachlässigbar ist, keine künstliche Eskalation erfinden — in einer Zeile festhalten.';
    }

    final cyrillicUzNote = lang == _aiLangUzbekCyrillic
        ? ' Use the Uzbek Cyrillic alphabet for all Uzbek words (do not use Latin letters). '
        : ' ';

    return 'You are Shifa AI, a clinical decision support assistant for licensed doctors. '
        'Always write in the doctor\'s selected UI language (language code: $backendLang); keep tone professional and concise.'
        '$cyrillicUzNote'
        '\n\n'
        'RESPONSE SHAPE — pick ONE approach per answer (do not force clinical sections onto factual questions):\n\n'
        '(A) DIRECT ANSWER — use by default for reference, education, coding/classification (e.g. ICD-10 examples), definitions, '
        'literature-oriented summaries, generic protocols, administrative/medico-legal generalities, or when the doctor asks for lists, '
        'examples, or explanations without a patient-specific decision.\n'
        '- Answer straight away with clear prose or short line-separated items.\n'
        '- Do NOT label your reply with the four clinical section headings below.\n'
        '- Add a brief safety caveat only when it materially matters (e.g. coding lists are illustrative, not exhaustive).\n\n'
        '(B) STRUCTURED CLINICAL SUPPORT — use only when the doctor is doing scenario-based reasoning: interpreting findings or symptoms, '
        'building differentials, weighing risks for a described presentation, or asking what to watch for / next diagnostic-clinical steps '
        'where separating synthesis from escalation guidance genuinely helps.\n'
        'Then use exactly these headings and order:\n'
        '1) $assessment\n'
        '2) $causes\n'
        '3) $redFlags\n'
        '4) $recommendations\n\n'
        'Rules for mode (B) only:\n'
        '- $uncertaintyRule\n'
        '- $redFlagsRule\n\n'
        'If unsure whether (A) or (B) fits, prefer (A) unless the question clearly depends on interpreting a patient-related clinical situation.\n'
        'General: you are not a substitute for bedside judgment; avoid definitive diagnoses and prescribing dosages; flag emergencies overtly when warranted.';
  }

  String _promptNoPatientSelected() {
    switch (_aiUiLanguageFromLocale(ref.read(languageProvider).locale).toUpperCase()) {
      case 'UZ':
        return 'Bemor tanlanmagan.';
      case _aiLangUzbekCyrillic:
        return transliterateUzbekLatinToCyrillicUi('Bemor tanlanmagan.');
      case 'RU':
        return 'Пациент не выбран.';
      case 'DE':
        return 'Kein Patient ausgewählt.';
      default:
        return 'No patient selected.';
    }
  }

  String _promptMedicalHistoryUnavailable() {
    switch (_aiUiLanguageFromLocale(ref.read(languageProvider).locale).toUpperCase()) {
      case 'UZ':
        return 'Tibbiy tarix brifingi mavjud emas (zarur bo‘lsa, uchrashuvlar bo‘yicha brifing yarating).';
      case _aiLangUzbekCyrillic:
        return transliterateUzbekLatinToCyrillicUi(
          'Tibbiy tarix brifingi mavjud emas (zarur bo‘lsa, uchrashuvlar bo‘yicha brifing yarating).',
        );
      case 'RU':
        return 'Брифинг по медицинскому анамнезу недоступен (при необходимости сгенерируйте брифинг по приёмам).';
      case 'DE':
        return 'Medizinischer Verlauf nicht verfügbar (bei Bedarf Briefing aus Terminen generieren).';
      default:
        return 'Medical history briefing unavailable (generate briefing from the appointment list if needed).';
    }
  }

  void _startNewSession() {
    setState(() {
      _conversation = [];
      _streamingAiText = '';
      _aiError = null;
      _draftId = null;
      _draftLabel = null;
      _aiLoading = false;
    });
    _persistConversation();
  }

  Future<void> _askAiWithPrompt(String prompt) async {
    if (_aiLoading) return;
    _aiController.text = prompt;
    await _askAi();
  }

  AiMessage? _latestAssistantMessage() {
    for (var i = _conversation.length - 1; i >= 0; i--) {
      if (_conversation[i].role == 'assistant') return _conversation[i];
    }
    return null;
  }

  String _conversationStorageKey() {
    final profile = ref.read(profileAllProvider).valueOrNull?.profile;
    final doctorId = (profile?['id'] ?? profile?['doctorId'] ?? 'unknown').toString();
    final patientId = _selectedPatientId ?? 'none';
    final consultationId = _selectedAppointmentId ?? 'none';
    return '$_conversationStoragePrefix:$doctorId:$patientId:$consultationId';
  }

  Future<void> _persistConversation() async {
    final key = _conversationStorageKey();
    final prefs = await SharedPreferences.getInstance();
    final payload = _conversation.map((m) => m.toJson()).toList();
    await prefs.setString(key, jsonEncode(payload));
  }

  Future<void> _loadConversationForCurrentContext() async {
    final key = _conversationStorageKey();
    if (_lastConversationStorageKey == key) return;
    _lastConversationStorageKey = key;
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(key);
    List<AiMessage> loaded = [];
    if (raw != null && raw.isNotEmpty) {
      try {
        final decoded = jsonDecode(raw) as List<dynamic>;
        loaded = decoded
            .whereType<Map>()
            .map((m) => Map<String, dynamic>.from(m))
            .map((m) => AiMessage.fromJson(m))
            .where((m) => m.content.trim().isNotEmpty)
            .toList();
      } catch (_) {}
    }
    if (!mounted) return;
    setState(() {
      _conversation = loaded;
      _streamingAiText = '';
      _aiError = null;
    });
    await _prefetchBriefingForAskShifaSilently();
  }

  /// Fetches patient briefing via API into [patientBriefingContextProvider] only.
  /// Does not open the Patient Briefing panel (unlike [PatientBriefingNotifier.generate]).
  Future<void> _prefetchBriefingForAskShifaSilently() async {
    if (_selectedPatientId == null) return;
    await ref.read(patientBriefingContextProvider.notifier).ensureLoaded(_selectedPatientId!);
    if (mounted) setState(() {});
  }

  Future<void> _askAi() async {
    final question = _aiController.text.trim();
    if (question.isEmpty) return;
    if (_selectedPatientId != null) {
      await ref.read(patientBriefingContextProvider.notifier).ensureLoaded(_selectedPatientId!);
    }

    final aiApi = ref.read(aiApiProvider);
    final nextConversation = [..._conversation, AiMessage(role: 'user', content: question)];
    final payloadMessages = _normalizedConversationFrom(nextConversation, withPatientContext: true);

    setState(() {
      _conversation = nextConversation;
      _streamingAiText = '';
      _aiError = null;
      _draftId = null;
      _draftLabel = null;
      _aiLoading = true;
    });
    _aiController.clear();

    _aiSub?.cancel();

    _aiSub = aiApi
        .streamAi(
          messages: payloadMessages,
          question: question,
          language: _aiLanguageBackendCode(_aiUiLanguageFromLocale(ref.read(languageProvider).locale)),
          patientId: _selectedPatientId == null ? null : int.tryParse(_selectedPatientId!),
          consultationId: _selectedAppointmentId != null ? int.tryParse(_selectedAppointmentId!) : null,
        )
        .listen(
          (event) {
            if (event is AiTokenEvent) {
              setState(() => _streamingAiText += event.token);
              _aiScroll.jumpTo(_aiScroll.position.maxScrollExtent + 20);
            } else if (event is AiDraftReadyEvent) {
              setState(() {
                _draftId = event.draft.draftId;
                _draftLabel = event.draft.draftLabel;
              });
            }
          },
          onDone: () {
            setState(() {
              final answer = _streamingAiText.trimRight();
              debugPrint('[Ask Shifa AI][HomePanel] Raw response: ${answer.replaceAll('\n', r'\n')}');
              if (answer.isNotEmpty) {
                _conversation = [..._conversation, AiMessage(role: 'assistant', content: answer)];
                _conversation = _normalizedConversationFrom(_conversation);
              }
              _streamingAiText = '';
              _aiLoading = false;
              _aiError = null;
            });
            _persistConversation();
          },
          onError: (e) => setState(() {
            _aiLoading = false;
            _aiError = e is AiStreamException ? e.userFacingMessage : e.toString();
          }),
        );
    _persistConversation();
  }

  String _buildPatientContextSystemMessage() {
    final patients = ref.read(patientsProvider);
    Patient? selectedPatient;
    if (_selectedPatientId != null) {
      for (final p in patients) {
        if (p.id.toString() == _selectedPatientId) {
          selectedPatient = p;
          break;
        }
      }
    }
    final pid = _selectedPatientId;
    final cache = pid != null ? ref.read(patientBriefingContextProvider)[pid] : null;
    final foreground = ref.read(patientBriefingProvider);
    String? historyText;
    if (cache != null && cache.hasText) {
      historyText = cache.briefingText.trim();
    } else if (foreground.patientId == pid && (foreground.briefingText ?? '').trim().isNotEmpty) {
      historyText = foreground.briefingText!.trim();
    }
    final demographics = selectedPatient == null
        ? _promptNoPatientSelected()
        : 'Name: ${selectedPatient.name}; '
            'Birth date: ${selectedPatient.general.birthDate?.toIso8601String() ?? 'Unknown'}; '
            'Language: ${selectedPatient.general.language ?? 'Unknown'}; '
            'Chronic disease: ${selectedPatient.general.chronicDisease ?? 'Not specified'}.';
    final meds = 'Current medications: Not explicitly available in structured data. '
        'Infer only from briefing text if medications are explicitly mentioned.';
    final history = historyText ?? _promptMedicalHistoryUnavailable();
    return 'Patient context:\n'
        '- Demographics: $demographics\n'
        '- Medical history summary: $history\n'
        '- $meds';
  }

  String _compressOlderMessages(List<AiMessage> older) {
    if (older.isEmpty) return '';
    final lines = older.take(10).map((m) {
      final clipped = m.content.replaceAll('\n', ' ').trim();
      final short = clipped.length > 160 ? '${clipped.substring(0, 160)}...' : clipped;
      return '- ${m.role.toUpperCase()}: $short';
    }).join('\n');
    return 'Conversation summary of earlier turns:\n$lines';
  }

  List<AiMessage> _normalizedConversationFrom(List<AiMessage> source, {bool withPatientContext = false}) {
    final trimmed = source.where((m) => m.content.trim().isNotEmpty).toList();
    final withoutSystem = trimmed.where((m) => m.role != 'system').toList();
    final summaryCutoff = withoutSystem.length > _maxConversationMessages;
    final recent = summaryCutoff
        ? withoutSystem.sublist(withoutSystem.length - _maxConversationMessages)
        : withoutSystem;
    final older = summaryCutoff
        ? withoutSystem.sublist(0, withoutSystem.length - _maxConversationMessages)
        : <AiMessage>[];

    final result = <AiMessage>[
      AiMessage(role: 'system', content: _assistantSystemPromptForLanguage()),
      if (withPatientContext) AiMessage(role: 'system', content: _buildPatientContextSystemMessage()),
    ];
    final compressed = _compressOlderMessages(older);
    if (compressed.isNotEmpty) {
      result.add(AiMessage(role: 'system', content: compressed));
    }
    result.addAll(recent);
    return result;
  }

  Future<void> _confirmDraft() async {
    if (_draftId == null) return;
    final aiApi = ref.read(aiApiProvider);
    try {
      await aiApi.confirmDraft(
        _draftId!,
        patientId: _selectedPatientId != null ? int.tryParse(_selectedPatientId!) : null,
        appointmentId: _selectedAppointmentId != null ? int.tryParse(_selectedAppointmentId!) : null,
      );
      if (!mounted) return;
      if (_selectedAppointmentId != null) {
        ref.invalidate(consultationNotesForAppointmentProvider(_selectedAppointmentId!));
      }
      setState(() {
        _draftId = null;
        _draftLabel = null;
      });
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(AppLocalizations.of(context)!.translate('draftSavedAsConsultationNote') ?? 'Draft saved as consultation note')),
      );
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(AppLocalizations.of(context)!.translate('failedToSaveDraft') ?? 'Failed to save draft'),
          backgroundColor: Colors.red,
        ),
      );
    }
  }

  Future<void> _discardDraft() async {
    if (_draftId == null) return;
    final aiApi = ref.read(aiApiProvider);
    try {
      await aiApi.discardDraft(_draftId!);
      if (!mounted) return;
      setState(() {
        _draftId = null;
        _draftLabel = null;
      });
      _persistConversation();
    } catch (_) {}
  }

  @override
  void dispose() {
    _aiSub?.cancel();
    _aiController.dispose();
    _aiScroll.dispose();
    super.dispose();
  }

  /// Today card: left column in sketch (~35% width on desktop). Used in LayoutBuilder for collapsible layout.
  Widget _buildTodayCard(
    BuildContext context,
    WidgetRef ref,
    AsyncValue<List<Appointment>> appointmentsAsync,
  ) {
    return Container(
      padding: const EdgeInsets.all(24),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            AppLocalizations.of(context)!.today,
            style: const TextStyle(
              fontSize: 22,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 24),
          Expanded(
            child: appointmentsAsync.when(
              loading: () => const Center(
                child: CircularProgressIndicator(),
              ),
              error: (e, _) => Center(child: Text('${AppLocalizations.of(context)!.error}: $e')),
              data: (appointments) {
                final validAppointments = appointments.where((appt) {
                  if (appt.id.isEmpty) return false;
                  if (appt.patientName.isEmpty) return false;
                  return true;
                }).toList();
                if (validAppointments.isEmpty) {
                  return Center(
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Icon(
                          Icons.calendar_today_outlined,
                          size: 64,
                          color: Colors.grey.shade400,
                        ),
                        const SizedBox(height: 16),
                        Text(
                          AppLocalizations.of(context)!.noAppointmentsToday,
                          style: TextStyle(
                            fontSize: 16,
                            color: Colors.grey.shade600,
                            fontWeight: FontWeight.w500,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          AppLocalizations.of(context)!.scheduleIsClear,
                          style: TextStyle(
                            fontSize: 14,
                            color: Colors.grey.shade500,
                          ),
                        ),
                      ],
                    ),
                  );
                }
                return ListView.separated(
                  itemCount: validAppointments.length,
                  separatorBuilder: (_, __) => const SizedBox(height: 12),
                  itemBuilder: (context, i) {
                    final appt = validAppointments[i];
                    return _AppointmentItem(
                      appointment: appt,
                      selected: _selectedAppointmentId == appt.id,
                      onTap: () {
                        setState(() {
                          _selectedAppointmentId = appt.id;
                          if (appt.patientId != null) {
                            _selectedPatientId = appt.patientId.toString();
                          }
                        });
                        _loadConversationForCurrentContext();
                      },
                      onStart: () async {
                        if (appt.patientId != null) {
                          try {
                            final patientAsync = ref.read(patientByIdProvider(appt.patientId!));
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
                              await showChronicDiseaseWarning(
                                context,
                                patient.name,
                                patient.general.chronicDisease!,
                              );
                            }
                          } catch (_) {}
                        }
                        if (mounted) {
                          ShellScope.pushNamed(
                            context,
                            appt.isVideo
                                ? AppRoutes.videoCall  // Skip waiting room, go directly to video call
                                : AppRoutes.inPerson,
                            arguments: appt,
                          );
                        }
                      },
                    );
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final appointmentsAsync = ref.watch(todayAppointmentsProvider);
    final brand = Theme.of(context).colorScheme.primary;
    final patients = ref.watch(patientsProvider);
    // Advanced analytics charts (trend / visit-type / engagement) are PREMIUM-only.
    // BASIC and PRO tiers see only the KPI summary cards (basic analytics).
    // Ask Shifa AI panel is PRO+.
    final showAdvancedAnalytics =
        ref.watch(doctorFeatureProvider(DoctorFeature.advancedAnalytics));
    final canUseAskShifaAi = ref.watch(doctorFeatureProvider(DoctorFeature.askShifaAi));
    return Scaffold(
      backgroundColor: const Color(0xFFF5F5F5),
      body: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          children: [
            // Layout from sketch: Left 35% Today | Right 65% KPI row + trend + donut + engagement. Collapses to stack on narrow screens.
            Expanded(
              child: LayoutBuilder(
                builder: (context, constraints) {
                  final isNarrow = constraints.maxWidth < 700;
                  if (isNarrow) {
                    // Stack: Today full width, then KPI row, then charts (collapsible on small screens)
                    return SingleChildScrollView(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.stretch,
                        children: [
                          SizedBox(
                            height: 320,
                            child: _buildTodayCard(context, ref, appointmentsAsync),
                          ),
                          const SizedBox(height: 16),
                          const AnalyticsKpiCards(),
                          if (showAdvancedAnalytics) ...const [
                            SizedBox(height: 16),
                            AppointmentsTrendChart(),
                            VisitTypeDonutChart(),
                            AnalyticsEngagementWidget(),
                          ],
                        ],
                      ),
                    );
                  }
                  // Desktop: Row — Today (35%) | Right column (65%)
                  return Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        flex: 35,
                        child: _buildTodayCard(context, ref, appointmentsAsync),
                      ),
                      const SizedBox(width: 24),
                      Expanded(
                        flex: 65,
                        child: SingleChildScrollView(
                          child: Column(
                            children: [
                              const AnalyticsKpiCards(),
                              if (showAdvancedAnalytics) ...const [
                                SizedBox(height: 16),
                                AppointmentsTrendChart(),
                                VisitTypeDonutChart(),
                                AnalyticsEngagementWidget(),
                              ],
                            ],
                          ),
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),

            if (canUseAskShifaAi) const SizedBox(height: 24),

            // -------- AI PANEL (Collapsible) --------
            if (canUseAskShifaAi) AnimatedContainer(
              duration: const Duration(milliseconds: 250),
              curve: Curves.easeInOut,
              // Let the panel size itself naturally so it can
              // shrink on shorter screens instead of overflowing.
              constraints: const BoxConstraints(
                minHeight: 56,
              ),
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: Colors.white,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(color: brand.withOpacity(0.2)),
                boxShadow: [
                  BoxShadow(
                    color: Colors.black.withOpacity(0.05),
                    blurRadius: 4,
                    offset: const Offset(0, 2),
                  ),
                ],
              ),
              child: _aiPanelExpanded
                  ? Column(
                      mainAxisSize: MainAxisSize.min,
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        // ---------- HEADER (with collapse toggle) ----------
                        Row(
                          children: [
                            Expanded(
                              child: Row(
                                children: [
                                  Icon(
                                    Icons.psychology,
                                    size: 20,
                                    color: brand,
                                  ),
                                  const SizedBox(width: 8),
                                  Text(
                                    AppLocalizations.of(context)!.translate('askShifaAi') ?? 'Ask Shifa AI',
                                    style: TextStyle(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                      color: brand,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                            SizedBox(
                              width: 260,
                              child: DropdownButtonFormField<String>(
                                initialValue: _selectedPatientId,
                                isExpanded: true,
                                icon: const Icon(Icons.arrow_drop_down),
                                decoration: InputDecoration(
                                  hintText: AppLocalizations.of(context)!.allPatients,
                                  isDense: true,
                                  contentPadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
                                  border: OutlineInputBorder(
                                    borderRadius: BorderRadius.circular(10),
                                    borderSide: BorderSide(color: Colors.grey.shade300),
                                  ),
                                  enabledBorder: OutlineInputBorder(
                                    borderRadius: BorderRadius.circular(10),
                                    borderSide: BorderSide(color: Colors.grey.shade300),
                                  ),
                                  filled: true,
                                  fillColor: Colors.grey.shade50,
                                ),
                                items: patients.isEmpty
                                    ? [
                                        DropdownMenuItem(
                                          value: null,
                                          child: Text(
                                            AppLocalizations.of(context)!.translate('loadingPatients') ?? 'Loading patients…',
                                            style: TextStyle(color: Colors.grey.shade600),
                                          ),
                                        ),
                                      ]
                                    : [
                                        DropdownMenuItem(
                                          value: null,
                                          child: Text(
                                            AppLocalizations.of(context)!.allPatients,
                                            style: TextStyle(color: Colors.grey.shade600),
                                          ),
                                        ),
                                        ...patients.map(
                                          (p) => DropdownMenuItem<String>(
                                            value: p.id.toString(),
                                            child: Text(
                                              p.name,
                                              overflow: TextOverflow.ellipsis,
                                            ),
                                          ),
                                        ),
                                      ],
                                onChanged: patients.isEmpty
                                    ? null
                                    : (v) {
                                        setState(() => _selectedPatientId = v);
                                        _loadConversationForCurrentContext();
                                      },
                              ),
                            ),
                            const SizedBox(width: 8),
                            Container(
                              width: 36,
                              height: 36,
                              decoration: BoxDecoration(
                                color: brand,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: IconButton(
                                icon: const Icon(
                                  Icons.keyboard_arrow_down,
                                  color: Colors.white,
                                  size: 20,
                                ),
                                onPressed: () => setState(() => _aiPanelExpanded = false),
                                tooltip: AppLocalizations.of(context)!.collapse,
                                padding: EdgeInsets.zero,
                                constraints: const BoxConstraints(),
                              ),
                            ),
                          ],
                        ),

                        const SizedBox(height: 12),

                        Align(
                          alignment: Alignment.centerLeft,
                          child: TextButton.icon(
                            onPressed: _aiLoading ? null : _startNewSession,
                            icon: const Icon(Icons.refresh, size: 15),
                            label: Text(AppLocalizations.of(context)!.translate('newSession')),
                            style: TextButton.styleFrom(
                              foregroundColor: Colors.grey.shade700,
                              visualDensity: VisualDensity.compact,
                            ),
                          ),
                        ),
                        const SizedBox(height: 10),
                        // ---------- CONVERSATION AREA ----------
                        Container(
                          constraints: const BoxConstraints(minHeight: 120, maxHeight: 280),
                          width: double.infinity,
                          padding: const EdgeInsets.all(12),
                          decoration: BoxDecoration(
                            color: Colors.white,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(color: Colors.grey.shade200),
                          ),
                          child: SingleChildScrollView(
                            controller: _aiScroll,
                            child: _aiError != null
                                ? Text(
                                    _aiError!,
                                    softWrap: true,
                                    textAlign: TextAlign.start,
                                    style: TextStyle(
                                      fontSize: 13,
                                      color: Theme.of(context).colorScheme.error,
                                      height: 1.4,
                                    ),
                                  )
                                : (_conversation.where((m) => m.role != 'system').isEmpty && _streamingAiText.isEmpty)
                                    ? Text(
                                        AppLocalizations.of(context)!.translate('aiWillRespondHere') ?? 'AI will respond here…',
                                        softWrap: true,
                                        textAlign: TextAlign.start,
                                        style: TextStyle(
                                          fontSize: 13,
                                          color: Colors.grey.shade500,
                                          fontStyle: FontStyle.italic,
                                        ),
                                      )
                                    : Column(
                                        crossAxisAlignment: CrossAxisAlignment.stretch,
                                        children: [
                                          ..._conversation
                                              .where((m) => m.role != 'system')
                                              .map(
                                                (msg) => _AiChatBubble(
                                                  role: msg.role,
                                                  child: AiResponseText(
                                                    text: msg.content,
                                                    style: const TextStyle(fontSize: 13, height: 1.5),
                                                    maxWidth: 560,
                                                  ),
                                                ),
                                              ),
                                          if (_streamingAiText.isNotEmpty)
                                            _AiChatBubble(
                                              role: 'assistant',
                                              child: AiResponseText(
                                                text: _streamingAiText,
                                                style: const TextStyle(fontSize: 13, height: 1.5),
                                                maxWidth: 560,
                                              ),
                                            ),
                                        ],
                                      ),
                          ),
                        ),
                        if (!_aiLoading && _latestAssistantMessage() != null) ...[
                          const SizedBox(height: 8),
                          Wrap(
                            spacing: 8,
                            runSpacing: 8,
                            children: [
                              ActionChip(
                                label: Text(AppLocalizations.of(context)!.translate('aiFollowupRefineDiagnosis')),
                                onPressed: () => _askAiWithPrompt(
                                  AppLocalizations.of(context)!.translate('aiFollowupPromptRefineDiagnosis'),
                                ),
                              ),
                              ActionChip(
                                label: Text(AppLocalizations.of(context)!.translate('aiFollowupTreatmentOptions')),
                                onPressed: () => _askAiWithPrompt(
                                  AppLocalizations.of(context)!.translate('aiFollowupPromptTreatmentOptions'),
                                ),
                              ),
                              ActionChip(
                                label: Text(AppLocalizations.of(context)!.translate('aiFollowupWhenToWorry')),
                                onPressed: () => _askAiWithPrompt(
                                  AppLocalizations.of(context)!.translate('aiFollowupPromptWhenToWorry'),
                                ),
                              ),
                            ],
                          ),
                        ],
                        const SizedBox(height: 10),
                        // ---------- INPUT ----------
                        TextField(
                          controller: _aiController,
                          decoration: InputDecoration(
                            hintText:
                                AppLocalizations.of(context)!.translate('askShifaAi') ??
                                'Ask Shifa AI',
                            suffixIcon: Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                if (_draftId != null)
                                  PopupMenuButton<String>(
                                    tooltip: _draftLabel != null && _draftLabel!.isNotEmpty
                                        ? _draftLabel!
                                        : AppLocalizations.of(context)!.translate('draftActions'),
                                    icon: Icon(Icons.more_vert, color: Colors.grey.shade700),
                                    onSelected: (value) {
                                      if (value == 'save') {
                                        _confirmDraft();
                                      } else if (value == 'discard') {
                                        _discardDraft();
                                      }
                                    },
                                    itemBuilder: (context) => [
                                      PopupMenuItem(
                                        value: 'save',
                                        child: Text(AppLocalizations.of(context)!.translate('saveDraftNote') ?? 'Save as Draft Note'),
                                      ),
                                      PopupMenuItem(
                                        value: 'discard',
                                        child: Text(AppLocalizations.of(context)!.discard),
                                      ),
                                    ],
                                  ),
                                IconButton(
                                  icon: _aiLoading
                                      ? SizedBox(
                                          width: 16,
                                          height: 16,
                                          child: CircularProgressIndicator(
                                            strokeWidth: 2,
                                            valueColor: AlwaysStoppedAnimation<Color>(brand),
                                          ),
                                        )
                                      : Icon(Icons.send, color: brand),
                                  onPressed: _aiLoading ? null : _askAi,
                                ),
                              ],
                            ),
                            contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(20),
                              borderSide: BorderSide(color: Colors.grey.shade300),
                            ),
                            enabledBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(20),
                              borderSide: BorderSide(color: Colors.grey.shade300),
                            ),
                            focusedBorder: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(20),
                              borderSide: BorderSide(color: brand, width: 1.8),
                            ),
                            filled: true,
                            fillColor: Colors.white,
                          ),
                          onSubmitted: (_) => _askAi(),
                        ),
                      ],
                    )
                  : Row(
                      children: [
                        Icon(
                          Icons.psychology,
                          size: 20,
                          color: brand,
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            AppLocalizations.of(context)!.translate('askShifaAi') ?? 'Ask Shifa AI',
                            style: TextStyle(
                              fontSize: 16,
                              fontWeight: FontWeight.bold,
                              color: brand,
                            ),
                          ),
                        ),
                        IconButton(
                          icon: Icon(
                            Icons.keyboard_arrow_up,
                            color: brand,
                          ),
                          onPressed: () {
                            setState(() => _aiPanelExpanded = true);
                            _loadConversationForCurrentContext();
                          },
                          tooltip: AppLocalizations.of(context)!.expand,
                          padding: EdgeInsets.zero,
                          constraints: const BoxConstraints(),
                        ),
                      ],
                    ),
            ),
          ],
        ),
      ),
    );
  }
}

class _AiChatBubble extends StatelessWidget {
  const _AiChatBubble({
    required this.role,
    required this.child,
  });

  final String role;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final isUser = role == 'user';
    return Align(
      alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 10),
        constraints: const BoxConstraints(maxWidth: 620),
        decoration: BoxDecoration(
          color: isUser ? const Color(0xFFE8F7FA) : Colors.white,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: isUser ? const Color(0xFFBCEAF1) : const Color(0xFFE3E6EB)),
        ),
        child: child,
      ),
    );
  }
}
