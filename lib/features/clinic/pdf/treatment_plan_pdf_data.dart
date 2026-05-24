import 'package:shifa_doc_app_v1/state/clinic/clinic_treatment_plan_models.dart';

import 'treatment_plan_pdf_translations.dart';

/// One row in per-installment schedule table.
class TreatmentPlanPdfInstallmentScheduleRow {
  final int sequenceNumber;
  final String dueDateDisplay;
  final String amountDisplay;
  final String statusDisplay;

  const TreatmentPlanPdfInstallmentScheduleRow({
    required this.sequenceNumber,
    required this.dueDateDisplay,
    required this.amountDisplay,
    required this.statusDisplay,
  });
}

/// One installment plan summary + rows for PDF rendering.
class TreatmentPlanPdfInstallmentPlanSection {
  final int installmentPlanId;
  final String status;
  final String totalAmountDisplay;
  final int numInstallments;
  final List<TreatmentPlanPdfInstallmentScheduleRow> scheduleRows;

  const TreatmentPlanPdfInstallmentPlanSection({
    required this.installmentPlanId,
    required this.status,
    required this.totalAmountDisplay,
    required this.numInstallments,
    required this.scheduleRows,
  });
}

/// One priced line shown in PDF.
class TreatmentPlanPdfLineRow {
  final int displayIndex;
  final String title;
  final String quantityDisplay;
  final String unitPriceDisplay;
  final String discountDisplay;
  final String lineTotalDisplay;
  final String lineStatusDisplay;
  final String? linkedVisitDisplay;

  const TreatmentPlanPdfLineRow({
    required this.displayIndex,
    required this.title,
    required this.quantityDisplay,
    required this.unitPriceDisplay,
    required this.discountDisplay,
    required this.lineTotalDisplay,
    required this.lineStatusDisplay,
    this.linkedVisitDisplay,
  });
}

/// Input for clinic treatment-plan PDF renderer.
class TreatmentPlanPdfData {
  final String planId;
  final String? clinicName;
  final DateTime generatedAt;

  final String patientName;
  final String patientIdDisplay;
  final String? planTitleDisplay;
  final String? diagnosis;
  final String? notes;
  final List<String> symptoms;

  final String attendingDoctorsDisplay;
  final String planStatusDisplay;
  final String paymentStatusDisplay;
  final String planKindDisplay;

  final String totalDisplay;
  final String paidDisplay;
  final String owedDisplay;

  final String currency;
  final String createdAtDisplay;
  final String updatedAtDisplay;

  final List<TreatmentPlanPdfLineRow> lines;
  final List<TreatmentPlanPdfInstallmentPlanSection> installmentPlans;

  const TreatmentPlanPdfData({
    required this.planId,
    this.clinicName,
    required this.generatedAt,
    required this.patientName,
    required this.patientIdDisplay,
    this.planTitleDisplay,
    this.diagnosis,
    this.notes,
    required this.symptoms,
    required this.attendingDoctorsDisplay,
    required this.planStatusDisplay,
    required this.paymentStatusDisplay,
    required this.planKindDisplay,
    required this.totalDisplay,
    required this.paidDisplay,
    required this.owedDisplay,
    required this.currency,
    required this.createdAtDisplay,
    required this.updatedAtDisplay,
    required this.lines,
    required this.installmentPlans,
  });

  static String minorToMoney(int minor, String currency) =>
      '${(minor / 100).toStringAsFixed(2)} $currency';

  static String formatShortDate(String? iso) {
    if (iso == null || iso.trim().isEmpty) return '—';
    try {
      final dt = DateTime.parse(iso).toLocal();
      final mm = dt.month.toString().padLeft(2, '0');
      final dd = dt.day.toString().padLeft(2, '0');
      return '${dt.year}-$mm-$dd';
    } catch (_) {
      return iso;
    }
  }

  static String? formatLinkedVisit({
    required AppointmentSummaryDto ap,
    required String languageCode,
  }) {
    final dayNames = TreatmentPlanPdfTranslations.dayNames(languageCode);
    final monthNames = TreatmentPlanPdfTranslations.monthNames(languageCode);
    String fmt(String iso) {
      try {
        final dt = DateTime.parse(iso).toLocal();
        final dow = dayNames[dt.weekday - 1];
        final ds = '${monthNames[dt.month]} ${dt.day}, ${dt.year}';
        final h = dt.hour.toString().padLeft(2, '0');
        final m = dt.minute.toString().padLeft(2, '0');
        return '$dow, $ds $h:$m';
      } catch (_) {
        return iso;
      }
    }

    final start = fmt(ap.startAt);
    final apStatus = ap.status;
    return '$start · ${ap.doctorName} · $apStatus (#${ap.id})';
  }

  factory TreatmentPlanPdfData.fromDetail({
    required TreatmentPlanDetailDto detail,
    required DateTime generatedAt,
    required String languageCode,
    String? clinicDisplayName,
  }) {
    final t = TreatmentPlanPdfTranslations.forLanguage(languageCode);
    final s = detail.summary;

    final doctors = s.attendingDoctors.isNotEmpty
        ? s.attendingDoctors.map((d) => d.name).join(', ')
        : (s.attendingDoctorName?.trim().isNotEmpty == true
            ? s.attendingDoctorName!.trim()
            : '—');

    final pid = s.patientId != null ? '${s.patientId}' : '—';

    final sortedLines = [...detail.lines]..sort((a, b) {
        final o = a.sortOrder.compareTo(b.sortOrder);
        if (o != 0) return o;
        return a.id.compareTo(b.id);
      });

    final lineRows = <TreatmentPlanPdfLineRow>[];
    for (var i = 0; i < sortedLines.length; i++) {
      final L = sortedLines[i];
      lineRows.add(
        TreatmentPlanPdfLineRow(
          displayIndex: i + 1,
          title: L.title,
          quantityDisplay: '${L.quantity}',
          unitPriceDisplay: minorToMoney(L.unitPriceMinor, L.currency),
          discountDisplay: minorToMoney(L.discountMinor, L.currency),
          lineTotalDisplay: minorToMoney(L.lineTotalMinor, L.currency),
          lineStatusDisplay: t.labelLineStatus(L.status),
          linkedVisitDisplay: L.linkedAppointment != null
              ? formatLinkedVisit(
                  ap: L.linkedAppointment!,
                  languageCode: languageCode,
                )
              : null,
        ),
      );
    }

    final instSections = detail.installmentPlans.map((ip) {
      final scheduled = ip.scheduleRows
          .map(
            (r) => TreatmentPlanPdfInstallmentScheduleRow(
              sequenceNumber: r.sequenceNumber,
              dueDateDisplay: formatShortDate(r.dueDate),
              amountDisplay: minorToMoney(r.amountMinor, r.currency),
              statusDisplay: t.labelInstallmentItemStatus(r.status),
            ),
          )
          .toList();

      return TreatmentPlanPdfInstallmentPlanSection(
        installmentPlanId: ip.installmentPlanId,
        status: ip.status,
        totalAmountDisplay: minorToMoney(ip.totalAmountMinor, ip.currency),
        numInstallments: ip.numInstallments,
        scheduleRows: scheduled,
      );
    }).toList();

    return TreatmentPlanPdfData(
      planId: '${s.id}',
      clinicName: clinicDisplayName?.trim(),
      generatedAt: generatedAt,
      patientName: s.patientName?.trim().isNotEmpty == true
          ? s.patientName!.trim()
          : '—',
      patientIdDisplay: pid,
      planTitleDisplay:
          s.title?.trim().isEmpty == false ? s.title!.trim() : null,
      diagnosis:
          s.diagnosis?.trim().isEmpty == false ? s.diagnosis!.trim() : null,
      notes: s.notes?.trim().isEmpty == false ? s.notes!.trim() : null,
      symptoms: List<String>.from(s.symptoms),
      attendingDoctorsDisplay: doctors,
      planStatusDisplay: t.labelPlanStatus(s.status),
      paymentStatusDisplay: t.labelPaymentStatus(s.planPaymentStatus),
      planKindDisplay: t.labelPlanKind(s.planKind),
      totalDisplay: minorToMoney(s.totalMinor, s.currency),
      paidDisplay: minorToMoney(s.paidMinor, s.currency),
      owedDisplay: minorToMoney(s.owedMinor, s.currency),
      currency: s.currency,
      createdAtDisplay: formatShortDate(s.createdAt),
      updatedAtDisplay: formatShortDate(s.updatedAt),
      lines: lineRows,
      installmentPlans: instSections,
    );
  }
}
