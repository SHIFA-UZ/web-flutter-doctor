// lib/features/appointments/services/appointment_pdf_service.dart

import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import 'appointment_pdf_data.dart';
import 'appointment_pdf_translations.dart';

/// Margin in points (1/72 inch). ~40px at 96 DPI ≈ 30pt; spec says 40px min.
const double _marginPt = 40;

/// Matches [AppColors.primaryTeal] (#00BBB0) for print-safe accent.
PdfColor get _brandTeal => PdfColor(0 / 255, 187 / 255, 176 / 255);

/// A4 with margins applied (content area).
PdfPageFormat get _pageFormat => PdfPageFormat.a4.copyWith(
      marginLeft: _marginPt,
      marginRight: _marginPt,
      marginTop: _marginPt,
      marginBottom: _marginPt,
    );

/// Generates a professional medical-style appointment summary PDF.
/// [data] – appointment and patient/doctor info.
/// [languageCode] – 'en' or 'uz' for localization.
/// [beforeImages] / [afterImages] – optional treatment images (appended after report).
/// Returns PDF bytes. Uses A4, UTF-8 font (DejaVu Sans), header, signatures, footer.
Future<Uint8List> generateAppointmentPdf({
  required AppointmentPdfData data,
  required String languageCode,
  List<XFile>? beforeImages,
  List<XFile>? afterImages,
}) async {
  final fontData = await rootBundle.load('assets/fonts/DejaVuSans.ttf');
  final font = pw.Font.ttf(fontData);
  final t = AppointmentPdfTranslations.forLanguage(languageCode);

  pw.ImageProvider? logoImage;
  try {
    final logoData = await rootBundle.load('assets/branding/shifa_logo.png');
    logoImage = pw.MemoryImage(logoData.buffer.asUint8List());
  } catch (_) {
    // Logo optional
  }

  final pdf = pw.Document();
  final int imagePageCount =
      (beforeImages?.length ?? 0) + (afterImages?.length ?? 0);
  final int totalPages = 1 + imagePageCount;

  final dayNames = AppointmentPdfTranslations.dayNames(languageCode);
  final monthNames = AppointmentPdfTranslations.monthNames(languageCode);
  final d = data.appointmentDate;
  final dayOfWeek = dayNames[d.weekday - 1];
  final fullDate = '${monthNames[d.month]} ${d.day}, ${d.year}';

  pdf.addPage(
    pw.MultiPage(
      pageFormat: _pageFormat,
      theme: pw.ThemeData.withFont(base: font),
      build: (pw.Context context) => [
        pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.stretch,
          children: _buildReportContentAsList(
            data: data,
            t: t,
            font: font,
            logoImage: logoImage,
            dayOfWeek: dayOfWeek,
            fullDate: fullDate,
          ),
        ),
      ],
      footer: (pw.Context context) => _buildFooter(
        t,
        font,
        context.pageNumber,
        totalPages,
      ),
    ),
  );

  int pageIndex = 2;
  if (beforeImages != null) {
    for (int i = 0; i < beforeImages.length; i++) {
      final label = beforeImages.length > 1
          ? '${t.beforeTreatment} - ${t.imageLabel} ${i + 1}'
          : t.beforeTreatment;
      await _addImagePage(
          pdf, beforeImages[i], label, font, t, pageIndex, totalPages);
      pageIndex++;
    }
  }
  if (afterImages != null) {
    for (int i = 0; i < afterImages.length; i++) {
      final label = afterImages.length > 1
          ? '${t.afterTreatment} - ${t.imageLabel} ${i + 1}'
          : t.afterTreatment;
      await _addImagePage(
          pdf, afterImages[i], label, font, t, pageIndex, totalPages);
      pageIndex++;
    }
  }

  return pdf.save();
}

/// Max lines per chunk so each widget fits on one page (~35 lines ≈ 400pt at 11pt font).
const int _maxLinesPerChunk = 35;

/// Uzbek regulatory line for dental documentation (sterilized instruments).
const String _uzDentalSterilizationAttestation =
    "Zararsizlantirilgan asboblar (instrumentlar) ishlatilganligi to'g'ri";

List<pw.Widget> _buildReportContentAsList({
  required AppointmentPdfData data,
  required AppointmentPdfTranslations t,
  required pw.Font font,
  pw.ImageProvider? logoImage,
  required String dayOfWeek,
  required String fullDate,
}) {
  final children = <pw.Widget>[
    _buildHeader(data, t, font, logoImage, dayOfWeek, fullDate),
    pw.SizedBox(height: 14),
    _buildTitleBand(t, font),
    pw.SizedBox(height: 16),
    _buildAppointmentMetadataCard(data, t, font),
    pw.SizedBox(height: 18),
  ];

  // Clinical narrative first (plan order), then dental services table when present.
  if (data.notes != null && data.notes!.trim().isNotEmpty) {
    children.add(_buildFormalSectionBar(t.clinicalNotes, font));
    children.add(_sectionBodyBox(
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          for (final chunk in _textChunks(data.notes!.trim(), _maxLinesPerChunk)) ...[
            _buildBodyParagraph(chunk, font),
            pw.SizedBox(height: 6),
          ],
        ],
      ),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  final billing = data.dentalBilling;
  if (billing != null && billing.lines.isNotEmpty) {
    children.add(_buildFormalSectionBar(t.servicesProvided, font));
    children.add(_sectionBodyBox(
      child: _buildDentalBillingContent(billing, t, font),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  final plan = data.treatmentPlan;
  if (plan != null) {
    children.add(_buildFormalSectionBar(t.treatmentPlanSection, font));
    children.add(_sectionBodyBox(
      child: _buildTreatmentPlanContent(plan, t, font),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  final hasIcd = (data.diagnosisCode != null &&
      data.diagnosisCode!.trim().isNotEmpty &&
      data.diagnosisDisplay != null &&
      data.diagnosisDisplay!.trim().isNotEmpty);
  final hasFreeTextDx =
      (data.diagnosis != null && data.diagnosis!.trim().isNotEmpty);

  if (hasIcd || hasFreeTextDx) {
    children.add(_buildFormalSectionBar(t.diagnosis, font));
    children.add(_sectionBodyBox(
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          if (hasIcd) ...[
            _buildBodyParagraph(
              '${data.diagnosisCode!.trim()} — ${data.diagnosisDisplay!.trim()}',
              font,
            ),
            pw.SizedBox(height: 6),
          ],
          if (hasFreeTextDx)
            for (final chunk in _textChunks(data.diagnosis!.trim(), _maxLinesPerChunk)) ...[
              _buildBodyParagraph(chunk, font),
              pw.SizedBox(height: 4),
            ],
        ],
      ),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  if (data.prescriptions != null && data.prescriptions!.trim().isNotEmpty) {
    children.add(_buildFormalSectionBar(t.prescriptions, font));
    children.add(_sectionBodyBox(
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          for (final chunk in _textChunks(data.prescriptions!.trim(), _maxLinesPerChunk)) ...[
            _buildBodyParagraph(chunk, font),
            pw.SizedBox(height: 4),
          ],
        ],
      ),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  if (data.recommendations != null && data.recommendations!.trim().isNotEmpty) {
    children.add(_buildFormalSectionBar(t.recommendations, font));
    children.add(_sectionBodyBox(
      child: pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          for (final chunk in _textChunks(data.recommendations!.trim(), _maxLinesPerChunk)) ...[
            _buildBodyParagraph(chunk, font),
            pw.SizedBox(height: 4),
          ],
        ],
      ),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  if (data.followUpDate != null && data.followUpDate!.trim().isNotEmpty) {
    children.add(_buildFormalSectionBar(t.followUpDate, font));
    children.add(_sectionBodyBox(
      child: _buildBodyParagraph(data.followUpDate!, font),
    ));
    children.add(pw.SizedBox(height: 16));
  }

  children.add(pw.SizedBox(height: 24));

  if (data.isDentalDocumentation) {
    children.add(_buildDentalSterilizationAttestation(data, font));
    children.add(pw.SizedBox(height: 16));
  }

  children.add(_buildFormalSectionBar(t.signaturesSection, font));
  children.add(_sectionBodyBox(
    child: _buildSignatureInner(data, t, font),
  ));

  return children;
}

pw.Widget _buildDentalBillingContent(
  AppointmentPdfDentalBilling billing,
  AppointmentPdfTranslations t,
  pw.Font font,
) {
  final amtStyle = pw.TextStyle(font: font, fontSize: 10);

  pw.Widget hdrCell(String s) => pw.Container(
        color: PdfColors.grey300,
        padding: const pw.EdgeInsets.symmetric(horizontal: 6, vertical: 6),
        child: pw.Text(
          s,
          style: pw.TextStyle(font: font, fontSize: 10, fontWeight: pw.FontWeight.bold),
        ),
      );

  pw.Widget cell(
    String s, {
    pw.TextAlign align = pw.TextAlign.left,
    PdfColor? bg,
    bool bold = false,
  }) =>
      pw.Container(
        color: bg,
        padding: const pw.EdgeInsets.symmetric(horizontal: 6, vertical: 5),
        alignment: align == pw.TextAlign.right
            ? pw.Alignment.centerRight
            : pw.Alignment.centerLeft,
        child: pw.Text(
          s,
          style: pw.TextStyle(
            font: font,
            fontSize: 10,
            fontWeight: bold ? pw.FontWeight.bold : pw.FontWeight.normal,
          ),
          textAlign: align,
        ),
      );

  final tableRows = <pw.TableRow>[
    pw.TableRow(
      decoration: const pw.BoxDecoration(color: PdfColors.grey300),
      children: [
        hdrCell(t.toothColumn),
        hdrCell(t.serviceColumn),
        hdrCell(t.amountColumn),
      ],
    ),
  ];

  for (var i = 0; i < billing.lines.length; i++) {
    final line = billing.lines[i];
    final zebra = i.isOdd ? PdfColors.grey100 : null;
    final label =
        '${(line.amountMinor / 100).toStringAsFixed(2)} ${line.currency}';
    tableRows.add(
      pw.TableRow(
        children: [
          cell(line.tooth, bg: zebra),
          cell(line.serviceTitle, bg: zebra),
          cell(label, align: pw.TextAlign.right, bg: zebra),
        ],
      ),
    );
  }

  final subLabel =
      '${(billing.subtotalMinor / 100).toStringAsFixed(2)} ${billing.currency}';
  final totLabel =
      '${(billing.totalMinor / 100).toStringAsFixed(2)} ${billing.currency}';

  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: [
      pw.Text(
        billing.header,
        style: pw.TextStyle(
          font: font,
          fontSize: 11,
          fontWeight: pw.FontWeight.bold,
        ),
      ),
      pw.SizedBox(height: 10),
      pw.Table(
        border: pw.TableBorder.all(color: PdfColors.grey500, width: 0.5),
        columnWidths: const {
          0: pw.FlexColumnWidth(1.25),
          1: pw.FlexColumnWidth(2.9),
          2: pw.FlexColumnWidth(1.35),
        },
        children: tableRows,
      ),
      pw.SizedBox(height: 10),
      pw.Align(
        alignment: pw.Alignment.centerRight,
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.end,
          children: [
            pw.Row(
              mainAxisSize: pw.MainAxisSize.min,
              children: [
                pw.SizedBox(
                  width: 140,
                  child: pw.Text(
                    '${t.subtotalRow}:',
                    style: amtStyle,
                    textAlign: pw.TextAlign.right,
                  ),
                ),
                pw.SizedBox(width: 12),
                pw.SizedBox(
                  width: 100,
                  child: pw.Text(
                    subLabel,
                    style: amtStyle,
                    textAlign: pw.TextAlign.right,
                  ),
                ),
              ],
            ),
            if (billing.discountPercent != null && billing.discountPercent! > 0) ...[
              pw.SizedBox(height: 4),
              pw.Row(
                mainAxisSize: pw.MainAxisSize.min,
                children: [
                  pw.SizedBox(
                    width: 140,
                    child: pw.Text(
                      '${t.discountRow}:',
                      style: amtStyle,
                      textAlign: pw.TextAlign.right,
                    ),
                  ),
                  pw.SizedBox(width: 12),
                  pw.SizedBox(
                    width: 100,
                    child: pw.Text(
                      '${billing.discountPercent!.toStringAsFixed(1)}%',
                      style: amtStyle,
                      textAlign: pw.TextAlign.right,
                    ),
                  ),
                ],
              ),
            ],
            pw.SizedBox(height: 6),
            pw.Container(width: 252, height: 1, color: PdfColors.grey600),
            pw.SizedBox(height: 6),
            pw.Row(
              mainAxisSize: pw.MainAxisSize.min,
              children: [
                pw.SizedBox(
                  width: 140,
                  child: pw.Text(
                    '${t.totalRow}:',
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 11,
                      fontWeight: pw.FontWeight.bold,
                    ),
                    textAlign: pw.TextAlign.right,
                  ),
                ),
                pw.SizedBox(width: 12),
                pw.SizedBox(
                  width: 100,
                  child: pw.Text(
                    totLabel,
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 11,
                      fontWeight: pw.FontWeight.bold,
                    ),
                    textAlign: pw.TextAlign.right,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    ],
  );
}

pw.Widget _buildTreatmentPlanContent(
  AppointmentPdfTreatmentPlanSection plan,
  AppointmentPdfTranslations t,
  pw.Font font,
) {
  final amtStyle = pw.TextStyle(font: font, fontSize: 10);
  String money(int minor) =>
      '${(minor / 100).toStringAsFixed(2)} ${plan.currency}';

  pw.Widget summaryRow(String label, int minor, {bool bold = false}) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 4),
      child: pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Text(
            label,
            style: pw.TextStyle(
              font: font,
              fontSize: 10,
              fontWeight: bold ? pw.FontWeight.bold : pw.FontWeight.normal,
            ),
          ),
          pw.Text(
            money(minor),
            style: pw.TextStyle(
              font: font,
              fontSize: 10,
              fontWeight: bold ? pw.FontWeight.bold : pw.FontWeight.normal,
            ),
          ),
        ],
      ),
    );
  }

  final children = <pw.Widget>[
    pw.Text(
      plan.planTitle != null && plan.planTitle!.trim().isNotEmpty
          ? '${t.treatmentPlanTitle} #${plan.planId} — ${plan.planTitle}'
          : '${t.treatmentPlanTitle} #${plan.planId}',
      style: pw.TextStyle(font: font, fontSize: 11, fontWeight: pw.FontWeight.bold),
    ),
    pw.SizedBox(height: 8),
    summaryRow(t.planTotalRow, plan.planTotalMinor),
    summaryRow(t.planPaidRow, plan.planPaidMinor),
    summaryRow(t.planOutstandingRow, plan.planOwedMinor, bold: true),
  ];

  if (plan.fulfilledThisVisit.isNotEmpty) {
    children.add(pw.SizedBox(height: 10));
    children.add(pw.Text(
      t.fulfilledThisVisit,
      style: pw.TextStyle(font: font, fontSize: 10, fontWeight: pw.FontWeight.bold),
    ));
    children.add(pw.SizedBox(height: 6));
    for (final line in plan.fulfilledThisVisit) {
      final toothPart =
          line.tooth.trim().isNotEmpty ? '${line.tooth} — ' : '';
      children.add(pw.Padding(
        padding: const pw.EdgeInsets.only(bottom: 3),
        child: pw.Text(
          '$toothPart${line.serviceTitle}: ${money(line.amountMinor)}',
          style: amtStyle,
        ),
      ));
    }
  }

  if (plan.sessionPaymentMinor != null && plan.sessionPaymentMinor! > 0) {
    children.add(pw.SizedBox(height: 8));
    final method = plan.sessionPaymentMethod?.trim();
    final methodSuffix =
        method != null && method.isNotEmpty ? ' ($method)' : '';
    children.add(summaryRow(
      '${t.sessionPaymentRow}$methodSuffix',
      plan.sessionPaymentMinor!,
      bold: true,
    ));
  }

  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: children,
  );
}

pw.Widget _buildFormalSectionBar(String title, pw.Font font) {
  return pw.Container(
    margin: const pw.EdgeInsets.only(top: 2),
    width: double.infinity,
    padding: const pw.EdgeInsets.symmetric(vertical: 6, horizontal: 8),
    color: PdfColors.grey300,
    child: pw.Text(
      title.toUpperCase(),
      style: pw.TextStyle(
        font: font,
        fontSize: 12,
        fontWeight: pw.FontWeight.bold,
      ),
    ),
  );
}

pw.Widget _sectionBodyBox({required pw.Widget child}) {
  return pw.Container(
    width: double.infinity,
    decoration: pw.BoxDecoration(
      border: pw.Border.all(color: PdfColors.grey600, width: 0.75),
      color: PdfColors.grey50,
    ),
    padding: const pw.EdgeInsets.all(12),
    child: child,
  );
}

pw.Widget _buildBodyParagraph(String text, pw.Font font) {
  return pw.Paragraph(
    text: text,
    style: pw.TextStyle(font: font, fontSize: 11),
    margin: pw.EdgeInsets.zero,
  );
}

/// Patient signature (when present) placed to the left of the sterilization attestation line.
pw.Widget _buildDentalSterilizationAttestation(
  AppointmentPdfData data,
  pw.Font font,
) {
  final hasImg = data.patientSignatureImageBytes != null &&
      data.patientSignatureImageBytes!.isNotEmpty;

  return pw.Container(
    width: double.infinity,
    padding: const pw.EdgeInsets.all(10),
    decoration: pw.BoxDecoration(
      border: pw.Border.all(color: PdfColors.grey500, width: 0.5),
      borderRadius: const pw.BorderRadius.all(pw.Radius.circular(4)),
      color: PdfColors.grey100,
    ),
    child: pw.Row(
      crossAxisAlignment: pw.CrossAxisAlignment.center,
      children: [
        if (hasImg) ...[
          pw.Container(
            width: 100,
            height: 52,
            alignment: pw.Alignment.center,
            child: pw.Image(
              pw.MemoryImage(data.patientSignatureImageBytes!),
              fit: pw.BoxFit.contain,
            ),
          ),
          pw.SizedBox(width: 12),
        ],
        pw.Expanded(
          child: pw.Text(
            _uzDentalSterilizationAttestation,
            style: pw.TextStyle(
              font: font,
              fontSize: 11,
              fontWeight: pw.FontWeight.bold,
            ),
          ),
        ),
      ],
    ),
  );
}

List<String> _textChunks(String text, int maxLines) {
  final lines = text.split('\n');
  if (lines.length <= maxLines) return [text];
  final chunks = <String>[];
  for (var i = 0; i < lines.length; i += maxLines) {
    final end = (i + maxLines).clamp(0, lines.length);
    chunks.add(lines.sublist(i, end).join('\n'));
  }
  return chunks;
}

pw.Widget _buildHeader(
  AppointmentPdfData data,
  AppointmentPdfTranslations t,
  pw.Font font,
  pw.ImageProvider? logoImage,
  String dayOfWeek,
  String fullDate,
) {
  final clinic = data.clinicName?.trim();
  final hasClinic = clinic != null && clinic.isNotEmpty;

  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: [
      pw.Row(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Row(
            mainAxisSize: pw.MainAxisSize.min,
            crossAxisAlignment: pw.CrossAxisAlignment.center,
            children: [
              if (logoImage != null)
                pw.Container(
                  width: 48,
                  height: 48,
                  child: pw.Image(logoImage, fit: pw.BoxFit.contain),
                ),
              if (logoImage != null) pw.SizedBox(width: 12),
              pw.Text(
                'SHIFA',
                style: pw.TextStyle(
                  font: font,
                  fontSize: 20,
                  fontWeight: pw.FontWeight.bold,
                ),
              ),
            ],
          ),
          pw.Expanded(
            child: pw.Column(
              crossAxisAlignment: pw.CrossAxisAlignment.end,
              children: [
                if (hasClinic) ...[
                  pw.Text(
                    '${t.clinic}:',
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 9,
                      fontWeight: pw.FontWeight.bold,
                      color: PdfColors.grey700,
                    ),
                  ),
                  pw.Text(
                    clinic,
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 11,
                      fontWeight: pw.FontWeight.bold,
                    ),
                    textAlign: pw.TextAlign.right,
                  ),
                  pw.SizedBox(height: 6),
                ],
                pw.Text(dayOfWeek, style: pw.TextStyle(font: font, fontSize: 11)),
                pw.Text(fullDate, style: pw.TextStyle(font: font, fontSize: 11)),
                pw.Text(data.timeStr, style: pw.TextStyle(font: font, fontSize: 11)),
                pw.SizedBox(height: 4),
                pw.Text(
                  '${t.appointmentIdLabel}: ${data.appointmentId}',
                  style: pw.TextStyle(
                    font: font,
                    fontSize: 10,
                    color: PdfColors.grey700,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      pw.SizedBox(height: 10),
      pw.Container(height: 2, color: _brandTeal),
    ],
  );
}

pw.Widget _buildTitleBand(AppointmentPdfTranslations t, pw.Font font) {
  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: [
      pw.Center(
        child: pw.Text(
          t.appointmentSummary,
          style: pw.TextStyle(
            font: font,
            fontSize: 18,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
      ),
      pw.SizedBox(height: 10),
      pw.Container(height: 1, color: PdfColors.grey400),
    ],
  );
}

pw.Widget _buildAppointmentMetadataCard(
  AppointmentPdfData data,
  AppointmentPdfTranslations t,
  pw.Font font,
) {
  pw.Widget labelled(String label, String value) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 8),
      child: pw.RichText(
        text: pw.TextSpan(
          children: [
            pw.TextSpan(
              text: '$label: ',
              style: pw.TextStyle(
                font: font,
                fontSize: 11,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
            pw.TextSpan(
              text: value,
              style: pw.TextStyle(font: font, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }

  final leftKids = <pw.Widget>[
    labelled(t.patientName, data.patientName),
    if (data.patientId != null && data.patientId!.trim().isNotEmpty)
      labelled(t.patientId, data.patientId!),
    if (data.dateOfBirth != null && data.dateOfBirth!.trim().isNotEmpty)
      labelled(t.dateOfBirth, data.dateOfBirth!),
    if (data.gender != null && data.gender!.trim().isNotEmpty)
      labelled(t.gender, data.gender!),
  ];

  final rightKids = <pw.Widget>[
    if (data.doctorName != null && data.doctorName!.trim().isNotEmpty)
      labelled(t.doctorName, data.doctorName!),
    if (data.specialization != null && data.specialization!.trim().isNotEmpty)
      labelled(t.specialization, data.specialization!),
    if (data.licenseNumber != null && data.licenseNumber!.trim().isNotEmpty)
      labelled(t.licenseNumber, data.licenseNumber!),
    labelled(t.appointmentType, data.appointmentType),
    if (data.duration != null && data.duration!.trim().isNotEmpty)
      labelled(t.duration, data.duration!),
  ];

  return pw.Container(
    decoration: pw.BoxDecoration(
      border: pw.Border.all(color: PdfColors.grey600, width: 1),
    ),
    padding: const pw.EdgeInsets.all(14),
    child: pw.Row(
      crossAxisAlignment: pw.CrossAxisAlignment.start,
      children: [
        pw.Expanded(
          child: pw.Column(
            crossAxisAlignment: pw.CrossAxisAlignment.start,
            children: leftKids,
          ),
        ),
        pw.SizedBox(width: 24),
        pw.Expanded(
          child: pw.Column(
            crossAxisAlignment: pw.CrossAxisAlignment.start,
            children: rightKids,
          ),
        ),
      ],
    ),
  );
}

pw.Widget _buildSignatureInner(
  AppointmentPdfData data,
  AppointmentPdfTranslations t,
  pw.Font font,
) {
  final hasPatientSignatureImage = data.patientSignatureImageBytes != null &&
      data.patientSignatureImageBytes!.isNotEmpty;

  return pw.Row(
    crossAxisAlignment: pw.CrossAxisAlignment.start,
    mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
    children: [
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text(
              '${t.doctorSignature}:',
              style: pw.TextStyle(
                font: font,
                fontSize: 10,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
            pw.SizedBox(height: 4),
            pw.Container(
              width: double.infinity,
              constraints: const pw.BoxConstraints(maxWidth: 220),
              height: 1,
              color: PdfColors.grey800,
            ),
            pw.SizedBox(height: 6),
            pw.Text(
              data.doctorName ?? '—',
              style: pw.TextStyle(font: font, fontSize: 9),
            ),
            if (data.licenseNumber != null && data.licenseNumber!.trim().isNotEmpty)
              pw.Text(
                data.licenseNumber!,
                style: pw.TextStyle(
                  font: font,
                  fontSize: 8,
                  color: PdfColors.grey700,
                ),
              ),
          ],
        ),
      ),
      pw.SizedBox(width: 24),
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text(
              '${t.patientSignature}:',
              style: pw.TextStyle(
                font: font,
                fontSize: 10,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
            pw.SizedBox(height: 4),
            if (hasPatientSignatureImage) ...[
              if (!data.isDentalDocumentation) ...[
                pw.Container(
                  width: double.infinity,
                  constraints: const pw.BoxConstraints(maxWidth: 220),
                  height: 48,
                  child: pw.Image(
                    pw.MemoryImage(data.patientSignatureImageBytes!),
                    fit: pw.BoxFit.contain,
                  ),
                ),
                pw.SizedBox(height: 6),
              ],
              if (data.patientSignedAt != null)
                pw.Text(
                  _formatSignedAt(data.patientSignedAt!, t.signedElectronicallyOn),
                  style: pw.TextStyle(
                    font: font,
                    fontSize: 8,
                    color: PdfColors.grey700,
                  ),
                ),
              pw.Text(
                t.viaShifaPatientApp,
                style: pw.TextStyle(
                  font: font,
                  fontSize: 8,
                  color: PdfColors.grey700,
                ),
              ),
            ] else ...[
              pw.Container(
                width: double.infinity,
                constraints: const pw.BoxConstraints(maxWidth: 220),
                height: 1,
                color: PdfColors.grey800,
              ),
              pw.SizedBox(height: 6),
              pw.Text(
                data.patientName,
                style: pw.TextStyle(font: font, fontSize: 9),
              ),
            ],
          ],
        ),
      ),
    ],
  );
}

String _formatSignedAt(DateTime dt, String prefix) {
  final m = dt.month.toString().padLeft(2, '0');
  final d = dt.day.toString().padLeft(2, '0');
  final y = dt.year;
  final h = dt.hour.toString().padLeft(2, '0');
  final min = dt.minute.toString().padLeft(2, '0');
  return '$prefix $y-$m-$d $h:$min';
}

pw.Widget _buildFooter(
  AppointmentPdfTranslations t,
  pw.Font font,
  int pageNumber,
  int totalPages,
) {
  return pw.Container(
    decoration: const pw.BoxDecoration(
      border: pw.Border(top: pw.BorderSide(color: PdfColors.grey400, width: 0.75)),
    ),
    padding: const pw.EdgeInsets.only(top: 8),
    child: pw.Column(
      children: [
        pw.Row(
          mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
          children: [
            pw.Expanded(
              child: pw.Text(
                t.generatedByShifa,
                style:
                    pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey700),
              ),
            ),
            pw.Text(
              '${t.pageOf} $pageNumber / $totalPages',
              style:
                  pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey700),
            ),
          ],
        ),
        pw.SizedBox(height: 6),
        pw.Center(
          child: pw.Text(
            t.confidentialMedicalDocument,
            style:
                pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey600),
          ),
        ),
      ],
    ),
  );
}

Future<void> _addImagePage(
  pw.Document pdf,
  XFile imageFile,
  String label,
  pw.Font font,
  AppointmentPdfTranslations t,
  int pageNumber,
  int totalPages,
) async {
  final imageBytes = await imageFile.readAsBytes();
  pdf.addPage(
    pw.Page(
      pageFormat: _pageFormat,
      theme: pw.ThemeData.withFont(base: font),
      build: (pw.Context context) {
        return pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text(
              label,
              style: pw.TextStyle(
                font: font,
                fontSize: 14,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
            pw.SizedBox(height: 12),
            pw.Expanded(
              child: pw.Center(
                child: pw.Image(
                  pw.MemoryImage(imageBytes),
                  fit: pw.BoxFit.contain,
                ),
              ),
            ),
            pw.SizedBox(height: 16),
            pw.Container(
              decoration: const pw.BoxDecoration(
                border:
                    pw.Border(top: pw.BorderSide(color: PdfColors.grey400, width: 1)),
              ),
              padding: const pw.EdgeInsets.only(top: 8),
              child: pw.Row(
                mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
                children: [
                  pw.Text(
                    t.generatedByShifa,
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 9,
                      color: PdfColors.grey700,
                    ),
                  ),
                  pw.Text(
                    '${t.pageOf} $pageNumber / $totalPages',
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 9,
                      color: PdfColors.grey700,
                    ),
                  ),
                ],
              ),
            ),
          ],
        );
      },
    ),
  );
}
