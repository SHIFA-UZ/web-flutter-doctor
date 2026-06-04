import 'dart:convert';
import 'dart:typed_data';

import 'package:flutter/foundation.dart' show kIsWeb;
import 'package:share_plus/share_plus.dart';
import 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_stub.dart'
    if (dart.library.html) 'package:shifa_doc_app_v1/features/admin/presentation/admin_pdf_downloader_web.dart'
    as file_download;

String financeCsvCell(String? value) {
  if (value == null || value.isEmpty) return '';
  if (value.contains(',') || value.contains('"') || value.contains('\n')) {
    return '"${value.replaceAll('"', '""')}"';
  }
  return value;
}

/// Plain amount string for CSV (no currency symbol formatting).
String financeCsvAmount(int amountMinor, String currency) {
  if (currency == 'UZS') {
    return '${amountMinor ~/ 100}';
  }
  return (amountMinor / 100).toStringAsFixed(2);
}

Future<void> downloadFinanceCsv(String filename, String content) async {
  final bytes = Uint8List.fromList(utf8.encode(content));
  if (!kIsWeb) {
    await Share.shareXFiles(
      [XFile.fromData(bytes, name: filename, mimeType: 'text/csv')],
      subject: filename,
    );
    return;
  }
  await file_download.downloadBytes(
    bytes,
    filename: filename,
    mimeType: 'text/csv;charset=utf-8',
  );
}

Future<void> downloadFinancePdf(Uint8List bytes, String filename) async {
  if (!kIsWeb) {
    await Share.shareXFiles(
      [XFile.fromData(bytes, name: filename, mimeType: 'application/pdf')],
      subject: filename,
    );
    return;
  }
  await file_download.downloadPdfBytes(bytes, filename: filename);
}
