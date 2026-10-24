import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api.dart';
import 'package:shifa_doc_app_v1/core/api/ai_api_provider.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/theme/app_design_system.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/intelligence/presentation/intelligence_card_text.dart';

final visitFactsForAppointmentProvider =
    FutureProvider.autoDispose.family<VisitFactsBundle, int>((ref, appointmentId) async {
  final api = ref.watch(aiApiProvider);
  return api.fetchVisitFactsForAppointment(appointmentId);
});

final patientTimelineProvider =
    FutureProvider.autoDispose.family<VisitFactsBundle, int>((ref, patientId) async {
  final api = ref.watch(aiApiProvider);
  return api.fetchPatientTimeline(patientId);
});

/// Draft visit-fact cards after scribe, with Document everything commit.
class VisitFactsPanel extends ConsumerStatefulWidget {
  const VisitFactsPanel({
    super.key,
    required this.appointmentId,
    this.patientId,
  });

  final int? appointmentId;
  final int? patientId;

  @override
  ConsumerState<VisitFactsPanel> createState() => _VisitFactsPanelState();
}

class _VisitFactsPanelState extends ConsumerState<VisitFactsPanel> {
  final Set<int> _rejected = {};
  bool _committing = false;
  String? _commitMessage;

  Future<void> _documentEverything(List<VisitFactDto> kept) async {
    final appointmentId = widget.appointmentId;
    if (appointmentId == null || kept.isEmpty) return;
    setState(() {
      _committing = true;
      _commitMessage = null;
    });
    try {
      final api = ref.read(aiApiProvider);
      final acceptIds =
          kept.where((f) => !_rejected.contains(f.id)).map((f) => f.id).toList();
      final rejectIds = kept
          .where((f) => _rejected.contains(f.id))
          .map((f) => f.id)
          .toList();
      final result = await api.commitVisitFacts(
        appointmentId: appointmentId,
        acceptFactIds: acceptIds,
        rejectFactIds: rejectIds,
      );
      ref.invalidate(visitFactsForAppointmentProvider(appointmentId));
      if (widget.patientId != null) {
        ref.invalidate(patientTimelineProvider(widget.patientId!));
      }
      if (!mounted) return;
      setState(() {
        _commitMessage = localizeIntelligenceHint(
          AppLocalizations.of(context)!,
          'Documented ${result.committedFacts.length} facts'
          '${result.createdTaskIds.isEmpty ? '' : ' and drafted ${result.createdTaskIds.length} medication tasks'}'
          '. They are on the patient timeline.',
        );
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _commitMessage = e.toString());
    } finally {
      if (mounted) setState(() => _committing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final appointmentId = widget.appointmentId;
    if (appointmentId == null) return const SizedBox.shrink();

    final async = ref.watch(visitFactsForAppointmentProvider(appointmentId));
    final l10n = AppLocalizations.of(context)!;
    final brand = Theme.of(context).colorScheme.primary;

    return async.when(
      loading: () => const Padding(
        padding: EdgeInsets.symmetric(vertical: 8),
        child: LinearProgressIndicator(minHeight: 2),
      ),
      error: (_, __) => const SizedBox.shrink(),
      data: (bundle) {
        final kept = bundle.keptDrafts;
        final dropped = bundle.dropped;
        final committed = bundle.facts.where((f) => f.isCommitted).toList();
        if (kept.isEmpty && dropped.isEmpty && committed.isEmpty) {
          return const SizedBox.shrink();
        }

        return Container(
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: Colors.white.withValues(alpha: 0.95),
            borderRadius: BorderRadius.circular(16),
            border: Border.all(color: brand.withValues(alpha: 0.2)),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(
                      _vf(l10n, 'visitFactCards', 'Visit facts from the call'),
                      style: AppDesignSystem.h2(context),
                    ),
                  ),
                  IconButton(
                    tooltip: l10n.translate('refresh'),
                    onPressed: () => ref.invalidate(
                      visitFactsForAppointmentProvider(appointmentId),
                    ),
                    icon: Icon(Icons.refresh, color: brand, size: 20),
                  ),
                ],
              ),
              Text(
                _vf(
                  l10n,
                  'visitFactCardsHint',
                  'Only what was spoken. Review, then document everything.',
                ),
                style: AppDesignSystem.caption(context),
              ),
              const SizedBox(height: 12),
              ...kept.map(
                (f) => _FactCard(
                  fact: f,
                  brand: brand,
                  rejected: _rejected.contains(f.id),
                  onToggleReject: () {
                    setState(() {
                      if (_rejected.contains(f.id)) {
                        _rejected.remove(f.id);
                      } else {
                        _rejected.add(f.id);
                      }
                    });
                  },
                ),
              ),
              if (dropped.isNotEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  _vf(l10n, 'droppedFacts', 'Dropped (not said)'),
                  style: AppDesignSystem.caption(context).copyWith(
                    fontWeight: FontWeight.w700,
                    color: Colors.orange.shade800,
                  ),
                ),
                const SizedBox(height: 6),
                ...dropped.map(
                  (f) => _FactCard(
                    fact: f,
                    brand: Colors.orange.shade700,
                    rejected: true,
                    readOnly: true,
                  ),
                ),
              ],
              if (bundle.patterns.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text(
                  l10n.translate('intelCardPatterns'),
                  style: AppDesignSystem.caption(context).copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                ...bundle.patterns.take(3).map(
                      (p) => Padding(
                        padding: const EdgeInsets.only(bottom: 6),
                        child: Text(
                          '${p.label} · ${p.count}×',
                          style: AppDesignSystem.body2(context),
                        ),
                      ),
                    ),
              ],
              if (kept.isNotEmpty) ...[
                const SizedBox(height: 12),
                ShifaPrimaryButton(
                  label: _committing
                      ? _vf(l10n, 'documenting', 'Documenting…')
                      : _vf(l10n, 'documentEverything', 'Document everything'),
                  onPressed: _committing
                      ? null
                      : () => _documentEverything(kept),
                  width: ButtonWidth.fill,
                ),
              ],
              if (committed.isNotEmpty && kept.isEmpty) ...[
                const SizedBox(height: 8),
                Text(
                  _vf(
                    l10n,
                    'factsAlreadyDocumented',
                    'These visit facts are already in the patient timeline.',
                  ),
                  style: AppDesignSystem.caption(context),
                ),
              ],
              if (_commitMessage != null) ...[
                const SizedBox(height: 8),
                Text(
                  _commitMessage!,
                  style: AppDesignSystem.caption(context).copyWith(
                    color: brand,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ],
            ],
          ),
        );
      },
    );
  }
}

class _FactCard extends StatelessWidget {
  const _FactCard({
    required this.fact,
    required this.brand,
    this.rejected = false,
    this.readOnly = false,
    this.onToggleReject,
  });

  final VisitFactDto fact;
  final Color brand;
  final bool rejected;
  final bool readOnly;
  final VoidCallback? onToggleReject;

  IconData get _icon => switch (fact.factType) {
        'MEDICATION' => Icons.medication_outlined,
        'DIAGNOSIS' => Icons.health_and_safety_outlined,
        'THERAPY' => Icons.healing_outlined,
        'SYMPTOM' => Icons.sick_outlined,
        _ => Icons.notes_outlined,
      };

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final dimmed = rejected || fact.isDropped;
    return Opacity(
      opacity: dimmed ? 0.55 : 1,
      child: Container(
        margin: const EdgeInsets.only(bottom: 8),
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: AppDesignSystem.backgroundSecondary,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: brand.withValues(alpha: 0.2)),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(_icon, size: 18, color: brand),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    '${intelligenceFactTypeLabel(l10n, fact.factType)} · ${fact.text}',
                    style: AppDesignSystem.body1(context).copyWith(
                      fontWeight: FontWeight.w600,
                      decoration:
                          dimmed ? TextDecoration.lineThrough : null,
                    ),
                  ),
                ),
                if (!readOnly && onToggleReject != null)
                  IconButton(
                    tooltip: AppLocalizations.of(context)!.translate(
                      rejected ? 'intelKeep' : 'intelReject',
                    ),
                    onPressed: onToggleReject,
                    icon: Icon(
                      rejected ? Icons.undo : Icons.close,
                      size: 18,
                      color: rejected ? brand : Colors.red.shade400,
                    ),
                  ),
              ],
            ),
            if (fact.quote != null && fact.quote!.isNotEmpty) ...[
              const SizedBox(height: 6),
              Text(
                '"${fact.quote}"',
                style: AppDesignSystem.caption(context).copyWith(
                  fontStyle: FontStyle.italic,
                ),
              ),
            ],
            if (fact.scheduleHint != null && fact.scheduleHint!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                AppLocalizations.of(context)!
                    .translate('intelScheduleHint')
                    .replaceAll('{hint}', fact.scheduleHint!),
                style: AppDesignSystem.caption(context),
              ),
            ],
            if (fact.dropReason != null && fact.dropReason!.isNotEmpty) ...[
              const SizedBox(height: 4),
              Text(
                fact.dropReason!,
                style: AppDesignSystem.caption(context).copyWith(
                  color: Colors.orange.shade800,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

/// Committed clinical timeline on the patient chart.
class PatientVisitTimelineCard extends ConsumerWidget {
  const PatientVisitTimelineCard({
    super.key,
    required this.patientId,
    required this.brand,
  });

  final int patientId;
  final Color brand;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(patientTimelineProvider(patientId));
    final l10n = AppLocalizations.of(context)!;

    return async.when(
      loading: () => const SizedBox.shrink(),
      error: (_, __) => const SizedBox.shrink(),
      data: (bundle) {
        if (bundle.facts.isEmpty && bundle.patterns.isEmpty) {
          return const SizedBox.shrink();
        }
        return Container(
          width: double.infinity,
          padding: const EdgeInsets.all(16),
          decoration: BoxDecoration(
            color: Colors.white,
            borderRadius: BorderRadius.circular(AppDesignSystem.cardRadiusSm),
            border: Border.all(
              color: AppDesignSystem.border.withValues(alpha: 0.7),
            ),
          ),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(Icons.timeline, color: brand, size: 20),
                  const SizedBox(width: 8),
                  Text(
                    _vf(l10n, 'clinicalTimeline', 'Clinical timeline'),
                    style: AppDesignSystem.h2(context),
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                _vf(
                  l10n,
                  'clinicalTimelineHint',
                  'Accepted visit facts, step by step. Sourced from what was spoken.',
                ),
                style: AppDesignSystem.caption(context),
              ),
              const SizedBox(height: 12),
              ...bundle.facts.map((f) {
                return Padding(
                  padding: const EdgeInsets.only(bottom: 10),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Container(
                        width: 10,
                        height: 10,
                        margin: const EdgeInsets.only(top: 5),
                        decoration: BoxDecoration(
                          color: brand,
                          shape: BoxShape.circle,
                        ),
                      ),
                      const SizedBox(width: 10),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(
                              '${intelligenceFactTypeLabel(l10n, f.factType)} · ${f.text}',
                              style: AppDesignSystem.body1(context).copyWith(
                                fontWeight: FontWeight.w600,
                              ),
                            ),
                            if (f.quote != null)
                              Text(
                                '"${f.quote}"',
                                style: AppDesignSystem.caption(context)
                                    .copyWith(fontStyle: FontStyle.italic),
                              ),
                            Text(
                              f.committedAt ?? f.createdAt,
                              style: AppDesignSystem.caption(context),
                            ),
                          ],
                        ),
                      ),
                    ],
                  ),
                );
              }),
              if (bundle.patterns.isNotEmpty) ...[
                const Divider(height: 20),
                Text(
                  l10n.translate('intelCardPatterns'),
                  style: AppDesignSystem.caption(context).copyWith(
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(height: 6),
                ...bundle.patterns.take(4).map(
                      (p) => Padding(
                        padding: const EdgeInsets.only(bottom: 4),
                        child: Text(
                          '${p.label} · ${p.count}×',
                          style: AppDesignSystem.body2(context),
                        ),
                      ),
                    ),
              ],
            ],
          ),
        );
      },
    );
  }
}

String _vf(AppLocalizations l10n, String key, String fallback) {
  final v = l10n.translate(key);
  return v == key ? fallback : v;
}
