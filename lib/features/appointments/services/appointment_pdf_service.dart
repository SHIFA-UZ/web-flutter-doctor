// lib/features/appointments/services/appointment_pdf_service.dart

import 'dart:typed_data';

import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;
import 'package:image_picker/image_picker.dart';

import 'appointment_pdf_data.dart';
import 'appointment_pdf_translations.dart';

/// Margin in points (1/72 inch). ~40px at 96 DPI ≈ 30pt; spec says 40px min.
const double _marginPt = 40;

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
/// Returns PDF bytes. Uses A4, UTF-8 font (DejaVu Sans), watermark, header, signatures, footer.
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
  final int imagePageCount = (beforeImages?.length ?? 0) + (afterImages?.length ?? 0);
  final int totalPages = 1 + imagePageCount;

  final dayNames = AppointmentPdfTranslations.dayNames(languageCode);
  final monthNames = AppointmentPdfTranslations.monthNames(languageCode);
  final d = data.appointmentDate;
  final dayOfWeek = dayNames[d.weekday - 1];
  final fullDate = '${monthNames[d.month]} ${d.day}, ${d.year}';

  // —— Report: use Column as SpanningWidget so content can flow across pages (avoids "Widget won't fit into the page").
  pdf.addPage(
    pw.MultiPage(
      pageFormat: _pageFormat,
      theme: pw.ThemeData.withFont(base: font),
      build: (pw.Context context) => [
        pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
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

  // —— Image pages ——
  int pageIndex = 2;
  if (beforeImages != null) {
    for (int i = 0; i < beforeImages.length; i++) {
      final label = beforeImages.length > 1
          ? '${t.beforeTreatment} - ${t.imageLabel} ${i + 1}'
          : t.beforeTreatment;
      await _addImagePage(pdf, beforeImages[i], label, font, t, pageIndex, totalPages);
      pageIndex++;
    }
  }
  if (afterImages != null) {
    for (int i = 0; i < afterImages.length; i++) {
      final label = afterImages.length > 1
          ? '${t.afterTreatment} - ${t.imageLabel} ${i + 1}'
          : t.afterTreatment;
      await _addImagePage(pdf, afterImages[i], label, font, t, pageIndex, totalPages);
      pageIndex++;
    }
  }

  return pdf.save();
}

/// Max lines per chunk so each widget fits on one page (~35 lines ≈ 400pt at 11pt font).
const int _maxLinesPerChunk = 35;

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
    pw.SizedBox(height: 16),
    _buildDivider(),
    pw.SizedBox(height: 20),
    _buildTitle(t, font),
    pw.SizedBox(height: 20),
    _buildAppointmentInfo(data, t, font),
    pw.SizedBox(height: 20),
  ];

  if (data.notes != null && data.notes!.trim().isNotEmpty) {
    children.add(_buildSectionTitle(t.clinicalNotes, font));
    children.add(pw.SizedBox(height: 8));
    for (final chunk in _textChunks(data.notes!.trim(), _maxLinesPerChunk)) {
      children.add(_buildNotes(chunk, font));
      children.add(pw.SizedBox(height: 6));
    }
    children.add(pw.SizedBox(height: 16));
  }
  final hasIcd = (data.diagnosisCode != null &&
      data.diagnosisCode!.trim().isNotEmpty &&
      data.diagnosisDisplay != null &&
      data.diagnosisDisplay!.trim().isNotEmpty);
  final hasFreeTextDx = (data.diagnosis != null && data.diagnosis!.trim().isNotEmpty);

  if (hasIcd || hasFreeTextDx) {
    children.add(_buildSectionTitle(t.diagnosis, font));
    children.add(pw.SizedBox(height: 4));
    if (hasIcd) {
      final line = '${data.diagnosisCode!.trim()} — ${data.diagnosisDisplay!.trim()}';
      children.add(_buildBodyText(line, font));
      children.add(pw.SizedBox(height: 4));
    }
    if (hasFreeTextDx) {
      for (final chunk in _textChunks(data.diagnosis!.trim(), _maxLinesPerChunk)) {
        children.add(_buildBodyText(chunk, font));
        children.add(pw.SizedBox(height: 4));
      }
    }
    children.add(pw.SizedBox(height: 12));
  }
  if (data.prescriptions != null && data.prescriptions!.trim().isNotEmpty) {
    children.add(_buildSectionTitle(t.prescriptions, font));
    children.add(pw.SizedBox(height: 4));
    for (final chunk in _textChunks(data.prescriptions!.trim(), _maxLinesPerChunk)) {
      children.add(_buildBodyText(chunk, font));
      children.add(pw.SizedBox(height: 4));
    }
    children.add(pw.SizedBox(height: 12));
  }
  if (data.recommendations != null && data.recommendations!.trim().isNotEmpty) {
    children.add(_buildSectionTitle(t.recommendations, font));
    children.add(pw.SizedBox(height: 4));
    for (final chunk in _textChunks(data.recommendations!.trim(), _maxLinesPerChunk)) {
      children.add(_buildBodyText(chunk, font));
      children.add(pw.SizedBox(height: 4));
    }
    children.add(pw.SizedBox(height: 12));
  }
  if (data.followUpDate != null && data.followUpDate!.trim().isNotEmpty) {
    children.add(_buildSectionTitle(t.followUpDate, font));
    children.add(pw.SizedBox(height: 4));
    children.add(_buildBodyText(data.followUpDate!, font));
    children.add(pw.SizedBox(height: 16));
  }

  children.add(pw.SizedBox(height: 80));
  children.add(_buildSignatureSection(data, t, font));
  return children;
}

/// Splits text into chunks of at most [maxLines] lines so each chunk fits on one PDF page.
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
  return pw.Row(
    mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
    crossAxisAlignment: pw.CrossAxisAlignment.start,
    children: [
      pw.Row(
        mainAxisSize: pw.MainAxisSize.min,
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
              fontSize: 18,
              fontWeight: pw.FontWeight.bold,
            ),
          ),
        ],
      ),
      pw.Column(
        crossAxisAlignment: pw.CrossAxisAlignment.end,
        children: [
          pw.Text(dayOfWeek, style: pw.TextStyle(font: font, fontSize: 11)),
          pw.Text(fullDate, style: pw.TextStyle(font: font, fontSize: 11)),
          pw.Text(data.timeStr, style: pw.TextStyle(font: font, fontSize: 11)),
          pw.SizedBox(height: 4),
          pw.Text(
            '${t.appointmentIdLabel}: ${data.appointmentId}',
            style: pw.TextStyle(font: font, fontSize: 10, color: PdfColors.grey700),
          ),
        ],
      ),
    ],
  );
}

pw.Widget _buildDivider() {
  return pw.Container(
    height: 1,
    color: PdfColors.grey400,
  );
}

pw.Widget _buildTitle(AppointmentPdfTranslations t, pw.Font font) {
  return pw.Center(
    child: pw.Text(
      t.appointmentSummary,
      style: pw.TextStyle(
        font: font,
        fontSize: 16,
        fontWeight: pw.FontWeight.bold,
      ),
    ),
  );
}

pw.Widget _buildAppointmentInfo(
  AppointmentPdfData data,
  AppointmentPdfTranslations t,
  pw.Font font,
) {
  return pw.Row(
    crossAxisAlignment: pw.CrossAxisAlignment.start,
    children: [
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            _infoRow(t.patientName, data.patientName, font),
            if (data.patientId != null && data.patientId!.isNotEmpty)
              _infoRow(t.patientId, data.patientId!, font),
            if (data.dateOfBirth != null && data.dateOfBirth!.isNotEmpty)
              _infoRow(t.dateOfBirth, data.dateOfBirth!, font),
            if (data.gender != null && data.gender!.isNotEmpty)
              _infoRow(t.gender, data.gender!, font),
          ],
        ),
      ),
      pw.SizedBox(width: 32),
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            if (data.doctorName != null && data.doctorName!.isNotEmpty)
              _infoRow(t.doctorName, data.doctorName!, font),
            if (data.specialization != null && data.specialization!.isNotEmpty)
              _infoRow(t.specialization, data.specialization!, font),
            if (data.licenseNumber != null && data.licenseNumber!.isNotEmpty)
              _infoRow(t.licenseNumber, data.licenseNumber!, font),
            _infoRow(t.appointmentType, data.appointmentType, font),
            if (data.duration != null && data.duration!.isNotEmpty)
              _infoRow(t.duration, data.duration!, font),
          ],
        ),
      ),
    ],
  );
}

pw.Widget _infoRow(String label, String value, pw.Font font) {
  return pw.Padding(
    padding: const pw.EdgeInsets.only(bottom: 6),
    child: pw.RichText(
      text: pw.TextSpan(
        children: [
          pw.TextSpan(
            text: '$label: ',
            style: pw.TextStyle(font: font, fontSize: 11, fontWeight: pw.FontWeight.bold),
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

pw.Widget _buildSectionTitle(String title, pw.Font font) {
  return pw.Text(
    title,
    style: pw.TextStyle(
      font: font,
      fontSize: 13,
      fontWeight: pw.FontWeight.bold,
    ),
  );
}

pw.Widget _buildBodyText(String text, pw.Font font) {
  return pw.Text(
    text,
    style: pw.TextStyle(font: font, fontSize: 11),
  );
}

pw.Widget _buildNotes(String notes, pw.Font font) {
  return pw.Text(
    notes,
    style: pw.TextStyle(font: font, fontSize: 11),
  );
}

pw.Widget _buildSignatureSection(
  AppointmentPdfData data,
  AppointmentPdfTranslations t,
  pw.Font font,
) {
  // Patient side: show signature image when present; show date line only if patientSignedAt is set
  final hasPatientSignatureImage = data.patientSignatureImageBytes != null &&
      data.patientSignatureImageBytes!.isNotEmpty;

  return pw.Row(
    mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
    crossAxisAlignment: pw.CrossAxisAlignment.end,
    children: [
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text(
              '${t.doctorSignature}:',
              style: pw.TextStyle(font: font, fontSize: 10, fontWeight: pw.FontWeight.bold),
            ),
            pw.SizedBox(height: 2),
            pw.Container(
              width: 180,
              height: 1,
              color: PdfColors.grey800,
            ),
            pw.SizedBox(height: 4),
            pw.Text(
              data.doctorName ?? '—',
              style: pw.TextStyle(font: font, fontSize: 9),
            ),
            if (data.licenseNumber != null && data.licenseNumber!.isNotEmpty)
              pw.Text(
                data.licenseNumber!,
                style: pw.TextStyle(font: font, fontSize: 8, color: PdfColors.grey700),
              ),
          ],
        ),
      ),
      pw.Expanded(
        child: pw.Column(
          crossAxisAlignment: pw.CrossAxisAlignment.start,
          children: [
            pw.Text(
              '${t.patientSignature}:',
              style: pw.TextStyle(font: font, fontSize: 10, fontWeight: pw.FontWeight.bold),
            ),
            pw.SizedBox(height: 2),
            if (hasPatientSignatureImage) ...[
              pw.Container(
                width: 180,
                height: 48,
                child: pw.Image(
                  pw.MemoryImage(data.patientSignatureImageBytes!),
                  fit: pw.BoxFit.contain,
                ),
              ),
              pw.SizedBox(height: 4),
              if (data.patientSignedAt != null)
                pw.Text(
                  _formatSignedAt(data.patientSignedAt!, t.signedElectronicallyOn),
                  style: pw.TextStyle(font: font, fontSize: 8, color: PdfColors.grey700),
                ),
              pw.Text(
                t.viaShifaPatientApp,
                style: pw.TextStyle(font: font, fontSize: 8, color: PdfColors.grey700),
              ),
            ] else ...[
              pw.Container(
                width: 180,
                height: 1,
                color: PdfColors.grey800,
              ),
              pw.SizedBox(height: 4),
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
  return pw.Column(
    children: [
      pw.Row(
        mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
        children: [
          pw.Text(
            t.generatedByShifa,
            style: pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey700),
          ),
          pw.Text(
            '${t.pageOf} $pageNumber / $totalPages',
            style: pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey700),
          ),
        ],
      ),
      pw.SizedBox(height: 6),
      pw.Center(
        child: pw.Text(
          t.confidentialMedicalDocument,
          style: pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey600),
        ),
      ),
    ],
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
            _buildDivider(),
            pw.SizedBox(height: 8),
            pw.Row(
              mainAxisAlignment: pw.MainAxisAlignment.spaceBetween,
              children: [
                pw.Text(
                  t.generatedByShifa,
                  style: pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey700),
                ),
                pw.Text(
                  '${t.pageOf} $pageNumber / $totalPages',
                  style: pw.TextStyle(font: font, fontSize: 9, color: PdfColors.grey700),
                ),
              ],
            ),
          ],
        );
      },
    ),
  );
}
