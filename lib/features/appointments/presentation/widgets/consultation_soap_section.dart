import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/doctor_speech_mic_button.dart';

/// Expanding SOAP fields; content is merged into the PDF via [composeConsultationNotesPdf].
class ConsultationSoapSection extends ConsumerWidget {
  const ConsultationSoapSection({
    super.key,
    required this.l10n,
    required this.subjective,
    required this.objective,
    required this.assessment,
    required this.plan,
    this.onTranscriptAppended,
  });

  final AppLocalizations l10n;
  final TextEditingController subjective;
  final TextEditingController objective;
  final TextEditingController assessment;
  final TextEditingController plan;
  final VoidCallback? onTranscriptAppended;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    return ExpansionTile(
      tilePadding: EdgeInsets.zero,
      childrenPadding: const EdgeInsets.only(bottom: 4),
      maintainState: true,
      title: Text(
        l10n.soapNotesSectionTitle,
        style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
      ),
      subtitle: Text(
        l10n.soapNotesSectionSubtitle,
        style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
      ),
      children: [
        _soapField(context, l10n.soapSubjective, subjective),
        _soapField(context, l10n.soapObjective, objective),
        _soapField(context, l10n.soapAssessment, assessment),
        _soapField(context, l10n.soapPlan, plan),
      ],
    );
  }

  Widget _soapField(BuildContext context, String label, TextEditingController c) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 10),
      child: TextField(
        controller: c,
        maxLines: 3,
        minLines: 1,
        textCapitalization: TextCapitalization.sentences,
        decoration: InputDecoration(
          labelText: label,
          alignLabelWithHint: true,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          isDense: true,
          suffixIcon: DoctorSpeechMicButton(
            controller: c,
            onTranscriptAppended: onTranscriptAppended,
          ),
        ),
      ),
    );
  }
}

/// Builds the text block saved to the appointment PDF (SOAP blocks + free-form notes).
String composeConsultationNotesPdf({
  required AppLocalizations l10n,
  required TextEditingController soapSubjective,
  required TextEditingController soapObjective,
  required TextEditingController soapAssessment,
  required TextEditingController soapPlan,
  required TextEditingController freeNotes,
}) {
  final soapParts = <String>[];
  void add(String label, TextEditingController c) {
    final t = c.text.trim();
    if (t.isNotEmpty) soapParts.add('$label\n$t');
  }

  add(l10n.soapSubjective, soapSubjective);
  add(l10n.soapObjective, soapObjective);
  add(l10n.soapAssessment, soapAssessment);
  add(l10n.soapPlan, soapPlan);

  final soapBlock = soapParts.join('\n\n');
  final free = freeNotes.text.trim();
  return [
    if (soapBlock.isNotEmpty) soapBlock,
    if (free.isNotEmpty) free,
  ].join('\n\n');
}
