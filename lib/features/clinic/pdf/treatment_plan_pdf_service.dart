// lib/features/clinic/pdf/treatment_plan_pdf_service.dart

import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

import 'treatment_plan_pdf_data.dart';
import 'treatment_plan_pdf_translations.dart';

const double _marginPt = 40;

/// Matches appointment PDF accent (#00BBB0).
PdfColor get _brandTeal => PdfColor(0 / 255, 187 / 255, 176 / 255);

PdfPageFormat get _pageFormat => PdfPageFormat.a4.copyWith(
      marginLeft: _marginPt,
      marginRight: _marginPt,
      marginTop: _marginPt,
      marginBottom: _marginPt,
    );

/// Max lines per chunk for wrapping long notes (~35 lines × ~11 pt).
const int _maxLinesPerChunk = 35;

Future<Uint8List> generateTreatmentPlanPdf({
  required TreatmentPlanPdfData data,
  required String languageCode,
}) async {
  final fontData = await rootBundle.load('assets/fonts/DejaVuSans.ttf');
  final font = pw.Font.ttf(fontData);
  final t = TreatmentPlanPdfTranslations.forLanguage(languageCode);

  pw.ImageProvider? logoImage;
  try {
    final logoData = await rootBundle.load('assets/branding/shifa_logo.png');
    logoImage = pw.MemoryImage(logoData.buffer.asUint8List());
  } catch (_) {}

  final pdf = pw.Document();
  final d = data.generatedAt.toLocal();
  final dayNames = TreatmentPlanPdfTranslations.dayNames(languageCode);
  final monthNames = TreatmentPlanPdfTranslations.monthNames(languageCode);
  final dayOfWeek = dayNames[d.weekday - 1];
  final fullDate =
      '$dayOfWeek, ${monthNames[d.month]} $d.day, $d.year ${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';

  pdf.addPage(
    pw.MultiPage(
      pageFormat: _pageFormat,
      theme: pw.ThemeData.withFont(base: font),
      build: (pw.Context ctx) =>
          _planContentWidgets(
            data: data,
            t: t,
            font: font,
            logoImage: logoImage,
            generatedLabel: fullDate,
          ),
      footer: (pw.Context c) => _footer(t, font, c.pageNumber, c.pagesCount),
    ),
  );

  // Yield so the embedder can rasterize pending frames (spinner/dialog) before
  // synchronous [Document.save], which blocks the UI isolate on Flutter web.
  await Future<void>.delayed(Duration.zero);
  return pdf.save();
}

pw.Widget _footer(TreatmentPlanPdfTranslations t, pw.Font font,
    int pageNumber, int totalPages) {
  return pw.Container(
    decoration: const pw.BoxDecoration(
      border:
          pw.Border(top: pw.BorderSide(color: PdfColors.grey400, width: 0.75)),
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
                style: pw.TextStyle(
                  font: font,
                  fontSize: 9,
                  color: PdfColors.grey700,
                ),
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
        pw.SizedBox(height: 6),
        pw.Center(
          child: pw.Text(
            t.confidentialDocument,
            style: pw.TextStyle(
              font: font,
              fontSize: 9,
              color: PdfColors.grey600,
            ),
          ),
        ),
      ],
    ),
  );
}

List<pw.Widget> _planContentWidgets({
  required TreatmentPlanPdfData data,
  required TreatmentPlanPdfTranslations t,
  required pw.Font font,
  pw.ImageProvider? logoImage,
  required String generatedLabel,
}) {
  final ch = <pw.Widget>[
    _headerBand(data, t, font, logoImage, generatedLabel),
    pw.SizedBox(height: 14),
    _centerTitle(font, data.planTitleDisplay, t.documentTitle),
    pw.SizedBox(height: 8),
    pw.Container(height: 1, color: PdfColors.grey400),
    pw.SizedBox(height: 16),
    _formalSection(t.planSummarySection, font),
    _sectionBody(child: _summaryCard(data, t, font)),
    pw.SizedBox(height: 16),
  ];

  if (data.symptoms.isNotEmpty) {
    ch.addAll([
      _formalSection(t.symptoms, font),
      _sectionBody(
        child: pw.Text(
          data.symptoms.map((s) => '• ${s.trim()}').join('\n'),
          style: pw.TextStyle(font: font, fontSize: 11),
        ),
      ),
      pw.SizedBox(height: 16),
    ]);
  }

  final notes = data.notes?.trim() ?? '';
  if (notes.isNotEmpty) {
    ch.addAll([
      _formalSection(t.notes, font),
      _sectionBody(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            for (final chunk in _chunks(notes, _maxLinesPerChunk)) ...[
              _paragraph(chunk, font),
              pw.SizedBox(height: 6),
            ],
          ],
        ),
      ),
      pw.SizedBox(height: 16),
    ]);
  }

  if (data.lines.isNotEmpty) {
    ch.addAll([
      _formalSection(t.procedures, font),
      _sectionBody(child: _linesTable(data, t, font)),
      pw.SizedBox(height: 16),
    ]);
  }

  if (data.installmentPlans.isNotEmpty) {
    ch.add(_formalSection(t.installments, font));
    ch.add(_sectionBody(child: _installSummaryTable(data, t, font)));
    for (final plan in data.installmentPlans) {
      if (plan.scheduleRows.isEmpty) continue;
      ch.add(pw.SizedBox(height: 12));
      ch.add(
        pw.Container(
          width: double.infinity,
          padding: const pw.EdgeInsets.only(bottom: 4),
          child: pw.Text(
            t.installmentScheduleSectionTitle(plan.installmentPlanId),
            style: pw.TextStyle(
              font: font,
              fontSize: 10.5,
              fontWeight: pw.FontWeight.bold,
              color: PdfColors.grey900,
            ),
          ),
        ),
      );
      ch.add(_sectionBody(child: _installScheduleTable(plan, t, font)));
    }
    ch.add(pw.SizedBox(height: 16));
  }

  ch.add(_formalSection(t.patientSignatureHeading, font));
  ch.add(_sectionBody(child: _patientSignatureBlock(data, t, font)));

  return ch;
}

pw.Widget _headerBand(
  TreatmentPlanPdfData data,
  TreatmentPlanPdfTranslations t,
  pw.Font font,
  pw.ImageProvider? logoImage,
  String generatedLabel,
) {
  final hasClinic = data.clinicName != null && data.clinicName!.trim().isNotEmpty;
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
                    data.clinicName!,
                    style: pw.TextStyle(
                      font: font,
                      fontSize: 11,
                      fontWeight: pw.FontWeight.bold,
                    ),
                    textAlign: pw.TextAlign.right,
                  ),
                  pw.SizedBox(height: 6),
                ],
                pw.Text(
                  '${t.treatmentPlanId}: #${data.planId}',
                  style: pw.TextStyle(font: font, fontSize: 11),
                ),
                pw.SizedBox(height: 4),
                pw.Text(
                  '${t.generatedAt}: $generatedLabel',
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

pw.Widget _centerTitle(pw.Font font, String? subtitle, String mainTitle) {
  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.stretch,
    children: [
      pw.Center(
        child: pw.Text(
          mainTitle,
          style: pw.TextStyle(
            font: font,
            fontSize: 18,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
      ),
      if (subtitle != null && subtitle.isNotEmpty) ...[
        pw.SizedBox(height: 6),
        pw.Center(
          child: pw.Padding(
            padding: const pw.EdgeInsets.symmetric(horizontal: 24),
            child: pw.Text(
              subtitle,
              style: pw.TextStyle(font: font, fontSize: 12),
              textAlign: pw.TextAlign.center,
            ),
          ),
        ),
      ],
    ],
  );
}

pw.Widget _summaryCard(
    TreatmentPlanPdfData data, TreatmentPlanPdfTranslations t, pw.Font font) {
  pw.Widget labelled(String lb, String v) {
    return pw.Padding(
      padding: const pw.EdgeInsets.only(bottom: 8),
      child: pw.RichText(
        text: pw.TextSpan(
          children: [
            pw.TextSpan(
              text: '$lb: ',
              style: pw.TextStyle(font: font, fontSize: 11, fontWeight: pw.FontWeight.bold),
            ),
            pw.TextSpan(
              text: v,
              style: pw.TextStyle(font: font, fontSize: 11),
            ),
          ],
        ),
      ),
    );
  }

  final leftCol = [
    labelled(t.patientName, data.patientName),
    labelled(t.patientId, data.patientIdDisplay),
    if ((data.planTitleDisplay ?? '').isNotEmpty)
      labelled(t.planTitle, data.planTitleDisplay!),
    if ((data.diagnosis ?? '').trim().isNotEmpty)
      labelled(t.diagnosis, data.diagnosis!),
    labelled(t.created, data.createdAtDisplay),
    labelled(t.updated, data.updatedAtDisplay),
  ];

  final rightCol = [
    labelled(t.attendingDoctor, data.attendingDoctorsDisplay),
    labelled(t.planStatus, data.planStatusDisplay),
    labelled(t.paymentStatus, data.paymentStatusDisplay),
    labelled(t.planKind, data.planKindDisplay),
    labelled(t.total, data.totalDisplay),
    labelled(t.paid, data.paidDisplay),
    labelled(t.outstanding, data.owedDisplay),
  ];

  return pw.Row(
    crossAxisAlignment: pw.CrossAxisAlignment.start,
    children: [
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: leftCol,
        ),
      ),
      pw.SizedBox(width: 28),
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: rightCol,
        ),
      ),
    ],
  );
}

pw.Widget _linesTable(
    TreatmentPlanPdfData data, TreatmentPlanPdfTranslations t, pw.Font font) {
  pw.Widget cell(String text, {bool bold = false, pw.TextAlign a = pw.TextAlign.left}) {
    return pw.Container(
      padding: const pw.EdgeInsets.all(5),
      child: pw.Text(
        text,
        style: pw.TextStyle(
          font: font,
          fontSize: 9,
          fontWeight: bold ? pw.FontWeight.bold : pw.FontWeight.normal,
        ),
        textAlign: a,
      ),
    );
  }

  pw.Widget hdr(String s) =>
      pw.Container(
        padding: const pw.EdgeInsets.all(5),
        color: PdfColors.grey300,
        child: pw.Text(
          s,
          style:
              pw.TextStyle(font: font, fontSize: 9, fontWeight: pw.FontWeight.bold),
        ),
      );

  final rows = <pw.TableRow>[
    pw.TableRow(children: [
      hdr(t.colIdx),
      hdr(t.service),
      hdr(t.quantity),
      hdr(t.unitPrice),
      hdr(t.discount),
      hdr(t.lineTotal),
      hdr(t.lineStatus),
      hdr(t.linkedVisit),
    ]),
  ];

  for (var i = 0; i < data.lines.length; i++) {
    final zebra = i.isOdd ? PdfColors.grey50 : null;
    final ln = data.lines[i];
    rows.add(pw.TableRow(
      children: [
        pw.Container(color: zebra, child: cell('${ln.displayIndex}', bold: false)),
        pw.Container(color: zebra, child: cell(ln.title)),
        pw.Container(
          color: zebra,
          child: cell(ln.quantityDisplay, a: pw.TextAlign.right),
        ),
        pw.Container(
          color: zebra,
          child: cell(ln.unitPriceDisplay, a: pw.TextAlign.right),
        ),
        pw.Container(
          color: zebra,
          child: cell(ln.discountDisplay, a: pw.TextAlign.right),
        ),
        pw.Container(
          color: zebra,
          child: cell(ln.lineTotalDisplay, a: pw.TextAlign.right, bold: true),
        ),
        pw.Container(color: zebra, child: cell(ln.lineStatusDisplay)),
        pw.Container(
          color: zebra,
          child: cell(ln.linkedVisitDisplay ?? '—'),
        ),
      ],
    ));
  }

  return pw.Table(
    border: pw.TableBorder.all(color: PdfColors.grey500, width: 0.5),
    columnWidths: const {
      0: pw.FixedColumnWidth(22),
      1: pw.FlexColumnWidth(2.8),
      2: pw.FixedColumnWidth(28),
      3: pw.FlexColumnWidth(1),
      4: pw.FlexColumnWidth(1),
      5: pw.FlexColumnWidth(1.1),
      6: pw.FlexColumnWidth(1),
      7: pw.FlexColumnWidth(2.2),
    },
    children: rows,
  );
}

pw.Widget _installSummaryTable(
    TreatmentPlanPdfData data, TreatmentPlanPdfTranslations t, pw.Font font) {
  pw.Widget cell(String s, {PdfColor? bg}) =>
      pw.Container(
        padding: const pw.EdgeInsets.all(5),
        color: bg,
        child: pw.Text(
          s,
          style: pw.TextStyle(font: font, fontSize: 10),
        ),
      );

  pw.Widget hdr(String s) =>
      pw.Container(
        padding: const pw.EdgeInsets.all(5),
        color: PdfColors.grey300,
        child: pw.Text(
          s,
          style:
              pw.TextStyle(font: font, fontSize: 10, fontWeight: pw.FontWeight.bold),
        ),
      );

  final rows = <pw.TableRow>[
    pw.TableRow(children: [
      hdr(t.colInstallmentId),
      hdr(t.colInstallmentStatus),
      hdr(t.colInstallmentAmount),
      hdr(t.colNumInstallments),
    ]),
  ];
  for (var i = 0; i < data.installmentPlans.length; i++) {
    final zebra = i.isOdd ? PdfColors.grey50 : null;
    final r = data.installmentPlans[i];
    rows.add(pw.TableRow(children: [
      pw.Container(color: zebra, child: cell('${r.installmentPlanId}')),
      pw.Container(color: zebra, child: cell(r.status)),
      pw.Container(color: zebra, child: cell(r.totalAmountDisplay)),
      pw.Container(color: zebra, child: cell('${r.numInstallments}')),
    ]));
  }
  return pw.Table(
    border: pw.TableBorder.all(color: PdfColors.grey500),
    columnWidths: const {
      0: pw.FlexColumnWidth(1),
      1: pw.FlexColumnWidth(1.2),
      2: pw.FlexColumnWidth(1),
      3: pw.FlexColumnWidth(0.9),
    },
    children: rows,
  );
}

pw.Widget _installScheduleTable(
  TreatmentPlanPdfInstallmentPlanSection plan,
  TreatmentPlanPdfTranslations t,
  pw.Font font,
) {
  pw.Widget cell(String s,
      {PdfColor? bg, pw.TextAlign a = pw.TextAlign.left}) {
    return pw.Container(
      padding: const pw.EdgeInsets.all(5),
      color: bg,
      child: pw.Text(
        s,
        style: pw.TextStyle(font: font, fontSize: 9.5),
        textAlign: a,
      ),
    );
  }

  pw.Widget hdr(String s) => pw.Container(
        padding: const pw.EdgeInsets.all(5),
        color: PdfColors.grey300,
        child: pw.Text(
          s,
          style: pw.TextStyle(
            font: font,
            fontSize: 9.5,
            fontWeight: pw.FontWeight.bold,
          ),
        ),
      );

  final rows = <pw.TableRow>[
    pw.TableRow(
      children: [
        hdr(t.colIdx),
        hdr(t.colScheduleDueDate),
        hdr(t.colScheduleAmount),
        hdr(t.colScheduleStatus),
      ],
    ),
  ];
  for (var i = 0; i < plan.scheduleRows.length; i++) {
    final zebra = i.isOdd ? PdfColors.grey50 : null;
    final r = plan.scheduleRows[i];
    rows.add(
      pw.TableRow(
        children: [
          pw.Container(color: zebra, child: cell('${r.sequenceNumber}')),
          pw.Container(color: zebra, child: cell(r.dueDateDisplay)),
          pw.Container(
            color: zebra,
            child: cell(r.amountDisplay, a: pw.TextAlign.right),
          ),
          pw.Container(color: zebra, child: cell(r.statusDisplay)),
        ],
      ),
    );
  }

  return pw.Table(
    border: pw.TableBorder.all(color: PdfColors.grey500, width: 0.5),
    columnWidths: const {
      0: pw.FixedColumnWidth(28),
      1: pw.FlexColumnWidth(1.2),
      2: pw.FlexColumnWidth(1.05),
      3: pw.FlexColumnWidth(1.05),
    },
    children: rows,
  );
}

pw.Widget _patientSignatureBlock(
  TreatmentPlanPdfData data,
  TreatmentPlanPdfTranslations t,
  pw.Font font,
) {
  pw.Widget underlineField(String caption) => pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.SizedBox(height: 28),
          pw.Container(
            height: 0.75,
            width: double.infinity,
            color: PdfColors.grey800,
          ),
          pw.SizedBox(height: 4),
          pw.Text(
            caption,
            style: pw.TextStyle(
              font: font,
              fontSize: 8.5,
              color: PdfColors.grey700,
            ),
          ),
        ],
      );

  return pw.Column(
    crossAxisAlignment: pw.CrossAxisAlignment.start,
    children: [
      pw.Text(
        t.patientSignatureNote,
        style: pw.TextStyle(font: font, fontSize: 10),
      ),
      pw.SizedBox(height: 10),
      pw.RichText(
        text: pw.TextSpan(
          children: [
            pw.TextSpan(
              text: '${t.patientName}: ',
              style: pw.TextStyle(
                font: font,
                fontSize: 10.5,
                fontWeight: pw.FontWeight.bold,
              ),
            ),
            pw.TextSpan(
              text: data.patientName,
              style: pw.TextStyle(font: font, fontSize: 10.5),
            ),
          ],
        ),
      ),
      pw.SizedBox(height: 18),
      pw.Row(
        crossAxisAlignment: pw.CrossAxisAlignment.start,
        children: [
          pw.Expanded(child: underlineField(t.signaturePatientCaption)),
          pw.SizedBox(width: 40),
          pw.Expanded(child: underlineField(t.signatureDateCaption)),
        ],
      ),
    ],
  );
}

pw.Widget _formalSection(String title, pw.Font font) {
  return pw.Container(
    margin: const pw.EdgeInsets.only(top: 2),
    padding: const pw.EdgeInsets.symmetric(horizontal: 8, vertical: 6),
    color: PdfColors.grey300,
    width: double.infinity,
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

pw.Widget _sectionBody({required pw.Widget child}) {
  return pw.Container(
    width: double.infinity,
    padding: const pw.EdgeInsets.all(12),
    decoration: pw.BoxDecoration(
      border: pw.Border.all(color: PdfColors.grey600, width: 0.75),
      color: PdfColors.grey50,
    ),
    child: child,
  );
}

pw.Widget _paragraph(String text, pw.Font font) => pw.Paragraph(
      text: text,
      margin: pw.EdgeInsets.zero,
      style: pw.TextStyle(font: font, fontSize: 11),
    );

List<String> _chunks(String text, int maxLines) {
  final lines = text.split('\n');
  if (lines.length <= maxLines) return [text];
  final out = <String>[];
  for (var i = 0; i < lines.length; i += maxLines) {
    final end = (i + maxLines).clamp(0, lines.length);
    out.add(lines.sublist(i, end).join('\n'));
  }
  return out;
}
