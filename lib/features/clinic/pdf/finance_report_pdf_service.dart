import 'package:flutter/services.dart';
import 'package:pdf/pdf.dart';
import 'package:pdf/widgets.dart' as pw;

PdfColor get _brandTeal => PdfColor(0 / 255, 187 / 255, 176 / 255);

PdfPageFormat get _pageFormat => PdfPageFormat.a4.landscape.copyWith(
      marginLeft: 32,
      marginRight: 32,
      marginTop: 32,
      marginBottom: 32,
    );

Future<Uint8List> generateFinanceTablePdf({
  required String title,
  required String subtitle,
  required List<String> headers,
  required List<List<String>> rows,
}) async {
  final fontData = await rootBundle.load('assets/fonts/DejaVuSans.ttf');
  final font = pw.Font.ttf(fontData);
  final boldData = await rootBundle.load('assets/fonts/DejaVuSans.ttf');
  final bold = pw.Font.ttf(boldData);

  final pdf = pw.Document();
  pdf.addPage(
    pw.MultiPage(
      pageFormat: _pageFormat,
      theme: pw.ThemeData.withFont(base: font, bold: bold),
      build: (ctx) => [
        pw.Text(
          title,
          style: pw.TextStyle(font: bold, fontSize: 16, color: _brandTeal),
        ),
        if (subtitle.isNotEmpty) ...[
          pw.SizedBox(height: 4),
          pw.Text(
            subtitle,
            style: pw.TextStyle(font: font, fontSize: 10, color: PdfColors.grey700),
          ),
        ],
        pw.SizedBox(height: 12),
        pw.TableHelper.fromTextArray(
          headers: headers,
          data: rows,
          headerStyle: pw.TextStyle(font: bold, fontSize: 9),
          cellStyle: pw.TextStyle(font: font, fontSize: 8),
          headerDecoration: const pw.BoxDecoration(color: PdfColors.grey300),
          cellAlignment: pw.Alignment.centerLeft,
          cellPadding: const pw.EdgeInsets.symmetric(horizontal: 4, vertical: 3),
          border: pw.TableBorder.all(color: PdfColors.grey400, width: 0.5),
        ),
      ],
      footer: (ctx) => pw.Align(
        alignment: pw.Alignment.centerRight,
        child: pw.Text(
          '${ctx.pageNumber} / ${ctx.pagesCount}',
          style: pw.TextStyle(font: font, fontSize: 8, color: PdfColors.grey600),
        ),
      ),
    ),
  );

  await Future<void>.delayed(Duration.zero);
  return pdf.save();
}
