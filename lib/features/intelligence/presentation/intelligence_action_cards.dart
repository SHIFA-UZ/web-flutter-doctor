import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/api/api_providers.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/features/appointments/application/consultation_notes_provider.dart';
import 'package:shifa_doc_app_v1/features/home/presentation/widgets/visit_facts_panel.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_card_text.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_inline_viewers.dart';
import 'package:shifa_doc_app_v1/features/shell/domain/doctor_shell_tab.dart';
import 'package:shifa_doc_app_v1/state/appointments/appointment_invalidation.dart';
import 'package:shifa_doc_app_v1/state/notifications/doctor_notifications_provider.dart';
import 'package:shifa_doc_app_v1/state/shell/shell_controller.dart';
import 'package:shifa_doc_app_v1/state/workspace/workspace_mode_provider.dart';

/// Renders a morning-tool payload as a clinical action card.
class IntelligenceActionCard extends ConsumerStatefulWidget {
  const IntelligenceActionCard({
    super.key,
    required this.toolName,
    required this.payload,
    this.onDismiss,
  });

  final String toolName;
  final Map<String, dynamic> payload;
  final VoidCallback? onDismiss;

  @override
  ConsumerState<IntelligenceActionCard> createState() =>
      _IntelligenceActionCardState();
}

class _IntelligenceActionCardState
    extends ConsumerState<IntelligenceActionCard> {
  bool _busy = false;
  String? _status;
  String? _error;
  bool _dismissed = false;
  bool _showOtherTimes = false;
  int? _openedDocumentId;

  String get _cardType =>
      (widget.payload['cardType'] as String?) ?? widget.toolName;

  String _localizedStatus(AppLocalizations l10n) {
    final raw = _status ?? '';
    final localized = localizeIntelligenceHint(l10n, raw);
    if (!intelligenceToolLineStillEnglish(localized)) return localized;
    final name = widget.payload['patientName']?.toString() ?? '';
    final slot = widget.payload['preferredSlot'];
    final time = slot is Map ? slot['startLocal']?.toString() ?? '' : '';
    final when =
        widget.payload['speakableDateLabel']?.toString() ??
        widget.payload['dateLabel']?.toString() ??
        '';
    if (name.isEmpty || time.isEmpty) return localized;
    final onWhen = when.isEmpty ? '' : ' on $when';
    final canonical = _cardType == 'propose_reschedule'
        ? 'Moved $name to $time$onWhen.'
        : 'Booked $name at $time$onWhen.';
    final rebuilt = localizeIntelligenceHint(l10n, canonical);
    return intelligenceToolLineStillEnglish(rebuilt) ? localized : rebuilt;
  }

  @override
  void initState() {
    super.initState();
    if (widget.payload['booked'] == true) {
      _status =
          widget.payload['statusMessage']?.toString() ?? 'Appointment booked.';
    }
    final autoOpen = (widget.payload['autoOpenDocumentId'] as num?)?.toInt();
    if (autoOpen != null) {
      _openedDocumentId = autoOpen;
    }
  }

  @override
  Widget build(BuildContext context) {
    if (_dismissed) return const SizedBox.shrink();
    final brand = Theme.of(context).colorScheme.primary;
    final l10n = AppLocalizations.of(context)!;

    return Container(
      margin: const EdgeInsets.only(top: 10, bottom: 4),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppDesignSystem.border),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.04),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Text(
            _title(l10n),
            style: AppDesignSystem.body1(
              context,
            ).copyWith(fontWeight: FontWeight.w700, color: brand),
          ),
          const SizedBox(height: 10),
          ..._body(context, brand, l10n),
          if (_error != null) ...[
            const SizedBox(height: 8),
            Text(
              localizeIntelligenceHint(l10n, _error!),
              style: AppDesignSystem.caption(
                context,
              ).copyWith(color: Colors.red.shade700),
            ),
          ],
          if (_status != null) ...[
            const SizedBox(height: 8),
            Text(
              _localizedStatus(l10n),
              style: AppDesignSystem.caption(context).copyWith(
                color: AppDesignSystem.success,
                fontWeight: FontWeight.w600,
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _title(AppLocalizations l10n) {
    switch (_cardType) {
      case 'schedule':
      case 'get_today_schedule':
        return l10n.translate('intelCardSchedule');
      case 'patient_appointments':
      case 'list_patient_appointments':
        return l10n.translate('intelCardPatientAppointments');
      case 'free_slots':
      case 'list_free_slots':
        return l10n.translate('intelCardFreeSlots');
      case 'patient_search':
      case 'search_patients':
        return l10n.translate('intelCardPatients');
      case 'propose_appointment':
        return l10n.translate('intelCardProposeBook');
      case 'propose_reschedule':
        return l10n.translate('intelCardProposeMove');
      case 'draft_visit_facts':
      case 'list_draft_visit_facts':
        return l10n.translate('intelCardDraftFacts');
      case 'most_visited':
      case 'get_most_visited_patients':
        return l10n.translate('intelCardMostVisited');
      case 'clinic_patterns':
      case 'get_clinic_patterns':
        return l10n.translate('intelCardPatterns');
      case 'patient_brief':
      case 'brief_patient':
        return l10n.translate('intelCardBrief');
      case 'patient_documents':
      case 'list_patient_documents':
        return l10n.translate('intelCardDocuments');
      case 'document_viewer':
      case 'open_patient_document':
        return l10n.translate('intelCardDocumentViewer');
      case 'dental_chart':
      case 'open_dental_chart':
        return l10n.translate('intelCardDentalChart');
      default:
        return l10n.translate('shifaIntelligence');
    }
  }

  List<Widget> _body(BuildContext context, Color brand, AppLocalizations l10n) {
    switch (_cardType) {
      case 'schedule':
      case 'get_today_schedule':
        return _scheduleBody(context, brand, l10n);
      case 'patient_appointments':
      case 'list_patient_appointments':
        return _patientAppointmentsBody(context, brand, l10n);
      case 'free_slots':
      case 'list_free_slots':
        return _freeSlotsBody(context, l10n);
      case 'patient_search':
      case 'search_patients':
        return _searchBody(context, l10n);
      case 'propose_appointment':
        return _proposeBookBody(context, brand, l10n);
      case 'propose_reschedule':
        return _proposeMoveBody(context, brand, l10n);
      case 'draft_visit_facts':
      case 'list_draft_visit_facts':
        return _draftFactsBody(context, brand, l10n);
      case 'most_visited':
      case 'get_most_visited_patients':
        return _listNameCountBody(context, l10n, 'patients');
      case 'clinic_patterns':
      case 'get_clinic_patterns':
        return _patternsBody(context, l10n);
      case 'patient_brief':
      case 'brief_patient':
        return _briefBody(context, l10n);
      case 'patient_documents':
      case 'list_patient_documents':
        return _documentsListBody(context, brand, l10n);
      case 'document_viewer':
      case 'open_patient_document':
        return _documentViewerBody(context, l10n);
      case 'dental_chart':
      case 'open_dental_chart':
        return _dentalChartBody(context, brand, l10n);
      default:
        return [
          Text(
            localizeIntelligenceHint(
              l10n,
              widget.payload['spokenHint']?.toString() ??
                  widget.payload['error']?.toString() ??
                  '',
            ),
            style: AppDesignSystem.body2(context),
          ),
        ];
    }
  }

  List<Widget> _documentsListBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final patientId = (widget.payload['patientId'] as num?)?.toInt();
    final docs = (widget.payload['documents'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final openedId = _openedDocumentId;
    Map<String, dynamic>? opened;
    if (openedId != null) {
      for (final d in docs) {
        if ((d['documentId'] as num?)?.toInt() == openedId) {
          opened = d;
          break;
        }
      }
    }

    return [
      if (docs.isEmpty)
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ??
                l10n.translate('intelNoDocuments'),
          ),
          style: AppDesignSystem.body2(context),
        )
      else
        ...docs.take(12).map((d) {
          final id = (d['documentId'] as num?)?.toInt();
          final title = intelligenceDocumentLabel(
            l10n,
            d['title']?.toString(),
          );
          final date = intelligenceDateLabel(
            l10n,
            isoDate: d['date']?.toString(),
            fallback: d['date']?.toString(),
          );
          final selected = id != null && id == openedId;
          return Padding(
            padding: const EdgeInsets.only(bottom: 4),
            child: Material(
              color: selected
                  ? brand.withValues(alpha: 0.08)
                  : Colors.transparent,
              borderRadius: BorderRadius.circular(8),
              child: InkWell(
                borderRadius: BorderRadius.circular(8),
                onTap: id == null || patientId == null
                    ? null
                    : () => setState(() => _openedDocumentId = id),
                child: Padding(
                  padding: const EdgeInsets.symmetric(
                    horizontal: 8,
                    vertical: 8,
                  ),
                  child: Row(
                    children: [
                      Icon(Icons.description_outlined, size: 18, color: brand),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              title,
                              style: AppDesignSystem.body2(
                                context,
                              ).copyWith(fontWeight: FontWeight.w600),
                            ),
                            if (date.isNotEmpty)
                              Text(
                                date,
                                style: AppDesignSystem.caption(context)
                                    .copyWith(
                                      color: AppDesignSystem.textSecondary,
                                    ),
                              ),
                          ],
                        ),
                      ),
                      Icon(
                        selected
                            ? Icons.visibility
                            : Icons.open_in_browser_outlined,
                        size: 18,
                        color: AppDesignSystem.textSecondary,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        }),
      if (opened != null && patientId != null && openedId != null) ...[
        const SizedBox(height: 10),
        IntelligenceInlineDocumentViewer(
          patientId: '$patientId',
          documentId: '$openedId',
          title: intelligenceDocumentLabel(l10n, opened['title']?.toString()),
        ),
      ],
    ];
  }

  List<Widget> _documentViewerBody(
    BuildContext context,
    AppLocalizations l10n,
  ) {
    final patientId = (widget.payload['patientId'] as num?)?.toInt();
    final documentId = (widget.payload['documentId'] as num?)?.toInt();
    final title = intelligenceDocumentLabel(
      l10n,
      widget.payload['title']?.toString(),
    );
    final err = widget.payload['error']?.toString();
    if (patientId == null || documentId == null) {
      return [
        Text(
          localizeIntelligenceHint(
            l10n,
            err ?? widget.payload['spokenHint']?.toString() ?? '',
          ),
          style: AppDesignSystem.body2(context),
        ),
      ];
    }
    return [
      IntelligenceInlineDocumentViewer(
        patientId: '$patientId',
        documentId: '$documentId',
        title: title,
      ),
    ];
  }

  List<Widget> _dentalChartBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final err = widget.payload['error']?.toString();
    final hasDoc =
        widget.payload['documentation'] is Map ||
        widget.payload['toothServiceCounts'] is Map;
    if (err != null && !hasDoc) {
      return [
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ?? err,
          ),
          style: AppDesignSystem.body2(context),
        ),
      ];
    }
    return [
      IntelligenceInlineDentalChart(brand: brand, payload: widget.payload),
    ];
  }

  List<Widget> _freeSlotsBody(BuildContext context, AppLocalizations l10n) {
    final slots = (widget.payload['slots'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final dateLabel = intelligenceFieldLabel(
      l10n,
      isoDate: widget.payload['date']?.toString(),
      fallback:
          widget.payload['dateLabel']?.toString() ??
          widget.payload['date']?.toString(),
    );
    return [
      if (dateLabel.isNotEmpty)
        Text(
          dateLabel,
          style: AppDesignSystem.body2(
            context,
          ).copyWith(fontWeight: FontWeight.w600),
        ),
      const SizedBox(height: 8),
      if (slots.isEmpty)
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ??
                l10n.translate('intelNoFreeSlots'),
          ),
          style: AppDesignSystem.body2(context),
        )
      else
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final s in slots.take(24))
              Chip(
                label: Text('${s['startLocal'] ?? ''}'),
                visualDensity: VisualDensity.compact,
                backgroundColor: Colors.white,
                side: BorderSide(
                  color: Theme.of(
                    context,
                  ).colorScheme.primary.withValues(alpha: 0.35),
                ),
              ),
          ],
        ),
      const SizedBox(height: 8),
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton(
          onPressed: () {
            ref.read(workspaceModeProvider.notifier).showClinic();
            ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar);
          },
          child: Text(l10n.translate('intelOpenCalendar')),
        ),
      ),
    ];
  }

  List<Widget> _scheduleBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final appts = (widget.payload['appointments'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    return [
      Text(
        '${l10n.translate('intelAppointmentsCount')}: ${widget.payload['count'] ?? appts.length}',
        style: AppDesignSystem.body2(context),
      ),
      const SizedBox(height: 8),
      if (appts.isEmpty)
        Text(
          l10n.translate('intelNoAppointmentsToday'),
          style: AppDesignSystem.body2(
            context,
          ).copyWith(color: AppDesignSystem.textSecondary),
        )
      else
        ...appts.take(8).map((a) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              children: [
                SizedBox(
                  width: 52,
                  child: Text(
                    '${a['startLocal'] ?? ''}',
                    style: AppDesignSystem.body2(
                      context,
                    ).copyWith(fontWeight: FontWeight.w600),
                  ),
                ),
                Expanded(
                  child: Text(
                    '${intelligencePatientLabel(l10n, a['patientName']?.toString())} · ${intelligenceStatusLabel(l10n, '${a['status'] ?? ''}')}',
                    style: AppDesignSystem.body2(context),
                  ),
                ),
              ],
            ),
          );
        }),
      const SizedBox(height: 8),
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton(
          onPressed: () {
            ref.read(workspaceModeProvider.notifier).showClinic();
            ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar);
          },
          child: Text(l10n.translate('intelOpenCalendar')),
        ),
      ),
    ];
  }

  List<Widget> _patientAppointmentsBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final appts = (widget.payload['appointments'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final rangeLabel = widget.payload['rangeLabel']?.toString();
    final rawPatient = widget.payload['patientName']?.toString();
    final patientName = intelligencePatientLabel(l10n, rawPatient);
    return [
      if (rawPatient != null && rawPatient.trim().isNotEmpty)
        Text(
          patientName,
          style: AppDesignSystem.body2(
            context,
          ).copyWith(fontWeight: FontWeight.w600),
        ),
      if (rangeLabel != null && rangeLabel.isNotEmpty) ...[
        const SizedBox(height: 4),
        Text(
          intelligenceRangeLabel(l10n, rangeLabel),
          style: AppDesignSystem.body2(
            context,
          ).copyWith(color: AppDesignSystem.textSecondary),
        ),
      ],
      const SizedBox(height: 8),
      Text(
        '${l10n.translate('intelAppointmentsCount')}: ${widget.payload['count'] ?? appts.length}',
        style: AppDesignSystem.body2(context),
      ),
      const SizedBox(height: 8),
      if (appts.isEmpty)
        Text(
          l10n.translate('intelNoPatientAppointmentsInRange'),
          style: AppDesignSystem.body2(
            context,
          ).copyWith(color: AppDesignSystem.textSecondary),
        )
      else
        ...appts.take(12).map((a) {
          final day = intelligenceDateLabel(
            l10n,
            isoDate: a['date']?.toString(),
            fallback: a['dateLabel']?.toString(),
          );
          final time = a['startLocal']?.toString() ?? '';
          final status = intelligenceStatusLabel(
            l10n,
            a['status']?.toString() ?? '',
          );
          final rowPatient = intelligencePatientLabel(
            l10n,
            a['patientName']?.toString(),
          );
          final headerName = widget.payload['patientName']?.toString() ?? '';
          final statusText = headerName.trim().isEmpty && rowPatient.isNotEmpty
              ? '$rowPatient · $status'
              : status;
          return Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                SizedBox(
                  width: 100,
                  child: Text(
                    day,
                    style: AppDesignSystem.body2(
                      context,
                    ).copyWith(fontWeight: FontWeight.w600),
                  ),
                ),
                SizedBox(
                  width: 48,
                  child: Text(time, style: AppDesignSystem.body2(context)),
                ),
                Expanded(
                  child: Text(
                    statusText,
                    style: AppDesignSystem.body2(
                      context,
                    ).copyWith(color: AppDesignSystem.textSecondary),
                  ),
                ),
              ],
            ),
          );
        }),
      const SizedBox(height: 8),
      Align(
        alignment: Alignment.centerLeft,
        child: TextButton(
          onPressed: () {
            ref.read(workspaceModeProvider.notifier).showClinic();
            ref.read(shellProvider.notifier).setTab(DoctorShellTab.calendar);
          },
          child: Text(l10n.translate('intelOpenCalendar')),
        ),
      ),
    ];
  }

  List<Widget> _searchBody(BuildContext context, AppLocalizations l10n) {
    final matches = (widget.payload['matches'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final fuzzy = widget.payload['fuzzy'] == true;
    final query = widget.payload['query']?.toString() ?? '';
    if (matches.isEmpty) {
      final hint = widget.payload['spokenHint']?.toString() ?? '';
      var body = hint.isEmpty
          ? l10n.translate('intelNoPatientsMatched')
          : localizeIntelligenceHint(l10n, hint);
      if (body.contains('No patients matched') && query.isNotEmpty) {
        body = localizeIntelligenceHint(
          l10n,
          'No patients matched "$query" in your roster.',
        );
      }
      return [
        Text(body, style: AppDesignSystem.body2(context)),
      ];
    }
    return [
      if (fuzzy) ...[
        Text(
          query.isEmpty
              ? l10n.translate('intelDidYouMean')
              : l10n
                    .translate('intelNoExactMatchFor')
                    .replaceAll('{query}', query),
          style: AppDesignSystem.body2(
            context,
          ).copyWith(fontWeight: FontWeight.w600),
        ),
        const SizedBox(height: 8),
      ],
      ...matches.map((m) {
        return Padding(
          padding: const EdgeInsets.only(bottom: 6),
          child: Text(
            '${intelligencePatientLabel(l10n, m['patientName']?.toString())}'
            '${(m['phone'] as String?)?.isNotEmpty == true ? ' · ${m['phone']}' : ''}',
            style: AppDesignSystem.body2(context),
          ),
        );
      }),
    ];
  }

  List<Widget> _proposeBookBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final patientName = intelligencePatientLabel(
      l10n,
      widget.payload['patientName']?.toString(),
    );
    final dateLabel = intelligenceFieldLabel(
      l10n,
      isoDate: widget.payload['date']?.toString(),
      fallback:
          widget.payload['dateLabel']?.toString() ??
          widget.payload['date']?.toString(),
    );
    final slots = (widget.payload['slots'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final preferred = widget.payload['preferredSlot'] is Map
        ? Map<String, dynamic>.from(widget.payload['preferredSlot'] as Map)
        : null;
    final preferredLabel = preferred?['startLocal']?.toString();
    final err = widget.payload['error']?.toString();
    final done = _status != null;
    final serverWorkflow = widget.payload['workflowId'] != null;

    return [
      _kv(context, l10n.translate('intelPatient'), patientName),
      _kv(
        context,
        l10n.translate('intelDate'),
        dateLabel.isEmpty ? '—' : dateLabel,
      ),
      _kv(
        context,
        l10n.translate('intelTime'),
        preferredLabel ??
            (slots.isEmpty
                ? l10n.translate('intelTimeNotSelected')
                : l10n.translate('intelChooseTime')),
      ),
      if (err != null) ...[
        const SizedBox(height: 6),
        Text(
          localizeIntelligenceHint(l10n, err),
          style: AppDesignSystem.caption(context),
        ),
      ],
      const SizedBox(height: 10),
      if (slots.isEmpty)
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ??
                l10n.translate('intelNoFreeSlots'),
          ),
          style: AppDesignSystem.body2(context),
        )
      else if (preferred != null) ...[
        if (!done && serverWorkflow)
          Text(
            localizeIntelligenceHint(
              l10n,
              widget.payload['spokenHint']?.toString() ??
                  'Confirm this exact booking with Shifa.',
            ),
            style: AppDesignSystem.body2(context),
          ),
        if (!done && !serverWorkflow)
          FilledButton(
            onPressed: _busy ? null : () => _bookSlot(preferred),
            child: _busy
                ? const SizedBox(
                    width: 18,
                    height: 18,
                    child: CircularProgressIndicator(strokeWidth: 2),
                  )
                : Text(
                    l10n
                        .translate('intelConfirmTime')
                        .replaceAll('{time}', preferredLabel ?? ''),
                  ),
          ),
        if (!done && !serverWorkflow) ...[
          const SizedBox(height: 8),
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton(
              onPressed: _busy
                  ? null
                  : () => setState(() => _showOtherTimes = !_showOtherTimes),
              child: Text(
                _showOtherTimes
                    ? l10n.translate('intelHideOtherTimes')
                    : l10n.translate('intelOtherTimes'),
              ),
            ),
          ),
        ],
        if (_showOtherTimes && !done && !serverWorkflow)
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final s in slots)
                if (s['startAt'] != preferred['startAt'])
                  OutlinedButton(
                    onPressed: _busy ? null : () => _bookSlot(s),
                    child: Text('${s['startLocal'] ?? ''}'),
                  ),
            ],
          ),
      ] else
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final s in slots)
              OutlinedButton(
                onPressed: _busy || _status != null || serverWorkflow
                    ? null
                    : () => _bookSlot(s),
                child: Text('${s['startLocal'] ?? ''}'),
              ),
          ],
        ),
      if (!done) ...[
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            onPressed: _busy
                ? null
                : () {
                    setState(() => _dismissed = true);
                    widget.onDismiss?.call();
                  },
            child: Text(l10n.translate('cancel')),
          ),
        ),
      ],
    ];
  }

  List<Widget> _proposeMoveBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final patientName = intelligencePatientLabel(
      l10n,
      widget.payload['patientName']?.toString(),
    );
    final current = intelligenceDateTimeLabel(
      l10n,
      '${widget.payload['currentDateLabel'] ?? ''} ${widget.payload['currentStartLocal'] ?? ''}',
    );
    final slots = (widget.payload['slots'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final preferred = widget.payload['preferredSlot'] is Map
        ? Map<String, dynamic>.from(widget.payload['preferredSlot'] as Map)
        : null;
    final preferredLabel = preferred?['startLocal']?.toString();
    final done = _status != null;
    final serverWorkflow = widget.payload['workflowId'] != null;

    return [
      _kv(context, l10n.translate('intelPatient'), patientName),
      _kv(
        context,
        l10n.translate('intelCurrentSlot'),
        current.isEmpty ? '—' : current,
      ),
      _kv(context, l10n.translate('intelNewDate'), () {
        final label = intelligenceFieldLabel(
          l10n,
          isoDate: widget.payload['date']?.toString(),
          fallback:
              widget.payload['dateLabel']?.toString() ??
              widget.payload['date']?.toString(),
        );
        return label.isEmpty ? '—' : label;
      }()),
      if (preferredLabel != null)
        _kv(context, l10n.translate('intelTime'), preferredLabel),
      const SizedBox(height: 10),
      if (slots.isEmpty)
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ??
                l10n.translate('intelNoFreeSlots'),
          ),
          style: AppDesignSystem.body2(context),
        )
      else if (preferred != null) ...[
        if (!done && serverWorkflow)
          Text(
            localizeIntelligenceHint(
              l10n,
              widget.payload['spokenHint']?.toString() ??
                  'Please confirm this exact reschedule in the conversation.',
            ),
            style: AppDesignSystem.body2(context),
          )
        else if (!done)
          FilledButton(
            onPressed: _busy ? null : () => _moveSlot(preferred),
            child: Text(
              l10n
                  .translate('intelConfirmTime')
                  .replaceAll('{time}', preferredLabel ?? ''),
            ),
          ),
      ] else
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final s in slots)
              OutlinedButton(
                onPressed: _busy || _status != null ? null : () => _moveSlot(s),
                child: Text('${s['startLocal'] ?? ''}'),
              ),
          ],
        ),
      if (!done) ...[
        const SizedBox(height: 8),
        Align(
          alignment: Alignment.centerLeft,
          child: TextButton(
            onPressed: _busy
                ? null
                : () {
                    setState(() => _dismissed = true);
                    widget.onDismiss?.call();
                  },
            child: Text(l10n.translate('cancel')),
          ),
        ),
      ],
    ];
  }

  List<Widget> _draftFactsBody(
    BuildContext context,
    Color brand,
    AppLocalizations l10n,
  ) {
    final facts = (widget.payload['facts'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    final appointmentId = (widget.payload['appointmentId'] as num?)?.toInt();

    return [
      _kv(
        context,
        l10n.translate('intelPatient'),
        intelligencePatientLabel(
          l10n,
          widget.payload['patientName']?.toString(),
        ),
      ),
      const SizedBox(height: 8),
      if (facts.isEmpty)
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ??
                l10n.translate('intelNoDraftFacts'),
          ),
          style: AppDesignSystem.body2(context),
        )
      else ...[
        ...facts.map((f) {
          return Padding(
            padding: const EdgeInsets.only(bottom: 6),
            child: Text(
              '${intelligenceFactTypeLabel(l10n, '${f['factType'] ?? ''}')}: ${f['text'] ?? ''}',
              style: AppDesignSystem.body2(context),
            ),
          );
        }),
        const SizedBox(height: 10),
        Row(
          children: [
            FilledButton(
              onPressed: _busy || _status != null || appointmentId == null
                  ? null
                  : () => _commitFacts(appointmentId, facts),
              child: _busy
                  ? const SizedBox(
                      width: 16,
                      height: 16,
                      child: CircularProgressIndicator(strokeWidth: 2),
                    )
                  : Text(l10n.translate('intelConfirmDocument')),
            ),
            const SizedBox(width: 8),
            TextButton(
              onPressed: _busy
                  ? null
                  : () {
                      setState(() => _dismissed = true);
                      widget.onDismiss?.call();
                    },
              child: Text(l10n.translate('cancel')),
            ),
          ],
        ),
      ],
    ];
  }

  List<Widget> _listNameCountBody(
    BuildContext context,
    AppLocalizations l10n,
    String key,
  ) {
    final rows = (widget.payload[key] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    if (rows.isEmpty) {
      return [
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ?? '—',
          ),
          style: AppDesignSystem.body2(context),
        ),
      ];
    }
    return rows.map((r) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(
          '${intelligencePatientLabel(l10n, (r['patientName'] ?? r['label'])?.toString())} · ${r['visitCount'] ?? r['count'] ?? ''}',
          style: AppDesignSystem.body2(context),
        ),
      );
    }).toList();
  }

  List<Widget> _patternsBody(BuildContext context, AppLocalizations l10n) {
    final rows = (widget.payload['patterns'] as List? ?? const [])
        .whereType<Map>()
        .map((e) => Map<String, dynamic>.from(e))
        .toList();
    if (rows.isEmpty) {
      return [
        Text(
          localizeIntelligenceHint(
            l10n,
            widget.payload['spokenHint']?.toString() ?? '—',
          ),
          style: AppDesignSystem.body2(context),
        ),
      ];
    }
    return rows.map((r) {
      return Padding(
        padding: const EdgeInsets.only(bottom: 6),
        child: Text(
          '${r['label'] ?? r['key'] ?? ''} · ${r['count'] ?? ''}',
          style: AppDesignSystem.body2(context),
        ),
      );
    }).toList();
  }

  List<Widget> _briefBody(BuildContext context, AppLocalizations l10n) {
    final briefing = widget.payload['briefing']?.toString();
    final hint = widget.payload['spokenHint']?.toString();
    final text = (briefing != null && briefing.trim().isNotEmpty)
        ? briefing
        : (hint ?? '');
    return [
      Text(
        localizeIntelligenceHint(l10n, text),
        style: AppDesignSystem.body2(context),
      ),
    ];
  }

  Widget _kv(BuildContext context, String k, String v) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 110,
            child: Text(
              k,
              style: AppDesignSystem.caption(
                context,
              ).copyWith(color: AppDesignSystem.textSecondary),
            ),
          ),
          Expanded(child: Text(v, style: AppDesignSystem.body2(context))),
        ],
      ),
    );
  }

  Future<void> _bookSlot(Map<String, dynamic> slot) async {
    final patientId = (widget.payload['patientId'] as num?)?.toInt();
    final startAt = slot['startAt']?.toString();
    final slotMinutes = (slot['slotMinutes'] as num?)?.toInt() ?? 30;
    if (patientId == null || startAt == null || startAt.isEmpty) {
      setState(
        () => _error = AppLocalizations.of(
          context,
        )!.translate('intelMissingPatientOrSlot'),
      );
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final client = ref.read(apiClientProvider);
      final body = <String, dynamic>{
        'startAt': startAt,
        'slotMinutes': slotMinutes,
        'patientId': patientId,
        'location': (widget.payload['isVideo'] == true)
            ? 'Video Consultation'
            : (slot['locationLabel']?.toString() ?? 'Clinic'),
        'reason': widget.payload['reason'],
        'isVideo': widget.payload['isVideo'] == true,
      };
      final locId = (slot['locationId'] as num?)?.toInt();
      if (locId != null && widget.payload['isVideo'] != true) {
        body['locationId'] = locId;
      }
      final res = await client.post('/api/schedule/book', body);
      if (!mounted) return;
      if (res.statusCode == 200 || res.statusCode == 201) {
        setState(() {
          _status = AppLocalizations.of(context)!.translate('intelBookedOk');
          _busy = false;
        });
        unawaited(_refreshHomeAfterScheduleChange(startAt));
        return;
      }
      setState(() {
        _busy = false;
        _error = res.statusCode == 409
            ? AppLocalizations.of(context)!.translate('intelSlotConflict')
            : AppLocalizations.of(
                context,
              )!.translate('intelBookingFailed').replaceAll(
                '{code}',
                '${res.statusCode}',
              );
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = e.toString();
      });
    }
  }

  Future<void> _refreshHomeAfterScheduleChange(String? startAtIso) async {
    DateTime? appointmentDay;
    if (startAtIso != null && startAtIso.isNotEmpty) {
      try {
        appointmentDay = DateTime.parse(startAtIso).toLocal();
      } catch (_) {}
    }
    try {
      ref.invalidate(doctorNotificationsProvider);
      ref.invalidate(doctorNotificationsUnreadCountProvider);
    } catch (_) {}
    await invalidateAppointmentRelatedProviders(
      ref,
      appointmentDay: appointmentDay,
    );
  }

  Future<void> _moveSlot(Map<String, dynamic> slot) async {
    final appointmentId = (widget.payload['appointmentId'] as num?)?.toInt();
    final startAt = slot['startAt']?.toString();
    final slotMinutes = (slot['slotMinutes'] as num?)?.toInt() ?? 30;
    if (appointmentId == null || startAt == null) {
      setState(
        () => _error = AppLocalizations.of(
          context,
        )!.translate('intelMissingAppointmentOrSlot'),
      );
      return;
    }
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final client = ref.read(apiClientProvider);
      final res = await client.put('/api/appointments/$appointmentId/change', {
        'startAt': startAt,
        'slotMinutes': slotMinutes,
      });
      if (!mounted) return;
      if (res.statusCode == 200 || res.statusCode == 204) {
        setState(() {
          _status = AppLocalizations.of(context)!.translate('intelMovedOk');
          _busy = false;
        });
        unawaited(_refreshHomeAfterScheduleChange(startAt));
        return;
      }
      setState(() {
        _busy = false;
        _error = res.statusCode == 409
            ? AppLocalizations.of(context)!.translate('intelSlotConflict')
            : AppLocalizations.of(
                context,
              )!.translate('intelRescheduleFailed').replaceAll(
                '{code}',
                '${res.statusCode}',
              );
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = e.toString();
      });
    }
  }

  Future<void> _commitFacts(
    int appointmentId,
    List<Map<String, dynamic>> facts,
  ) async {
    setState(() {
      _busy = true;
      _error = null;
    });
    try {
      final api = ref.read(aiApiProvider);
      final ids = facts
          .map((f) => (f['factId'] as num?)?.toInt())
          .whereType<int>()
          .toList();
      final result = await api.commitVisitFacts(
        appointmentId: appointmentId,
        acceptFactIds: ids.isEmpty ? null : ids,
      );
      final patientId = (widget.payload['patientId'] as num?)?.toInt();
      ref.invalidate(visitFactsForAppointmentProvider(appointmentId));
      ref.invalidate(consultationNotesForAppointmentProvider('$appointmentId'));
      if (patientId != null) {
        ref.invalidate(patientTimelineProvider(patientId));
      }
      if (!mounted) return;
      setState(() {
        _busy = false;
        _status = AppLocalizations.of(context)!
            .translate('intelDocumentedFacts')
            .replaceAll('{count}', '${result.committedFacts.length}');
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _busy = false;
        _error = e.toString();
      });
    }
  }
}
