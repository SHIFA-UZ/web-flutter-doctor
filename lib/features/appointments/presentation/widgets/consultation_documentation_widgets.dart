// Shared UI for in-person and video-call consultation documentation panels.
import 'package:flutter/material.dart';
import 'package:shifa_doc_app_v1/core/localization/app_localizations.dart';
import 'package:shifa_doc_app_v1/core/widgets/shifa_button.dart';
import 'package:shifa_doc_app_v1/features/appointments/domain/appointment_models.dart';
import 'package:shifa_doc_app_v1/features/patients/domain/patient_models.dart';
import 'package:url_launcher/url_launcher_string.dart';

/// Ordered clinical buckets for grouping patient documents in the consultation UI.
const List<String> kDocumentSectionOrder = [
  'laboratory',
  'imaging',
  'clinical',
  'forms',
  'private',
  'other',
  'uncategorized',
];

String clinicalSectionBucket(String? category) {
  if (category == null || category.isEmpty) return 'uncategorized';
  const lab = <String>{
    'BLOOD_TEST',
    'URINE_TEST',
    'STOOL_TEST',
    'LAB_RESULT',
    'BIOPSY',
    'PATHOLOGY',
  };
  const imaging = <String>{
    'MRI',
    'CT_SCAN',
    'XRAY',
    'ULTRASOUND',
    'MAMMOGRAPHY',
    'ECG',
    'EEG',
    'ENDOSCOPY',
    'IMAGING_OTHER',
  };
  const clinical = <String>{
    'PRESCRIPTION',
    'VACCINATION_RECORD',
    'DISCHARGE_SUMMARY',
    'REFERRAL',
    'HOSPITAL_REPORT',
    'ALLERGY_REPORT',
    'OTHER_MEDICAL',
  };
  const forms = <String>{
    'FORM_025_2',
    'APPOINTMENT_NOTE',
    'INTERNAL_NOTE',
    'REMOTE_TASK_DOCUMENT',
  };
  if (lab.contains(category)) return 'laboratory';
  if (imaging.contains(category)) return 'imaging';
  if (clinical.contains(category)) return 'clinical';
  if (forms.contains(category)) return 'forms';
  if (category == 'OTHER_PRIVATE') return 'private';
  return 'other';
}

class DocumentSectionGroup {
  DocumentSectionGroup({required this.bucket, required this.docs});
  final String bucket;
  final List<PatientDocument> docs;
}

List<DocumentSectionGroup> groupPatientDocuments(List<PatientDocument> docs) {
  final map = <String, List<PatientDocument>>{};
  for (final d in docs) {
    final b = clinicalSectionBucket(d.category);
    map.putIfAbsent(b, () => []).add(d);
  }
  for (final list in map.values) {
    list.sort((a, b) => b.date.compareTo(a.date));
  }
  final out = <DocumentSectionGroup>[];
  for (final key in kDocumentSectionOrder) {
    final list = map[key];
    if (list != null && list.isNotEmpty) {
      out.add(DocumentSectionGroup(bucket: key, docs: list));
    }
  }
  return out;
}

String sectionTitle(AppLocalizations l10n, String bucket) {
  switch (bucket) {
    case 'laboratory':
      return l10n.docSectionLaboratory;
    case 'imaging':
      return l10n.docSectionImaging;
    case 'clinical':
      return l10n.docSectionClinical;
    case 'forms':
      return l10n.docSectionForms;
    case 'private':
      return l10n.docSectionPrivate;
    case 'other':
      return l10n.docSectionOther;
    default:
      return l10n.docSectionUncategorized;
  }
}

IconData iconForDocumentTitle(String title) {
  final lower = title.toLowerCase();
  if (lower.endsWith('.pdf')) return Icons.picture_as_pdf_outlined;
  if (lower.endsWith('.jpg') ||
      lower.endsWith('.jpeg') ||
      lower.endsWith('.png') ||
      lower.endsWith('.webp') ||
      lower.endsWith('.gif')) {
    return Icons.image_outlined;
  }
  if (lower.endsWith('.doc') || lower.endsWith('.docx')) {
    return Icons.description_outlined;
  }
  if (lower.endsWith('.xls') || lower.endsWith('.xlsx')) {
    return Icons.table_chart_outlined;
  }
  return Icons.insert_drive_file_outlined;
}

int? ageFromBirthDate(DateTime? birthDate) {
  if (birthDate == null) return null;
  final now = DateTime.now();
  var age = now.year - birthDate.year;
  if (now.month < birthDate.month ||
      (now.month == birthDate.month && now.day < birthDate.day)) {
    age--;
  }
  return age < 0 ? null : age;
}

Color appointmentStatusColor(AppointmentStatus? status) {
  switch (status) {
    case AppointmentStatus.completed:
      return const Color(0xFF2E7D32);
    case AppointmentStatus.cancelled:
      return const Color(0xFFC62828);
    case AppointmentStatus.requested:
      return const Color(0xFFE65100);
    case AppointmentStatus.confirmed:
      return const Color(0xFF00897B);
    case AppointmentStatus.inProgress:
      return const Color(0xFF1565C0);
    default:
      return Colors.grey.shade700;
  }
}

String? appointmentStatusLabel(AppLocalizations l10n, AppointmentStatus? status) {
  switch (status) {
    case AppointmentStatus.requested:
      return l10n.appointmentStatusRequested;
    case AppointmentStatus.confirmed:
      return l10n.appointmentStatusConfirmed;
    case AppointmentStatus.cancelled:
      return l10n.appointmentStatusCancelled;
    case AppointmentStatus.completed:
      return l10n.appointmentStatusCompleted;
    case AppointmentStatus.inProgress:
      return l10n.appointmentStatusInProgress;
    default:
      return null;
  }
}

/// White consultation card with soft elevation (replaces flat bordered boxes).
class DocumentationSectionCard extends StatelessWidget {
  const DocumentationSectionCard({
    super.key,
    required this.title,
    required this.child,
    this.titleTrailing,
  });

  final String title;
  final Widget child;
  final Widget? titleTrailing;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: Colors.black.withValues(alpha: 0.06)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.06),
            blurRadius: 20,
            offset: const Offset(0, 8),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (titleTrailing != null)
            Row(
              children: [
                Text(
                  title,
                  style: const TextStyle(
                    fontSize: 17,
                    fontWeight: FontWeight.w700,
                    letterSpacing: -0.2,
                  ),
                ),
                const Spacer(),
                titleTrailing!,
              ],
            )
          else
            Text(
              title,
              style: const TextStyle(
                fontSize: 17,
                fontWeight: FontWeight.w700,
                letterSpacing: -0.2,
              ),
            ),
          const SizedBox(height: 12),
          Expanded(child: child),
        ],
      ),
    );
  }
}

class PatientDocumentTile extends StatelessWidget {
  const PatientDocumentTile(
    this.doc,
    this.brand, {
    super.key,
    this.patientId,
    this.onRequestAccess,
  });

  final PatientDocument doc;
  final Color brand;
  final String? patientId;
  final Future<void> Function()? onRequestAccess;

  @override
  Widget build(BuildContext context) {
    final date = doc.date;
    final dateStr =
        '${date.day.toString().padLeft(2, '0')}.'
        '${date.month.toString().padLeft(2, '0')}.'
        '${date.year}';
    final l10n = AppLocalizations.of(context)!;
    final isLocked = !doc.canView;
    final canOpen = doc.canView && doc.url != null && doc.url!.isNotEmpty;
    final fileIcon = iconForDocumentTitle(doc.title);
    final creator =
        doc.creatorLabel == 'Unknown'
            ? () {
                final t = l10n.translate('anotherUser');
                return (t.trim().isEmpty || t == 'anotherUser')
                    ? 'Another user'
                    : t;
              }()
            : doc.creatorLabel;

    String? categoryLabel;
    final cat = doc.category;
    if (cat != null && cat.isNotEmpty) {
      final key = 'documentCategory_$cat';
      final t = l10n.translate(key);
      categoryLabel =
          (t.trim().isNotEmpty && t.trim() != key) ? t : cat;
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 12),
      decoration: BoxDecoration(
        color: const Color(0xFFF8FAFA),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.black.withValues(alpha: 0.05)),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.03),
            blurRadius: 8,
            offset: const Offset(0, 2),
          ),
        ],
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(fileIcon, color: brand, size: 22),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (isLocked) ...[
                      Icon(
                        Icons.lock_outline,
                        size: 14,
                        color: Colors.grey.shade600,
                      ),
                      const SizedBox(width: 4),
                    ],
                    Expanded(
                      child: Text(
                        doc.title,
                        style: const TextStyle(
                          fontWeight: FontWeight.w600,
                          height: 1.25,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 4),
                Text(
                  dateStr,
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey.shade600,
                  ),
                ),
                if (categoryLabel != null) ...[
                  const SizedBox(height: 6),
                  Align(
                    alignment: Alignment.centerLeft,
                    child: Container(
                      padding: const EdgeInsets.symmetric(
                        horizontal: 8,
                        vertical: 2,
                      ),
                      decoration: BoxDecoration(
                        color: brand.withValues(alpha: 0.1),
                        borderRadius: BorderRadius.circular(999),
                      ),
                      child: Text(
                        categoryLabel,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.w600,
                          color: brand.darken(0.05),
                        ),
                      ),
                    ),
                  ),
                ],
                const SizedBox(height: 4),
                Text(
                  '${l10n.uploadedBy} $creator',
                  style: TextStyle(
                    fontSize: 11,
                    color: Colors.grey.shade600,
                  ),
                ),
              ],
            ),
          ),
          if (isLocked && patientId != null && onRequestAccess != null)
            TextButton.icon(
              onPressed: () async {
                try {
                  await onRequestAccess?.call();
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text(l10n.requestAccessSent)),
                    );
                  }
                } catch (e) {
                  if (context.mounted) {
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(
                        content: Text('${l10n.error}: $e'),
                        backgroundColor: Colors.red,
                      ),
                    );
                  }
                }
              },
              icon: const Icon(Icons.lock_open, size: 18),
              label: Text(l10n.requestAccess),
            ),
          IconButton.filledTonal(
            onPressed: canOpen
                ? () async {
                    final url = doc.url!;
                    try {
                      if (await canLaunchUrlString(url)) {
                        await launchUrlString(
                          url,
                          mode: LaunchMode.platformDefault,
                        );
                      } else if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(l10n.cannotOpenDocumentUrl),
                            backgroundColor: Colors.red,
                          ),
                        );
                      }
                    } catch (e) {
                      if (context.mounted) {
                        ScaffoldMessenger.of(context).showSnackBar(
                          SnackBar(
                            content: Text(
                              '${l10n.errorOpeningDocument}: $e',
                            ),
                            backgroundColor: Colors.red,
                          ),
                        );
                      }
                    }
                  }
                : null,
            icon: const Icon(Icons.open_in_browser),
            style: ButtonStyle(
              backgroundColor: WidgetStatePropertyAll(
                brand.withValues(alpha: 0.15),
              ),
              foregroundColor: WidgetStatePropertyAll(brand),
            ),
          ),
        ],
      ),
    );
  }
}

extension on Color {
  Color darken(double amount) {
    final hsl = HSLColor.fromColor(this);
    final l = (hsl.lightness - amount).clamp(0.0, 1.0);
    return hsl.withLightness(l).toColor();
  }
}

class GroupedPatientDocumentsList extends StatelessWidget {
  const GroupedPatientDocumentsList({
    super.key,
    required this.documents,
    required this.brand,
    required this.patientId,
    this.onRequestAccess,
  });

  final List<PatientDocument> documents;
  final Color brand;
  final String? patientId;
  final Future<void> Function(PatientDocument doc)? onRequestAccess;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    if (documents.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.folder_open_outlined, size: 40, color: Colors.grey.shade400),
              const SizedBox(height: 12),
              Text(
                l10n.noDocuments,
                style: TextStyle(
                  fontWeight: FontWeight.w600,
                  color: Colors.grey.shade700,
                ),
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 6),
              Text(
                l10n.documentsEmptyHint,
                style: TextStyle(fontSize: 13, color: Colors.grey.shade600, height: 1.35),
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );
    }

    final groups = groupPatientDocuments(documents);
    return ListView(
      padding: EdgeInsets.zero,
      children: [
        for (var gi = 0; gi < groups.length; gi++) ...[
          if (gi > 0) const SizedBox(height: 16),
          Text(
            sectionTitle(l10n, groups[gi].bucket),
            style: TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w700,
              color: Colors.grey.shade700,
              letterSpacing: 0.4,
            ),
          ),
          const SizedBox(height: 8),
          for (final d in groups[gi].docs) ...[
            PatientDocumentTile(
              d,
              brand,
              patientId: patientId,
              onRequestAccess:
                  (!d.canView && patientId != null && onRequestAccess != null)
                  ? () => onRequestAccess!(d)
                  : null,
            ),
            const SizedBox(height: 8),
          ],
        ],
      ],
    );
  }
}

/// Patient-centric header for consultation documentation (desktop/web shell).
class AppointmentConsultationHeader extends StatelessWidget {
  const AppointmentConsultationHeader({
    super.key,
    required this.appointment,
    this.resolvedPatient,
    this.patientLoading = false,
    required this.onBack,
  });

  final Appointment appointment;

  /// Patient profile when available (age, photo, canonical id).
  final Patient? resolvedPatient;

  /// True while [resolvedPatient] is still loading (shows compact spinner).
  final bool patientLoading;

  final VoidCallback onBack;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    final ml = MaterialLocalizations.of(context);
    final use24 = MediaQuery.of(context).alwaysUse24HourFormat;
    final startStr = ml.formatTimeOfDay(
      appointment.start,
      alwaysUse24HourFormat: use24,
    );
    final endStr = ml.formatTimeOfDay(
      appointment.end,
      alwaysUse24HourFormat: use24,
    );
    final scheduleLine = l10n.consultationScheduleLine(startStr, endStr);

    final statusLabel = appointmentStatusLabel(l10n, appointment.status);
    final statusColor = appointmentStatusColor(appointment.status);

    final photoUrl = resolvedPatient?.photoUrl ?? appointment.photoUrl;
    final idShow =
        (resolvedPatient?.id.isNotEmpty == true)
            ? resolvedPatient!.id
            : (appointment.patientId ?? '');
    final age =
        resolvedPatient != null
            ? ageFromBirthDate(resolvedPatient!.general.birthDate)
            : null;

    Widget metaRow;
    if (patientLoading && resolvedPatient == null) {
      metaRow = SizedBox(
        height: 14,
        width: 14,
        child: CircularProgressIndicator(
          strokeWidth: 2,
          color: Colors.grey.shade500,
        ),
      );
    } else {
      final parts = <String>[];
      if (age != null) parts.add(l10n.patientAgeYears(age));
      if (idShow.isNotEmpty) parts.add(l10n.patientIdLabel(idShow));
      metaRow =
          parts.isEmpty
              ? const SizedBox.shrink()
              : Text(
                parts.join(' • '),
                style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
              );
    }

    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(
          bottom: BorderSide(color: Colors.black.withValues(alpha: 0.06)),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.05),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      padding: const EdgeInsets.fromLTRB(4, 8, 24, 16),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          IconButton(
            onPressed: onBack,
            icon: const Icon(Icons.arrow_back, color: Colors.black87),
          ),
          _HeaderAvatar(photoUrl: photoUrl),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: Text(
                        appointment.patientName,
                        style: const TextStyle(
                          fontSize: 20,
                          fontWeight: FontWeight.w700,
                          height: 1.2,
                          color: Color(0xFF1A1A1A),
                        ),
                      ),
                    ),
                    if (statusLabel != null) ...[
                      const SizedBox(width: 8),
                      Container(
                        padding: const EdgeInsets.symmetric(
                          horizontal: 10,
                          vertical: 4,
                        ),
                        decoration: BoxDecoration(
                          color: statusColor.withValues(alpha: 0.12),
                          borderRadius: BorderRadius.circular(999),
                          border: Border.all(
                            color: statusColor.withValues(alpha: 0.35),
                          ),
                        ),
                        child: Text(
                          statusLabel,
                          style: TextStyle(
                            fontSize: 11,
                            fontWeight: FontWeight.w700,
                            color: statusColor.darken(0.05),
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 4),
                metaRow,
                const SizedBox(height: 4),
                Text(
                  scheduleLine,
                  style: TextStyle(fontSize: 13, color: Colors.grey.shade700),
                ),
                if (appointment.location.trim().isNotEmpty) ...[
                  const SizedBox(height: 2),
                  Text(
                    appointment.location,
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HeaderAvatar extends StatelessWidget {
  const _HeaderAvatar({this.photoUrl});

  final String? photoUrl;

  @override
  Widget build(BuildContext context) {
    final url = photoUrl;
    return CircleAvatar(
      radius: 28,
      backgroundColor: Colors.teal.shade50,
      backgroundImage:
          url != null && url.isNotEmpty ? NetworkImage(url) : null,
      child:
          url == null || url.isEmpty
              ? Icon(Icons.person_rounded, size: 30, color: Colors.teal.shade700)
              : null,
    );
  }
}

/// Sticky actions bar for ending consultation and signatures.
class ConsultationStickyFooter extends StatelessWidget {
  const ConsultationStickyFooter({
    super.key,
    required this.hasPatientSignature,
    required this.signatureRequested,
    required this.showRequestSignatureButton,
    required this.isRequestingSignature,
    required this.onRequestSignature,
    required this.isEndingAppointment,
    required this.onEndAppointment,
  });

  final bool hasPatientSignature;
  final bool signatureRequested;
  final bool showRequestSignatureButton;
  final bool isRequestingSignature;
  final VoidCallback onRequestSignature;
  final bool isEndingAppointment;
  final VoidCallback onEndAppointment;

  @override
  Widget build(BuildContext context) {
    final l10n = AppLocalizations.of(context)!;
    return Container(
      width: double.infinity,
      decoration: BoxDecoration(
        color: Colors.white,
        border: Border(
          top: BorderSide(color: Colors.black.withValues(alpha: 0.06)),
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: 0.07),
            blurRadius: 24,
            offset: const Offset(0, -6),
          ),
        ],
      ),
      padding: const EdgeInsets.fromLTRB(24, 12, 24, 12),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            if (hasPatientSignature)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Row(
                  children: [
                    Icon(Icons.check_circle, color: Colors.green.shade600, size: 20),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        l10n.patientSigned,
                        style: TextStyle(
                          color: Colors.green.shade700,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    ),
                  ],
                ),
              )
            else if (signatureRequested)
              Padding(
                padding: const EdgeInsets.only(bottom: 8),
                child: Text(
                  l10n.waitingForPatientSignature,
                  style: TextStyle(color: Colors.orange.shade800, fontSize: 13),
                ),
              ),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                if (showRequestSignatureButton)
                  Padding(
                    padding: const EdgeInsets.only(right: 12),
                    child: ShifaSecondaryButton(
                      label: l10n.requestSignature,
                      onPressed: isRequestingSignature ? null : onRequestSignature,
                      icon: Icons.draw,
                      isLoading: isRequestingSignature,
                    ),
                  ),
                ShifaPrimaryButton(
                  label: l10n.endAppointment,
                  onPressed: isEndingAppointment ? null : onEndAppointment,
                  variant: ButtonVariant.destructive,
                  isLoading: isEndingAppointment,
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
